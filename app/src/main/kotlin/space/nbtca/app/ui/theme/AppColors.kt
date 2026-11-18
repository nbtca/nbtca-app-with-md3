package space.nbtca.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** State colors shared by repair tickets and the service monitor. */
object AppColors {
    val StatusPending: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFFFFBF66) else Color(0xFF795300)
    val StatusCompleted: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFF95D5A5) else Color(0xFF246638)
    val StatusAlert: Color
        @Composable get() = MaterialTheme.colorScheme.error
}
