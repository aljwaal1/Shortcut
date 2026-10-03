package com.explapp.shortcut.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryRuleTest {
    @Test
    fun belowThresholdFiresOnlyWhenCrossingDown() {
        val rule = BatteryRule(threshold = 20, direction = BatteryDirection.BELOW)
        assertFalse(rule.crossed(previous = 25, current = 24))
        assertTrue(rule.crossed(previous = 21, current = 20))
        assertFalse(rule.crossed(previous = 19, current = 18))
    }

    @Test
    fun aboveThresholdFiresOnlyWhenCrossingUp() {
        val rule = BatteryRule(threshold = 80, direction = BatteryDirection.ABOVE)
        assertFalse(rule.crossed(previous = 70, current = 75))
        assertTrue(rule.crossed(previous = 79, current = 80))
        assertFalse(rule.crossed(previous = 85, current = 90))
    }
}
