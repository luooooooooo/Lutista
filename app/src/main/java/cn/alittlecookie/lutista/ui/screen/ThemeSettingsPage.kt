package cn.alittlecookie.lutista.ui.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.manager.ThemeMode
import cn.alittlecookie.lutista.ui.theme.getAppBarColor
import cn.alittlecookie.lutista.ui.theme.lutistaBlurEffect
import cn.alittlecookie.lutista.ui.theme.rememberLutistaBlurBackdrop
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.utils.overScrollVertical

private data class PresetColor(val name: String, val color: Color)

private val presetColors = listOf(
    PresetColor("默认", Color(0xFF6750A4)),
    PresetColor("粉色", Color(0xFFB94073)),
    PresetColor("红色", Color(0xFFBA1A1A)),
    PresetColor("橙色", Color(0xFFB74D1A)),
    PresetColor("琥珀", Color(0xFFBA7F1A)),
    PresetColor("黄色", Color(0xFFB0B01A)),
    PresetColor("青柠", Color(0xFF6F941A)),
    PresetColor("绿色", Color(0xFF3C8C1A)),
    PresetColor("青色", Color(0xFF178C74)),
    PresetColor("蓝绿", Color(0xFF17828C)),
    PresetColor("淡蓝", Color(0xFF176B8C)),
    PresetColor("蓝色", Color(0xFF1A56B0)),
    PresetColor("靛蓝", Color(0xFF5740B0)),
    PresetColor("紫色", Color(0xFF8A40B0)),
    PresetColor("深紫", Color(0xFF8C408C)),
    PresetColor("蓝灰", Color(0xFF65758C)),
    PresetColor("棕色", Color(0xFF756340)),
    PresetColor("灰色", Color(0xFF6B6B6B))
)

private val paletteStyleNames = listOf(
    "TonalSpot", "Neutral", "Vibrant", "Expressive",
    "Rainbow", "FruitSalad", "Monochrome", "Fidelity", "Content"
)

private val supportsSpec2025 = setOf("TonalSpot", "Neutral", "Vibrant", "Expressive")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSettingsPage(
    onBackClick: () -> Unit
) {
    val settingsManager: SettingsManager = koinInject()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var themeMode by remember { mutableStateOf(settingsManager.getThemeModeSync()) }
    var useBlur by remember { mutableStateOf(settingsManager.getUseBlurSync()) }
    var useMonet by remember { mutableStateOf(settingsManager.getUseMonetSync()) }
    var useDynamicColor by remember { mutableStateOf(settingsManager.getUseDynamicColorSync()) }
    var seedColor by remember { mutableStateOf(settingsManager.getSeedColorSync()) }
    var paletteStyle by remember { mutableStateOf(settingsManager.getPaletteStyleSync()) }
    var colorSpec by remember { mutableStateOf(settingsManager.getColorSpecSync()) }

    LaunchedEffect(Unit) { settingsManager.themeMode.collect { themeMode = it } }
    LaunchedEffect(Unit) { settingsManager.useBlur.collect { useBlur = it } }
    LaunchedEffect(Unit) { settingsManager.useMonet.collect { useMonet = it } }
    LaunchedEffect(Unit) { settingsManager.useDynamicColor.collect { useDynamicColor = it } }
    LaunchedEffect(Unit) { settingsManager.seedColor.collect { seedColor = it } }
    LaunchedEffect(Unit) { settingsManager.paletteStyle.collect { paletteStyle = it } }
    LaunchedEffect(Unit) { settingsManager.colorSpec.collect { colorSpec = it } }

    val backdrop = rememberLutistaBlurBackdrop(useBlur)
    val barColor = backdrop.getAppBarColor()

    BackHandler { onBackClick() }

    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.lutistaBlurEffect(backdrop),
                color = barColor,
                title = "主题设置",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SmallTitle(
                    text = "外观",
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Card {
                    val themeOptions = remember {
                        listOf(
                            DropdownItem(text = "跟随系统"),
                            DropdownItem(text = "浅色"),
                            DropdownItem(text = "深色")
                        )
                    }
                    OverlaySpinnerPreference(
                        title = "主题模式",
                        summary = "控制全局深浅色外观",
                        items = themeOptions,
                        selectedIndex = themeMode.ordinal,
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsManager.setThemeMode(ThemeMode.entries[index])
                            }
                        }
                    )
                }
            }

            item {
                SmallTitle(
                    text = "自定义颜色",
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Card {
                    SwitchPreference(
                        title = "自定义颜色",
                        summary = "启用动态取色 (Monet) 自定义主题配色",
                        checked = useMonet,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setUseMonet(enabled) }
                        }
                    )
                    AnimatedVisibility(
                        visible = useMonet,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Card {
                            if (dynamicColorAvailable) {
                                SwitchPreference(
                                    title = "动态取色",
                                    summary = "优先使用系统壁纸颜色作为种子色",
                                    checked = useDynamicColor,
                                    onCheckedChange = { enabled ->
                                        scope.launch { settingsManager.setUseDynamicColor(enabled) }
                                    }
                                )
                            }

                            val paletteItems = remember {
                                paletteStyleNames.map { DropdownItem(text = it) }
                            }
                            val paletteIndex = paletteStyleNames.indexOf(paletteStyle).coerceAtLeast(0)
                            OverlaySpinnerPreference(
                                title = "调色板风格",
                                summary = "控制 Monet 生成的色调风格",
                                items = paletteItems,
                                selectedIndex = paletteIndex,
                                onSelectedIndexChange = { index ->
                                    val newStyle = paletteStyleNames[index]
                                    scope.launch { settingsManager.setPaletteStyle(newStyle) }
                                    // Downgrade colorSpec if new style doesn't support Spec2025
                                    if (newStyle !in supportsSpec2025 && colorSpec == "Spec2025") {
                                        scope.launch { settingsManager.setColorSpec("Spec2021") }
                                    }
                                }
                            )

                            val currentPalette = paletteStyle
                            val specItems = if (currentPalette in supportsSpec2025) {
                                remember {
                                    listOf(
                                        DropdownItem(text = "Material 3 (2021)"),
                                        DropdownItem(text = "Expressive (2025)")
                                    )
                                }
                            } else {
                                remember {
                                    listOf(DropdownItem(text = "Material 3 (2021)"))
                                }
                            }
                            val specIndex = if (colorSpec == "Spec2025" && currentPalette in supportsSpec2025) 1 else 0
                            OverlaySpinnerPreference(
                                title = "颜色规范",
                                summary = "选择 Material 颜色规范版本",
                                items = specItems,
                                selectedIndex = specIndex,
                                onSelectedIndexChange = { index ->
                                    val newSpec = if (index == 1) "Spec2025" else "Spec2021"
                                    scope.launch { settingsManager.setColorSpec(newSpec) }
                                }
                            )

                            // Seed color grid — shown when dynamic color is OFF or unavailable
                            AnimatedVisibility(
                                visible = !useDynamicColor || !dynamicColorAvailable,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    SmallTitle(
                                        text = "种子色",
                                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        presetColors.forEach { preset ->
                                            val isSelected = try {
                                                val hex = String.format("#%08X", preset.color.toArgb())
                                                hex == seedColor
                                            } catch (_: Exception) { false }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(preset.color)
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier.border(3.dp, Color.White, CircleShape)
                                                        } else Modifier
                                                    )
                                                    .clickable {
                                                        val hex = String.format("#%08X", preset.color.toArgb())
                                                        scope.launch { settingsManager.setSeedColor(hex) }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.8f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SmallTitle(
                    text = "效果",
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Card {
                    if (isRuntimeShaderSupported()) {
                        SwitchPreference(
                            title = "启用模糊",
                            summary = "顶栏和底栏使用背景模糊效果（需 API 33+）",
                            checked = useBlur,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsManager.setUseBlur(enabled)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
