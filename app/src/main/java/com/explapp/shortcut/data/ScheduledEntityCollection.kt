package com.explapp.shortcut.data

import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage

object ScheduledEntityCollection {
    fun upsertShortcut(
        items: List<ScheduledAppShortcut>,
        item: ScheduledAppShortcut,
    ): List<ScheduledAppShortcut> = upsert(items, item) { it.id }

    fun upsertMessage(
        items: List<ScheduledMessage>,
        item: ScheduledMessage,
    ): List<ScheduledMessage> = upsert(items, item) { it.id }

    fun removeShortcut(items: List<ScheduledAppShortcut>, id: String): List<ScheduledAppShortcut> =
        items.filterNot { it.id == id }

    fun removeMessage(items: List<ScheduledMessage>, id: String): List<ScheduledMessage> =
        items.filterNot { it.id == id }

    private fun <T> upsert(items: List<T>, replacement: T, idOf: (T) -> String): List<T> {
        val id = idOf(replacement)
        val index = items.indexOfFirst { idOf(it) == id }
        if (index == -1) return items + replacement
        return items.toMutableList().apply { this[index] = replacement }
    }
}
