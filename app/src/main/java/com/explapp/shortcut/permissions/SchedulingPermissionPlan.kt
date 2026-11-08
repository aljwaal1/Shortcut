package com.explapp.shortcut.permissions

enum class SchedulingPermissionStep { NOTIFICATIONS, EXACT_ALARM }

object SchedulingPermissionPlan {
    fun steps(
        apiLevel: Int,
        notificationsGranted: Boolean,
        exactAlarmGranted: Boolean,
    ): List<SchedulingPermissionStep> = buildList {
        if (apiLevel >= 33 && !notificationsGranted) add(SchedulingPermissionStep.NOTIFICATIONS)
        if (apiLevel >= 31 && !exactAlarmGranted) add(SchedulingPermissionStep.EXACT_ALARM)
    }
}
