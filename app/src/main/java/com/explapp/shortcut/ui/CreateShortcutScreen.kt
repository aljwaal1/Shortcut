package com.explapp.shortcut.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.explapp.shortcut.R
import com.explapp.shortcut.data.InstalledApp
import com.explapp.shortcut.data.InstalledAppRepository
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut

@Composable
fun CreateShortcutScreen(
    onCancel: () -> Unit,
    onSave: (ScheduledAppShortcut) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { InstalledAppRepository(context).loadLaunchableApps() }

    var name by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var hour by remember { mutableStateOf("7") }
    var minute by remember { mutableStateOf("30") }
    var repeat by remember { mutableStateOf(RepeatOption.ONCE) }
    var showAppPicker by remember { mutableStateOf(false) }

    val model = ScheduledAppShortcut(
        name = name.ifBlank { selectedApp?.label.orEmpty() },
        packageName = selectedApp?.packageName.orEmpty(),
        hour = hour.toIntOrNull() ?: -1,
        minute = minute.toIntOrNull() ?: -1,
        repeat = repeat,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.builder_title), style = MaterialTheme.typography.headlineMedium)
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.choose_app), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedApp?.label ?: stringResource(R.string.choose_app))
                }
            }
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
                        label = { Text(repeatLabel(option)) },
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onSave(model) },
                    enabled = model.isValid(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(stringResource(R.string.choose_app)) },
            text = {
                if (apps.isEmpty()) {
                    Text(stringResource(R.string.no_apps_found))
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(apps) { app ->
                            TextButton(
                                onClick = {
                                    selectedApp = app
                                    if (name.isBlank()) name = app.label
                                    showAppPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(app.label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun repeatLabel(option: RepeatOption): String = when (option) {
    RepeatOption.ONCE -> stringResource(R.string.repeat_once)
    RepeatOption.DAILY -> stringResource(R.string.repeat_daily)
    RepeatOption.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatOption.WEEKLY -> stringResource(R.string.repeat_weekly)
}
