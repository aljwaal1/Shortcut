package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineExecutorTest {
    @Test
    fun executesActionsInOrderAndKeepsGoingAfterPreparedResult() {
        val seen = mutableListOf<RoutineActionType>()
        val runner = object : RoutineActionRunner {
            override fun run(action: RoutineAction): RoutineActionResult {
                seen += action.type
                return if (action.type == RoutineActionType.PREPARE_WHATSAPP) {
                    RoutineActionResult.prepared(action)
                } else {
                    RoutineActionResult.success(action)
                }
            }
        }
        val routine = AutomationRoutine(
            id = "r1",
            name = "Morning",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(
                RoutineAction(RoutineActionType.OPEN_APP, "maps"),
                RoutineAction(RoutineActionType.PREPARE_WHATSAPP, "+4670", "Hi"),
                RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Done"),
            ),
        )

        val result = RoutineExecutor(runner).execute(routine, startedAtMs = 100L, finishedAtMs = 200L)

        assertEquals(listOf(RoutineActionType.OPEN_APP, RoutineActionType.PREPARE_WHATSAPP, RoutineActionType.SHOW_NOTIFICATION), seen)
        assertEquals(RoutineRunStatus.PREPARED, result.status)
        assertEquals(3, result.actionResults.size)
    }

    @Test
    fun stopShortcutPreventsLaterActions() {
        val seen = mutableListOf<RoutineActionType>()
        val runner = object : RoutineActionRunner {
            override fun run(action: RoutineAction): RoutineActionResult {
                seen += action.type
                return RoutineActionResult.success(action)
            }
        }
        val routine = AutomationRoutine(
            name = "Stop flow",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(
                RoutineAction(RoutineActionType.SET_VARIABLE, "name", "value"),
                RoutineAction(RoutineActionType.STOP_SHORTCUT, ""),
                RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "must not run"),
            ),
        )

        val result = RoutineExecutor(runner).execute(routine)

        assertEquals(listOf(RoutineActionType.SET_VARIABLE, RoutineActionType.STOP_SHORTCUT), seen)
        assertEquals(RoutineRunStatus.SUCCESS, result.status)
        assertEquals(2, result.actionResults.size)
    }

    @Test
    fun fatalFailureStopsUnlessContinueOnErrorIsEnabled() {
        val seen = mutableListOf<RoutineActionType>()
        val runner = object : RoutineActionRunner {
            override fun run(action: RoutineAction): RoutineActionResult {
                seen += action.type
                return if (action.type == RoutineActionType.OPEN_URL) RoutineActionResult.failure(action, "bad url")
                else RoutineActionResult.success(action)
            }
        }
        val routine = AutomationRoutine(
            name = "Failure",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(
                RoutineAction(RoutineActionType.OPEN_URL, "bad"),
                RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "never"),
            ),
        )

        val result = RoutineExecutor(runner).execute(routine)

        assertEquals(listOf(RoutineActionType.OPEN_URL), seen)
        assertEquals(RoutineRunStatus.FAILED, result.status)
    }
}
