package com.explapp.shortcut.scheduler

import android.app.AlarmManager
import android.content.Intent

object StartupEvent {
    fun shouldReschedule(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
}
