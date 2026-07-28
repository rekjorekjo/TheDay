package io.github.thedayapp.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.thedayapp.data.LocalImageReference

internal enum class ImageFocusPreviewMode {
    EVENT_BACKGROUND,
    CATEGORY_COVER,
}

private data class FocusPreviewSpec(
    val label: String,
    val aspectRatio: Float,
)

@Composable
internal fun ImageFocusDialog(
    image: LocalImageReference,
    mode: ImageFocusPreviewMode,
    onDismiss: () -> Unit,
    onConfirm: (LocalImageReference) -> Unit,
) {
    var focusX by remember(image.fileName, image.focusX) {
        mutableStateOf(safeFocus(image.focusX))
    }

    var focusY by remember(image.fileName, image.focusY) {
        mutableStateOf(safeFocus(image.focusY))
    }

    val previewImage = image.copy(
        focusX = focusX,
        focusY = focusY,
    )

    val imageBitmap = rememberLocalImageBitmap(
        image = previewImage,
        maxDecodeLongEdgePx = 1536,
    )

    val previewSpecs = when (mode) {
        ImageFocusPreviewMode.EVENT_BACKGROUND -> listOf(
            FocusPreviewSpec(
                label = "首页主卡",
                aspectRatio = 1.65f,
            ),
            FocusPreviewSpec(
                label = "详情大卡",
                aspectRatio = 1.25f,
            ),
        )

        ImageFocusPreviewMode.CATEGORY_COVER -> listOf(
            FocusPreviewSpec(
                label = "分类书册",
                aspectRatio = 0.72f,
            ),
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = when (mode) {
                        ImageFocusPreviewMode.EVENT_BACKGROUND -> "调整背景位置"
                        ImageFocusPreviewMode.CATEGORY_COVER -> "调整封面位置"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = when (mode) {
                        ImageFocusPreviewMode.EVENT_BACKGROUND -> "调整后会同时用于首页主卡和详情大卡"
                        ImageFocusPreviewMode.CATEGORY_COVER -> "调整后会用于分类书册"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                previewSpecs.forEach { spec ->
                    FocusPreview(
                        spec = spec,
                        image = previewImage,
                        imageBitmap = imageBitmap,
                    )
                }

                Column {
                    Text(
                        text = "水平位置",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = focusX,
                        onValueChange = { focusX = it.coerceIn(0f, 1f) },
                        valueRange = 0f..1f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "左",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "右",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column {
                    Text(
                        text = "垂直位置",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = focusY,
                        onValueChange = { focusY = it.coerceIn(0f, 1f) },
                        valueRange = 0f..1f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "上",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "下",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            focusX = 0.5f
                            focusY = 0.5f
                        },
                    ) {
                        Text("居中")
                    }
                    Box(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onConfirm(
                                image.copy(
                                    focusX = focusX.coerceIn(0f, 1f),
                                    focusY = focusY.coerceIn(0f, 1f),
                                ),
                            )
                        },
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusPreview(
    spec: FocusPreviewSpec,
    image: LocalImageReference,
    imageBitmap: ImageBitmap?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(spec.aspectRatio),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alignment = localImageAlignment(image),
                    )
                } else {
                    Text(
                        text = "图片加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun safeFocus(value: Float): Float {
    return if (value.isFinite()) {
        value.coerceIn(0f, 1f)
    } else {
        0.5f
    }
}