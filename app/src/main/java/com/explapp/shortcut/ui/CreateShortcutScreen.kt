package com.explapp.shortcut.ui

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.explapp.shortcut.R
import com.explapp.shortcut.data.InstalledApp
import com.explapp.shortcut.data.InstalledAppRepository
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.permissions.SchedulingPermissionPlan
import com.explapp.shortcut.permissions.SchedulingPermissionStep
import java.util.Locale
import java.util.UUID

@Composable
fun CreateShortcutScreen(
    onCancel: () -> Unit,
    onSave: (ScheduledAppShortcut) -> Unit,
    initial: ScheduledAppShortcut? = null,
) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val apps = remember { InstalledAppRepository(context).loadLaunchableApps() }
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    val stableId = remember(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }

    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var selectedApp by remember(initial?.id, apps) {
        mutableStateOf(initial?.let { current -> apps.firstOrNull { it.packageName == current.packageName } ?: InstalledApp(current.name, current.packageName) })
    }
    var hour by remember(initial?.id) { mutableIntStateOf(initial?.hour ?: 7) }
    var minute by remember(initial?.id) { mutableIntStateOf(initial?.minute ?: 30) }
    var repeat by remember(initial?.id) { mutableStateOf(initial?.repeat ?: RepeatOption.ONCE) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ScheduledAppShortcut?>(null) }
    var permissionError by remember { mutableStateOf(false) }

    fun finishPendingSave() {
        pendingSave?.let(onSave)
        pendingSave = null
        permissionError = false
    }

    val exactAlarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishPendingSave() }

    fun requestExactAlarmOrSave() {
        val exactGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (!exactGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        } else finishPendingSave()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestExactAlarmOrSave()
        } else {
            pendingSave = null
            permissionError = true
        }
    }

    val model = ScheduledAppShortcut(
        id = stableId,
        name = name.ifBlank { selectedApp?.label.orEmpty() },
        packageName = selectedApp?.packageName.orEmpty(),
        hour = hour,
        minute = minute,
        repeat = repeat,
        isEnabled = initial?.isEnabled ?: true,
    )

    fun saveWithNeededPermissions() {
        permissionError = false
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
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), modifier = Modifier.size(50.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (initial == null) stringResource(R.string.builder_title) else if (ar) "تعديل المهمة" else "Edit automation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(if (ar) "اختر التطبيق والوقت والتكرار" else "Choose the app, time and repeat rule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.shortcut_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            BuilderSectionCard(title = stringResource(R.string.choose_app), subtitle = selectedApp?.label ?: if (ar) "ابحث عن التطبيق المثبت على جهازك" else "Search installed apps on your phone", accent = MaterialTheme.colorScheme.secondary) {
                Button(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedApp?.label ?: stringResource(R.string.choose_app), fontWeight = FontWeight.Bold) }
            }
        }
        item {
            BuilderSectionCard(title = stringResource(R.string.time), subtitle = if (ar) "وقت ظهور تنبيه فتح التطبيق" else "When the open-app notification should appear", accent = MaterialTheme.colorScheme.tertiary) {
                Button(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() }, modifier = Modifier.fillMaxWidth()) {
                    Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item {
            BuilderSectionCard(title = stringResource(R.string.repeat), subtitle = if (ar) "اختر مرة واحدة أو تكرار تلقائي" else "Run once or repeat automatically", accent = MaterialTheme.colorScheme.primary) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    RepeatOption.entries.forEach { option -> FilterChip(selected = repeat == option, onClick = { repeat = option }, label = { Text(repeatLabel(option)) }) }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
                Text(
                    if (ar) "عند الموعد يظهر إشعار موثوق. اضغط عليه لفتح التطبيق؛ أندرويد يقيّد فتح التطبيقات قسرًا من الخلفية." else "At the scheduled time Shortcut shows a reliable notification. Tap it to open the app; Android restricts forced background app launches.",
                    modifier = Modifier.padding(15.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (permissionError) {
            item {
                Text(
                    if (ar) "يلزم السماح بالإشعارات لحفظ هذه المهمة المجدولة." else "Notification permission is required to save this scheduled app task.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = ::saveWithNeededPermissions, enabled = model.isValid(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showAppPicker) {
        var query by remember { mutableStateOf("") }
        val filteredApps = remember(apps, query) { AppSearch.filter(apps, query) }
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(stringResource(R.string.choose_app), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, placeholder = { Text(if (ar) "ابحث باسم التطبيق" else "Search by app name") })
                    if (filteredApps.isEmpty()) Text(stringResource(R.string.no_apps_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LazyColumn(modifier = Modifier.height(390.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            TextButton(onClick = { selectedApp = app; if (name.isBlank()) name = app.label; showAppPicker = false }, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap() }.getOrNull()?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(34.dp)) }
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
private fun BuilderSectionCard(title: String, subtitle: String, accent: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.07f)), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun repeatLabel(option: RepeatOption): String = when (option) {
    RepeatOption.ONCE -> stringResource(R.string.repeat_once)
    RepeatOption.DAILY -> stringResource(R.string.repeat_daily)
    RepeatOption.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatOption.WEEKLY -> stringResource(R.string.repeat_weekly)
}
