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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.explapp.shortcut.scheduler.AndroidAlarmScheduler
import com.explapp.shortcut.scheduler.AndroidMessageScheduler

private const val PREFS = "shortcut_preferences"
private const val KEY_ONBOARDING = "onboarding_complete"

private enum class MainTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Default.Home),
    TEMPLATES(R.string.templates, Icons.Default.AutoAwesome),
    HISTORY(R.string.history, Icons.Default.History),
    SETTINGS(R.string.settings, Icons.Default.Settings),
}

@Composable
fun ShortcutApp(onOpenTools: () -> Unit = {}) {
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
    var editingShortcut by remember { mutableStateOf<ScheduledAppShortcut?>(null) }
    var editingMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    var showPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shortcuts.filter { it.isEnabled }.forEach(scheduler::schedule)
        messages.filter { it.isEnabled }.forEach(messageScheduler::schedule)
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
        editingShortcut != null -> CreateShortcutScreen(
            initial = editingShortcut,
            onCancel = { editingShortcut = null },
            onSave = { saved ->
                val old = editingShortcut
                if (old != null) scheduler.cancelById(old.id)
                val index = shortcuts.indexOfFirst { it.id == saved.id }
                if (index >= 0) shortcuts[index] = saved else shortcuts.add(saved)
                store.save(shortcuts)
                if (saved.isEnabled) scheduler.schedule(saved)
                editingShortcut = null
            },
        )
        editingMessage != null -> CreateMessageScreen(
            initial = editingMessage,
            initialPlatform = editingMessage?.platform ?: MessagePlatform.WHATSAPP,
            onCancel = { editingMessage = null },
            onSave = { saved ->
                val old = editingMessage
                if (old != null) messageScheduler.cancelById(old.id)
                val index = messages.indexOfFirst { it.id == saved.id }
                if (index >= 0) messages[index] = saved else messages.add(saved)
                messageStore.save(messages)
                if (saved.isEnabled) messageScheduler.schedule(saved)
                editingMessage = null
            },
        )
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
            onOpenTools = onOpenTools,
            onCreateMessage = { showMessageBuilder = it },
            onEditShortcut = { editingShortcut = it },
            onEditMessage = { editingMessage = it },
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
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = if (page == 2) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (page == 2) Icons.Default.Language else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = if (page == 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(pages[page].first), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(pages[page].second),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "${page + 1} / ${pages.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(28.dp))
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
    onOpenTools: () -> Unit,
    onCreateMessage: (MessagePlatform) -> Unit,
    onEditShortcut: (ScheduledAppShortcut) -> Unit,
    onEditMessage: (ScheduledMessage) -> Unit,
    onDeleteShortcut: (ScheduledAppShortcut) -> Unit,
    onDeleteMessage: (ScheduledMessage) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    var selected by remember { mutableStateOf(MainTab.HOME) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 5.dp,
            ) {
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
                FloatingActionButton(
                    onClick = onCreateShortcut,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_shortcut))
                }
            }
        },
    ) { padding ->
        when (selected) {
            MainTab.HOME -> HomeScreen(
                padding = padding,
                shortcuts = shortcuts,
                messages = messages,
                onCreateShortcut = onCreateShortcut,
                onOpenTools = onOpenTools,
                onEditShortcut = onEditShortcut,
                onEditMessage = onEditMessage,
                onDeleteShortcut = onDeleteShortcut,
                onDeleteMessage = onDeleteMessage,
            )
            MainTab.TEMPLATES -> TemplatesScreen(padding, onCreateShortcut, onCreateMessage)
            MainTab.HISTORY -> ExecutionHistoryScreen(padding)
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
    onOpenTools: () -> Unit,
    onEditShortcut: (ScheduledAppShortcut) -> Unit,
    onEditMessage: (ScheduledMessage) -> Unit,
    onDeleteShortcut: (ScheduledAppShortcut) -> Unit,
    onDeleteMessage: (ScheduledMessage) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
    val recent = TaskExecutionReporter(context.applicationContext).last()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ShortcutHero(
                isArabic = ar,
                activeCount = shortcuts.size + messages.size,
                onOpenTools = onOpenTools,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    label = stringResource(R.string.scheduled_automations),
                    value = (shortcuts.size + messages.size).toString(),
                    icon = Icons.Default.AutoAwesome,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.scheduled_messages),
                    value = messages.size.toString(),
                    icon = Icons.Default.Language,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    label = stringResource(R.string.ready_templates),
                    value = "3",
                    icon = Icons.Default.Add,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.recent_activity),
                    value = if (recent == null) "0" else "1",
                    icon = Icons.Default.History,
                    accent = Color(0xFF1B9C68),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Button(onClick = onCreateShortcut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  ${stringResource(R.string.create_shortcut)}", fontWeight = FontWeight.Bold)
            }
        }
        item {
            SectionLabel(
                title = if (ar) "آخر تنفيذ" else "Latest run",
                subtitle = if (ar) "تعرف فورًا إن كانت المهمة نجحت أو فشلت" else "See immediately whether the latest task succeeded",
            )
        }
        item { ExecutionStatusCard(result = recent, isArabic = ar) }
        item { HomeToolQuickRows() }

        if (shortcuts.isNotEmpty()) {
            item {
                SectionLabel(
                    title = stringResource(R.string.saved_shortcuts),
                    subtitle = if (ar) "مهامك المجدولة الجاهزة للعمل" else "Your scheduled actions, ready to run",
                )
            }
            items(shortcuts, key = { it.id }) { shortcut ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(shortcut.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(shortcut.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "%02d:%02d • %s".format(shortcut.hour, shortcut.minute, shortcut.repeat.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onEditShortcut(shortcut) }) {
                            Icon(Icons.Default.Edit, contentDescription = if (ar) "تعديل المهمة" else "Edit task", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onDeleteShortcut(shortcut) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_shortcut), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (messages.isNotEmpty()) {
            item {
                SectionLabel(
                    title = stringResource(R.string.scheduled_messages),
                    subtitle = if (ar) "رسائل مجهزة تفتح في الوقت الذي اخترته" else "Prepared messages opened at your chosen time",
                )
            }
            items(messages, key = { it.id }) { message ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccentIcon(Icons.Default.Language, MaterialTheme.colorScheme.secondary)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(message.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(if (message.platform == MessagePlatform.WHATSAPP) R.string.whatsapp else R.string.telegram),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(message.recipient, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "%02d:%02d • %s".format(message.hour, message.minute, message.repeat.name),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconButton(onClick = { onEditMessage(message) }) {
                            Icon(Icons.Default.Edit, contentDescription = if (ar) "تعديل الرسالة" else "Edit message", tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { onDeleteMessage(message) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_message), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TemplatesScreen(
    padding: PaddingValues,
    onCreateShortcut: () -> Unit,
    onCreateMessage: (MessagePlatform) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
    data class TemplateUi(val title: Int, val accent: Color, val action: () -> Unit)
    val templates = listOf(
        TemplateUi(R.string.template_open_app, MaterialTheme.colorScheme.primary, onCreateShortcut),
        TemplateUi(R.string.template_whatsapp, MaterialTheme.colorScheme.secondary) { onCreateMessage(MessagePlatform.WHATSAPP) },
        TemplateUi(R.string.template_telegram, MaterialTheme.colorScheme.tertiary) { onCreateMessage(MessagePlatform.TELEGRAM) },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionLabel(
                title = stringResource(R.string.ready_templates),
                subtitle = if (ar) "ابدأ من قالب جاهز ثم عدله كما تريد" else "Start from a ready template and make it yours",
            )
        }
        items(templates) { template ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = template.accent.copy(alpha = 0.09f)),
                onClick = template.action,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, template.accent)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(template.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (ar) "إعداد سريع بخطوات قليلة" else "Quick setup in a few steps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", color = template.accent, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, onOpenPermissions: () -> Unit) {
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
    val rows = listOf(
        R.string.backup,
        R.string.contact_us,
        R.string.feedback,
        R.string.report_problem,
        R.string.privacy,
        R.string.about,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel(
                title = stringResource(R.string.settings),
                subtitle = if (ar) "خصص اللغة والصلاحيات وخيارات التطبيق" else "Language, permissions and app options",
            )
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentIcon(Icons.Default.Language, MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { setAppLocale("") }) { Text(stringResource(R.string.language_system)) }
                        TextButton(onClick = { setAppLocale("ar") }) { Text(stringResource(R.string.language_arabic)) }
                        TextButton(onClick = { setAppLocale("en") }) { Text(stringResource(R.string.language_english)) }
                    }
                }
            }
        }
        item {
            ElevatedCard(
                onClick = onOpenPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.Settings, MaterialTheme.colorScheme.secondary)
                    Text(stringResource(R.string.permissions), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("→", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        items(rows) { label ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.tertiary)
                    Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
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
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column {
                    Text(stringResource(R.string.permissions), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (ar) "لا نطلب إلا ما تحتاجه الميزة" else "Only permissions needed by the feature",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    val accent = if (granted) Color(0xFF1B9C68) else MaterialTheme.colorScheme.tertiary
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f), modifier = Modifier.size(12.dp)) {}
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(if (granted) R.string.permission_granted else R.string.permission_needed),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            if (action != null) Button(onClick = action) { Text(stringResource(R.string.allow_permission)) }
        }
    }
}

private fun setAppLocale(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}