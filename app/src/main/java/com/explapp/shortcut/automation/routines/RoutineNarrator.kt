package com.explapp.shortcut.automation.routines

/**
 * A side-effect-free explanation of the exact routine model being edited or saved.
 * Inspired by the explicit review-before-run pattern already adapted from OpenTasker (MIT).
 * Never include message bodies, bot tokens, script source or other credential values.
 */
object RoutineNarrator {
    fun describe(routine: AutomationRoutine, ar: Boolean): String {
        val parts = mutableListOf<String>()
        parts += trigger(routine.trigger, ar)
        if (routine.conditions.isNotEmpty()) {
            val clauses = routine.conditions.map { condition(it, ar) }
            parts += if (ar) "بشرط تحقق جميع الشروط: " + clauses.joinToString("، ") + "."
                else "Only if all conditions match: " + clauses.joinToString("; ") + "."
        }
        if (routine.actions.isEmpty()) {
            parts += if (ar) "لم تُضف أي خطوات بعد." else "No actions have been added yet."
        } else {
            parts += if (ar) "تُنفذ الخطوات بالترتيب، باستثناء الخطوات المتجاورة التي تحمل مجموعة توازٍ واحدة:" else "Steps run in order except adjacent steps with the same parallel group:"
            routine.actions.forEachIndexed { index, action ->
                val behavior = describeAction(action, ar)
                val error = if (action.continueOnError) {
                    if (ar) " عند الفشل، تابع إلى الخطوة التالية." else " If it fails, continue to the next step."
                } else {
                    if (ar) " عند الفشل، أوقف بقية الخطوات." else " If it fails, stop the remaining steps."
                }
                val retry = RoutineRetryPolicy.count(action)
                val retryText = if (retry > 0) {
                    if (ar) " عند فشل الانتظار، أعد المحاولة حتى $retry مرات." else " Retry a failed wait up to $retry times."
                } else ""
                val group = action.parameters["parallelGroup"].orEmpty()
                val parallel = if (group.isNotBlank()) {
                    if (ar) " بالتوازي ضمن المجموعة ${safe(group)}." else " In parallel group ${safe(group)}."
                } else ""
                parts += "${index + 1}. $behavior$parallel$retryText$error"
            }
        }
        val manual = routine.actions.any {
            it.type == RoutineActionType.PREPARE_TELEGRAM ||
                it.type == RoutineActionType.PREPARE_WHATSAPP ||
                it.type == RoutineActionType.SHARE_TEXT ||
                it.type == RoutineActionType.SHARE_FILE
        }
        if (manual) parts += if (ar) {
            "بعض الخطوات تفتح تطبيقًا آخر وتحتاج تأكيدك؛ تجهيز الرسالة ليس إرسالًا تلقائيًا."
        } else {
            "Some steps open another app and require your confirmation; preparing a message is not automatic sending."
        }
        if (routine.actions.any { it.type == RoutineActionType.SEND_TELEGRAM_BOT }) {
            parts += if (ar) {
                "إرسال البوت يتم عبر الإنترنت وبصلاحيات البوت فقط، وليس من حساب تيليجرام الشخصي."
            } else {
                "Bot delivery requires internet and bot permissions; it does not send from your personal Telegram account."
            }
        }
        if (!routine.isEnabled) parts += if (ar) "هذه الأتمتة متوقفة حاليًا." else "This automation is currently paused."
        return parts.joinToString("\n")
    }

    private fun trigger(value: RoutineTrigger, ar: Boolean): String = when (value.type) {
        RoutineTriggerType.MANUAL ->
            if (ar) "عند الضغط على تشغيل يدويًا:" else "When you tap Run manually:"
        RoutineTriggerType.TIME -> {
            val frequency = when (value.repeat) {
                RoutineRepeat.ONCE -> if (ar) "مرة واحدة بتاريخ ${safe(value.repeatValue)}" else "once on ${safe(value.repeatValue)}"
                RoutineRepeat.DAILY -> if (ar) "يوميًا" else "daily"
                RoutineRepeat.WEEKLY -> if (ar) "أسبوعيًا في اليوم ${safe(value.repeatValue)}" else "weekly on day ${safe(value.repeatValue)}"
                RoutineRepeat.MONTHLY -> if (ar) "شهريًا في اليوم ${safe(value.repeatValue)}" else "monthly on day ${safe(value.repeatValue)}"
                RoutineRepeat.YEARLY -> if (ar) "سنويًا بتاريخ ${safe(value.repeatValue)}" else "yearly on ${safe(value.repeatValue)}"
            }
            if (ar) "عند الساعة ${safe(value.value)}، $frequency:" else "At ${safe(value.value)}, $frequency:"
        }
        RoutineTriggerType.CHARGER_CONNECTED ->
            if (ar) "عند توصيل الشاحن:" else "When the charger connects:"
        RoutineTriggerType.CHARGER_DISCONNECTED ->
            if (ar) "عند فصل الشاحن:" else "When the charger disconnects:"
        RoutineTriggerType.BATTERY_BELOW ->
            if (ar) "عندما تنخفض البطارية إلى ${safe(value.value)}% أو أقل:" else "When battery reaches ${safe(value.value)}% or lower:"
        RoutineTriggerType.NFC ->
            if (ar) "عند قراءة وسم NFC المرتبط بالأتمتة:" else "When the linked NFC tag is scanned:"
        RoutineTriggerType.BOOT ->
            if (ar) "بعد تشغيل الجهاز، وفق قيود Android:" else "After device boot, subject to Android restrictions:"
    }

    private fun condition(value: RoutineCondition, ar: Boolean): String = when (value.type) {
        RoutineConditionType.BATTERY_ABOVE ->
            if (ar) "البطارية أعلى من ${safe(value.value)}%" else "battery above ${safe(value.value)}%"
        RoutineConditionType.BATTERY_BELOW ->
            if (ar) "البطارية أقل من ${safe(value.value)}%" else "battery below ${safe(value.value)}%"
        RoutineConditionType.DAY_OF_WEEK ->
            if (ar) "اليوم رقم ${safe(value.value)} من الأسبوع" else "ISO weekday ${safe(value.value)}"
        RoutineConditionType.VARIABLE_EQUALS ->
            if (ar) "المتغير ${safe(value.value)} يساوي القيمة المحددة" else "variable ${safe(value.value)} equals the chosen value"
        RoutineConditionType.VARIABLE_CONTAINS ->
            if (ar) "المتغير ${safe(value.value)} يحتوي على القيمة المحددة" else "variable ${safe(value.value)} contains the chosen value"
    }

    private fun describeAction(value: RoutineAction, ar: Boolean): String {
        val title = RoutineCatalog.meta(value.type).let { if (ar) it.titleAr else it.titleEn }
        val suffix = when (value.type) {
            RoutineActionType.WAIT -> if (ar) " لمدة ${safe(value.value)} مللي ثانية." else " for ${safe(value.value)} milliseconds."
            RoutineActionType.PREPARE_TELEGRAM,
            RoutineActionType.PREPARE_WHATSAPP,
            -> if (ar) " للمستلم ${safe(value.value)} (دون إرسال تلقائي)." else " for ${safe(value.value)} (not automatically sent)."
            RoutineActionType.SEND_TELEGRAM_BOT -> {
                val recipient = safe(value.parameters["chatId"].orEmpty())
                if (ar) " إلى محادثة البوت $recipient، مع التحقق من نتيجة الطلب." else " to bot chat $recipient, checking the request result."
            }
            RoutineActionType.SET_VARIABLE,
            RoutineActionType.READ_CLIPBOARD,
            -> if (ar) " باسم ${safe(value.value)}." else " as ${safe(value.value)}."
            RoutineActionType.STOP_SHORTCUT -> if (ar) "، ولا تُنفذ الخطوات التالية." else "; later steps will not run."
            RoutineActionType.TAKE_SCREENSHOT,
            RoutineActionType.OPEN_APP_SCREENSHOT,
            -> if (ar) "، وقد تتطلب موافقة تصوير الشاشة." else "; screen capture may require your permission."
            RoutineActionType.CUSTOM_SCRIPT ->
                if (ar) " داخل بيئة التنفيذ المقيدة." else " in the restricted execution environment."
            else -> "."
        }
        return title + suffix
    }

    private fun safe(value: String): String = value
        .replace(Regex("[\\r\\n\\t]"), " ")
        .trim()
        .take(64)
        .ifBlank { "…" }
}
