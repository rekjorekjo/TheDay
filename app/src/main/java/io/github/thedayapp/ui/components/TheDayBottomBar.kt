package io.github.thedayapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class TheDayTab {
    DAYS,
    CATEGORIES,
    EXPORT,
    SETTINGS,
}

@Composable
fun TheDayBottomBar(
    selectedTab: TheDayTab,
    onDaysClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onExportClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding(),
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.16f,
                ),
            )

            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .selectableGroup(),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = 0,
                ),
            ) {
                TheDayNavigationItem(
                    selected =
                        selectedTab ==
                            TheDayTab.DAYS,
                    onClick = onDaysClick,
                    icon =
                        Icons.Rounded.CalendarMonth,
                    label = "日子",
                )

                TheDayNavigationItem(
                    selected =
                        selectedTab ==
                            TheDayTab.CATEGORIES,
                    onClick = onCategoriesClick,
                    icon =
                        Icons.Rounded.MenuBook,
                    label = "分类",
                )

                TheDayNavigationItem(
                    selected =
                        selectedTab ==
                            TheDayTab.EXPORT,
                    onClick = onExportClick,
                    icon =
                        Icons.Rounded.IosShare,
                    label = "导出",
                )

                TheDayNavigationItem(
                    selected =
                        selectedTab ==
                            TheDayTab.SETTINGS,
                    onClick = onSettingsClick,
                    icon =
                        Icons.Rounded.Settings,
                    label = "设置",
                )
            }
        }
    }
}

@Composable
private fun RowScope.TheDayNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme
                .onSurfaceVariant
        }

    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.13f)
        } else {
            Color.Transparent
        }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 68.dp)
                .clip(
                    RoundedCornerShape(18.dp),
                )
                .background(containerColor)
                .padding(
                    horizontal = 14.dp,
                    vertical = 5.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )

            Spacer(
                modifier = Modifier.height(2.dp),
            )

            Text(
                text = label,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}