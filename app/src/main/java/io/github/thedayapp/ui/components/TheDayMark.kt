package io.github.thedayapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
