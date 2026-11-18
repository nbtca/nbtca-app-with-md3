package space.nbtca.app.core.config

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import space.nbtca.app.R

internal enum class LauncherIcon(
    val label: String,
    @DrawableRes val drawable: Int,
    private val alias: String
) {
    Modern("圆章图标", R.mipmap.ic_launcher, "ModernLauncher"),
    Classic("经典图标", R.mipmap.ic_launcher_classic, "ClassicLauncher");

    private fun component(context: Context) = ComponentName(context.packageName, "${context.packageName}.$alias")

    fun select(context: Context) {
        val manager = context.packageManager
        val chosen = component(context)
        val other = (if (this == Modern) Classic else Modern).component(context)
        val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val flags = PackageManager.DONT_KILL_APP

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.setComponentEnabledSettings(
                listOf(
                    PackageManager.ComponentEnabledSetting(chosen, enabled, flags),
                    PackageManager.ComponentEnabledSetting(other, disabled, flags)
                )
            )
        } else {
            val previous = manager.getComponentEnabledSetting(chosen)
            manager.setComponentEnabledSetting(chosen, enabled, flags)
            try {
                manager.setComponentEnabledSetting(other, disabled, flags)
            } catch (failure: RuntimeException) {
                manager.setComponentEnabledSetting(chosen, previous, flags)
                throw failure
            }
        }
    }

    companion object {
        fun current(context: Context): LauncherIcon {
            val state = context.packageManager.getComponentEnabledSetting(Classic.component(context))
            return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) Classic else Modern
        }
    }
}
