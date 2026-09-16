package com.explapp.shortcut.domain

enum class RepeatOption(val isoWeekdays: Set<Int>) {
    ONCE(emptySet()),
    DAILY(setOf(1, 2, 3, 4, 5, 6, 7)),
    WEEKDAYS(setOf(1, 2, 3, 4, 5)),
    WEEKLY(emptySet()),
}

data class ScheduledAppShortcut(
    val name: String,
    val packageName: String,
    val hour: Int,
    val minute: Int,
    val repeat: RepeatOption,
) {
    fun isValid(): Boolean =
        packageName.isNotBlank() &&
            hour in 0..23 &&
            minute in 0..59
}
