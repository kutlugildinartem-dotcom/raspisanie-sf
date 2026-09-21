package ru.uust.schedule.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.uust.schedule.BuildConfig

/** Релиз, найденный на GitHub. */
data class ReleaseInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
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

        return ReleaseInfo(
            versionName = dto.tagName.removePrefix("v").trim(),
            notes = dto.body.orEmpty().trim(),
            apkUrl = asset.browserDownloadUrl,
            sizeBytes = asset.size,
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
