package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MultiUrlPdfActivity : AppCompatActivity() {
    private var urls: List<String> = emptyList()
    private var index = 0
    private var browserOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        promptUrls()
    }

    override fun onResume() {
        super.onResume()
        if (browserOpened) {
            browserOpened = false
            index += 1
            showNextPrompt()
        }
    }

    private fun promptUrls() {
        val input = EditText(this).apply {
            minLines = 5
            hint = local("One URL per line", "رابط في كل سطر")
        }
        AlertDialog.Builder(this)
            .setTitle(local("Multiple URLs to PDF", "عدة روابط إلى PDF"))
            .setMessage(local(
                "Android will open each page one by one. In the browser choose Print → Save as PDF, then return to Shortcut for the next link.",
                "سيفتح Android كل رابط بالتتابع. من المتصفح اختر طباعة ← حفظ كـ PDF، ثم ارجع إلى Shortcut للرابط التالي.",
            ))
            .setView(input)
            .setPositiveButton(local("Start", "ابدأ")) { _, _ ->
                urls = input.text.toString()
                    .lineSequence()
                    .map(String::trim)
                    .filter { it.isNotBlank() }
                    .map { normalizeUrl(it) }
                    .toList()
                if (urls.isEmpty()) finish() else showNextPrompt()
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showNextPrompt() {
        if (index >= urls.size) {
            AlertDialog.Builder(this)
                .setTitle(local("Finished", "انتهت الروابط"))
                .setMessage(local("All links were opened for PDF printing.", "تم فتح جميع الروابط للطباعة إلى PDF."))
                .setPositiveButton(local("Close", "إغلاق")) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        val url = urls[index]
        AlertDialog.Builder(this)
            .setTitle(local("Link ${index + 1} of ${urls.size}", "الرابط ${index + 1} من ${urls.size}"))
            .setMessage(url)
            .setPositiveButton(local("Open to print", "فتح للطباعة")) { _, _ -> openUrl(url) }
            .setNeutralButton(local("Skip", "تخطي")) { _, _ ->
                index += 1
                showNextPrompt()
            }
            .setNegativeButton(local("Stop", "إيقاف")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun openUrl(url: String) {
        runCatching {
            browserOpened = true
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            browserOpened = false
            index += 1
            showNextPrompt()
        }
    }

    private fun normalizeUrl(value: String): String =
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) value
        else "https://$value"

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en
}
