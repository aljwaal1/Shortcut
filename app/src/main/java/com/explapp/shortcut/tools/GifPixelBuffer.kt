package com.explapp.shortcut.tools

/** Square's GIF encoder accepts pixel rows indexed as [y][x], including non-square frames. */
internal object GifPixelBuffer {
    fun rows(pixels: IntArray, width: Int, height: Int): Array<IntArray> {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
        return Array(height) { y -> IntArray(width) { x -> pixels[y * width + x] } }
    }
}
