package com.explapp.shortcut.tools

/** Separate non-recorded controls from the optional, intentionally recorded pointer. */
object VideoOverlayPolicy {
    /** Allow the capture consent activity to finish before recording the foreground app. */
    const val startAfterConsentDelayMs = 1_200L

    fun showControlBubble(recording: Boolean, capturePending: Boolean, compactMode: Boolean = false): Boolean =
        !capturePending && (!recording || compactMode)

    fun showPointer(recording: Boolean, pointerEnabled: Boolean, overlayAllowed: Boolean): Boolean =
        recording && pointerEnabled && overlayAllowed
}
