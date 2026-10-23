package com.explapp.shortcut.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.explapp.shortcut.automation.routines.TelegramPersonalCredentialVault
import com.explapp.shortcut.automation.routines.TelegramPersonalDeliveryPolicy
import com.explapp.shortcut.automation.routines.TelegramTdlibSession

/**
 * One step at a time. The primary action is disabled until its field is valid,
 * changes color when ready, and gives immediate progress/response feedback.
 */
class TelegramPersonalSetupActivity : AppCompatActivity() {
    private lateinit var layout: LinearLayout
    private lateinit var status: TextView
    private lateinit var heading: TextView
    private lateinit var guidance: TextView
    private lateinit var primary: Button
    private lateinit var fieldOne: EditText
    private lateinit var fieldTwo: EditText
    private lateinit var secondary: Button
    private lateinit var changeNumber: Button
    private var editingPhoneWhileWaiting = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastPhase = ""
    private var busy = false
    private var message = ""
    private var ar = false
    private val refreshLoop = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                updateScreen()
                handler.postDelayed(this, 850L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ar = resources.configuration.locales[0].language == "ar"
        val pad = (18 * resources.displayMetrics.density).toInt()
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        heading = label("", 23f, true)
        guidance = label("", 15f)
        status = label("", 14f)
        fieldOne = EditText(this).apply { setSingleLine(true) }
        fieldTwo = EditText(this).apply { setSingleLine(true) }
        layout.addView(fieldOne, wide())
        layout.addView(fieldTwo, wide())
        primary = Button(this)
        layout.addView(primary, wide())
        secondary = Button(this)
        layout.addView(secondary, wide())
        changeNumber = Button(this)
        layout.addView(changeNumber, wide())

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                stylePrimary()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        fieldOne.addTextChangedListener(watcher)
        fieldTwo.addTextChangedListener(watcher)
        primary.setOnClickListener { act() }
        secondary.setOnClickListener {
            when (lastPhase) {
                "ready" -> {
                    TelegramTdlibSession.logout()
                    message = if (ar) "تم طلب تسجيل الخروج" else "Logout requested"
                    updateScreen()
                }
                "code" -> resendCode()
            }
        }
        changeNumber.setOnClickListener {
            if (busy) return@setOnClickListener
            if (editingPhoneWhileWaiting) {
                editingPhoneWhileWaiting = false
                message = if (ar) "يمكنك إدخال الرمز الموجود أو إعادة إرساله إن سمح تيليجرام."
                else "You can enter the received code or resend if Telegram permits it."
            } else {
                editingPhoneWhileWaiting = true
                message = if (ar) "أدخل الرقم مجددًا مع مفتاح الدولة. هذا لا يحذف الأتمتات أو إعدادات التطبيق."
                else "Re-enter your number with country code. Your automations and app settings are not deleted."
            }
            updateScreen()
        }
        // Do not request phone/code unless Telegram actually asks for them.
        if (TelegramPersonalCredentialVault.load(applicationContext) != null) {
            Thread { TelegramTdlibSession.reconnect(applicationContext) }.start()
        }
        updateScreen()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshLoop)
        handler.post(refreshLoop)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    private fun wide() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun label(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).also {
            it.text = value
            it.textSize = size
            if (bold) it.setTypeface(null, Typeface.BOLD)
            it.setPadding(0, 10, 0, 12)
            layout.addView(it, wide())
        }

    private fun phase(): String {
        val tdState = TelegramTdlibSession.status()
        if (editingPhoneWhileWaiting &&
            tdState in setOf("AuthorizationStateWaitCode", "AuthorizationStateWaitEmailCode",
                "AuthorizationStateWaitPassword", "AuthorizationStateWaitPhoneNumber")) {
            return "phone"
        }
        if (tdState == "AuthorizationStateReady") editingPhoneWhileWaiting = false
        return when (tdState) {
        "AuthorizationStateReady" -> "ready"
        "AuthorizationStateWaitPhoneNumber" -> "phone"
        "AuthorizationStateWaitCode", "AuthorizationStateWaitEmailCode" -> "code"
        "AuthorizationStateWaitPassword" -> "password"
        "AuthorizationStateWaitEmailAddress" -> "email"
        "AuthorizationStateWaitOtherDeviceConfirmation" -> "other_device"
        else -> if (TelegramPersonalCredentialVault.load(applicationContext) == null) "api" else "connecting"
        }
    }

    private fun updateScreen() {
        val state = phase()
        if (state != lastPhase) {
            lastPhase = state
            busy = false
            fieldOne.text.clear()
            fieldTwo.text.clear()
            // Keep useful guidance when the user explicitly changes login mode.
        }
        heading.text = when (state) {
            "api" -> if (ar) "١. إعداد اتصال تيليجرام" else "1. Configure Telegram"
            "phone" -> if (ar) "٢. رقم هاتفك" else "2. Your phone number"
            "code" -> if (ar) "٣. رمز التحقق" else "3. Verification code"
            "password" -> if (ar) "التحقق بخطوتين" else "Two-step verification"
            "email" -> if (ar) "البريد الإلكتروني" else "Email address"
            "ready" -> if (ar) "تم ربط حسابك ✓" else "Account connected ✓"
            "other_device" -> if (ar) "تأكيد من الجهاز الآخر" else "Confirm on another device"
            else -> if (ar) "جارٍ الاتصال بتيليجرام…" else "Connecting to Telegram…"
        }
        guidance.text = when (state) {
            "api" -> if (ar) "إعداد لمرة واحدة: أدخل api_id و api_hash من my.telegram.org/apps. لن تحتاج إليهما بعد حفظ الاتصال." else "One-time setup: enter api_id and api_hash from my.telegram.org/apps."
            "phone" -> if (ar) "أدخل رقم تيليجرام بمفتاح الدولة، ثم اضغط الزر الأزرق." else "Enter your Telegram phone number with country code, then tap the blue button."
            "code" -> {
                val delivery = TelegramTdlibSession.currentCodeDelivery().orEmpty()
                val next = TelegramTdlibSession.nextCodeDelivery()
                if (ar) "أدخل رمز الدخول الذي أرسله تيليجرام. افحص محادثة Telegram الرسمية داخل حساباتك المفتوحة على الأجهزة الأخرى. وسيلة الإرسال: $delivery." +
                    (next?.let { " الوسيلة التالية: $it." } ?: " لا تتوفر وسيلة ثانية حاليًا.")
                else "Enter the Telegram login code. Check the official Telegram chat on your other logged-in devices. Current method: $delivery." +
                    (next?.let { " Next method: $it." } ?: " No alternate delivery method is currently available.")
            }
            "password" -> if (ar) "أدخل كلمة مرور التحقق بخطوتين لحسابك." else "Enter your Telegram two-step verification password."
            "ready" -> if (ar) "تم الربط. أضف @username من شاشة إعداد الأتمتة، ولا يلزم تسجيل الدخول مرة أخرى عادةً." else "Connected. Choose @username in the automation editor."
            "other_device" -> if (ar) "افتح تيليجرام على الجهاز الآخر وأكّد طلب تسجيل الدخول." else "Open Telegram on your other device and approve the login."
            "email" -> if (ar) "أدخل البريد الذي يطلبه تيليجرام." else "Enter the email requested by Telegram."
            else -> if (ar) "يجري تهيئة جلسة تيليجرام. تظهر الخطوة المطلوبة تلقائيًا." else "Initializing Telegram session. The next step appears automatically."
        }
        fieldOne.visibility = if (state in setOf("api", "phone", "code", "password", "email")) View.VISIBLE else View.GONE
        fieldTwo.visibility = if (state == "api") View.VISIBLE else View.GONE
        fieldOne.hint = when (state) {
            "api" -> "api_id"
            "phone" -> if (ar) "رقم الهاتف: +46…" else "Phone number: +46…"
            "code" -> if (ar) "رمز التحقق" else "Verification code"
            "password" -> if (ar) "كلمة المرور" else "Password"
            "email" -> "Email"
            else -> ""
        }
        fieldTwo.hint = "api_hash"
        fieldOne.inputType = when (state) {
            "api" -> InputType.TYPE_CLASS_NUMBER
            "phone" -> InputType.TYPE_CLASS_PHONE
            "code" -> InputType.TYPE_CLASS_NUMBER
            "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            else -> InputType.TYPE_CLASS_TEXT
        }
        fieldTwo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        primary.text = if (busy) {
            if (ar) "جارٍ التنفيذ…" else "Working…"
        } else when (state) {
            "api" -> if (ar) "ربط تيليجرام ←" else "Connect Telegram →"
            "phone" -> if (ar) "إرسال الرقم ←" else "Submit phone →"
            "code" -> if (ar) "تأكيد الرمز ←" else "Verify code →"
            "password" -> if (ar) "تأكيد كلمة المرور ←" else "Verify password →"
            "email" -> if (ar) "إرسال البريد ←" else "Submit email →"
            else -> if (ar) "بانتظار تيليجرام…" else "Waiting for Telegram…"
        }
        primary.visibility = if (state in setOf("api", "phone", "code", "password", "email")) View.VISIBLE else View.GONE
        changeNumber.visibility = if (!editingPhoneWhileWaiting &&
            TelegramTdlibSession.status() in setOf(
                "AuthorizationStateWaitCode", "AuthorizationStateWaitEmailCode",
                "AuthorizationStateWaitPassword")) View.VISIBLE
            else if (editingPhoneWhileWaiting) View.VISIBLE else View.GONE
        changeNumber.isEnabled = !busy
        changeNumber.text = if (editingPhoneWhileWaiting) {
            if (ar) "الرجوع إلى رمز التحقق" else "Back to verification code"
        } else {
            if (ar) "تغيير الرقم / بدء تسجيل الدخول من جديد" else "Change number / start again"
        }
        secondary.visibility = if (state == "ready" || state == "code") View.VISIBLE else View.GONE
        if (state == "code") {
            val seconds = TelegramTdlibSession.codeResendRemainingSeconds()
            secondary.isEnabled = !busy && TelegramTdlibSession.canResendCode()
            secondary.text = when {
                seconds > 0 -> if (ar) "إعادة إرسال الرمز بعد ${seconds} ث" else "Resend code in ${seconds}s"
                !TelegramTdlibSession.canResendCode() -> if (ar) "إعادة الإرسال غير متاحة حاليًا" else "Resend unavailable right now"
                else -> if (ar) "إعادة إرسال رمز الدخول" else "Resend login code"
            }
        } else {
            secondary.isEnabled = !busy
            secondary.text = if (ar) "تسجيل الخروج" else "Log out"
        }
        status.text = (if (ar) "الحالة: " else "Status: ") + TelegramTdlibSession.status() +
            (TelegramTdlibSession.lastError()?.let { "\n$it" } ?: "") +
            (if (message.isNotBlank()) "\n$message" else "")
        stylePrimary()
    }

    private fun valid(): Boolean {
        val value = fieldOne.text.toString().trim()
        return when (lastPhase) {
            "api" -> (value.toIntOrNull() ?: 0) > 0 && fieldTwo.text.toString().trim().length >= 16
            "phone" -> value.matches(Regex("^\\+[0-9]{7,15}$"))
            "code" -> value.length in 3..12
            "password" -> value.isNotBlank()
            "email" -> android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()
            else -> false
        }
    }

    private fun stylePrimary() {
        if (!::primary.isInitialized) return
        val ready = valid() && !busy
        primary.isEnabled = ready
        primary.alpha = if (ready) 1f else 0.45f
        primary.setBackgroundColor(if (ready) Color.rgb(45, 91, 218) else Color.rgb(158, 165, 178))
        primary.setTextColor(Color.WHITE)
        primary.contentDescription = if (ready) {
            if (ar) "المدخلات جاهزة، اضغط للمتابعة" else "Ready: tap to continue"
        } else {
            if (ar) "أكمل الإدخال لتفعيل الزر" else "Complete the field to enable the button"
        }
    }

    private fun resendCode() {
        if (busy || lastPhase != "code" || !TelegramTdlibSession.canResendCode()) return
        busy = true
        message = if (ar) "جارٍ طلب رمز جديد من تيليجرام…" else "Requesting another code from Telegram…"
        updateScreen()
        Thread {
            val ok = TelegramTdlibSession.resendAuthenticationCode()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                message = if (ok) {
                    if (ar) "وافق تيليجرام على طلب إعادة الإرسال. تحقق من طريقة الوصول الموضحة أعلاه."
                    else "Telegram accepted the resend request. Check the delivery method shown above."
                } else {
                    if (ar) "لم يؤكد تيليجرام إرسال رمز جديد. راجع رسالة الخطأ والمهلة."
                    else "Telegram did not confirm a new code. Check the error and timeout."
                }
                updateScreen()
            }
        }.start()
    }

    private fun act() {
        if (busy || !valid()) return
        val state = lastPhase
        val one = fieldOne.text.toString().trim()
        val two = fieldTwo.text.toString().trim()
        busy = true
        message = if (ar) "جارٍ إرسال الطلب…" else "Submitting…"
        updateScreen()
        // TDLib initialization can load native libraries; never block the UI.
        Thread {
            val ok = when (state) {
                "api" -> TelegramTdlibSession.open(applicationContext, one.toInt(), two)
                "phone" -> TelegramTdlibSession.requestPhoneNumber(one)
                "code" -> TelegramTdlibSession.provideCode(one)
                "password" -> TelegramTdlibSession.providePassword(one)
                "email" -> false // TDLib email authorization varies; do not fake success.
                else -> false
            }
            runOnUiThread {
                busy = false
                if (state == "api" && ok) fieldTwo.text.clear()
                if (state == "phone" && ok) editingPhoneWhileWaiting = false
                if (state in setOf("code", "password")) fieldOne.text.clear()
                message = if (ok) {
                    if (ar) "تم إرسال الطلب. بانتظار تأكيد تيليجرام…" else "Request sent. Awaiting Telegram response…"
                } else {
                    if (ar) "تعذر إرسال الطلب. راجع البيانات والحالة." else "Could not submit. Check details and status."
                }
                updateScreen()
            }
        }.start()
    }
}
