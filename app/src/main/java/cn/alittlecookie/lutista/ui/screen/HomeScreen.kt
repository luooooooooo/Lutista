package cn.alittlecookie.lutista.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.alittlecookie.lutista.data.repository.FilterRepository
import cn.alittlecookie.lutista.data.repository.WatermarkRepository
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical


@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    onPhotoSelected: (List<String>) -> Unit = {},
    launchPicker: () -> Unit = {}
) {
    val filterRepository: FilterRepository = koinInject()
    val watermarkRepository: WatermarkRepository = koinInject()

    var isMonitorRunning by remember { mutableStateOf(false) }
    var isCameraMode by remember { mutableStateOf(false) }
    var processedCount by remember { mutableIntStateOf(0) }
    var onlyNewFiles by remember { mutableStateOf(true) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var selectedWatermarkIndex by remember { mutableIntStateOf(0) }

    val filtersFromDb by filterRepository.getAllFilters().collectAsState(initial = emptyList())

    val filterOptions = remember(filtersFromDb) {
        buildList {
            add(DropdownItem(text = "无滤镜"))
            addAll(filtersFromDb.map { filter ->
                DropdownItem(text = filter.name)
            })
        }
    }

    val watermarksFromDb by watermarkRepository.getAllWatermarks().collectAsState(initial = emptyList())

    val watermarkOptions = remember(watermarksFromDb) {
        buildList {
            add(DropdownItem(text = "无水印"))
            addAll(watermarksFromDb.map { DropdownItem(text = it.name) })
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatusSection(
                isMonitorRunning = isMonitorRunning,
                processedCount = processedCount,
                isCameraMode = isCameraMode,
                onMonitorToggle = { isMonitorRunning = !isMonitorRunning },
                onCameraModeChange = { isCameraMode = it },
                onCameraClick = { /* TODO: 打开联机页面 */ },
                onPhotoSelected = onPhotoSelected,
                launchPicker = launchPicker
            )
        }

        item {
            MonitorSettingsCard(
                onlyNewFiles = onlyNewFiles,
                onOnlyNewFilesChange = { onlyNewFiles = it },
                onMonitorFolderClick = { /* TODO: SAF 选择监控文件夹 */ },
                onOutputFolderClick = { /* TODO: SAF 选择输出文件夹 */ },
                filterOptions = filterOptions,
                selectedFilterIndex = selectedFilterIndex,
                onFilterSelected = { selectedFilterIndex = it },
                watermarkOptions = watermarkOptions,
                selectedWatermarkIndex = selectedWatermarkIndex,
                onWatermarkSelected = { selectedWatermarkIndex = it }
            )
        }
    }
}

@Composable
private fun StatusSection(
    isMonitorRunning: Boolean,
    processedCount: Int,
    isCameraMode: Boolean,
    onMonitorToggle: () -> Unit,
    onCameraModeChange: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    onPhotoSelected: (List<String>) -> Unit,
    launchPicker: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.defaultColors(
                color = if (isMonitorRunning) {
                    Color(0xFFDFFAE4)
                } else {
                    colorScheme.surfaceVariant
                }
            ),
            onClick = onMonitorToggle,
            showIndication = true
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(30.dp, 36.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = if (isMonitorRunning) Icons.Rounded.AutoAwesome else MiuixIcons.Demibold.Close2,
                        tint = if (isMonitorRunning) {
                            Color(0xFF36D167).copy(alpha = 0.8f)
                        } else {
                            Color(0xFFFF4444).copy(alpha = 0.2f)
                        },
                        contentDescription = null
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 16.dp)
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (isMonitorRunning) "实时处理已启动" else "实时处理未启动",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMonitorRunning) {
                            Color(0xFF1A3825)
                        } else {
                            colorScheme.onSurface
                        }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "已处理 $processedCount 张图片",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isMonitorRunning) {
                            Color(0xFF1A3825).copy(alpha = 0.7f)
                        } else {
                            colorScheme.onSurfaceVariantSummary
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                insideMargin = PaddingValues(16.dp),
                onClick = onCameraClick,
                showIndication = true
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "联机模式",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (isCameraMode) "已开启" else "已关闭",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = isCameraMode,
                        onCheckedChange = onCameraModeChange
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                insideMargin = PaddingValues(16.dp),
                onClick = launchPicker,
                showIndication = true
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "从相册选择",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = colorScheme.onSurface
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "选择图片进行处理",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorSettingsCard(
    onlyNewFiles: Boolean,
    onOnlyNewFilesChange: (Boolean) -> Unit,
    onMonitorFolderClick: () -> Unit,
    onOutputFolderClick: () -> Unit,
    filterOptions: List<DropdownItem>,
    selectedFilterIndex: Int,
    onFilterSelected: (Int) -> Unit,
    watermarkOptions: List<DropdownItem>,
    selectedWatermarkIndex: Int,
    onWatermarkSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "仅处理新增文件",
                summary = "开启后只处理监控文件夹中新增的图片",
                checked = onlyNewFiles,
                onCheckedChange = onOnlyNewFilesChange
            )
            ArrowPreference(
                title = "监控文件夹",
                summary = "点击设置监控文件夹",
                onClick = onMonitorFolderClick
            )

            ArrowPreference(
                title = "输出文件夹",
                summary = "点击设置输出文件夹",
                onClick = onOutputFolderClick
            )

            OverlaySpinnerPreference(
                title = "滤镜选择",
                summary = "选择要应用的滤镜",
                items = filterOptions,
                selectedIndex = selectedFilterIndex,
                onSelectedIndexChange = onFilterSelected
            )

            OverlaySpinnerPreference(
                title = "边框水印选择",
                summary = "选择要应用的边框水印",
                items = watermarkOptions,
                selectedIndex = selectedWatermarkIndex,
                onSelectedIndexChange = onWatermarkSelected
            )
        }
    }
}
