package com.explapp.shortcut.ui

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.util.Calendar
import java.util.Locale
import com.explapp.shortcut.automation.routines.AutomationRoutine
import com.explapp.shortcut.automation.routines.RoutineAction
import com.explapp.shortcut.automation.routines.RoutineActionType
import com.explapp.shortcut.automation.routines.RoutineCatalog
import com.explapp.shortcut.automation.routines.RoutineCondition
import com.explapp.shortcut.automation.routines.RoutineConditionType
import com.explapp.shortcut.automation.routines.RoutineDispatcher
import com.explapp.shortcut.automation.routines.RoutinePermissionPolicy
import com.explapp.shortcut.automation.routines.RoutineRepeat
import com.explapp.shortcut.automation.routines.RoutineScheduler
import com.explapp.shortcut.automation.routines.RoutineStore
import com.explapp.shortcut.automation.routines.RoutineTemplateCatalog
import com.explapp.shortcut.automation.routines.RoutineTrigger
import com.explapp.shortcut.automation.routines.RoutineTriggerType
import com.explapp.shortcut.automation.routines.TelegramBotSender
import com.explapp.shortcut.data.InstalledAppRepository
import com.explapp.shortcut.tools.NfcSetupActivity
import com.explapp.shortcut.tools.ScreenCaptureActivity
import com.explapp.shortcut.tools.PersistentScreenCaptureService
import com.explapp.shortcut.tools.ToolCatalog
import com.explapp.shortcut.tools.ToolId

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
    var showDailyScreenshotTelegram by remember { mutableStateOf(false) }
    var showDailyScreenshotTelegramChat by remember { mutableStateOf(false) }
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

        showDailyScreenshotTelegram -> DailyScreenshotTelegramWizard(
            useBot = true,
            onCancel = { showDailyScreenshotTelegram = false },
            onSave = { routine ->
                upsert(routine)
                showDailyScreenshotTelegram = false
            },
        )

        showDailyScreenshotTelegramChat -> DailyScreenshotTelegramWizard(
            useBot = false,
            onCancel = { showDailyScreenshotTelegramChat = false },
            onSave = { routine ->
                upsert(routine)
                showDailyScreenshotTelegramChat = false
            },
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
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (ar) "1. لقطة شاشة وإرسال تلقائي بواسطة بوت تيليجرام"
                            else "1. Screenshot and auto-send with Telegram bot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (ar) "أدخل اسم مستخدم تيليجرام فقط، وسيحاول التطبيق العثور على المحادثة المناسبة تلقائيًا."
                            else "Enter only the Telegram username; Shortcut will try to resolve the correct chat automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { showDailyScreenshotTelegram = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (ar) "إعداد مهمة البوت" else "Set up bot task")
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (ar) "2. لقطة شاشة ومشاركتها في محادثة تيليجرام عادية"
                            else "2. Screenshot and share to a normal Telegram chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (ar) "بدون بوت. بعد التقاط الصورة يحاول التطبيق فتح تيليجرام بالصورة مباشرة. إذا منع أندرويد الفتح من الخلفية، يظهر إشعار «فتح تيليجرام» كبديل."
                            else "No bot. After capture, Shortcut tries to open Telegram with the image immediately. If Android blocks background opening, an Open Telegram notification appears as a fallback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { showDailyScreenshotTelegramChat = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (ar) "إعداد المحادثة العادية" else "Set up normal chat task")
                        }
                    }
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
                                Text(
                                    triggerTitle(routine.trigger.type, ar) + " " + routine.trigger.value +
                                        if (routine.trigger.type == RoutineTriggerType.TIME) " • " + repeatSummary(routine.trigger, ar) else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
private fun DailyScreenshotTelegramWizard(
    useBot: Boolean,
    onCancel: () -> Unit,
    onSave: (AutomationRoutine) -> Unit,
) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val installedApps = remember(context) { InstalledAppRepository(context).loadLaunchableApps() }

    var shortcutName by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(RoutineRepeat.DAILY) }
    var repeatValue by remember { mutableStateOf("") }
    var appPackage by remember { mutableStateOf("") }
    var delayMs by remember { mutableStateOf("3000") }
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var destinationStatus by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var keepCaptureSession by remember { mutableStateOf(true) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pendingRoutine by remember { mutableStateOf<AutomationRoutine?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }

    fun normalizedTelegramDestination(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        if (value.startsWith("@")) return value
        if (value.matches(Regex("-?\\d+"))) return value
        return "@" + value
    }

    fun telegramDestinationHint(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) {
            return if (ar) "أدخل اسم المستخدم فقط. يمكنك كتابته مع @ أو بدونها." else "Enter the username only. You can type it with or without @."
        }
        return when {
            value.matches(Regex("-?\\d+")) ->
                if (ar) "هذه قيمة رقمية. الأفضل إدخال اسم المستخدم بدلًا منها." else "This is a numeric value. Prefer entering the username instead."
            else ->
                if (ar) "سيبحث التطبيق عن " + normalizedTelegramDestination(value) + " تلقائيًا."
                else "Shortcut will resolve " + normalizedTelegramDestination(value) + " automatically."
        }
    }

    fun finishSave() {
        val routine = pendingRoutine ?: return
        onSave(routine)
        pendingRoutine = null
    }

    val exactAlarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finishSave()
    }

    fun requestExactAlarmOrSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            permissionMessage = if (ar) "فعّل السماح بالمنبهات الدقيقة حتى يبدأ الاختصار أقرب ما يمكن إلى الوقت الذي اخترته." else "Allow exact alarms so the shortcut can start as close as possible to the selected time."
            exactAlarmLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            finishSave()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestExactAlarmOrSave()
        } else {
            permissionMessage = if (ar) "يلزم السماح بالإشعارات لأن أندرويد يحتاج أن يعرض لك إشعارًا لتأكيد تصوير الشاشة عند حلول الوقت." else "Notification permission is required because Android must show you a notification to approve screen capture at the scheduled time."
            pendingRoutine = null
        }
    }

    val selectedApp = installedApps.firstOrNull { it.packageName == appPackage }
    val repeatValid = when (repeat) {
        RoutineRepeat.ONCE -> runCatching { java.time.LocalDate.parse(repeatValue) }.isSuccess
        RoutineRepeat.DAILY -> true
        RoutineRepeat.WEEKLY -> repeatValue.toIntOrNull() in 1..7
        RoutineRepeat.MONTHLY -> repeatValue.toIntOrNull() in 1..31
        RoutineRepeat.YEARLY -> runCatching { java.time.MonthDay.parse("--" + repeatValue) }.isSuccess
    }
    val canSave = time.isNotBlank() && appPackage.isNotBlank() && repeatValid &&
        (!useBot || (botToken.isNotBlank() && chatId.isNotBlank()))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (useBot) {
                    if (ar) "فتح تطبيق يوميًا وإرسال لقطة الشاشة بواسطة بوت تيليجرام"
                    else "Open an app daily and send the screenshot with a Telegram bot"
                } else {
                    if (ar) "فتح تطبيق يوميًا ومشاركة لقطة الشاشة في محادثة تيليجرام عادية"
                    else "Open an app daily and share the screenshot to a normal Telegram chat"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            ClearHint(
                if (useBot) {
                    if (ar) "المسار: الوقت المحدد ← فتح التطبيق ← التقاط الصورة ← العثور على المستخدم من اسمه ← الإرسال بواسطة البوت."
                    else "Flow: scheduled time → open app → capture screenshot → resolve the username → send with the bot."
                } else {
                    if (ar) "المسار: الوقت المحدد ← فتح التطبيق ← التقاط الصورة ← فتح تيليجرام بالصورة مباشرة إن سمح النظام، وإلا يظهر إشعار «فتح تيليجرام» ← اختيار المحادثة وتأكيد الإرسال."
                    else "Flow: scheduled time → open app → capture screenshot → open Telegram with the image when Android allows it; otherwise show an Open Telegram notification → choose the chat and confirm sending."
                },
                ar,
            )
        }

        item {
            OutlinedTextField(
                value = shortcutName,
                onValueChange = { shortcutName = it },
                label = { Text(if (ar) "اسم الجدولة" else "Schedule name") },
                placeholder = {
                    Text(
                        if (useBot) {
                            if (ar) "مثال: تقرير الصباح إلى البوت" else "Example: Morning report to bot"
                        } else {
                            if (ar) "مثال: لقطة الصباح إلى تيليجرام" else "Example: Morning screenshot to Telegram"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(if (ar) "1. الوقت" else "1. Time", fontWeight = FontWeight.Bold)
            val now = Calendar.getInstance()
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.HOUR_OF_DAY)
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: now.get(Calendar.MINUTE)
            Button(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> time = String.format(Locale.US, "%02d:%02d", h, m) },
                        hour,
                        minute,
                        true,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (time.isBlank()) {
                        if (ar) "اختر الوقت" else "Choose time"
                    } else {
                        (if (ar) "كل يوم الساعة " else "Every day at ") + time
                    },
                )
            }
        }

        item {
            Text(if (ar) "2. التكرار" else "2. Repeat", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RoutineRepeat.entries) { option ->
                    FilterChip(
                        selected = repeat == option,
                        onClick = {
                            repeat = option
                            val now = Calendar.getInstance()
                            repeatValue = when (option) {
                                RoutineRepeat.ONCE -> String.format(
                                    Locale.US,
                                    "%04d-%02d-%02d",
                                    now.get(Calendar.YEAR),
                                    now.get(Calendar.MONTH) + 1,
                                    now.get(Calendar.DAY_OF_MONTH),
                                )
                                RoutineRepeat.DAILY -> ""
                                RoutineRepeat.WEEKLY -> {
                                    val day = now.get(Calendar.DAY_OF_WEEK)
                                    if (day == Calendar.SUNDAY) "7" else (day - 1).toString()
                                }
                                RoutineRepeat.MONTHLY -> now.get(Calendar.DAY_OF_MONTH).toString()
                                RoutineRepeat.YEARLY -> String.format(
                                    Locale.US,
                                    "%02d-%02d",
                                    now.get(Calendar.MONTH) + 1,
                                    now.get(Calendar.DAY_OF_MONTH),
                                )
                            }
                        },
                        label = { Text(repeatLabel(option, ar)) },
                    )
                }
            }

            when (repeat) {
                RoutineRepeat.ONCE -> {
                    val now = Calendar.getInstance()
                    val parts = repeatValue.split("-")
                    val year = parts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR)
                    val month = parts.getOrNull(1)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)
                    val day = parts.getOrNull(2)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
                    Button(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, pickedYear, pickedMonth, pickedDay ->
                                    repeatValue = String.format(Locale.US, "%04d-%02d-%02d", pickedYear, pickedMonth + 1, pickedDay)
                                },
                                year,
                                month - 1,
                                day,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (repeatValue.isBlank()) {
                                if (ar) "اختر تاريخ التنفيذ" else "Choose run date"
                            } else {
                                (if (ar) "التاريخ: " else "Date: ") + repeatValue
                            },
                        )
                    }
                    ClearHint(
                        if (ar) "سيعمل الاختصار مرة واحدة فقط في هذا التاريخ والوقت."
                        else "The shortcut will run once on this date and time.",
                        ar,
                    )
                }
                RoutineRepeat.DAILY -> ClearHint(
                    if (ar) "سيعمل في الوقت المحدد كل يوم." else "Runs every day at the selected time.",
                    ar,
                )
                RoutineRepeat.WEEKLY -> {
                    Text(if (ar) "يوم الأسبوع" else "Weekday", fontWeight = FontWeight.Bold)
                    val days = if (ar) {
                        listOf("1" to "الاثنين", "2" to "الثلاثاء", "3" to "الأربعاء", "4" to "الخميس", "5" to "الجمعة", "6" to "السبت", "7" to "الأحد")
                    } else {
                        listOf("1" to "Monday", "2" to "Tuesday", "3" to "Wednesday", "4" to "Thursday", "5" to "Friday", "6" to "Saturday", "7" to "Sunday")
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(days) { (stored, label) ->
                            FilterChip(
                                selected = repeatValue == stored,
                                onClick = { repeatValue = stored },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                RoutineRepeat.MONTHLY -> {
                    Text(if (ar) "يوم الشهر" else "Day of month", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..31).toList()) { day ->
                            FilterChip(
                                selected = repeatValue == day.toString(),
                                onClick = { repeatValue = day.toString() },
                                label = { Text(day.toString()) },
                            )
                        }
                    }
                }
                RoutineRepeat.YEARLY -> {
                    val now = Calendar.getInstance()
                    val parts = repeatValue.split("-")
                    val month = parts.getOrNull(0)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)
                    val day = parts.getOrNull(1)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
                    Button(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, _, pickedMonth, pickedDay ->
                                    repeatValue = String.format(Locale.US, "%02d-%02d", pickedMonth + 1, pickedDay)
                                },
                                now.get(Calendar.YEAR),
                                month - 1,
                                day,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (repeatValue.isBlank()) {
                                if (ar) "اختر التاريخ السنوي" else "Choose yearly date"
                            } else {
                                (if (ar) "التاريخ: " else "Date: ") + repeatValue
                            },
                        )
                    }
                }
            }
        }

        item {
            Text(if (ar) "3. التطبيق" else "3. App", fontWeight = FontWeight.Bold)
            Button(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedApp?.label ?: if (ar) "اختر التطبيق المطلوب فتحه" else "Choose the app to open")
            }
            Text(if (ar) "مدة الانتظار قبل التقاط الصورة" else "Wait before taking the screenshot", fontWeight = FontWeight.Bold)
            DelayChips(ar, delayMs) { delayMs = it }
            ClearHint(
                if (ar) "اختر 3 إلى 5 ثوانٍ عادةً حتى يكتمل تحميل شاشة التطبيق قبل التصوير."
                else "Usually 3–5 seconds is enough for the app screen to load before capture.",
                ar,
            )
        }

        item {
            Text(if (ar) "4. لقطة الشاشة" else "4. Screenshot", fontWeight = FontWeight.Bold)
            Text(if (ar) "وضع تصوير الشاشة" else "Screen-capture mode", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = !keepCaptureSession,
                        onClick = { keepCaptureSession = false },
                        label = { Text(if (ar) "موافقة في كل مرة" else "Approve each time") },
                    )
                }
                item {
                    FilterChip(
                        selected = keepCaptureSession,
                        onClick = { keepCaptureSession = true },
                        label = { Text(if (ar) "جلسة تصوير مستمرة" else "Keep capture session active") },
                    )
                }
            }
            if (keepCaptureSession) {
                ClearHint(
                    if (ar) "توافق مرة واحدة الآن، ثم تبقى جلسة تصوير الشاشة نشطة في خدمة أمامية. يمكن للاختصار استخدام الجلسة لاحقًا دون طلب موافقة جديدة ما دامت الجلسة لم تتوقف ولم يُعاد تشغيل الهاتف."
                    else "Approve once now, then Shortcut keeps one foreground screen-capture session active. Scheduled captures can reuse it without asking again while the session remains active.",
                    ar,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, ScreenCaptureActivity::class.java)
                                .putExtra(ScreenCaptureActivity.EXTRA_PERSISTENT_START_ONLY, true),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (PersistentScreenCaptureService.isSessionActive(context)) {
                            if (ar) "جلسة التصوير نشطة" else "Capture session is active"
                        } else {
                            if (ar) "تفعيل جلسة التصوير الآن" else "Activate capture session now"
                        },
                    )
                }
            } else {
                ClearHint(
                    if (ar) "عند كل موعد سيظهر إشعار لتأكيد إذن تصوير الشاشة قبل أن يكمل الاختصار."
                    else "At each scheduled run, a notification asks you to approve screen capture before the shortcut continues.",
                    ar,
                )
            }
        }

        if (useBot) {
            item {
                Text(if (ar) "5. تيليجرام" else "5. Telegram", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text(if (ar) "رمز البوت" else "Bot token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = {
                        chatId = it
                        destinationStatus = null
                    },
                    label = { Text(if (ar) "اسم مستخدم تيليجرام" else "Telegram username") },
                    placeholder = { Text(if (ar) "مثال: @username" else "Example: @username") },
                    supportingText = {
                        Column {
                            Text(telegramDestinationHint(chatId))
                            destinationStatus?.let { status ->
                                Text(status, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(if (ar) "نص الرسالة مع الصورة" else "Message with the image") },
                    placeholder = { Text(if (ar) "مثال: لقطة الشاشة اليومية" else "Example: Daily screenshot") },
                    modifier = Modifier.fillMaxWidth(),
                )
    
                Button(
                    onClick = {
                        Thread {
                            val sender = TelegramBotSender()
                            val normalized = normalizedTelegramDestination(chatId)
                            val destination = sender.validateDestination(botToken.trim(), normalized)
                            val result = if (destination.isSuccess) {
                                sender.sendText(
                                    botToken.trim(),
                                    normalized,
                                    if (ar) "رسالة اختبار من تطبيق Shortcut" else "Test message from Shortcut",
                                )
                            } else {
                                destination
                            }
                            (context as? android.app.Activity)?.runOnUiThread {
                                val message = if (result.isSuccess) {
                                    if (ar) "صالح ✓ تم التحقق ووصلت رسالة الاختبار." else "Valid ✓ Verified and test message sent."
                                } else {
                                    val reason = result.exceptionOrNull()?.message.orEmpty()
                                    if (ar) "غير صالح ✗ " + reason else "Invalid ✗ " + reason
                                }
                                destinationStatus = message
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }.start()
                    },
                    enabled = botToken.isNotBlank() && chatId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (ar) "تحقق من اسم المستخدم وأرسل اختبارًا" else "Verify username and send test")
                }
    
                if (keepCaptureSession) {
                    Button(
                        onClick = {
                            val captureIntent = Intent(context, PersistentScreenCaptureService::class.java)
                                .setAction(PersistentScreenCaptureService.ACTION_CAPTURE)
                                .putExtra(PersistentScreenCaptureService.EXTRA_LAUNCH_PACKAGE, appPackage)
                                .putExtra(PersistentScreenCaptureService.EXTRA_CAPTURE_DELAY_MS, delayMs.toLongOrNull() ?: 3_000L)
                                .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_BOT_TOKEN, botToken)
                                .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_CHAT_ID, normalizedTelegramDestination(chatId))
                                .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_CAPTION, caption)
                            ContextCompat.startForegroundService(context, captureIntent)
                            Toast.makeText(
                                context,
                                if (ar) "بدأ اختبار: فتح التطبيق ثم التقاط الشاشة وإرسالها." else "Test started: opening the app, capturing, and sending.",
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        enabled = appPackage.isNotBlank() &&
                            botToken.isNotBlank() &&
                            chatId.isNotBlank() &&
                            PersistentScreenCaptureService.isSessionActive(context),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (ar) "اختبار لقطة الشاشة والإرسال الآن" else "Test screenshot and send now")
                    }
                    if (!PersistentScreenCaptureService.isSessionActive(context)) {
                        ClearHint(
                            if (ar) "فعّل جلسة تصوير الشاشة أولًا، ثم سيعمل زر الاختبار الكامل."
                            else "Activate the persistent capture session first, then the full test button becomes available.",
                            ar,
                        )
                    }
                }
            }
    
    
        } else {
            item {
                Text(if (ar) "3. تيليجرام" else "3. Telegram", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(if (ar) "نص اختياري مع الصورة" else "Optional text with the image") },
                    placeholder = { Text(if (ar) "مثال: لقطة الشاشة اليومية" else "Example: Daily screenshot") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ClearHint(
                    if (ar) "هذه المهمة لا تستخدم بوت. لا يمكن لأندرويد إجبار تيليجرام على الإرسال إلى شخص محدد من حسابك الشخصي بدون تدخل منك. بعد التقاط الصورة اضغط إشعار فتح تيليجرام، اختر المحادثة، ثم اضغط إرسال."
                    else "This task does not use a bot. Android cannot force Telegram to send from your personal account to a specific person without your confirmation. After capture, tap the Telegram notification, choose the chat, then tap Send.",
                    ar,
                )
            }
        }

        item {
            ClearHint(
                if (keepCaptureSession) {
                    if (ar) "إذا كانت الجلسة المستمرة نشطة فلن تحتاج لموافقة جديدة عند كل موعد. إذا توقفت الجلسة أو أُعيد تشغيل الهاتف، سيظهر إشعار لإعادة تفعيلها."
                    else "While the persistent session is active, scheduled runs do not need fresh capture approval. If the session stops or the phone restarts, Shortcut will ask you to reactivate it."
                } else {
                    if (ar) "عند حلول الوقت سيظهر إشعار؛ اضغط عليه ووافق على إذن تصوير الشاشة، ثم يكمل التطبيق الفتح والتصوير والإرسال."
                    else "At the scheduled time you will get a notification; tap it and approve screen capture, then Shortcut continues with opening, capture, and sending."
                },
                ar,
            )
        }

        permissionMessage?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(if (ar) "إلغاء" else "Cancel")
                }
                Button(
                    onClick = {
                        val routine = AutomationRoutine(
                            name = shortcutName.ifBlank {
                                if (useBot) {
                                    if (ar) "لقطة مجدولة إلى تيليجرام بواسطة البوت" else "Scheduled screenshot to Telegram bot"
                                } else {
                                    if (ar) "لقطة مجدولة إلى محادثة تيليجرام عادية" else "Scheduled screenshot to normal Telegram chat"
                                }
                            },
                            trigger = RoutineTrigger(
                                type = RoutineTriggerType.TIME,
                                value = time,
                                repeat = repeat,
                                repeatValue = repeatValue,
                            ),
                            actions = if (useBot) {
                                listOf(
                                    RoutineAction(
                                        type = RoutineActionType.OPEN_APP_SCREENSHOT,
                                        value = appPackage,
                                        secondaryValue = delayMs,
                                        parameters = mapOf(
                                            "persistentCapture" to keepCaptureSession.toString(),
                                        ),
                                    ),
                                    RoutineAction(
                                        type = RoutineActionType.SEND_TELEGRAM_BOT,
                                        value = "telegram",
                                        secondaryValue = caption,
                                        parameters = mapOf(
                                            "botToken" to botToken,
                                            "chatId" to chatId,
                                            "attachment" to "__screenshot_output__",
                                        ),
                                    ),
                                )
                            } else {
                                listOf(
                                    RoutineAction(
                                        type = RoutineActionType.OPEN_APP_SCREENSHOT,
                                        value = appPackage,
                                        secondaryValue = delayMs,
                                        parameters = mapOf(
                                            "persistentCapture" to keepCaptureSession.toString(),
                                            "normalTelegramShare" to "true",
                                            "telegramCaption" to caption,
                                        ),
                                    ),
                                )
                            },
                        )
                        pendingRoutine = routine
                        permissionMessage = null

                        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            requestExactAlarmOrSave()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (ar) "حفظ الأتمتة" else "Save automation")
                }
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
                                appPackage = app.packageName
                                showAppPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text(if (ar) "إلغاء" else "Cancel")
                }
            },
        )
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
    var triggerRepeat by remember(initial?.id) { mutableStateOf(initial?.trigger?.repeat ?: RoutineRepeat.DAILY) }
    var triggerRepeatValue by remember(initial?.id) { mutableStateOf(initial?.trigger?.repeatValue.orEmpty()) }
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
    var editingActionRef by remember { mutableStateOf<RoutineAction?>(null) }
    var actionSearch by remember { mutableStateOf("") }

    var pendingRoutine by remember { mutableStateOf<AutomationRoutine?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var actionPickerSearch by remember { mutableStateOf("") }
    var showToolPicker by remember { mutableStateOf(false) }
    var toolPickerSearch by remember { mutableStateOf("") }

    fun resetActionEditor() {
        value = ""
        secondary = ""
        params = emptyMap()
        continueOnError = false
        editingActionRef = null
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

        if (triggerType == RoutineTriggerType.TIME) {
            item {
                val now = Calendar.getInstance()
                val parts = triggerValue.split(":")
                val selectedHour = parts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.HOUR_OF_DAY)
                val selectedMinute = parts.getOrNull(1)?.toIntOrNull() ?: now.get(Calendar.MINUTE)
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> triggerValue = String.format(Locale.US, "%02d:%02d", hour, minute) },
                            selectedHour,
                            selectedMinute,
                            true,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (triggerValue.isBlank()) {
                            if (ar) "اختر الوقت" else "Choose time"
                        } else {
                            (if (ar) "الوقت المختار: " else "Selected time: ") + triggerValue
                        },
                    )
                }

                Text(if (ar) "التكرار" else "Repeat", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RoutineRepeat.entries) { repeat ->
                        FilterChip(
                            selected = triggerRepeat == repeat,
                            onClick = {
                                triggerRepeat = repeat
                                triggerRepeatValue = when (repeat) {
                                    RoutineRepeat.ONCE -> String.format(
                                        Locale.US,
                                        "%04d-%02d-%02d",
                                        now.get(Calendar.YEAR),
                                        now.get(Calendar.MONTH) + 1,
                                        now.get(Calendar.DAY_OF_MONTH),
                                    )
                                    RoutineRepeat.DAILY -> ""
                                    RoutineRepeat.WEEKLY -> {
                                        val iso = now.get(Calendar.DAY_OF_WEEK)
                                        if (iso == Calendar.SUNDAY) "7" else (iso - 1).toString()
                                    }
                                    RoutineRepeat.MONTHLY -> now.get(Calendar.DAY_OF_MONTH).toString()
                                    RoutineRepeat.YEARLY -> String.format(
                                        Locale.US,
                                        "%02d-%02d",
                                        now.get(Calendar.MONTH) + 1,
                                        now.get(Calendar.DAY_OF_MONTH),
                                    )
                                }
                            },
                            label = { Text(repeatLabel(repeat, ar)) },
                        )
                    }
                }

                when (triggerRepeat) {
                    RoutineRepeat.ONCE -> {
                        val partsDate = triggerRepeatValue.split("-")
                        val year = partsDate.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR)
                        val month = partsDate.getOrNull(1)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)
                        val day = partsDate.getOrNull(2)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
                        Button(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, pickedYear, pickedMonth, pickedDay ->
                                        triggerRepeatValue = String.format(Locale.US, "%04d-%02d-%02d", pickedYear, pickedMonth + 1, pickedDay)
                                    },
                                    year,
                                    month - 1,
                                    day,
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (triggerRepeatValue.isBlank()) {
                                    if (ar) "اختر تاريخ التنفيذ" else "Choose run date"
                                } else {
                                    (if (ar) "التاريخ: " else "Date: ") + triggerRepeatValue
                                },
                            )
                        }
                    }

                    RoutineRepeat.DAILY -> ClearHint(
                        if (ar) "سيعمل الاختصار كل يوم في الوقت المحدد."
                        else "The shortcut will run every day at the selected time.",
                        ar,
                    )

                    RoutineRepeat.WEEKLY -> {
                        Text(if (ar) "اختر يوم الأسبوع" else "Choose weekday", fontWeight = FontWeight.Bold)
                        val days = if (ar) {
                            listOf("1" to "الاثنين", "2" to "الثلاثاء", "3" to "الأربعاء", "4" to "الخميس", "5" to "الجمعة", "6" to "السبت", "7" to "الأحد")
                        } else {
                            listOf("1" to "Monday", "2" to "Tuesday", "3" to "Wednesday", "4" to "Thursday", "5" to "Friday", "6" to "Saturday", "7" to "Sunday")
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(days) { (stored, label) ->
                                FilterChip(
                                    selected = triggerRepeatValue == stored,
                                    onClick = { triggerRepeatValue = stored },
                                    label = { Text(label) },
                                )
                            }
                        }
                        ClearHint(
                            if (ar) "سيعمل مرة كل أسبوع في اليوم والوقت اللذين اخترتهما."
                            else "The shortcut will run once each week on the selected weekday and time.",
                            ar,
                        )
                    }

                    RoutineRepeat.MONTHLY -> {
                        Text(if (ar) "اختر يوم الشهر" else "Choose day of month", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items((1..31).toList()) { day ->
                                FilterChip(
                                    selected = triggerRepeatValue == day.toString(),
                                    onClick = { triggerRepeatValue = day.toString() },
                                    label = { Text(day.toString()) },
                                )
                            }
                        }
                        ClearHint(
                            if (ar) "سيعمل مرة كل شهر في هذا اليوم. إذا لم يوجد هذا اليوم في شهر معيّن، ينتقل إلى الشهر التالي الذي يحتويه."
                            else "Runs once each month on this day. If a month does not contain that day, it skips to the next month that does.",
                            ar,
                        )
                    }

                    RoutineRepeat.YEARLY -> {
                        val monthDay = triggerRepeatValue.split("-")
                        val month = monthDay.getOrNull(0)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)
                        val day = monthDay.getOrNull(1)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
                        Button(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, _, pickedMonth, pickedDay ->
                                        triggerRepeatValue = String.format(Locale.US, "%02d-%02d", pickedMonth + 1, pickedDay)
                                    },
                                    now.get(Calendar.YEAR),
                                    month - 1,
                                    day,
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (triggerRepeatValue.isBlank()) {
                                    if (ar) "اختر التاريخ السنوي" else "Choose yearly date"
                                } else {
                                    (if (ar) "التاريخ السنوي: " else "Yearly date: ") + triggerRepeatValue
                                },
                            )
                        }
                        ClearHint(
                            if (ar) "سيعمل مرة كل سنة في هذا التاريخ والوقت."
                            else "The shortcut will run once each year on this date and time.",
                            ar,
                        )
                    }
                }

                ClearHint(
                    if (ar) "اضغط لاختيار الوقت من الساعة بدل كتابته يدويًا."
                    else "Tap to choose the time from a clock instead of typing it manually.",
                    ar,
                )
            }
        } else if (triggerType == RoutineTriggerType.BATTERY_BELOW || triggerType == RoutineTriggerType.NFC) {
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

        itemsIndexed(conditions) { index, condition ->
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
            Button(
                onClick = { showActionPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ar) "بحث وتصفح جميع الإجراءات" else "Search and browse all actions")
            }
            OutlinedTextField(
                value = actionSearch,
                onValueChange = { actionSearch = it },
                label = { Text(if (ar) "بحث سريع" else "Quick search") },
                placeholder = { Text(if (ar) "ملف، مشاركة، نص، صورة، ويب، متغير..." else "File, share, text, image, web, variable...") },
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
                            if (actions.lastOrNull()?.type in setOf(RoutineActionType.OPEN_APP_SCREENSHOT, RoutineActionType.TAKE_SCREENSHOT)) {
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
                            ClearHint(
                                if (ar) "مثال: إذا سميت المتغير «النتيجة»، يمكنك استخدام {{النتيجة}} داخل أي حقل نصي في الخطوات التالية."
                                else "Example: if you name the variable result, use {{result}} in text fields in later steps.",
                                ar,
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
                            ClearHint(
                                if (ar) "سيقرأ التطبيق الحافظة عند وصول التنفيذ إلى هذه الخطوة. إذا سميت المتغير «النص»، استخدم {{النص}} في الخطوات التالية."
                                else "Shortcut reads the clipboard when this step runs. If you name the variable text, use {{text}} in later steps.",
                                ar,
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
                            ClearHint(
                                if (ar) "يمكنك كتابة نص ثابت، أو إدخال متغير سابق بين قوسين مزدوجين مثل {{النتيجة}}."
                                else "You can enter fixed text or insert a previous variable using double braces, such as {{result}}.",
                                ar,
                            )
                        }

                        RoutineActionType.SAVE_TEXT_FILE -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "النص المراد حفظه" else "Text to save") },
                                placeholder = { Text(if (ar) "اكتب نصًا أو استخدم متغيرًا سابقًا" else "Enter text or use a previous variable") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = secondary,
                                onValueChange = { secondary = it },
                                label = { Text(if (ar) "اسم الملف" else "File name") },
                                placeholder = { Text(if (ar) "مثال: تقرير اليوم" else "Example: Daily report") },
                                supportingText = { Text(if (ar) "سيُحفظ الملف بصيغة TXT داخل مجلد Shortcut في التنزيلات." else "The TXT file is saved in the Shortcut folder inside Downloads.") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineActionType.SHARE_TEXT -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "النص المراد مشاركته" else "Text to share") },
                                placeholder = { Text(if (ar) "اكتب النص أو استخدم نتيجة سابقة" else "Enter text or use a previous result") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ClearHint(
                                if (ar) "عند التنفيذ سيظهر لك اختيار تطبيق المشاركة مثل الرسائل أو البريد أو تيليجرام."
                                else "When this step runs, Android opens the share sheet so you can choose an app such as messaging, email, or Telegram.",
                                ar,
                            )
                        }

                        RoutineActionType.SHARE_FILE -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "الملف المراد مشاركته" else "File to share") },
                                placeholder = { Text(if (ar) "يمكن استخدام ملف ناتج من خطوة حفظ سابقة" else "You can use a file created by an earlier save step") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ClearHint(
                                if (ar) "إذا جاءت هذه الخطوة بعد «حفظ النص كملف»، يمكنك استخدام {{lastFile}} لمشاركة الملف الناتج."
                                else "If this comes after Save text as file, use {{lastFile}} to share the file that was created.",
                                ar,
                            )
                        }

                        RoutineActionType.WEB_SEARCH -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text(if (ar) "ما الذي تريد البحث عنه؟" else "What do you want to search for?") },
                                placeholder = { Text(if (ar) "نص ثابت أو متغير سابق" else "Fixed text or a previous variable") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineActionType.STOP_SHORTCUT -> {
                            ClearHint(
                                if (ar) "عندما يصل التنفيذ إلى هذه الخطوة، يتوقف الاختصار فورًا ولا ينفذ أي خطوة بعدها."
                                else "When execution reaches this block, the shortcut stops immediately and no later steps run.",
                                ar,
                            )
                        }

                        RoutineActionType.OPEN_TOOL -> {
                            val selectedTool = ToolCatalog.all().firstOrNull { it.id.name == value }
                            Button(
                                onClick = { showToolPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    selectedTool?.let { if (ar) it.titleAr else it.titleEn }
                                        ?: if (ar) "اختر أداة من التطبيق" else "Choose a built-in tool",
                                )
                            }
                            ClearHint(
                                if (ar) "يمكنك تشغيل أدوات التطبيق نفسها داخل الاختصار، مثل QR وPDF وOCR وضغط الملفات والصور وأدوات الحافظة وغيرها."
                                else "You can launch Shortcut's built-in tools inside an automation, including QR, PDF, OCR, ZIP, image, clipboard, and other tools.",
                                ar,
                            )
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
                            val editing = editingActionRef
                            if (editing == null) {
                                actions += draft
                            } else {
                                val editIndex = actions.indexOfFirst { it === editing }
                                if (editIndex >= 0) actions[editIndex] = draft else actions += draft
                            }
                            resetActionEditor()
                        },
                        enabled = draft.isValid(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (editingActionRef != null) {
                                if (ar) "حفظ تعديل الخطوة" else "Save step changes"
                            } else {
                                if (ar) "إضافة الخطوة" else "Add step"
                            },
                        )
                    }
                }
            }
        }

        itemsIndexed(actions) { index, action ->
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
                            editingActionRef = action
                        }) { Text(if (ar) "تعديل" else "Edit") }
                        TextButton(onClick = {
                            if (editingActionRef === action) resetActionEditor()
                            actions.remove(action)
                        }) { Text(if (ar) "حذف" else "Remove") }
                    }
                }
            }
        }

        item {
            val routine = AutomationRoutine(
                id = stableId,
                name = name.ifBlank { if (ar) "اختصار جديد" else "New shortcut" },
                isEnabled = initial?.isEnabled ?: true,
                trigger = RoutineTrigger(
                    type = triggerType,
                    value = triggerValue,
                    repeat = if (triggerType == RoutineTriggerType.TIME) triggerRepeat else RoutineRepeat.DAILY,
                    repeatValue = if (triggerType == RoutineTriggerType.TIME) triggerRepeatValue else "",
                ),
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

    if (showActionPicker) {
        val pickerItems = RoutineCatalog.actions.filter {
            actionPickerSearch.isBlank() ||
                (if (ar) it.titleAr else it.titleEn).contains(actionPickerSearch, ignoreCase = true) ||
                (if (ar) it.categoryAr else it.categoryEn).contains(actionPickerSearch, ignoreCase = true) ||
                (if (ar) it.hintAr else it.hintEn).contains(actionPickerSearch, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showActionPicker = false },
            title = { Text(if (ar) "اختيار إجراء" else "Choose an action") },
            text = {
                Column {
                    OutlinedTextField(
                        value = actionPickerSearch,
                        onValueChange = { actionPickerSearch = it },
                        label = { Text(if (ar) "ابحث بالاسم أو الفئة أو الوظيفة" else "Search by name, category, or purpose") },
                        placeholder = { Text(if (ar) "مثال: حفظ، مشاركة، ملف، متغير، صورة..." else "Example: save, share, file, variable, image...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(pickerItems) { meta ->
                            TextButton(
                                onClick = {
                                    actionType = meta.type
                                    resetActionEditor()
                                    showActionPicker = false
                                    actionPickerSearch = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(if (ar) meta.titleAr else meta.titleEn, fontWeight = FontWeight.Bold)
                                    Text(
                                        (if (ar) meta.categoryAr else meta.categoryEn) + " • " + (if (ar) meta.hintAr else meta.hintEn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionPicker = false }) {
                    Text(if (ar) "إغلاق" else "Close")
                }
            },
        )
    }

    if (showToolPicker) {
        val tools = ToolCatalog.all().filter {
            toolPickerSearch.isBlank() ||
                (if (ar) it.titleAr else it.titleEn).contains(toolPickerSearch, ignoreCase = true) ||
                it.id.name.contains(toolPickerSearch, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showToolPicker = false },
            title = { Text(if (ar) "اختيار أداة" else "Choose a tool") },
            text = {
                Column {
                    OutlinedTextField(
                        value = toolPickerSearch,
                        onValueChange = { toolPickerSearch = it },
                        label = { Text(if (ar) "ابحث عن أداة" else "Search tools") },
                        placeholder = { Text(if (ar) "مثال: PDF، QR، نص، صور، ضغط..." else "Example: PDF, QR, text, images, ZIP...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(tools) { tool ->
                            TextButton(
                                onClick = {
                                    value = tool.id.name
                                    showToolPicker = false
                                    toolPickerSearch = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (ar) tool.titleAr else tool.titleEn)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showToolPicker = false }) {
                    Text(if (ar) "إغلاق" else "Close")
                }
            },
        )
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

private fun repeatLabel(repeat: RoutineRepeat, ar: Boolean): String = when (repeat) {
    RoutineRepeat.ONCE -> if (ar) "مرة واحدة" else "Once"
    RoutineRepeat.DAILY -> if (ar) "يومي" else "Daily"
    RoutineRepeat.WEEKLY -> if (ar) "أسبوعي" else "Weekly"
    RoutineRepeat.MONTHLY -> if (ar) "شهري" else "Monthly"
    RoutineRepeat.YEARLY -> if (ar) "سنوي" else "Yearly"
}

private fun repeatSummary(trigger: RoutineTrigger, ar: Boolean): String = when (trigger.repeat) {
    RoutineRepeat.ONCE -> if (ar) "مرة واحدة • " + trigger.repeatValue else "Once • " + trigger.repeatValue
    RoutineRepeat.DAILY -> if (ar) "يومي" else "Daily"
    RoutineRepeat.WEEKLY -> {
        val day = trigger.repeatValue.toIntOrNull()
        val namesAr = listOf("", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
        val namesEn = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        if (ar) "أسبوعي • " + (namesAr.getOrNull(day ?: 0) ?: "")
        else "Weekly • " + (namesEn.getOrNull(day ?: 0) ?: "")
    }
    RoutineRepeat.MONTHLY -> if (ar) "شهري • يوم " + trigger.repeatValue else "Monthly • day " + trigger.repeatValue
    RoutineRepeat.YEARLY -> if (ar) "سنوي • " + trigger.repeatValue else "Yearly • " + trigger.repeatValue
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
    else -> if (ar) "اختر قيمة من الخيارات" else "Choose a value from the options"
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
    RoutineActionType.SEND_TELEGRAM_BOT -> (if (ar) "معرّف المحادثة: " else "Chat ID: ") + action.parameters["chatId"].orEmpty()
    RoutineActionType.CUSTOM_SCRIPT -> action.value.lineSequence().firstOrNull().orEmpty().take(80)
    RoutineActionType.SET_VARIABLE -> (if (ar) "المتغير: " else "Variable: ") + action.value
    RoutineActionType.READ_CLIPBOARD -> (if (ar) "يحفظ في: " else "Stores in: ") + action.value
    RoutineActionType.COPY_TO_CLIPBOARD -> action.value.take(100)
    RoutineActionType.STOP_SHORTCUT -> if (ar) "يتوقف التنفيذ هنا" else "Execution stops here"
    RoutineActionType.SAVE_TEXT_FILE -> if (ar) "حفظ ملف نصي" else "Save text file"
    RoutineActionType.SHARE_TEXT -> if (ar) "مشاركة نص" else "Share text"
    RoutineActionType.SHARE_FILE -> if (ar) "مشاركة ملف" else "Share file"
    RoutineActionType.WEB_SEARCH -> if (ar) "بحث: " + action.value.take(70) else "Search: " + action.value.take(70)
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
