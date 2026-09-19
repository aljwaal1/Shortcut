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
}
