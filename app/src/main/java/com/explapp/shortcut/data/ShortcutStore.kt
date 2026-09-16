package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledAppShortcut

class ShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledAppShortcut> =
        ShortcutCodec.decode(preferences.getString(KEY_SHORTCUTS, "").orEmpty())

    fun save(shortcuts: List<ScheduledAppShortcut>) {
        preferences.edit()
            .putString(KEY_SHORTCUTS, ShortcutCodec.encode(shortcuts))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "shortcut_store"
        const val KEY_SHORTCUTS = "scheduled_shortcuts"
    }
}
