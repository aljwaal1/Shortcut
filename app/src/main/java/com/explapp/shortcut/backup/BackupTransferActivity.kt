package com.explapp.shortcut.backup

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.automation.routines.RoutineScheduler
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import com.explapp.shortcut.scheduler.AndroidAlarmScheduler
import com.explapp.shortcut.scheduler.AndroidMessageScheduler
import com.explapp.shortcut.tools.ToolId
import com.explapp.shortcut.tools.ToolPreferencesStore
import com.explapp.shortcut.ui.ShortcutTheme

class BackupTransferActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShortcutTheme { BackupTransferScreen(onBack = ::finish) } }
    }
}

@Composable
private fun BackupTransferScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    var status by remember { mutableStateOf("") }

    fun write(uri: Uri) {
        val toolPrefs = ToolPreferencesStore(context)
        val payload = BackupPayload(
            shortcuts = ShortcutStore(context).load(),
            messages = MessageStore(context).load(),
            routines = RoutineStore(context).load(),
            favoriteToolIds = toolPrefs.favorites().map { it.name },
            recentToolIds = toolPrefs.recents().map { it.name },
        )
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(BackupCodec.encode(payload)) }
                ?: error("Cannot open destination")
        }.onSuccess { status = if (ar) "تم تصدير النسخة الاحتياطية" else "Backup exported" }
            .onFailure { status = it.message.orEmpty() }
    }

    fun read(uri: Uri) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Cannot open backup")
            BackupCodec.decode(raw).getOrThrow()
        }.onSuccess { payload ->
            val shortcutStore = ShortcutStore(context)
            val messageStore = MessageStore(context)
            val routineStore = RoutineStore(context)
            val toolPrefs = ToolPreferencesStore(context)
            val appScheduler = AndroidAlarmScheduler(context)
            val messageScheduler = AndroidMessageScheduler(context)
            val routineScheduler = RoutineScheduler(context)

            val cancellation = RestoreCancellationPlan.from(
                shortcutIds = shortcutStore.load().map { it.id },
                messageIds = messageStore.load().map { it.id },
                routineIds = routineStore.load().map { it.id },
            )
            cancellation.shortcutIds.forEach(appScheduler::cancelById)
            cancellation.messageIds.forEach(messageScheduler::cancelById)
            cancellation.routineIds.forEach(routineScheduler::cancel)

            shortcutStore.save(payload.shortcuts)
            messageStore.save(payload.messages)
            routineStore.save(payload.routines)
            toolPrefs.replaceFavorites(payload.favoriteToolIds.mapNotNull { runCatching { ToolId.valueOf(it) }.getOrNull() })
            toolPrefs.replaceRecents(payload.recentToolIds.mapNotNull { runCatching { ToolId.valueOf(it) }.getOrNull() })

            payload.shortcuts.filter { it.isEnabled }.forEach(appScheduler::schedule)
            payload.messages.filter { it.isEnabled }.forEach(messageScheduler::schedule)
            payload.routines.filter { it.isEnabled && it.isValid() }.forEach(routineScheduler::schedule)
            status = if (ar) "تم الاستيراد بنجاح" else "Backup imported"
        }.onFailure { status = it.message.orEmpty() }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::write) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::read) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (ar) "النسخ الاحتياطي والاستعادة" else "Backup & Restore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(if (ar) "اختر بنفسك مكان حفظ ملف JSON. لا نحتاج صلاحية وصول شاملة للملفات." else "Choose the JSON destination yourself. Broad storage permission is not required.")
        Button(onClick = { create.launch("shortcut-backup.json") }, modifier = Modifier.fillMaxWidth()) { Text(if (ar) "تصدير نسخة" else "Export backup") }
        Button(onClick = { open.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text(if (ar) "استيراد نسخة" else "Import backup") }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onBack) { Text(if (ar) "رجوع" else "Back") }
    }
}
