package cn.alittlecookie.lutista.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import cn.alittlecookie.lutista.manager.ThemeMode
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun LutistaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MiuixTheme(colors = colorScheme) { content() }
}

@Composable
fun rememberLutistaBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun LayerBackdrop?.getAppBarColor(): Color =
    this?.let { Color.Transparent } ?: MiuixTheme.colorScheme.surface

@Composable
fun Modifier.lutistaBlurEffect(
    backdrop: LayerBackdrop?,
    enabled: Boolean = true,
    blurRadius: Float = 25f,
    shape: Shape = RectangleShape
): Modifier {
    if (!enabled || backdrop == null) return this
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
    return this.then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(BlendColorEntry(color = blendColor))
            )
        )
    )
}
