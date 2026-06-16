package cn.alittlecookie.lut2photo_n.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.alittlecookie.lut2photo_n.data.database.entity.FilterEntity
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.domain.usecase.ExportFilterUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ImportLutUseCase
import cn.alittlecookie.lut2photo_n.manager.BuiltinFilterManager
import cn.alittlecookie.lut2photo_n.manager.LogManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.compose.koinInject
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
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

enum class RecipeTab(val label: String) {
    FILTER("滤镜"),
    WATERMARK("边框水印")
}

data class RecipeItem(
    val id: Long,
    val name: String,
    val isEnabled: Boolean = true,
    val type: RecipeTab,
    val isBuiltIn: Boolean = false
)

enum class MoreAction {
    TOGGLE_ENABLE,
    RENAME,
    COPY,
    EXPORT_LUT,
    EXPORT_CONFIG,
    DELETE
}

@Composable
fun RecipeRoomScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    onEditFilter: (Long) -> Unit = {},
    importTrigger: Long = 0L
) {
    val context = LocalContext.current
    val filterRepository: FilterRepository = koinInject()
    val builtinFilterManager: BuiltinFilterManager = koinInject()
    val exportFilterUseCase: ExportFilterUseCase = koinInject()
    val importLutUseCase: ImportLutUseCase = koinInject()
    val logger: LogManager = koinInject()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = RecipeTab.entries.map { it.label }

    val filtersFromDb by filterRepository.getAllFilters().collectAsState(initial = emptyList())

    val filters = remember(filtersFromDb) {
        mutableStateListOf<RecipeItem>().apply {
            clear()
            addAll(filtersFromDb.map { entity ->
                RecipeItem(
                    id = entity.id,
                    name = entity.name,
                    isEnabled = true,
                    type = RecipeTab.FILTER,
                    isBuiltIn = entity.isBuiltIn
                )
            })
        }
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

    // SAF file picker for import
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleImport(it, context, scope, importLutUseCase, filterRepository, logger) }
    }

    LaunchedEffect(importTrigger) {
        if (importTrigger > 0) {
            importPicker.launch(arrayOf("*/*"))
        }
    }

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
            onEditClick = { item ->
                if (item.isBuiltIn) {
                    scope.launch {
                        val copy = builtinFilterManager.copyBuiltinFilter(item.id)
                        if (copy != null) {
                            Toast.makeText(context, "已创建\"${copy.name}\"滤镜", Toast.LENGTH_SHORT).show()
                            onEditFilter(copy.id)
                        }
                    }
                } else {
                    onEditFilter(item.id)
                }
            },
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
                        scope.launch {
                            val filterEntity = filterRepository.getFilterById(item.id)
                            if (filterEntity != null) {
                                if (filterEntity.isBuiltIn) {
                                    builtinFilterManager.copyBuiltinFilter(item.id)
                                } else {
                                    val newName = "${item.name}-拷贝${System.currentTimeMillis()}"
                                    val newEntity = filterEntity.copy(
                                        id = 0,
                                        name = newName,
                                        isBuiltIn = false,
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    filterRepository.insert(newEntity)
                                }
                            }
                        }
                    }
                    MoreAction.EXPORT_LUT -> {
                        scope.launch {
                            exportFilterUseCase.exportUnifiedLut(item.id)
                        }
                    }
                    MoreAction.EXPORT_CONFIG -> {
                        scope.launch {
                            exportFilterUseCase.exportFilterConfig(item.id)
                        }
                    }
                    MoreAction.DELETE -> {
                        if (!item.isBuiltIn) {
                            scope.launch {
                                filterRepository.deleteById(item.id)
                            }
                        }
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

private fun handleImport(
    uri: android.net.Uri,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    importLutUseCase: ImportLutUseCase,
    filterRepository: FilterRepository,
    logger: LogManager
) {
    val fileName = uri.lastPathSegment ?: "unknown"
    when (val ext = fileName.substringAfterLast('.').lowercase()) {
        "cube", "vlt" -> {
            scope.launch {
                when (val result = importLutUseCase(uri, ImportLutUseCase.DuplicateAction.RENAME)) {
                    is ImportLutUseCase.ImportResult.Success -> {
                        Toast.makeText(context, "已导入: ${result.filterName}", Toast.LENGTH_SHORT).show()
                    }
                    is ImportLutUseCase.ImportResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ImportLutUseCase.ImportResult.DuplicateExists -> {
                        Toast.makeText(context, "滤镜 \"${result.filterName}\" 已存在", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        "zip" -> {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri) ?: return@launch
                    val zis = ZipInputStream(content)
                    var hasCubeOrVlt = false
                    var hasConfigJson = false
                    var configName: String? = null
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (entryName.endsWith(".cube") || entryName.endsWith(".vlt")) {
                            hasCubeOrVlt = true
                        }
                        if (entryName.endsWith(".json")) {
                            hasConfigJson = true
                            try {
                                val bytes = zis.readBytes()
                                val json = JSONObject(String(bytes))
                                configName = json.optString("name", null)
                            } catch (_: Exception) {}
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    zis.close()
                    content.close()

                    if (hasCubeOrVlt && hasConfigJson) {
                        // Filter package
                        importFilterPackage(context, uri, filterRepository, logger)
                    } else {
                        // Placeholder: watermark package
                        Toast.makeText(context, "边框水印导入暂未实现", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    logger.e("Import", "导入压缩包失败", e)
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else -> {
            Toast.makeText(context, "不支持的文件格式: $ext", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun importFilterPackage(
    context: android.content.Context,
    uri: android.net.Uri,
    filterRepository: FilterRepository,
    logger: LogManager
) {
    try {
        val content = context.contentResolver.openInputStream(uri) ?: return
        val zis = ZipInputStream(content)
        val filterDir = File(context.filesDir, "filters")
        filterDir.mkdirs()

        var configJson: JSONObject? = null
        var lut1Path: String? = null
        var lut2Path: String? = null
        var filterName = "导入滤镜"

        var entry = zis.nextEntry
        while (entry != null) {
            val rawName = entry.name.substringAfterLast('/')
            if (rawName.isEmpty()) { zis.closeEntry(); entry = zis.nextEntry; continue }

            val bytes = zis.readBytes()

            if (rawName.endsWith(".json", ignoreCase = true)) {
                try {
                    configJson = JSONObject(String(bytes))
                    filterName = configJson.optString("name", filterName)
                } catch (_: Exception) {}
            } else if (rawName.endsWith(".cube", ignoreCase = true) || rawName.endsWith(".vlt", ignoreCase = true)) {
                val subDir = File(filterDir, "import_${System.currentTimeMillis()}")
                subDir.mkdirs()
                val destFile = File(subDir, rawName)
                FileOutputStream(destFile).use { it.write(bytes) }

                if (rawName.contains("1", ignoreCase = true) || lut1Path == null) {
                    lut1Path = destFile.absolutePath
                } else {
                    lut2Path = destFile.absolutePath
                }
            }

            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        content.close()

        if (configJson != null) {
            val json = configJson
            val entity = FilterEntity(
                name = filterName,
                lutPath1 = lut1Path,
                lutConcentration1 = json.optDouble("lutConcentration1", 1.0).toFloat(),
                lutPath2 = lut2Path,
                lutConcentration2 = json.optDouble("lutConcentration2", 1.0).toFloat(),
                lutPrecision = json.optInt("lutPrecision", 33),
                exposure = json.optDouble("exposure", 0.0).toFloat(),
                contrast = json.optDouble("contrast", 0.0).toFloat(),
                highlights = json.optDouble("highlights", 0.0).toFloat(),
                shadows = json.optDouble("shadows", 0.0).toFloat(),
                saturation = json.optDouble("saturation", 0.0).toFloat(),
                colorTemperature = json.optDouble("colorTemperature", 0.0).toFloat(),
                tint = json.optDouble("tint", 0.0).toFloat(),
                curveJson = json.optString("curveJson", "{}"),
                lchJson = json.optString("lchJson", "{}"),
                vignetteStrength = json.optDouble("vignetteStrength", 0.0).toFloat(),
                grainJson = json.optString("grainJson", "{}"),
                isBuiltIn = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            filterRepository.insert(entity)
            Toast.makeText(context, "已导入滤镜: $filterName", Toast.LENGTH_SHORT).show()
            logger.i("Import", "滤镜包导入成功: $filterName")
        } else {
            Toast.makeText(context, "压缩包中未找到配置文件", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        logger.e("Import", "导入滤镜包失败", e)
        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MiuixTheme.textStyles.body1,
                        color = if (item.isEnabled) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                        }
                    )
                    if (item.isBuiltIn) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "内置",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
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
                            if (!item.isBuiltIn) add("重命名")
                            add("拷贝")
                            if (!isWatermarkTab) {
                                add("导出统一LUT")
                                add("导出滤镜配置")
                            }
                            if (!item.isBuiltIn) add("删除")
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
                                        "导出滤镜配置" -> onMoreAction(MoreAction.EXPORT_CONFIG)
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
