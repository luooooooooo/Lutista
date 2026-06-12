package cn.alittlecookie.lut2photo_n.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun MainScreen() {
    var showPhotoProcess by remember { mutableStateOf(false) }
    var selectedPhotoUris by remember { mutableStateOf<List<String>>(emptyList()) }

    if (showPhotoProcess && selectedPhotoUris.isNotEmpty()) {
        PhotoProcessScreen(
            photoUris = selectedPhotoUris,
            onBackClick = { showPhotoProcess = false },
            onProcessComplete = { result ->
                // Handle process complete
                showPhotoProcess = false
            }
        )
    } else {
        MainTabScreen(
            onPhotoSelected = { uris ->
                selectedPhotoUris = uris
                showPhotoProcess = true
            }
        )
    }
}
