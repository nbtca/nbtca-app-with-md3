package space.nbtca.app.ui.theme

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import space.nbtca.app.core.config.AppearanceSettings

val LocalIsDark = staticCompositionLocalOf { false }

internal val supportsWallpaperColors: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NbtcaTheme(
    darkTheme: Boolean,
    appearance: AppearanceSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = remember(context, configuration, darkTheme, appearance.dynamicColor, appearance.palette) {
        if (appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            appearance.palette.colorScheme(darkTheme)
        }
    }
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialExpressiveTheme(colorScheme = colors, content = content)
    }
}

object AppThemeColors {
    val textPrimary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
    val textSecondary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val divider: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val surface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val surfaceElevated: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
}
