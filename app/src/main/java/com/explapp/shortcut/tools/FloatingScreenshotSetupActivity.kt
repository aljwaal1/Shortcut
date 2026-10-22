package com.explapp.shortcut.tools

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
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
        fun option(label: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
        }
        fun enable(mode: String, duration: Long = 0L) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                android.widget.Toast.makeText(this, if (ar) "اسمح بالظهور فوق التطبيقات ثم اختر الوضع مجددًا" else "Allow overlay permission, then select a mode again", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(this, FloatingScreenshotService::class.java)
                .putExtra(FloatingScreenshotService.EXTRA_MODE, mode)
                .putExtra(FloatingScreenshotService.EXTRA_DURATION_MS, duration)
            startService(intent)
            finish()
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
