package com.explapp.shortcut.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.explapp.shortcut.R
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.permissions.SchedulingPermissionPlan
import com.explapp.shortcut.permissions.SchedulingPermissionStep
import java.util.UUID

@Composable
fun CreateMessageScreen(
    initialPlatform: MessagePlatform,
    onCancel: () -> Unit,
    onSave: (ScheduledMessage) -> Unit,
    initial: ScheduledMessage? = null,
) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var platform by remember(initial?.id) { mutableStateOf(initial?.platform ?: initialPlatform) }
    var recipient by remember(initial?.id) { mutableStateOf(initial?.recipient.orEmpty()) }
    var body by remember(initial?.id) { mutableStateOf(initial?.message.orEmpty()) }
    var hour by remember(initial?.id) { mutableStateOf((initial?.hour ?: 8).toString()) }
    var minute by remember(initial?.id) { mutableStateOf("%02d".format(initial?.minute ?: 0)) }
    var repeat by remember(initial?.id) { mutableStateOf(initial?.repeat ?: RepeatOption.ONCE) }
    var pendingSave by remember { mutableStateOf<ScheduledMessage?>(null) }

    fun finishPendingSave() { pendingSave?.let(onSave); pendingSave = null }
    val exactAlarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishPendingSave() }
    fun requestExactAlarmOrSave() {
        val exactGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (!exactGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        } else finishPendingSave()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requestExactAlarmOrSave() }

    val defaultName = when (platform) {
        MessagePlatform.WHATSAPP -> stringResource(R.string.template_whatsapp)
        MessagePlatform.TELEGRAM -> stringResource(R.string.template_telegram)
    }
    val model = ScheduledMessage(
        id = initial?.id ?: UUID.randomUUID().toString(),
        name = name.ifBlank { defaultName },
        platform = platform,
        recipient = recipient,
        message = body,
        hour = hour.toIntOrNull() ?: -1,
        minute = minute.toIntOrNull() ?: -1,
        repeat = repeat,
        isEnabled = initial?.isEnabled ?: true,
        deliveryMode = initial?.deliveryMode ?: com.explapp.shortcut.domain.MessageDeliveryMode.PREPARED,
    )

    fun saveWithNeededPermissions() {
        pendingSave = model
        val notificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val exactGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        when (SchedulingPermissionPlan.steps(Build.VERSION.SDK_INT, notificationsGranted, exactGranted).firstOrNull()) {
            SchedulingPermissionStep.NOTIFICATIONS -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            SchedulingPermissionStep.EXACT_ALARM -> requestExactAlarmOrSave()
            null -> finishPendingSave()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f))) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f), modifier = Modifier.size(50.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (initial == null) stringResource(R.string.message_builder_title) else if (ar) "تعديل الرسالة المجدولة" else "Edit scheduled message", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(if (ar) "جهّز الرسالة وحدد وقت التنبيه" else "Prepare the message and choose when to be notified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            MessageSectionCard(title = stringResource(R.string.message_app), subtitle = if (ar) "اختر التطبيق الذي ستجهز فيه الرسالة" else "Choose where the prepared message should open", accent = MaterialTheme.colorScheme.secondary) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessagePlatform.entries.forEach { option -> FilterChip(selected = platform == option, onClick = { platform = option }, label = { Text(stringResource(if (option == MessagePlatform.WHATSAPP) R.string.whatsapp else R.string.telegram)) }) }
                }
            }
        }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.shortcut_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text(stringResource(if (platform == MessagePlatform.WHATSAPP) R.string.whatsapp_number else R.string.telegram_username)) }, supportingText = { Text(stringResource(if (platform == MessagePlatform.WHATSAPP) R.string.whatsapp_number_hint else R.string.telegram_username_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item { OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text(stringResource(R.string.message_text)) }, modifier = Modifier.fillMaxWidth(), minLines = 4) }
        item {
            MessageSectionCard(title = stringResource(R.string.time), subtitle = if (ar) "وقت إظهار تنبيه الرسالة الجاهزة" else "When to notify that the prepared message is ready", accent = MaterialTheme.colorScheme.tertiary) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = hour, onValueChange = { hour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.hour)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = minute, onValueChange = { minute = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.minute)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        }
        item {
            MessageSectionCard(title = stringResource(R.string.repeat), subtitle = if (ar) "اختر عدد مرات تجهيز الرسالة" else "Choose how often the message should be prepared", accent = MaterialTheme.colorScheme.primary) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { RepeatOption.entries.forEach { option -> FilterChip(selected = repeat == option, onClick = { repeat = option }, label = { Text(messageRepeatLabel(option)) }) } }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
                Text(if (ar) "الوضع القياسي: عند الموعد يظهر إشعار. اضغط عليه لفتح المحادثة والنص جاهز. لا نسجل الرسالة كمرسلة تلقائيًا." else "Standard mode: a notification appears at the scheduled time. Tap it to open the chat with the text prepared. Shortcut does not claim it was auto-sent.", modifier = Modifier.padding(15.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = ::saveWithNeededPermissions, enabled = model.isValid(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MessageSectionCard(title: String, subtitle: String, accent: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.07f)), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun messageRepeatLabel(option: RepeatOption): String = when (option) {
    RepeatOption.ONCE -> stringResource(R.string.repeat_once)
    RepeatOption.DAILY -> stringResource(R.string.repeat_daily)
    RepeatOption.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatOption.WEEKLY -> stringResource(R.string.repeat_weekly)
}
