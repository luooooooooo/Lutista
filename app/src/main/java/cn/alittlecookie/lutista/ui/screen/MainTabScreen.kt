package cn.alittlecookie.lutista.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Settings

enum class MainPage(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("主页", MiuixIcons.Album),
    RECIPE("配方室", MiuixIcons.Filter),
    SETTINGS("设置", MiuixIcons.Settings)
}

@Composable
fun MainTabScreen(
    onPhotoSelected: (List<String>) -> Unit,
    launchPicker: () -> Unit = {},
    onEditFilter: (Long) -> Unit = {},
    onEditWatermark: (Long) -> Unit = {},
    initialPage: Int = 0,
    recipeSubTab: Int = 0,
    onRecipeSubTabChange: (Int) -> Unit = {}
) {
    var selectedPage by rememberSaveable { mutableIntStateOf(initialPage) }
    val pages = MainPage.entries
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var recipeImportTrigger by remember { mutableLongStateOf(0L) }
    var recipeCreateTrigger by remember { mutableLongStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = pages[selectedPage].label,
                largeTitle = pages[selectedPage].label,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (selectedPage == 1) {
                        RecipeRoomTopBar(
                            onCreateClick = { recipeCreateTrigger++ },
                            onImportClick = { recipeImportTrigger++ }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                pages.forEachIndexed { index, page ->
                    NavigationBarItem(
                        selected = selectedPage == index,
                        onClick = { selectedPage = index },
                        icon = page.icon,
                        label = page.label
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedPage) {
            0 -> HomeScreen(
                modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
                scrollBehavior = scrollBehavior,
                onPhotoSelected = onPhotoSelected,
                launchPicker = launchPicker
            )
            1 -> RecipeRoomScreen(
                modifier = Modifier.padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                ),
                scrollBehavior = scrollBehavior,
                onEditFilter = onEditFilter,
                onEditWatermark = onEditWatermark,
                importTrigger = recipeImportTrigger,
                createTrigger = recipeCreateTrigger,
                selectedSubTab = recipeSubTab,
                onSubTabChange = onRecipeSubTabChange
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
                scrollBehavior = scrollBehavior
            )
        }
    }
}
