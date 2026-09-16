package com.explapp.shortcut.advanced

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.explapp.shortcut.R

class ShortcutAutomationAccessibilityService : AccessibilityService() {
    private var receiverRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            if (!AdvancedAutomationStore(context).unlockWifiMapsEnabled) return
            showUnlockActions(context)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun showUnlockActions(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "unlock_shortcuts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.unlock_template_title),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        val wifiIntent = Intent(WifiAction.actionForSdk(Build.VERSION.SDK_INT))
        val wifiPendingIntent = PendingIntent.getActivity(
            context,
            9101,
            wifiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        val mapsPendingIntent = PendingIntent.getActivity(
            context,
            9102,
            mapsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(context.getString(R.string.unlock_template_title))
            .setContentText(context.getString(R.string.unlock_template_notification))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.open_wifi), wifiPendingIntent)
            .addAction(0, context.getString(R.string.open_maps), mapsPendingIntent)
            .setContentIntent(mapsPendingIntent)
            .build()

        manager.notify(9100, notification)
    }
}
