package com.explapp.shortcut.automation.routines

import android.content.Context

/**
 * Persist only execution metadata, never payloads, recipients or credentials.
 * A process crash leaves IN_FLIGHT; it is NOT automatically retried because
 * external effects such as a Telegram send may already have happened.
 */
class RoutineProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("routine_progress_v1", Context.MODE_PRIVATE)

    fun begin(id: String): Boolean = prefs.edit()
        .putString("$id:state", "IN_FLIGHT")
        .putInt("$id:index", -1)
        .putBoolean("$id:cancel", false)
        .commit()

    fun record(id: String, index: Int, status: RoutineActionStatus): Boolean = prefs.edit()
        .putInt("$id:index", index)
        .putString("$id:last", status.name)
        .commit()

    /**
     * A safe resume is allowed only when every previously completed action is
     * a local WAIT. Any user-facing or network side effect requires manual review.
     */
    fun safeResumeIndex(routine: AutomationRoutine): Int? {
        val state = snapshot(routine.id) ?: return null
        if (state.status != "UNCERTAIN") return null
        val index = state.lastCompletedActionIndex
        if (index < 0) return 0
        if (index >= routine.actions.size) return null
        if (prefs.getString("${routine.id}:last", "") != RoutineActionStatus.SUCCESS.name) return null
        if (routine.actions.take(index + 1).any { it.type != RoutineActionType.WAIT }) return null
        return index + 1
    }

    fun requestCancel(id: String): Boolean = prefs.edit()
        .putBoolean("$id:cancel", true)
        .putString("$id:state", "CANCEL_REQUESTED")
        .commit()

    fun isCancelled(id: String): Boolean = prefs.getBoolean("$id:cancel", false)

    fun finish(id: String, status: RoutineRunStatus): Boolean = prefs.edit()
        .putString("$id:state", status.name)
        .commit()

    fun snapshot(id: String): Snapshot? {
        if (!prefs.contains("$id:state")) return null
        val stored = prefs.getString("$id:state", "IN_FLIGHT").orEmpty()
        val visible = when (stored) {
            "IN_FLIGHT" -> "UNCERTAIN"
            else -> stored
        }
        return Snapshot(visible, prefs.getInt("$id:index", -1))
    }

    data class Snapshot(val status: String, val lastCompletedActionIndex: Int)
}
