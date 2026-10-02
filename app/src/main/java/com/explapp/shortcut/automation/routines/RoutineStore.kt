package com.explapp.shortcut.automation.routines

import android.content.Context

class RoutineStore(context: Context) {
    private val prefs = context.getSharedPreferences("automation_routines", Context.MODE_PRIVATE)
    private val secrets = RoutineSecretStore(context.applicationContext)

    fun load(): List<AutomationRoutine> {
        val decoded = RoutineCodec.decode(prefs.getString(KEY, "[]").orEmpty())
        var migrated = false
        val hydrated = decoded.map { routine ->
            routine.copy(
                actions = routine.actions.mapIndexed { index, action ->
                    val updated = action.parameters.toMutableMap()
                    SECRET_KEYS.forEach { key ->
                        val value = updated[key].orEmpty()
                        val secretKey = secretKey(routine.id, index, key)
                        when {
                            value == SECRET_MARKER -> {
                                val secret = secrets.get(secretKey)
                                if (secret.isNotBlank()) updated[key] = secret else updated.remove(key)
                            }
                            value.isNotBlank() -> {
                                if (secrets.put(secretKey, value)) {
                                    migrated = true
                                }
                            }
                        }
                    }
                    action.copy(parameters = updated)
                },
            )
        }
        if (migrated) save(hydrated)
        return hydrated
    }

    fun save(items: List<AutomationRoutine>) {
        val distinct = items.distinctBy { it.id }
        val protected = distinct.map { routine ->
            // Rewrite the routine's secret namespace from the hydrated in-memory model.
            // This removes tokens left behind by deleted/reordered actions.
            secrets.removePrefix("${routine.id}:")
            routine.copy(
                actions = routine.actions.mapIndexed { index, action ->
                    val updated = action.parameters.toMutableMap()
                    SECRET_KEYS.forEach { key ->
                        val value = updated[key].orEmpty()
                        if (value.isBlank() || value == SECRET_MARKER) {
                            updated.remove(key)
                        } else {
                            check(secrets.put(secretKey(routine.id, index, key), value)) {
                                "Could not store automation credential securely"
                            }
                            updated[key] = SECRET_MARKER
                        }
                    }
                    action.copy(parameters = updated)
                },
            )
        }
        prefs.edit().putString(KEY, RoutineCodec.encode(protected)).apply()
    }

    fun upsert(item: AutomationRoutine) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) current[index] = item else current += item
        save(current)
    }

    fun removeById(id: String) {
        secrets.removePrefix("$id:")
        save(load().filterNot { it.id == id })
    }

    private fun secretKey(routineId: String, actionIndex: Int, parameter: String): String =
        "$routineId:$actionIndex:$parameter"

    companion object {
        private const val KEY = "items"
        private const val SECRET_MARKER = "__shortcut_secret_v1__"
        private val SECRET_KEYS = setOf("botToken", "telegramBotToken")
    }
}
