package com.explapp.shortcut.scheduler

import android.content.Intent

object StartupEvent {
    fun shouldReschedule(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
}
