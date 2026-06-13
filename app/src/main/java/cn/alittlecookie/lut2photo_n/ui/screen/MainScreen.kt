package cn.alittlecookie.lut2photo_n.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun MainScreen() {
    var showPhotoProcess by remember { mutableStateOf(false) }
    var selectedPhotoUris by remember { mutableStateOf<List<String>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotoUris = uris.map { it.toString() }
            showPhotoProcess = true
        }
    }

    if (showPhotoProcess && selectedPhotoUris.isNotEmpty()) {
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
            }
        )
    }
}
