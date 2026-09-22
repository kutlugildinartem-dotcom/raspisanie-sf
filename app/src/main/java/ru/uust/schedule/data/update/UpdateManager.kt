package ru.uust.schedule.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.sigpipe.jbsdiff.Patch
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.uust.schedule.BuildConfig
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.remote.PatchInfo
import ru.uust.schedule.data.remote.ReleaseInfo
import ru.uust.schedule.data.remote.ScheduleApi
import ru.uust.schedule.data.remote.UpdateApi
import ru.uust.schedule.data.remote.VersionCompare

/** Что показывать в баннере обновления. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    /** [isDelta] — качаем патч (сотни КБ), а не весь APK — баннер показывает это отдельно. */
    data class Downloading(val release: ReleaseInfo, val progress: Float, val isDelta: Boolean = false) :
        UpdateState
    /** APK скачан и передан системе; дальше пользователь жмёт «Установить» в диалоге Android. */
    data class ReadyToInstall(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Обновление приложения без переустановки.
 *
 * APK ставится поверх установленного через PackageInstaller: подпись та же,
 * поэтому Android обновляет приложение на месте и сохраняет настройки,
 * выбранную группу и заметки.
 *
 * Когда для версии есть бинарный патч (см. release.sh), скачивается только он —
 * обычно сотни килобайт вместо нескольких мегабайт целого APK. Патч применяется
 * к байтам уже установленного APK (он и так лежит на телефоне, скачивать не надо)
 * через bspatch. Результат целиком проверяется по SHA-256 ДО того, как попасть
 * в сессию установки: если контрольная сумма базы или результата не совпала —
 * патч тихо отбрасывается и скачивается обычный полный APK. Пользователь никогда
 * не видит эту неудачу — только чуть больший трафик в тот раз.
 */
object UpdateManager {

    private const val SESSION_NAME = "uust_update"
    const val ACTION_INSTALL_RESULT = "ru.uust.schedule.INSTALL_RESULT"

    /** Не чаще раза в сутки дёргать GitHub тихой проверкой — трафик того не стоит. */
    private const val SILENT_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val api = UpdateApi()
    private val client: OkHttpClient = ScheduleApi.defaultClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Закрывает баннер и запоминает версию: при следующих тихих проверках она
     * больше не всплывёт сама. Явная проверка кнопкой «Проверить» это не учитывает —
     * если её нажали, значит хотят увидеть даже то, что уже закрывали.
     */
    fun dismiss(context: Context) {
        val version = (_state.value as? UpdateState.Available)?.release?.versionName
        _state.value = UpdateState.Idle
        if (version != null) {
            scope.launch {
                SettingsStore.get(context).update { it.copy(dismissedUpdateVersion = version) }
            }
        }
    }

    suspend fun check(context: Context, silent: Boolean = true) = withContext(Dispatchers.IO) {
        if (_state.value is UpdateState.Downloading) return@withContext

        val store = SettingsStore.get(context)
        val settings = store.current()

        if (silent) {
            val elapsed = System.currentTimeMillis() - settings.lastUpdateCheckAt
            if (elapsed < SILENT_CHECK_INTERVAL_MS) return@withContext
        } else {
            _state.value = UpdateState.Checking
        }

        store.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }

        val result = runCatching { api.latestRelease() }
        result.onFailure { error ->
            // При тихой проверке молчим: недоступный GitHub не повод пугать пользователя.
            _state.value = if (silent) UpdateState.Idle
            else UpdateState.Failed(error.message ?: "Не удалось проверить обновления")
        }

        val release = result.getOrNull() ?: run {
            if (!silent && result.isSuccess) _state.value = UpdateState.UpToDate
            return@withContext
        }

        if (!VersionCompare.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.UpToDate
            return@withContext
        }

        // Версию, которую уже закрывали, тихая проверка больше не показывает —
        // иначе баннер возвращался бы при каждом возврате в приложение.
        if (silent && release.versionName == settings.dismissedUpdateVersion) {
            return@withContext
        }

        _state.value = UpdateState.Available(release)
    }

    suspend fun downloadAndInstall(context: Context, release: ReleaseInfo) =
        withContext(Dispatchers.IO) {
            val patch = release.patch
            if (patch != null && patch.fromVersion == BuildConfig.VERSION_NAME) {
                val bytes = runCatching { buildFromPatch(context, release, patch) }.getOrNull()
                if (bytes != null) {
                    installReconstructed(context, release, bytes)
                    return@withContext
                }
                // Патч не подошёл или не применился — тихо откатываемся на полную загрузку.
                // Пользователь не должен видеть техническую неудачу там, где обновление
                // всё равно сейчас скачается и установится обычным путём.
            }
            downloadFullAndInstall(context, release)
        }

    /**
     * Собирает новый APK из уже установленного плюс патча. Возвращает null на любом
     * несовпадении контрольной суммы — вызывающая сторона обязана откатиться на
     * полную загрузку, а не показывать пользователю ошибку.
     */
    private fun buildFromPatch(context: Context, release: ReleaseInfo, patch: PatchInfo): ByteArray? {
        val ownApk = runCatching { File(context.applicationInfo.sourceDir).readBytes() }
            .getOrNull() ?: return null

        // Патч посчитан для конкретных байт предыдущей версии — если на телефоне
        // почему-либо другая сборка (переустановка, ручная правка), патч не подойдёт.
        if (sha256(ownApk) != patch.baseSha256) return null

        _state.value = UpdateState.Downloading(release, 0f, isDelta = true)
        val patchBytes = runCatching {
            downloadBytes(patch.url, patch.sizeBytes) { progress ->
                _state.value = UpdateState.Downloading(release, progress, isDelta = true)
            }
        }.getOrNull() ?: return null

        val out = ByteArrayOutputStream(release.sizeBytes.coerceAtLeast(1L).toInt())
        val applied = runCatching { Patch.patch(ownApk, patchBytes, out) }.isSuccess
        if (!applied) return null

        val result = out.toByteArray()
        // Финальная проверка результата патчинга — только теперь, когда байты верны,
        // они вообще попадают в сессию установки.
        if (sha256(result) != patch.resultSha256) return null
        return result
    }

    private fun downloadBytes(url: String, expectedSize: Long, onProgress: (Float) -> Unit): ByteArray {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IllegalStateException("Пустой ответ при скачивании")
            if (!response.isSuccessful) throw IllegalStateException("Скачивание не удалось (${response.code})")

            val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
            val out = ByteArrayOutputStream(total.coerceAtLeast(1L).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            return out.toByteArray()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Уже проверенные по контрольной сумме байты — просто записать в сессию и закоммитить. */
    private fun installReconstructed(context: Context, release: ReleaseInfo, bytes: ByteArray) {
        runCatching {
            val (_, session, sessionId) = openSession(context)
            session.use {
                it.openWrite(SESSION_NAME, 0, bytes.size.toLong()).use { out ->
                    out.write(bytes)
                    it.fsync(out)
                }
                commitSession(context, it, sessionId)
            }
            _state.value = UpdateState.ReadyToInstall(release)
        }.onFailure { error ->
            _state.value = UpdateState.Failed(error.message ?: "Не удалось установить обновление")
        }
    }

    /** Прежний путь: качаем весь APK потоком прямо в сессию установки, без файла на диске. */
    private fun downloadFullAndInstall(context: Context, release: ReleaseInfo) {
        _state.value = UpdateState.Downloading(release, 0f)

        runCatching {
            val (_, session, sessionId) = openSession(context)
            session.use {
                val request = Request.Builder().url(release.apkUrl).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: throw IllegalStateException("Пустой ответ при скачивании")
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Скачивание не удалось (${response.code})")
                    }

                    val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                    it.openWrite(SESSION_NAME, 0, total).use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    _state.value = UpdateState.Downloading(
                                        release, (written.toFloat() / total).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                        it.fsync(out)
                    }
                }
                commitSession(context, it, sessionId)
            }
            _state.value = UpdateState.ReadyToInstall(release)
        }.onFailure { error ->
            _state.value = UpdateState.Failed(error.message ?: "Не удалось установить обновление")
        }
    }

    private data class Session(
        val installer: PackageInstaller,
        val session: PackageInstaller.Session,
        val sessionId: Int,
    )

    private fun openSession(context: Context): Session {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        return Session(installer, installer.openSession(sessionId), sessionId)
    }

    private fun commitSession(context: Context, session: PackageInstaller.Session, sessionId: Int) {
        // MUTABLE обязателен: система дописывает в этот intent свой статус.
        val callback = PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        session.commit(callback.intentSender)
    }

    /** На Android 8+ установка из приложения требует отдельного разрешения пользователя. */
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    internal fun report(state: UpdateState) {
        _state.value = state
    }
}

/** Принимает статус установки от PackageInstaller. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.ACTION_INSTALL_RESULT) return

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Система просит подтверждение — показываем её диалог установки.
                val confirm = @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { confirm?.let(context::startActivity) }
            }

            PackageInstaller.STATUS_SUCCESS -> UpdateManager.report(UpdateState.Idle)

            PackageInstaller.STATUS_FAILURE_ABORTED ->
                UpdateManager.report(UpdateState.Idle)

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateManager.report(
                    UpdateState.Failed(message ?: "Установка не завершилась")
                )
            }
        }
    }
}
