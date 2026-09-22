package ru.uust.schedule.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.uust.schedule.BuildConfig

/**
 * Бинарный патч на этот релиз: применяется к уже установленному APK
 * предыдущей версии и восстанавливает новый APK без скачивания его целиком.
 */
data class PatchInfo(
    /** Версия, от которой посчитан патч. Патч годится только для неё. */
    val fromVersion: String,
    /** SHA-256 APK версии [fromVersion] — проверка, что на телефоне ровно та же сборка. */
    val baseSha256: String,
    val url: String,
    val sizeBytes: Long,
    /** SHA-256 APK, который должен получиться после применения патча. */
    val resultSha256: String,
) {
    val sizeMb: String get() = "%.2f МБ".format(sizeBytes / 1024.0 / 1024.0)
}

/** Релиз, найденный на GitHub. */
data class ReleaseInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** null, если патча нет — первый релиз с этой системой или сайт вернул неполные данные. */
    val patch: PatchInfo? = null,
) {
    val sizeMb: String get() = "%.1f МБ".format(sizeBytes / 1024.0 / 1024.0)
}

/**
 * Проверка обновлений через публичный GitHub Releases API.
 *
 * Репозиторий обязан быть публичным: приватный отдаёт ассеты только по токену,
 * а токен, зашитый в APK, достаётся из него за минуту.
 */
class UpdateApi(
    private val client: OkHttpClient = ScheduleApi.defaultClient(),
    private val owner: String = BuildConfig.GITHUB_OWNER,
    private val repo: String = BuildConfig.GITHUB_REPO,
    private val apiBase: String = "https://api.github.com",
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Последний опубликованный релиз, или null, если релизов ещё нет. */
    fun latestRelease(): ReleaseInfo? {
        val request = Request.Builder()
            .url("$apiBase/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        val body = client.newCall(request).execute().use { response ->
            // 404 — релизов ещё нет; это не ошибка, просто обновлять нечего.
            if (response.code == 404) return null
            if (!response.isSuccessful) throw ScheduleException("GitHub ответил ${response.code}")
            response.body?.string().orEmpty()
        }

        val dto = json.decodeFromString(ReleaseDto.serializer(), body)
        if (dto.draft || dto.prerelease) return null

        val asset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null

        // Манифест — необязательный ассет: его отсутствие или битый формат не должны
        // мешать обычному полному обновлению, поэтому любая ошибка здесь просто даёт null.
        val manifestAsset = dto.assets.firstOrNull { it.name == MANIFEST_NAME }
        val patch = manifestAsset?.let { runCatching { fetchPatchInfo(it.browserDownloadUrl) }.getOrNull() }

        return ReleaseInfo(
            versionName = dto.tagName.removePrefix("v").trim(),
            notes = dto.body.orEmpty().trim(),
            apkUrl = asset.browserDownloadUrl,
            sizeBytes = asset.size,
            patch = patch,
        )
    }

    private fun fetchPatchInfo(manifestUrl: String): PatchInfo? {
        val body = client.newCall(Request.Builder().url(manifestUrl).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.string().orEmpty()
        }
        val manifest = json.decodeFromString(ManifestDto.serializer(), body)
        val p = manifest.patch ?: return null
        return PatchInfo(
            fromVersion = p.fromVersion,
            baseSha256 = p.baseSha256,
            url = p.url,
            sizeBytes = p.size,
            resultSha256 = p.resultSha256,
        )
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        val size: Long = 0,
    )

    @Serializable
    private data class ManifestDto(
        val version: String = "",
        val apkSha256: String = "",
        val patch: PatchDto? = null,
    )

    @Serializable
    private data class PatchDto(
        val fromVersion: String,
        val baseSha256: String,
        val url: String,
        val size: Long,
        val resultSha256: String,
    )

    companion object {
        const val MANIFEST_NAME = "update-manifest.json"
    }
}

/**
 * Сравнение версий вида «1.2.3» по числовым сегментам.
 *
 * Строковое сравнение здесь не годится: "1.10" лексикографически меньше "1.9",
 * хотя версия новее.
 */
object VersionCompare {

    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun segments(version: String): List<Int> =
        version.trim().removePrefix("v")
            .split('.', '-', '+')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}
