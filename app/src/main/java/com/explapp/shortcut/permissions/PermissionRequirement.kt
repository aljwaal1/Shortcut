package com.explapp.shortcut.permissions

object PermissionRequirement {
    fun notificationRuntimePermissionRequired(apiLevel: Int): Boolean = apiLevel >= 33

    fun exactAlarmAccessRequired(apiLevel: Int): Boolean = apiLevel >= 31
}
