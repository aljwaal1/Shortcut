package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNarratorCatalogTest {
    @Test fun allActionsHaveDescriptions() {
        for (type in RoutineActionType.entries) {
            val routine = AutomationRoutine(name = "Demo", trigger = RoutineTrigger(RoutineTriggerType.MANUAL), actions = listOf(RoutineAction(type, "example")))
            assertTrue(RoutineNarrator.describe(routine, false).isNotBlank())
        }
    }
}
