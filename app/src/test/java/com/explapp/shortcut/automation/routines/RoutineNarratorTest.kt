package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNarratorTest {
    @Test fun timeAndOrder() {
        val routine = AutomationRoutine(name = "Example", trigger = RoutineTrigger(RoutineTriggerType.TIME, "08:30"), actions = listOf(RoutineAction(RoutineActionType.WAIT, "1000"), RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Hi")))
        val text = RoutineNarrator.describe(routine, true)
        assertTrue(text.contains("08:30"))
        assertTrue(text.contains("1."))
        assertTrue(text.contains("2."))
    }
}
