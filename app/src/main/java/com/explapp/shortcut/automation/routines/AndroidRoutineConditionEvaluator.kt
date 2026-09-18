package com.explapp.shortcut.automation.routines

import android.content.Context
import android.os.BatteryManager
import java.util.Calendar

class AndroidRoutineConditionEvaluator(private val context: Context) : RoutineConditionEvaluator {
    override fun matches(condition: RoutineCondition): Boolean = when (condition.type) {
        RoutineConditionType.BATTERY_ABOVE -> batteryPercent() > (condition.value.toIntOrNull() ?: return false)
        RoutineConditionType.BATTERY_BELOW -> batteryPercent() < (condition.value.toIntOrNull() ?: return false)
        RoutineConditionType.DAY_OF_WEEK -> {
            val requested = condition.value.toIntOrNull() ?: return false
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            day == requested
        }
        RoutineConditionType.VARIABLE_EQUALS,
        RoutineConditionType.VARIABLE_CONTAINS,
        -> true
    }

    private fun batteryPercent(): Int {
        val manager = context.getSystemService(BatteryManager::class.java)
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
