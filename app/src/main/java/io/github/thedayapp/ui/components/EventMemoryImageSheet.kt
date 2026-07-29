package io.github.thedayapp.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.thedayapp.R
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.sharing.EventMemoryImageRenderer
import io.github.thedayapp.sharing.EventShareActions
import io.github.thedayapp.sharing.MemoryImagePalette
import io.github.thedayapp.sharing.MemoryImageTemplatePreferences
import io.github.thedayapp.sharing.next
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventMemoryImageSheet(
    event: DayEvent,
    today: LocalDate,
    palette: MemoryImagePalette,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val initialTemplate = remember(context) {
        MemoryImageTemplatePreferences.load(context)
    }
    var selectedTemplate by rememberSaveable {
        mutableStateOf(initialTemplate)
    }

    var renderedBitmap by remember(event, today, palette) {
        mutableStateOf<Bitmap?>(null)
    }
    var isGenerating by remember(event, today, palette) {
        mutableStateOf(false)
    }
    var generationFailed by remember(event, today, palette) {
        mutableStateOf(false)
    }
    var isSharing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val retiredBitmaps = remember {
        mutableListOf<Bitmap>()
    }

    fun saveBitmap(bitmap: Bitmap) {
        if (
            isSaving ||
            isGenerating ||
            bitmap.isRecycled
        ) {
            return
        }

        scope.launch {
            isSaving = true

            val result = EventShareActions.saveImageToGallery(
                context = context,
                bitmap = bitmap,
            )

            isSaving = false

            if (result.isSuccess) {
                onSaved()
            } else {
                onError("图片保存失败")
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val bitmap = renderedBitmap

        if (
            granted &&
            bitmap != null &&
            !bitmap.isRecycled &&
            !isGenerating
        ) {
            saveBitmap(bitmap)
        } else if (!granted) {
            onError("需要存储权限才能保存到相册")
        }
    }

    LaunchedEffect(
        event,
        today,
        palette,
        selectedTemplate,
    ) {
        isGenerating = true
        generationFailed = false

        val result = EventMemoryImageRenderer.render(
            context = context,
            event = event,
            today = today,
            palette = palette,
            template = selectedTemplate,
        )

        val generatedBitmap = result.getOrNull()

        try {
            ensureActive()
        } catch (exception: CancellationException) {
            if (
                generatedBitmap != null &&
                !generatedBitmap.isRecycled
            ) {
                generatedBitmap.recycle()
            }
            throw exception
        }

        if (generatedBitmap != null) {
            val oldBitmap = renderedBitmap

            renderedBitmap = generatedBitmap
            isGenerating = false

            if (
                oldBitmap != null &&
                oldBitmap !== generatedBitmap &&
                !oldBitmap.isRecycled
            ) {
                retiredBitmaps.add(oldBitmap)

                scope.launch {
                    delay(300L)

                    if (!oldBitmap.isRecycled) {
                        oldBitmap.recycle()
                    }

                    retiredBitmaps.remove(oldBitmap)
                }
            }
        } else {
            isGenerating = false
            generationFailed = renderedBitmap == null
            onError("图片生成失败")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activeBitmap = renderedBitmap

            if (
                activeBitmap != null &&
                !activeBitmap.isRecycled
            ) {
                activeBitmap.recycle()
            }

            retiredBitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            retiredBitmaps.clear()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val sheetMaxWidth = maxWidth
            val previewMaxHeight =
                (maxHeight - 190.dp)
                    .coerceAtLeast(180.dp)
            val currentBitmap = renderedBitmap

            val imageAspectRatio =
                if (
                    currentBitmap != null &&
                    !currentBitmap.isRecycled
                ) {
                    currentBitmap.width.toFloat() /
                        currentBitmap.height.toFloat()
                } else {
                    3f / 4f
                }

            val previewWidth = minOf(
                sheetMaxWidth - 40.dp,
                previewMaxHeight * imageAspectRatio,
            )
            val previewHeight =
                previewWidth / imageAspectRatio

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.memory_image_title,
                        ),
                        modifier = Modifier.align(
                            Alignment.Center,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val nextTemplate =
                                selectedTemplate.next()

                            selectedTemplate = nextTemplate

                            MemoryImageTemplatePreferences.save(
                                context = context,
                                template = nextTemplate,
                            )
                        },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(
                                R.string.memory_image_change_template,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                when {
                    currentBitmap != null &&
                        !currentBitmap.isRecycled -> {
                        Box(
                            modifier = Modifier
                                .width(previewWidth)
                                .height(previewHeight)
                                .clip(RoundedCornerShape(18.dp)),
                        ) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "纪念图预览",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Fit,
                            )

                            if (isGenerating) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            MaterialTheme.colorScheme.scrim.copy(
                                                alpha = 0.12f,
                                            ),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp,
                                    )
                                }
                            }
                        }
                    }

                    isGenerating -> {
                        Box(
                            modifier = Modifier
                                .width(previewWidth)
                                .height(previewHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    MaterialTheme.colorScheme
                                        .surfaceContainerHigh,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    generationFailed -> {
                        Box(
                            modifier = Modifier
                                .width(previewWidth)
                                .height(previewHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    MaterialTheme.colorScheme
                                        .surfaceContainerHigh,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "纪念图生成失败",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                val hasValidBitmap =
                    currentBitmap != null &&
                        !currentBitmap.isRecycled &&
                        !isGenerating &&
                        !generationFailed

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val bitmap = renderedBitmap

                            if (
                                bitmap != null &&
                                !bitmap.isRecycled &&
                                !isSharing &&
                                !isGenerating
                            ) {
                                scope.launch {
                                    isSharing = true

                                    val result =
                                        EventShareActions.shareEventImage(
                                            context = context,
                                            bitmap = bitmap,
                                        )

                                    isSharing = false

                                    if (result.isFailure) {
                                        onError("无法打开分享面板")
                                    }
                                }
                            }
                        },
                        enabled =
                            hasValidBitmap &&
                                !isSharing &&
                                !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (isSharing) {
                                "正在分享"
                            } else {
                                "分享纪念图"
                            },
                        )
                    }

                    Button(
                        onClick = {
                            val bitmap = renderedBitmap
                                ?: return@Button

                            if (
                                bitmap.isRecycled ||
                                isGenerating
                            ) {
                                return@Button
                            }

                            val needsLegacyPermission =
                                Build.VERSION.SDK_INT <=
                                    Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission
                                            .WRITE_EXTERNAL_STORAGE,
                                    ) != PackageManager.PERMISSION_GRANTED

                            if (needsLegacyPermission) {
                                storagePermissionLauncher.launch(
                                    Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE,
                                )
                            } else {
                                saveBitmap(bitmap)
                            }
                        },
                        enabled =
                            hasValidBitmap &&
                                !isSharing &&
                                !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (isSaving) {
                                "正在保存"
                            } else {
                                "保存到相册"
                            },
                        )
                    }
                }
            }
        }
    }
}
