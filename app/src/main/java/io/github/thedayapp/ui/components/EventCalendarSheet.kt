package io.github.thedayapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.domain.CalendarEventDates
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCalendarSheet(
    today: LocalDate,
    events: List<DayEvent>,
    onDismissRequest: () -> Unit,
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        currentMonth = currentMonth.minusMonths(1)
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "上一个月",
                    )
                }

                Text(
                    text = "${currentMonth.year}年${currentMonth.monthValue}月",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )

                IconButton(
                    onClick = {
                        currentMonth = currentMonth.plusMonths(1)
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "下一个月",
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (dayOfWeek in 1..7) {
                    val dayName = when (dayOfWeek) {
                        1 -> "一"
                        2 -> "二"
                        3 -> "三"
                        4 -> "四"
                        5 -> "五"
                        6 -> "六"
                        7 -> "日"
                        else -> ""
                    }
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val eventDates = CalendarEventDates.datesInMonth(events, currentMonth)
            val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value

            for (week in 0..5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (day in 1..7) {
                        val position = week * 7 + day
                        val dayOfMonth = position - firstDayOfWeek + 1

                        val date = if (dayOfMonth in 1..currentMonth.lengthOfMonth()) {
                            currentMonth.atDay(dayOfMonth)
                        } else {
                            null
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val isToday = date == today
                                val hasEvent = date in eventDates

                                val backgroundColor = when {
                                    isToday && hasEvent -> MaterialTheme.colorScheme.primary
                                    hasEvent -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                }

                                val textColor = when {
                                    isToday && hasEvent -> MaterialTheme.colorScheme.onPrimary
                                    hasEvent -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                val borderModifier = if (isToday && !hasEvent) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(backgroundColor)
                                        .then(borderModifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}