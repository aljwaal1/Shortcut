package com.explapp.shortcut.advanced

import android.content.Context

class AdvancedAutomationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var unlockWifiMapsEnabled: Boolean
        get() = preferences.getBoolean(KEY_UNLOCK_WIFI_MAPS, false)
        set(value) {
            preferences.edit().putBoolean(KEY_UNLOCK_WIFI_MAPS, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "advanced_automation"
        const val KEY_UNLOCK_WIFI_MAPS = "unlock_wifi_maps_enabled"
    }
}
