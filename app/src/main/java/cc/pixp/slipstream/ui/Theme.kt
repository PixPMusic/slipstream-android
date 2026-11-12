package cc.pixp.slipstream.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SlipstreamTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if(android.os.Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if(dark) darkColorScheme() else lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
