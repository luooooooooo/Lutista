package cn.alittlecookie.lut2photo_n.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

enum class RecipeTab(val label: String) {
    FILTER("滤镜"),
    WATERMARK("边框水印")
}

data class RecipeItem(
    val id: Long,
    val name: String,
    val isEnabled: Boolean = true,
    val type: RecipeTab
)

enum class MoreAction {
    TOGGLE_ENABLE,
    RENAME,
    COPY,
    EXPORT_LUT,
    DELETE
}

@Composable
fun RecipeRoomScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = RecipeTab.entries.map { it.label }

    val filters = remember {
        mutableStateListOf(
            RecipeItem(1, "电影风格", type = RecipeTab.FILTER),
            RecipeItem(2, "日系清新", type = RecipeTab.FILTER),
            RecipeItem(3, "黑白经典", type = RecipeTab.FILTER),
            RecipeItem(4, "复古胶片", type = RecipeTab.FILTER),
            RecipeItem(5, "人像美颜", type = RecipeTab.FILTER)
        )
    }

    val watermarks = remember {
        mutableStateListOf(
            RecipeItem(101, "白色边框", type = RecipeTab.WATERMARK),
            RecipeItem(102, "黑色边框", type = RecipeTab.WATERMARK),
            RecipeItem(103, "自定义水印", type = RecipeTab.WATERMARK)
        )
    }

    val currentList = if (selectedTab == 0) filters else watermarks
    val isWatermarkTab = selectedTab == 1

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TabRowWithContour(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        RecipeList(
            items = currentList,
            isWatermarkTab = isWatermarkTab,
            onMove = { from, to ->
                if (selectedTab == 0) {
                    val item = filters.removeAt(from.index)
                    filters.add(to.index, item)
                } else {
                    val item = watermarks.removeAt(from.index)
                    watermarks.add(to.index, item)
                }
            },
            onEditClick = { /* TODO */ },
            onMoreAction = { item, action ->
                when (action) {
                    MoreAction.TOGGLE_ENABLE -> {
                        val index = currentList.indexOf(item)
                        if (index >= 0) {
                            if (selectedTab == 0) {
                                filters[index] = item.copy(isEnabled = !item.isEnabled)
                            } else {
                                watermarks[index] = item.copy(isEnabled = !item.isEnabled)
                            }
                        }
                    }
                    MoreAction.RENAME -> {}
                    MoreAction.COPY -> {
                        val newItem = item.copy(id = System.currentTimeMillis(), name = "${item.name} 副本")
                        if (selectedTab == 0) filters.add(newItem) else watermarks.add(newItem)
                    }
                    MoreAction.EXPORT_LUT -> {}
                    MoreAction.DELETE -> {
                        if (selectedTab == 0) filters.remove(item) else watermarks.remove(item)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scrollBehavior != null) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

@Composable
private fun RecipeList(
    items: List<RecipeItem>,
    isWatermarkTab: Boolean,
    onMove: suspend kotlinx.coroutines.CoroutineScope.(from: androidx.compose.foundation.lazy.LazyListItemInfo, to: androidx.compose.foundation.lazy.LazyListItemInfo) -> Unit,
    onEditClick: (RecipeItem) -> Unit,
    onMoreAction: (RecipeItem, MoreAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState, onMove = onMove)

    LazyColumn(
        state = lazyListState,
        modifier = modifier.overScrollVertical(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
            ReorderableItem(reorderableState, key = item.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                RecipeListItem(
                    item = item,
                    isDragging = isDragging,
                    isWatermarkTab = isWatermarkTab,
                    elevation = elevation,
                    dragHandleModifier = Modifier.draggableHandle(
                        onDragStarted = {},
                        onDragStopped = {}
                    ),
                    onEditClick = { onEditClick(item) },
                    onMoreAction = { action -> onMoreAction(item, action) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RecipeListItem(
    item: RecipeItem,
    isDragging: Boolean,
    isWatermarkTab: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    dragHandleModifier: Modifier = Modifier,
    onEditClick: () -> Unit,
    onMoreAction: (MoreAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMorePopup by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(16.dp)),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽把手 - 使用传入的 draggableHandle modifier
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = "拖拽排序",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MiuixTheme.textStyles.body1,
                    color = if (item.isEnabled) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                    }
                )
                if (!item.isEnabled) {
                    Text(
                        text = "已禁用",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Create,
                    contentDescription = "编辑",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMorePopup = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.More,
                        contentDescription = "更多",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(20.dp)
                    )
                }

                OverlayListPopup(
                    show = showMorePopup,
                    onDismissRequest = { showMorePopup = false },
                    alignment = PopupPositionProvider.Align.End
                ) {
                    ListPopupColumn {
                        val menuItems = buildList {
                            add(if (item.isEnabled) "禁用" else "启用")
                            add("重命名")
                            add("拷贝")
                            if (!isWatermarkTab) add("导出统一LUT")
                            add("删除")
                        }

                        menuItems.forEachIndexed { index, text ->
                            val isDestructive = text == "删除"
                            DropdownImpl(
                                text = text,
                                optionSize = menuItems.size,
                                isSelected = false,
                                index = index,
                                dropdownColors = if (isDestructive) {
                                    DropdownDefaults.dropdownColors(
                                        contentColor = MiuixTheme.colorScheme.error
                                    )
                                } else {
                                    DropdownDefaults.dropdownColors()
                                },
                                onSelectedIndexChange = {
                                    showMorePopup = false
                                    when (text) {
                                        "禁用", "启用" -> onMoreAction(MoreAction.TOGGLE_ENABLE)
                                        "重命名" -> onMoreAction(MoreAction.RENAME)
                                        "拷贝" -> onMoreAction(MoreAction.COPY)
                                        "导出统一LUT" -> onMoreAction(MoreAction.EXPORT_LUT)
                                        "删除" -> onMoreAction(MoreAction.DELETE)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeRoomTopBar(
    onImportClick: () -> Unit
) {
    IconButton(onClick = onImportClick) {
        Icon(
            imageVector = MiuixIcons.Import,
            contentDescription = "导入",
            tint = MiuixTheme.colorScheme.onSurface
        )
    }
}
