package com.explapp.shortcut.automation.routines

data class RoutineActionMeta(
    val type: RoutineActionType,
    val categoryEn: String,
    val categoryAr: String,
    val titleEn: String,
    val titleAr: String,
    val hintEn: String,
    val hintAr: String,
)

object RoutineCatalog {
    val actions: List<RoutineActionMeta> = listOf(
        RoutineActionMeta(RoutineActionType.OPEN_APP, "Apps", "التطبيقات", "Open app", "فتح تطبيق", "Choose any installed app.", "اختر أي تطبيق مثبت على جهازك."),
        RoutineActionMeta(RoutineActionType.OPEN_APP_SCREENSHOT, "Apps", "التطبيقات", "Open app + screenshot", "فتح تطبيق + لقطة شاشة", "Open an app, wait, then capture one screenshot.", "افتح تطبيقًا وانتظر ثم التقط صورة شاشة واحدة."),
        RoutineActionMeta(RoutineActionType.TAKE_SCREENSHOT, "Media", "الوسائط", "Take screenshot", "التقاط لقطة شاشة", "Capture the visible screen after a delay.", "التقط الشاشة الظاهرة بعد مدة تحددها."),
        RoutineActionMeta(RoutineActionType.WAIT, "Flow", "التحكم", "Wait", "انتظار", "Pause before the next action.", "انتظر قبل تنفيذ الخطوة التالية."),
        RoutineActionMeta(RoutineActionType.SHOW_NOTIFICATION, "System", "النظام", "Show notification", "إظهار إشعار", "Display a local notification.", "اعرض إشعارًا محليًا."),
        RoutineActionMeta(RoutineActionType.OPEN_URL, "Web", "الويب", "Open URL", "فتح رابط", "Open a website or deep link.", "افتح موقعًا أو رابطًا عميقًا."),
        RoutineActionMeta(RoutineActionType.OPEN_MAPS, "Apps", "التطبيقات", "Open maps", "فتح الخرائط", "Open a place or search in Maps.", "افتح مكانًا أو بحثًا في الخرائط."),
        RoutineActionMeta(RoutineActionType.PREPARE_WHATSAPP, "Communication", "التواصل", "Prepare WhatsApp", "تجهيز واتساب", "Prepare recipient and message for user confirmation.", "جهز المستلم والرسالة ليكمل المستخدم الإرسال."),
        RoutineActionMeta(RoutineActionType.PREPARE_TELEGRAM, "Communication", "التواصل", "Prepare Telegram", "تجهيز تيليجرام", "Prepare a Telegram message for user confirmation.", "جهز رسالة تيليجرام ليكمل المستخدم الإرسال."),
        RoutineActionMeta(RoutineActionType.SEND_TELEGRAM_BOT, "Communication", "التواصل", "Telegram Bot send", "إرسال عبر Telegram Bot", "Send text or an output file automatically using your bot.", "أرسل نصًا أو ملفًا ناتجًا تلقائيًا بواسطة البوت."),
        RoutineActionMeta(RoutineActionType.CUSTOM_SCRIPT, "Advanced", "متقدم", "Custom JavaScript", "JavaScript مخصص", "Run sandboxed JavaScript and store its returned value.", "شغّل JavaScript محدود الصلاحيات واستخدم النتيجة في الخطوات التالية."),
        RoutineActionMeta(RoutineActionType.OPEN_TOOL, "Shortcut", "Shortcut", "Open Shortcut tool", "فتح أداة Shortcut", "Open one of Shortcut's built-in tools.", "افتح إحدى أدوات Shortcut الداخلية."),
    )

    fun meta(type: RoutineActionType): RoutineActionMeta = actions.first { it.type == type }

    fun suggestions(after: RoutineActionType?): List<RoutineActionType> = when (after) {
        RoutineActionType.OPEN_APP -> listOf(RoutineActionType.WAIT, RoutineActionType.TAKE_SCREENSHOT, RoutineActionType.SHOW_NOTIFICATION)
        RoutineActionType.OPEN_APP_SCREENSHOT,
        RoutineActionType.TAKE_SCREENSHOT,
        -> listOf(RoutineActionType.SEND_TELEGRAM_BOT, RoutineActionType.CUSTOM_SCRIPT, RoutineActionType.SHOW_NOTIFICATION)
        RoutineActionType.CUSTOM_SCRIPT -> listOf(RoutineActionType.SEND_TELEGRAM_BOT, RoutineActionType.SHOW_NOTIFICATION, RoutineActionType.OPEN_URL)
        else -> listOf(RoutineActionType.OPEN_APP, RoutineActionType.WAIT, RoutineActionType.SHOW_NOTIFICATION)
    }

    fun triggerHint(type: RoutineTriggerType, ar: Boolean): String = when (type) {
        RoutineTriggerType.MANUAL -> if (ar) "مثال: شغّل الاختصار يدويًا من داخل التطبيق." else "Example: run this shortcut manually."
        RoutineTriggerType.TIME -> if (ar) "مثال: 08:00 للتشغيل يوميًا في هذا الوقت." else "Example: 08:00 to run daily at this time."
        RoutineTriggerType.CHARGER_CONNECTED -> if (ar) "يعمل عند توصيل الشاحن." else "Runs when the charger is connected."
        RoutineTriggerType.CHARGER_DISCONNECTED -> if (ar) "يعمل عند فصل الشاحن." else "Runs when the charger is disconnected."
        RoutineTriggerType.BATTERY_BELOW -> if (ar) "مثال: 20 للتشغيل عند نزول البطارية تحت 20%." else "Example: 20 to run below 20% battery."
        RoutineTriggerType.NFC -> if (ar) "اكتب وسم NFC لهذا الاختصار بعد الحفظ." else "Write an NFC tag for this shortcut after saving."
        RoutineTriggerType.BOOT -> if (ar) "يعمل بعد تشغيل الجهاز." else "Runs after device startup."
    }
}
