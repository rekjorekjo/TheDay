package io.github.thedayapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.ui.media.localImageAlignment
import io.github.thedayapp.ui.media.rememberLocalImageBitmap
import java.text.Collator
import java.util.Locale

private data class CategorySummary(
    val name: String,
    val eventCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    events: List<DayEvent>,
    categoryCovers: Map<String, LocalImageReference>,
    onOpenCategory: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val locale = Locale.getDefault()
    val categories = remember(events, locale) {
        val grouped = events
            .groupBy { event -> normalizedCategoryName(event.category) }
            .map { (name, list) ->
                CategorySummary(name = name, eventCount = list.size)
            }

        val collator = Collator.getInstance(locale)
        val sorted = grouped.sortedWith { a, b ->
            when {
                a.name == b.name -> 0
                a.name == UNCLASSIFIED_CATEGORY_NAME -> 1
                b.name == UNCLASSIFIED_CATEGORY_NAME -> -1
                else -> collator.compare(a.name, b.name)
            }
        }
        sorted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("分类") },
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                items(categories) { category ->
                    CategoryBookCard(
                        category = category,
                        cover = categoryCovers[category.name],
                        onClick = { onOpenCategory(category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBookCard(
    category: CategorySummary,
    cover: LocalImageReference?,
    onClick: () -> Unit,
) {
    val coverBitmap = rememberLocalImageBitmap(
        image = cover,
        maxDecodeLongEdgePx = 768,
    )
    val hasCover = cover != null && coverBitmap != null

    val mainContentColor = if (hasCover) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val secondaryContentColor = if (hasCover) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    }

    val iconTint = if (hasCover) {
        Color.White.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
            bottomStart = 4.dp,
        ),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCover && coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = localImageAlignment(cover!!),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.16f),
                                    Color.Black.copy(alpha = 0.30f),
                                    Color.Black.copy(alpha = 0.72f),
                                ),
                            ),
                        ),
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .fillMaxHeight()
                        .background(
                            if (hasCover) {
                                Color.Black.copy(alpha = 0.32f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            },
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = iconTint,
                    )
                    Column {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = mainContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${category.eventCount} 个日子",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryContentColor,
                        )
                    }
                }
            }
        }
    }
}