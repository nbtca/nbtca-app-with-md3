package space.nbtca.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.components.CoverImage
import space.nbtca.app.ui.components.MarkdownText
import space.nbtca.app.ui.components.OfflineBanner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleDetailScreen(
    initialArticle: NewsArticle,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    var article by remember(initialArticle.id) { mutableStateOf(initialArticle) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadArticle() {
        if (article.id.isEmpty() || isLoading) return
        isLoading = true
        error = null
        scope.launch {
            apiClient.getArticle(article.id).fold(
                onSuccess = { article = it },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    LaunchedEffect(initialArticle.id) { loadArticle() }
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article.category.ifBlank { "文章" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadArticle() }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新正文")
                    }
                    IconButton(
                        onClick = { openUrl(context, article.articleUrl) },
                        enabled = article.articleUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "在浏览器打开原文")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp)) }
            }
            if (article.coverUrl.isNotEmpty()) {
                item {
                    CoverImage(
                        url = article.coverUrl,
                        category = article.category,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large),
                        labelSize = 24
                    )
                }
            }
            item {
                // SelectionContainer：长按即可选中/复制正文与标题。
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            article.title,
                            modifier = Modifier.semantics { heading() },
                            style = typography.headlineMediumEmphasized
                        )
                        val metadata = listOf(article.published, article.author).filter { it.isNotBlank() }
                        if (metadata.isNotEmpty()) {
                            Text(
                                metadata.joinToString(" · "),
                                style = typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        if (article.tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                article.tags.forEach { tag ->
                                    Surface(
                                        color = colors.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "#$tag",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                        error?.let { OfflineBanner(message = it) }
                        HorizontalDivider()
                        val body = article.contentMarkdown
                            ?.removePrefix("${article.category}${article.published}\n\n")
                            ?: article.summary
                        if (body.isNotBlank()) MarkdownText(body, skipLeadingTitle = article.title)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
