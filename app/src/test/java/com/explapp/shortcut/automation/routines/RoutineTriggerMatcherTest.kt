package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTriggerMatcherTest {
    @Test
    fun matchesTimeAndChargerAndBatteryThresholds() {
        assertTrue(RoutineTriggerMatcher.matches(RoutineTrigger(RoutineTriggerType.TIME, "07:30"), RoutineEvent(RoutineTriggerType.TIME, "07:30")))
        assertFalse(RoutineTriggerMatcher.matches(RoutineTrigger(RoutineTriggerType.TIME, "07:30"), RoutineEvent(RoutineTriggerType.TIME, "07:31")))
        assertTrue(RoutineTriggerMatcher.matches(RoutineTrigger(RoutineTriggerType.CHARGER_CONNECTED), RoutineEvent(RoutineTriggerType.CHARGER_CONNECTED)))
        assertTrue(RoutineTriggerMatcher.matches(RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "20"), RoutineEvent(RoutineTriggerType.BATTERY_BELOW, "15")))
        assertFalse(RoutineTriggerMatcher.matches(RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "20"), RoutineEvent(RoutineTriggerType.BATTERY_BELOW, "50")))
    }

    @Test
    fun pausedRoutineNeverMatches() {
        val routine = AutomationRoutine(
            name = "Paused",
            isEnabled = false,
            trigger = RoutineTrigger(RoutineTriggerType.CHARGER_CONNECTED),
            actions = emptyList(),
        )
        assertFalse(RoutineTriggerMatcher.matches(routine, RoutineEvent(RoutineTriggerType.CHARGER_CONNECTED)))
    }

    @Test
    fun enabledButInvalidRoutineNeverMatches() {
        val routine = AutomationRoutine(
            name = "Incomplete template",
            isEnabled = true,
            trigger = RoutineTrigger(RoutineTriggerType.CHARGER_CONNECTED),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_APP, "")),
        )
        assertFalse(RoutineTriggerMatcher.matches(routine, RoutineEvent(RoutineTriggerType.CHARGER_CONNECTED)))
    }
}
