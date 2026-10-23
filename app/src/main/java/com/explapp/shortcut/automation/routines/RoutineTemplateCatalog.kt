package com.explapp.shortcut.automation.routines

import com.explapp.shortcut.tools.ToolId

/**
 * Guided blueprint catalog. The ready/setup distinction follows the review-before-install
 * blueprint pattern already adapted from OpenTasker (MIT); see
 * THIRD_PARTY_NOTICES/OpenTasker-LICENSE.txt.
 *
 * Templates are editable drafts, not claims that external apps or files run unattended.
 */
object RoutineTemplateCatalog {
    enum class Readiness { READY, NEEDS_SETUP }

    private data class Copy(
        val ar: String,
        val en: String,
        val detailsAr: String,
        val detailsEn: String,
    )

    private val copy = mapOf(
        "Morning apps" to Copy("تطبيقات الصباح", "Morning apps", "اختر التطبيق المثبت قبل الحفظ.", "Choose an installed app before saving."),
        "Commute maps" to Copy("فتح الخرائط", "Open maps", "يفتح تطبيق الخرائط مباشرة. الوجهة اختيارية؛ عند الجدولة قد يتطلب أندرويد النقر على إشعار لفتح التطبيق.", "Opens the maps app directly. Destination is optional; scheduled launches may require tapping an Android notification."),
        "Charging reminder" to Copy("تذكير الشحن", "Charging reminder", "إشعار محلي عند توصيل الشاحن.", "A local notification when charging begins."),
        "Low battery" to Copy("تنبيه البطارية", "Low battery", "تنبيه عند وصول البطارية إلى 20%.", "A notification at 20% battery."),
        "WhatsApp prepared message" to Copy("تجهيز رسالة واتساب", "WhatsApp prepared message", "حدد المستلم والنص؛ يتطلب الإرسال تأكيدك.", "Choose recipient and text; sending needs your confirmation."),
        "Telegram prepared message" to Copy("تجهيز رسالة تيليجرام", "Telegram prepared message", "حدد المستلم والنص؛ لا يُرسل تلقائيًا.", "Choose recipient and text; it does not auto-send."),
        "Open app + dated screenshot" to Copy("فتح تطبيق وتصوير الشاشة", "Open app + dated screenshot", "اختر التطبيق؛ يتطلب تصوير الشاشة موافقة Android.", "Choose an app; Android screen capture consent may be required."),
        "App usage" to Copy("استخدام التطبيقات", "App usage", "يفتح أداة متابعة استخدام التطبيقات.", "Opens the app usage tool."),
        "Daily focus reminder" to Copy("تذكير التركيز اليومي", "Daily focus reminder", "تذكير يومي الساعة التاسعة صباحًا.", "A daily reminder at 09:00."),
        "Charging sequence" to Copy("تسلسل عند الشحن", "Charging sequence", "انتظار قصير ثم إشعار؛ مثال على التنفيذ المتتالي.", "Wait briefly, then notify: a sequential example."),
        "Parallel morning check" to Copy("فحص صباحي متوازٍ", "Parallel morning check", "انتظاران مستقلان بالتوازي ثم إشعار عند اكتمالهما.", "Two independent waits in parallel, then a notification."),
        "PDF from images" to Copy("صور إلى PDF", "PDF from images", "يفتح أداة اختيار الصور وتحويلها محليًا إلى PDF.", "Opens the local images-to-PDF tool."),
        "Extract PDF text" to Copy("استخراج نص PDF", "Extract PDF text", "يفتح أداة استخراج النص من مستند PDF.", "Opens the PDF text extraction tool."),
        "OCR an image" to Copy("قراءة نص صورة", "OCR an image", "يفتح أداة OCR للصور.", "Opens the image OCR tool."),
        "Merge images" to Copy("دمج الصور", "Merge images", "يفتح أداة دمج الصور.", "Opens the image merging tool."),
        "ZIP selected files" to Copy("ضغط ملفات ZIP", "ZIP selected files", "يفتح أداة ضغط الملفات محليًا.", "Opens the local ZIP tool."),
        "Create QR code" to Copy("إنشاء رمز QR", "Create QR code", "يفتح أداة إنشاء رمز QR.", "Opens the QR creation tool."),
        "Telegram bot message" to Copy("رسالة بوت تيليجرام", "Telegram bot message", "أدخل رمز البوت ومعرّف المحادثة والرسالة قبل الحفظ.", "Enter the bot token, chat ID and message before saving."),
    )

    fun title(template: AutomationRoutine, ar: Boolean): String =
        copy[template.name]?.let { if (ar) it.ar else it.en } ?: template.name

    fun description(template: AutomationRoutine, ar: Boolean): String =
        copy[template.name]?.let { if (ar) it.detailsAr else it.detailsEn }.orEmpty()

    fun readiness(template: AutomationRoutine): Readiness =
        if (template.isValid() && RoutineReview.inspect(template).canRun) Readiness.READY
        else Readiness.NEEDS_SETUP

    private fun tool(name: String, id: ToolId) = AutomationRoutine(
        name = name,
        trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
        actions = listOf(RoutineAction(RoutineActionType.OPEN_TOOL, id.name)),
    )

    fun templates(): List<AutomationRoutine> = listOf(
        AutomationRoutine(
            name = "Morning apps",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "07:30"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_APP, "")),
        ),
        AutomationRoutine(
            name = "Commute maps",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "08:00"),
            actions = listOf(RoutineAction(RoutineActionType.OPEN_MAPS, "")),
        ),
        AutomationRoutine(
            name = "Charging reminder",
            trigger = RoutineTrigger(RoutineTriggerType.CHARGER_CONNECTED),
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Charging started")),
        ),
        AutomationRoutine(
            name = "Low battery",
            trigger = RoutineTrigger(RoutineTriggerType.BATTERY_BELOW, "20"),
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Battery below 20%")),
        ),
        AutomationRoutine(
            name = "WhatsApp prepared message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.PREPARE_WHATSAPP, "", "")),
        ),
        AutomationRoutine(
            name = "Telegram prepared message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.PREPARE_TELEGRAM, "", "")),
        ),
        AutomationRoutine(
            name = "Open app + dated screenshot",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(
                RoutineAction(
                    RoutineActionType.OPEN_APP_SCREENSHOT,
                    "",
                    "3000",
                    parameters = mapOf("stampDateTime" to "true", "persistentCapture" to "false"),
                ),
            ),
        ),
        tool("App usage", ToolId.APP_OPEN_ROUTINE),
        AutomationRoutine(
            name = "Daily focus reminder",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "09:00"),
            actions = listOf(RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Review today's tasks")),
        ),
        AutomationRoutine(
            name = "Charging sequence",
            trigger = RoutineTrigger(RoutineTriggerType.CHARGER_CONNECTED),
            actions = listOf(
                RoutineAction(RoutineActionType.WAIT, "1000"),
                RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Charging routine ready"),
            ),
        ),
        AutomationRoutine(
            name = "Parallel morning check",
            trigger = RoutineTrigger(RoutineTriggerType.TIME, "08:30"),
            actions = listOf(
                RoutineAction(RoutineActionType.WAIT, "1000", parameters = mapOf("parallelGroup" to "morning")),
                RoutineAction(RoutineActionType.WAIT, "1200", parameters = mapOf("parallelGroup" to "morning")),
                RoutineAction(RoutineActionType.SHOW_NOTIFICATION, "Morning checks completed"),
            ),
        ),
        tool("PDF from images", ToolId.IMAGES_TO_PDF),
        tool("Extract PDF text", ToolId.PDF_TEXT),
        tool("OCR an image", ToolId.IMAGE_OCR),
        tool("Merge images", ToolId.MERGE_IMAGES),
        tool("ZIP selected files", ToolId.ZIP_FILES),
        tool("Create QR code", ToolId.QR_CREATE),
        AutomationRoutine(
            name = "Telegram bot message",
            trigger = RoutineTrigger(RoutineTriggerType.MANUAL),
            actions = listOf(RoutineAction(RoutineActionType.SEND_TELEGRAM_BOT, "", "")),
        ),
    )
}
