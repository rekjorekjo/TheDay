package io.github.thedayapp.ui.screens

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
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.RepeatMode
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.ui.media.localImageAlignment
import io.github.thedayapp.ui.media.rememberLocalImageBitmap
import io.github.thedayapp.util.DateFormatting
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
    val locale = Locale.getDefault()
    val delta = DayMath.signedDays(event, today)
    val displayDate = DayMath.effectiveDate(event, today)
    var confirmDelete by remember { mutableStateOf(false) }

    val imageReference = event.backgroundImage
    val imageBitmap = rememberLocalImageBitmap(
        image = imageReference,
        maxDecodeLongEdgePx = 1280,
    )
    val hasBackgroundImage = imageReference != null && imageBitmap != null

    Scaffold(
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
                    IconButton(onClick = onTogglePinned) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignTop,
                            contentDescription = if (event.isPinned) "取消置顶" else "置顶",
                            tint = if (event.isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(24.dp))
                        if (delta == 0L) {
                            Text(
                                text = "今天",
                                style = MaterialTheme.typography.displayLarge,
                                color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Text(
                                text = if (delta > 0) "倒数" else "正数",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (hasBackgroundImage) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = abs(delta).toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "天",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (hasBackgroundImage) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "${DateFormatting.longDate(displayDate, locale)} · ${DateFormatting.weekday(displayDate, locale)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hasBackgroundImage) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("编辑")
                }
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
