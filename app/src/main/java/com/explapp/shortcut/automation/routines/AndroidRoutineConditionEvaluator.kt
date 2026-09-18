package com.explapp.shortcut.automation.routines

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AndroidRoutineConditionEvaluator(private val context: Context) : RoutineConditionEvaluator {
    override fun matches(condition: RoutineCondition): Boolean = when (condition.type) {
        RoutineConditionType.BATTERY_ABOVE -> batteryPercent() > (condition.value.toIntOrNull() ?: return false)
        RoutineConditionType.BATTERY_BELOW -> batteryPercent() < (condition.value.toIntOrNull() ?: return false)
        RoutineConditionType.DAY_OF_WEEK -> {
            val requested = condition.value.toIntOrNull() ?: return false
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            day == requested
        }
        RoutineConditionType.VARIABLE_EQUALS -> {
            val actual = builtInVariable(condition.value) ?: return false
            actual == condition.secondaryValue
        }
        RoutineConditionType.VARIABLE_CONTAINS -> {
            val actual = builtInVariable(condition.value) ?: return false
            actual.contains(condition.secondaryValue, ignoreCase = true)
        }
    }

    private fun builtInVariable(name: String): String? = when (name.trim()) {
        "batteryPercent" -> batteryPercent().toString()
        "currentDate" -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        "currentTime" -> SimpleDateFormat("HH:mm", Locale.US).format(Date())
        "dayOfWeek" -> Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toString()
        else -> null
    }

    private fun batteryPercent(): Int {
        val manager = context.getSystemService(BatteryManager::class.java)
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
