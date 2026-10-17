package com.explapp.shortcut.execution

object TaskExecutionHistory {
    fun append(
        existing: List<TaskExecutionResult>,
        result: TaskExecutionResult,
        maxRecords: Int = 200,
    ): List<TaskExecutionResult> {
        if (maxRecords <= 0) return emptyList()
        return (listOf(result) + existing).take(maxRecords)
    }

    fun filter(
        records: List<TaskExecutionResult>,
        status: TaskExecutionStatus?,
    ): List<TaskExecutionResult> = if (status == null) records else records.filter { it.status == status }
}
