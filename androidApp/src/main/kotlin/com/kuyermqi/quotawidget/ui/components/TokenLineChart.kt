package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.formatTokenCount

/**
 * Minimal token curve: smoothed line with a soft area fill, peak annotation,
 * and a start/end range row. Values are bucket totals, oldest first.
 */
@Composable
fun TokenLineChart(
    values: List<Long>,
    title: String,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (values.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.new_api_admin_chart_peak,
                        formatTokenCount(values.max()),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val lineColor = MaterialTheme.colorScheme.primary
        val fillColorTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            if (values.size < 2) {
                // Not enough data yet: draw a flat baseline so the slot is visible.
                drawLine(
                    color = lineColor.copy(alpha = 0.35f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                return@Canvas
            }
            val maxValue = values.max().coerceAtLeast(1L)
            val stepX = size.width / (values.size - 1)
            fun pointAt(index: Int): Offset =
                Offset(
                    x = index * stepX,
                    y = size.height * (1f - values[index].toFloat() / maxValue),
                )
            val linePath = Path().apply {
                moveTo(0f, pointAt(0).y)
                for (i in 1 until values.size) {
                    lineTo(i * stepX, pointAt(i).y)
                }
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColorTop, Color.Transparent),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = startLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = endLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
