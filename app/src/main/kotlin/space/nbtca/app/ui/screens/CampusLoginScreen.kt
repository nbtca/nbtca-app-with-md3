package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.core.academic.AcademicWebSync
import space.nbtca.app.ui.theme.AppColors

private enum class LoginStep { Portal, SingleSignOn, Menu, Timetable }

/**
 * 教务网页登录：只负责让用户完成 CAS 登录（含验证码）并保存会话，
 * 登录成功后立即返回，不自动拉取数据——是否同步由用户在课表页手动点「同步」。
 *
 * 拦截主框架加载错误，显示重试入口。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusLoginScreen(
    store: AcademicStore,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var pageVersion by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf(LoginStep.Portal) }
    var needsAuthInput by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val showLoginProgress = loadError == null && !needsAuthInput

    fun checkPage(view: WebView, script: String, onResult: (Boolean) -> Unit) {
        val version = pageVersion
        val url = view.url
        view.evaluateJavascript(script) { result ->
            if (view === webView && version == pageVersion && view.url == url && !isLoading && loadError == null) {
                onResult(result == "true")
            }
        }
    }

    fun goTo(view: WebView, next: LoginStep, url: String) {
        pageVersion++
        step = next
        needsAuthInput = false
        isLoading = true
        view.loadUrl(url)
    }

    fun onLoginPageReady(view: WebView, url: String) {
        val uri = Uri.parse(url)
        val origin = "${uri.scheme}://${uri.encodedAuthority}"
        if (origin == AcademicWebSync.PORTAL_URL.removeSuffix("/")) {
            checkPage(view, AcademicWebSync.PORTAL_READY_SCRIPT) { ready ->
                if (ready) {
                    if (step == LoginStep.Portal) {
                        goTo(view, LoginStep.SingleSignOn, AcademicWebSync.JWXT_SSO_URL)
                    } else {
                        loadError = "教务验证返回了学校门户，请重试"
                    }
                } else if (step == LoginStep.Portal || uri.path in listOf("/login", "/users/sign_in")) {
                    needsAuthInput = true
                } else {
                    loadError = "教务验证跳回了学校门户，请重试"
                }
            }
            return
        }
        if (origin == AcademicWebSync.AUTH_ORIGIN || origin == "https://authserver.nbt.edu.cn") {
            needsAuthInput = true
            return
        }
        if (origin != AcademicWebSync.JWXT_ORIGIN || step == LoginStep.Portal) {
            loadError = "教务验证跳转到未知页面，请重试"
            return
        }
        when (step) {
            LoginStep.SingleSignOn -> {
                if (uri.encodedPath == AcademicWebSync.JWXT_MENU_PATH) {
                    goTo(view, LoginStep.Timetable, AcademicWebSync.TIMETABLE_URL)
                } else {
                    goTo(view, LoginStep.Menu, AcademicWebSync.JWXT_MENU_URL)
                }
            }
            LoginStep.Menu -> goTo(view, LoginStep.Timetable, AcademicWebSync.TIMETABLE_URL)
            LoginStep.Timetable -> if (uri.encodedPath != AcademicWebSync.TIMETABLE_PATH) {
                loadError = "教务验证未进入课表页，请重试"
            }
            LoginStep.Portal -> Unit
        }
    }

    fun retry() {
        loadError = null
        isLoading = true
        needsAuthInput = false
        step = LoginStep.Portal
        reloadToken++
    }

    LaunchedEffect(reloadToken, showLoginProgress) {
        if (!showLoginProgress) return@LaunchedEffect
        delay(30_000)
        if (loadError == null && !needsAuthInput) loadError = "连接教务系统超时，请重试"
    }

    LaunchedEffect(webView, pageVersion, step, isLoading, showLoginProgress) {
        val view = webView ?: return@LaunchedEffect
        val url = view.url ?: return@LaunchedEffect
        if (step != LoginStep.Timetable || isLoading || !showLoginProgress ||
            Uri.parse(url).encodedPath != AcademicWebSync.TIMETABLE_PATH
        ) return@LaunchedEffect

        val version = pageVersion
        val result = withTimeoutOrNull(12_000) {
            var studentId = evaluateJsValue(view, AcademicWebSync.TIMETABLE_IDENTITY_SCRIPT)
            while (studentId == null || studentId == "null") {
                delay(300)
                studentId = evaluateJsValue(view, AcademicWebSync.TIMETABLE_IDENTITY_SCRIPT)
            }
            studentId
        }
        if (view !== webView || version != pageVersion || view.url != url || loadError != null) return@LaunchedEffect
        val studentId = result?.let(AcademicWebSync::decodeStudentId).orEmpty()
        val cookies = AcademicWebSync.sessionCookies()
        if (studentId.isEmpty() || cookies == null) {
            loadError = "未能确认当前教务学号，请重试"
            return@LaunchedEffect
        }
        store.saveWebSession(studentId, cookies)
        CookieManager.getInstance().flush()
        onLoggedIn()
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("教务网页登录", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val error = loadError
            if (error == null) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(reloadToken) {
                        val attempt = reloadToken
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            if (view !== webView || attempt != reloadToken || loadError != null) return
                                            pageVersion++
                                            needsAuthInput = false
                                            isLoading = true
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            errorDescription: WebResourceError?
                                        ) {
                                            // 只处理主框架错误，忽略图片/脚本等子资源失败
                                            if (view === webView && attempt == reloadToken && request?.isForMainFrame == true) {
                                                isLoading = false
                                                loadError = errorDescription?.description?.toString()
                                                    ?: "无法连接教务服务器"
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            if (view == null || url == null || view !== webView || attempt != reloadToken ||
                                                loadError != null || view.url != url
                                            ) return
                                            isLoading = false
                                            onLoginPageReady(view, url)
                                        }
                                    }
                                    loadUrl(AcademicWebSync.PORTAL_URL)
                                }
                            },
                            update = { view -> view.visibility = if (showLoginProgress) View.INVISIBLE else View.VISIBLE },
                            onRelease = { view ->
                                if (webView === view) webView = null
                                view.stopLoading()
                                view.destroy()
                            }
                        )
                    }
                    if (showLoginProgress) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LoadingIndicator()
                            Text(
                                text = if (step == LoginStep.Portal) "正在打开教务网页登录…" else "登录中…",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = AppColors.StatusAlert,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (step == LoginStep.Portal) "无法连接教务门户" else "未能确认教务登录",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "错误详情：$error",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { retry() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重试连接")
                            }
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("稍后再说")
                            }
                        }
                    }
                }
            }

        }
    }
}
