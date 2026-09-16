package com.explapp.shortcut.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.explapp.shortcut.R
import com.explapp.shortcut.data.InstalledApp
import com.explapp.shortcut.data.InstalledAppRepository
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import java.util.Locale

@Composable
fun CreateShortcutScreen(
    onCancel: () -> Unit,
    onSave: (ScheduledAppShortcut) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { InstalledAppRepository(context).loadLaunchableApps() }

    var name by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(30) }
    var repeat by remember { mutableStateOf(RepeatOption.ONCE) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ScheduledAppShortcut?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        pendingSave?.let(onSave)
        pendingSave = null
    }

    val model = ScheduledAppShortcut(
        name = name.ifBlank { selectedApp?.label.orEmpty() },
        packageName = selectedApp?.packageName.orEmpty(),
        hour = hour,
        minute = minute,
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.builder_title), style = MaterialTheme.typography.headlineMedium) }
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
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.choose_app), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedApp?.label ?: stringResource(R.string.choose_app))
                    }
                }
            }
        }
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.time), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
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
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = ::saveWithNeededPermission, enabled = model.isValid(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showAppPicker) {
        var query by remember { mutableStateOf("") }
        val filteredApps = remember(apps, query) { AppSearch.filter(apps, query) }
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(stringResource(R.string.choose_app)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.choose_app)) },
                    )
                    if (filteredApps.isEmpty()) {
                        Text(stringResource(R.string.no_apps_found))
                    } else {
                        LazyColumn(modifier = Modifier.height(390.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                TextButton(
                                    onClick = {
                                        selectedApp = app
                                        if (name.isBlank()) name = app.label
                                        showAppPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap() }
                                            .getOrNull()?.let { icon ->
                                                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                            }
                                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text(stringResource(R.string.cancel)) } },
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
