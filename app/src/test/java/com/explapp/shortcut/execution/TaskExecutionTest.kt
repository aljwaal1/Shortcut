package com.explapp.shortcut.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExecutionTest {
    @Test
    fun successStoresDurationAndHumanReadableStatus() {
        val result = TaskExecutionResult.success("Open Maps", startedAtMs = 1_000L, finishedAtMs = 2_650L)
        assertEquals(TaskExecutionStatus.SUCCESS, result.status)
        assertEquals(1_650L, result.durationMs)
        assertTrue(result.summaryEn.contains("Completed"))
        assertTrue(result.summaryAr.contains("تم"))
    }

    @Test
    fun failureStoresReasonAndDuration() {
        val result = TaskExecutionResult.failure("Open Maps", "App not found", startedAtMs = 1_000L, finishedAtMs = 1_420L)
        assertEquals(TaskExecutionStatus.FAILURE, result.status)
        assertEquals("App not found", result.reason)
        assertEquals(420L, result.durationMs)
    }
}
