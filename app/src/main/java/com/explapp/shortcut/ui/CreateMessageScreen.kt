package com.explapp.shortcut.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.explapp.shortcut.R
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledMessage

@Composable
fun CreateMessageScreen(
    initialPlatform: MessagePlatform,
    onCancel: () -> Unit,
    onSave: (ScheduledMessage) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(initialPlatform) }
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("8") }
    var minute by remember { mutableStateOf("00") }
    var repeat by remember { mutableStateOf(RepeatOption.ONCE) }
    var pendingSave by remember { mutableStateOf<ScheduledMessage?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        pendingSave?.let(onSave)
        pendingSave = null
    }

    val defaultName = when (platform) {
        MessagePlatform.WHATSAPP -> stringResource(R.string.template_whatsapp)
        MessagePlatform.TELEGRAM -> stringResource(R.string.template_telegram)
    }
    val model = ScheduledMessage(
        name = name.ifBlank { defaultName },
        platform = platform,
        recipient = recipient,
        message = body,
        hour = hour.toIntOrNull() ?: -1,
        minute = minute.toIntOrNull() ?: -1,
        repeat = repeat,
    )

    fun saveWithNeededPermission() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingSave = model
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSave(model)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.message_builder_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(stringResource(R.string.message_app), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessagePlatform.entries.forEach { option ->
                    FilterChip(
                        selected = platform == option,
                        onClick = { platform = option },
                        label = {
                            Text(
                                stringResource(
                                    if (option == MessagePlatform.WHATSAPP) R.string.whatsapp else R.string.telegram,
                                ),
                            )
                        },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shortcut_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = {
                    Text(
                        stringResource(
                            if (platform == MessagePlatform.WHATSAPP) R.string.whatsapp_number else R.string.telegram_username,
                        ),
                    )
                },
                supportingText = {
                    Text(
                        stringResource(
                            if (platform == MessagePlatform.WHATSAPP) R.string.whatsapp_number_hint else R.string.telegram_username_hint,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.message_text)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        }
        item {
            Text(stringResource(R.string.time), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { value -> hour = value.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.hour)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minute,
                    onValueChange = { value -> minute = value.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.minute)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
        item {
            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RepeatOption.entries.forEach { option ->
                    FilterChip(
                        selected = repeat == option,
                        onClick = { repeat = option },
                        label = { Text(messageRepeatLabel(option)) },
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.message_standard_mode_note), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = ::saveWithNeededPermission,
                    enabled = model.isValid(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
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
