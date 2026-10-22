package com.explapp.shortcut.automation.routines

import android.content.Context
import java.security.MessageDigest

/**
 * Persist only execution metadata, never payloads, recipients or credentials.
 * A process crash leaves IN_FLIGHT; it is NOT automatically retried because
 * external effects such as a Telegram send may already have happened.
 */
class RoutineProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("routine_progress_v1", Context.MODE_PRIVATE)

    fun begin(routine: AutomationRoutine): Boolean = prefs.edit()
        .putString("${routine.id}:state", "IN_FLIGHT")
        .putInt("${routine.id}:index", -1)
        .remove("${routine.id}:last")
        .putBoolean("${routine.id}:cancel", false)
        .putString("${routine.id}:fingerprint", fingerprint(routine))
        .commit()

    private fun fingerprint(routine: AutomationRoutine): String {
        val definition = buildString {
            append(routine.trigger)
            routine.conditions.forEach { append('|'); append(it) }
            routine.actions.forEach { append('|'); append(it) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(definition.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

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
        if (prefs.getString("${routine.id}:fingerprint", null) != fingerprint(routine)) return null
        val index = state.lastCompletedActionIndex
        // No persisted completed action does not prove the first action never ran.
        if (index < 0) return null
        if (index >= routine.actions.size) return null
        if (prefs.getString("${routine.id}:last", "") != RoutineActionStatus.SUCCESS.name) return null
        // Resume only if the *entire* routine is a side-effect-free wait sequence.
        // Otherwise a crash could have occurred during the next external action,
        // after the last WAIT was durably recorded.
        if (routine.actions.any { it.type != RoutineActionType.WAIT }) return null
        return (index + 1).takeIf { it < routine.actions.size }
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
            "IN_FLIGHT", "PREPARED" -> "UNCERTAIN"
            else -> stored
        }
        return Snapshot(visible, prefs.getInt("$id:index", -1))
    }

    data class Snapshot(val status: String, val lastCompletedActionIndex: Int)
}
