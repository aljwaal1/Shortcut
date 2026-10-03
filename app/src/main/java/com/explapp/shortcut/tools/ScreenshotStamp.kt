package com.explapp.shortcut.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenshotStamp {
    fun apply(bitmap: Bitmap, nowMs: Long = System.currentTimeMillis()) {
        val canvas = Canvas(bitmap)
        val label = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(nowMs))
        val density = bitmap.width.coerceAtLeast(1) / 1080f
        val textSize = (34f * density).coerceIn(22f, 52f)
        val padding = (18f * density).coerceIn(12f, 28f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val metrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(label)
        val textHeight = metrics.bottom - metrics.top

        val right = bitmap.width - padding
        val bottom = bitmap.height - padding
        val left = (right - textWidth - padding * 2).coerceAtLeast(padding)
        val top = (bottom - textHeight - padding * 2).coerceAtLeast(padding)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 0, 0, 0)
        }
        canvas.drawRoundRect(left, top, right, bottom, padding * 0.55f, padding * 0.55f, background)
        canvas.drawText(label, left + padding, bottom - padding - metrics.bottom, textPaint)
    }

    fun fileName(nowMs: Long = System.currentTimeMillis()): String =
        "Screenshot_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(nowMs)) + ".png"
}
