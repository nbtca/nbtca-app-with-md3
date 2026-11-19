package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import space.nbtca.app.core.card.MemberCardData
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import space.nbtca.app.core.card.CardAuthorizationException
import space.nbtca.app.core.card.CardRepository
import space.nbtca.app.core.card.CardLoginRequired
import space.nbtca.app.ui.components.ContentCard
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardScreen() {
    var path by remember { mutableStateOf("/me") }
    var doc by remember { mutableStateOf<Document?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Exception?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var forceRefresh by remember { mutableStateOf(false) }
    var usingCachedCopy by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember(context) { CardRepository.getInstance(context) }
    val imageLoader = rememberCardImageLoader()
    val config = LocalConfiguration.current
    val cardWidth = minOf(config.screenWidthDp - 64f, (config.screenHeightDp - 300f) / 1.4f).coerceAtLeast(180f).dp
    val scope = rememberCoroutineScope()
    fun navigate(next: String) {
        if (submitting) return
        path = next
        forceRefresh = false
        usingCachedCopy = false
        doc = null
        error = null
        notice = null
    }
    fun refreshPage() {
        forceRefresh = true
        refresh++
    }
    fun showLogin() {
        login = true
        doc = null
        error = null
        notice = null
        usingCachedCopy = false
    }
    if (login) {
        CardLoginScreen(onClose = { login = false }, onLoggedIn = { login = false; refreshPage() })
        return
    }
    LaunchedEffect(path, refresh) {
        val bypassCache = forceRefresh
        forceRefresh = false
        loading = true
        error = null
        if (path.startsWith("/e/")) doc = null
        try {
            val page = repository.load(path, forceRefresh = bypassCache) { cached ->
                if (doc == null) doc = cached
            }
            doc = page.document
            usingCachedCopy = page.usingCachedCopy
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            doc = null
            usingCachedCopy = false
            error = failure
        } finally {
            loading = false
        }
    }
    // A displayed pass is re-read so redeemed/expired codes are not kept on screen.
    LaunchedEffect(path, doc) {
        if (path.startsWith("/e/") && doc?.selectFirst("[data-ticket]") != null) {
            delay(15000)
            refresh++
        }
    }
    val detail = path.startsWith("/e/") || path.startsWith("/members/")
    val sections = listOf("/me" to "我的卡片", "/e" to "活动券", "/members" to "社员墙")
    val activeSection = sections.indexOfFirst { (route, _) -> path == route || path.startsWith("$route/") }
    fun navigateUp() = navigate(if (path.startsWith("/members/")) "/members" else "/e")
    BackHandler(detail, onBack = ::navigateUp)
    fun post(fields: Map<String,String>) {
        if (submitting || loading) return
        submitting = true
        scope.launch {
            try {
                doc = repository.claim(path, fields)
                notice = "已更新活动券状态"
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is CardAuthorizationException) doc = null
                error = failure
            }
            finally { submitting = false }
        }
    }
    if (editing && doc?.selectFirst(".card.full") != null) {
        CardEditScreen(doc!!, imageLoader, repository, onBack = { editing = false }, onSaved = {
            doc = it
            usingCachedCopy = false
            editing = false
            notice = "卡片已保存"
        })
        return
    }
    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = { Text("Card", style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    if (detail) {
                        IconButton(onClick = ::navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = ::refreshPage, enabled = !loading && !submitting) {
                        Icon(Icons.Default.Refresh, "刷新 Card")
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !submitting) { Icon(Icons.Default.MoreVert, "Card 菜单") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("重新授权") },
                                onClick = {
                                    menu = false
                                    showLogin()
                                }
                            )
                        }
                    }
                }
            )
            if (!detail && activeSection >= 0) {
                SecondaryTabRow(selectedTabIndex = activeSection) {
                    sections.forEachIndexed { index, (route, label) ->
                        Tab(
                            selected = activeSection == index,
                            onClick = { if (path != route) navigate(route) },
                            enabled = !submitting,
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (loading && doc == null) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            error?.let { failure ->
                item {
                    val authorizationRequired = failure is CardLoginRequired
                    ContentCard {
                        Text(
                            if (authorizationRequired) "需要 Card 授权" else "无法载入 Card",
                            style = MaterialTheme.typography.titleMediumEmphasized
                        )
                        Text(
                            if (authorizationRequired) "登录后可查看社员卡、活动券和社员墙。"
                            else failure.message ?: "请稍后重试。",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::showLogin) { Text("登录 Card") }
                            TextButton(onClick = ::refreshPage) { Text("重试") }
                        }
                    }
                }
            }
            if (usingCachedCopy) item {
                Text("暂时无法更新，正在显示上次加载的内容。", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = AppThemeColors.textSecondary)
            }
            notice?.let { item { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.primary) } }
            val current = doc
            if (current != null) {
                if (path == "/e") {
                    val entries = current.select(".caption")
                    if (entries.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            TicketVisual(null, Modifier.width(cardWidth), preview = true)
                            Text("近期暂无活动", Modifier.padding(top = 20.dp), fontWeight = FontWeight.Bold)
                            Text("有新活动时，可在这里领取专属活动券", style = MaterialTheme.typography.bodySmall, color = AppThemeColors.textSecondary)
                        }
                    }
                    items(entries) { entry ->
                        val link = entry.selectFirst("a[href]")
                        ContentCard(onClick = { link?.attr("href")?.let(::navigate) }) {
                            entry.parent()?.selectFirst(".pass-card")?.let { TicketVisual(it, Modifier.fillMaxWidth(), compact = true) }
                            Text(entry.selectFirst(".chip")?.text().orEmpty(), color = MaterialTheme.colorScheme.primary)
                            Text(entry.selectFirst("h3")?.text().orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp))
                            Text(entry.selectFirst("p")?.wholeText().orEmpty(), color = AppThemeColors.textSecondary)
                        }
                    }
                } else if (path == "/members") {
                    val cards = current.select("a.card")
                    if (cards.isEmpty()) item { ContentCard { Text("暂无社员卡") } }
                    item { Text("${cards.size} 位社员", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = AppThemeColors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                    items(cards.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { card -> MemberCardVisual(MemberCardData.parse(card), imageLoader, Modifier.weight(1f), compact = true, onClick = { navigate(card.attr("href")) }) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else if (path == "/me" || path.startsWith("/members/")) {
                    current.selectFirst(".card.full")?.let { card -> item {
                        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            MemberCardVisual(MemberCardData.parse(card), imageLoader, Modifier.width(cardWidth))
                            Text("点击翻面 · 轻拖查看光泽", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodySmall, color = AppThemeColors.textSecondary)
                            if (path == "/me") {
                                Button(onClick = { editing = true }, enabled = !loading, modifier = Modifier.padding(top = 12.dp)) {
                                    Text("装扮我的卡片")
                                }
                            }
                        }
                    } }
                } else if (path.startsWith("/e/")) {
                    current.selectFirst(".pass-card.full")?.let { pass -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { TicketVisual(pass, Modifier.width(cardWidth)) }
                    } }
                    item { ContentCard {
                        Text(current.selectFirst(".info h1")?.text().orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(current.selectFirst(".info > .chip")?.text().orEmpty(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                        Text(current.selectFirst(".when")?.text().orEmpty())
                        current.selectFirst(".summary")?.text()?.let { Text(it, Modifier.padding(vertical = 12.dp)) }
                        current.select(".timeline li").forEach { Text(it.text(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                    } }
                    item { ContentCard {
                        Text("我的活动券", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(current.selectFirst(".who .name")?.text().orEmpty())
                        Text(current.selectFirst(".act .notice")?.text().orEmpty(), modifier = Modifier.padding(vertical = 8.dp))
                        if (current.selectFirst("form.claim") != null) {
                            val parsed = remember(current) { runCatching { CardRepository.claimFields(current) } }
                            val claimFields = parsed.getOrNull().orEmpty()
                            val values = remember(current) { mutableStateMapOf<String,String>().apply { claimFields.forEach { put(it.name,it.value) } } }
                            parsed.exceptionOrNull()?.message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                            claimFields.filterNot { it.hidden }.forEach { field ->
                                OutlinedTextField(values[field.name].orEmpty(), { values[field.name]=it.take(field.maxLength) },
                                    label={Text(field.label + if(field.required) " *" else "")},singleLine=true,
                                    modifier=Modifier.fillMaxWidth().padding(top=8.dp),enabled=!submitting)
                            }
                            Button(onClick = { post(values.toMap()) }, enabled = !submitting && !loading && parsed.isSuccess && claimFields.all { !it.required || !values[it.name].isNullOrBlank() }) { Text(if (submitting) "正在领取…" else "领取活动券") }
                        } else if (current.selectFirst(".act a[href^=/login]") != null) {
                            Button(onClick = ::showLogin) { Text("登录后领取") }
                        }
                    } }
                }
            }
        }
    }
}
