package com.explapp.shortcut.backup

import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage

data class BackupRestorePlan(
    val shortcuts: List<ScheduledAppShortcut>,
    val messages: List<ScheduledMessage>,
    val unlockWifiMapsEnabled: Boolean,
) {
    companion object {
        fun from(payload: BackupPayload): BackupRestorePlan = BackupRestorePlan(
            shortcuts = payload.shortcuts,
            messages = payload.messages,
            unlockWifiMapsEnabled = false,
        )
    }
}
