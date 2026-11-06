package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GifRenderOptionsTest {
    @Test fun balancedKeepsReadableResolution() {
        assertEquals(960, GifRenderOptions.BALANCED.maxDimension)
        assertEquals(350L, GifRenderOptions.BALANCED.delayMillis)
    }

    @Test fun highQualityOffersLargerFrames() {
        assertEquals(1440, GifRenderOptions.HIGH.maxDimension)
    }

    @Test fun rejectsZeroSourceFrames() {
        assertThrows(IllegalArgumentException::class.java) {
            GifRenderOptions.validateFrames(0)
        }
    }
}
