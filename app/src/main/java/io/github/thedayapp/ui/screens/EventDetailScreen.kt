package io.github.thedayapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.RepeatMode
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.sharing.MemoryImagePalette
import io.github.thedayapp.ui.components.EventMemoryImageSheet
import io.github.thedayapp.ui.media.localImageAlignment
import io.github.thedayapp.ui.media.rememberLocalImageBitmap
import io.github.thedayapp.util.DateFormatting
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    event: DayEvent,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val delta = DayMath.signedDays(event, today)
    val displayDate = DayMath.effectiveDate(event, today)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmDelete by remember { mutableStateOf(false) }
    var showMemoryImageSheet by remember { mutableStateOf(false) }

    val imageReference = event.backgroundImage
    val imageBitmap = rememberLocalImageBitmap(
        image = imageReference,
        maxDecodeLongEdgePx = 1280,
    )
    val hasBackgroundImage = imageReference != null && imageBitmap != null

    val colorScheme = MaterialTheme.colorScheme
    val memoryImagePalette = remember(colorScheme) {
        MemoryImagePalette(
            background = colorScheme.background.toArgb(),
            surface = colorScheme.surface.toArgb(),
            primary = colorScheme.primary.toArgb(),
            primaryContainer = colorScheme.primaryContainer.toArgb(),
            onPrimary = colorScheme.onPrimary.toArgb(),
            onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb(),
            onSurface = colorScheme.onSurface.toArgb(),
            onSurfaceVariant = colorScheme.onSurfaceVariant.toArgb(),
            isDark = colorScheme.surface.luminance() < 0.5f,
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("日子详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    if (hasBackgroundImage && imageBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            alignment = localImageAlignment(imageReference!!),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.18f),
                                            Color.Black.copy(alpha = 0.38f),
                                            Color.Black.copy(alpha = 0.66f),
                                        ),
                                    ),
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                    ),
                                ),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = event.title,
                                modifier = Modifier.weight(
                                    weight = 1f,
                                    fill = false,
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (hasBackgroundImage) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                            if (delta != 0L) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (delta > 0L) "还有" else "已经",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (hasBackgroundImage) {
                                        Color.White.copy(alpha = 0.84f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        if (delta == 0L) {
                            Text(
                                text = "今天",
                                style = MaterialTheme.typography.displayLarge,
                                color = if (hasBackgroundImage) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = abs(delta).toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = if (hasBackgroundImage) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "天",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (hasBackgroundImage) {
                                        Color.White.copy(alpha = 0.84f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "${DateFormatting.longDate(displayDate, locale)} · " +
                                DateFormatting.weekday(displayDate, locale),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hasBackgroundImage) {
                                Color.White.copy(alpha = 0.84f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    DetailRow(
                        icon = Icons.Rounded.CalendarMonth,
                        label = "原始日期",
                        value = DateFormatting.longDate(event.date, locale),
                    )
                    HorizontalDivider()
                    DetailRow(
                        icon = Icons.Rounded.Repeat,
                        label = "重复",
                        value = if (event.repeatMode == RepeatMode.YEARLY) "每年" else "不重复",
                    )
                    if (event.category.isNotBlank()) {
                        HorizontalDivider()
                        DetailRow(
                            icon = Icons.Rounded.Label,
                            label = "分类",
                            value = event.category,
                        )
                    }
                    HorizontalDivider()
                    DetailRow(
                        icon = Icons.Rounded.Notifications,
                        label = "提醒",
                        value = reminderText(event.reminderDaysBefore),
                    )
                }
            }

            if (event.note.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notes,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text("备注", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = event.note,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailActionButton(
                    text = "置顶",
                    icon = Icons.Rounded.VerticalAlignTop,
                    actionDescription = if (event.isPinned) "取消置顶" else "置顶",
                    onClick = onTogglePinned,
                    selected = event.isPinned,
                    modifier = Modifier.weight(1f),
                )
                DetailActionButton(
                    text = "分享纪念图",
                    icon = Icons.Rounded.Share,
                    actionDescription = "分享纪念图",
                    onClick = {
                        showMemoryImageSheet = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个日子？") },
            text = { Text("此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showMemoryImageSheet) {
        EventMemoryImageSheet(
            event = event,
            today = today,
            palette = memoryImagePalette,
            onDismissRequest = {
                showMemoryImageSheet = false
            },
            onSaved = {
                scope.launch {
                    snackbarHostState.showSnackbar("已保存到相册")
                }
            },
            onError = { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            },
        )
    }
}

@Composable
private fun DetailActionButton(
    text: String,
    icon: ImageVector,
    actionDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .semantics {
                contentDescription = actionDescription
            },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
    }
}

private fun reminderText(daysBefore: Int?): String = when (daysBefore) {
    null -> "不提醒"
    0 -> "当天"
    1 -> "提前 1 天"
    3 -> "提前 3 天"
    7 -> "提前 7 天"
    else -> "提前 $daysBefore 天"
}
