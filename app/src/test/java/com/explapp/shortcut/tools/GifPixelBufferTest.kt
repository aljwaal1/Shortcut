package com.explapp.shortcut.tools

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GifPixelBufferTest {
    @Test fun portraitImageUsesRowsNotColumns() {
        val rows = GifPixelBuffer.rows(intArrayOf(1, 2, 3, 4, 5, 6), width = 2, height = 3)
        assertEquals(3, rows.size)
        assertArrayEquals(intArrayOf(1, 2), rows[0])
        assertArrayEquals(intArrayOf(3, 4), rows[1])
        assertArrayEquals(intArrayOf(5, 6), rows[2])
    }

    @Test fun landscapeImageKeepsAllPixels() {
        val rows = GifPixelBuffer.rows(intArrayOf(1, 2, 3, 4, 5, 6), width = 3, height = 2)
        assertEquals(2, rows.size)
        assertArrayEquals(intArrayOf(1, 2, 3), rows[0])
        assertArrayEquals(intArrayOf(4, 5, 6), rows[1])
    }
}
