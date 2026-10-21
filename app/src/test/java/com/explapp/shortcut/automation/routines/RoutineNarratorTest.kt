package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNarratorTest {
    @Test fun manualPreview() {
        val routine = AutomationRoutine(name = "Demo", trigger = RoutineTrigger(RoutineTriggerType.MANUAL), actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Hi")))
        assertTrue(RoutineNarrator.describe(routine, true).contains("يدوي"))
    }
}
