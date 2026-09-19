package com.explapp.shortcut.execution

enum class TaskExecutionStatus { SUCCESS, PREPARED, FAILURE }

data class TaskExecutionResult(
    val taskName: String,
    val status: TaskExecutionStatus,
    val reason: String?,
    val startedAtMs: Long,
    val finishedAtMs: Long,
) {
    val durationMs: Long get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)

    val summaryEn: String
        get() = when (status) {
            TaskExecutionStatus.SUCCESS -> "Completed: $taskName"
            TaskExecutionStatus.PREPARED -> "Prepared: $taskName"
            TaskExecutionStatus.FAILURE -> "Failed: $taskName${reason?.let { " — $it" } ?: ""}"
        }

    val summaryAr: String
        get() = when (status) {
            TaskExecutionStatus.SUCCESS -> "تم تنفيذ المهمة: $taskName"
            TaskExecutionStatus.PREPARED -> "الرسالة جاهزة: $taskName"
            TaskExecutionStatus.FAILURE -> "فشلت المهمة: $taskName${reason?.let { " — $it" } ?: ""}"
        }

    companion object {
        fun success(taskName: String, startedAtMs: Long, finishedAtMs: Long = System.currentTimeMillis()) =
            TaskExecutionResult(taskName, TaskExecutionStatus.SUCCESS, null, startedAtMs, finishedAtMs)

        fun prepared(taskName: String, startedAtMs: Long, finishedAtMs: Long = System.currentTimeMillis()) =
            TaskExecutionResult(taskName, TaskExecutionStatus.PREPARED, null, startedAtMs, finishedAtMs)

        fun failure(taskName: String, reason: String, startedAtMs: Long, finishedAtMs: Long = System.currentTimeMillis()) =
            TaskExecutionResult(taskName, TaskExecutionStatus.FAILURE, reason, startedAtMs, finishedAtMs)
    }
}
