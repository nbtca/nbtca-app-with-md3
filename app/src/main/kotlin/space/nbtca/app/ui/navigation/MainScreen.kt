package space.nbtca.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.config.AppearanceSettings
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.screens.CardScreen
import space.nbtca.app.ui.screens.NewsroomScreen
import space.nbtca.app.ui.screens.ProfileScreen
import space.nbtca.app.ui.screens.RepairScreen
import space.nbtca.app.ui.screens.ToolsScreen

private data class Destination(val label: String, val active: ImageVector, val inactive: ImageVector)

private val destinations = listOf(
    Destination("资讯", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    Destination("维修", Icons.Filled.BuildCircle, Icons.Outlined.BuildCircle),
    Destination("工具", Icons.Filled.Widgets, Icons.Outlined.Widgets),
    Destination("Card", Icons.Filled.CreditCard, Icons.Outlined.CreditCard),
    Destination("我的", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
)

@Composable
fun MainScreen(
    authManager: AuthManager,
    apiClient: ApiClient,
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val screenState = rememberSaveableStateHolder()
    val context = LocalContext.current
    val academicStore = remember(context) { AcademicStore.getInstance(context) }

    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        val useRail = maxWidth >= 840.dp
        val iconPosition = if (maxWidth >= 600.dp) NavigationItemIconPosition.Start
        else NavigationItemIconPosition.Top

        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                WideNavigationRail(
                    colors = WideNavigationRailDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    destinations.forEachIndexed { position, item ->
                        val selected = index == position
                        WideNavigationRailItem(
                            selected = selected,
                            onClick = { index = position },
                            icon = { DestinationIcon(item, selected) },
                            label = { DestinationLabel(item, selected) },
                            railExpanded = false
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (!useRail) {
                        ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            destinations.forEachIndexed { position, item ->
                                val selected = index == position
                                ShortNavigationBarItem(
                                    selected = selected,
                                    onClick = { index = position },
                                    icon = { DestinationIcon(item, selected) },
                                    label = { DestinationLabel(item, selected) },
                                    iconPosition = iconPosition
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                // Destination scaffolds apply their own status-bar insets.
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight()) {
                        screenState.SaveableStateProvider(index) {
                            when (index) {
                                0 -> NewsroomScreen(apiClient)
                                1 -> RepairScreen(apiClient, authManager)
                                2 -> ToolsScreen(apiClient, academicStore)
                                3 -> CardScreen()
                                else -> ProfileScreen(
                                    authManager = authManager,
                                    academicStore = academicStore,
                                    appearance = appearance,
                                    onAppearanceChange = onAppearanceChange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(item: Destination, selected: Boolean) {
    Icon(if (selected) item.active else item.inactive, contentDescription = null)
}

@Composable
private fun DestinationLabel(item: Destination, selected: Boolean) {
    Text(item.label, fontWeight = if (selected) FontWeight.Bold else null, maxLines = 1)
}
