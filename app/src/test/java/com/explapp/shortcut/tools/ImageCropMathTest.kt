package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCropMathTest {
    @Test
    fun wideImageCropsWidthForSquare() {
        val crop = ImageCropMath.centerCrop(width = 1600, height = 900, targetRatio = 1f)
        assertEquals(CropRect(left = 350, top = 0, width = 900, height = 900), crop)
    }

    @Test
    fun tallImageCropsHeightForFourByThree() {
        val crop = ImageCropMath.centerCrop(width = 900, height = 1600, targetRatio = 4f / 3f)
        assertEquals(CropRect(left = 0, top = 462, width = 900, height = 675), crop)
    }
}
