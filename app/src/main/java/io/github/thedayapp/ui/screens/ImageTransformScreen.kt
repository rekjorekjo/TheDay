package io.github.thedayapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.ImagePlacementTarget
import io.github.thedayapp.data.ImageTransform
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.ui.media.EventImageCardType
import io.github.thedayapp.ui.media.TransformableLocalImageViewport
import io.github.thedayapp.ui.media.adaptiveEventImagePreviewAspectRatio
import io.github.thedayapp.ui.media.rememberLocalImageBitmap
import io.github.thedayapp.util.DateFormatting
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTransformScreen(
    event: DayEvent,
    today: LocalDate,
    target: ImagePlacementTarget,
    onBack: () -> Unit,
    onSave: (ImageTransform) -> Unit,
) {
    val image = event.backgroundImage
    val imageBitmap = rememberLocalImageBitmap(
        image = image,
        maxDecodeLongEdgePx = 2048,
    )

    var workingTransform by remember(
        event.id,
        image?.fileName,
        target,
    ) {
        mutableStateOf(
            image?.transformFor(target) ?: ImageTransform(),
        )
    }

    LaunchedEffect(image?.fileName, target) {
        workingTransform = image?.transformFor(target) ?: ImageTransform()
    }

    val cardType = when (target) {
        ImagePlacementTarget.HOME -> EventImageCardType.HOME_HERO
        ImagePlacementTarget.DETAIL -> EventImageCardType.DETAIL
    }
    val previewAspectRatio = image
        ?.let { adaptiveEventImagePreviewAspectRatio(it, cardType) }
        ?: 1f
    val locale = Locale.getDefault()
    val delta = DayMath.signedDays(event, today)
    val displayDate = DayMath.effectiveDate(event, today)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        if (target == ImagePlacementTarget.HOME) {
                            "调整首页图片"
                        } else {
                            "调整详情图片"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "单指拖动，双指缩放",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "这里只调整当前页面的构图，不会修改或重复压缩原图。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewAspectRatio.coerceIn(0.6f, 1.65f))
                    .heightIn(min = 210.dp, max = 520.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    )
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (imageBitmap != null) {
                    TransformableLocalImageViewport(
                        bitmap = imageBitmap,
                        transform = workingTransform,
                        onTransformChange = {
                            workingTransform = it
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.14f),
                                        Color.Black.copy(alpha = 0.32f),
                                        Color.Black.copy(alpha = 0.66f),
                                    ),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = event.title,
                                style = if (target == ImagePlacementTarget.HOME) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.headlineMedium
                                },
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            if (delta != 0L) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (delta > 0L) "还有" else "已经",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.84f),
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = if (delta == 0L) "今天" else abs(delta).toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                        )
                        if (delta != 0L) {
                            Text(
                                text = "天",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.84f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = DateFormatting.longDate(displayDate, locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.84f),
                        )
                    }
                } else {
                    Text(
                        text = "图片暂时无法读取",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "缩放 ${"%.2f".format(workingTransform.zoom)}×",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        workingTransform = ImageTransform()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("重置")
                }
                Button(
                    onClick = {
                        onSave(workingTransform.normalized())
                    },
                    modifier = Modifier.weight(1f),
                    enabled = imageBitmap != null,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("保存")
                }
            }
        }
    }
}
