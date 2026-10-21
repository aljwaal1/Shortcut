package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutineWorkTest {
    @Test
    fun telegramBotRoutineRequiresConnectivityBeforeWorkerStarts() {
        val routine = AutomationRoutine(
            name = "Network",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.SEND_TELEGRAM_BOT, "", "Hello",
                parameters = mapOf("botToken" to "test", "chatId" to "123"))),
        )
        assertEquals(true, RoutineWork.requiresNetwork(routine))
        assertEquals(false, RoutineWork.requiresNetwork(routine.copy(
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Local")))))
    }

    @Test
    fun uniqueWorkNameIsStablePerRoutine() {
        assertEquals(
            RoutineWork.uniqueName("routine-a"),
            RoutineWork.uniqueName("routine-a"),
        )
        assertNotEquals(
            RoutineWork.uniqueName("routine-a"),
            RoutineWork.uniqueName("routine-b"),
        )
    }
    @Test
    fun oneShotTimeRoutineIsDisabledAfterAttempt() {
        val once = AutomationRoutine(
            name = "Once",
            trigger = RoutineTrigger(
                type = RoutineTriggerType.TIME,
                value = "08:00",
                repeat = RoutineRepeat.ONCE,
                repeatValue = "2026-10-02",
            ),
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Done")),
        )
        val daily = once.copy(
            trigger = once.trigger.copy(repeat = RoutineRepeat.DAILY, repeatValue = ""),
        )

        fun outcome(status: RoutineRunStatus) = RoutineRunResult(
            once.id, once.name, status, emptyList(), 100L, 200L,
        )
        assertEquals(true, RoutineWork.shouldDisableAfterAttempt(once, outcome(RoutineRunStatus.SUCCESS)))
        assertEquals(false, RoutineWork.shouldDisableAfterAttempt(daily, outcome(RoutineRunStatus.SUCCESS)))
        assertEquals(false, RoutineWork.shouldDisableAfterAttempt(once, null))
        assertEquals(false, RoutineWork.shouldDisableAfterAttempt(once, outcome(RoutineRunStatus.FAILED)))
        assertEquals(false, RoutineWork.shouldDisableAfterAttempt(once, outcome(RoutineRunStatus.PREPARED)))
    }
}

