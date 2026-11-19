package space.nbtca.app.core.academic

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * 教务数据本地仓库：学号、密码（Keystore 加密）、课表/考试/成绩快照全部落盘，
 * 断网也能看。密码只用于自动重新登录教务系统。
 */
class AcademicStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("academic_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    var studentId: String
        get() = prefs.getString(KEY_STUDENT_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_STUDENT_ID, value.trim()).apply()

    var password: String
        get() = decrypt(prefs.getString(KEY_PASSWORD, null)).orEmpty()
        set(value) {
            if (value.isEmpty()) {
                prefs.edit().remove(KEY_PASSWORD).apply()
            } else {
                encrypt(value)?.let { prefs.edit().putString(KEY_PASSWORD, it).apply() }
            }
        }

    val hasCredentials: Boolean get() = studentId.isNotEmpty() && password.isNotEmpty()

    @Synchronized
    fun loadSnapshots(): List<AcademicSnapshot> {
        val snapshots = readSnapshots()
        val owner = studentId.ifBlank { snapshots.firstOrNull()?.studentId.orEmpty() }
        val otherAccountExists = snapshots.any { it.studentId.isNotBlank() && it.studentId != owner }
        return snapshots.filter { it.studentId == owner || (it.studentId.isBlank() && !otherAccountExists) }
    }

    private fun readSnapshots(): List<AcademicSnapshot> {
        val current = prefs.getString(KEY_SNAPSHOTS, null)?.let {
            runCatching { json.decodeFromString<List<AcademicSnapshot>>(it) }.getOrNull()
        }
        if (current != null) return current
        val legacy = prefs.getString(KEY_SNAPSHOT, null) ?: return emptyList()
        return listOfNotNull(runCatching { json.decodeFromString<AcademicSnapshot>(legacy) }.getOrNull())
    }

    @Synchronized
    fun saveSnapshot(snapshot: AcademicSnapshot) {
        val stored = readSnapshots()
        val otherAccountExists = stored.any { it.studentId.isNotBlank() && it.studentId != snapshot.studentId }
        val retained = stored.filterNot {
            val sameAccount = it.studentId == snapshot.studentId || (it.studentId.isBlank() && !otherAccountExists)
            sameAccount && it.matchesTerm(snapshot)
        }
        val editor = prefs.edit()
            .putString(KEY_SNAPSHOTS, json.encodeToString(listOf(snapshot) + retained))
            .remove(KEY_SNAPSHOT)
        if (snapshot.studentId.isNotBlank()) editor.putString(KEY_STUDENT_ID, snapshot.studentId)
        editor.apply()
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_STUDENT_ID).remove(KEY_PASSWORD).remove(KEY_COOKIES).apply()
    }

    suspend fun signOut() = withContext(NonCancellable + Dispatchers.Main) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            suspendCancellableCoroutine<Unit> { completion ->
                WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), "nbt.edu.cn") {
                    completion.resume(Unit)
                }
            }
        } else {
            withContext(Dispatchers.IO) { clearLegacyWebCookies() }
        }
        clearCredentials()
    }

    private fun clearLegacyWebCookies() {
        val manager = CookieManager.getInstance()
        val entryPoints = listOf(
            "https://webvpn.nbt.edu.cn/users/auth/cas/callback",
            "https://webvpn.nbt.edu.cn/users/sign_in",
            "https://webvpn.nbt.edu.cn/login",
            "https://webvpn.nbt.edu.cn/vpn_key/update",
            "https://authserver-443.webvpn.nbt.edu.cn/authserver/login",
            "https://authserver.nbt.edu.cn/authserver/login",
            AcademicWebSync.JWXT_SSO_URL,
            AcademicWebSync.JWXT_MENU_URL,
            AcademicWebSync.TIMETABLE_URL
        )
        for (url in entryPoints) {
            val header = manager.getCookie(url) ?: continue
            val names = header.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.distinct()
            val uri = Uri.parse(url)
            val host = requireNotNull(uri.host)
            val domains = listOf(null, host, host.substringAfter('.', host), "nbt.edu.cn")
                .filter { it == null || it == "nbt.edu.cn" || it.endsWith(".nbt.edu.cn") }
                .distinct()
            // CookieManager on older WebViews exposes names without their Domain and Path attributes.
            var path = ""
            val paths = buildList {
                add("/")
                for (segment in uri.pathSegments) {
                    path += "/$segment"
                    add(path)
                    add("$path/")
                }
            }
            for (name in names) for (domain in domains) for (cookiePath in paths) {
                val domainAttribute = domain?.let { "; Domain=$it" }.orEmpty()
                manager.setCookie(url, "$name=; Max-Age=0; Path=$cookiePath; Secure$domainAttribute")
            }
        }
        manager.flush()
    }

    fun clearAll() = prefs.edit().clear().apply()

    var cookies: String?
        get() = prefs.getString(KEY_COOKIES, null)
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    // ---------------- Keystore AES/GCM ----------------

    private fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        generator.generateKey()
    }.getOrNull()

    private fun encrypt(plain: String): String? = runCatching {
        val key = secretKey() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray())
        Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return runCatching {
            val key = secretKey() ?: return null
            val data = Base64.decode(stored, Base64.NO_WRAP)
            if (data.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            String(cipher.doFinal(data, 12, data.size - 12))
        }.getOrNull()
    }

    companion object {
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_PASSWORD = "password_cipher"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_SNAPSHOTS = "term_snapshots"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_ALIAS = "nbtca_academic_password"

        @Volatile
        private var instance: AcademicStore? = null

        fun getInstance(context: Context): AcademicStore =
            instance ?: synchronized(this) {
                instance ?: AcademicStore(context.applicationContext).also { instance = it }
            }
    }
}
