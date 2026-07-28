package io.github.thedayapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class TheDayTab {
    DAYS,
    CATEGORIES,
    NEW,
    SETTINGS,
}

@Composable
fun TheDayBottomBar(
    selectedTab: TheDayTab,
    onDaysClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onNewClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding(),
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
            )
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                NavigationBarItem(
                    selected = selectedTab == TheDayTab.DAYS,
                    onClick = onDaysClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = "日子",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == TheDayTab.CATEGORIES,
                    onClick = onCategoriesClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = "分类",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == TheDayTab.NEW,
                    onClick = onNewClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = "新建",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == TheDayTab.SETTINGS,
                    onClick = onSettingsClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = itemColors,
                )
            }
        }
    }
}