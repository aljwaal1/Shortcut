package com.explapp.shortcut.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOverlayPolicyTest {
    @Test fun controlsHideBeforeCaptureAndRemainHiddenUntilFinished() {
        assertTrue(VideoOverlayPolicy.showControlBubble(recording = false, capturePending = false))
        assertFalse(VideoOverlayPolicy.showControlBubble(recording = false, capturePending = true))
        assertFalse(VideoOverlayPolicy.showControlBubble(recording = true, capturePending = false))
    }

    @Test fun pointerOnlyAppearsInRecordingWhenChosenAndAllowed() {
        assertFalse(VideoOverlayPolicy.showPointer(true, false, true))
        assertFalse(VideoOverlayPolicy.showPointer(false, true, true))
        assertFalse(VideoOverlayPolicy.showPointer(true, true, false))
        assertTrue(VideoOverlayPolicy.showPointer(true, true, true))
    }

    @Test fun compactControlsCanRemainVisibleDuringRecordingButNotWhenExplicitlyHidden() {
        assertTrue(VideoOverlayPolicy.showControlBubble(true, false, true))
        assertFalse(VideoOverlayPolicy.showControlBubble(true, true, true))
        assertTrue(VideoOverlayPolicy.showControlBubble(false, false, true))
    }
}
