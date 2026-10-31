package com.explapp.shortcut.domain

object ShortcutCollection {
    fun remove(
        shortcuts: List<ScheduledAppShortcut>,
        shortcut: ScheduledAppShortcut,
    ): List<ScheduledAppShortcut> = shortcuts.filterNot { it == shortcut }
}
