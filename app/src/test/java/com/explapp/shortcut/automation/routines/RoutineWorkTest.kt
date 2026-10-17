package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutineWorkTest {
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

        assertEquals(true, RoutineWork.shouldDisableAfterAttempt(once))
        assertEquals(false, RoutineWork.shouldDisableAfterAttempt(daily))
    }
}

