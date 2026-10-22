package com.explapp.shortcut.ui

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.explapp.shortcut.automation.routines.TelegramTdlibSession

/** Explicit Telegram user-account authorization. No codes/passwords are persisted. */
class TelegramPersonalSetupActivity : AppCompatActivity() {
    private lateinit var layout: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ar = resources.configuration.locales[0].language == "ar"
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 18f }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
        label(if (ar) "تيليجرام — الحساب الشخصي (TDLib)" else "Telegram personal account (TDLib)")
        label(if (ar) "هذا تسجيل دخول منفصل عن تطبيق تيليجرام. احصل على api_id و api_hash من my.telegram.org، ولا ترسل رمز الدخول أو كلمة المرور لأي شخص." else "This is a separate Telegram login. Get api_id and api_hash from my.telegram.org; never share login codes or passwords.")
        layout.addView(status)
        inputs(ar)
        button(if (ar) "تحديث الحالة" else "Refresh status") { refresh(ar) }
        button(if (ar) "تسجيل الخروج" else "Log out") {
            TelegramTdlibSession.logout()
            refresh(ar)
        }
        refresh(ar)
    }

    private fun label(message: String) {
        layout.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(0, 4, 0, 12)
        })
    }

    private fun field(hintText: String, password: Boolean = false): EditText =
        EditText(this).also {
            it.hint = hintText
            it.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            layout.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun button(text: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun inputs(ar: Boolean) {
        val id = field("api_id")
        val hash = field("api_hash", true)
        button(if (ar) "الاتصال بتيليجرام" else "Connect Telegram") {
            val parsed = id.text.toString().toIntOrNull() ?: 0
            TelegramTdlibSession.open(applicationContext, parsed, hash.text.toString().trim())
            hash.text.clear()
            refresh(ar)
        }
        val phone = field(if (ar) "رقم الهاتف الدولي: +46..." else "Phone with country code: +46...")
        button(if (ar) "إرسال رقم الهاتف" else "Submit phone") {
            TelegramTdlibSession.providePhone(phone.text.toString())
            refresh(ar)
        }
        val code = field(if (ar) "رمز تسجيل الدخول" else "Login code")
        button(if (ar) "تأكيد الرمز" else "Confirm login code") {
            TelegramTdlibSession.provideCode(code.text.toString())
            code.text.clear()
            refresh(ar)
        }
        val password = field(if (ar) "كلمة مرور التحقق بخطوتين" else "2-step verification password", true)
        button(if (ar) "تأكيد كلمة المرور" else "Confirm password") {
            TelegramTdlibSession.providePassword(password.text.toString())
            password.text.clear()
            refresh(ar)
        }
    }

    private fun refresh(ar: Boolean) {
        val state = TelegramTdlibSession.status()
        status.text = (if (ar) "حالة الحساب: " else "Account state: ") + state +
            (TelegramTdlibSession.lastError()?.let { "\n$it" } ?: "")
    }
}
