package cn.alittlecookie.lutista.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import cn.alittlecookie.lutista.manager.ThemeMode
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val COLOR_DEFAULT_SEED = Color(0xFF6750A4)

private val paletteStylesSupportSpec2025 = setOf("TonalSpot", "Neutral", "Vibrant", "Expressive")

private fun parseColorHex(hex: String): Color? = try {
    val argb = hex.removePrefix("#").toLong(16).toInt()
    Color(argb)
} catch (_: Exception) { null }

private fun parsePaletteStyle(name: String): ThemePaletteStyle = when (name) {
    "Neutral" -> ThemePaletteStyle.Neutral
    "Vibrant" -> ThemePaletteStyle.Vibrant
    "Expressive" -> ThemePaletteStyle.Expressive
    "Rainbow" -> ThemePaletteStyle.Rainbow
    "FruitSalad" -> ThemePaletteStyle.FruitSalad
    "Monochrome" -> ThemePaletteStyle.Monochrome
    "Fidelity" -> ThemePaletteStyle.Fidelity
    "Content" -> ThemePaletteStyle.Content
    else -> ThemePaletteStyle.TonalSpot
}

private fun parseColorSpec(name: String, paletteStyle: String): ThemeColorSpec =
    if (name == "Spec2025" && paletteStyle in paletteStylesSupportSpec2025)
        ThemeColorSpec.Spec2025
    else
        ThemeColorSpec.Spec2021

@Composable
fun LutistaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useMonet: Boolean = false,
    useDynamicColor: Boolean = true,
    seedColor: String = "",
    paletteStyle: String = "TonalSpot",
    colorSpec: String = "Spec2021",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Compute keyColor outside remember because colorResource is @Composable
    val keyColor: Color = if (useMonet) {
        if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            colorResource(id = android.R.color.system_accent1_500)
        } else if (seedColor.isNotEmpty()) {
            parseColorHex(seedColor) ?: COLOR_DEFAULT_SEED
        } else {
            COLOR_DEFAULT_SEED
        }
    } else {
        Color.Unspecified // unused when useMonet=false
    }

    val controller = remember(themeMode, useMonet, useDynamicColor, seedColor, paletteStyle, colorSpec, darkTheme) {
        if (useMonet) {
            val monetMode = when (themeMode) {
                ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
                ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
                ThemeMode.DARK -> ColorSchemeMode.MonetDark
            }
            val style = parsePaletteStyle(paletteStyle)
            val spec = parseColorSpec(colorSpec, paletteStyle)
            ThemeController(
                colorSchemeMode = monetMode,
                keyColor = keyColor,
                paletteStyle = style,
                colorSpec = spec,
                isDark = darkTheme
            )
        } else {
            ThemeController(
                colorSchemeMode = when (themeMode) {
                    ThemeMode.SYSTEM -> ColorSchemeMode.System
                    ThemeMode.LIGHT -> ColorSchemeMode.Light
                    ThemeMode.DARK -> ColorSchemeMode.Dark
                },
                isDark = darkTheme
            )
        }
    }

    MiuixTheme(controller = controller) { content() }
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
