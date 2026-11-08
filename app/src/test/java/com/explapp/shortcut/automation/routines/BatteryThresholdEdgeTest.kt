package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryThresholdEdgeTest {
    @Test
    fun firesOnlyWhenCrossingFromAboveToAtOrBelowThreshold() {
        assertTrue(BatteryThresholdEdge.shouldFire(wasBelow = false, level = 20, threshold = 20))
        assertFalse(BatteryThresholdEdge.shouldFire(wasBelow = true, level = 19, threshold = 20))
        assertFalse(BatteryThresholdEdge.shouldFire(wasBelow = false, level = 21, threshold = 20))
    }

    @Test
    fun leavingBelowStateRearmsFutureCrossing() {
        assertFalse(BatteryThresholdEdge.isBelow(level = 25, threshold = 20))
        assertTrue(BatteryThresholdEdge.shouldFire(wasBelow = false, level = 20, threshold = 20))
    }
}
