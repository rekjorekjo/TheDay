package io.github.thedayapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.data.DayAlbum
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.ImagePlacementTarget
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.ui.media.EventImageCardType
import io.github.thedayapp.ui.media.LocalImageViewport
import io.github.thedayapp.ui.media.adaptiveEventImagePreviewAspectRatio
import io.github.thedayapp.ui.media.rememberLocalImageBitmap
import io.github.thedayapp.util.DateFormatting
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    state: TheDayState,
    onBack: () -> Unit,
    onCreateAlbum: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val eventById = remember(state.events) { state.events.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("纪念册") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateAlbum) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建纪念册")
                    }
                },
            )
        },
    ) { contentPadding ->
        if (state.albums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "暂无纪念册",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = onCreateAlbum) {
                        Text("新建纪念册")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.albums, key = { it.id }) { album ->
                    AlbumBookCard(
                        album = album,
                        events = album.eventIds.mapNotNull(eventById::get),
                        cover = album.coverEventId?.let(eventById::get)
                            ?: album.eventIds.firstNotNullOfOrNull(eventById::get),
                        onClick = { onOpenAlbum(album.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: DayAlbum,
    events: List<DayEvent>,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSetCover: (String) -> Unit,
    onRemoveEvent: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val locale = currentJavaLocale()
    var currentIndex by rememberSaveable(album.id) { mutableStateOf(0) }
    var selectionMode by rememberSaveable(album.id) { mutableStateOf(false) }

    LaunchedEffect(events) {
        if (events.isNotEmpty() && currentIndex > events.lastIndex) {
            currentIndex = events.lastIndex
        }
        if (events.isEmpty()) {
            selectionMode = false
        }
    }

    val currentEvent = events.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { currentEvent?.id?.let(onSetCover) },
                        enabled = currentEvent != null,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = "设为封面",
                            tint = if (currentEvent?.id == album.coverEventId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑纪念册")
                    }
                },
            )
        },
    ) { contentPadding ->
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "这个纪念册还没有日子",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AlbumCardDeck(
                        events = events,
                        today = today,
                        locale = locale,
                        currentIndex = currentIndex,
                        onCurrentIndexChange = { updatedIndex ->
                            currentIndex = wrappedAlbumIndex(updatedIndex, events.size)
                            selectionMode = false
                        },
                        onLongPressCurrent = { selectionMode = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    AlbumPageIndicator(
                        count = events.size,
                        currentIndex = currentIndex,
                        modifier = Modifier.padding(bottom = 22.dp),
                    )
                }

                if (selectionMode && currentEvent != null) {
                    AlbumSelectionToolbar(
                        onDelete = {
                            onRemoveEvent(currentEvent.id)
                            currentIndex = currentIndex
                                .coerceAtMost((events.size - 2).coerceAtLeast(0))
                            selectionMode = false
                        },
                        onEdit = {
                            selectionMode = false
                            onOpenEvent(currentEvent.id)
                        },
                        onDone = { selectionMode = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumEditorScreen(
    album: DayAlbum?,
    events: List<DayEvent>,
    onBack: () -> Unit,
    onSave: (DayAlbum) -> Unit,
) {
    val eventById = remember(events) { events.associateBy { it.id } }
    var title by rememberSaveable(album?.id) { mutableStateOf(album?.title ?: "") }
    val selectedEventIds = remember(album?.id) {
        mutableStateListOf<String>().also { list ->
            album?.eventIds
                ?.filter { eventId -> eventId in eventById }
                ?.let(list::addAll)
        }
    }

    LaunchedEffect(events) {
        val validIds = events.mapTo(mutableSetOf()) { it.id }
        selectedEventIds.removeAll { it !in validIds }
    }

    fun toggleEvent(eventId: String) {
        if (eventId in selectedEventIds) {
            selectedEventIds.remove(eventId)
        } else {
            selectedEventIds.add(eventId)
        }
    }

    fun saveAlbum() {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank() || selectedEventIds.isEmpty()) return
        onSave(
            DayAlbum(
                id = album?.id ?: java.util.UUID.randomUUID().toString(),
                title = trimmedTitle,
                eventIds = selectedEventIds.toList(),
                coverEventId = album?.coverEventId
                    ?.takeIf { it in selectedEventIds }
                    ?: selectedEventIds.firstOrNull(),
                createdAtEpochMillis = album?.createdAtEpochMillis ?: System.currentTimeMillis(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(if (album == null) "新建纪念册" else "编辑纪念册") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::saveAlbum,
                        enabled = title.isNotBlank() && selectedEventIds.isNotEmpty(),
                    ) {
                        Text("保存")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("纪念册名称") },
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "选择日子",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${selectedEventIds.size} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无可选择的日子",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                ) {
                    items(events, key = { it.id }) { event ->
                        AlbumEventPickerRow(
                            event = event,
                            selected = event.id in selectedEventIds,
                            onToggle = { toggleEvent(event.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumBookCard(
    album: DayAlbum,
    events: List<DayEvent>,
    cover: DayEvent?,
    onClick: () -> Unit,
) {
    val coverImage = cover?.backgroundImage
    val coverBitmap = rememberLocalImageBitmap(
        image = coverImage,
        maxDecodeLongEdgePx = 768,
    )
    val hasCover = coverImage != null && coverBitmap != null

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 18.dp,
            bottomEnd = 18.dp,
            bottomStart = 4.dp,
        ),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverBitmap != null && coverImage != null) {
                LocalImageViewport(
                    bitmap = coverBitmap,
                    transform = coverImage.transformFor(ImagePlacementTarget.DETAIL),
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.74f),
                                ),
                            ),
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(7.dp)
                    .fillMaxSize()
                    .background(
                        if (hasCover) Color.Black.copy(alpha = 0.32f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.74f),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = if (hasCover) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (hasCover) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "${events.size} 个日子",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasCover) {
                            Color.White.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCardDeck(
    events: List<DayEvent>,
    today: LocalDate,
    locale: Locale,
    currentIndex: Int,
    onCurrentIndexChange: (Int) -> Unit,
    onLongPressCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val currentEvent = events.getOrNull(currentIndex)
        val availableHeight = (maxHeight - 8.dp).coerceAtLeast(280.dp)
        val maxCardWidth = maxWidth * 0.98f
        val imageAspectRatio = currentEvent
            ?.backgroundImage
            ?.let { image ->
                adaptiveEventImagePreviewAspectRatio(
                    image = image,
                    type = EventImageCardType.DETAIL,
                )
            }
            ?: 0.76f

        val idealHeight = maxCardWidth / imageAspectRatio
        val cardWidth = if (idealHeight > availableHeight) {
            (availableHeight * imageAspectRatio).coerceAtMost(maxCardWidth)
        } else {
            maxCardWidth
        }
        val cardHeight = if (idealHeight > availableHeight) {
            availableHeight
        } else {
            idealHeight
        }

        AlbumStackBackPlate(
            strong = true,
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
                .offset(x = 16.dp, y = 16.dp)
                .graphicsLayer {
                    scaleX = 0.985f
                    scaleY = 0.985f
                    rotationZ = 3.8f
                    alpha = 0.86f
                },
        )
        AlbumStackBackPlate(
            strong = false,
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
                .offset(x = -14.dp, y = 28.dp)
                .graphicsLayer {
                    scaleX = 0.955f
                    scaleY = 0.955f
                    rotationZ = -4.2f
                    alpha = 0.72f
                },
        )

        currentEvent?.let { event ->
            AlbumStackPhotoCard(
                event = event,
                today = today,
                locale = locale,
                modifier = Modifier.size(width = cardWidth, height = cardHeight),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(currentIndex, events.size) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragCancel = { totalDragX = 0f },
                        onDragEnd = {
                            val threshold = size.width * 0.10f
                            when {
                                totalDragX < -threshold -> {
                                    onCurrentIndexChange(currentIndex + 1)
                                }
                                totalDragX > threshold -> {
                                    onCurrentIndexChange(currentIndex - 1)
                                }
                            }
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        },
                    )
                }
                .pointerInput(currentIndex, events.size) {
                    detectTapGestures(onLongPress = { onLongPressCurrent() })
                },
        )
    }
}

@Composable
private fun AlbumStackBackPlate(
    strong: Boolean,
    modifier: Modifier = Modifier,
) {
    val startColor = if (strong) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val endColor = if (strong) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = startColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (strong) 8.dp else 5.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(startColor, endColor),
                    ),
                ),
        )
    }
}
@Composable
private fun AlbumStackPhotoCard(
    event: DayEvent,
    today: LocalDate,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val image = event.backgroundImage
    val bitmap = rememberLocalImageBitmap(
        image = image,
        maxDecodeLongEdgePx = 1280,
    )
    val hasImage = image != null && bitmap != null
    val delta = DayMath.signedDays(event, today)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (image != null && bitmap != null) {
                LocalImageViewport(
                    bitmap = bitmap,
                    transform = image.transformFor(ImagePlacementTarget.DETAIL),
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.06f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.70f),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = event.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (hasImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        color = if (hasImage) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                        contentColor = if (hasImage) Color.White else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(99.dp),
                    ) {
                        Text(
                            text = dayDistanceLabel(delta),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = DateFormatting.longDate(DayMath.effectiveDate(event, today), locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasImage) {
                        Color.White.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.70f)
                    },
                )
            }
        }
    }
}
@Composable
private fun AlbumPageIndicator(
    count: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == currentIndex
            val dotSize by animateDpAsState(
                targetValue = if (selected) 9.dp else 7.dp,
                label = "album-dot-size",
            )
            val dotColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                label = "album-dot-color",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(RoundedCornerShape(99.dp))
                    .background(dotColor),
            )
        }
    }
}

@Composable
private fun AlbumEventPickerRow(
    event: DayEvent,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val locale = currentJavaLocale()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(DateFormatting.compactDate(event.date, locale))
                        append(" · ")
                        append(event.category.ifBlank { "未分类" })
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlbumSelectionToolbar(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "移出纪念册")
            }
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "编辑日子")
            }
            Button(
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

private fun wrappedAlbumIndex(index: Int, count: Int): Int {
    if (count <= 0) return 0
    val remainder = index % count
    return if (remainder < 0) remainder + count else remainder
}
private fun dayDistanceLabel(delta: Long): String {
    return when {
        delta == 0L -> "今天"
        delta > 0L -> "还有 ${abs(delta)} 天"
        else -> "已经 ${abs(delta)} 天"
    }
}
