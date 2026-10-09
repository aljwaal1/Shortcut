package com.explapp.shortcut.tools

import kotlin.math.roundToInt

object ScreenFrameReadiness {
    fun isLikelyBlank(sampledArgb: IntArray): Boolean {
        if (sampledArgb.isEmpty()) return false

        var nearWhite = 0
        var nearBlack = 0
        var minLuma = 255
        var maxLuma = 0

        sampledArgb.forEach { color ->
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            val luma = (red * 0.2126 + green * 0.7152 + blue * 0.0722).roundToInt()
            minLuma = minOf(minLuma, luma)
            maxLuma = maxOf(maxLuma, luma)
            if (luma >= 245) nearWhite++
            if (luma <= 10) nearBlack++
        }

        val whiteRatio = nearWhite.toDouble() / sampledArgb.size
        val blackRatio = nearBlack.toDouble() / sampledArgb.size
        val lowContrast = maxLuma - minLuma <= 18
        return lowContrast && (whiteRatio >= 0.92 || blackRatio >= 0.92)
    }
}
