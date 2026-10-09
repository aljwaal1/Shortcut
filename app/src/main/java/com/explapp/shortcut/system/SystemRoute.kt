package com.explapp.shortcut.system

enum class SystemAction { NEW_AUTOMATION, APP_USAGE, QR, FAVORITE_TOOL }

data class SystemRoute(val value: String) {
    companion object {
        fun forAction(action: SystemAction): SystemRoute = when (action) {
            SystemAction.NEW_AUTOMATION -> SystemRoute("automations")
            SystemAction.APP_USAGE -> SystemRoute("usage")
            SystemAction.QR -> SystemRoute("qr")
            SystemAction.FAVORITE_TOOL -> SystemRoute("favorite")
        }

        fun forRoutine(id: String): SystemRoute = SystemRoute("routine:$id")
    }
}
