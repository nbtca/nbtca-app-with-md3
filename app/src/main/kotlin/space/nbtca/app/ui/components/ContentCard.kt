package space.nbtca.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    margin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.fillMaxWidth().padding(margin)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick == null) {
        Card(modifier = cardModifier, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(onClick = onClick, modifier = cardModifier, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}
