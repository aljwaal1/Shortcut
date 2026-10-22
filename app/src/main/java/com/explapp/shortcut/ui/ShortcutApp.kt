package com.explapp.shortcut.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.explapp.shortcut.backup.BackupTransferActivity
import com.explapp.shortcut.data.MessageStore
import com.explapp.shortcut.data.ShortcutStore
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.domain.ScheduledAppShortcut
import com.explapp.shortcut.domain.ScheduledMessage
import com.explapp.shortcut.domain.RepeatOption
import com.explapp.shortcut.domain.ShortcutCollection
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.quality.CrashLogStore
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.search.CommandPaletteActivity
import com.explapp.shortcut.tools.ToolCatalog
import com.explapp.shortcut.tools.ToolRouter
import com.explapp.shortcut.tools.ToolPreferencesStore
import com.explapp.shortcut.tools.PersistentScreenCaptureService
import com.explapp.shortcut.scheduler.AndroidAlarmScheduler
import com.explapp.shortcut.scheduler.AndroidMessageScheduler

private const val PREFS = "shortcut_preferences"
private const val KEY_ONBOARDING = "onboarding_complete"

private enum class MainTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Default.Home),
    TEMPLATES(R.string.templates, Icons.Default.AutoAwesome),
    TOOLS(R.string.tools, Icons.Default.Build),
    HISTORY(R.string.history, Icons.Default.History),
    SETTINGS(R.string.settings, Icons.Default.Settings),
}

@Composable
fun ShortcutApp(onOpenTools: () -> Unit = {}) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
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

    fun scheduleShortcut(item: ScheduledAppShortcut): ScheduledAppShortcut {
        if (!item.isEnabled) {
            runCatching { scheduler.cancelById(item.id) }
            return item
        }
        if (scheduler.schedule(item)) return item
        runCatching { scheduler.cancelById(item.id) }
        Toast.makeText(
            context,
            if (ar) {
                "تعذر جدولة المهمة. تم حفظها كمتوقفة بدل إظهارها كمفعلة."
            } else {
                "Could not schedule this task. It was saved as paused instead of appearing active."
            },
            Toast.LENGTH_LONG,
        ).show()
        return item.copy(isEnabled = false)
    }

    fun scheduleMessage(item: ScheduledMessage): ScheduledMessage {
        if (!item.isEnabled) {
            runCatching { messageScheduler.cancelById(item.id) }
            return item
        }
        if (messageScheduler.schedule(item)) return item
        runCatching { messageScheduler.cancelById(item.id) }
        Toast.makeText(
            context,
            if (ar) {
                "تعذر جدولة الرسالة. تم حفظها كمتوقفة بدل إظهارها كمفعلة."
            } else {
                "Could not schedule this message. It was saved as paused instead of appearing active."
            },
            Toast.LENGTH_LONG,
        ).show()
        return item.copy(isEnabled = false)
    }

    LaunchedEffect(Unit) {
        shortcuts.filter { it.isEnabled }.forEach { shortcut ->
            runCatching { scheduler.schedule(shortcut) }
        }
        messages.filter { it.isEnabled }.forEach { message ->
            runCatching { messageScheduler.schedule(message) }
        }
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
                val persisted = scheduleShortcut(saved)
                val index = shortcuts.indexOfFirst { it.id == persisted.id }
                if (index >= 0) shortcuts[index] = persisted else shortcuts.add(persisted)
                store.save(shortcuts)
                editingShortcut = null
            },
        )
        editingMessage != null -> CreateMessageScreen(
            initial = editingMessage,
            initialPlatform = editingMessage?.platform ?: MessagePlatform.WHATSAPP,
            onCancel = { editingMessage = null },
            onSave = { saved ->
                val persisted = scheduleMessage(saved)
                val index = messages.indexOfFirst { it.id == persisted.id }
                if (index >= 0) messages[index] = persisted else messages.add(persisted)
                messageStore.save(messages)
                editingMessage = null
            },
        )
        showBuilder -> CreateShortcutScreen(
            onCancel = { showBuilder = false },
            onSave = { shortcut ->
                shortcuts.add(scheduleShortcut(shortcut))
                store.save(shortcuts)
                showBuilder = false
            },
        )
        showMessageBuilder != null -> CreateMessageScreen(
            initialPlatform = showMessageBuilder ?: MessagePlatform.WHATSAPP,
            onCancel = { showMessageBuilder = null },
            onSave = { message ->
                messages.add(scheduleMessage(message))
                messageStore.save(messages)
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
                runCatching { scheduler.cancel(shortcut) }
                val updated = ShortcutCollection.remove(shortcuts, shortcut)
                shortcuts.clear()
                shortcuts.addAll(updated)
                store.save(updated)
            },
            onDeleteMessage = { message ->
                runCatching { messageScheduler.cancel(message) }
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
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showCreateMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 5.dp,
            ) {
                MainTab.entries.filter { it == MainTab.HOME || it == MainTab.TEMPLATES || it == MainTab.TOOLS }.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = {
                            if (tab == MainTab.TOOLS) onOpenTools() else selected = tab
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selected == MainTab.HOME) {
                FloatingActionButton(
                    onClick = { showCreateMenu = true },
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
                onOpenTemplates = { selected = MainTab.TEMPLATES },
                onOpenHistory = { selected = MainTab.HISTORY },
                onOpenSettings = { selected = MainTab.SETTINGS },
                onEditShortcut = onEditShortcut,
                onEditMessage = onEditMessage,
                onDeleteShortcut = onDeleteShortcut,
                onDeleteMessage = onDeleteMessage,
            )
            MainTab.TEMPLATES -> TemplatesScreen(
                padding, onCreateShortcut, onCreateMessage,
                onOpenAdvancedTemplates = {
                    context.startActivity(
                        Intent(context, MyAutomationsActivity::class.java)
                            .putExtra(MyAutomationsActivity.EXTRA_START_MODE, MyAutomationsActivity.MODE_TEMPLATES),
                    )
                },
                onOpenScreenshotTemplate = {
                    context.startActivity(
                        Intent(context, MyAutomationsActivity::class.java)
                            .putExtra(MyAutomationsActivity.EXTRA_START_MODE, MyAutomationsActivity.MODE_SCREENSHOT),
                    )
                },
            )
            MainTab.TOOLS -> Unit
            MainTab.HISTORY -> ExecutionHistoryScreen(padding)
            MainTab.SETTINGS -> SettingsScreen(padding, onOpenPermissions)
        }
    }

    if (showCreateMenu) {
        AlertDialog(
            onDismissRequest = { showCreateMenu = false },
            title = { Text(if (ar) "ماذا تريد أن تنشئ؟" else "What do you want to create?", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreateChoice(
                        title = if (ar) "فتح تطبيق بموعد" else "Scheduled app",
                        subtitle = if (ar) "افتح تطبيقًا تلقائيًا في الوقت الذي تختاره" else "Open an app automatically at a chosen time",
                        accent = MaterialTheme.colorScheme.primary,
                    ) {
                        showCreateMenu = false
                        onCreateShortcut()
                    }
                    CreateChoice(
                        title = if (ar) "رسالة واتساب" else "WhatsApp message",
                        subtitle = if (ar) "جهّز رسالة في موعد محدد" else "Prepare a message on schedule",
                        accent = MaterialTheme.colorScheme.secondary,
                    ) {
                        showCreateMenu = false
                        onCreateMessage(MessagePlatform.WHATSAPP)
                    }
                    CreateChoice(
                        title = if (ar) "رسالة تيليجرام" else "Telegram message",
                        subtitle = if (ar) "جهّز رسالة في موعد محدد" else "Prepare a message on schedule",
                        accent = MaterialTheme.colorScheme.tertiary,
                    ) {
                        showCreateMenu = false
                        onCreateMessage(MessagePlatform.TELEGRAM)
                    }
                    CreateChoice(
                        title = if (ar) "أتمتة متقدمة" else "Advanced automation",
                        subtitle = if (ar) "مشغلات وشروط وإجراءات متعددة" else "Triggers, conditions and multi-step actions",
                        accent = MaterialTheme.colorScheme.primary,
                    ) {
                        showCreateMenu = false
                        context.startActivity(Intent(context, MyAutomationsActivity::class.java))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCreateMenu = false }) {
                    Text(if (ar) "إغلاق" else "Close")
                }
            },
        )
    }
}

@Composable
private fun CreateChoice(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentIcon(Icons.Default.Add, accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.14f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(12.dp).size(26.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
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
    onOpenTemplates: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditShortcut: (ScheduledAppShortcut) -> Unit,
    onEditMessage: (ScheduledMessage) -> Unit,
    onDeleteShortcut: (ScheduledAppShortcut) -> Unit,
    onDeleteMessage: (ScheduledMessage) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
    var dashboardRefresh by remember { mutableIntStateOf(0) }
    var pendingDeleteShortcut by remember { mutableStateOf<ScheduledAppShortcut?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) dashboardRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val reporter = remember(context) { TaskExecutionReporter(context.applicationContext) }
    val recent = remember(dashboardRefresh) { reporter.last() }
    val advancedRoutines = remember(dashboardRefresh) { RoutineStore(context.applicationContext).load() }
    val toolCount = remember { ToolCatalog.all().size }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ShortcutHero(
                isArabic = ar,
                activeCount = shortcuts.size + messages.size + advancedRoutines.count { it.isEnabled },
                onOpenTools = onOpenTools,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { setAppLocale("ar") }) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Text(" العربية")
                }
                TextButton(onClick = { setAppLocale("en") }) { Text("English") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text(if (ar) " الإعدادات" else " Settings")
                }
            }
        }
        item {
            SectionLabel(
                title = if (ar) "مركز الأتمتة" else "Automation center",
                subtitle = if (ar) "كل أنواع الأتمتة والقوالب والتقارير من مكان واحد"
                else "All automation types, templates, and reports in one place",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeNavCard(
                    title = if (ar) "تصميم أتمتة" else "Design automation",
                    subtitle = if (ar) "أنشئ أتمتة متقدمة بمشغل وشروط وخطوات متعددة" else "Build an advanced routine with a trigger, conditions and multiple steps",
                    icon = Icons.Default.DesignServices,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    context.startActivity(
                        Intent(context, MyAutomationsActivity::class.java)
                            .putExtra(MyAutomationsActivity.EXTRA_START_MODE, MyAutomationsActivity.MODE_CREATE),
                    )
                }
                HomeNavCard(
                    title = if (ar) "أتمتاتي" else "My automations",
                    subtitle = if (ar) "عرض جميع الأتمتات وتشغيلها وتعديلها" else "View, run and edit all of your automations",
                    icon = Icons.Default.ListAlt,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    context.startActivity(Intent(context, MyAutomationsActivity::class.java))
                }
                HomeNavCard(
                    title = if (ar) "قوالب الأتمتة" else "Automation templates",
                    subtitle = if (ar) "قوالب جاهزة قابلة للتعديل والنسخ" else "Ready-made routines you can edit and copy",
                    icon = Icons.Default.CollectionsBookmark,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    context.startActivity(
                        Intent(context, MyAutomationsActivity::class.java)
                            .putExtra(MyAutomationsActivity.EXTRA_START_MODE, MyAutomationsActivity.MODE_TEMPLATES),
                    )
                }
                HomeNavCard(
                    title = if (ar) "قوالب سريعة" else "Quick templates",
                    subtitle = if (ar) "قوالب سريعة للتطبيقات والرسائل" else "Quick templates for apps and messages",
                    icon = Icons.Default.AutoAwesome,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) { onOpenTemplates() }
                HomeNavCard(
                    title = if (ar) "فتح تطبيق + لقطة شاشة" else "Open app + screenshot",
                    subtitle = if (ar) "تشغيل يدوي أو بموعد، مع التاريخ والحفظ أو المشاركة" else "Manual or scheduled, dated, save or share",
                    icon = Icons.Default.CameraAlt,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    context.startActivity(
                        Intent(context, MyAutomationsActivity::class.java)
                            .putExtra(MyAutomationsActivity.EXTRA_START_MODE, MyAutomationsActivity.MODE_SCREENSHOT),
                    )
                }
                HomeNavCard(
                    title = if (ar) "المهام المجدولة" else "Scheduled tasks",
                    subtitle = if (ar) "إدارة المواعيد والتكرار للمهام" else "Manage scheduled times and repeats",
                    icon = Icons.Default.Schedule,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    context.startActivity(Intent(context, ScheduledTasksManagerActivity::class.java))
                }
                HomeNavCard(
                    title = if (ar) "الرسائل" else "Messages",
                    subtitle = if (ar) "أتمتة رسائل واتساب وتيليجرام" else "WhatsApp and Telegram message automation",
                    icon = Icons.Default.Chat,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) { onOpenTemplates() }
                HomeNavCard(
                    title = if (ar) "سجل Debug" else "Debug log",
                    subtitle = if (ar) "سجل النجاح والفشل وأسباب المشاكل لكل الأتمتات" else "Success, failure and diagnostic reasons for all automations",
                    icon = Icons.Default.BugReport,
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                ) { onOpenHistory() }
                HomeNavCard(
                    title = if (ar) "الإعدادات والخصوصية" else "Settings and privacy",
                    subtitle = if (ar) "الأذونات والمظهر والنسخ الاحتياطي" else "Permissions, appearance and backup",
                    icon = Icons.Default.Settings,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                ) { onOpenSettings() }
                HomeNavCard(
                    title = if (ar) "الأدوات" else "Tools",
                    subtitle = if (ar) "الوصول إلى جميع أدوات Shortcut" else "Open all Shortcut tools",
                    icon = Icons.Default.Build,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                ) { onOpenTools() }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ElevatedCard(
                    onClick = { context.startActivity(Intent(context, MyAutomationsActivity::class.java)) },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.primary)
                        Text(if (ar) "استوديو الأتمتة" else "Automation studio", fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (ar) "أنشئ مهام متعددة الخطوات وشغّلها يدويًا أو تلقائيًا"
                            else "Build multi-step routines and run them manually or automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ElevatedCard(
                    onClick = { context.startActivity(Intent(context, CommandPaletteActivity::class.java)) },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentIcon(Icons.Default.Search, MaterialTheme.colorScheme.secondary)
                        Text(if (ar) "بحث سريع" else "Quick search", fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (ar) "ابحث في الأدوات والاختصارات والقوالب من مكان واحد"
                            else "Find tools, shortcuts and templates from one place",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    label = stringResource(R.string.scheduled_automations),
                    value = (shortcuts.size + messages.size + advancedRoutines.size).toString(),
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
                    label = if (ar) "الأدوات المتاحة" else "Available tools",
                    value = toolCount.toString(),
                    icon = Icons.Default.Build,
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
            val notificationsReady = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
            val exactReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            val captureReady = PersistentScreenCaptureService.isSessionActive(context)

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ar) "جاهزية الأتمتة" else "Automation readiness",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                if (ar) "حالة أهم المتطلبات التي تؤثر على التنفيذ"
                                else "Key requirements that affect reliable execution",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { dashboardRefresh++ }) {
                            Text(if (ar) "تحديث" else "Refresh")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadinessPill(
                            label = if (ar) "الإشعارات" else "Notifications",
                            ready = notificationsReady,
                            modifier = Modifier.weight(1f),
                        )
                        ReadinessPill(
                            label = if (ar) "الوقت الدقيق" else "Exact time",
                            ready = exactReady,
                            modifier = Modifier.weight(1f),
                        )
                        ReadinessPill(
                            label = if (ar) "التصوير" else "Capture",
                            ready = captureReady,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
                                "%02d:%02d • %s".format(shortcut.hour, shortcut.minute, repeatText(shortcut.repeat, ar)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onEditShortcut(shortcut) }) {
                            Icon(Icons.Default.Edit, contentDescription = if (ar) "تعديل المهمة" else "Edit task", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { pendingDeleteShortcut = shortcut }) {
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
                                "%02d:%02d • %s".format(message.hour, message.minute, repeatText(message.repeat, ar)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconButton(onClick = { onEditMessage(message) }) {
                            Icon(Icons.Default.Edit, contentDescription = if (ar) "تعديل الرسالة" else "Edit message", tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { pendingDeleteMessage = message }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_message), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    pendingDeleteShortcut?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteShortcut = null },
            title = { Text(if (ar) "حذف الاختصار؟" else "Delete shortcut?", fontWeight = FontWeight.Bold) },
            text = { Text(if (ar) "سيتم حذف «${item.name}» وإلغاء جدولته." else "“${item.name}” will be deleted and unscheduled.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteShortcut(item)
                    pendingDeleteShortcut = null
                }) { Text(if (ar) "حذف" else "Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteShortcut = null }) { Text(if (ar) "إلغاء" else "Cancel") } },
        )
    }

    pendingDeleteMessage?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text(if (ar) "حذف الرسالة المجدولة؟" else "Delete scheduled message?", fontWeight = FontWeight.Bold) },
            text = { Text(if (ar) "سيتم حذف «${item.name}» وإلغاء جدولتها." else "“${item.name}” will be deleted and unscheduled.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMessage(item)
                    pendingDeleteMessage = null
                }) { Text(if (ar) "حذف" else "Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteMessage = null }) { Text(if (ar) "إلغاء" else "Cancel") } },
        )
    }
}

@Composable
private fun ReadinessPill(
    label: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (ready) Color(0xFF1B9C68) else MaterialTheme.colorScheme.tertiary
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.10f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (ready) "✓" else "!",
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
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
    onOpenAdvancedTemplates: () -> Unit,
    onOpenScreenshotTemplate: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
    var templateSearch by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val store = remember(context) { TemplateLibraryStore(context) }
    val toolStore = remember(context) { ToolPreferencesStore(context.applicationContext) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var recent by remember { mutableStateOf(store.recent()) }
    var selectedCategory by rememberSaveable { mutableStateOf(TemplateLibraryFilter.ALL) }
    data class TemplateUi(val id: String, val category: String, val title: String, val description: String, val accent: Color, val action: () -> Unit)
    val templates = listOf(
        TemplateUi("advanced", "automation", if (ar) "قوالب الأتمتة المتقدمة" else "Advanced automations",
            if (ar) "سلاسل إجراءات جاهزة يمكنك تعديلها" else "Ready multi-step workflows to customize",
            MaterialTheme.colorScheme.primary, onOpenAdvancedTemplates),
        TemplateUi("screenshot", "media", if (ar) "فتح تطبيق وتصوير الشاشة" else "Open app and capture screen",
            if (ar) "اختر التطبيق وأضف التاريخ واحفظ الصورة" else "Choose an app, stamp the date and save the image",
            MaterialTheme.colorScheme.secondary, onOpenScreenshotTemplate),
        TemplateUi("scheduled_app", "automation", stringResource(R.string.template_open_app),
            if (ar) "افتح تطبيقًا في وقت تحدده" else "Open an app at a chosen time",
            MaterialTheme.colorScheme.primary, onCreateShortcut),
        TemplateUi("whatsapp", "messaging", stringResource(R.string.template_whatsapp),
            if (ar) "إعداد رسالة واتساب مجدولة" else "Prepare a scheduled WhatsApp message",
            MaterialTheme.colorScheme.secondary) { onCreateMessage(MessagePlatform.WHATSAPP) },
        TemplateUi("telegram", "messaging", stringResource(R.string.template_telegram),
            if (ar) "إعداد رسالة تيليجرام" else "Prepare a Telegram message",
            MaterialTheme.colorScheme.tertiary) { onCreateMessage(MessagePlatform.TELEGRAM) },
    ) + TemplateToolPresets.entries.map { preset ->
        TemplateUi(
            id = preset.id,
            category = preset.category,
            title = if (ar) preset.titleAr else preset.titleEn,
            description = if (ar) preset.descriptionAr else preset.descriptionEn,
            accent = when (preset.category) {
                "pdf" -> MaterialTheme.colorScheme.primary
                "images" -> MaterialTheme.colorScheme.secondary
                "files" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            action = {
                context.startActivity(ToolRouter.intent(context, preset.tool))
                toolStore.recordRecent(preset.tool)
            },
        )
    }
    val visibleTemplates = TemplateLibraryFilter.select(
        templates.map { template ->
            val preset = TemplateToolPresets.entries.firstOrNull { it.id == template.id }
            TemplateDescriptor(
                template.id, template.title, template.description, template.category,
                searchTerms = preset?.let {
                    "${it.titleAr} ${it.titleEn} ${it.descriptionAr} ${it.descriptionEn}"
                }.orEmpty(),
            )
        },
        templateSearch, selectedCategory, favorites, recent,
    ).mapNotNull { descriptor -> templates.firstOrNull { it.id == descriptor.id } }
    val openTemplate: (TemplateUi) -> Unit = { template ->
        recent = store.recordOpen(template.id)
        template.action()
    }
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
        item {
            androidx.compose.material3.OutlinedTextField(
                value = templateSearch,
                onValueChange = { templateSearch = it },
                label = { Text(if (ar) "ابحث عن قالب" else "Search templates") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val filters = listOf(
                    TemplateLibraryFilter.ALL to (if (ar) "الكل" else "All"),
                    TemplateLibraryFilter.FAVORITES to (if (ar) "المفضلة" else "Favorites"),
                    TemplateLibraryFilter.RECENT to (if (ar) "الأخيرة" else "Recent"),
                    "automation" to (if (ar) "الأتمتة" else "Automation"),
                    "messaging" to (if (ar) "الرسائل" else "Messages"),
                    "media" to (if (ar) "الوسائط" else "Media"),
                    "pdf" to "PDF",
                    "images" to (if (ar) "الصور" else "Images"),
                    "files" to (if (ar) "الملفات" else "Files"),
                    "quick" to (if (ar) "أدوات سريعة" else "Quick"),
                )
                items(filters.size) { index ->
                    val (key, label) = filters[index]
                    androidx.compose.material3.FilterChip(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (visibleTemplates.isEmpty()) {
            item {
                InlineNotice(
                    title = if (ar) "لا توجد قوالب مطابقة" else "No matching templates",
                    body = if (ar) "جرّب البحث بكلمة أخرى أو امسح حقل البحث." else "Try another term or clear your search.",
                    color = MaterialTheme.colorScheme.primary,
                    action = {
                        TextButton(onClick = { templateSearch = ""; selectedCategory = TemplateLibraryFilter.ALL }) {
                            Text(if (ar) "عرض جميع القوالب" else "Show all templates")
                        }
                    },
                )
            }
        }
        items(visibleTemplates, key = { it.id }) { template ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = template.accent.copy(alpha = 0.09f)),
                onClick = { openTemplate(template) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, template.accent)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { favorites = store.toggleFavorite(template.id) }) {
                        Icon(
                            imageVector = if (template.id in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (template.id in favorites) {
                                if (ar) "إزالة من المفضلة" else "Remove from favorites"
                            } else {
                                if (ar) "إضافة إلى المفضلة" else "Add to favorites"
                            },
                            tint = template.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val ar = configuration.locales[0].language == "ar"
    val crashStore = remember(context) { CrashLogStore(context.applicationContext) }
    var crashRecord by remember { mutableStateOf(crashStore.last()) }
    var infoDialog by rememberSaveable { mutableStateOf<String?>(null) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }

    fun openProjectPage(path: String = "") {
        val base = "https://github.com/aljwaal1/Shortcut"
        val uri = Uri.parse(if (path.isBlank()) base else base + "/" + path)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { setAppLocale("ar") }) { Text("العربية") }
                        TextButton(onClick = { setAppLocale("en") }) { Text("English") }
                        TextButton(onClick = { setAppLocale("") }) { Text(stringResource(R.string.language_system)) }
                    }
                }
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentIcon(Icons.Default.Settings, MaterialTheme.colorScheme.tertiary)
                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val currentThemeMode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).apply()
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        }) {
                            Text(if (ar) "النظام" else "System")
                        }
                        TextButton(onClick = {
                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO).apply()
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        }) {
                            Text(if (ar) "فاتح" else "Light")
                        }
                        TextButton(onClick = {
                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES).apply()
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        }) {
                            Text(if (ar) "داكن" else "Dark")
                        }
                    }
                    Text(
                        when (currentThemeMode) {
                            AppCompatDelegate.MODE_NIGHT_NO -> if (ar) "المظهر الحالي: فاتح" else "Current appearance: Light"
                            AppCompatDelegate.MODE_NIGHT_YES -> if (ar) "المظهر الحالي: داكن" else "Current appearance: Dark"
                            else -> if (ar) "المظهر الحالي: حسب النظام" else "Current appearance: System"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        item {
            ElevatedCard(
                onClick = { context.startActivity(Intent(context, BackupTransferActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.tertiary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (ar) "تصدير أو استعادة المهام والأتمتة محليًا" else "Export or restore tasks and automations locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item {
            ElevatedCard(
                onClick = { openProjectPage("issues/new") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.report_problem), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (ar) "افتح صفحة المشروع واكتب المشكلة بالتفصيل" else "Open the project page and describe the issue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item {
            ElevatedCard(
                onClick = { openProjectPage("issues") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.feedback), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (ar) "شارك اقتراحك أو تابع الملاحظات الحالية" else "Share an idea or review existing feedback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        if (crashRecord != null) {
            item {
                ElevatedCard(
                    onClick = { infoDialog = "crash" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.07f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentIcon(Icons.Default.History, MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ar) "آخر توقف غير متوقع" else "Last unexpected crash",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                crashRecord?.displayTime().orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("→", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            ElevatedCard(
                onClick = { infoDialog = "privacy" },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.Settings, MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.privacy), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("→", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            ElevatedCard(
                onClick = { infoDialog = "about" },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentIcon(Icons.Default.AutoAwesome, MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (ar) "الإصدار $versionName" else "Version $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }

    infoDialog?.let { dialog ->
        val isPrivacy = dialog == "privacy"
        val isCrash = dialog == "crash"
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = {
                Text(
                    when {
                        isCrash -> if (ar) "تفاصيل آخر توقف" else "Last crash details"
                        isPrivacy -> stringResource(R.string.privacy)
                        else -> stringResource(R.string.about)
                    },
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(
                    when {
                        isCrash -> {
                            val record = crashRecord
                            if (record == null) {
                                if (ar) "لا توجد تفاصيل متاحة." else "No crash details available."
                            } else {
                                record.displayTime() + "\n" +
                                    (if (ar) "المسار: " else "Thread: ") + record.threadName + "\n\n" +
                                    record.summary + "\n\n" + record.stackTrace.take(5000)
                            }
                        }
                        isPrivacy -> {
                            if (ar) "يعمل Shortcut محليًا قدر الإمكان ولا يحتاج إلى حساب. لا يرسل بياناتك إلى خادم خاص بالتطبيق. بعض الميزات تتصل بخدمات خارجية فقط عندما تطلب ذلك، مثل Telegram Bot API أو فتح روابط خارجية. يمكنك مراجعة الصلاحيات من صفحة الصلاحيات."
                            else "Shortcut works locally whenever possible and does not require an account. It does not send your data to an app-owned server. Some features contact external services only when you request them, such as the Telegram Bot API or opening external links. You can review permissions from the Permissions screen."
                        }
                        else -> {
                            if (ar) "Shortcut $versionName — تطبيق أندرويد محلي للأتمتة والأدوات اليومية. صُمم ليجمع الجدولة والأدوات والاختصارات في واجهة واحدة سريعة."
                            else "Shortcut $versionName — a local-first Android automation and utility app designed to combine scheduling, tools and shortcuts in one fast interface."
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text(if (ar) "حسنًا" else "OK")
                }
            },
            dismissButton = if (isCrash) {
                {
                    TextButton(onClick = {
                        crashStore.clear()
                        crashRecord = null
                        infoDialog = null
                    }) {
                        Text(if (ar) "مسح السجل" else "Clear log")
                    }
                }
            } else null,
        )
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

private fun repeatText(repeat: RepeatOption, ar: Boolean): String = when (repeat) {
    RepeatOption.ONCE -> if (ar) "مرة واحدة" else "Once"
    RepeatOption.DAILY -> if (ar) "يوميًا" else "Daily"
    RepeatOption.WEEKDAYS -> if (ar) "أيام العمل" else "Weekdays"
    RepeatOption.WEEKLY -> if (ar) "أسبوعيًا" else "Weekly"
}

private fun setAppLocale(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}