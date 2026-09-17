package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineValidationTest {
    @Test
    fun rejectsIncompleteTemplateActions() {
        val routine = AutomationRoutine(
            name = "Morning",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "07:30"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_APP, "")),
        )
        assertFalse(routine.isValid())
    }

    @Test
    fun rejectsPreparedMessageWithoutBody() {
        val routine = AutomationRoutine(
            name = "Message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.PREPARE_TELEGRAM, "user", "")),
        )
        assertFalse(routine.isValid())
    }

    @Test
    fun validatesTimeAndBatteryTriggerValues() {
        val action = RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Hello")
        assertTrue(AutomationRoutine(name = "Time", trigger = RoutineTrigger(RoutineTriggerType.TIME, "23:59"), actions = listOf(action)).isValid())
        assertFalse(AutomationRoutine(name = "Bad time", trigger = RoutineTrigger(RoutineTriggerType.TIME, "25:00"), actions = listOf(action)).isValid())
        assertTrue(AutomationRoutine(name = "Battery", trigger = RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "20"), actions = listOf(action)).isValid())
        assertFalse(AutomationRoutine(name = "Bad battery", trigger = RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "0"), actions = listOf(action)).isValid())
    }
}
