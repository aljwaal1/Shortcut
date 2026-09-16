package com.explapp.shortcut.tools

import android.content.Context
import android.content.Intent
import com.explapp.shortcut.automation.BatterySetupActivity
import com.explapp.shortcut.automation.CarModeActivity

object ToolRouter {
    fun intent(context: Context, tool: ToolId): Intent = when (tool) {
        ToolId.CAR_MODE -> Intent(context, CarModeActivity::class.java)
        ToolId.BATTERY_CHARGER -> Intent(context, BatterySetupActivity::class.java)
        ToolId.NFC_TRIGGER -> Intent(context, NfcSetupActivity::class.java)
        else -> Intent(context, ToolActivity::class.java).putExtra(ToolActivity.EXTRA_TOOL, tool.name)
    }
}
