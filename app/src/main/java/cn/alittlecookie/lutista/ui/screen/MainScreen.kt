package cn.alittlecookie.lutista.ui.screen

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import cn.alittlecookie.lutista.gpu.VulkanGpuEngine
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
}

@Composable
fun MainScreen() {
    val vulkanGpuEngine: VulkanGpuEngine = koinInject()
    val context = LocalContext.current

    var showVulkanDialog by remember { mutableStateOf(false) }
    var returnToRecipeTab by remember { mutableStateOf(false) }
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

    val entryProvider = remember {
        entryProvider<NavKey> {
            entry(Screen.Main) {
                MainTabScreen(
                    onPhotoSelected = { uris ->
                        backStack.add(Screen.PhotoProcess(uris))
                    },
                    launchPicker = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onEditFilter = { filterId ->
                        backStack.add(Screen.FilterEditor(filterId))
                    },
                    onEditWatermark = { watermarkId ->
                        backStack.add(Screen.WatermarkEditor(watermarkId))
                    },
                    initialPage = if (returnToRecipeTab) { returnToRecipeTab = false; 1 } else 0,
                    recipeSubTab = recipeSubTab,
                    onRecipeSubTabChange = { recipeSubTab = it }
                )
            }
            entry<Screen.PhotoProcess> { screen ->
                PhotoProcessScreen(
                    photoUris = screen.uris,
                    onBackClick = { backStack.removeLast() },
                    onProcessComplete = { backStack.removeLast() },
                    onContinueProcessing = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
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
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = { backStack.removeLast() }
    )
}
