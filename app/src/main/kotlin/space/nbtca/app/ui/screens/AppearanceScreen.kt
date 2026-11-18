package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import space.nbtca.app.core.config.AppearanceSettings
import space.nbtca.app.core.config.ThemeMode
import space.nbtca.app.core.config.ThemePalette
import space.nbtca.app.ui.theme.LocalIsDark
import space.nbtca.app.ui.theme.colorScheme
import space.nbtca.app.ui.theme.supportsWallpaperColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit
) {
    val isDark = LocalIsDark.current
    val dynamicColor = supportsWallpaperColors && appearance.dynamicColor
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AppearancePreview(
                    title = if (dynamicColor) "壁纸配色" else appearance.palette.label,
                    isDark = isDark
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceHeading("显示模式")
                    ModePicker(appearance.mode) { onAppearanceChange(appearance.copy(mode = it)) }
                }
            }
            if (supportsWallpaperColors) {
                item {
                    val colors = MaterialTheme.colorScheme
                    ListItem(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).toggleable(
                            value = dynamicColor,
                            role = Role.Switch,
                            onValueChange = { onAppearanceChange(appearance.copy(dynamicColor = it)) }
                        ),
                        headlineContent = { Text("跟随壁纸配色") },
                        supportingContent = { Text("使用系统动态颜色") },
                        leadingContent = { Icon(Icons.Outlined.Wallpaper, contentDescription = null) },
                        trailingContent = { Switch(checked = dynamicColor, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainerLow)
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceHeading("主题配色")
                    PalettePicker(
                        selected = appearance.palette.takeUnless { dynamicColor },
                        isDark = isDark,
                        onSelect = { onAppearanceChange(appearance.copy(palette = it, dynamicColor = false)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearancePreview(title: String, isDark: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isDark) "深色预览" else "浅色预览", style = MaterialTheme.typography.labelLarge)
                Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
            }
            Surface(
                modifier = Modifier.width(116.dp),
                shape = MaterialTheme.shapes.medium,
                color = colors.surface,
                contentColor = colors.onSurface
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("拔电关机", style = MaterialTheme.typography.titleSmall)
                    PaletteSwatch(colors)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        ThemeMode.entries.forEach { mode ->
            val shapes = when (mode) {
                ThemeMode.System -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                ThemeMode.Light -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                ThemeMode.Dark -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            }
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { if (it) onSelect(mode) },
                modifier = Modifier.weight(1f),
                shapes = shapes,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(mode.label)
            }
        }
    }
}

@Composable
private fun PalettePicker(
    selected: ThemePalette?,
    isDark: Boolean,
    onSelect: (ThemePalette) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 3 else 2
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePalette.entries.chunked(columns).forEach { palettes ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palettes.forEach { palette ->
                        PaletteCard(
                            palette = palette,
                            selected = selected == palette,
                            isDark = isDark,
                            onClick = { onSelect(palette) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: ThemePalette,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val preview = palette.colorScheme(isDark)
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.clip(shape).selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick
        ),
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(1.dp, colors.primary) else null
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletteSwatch(preview)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(palette.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Spacer(Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(colors: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).clip(CircleShape)
    ) {
        Box(Modifier.weight(2f).fillMaxHeight().background(colors.primary))
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.secondaryContainer))
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.tertiary))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMediumEmphasized)
}
