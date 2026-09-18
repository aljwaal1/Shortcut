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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.abs
import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineAction
import com.explapp.shortcut.automation.routines.RoutineActionType
import com.explapp.shortcut.automation.routines.RoutineCatalog
import com.explapp.shortcut.automation.routines.RoutineCondition
import com.explapp.shortcut.automation.routines.RoutineConditionType
import com.explapp.shortcut.automation.routines.RoutineDispatcher
import com.explapp.shortcut.automation.routines.RoutinePermissionPolicy
import com.explapp.shortcut.automation.routines.RoutineScheduler
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.automation.routines.RoutineTemplateCatalog
import com.explapp.shortcut.automation.routines.RoutineTrigger
import com.explapp.shortcut.automation.routines.RoutineTriggerType
import com.explapp.shortcut.data.InstalledAppRepository
import com.explapp.shortcut.tools.NfcSetupActivity

class MyAutomationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShortcutTheme { MyAutomationsScreen(onBack = ::finish) } }
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
        itemsState.clear()
        itemsState.addAll(items)
        store.save(items)
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
                    Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) { Text(if (ar) "اختصار جديد" else "New shortcut") }
                    TextButton(onClick = { showTemplates = true }, modifier = Modifier.weight(1f)) { Text(if (ar) "القوالب" else "Templates") }
                }
            }
            item {
                TextButton(
                    onClick = { context.startActivity(Intent(context, ScheduledTasksManagerActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ar) "المهام المجدولة" else "Scheduled tasks") }
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
                item { Text(if (ar) "لا توجد اختصارات بعد. أنشئ واحدًا أو ابدأ من قالب." else "No shortcuts yet. Create one or start from a template.") }
            }
            items(itemsState, key = { it.id }) { routine ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(triggerTitle(routine.trigger.type, ar) + " " + routine.trigger.value, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (ar) "${routine.conditions.size} شروط • ${routine.actions.size} خطوات"
                                    else "${routine.conditions.size} conditions • ${routine.actions.size} steps",
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
                            }) { Text(if (ar) "كتابة وسم NFC" else "Write NFC tag") }
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
    val conditions = remember(initial?.id) { mutableStateListOf<RoutineCondition>().apply { addAll(initial?.conditions.orEmpty()) } }
    val actions = remember(initial?.id) { mutableStateListOf<RoutineAction>().apply { addAll(initial?.actions.orEmpty()) } }

    var conditionType by remember { mutableStateOf(RoutineConditionType.BATTERY_ABOVE) }
    var conditionValue by remember { mutableStateOf("") }
    var conditionSecondary by remember { mutableStateOf("") }

    var actionType by remember { mutableStateOf(RoutineActionType.OPEN_APP) }
    var value by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var params by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var continueOnError by remember { mutableStateOf(false) }
    var actionSearch by remember { mutableStateOf("") }

    var pendingRoutine by remember { mutableStateOf<AutomationRoutine?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    fun resetActionEditor() {
        value = ""
        secondary = ""
        params = emptyMap()
        continueOnError = false
    }

    fun finishPendingSave() {
        pendingRoutine?.let(onSave)
        pendingRoutine = null
        permissionError = false
    }

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
        if (granted) requestExactAlarmOrSave() else {
            pendingRoutine = null
            permissionError = true
        }
    }

    fun saveWithNeededPermissions(routine: AutomationRoutine) {
        pendingRoutine = routine
        permissionError = false
        val notificationsNeeded = RoutinePermissionPolicy.needsNotifications(routine.trigger.type, routine.actions)
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationsNeeded && !notificationsGranted && Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestExactAlarmOrSave()
        }
    }

    val visibleActionMeta = remember(actionSearch) {
        RoutineCatalog.actions.filter {
            actionSearch.isBlank() ||
                it.titleEn.contains(actionSearch, true) ||
                it.titleAr.contains(actionSearch, true) ||
                it.categoryEn.contains(actionSearch, true) ||
                it.categoryAr.contains(actionSearch, true)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(if (ar) "منشئ الاختصار" else "Shortcut Builder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                if (ar) "عندما يحدث شيء → تحقق من الشروط → نفّذ الخطوات بالترتيب."
                else "When something happens → check conditions → run steps in order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (ar) "اسم الاختصار" else "Shortcut name") },
                placeholder = { Text(if (ar) "مثال: تقرير الصباح" else "Example: Morning report") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SectionTitle(if (ar) "1. عندما" else "1. When")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RoutineTriggerType.entries) { type ->
                    FilterChip(
                        selected = triggerType == type,
                        onClick = {
                            triggerType = type
                            if (type !in setOf(RoutineTriggerType.TIME, RoutineTriggerType.BATTERY_BELOW, RoutineTriggerType.NFC)) triggerValue = ""
                        },
                        label = { Text(triggerTitle(type, ar)) },
                    )
                }
            }
            ClearHint(RoutineCatalog.triggerHint(triggerType, ar), ar)
        }

        if (triggerType == RoutineTriggerType.TIME || triggerType == RoutineTriggerType.BATTERY_BELOW || triggerType == RoutineTriggerType.NFC) {
            item {
                OutlinedTextField(
                    value = triggerValue,
                    onValueChange = { triggerValue = it },
                    label = { Text(triggerValueLabel(triggerType, ar)) },
                    placeholder = { Text(triggerPlaceholder(triggerType, ar)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionTitle(if (ar) "2. شروط اختيارية" else "2. Optional conditions")
            ClearHint(
                if (ar) "الشروط اختيارية. إذا أضفت أكثر من شرط فيجب أن تتحقق جميعها قبل تنفيذ الخطوات. يمكنك سحب الشروط لاحقًا لتغيير ترتيب عرضها."
                else "Conditions are optional. If you add more than one, all must match before the steps run. You can drag conditions later to reorder them.",
                ar,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RoutineConditionType.entries) { type ->
                    FilterChip(
                        selected = conditionType == type,
                        onClick = { conditionType = type },
                        label = { Text(conditionTitle(type, ar)) },
                    )
                }
            }
            if (conditionType == RoutineConditionType.VARIABLE_EQUALS || conditionType == RoutineConditionType.VARIABLE_CONTAINS) {
                Text(if (ar) "اختر القيمة التي تريد فحصها" else "Choose the value to check", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        listOf(
                            "currentDate" to if (ar) "التاريخ الحالي" else "Current date",
                            "currentTime" to if (ar) "الوقت الحالي" else "Current time",
                            "batteryPercent" to if (ar) "نسبة البطارية" else "Battery percentage",
                            "dayOfWeek" to if (ar) "يوم الأسبوع" else "Day of week",
                        ),
                    ) { (storedValue, label) ->
                        FilterChip(
                            selected = conditionValue == storedValue,
                            onClick = { conditionValue = storedValue },
                            label = { Text(label) },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = conditionValue,
                    onValueChange = { conditionValue = it },
                    label = { Text(conditionValueLabel(conditionType, ar)) },
                    placeholder = { Text(conditionValueHint(conditionType, ar)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ClearHint(conditionHint(conditionType, ar), ar)
            if (conditionType == RoutineConditionType.VARIABLE_EQUALS || conditionType == RoutineConditionType.VARIABLE_CONTAINS) {
                OutlinedTextField(
                    value = conditionSecondary,
                    onValueChange = { conditionSecondary = it },
                    label = { Text(if (ar) "القيمة المتوقعة" else "Expected value") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                onClick = {
                    conditions += RoutineCondition(conditionType, conditionValue, conditionSecondary)
                    conditionValue = ""
                    conditionSecondary = ""
                },
                enabled = RoutineCondition(conditionType, conditionValue, conditionSecondary).isValid(),
            ) { Text(if (ar) "+ إضافة شرط" else "+ Add condition") }
        }

        items(conditions, key = { it.hashCode() }) { condition ->
            val index = conditions.indexOf(condition)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ReorderHandle(
                            ar = ar,
                            index = index,
                            size = conditions.size,
                            onMove = { from, to ->
                                val moved = conditions.removeAt(from)
                                conditions.add(to, moved)
                            },
                        )
                        Text(conditionDisplay(condition, ar), modifier = Modifier.weight(1f))
                        TextButton(onClick = { conditions.remove(condition) }) { Text(if (ar) "حذف" else "Remove") }
                    }
                }
            }
        }

        item {
            SectionTitle(if (ar) "3. نفّذ" else "3. Do")
            ClearHint(
                if (ar) "أضف ما تحتاجه من خطوات. ينفذ التطبيق الخطوات من الأعلى إلى الأسفل. اضغط مطولًا على مقبض السحب بجانب أي خطوة واسحبها لتغيير مكانها."
                else "Add as many steps as you need. Steps run from top to bottom. Long-press the drag handle beside a step and drag it to reorder.",
                ar,
            )
            if (actions.isNotEmpty()) {
                Text(if (ar) "اقتراحات مناسبة بعد الخطوة السابقة" else "Suggested after the previous step", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RoutineCatalog.suggestions(actions.lastOrNull()?.type)) { type ->
                        val meta = RoutineCatalog.meta(type)
                        FilterChip(
                            selected = actionType == type,
                            onClick = { actionType = type; resetActionEditor() },
                            label = { Text(if (ar) meta.titleAr else meta.titleEn) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = actionSearch,
                onValueChange = { actionSearch = it },
                label = { Text(if (ar) "ابحث عن إجراء" else "Search actions") },
                placeholder = { Text(if (ar) "تطبيق، صورة، رسالة، انتظار، سكربت..." else "App, image, message, wait, script...") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            val grouped = visibleActionMeta.groupBy { if (ar) it.categoryAr else it.categoryEn }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (category, metas) ->
                    Text(category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(metas) { meta ->
                            FilterChip(
                                selected = actionType == meta.type,
                                onClick = { actionType = meta.type; resetActionEditor() },
                                label = { Text(if (ar) meta.titleAr else meta.titleEn) },
                            )
                        }
                    }
                }
            }
        }

        item {
            val meta = RoutineCatalog.meta(actionType)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ar) meta.titleAr else meta.titleEn, fontWeight = FontWeight.Bold)
                    ClearHint(if (ar) meta.hintAr else meta.hintEn, ar)

                    when (actionType) {
                        RoutineActionType.OPEN_APP,
                        RoutineActionType.OPEN_APP_SCREENSHOT,
                        -> {
                            val selectedLabel = installedApps.firstOrNull { it.packageName == value }?.label
                            Button(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedLabel ?: if (ar) "اختر التطبيق" else "Choose app")
                            }
                            if (actionType == RoutineActionType.OPEN_APP_SCREENSHOT) {
                                DelayChips(ar, secondary.ifBlank { "3000" }) { secondary = it }
                            }
                        }

                        RoutineActionType.TAKE_SCREENSHOT -> DelayChips(ar, value.ifBlank { "3000" }) { value = it }

                        RoutineActionType.WAIT -> {
                            Text(if (ar) "مدة الانتظار" else "Wait duration", fontWeight = FontWeight.Bold)
                            DelayChips(ar, value.ifBlank { "2000" }) { value = it }
                        }

                        RoutineActionType.PREPARE_WHATSAPP,
                        RoutineActionType.PREPARE_TELEGRAM,
                        -> {
                            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(if (ar) "المستلم" else "Recipient") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = secondary, onValueChange = { secondary = it }, label = { Text(if (ar) "نص الرسالة" else "Message") }, modifier = Modifier.fillMaxWidth())
                        }

                        RoutineActionType.SEND_TELEGRAM_BOT -> {
                            OutlinedTextField(
                                value = params["botToken"].orEmpty(),
                                onValueChange = { params = params + ("botToken" to it) },
                                label = { Text(if (ar) "رمز البوت" else "Bot token") },
                                visualTransformation = PasswordVisualTransformation(),
                                supportingText = { Text(if (ar) "أدخل الرمز الذي حصلت عليه من بوت فاذر. يُحفظ محليًا ولا يدخل في النسخ الاحتياطي أو التصدير." else "Enter the token you received from BotFather. It stays local and is excluded from backups and exports.") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = params["chatId"].orEmpty(),
                                onValueChange = { params = params + ("chatId" to it) },
                                label = { Text(if (ar) "معرّف المحادثة" else "Chat ID") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = secondary,
                                onValueChange = { secondary = it },
                                label = { Text(if (ar) "نص الرسالة أو تعليق الصورة" else "Message or image caption") },
                                placeholder = { Text(if (ar) "مثال: تقرير اليوم" else "Example: Today's report") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (actions.lastOrNull()?.type == RoutineActionType.OPEN_APP_SCREENSHOT) {
                                ClearHint(
                                    if (ar) "تم اكتشاف لقطة شاشة قبل هذه الخطوة. سيستخدم التطبيق الصورة الناتجة تلقائيًا كمرفق، ولا تحتاج إلى اختيار ملف يدويًا."
                                    else "A screenshot was detected before this step. Shortcut will automatically use that image as the attachment.",
                                    ar,
                                )
                            } else {
                                OutlinedTextField(
                                    value = params["attachment"].orEmpty(),
                                    onValueChange = { params = params + ("attachment" to it) },
                                    label = { Text(if (ar) "ملف اختياري" else "Optional file") },
                                    placeholder = { Text(if (ar) "اتركه فارغًا لإرسال النص فقط" else "Leave empty to send text only") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        RoutineActionType.CUSTOM_SCRIPT -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "السكربت المخصص" else "Custom script") },
                                placeholder = { Text("return input.toUpperCase();") },
                                supportingText = {
                                    Text(
                                        if (ar) "استخدمه للحسابات ومعالجة النصوص والبيانات والمنطق المخصص. يعمل داخل بيئة محدودة ولا يملك وصولًا مباشرًا إلى نظام الهاتف."
                                        else "Use it for calculations, text/data processing, and custom logic. It runs in a restricted environment with no direct phone-system access.",
                                    )
                                },
                                minLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = secondary,
                                onValueChange = { secondary = it },
                                label = { Text(if (ar) "القيمة الداخلة الاختيارية" else "Optional input") },
                                placeholder = { Text(if (ar) "يمكن تركها فارغة" else "You can leave this empty") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineActionType.SET_VARIABLE -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "اسم المتغير" else "Variable name") },
                                placeholder = { Text(if (ar) "مثال: النتيجة" else "Example: result") },
                                supportingText = { Text(if (ar) "اختر اسمًا قصيرًا لتستخدم القيمة لاحقًا داخل نفس الاختصار." else "Choose a short name so later steps can reuse this value.") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = secondary,
                                onValueChange = { secondary = it },
                                label = { Text(if (ar) "القيمة التي تريد حفظها" else "Value to save") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineActionType.READ_CLIPBOARD -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "اسم المتغير الذي سيحفظ النص" else "Variable name for clipboard text") },
                                placeholder = { Text(if (ar) "مثال: النص" else "Example: text") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineActionType.COPY_TO_CLIPBOARD -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "النص المراد نسخه" else "Text to copy") },
                                placeholder = { Text(if (ar) "اكتب النص أو استخدم قيمة من خطوة سابقة" else "Enter text or use a value from a previous step") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineActionType.OPEN_TOOL -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(selected = value == "app_usage", onClick = { value = "app_usage" }, label = { Text(if (ar) "وقت استخدام التطبيقات" else "App usage") })
                                }
                            }
                        }

                        else -> OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text(if (ar) "القيمة" else "Value") },
                            placeholder = { Text(actionValueHint(actionType, ar)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ar) "الاستمرار عند فشل الخطوة" else "Continue if this step fails", modifier = Modifier.weight(1f))
                        Switch(checked = continueOnError, onCheckedChange = { continueOnError = it })
                    }
                    ClearHint(
                        if (ar) "إذا كان هذا الخيار مفعّلًا وفشلت الخطوة، فلن يتوقف الاختصار بل سينتقل إلى الخطوة التالية."
                        else "When enabled, a failure in this step will not stop the shortcut; the next step will still run.",
                        ar,
                    )

                    val draft = RoutineAction(actionType, value, secondary, continueOnError, params)
                    Button(
                        onClick = {
                            actions += draft
                            resetActionEditor()
                        },
                        enabled = draft.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (ar) "إضافة الخطوة" else "Add step") }
                }
            }
        }

        items(actions, key = { action -> action.hashCode() }) { action ->
            val index = actions.indexOf(action)
            val meta = RoutineCatalog.meta(action.type)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${index + 1}. " + if (ar) meta.titleAr else meta.titleEn, fontWeight = FontWeight.Bold)
                    val appLabel = installedApps.firstOrNull { it.packageName == action.value }?.label
                    val summary = actionSummary(action, appLabel, ar)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReorderHandle(
                            ar = ar,
                            index = index,
                            size = actions.size,
                            onMove = { from, to ->
                                val moved = actions.removeAt(from)
                                actions.add(to, moved)
                            },
                        )
                        TextButton(onClick = {
                            actionType = action.type
                            value = action.value
                            secondary = action.secondaryValue
                            params = action.parameters
                            continueOnError = action.continueOnError
                            actions.remove(action)
                        }) { Text(if (ar) "تعديل" else "Edit") }
                        TextButton(onClick = { actions.remove(action) }) { Text(if (ar) "حذف" else "Remove") }
                    }
                }
            }
        }

        item {
            val routine = AutomationRoutine(
                id = stableId,
                name = name.ifBlank { if (ar) "اختصار جديد" else "New shortcut" },
                isEnabled = initial?.isEnabled ?: true,
                trigger = RoutineTrigger(triggerType, triggerValue),
                conditions = conditions.toList(),
                actions = actions.toList(),
            )

            if (permissionError) {
                Text(
                    if (ar) "يلزم السماح بالإشعارات لهذا النوع من الأتمتة."
                    else "Notification permission is required for this type of automation.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(if (ar) "إلغاء" else "Cancel") }
                Button(
                    onClick = { saveWithNeededPermissions(routine) },
                    enabled = routine.isValid(),
                    modifier = Modifier.weight(1f),
                ) { Text(if (ar) "حفظ" else "Save") }
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
                                if (actionType == RoutineActionType.OPEN_APP_SCREENSHOT && secondary.isBlank()) secondary = "3000"
                                showAppPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(app.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text(if (ar) "إلغاء" else "Cancel") } },
        )
    }
}

@Composable
private fun DelayChips(ar: Boolean, selected: String, onSelect: (String) -> Unit) {
    Text(if (ar) "المدة" else "Delay", fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf(1000L, 2000L, 3000L, 5000L, 10_000L)) { delay ->
            FilterChip(
                selected = (selected.toLongOrNull() ?: 3000L) == delay,
                onClick = { onSelect(delay.toString()) },
                label = { Text("${delay / 1000} ${if (ar) "ث" else "s"}") },
            )
        }
    }
}

@Composable
private fun ClearHint(text: String, ar: Boolean) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Text(
            text = "💡 " + text,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReorderHandle(
    ar: Boolean,
    index: Int,
    size: Int,
    onMove: (Int, Int) -> Unit,
) {
    var dragged by remember(index, size) { mutableStateOf(0f) }
    Text(
        text = if (ar) "⋮⋮ سحب" else "⋮⋮ Drag",
        modifier = Modifier
            .padding(end = 8.dp)
            .pointerInput(index, size) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragged += amount.y
                        val threshold = 48.dp.toPx()
                        if (abs(dragged) >= threshold) {
                            val target = if (dragged > 0) index + 1 else index - 1
                            if (target in 0 until size) onMove(index, target)
                            dragged = 0f
                        }
                    },
                )
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

private fun conditionHint(type: RoutineConditionType, ar: Boolean): String = when (type) {
    RoutineConditionType.BATTERY_ABOVE -> if (ar) "اكتب النسبة فقط، مثل 30. لن تنفذ الخطوات إلا إذا كانت البطارية أعلى منها." else "Enter a percentage such as 30. Steps run only when battery is above it."
    RoutineConditionType.BATTERY_BELOW -> if (ar) "اكتب النسبة فقط، مثل 20. لن تنفذ الخطوات إلا إذا كانت البطارية أقل منها." else "Enter a percentage such as 20. Steps run only when battery is below it."
    RoutineConditionType.DAY_OF_WEEK -> if (ar) "استخدم رقم اليوم: 1 الأحد، 2 الاثنين، 3 الثلاثاء، 4 الأربعاء، 5 الخميس، 6 الجمعة، 7 السبت." else "Use the day number: 1 Sunday, 2 Monday, 3 Tuesday, 4 Wednesday, 5 Thursday, 6 Friday, 7 Saturday."
    RoutineConditionType.VARIABLE_EQUALS -> if (ar) "اختر قيمة من الخيارات ثم اكتب القيمة التي يجب أن تساويها بالضبط." else "Choose a built-in value, then enter the exact value it must equal."
    RoutineConditionType.VARIABLE_CONTAINS -> if (ar) "اختر قيمة من الخيارات ثم اكتب النص الذي يجب أن يكون موجودًا داخلها." else "Choose a built-in value, then enter text that must appear inside it."
}

private fun conditionDisplay(condition: RoutineCondition, ar: Boolean): String {
    val valueLabel = when (condition.value) {
        "currentDate" -> if (ar) "التاريخ الحالي" else "Current date"
        "currentTime" -> if (ar) "الوقت الحالي" else "Current time"
        "batteryPercent" -> if (ar) "نسبة البطارية" else "Battery percentage"
        "dayOfWeek" -> if (ar) "يوم الأسبوع" else "Day of week"
        else -> condition.value
    }
    return conditionTitle(condition.type, ar) + ": " + valueLabel +
        if (condition.secondaryValue.isNotBlank()) " → " + condition.secondaryValue else ""
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
}

private fun triggerTitle(type: RoutineTriggerType, ar: Boolean): String = when (type) {
    RoutineTriggerType.MANUAL -> if (ar) "يدوي" else "Manual"
    RoutineTriggerType.TIME -> if (ar) "وقت يومي" else "Daily time"
    RoutineTriggerType.CHARGER_CONNECTED -> if (ar) "توصيل الشاحن" else "Charger connected"
    RoutineTriggerType.CHARGER_DISCONNECTED -> if (ar) "فصل الشاحن" else "Charger disconnected"
    RoutineTriggerType.BATTERY_BELOW -> if (ar) "البطارية أقل من" else "Battery below"
    RoutineTriggerType.NFC -> "NFC"
    RoutineTriggerType.BOOT -> if (ar) "تشغيل الجهاز" else "Device boot"
}

private fun triggerValueLabel(type: RoutineTriggerType, ar: Boolean): String = when (type) {
    RoutineTriggerType.TIME -> if (ar) "الوقت" else "Time"
    RoutineTriggerType.BATTERY_BELOW -> if (ar) "نسبة البطارية" else "Battery percent"
    RoutineTriggerType.NFC -> if (ar) "اسم اختياري للوسم" else "Optional tag name"
    else -> if (ar) "القيمة" else "Value"
}

private fun triggerPlaceholder(type: RoutineTriggerType, ar: Boolean): String = when (type) {
    RoutineTriggerType.TIME -> "08:00"
    RoutineTriggerType.BATTERY_BELOW -> "20"
    RoutineTriggerType.NFC -> if (ar) "مثال: السيارة" else "Example: Car"
    else -> ""
}

private fun conditionTitle(type: RoutineConditionType, ar: Boolean): String = when (type) {
    RoutineConditionType.BATTERY_ABOVE -> if (ar) "البطارية أعلى من" else "Battery above"
    RoutineConditionType.BATTERY_BELOW -> if (ar) "البطارية أقل من" else "Battery below"
    RoutineConditionType.DAY_OF_WEEK -> if (ar) "يوم الأسبوع" else "Day of week"
    RoutineConditionType.VARIABLE_EQUALS -> if (ar) "متغير يساوي" else "Variable equals"
    RoutineConditionType.VARIABLE_CONTAINS -> if (ar) "متغير يحتوي" else "Variable contains"
}

private fun conditionValueLabel(type: RoutineConditionType, ar: Boolean): String = when (type) {
    RoutineConditionType.BATTERY_ABOVE,
    RoutineConditionType.BATTERY_BELOW,
    -> if (ar) "النسبة" else "Percent"
    RoutineConditionType.DAY_OF_WEEK -> if (ar) "اليوم 1-7" else "Day 1-7"
    RoutineConditionType.VARIABLE_EQUALS,
    RoutineConditionType.VARIABLE_CONTAINS,
    -> if (ar) "اسم المتغير" else "Variable name"
}

private fun conditionValueHint(type: RoutineConditionType, ar: Boolean): String = when (type) {
    RoutineConditionType.BATTERY_ABOVE,
    RoutineConditionType.BATTERY_BELOW,
    -> "30"
    RoutineConditionType.DAY_OF_WEEK -> if (ar) "1=الأحد ... 7=السبت" else "1=Sunday ... 7=Saturday"
    else -> if (ar) "currentDate / currentTime / batteryPercent / dayOfWeek" else "currentDate / currentTime / batteryPercent / dayOfWeek"
}

private fun actionValueHint(type: RoutineActionType, ar: Boolean): String = when (type) {
    RoutineActionType.OPEN_URL -> "https://example.com"
    RoutineActionType.OPEN_MAPS -> if (ar) "اسم المكان أو العنوان" else "Place or address"
    RoutineActionType.SHOW_NOTIFICATION -> if (ar) "نص الإشعار" else "Notification text"
    else -> if (ar) "أدخل القيمة" else "Enter value"
}

private fun actionSummary(action: RoutineAction, appLabel: String?, ar: Boolean): String = when (action.type) {
    RoutineActionType.OPEN_APP,
    RoutineActionType.OPEN_APP_SCREENSHOT,
    -> appLabel ?: action.value
    RoutineActionType.WAIT,
    RoutineActionType.TAKE_SCREENSHOT,
    -> "${(action.value.toLongOrNull() ?: action.secondaryValue.toLongOrNull() ?: 0L) / 1000.0} ${if (ar) "ث" else "s"}"
    RoutineActionType.SEND_TELEGRAM_BOT -> "Chat ID: " + action.parameters["chatId"].orEmpty()
    RoutineActionType.CUSTOM_SCRIPT -> action.value.lineSequence().firstOrNull().orEmpty().take(80)
    else -> action.value.take(100)
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
                        Text(triggerTitle(template.trigger.type, ar) + " • " + template.actions.firstOrNull()?.let { RoutineCatalog.meta(it.type).let { m -> if (ar) m.titleAr else m.titleEn } }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onUse(template) }) { Text(if (ar) "استخدام" else "Use") }
                }
            }
        }
    }
}
