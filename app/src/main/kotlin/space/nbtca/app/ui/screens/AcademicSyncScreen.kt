package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import space.nbtca.app.core.academic.AcademicException
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.core.academic.AcademicTerm
import space.nbtca.app.core.academic.AcademicWebSync
import space.nbtca.app.core.academic.TermCatalog
import space.nbtca.app.ui.theme.AppThemeColors
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 教务同步：登录与取数在同一个 WebView 内完成。
 * webvpn 会话绑在 WebView 上，另开隐藏 WebView 会丢会话，所以必须复用这一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcademicSyncScreen(
    store: AcademicStore,
    onBack: () -> Unit,
    onSynced: (String?) -> Unit
) {
    var status by remember { mutableStateOf("正在连接 webvpn…") }
    var stage by remember { mutableStateOf(Stage.LOGIN) }
    var loading by remember { mutableStateOf(true) }
    var catalog by remember { mutableStateOf<TermCatalog?>(null) }
    var selectedYear by remember { mutableStateOf("") }
    var selectedTerms by remember { mutableStateOf(emptySet<AcademicTerm>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ssoRetries by remember { mutableStateOf(0) }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("教务同步", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = AppThemeColors.textSecondary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                val current = url.orEmpty()
                                android.util.Log.i("AcademicSync", "page[$stage]: ${android.net.Uri.parse(current).host}${android.net.Uri.parse(current).path}")
                                if (current.contains("authserver")) {
                                    // SSO 也可能要求再次登录，撤掉遮罩以便交互验证。
                                    stage = Stage.LOGIN
                                    error = null
                                    status = "请在此登录（含验证码），登录后会自动继续"
                                    return
                                }
                                when (stage) {
                                    Stage.LOGIN -> {
                                        if (android.net.Uri.parse(current).host == "jwxt-443.webvpn.nbt.edu.cn") {
                                            stage = Stage.TERMS
                                            status = "正在读取学期列表…"
                                            view?.evaluateJavascript(AcademicWebSync.TERMS_SCRIPT, null)
                                            return
                                        }
                                        if (!current.contains("webvpn.nbt.edu.cn")) return
                                        // 用页面内容判断是否真的登录成功：门户有「常用站点」，
                                        // 且不再有登录表单。只看 URL 会被 webvpn 的 302 骗到。
                                        view?.evaluateJavascript(
                                            "(function(){var t=document.body?document.body.innerText:'';" +
                                                "return (t.indexOf('常用站点')>=0 && !document.getElementById('pwdEncryptSalt'))?'yes':'no';})()"
                                        ) { value ->
                                            if (value != null && value.contains("yes")) {
                                                stage = Stage.SSO
                                                status = "已登录，正在进入教务系统…"
                                                // 等 webvpn 门户把会话脚本跑完再跳 SSO，否则 jwxt 仍是未登录
                                                view.postDelayed({ view.loadUrl(SSO_URL) }, 1500)
                                            } else if (value != null) {
                                                status = "请在此登录（含验证码），登录后会自动继续"
                                            }
                                        }
                                    }
                                    Stage.SSO -> {
                                        if (!current.contains("jwxt-443.webvpn.nbt.edu.cn")) return
                                        stage = Stage.TERMS
                                        status = "正在读取学期列表…"
                                        // 不再跳转课表页，直接在当前教务页面里用 fetch 读学期，
                                        // 少一次跳转就少一次被弹回登录页的机会。
                                        view?.evaluateJavascript(AcademicWebSync.TERMS_SCRIPT, null)
                                    }
                                    Stage.TERMS -> {
                                        if (!current.contains("jwxt-443.webvpn.nbt.edu.cn")) return
                                        view?.evaluateJavascript(AcademicWebSync.TERMS_SCRIPT, null)
                                    }
                                    else -> Unit
                                }
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    error = "连接教务系统失败，请检查网络后重试（${failure?.errorCode}）"
                                    status = error.orEmpty()
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                        }
                        webView = this
                        loadUrl("https://webvpn.nbt.edu.cn/")
                    }
                },
                onRelease = { view ->
                    if (webView === view) webView = null
                    view.stopLoading()
                    view.destroy()
                }
            )

            if (stage != Stage.LOGIN) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error == null && stage != Stage.PICK_TERM) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = error ?: status,
                            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                            fontSize = 13.sp,
                            color = if (error != null) MaterialTheme.colorScheme.error else AppThemeColors.textSecondary
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(stage) {
        val view = webView
        when (stage) {
            Stage.SSO -> {
                delay(30000)
                loading = false
                error = "进入教务系统超时，请关闭后重试"
            }
            Stage.TERMS -> {
                if (view == null) return@LaunchedEffect
                repeat(40) { attempt ->
                    delay(400)
                    val probe = evaluateJsValue(
                        view,
                        "(location.href+'|xnm='+!!document.getElementById('xnm')+'|terms='+(window.__nbtTerms?1:0))"
                    )
                    if (attempt < 3) android.util.Log.d("AcademicSync", "probe: $probe")
                    val raw = evaluateJsValue(
                        view,
                        "(window.__nbtTerms || (function(){try{return localStorage.getItem('nbt_terms')}catch(e){return null}})())"
                    )
                    if (attempt == 0) android.util.Log.d("AcademicSync", "terms raw: ${raw?.take(200)}")
                    if (!raw.isNullOrBlank() && raw != "null") {
                        runCatching { AcademicWebSync.parseTerms(raw) }
                            .onSuccess { parsed ->
                                catalog = parsed
                                selectedYear = parsed.selectedYear
                                if (selectedTerms.isEmpty()) {
                                    val year = parsed.years.first { it.value == selectedYear }
                                    val semester = parsed.semesters.first { it.value == parsed.selectedSemester }
                                    selectedTerms = setOf(AcademicTerm(year.value, semester.value, year.label, semester.label))
                                }
                                status = "请选择要同步的学期"
                                stage = Stage.PICK_TERM
                            }
                            .onFailure {
                                if (ssoRetries < 1) {
                                    ssoRetries += 1
                                    status = "重新尝试进入教务系统…"
                                    stage = Stage.SSO
                                    webView?.loadUrl(SSO_URL)
                                } else {
                                    error = "读取学期失败：${it.message}"
                                }
                            }
                        return@LaunchedEffect
                    }
                }
                if (error == null) error = "读取学期超时，请重试"
            }
            Stage.FETCHING -> {
                if (view == null) return@LaunchedEffect
                val batch = selectedTerms.toList()
                val failures = mutableListOf<Pair<String, String>>()
                var batchStudentId: String? = null
                for ((index, term) in batch.withIndex()) {
                    status = "正在同步 ${index + 1}/${batch.size} · ${term.label}"
                    try {
                        val raw = fetchTerm(view, term)
                        val snapshot = withContext(Dispatchers.IO) { AcademicWebSync.parse(raw, term) }
                        if (batchStudentId != null && snapshot.studentId != batchStudentId) {
                            throw AcademicException("教务账号在同步期间变更，请重新同步")
                        }
                        val cookies = AcademicWebSync.sessionCookies()
                            ?: throw AcademicException("教务会话已失效，请重新登录")
                        store.saveSnapshot(snapshot, cookies)
                        batchStudentId = snapshot.studentId
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failures += term.label to (failure.message ?: "无法读取教务数据")
                    }
                }
                loading = false
                if (failures.size == batch.size) {
                    error = "同步失败：${failures.firstOrNull()?.second ?: "未选择学期"}"
                } else {
                    val failedLabels = failures.joinToString("、") { it.first }
                    onSynced(failedLabels.takeIf { it.isNotEmpty() }?.let { "以下学期同步失败：$it" })
                }
            }
            else -> Unit
        }
    }

    catalog?.let { current ->
        if (stage == Stage.PICK_TERM) {
            AlertDialog(
                onDismissRequest = { onBack() },
                title = { Text("选择同步学期") },
                text = {
                    // 学年可能十几个，横排滚动；学期只有 1/2/3，单独一行，避免被挤出对话框
                    Column(
                        modifier = Modifier.verticalScroll(
                            androidx.compose.foundation.rememberScrollState()
                        )
                    ) {
                        Text("学年", fontSize = 12.sp, color = AppThemeColors.textSecondary)
                        val yearListState = androidx.compose.foundation.lazy.rememberLazyListState(
                            initialFirstVisibleItemIndex = current.years
                                .indexOfFirst { it.value == selectedYear }
                                .coerceAtLeast(0)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            state = yearListState,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(current.years.size) { index ->
                                val option = current.years[index]
                                FilterChip(
                                    selected = option.value == selectedYear,
                                    onClick = { selectedYear = option.value },
                                    label = { Text(option.label, fontSize = 12.sp) }
                                )
                            }
                        }
                        Text(
                            "学期",
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 12.sp,
                            color = AppThemeColors.textSecondary
                        )
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val year = current.years.first { it.value == selectedYear }
                            current.semesters.forEach { option ->
                                val term = AcademicTerm(year.value, option.value, year.label, option.label)
                                FilterChip(
                                    selected = term in selectedTerms,
                                    onClick = {
                                        selectedTerms = if (term in selectedTerms) selectedTerms - term else selectedTerms + term
                                    },
                                    label = { Text(option.label, fontSize = 12.sp) }
                                )
                            }
                        }
                        if (selectedTerms.isNotEmpty()) {
                            Text(
                                text = selectedTerms.joinToString("\n") { it.label },
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedTerms.isNotEmpty(),
                        onClick = {
                            error = null
                            loading = true
                            stage = Stage.FETCHING
                        }
                    ) { Text("同步 ${selectedTerms.size} 个学期") }
                },
                dismissButton = { TextButton(onClick = onBack) { Text("取消") } }
            )
        }
    }
}

private enum class Stage { LOGIN, SSO, TERMS, PICK_TERM, FETCHING }

private const val SSO_URL = "https://jwxt-443.webvpn.nbt.edu.cn/sso/jziotlogin"

private suspend fun fetchTerm(view: WebView, term: AcademicTerm): String {
    val requestId = UUID.randomUUID().toString()
    return withTimeoutOrNull(48_000L) {
        evaluateJsValue(view, AcademicWebSync.dataScript(term, requestId))
        var result: String? = null
        while (result == null) {
            delay(400)
            result = evaluateJsValue(
                view,
                "window.__nbtSync?.requestId === '$requestId' ? window.__nbtSync.result : null"
            )?.takeUnless { it.isBlank() || it == "null" }
        }
        result
    } ?: throw AcademicException("读取学期数据超时")
}

internal suspend fun evaluateJsValue(view: WebView, script: String): String? =
    suspendCancellableCoroutine { continuation ->
        try {
            view.evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
