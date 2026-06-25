package cn.alittlecookie.lutista.ui.screen

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.alittlecookie.lutista.data.repository.FilterRepository
import cn.alittlecookie.lutista.data.repository.WatermarkRepository
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.service.FileMonitorService
import cn.alittlecookie.lutista.ui.theme.getAppBarColor
import cn.alittlecookie.lutista.ui.theme.lutistaBlurEffect
import cn.alittlecookie.lutista.ui.theme.rememberLutistaBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical


@Composable
fun HomeScreen(
    launchPicker: () -> Unit = {},
    onMonitorFolderClick: () -> Unit = {},
    onOutputFolderClick: () -> Unit = {}
) {
    val filterRepository: FilterRepository = koinInject()
    val watermarkRepository: WatermarkRepository = koinInject()
    val settingsManager: SettingsManager = koinInject()
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var isMonitorRunning by remember { mutableStateOf(false) }
    var monitorRunningPersisted by remember { mutableStateOf(settingsManager.getMonitorRunningSync()) }
    var processedCount by remember { mutableIntStateOf(settingsManager.getProcessedCountSync()) }
    var pendingStartAfterPathSelect by remember { mutableStateOf(false) }

    var isCameraMode by remember { mutableStateOf(false) }
    var onlyNewFiles by remember { mutableStateOf(settingsManager.getOnlyNewFilesSync()) }

    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var selectedWatermarkIndex by remember { mutableIntStateOf(0) }

    val filtersFromDb by filterRepository.getEnabledFilters().collectAsState(initial = emptyList())
    val watermarksFromDb by watermarkRepository.getEnabledWatermarks().collectAsState(initial = emptyList())
    var monitorPath by remember { mutableStateOf(settingsManager.getMonitorPathSync()) }
    var outputPath by remember { mutableStateOf(settingsManager.getOutputPathSync()) }
    var globalFilterId by remember { mutableStateOf(settingsManager.getGlobalFilterIdSync()) }
    var globalWatermarkId by remember { mutableStateOf(settingsManager.getGlobalWatermarkIdSync()) }

    LaunchedEffect(Unit) {
        settingsManager.monitorRunning.collect { monitorRunningPersisted = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.processedCount.collect { processedCount = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.onlyNewFiles.collect { onlyNewFiles = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.monitorPath.collect { monitorPath = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.outputPath.collect { outputPath = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.globalFilterId.collect { globalFilterId = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.globalWatermarkId.collect { globalWatermarkId = it }
    }

    val filterOptions = remember(filtersFromDb) {
        buildList {
            add(DropdownItem(text = "无滤镜"))
            addAll(filtersFromDb.map { filter ->
                DropdownItem(text = filter.name)
            })
        }
    }

    val watermarkOptions = remember(watermarksFromDb) {
        buildList {
            add(DropdownItem(text = "无水印"))
            addAll(watermarksFromDb.map { DropdownItem(text = it.name) })
        }
    }

    LaunchedEffect(filtersFromDb, globalFilterId) {
        if (globalFilterId > 0L) {
            val idx = filtersFromDb.indexOfFirst { it.id == globalFilterId }
            if (idx >= 0) selectedFilterIndex = idx + 1
        }
    }

    LaunchedEffect(watermarksFromDb, globalWatermarkId) {
        if (globalWatermarkId != "none") {
            val id = globalWatermarkId.toLongOrNull()
            if (id != null) {
                val idx = watermarksFromDb.indexOfFirst { it.id == id }
                if (idx >= 0) selectedWatermarkIndex = idx + 1
            }
        }
    }

    val context = LocalContext.current

    fun startMonitorService() {
        val intent = Intent(context, FileMonitorService::class.java).apply {
            action = FileMonitorService.ACTION_START
            putExtra(FileMonitorService.EXTRA_PATH, monitorPath)
            putExtra(FileMonitorService.EXTRA_ONLY_NEW, onlyNewFiles)
        }
        context.startForegroundService(intent)
    }

    fun stopMonitorService() {
        val intent = Intent(context, FileMonitorService::class.java).apply {
            action = FileMonitorService.ACTION_STOP
        }
        context.startService(intent)
    }

    // Auto-start service when monitorPath becomes available after SAF selection
    LaunchedEffect(monitorPath) {
        if (pendingStartAfterPathSelect && monitorPath.isNotEmpty()) {
            pendingStartAfterPathSelect = false
            isMonitorRunning = true
            settingsManager.setMonitorRunning(true)
            startMonitorService()
        }
    }

    DisposableEffect(monitorRunningPersisted) {
        if (!monitorRunningPersisted) return@DisposableEffect onDispose { }

        val serviceIntent = Intent(context, FileMonitorService::class.java)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isMonitorRunning = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMonitorRunning = false
                scope.launch { settingsManager.setMonitorRunning(false) }
            }
        }

        val bound = context.bindService(serviceIntent, conn, 0)
        if (bound) {
            onDispose { context.unbindService(conn) }
        } else {
            isMonitorRunning = false
            scope.launch { settingsManager.setMonitorRunning(false) }
            onDispose { }
        }
    }

    var useBlur by remember { mutableStateOf(settingsManager.getUseBlurSync()) }
    LaunchedEffect(Unit) {
        settingsManager.useBlur.collect { useBlur = it }
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val backdrop = rememberLutistaBlurBackdrop(useBlur)
    val barColor = backdrop.getAppBarColor()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.lutistaBlurEffect(backdrop),
                color = barColor,
                title = "主页",
                largeTitle = "主页",
                scrollBehavior = scrollBehavior
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
            bottom = 12.dp,
            start = 12.dp,
            end = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatusSection(
                isMonitorRunning = isMonitorRunning,
                processedCount = processedCount,
                isCameraMode = isCameraMode,
                onMonitorToggle = {
                    if (!isMonitorRunning) {
                        val noFilter = selectedFilterIndex == 0
                        val noWatermark = selectedWatermarkIndex == 0
                        if (noFilter && noWatermark) {
                            Toast.makeText(context, "需要至少应用一个效果", Toast.LENGTH_SHORT).show()
                        } else if (monitorPath.isEmpty()) {
                            Toast.makeText(context, "请选择要监控的文件夹", Toast.LENGTH_SHORT).show()
                            pendingStartAfterPathSelect = true
                            onMonitorFolderClick()
                        } else {
                            isMonitorRunning = true
                            scope.launch {
                                settingsManager.setMonitorRunning(true)
                                startMonitorService()
                            }
                        }
                    } else {
                        isMonitorRunning = false
                        pendingStartAfterPathSelect = false
                        scope.launch {
                            settingsManager.setMonitorRunning(false)
                            stopMonitorService()
                        }
                    }
                },
                onCameraModeChange = { isCameraMode = it },
                onCameraClick = { /* TODO: 打开联机页面 */ },
                launchPicker = launchPicker
            )
        }

        item {
            MonitorSettingsCard(
                isMonitorRunning = isMonitorRunning,
                onlyNewFiles = onlyNewFiles,
                onOnlyNewFilesChange = {
                    onlyNewFiles = it
                    scope.launch {
                        settingsManager.setOnlyNewFiles(it)
                        if (isMonitorRunning && monitorPath.isNotEmpty()) {
                            startMonitorService()
                        }
                    }
                },
                onMonitorFolderClick = onMonitorFolderClick,
                onOutputFolderClick = onOutputFolderClick,
                monitorPath = monitorPath,
                outputPath = outputPath,
                filterOptions = filterOptions,
                selectedFilterIndex = selectedFilterIndex,
                onFilterSelected = { idx ->
                    selectedFilterIndex = idx
                    scope.launch {
                        if (idx == 0) {
                            settingsManager.setGlobalFilterId(0L)
                        } else {
                            val filter = filtersFromDb.getOrNull(idx - 1)
                            if (filter != null) settingsManager.setGlobalFilterId(filter.id)
                        }
                    }
                },
                watermarkOptions = watermarkOptions,
                selectedWatermarkIndex = selectedWatermarkIndex,
                onWatermarkSelected = { idx ->
                    selectedWatermarkIndex = idx
                    scope.launch {
                        if (idx == 0) {
                            settingsManager.setGlobalWatermarkId("none")
                        } else {
                            val wm = watermarksFromDb.getOrNull(idx - 1)
                            if (wm != null) settingsManager.setGlobalWatermarkId(wm.id.toString())
                        }
                    }
                }
            )
        }
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
                        text = if (isMonitorRunning) "已处理 $processedCount 张图片" else "点击此处开启服务",
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
    isMonitorRunning: Boolean = false,
    onlyNewFiles: Boolean,
    onOnlyNewFilesChange: (Boolean) -> Unit,
    onMonitorFolderClick: () -> Unit,
    onOutputFolderClick: () -> Unit,
    monitorPath: String = "",
    outputPath: String = "",
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
                summary = if (isMonitorRunning) "服务运行中，不可更改" else "开启后只处理监控文件夹中新增的图片",
                checked = onlyNewFiles,
                enabled = !isMonitorRunning,
                onCheckedChange = onOnlyNewFilesChange
            )
            ArrowPreference(
                title = "监控文件夹",
                summary = if (monitorPath.isNotEmpty()) decodeSafPath(monitorPath) else "点击设置监控文件夹",
                enabled = !isMonitorRunning,
                onClick = if (isMonitorRunning) ({}) else onMonitorFolderClick
            )

            ArrowPreference(
                title = "输出文件夹",
                summary = if (outputPath.isNotEmpty()) decodeSafPath(outputPath) else "Pictures/Lutista",
                enabled = !isMonitorRunning,
                onClick = if (isMonitorRunning) ({}) else onOutputFolderClick
            )

            OverlaySpinnerPreference(
                title = "滤镜选择",
                summary = "选择要应用的滤镜",
                enabled = !isMonitorRunning,
                items = filterOptions,
                selectedIndex = selectedFilterIndex,
                onSelectedIndexChange = if (isMonitorRunning) ({ _ -> }) else onFilterSelected
            )

            OverlaySpinnerPreference(
                title = "边框水印选择",
                summary = "选择要应用的边框水印",
                enabled = !isMonitorRunning,
                items = watermarkOptions,
                selectedIndex = selectedWatermarkIndex,
                onSelectedIndexChange = if (isMonitorRunning) ({ _ -> }) else onWatermarkSelected
            )
        }
    }
}

private fun decodeSafPath(uri: String): String {
    if (!uri.startsWith("content://")) return uri.substringAfterLast('/').ifEmpty { uri }
    val path = Uri.decode(uri.substringAfterLast("tree/").substringBefore('?'))
    val trimmed = path.removePrefix("primary:").removePrefix("/")
    return if (trimmed.length > 30) "...${trimmed.takeLast(28)}" else trimmed
}
