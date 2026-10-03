package com.explapp.shortcut.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskExecutionHistoryTest {
    private fun result(index: Int, status: TaskExecutionStatus = TaskExecutionStatus.SUCCESS) =
        TaskExecutionResult("task-$index", status, if (status == TaskExecutionStatus.FAILURE) "reason-$index" else null, index.toLong(), index.toLong() + 10L)

    @Test
    fun appendIsNewestFirstAndRetainsOnlyNewest200() {
        var history = emptyList<TaskExecutionResult>()
        repeat(205) { history = TaskExecutionHistory.append(history, result(it)) }
        assertEquals(200, history.size)
        assertEquals("task-204", history.first().taskName)
        assertEquals("task-5", history.last().taskName)
    }

    @Test
    fun filtersByStatusWithoutChangingOrder() {
        val history = listOf(
            result(3, TaskExecutionStatus.FAILURE),
            result(2, TaskExecutionStatus.SUCCESS),
            result(1, TaskExecutionStatus.FAILURE),
        )
        val failures = TaskExecutionHistory.filter(history, TaskExecutionStatus.FAILURE)
        assertEquals(listOf("task-3", "task-1"), failures.map { it.taskName })
        assertEquals(history, TaskExecutionHistory.filter(history, null))
    }
}
