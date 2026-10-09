package com.explapp.shortcut.automation.routines

import android.content.Context
import com.explapp.shortcut.automation.currentBatteryLevel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AndroidRoutineConditionEvaluator(private val context: Context) : RoutineConditionEvaluator {
    override fun matches(condition: RoutineCondition): Boolean = when (condition.type) {
        RoutineConditionType.BATTERY_ABOVE -> {
            val battery = batteryPercent()
            battery in 0..100 && battery > (condition.value.toIntOrNull() ?: return false)
        }
        RoutineConditionType.BATTERY_BELOW -> {
            val battery = batteryPercent()
            battery in 0..100 && battery < (condition.value.toIntOrNull() ?: return false)
        }
        RoutineConditionType.DAY_OF_WEEK -> {
            val requested = condition.value.toIntOrNull() ?: return false
            isoDayOfWeek() == requested
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
        "batteryPercent" -> batteryPercent().takeIf { it in 0..100 }?.toString()
        "currentDate" -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        "currentTime" -> SimpleDateFormat("HH:mm", Locale.US).format(Date())
        "dayOfWeek" -> isoDayOfWeek().toString()
        else -> null
    }

    private fun batteryPercent(): Int = currentBatteryLevel(context)

    private fun isoDayOfWeek(): Int {
        val androidDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (androidDay == Calendar.SUNDAY) 7 else androidDay - 1
    }
}
