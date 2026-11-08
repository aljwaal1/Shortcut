package com.explapp.shortcut.automation.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineRetryPolicyTest {
    private fun waiting(count: String) = RoutineAction(
        RoutineActionType.WAIT, "100",
        parameters = mapOf("retryCount" to count),
    )

    @Test fun retriesTransientWaitingFailure() {
        var calls = 0
        val result = RoutineRetryPolicy.run(waiting("2"), RoutineActionRunner { a ->
            calls++
            if (calls < 3) RoutineActionResult.failure(a, "temporary")
            else RoutineActionResult.success(a)
        })
        assertEquals(3, calls)
        assertEquals(RoutineActionStatus.SUCCESS, result.status)
    }

    @Test fun neverRetriesExternalDeliveryEvenWithUnsafeParameters() {
        var calls = 0
        val send = RoutineAction(RoutineActionType.SEND_TELEGRAM_BOT, "", "message",
            parameters = mapOf("retryCount" to "3"))
        assertEquals(false, RoutineRetryPolicy.valid(send))
        RoutineRetryPolicy.run(send, RoutineActionRunner { action ->
            calls++
            RoutineActionResult.failure(action, "uncertain")
        })
        assertEquals(1, calls)
    }

    @Test fun preparedOutcomeIsNeverRetried() {
        var calls = 0
        val result = RoutineRetryPolicy.run(waiting("3"), RoutineActionRunner { a ->
            calls++
            RoutineActionResult.prepared(a)
        })
        assertEquals(1, calls)
        assertEquals(RoutineActionStatus.PREPARED, result.status)
    }

    @Test fun rejectsExcessiveRetryCount() {
        assertEquals(false, RoutineRetryPolicy.valid(waiting("999")))
        assertEquals(true, RoutineRetryPolicy.valid(waiting("3")))
    }
}
