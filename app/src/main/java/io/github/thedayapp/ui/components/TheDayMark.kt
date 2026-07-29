package io.github.thedayapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TheDayMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.075f
        val center = Offset(this.size.width / 2f, this.size.height * 0.42f)
        val radius = this.size.minDimension * 0.24f
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.18f, this.size.height * 0.70f),
            end = Offset(this.size.width * 0.82f, this.size.height * 0.70f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.50f, this.size.height * 0.70f),
            end = Offset(this.size.width * 0.50f, this.size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun TodayCalendarMark(
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayOfWeekAbbrev = when (today.dayOfWeek) {
        DayOfWeek.MONDAY -> "MON"
        DayOfWeek.TUESDAY -> "TUE"
        DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"
        DayOfWeek.FRIDAY -> "FRI"
        DayOfWeek.SATURDAY -> "SAT"
        DayOfWeek.SUNDAY -> "SUN"
    }

    val dayOfWeekChinese = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)

    Surface(
        modifier = modifier
            .size(width = 38.dp, height = 42.dp)
            .semantics {
                contentDescription = "今天是${dayOfWeekChinese}，${today.dayOfMonth} 日，打开日历"
                role = androidx.compose.ui.semantics.Role.Button
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayOfWeekAbbrev,
                    modifier = Modifier.offset(y = -0.5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = today.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
