package com.explapp.shortcut.automation

enum class BatteryDirection { BELOW, ABOVE }

data class BatteryRule(
    val threshold: Int,
    val direction: BatteryDirection,
) {
    init {
        require(threshold in 1..100)
    }

    fun crossed(previous: Int, current: Int): Boolean = when (direction) {
        BatteryDirection.BELOW -> previous > threshold && current <= threshold
        BatteryDirection.ABOVE -> previous < threshold && current >= threshold
    }
}
