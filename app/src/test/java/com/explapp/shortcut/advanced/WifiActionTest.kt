package com.explapp.shortcut.advanced

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiActionTest {
    @Test
    fun modernAndroidUsesWifiPanel() {
        assertEquals(Settings.Panel.ACTION_WIFI, WifiAction.actionForSdk(29))
    }

    @Test
    fun olderAndroidUsesWifiSettings() {
        assertEquals(Settings.ACTION_WIFI_SETTINGS, WifiAction.actionForSdk(28))
    }
}
