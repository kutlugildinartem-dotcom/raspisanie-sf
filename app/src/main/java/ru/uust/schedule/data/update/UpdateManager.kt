package ru.uust.schedule.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.uust.schedule.BuildConfig
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
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
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
 * Настоящих дельта-обновлений здесь нет и быть не может — патчить установленный
 * APK умеет только Google Play. Скачивается релиз целиком, но он поток за потоком
 * пишется прямо в сессию установки, без промежуточного файла на диске.
 */
object UpdateManager {

    private const val SESSION_NAME = "uust_update"
    const val ACTION_INSTALL_RESULT = "ru.uust.schedule.INSTALL_RESULT"

    private val api = UpdateApi()
    private val client: OkHttpClient = ScheduleApi.defaultClient()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Сбрасывает баннер, если пользователь его закрыл. */
    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    suspend fun check(silent: Boolean = true) = withContext(Dispatchers.IO) {
        if (_state.value is UpdateState.Downloading) return@withContext
        if (!silent) _state.value = UpdateState.Checking

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

        _state.value = if (VersionCompare.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
            UpdateState.Available(release)
        } else {
            if (silent) UpdateState.Idle else UpdateState.UpToDate
        }
    }

    /**
     * Скачивает релиз и отдаёт системе. Возвращает управление сразу после commit —
     * дальше Android сам показывает диалог подтверждения.
     */
    suspend fun downloadAndInstall(context: Context, release: ReleaseInfo) =
        withContext(Dispatchers.IO) {
            _state.value = UpdateState.Downloading(release, 0f)

            runCatching {
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
                installer.openSession(sessionId).use { session ->
                    val request = Request.Builder().url(release.apkUrl).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body
                            ?: throw IllegalStateException("Пустой ответ при скачивании")
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Скачивание не удалось (${response.code})")
                        }

                        val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                        session.openWrite(SESSION_NAME, 0, total).use { out ->
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
                            session.fsync(out)
                        }
                    }

                    // MUTABLE обязателен: система дописывает в этот intent свой статус.
                    val callback = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(callback.intentSender)
                }
                _state.value = UpdateState.ReadyToInstall(release)
            }.onFailure { error ->
                _state.value = UpdateState.Failed(error.message ?: "Не удалось установить обновление")
            }
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
