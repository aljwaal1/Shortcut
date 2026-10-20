package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineReviewTest {
    private fun flow(actions: List<RoutineAction>) = AutomationRoutine(
        name = "Preview",
        trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
        actions = actions,
    )

    @Test fun rejectsEmptyWorkflowsWithoutExecutingAnything() {
        val report = RoutineReview.inspect(flow(emptyList()))
        assertFalse(report.canRun)
        assertTrue(report.findings.any { it.code == "NO_ACTIONS" })
    }

    @Test fun warnsAboutUnreachableSteps() {
        val report = RoutineReview.inspect(flow(listOf(
            RoutineAction(RoutineActionType.STOP_SHORTCUT, ""),
            RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "never"),
        )))
        assertTrue(report.canRun)
        assertEquals(1, report.warnings.size)
        assertEquals("ACTIONS_AFTER_STOP_UNREACHABLE", report.warnings[0].code)
    }

    @Test fun warnsAboutUsernameAndManualHandoffs() {
        val report = RoutineReview.inspect(flow(listOf(
            RoutineAction(RoutineActionType.SEND_TELEGRAM_BOT, "", "hello",
                parameters = mapOf("botToken" to "sample", "chatId" to "@exampleuser")),
            RoutineAction(RoutineActionType.PREPARE_WHATSAPP, "12345", "hello"),
        )))
        assertTrue(report.canRun)
        assertEquals(2, report.warnings.size)
    }

    @Test fun rejectsInvalidActionWithIndex() {
        val report = RoutineReview.inspect(flow(listOf(RoutineAction(RoutineActionType.WAIT, "invalid"))))
        assertFalse(report.canRun)
        assertEquals(0, report.findings.first().actionIndex)
    }
}
