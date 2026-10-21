package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineProgressCallbackTest {
    @Test fun sequentialReportsLastAttemptedStep() {
        val records = mutableListOf<Pair<Int, RoutineActionStatus>>()
        val r = AutomationRoutine(
            name = "Progress", trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(
                RoutineAction(RoutineActionType.WAIT, "100"),
                RoutineAction(RoutineActionType.WAIT, "100"),
            ),
        )
        RoutineExecutor(
            RoutineActionRunner { RoutineActionResult.success(it) },
            onProgress = { index, status -> records += index to status },
        ).execute(r)
        assertEquals(listOf(0, 1), records.map { it.first })
        assertEquals(true, records.all { it.second == RoutineActionStatus.SUCCESS })
    }

    @Test fun preparedStopsAndRecordsItsIndex() {
        val records = mutableListOf<Int>()
        val r = AutomationRoutine(
            name = "Pending", trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(
                RoutineAction(RoutineActionType.PREPARE_WHATSAPP, "123", "hello"),
                RoutineAction(RoutineActionType.WAIT, "100"),
            ),
        )
        val outcome = RoutineExecutor(
            RoutineActionRunner { RoutineActionResult.prepared(it) },
            onProgress = { index, _ -> records += index },
        ).execute(r)
        assertEquals(RoutineRunStatus.PREPARED, outcome.status)
        assertEquals(listOf(0), records)
    }
}
