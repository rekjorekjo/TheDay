package io.github.thedayapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.RepeatMode
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.ui.media.localImageAlignment
import io.github.thedayapp.ui.media.rememberLocalImageBitmap
import io.github.thedayapp.util.DateFormatting
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@Composable
fun HeroCard(
    event: DayEvent?,
    today: LocalDate,
    locale: Locale,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    emptyTitle: String,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    val imageReference = event?.backgroundImage
    val imageBitmap = rememberLocalImageBitmap(
        image = imageReference,
        maxDecodeLongEdgePx = 1280,
    )
    val hasBackgroundImage = imageReference != null && imageBitmap != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            if (hasBackgroundImage && imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = localImageAlignment(imageReference!!),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.Black.copy(alpha = 0.66f),
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

            if (event == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TheDayMark(size = 46.dp)
                    Text(
                        text = emptyTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                val delta = DayMath.signedDays(event, today)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = DateFormatting.longDate(
                                    DayMath.effectiveDate(event, today),
                                    locale,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasBackgroundImage) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (event.isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.VerticalAlignTop,
                                contentDescription = "已置顶",
                                tint = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    if (delta == 0L) {
                        Text(
                            text = "今天",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (delta > 0) "倒数" else "正数",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasBackgroundImage) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = abs(delta).toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = if (hasBackgroundImage) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "天",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasBackgroundImage) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(
    event: DayEvent,
    today: LocalDate,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val delta = DayMath.signedDays(event, today)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 68.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (event.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignTop,
                            contentDescription = "已置顶",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = DateFormatting.compactDate(
                        DayMath.effectiveDate(event, today),
                        locale,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.category.isNotBlank() || event.repeatMode == RepeatMode.YEARLY) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (event.category.isNotBlank()) {
                            MiniTag(event.category)
                        }
                        if (event.repeatMode == RepeatMode.YEARLY) {
                            MiniTag("每年", showRepeatIcon = true)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (delta == 0L) {
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = if (delta > 0) "倒数" else "正数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = abs(delta).toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "天",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, showRepeatIcon: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(99.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showRepeatIcon) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}