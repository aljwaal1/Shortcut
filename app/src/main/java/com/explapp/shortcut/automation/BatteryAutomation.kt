package com.explapp.shortcut.automation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.explapp.shortcut.R

private const val PREFS = "battery_automation"
private const val KEY_ENABLED = "enabled"
private const val KEY_THRESHOLD = "threshold"
private const val KEY_DIRECTION = "direction"
private const val KEY_PREVIOUS = "previous"
private const val KEY_CHARGER = "charger"
private const val ACTION_CHECK = "com.explapp.shortcut.BATTERY_CHECK"
private const val CHANNEL = "battery_automation"

class BatteryAutomationStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, 20)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(1, 100)).apply()

    var direction: BatteryDirection
        get() = runCatching { BatteryDirection.valueOf(prefs.getString(KEY_DIRECTION, BatteryDirection.BELOW.name).orEmpty()) }
            .getOrDefault(BatteryDirection.BELOW)
        set(value) = prefs.edit().putString(KEY_DIRECTION, value.name).apply()

    var chargerNotifications: Boolean
        get() = prefs.getBoolean(KEY_CHARGER, true)
        set(value) = prefs.edit().putBoolean(KEY_CHARGER, value).apply()

    var previousLevel: Int
        get() = prefs.getInt(KEY_PREVIOUS, -1)
        set(value) = prefs.edit().putInt(KEY_PREVIOUS, value).apply()
}

object BatteryAutomationScheduler {
    fun schedule(context: Context) {
        val store = BatteryAutomationStore(context)
        if (!store.enabled) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            7401,
            Intent(context, BatteryCheckReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000L,
            pending,
        )
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            7401,
            Intent(context, BatteryCheckReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
    }
}

class BatteryCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return
        val store = BatteryAutomationStore(context)
        if (!store.enabled) return
        val current = currentBatteryLevel(context)
        val previous = store.previousLevel
        if (previous >= 0 && BatteryRule(store.threshold, store.direction).crossed(previous, current)) {
            val symbol = if (store.direction == BatteryDirection.BELOW) "≤" else "≥"
            notify(context, "Battery $symbol ${store.threshold}%", "Battery is now $current%")
        }
        store.previousLevel = current
        BatteryAutomationScheduler.schedule(context)
    }
}

class ChargerEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = BatteryAutomationStore(context)
        if (!store.enabled || !store.chargerNotifications) return
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> notify(context, "Charger connected", "Charging started")
            Intent.ACTION_POWER_DISCONNECTED -> notify(context, "Charger disconnected", "Charging stopped")
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                store.previousLevel = currentBatteryLevel(context)
                BatteryAutomationScheduler.schedule(context)
            }
        }
    }
}

fun currentBatteryLevel(context: Context): Int {
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else {
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.coerceIn(0, 100)
}

private fun notify(context: Context, title: String, text: String) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Battery automations", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
    val notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .build()
    manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
}
