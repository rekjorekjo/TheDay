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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.DayMilestone
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.sharing.BatchExportRenderer
import io.github.thedayapp.sharing.EventShareActions
import io.github.thedayapp.sharing.MemoryImagePalette
import io.github.thedayapp.sharing.MemoryImageTemplate
import io.github.thedayapp.util.DateFormatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private enum class MilestoneExportAction {
    SHARE,
    SAVE,
}

private enum class MilestoneStep {
    LIST,
    SORT,
    SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneScreen(
    state: TheDayState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val locale = currentJavaLocale()
    var showEditor by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(MilestoneStep.LIST) }
    var selectedTemplate by remember { mutableStateOf(MemoryImageTemplate.MINIMAL) }
    var exportTitle by remember { mutableStateOf("里程碑") }
    var workingAction by remember { mutableStateOf<MilestoneExportAction?>(null) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val sortedSelectedIds = remember { mutableStateListOf<String>() }
    var manageMode by remember { mutableStateOf(false) }
    val isWorking = workingAction != null
    val selectionMode = manageMode

    val milestoneById = remember(state.milestones) {
        state.milestones.associateBy { milestone -> milestone.id }
    }
    val selectedMilestones = sortedSelectedIds.mapNotNull { id -> milestoneById[id] }

    BackHandler(enabled = step != MilestoneStep.LIST || selectionMode) {
        when (step) {
            MilestoneStep.SETTINGS -> step = MilestoneStep.SORT
            MilestoneStep.SORT -> step = MilestoneStep.LIST
            MilestoneStep.LIST -> {
                selectedIds.clear()
                manageMode = false
            }
        }
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

    fun openSort() {
        if (selectedIds.isEmpty()) return
        sortedSelectedIds.clear()
        sortedSelectedIds.addAll(selectedIds)
        step = MilestoneStep.SORT
    }

    fun deleteSelectedMilestones() {
        selectedIds.toList().forEach { id -> state.deleteMilestone(id) }
        selectedIds.clear()
        sortedSelectedIds.clear()
        manageMode = false
        step = MilestoneStep.LIST
    }

    fun moveSortedMilestone(milestoneId: String, direction: Int): Boolean {
        val fromIndex = sortedSelectedIds.indexOf(milestoneId)
        if (fromIndex !in sortedSelectedIds.indices) return false

        val toIndex = (fromIndex + direction).coerceIn(0, sortedSelectedIds.lastIndex)
        if (fromIndex == toIndex) return false

        val moved = sortedSelectedIds.removeAt(fromIndex)
        sortedSelectedIds.add(toIndex, moved)
        return true
    }

    fun milestoneEvents(): List<DayEvent> = selectedMilestones.map { milestone ->
        DayEvent(
            id = milestone.id,
            title = milestone.title,
            date = milestone.date,
            category = "里程碑",
            note = milestone.note,
            createdAtEpochMillis = milestone.createdAtEpochMillis,
        )
    }

    suspend fun generatePages(): Result<List<Bitmap>> {
        return BatchExportRenderer.renderListPages(
            context = context,
            events = milestoneEvents(),
            today = state.today,
            locale = locale,
            palette = memoryPalette,
            template = selectedTemplate,
            title = exportTitle.trim().takeIf { it.isNotEmpty() } ?: "里程碑",
            onProgress = { fraction ->
                withContext(Dispatchers.Main.immediate) {
                    exportProgress = 0.06f + fraction * 0.78f
                }
            },
        )
    }

    fun recycleBitmaps(bitmaps: List<Bitmap>) {
        bitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun exportMilestones(action: MilestoneExportAction) {
        if (isWorking || selectedMilestones.isEmpty()) return
        scope.launch {
            workingAction = action
            exportProgress = 0.02f
            val bitmaps = generatePages().getOrNull()
            if (bitmaps == null) {
                workingAction = null
                exportProgress = 0f
                snackbarHostState.showSnackbar("里程碑列表生成失败")
                return@launch
            }
            val result = if (action == MilestoneExportAction.SHARE) {
                exportProgress = 0.90f
                EventShareActions.shareImages(context, bitmaps)
            } else {
                var savedCount = 0
                bitmaps.forEachIndexed { index, bitmap ->
                    if (EventShareActions.saveImageToGallery(context, bitmap).isSuccess) {
                        savedCount += 1
                    }
                    exportProgress = 0.84f + (index + 1f) / bitmaps.size.coerceAtLeast(1) * 0.16f
                }
                if (savedCount == bitmaps.size) Result.success(Unit) else Result.failure(IllegalStateException())
            }
            exportProgress = 1f
            recycleBitmaps(bitmaps)
            workingAction = null
            if (result.isFailure) {
                snackbarHostState.showSnackbar("导出失败")
            }
            exportProgress = 0f
        }
    }

    val legacySaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            exportMilestones(MilestoneExportAction.SAVE)
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要存储权限才能保存") }
        }
    }

    fun requestSave() {
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            legacySaveLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            exportMilestones(MilestoneExportAction.SAVE)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (step) {
                MilestoneStep.LIST -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = { Text(if (selectionMode && selectedIds.isNotEmpty()) "已选 ${selectedIds.size}" else if (selectionMode) "选择里程碑" else "里程碑") },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (selectionMode) { selectedIds.clear(); manageMode = false } else onBack()
                                },
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            if (!selectionMode) {
                                IconButton(onClick = { showEditor = true }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "新增里程碑")
                                }
                            }
                        },
                    )
                }

                MilestoneStep.SORT -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = { Text("调整顺序") },
                        navigationIcon = {
                            IconButton(onClick = { step = MilestoneStep.LIST }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            TextButton(onClick = { step = MilestoneStep.SETTINGS }) {
                                Text("确认")
                            }
                        },
                    )
                }

                MilestoneStep.SETTINGS -> {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = { Text("导出里程碑") },
                        navigationIcon = {
                            IconButton(onClick = { step = MilestoneStep.SORT }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        when (step) {
            MilestoneStep.LIST -> MilestoneListPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                milestones = state.milestones,
                today = state.today,
                locale = locale,
                selectedIds = selectedIds,
                selectionMode = selectionMode,
                onToggleSelection = { milestoneId ->
                    manageMode = true
                    if (milestoneId in selectedIds) {
                        selectedIds.remove(milestoneId)
                    } else {
                        selectedIds.add(milestoneId)
                    }
                },
                onMove = state::moveMilestone,
                onExportSelected = ::openSort,
                onDeleteSelected = ::deleteSelectedMilestones,
                onDoneSelection = {
                    selectedIds.clear()
                    manageMode = false
                },
            )

            MilestoneStep.SORT -> MilestoneSortPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                milestones = selectedMilestones,
                today = state.today,
                locale = locale,
                onMove = ::moveSortedMilestone,
            )

            MilestoneStep.SETTINGS -> MilestoneExportSettingsPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                selectedCount = selectedMilestones.size,
                exportTitle = exportTitle,
                onExportTitleChange = { exportTitle = it },
                selectedTemplate = selectedTemplate,
                onTemplateSelected = { selectedTemplate = it },
                workingAction = workingAction,
                exportProgress = exportProgress,
                onShare = { exportMilestones(MilestoneExportAction.SHARE) },
                onSave = ::requestSave,
            )
        }
    }

    if (showEditor) {
        MilestoneEditorDialog(
            initialDate = state.today,
            onDismiss = { showEditor = false },
            onSave = { title, date, note ->
                state.upsertMilestone(
                    DayMilestone(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        date = date,
                        note = note,
                    ),
                )
                showEditor = false
            },
        )
    }
}

@Composable
private fun MilestoneListPage(
    modifier: Modifier,
    milestones: List<DayMilestone>,
    today: LocalDate,
    locale: Locale,
    selectedIds: List<String>,
    selectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onMove: (String, Int) -> Boolean,
    onExportSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDoneSelection: () -> Unit,
) {
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    val density = LocalDensity.current
    val itemGapPx = with(density) { 12.dp.toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentMilestones by rememberUpdatedState(milestones)
    val currentOnMove by rememberUpdatedState(onMove)

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = if (selectionMode) 116.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProgressCard(
                        modifier = Modifier.weight(1f),
                        title = "${today.year}年",
                        progress = today.dayOfYear.toFloat() / today.lengthOfYear().toFloat(),
                    )
                    ProgressCard(
                        modifier = Modifier.weight(1f),
                        title = "${today.monthValue}月",
                        progress = today.dayOfMonth.toFloat() / today.lengthOfMonth().toFloat(),
                    )
                }
            }
            if (milestones.isEmpty()) {
                item {
                    Text(
                        text = "还没有里程碑",
                        modifier = Modifier.padding(vertical = 22.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(milestones, key = { it.id }) { milestone ->
                    val selected = milestone.id in selectedIds
                    val isDragging = draggedId == milestone.id
                    val highlighted = isDragging && dragOffset != 0f
                    val itemHeight = itemHeights[milestone.id]?.toFloat() ?: 1f
                    val dragModifier = (if (isDragging) {
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
                            itemHeights[milestone.id] = size.height
                        }
                        .then(
                            Modifier.pointerInput(milestone.id) {
                                detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = milestone.id
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

                                            val latestMilestones = currentMilestones
                                            val currentIndex = latestMilestones.indexOfFirst {
                                                it.id == milestone.id
                                            }

                                            when {
                                                dragOffset > 0f -> {
                                                    val adjacent = latestMilestones.getOrNull(currentIndex + 1)
                                                    val adjacentHeight = adjacent
                                                        ?.let { itemHeights[it.id] }
                                                        ?.toFloat()
                                                        ?: itemHeight
                                                    val threshold = adjacentHeight * 0.50f
                                                    if (
                                                        dragOffset > threshold &&
                                                        currentOnMove(milestone.id, 1)
                                                    ) {
                                                        dragOffset -= adjacentHeight + itemGapPx
                                                    }
                                                }

                                                dragOffset < 0f -> {
                                                    val adjacent = latestMilestones.getOrNull(currentIndex - 1)
                                                    val adjacentHeight = adjacent
                                                        ?.let { itemHeights[it.id] }
                                                        ?.toFloat()
                                                        ?: itemHeight
                                                    val threshold = adjacentHeight * 0.50f
                                                    if (
                                                        dragOffset < -threshold &&
                                                        currentOnMove(milestone.id, -1)
                                                    ) {
                                                        dragOffset += adjacentHeight + itemGapPx
                                                    }
                                                }
                                            }
                                        },
                                )
                            },
                        )

                    MilestoneCard(
                        milestone = milestone,
                        today = today,
                        locale = locale,
                        selected = selected,
                        highlighted = highlighted,
                        selectionMode = selectionMode,
                        onClick = {
                            onToggleSelection(milestone.id)
                        },
                        modifier = dragModifier,
                    )
                }
            }
        }

        if (selectionMode) {
            MilestoneSelectionToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                hasSelection = selectedIds.isNotEmpty(),
                onExport = onExportSelected,
                onDelete = onDeleteSelected,
                onDone = onDoneSelection,
            )
        }
    }
}
@Composable
private fun ProgressCard(
    modifier: Modifier,
    title: String,
    progress: Float,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "%.2f%%".format(Locale.US, safeProgress * 100f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { safeProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MilestoneCard(
    milestone: DayMilestone,
    today: LocalDate,
    locale: Locale,
    selected: Boolean,
    highlighted: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val delta = ChronoUnit.DAYS.between(today, milestone.date)
    val containerColor by animateColorAsState(
        targetValue = if (selected || highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "milestoneSelectionColor",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 62.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = DateFormatting.compactDate(milestone.date, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (milestone.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = milestone.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = relativeMilestoneText(delta),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
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
private fun MilestoneSelectionToolbar(
    modifier: Modifier,
    hasSelection: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onDelete,
                enabled = hasSelection,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除")
            }
            Button(
                onClick = onExport,
                enabled = hasSelection,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.IosShare, contentDescription = "导出")
            }
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "完成")
            }
        }
    }
}
@Composable
private fun MilestoneSortPage(
    modifier: Modifier,
    milestones: List<DayMilestone>,
    today: LocalDate,
    locale: Locale,
    onMove: (String, Int) -> Boolean,
) {
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    val density = LocalDensity.current
    val itemGapPx = with(density) { 10.dp.toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentMilestones by rememberUpdatedState(milestones)
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
                text = "总数：${milestones.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            items(
                items = milestones,
                key = { milestone -> milestone.id },
            ) { milestone ->
                val isDragging = draggedId == milestone.id
                val itemHeight = itemHeights[milestone.id]?.toFloat() ?: 1f

                MilestoneSortRow(
                    milestone = milestone,
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
                            itemHeights[milestone.id] = size.height
                        }
                        .pointerInput(milestone.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedId = milestone.id
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

                                    val latestMilestones = currentMilestones
                                    val currentIndex = latestMilestones.indexOfFirst {
                                        it.id == milestone.id
                                    }

                                    when {
                                        dragOffset > 0f -> {
                                            val adjacent = latestMilestones.getOrNull(currentIndex + 1)
                                            val adjacentHeight = adjacent
                                                ?.let { itemHeights[it.id] }
                                                ?.toFloat()
                                                ?: itemHeight
                                            val threshold = adjacentHeight * 0.50f
                                            if (
                                                dragOffset > threshold &&
                                                currentOnMove(milestone.id, 1)
                                            ) {
                                                dragOffset -= adjacentHeight + itemGapPx
                                            }
                                        }

                                        dragOffset < 0f -> {
                                            val adjacent = latestMilestones.getOrNull(currentIndex - 1)
                                            val adjacentHeight = adjacent
                                                ?.let { itemHeights[it.id] }
                                                ?.toFloat()
                                                ?: itemHeight
                                            val threshold = adjacentHeight * 0.50f
                                            if (
                                                dragOffset < -threshold &&
                                                currentOnMove(milestone.id, -1)
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
        }
    }
}

@Composable
private fun MilestoneSortRow(
    milestone: DayMilestone,
    today: LocalDate,
    locale: Locale,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val delta = ChronoUnit.DAYS.between(today, milestone.date)
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
        label = "milestoneSortHighlight",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
                    text = milestone.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = DateFormatting.compactDate(milestone.date, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = relativeMilestoneText(delta),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
@Composable
private fun MilestoneExportSettingsPage(
    modifier: Modifier,
    selectedCount: Int,
    exportTitle: String,
    onExportTitleChange: (String) -> Unit,
    selectedTemplate: MemoryImageTemplate,
    onTemplateSelected: (MemoryImageTemplate) -> Unit,
    workingAction: MilestoneExportAction?,
    exportProgress: Float,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            OutlinedTextField(
                value = exportTitle,
                onValueChange = onExportTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                singleLine = true,
            )
        }
        item {
            Text(
                text = "装饰主题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
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
        }
        item {
            Text(
                text = "已选择 $selectedCount 个里程碑",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onShare,
                    enabled = selectedCount > 0 &&
                        (workingAction == null || workingAction == MilestoneExportAction.SHARE),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    MilestoneExportContent(
                        Icons.Rounded.IosShare,
                        "分享",
                        workingAction == MilestoneExportAction.SHARE,
                        exportProgress,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = selectedCount > 0 &&
                        (workingAction == null || workingAction == MilestoneExportAction.SAVE),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    MilestoneExportContent(
                        Icons.Rounded.DoneAll,
                        "保存到相册",
                        workingAction == MilestoneExportAction.SAVE,
                        exportProgress,
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneExportContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    progress: Float,
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "milestoneExportProgress",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (active) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(if (active) "生成中" else text, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestoneEditorDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val locale = currentJavaLocale()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增里程碑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("名称") },
                    singleLine = true,
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("日期", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DateFormatting.longDate(date, locale), style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("备注") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), date, note.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun relativeMilestoneText(delta: Long): String {
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
