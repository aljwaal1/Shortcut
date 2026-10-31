package com.explapp.shortcut.automation.routines

import com.explapp.shortcut.execution.TaskExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineHistoryMappingTest {
    @Test
    fun mapsPreparedRoutineToPreparedHistoryEntry() {
        val result = RoutineRunResult(
            routineId = "r1",
            routineName = "Morning",
            status = RoutineRunStatus.PREPARED,
            actionResults = emptyList(),
            startedAtMs = 100,
            finishedAtMs = 150,
        )

        val history = result.toTaskExecutionResult()

        assertEquals("Morning", history.taskName)
        assertEquals(TaskExecutionStatus.PREPARED, history.status)
        assertEquals(100, history.startedAtMs)
        assertEquals(150, history.finishedAtMs)
    }

    @Test
    fun mapsFailureReasonFromFirstFailedAction() {
        val action = RoutineAction(RoutineActionType.OPEN_APP, "missing")
        val result = RoutineRunResult(
            routineId = "r2",
            routineName = "Broken",
            status = RoutineRunStatus.FAILED,
            actionResults = listOf(RoutineActionResult.failure(action, "Target is unavailable")),
            startedAtMs = 10,
            finishedAtMs = 20,
        )

        val history = result.toTaskExecutionResult()

        assertEquals(TaskExecutionStatus.FAILURE, history.status)
        assertEquals("Target is unavailable", history.reason)
    }
    @Test
    fun includesStepStatusAndFailureReasonInDetails() {
        val open = RoutineAction(RoutineActionType.OPEN_APP, "pkg")
        val screenshot = RoutineAction(
            RoutineActionType.OPEN_APP_SCREENSHOT,
            "pkg",
            "3000",
            parameters = mapOf("stampDateTime" to "true"),
        )
        val result = RoutineRunResult(
            routineId = "r3",
            routineName = "Daily capture",
            status = RoutineRunStatus.FAILED,
            actionResults = listOf(
                RoutineActionResult.success(open),
                RoutineActionResult.failure(screenshot, "Capture timed out"),
            ),
            startedAtMs = 1,
            finishedAtMs = 5,
        )

        val history = result.toTaskExecutionResult()

        assertEquals(2, history.details.size)
        assertEquals("1. OPEN_APP = SUCCESS [package=pkg]", history.details[0])
        assertEquals("2. OPEN_APP_SCREENSHOT = FAILURE [package=pkg, delayMs=3000, stamp=true, persistent=false] — Capture timed out", history.details[1])
    }
}

