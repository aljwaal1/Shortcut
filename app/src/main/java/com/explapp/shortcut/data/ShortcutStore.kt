package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledAppShortcut

class ShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledAppShortcut> = synchronized(LOCK) {
        val raw = preferences.getString(KEY_SHORTCUTS, "").orEmpty()
        val decoded = ShortcutCodec.decode(raw)
        if (raw.isNotBlank() && ShortcutCodec.needsMigration(raw)) save(decoded)
        decoded
    }

    fun save(shortcuts: List<ScheduledAppShortcut>) = synchronized(LOCK) {
        preferences.edit()
            .putString(KEY_SHORTCUTS, ShortcutCodec.encode(shortcuts))
            .apply()
    }

    fun upsert(shortcut: ScheduledAppShortcut): List<ScheduledAppShortcut> = synchronized(LOCK) {
        ScheduledEntityCollection.upsertShortcut(load(), shortcut).also(::save)
    }

    fun remove(shortcut: ScheduledAppShortcut) = removeById(shortcut.id)

    fun removeById(id: String) = synchronized(LOCK) {
        save(ScheduledEntityCollection.removeShortcut(load(), id))
    }

    fun findById(id: String): ScheduledAppShortcut? = synchronized(LOCK) {
        load().firstOrNull { it.id == id }
    }

    private companion object {
        const val PREFS_NAME = "shortcut_store"
        const val KEY_SHORTCUTS = "scheduled_shortcuts"
        val LOCK = Any()
    }
}
