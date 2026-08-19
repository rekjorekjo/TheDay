package io.github.thedayapp.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thedayapp.R
import io.github.thedayapp.data.PaletteStyle
import io.github.thedayapp.data.SortDirection
import io.github.thedayapp.data.SortMode
import io.github.thedayapp.data.ThemeMode
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.ui.theme.palettePreviewColor
import io.github.thedayapp.util.DateFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: TheDayState,
    bottomBar: @Composable () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    Scaffold(
        topBar = {
            val topBarBackground = MaterialTheme.colorScheme.background
            val topBarContent = MaterialTheme.colorScheme.onBackground

            TopAppBar(
                modifier = Modifier.background(topBarBackground),
                colors = TopAppBarDefaults.topAppBarColors(
                    // Material 3 会动画过渡顶部栏容器色；主题切换时直接在 Modifier 绘制背景，避免残留旧配色。
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = topBarContent,
                    navigationIconContentColor = topBarContent,
                    actionIconContentColor = topBarContent,
                ),
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.about_the_day),
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(
                icon = Icons.Rounded.Palette,
                title = "外观",
            ) {
                Text(
                    "明暗模式",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = {
                                state.updateSettings(state.settings.copy(themeMode = mode))
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "主题配色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PaletteColorGridSelector(
                    selectedStyle = state.settings.paletteStyle,
                    onStyleSelected = { style ->
                        state.updateSettings(state.settings.copy(paletteStyle = style))
                    },
                )
            }

            SettingsCard(
                icon = Icons.Rounded.Sort,
                title = "\u6392\u5e8f",
            ) {
                SortMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) HorizontalDivider()
                    ChoiceRow(
                        label = when (mode) {
                            SortMode.SMART -> "智能排序"
                            SortMode.DATE -> "按日期"
                            SortMode.TITLE -> "按标题"
                            SortMode.CREATED -> "按创建时间"
                        },
                        description = when (mode) {
                            SortMode.SMART -> "置顶优先；倒数在前，正数在后"
                            SortMode.DATE -> "按事件日期排序"
                            SortMode.TITLE -> "按名称排序"
                            SortMode.CREATED -> "按创建时间排序"
                        },
                        selected = state.settings.sortMode == mode,
                        onClick = {
                            state.updateSettings(state.settings.copy(sortMode = mode))
                        },
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("排序方向", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (state.settings.sortMode) {
                                SortMode.SMART -> if (state.settings.sortDirection == SortDirection.ASCENDING) {
                                    "同组事件由近到远"
                                } else {
                                    "同组事件由远到近"
                                }
                                SortMode.DATE -> if (state.settings.sortDirection == SortDirection.ASCENDING) {
                                    "日期由早到晚"
                                } else {
                                    "日期由晚到早"
                                }
                                SortMode.TITLE -> if (state.settings.sortDirection == SortDirection.ASCENDING) {
                                    "名称正序"
                                } else {
                                    "名称倒序"
                                }
                                SortMode.CREATED -> if (state.settings.sortDirection == SortDirection.ASCENDING) {
                                    "最早创建优先"
                                } else {
                                    "最近创建优先"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val newDirection = if (state.settings.sortDirection == SortDirection.ASCENDING) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                            state.updateSettings(state.settings.copy(sortDirection = newDirection))
                        },
                    ) {
                        Text(if (state.settings.sortDirection == SortDirection.ASCENDING) "升序" else "降序")
                    }
                }
            }

            SettingsCard(
                icon = Icons.Rounded.Notifications,
                title = "提醒",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("提醒时间", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DateFormatting.time(
                                state.settings.reminderHour,
                                state.settings.reminderMinute,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    state.updateSettings(
                                        state.settings.copy(
                                            reminderHour = hour,
                                            reminderMinute = minute,
                                        ),
                                    )
                                },
                                state.settings.reminderHour,
                                state.settings.reminderMinute,
                                true,
                            ).show()
                        },
                    ) {
                        Text("调整")
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知权限", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (notificationGranted) "已允许" else "未允许，提醒不会显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!notificationGranted) {
                            OutlinedButton(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            ) {
                                Text("申请权限")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.events.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("清空全部事件")
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部事件？") },
            text = { Text("所有倒数日、正数日和对应提醒都会被删除，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.clearAllEvents()
                        confirmClear = false
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    trailingAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                trailingAction?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

private val PaletteTileShape = RoundedCornerShape(10.dp)

@Composable
private fun PaletteColorGridSelector(
    selectedStyle: PaletteStyle,
    onStyleSelected: (PaletteStyle) -> Unit,
) {
    val rows = PaletteStyle.entries.chunked(4)
    val horizontalGap = 8.dp
    val verticalGap = 8.dp

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val tileWidth = ((maxWidth.value - horizontalGap.value * 3f) / 4f)
            .coerceAtMost(80f)
            .coerceAtLeast(54f)
            .dp
        val tileHeight = (tileWidth.value * 0.62f).dp
        val gridWidth = (tileWidth.value * 4f + horizontalGap.value * 3f).dp

        Column(
            modifier = Modifier.width(gridWidth),
            verticalArrangement = Arrangement.spacedBy(verticalGap),
        ) {
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { style ->
                        PaletteColorTile(
                            style = style,
                            selected = selectedStyle == style,
                            width = tileWidth,
                            height = tileHeight,
                            onClick = { onStyleSelected(style) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteColorTile(
    style: PaletteStyle,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val color = palettePreviewColor(style)
    val glowPadding = if (selected) 4.dp else 0.dp
    val glowColor = if (selected) {
        color.copy(alpha = 0.36f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(PaletteTileShape)
            .background(glowColor)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "\u4e3b\u9898\u914d\u8272\uff1a${paletteName(style)}"
            }
            .padding(glowPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(PaletteTileShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.background(Color.White.copy(alpha = 0.18f))
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.34f),
                            shape = PaletteTileShape,
                        )
                    },
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = paletteName(style),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun paletteName(style: PaletteStyle): String = when (style) {
    PaletteStyle.MIDNIGHT -> "\u66ae\u84dd"
    PaletteStyle.CINNABAR -> "\u6731\u7802"
    PaletteStyle.PINE -> "\u677e\u70df"
    PaletteStyle.ANTIQUE_GOLD -> "\u53e4\u91d1"
    PaletteStyle.BLOOM_PETAL -> "\u82b1\u74f7"
    PaletteStyle.BLOOM_MIST -> "\u96fe\u84dd"
    PaletteStyle.BLOOM_VERDANT -> "\u8349\u6728"
    PaletteStyle.BLOOM_STONE -> "\u6696\u77f3"
    PaletteStyle.BLOOM_WHEAT -> "\u9ea6\u7a57"
    PaletteStyle.BLOOM_INK -> "\u6c34\u58a8"
    PaletteStyle.BLOOM_AMBER -> "\u7425\u73c0"
    PaletteStyle.BLOOM_LAPIS -> "\u9752\u91d1"
    PaletteStyle.BLOOM_RIPPLE -> "\u6d9f\u6f2a"
    PaletteStyle.BLOOM_CINNABAR -> "\u4e39\u7ea2"
    PaletteStyle.BLOOM_SAGE -> "\u9f20\u5c3e\u8349"
    PaletteStyle.BLOOM_SPRING -> "\u7d2b\u8bed"
}

@Composable
private fun ChoiceRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
