package com.explapp.shortcut.data

import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage

object ScheduledEntityCollection {
    fun upsertShortcut(
        items: List<ScheduledAppShortcut>,
        item: ScheduledAppShortcut,
    ): List<ScheduledAppShortcut> = upsert(items, item.id) { it.id }

    fun upsertMessage(
        items: List<ScheduledMessage>,
        item: ScheduledMessage,
    ): List<ScheduledMessage> = upsert(items, item.id) { it.id }

    private fun <T> upsert(items: List<T>, replacement: T, idOf: (T) -> String): List<T> {
        val id = idOf(replacement)
        val index = items.indexOfFirst { idOf(it) == id }
        if (index == -1) return items + replacement
        return items.toMutableList().apply { this[index] = replacement }
    }

    private fun <T> upsert(items: List<T>, id: String, idOf: (T) -> String): List<T> =
        error("Use typed overload")
}
