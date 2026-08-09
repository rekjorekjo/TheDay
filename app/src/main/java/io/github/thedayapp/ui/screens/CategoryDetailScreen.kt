package io.github.thedayapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.R
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.ui.components.EventCard
import io.github.thedayapp.ui.media.deleteTemporaryPickerImage
import io.github.thedayapp.ui.media.rememberImageRecropLauncher
import io.github.thedayapp.ui.media.rememberSingleImagePickerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryName: String,
    state: TheDayState,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val context = LocalContext.current
    val locale = currentJavaLocale()
    val categoryEvents = remember(state.events, categoryName) {
        state.events.filter { event ->
            normalizedCategoryName(event.category) == categoryName
        }
    }

    val sortedEvents = remember(categoryEvents, state.settings, state.today) {
        EventOrdering.sort(
            events = categoryEvents,
            mode = state.settings.sortMode,
            direction = state.settings.sortDirection,
            today = state.today,
        )
    }

    val currentCover = state.categoryCoverFor(categoryName)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var coverMenuExpanded by remember { mutableStateOf(false) }
    var isImportingCover by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }

    val launchCoverPicker = rememberSingleImagePickerLauncher(
        onImagePicked = { selection ->
            if (isImportingCover) {
                deleteTemporaryPickerImage(context, selection.originalUri)
                deleteTemporaryPickerImage(context, selection.croppedUri)
                return@rememberSingleImagePickerLauncher
            }

            coroutineScope.launch {
                isImportingCover = true

                try {
                    val result = try {
                        state.importLocalImage(
                            selection.originalUri,
                            selection.croppedUri,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }

                    val importedCover = result.getOrNull()

                    if (importedCover != null) {
                        state.updateCategoryCover(
                            categoryName = categoryName,
                            image = importedCover,
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.cover_import_failed,
                            ),
                        )
                    }
                } finally {
                    deleteTemporaryPickerImage(context, selection.originalUri)
                    deleteTemporaryPickerImage(context, selection.croppedUri)
                    isImportingCover = false
                }
            }
        },
        onCropFailed = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.cover_crop_failed,
                    ),
                )
            }
        },
    )

    val launchCoverRecrop = rememberImageRecropLauncher(
        image = currentCover,
        onImageCropped = { croppedUri ->
            val cover = currentCover
            if (cover == null || isImportingCover) {
                deleteTemporaryPickerImage(context, croppedUri)
                return@rememberImageRecropLauncher
            }

            coroutineScope.launch {
                isImportingCover = true

                try {
                    val result = try {
                        state.recropLocalImage(cover, croppedUri)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }

                    val recroppedCover = result.getOrNull()
                    if (recroppedCover != null) {
                        state.updateCategoryCover(
                            categoryName = categoryName,
                            image = recroppedCover,
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.cover_crop_failed,
                            ),
                        )
                    }
                } finally {
                    deleteTemporaryPickerImage(context, croppedUri)
                    isImportingCover = false
                }
            }
        },
        onCropFailed = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.cover_crop_failed,
                    ),
                )
            }
        },
    )

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("删除分类") },
            text = { Text("删除“$categoryName”分类？分类中的日子不会被删除，只会变为未分类。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        state.deleteCategory(categoryName)
                        onBack()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(categoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (categoryName != "未分类") {
                        IconButton(
                            onClick = { deleteDialogVisible = true },
                            enabled = !isImportingCover,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "删除分类",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Box {
                        if (isImportingCover) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { coverMenuExpanded = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = "设置封面",
                                    tint = if (currentCover != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = coverMenuExpanded && !isImportingCover,
                            onDismissRequest = { coverMenuExpanded = false },
                        ) {
                            if (currentCover == null) {
                                DropdownMenuItem(
                                    text = { Text("选择封面") },
                                    onClick = {
                                        coverMenuExpanded = false
                                        launchCoverPicker()
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("重新裁剪") },
                                    onClick = {
                                        coverMenuExpanded = false
                                        launchCoverRecrop()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("更换封面") },
                                    onClick = {
                                        coverMenuExpanded = false
                                        launchCoverPicker()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "移除封面",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        coverMenuExpanded = false
                                        state.updateCategoryCover(
                                            categoryName = categoryName,
                                            image = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        if (sortedEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无事件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "${categoryEvents.size} 个日子",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    items = sortedEvents,
                    key = { it.id },
                ) { event ->
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