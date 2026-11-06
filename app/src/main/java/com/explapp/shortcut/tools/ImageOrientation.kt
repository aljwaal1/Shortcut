package com.explapp.shortcut.tools

import androidx.exifinterface.media.ExifInterface

object ImageOrientation {
    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    fun isMirrored(orientation: Int): Boolean = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
        ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE -> true
        else -> false
    }
}
