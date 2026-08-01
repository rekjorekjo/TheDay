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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                    // Material 3 animates the app-bar container color. When
                    // switching light/dark mode that left the old color on
                    // screen for several frames. Draw the background directly
                    // on the modifier and keep the internal container clear.
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PaletteStyle.entries.forEach { style ->
                        PaletteOption(
                            style = style,
                            selected = state.settings.paletteStyle == style,
                            onClick = {
                                state.updateSettings(state.settings.copy(paletteStyle = style))
                            },
                        )
                    }
                }
            }

            SettingsCard(
                icon = Icons.Rounded.Sort,
                title = "事件显示",
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
                HorizontalDivider()
                SwitchRow(
                    title = "显示正数日",
                    description = "关闭后，首页和小组件只显示倒数日和今天",
                    checked = state.settings.showPastEvents,
                    onCheckedChange = {
                        state.updateSettings(state.settings.copy(showPastEvents = it))
                    },
                )
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
                    Text("清空")
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

@Composable
private fun PaletteOption(
    style: PaletteStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = palettePreviewColor(style)
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .padding(4.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (style) {
                PaletteStyle.MIDNIGHT -> "暮蓝"
                PaletteStyle.CINNABAR -> "朱砂"
                PaletteStyle.PINE -> "松烟"
                PaletteStyle.ANTIQUE_GOLD -> "古金"
                PaletteStyle.BLOOM_PETAL -> "花瓣"
                PaletteStyle.BLOOM_MIST -> "雾蓝"
                PaletteStyle.BLOOM_VERDANT -> "草木"
                PaletteStyle.BLOOM_STONE -> "暖石"
                PaletteStyle.BLOOM_WHEAT -> "麦穗"
                PaletteStyle.BLOOM_INK -> "水墨"
                PaletteStyle.BLOOM_AMBER -> "琥珀"
                PaletteStyle.BLOOM_LAPIS -> "青金"
                PaletteStyle.BLOOM_RIPPLE -> "涟漪"
                PaletteStyle.BLOOM_CINNABAR -> "丹红"
                PaletteStyle.BLOOM_SAGE -> "鼠尾草"
                PaletteStyle.BLOOM_SPRING -> "紫语"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
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
