package com.explapp.shortcut.automation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class CarModeActivity : AppCompatActivity() {
    private val bluetoothStep = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openMaps()
    }

    private val wifiStep = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openBluetoothOrMaps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wifiAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        runCatching { wifiStep.launch(Intent(wifiAction)) }
            .onFailure { openBluetoothOrMaps() }
    }

    private fun openBluetoothOrMaps() {
        runCatching { bluetoothStep.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { openMaps() }
    }

    private fun openMaps() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
        runCatching { startActivity(intent) }
        finish()
    }
}
