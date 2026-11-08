package com.explapp.shortcut.automation.routines

object BatteryThresholdEdge {
    fun isBelow(level: Int, threshold: Int): Boolean = level in 0..100 && level <= threshold

    fun shouldFire(wasBelow: Boolean, level: Int, threshold: Int): Boolean =
        !wasBelow && isBelow(level, threshold)
}
