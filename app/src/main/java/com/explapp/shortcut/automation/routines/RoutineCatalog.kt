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
        RoutineActionMeta(RoutineActionType.OPEN_APP, "Apps", "التطبيقات", "Open app", "فتح تطبيق", "Choose any installed app. Use this only when you actually need another app to open.", "اختر أي تطبيق مثبت على جهازك. استخدم هذه الخطوة فقط عندما تحتاج فعلًا إلى فتح تطبيق آخر."),
        RoutineActionMeta(RoutineActionType.OPEN_APP_SCREENSHOT, "Apps", "التطبيقات", "Open app and take screenshot", "فتح تطبيق والتقاط الشاشة", "Android asks for screen-capture permission, then Shortcut opens the app, waits, takes exactly one screenshot, and stops capture automatically.", "سيطلب أندرويد موافقتك على تصوير الشاشة، ثم يفتح التطبيق وينتظر ويلتقط صورة شاشة واحدة فقط، وبعدها يوقف التصوير تلقائيًا."),
        RoutineActionMeta(RoutineActionType.TAKE_SCREENSHOT, "Media", "الوسائط", "Take screenshot", "التقاط الشاشة", "Android asks for screen-capture permission. After your chosen delay, Shortcut takes exactly one screenshot of the visible screen and stops automatically.", "سيطلب أندرويد موافقتك على تصوير الشاشة. بعد المدة التي تختارها يلتقط التطبيق صورة شاشة واحدة فقط لما هو ظاهر، ثم يتوقف تلقائيًا."),
        RoutineActionMeta(RoutineActionType.WAIT, "Flow", "التحكم", "Wait", "انتظار", "Pause the shortcut before the next step. Useful when another screen needs time to load.", "يوقف الاختصار مؤقتًا قبل الخطوة التالية. مفيد عندما تحتاج شاشة أو عملية إلى وقت حتى تجهز."),
        RoutineActionMeta(RoutineActionType.SHOW_NOTIFICATION, "System", "النظام", "Show notification", "إظهار إشعار", "Show a local notification with your text.", "يعرض إشعارًا على الهاتف بالنص الذي تكتبه."),
        RoutineActionMeta(RoutineActionType.OPEN_URL, "Web", "الويب", "Open link", "فتح رابط", "Open a website, deep link, or any supported address.", "يفتح موقعًا أو رابطًا عميقًا أو عنوانًا تدعمه التطبيقات المثبتة."),
        RoutineActionMeta(RoutineActionType.OPEN_MAPS, "Apps", "التطبيقات", "Open maps", "فتح الخرائط", "Open a place, address, or search in the maps app.", "يفتح مكانًا أو عنوانًا أو عبارة بحث في تطبيق الخرائط."),
        RoutineActionMeta(RoutineActionType.PREPARE_WHATSAPP, "Communication", "التواصل", "Prepare WhatsApp message", "تجهيز رسالة واتساب", "Prepare the recipient and message, then let you confirm sending in WhatsApp.", "يجهز المستلم والرسالة، ثم يفتح واتساب لتؤكد الإرسال بنفسك."),
        RoutineActionMeta(RoutineActionType.PREPARE_TELEGRAM, "Communication", "التواصل", "Prepare Telegram message", "تجهيز رسالة تيليجرام", "Prepare the message and open Telegram for your confirmation.", "يجهز الرسالة ويفتح تيليجرام لتؤكد الإرسال بنفسك."),
        RoutineActionMeta(RoutineActionType.SEND_TELEGRAM_BOT, "Communication", "التواصل", "Send with Telegram bot", "إرسال بواسطة بوت تيليجرام", "Automatically send text or the output file from a previous step using your Telegram bot.", "يرسل تلقائيًا نصًا أو ملفًا ناتجًا من خطوة سابقة بواسطة بوت تيليجرام."),
        RoutineActionMeta(RoutineActionType.SET_VARIABLE, "Variables", "المتغيرات", "Set variable", "تعيين متغير", "Save a value under a name so later steps can reuse it.", "يحفظ قيمة باسم تختاره حتى تستطيع الخطوات التالية استخدامها."),
        RoutineActionMeta(RoutineActionType.READ_CLIPBOARD, "Text", "النصوص", "Read clipboard", "قراءة الحافظة", "Read the current clipboard text and store it in a variable.", "يقرأ النص الموجود حاليًا في الحافظة ويحفظه داخل متغير."),
        RoutineActionMeta(RoutineActionType.COPY_TO_CLIPBOARD, "Text", "النصوص", "Copy to clipboard", "نسخ إلى الحافظة", "Copy text, including a previous result, to the device clipboard.", "ينسخ نصًا إلى حافظة الهاتف، ويمكن أن يكون النص ناتجًا من خطوة سابقة."),
        RoutineActionMeta(RoutineActionType.STOP_SHORTCUT, "Flow", "التحكم", "Stop shortcut", "إيقاف الاختصار", "Stop here and do not run any steps below this block.", "يوقف التنفيذ عند هذه النقطة، ولن تُنفذ أي خطوة موجودة بعدها."),
        RoutineActionMeta(RoutineActionType.CUSTOM_SCRIPT, "Advanced", "متقدم", "Custom script", "سكربت مخصص", "Run restricted JavaScript for calculations, text processing, JSON, or custom logic, then store its result.", "يشغّل سكربتًا مخصصًا داخل بيئة محدودة لمعالجة النصوص أو الأرقام أو البيانات، ثم يحفظ النتيجة للخطوات التالية."),
        RoutineActionMeta(RoutineActionType.OPEN_TOOL, "Shortcut", "أدوات التطبيق", "Open built-in tool", "فتح أداة داخلية", "Open one of Shortcut's built-in tools.", "يفتح إحدى الأدوات الموجودة داخل التطبيق."),
    )

    fun meta(type: RoutineActionType): RoutineActionMeta = actions.first { it.type == type }

    fun suggestions(after: RoutineActionType?): List<RoutineActionType> = when (after) {
        RoutineActionType.OPEN_APP -> listOf(RoutineActionType.WAIT, RoutineActionType.TAKE_SCREENSHOT, RoutineActionType.SHOW_NOTIFICATION)
        RoutineActionType.OPEN_APP_SCREENSHOT,
        RoutineActionType.TAKE_SCREENSHOT,
        -> listOf(RoutineActionType.SEND_TELEGRAM_BOT, RoutineActionType.COPY_TO_CLIPBOARD, RoutineActionType.CUSTOM_SCRIPT)
        RoutineActionType.READ_CLIPBOARD,
        RoutineActionType.SET_VARIABLE,
        RoutineActionType.CUSTOM_SCRIPT,
        -> listOf(RoutineActionType.COPY_TO_CLIPBOARD, RoutineActionType.SEND_TELEGRAM_BOT, RoutineActionType.SHOW_NOTIFICATION)
        RoutineActionType.STOP_SHORTCUT -> emptyList()
        else -> listOf(RoutineActionType.SET_VARIABLE, RoutineActionType.WAIT, RoutineActionType.SHOW_NOTIFICATION)
    }

    fun triggerHint(type: RoutineTriggerType, ar: Boolean): String = when (type) {
        RoutineTriggerType.MANUAL -> if (ar) "يعمل فقط عندما تضغط تشغيل. مناسب للاختصارات التي تريد تنفيذها عند الطلب." else "Runs only when you tap Run. Best for shortcuts you want to start on demand."
        RoutineTriggerType.TIME -> if (ar) "اكتب الوقت بصيغة 24 ساعة، مثل 08:00. سيحاول الاختصار التشغيل يوميًا في هذا الوقت." else "Enter a 24-hour time such as 08:00. The shortcut will try to run every day at that time."
        RoutineTriggerType.CHARGER_CONNECTED -> if (ar) "يبدأ الاختصار عندما يتصل الهاتف بالشاحن." else "Starts when the phone is connected to a charger."
        RoutineTriggerType.CHARGER_DISCONNECTED -> if (ar) "يبدأ الاختصار عندما يُفصل الهاتف عن الشاحن." else "Starts when the phone is disconnected from the charger."
        RoutineTriggerType.BATTERY_BELOW -> if (ar) "اكتب نسبة مثل 20. يبدأ الاختصار عندما تنخفض البطارية إلى أقل من هذه النسبة." else "Enter a percentage such as 20. The shortcut starts when battery drops below it."
        RoutineTriggerType.NFC -> if (ar) "بعد الحفظ، اكتب هذا الاختصار على وسم NFC. عند لمس الوسم سيبدأ الاختصار." else "After saving, write this shortcut to an NFC tag. Tapping the tag will start it."
        RoutineTriggerType.BOOT -> if (ar) "يبدأ بعد تشغيل الهاتف أو إعادة تشغيله، وفق قيود أندرويد على التشغيل في الخلفية." else "Starts after device boot or restart, subject to Android background limits."
    }
}
