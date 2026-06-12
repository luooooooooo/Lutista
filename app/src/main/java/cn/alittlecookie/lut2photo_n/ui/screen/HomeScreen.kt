package cn.alittlecookie.lut2photo_n.ui.screen

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
@Preview
fun HomeScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null
) {
    var isMonitorRunning by remember { mutableStateOf(false) }
    var isCameraMode by remember { mutableStateOf(false) }
    var processedCount by remember { mutableIntStateOf(0) }
    var onlyNewFiles by remember { mutableStateOf(true) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var selectedWatermarkIndex by remember { mutableIntStateOf(0) }

    val filterOptions = listOf(
        DropdownItem(text = "无滤镜"),
        DropdownItem(text = "默认滤镜"),
        DropdownItem(text = "电影风格"),
        DropdownItem(text = "日系清新")
    )
    val watermarkOptions = listOf(
        DropdownItem(text = "无水印"),
        DropdownItem(text = "默认边框"),
        DropdownItem(text = "白色边框"),
        DropdownItem(text = "黑色边框")
    )

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
                onCameraClick = { /* TODO: 打开联机页面 */ }
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
    onCameraClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：实时处理状态卡片
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

        // 右侧：两个小卡片
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 联机模式卡片
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
                            fontSize = 15.sp,
                            color = colorScheme.onSurfaceVariantSummary
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

            // 从相册选择卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                insideMargin = PaddingValues(16.dp),
                onClick = { /* TODO: 打开相册 */ },
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
                        fontSize = 15.sp,
                        color = colorScheme.onSurfaceVariantSummary
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
            // 仅处理新增文件
            SwitchPreference(
                title = "仅处理新增文件",
                summary = "开启后只处理监控文件夹中新增的图片",
                checked = onlyNewFiles,
                onCheckedChange = onOnlyNewFilesChange
            )
            // 监控文件夹
            ArrowPreference(
                title = "监控文件夹",
                summary = "点击设置监控文件夹",
                onClick = onMonitorFolderClick
            )

            // 输出文件夹
            ArrowPreference(
                title = "输出文件夹",
                summary = "点击设置输出文件夹",
                onClick = onOutputFolderClick
            )

            // 滤镜选择
            OverlaySpinnerPreference(
                title = "滤镜选择",
                summary = "选择要应用的滤镜",
                items = filterOptions,
                selectedIndex = selectedFilterIndex,
                onSelectedIndexChange = onFilterSelected
            )

            // 边框水印选择
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
