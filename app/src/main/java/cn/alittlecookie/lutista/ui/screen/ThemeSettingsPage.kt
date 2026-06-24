package cn.alittlecookie.lutista.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.manager.ThemeMode
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ThemeSettingsPage(
    onBackClick: () -> Unit
) {
    val settingsManager: SettingsManager = koinInject()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var themeMode by remember { mutableStateOf(settingsManager.getThemeModeSync()) }
    var useBlur by remember { mutableStateOf(settingsManager.getUseBlurSync()) }

    LaunchedEffect(Unit) {
        settingsManager.themeMode.collect { themeMode = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.useBlur.collect { useBlur = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
