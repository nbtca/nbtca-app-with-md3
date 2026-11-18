package space.nbtca.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.nbtca.app.core.model.NewsArticle

@Composable
fun NewsroomCard(article: NewsArticle, isLargeFeatured: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = if (isLargeFeatured) MaterialTheme.shapes.extraLarge else CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isLargeFeatured) colors.primaryContainer else colors.surfaceContainerLow,
            contentColor = if (isLargeFeatured) colors.onPrimaryContainer else colors.onSurface
        )
    ) {
        if (isLargeFeatured) {
            CoverImage(
                url = article.coverUrl,
                category = article.category,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            ArticleCardText(article, featured = true, modifier = Modifier.padding(16.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArticleCardText(article, featured = false, modifier = Modifier.weight(1f))
                if (article.coverUrl.isNotBlank()) {
                    CoverImage(
                        url = article.coverUrl,
                        category = article.category,
                        modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.extraSmall),
                        labelSize = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArticleCardText(article: NewsArticle, featured: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val metadata = listOf(article.category, article.published).filter { it.isNotBlank() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (metadata.isNotEmpty()) {
            Text(
                metadata.joinToString(" · "),
                style = typography.labelMedium,
                color = if (featured) colors.onPrimaryContainer else colors.onSurfaceVariant
            )
        }
        Text(
            article.title,
            style = if (featured) typography.headlineSmallEmphasized else typography.titleMediumEmphasized
        )
        if (article.summary.isNotBlank()) {
            Text(
                article.summary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = typography.bodyMedium,
                color = if (featured) colors.onPrimaryContainer else colors.onSurfaceVariant
            )
        }
    }
}
