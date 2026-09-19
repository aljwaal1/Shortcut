package com.explapp.shortcut.tools

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageOrientationTest {
    @Test
    fun mapsExifRotationToDisplayRotation() {
        assertEquals(0, ImageOrientation.rotationDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(90, ImageOrientation.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, ImageOrientation.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, ImageOrientation.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun identifiesMirroredExifValues() {
        assertEquals(true, ImageOrientation.isMirrored(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(true, ImageOrientation.isMirrored(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(false, ImageOrientation.isMirrored(ExifInterface.ORIENTATION_NORMAL))
    }
}
