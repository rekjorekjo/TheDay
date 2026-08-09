package io.github.thedayapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.R
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.data.NewEventDraft
import io.github.thedayapp.data.RepeatMode
import io.github.thedayapp.ui.media.deleteTemporaryPickerImage
import io.github.thedayapp.ui.media.rememberImageRecropLauncher
import io.github.thedayapp.ui.media.rememberSingleImagePickerLauncher
import io.github.thedayapp.util.DateFormatting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private data class ReminderChoice(val days: Int?, val label: String)

private val reminderChoices = listOf(
    ReminderChoice(null, "不提醒"),
    ReminderChoice(0, "当天提醒"),
    ReminderChoice(1, "提前 1 天"),
    ReminderChoice(3, "提前 3 天"),
    ReminderChoice(7, "提前 7 天"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    existing: DayEvent?,
    onBack: (() -> Unit)?,
    onSave: (DayEvent) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    initialDraft: NewEventDraft? = null,
    onDraftSave: (NewEventDraft) -> Unit = {},
    onDraftClear: () -> Unit = {},
    initialBackgroundImage: LocalImageReference? = null,
    onImportBackgroundImage: (suspend (Uri, Uri) -> Result<LocalImageReference>)? = null,
    onRecropBackgroundImage: (suspend (LocalImageReference, Uri) -> Result<LocalImageReference>)? = null,
    onReleaseBackgroundImage: (LocalImageReference?) -> Unit = {},
) {
    val context = LocalContext.current
    val locale = currentJavaLocale()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isNewEvent = existing == null

    val defaultNewDate = remember(existing?.id) { LocalDate.now() }

    var title by remember(existing?.id) {
        mutableStateOf(existing?.title ?: initialDraft?.title ?: "")
    }
    var date by remember(existing?.id) {
        mutableStateOf(existing?.date ?: initialDraft?.date ?: defaultNewDate)
    }
    var category by remember(existing?.id) {
        mutableStateOf(existing?.category ?: initialDraft?.category ?: "")
    }
    var note by remember(existing?.id) {
        mutableStateOf(existing?.note ?: initialDraft?.note ?: "")
    }
    var yearly by remember(existing?.id) {
        mutableStateOf(
            existing?.repeatMode == RepeatMode.YEARLY ||
                (existing == null && initialDraft?.repeatMode == RepeatMode.YEARLY),
        )
    }
    var pinned by remember(existing?.id) {
        mutableStateOf(existing?.isPinned ?: initialDraft?.isPinned ?: false)
    }
    var reminderDays by remember(existing?.id) {
        mutableStateOf(
            if (existing != null) {
                existing.reminderDaysBefore
            } else {
                initialDraft?.reminderDaysBefore
            },
        )
    }
    var backgroundImage by remember(existing?.id) {
        mutableStateOf(
            existing?.backgroundImage
                ?: initialBackgroundImage
                ?: initialDraft?.backgroundImage,
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }
    var showDiscardDraftDialog by remember { mutableStateOf(false) }
    var isImportingBackground by remember { mutableStateOf(false) }
    var backgroundMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(backgroundImage) {
        val imageToRelease = backgroundImage
        onDispose {
            onReleaseBackgroundImage(imageToRelease)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun hasDraftContent(): Boolean {
        return title.isNotEmpty() ||
            category.isNotEmpty() ||
            note.isNotEmpty() ||
            date != defaultNewDate ||
            yearly ||
            pinned ||
            reminderDays != null ||
            backgroundImage != null
    }

    fun persistDraft(
        newTitle: String = title,
        newDate: LocalDate = date,
        newCategory: String = category,
        newNote: String = note,
        newYearly: Boolean = yearly,
        newPinned: Boolean = pinned,
        newReminderDays: Int? = reminderDays,
        newBackgroundImage: LocalImageReference? = backgroundImage,
    ) {
        if (!isNewEvent) return

        val hasContent =
            newTitle.isNotEmpty() ||
                newCategory.isNotEmpty() ||
                newNote.isNotEmpty() ||
                newDate != defaultNewDate ||
                newYearly ||
                newPinned ||
                newReminderDays != null ||
                newBackgroundImage != null

        if (!hasContent) {
            onDraftClear()
            return
        }

        onDraftSave(
            NewEventDraft(
                title = newTitle,
                date = newDate,
                category = newCategory,
                note = newNote,
                repeatMode = if (newYearly) RepeatMode.YEARLY else RepeatMode.NONE,
                isPinned = newPinned,
                reminderDaysBefore = newReminderDays,
                backgroundImage = newBackgroundImage,
            ),
        )
    }

    val launchBackgroundPicker = rememberSingleImagePickerLauncher(
        onImagePicked = { selection ->
            val importer = onImportBackgroundImage

            if (importer == null || isImportingBackground) {
                deleteTemporaryPickerImage(context, selection.originalUri)
                deleteTemporaryPickerImage(context, selection.croppedUri)
                return@rememberSingleImagePickerLauncher
            }

            coroutineScope.launch {
                isImportingBackground = true

                try {
                    val result = try {
                        importer(
                            selection.originalUri,
                            selection.croppedUri,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }

                    result.onSuccess { imported ->
                        backgroundImage = imported
                        persistDraft(newBackgroundImage = imported)
                    }.onFailure {
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.image_import_failed,
                            ),
                        )
                    }
                } finally {
                    deleteTemporaryPickerImage(context, selection.originalUri)
                    deleteTemporaryPickerImage(context, selection.croppedUri)
                    isImportingBackground = false
                }
            }
        },
        onCropFailed = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.image_crop_failed,
                    ),
                )
            }
        },
    )

    val launchBackgroundRecrop = rememberImageRecropLauncher(
        image = backgroundImage,
        onImageCropped = { croppedUri ->
            val image = backgroundImage
            val recropper = onRecropBackgroundImage

            if (image == null || recropper == null || isImportingBackground) {
                deleteTemporaryPickerImage(context, croppedUri)
                return@rememberImageRecropLauncher
            }

            coroutineScope.launch {
                isImportingBackground = true

                try {
                    val result = try {
                        recropper(image, croppedUri)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }

                    result.onSuccess { recropped ->
                        backgroundImage = recropped
                        persistDraft(newBackgroundImage = recropped)
                    }.onFailure {
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.image_crop_failed,
                            ),
                        )
                    }
                } finally {
                    deleteTemporaryPickerImage(context, croppedUri)
                    isImportingBackground = false
                }
            }
        },
        onCropFailed = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.image_crop_failed,
                    ),
                )
            }
        },
    )

    fun saveEvent() {
        val event = DayEvent(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            title = title.trim(),
            date = date,
            repeatMode = if (yearly) RepeatMode.YEARLY else RepeatMode.NONE,
            category = category.trim(),
            note = note.trim(),
            isPinned = pinned,
            reminderDaysBefore = reminderDays,
            backgroundImage = backgroundImage,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
        )

        if (
            reminderDays != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onSave(event)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(if (existing == null) "新建日子" else "编辑日子") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::saveEvent,
                        enabled = title.isNotBlank(),
                    ) {
                        Text("保存")
                    }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { input ->
                            val newValue = input.take(60)
                            title = newValue
                            persistDraft(newTitle = newValue)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { input ->
                            val newValue = input.take(24)
                            category = newValue
                            persistDraft(newCategory = newValue)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("分类（可选）") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { input ->
                            val newValue = input.take(500)
                            note = newValue
                            persistDraft(newNote = newValue)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注（可选）") },
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("日期", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = DateFormatting.longDate(date, locale),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text("选择", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (onImportBackgroundImage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "背景图片",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (backgroundImage == null) {
                                    "使用默认主题背景"
                                } else {
                                    "已选择背景图片"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isImportingBackground) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            if (backgroundImage == null) {
                                OutlinedButton(onClick = { launchBackgroundPicker() }) {
                                    Text("选择图片")
                                }
                            } else {
                                Box {
                                    OutlinedButton(
                                        onClick = { backgroundMenuExpanded = true },
                                    ) {
                                        Text("管理")
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDropDown,
                                            contentDescription = null,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = backgroundMenuExpanded && !isImportingBackground,
                                        onDismissRequest = { backgroundMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("重新裁剪") },
                                            onClick = {
                                                backgroundMenuExpanded = false
                                                launchBackgroundRecrop()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("更换图片") },
                                            onClick = {
                                                backgroundMenuExpanded = false
                                                launchBackgroundPicker()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "移除图片",
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                backgroundMenuExpanded = false
                                                backgroundImage = null
                                                persistDraft(newBackgroundImage = null)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                    EditorSwitchRow(
                        title = "每年重复",
                        checked = yearly,
                        onCheckedChange = { value ->
                            yearly = value
                            persistDraft(newYearly = value)
                        },
                    )
                    HorizontalDivider()
                    EditorSwitchRow(
                        title = "置顶",
                        checked = pinned,
                        onCheckedChange = { value ->
                            pinned = value
                            persistDraft(newPinned = value)
                        },
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "提醒",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        OutlinedButton(onClick = { reminderExpanded = true }) {
                            Text(
                                reminderChoices.firstOrNull { it.days == reminderDays }?.label
                                    ?: "不提醒",
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = reminderExpanded,
                            onDismissRequest = { reminderExpanded = false },
                        ) {
                            reminderChoices.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.label) },
                                    onClick = {
                                        reminderDays = choice.days
                                        persistDraft(newReminderDays = choice.days)
                                        reminderExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = ::saveEvent,
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (existing == null) "创建日子" else "保存修改")
            }

            if (isNewEvent && hasDraftContent()) {
                TextButton(
                    onClick = { showDiscardDraftDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "丢弃草稿",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val newDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            date = newDate
                            persistDraft(newDate = newDate)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDiscardDraftDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDraftDialog = false },
            title = { Text("丢弃草稿？") },
            text = { Text("草稿内容将被清除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        title = ""
                        date = defaultNewDate
                        category = ""
                        note = ""
                        yearly = false
                        pinned = false
                        reminderDays = null
                        backgroundImage = null
                        reminderExpanded = false
                        showDatePicker = false
                        showDiscardDraftDialog = false
                        onDraftClear()
                    },
                ) {
                    Text("丢弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDraftDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

}

@Composable
private fun EditorSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!description.isNullOrEmpty()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}