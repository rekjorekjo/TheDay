package io.github.thedayapp.ui.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun rememberSingleImagePickerLauncher(
    onImagePicked: (Uri) -> Unit,
): () -> Unit {
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            currentOnImagePicked(uri)
        }
    }

    return {
        launcher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }
}