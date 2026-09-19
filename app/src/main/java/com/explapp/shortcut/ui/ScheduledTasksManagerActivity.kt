package com.explapp.shortcut.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.messages.MessageDeepLinkFactory
import com.explapp.shortcut.scheduler.AndroidAlarmScheduler
import com.explapp.shortcut.scheduler.AndroidMessageScheduler
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ScheduledTasksManagerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShortcutTheme { ScheduledTasksManagerScreen(onBack = ::finish) } }
    }
}

@Composable
private fun ScheduledTasksManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val shortcutStore = remember { ShortcutStore(context.applicationContext) }
    val messageStore = remember { MessageStore(context.applicationContext) }
    val appScheduler = remember { AndroidAlarmScheduler(context.applicationContext) }
    val messageScheduler = remember { AndroidMessageScheduler(context.applicationContext) }
    val shortcuts = remember { mutableStateListOf<ScheduledAppShortcut>().apply { addAll(shortcutStore.load()) } }
    val messages = remember { mutableStateListOf<ScheduledMessage>().apply { addAll(messageStore.load()) } }
    var editingShortcut by remember { mutableStateOf<ScheduledAppShortcut?>(null) }
    var editingMessage by remember { mutableStateOf<ScheduledMessage?>(null) }

    fun replaceShortcut(item: ScheduledAppShortcut) {
        val index = shortcuts.indexOfFirst { it.id == item.id }
        if (index >= 0) shortcuts[index] = item else shortcuts += item
        shortcutStore.save(shortcuts)
        appScheduler.cancelById(item.id)
        if (item.isEnabled) appScheduler.schedule(item)
    }
    fun replaceMessage(item: ScheduledMessage) {
        val index = messages.indexOfFirst { it.id == item.id }
        if (index >= 0) messages[index] = item else messages += item
        messageStore.save(messages)
        messageScheduler.cancelById(item.id)
        if (item.isEnabled) messageScheduler.schedule(item)
    }

    when {
        editingShortcut != null -> CreateShortcutScreen(
            initial = editingShortcut,
            onCancel = { editingShortcut = null },
            onSave = { saved -> replaceShortcut(saved); editingShortcut = null },
        )
        editingMessage != null -> CreateMessageScreen(
            initial = editingMessage,
            initialPlatform = editingMessage?.platform ?: MessagePlatform.WHATSAPP,
            onCancel = { editingMessage = null },
            onSave = { saved -> replaceMessage(saved); editingMessage = null },
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) "المهام المجدولة" else "Scheduled tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(if (ar) "تعديل • إيقاف • تشغيل الآن • نسخ" else "Edit • Pause • Run now • Duplicate")
                    }
                    TextButton(onClick = onBack) { Text(if (ar) "رجوع" else "Back") }
                }
            }
            items(shortcuts, key = { it.id }) { item ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(nextRunLabel(item, ar), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = item.isEnabled, onCheckedChange = { replaceShortcut(item.copy(isEnabled = it)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { editingShortcut = item }) { Text(if (ar) "تعديل" else "Edit") }
                            TextButton(onClick = {
                                context.packageManager.getLaunchIntentForPackage(item.packageName)?.let {
                                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }) { Text(if (ar) "تشغيل الآن" else "Run now") }
                            TextButton(onClick = { replaceShortcut(item.duplicate()) }) { Text(if (ar) "نسخ" else "Duplicate") }
                            TextButton(onClick = {
                                appScheduler.cancelById(item.id)
                                shortcuts.removeAll { it.id == item.id }
                                shortcutStore.save(shortcuts)
                            }) { Text(if (ar) "حذف" else "Delete") }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { item ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(nextRunLabel(item, ar), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = item.isEnabled, onCheckedChange = { replaceMessage(item.copy(isEnabled = it)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { editingMessage = item }) { Text(if (ar) "تعديل" else "Edit") }
                            TextButton(onClick = {
                                val link = MessageDeepLinkFactory.build(item.platform, item.recipient, item.message)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            }) { Text(if (ar) "تشغيل الآن" else "Run now") }
                            TextButton(onClick = { replaceMessage(item.duplicate()) }) { Text(if (ar) "نسخ" else "Duplicate") }
                            TextButton(onClick = {
                                messageScheduler.cancelById(item.id)
                                messages.removeAll { it.id == item.id }
                                messageStore.save(messages)
                            }) { Text(if (ar) "حذف" else "Delete") }
                        }
                    }
                }
            }
        }
    }
}

private fun nextRunLabel(item: ScheduledAppShortcut, ar: Boolean): String {
    if (!item.isEnabled) return if (ar) "متوقفة" else "Paused"
    val next = ScheduledTaskActions.nextRun(ZonedDateTime.now(), item) ?: return if (ar) "متوقفة" else "Paused"
    return (if (ar) "التشغيل التالي: " else "Next run: ") + next.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun nextRunLabel(item: ScheduledMessage, ar: Boolean): String {
    if (!item.isEnabled) return if (ar) "متوقفة" else "Paused"
    val next = ScheduledTaskActions.nextRun(ZonedDateTime.now(), item) ?: return if (ar) "متوقفة" else "Paused"
    return (if (ar) "التشغيل التالي: " else "Next run: ") + next.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
