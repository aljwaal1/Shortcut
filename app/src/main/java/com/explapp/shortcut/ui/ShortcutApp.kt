package com.explapp.shortcut.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.explapp.shortcut.R
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.domain.ShortcutCollection
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionStatus
import com.explapp.shortcut.scheduler.AndroidAlarmScheduler
import com.explapp.shortcut.scheduler.AndroidMessageScheduler
import java.text.DateFormat
import java.util.Date

private const val PREFS = "shortcut_preferences"
private const val KEY_ONBOARDING = "onboarding_complete"

private enum class MainTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Default.Home),
    TEMPLATES(R.string.templates, Icons.Default.AutoAwesome),
    HISTORY(R.string.history, Icons.Default.History),
    SETTINGS(R.string.settings, Icons.Default.Settings),
}

@Composable
fun ShortcutApp() {
    val context = LocalContext.current
    val store = remember(context) { ShortcutStore(context.applicationContext) }
    val scheduler = remember(context) { AndroidAlarmScheduler(context.applicationContext) }
    val messageStore = remember(context) { MessageStore(context.applicationContext) }
    val messageScheduler = remember(context) { AndroidMessageScheduler(context.applicationContext) }
    val shortcuts = remember { mutableStateListOf<ScheduledAppShortcut>().apply { addAll(store.load()) } }
    val messages = remember { mutableStateListOf<ScheduledMessage>().apply { addAll(messageStore.load()) } }
    var onboardingComplete by remember {
        mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING, false))
    }
    var showBuilder by remember { mutableStateOf(false) }
    var showMessageBuilder by remember { mutableStateOf<MessagePlatform?>(null) }
    var showPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shortcuts.forEach(scheduler::schedule)
        messages.forEach(messageScheduler::schedule)
    }

    when {
        !onboardingComplete -> OnboardingScreen(
            onDone = {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ONBOARDING, true).apply()
                onboardingComplete = true
            },
        )
        showPermissions -> PermissionsScreen(onBack = { showPermissions = false })
        showBuilder -> CreateShortcutScreen(
            onCancel = { showBuilder = false },
            onSave = { shortcut ->
                shortcuts.add(shortcut)
                store.save(shortcuts)
                scheduler.schedule(shortcut)
                showBuilder = false
            },
        )
        showMessageBuilder != null -> CreateMessageScreen(
            initialPlatform = showMessageBuilder ?: MessagePlatform.WHATSAPP,
            onCancel = { showMessageBuilder = null },
            onSave = { message ->
                messages.add(message)
                messageStore.save(messages)
                messageScheduler.schedule(message)
                showMessageBuilder = null
            },
        )
        else -> MainShell(
            shortcuts = shortcuts,
            messages = messages,
            onCreateShortcut = { showBuilder = true },
            onCreateMessage = { showMessageBuilder = it },
            onDeleteShortcut = { shortcut ->
                scheduler.cancel(shortcut)
                val updated = ShortcutCollection.remove(shortcuts, shortcut)
                shortcuts.clear()
                shortcuts.addAll(updated)
                store.save(updated)
            },
            onDeleteMessage = { message ->
                messageScheduler.cancel(message)
                messages.remove(message)
                messageStore.save(messages)
            },
            onOpenPermissions = { showPermissions = true },
        )
    }
}

@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        R.string.onboarding_title_1 to R.string.onboarding_body_1,
        R.string.onboarding_title_2 to R.string.onboarding_body_2,
        R.string.onboarding_title_3 to R.string.onboarding_body_3,
    )
    var page by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = if (page == 2) Icons.Default.Language else Icons.Default.AutoAwesome, contentDescription = null)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(pages[page].first), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(pages[page].second), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.skip)) }
            Button(onClick = { if (page < pages.lastIndex) page++ else onDone() }) {
                Text(stringResource(if (page < pages.lastIndex) R.string.next else R.string.get_started))
            }
        }
    }
}

@Composable
private fun MainShell(
    shortcuts: List<ScheduledAppShortcut>,
    messages: List<ScheduledMessage>,
    onCreateShortcut: () -> Unit,
    onCreateMessage: (MessagePlatform) -> Unit,
    onDeleteShortcut: (ScheduledAppShortcut) -> Unit,
    onDeleteMessage: (ScheduledMessage) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    var selected by remember { mutableStateOf(MainTab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selected == MainTab.HOME) {
                FloatingActionButton(onClick = onCreateShortcut) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_shortcut))
                }
            }
        },
    ) { padding ->
        when (selected) {
            MainTab.HOME -> HomeScreen(padding, shortcuts, messages, onCreateShortcut, onDeleteShortcut, onDeleteMessage)
            MainTab.TEMPLATES -> TemplatesScreen(padding, onCreateShortcut, onCreateMessage)
            MainTab.HISTORY -> HistoryScreen(padding)
            MainTab.SETTINGS -> SettingsScreen(padding, onOpenPermissions)
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    shortcuts: List<ScheduledAppShortcut>,
    messages: List<ScheduledMessage>,
    onCreateShortcut: () -> Unit,
    onDeleteShortcut: (ScheduledAppShortcut) -> Unit,
    onDeleteMessage: (ScheduledMessage) -> Unit,
) {
    val context = LocalContext.current
    val recent = TaskExecutionReporter(context.applicationContext).last()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.my_shortcuts), style = MaterialTheme.typography.titleMedium)
        }
        item { DashboardCard(R.string.scheduled_automations, (shortcuts.size + messages.size).toString()) }
        item { DashboardCard(R.string.scheduled_messages, messages.size.toString()) }
        item { DashboardCard(R.string.ready_templates, "3") }
        item { DashboardCard(R.string.recent_activity, if (recent == null) "0" else "1") }
        item {
            Button(onClick = onCreateShortcut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  ${stringResource(R.string.create_shortcut)}")
            }
        }
        if (shortcuts.isNotEmpty()) {
            item { Text(stringResource(R.string.saved_shortcuts), style = MaterialTheme.typography.titleLarge) }
            items(shortcuts) { shortcut ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(shortcut.name, style = MaterialTheme.typography.titleMedium)
                            Text(shortcut.packageName, style = MaterialTheme.typography.bodySmall)
                            Text("%02d:%02d • %s".format(shortcut.hour, shortcut.minute, shortcut.repeat.name), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onDeleteShortcut(shortcut) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_shortcut))
                        }
                    }
                }
            }
        }
        if (messages.isNotEmpty()) {
            item { Text(stringResource(R.string.scheduled_messages), style = MaterialTheme.typography.titleLarge) }
            items(messages) { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(message.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (message.platform == MessagePlatform.WHATSAPP) R.string.whatsapp else R.string.telegram), style = MaterialTheme.typography.bodySmall)
                            Text(message.recipient, style = MaterialTheme.typography.bodySmall)
                            Text("%02d:%02d • %s".format(message.hour, message.minute, message.repeat.name), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onDeleteMessage(message) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_message))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(label: Int, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TemplatesScreen(
    padding: PaddingValues,
    onCreateShortcut: () -> Unit,
    onCreateMessage: (MessagePlatform) -> Unit,
) {
    val templates = listOf(R.string.template_open_app, R.string.template_whatsapp, R.string.template_telegram)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.ready_templates), style = MaterialTheme.typography.headlineMedium) }
        items(templates) { title ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    when (title) {
                        R.string.template_open_app -> Button(onClick = onCreateShortcut) { Text(stringResource(R.string.create_shortcut)) }
                        R.string.template_whatsapp -> Button(onClick = { onCreateMessage(MessagePlatform.WHATSAPP) }) { Text(stringResource(R.string.template_whatsapp)) }
                        R.string.template_telegram -> Button(onClick = { onCreateMessage(MessagePlatform.TELEGRAM) }) { Text(stringResource(R.string.template_telegram)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val result = TaskExecutionReporter(context.applicationContext).last()
    val ar = configuration.locales[0].language == "ar"
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium) }
        if (result == null) {
            item { Text(stringResource(R.string.recent_activity)) }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (result.status == TaskExecutionStatus.SUCCESS) {
                                if (ar) "✅ تم التنفيذ بنجاح" else "✅ Completed successfully"
                            } else {
                                if (ar) "❌ فشل التنفيذ" else "❌ Execution failed"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(result.taskName, style = MaterialTheme.typography.bodyLarge)
                        result.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            if (ar) "المدة: %.1f ث".format(result.durationMs / 1000.0) else "Duration: %.1fs".format(result.durationMs / 1000.0),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.finishedAtMs)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, onOpenPermissions: () -> Unit) {
    val rows = listOf(R.string.backup, R.string.contact_us, R.string.feedback, R.string.report_problem, R.string.privacy, R.string.about)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { setAppLocale("") }) { Text(stringResource(R.string.language_system)) }
                        TextButton(onClick = { setAppLocale("ar") }) { Text(stringResource(R.string.language_arabic)) }
                        TextButton(onClick = { setAppLocale("en") }) { Text(stringResource(R.string.language_english)) }
                    }
                }
            }
        }
        item {
            Card(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.permissions), modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        items(rows) { label ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label), modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshKey++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshKey++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsGranted = remember(refreshKey) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    val exactAlarmGranted = remember(refreshKey) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.permissions), style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            PermissionCard(
                title = stringResource(R.string.notification_permission),
                description = stringResource(R.string.notification_permission_desc),
                granted = notificationsGranted,
                action = if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                    { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                } else null,
            )
        }
        item {
            PermissionCard(
                title = stringResource(R.string.exact_alarm_permission),
                description = stringResource(R.string.exact_alarm_permission_desc),
                granted = exactAlarmGranted,
                action = if (!exactAlarmGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, action: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(if (granted) R.string.permission_granted else R.string.permission_needed), style = MaterialTheme.typography.labelLarge)
            if (action != null) Button(onClick = action) { Text(stringResource(R.string.allow_permission)) }
        }
    }
}

private fun setAppLocale(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}
