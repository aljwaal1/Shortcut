package com.explapp.shortcut.execution

enum class TaskExecutionStatus { SUCCESS, FAILURE }

data class TaskExecutionResult(
    val taskName: String,
    val status: TaskExecutionStatus,
    val reason: String?,
    val startedAtMs: Long,
    val finishedAtMs: Long,
) {
    val durationMs: Long get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)
    val summaryEn: String get() = if (status == TaskExecutionStatus.SUCCESS) "Completed: $taskName" else "Failed: $taskName${reason?.let { " — $it" } ?: ""}"
    val summaryAr: String get() = if (status == TaskExecutionStatus.SUCCESS) "تم تنفيذ المهمة: $taskName" else "فشلت المهمة: $taskName${reason?.let { " — $it" } ?: ""}"

    companion object {
        fun success(taskName: String, startedAtMs: Long, finishedAtMs: Long = System.currentTimeMillis()) =
            TaskExecutionResult(taskName, TaskExecutionStatus.SUCCESS, null, startedAtMs, finishedAtMs)

        fun failure(taskName: String, reason: String, startedAtMs: Long, finishedAtMs: Long = System.currentTimeMillis()) =
            TaskExecutionResult(taskName, TaskExecutionStatus.FAILURE, reason, startedAtMs, finishedAtMs)
    }
}
