package com.explapp.shortcut.automation.routines

import android.content.Context

class RoutineStore(context: Context) {
    private val prefs = context.getSharedPreferences("automation_routines", Context.MODE_PRIVATE)

    fun load(): List<AutomationRoutine> = RoutineCodec.decode(prefs.getString(KEY, "[]").orEmpty())

    fun save(items: List<AutomationRoutine>) {
        prefs.edit().putString(KEY, RoutineCodec.encode(items.distinctBy { it.id })).apply()
    }

    fun upsert(item: AutomationRoutine) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) current[index] = item else current += item
        save(current)
    }

    fun removeById(id: String) = save(load().filterNot { it.id == id })

    companion object { private const val KEY = "items" }
}
