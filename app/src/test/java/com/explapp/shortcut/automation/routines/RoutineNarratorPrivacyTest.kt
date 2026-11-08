package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNarratorPrivacyTest {
    @Test fun preparedTelegramIsNotAutoSent() {
        val routine = AutomationRoutine(name = "Demo", trigger = RoutineTrigger(RoutineTriggerType.MANUAL), actions = listOf(RoutineAction(RoutineActionType.PREPARE_TELEGRAM, "@friend", "MESSAGE_SENTINEL")))
        val preview = RoutineNarrator.describe(routine, true)
        assertTrue(preview.contains("دون إرسال تلقائي"))
        assertFalse(preview.contains("MESSAGE_SENTINEL"))
    }
}
