package com.explapp.shortcut.domain

import java.util.UUID

enum class RepeatOption(val isoWeekdays: Set<Int>) {
    ONCE(emptySet()),
    DAILY(setOf(1, 2, 3, 4, 5, 6, 7)),
    WEEKDAYS(setOf(1, 2, 3, 4, 5)),
    WEEKLY(emptySet()),
}

data class ScheduledAppShortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageName: String,
    val hour: Int,
    val minute: Int,
    val repeat: RepeatOption,
    val isEnabled: Boolean = true,
) {
    fun isValid(): Boolean =
        id.isNotBlank() &&
            packageName.isNotBlank() &&
            hour in 0..23 &&
            minute in 0..59

    fun duplicate(newId: String = UUID.randomUUID().toString()): ScheduledAppShortcut =
        copy(id = newId, isEnabled = true)
}
