package com.explapp.shortcut.tools

enum class ToolSection { IMAGES, PDF_FILES, QUICK, AUTOMATION }

enum class ToolId {
    MERGE_IMAGES,
    IMAGES_TO_PDF,
    IMAGE_OCR,
    PDF_TEXT,
    ZIP_FILES,
    UNZIP_FILES,
    IMAGE_TO_JPEG,
    IMAGE_INFO,
    QR_CREATE,
    CLIPBOARD,
    SCREENSHOT_OCR_SEARCH,
    CAR_MODE,
    PARKED_CAR,
    BATTERY_CHARGER,
    NFC_TRIGGER,
    APP_OPEN_ROUTINE,
    MORNING_SLEEP,
    CALENDAR_REMINDERS,
    WATER_EJECT,
    GIF_CREATE,
    IMAGE_RESIZE_COMPRESS,
    IMAGE_CROP,
    LATEST_PHOTO,
    LATEST_SCREENSHOT,
    URL_TO_PDF,
    MULTI_URL_TO_PDF,
    SCREENSHOT_CAPTURE,
}

data class ShortcutTool(
    val id: ToolId,
    val section: ToolSection,
    val titleEn: String,
    val titleAr: String,
)

object ToolCatalog {
    fun all(): List<ShortcutTool> = listOf(
        ShortcutTool(ToolId.MERGE_IMAGES, ToolSection.IMAGES, "Merge images", "دمج الصور"),
        ShortcutTool(ToolId.IMAGES_TO_PDF, ToolSection.IMAGES, "Images to PDF", "الصور إلى PDF"),
        ShortcutTool(ToolId.IMAGE_OCR, ToolSection.IMAGES, "Extract text from image", "استخراج النص من الصورة"),
        ShortcutTool(ToolId.IMAGE_TO_JPEG, ToolSection.IMAGES, "Convert to JPEG", "تحويل إلى JPEG"),
        ShortcutTool(ToolId.IMAGE_INFO, ToolSection.IMAGES, "Image information", "معلومات الصورة"),
        ShortcutTool(ToolId.GIF_CREATE, ToolSection.IMAGES, "Create GIF", "إنشاء GIF"),
        ShortcutTool(ToolId.IMAGE_RESIZE_COMPRESS, ToolSection.IMAGES, "Compress / resize image", "ضغط / تغيير حجم الصورة"),
        ShortcutTool(ToolId.IMAGE_CROP, ToolSection.IMAGES, "Crop image", "قص الصورة"),
        ShortcutTool(ToolId.LATEST_PHOTO, ToolSection.IMAGES, "Share latest photo", "مشاركة آخر صورة"),
        ShortcutTool(ToolId.LATEST_SCREENSHOT, ToolSection.IMAGES, "Share latest screenshot", "مشاركة آخر لقطة شاشة"),

        ShortcutTool(ToolId.PDF_TEXT, ToolSection.PDF_FILES, "Extract text from PDF", "استخراج النص من PDF"),
        ShortcutTool(ToolId.ZIP_FILES, ToolSection.PDF_FILES, "Create ZIP", "إنشاء ZIP"),
        ShortcutTool(ToolId.UNZIP_FILES, ToolSection.PDF_FILES, "Unzip files", "فك ZIP"),
        ShortcutTool(ToolId.URL_TO_PDF, ToolSection.PDF_FILES, "Web page / URL to PDF", "حفظ صفحة أو رابط كـ PDF"),
        ShortcutTool(ToolId.MULTI_URL_TO_PDF, ToolSection.PDF_FILES, "Multiple URLs to PDFs", "عدة روابط إلى PDFs"),

        ShortcutTool(ToolId.QR_CREATE, ToolSection.QUICK, "Create QR code", "إنشاء QR Code"),
        ShortcutTool(ToolId.CLIPBOARD, ToolSection.QUICK, "Clipboard tools", "أدوات الحافظة"),
        ShortcutTool(ToolId.SCREENSHOT_CAPTURE, ToolSection.QUICK, "Take screenshot", "التقاط لقطة شاشة"),
        ShortcutTool(ToolId.SCREENSHOT_OCR_SEARCH, ToolSection.QUICK, "Screenshot → OCR / Search", "لقطة شاشة → نص / بحث"),
        ShortcutTool(ToolId.WATER_EJECT, ToolSection.QUICK, "Water Eject", "طرد الماء"),

        ShortcutTool(ToolId.CAR_MODE, ToolSection.AUTOMATION, "Car mode", "وضع السيارة"),
        ShortcutTool(ToolId.PARKED_CAR, ToolSection.AUTOMATION, "Parked car location", "موقع السيارة المركونة"),
        ShortcutTool(ToolId.BATTERY_CHARGER, ToolSection.AUTOMATION, "Battery / charger", "البطارية / الشاحن"),
        ShortcutTool(ToolId.NFC_TRIGGER, ToolSection.AUTOMATION, "NFC trigger", "تشغيل عبر NFC"),
        ShortcutTool(ToolId.APP_OPEN_ROUTINE, ToolSection.AUTOMATION, "App usage time", "وقت استخدام التطبيقات"),
        ShortcutTool(ToolId.MORNING_SLEEP, ToolSection.AUTOMATION, "Morning / sleep routine", "روتين الصباح / النوم"),
        ShortcutTool(ToolId.CALENDAR_REMINDERS, ToolSection.AUTOMATION, "Calendar reminders", "تنبيهات التقويم"),
    )
}
