package com.explapp.shortcut.backup

data class RestoreCancellationPlan(
    val shortcutIds: List<String>,
    val messageIds: List<String>,
    val routineIds: List<String>,
) {
    companion object {
        fun from(
            shortcutIds: List<String>,
            messageIds: List<String>,
            routineIds: List<String>,
        ): RestoreCancellationPlan = RestoreCancellationPlan(
            shortcutIds = shortcutIds.distinct(),
            messageIds = messageIds.distinct(),
            routineIds = routineIds.distinct(),
        )
    }
}
