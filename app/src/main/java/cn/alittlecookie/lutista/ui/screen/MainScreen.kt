package cn.alittlecookie.lutista.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import cn.alittlecookie.lutista.gpu.VulkanGpuEngine
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.ui.theme.LutistaTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

sealed interface Screen : NavKey {
    data object Main : Screen
    data class PhotoProcess(val uris: List<String>) : Screen
    data class FilterEditor(val filterId: Long) : Screen
    data class WatermarkEditor(val watermarkId: Long) : Screen
    data object ThemeSettings : Screen
}

@Composable
fun MainScreen() {
    val settingsManager: SettingsManager = koinInject()

    var themeMode by remember { mutableStateOf(settingsManager.getThemeModeSync()) }
    var useMonet by remember { mutableStateOf(settingsManager.getUseMonetSync()) }
    var useDynamicColor by remember { mutableStateOf(settingsManager.getUseDynamicColorSync()) }
    var seedColor by remember { mutableStateOf(settingsManager.getSeedColorSync()) }
    var paletteStyle by remember { mutableStateOf(settingsManager.getPaletteStyleSync()) }
    var colorSpec by remember { mutableStateOf(settingsManager.getColorSpecSync()) }

    LaunchedEffect(Unit) {
        settingsManager.themeMode.collect { themeMode = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.useMonet.collect { useMonet = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.useDynamicColor.collect { useDynamicColor = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.seedColor.collect { seedColor = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.paletteStyle.collect { paletteStyle = it }
    }
    LaunchedEffect(Unit) {
        settingsManager.colorSpec.collect { colorSpec = it }
    }

    LutistaTheme(
        themeMode = themeMode,
        useMonet = useMonet,
        useDynamicColor = useDynamicColor,
        seedColor = seedColor,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec
    ) {
        MainScreenContent()
    }
}

@Composable
private fun MainScreenContent() {
    val vulkanGpuEngine: VulkanGpuEngine = koinInject()
    val context = LocalContext.current

    var showVulkanDialog by remember { mutableStateOf(false) }
    var returnToRecipeTab by remember { mutableStateOf(false) }
    var returnToSettingsTab by remember { mutableStateOf(false) }
    var recipeSubTab by remember { mutableIntStateOf(0) }

    val backStack = remember { mutableStateListOf<NavKey>(Screen.Main) }

    LaunchedEffect(Unit) {
        while (!vulkanGpuEngine.initialized) {
            kotlinx.coroutines.delay(100)
        }
        if (!vulkanGpuEngine.isAvailable) {
            showVulkanDialog = true
        }
    }

    if (showVulkanDialog) {
        WindowDialog(show = true, onDismissRequest = { showVulkanDialog = false }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("设备不支持", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface)
                Text("您的设备不支持 Vulkan GPU 加速。\n请下载支持 CPU 处理的版本。",
                    fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center, lineHeight = 20.sp)
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/qh7574/Lutista-Android")))
                }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)) {
                    Text("下载 CPU 版本", color = Color.White)
                }
                Button(onClick = { showVulkanDialog = false }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant)) {
                    Text("取消", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        return
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            backStack.add(Screen.PhotoProcess(uris.map { it.toString() }))
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op: ACCESS_MEDIA_LOCATION is silently granted */ }

    fun launchPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Request all required permissions at startup
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.POST_NOTIFICATIONS)

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            notificationPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    val settingsManager: SettingsManager = koinInject()
    val scope = rememberCoroutineScope()

    val monitorFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch { settingsManager.setMonitorPath(it.toString()) }
        }
    }

    val outputFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { settingsManager.setOutputPath(it.toString()) }
        }
    }

    val entryProvider = remember {
        entryProvider<NavKey> {
            entry(Screen.Main) {
                MainTabScreen(
                    onPhotoSelected = { uris ->
                        backStack.add(Screen.PhotoProcess(uris))
                    },
                    launchPicker = { launchPicker() },
                    onEditFilter = { filterId ->
                        backStack.add(Screen.FilterEditor(filterId))
                    },
                    onEditWatermark = { watermarkId ->
                        backStack.add(Screen.WatermarkEditor(watermarkId))
                    },
                    onMonitorFolderClick = { monitorFolderLauncher.launch(null) },
                    onOutputFolderClick = { outputFolderLauncher.launch(null) },
                    initialPage = when {
                        returnToRecipeTab -> { returnToRecipeTab = false; 1 }
                        returnToSettingsTab -> { returnToSettingsTab = false; 2 }
                        else -> 0
                    },
                    recipeSubTab = recipeSubTab,
                    onRecipeSubTabChange = { recipeSubTab = it },
                    onThemeSettingsClick = { backStack.add(Screen.ThemeSettings) }
                )
            }
            entry<Screen.PhotoProcess> { screen ->
                PhotoProcessScreen(
                    photoUris = screen.uris,
                    onBackClick = { backStack.removeLast() },
                    onProcessComplete = { backStack.removeLast() },
                    onContinueProcessing = { launchPicker() }
                )
            }
            entry<Screen.FilterEditor> { screen ->
                FilterEditorScreen(
                    filterId = screen.filterId,
                    onBackClick = {
                        returnToRecipeTab = true
                        backStack.removeLast()
                    }
                )
            }
            entry<Screen.WatermarkEditor> { screen ->
                WatermarkEditorScreen(
                    watermarkId = screen.watermarkId,
                    onBackClick = {
                        returnToRecipeTab = true
                        backStack.removeLast()
                    }
                )
            }
            entry<Screen.ThemeSettings> {
                ThemeSettingsPage(
                    onBackClick = {
                        returnToSettingsTab = true
                        backStack.removeLast()
                    }
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = {
            if (backStack.lastOrNull() is Screen.ThemeSettings) {
                returnToSettingsTab = true
            }
            backStack.removeLast()
        }
    )
}
