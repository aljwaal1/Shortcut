package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledAppShortcut

class ShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledAppShortcut> {
        val raw = preferences.getString(KEY_SHORTCUTS, "").orEmpty()
        val decoded = ShortcutCodec.decode(raw)
        if (raw.isNotBlank() && ShortcutCodec.needsMigration(raw)) save(decoded)
        return decoded
    }

    fun save(shortcuts: List<ScheduledAppShortcut>) {
        preferences.edit()
            .putString(KEY_SHORTCUTS, ShortcutCodec.encode(shortcuts))
            .apply()
    }

    fun upsert(shortcut: ScheduledAppShortcut): List<ScheduledAppShortcut> =
        ScheduledEntityCollection.upsertShortcut(load(), shortcut).also(::save)

    fun remove(shortcut: ScheduledAppShortcut) = removeById(shortcut.id)

    fun removeById(id: String) {
        save(ScheduledEntityCollection.removeShortcut(load(), id))
    }

    fun findById(id: String): ScheduledAppShortcut? = load().firstOrNull { it.id == id }

    private companion object {
        const val PREFS_NAME = "shortcut_store"
        const val KEY_SHORTCUTS = "scheduled_shortcuts"
    }
}
