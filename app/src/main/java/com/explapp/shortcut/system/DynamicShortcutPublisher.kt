package com.explapp.shortcut.system

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.explapp.shortcut.search.CommandPaletteActivity
import com.explapp.shortcut.tools.QuickFileActivity
import com.explapp.shortcut.ui.MyAutomationsActivity
import com.explapp.shortcut.usage.AppUsageActivity

object DynamicShortcutPublisher {
    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = listOf(
            ShortcutInfo.Builder(context, "new_automation")
                .setShortLabel("New automation")
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_input_add))
                .setIntent(Intent(context, MyAutomationsActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build(),
            ShortcutInfo.Builder(context, "command_search")
                .setShortLabel("Search")
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_search))
                .setIntent(Intent(context, CommandPaletteActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build(),
            ShortcutInfo.Builder(context, "app_usage")
                .setShortLabel("App usage")
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_recent_history))
                .setIntent(Intent(context, AppUsageActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build(),
            ShortcutInfo.Builder(context, "qr")
                .setShortLabel("QR")
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_share))
                .setIntent(Intent(context, QuickFileActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(QuickFileActivity.EXTRA_TOOL, "QR_CREATE"))
                .build(),
        )
        manager.dynamicShortcuts = shortcuts
    }
}
