package com.explapp.shortcut.advanced

import android.provider.Settings

object WifiAction {
    fun actionForSdk(sdkInt: Int): String =
        if (sdkInt >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
}
