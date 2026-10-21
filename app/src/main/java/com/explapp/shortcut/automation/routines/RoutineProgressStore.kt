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
        .commit()

    fun record(id: String, index: Int, status: RoutineActionStatus): Boolean = prefs.edit()
        .putInt("$id:index", index)
        .putString("$id:last", status.name)
        .commit()

    fun finish(id: String, status: RoutineRunStatus): Boolean = prefs.edit()
        .putString("$id:state", status.name)
        .commit()

    fun snapshot(id: String): Snapshot? {
        if (!prefs.contains("$id:state")) return null
        val stored = prefs.getString("$id:state", "IN_FLIGHT").orEmpty()
        val visible = if (stored == "IN_FLIGHT") "UNCERTAIN" else stored
        return Snapshot(visible, prefs.getInt("$id:index", -1))
    }

    data class Snapshot(val status: String, val lastCompletedActionIndex: Int)
}
