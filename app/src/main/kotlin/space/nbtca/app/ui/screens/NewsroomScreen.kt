package space.nbtca.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.model.NewsFeedResult
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.components.NewsroomCard
import space.nbtca.app.ui.components.OfflineBanner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewsroomScreen(apiClient: ApiClient) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var feed by remember { mutableStateOf<NewsFeedResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("全部") }
    var openedArticle by remember { mutableStateOf<NewsArticle?>(null) }

    suspend fun load(force: Boolean) {
        if (refreshing) return
        refreshing = true
        try {
            feed = apiClient.getNewsFeed(force)
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(apiClient) { load(force = false) }

    val opened = openedArticle
    if (opened != null) {
        ArticleDetailScreen(opened, apiClient, onBack = { openedArticle = null })
        return
    }

    val snapshot = feed
    val categories = remember(snapshot) {
        (listOf("全部") + snapshot?.categories.orEmpty() +
            (snapshot?.featured.orEmpty() + snapshot?.recent.orEmpty()).map { it.category })
            .filter { it.isNotBlank() }
            .distinct()
    }
    val category = selectedCategory.takeIf { it in categories } ?: "全部"
    val featured = snapshot?.featured.orEmpty().matchingCategory(category).distinctBy { it.id }
    val recent = snapshot?.recent.orEmpty().matchingCategory(category)
        .filterNot { article -> featured.any { it.id == article.id } }
        .distinctBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拔电关机") },
                subtitle = { Text("To be at the intersection of technology and liberal arts.") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        if (snapshot == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { load(force = true) } },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "categories") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories, key = { it }) { label ->
                                val selected = category == label
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCategory = label
                                        listState.requestScrollToItem(0)
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                    if (snapshot.isOffline) {
                        item { OfflineBanner("未能连接资讯服务，正在显示离线内容。") }
                    }
                    if (featured.isEmpty() && recent.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Article,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "暂无「$category」分类的文章",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (featured.isNotEmpty()) {
                        item { NewsSectionTitle("精选") }
                        items(featured, key = { "featured:" + it.id }) { article ->
                            NewsroomCard(article, isLargeFeatured = article.id == featured.first().id) {
                                openedArticle = article
                            }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item { NewsSectionTitle("最新文章") }
                        items(recent, key = { "recent:" + it.id }) { article ->
                            NewsroomCard(article, isLargeFeatured = false) { openedArticle = article }
                        }
                    }
                }
            }
        }
    }
}

private fun List<NewsArticle>.matchingCategory(category: String): List<NewsArticle> =
    if (category == "全部") this else filter { it.category == category || category in it.tags }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewsSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized)
}
