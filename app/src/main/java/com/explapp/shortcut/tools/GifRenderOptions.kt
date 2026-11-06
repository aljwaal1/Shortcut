package com.explapp.shortcut.tools

/** Resolution/speed pairs shared by the mobile GIF creation flow. */
enum class GifRenderOptions(val maxDimension: Int, val delayMillis: Long) {
    COMPACT(480, 350),
    BALANCED(960, 350),
    HIGH(1440, 350);

    companion object {
        fun validateFrames(count: Int) {
            require(count in 2..20) { "Choose between 2 and 20 images" }
        }
    }
}
