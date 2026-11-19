package space.nbtca.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import space.nbtca.app.core.model.AuthState
import space.nbtca.app.core.model.UserProfile
import java.util.concurrent.TimeUnit

/**
 * Logto 身份 + Saturday 会员角色 + 界面视角。
 * 与 Flutter AuthService 行为对齐：token 过期自动 refresh，视角切换不改 token。
 */
class AuthManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private val sessionLock = Any()

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var idToken: String? = null
    private var expiresAt: Long = 0L
    private var viewRole: String? = null
    private var loginAttempt = 0L

    init {
        restore()
    }

    private fun restore() {
        accessToken = TokenCipher.decrypt(prefs.getString(KEY_ACCESS, null))
            ?: prefs.getString(KEY_ACCESS, null)   // 兼容旧版本明文令牌
        refreshToken = TokenCipher.decrypt(prefs.getString(KEY_REFRESH, null))
            ?: prefs.getString(KEY_REFRESH, null)
        idToken = TokenCipher.decrypt(prefs.getString(KEY_ID, null))
            ?: prefs.getString(KEY_ID, null)
        expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        viewRole = prefs.getString(KEY_VIEW_ROLE, null)
        val legacyProfile = prefs.getString(KEY_PROFILE, null)
        val cached = TokenCipher.decrypt(prefs.getString(KEY_ENCRYPTED_PROFILE, null)) ?: legacyProfile
        val profile = cached?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }
        if (profile != null && profile.sub.isNotBlank() && !accessToken.isNullOrEmpty()) {
            _state.value = AuthState(profile = profile, isLoggedIn = true, viewRole = viewRole)
            if (legacyProfile != null) prefs.edit().putCachedProfile(profile).apply()
        }
    }

    fun currentToken(): String? = synchronized(sessionLock) { accessToken }

    /** 业务请求前取有效 token，过期则自动 refresh。 */
    suspend fun getValidAccessToken(): String? {
        val (token, expiry) = synchronized(sessionLock) { accessToken to expiresAt }
        if (token == null) return null
        if (token.startsWith("dev_") || token.startsWith("mock_")) return token
        if (expiry == 0L || System.currentTimeMillis() < expiry - 60_000L) return token
        return refresh()
    }

    /**
     * 串行刷新：Logto 的 refresh_token 是一次性轮换的，并发刷新会让后一次拿到
     * 已失效的旧 token（invalid_grant）从而被误判成"登录过期"。
     */
    private suspend fun refresh(): String? = refreshMutex.withLock {
        // 拿到锁后先复查：可能已被其他请求刷新过。
        val (current, rt, expiry) = synchronized(sessionLock) { Triple(accessToken, refreshToken, expiresAt) }
        if (current == null) return@withLock null
        if (System.currentTimeMillis() < expiry - 60_000L) {
            return@withLock current
        }
        if (rt.isNullOrEmpty()) {
            return@withLock current.takeIf { System.currentTimeMillis() < expiry }
        }
        val result = LogtoAuth.refreshToken(http, rt)
        synchronized(sessionLock) {
            if (accessToken != current || refreshToken != rt) return@withLock null
            result.fold(
                onSuccess = { tokens ->
                    applyTokens(tokens, rt)
                    tokens.access_token
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (error is LogtoHttpError && (error.code == 401 ||
                            (error.code == 400 && parseFlatObject(error.body)?.get("error") == "invalid_grant"))
                    ) {
                        logout()
                        null
                    } else {
                        current.takeIf { System.currentTimeMillis() < expiry }
                    }
                }
            )
        }
    }

    /**
     * App 启动时恢复登录：token 临近过期则静默刷新，再校验一次用户资料。
     * 全程不阻塞首屏，网络失败时保留本地缓存身份（不需要重新登录）。
     */
    suspend fun bootstrap() {
        validateStoredSession()
    }

    private fun applyTokens(tokens: LogtoTokenSet, fallbackRefresh: String? = null, profile: UserProfile? = null) {
        accessToken = tokens.access_token
        refreshToken = tokens.refresh_token ?: fallbackRefresh
        idToken = tokens.id_token ?: idToken.takeIf { profile == null }
        expiresAt = System.currentTimeMillis() + tokens.expires_in.coerceAtLeast(60) * 1000L
        val editor = prefs.edit()
            .putString(KEY_ACCESS, TokenCipher.encrypt(accessToken))
            .putString(KEY_REFRESH, TokenCipher.encrypt(refreshToken))
            .putString(KEY_ID, TokenCipher.encrypt(idToken))
            .putLong(KEY_EXPIRES, expiresAt)
        if (profile != null) {
            viewRole = null
            editor.remove(KEY_VIEW_ROLE).remove(KEY_PROFILE).remove(KEY_ENCRYPTED_PROFILE).putCachedProfile(profile)
        }
        editor.apply()
    }

    /** PKCE 换取 token 后建立登录态，并拉取 Logto + Saturday 身份。 */
    suspend fun loginWithTokens(tokens: LogtoTokenSet): Result<UserProfile> = withContext(Dispatchers.IO) {
        val attempt = synchronized(sessionLock) {
            _state.value = _state.value.copy(isAuthenticating = true)
            ++loginAttempt
        }
        try {
            val profile = fetchProfile(tokens.access_token, tokens.id_token)
            ensureActive()
            synchronized(sessionLock) {
                if (loginAttempt != attempt) throw CancellationException("登录已取消")
                applyTokens(tokens, profile = profile)
                _state.value = AuthState(profile = profile, isLoggedIn = true)
            }
            Result.success(profile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            synchronized(sessionLock) {
                if (loginAttempt == attempt) _state.value = _state.value.copy(isAuthenticating = false)
            }
        }
    }

    private fun SharedPreferences.Editor.putCachedProfile(profile: UserProfile): SharedPreferences.Editor {
        val encoded = json.encodeToString(UserProfile.serializer(), profile)
        val encrypted = TokenCipher.encrypt(encoded) ?: return this
        return putString(KEY_ENCRYPTED_PROFILE, encrypted).remove(KEY_PROFILE)
    }

    private fun fetchProfile(token: String, idTokenValue: String?, requireFresh: Boolean = false): UserProfile {
        val info = tryGetJson("${LogtoAuth.ENDPOINT}/oidc/me", token)
        val claims = LogtoAuth.parseJwtClaims(idTokenValue)
        val member = tryGetJson("${SATURDAY_ENDPOINT}/member", token, allowMissingMember = true)
        // 启动校验只用完整的联网结果替换已有资料，避免离线或单个服务故障清空字段。
        check(!requireFresh || (info != null && member != null)) { "未能更新完整账号资料" }

        val merged = info ?: claims ?: member
            ?: throw IllegalStateException("无法验证登录身份，请检查网络后重试")

        val roles = LinkedHashSet<String>()
        info?.get("roles")?.let { raw -> splitRoles(raw).forEach { roles.add(it) } }
        claims?.get("roles")?.let { raw -> splitRoles(raw).forEach { roles.add(it) } }
        when (member?.get("role")?.lowercase()) {
            "admin" -> {
                roles.add("Repair Admin")
                roles.add("Repair Member")
            }
            "member" -> roles.add("Repair Member")
        }

        val sub = merged["sub"]?.takeIf { it.isNotBlank() }
            ?: member?.get("memberId")
            ?: ""
        if (sub.isBlank()) throw IllegalStateException("身份服务未返回用户标识")

        return UserProfile(
            sub = sub,
            name = merged["name"] ?: merged["username"] ?: member?.get("alias") ?: member?.get("name") ?: sub,
            email = merged["email"] ?: "",
            username = merged["username"] ?: merged["preferred_username"] ?: member?.get("alias"),
            avatar = merged["picture"] ?: merged["avatar"] ?: member?.get("avatar"),
            roles = roles.toList(),
            memberId = member?.get("memberId"),
            saturdayRole = member?.get("role")
        )
    }

    private fun splitRoles(raw: String): List<String> =
        raw.split("|", ",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun tryGetJson(url: String, token: String, allowMissingMember: Boolean = false): Map<String, String>? {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { res ->
                if (allowMissingMember && res.code in setOf(403, 404)) return emptyMap()
                if (!res.isSuccessful) return null
                parseFlatObject(res.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFlatObject(raw: String): Map<String, String>? {
        return try {
            val obj: JsonObject = json.parseToJsonElement(raw).jsonObject
            obj.mapValues { (_, value) ->
                when (value) {
                    is JsonArray -> runCatching {
                        value.jsonArray.joinToString("|") { it.jsonPrimitive.content }
                    }.getOrElse { "" }
                    is JsonObject -> ""
                    else -> value.jsonPrimitive.contentOrNull ?: ""
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 只切换界面视角，不改账号与 token。 */
    fun setViewRole(role: String?) = synchronized(sessionLock) {
        val current = _state.value
        val profile = current.profile
        if (!current.isLoggedIn || profile == null) {
            viewRole = null
            _state.value = current.copy(viewRole = null)
            return
        }
        val normalized = role?.trim()?.lowercase()
        if (normalized != null && normalized !in setOf("admin", "member", "client")) return
        if (normalized == "admin" && !profile.isRepairAdmin) return
        if (normalized == "member" && !profile.isRepairStaff) return
        viewRole = normalized
        prefs.edit().putString(KEY_VIEW_ROLE, normalized).apply()
        _state.value = current.copy(viewRole = normalized)
    }

    /** 手动退出：清除本机保存的账号信息、令牌与头像缓存。 */
    fun logout() {
        synchronized(sessionLock) {
            loginAttempt++
            clearLocal()
            _state.value = AuthState()
        }
        space.nbtca.app.core.card.CardImages.clear()
        runCatching {
            coil.Coil.imageLoader(appContext).apply {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
    }

    private fun clearLocal() {
        accessToken = null
        refreshToken = null
        idToken = null
        expiresAt = 0L
        viewRole = null
        prefs.edit().clear().apply()
    }

    /** 启动时静默校验；网络失败保留缓存身份，不强制退出。 */
    suspend fun validateStoredSession() {
        val token = getValidAccessToken() ?: return
        if (token.startsWith("dev_") || token.startsWith("mock_")) return
        val (storedIdToken, cached) = synchronized(sessionLock) {
            if (accessToken != token) return
            idToken to _state.value.profile
        }
        try {
            val profile = withContext(Dispatchers.IO) { fetchProfile(token, storedIdToken, requireFresh = cached != null) }
            synchronized(sessionLock) {
                if (accessToken != token || (cached != null && profile.sub != cached.sub)) return
                prefs.edit().putCachedProfile(profile).apply()
                _state.value = _state.value.copy(profile = profile, isLoggedIn = true)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 离线保留缓存身份。
        }
    }

    companion object {
        const val SATURDAY_ENDPOINT = "https://api.nbtca.space/v2"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ID = "id_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_PROFILE = "user_profile"
        private const val KEY_ENCRYPTED_PROFILE = "user_profile_encrypted"
        private const val KEY_VIEW_ROLE = "view_role"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
    }
}
