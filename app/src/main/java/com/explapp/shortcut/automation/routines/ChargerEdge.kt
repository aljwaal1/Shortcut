package com.explapp.shortcut.automation.routines

object ChargerEdge {
    fun shouldFire(triggerType: RoutineTriggerType, previousConnected: Boolean?, connected: Boolean): Boolean {
        if (previousConnected == null || previousConnected == connected) return false
        return when (triggerType) {
            RoutineTriggerType.CHARGER_CONNECTED -> connected
            RoutineTriggerType.CHARGER_DISCONNECTED -> !connected
            else -> false
        }
    }
}
