package space.nbtca.app.core.config

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode(val key: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色")
}

enum class ThemePalette(val key: String, val label: String) {
    NbtcaBlue("nbtca_blue", "协会蓝"),
    Forest("forest", "青松绿"),
    Lilac("lilac", "暮藤紫"),
    Amber("amber", "琥珀橙"),
    Rose("rose", "山茶红"),
    Graphite("graphite", "石墨灰")
}

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.System,
    val palette: ThemePalette = ThemePalette.NbtcaBlue,
    val dynamicColor: Boolean = false
)

class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var appearance: AppearanceSettings
        get() {
            val mode = prefs.getString(KEY_THEME_MODE, null)
            val palette = prefs.getString(KEY_PALETTE, null)
            return AppearanceSettings(
                mode = ThemeMode.entries.firstOrNull { it.key == mode } ?: ThemeMode.System,
                palette = ThemePalette.entries.firstOrNull { it.key == palette } ?: ThemePalette.NbtcaBlue,
                dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_THEME_MODE, value.mode.key)
                .putString(KEY_PALETTE, value.palette.key)
                .putBoolean(KEY_DYNAMIC_COLOR, value.dynamicColor)
                .apply()
        }

    companion object {
        private const val KEY_THEME_MODE = "settings.theme_mode"
        private const val KEY_PALETTE = "settings.theme_palette"
        private const val KEY_DYNAMIC_COLOR = "settings.dynamic_color"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
