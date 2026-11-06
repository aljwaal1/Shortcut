package com.explapp.shortcut.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFrameReadinessTest {
    @Test
    fun uniformWhiteFrameIsRejected() {
        val white = IntArray(165) { 0xFFFFFFFF.toInt() }
        assertTrue(ScreenFrameReadiness.isLikelyBlank(white))
    }

    @Test
    fun uniformBlackFrameIsRejected() {
        val black = IntArray(165) { 0xFF000000.toInt() }
        assertTrue(ScreenFrameReadiness.isLikelyBlank(black))
    }

    @Test
    fun frameWithVisibleContentIsAccepted() {
        val pixels = IntArray(165) { 0xFFFFFFFF.toInt() }
        for (i in 0 until 30) {
            pixels[i * 5 % pixels.size] = 0xFF202020.toInt()
        }
        assertFalse(ScreenFrameReadiness.isLikelyBlank(pixels))
    }
}
