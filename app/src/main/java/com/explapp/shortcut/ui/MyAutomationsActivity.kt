package com.explapp.shortcut.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineAction
import com.explapp.shortcut.automation.routines.RoutineActionType
import com.explapp.shortcut.automation.routines.RoutineDispatcher
import com.explapp.shortcut.automation.routines.RoutinePermissionPolicy
import com.explapp.shortcut.automation.routines.RoutineScheduler
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.automation.routines.RoutineTemplateCatalog
import com.explapp.shortcut.automation.routines.RoutineTrigger
import com.explapp.shortcut.automation.routines.RoutineTriggerType
import com.explapp.shortcut.backup.BackupTransferActivity
import com.explapp.shortcut.data.InstalledAppRepository
import com.explapp.shortcut.tools.NfcSetupActivity

class MyAutomationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShortcutTheme {
                MyAutomationsScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun MyAutomationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val store = remember { RoutineStore(context.applicationContext) }
    val scheduler = remember { RoutineScheduler(context.applicationContext) }
    val itemsState = remember { mutableStateListOf<AutomationRoutine>().apply { addAll(store.load()) } }
    var editing by remember { mutableStateOf<AutomationRoutine?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var lastDeleted by remember { mutableStateOf<AutomationRoutine?>(null) }

    fun persist(items: List<AutomationRoutine>) {
        itemsState.clear(); itemsState.addAll(items); store.save(items)
    }
    fun upsert(item: AutomationRoutine) {
        val next = itemsState.toMutableList()
        val index = next.indexOfFirst { it.id == item.id }
        if (index >= 0) next[index] = item else next += item
        scheduler.cancel(item.id)
        if (item.isEnabled) scheduler.schedule(item)
        persist(next)
    }

    when {
        creating || editing != null -> RoutineBuilderScreen(
            initial = editing,
            onCancel = { creating = false; editing = null },
            onSave = { routine -> upsert(routine); creating = false; editing = null },
        )
        showTemplates -> RoutineTemplatesScreen(
            onBack = { showTemplates = false },
            onUse = { template -> editing = template.duplicate(); showTemplates = false },
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) "الأتمتة الخاصة بي" else "My Automations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(if (ar) "نشطة ${itemsState.count { it.isEnabled }} • متوقفة ${itemsState.count { !it.isEnabled }}" else "${itemsState.count { it.isEnabled }} active • ${itemsState.count { !it.isEnabled }} paused")
                    }
                    TextButton(onClick = onBack) { Text(if (ar) "رجوع" else "Back") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) { Text(if (ar) "أتمتة جديدة" else "New automation") }
                    TextButton(onClick = { showTemplates = true }, modifier = Modifier.weight(1f)) { Text(if (ar) "القوالب" else "Templates") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { context.startActivity(Intent(context, ScheduledTasksManagerActivity::class.java)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (ar) "المهام المجدولة" else "Scheduled tasks") }
                    TextButton(
                        onClick = { context.startActivity(Intent(context, BackupTransferActivity::class.java)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (ar) "نسخ احتياطي" else "Backup") }
                }
            }
            lastDeleted?.let { deleted ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ar) "تم الحذف" else "Deleted", modifier = Modifier.weight(1f))
                            TextButton(onClick = { upsert(deleted); lastDeleted = null }) { Text(if (ar) "تراجع" else "Undo") }
                        }
                    }
                }
            }
            if (itemsState.isEmpty()) {
                item { Text(if (ar) "لا توجد أتمتة بعد. ابدأ من قالب أو أنشئ واحدة." else "No automations yet. Start with a template or create one.") }
            }
            items(itemsState, key = { it.id }) { routine ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${routine.trigger.type.name} ${routine.trigger.value}".trim(), style = MaterialTheme.typography.bodySmall)
                                Text(if (ar) "${routine.actions.size} إجراءات" else "${routine.actions.size} actions", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = routine.isEnabled, onCheckedChange = { enabled -> upsert(routine.copy(isEnabled = enabled)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { RoutineDispatcher(context).execute(routine, userInitiated = true) }) { Text(if (ar) "تشغيل" else "Run") }
                            TextButton(onClick = { editing = routine }) { Text(if (ar) "تعديل" else "Edit") }
                            TextButton(onClick = { upsert(routine.duplicate()) }) { Text(if (ar) "نسخ" else "Copy") }
                            TextButton(onClick = {
                                scheduler.cancel(routine.id)
                                lastDeleted = routine
                                persist(itemsState.filterNot { it.id == routine.id })
                            }) { Text(if (ar) "حذف" else "Delete") }
                        }
                        if (routine.trigger.type == RoutineTriggerType.NFC) {
                            TextButton(onClick = {
                                context.startActivity(Intent(context, NfcSetupActivity::class.java).putExtra(NfcSetupActivity.EXTRA_ROUTINE_ID, routine.id))
                            }) { Text(if (ar) "كتابة وسم NFC لهذا الروتين" else "Write NFC tag for this routine") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineBuilderScreen(
    initial: AutomationRoutine?,
    onCancel: () -> Unit,
    onSave: (AutomationRoutine) -> Unit,
) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    val installedApps = remember(context) { InstalledAppRepository(context).loadLaunchableApps() }
    val stableId = remember(initial?.id) { initial?.id ?: java.util.UUID.randomUUID().toString() }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var triggerType by remember(initial?.id) { mutableStateOf(initial?.trigger?.type ?: RoutineTriggerType.MANUAL) }
    var triggerValue by remember(initial?.id) { mutableStateOf(initial?.trigger?.value.orEmpty()) }
    val actions = remember(initial?.id) { mutableStateListOf<RoutineAction>().apply { addAll(initial?.actions.orEmpty()) } }
    var actionType by remember { mutableStateOf(RoutineActionType.OPEN_APP) }
    var value by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var pendingRoutine by remember { mutableStateOf<AutomationRoutine?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    fun finishPendingSave() { pendingRoutine?.let(onSave); pendingRoutine = null; permissionError = false }
    val exactAlarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishPendingSave() }
    fun requestExactAlarmOrSave() {
        val routine = pendingRoutine ?: return
        val exactNeeded = RoutinePermissionPolicy.needsExactAlarm(routine.trigger.type)
        val exactGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactNeeded && !exactGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        } else {
            finishPendingSave()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestExactAlarmOrSave()
        } else {
            pendingRoutine = null
            permissionError = true
        }
    }

    fun saveWithNeededPermissions(routine: AutomationRoutine) {
        pendingRoutine = routine
        permissionError = false
        val notificationsNeeded = RoutinePermissionPolicy.needsNotifications(routine.trigger.type, routine.actions)
        val notificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationsNeeded && !notificationsGranted && Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestExactAlarmOrSave()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (ar) "منشئ الأتمتة" else "Automation Builder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (ar) "الاسم" else "Name") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Text(if (ar) "المحفز" else "Trigger", fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoutineTriggerType.entries.forEach { type ->
                    FilterChip(selected = triggerType == type, onClick = { triggerType = type }, label = { Text(type.name) })
                }
            }
        }
        if (triggerType == RoutineTriggerType.TIME || triggerType == RoutineTriggerType.BATTERY_BELOW || triggerType == RoutineTriggerType.NFC) {
            item { OutlinedTextField(value = triggerValue, onValueChange = { triggerValue = it }, label = { Text(if (ar) "قيمة المحفز" else "Trigger value") }, modifier = Modifier.fillMaxWidth()) }
        }
        item {
            Text(if (ar) "أضف إجراء" else "Add action", fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoutineActionType.entries.forEach { type ->
                    FilterChip(selected = actionType == type, onClick = { actionType = type }, label = { Text(type.name) })
                }
            }
        }
        if (actionType == RoutineActionType.OPEN_APP || actionType == RoutineActionType.OPEN_APP_SCREENSHOT) {
            item {
                val selectedLabel = installedApps.firstOrNull { it.packageName == value }?.label
                Button(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedLabel ?: if (ar) "اختر التطبيق" else "Choose app")
                }
            }
        } else {
            item { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(if (ar) "القيمة / الرابط / المستلم" else "Value / URL / recipient") }, modifier = Modifier.fillMaxWidth()) }
        }
        if (actionType == RoutineActionType.PREPARE_WHATSAPP || actionType == RoutineActionType.PREPARE_TELEGRAM) {
            item { OutlinedTextField(value = secondary, onValueChange = { secondary = it }, label = { Text(if (ar) "نص الرسالة" else "Message text") }, modifier = Modifier.fillMaxWidth()) }
        }
        if (actionType == RoutineActionType.OPEN_APP_SCREENSHOT) {
            item {
                Text(if (ar) "الانتظار قبل التقاط الصورة" else "Wait before screenshot", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1000L, 2000L, 3000L, 5000L).forEach { delay ->
                        FilterChip(
                            selected = (secondary.toLongOrNull() ?: 2000L) == delay,
                            onClick = { secondary = delay.toString() },
                            label = { Text("${delay / 1000} ${if (ar) "ث" else "s"}") },
                        )
                    }
                }
                Text(
                    if (ar) "سيطلب Android موافقة التقاط الشاشة، ثم يفتح التطبيق ويلتقط الصورة بعد المدة المحددة."
                    else "Android asks for screen-capture consent, then opens the app and captures it after the selected delay.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Button(onClick = {
                actions += RoutineAction(actionType, value, secondary)
                value = ""; secondary = ""
            }, enabled = value.isNotBlank()) { Text(if (ar) "إضافة الإجراء" else "Add action") }
        }
        items(actions) { action ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val appLabel = installedApps.firstOrNull { it.packageName == action.value }?.label
                    Text("${action.type.name}: ${appLabel ?: action.value}", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        actionType = action.type
                        value = action.value
                        secondary = action.secondaryValue
                        actions.remove(action)
                    }) { Text(if (ar) "تعديل" else "Edit") }
                    TextButton(onClick = { actions.remove(action) }) { Text(if (ar) "إزالة" else "Remove") }
                }
            }
        }
        item {
            val routine = AutomationRoutine(
                id = stableId,
                name = name.ifBlank { if (ar) "أتمتة جديدة" else "New automation" },
                isEnabled = initial?.isEnabled ?: true,
                trigger = RoutineTrigger(triggerType, triggerValue),
                actions = actions.toList(),
            )
            if (permissionError) {
                Text(
                    if (ar) "يلزم السماح بالإشعارات لهذا النوع من الأتمتة حتى يتمكن Shortcut من تنبيهك بدل محاولة فتح تطبيق من الخلفية." else "Notification permission is required for this automation so Shortcut can notify you instead of attempting to force an app open from the background.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(if (ar) "إلغاء" else "Cancel") }
                Button(onClick = { saveWithNeededPermissions(routine) }, enabled = routine.isValid(), modifier = Modifier.weight(1f)) { Text(if (ar) "حفظ" else "Save") }
            }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(if (ar) "اختر التطبيق" else "Choose app") },
            text = {
                LazyColumn {
                    items(installedApps, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = {
                                value = app.packageName
                                if (actionType == RoutineActionType.OPEN_APP_SCREENSHOT && secondary.isBlank()) secondary = "2000"
                                showAppPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(app.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text(if (ar) "إلغاء" else "Cancel") }
            },
        )
    }
}

@Composable
private fun RoutineTemplatesScreen(onBack: () -> Unit, onUse: (AutomationRoutine) -> Unit) {
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val templates = remember { RoutineTemplateCatalog.templates() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (ar) "قوالب الأتمتة" else "Automation templates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onBack) { Text(if (ar) "رجوع" else "Back") }
            }
        }
        items(templates) { template ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontWeight = FontWeight.Bold)
                        Text("${template.trigger.type.name} • ${template.actions.firstOrNull()?.type?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onUse(template) }) { Text(if (ar) "استخدام" else "Use") }
                }
            }
        }
    }
}
