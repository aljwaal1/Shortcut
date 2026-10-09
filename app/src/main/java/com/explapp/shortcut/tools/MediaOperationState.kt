package com.explapp.shortcut.tools

sealed interface MediaOperationResult {
    data object Completed : MediaOperationResult
    data object Cancelled : MediaOperationResult
    data class Failed(val message: String) : MediaOperationResult
}

data class MediaOperationProgress(
    val total: Int,
    val completed: Int,
    val cancelled: Boolean,
) {
    val normalizedTotal: Int get() = total.coerceAtLeast(0)
    val normalizedCompleted: Int get() = completed.coerceIn(0, normalizedTotal)
    val fraction: Float get() = if (normalizedTotal == 0) 0f else normalizedCompleted.toFloat() / normalizedTotal.toFloat()
    val isComplete: Boolean get() = !cancelled && normalizedTotal > 0 && normalizedCompleted >= normalizedTotal

    fun next(): MediaOperationProgress = copy(completed = (normalizedCompleted + 1).coerceAtMost(normalizedTotal))
    fun cancel(): MediaOperationProgress = copy(cancelled = true)
    fun result(): MediaOperationResult = when {
        cancelled -> MediaOperationResult.Cancelled
        isComplete -> MediaOperationResult.Completed
        else -> MediaOperationResult.Failed("Operation is not complete")
    }
}
