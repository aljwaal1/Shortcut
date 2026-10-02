package com.explapp.shortcut.backup

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var pendingImport by remember { mutableStateOf<BackupPayload?>(null) }
    val scope = rememberCoroutineScope()

    fun write(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val toolPrefs = ToolPreferencesStore(context)
                    val payload = BackupPayload(
                        shortcuts = ShortcutStore(context).load(),
                        messages = MessageStore(context).load(),
                        routines = RoutineStore(context).load(),
                        favoriteToolIds = toolPrefs.favorites().map { it.name },
                        recentToolIds = toolPrefs.recents().map { it.name },
                    )
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(BackupCodec.encode(payload))
                    } ?: error("Cannot open destination")
                }
            }
            result.onSuccess { status = if (ar) "تم تصدير النسخة الاحتياطية" else "Backup exported" }
                .onFailure { status = it.message.orEmpty() }
        }
    }

    fun read(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Cannot open backup")
                    BackupCodec.decode(raw).getOrThrow()
                }
            }
            result.onSuccess { payload ->
                pendingImport = payload
                status = if (ar) "تم فحص النسخة. راجع التفاصيل ثم أكد الاستعادة." else "Backup validated. Review the details and confirm restore."
            }.onFailure {
                status = if (ar) "فشل فحص النسخة: " + (it.message ?: "خطأ غير معروف") else "Backup validation failed: " + (it.message ?: "Unknown error")
            }
        }
    }

    fun restore(payload: BackupPayload) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
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
            cancellation.shortcutIds.forEach { id -> runCatching { appScheduler.cancelById(id) } }
            cancellation.messageIds.forEach { id -> runCatching { messageScheduler.cancelById(id) } }
            cancellation.routineIds.forEach { id -> runCatching { routineScheduler.cancel(id) } }

            shortcutStore.save(payload.shortcuts)
            messageStore.save(payload.messages)
            routineStore.save(payload.routines)
            toolPrefs.replaceFavorites(payload.favoriteToolIds.mapNotNull { runCatching { ToolId.valueOf(it) }.getOrNull() })
            toolPrefs.replaceRecents(payload.recentToolIds.mapNotNull { runCatching { ToolId.valueOf(it) }.getOrNull() })

            payload.shortcuts.filter { it.isEnabled }.forEach { item -> runCatching { appScheduler.schedule(item) } }
            payload.messages.filter { it.isEnabled }.forEach { item -> runCatching { messageScheduler.schedule(item) } }
            payload.routines.filter { it.isEnabled && it.isValid() }.forEach { item -> runCatching { routineScheduler.schedule(item) } }
                }
            }
            result.onSuccess {
                pendingImport = null
                status = if (ar) "تمت الاستعادة بنجاح" else "Backup restored"
            }.onFailure {
                status = if (ar) "فشلت الاستعادة: " + (it.message ?: "خطأ غير معروف") else "Restore failed: " + (it.message ?: "Unknown error")
            }
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::write) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::read) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (ar) "رجوع" else "Back",
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (ar) "النسخ الاحتياطي والاستعادة" else "Backup & Restore",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (ar) "احتفظ بإعداداتك محليًا وانقلها عند الحاجة"
                    else "Keep your setup portable and under your control",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (ar) "تصدير نسخة احتياطية" else "Export backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (ar) "يحفظ الاختصارات والرسائل والأتمتة والمفضلة في ملف JSON تختار مكانه بنفسك."
                    else "Save shortcuts, messages, automations and favorites to a JSON file you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { create.launch("shortcut-backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ar) "اختيار مكان الحفظ" else "Choose save location") }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    if (ar) "استيراد نسخة" else "Import backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (ar) "يفحص الملف ثم يستبدل البيانات الحالية ويعيد جدولة المهام الصالحة."
                    else "Validate a backup, replace the current data and reschedule valid tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { open.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ar) "اختيار ملف" else "Choose file") }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF1B9C68))
                Text(
                    if (ar) "رموز بوت تيليجرام وكلمات الاعتماد لا تُصدّر داخل النسخة الاحتياطية."
                    else "Telegram bot tokens and credentials are intentionally excluded from backups.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (status.isNotBlank()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    status,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    pendingImport?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(if (ar) "استعادة هذه النسخة؟" else "Restore this backup?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (ar) {
                        "ستستبدل البيانات الحالية بـ " +
                            payload.shortcuts.size + " اختصار، و" +
                            payload.messages.size + " رسالة، و" +
                            payload.routines.size + " أتمتة. رموز البوت غير موجودة في النسخة لأسباب أمنية."
                    } else {
                        "Current data will be replaced with " +
                            payload.shortcuts.size + " shortcuts, " +
                            payload.messages.size + " messages and " +
                            payload.routines.size + " automations. Bot tokens are excluded from backups for security."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { restore(payload) }) {
                    Text(if (ar) "استعادة" else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(if (ar) "إلغاء" else "Cancel")
                }
            },
        )
    }
}
