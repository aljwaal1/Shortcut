package com.explapp.shortcut.data

import android.content.Context
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduleAnchor
import java.time.ZonedDateTime

class ShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ScheduledAppShortcut> {
        val raw = preferences.getString(KEY_SHORTCUTS, "").orEmpty()
        val decoded = ShortcutCodec.decode(raw)
        val now = ZonedDateTime.now()
        val normalized = decoded.map { item ->
            if (item.isEnabled && ScheduleAnchor.isExpiredOneShot(now, item.repeat, item.oneShotEpochDay, item.hour, item.minute)) {
                item.copy(isEnabled = false)
            } else item
        }
        if (raw.isNotBlank() && (ShortcutCodec.needsMigration(raw) || normalized != decoded)) save(normalized)
        return normalized
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
