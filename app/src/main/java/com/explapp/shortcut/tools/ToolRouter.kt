package com.explapp.shortcut.tools

import android.content.Context
import android.content.Intent
import com.explapp.shortcut.automation.BatterySetupActivity
import com.explapp.shortcut.automation.CarModeActivity
import com.explapp.shortcut.automation.ParkedCarActivity

object ToolRouter {
    fun intent(context: Context, tool: ToolId): Intent = when (tool) {
        ToolId.CAR_MODE -> Intent(context, CarModeActivity::class.java)
        ToolId.PARKED_CAR -> Intent(context, ParkedCarActivity::class.java)
        ToolId.BATTERY_CHARGER -> Intent(context, BatterySetupActivity::class.java)
        ToolId.NFC_TRIGGER -> Intent(context, NfcSetupActivity::class.java)
        ToolId.IMAGE_CROP -> Intent(context, ImageCropActivity::class.java)
        ToolId.IMAGE_TO_JPEG -> Intent(context, JpegConvertActivity::class.java)
        ToolId.MULTI_URL_TO_PDF -> Intent(context, MultiUrlPdfActivity::class.java)
        ToolId.SCREENSHOT_CAPTURE -> Intent(context, ScreenCaptureActivity::class.java)
        ToolId.SCREENSHOT_OCR_SEARCH -> Intent(context, ScreenCaptureActivity::class.java)
            .putExtra(ScreenCaptureActivity.EXTRA_OCR, true)
        else -> Intent(context, ToolActivity::class.java).putExtra(ToolActivity.EXTRA_TOOL, tool.name)
    }
}
