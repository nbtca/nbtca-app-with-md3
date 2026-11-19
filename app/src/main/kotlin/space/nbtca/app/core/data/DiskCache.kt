package space.nbtca.app.core.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/**
 * 极简的磁盘 JSON 缓存，用来在断网时显示上一次成功的数据。
 * 文件名使用 key 的 SHA-256，避免文章 id 里的 "/" 变成目录。
 */
class DiskCache(directory: File) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "nbtca-cache"))

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val root = directory.apply { mkdirs() }

    fun <T> read(key: String, serializer: KSerializer<T>, maxAgeMs: Long): T? {
        val file = fileFor(key)
        if (!file.isFile) return null
        if (maxAgeMs > 0 && System.currentTimeMillis() - file.lastModified() > maxAgeMs) return null
        return runCatching { json.decodeFromString(serializer, file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    /** 忽略过期时间，用于网络失败时的 last-good 回退。 */
    fun <T> readStale(key: String, serializer: KSerializer<T>): T? =
        read(key, serializer, maxAgeMs = 0)

    fun <T> write(key: String, serializer: KSerializer<T>, value: T): Boolean {
        val target = fileFor(key)
        val temp = File(root, target.name + ".tmp")
        return synchronized(this) {
            val written = runCatching {
                temp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
                Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            }.isSuccess
            if (!written) runCatching { Files.deleteIfExists(temp.toPath()) }
            written
        }
    }

    fun remove(key: String) {
        runCatching { fileFor(key).delete() }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, digest + ".json")
    }
}
