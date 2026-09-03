package com.kuyermqi.quotawidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.kuyermqi.quotawidget.domain.formatTokenCount

/**
 * Token curve rendered into a plain [Bitmap] so Glance can display it via
 * [androidx.glance.ImageProvider] (Glance has no Canvas composable).
 * Draw at ~2x the display size for crisp scaling.
 */
object TokenCurveBitmap {

    /** Day-mode line; night mode picks a lighter tint of the default seed. */
    fun lineColor(nightMode: Boolean): Int =
        if (nightMode) 0xFFCCC2DC.toInt() else 0xFF6750A4.toInt()

    fun build(
        values: List<Long>,
        widthPx: Int,
        heightPx: Int,
        lineColor: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = (heightPx / 22f).coerceIn(2f, 5f)
        }
        if (values.size < 2) {
            canvas.drawLine(
                0f,
                heightPx - 1f,
                widthPx.toFloat(),
                heightPx - 1f,
                stroke,
            )
            return bitmap
        }
        val maxValue = values.max().coerceAtLeast(1L)
        val stepX = widthPx.toFloat() / (values.size - 1)
        fun yAt(value: Long): Float =
            heightPx * (1f - value.toFloat() / maxValue)

        val line = Path().apply {
            moveTo(0f, yAt(values[0]))
            for (i in 1 until values.size) {
                lineTo(i * stepX, yAt(values[i]))
            }
        }
        val area = Path(line).apply {
            lineTo(widthPx.toFloat(), heightPx.toFloat())
            lineTo(0f, heightPx.toFloat())
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                heightPx.toFloat(),
                (lineColor and 0x00FFFFFF) or 0x48000000,
                0x00000000,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(area, fill)
        canvas.drawPath(line, stroke)
        return bitmap
    }

    /** Peak annotation text used above widget charts. */
    fun peakLabel(values: List<Long>): String? =
        values.takeIf { it.isNotEmpty() }?.let { formatTokenCount(it.max()) }
}
