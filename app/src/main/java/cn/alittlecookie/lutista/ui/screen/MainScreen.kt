package cn.alittlecookie.lutista.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import cn.alittlecookie.lutista.gpu.VulkanGpuEngine
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen() {
    val vulkanGpuEngine: VulkanGpuEngine = koinInject()
    val context = LocalContext.current

    var showVulkanDialog by remember { mutableStateOf(false) }
    var showPhotoProcess by remember { mutableStateOf(false) }
    var selectedPhotoUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFilterEditor by remember { mutableStateOf(false) }
    var editingFilterId by remember { mutableStateOf(0L) }
    var returnToRecipeTab by remember { mutableStateOf(false) }

    // Check Vulkan availability on first composition
    LaunchedEffect(Unit) {
        if (!vulkanGpuEngine.isAvailable) {
            showVulkanDialog = true
        }
    }

    // Vulkan not available dialog
    if (showVulkanDialog) {
        VulkanNotAvailableDialog(
            onDownloadClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qh7574/Lutista-Android"))
                context.startActivity(intent)
            },
            onDismiss = { showVulkanDialog = false }
        )
        return
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotoUris = uris.map { it.toString() }
            showPhotoProcess = true
        }
    }

    if (showFilterEditor) {
        FilterEditorScreen(
            filterId = editingFilterId,
            onBackClick = {
                showFilterEditor = false
                returnToRecipeTab = true
            }
        )
    } else if (showPhotoProcess && selectedPhotoUris.isNotEmpty()) {
        PhotoProcessScreen(
            photoUris = selectedPhotoUris,
            onBackClick = { showPhotoProcess = false },
            onProcessComplete = { result ->
                showPhotoProcess = false
            },
            onContinueProcessing = {
                showPhotoProcess = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    } else {
        MainTabScreen(
            onPhotoSelected = { uris ->
                selectedPhotoUris = uris
                showPhotoProcess = true
            },
            launchPicker = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onEditFilter = { filterId ->
                editingFilterId = filterId
                showFilterEditor = true
            },
            initialPage = if (returnToRecipeTab) {
                returnToRecipeTab = false
                1
            } else 0
        )
    }
}

@Composable
private fun VulkanNotAvailableDialog(
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(24.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "设备不支持",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "您的设备不支持 Vulkan GPU 加速。\n请下载支持 CPU 处理的版本。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "下载 CPU 版本",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "取消",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}
