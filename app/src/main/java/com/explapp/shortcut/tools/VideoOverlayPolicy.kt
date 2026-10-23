package com.explapp.shortcut.tools

/** Separate non-recorded controls from the optional, intentionally recorded pointer. */
object VideoOverlayPolicy {
    fun showControlBubble(recording: Boolean, capturePending: Boolean): Boolean =
        !recording && !capturePending

    fun showPointer(recording: Boolean, pointerEnabled: Boolean, overlayAllowed: Boolean): Boolean =
        recording && pointerEnabled && overlayAllowed
}
