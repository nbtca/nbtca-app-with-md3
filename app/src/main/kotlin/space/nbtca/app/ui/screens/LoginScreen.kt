package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.auth.LogtoAuth
import space.nbtca.app.ui.theme.AppThemeColors
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 视口高度兜底脚本：仅当 100vh 解析为 0（WebView 缺陷）时才注入固定像素高度，
 * 正常环境探测到 vh > 0 会直接返回，不做任何修改。
 */
internal const val VIEWPORT_HEIGHT_FALLBACK = """
(function () {
  try {
    var probe = document.createElement('div');
    probe.style.cssText = 'position:absolute;visibility:hidden;height:100vh';
    document.body.appendChild(probe);
    var vh = probe.getBoundingClientRect().height;
    probe.parentNode.removeChild(probe);
    if (vh > 0) return 'ok';
    var h = window.innerHeight || document.documentElement.clientHeight;
    if (h <= 0) return 'skip';
    var style = document.getElementById('nbtca-viewport-fallback');
    if (!style) {
      style = document.createElement('style');
      style.id = 'nbtca-viewport-fallback';
      document.head.appendChild(style);
    }
    style.textContent =
      'html,body{height:' + h + 'px !important;min-height:' + h + 'px !important}' +
      'div[class*="viewBox"]{position:relative !important;height:' + h + 'px !important;min-height:' + h + 'px !important}' +
      '.logto_page-container,.logto_main-content{min-height:' + h + 'px !important}';
    return 'fixed';
  } catch (e) {
    return 'error';
  }
})()
"""

private data class BrowserAuthorization(val verifier: String, val state: String, val nonce: String)

private val browserAuthorizationSaver = Saver<BrowserAuthorization?, Bundle>(
    save = { request ->
        request?.let { bundleOf("verifier" to it.verifier, "state" to it.state, "nonce" to it.nonce) }
    },
    restore = { bundle ->
        val verifier = bundle.getString("verifier")
        val state = bundle.getString("state")
        val nonce = bundle.getString("nonce")
        if (verifier == null || state == null || nonce == null) null
        else BrowserAuthorization(verifier, state, nonce)
    }
)

private suspend fun isBrowserCallbackPublished(http: OkHttpClient): Boolean = withContext(Dispatchers.IO) {
    val callback = Uri.parse(LogtoAuth.REDIRECT_URI)
    val url = callback.buildUpon().path("/.well-known/assetlinks.json").clearQuery().build()
    val request = Request.Builder().url(url.toString()).build()
    http.newBuilder().followRedirects(false).build().newCall(request).execute().use { response ->
        val type = response.body?.contentType()
        response.isSuccessful && type?.type == "application" && type.subtype == "json"
    }
}

/**
 * Logto OAuth2 + PKCE 登录：WebView 拦截官网回调地址，取 code 本地换 token。
 * 与 Flutter logto_webview_page.dart 的流程一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verifier = remember { LogtoAuth.generateCodeVerifier() }
    val state = remember { LogtoAuth.generateState() }
    val authorizeUrl = remember { LogtoAuth.buildAuthorizeUrl(LogtoAuth.generateCodeChallenge(verifier), state) }
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var exchanging by remember { mutableStateOf(false) }
    var browserChecking by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf<String?>(null) }
    var browserAuthorization by rememberSaveable(stateSaver = browserAuthorizationSaver) {
        mutableStateOf<BrowserAuthorization?>(null)
    }
    // 记录 WebView 实例，退出时显式销毁：否则每次打开登录都会留下一个存活的 WebView 渲染进程。
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

    BackHandler { onClose() }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder[0]?.let { view ->
                view.stopLoading()
                view.destroy()
            }
            webViewHolder[0] = null
        }
    }

    fun handleCode(code: String, requestVerifier: String, nonce: String? = null) {
        if (exchanging) return
        exchanging = true
        scope.launch {
            val result = LogtoAuth.exchangeCode(http, code, requestVerifier)
            result.fold(
                onSuccess = { tokens ->
                    if (nonce != null && !LogtoAuth.matchesNonce(tokens.id_token, nonce)) {
                        exchanging = false
                        browserError = "登录响应校验失败，请重新登录。"
                        return@fold
                    }
                    val login = authManager.loginWithTokens(tokens)
                    exchanging = false
                    login.fold(
                        onSuccess = { onClose() },
                        onFailure = { error = it.message ?: "登录失败" }
                    )
                },
                onFailure = {
                    exchanging = false
                    error = "授权码换取 Token 失败: ${it.message}"
                }
            )
        }
    }

    val browserContract = remember { AuthTabIntent.AuthenticateUserResultContract() }
    val browserLauncher = rememberLauncherForActivityResult(browserContract) { result ->
        val request = browserAuthorization ?: return@rememberLauncherForActivityResult
        browserAuthorization = null
        when (result.resultCode) {
            AuthTabIntent.RESULT_OK -> {
                val callback = result.resultUri?.takeIf { LogtoAuth.isRedirect(it.toString()) }
                val code = callback?.getQueryParameter("code")
                if (callback != null &&
                    callback.getQueryParameter("state") == request.state && !code.isNullOrBlank()
                ) {
                    handleCode(code, request.verifier, request.nonce)
                } else {
                    browserError = "浏览器返回的登录结果校验失败，请重新登录。"
                }
            }
            AuthTabIntent.RESULT_CANCELED -> Unit
            AuthTabIntent.RESULT_VERIFICATION_FAILED,
            AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT ->
                browserError = "浏览器未能验证返回地址，请确认 nbtca.space 已关联当前 APK 的签名。"
            else -> browserError = "浏览器登录结束，请重试。"
        }
    }

    fun openBrowserLogin() {
        browserChecking = true
        scope.launch {
            try {
                if (!isBrowserCallbackPublished(http)) {
                    browserError = "浏览器登录需要 NBTCA 管理员先配置 nbtca.space 的 APK 签名关联。"
                    return@launch
                }
                val browser = CustomTabsClient.getPackageName(context, null)
                if (browser == null || !CustomTabsClient.isAuthTabSupported(context, browser)) {
                    browserError = "请将 Chrome 更新到支持身份验证标签页的版本，并设为默认浏览器后重试。"
                    return@launch
                }
                if (exchanging) return@launch

                val request = BrowserAuthorization(
                    LogtoAuth.generateCodeVerifier(), LogtoAuth.generateState(), LogtoAuth.generateState()
                )
                val url = LogtoAuth.buildAuthorizeUrl(
                    LogtoAuth.generateCodeChallenge(request.verifier), request.state, request.nonce
                )
                val callback = Uri.parse(LogtoAuth.REDIRECT_URI)
                val tab = AuthTabIntent.Builder().build()
                tab.intent.setPackage(browser)
                browserAuthorization = request
                tab.launch(browserLauncher, Uri.parse(url), requireNotNull(callback.host), requireNotNull(callback.path))
            } catch (_: IOException) {
                browserError = "暂时无法验证浏览器返回地址，请检查网络后重试。"
            } catch (_: ActivityNotFoundException) {
                browserAuthorization = null
                browserError = "无法打开系统浏览器，请检查默认浏览器设置。"
            } finally {
                browserChecking = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("登录 NBTCA 账号", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !exchanging && !browserChecking && browserAuthorization == null,
                            onClick = ::openBrowserLogin
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "用浏览器登录")
                        }
                        LoginHelpButton(browserSignInAvailable = true)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                if (isLoading || exchanging || browserChecking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewHolder[0] = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        enableAppPasskeys()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                // Logto 用 position:absolute;inset:0 撑满视口。个别 WebView
                                // （Android 17 预览镜像的 WebView 155）把 100vh/100dvh 解析成 0，
                                // 整页会塌成空白；探测到这种环境时用 innerHeight 兜底。
                                view?.evaluateJavascript(VIEWPORT_HEIGHT_FALLBACK, null)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                if (request?.isForMainFrame != true) return false
                                val target = request.url.toString()
                                if (LogtoAuth.isRedirect(target)) {
                                    if (browserAuthorization != null) return true
                                    val callback = Uri.parse(target)
                                    if (callback.getQueryParameter("state") != state) {
                                        error = "登录校验失败，请关闭后重试"
                                        return true
                                    }
                                    val code = callback.getQueryParameter("code")
                                    if (!code.isNullOrEmpty()) {
                                        handleCode(code, verifier)
                                    } else {
                                        error = "授权回调缺少 code 参数"
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(authorizeUrl)
                    }
                }
            )

            if (exchanging) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            error?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    browserError?.let { message ->
        AlertDialog(
            onDismissRequest = { browserError = null },
            title = { Text("浏览器登录") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { browserError = null }) { Text("返回登录") } }
        )
    }
}
