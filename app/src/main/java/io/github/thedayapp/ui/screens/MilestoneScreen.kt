package io.github.thedayapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.DayMilestone
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.DayMath
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
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private enum class MilestoneExportAction {
    SHARE,
    SAVE,
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
    val locale = Locale.getDefault()
    var showEditor by remember { mutableStateOf(false) }
    var workingAction by remember { mutableStateOf<MilestoneExportAction?>(null) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    val isWorking = workingAction != null

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

    fun milestoneEvents(): List<DayEvent> = state.milestones.map { milestone ->
        DayEvent(
            id = milestone.id,
            title = milestone.title,
            date = milestone.date,
            category = "\u91cc\u7a0b\u7891",
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
            template = MemoryImageTemplate.MINIMAL,
            title = "\u91cc\u7a0b\u7891",
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
        if (isWorking || state.milestones.isEmpty()) return
        scope.launch {
            workingAction = action
            exportProgress = 0.02f
            val bitmaps = generatePages().getOrNull()
            if (bitmaps == null) {
                workingAction = null
                exportProgress = 0f
                snackbarHostState.showSnackbar("\u91cc\u7a0b\u7891\u5217\u8868\u751f\u6210\u5931\u8d25")
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
                snackbarHostState.showSnackbar("\u5bfc\u51fa\u5931\u8d25")
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
            scope.launch { snackbarHostState.showSnackbar("\u9700\u8981\u5b58\u50a8\u6743\u9650\u624d\u80fd\u4fdd\u5b58") }
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("\u91cc\u7a0b\u7891") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "\u65b0\u589e\u91cc\u7a0b\u7891")
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
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
                        title = "\u4eca\u5e74",
                        progress = state.today.dayOfYear.toFloat() / state.today.lengthOfYear().toFloat(),
                    )
                    ProgressCard(
                        modifier = Modifier.weight(1f),
                        title = "\u672c\u6708",
                        progress = state.today.dayOfMonth.toFloat() / state.today.lengthOfMonth().toFloat(),
                    )
                }
            }
            if (state.milestones.isEmpty()) {
                item {
                    Text(
                        text = "\u8fd8\u6ca1\u6709\u91cc\u7a0b\u7891",
                        modifier = Modifier.padding(vertical = 22.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.milestones, key = { it.id }) { milestone ->
                    MilestoneCard(
                        milestone = milestone,
                        today = state.today,
                        locale = locale,
                        onDelete = { state.deleteMilestone(milestone.id) },
                    )
                }
            }
            item {
                ExportMilestoneActions(
                    enabled = state.milestones.isNotEmpty(),
                    workingAction = workingAction,
                    progress = exportProgress,
                    onShare = { exportMilestones(MilestoneExportAction.SHARE) },
                    onSave = ::requestSave,
                )
            }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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

@Composable
private fun MilestoneCard(
    milestone: DayMilestone,
    today: LocalDate,
    locale: Locale,
    onDelete: () -> Unit,
) {
    val delta = java.time.temporal.ChronoUnit.DAYS.between(today, milestone.date)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                text = when {
                    delta > 0L -> "\u8fd8\u6709 ${abs(delta)} \u5929"
                    delta < 0L -> "\u5df2\u7ecf ${abs(delta)} \u5929"
                    else -> "\u4eca\u5929"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "\u5220\u9664")
            }
        }
    }
}

@Composable
private fun ExportMilestoneActions(
    enabled: Boolean,
    workingAction: MilestoneExportAction?,
    progress: Float,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onShare,
            enabled = enabled && (workingAction == null || workingAction == MilestoneExportAction.SHARE),
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            MilestoneExportContent(Icons.Rounded.IosShare, "\u5206\u4eab", workingAction == MilestoneExportAction.SHARE, progress)
        }
        Button(
            onClick = onSave,
            enabled = enabled && (workingAction == null || workingAction == MilestoneExportAction.SAVE),
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            MilestoneExportContent(Icons.Rounded.DoneAll, "\u4fdd\u5b58\u5230\u76f8\u518c", workingAction == MilestoneExportAction.SAVE, progress)
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (active) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(if (active) "\u751f\u6210\u4e2d" else text, maxLines = 1)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u65b0\u589e\u91cc\u7a0b\u7891") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("\u540d\u79f0") },
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
                        Text("\u65e5\u671f", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DateFormatting.longDate(date, Locale.getDefault()), style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("\u5907\u6ce8") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), date, note.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text("\u4fdd\u5b58")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") }
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
                ) { Text("\u786e\u5b9a") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("\u53d6\u6d88") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}