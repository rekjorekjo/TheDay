package io.github.thedayapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.ui.components.EventCalendarSheet
import io.github.thedayapp.ui.components.EventCard
import io.github.thedayapp.ui.components.HeroCard
import io.github.thedayapp.ui.components.TheDayMark
import io.github.thedayapp.ui.components.TodayCalendarMark
import io.github.thedayapp.util.DateFormatting
import java.util.Locale
import kotlin.math.roundToInt

private enum class HomeFilter {
    ALL,
    UPCOMING,
    PAST,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: TheDayState,
    onOpenEvent: (String) -> Unit,
    onAdjustHeroImage: (String) -> Unit,
    onOpenTools: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val locale = currentJavaLocale()
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var showCalendarSheet by rememberSaveable { mutableStateOf(false) }

    val monthProgress = (
        state.today.dayOfMonth.toFloat() /
            state.today.lengthOfMonth().toFloat()
    ).coerceIn(minimumValue = 0f, maximumValue = 1f)

    val monthProgressPercent = (monthProgress * 100f).roundToInt()

    val settingsVisible = state.events.filter {
        state.settings.showPastEvents || !DayMath.isPast(it, state.today)
    }
    val categories = settingsVisible
        .map { it.category }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    LaunchedEffect(categories, selectedCategories) {
        val availableCategories = categories.toSet()
        val retainedCategories = selectedCategories.filter { it in availableCategories }.toSet()
        if (retainedCategories != selectedCategories) {
            selectedCategories = retainedCategories
        }
    }
    LaunchedEffect(state.settings.showPastEvents, filter) {
        if (!state.settings.showPastEvents && filter == HomeFilter.PAST) {
            filter = HomeFilter.ALL
        }
    }

    val upcomingCount = settingsVisible.count { DayMath.isUpcoming(it, state.today) }
    val pastCount = settingsVisible.count { DayMath.isPast(it, state.today) }

    val filtered = settingsVisible.filter { event ->
        val matchesTime = when (filter) {
            HomeFilter.ALL -> true
            HomeFilter.UPCOMING -> DayMath.isUpcoming(event, state.today)
            HomeFilter.PAST -> DayMath.isPast(event, state.today)
        }
        val matchesCategory = selectedCategories.isEmpty() || event.category in selectedCategories
        matchesTime && matchesCategory
    }
    val sorted = EventOrdering.sort(filtered, state.settings.sortMode, state.settings.sortDirection, state.today)
    val hero = EventOrdering.heroEvent(settingsVisible, state.today)

    if (showCalendarSheet) {
        EventCalendarSheet(
            today = state.today,
            events = state.events,
            onDismissRequest = {
                showCalendarSheet = false
            },
        )
    }

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
                        TodayCalendarMark(
                            today = state.today,
                            onClick = {
                                showCalendarSheet = true
                            },
                        )
                        Column {
                            Text(
                                text = "THE DAY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 118.dp, max = 150.dp)
                                    .semantics {
                                        contentDescription = "本月已过去 $monthProgressPercent%"
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LinearProgressIndicator(
                                    progress = monthProgress,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(percent = 50)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "$monthProgressPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenTools,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = "工具栏",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
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
                    onAdjustImage = hero
                        ?.takeIf { it.backgroundImage != null }
                        ?.let { event ->
                            { onAdjustHeroImage(event.id) }
                        },
                    onImageTransformChange = hero
                        ?.takeIf { it.backgroundImage != null }
                        ?.let { event ->
                            { transform ->
                                state.updateEventImageTransform(
                                    eventId = event.id,
                                    target = io.github.thedayapp.data.ImagePlacementTarget.HOME,
                                    transform = transform,
                                )
                            }
                        },
                    emptyTitle = if (hiddenPastOnly) "暂无可显示事件" else "暂无事件",
                )
            }

            if (settingsVisible.isNotEmpty()) {
                item {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val popupWidth = maxWidth
                        val popupOffsetY = with(LocalDensity.current) { 56.dp.roundToPx() }

                        HomeFilterButton(
                            filter = filter,
                            selectedCategories = selectedCategories,
                            totalCount = settingsVisible.size,
                            upcomingCount = upcomingCount,
                            pastCount = pastCount,
                            expanded = filtersExpanded,
                            onClick = { filtersExpanded = !filtersExpanded },
                        )
                        if (filtersExpanded) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                offset = IntOffset(x = 0, y = popupOffsetY),
                                onDismissRequest = { filtersExpanded = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                HomeFilterForm(
                                    categories = categories,
                                    filter = filter,
                                    selectedCategories = selectedCategories,
                                    totalCount = settingsVisible.size,
                                    upcomingCount = upcomingCount,
                                    pastCount = pastCount,
                                    showPastFilter = state.settings.showPastEvents,
                                    onFilterChange = { filter = it },
                                    onCategoryToggle = { category ->
                                        selectedCategories = if (category in selectedCategories) {
                                            selectedCategories - category
                                        } else {
                                            selectedCategories + category
                                        }
                                    },
                                    onClearCategories = { selectedCategories = emptySet() },
                                    onDone = { filtersExpanded = false },
                                    modifier = Modifier.width(popupWidth),
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

@Composable
private fun HomeFilterButton(
    filter: HomeFilter,
    selectedCategories: Set<String>,
    totalCount: Int,
    upcomingCount: Int,
    pastCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = "\u7b5b\u9009\uff1a${homeFilterWithCount(filter, totalCount, upcomingCount, pastCount)} \u00b7 ${homeCategorySummary(selectedCategories)}",
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeFilterForm(
    categories: List<String>,
    filter: HomeFilter,
    selectedCategories: Set<String>,
    totalCount: Int,
    upcomingCount: Int,
    pastCount: Int,
    showPastFilter: Boolean,
    onFilterChange: (HomeFilter) -> Unit,
    onCategoryToggle: (String) -> Unit,
    onClearCategories: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == HomeFilter.ALL,
                onClick = { onFilterChange(HomeFilter.ALL) },
                label = { Text("${homeFilterName(HomeFilter.ALL)} $totalCount") },
            )
            FilterChip(
                selected = filter == HomeFilter.UPCOMING,
                onClick = { onFilterChange(HomeFilter.UPCOMING) },
                label = { Text("${homeFilterName(HomeFilter.UPCOMING)} $upcomingCount") },
            )
            if (showPastFilter) {
                FilterChip(
                    selected = filter == HomeFilter.PAST,
                    onClick = { onFilterChange(HomeFilter.PAST) },
                    label = { Text("${homeFilterName(HomeFilter.PAST)} $pastCount") },
                )
            }
        }

        if (selectedCategories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClearCategories) {
                    Text("\u6e05\u7a7a\u5206\u7c7b")
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategories.isEmpty(),
                onClick = onClearCategories,
                label = { Text("\u5168\u90e8\u5206\u7c7b") },
            )
            categories.forEach { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = { onCategoryToggle(category) },
                    modifier = Modifier.widthIn(max = 180.dp),
                    label = {
                        Text(
                            text = category,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("\u5b8c\u6210")
        }
    }
}

private fun homeFilterWithCount(
    filter: HomeFilter,
    totalCount: Int,
    upcomingCount: Int,
    pastCount: Int,
): String = "${homeFilterName(filter)} ${homeFilterCount(filter, totalCount, upcomingCount, pastCount)}"

private fun homeFilterCount(
    filter: HomeFilter,
    totalCount: Int,
    upcomingCount: Int,
    pastCount: Int,
): Int = when (filter) {
    HomeFilter.ALL -> totalCount
    HomeFilter.UPCOMING -> upcomingCount
    HomeFilter.PAST -> pastCount
}

private fun homeFilterName(filter: HomeFilter): String = when (filter) {
    HomeFilter.ALL -> "\u5168\u90e8"
    HomeFilter.UPCOMING -> "\u5012\u6570"
    HomeFilter.PAST -> "\u6b63\u6570"
}

private fun homeCategorySummary(selectedCategories: Set<String>): String = when (selectedCategories.size) {
    0 -> "\u5168\u90e8\u5206\u7c7b"
    1 -> selectedCategories.first()
    else -> "${selectedCategories.size} \u4e2a\u5206\u7c7b"
}
