package com.explapp.shortcut.ui

import com.explapp.shortcut.tools.ToolId

/**
 * Ready-to-run, local tool presets for the template library.
 * A preset launches an existing Shortcut tool; it does not claim to create a scheduled automation.
 * Stable IDs keep saved favorites and recents intact across application updates.
 */
internal data class TemplateToolPreset(
    val id: String,
    val tool: ToolId,
    val category: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
)

internal object TemplateToolPresets {
    val entries: List<TemplateToolPreset> = listOf(
        TemplateToolPreset("tool_images_to_pdf", ToolId.IMAGES_TO_PDF, "pdf",
            "تحويل الصور إلى PDF", "Images to PDF",
            "اختر صورًا وحوّلها إلى ملف PDF محلي", "Select images and save them as a local PDF"),
        TemplateToolPreset("tool_pdf_text", ToolId.PDF_TEXT, "pdf",
            "استخراج النص من PDF", "Extract PDF text",
            "استخرج النص القابل للنسخ من مستند PDF", "Extract selectable text from a PDF document"),
        TemplateToolPreset("tool_url_to_pdf", ToolId.URL_TO_PDF, "pdf",
            "رابط إلى PDF", "Web page to PDF",
            "افتح صفحة للطباعة والحفظ كملف PDF", "Open a page for printing and saving as PDF"),
        TemplateToolPreset("tool_merge_images", ToolId.MERGE_IMAGES, "images",
            "دمج الصور", "Merge images",
            "ادمج عدة صور أفقيًا أو عموديًا", "Combine several images horizontally or vertically"),
        TemplateToolPreset("tool_image_ocr", ToolId.IMAGE_OCR, "images",
            "استخراج نص من صورة", "Image text recognition",
            "تعرّف على النص داخل صورة محددة", "Recognize text from a selected image"),
        TemplateToolPreset("tool_image_resize", ToolId.IMAGE_RESIZE_COMPRESS, "images",
            "ضغط الصور", "Compress images",
            "قلّل حجم الصور باستخدام أداة الضغط المحلية", "Reduce image size with the local compression tool"),
        TemplateToolPreset("tool_image_crop", ToolId.IMAGE_CROP, "images",
            "قص صورة", "Crop an image",
            "اختر صورة وحدد الجزء الذي تريد الاحتفاظ به", "Choose an image and select the area to keep"),
        TemplateToolPreset("tool_gif_create", ToolId.GIF_CREATE, "images",
            "إنشاء GIF", "Create a GIF",
            "أنشئ صورة متحركة من صورك", "Create an animated image from selected pictures"),
        TemplateToolPreset("tool_qr_create", ToolId.QR_CREATE, "quick",
            "إنشاء رمز QR", "Create a QR code",
            "حوّل نصًا أو رابطًا إلى رمز QR", "Turn text or a link into a QR code"),
        TemplateToolPreset("tool_zip_files", ToolId.ZIP_FILES, "files",
            "ضغط ملفات ZIP", "Create ZIP archive",
            "اجمع ملفات مختارة في أرشيف ZIP", "Package selected files in a ZIP archive"),
    )
}
