package io.github.thedayapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.sharing.BatchExportRenderer
import io.github.thedayapp.sharing.EventShareActions
import io.github.thedayapp.sharing.MemoryImagePalette
import io.github.thedayapp.sharing.MemoryImageTemplate
import io.github.thedayapp.util.DateFormatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private enum class ExportMode(val label: String) {
    LONG_IMAGE("导出长图"),
    LIST("导出列表"),
}

private enum class ExportStep {
    SELECT,
    SORT,
    SETTINGS,
}

private enum class ExportAction {
    SHARE,
    SAVE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: TheDayState,
    bottomBar: @Composable () -> Unit,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val locale = Locale.getDefault()

    val visibleEvents = remember(
        state.events,
        state.settings.showPastEvents,
        state.today,
    ) {
        state.events.filter { event ->
            state.settings.showPastEvents || !DayMath.isPast(event, state.today)
        }
    }
    val sortedEvents = remember(
        visibleEvents,
        state.settings.sortMode,
        state.settings.sortDirection,
        state.today,
    ) {
        EventOrdering.sort(
            visibleEvents,
            state.settings.sortMode,
            state.settings.sortDirection,
            state.today,
        )
    }

    val selectedIds = remember { mutableStateListOf<String>() }
    val sortedSelectedIds = remember { mutableStateListOf<String>() }
    var step by rememberSaveable { mutableStateOf(ExportStep.SELECT) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ExportMode.LONG_IMAGE) }
    var exportTitle by rememberSaveable { mutableStateOf("The Day") }
    var selectedTemplate by rememberSaveable {
        mutableStateOf(MemoryImageTemplate.CIRCLES)
    }
    var workingAction by rememberSaveable {
        mutableStateOf<ExportAction?>(null)
    }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    val isWorking = workingAction != null

    val eventById = remember(sortedEvents) {
        sortedEvents.associateBy { event -> event.id }
    }
    val selectedEvents = sortedSelectedIds.mapNotNull { id -> eventById[id] }

    LaunchedEffect(sortedEvents) {
        val validIds = sortedEvents.mapTo(mutableSetOf()) { it.id }
        selectedIds.removeAll { it !in validIds }
        sortedSelectedIds.removeAll { it !in validIds }
    }

    val colorScheme = MaterialTheme.colorScheme
    val memoryPalette = remember(colorScheme) {
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

    fun openSort(selectedMode: ExportMode) {
        if (selectedIds.isEmpty()) return
        mode = selectedMode
        sortedSelectedIds.clear()
        sortedSelectedIds.addAll(selectedIds)
        step = ExportStep.SORT
    }

    fun moveSortedEvent(eventId: String, direction: Int): Boolean {
        val fromIndex = sortedSelectedIds.indexOf(eventId)
        if (fromIndex !in sortedSelectedIds.indices) return false

        val toIndex = (fromIndex + direction)
            .coerceIn(0, sortedSelectedIds.lastIndex)
        if (fromIndex == toIndex) return false

        val moved = sortedSelectedIds.removeAt(fromIndex)
        sortedSelectedIds.add(toIndex, moved)
        return true
    }

    BackHandler(enabled = step != ExportStep.SELECT) {
        step = when (step) {
            ExportStep.SETTINGS -> ExportStep.SORT
            ExportStep.SORT -> ExportStep.SELECT
            ExportStep.SELECT -> ExportStep.SELECT
        }
    }

    suspend fun generatePages(): Result<List<Bitmap>> {
        val title = exportTitle.trim().takeIf { it.isNotEmpty() }
        return when (mode) {
            ExportMode.LONG_IMAGE -> BatchExportRenderer.renderLongImagePages(
                context = context,
                events = selectedEvents,
                today = state.today,
                locale = locale,
                palette = memoryPalette,
                template = selectedTemplate,
                title = title,
                onProgress = { fraction ->
                    withContext(Dispatchers.Main.immediate) {
                        exportProgress = 0.06f + fraction * 0.78f
                    }
                },
            )

            ExportMode.LIST -> BatchExportRenderer.renderListPages(
                context = context,
                events = selectedEvents,
                today = state.today,
                locale = locale,
                palette = memoryPalette,
                template = selectedTemplate,
                title = title,
                onProgress = { fraction ->
                    withContext(Dispatchers.Main.immediate) {
                        exportProgress = 0.06f + fraction * 0.78f
                    }
                },
            )
        }
    }

    fun recycleBitmaps(bitmaps: List<Bitmap>) {
        bitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun saveToGallery() {
        if (isWorking || selectedEvents.isEmpty()) return

        scope.launch {
            workingAction = ExportAction.SAVE
            exportProgress = 0.02f

            val bitmaps = generatePages().getOrNull()
            if (bitmaps == null) {
                workingAction = null
                exportProgress = 0f
                snackbarHostState.showSnackbar("导出图片生成失败")
                return@launch
            }

            var savedCount = 0
            bitmaps.forEachIndexed { index, bitmap ->
                if (EventShareActions.saveImageToGallery(context, bitmap).isSuccess) {
                    savedCount += 1
                }
                exportProgress = 0.84f +
                    (index + 1f) / bitmaps.size.coerceAtLeast(1) * 0.16f
            }
            recycleBitmaps(bitmaps)
            workingAction = null

            if (savedCount == bitmaps.size) {
                snackbarHostState.showSnackbar("已保存 $savedCount 张图片到相册")
            } else {
                snackbarHostState.showSnackbar(
                    "已保存 $savedCount 张图片，部分图片保存失败",
                )
            }
            exportProgress = 0f
        }
    }

    val legacySaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            saveToGallery()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("需要存储权限才能保存到相册")
            }
        }
    }

    fun requestSave() {
        val needsLegacyPermission =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED

        if (needsLegacyPermission) {
            legacySaveLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveToGallery()
        }
    }

    fun shareImages() {
        if (isWorking || selectedEvents.isEmpty()) return

        scope.launch {
            workingAction = ExportAction.SHARE
            exportProgress = 0.02f

            val bitmaps = generatePages().getOrNull()
            if (bitmaps == null) {
                workingAction = null
                exportProgress = 0f
                snackbarHostState.showSnackbar("导出图片生成失败")
                return@launch
            }

            exportProgress = 0.90f
            val result = EventShareActions.shareImages(context, bitmaps)
            exportProgress = 1f
            recycleBitmaps(bitmaps)
            workingAction = null

            if (result.isFailure) {
                snackbarHostState.showSnackbar("无法打开分享面板")
            }
            exportProgress = 0f
        }
    }

    val estimatedPageCount = remember(mode, selectedEvents) {
        when (mode) {
            ExportMode.LONG_IMAGE -> {
                BatchExportRenderer.estimateLongImagePageCount(selectedEvents)
            }

            ExportMode.LIST -> {
                BatchExportRenderer.estimateListPageCount(selectedEvents)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            when (step) {
                ExportStep.SELECT -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = { Text("导出") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "返回",
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    if (selectionMode) {
                                        selectionMode = false
                                    } else {
                                        selectedIds.clear()
                                        selectionMode = true
                                    }
                                },
                            ) {
                                Text(if (selectionMode) "完成" else "选择")
                            }
                        },
                    )
                }

                ExportStep.SORT -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = { Text("调整顺序") },
                        navigationIcon = {
                            IconButton(onClick = { step = ExportStep.SELECT }) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "返回",
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { step = ExportStep.SETTINGS }) {
                                Text("确认")
                            }
                        },
                    )
                }

                ExportStep.SETTINGS -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = { Text(mode.label) },
                        navigationIcon = {
                            IconButton(onClick = { step = ExportStep.SORT }) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "返回",
                                )
                            }
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (step == ExportStep.SELECT) {
                bottomBar()
            }
        },
    ) { contentPadding ->
        when (step) {
            ExportStep.SELECT -> ExportSelectionPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                events = sortedEvents,
                today = state.today,
                locale = locale,
                selectedIds = selectedIds,
                selectionMode = selectionMode,
                onToggleSelection = { eventId ->
                    if (eventId in selectedIds) {
                        selectedIds.remove(eventId)
                    } else {
                        selectedIds.add(eventId)
                    }
                },
                onOpenEvent = onOpenEvent,
                onExportLongImage = {
                    openSort(ExportMode.LONG_IMAGE)
                },
                onExportList = {
                    openSort(ExportMode.LIST)
                },
            )

            ExportStep.SORT -> ExportSortPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                events = selectedEvents,
                today = state.today,
                locale = locale,
                onMove = ::moveSortedEvent,
            )

            ExportStep.SETTINGS -> ExportSettingsPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                selectedCount = selectedEvents.size,
                estimatedPageCount = estimatedPageCount,
                exportTitle = exportTitle,
                onExportTitleChange = { exportTitle = it },
                selectedTemplate = selectedTemplate,
                onTemplateSelected = { selectedTemplate = it },
                workingAction = workingAction,
                exportProgress = exportProgress,
                onShare = ::shareImages,
                onSave = ::requestSave,
            )
        }
    }
}

@Composable
private fun ExportSelectionPage(
    modifier: Modifier,
    events: List<DayEvent>,
    today: LocalDate,
    locale: Locale,
    selectedIds: List<String>,
    selectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onExportLongImage: () -> Unit,
    onExportList: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExportTypeButton(
                modifier = Modifier.weight(1f),
                title = "导出长图",
                icon = Icons.Rounded.Image,
                enabled = selectedIds.isNotEmpty(),
                highlighted = selectionMode && selectedIds.isNotEmpty(),
                onClick = onExportLongImage,
            )
            ExportTypeButton(
                modifier = Modifier.weight(1f),
                title = "导出列表",
                icon = Icons.Rounded.List,
                enabled = selectedIds.isNotEmpty(),
                highlighted = selectionMode && selectedIds.isNotEmpty(),
                onClick = onExportList,
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            itemsIndexed(
                items = events,
                key = { _, event -> event.id },
            ) { _, event ->
                ExportSelectionRow(
                    event = event,
                    today = today,
                    locale = locale,
                    selected = selectionMode && event.id in selectedIds,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelection(event.id)
                        } else {
                            onOpenEvent(event.id)
                        }
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ExportSortPage(
    modifier: Modifier,
    events: List<DayEvent>,
    today: LocalDate,
    locale: Locale,
    onMove: (String, Int) -> Boolean,
) {
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    val density = LocalDensity.current
    val itemGapPx = with(density) { 10.dp.toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentEvents by rememberUpdatedState(events)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(
        modifier = modifier.padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "长按卡片后上下拖动",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "总数：${events.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = events,
                key = { _, event -> event.id },
            ) { _, event ->
                val isDragging = draggedId == event.id
                val itemHeight = itemHeights[event.id]?.toFloat() ?: 1f

                ExportSortRow(
                    event = event,
                    today = today,
                    locale = locale,
                    isDragging = isDragging,
                    modifier = (if (isDragging) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            fadeOutSpec = null,
                        )
                    })
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                        }
                        .onSizeChanged { size ->
                            itemHeights[event.id] = size.height
                        }
                        .pointerInput(event.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedId = event.id
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedId = null
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggedId = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    val latestEvents = currentEvents
                                    val currentIndex = latestEvents.indexOfFirst {
                                        it.id == event.id
                                    }

                                    when {
                                        dragOffset > 0f -> {
                                            val adjacent = latestEvents.getOrNull(currentIndex + 1)
                                            val adjacentHeight = adjacent
                                                ?.let { itemHeights[it.id] }
                                                ?.toFloat()
                                                ?: itemHeight
                                            val threshold = adjacentHeight * 0.50f
                                            if (
                                                dragOffset > threshold &&
                                                currentOnMove(event.id, 1)
                                            ) {
                                                dragOffset -= adjacentHeight + itemGapPx
                                            }
                                        }

                                        dragOffset < 0f -> {
                                            val adjacent = latestEvents.getOrNull(currentIndex - 1)
                                            val adjacentHeight = adjacent
                                                ?.let { itemHeights[it.id] }
                                                ?.toFloat()
                                                ?: itemHeight
                                            val threshold = adjacentHeight * 0.50f
                                            if (
                                                dragOffset < -threshold &&
                                                currentOnMove(event.id, -1)
                                            ) {
                                                dragOffset += adjacentHeight + itemGapPx
                                            }
                                        }
                                    }
                                },
                            )
                        },
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ExportSettingsPage(
    modifier: Modifier,
    selectedCount: Int,
    estimatedPageCount: Int,
    exportTitle: String,
    onExportTitleChange: (String) -> Unit,
    selectedTemplate: MemoryImageTemplate,
    onTemplateSelected: (MemoryImageTemplate) -> Unit,
    workingAction: ExportAction?,
    exportProgress: Float,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = exportTitle,
            onValueChange = onExportTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("标题") },
            singleLine = true,
        )

        Spacer(Modifier.height(22.dp))

        Text(
            text = "装饰主题",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        val templateRows = MemoryImageTemplate.values().toList().chunked(3)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            templateRows.forEach { rowTemplates ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowTemplates.forEach { template ->
                        FilterChip(
                            selected = selectedTemplate == template,
                            onClick = { onTemplateSelected(template) },
                            label = {
                                Text(
                                    text = templateLabel(template),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        )
                    }
                    repeat(3 - rowTemplates.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "预计导出 $estimatedPageCount 页图片",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onShare,
                enabled = selectedCount > 0 &&
                    (workingAction == null || workingAction == ExportAction.SHARE),
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp),
            ) {
                ExportActionContent(
                    icon = Icons.Rounded.IosShare,
                    text = "分享",
                    active = workingAction == ExportAction.SHARE,
                    progress = exportProgress,
                )
            }

            Button(
                onClick = onSave,
                enabled = selectedCount > 0 &&
                    (workingAction == null || workingAction == ExportAction.SAVE),
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp),
            ) {
                ExportActionContent(
                    icon = Icons.Rounded.DoneAll,
                    text = "保存到相册",
                    active = workingAction == ExportAction.SAVE,
                    progress = exportProgress,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ExportActionContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "exportProgress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (active) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(3.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (active) "生成中" else text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ExportTypeButton(
    modifier: Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                highlighted -> MaterialTheme.colorScheme.primaryContainer
                enabled -> MaterialTheme.colorScheme.surfaceContainerLow
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when {
                    highlighted -> MaterialTheme.colorScheme.primary
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                },
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ExportSelectionRow(
    event: DayEvent,
    today: LocalDate,
    locale: Locale,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
) {
    val delta = DayMath.signedDays(event, today)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(event.category.ifBlank { "未分类" })
                        append(" · ")
                        append(
                            DateFormatting.compactDate(
                                DayMath.effectiveDate(event, today),
                                locale,
                            ),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = relativeDayText(delta),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )

            if (selectionMode) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "已选择",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportSortRow(
    event: DayEvent,
    today: LocalDate,
    locale: Locale,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val delta = DayMath.signedDays(event, today)
    val containerColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "exportSortHighlight",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(event.category.ifBlank { "未分类" })
                        append(" · ")
                        append(
                            DateFormatting.compactDate(
                                DayMath.effectiveDate(event, today),
                                locale,
                            ),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = relativeDayText(delta),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun relativeDayText(delta: Long): String {
    return when {
        delta > 0L -> "还有 ${abs(delta)} 天"
        delta < 0L -> "已经 ${abs(delta)} 天"
        else -> "今天"
    }
}

private fun templateLabel(template: MemoryImageTemplate): String {
    return when (template) {
        MemoryImageTemplate.CIRCLES -> "圆圈"
        MemoryImageTemplate.STARS -> "星星"
        MemoryImageTemplate.HEARTS -> "爱心"
        MemoryImageTemplate.METEORS -> "流星"
        MemoryImageTemplate.WAVES -> "波浪"
        MemoryImageTemplate.MINIMAL -> "极简"
    }
}
