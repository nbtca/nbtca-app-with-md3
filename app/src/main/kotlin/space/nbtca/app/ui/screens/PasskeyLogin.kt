package space.nbtca.app.ui.screens

import android.webkit.WebView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

internal fun WebView.enableAppPasskeys() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
        WebSettingsCompat.setWebAuthenticationSupport(
            settings,
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
        )
    }
}

@Composable
internal fun LoginHelpButton(browserSignInAvailable: Boolean = false) {
    var showHelp by remember { mutableStateOf(false) }
    IconButton(onClick = { showHelp = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "登录帮助")
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("通行密钥不可用时") },
            text = {
                Text(
                    if (browserSignInAvailable) {
                        "使用 GitHub 通行密钥时，可以从右上角打开浏览器登录，再选择 GitHub。" +
                            "也可以在当前网页选择账号已绑定的其他验证方式。"
                    } else {
                        "遇到通行密钥不可用时，请在网页登录页选择账号已绑定的其他验证方式。"
                    }
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("返回登录") } }
        )
    }
}
