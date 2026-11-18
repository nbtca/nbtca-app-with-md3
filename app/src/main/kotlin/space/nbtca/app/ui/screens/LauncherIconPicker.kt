package space.nbtca.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import space.nbtca.app.core.config.LauncherIcon

@Composable
internal fun LauncherIconPicker() {
    val context = LocalContext.current
    val imageSize = with(LocalDensity.current) { 48.dp.roundToPx() }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    var selected by remember(context) { mutableStateOf(LauncherIcon.current(context)) }
    var switchFailed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LauncherIcon.entries.forEach { icon ->
                val checked = icon == selected
                val preview = remember(context, icon, imageSize) {
                    requireNotNull(ContextCompat.getDrawable(context, icon.drawable))
                        .mutate().toBitmap(imageSize, imageSize).asImageBitmap()
                }
                Surface(
                    modifier = Modifier.weight(1f).clip(shape).selectable(
                        selected = checked,
                        role = Role.RadioButton,
                        onClick = {
                            if (!checked) {
                                try {
                                    icon.select(context)
                                    selected = icon
                                    switchFailed = false
                                } catch (_: RuntimeException) {
                                    switchFailed = true
                                }
                            }
                        }
                    ),
                    shape = shape,
                    color = if (checked) colors.primaryContainer else colors.surfaceContainerLow,
                    contentColor = if (checked) colors.onPrimaryContainer else colors.onSurface,
                    border = if (checked) BorderStroke(1.dp, colors.primary) else null
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(bitmap = preview, contentDescription = null, modifier = Modifier.size(48.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(icon.label, style = MaterialTheme.typography.titleSmall)
                            if (checked) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        if (switchFailed) {
            Text("桌面图标切换失败，请重试。", color = colors.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
