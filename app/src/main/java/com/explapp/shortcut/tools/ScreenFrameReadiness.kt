package com.explapp.shortcut.tools

import kotlin.math.roundToInt

object ScreenFrameReadiness {
    /**
     * Screens captured during app launch can be almost white while the system
     * status bar and overlays remain sharp. Only reject very pale frames with
     * almost no dark content; never alter image colors or brightness.
     */
    fun isLikelyWashedOut(sampledArgb: IntArray): Boolean {
        if (sampledArgb.size < 20) return false
        var pale = 0
        var dark = 0
        var totalLuma = 0.0
        sampledArgb.forEach { color ->
            val r = (color shr 16) and 255
            val g = (color shr 8) and 255
            val b = color and 255
            val luma = r * 0.2126 + g * 0.7152 + b * 0.0722
            totalLuma += luma
            if (luma >= 235 && minOf(r, g, b) >= 215) pale++
            if (luma < 105) dark++
        }
        val count = sampledArgb.size.toDouble()
        return pale / count >= 0.72 && dark / count <= 0.025 && totalLuma / count >= 229
    }

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
