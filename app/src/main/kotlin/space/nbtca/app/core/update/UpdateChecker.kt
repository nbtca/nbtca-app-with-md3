package space.nbtca.app.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val prerelease: Boolean = false
)

enum class UpdateChannel { STABLE, BETA }

/**
 * 从 GitHub Releases 检查更新：
 * · 稳定版读取 /releases/latest
 * · Beta 读取 /releases 列表，筛选预发布版本。
 */
object UpdateChecker {

    private const val REPOSITORY = "nbtca/nbtca-app-with-md3"
    const val RELEASES_PAGE = "https://github.com/$REPOSITORY/releases"
    private const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases"
    private const val LATEST_API = "$RELEASES_API/latest"
    private const val LIST_API = "$RELEASES_API?per_page=20"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(
        currentVersion: String,
        channel: UpdateChannel
    ): Result<ReleaseInfo?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val releases = when (channel) {
                UpdateChannel.STABLE -> {
                    val latest = parseReleaseObject(json.parseToJsonElement(get(LATEST_API)).jsonObject)
                    listOfNotNull(latest)
                }
                UpdateChannel.BETA -> {
                    val releasesJson = json.parseToJsonElement(get(LIST_API)).jsonArray
                    releasesJson.mapNotNull { item -> (item as? JsonObject)?.let(::parseReleaseObject) }
                        .filter { it.prerelease }
                }
            }
            releases.firstOrNull { isNewer(it.version, currentVersion) }
        }.onFailure {
            android.util.Log.e("UpdateChecker", "check failed", it)
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "nbtca-app-with-md3-android")
            .get()
            .build()
        return http.newCall(request).execute().use { response ->
            if (response.code == 404) {
                throw IllegalStateException("发布页暂不可访问，仓库可能为私有或尚未发布版本")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub 返回 HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun parseReleaseObject(root: JsonObject): ReleaseInfo? {
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val assets = (root["assets"] as? JsonArray) ?: JsonArray(emptyList())
        val apk = assets.mapNotNull { it as? JsonObject }
            .firstOrNull { asset ->
                (asset["name"] as? JsonPrimitive)?.contentOrNull?.endsWith(".apk", true) == true
            }
            ?.let { asset -> (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull }
        return ReleaseInfo(
            version = normalize(tag),
            name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: tag,
            notes = (root["body"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            pageUrl = (root["html_url"] as? JsonPrimitive)?.contentOrNull ?: RELEASES_PAGE,
            apkUrl = apk,
            prerelease = (root["prerelease"] as? JsonPrimitive)?.contentOrNull?.toBoolean() ?: false
        )
    }

    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        for (index in 0 until maxOf(a.core.size, b.core.size)) {
            val left = a.core.getOrElse(index) { 0L }
            val right = b.core.getOrElse(index) { 0L }
            if (left != right) return left > right
        }
        if (a.prerelease.isEmpty() || b.prerelease.isEmpty()) {
            return a.prerelease.isEmpty() && b.prerelease.isNotEmpty()
        }
        for (index in 0 until minOf(a.prerelease.size, b.prerelease.size)) {
            val left = a.prerelease[index]
            val right = b.prerelease[index]
            if (left == right) continue
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            return when {
                leftNumber != null && rightNumber != null -> leftNumber > rightNumber
                leftNumber != null -> false
                rightNumber != null -> true
                else -> left > right
            }
        }
        return a.prerelease.size > b.prerelease.size
    }

    private data class Version(val core: List<Long>, val prerelease: List<String>)

    private fun parseVersion(raw: String): Version? {
        val parts = normalize(raw).substringBefore('+').split('-', limit = 2)
        val core = parts[0].split('.').map { it.toLongOrNull() ?: return null }
        val prerelease = parts.getOrNull(1)?.split('.') ?: emptyList()
        if (prerelease.any { it.isEmpty() }) return null
        return Version(core, prerelease)
    }
}
