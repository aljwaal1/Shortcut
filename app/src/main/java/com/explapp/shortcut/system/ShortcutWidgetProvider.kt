package com.explapp.shortcut.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.explapp.shortcut.R
import com.explapp.shortcut.tools.QuickFileActivity
import com.explapp.shortcut.ui.MyAutomationsActivity
import com.explapp.shortcut.usage.AppUsageActivity

class ShortcutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_shortcut)
            views.setOnClickPendingIntent(R.id.widget_automations, pending(context, 9101, Intent(context, MyAutomationsActivity::class.java)))
            views.setOnClickPendingIntent(R.id.widget_usage, pending(context, 9102, Intent(context, AppUsageActivity::class.java)))
            views.setOnClickPendingIntent(
                R.id.widget_qr,
                pending(context, 9103, Intent(context, QuickFileActivity::class.java).putExtra(QuickFileActivity.EXTRA_TOOL, "QR_CREATE")),
            )
            manager.updateAppWidget(id, views)
        }
    }

    private fun pending(context: Context, requestCode: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
