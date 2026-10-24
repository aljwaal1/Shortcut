package com.explapp.shortcut.tools

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView

class FloatingScreenshotSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ar = resources.configuration.locales[0].language == "ar"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = if (ar) "زر لقطة شاشة العائم" else "Floating screenshot button"
            textSize = 22f
        })
        layout.addView(TextView(this).apply {
            text = if (ar) "يحتاج الزر العائم إلى إذن الظهور فوق التطبيقات. قد يمنع Android هذا الإذن لبعض التطبيقات؛ يمكنك استخدام لقطة شاشة عادية دون زر عائم." else "The floating button requires display-over-apps permission. Android may restrict this permission; you can still use a regular screenshot without the overlay."
            textSize = 15f
            setPadding(0, 20, 0, 20)
        })
        fun option(label: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
        }
        fun enable(mode: String, duration: Long = 0L) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, if (ar) "إذن الظهور فوق التطبيقات غير متاح حاليًا. إذا رفضه Android يمكنك استخدام التقاط الشاشة العادي." else "Overlay access is not available. If Android denies it, use regular screenshot capture instead.", Toast.LENGTH_LONG).show()
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                return
            }
            val intent = Intent(this, FloatingScreenshotService::class.java)
                .putExtra(FloatingScreenshotService.EXTRA_MODE, mode)
                .putExtra(FloatingScreenshotService.EXTRA_DURATION_MS, duration)
            startService(intent)
            finish()
        }
        option(if (ar) "لقطة شاشة بدون زر عائم" else "Screenshot without floating button") {
            startActivity(Intent(this, ScreenCaptureActivity::class.java))
        }
        option(if (ar) "دائم" else "Always") { enable("always") }
        option(if (ar) "مؤقت — 5 دقائق" else "Temporary — 5 minutes") { enable("temporary", 300_000L) }
        option(if (ar) "مؤقت — 30 دقيقة" else "Temporary — 30 minutes") { enable("temporary", 1_800_000L) }
        option(if (ar) "مؤقت — ساعة" else "Temporary — 1 hour") { enable("temporary", 3_600_000L) }
        option(if (ar) "إلغاء تفعيل الزر العائم" else "Disable floating button") {
            startService(Intent(this, FloatingScreenshotService::class.java).setAction(FloatingScreenshotService.ACTION_OFF))
            finish()
        }
        setContentView(layout)
    }
}
