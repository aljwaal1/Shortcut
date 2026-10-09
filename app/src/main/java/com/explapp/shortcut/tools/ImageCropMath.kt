package com.explapp.shortcut.tools

data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object ImageCropMath {
    fun centerCrop(width: Int, height: Int, targetRatio: Float): CropRect {
        require(width > 0 && height > 0)
        require(targetRatio > 0f)
        val sourceRatio = width.toFloat() / height.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropWidth = (height * targetRatio).toInt().coerceIn(1, width)
            CropRect(
                left = (width - cropWidth) / 2,
                top = 0,
                width = cropWidth,
                height = height,
            )
        } else {
            val cropHeight = (width / targetRatio).toInt().coerceIn(1, height)
            CropRect(
                left = 0,
                top = (height - cropHeight) / 2,
                width = width,
                height = cropHeight,
            )
        }
    }
}
