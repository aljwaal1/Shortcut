package com.explapp.shortcut.automation.routines

import android.content.Context
import java.util.UUID

class RoutineStore(context: Context) {
    private val prefs = context.getSharedPreferences("automation_routines", Context.MODE_PRIVATE)
    private val secrets = RoutineSecretStore(context.applicationContext)

    fun load(): List<AutomationRoutine> = synchronized(LOCK) {
        val decoded = RoutineCodec.decode(prefs.getString(KEY, "[]").orEmpty())
        var migrated = false
        val hydrated = decoded.map { routine ->
            routine.copy(
                actions = routine.actions.mapIndexed { index, action ->
                    val updated = action.parameters.toMutableMap()
                    SECRET_KEYS.forEach { key ->
                        val value = updated[key].orEmpty()
                        val secretKey = if (value.startsWith(SECRET_MARKER_V2)) {
                            stableSecretKey(routine.id, value.removePrefix(SECRET_MARKER_V2), key)
                        } else secretKey(routine.id, index, key)
                        when {
                            value == SECRET_MARKER || value.startsWith(SECRET_MARKER_V2) -> {
                                val secret = secrets.get(secretKey)
                                if (secret.isNotBlank()) {
                                    updated[key] = secret
                                } else {
                                    // Fail closed: do not overwrite the stored marker and
                                    // delete encrypted data when Android Keystore is unavailable.
                                    throw IllegalStateException("Stored automation credential cannot be decrypted")
                                }
                            }
                            value.isNotBlank() -> {
                                if (secrets.put(secretKey, value)) {
                                    updated[key] = value
                                } else {
                                    // Never keep a credential in plaintext when secure storage is unavailable.
                                    updated.remove(key)
                                }
                                migrated = true
                            }
                        }
                    }
                    action.copy(parameters = updated)
                },
            )
        }
        if (migrated) save(hydrated)
        hydrated
    }

    fun save(items: List<AutomationRoutine>) = synchronized(LOCK) {
        val distinct = items.distinctBy { it.id }
        val retainedSecrets = mutableSetOf<String>()
        val protected = distinct.map { routine ->
            val protectedRoutine = routine.copy(
                actions = routine.actions.mapIndexed { index, action ->
                    val updated = action.parameters.toMutableMap()
                    SECRET_KEYS.forEach { key ->
                        val value = updated[key].orEmpty()
                        when {
                            value.startsWith(SECRET_MARKER_V2) -> {
                                val reference = value.removePrefix(SECRET_MARKER_V2)
                                val storageKey = stableSecretKey(routine.id, reference, key)
                                if (reference.isNotBlank() && secrets.get(storageKey).isNotBlank()) {
                                    retainedSecrets += storageKey
                                } else updated.remove(key)
                            }
                            value == SECRET_MARKER -> {
                                val oldKey = secretKey(routine.id, index, key)
                                val oldValue = secrets.get(oldKey)
                                if (oldValue.isNotBlank()) {
                                    val reference = UUID.randomUUID().toString()
                                    val newKey = stableSecretKey(routine.id, reference, key)
                                    check(secrets.put(newKey, oldValue)) { "Could not protect migrated credential" }
                                    retainedSecrets += newKey
                                    updated[key] = SECRET_MARKER_V2 + reference
                                } else updated.remove(key)
                            }
                            value.isBlank() -> updated.remove(key)
                            else -> {
                                val reference = UUID.randomUUID().toString()
                                val storageKey = stableSecretKey(routine.id, reference, key)
                                check(secrets.put(storageKey, value)) { "Could not protect automation credential" }
                                retainedSecrets += storageKey
                                updated[key] = SECRET_MARKER_V2 + reference
                            }
                        }
                    }
                    action.copy(parameters = updated)
                },
            )
            protectedRoutine
        }
        check(prefs.edit().putString(KEY, RoutineCodec.encode(protected)).commit()) {
            "Could not save automation routines"
        }
        // Only remove credentials after the new routine list is safely persisted.
        secrets.keys("").filter { key ->
            key !in retainedSecrets
        }.forEach(secrets::remove)
    }

    fun upsert(item: AutomationRoutine) = synchronized(LOCK) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) current[index] = item else current += item
        save(current)
    }

    fun removeById(id: String) = synchronized(LOCK) {
        // Load and persist the remaining routines before dropping credentials.
        // Doing the reverse can erase unrelated encrypted values during hydration.
        val remaining = load().filterNot { it.id == id }
        save(remaining)
        secrets.removePrefix("$id:")
    }

    private fun stableSecretKey(routineId: String, reference: String, parameter: String): String =
        "$routineId:v2:$reference:$parameter"

    private fun secretKey(routineId: String, actionIndex: Int, parameter: String): String =
        "$routineId:$actionIndex:$parameter"

    companion object {
        private const val KEY = "items"
        private const val SECRET_MARKER = "__shortcut_secret_v1__"
        private const val SECRET_MARKER_V2 = "__shortcut_secret_v2__:"
        private val SECRET_KEYS = setOf("botToken", "telegramBotToken")
        private val LOCK = Any()
    }
}
