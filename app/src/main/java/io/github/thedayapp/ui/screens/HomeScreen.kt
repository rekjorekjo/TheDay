package io.github.thedayapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.ui.components.EventCard
import io.github.thedayapp.ui.components.HeroCard
import io.github.thedayapp.ui.components.TheDayMark
import io.github.thedayapp.util.DateFormatting
import java.util.Locale

private enum class HomeFilter {
    ALL,
    UPCOMING,
    PAST,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: TheDayState,
    onOpenEvent: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val locale = Locale.getDefault()
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val settingsVisible = state.events.filter {
        state.settings.showPastEvents || !DayMath.isPast(it, state.today)
    }
    val categories = settingsVisible
        .map { it.category }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    LaunchedEffect(categories, selectedCategory) {
        if (selectedCategory != null && selectedCategory !in categories) {
            selectedCategory = null
        }
    }

    val filtered = settingsVisible.filter { event ->
        val matchesTime = when (filter) {
            HomeFilter.ALL -> true
            HomeFilter.UPCOMING -> DayMath.isUpcoming(event, state.today)
            HomeFilter.PAST -> DayMath.isPast(event, state.today)
        }
        val matchesCategory = selectedCategory == null || event.category == selectedCategory
        matchesTime && matchesCategory
    }
    val sorted = EventOrdering.sort(filtered, state.settings.sortMode, state.settings.sortDirection, state.today)
    val hero = EventOrdering.heroEvent(settingsVisible, state.today)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TheDayMark(size = 34.dp)
                        Column {
                            Text(
                                text = "THE DAY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = "${DateFormatting.compactDate(state.today, locale)} · ${DateFormatting.weekday(state.today, locale)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                val hiddenPastOnly = state.events.isNotEmpty() && settingsVisible.isEmpty()
                HeroCard(
                    event = hero,
                    today = state.today,
                    locale = locale,
                    onClick = hero?.let { { onOpenEvent(it.id) } },
                    emptyTitle = if (hiddenPastOnly) "暂无可显示事件" else "暂无事件",
                )
            }

            if (settingsVisible.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filter == HomeFilter.ALL,
                            onClick = { filter = HomeFilter.ALL },
                            label = { Text("全部 ${settingsVisible.size}") },
                        )
                        FilterChip(
                            selected = filter == HomeFilter.UPCOMING,
                            onClick = { filter = HomeFilter.UPCOMING },
                            label = {
                                Text("倒数 ${settingsVisible.count { DayMath.isUpcoming(it, state.today) }}")
                            },
                        )
                        if (state.settings.showPastEvents) {
                            FilterChip(
                                selected = filter == HomeFilter.PAST,
                                onClick = { filter = HomeFilter.PAST },
                                label = {
                                    Text("正数 ${settingsVisible.count { DayMath.isPast(it, state.today) }}")
                                },
                            )
                        }
                    }
                }

                if (categories.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("全部分类") },
                            )
                            categories.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category) },
                                )
                            }
                        }
                    }
                }

                if (sorted.isEmpty()) {
                    item {
                        Text(
                            text = "当前筛选下没有事件",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(sorted, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            today = state.today,
                            locale = locale,
                            onClick = { onOpenEvent(event.id) },
                        )
                    }
                }
            }
        }
    }
}