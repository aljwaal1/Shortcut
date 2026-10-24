package com.explapp.shortcut.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenshotStamp {
    fun apply(bitmap: Bitmap, nowMs: Long = System.currentTimeMillis(), name: String = "") {
        val canvas = Canvas(bitmap)
        val date = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date(nowMs))
        val safeName = name.trim().replace(Regex("[\\r\\n\\t]"), " ").take(80)
        val label = if (safeName.isBlank()) date else "$safeName  •  $date"
        val density = bitmap.width.coerceAtLeast(1) / 1080f
        val textSize = (32f * density).coerceIn(18f, 48f)
        val padding = (18f * density).coerceIn(10f, 28f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val metrics = textPaint.fontMetrics
        // Scale long labels to fit inside narrow or rotated screenshots.
        val maxTextWidth = (bitmap.width - padding * 4).coerceAtLeast(1f)
        while (textPaint.measureText(label) > maxTextWidth && textPaint.textSize > 12f) {
            textPaint.textSize = (textPaint.textSize - 1f).coerceAtLeast(12f)
        }
        val textWidth = textPaint.measureText(label)
        val textHeight = metrics.bottom - metrics.top

        val right = bitmap.width - padding
        val systemBarInset = (bitmap.height * 0.035f).coerceAtLeast(padding)
        val bottom = bitmap.height - systemBarInset
        val left = (right - textWidth - padding * 2).coerceAtLeast(padding)
        val top = (bottom - textHeight - padding * 2).coerceAtLeast(padding)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 18, 24, 34)
        }
        canvas.drawRoundRect(left, top, right, bottom, padding * 0.55f, padding * 0.55f, background)
        canvas.drawText(label, left + padding, bottom - padding - metrics.bottom, textPaint)
    }

    fun fileName(nowMs: Long = System.currentTimeMillis()): String =
        "Screenshot_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(nowMs)) + ".png"
}
