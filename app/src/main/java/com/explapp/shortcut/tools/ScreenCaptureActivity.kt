package com.explapp.shortcut.tools

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.IBinder
import android.os.HandlerThread
import android.os.SystemClock
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.explapp.shortcut.automation.routines.TelegramBotSender
import com.explapp.shortcut.automation.routines.TelegramTdlibSession
import com.explapp.shortcut.automation.routines.TelegramPersonalDeliveryCoordinator
import com.explapp.shortcut.automation.routines.PersonalPhotoRequest
import com.explapp.shortcut.automation.routines.PersonalDeliveryResult
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionResult
import com.explapp.shortcut.execution.TaskExecutionStatus
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureActivity : AppCompatActivity() {
    private var runOcr = false
    private var launchPackage: String? = null
    private var captureDelayMs: Long = DEFAULT_CAPTURE_DELAY_MS
    private var telegramBotToken: String = ""
    private var telegramChatId: String = ""
    private var telegramPersonalUsername: String = ""
    private var telegramCaption: String = ""
    private var normalTelegramShare: Boolean = false
    private var shareAnyApp: Boolean = false
    private var galleryOnly: Boolean = false
    private var floatingQuickCapture = false
    private var persistentStartOnly: Boolean = false
    private var persistentResumeCapture: Boolean = false
    private var stampDateTime: Boolean = false
    private var stampName: String = ""
    private var routineId: String = ""
    private var routineName: String = ""
    private var routineStartedAtMs: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val path = intent.getStringExtra(ScreenCaptureService.EXTRA_PATH)
            if (path.isNullOrBlank()) {
                reportRoutineScreenshot(
                    success = false,
                    reason = "Screen capture returned no image",
                    details = listOf(
                        "SCREENSHOT_CAPTURE = FAILURE",
                        "DateTimeStamp = $stampDateTime",
                        "ShareAnyApp = $shareAnyApp",
                    ),
                )
                Toast.makeText(this@ScreenCaptureActivity, local("Could not capture screenshot", "تعذر التقاط الشاشة"), Toast.LENGTH_LONG).show()
                if (floatingQuickCapture) restoreFloatingButton()
                finish()
                return
            }
            val file = File(path)
            if (floatingQuickCapture) restoreFloatingButton()
            if (runOcr) ocr(file) else saveScreenshot(file)
        }
    }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (floatingQuickCapture && (result.resultCode != Activity.RESULT_OK || data == null)) {
            restoreFloatingButton()
        }
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            reportRoutineScreenshot(
                success = false,
                reason = "Screen capture permission was not granted",
                details = listOf(
                    "SCREEN_CAPTURE_PERMISSION = DENIED",
                    "DateTimeStamp = $stampDateTime",
                ),
            )
            finish()
            return@registerForActivityResult
        }
        if (persistentStartOnly) {
            val service = Intent(this, PersistentScreenCaptureService::class.java)
                .setAction(PersistentScreenCaptureService.ACTION_START_SESSION)
                .putExtra(PersistentScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(PersistentScreenCaptureService.EXTRA_DATA, data)
            // Resume is carried by the SAME start command, eliminating the
            // unreliable fixed 400ms race with MediaProjection initialization.
            if (persistentResumeCapture && !launchPackage.isNullOrBlank()) {
                service.putExtra(PersistentScreenCaptureService.EXTRA_RESUME_AFTER_CONSENT, true)
                    .putExtra(PersistentScreenCaptureService.EXTRA_LAUNCH_PACKAGE, launchPackage)
                    .putExtra(PersistentScreenCaptureService.EXTRA_CAPTURE_DELAY_MS, captureDelayMs)
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_BOT_TOKEN, telegramBotToken)
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_CHAT_ID, telegramChatId)
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_PERSONAL_USERNAME, telegramPersonalUsername)
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_CAPTION, telegramCaption)
                    .putExtra(PersistentScreenCaptureService.EXTRA_NORMAL_TELEGRAM_SHARE, normalTelegramShare)
                    .putExtra(PersistentScreenCaptureService.EXTRA_SHARE_ANY_APP, shareAnyApp)
                    .putExtra(PersistentScreenCaptureService.EXTRA_STAMP_DATE_TIME, stampDateTime)
                    .putExtra(PersistentScreenCaptureService.EXTRA_ROUTINE_ID, routineId)
                    .putExtra(PersistentScreenCaptureService.EXTRA_ROUTINE_NAME, routineName)
            }
            ContextCompat.startForegroundService(this, service)
            Toast.makeText(
                this,
                if (persistentResumeCapture) {
                    local("Capture approved; resuming the pending automation", "تمت الموافقة؛ جارٍ استئناف الأتمتة المعلقة")
                } else {
                    local("Persistent screen-capture session started", "تم تشغيل جلسة تصوير الشاشة المستمرة")
                },
                Toast.LENGTH_LONG,
            ).show()
            finish()
        } else {
            val packageNameToOpen = launchPackage
            val handleResultInService = !runOcr && !packageNameToOpen.isNullOrBlank()
            val service = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                .putExtra(ScreenCaptureService.EXTRA_CAPTURE_DELAY_MS, captureDelayMs)
                .putExtra(ScreenCaptureService.EXTRA_HANDLE_RESULT_IN_SERVICE, handleResultInService)
                .putExtra(ScreenCaptureService.EXTRA_TELEGRAM_BOT_TOKEN, telegramBotToken)
                .putExtra(ScreenCaptureService.EXTRA_TELEGRAM_CHAT_ID, telegramChatId)
                .putExtra(ScreenCaptureService.EXTRA_TELEGRAM_PERSONAL_USERNAME, telegramPersonalUsername)
                .putExtra(ScreenCaptureService.EXTRA_TELEGRAM_CAPTION, telegramCaption)
                .putExtra(ScreenCaptureService.EXTRA_NORMAL_TELEGRAM_SHARE, normalTelegramShare)
                .putExtra(ScreenCaptureService.EXTRA_SHARE_ANY_APP, shareAnyApp)
                .putExtra(ScreenCaptureService.EXTRA_STAMP_DATE_TIME, stampDateTime)
                .putExtra(ScreenCaptureService.EXTRA_STAMP_NAME, stampName)
                .putExtra(ScreenCaptureService.EXTRA_GALLERY_ONLY, galleryOnly)
                .putExtra(ScreenCaptureService.EXTRA_ROUTINE_ID, routineId)
                .putExtra(ScreenCaptureService.EXTRA_ROUTINE_NAME, routineName)
                .putExtra(ScreenCaptureService.EXTRA_ROUTINE_STARTED_AT_MS, routineStartedAtMs)
            ContextCompat.startForegroundService(this, service)

            if (!packageNameToOpen.isNullOrBlank()) {
                val target = packageManager.getLaunchIntentForPackage(packageNameToOpen)
                if (target != null) {
                    // Launch from this visible activity so Android background-start restrictions do not block it.
                    // Do not keep this transparent/blank consent activity on top of the target app.
                    startActivity(target)
                    if (handleResultInService) finish()
                } else {
                    Toast.makeText(this, local("Target app is unavailable", "التطبيق المطلوب غير متاح"), Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                moveTaskToBack(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        floatingQuickCapture = intent.getBooleanExtra(EXTRA_FLOATING_QUICK_CAPTURE, false)
        runOcr = intent.getBooleanExtra(EXTRA_OCR, false)
        launchPackage = intent.getStringExtra(EXTRA_LAUNCH_PACKAGE)?.takeIf { it.isNotBlank() }
        captureDelayMs = intent.getLongExtra(EXTRA_CAPTURE_DELAY_MS, DEFAULT_CAPTURE_DELAY_MS)
            .coerceIn(500L, 10_000L)
        telegramBotToken = intent.getStringExtra(EXTRA_TELEGRAM_BOT_TOKEN).orEmpty()
        telegramChatId = intent.getStringExtra(EXTRA_TELEGRAM_CHAT_ID).orEmpty()
        telegramPersonalUsername = intent.getStringExtra(EXTRA_TELEGRAM_PERSONAL_USERNAME).orEmpty()
        if (telegramPersonalUsername.isNotBlank()) TelegramTdlibSession.reconnect(applicationContext)
        telegramCaption = intent.getStringExtra(EXTRA_TELEGRAM_CAPTION).orEmpty()
        normalTelegramShare = intent.getBooleanExtra(EXTRA_NORMAL_TELEGRAM_SHARE, false)
        shareAnyApp = intent.getBooleanExtra(EXTRA_SHARE_ANY_APP, false)
        persistentStartOnly = intent.getBooleanExtra(EXTRA_PERSISTENT_START_ONLY, false)
        persistentResumeCapture = intent.getBooleanExtra(EXTRA_PERSISTENT_RESUME_CAPTURE, false)
        stampDateTime = intent.getBooleanExtra(EXTRA_STAMP_DATE_TIME, false)
        stampName = intent.getStringExtra(EXTRA_STAMP_NAME).orEmpty()
        routineId = intent.getStringExtra(EXTRA_ROUTINE_ID).orEmpty()
        routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME).orEmpty()
        routineStartedAtMs = System.currentTimeMillis()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ScreenCaptureService.ACTION_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (floatingQuickCapture) {
            showFloatingCaptureChoices()
        } else showCaptureInstructions()
    }

    private fun restoreFloatingButton(delayMs: Long = 0L) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                startService(Intent(this, FloatingScreenshotService::class.java)
                    .setAction(FloatingScreenshotService.ACTION_SHOW_BUBBLE))
            }
        }, delayMs)
    }

    private fun showFloatingCaptureChoices() {
        val options = arrayOf(
            local("Save to gallery", "حفظ في المعرض"),
            local("Share with an app", "مشاركة مع تطبيق"),
            local("Share in Telegram", "مشاركة عبر تيليجرام"),
        )
        AlertDialog.Builder(this)
            .setTitle(local("Screenshot: choose an action", "لقطة شاشة: اختر الإجراء"))
            .setItems(options) { _, selected ->
                galleryOnly = selected == 0
                shareAnyApp = selected == 1
                normalTelegramShare = selected == 2
                // All floating-button destinations receive the same timestamped image.
                stampDateTime = true
                captureDelayMs = 1_500L
                if (PersistentScreenCaptureService.isSessionActive(this)) {
                    val capture = Intent(this, PersistentScreenCaptureService::class.java)
                        .setAction(PersistentScreenCaptureService.ACTION_CAPTURE)
                        .putExtra(PersistentScreenCaptureService.EXTRA_SKIP_APP_LAUNCH, true)
                        .putExtra(PersistentScreenCaptureService.EXTRA_CAPTURE_DELAY_MS, 1_000L)
                        .putExtra(PersistentScreenCaptureService.EXTRA_SHARE_ANY_APP, shareAnyApp)
                        .putExtra(PersistentScreenCaptureService.EXTRA_NORMAL_TELEGRAM_SHARE, normalTelegramShare)
                        .putExtra(PersistentScreenCaptureService.EXTRA_STAMP_DATE_TIME, stampDateTime)
                        .putExtra(PersistentScreenCaptureService.EXTRA_FLOATING_CAPTURE, true)
                    runCatching { startService(capture) }
                        .onFailure {
                            restoreFloatingButton()
                            Toast.makeText(this, local("Could not start capture", "تعذر بدء التصوير"), Toast.LENGTH_LONG).show()
                        }
                    // The persistent service restores the overlay after the actual result.
                    finish()
                } else {
                    launchConsent()
                }
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ ->
                restoreFloatingButton()
                finish()
            }
            .setOnCancelListener {
                restoreFloatingButton()
                finish()
            }
            .show()
    }

    private fun showCaptureInstructions() {
        if (persistentStartOnly) {
            AlertDialog.Builder(this)
                .setTitle(local("Keep screen-capture session active", "إبقاء جلسة تصوير الشاشة نشطة"))
                .setMessage(
                    local(
                        "Approve screen capture once. Shortcut will keep one foreground capture session active and can reuse it for scheduled screenshots until you stop the session, Android stops it, or the phone restarts.",
                        "وافق على تصوير الشاشة مرة واحدة. سيبقي التطبيق جلسة تصوير أمامية واحدة نشطة ويمكنه استخدامها للصور المجدولة حتى توقف الجلسة، أو يوقفها أندرويد، أو تعيد تشغيل الهاتف.",
                    ),
                )
                .setPositiveButton(local("Start session", "تشغيل الجلسة")) { _, _ -> launchConsent() }
                .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        val packageNameToOpen = launchPackage
        if (!packageNameToOpen.isNullOrBlank()) {
            val appLabel = runCatching {
                val info = packageManager.getApplicationInfo(packageNameToOpen, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageNameToOpen)

            // The delivery choice is made before requesting the system capture permission.
            // Automatic delivery is available only for a configured Telegram bot; ordinary
            // messaging apps cannot be made to send a message without their user consent.
            val hasBot = telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()
            var deliveryMode = if (hasBot && !shareAnyApp && !normalTelegramShare) 0 else 1
            val density = resources.displayMetrics.density
            fun dp(value: Int) = (value * density + 0.5f).toInt()
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(10), dp(20), dp(6))
            }
            val explanation = TextView(this).apply {
                text = local(
                    "Approve full-screen capture in Android. $appLabel will open, then a dated screenshot will be taken after ${captureDelayMs / 1000.0} seconds. Choose how to deliver it:",
                    "اسمح بتصوير الشاشة بالكامل من أندرويد. سيفتح $appLabel، ثم تُلتقط الصورة بعد ${captureDelayMs / 1000.0} ثانية مع ختم التاريخ والوقت. اختر طريقة إرسالها:",
                )
                textSize = 14f
                setPadding(0, 0, 0, dp(12))
            }
            content.addView(explanation)
            fun deliveryCard(title: String, description: String): TextView = TextView(this).apply {
                text = "$title\\n$description"
                textSize = 15f
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isClickable = true
                isFocusable = true
                val params = LinearLayout.LayoutParams(-1, -2)
                params.bottomMargin = dp(10)
                content.addView(this, params)
            }
            val autoCard = deliveryCard(
                local("1. Automatic sending", "١. إرسال تلقائي"),
                local("Send by configured Telegram bot, without opening the share sheet.", "إرسال عبر بوت تيليجرام المهيأ، دون فتح قائمة المشاركة."),
            )
            val manualCard = deliveryCard(
                local("2. Open another app", "٢. فتح تطبيق آخر"),
                local("Open the share sheet with the dated screenshot attached; you confirm Send.", "فتح تطبيق آخر والصورة المختومة مرفقة؛ تؤكد الإرسال بنفسك."),
            )
            val galleryCard = deliveryCard(
                local("3. Save to gallery", "٣. الحفظ في الاستديو"),
                local("Save the dated screenshot to Pictures/Shortcut Screenshots.", "حفظ الصورة المختومة في مجلد Shortcut Screenshots داخل الاستديو."),
            )
            fun repaint() {
                listOf(autoCard, manualCard, galleryCard).forEachIndexed { index, card ->
                    val selected = index == deliveryMode
                    card.background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(if (selected) 0x193F8FCE else 0x12000000)
                        setStroke(dp(if (selected) 2 else 1), if (selected) 0xFF277DC0.toInt() else 0xFF999999.toInt())
                    }
                    card.alpha = if (index == 0 && !hasBot) 0.65f else 1f
                }
            }
            autoCard.setOnClickListener {
                if (!hasBot) {
                    Toast.makeText(
                        this,
                        local("Set Telegram bot token and chat ID in the routine to enable automatic sending", "أدخل رمز بوت تيليجرام ومعرّف المحادثة في الاختصار لتفعيل الإرسال التلقائي"),
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    deliveryMode = 0
                    repaint()
                }
            }
            manualCard.setOnClickListener { deliveryMode = 1; repaint() }
            galleryCard.setOnClickListener { deliveryMode = 2; repaint() }
            repaint()
            val stampInput = EditText(this).apply {
                hint = local("Optional name (blank = date only)", "الاسم (اختياري؛ اتركه فارغًا للتاريخ فقط)")
                setSingleLine(true)
                setText(stampName)
            }
            content.addView(stampInput)
            AlertDialog.Builder(this)
                .setTitle(local("Screenshot delivery", "طريقة مشاركة لقطة الشاشة"))
                .setView(content)
                .setPositiveButton(local("Allow and capture", "السماح والتقاط الشاشة")) { _, _ ->
                    stampDateTime = true
                    stampName = stampInput.text.toString().trim().take(80)
                    galleryOnly = deliveryMode == 2
                    if (deliveryMode == 0 && hasBot) {
                        shareAnyApp = false
                        normalTelegramShare = false
                    } else {
                        telegramBotToken = ""
                        telegramChatId = ""
                        normalTelegramShare = false
                        shareAnyApp = deliveryMode == 1
                    }
                    if (galleryOnly && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) galleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else launchConsent()
                }
                .setNeutralButton(local("App settings", "إعدادات التطبيق")) { _, _ ->
                    runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:$packageName")))
                    }.onFailure {
                        Toast.makeText(this, local("Could not open app settings", "تعذر فتح إعدادات التطبيق"), Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
                .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        val choices = arrayOf(
            local("3 seconds", "3 ثوانٍ"),
            local("5 seconds", "5 ثوانٍ"),
            local("10 seconds", "10 ثوانٍ"),
        )
        val delays = longArrayOf(3_000L, 5_000L, 10_000L)
        var selected = 0
        captureDelayMs = delays[selected]

        AlertDialog.Builder(this)
            .setTitle(local("Take one screenshot", "التقاط لقطة شاشة واحدة"))
            .setMessage(
                local(
                    "What happens:\n1. Android asks for screen-capture permission.\n2. Shortcut moves to the background.\n3. You open the screen you want to capture.\n4. After the selected delay, ONE screenshot of the entire visible screen is taken.\n5. Capture stops automatically and the result opens.\n\nChoose how much time you need to reach the screen:",
                    "ماذا سيحدث:\n1. سيطلب Android موافقة تصوير الشاشة.\n2. سينتقل Shortcut إلى الخلفية.\n3. افتح الشاشة التي تريد تصويرها.\n4. بعد المدة التي تختارها ستؤخذ لقطة واحدة للشاشة الظاهرة بالكامل.\n5. يتوقف التصوير تلقائيًا وتظهر النتيجة.\n\nاختر الوقت الذي تحتاجه للوصول إلى الشاشة:",
                ),
            )
            .setSingleChoiceItems(choices, selected) { _, which ->
                selected = which
                captureDelayMs = delays[which]
            }
            .setPositiveButton(local("Start", "ابدأ")) { _, _ -> launchConsent() }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private val galleryPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) launchConsent() else {
            Toast.makeText(this, local("Storage permission required", "يلزم إذن التخزين للحفظ في الاستديو"), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun launchConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        // Android 14+ defaults to a single-app selector. This routine launches another
        // application after consent, so capturing Shortcut's own window produces blanks.
        // Ask Android for the whole display instead; the system still requires consent.
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(
                android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay(),
            )
        } else {
            manager.createScreenCaptureIntent()
        }
        consent.launch(request)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun saveScreenshot(file: File) {
        runCatching {
            val uri = ToolOutputStore(this).create(
                ScreenshotStamp.fileName(),
                "image/png",
                true,
            )
            contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output)
                file.inputStream().use { it.copyTo(output) }
            }
            uri
        }.onSuccess { uri ->
            val baseDetails = listOf(
                "SCREENSHOT_SAVE = SUCCESS",
                "Uri = $uri",
                "DateTimeStamp = $stampDateTime",
                "ShareAnyApp = $shareAnyApp",
            )
            if (telegramPersonalUsername.isNotBlank()) {
                Thread {
                    val result = TelegramPersonalDeliveryCoordinator(TelegramTdlibSession).deliver(
                        PersonalPhotoRequest(
                            requestId = "quick-" + System.currentTimeMillis(),
                            username = telegramPersonalUsername,
                            file = file,
                            caption = telegramCaption,
                        ),
                    )
                    when (result) {
                        PersonalDeliveryResult.Confirmed -> reportRoutineScreenshot(true, null, baseDetails + "PERSONAL_TELEGRAM = CONFIRMED")
                        is PersonalDeliveryResult.Uncertain -> reportRoutineScreenshotPrepared(baseDetails + "PERSONAL_TELEGRAM = UNCERTAIN: ${result.reason}")
                        else -> reportRoutineScreenshot(false, result.toString(), baseDetails + "PERSONAL_TELEGRAM = FAILURE")
                    }
                    file.delete()
                }.start()
                ToolResultActions.show(this, listOf(uri), ToolOutputResultPolicy.forTool(ToolId.SCREENSHOT_CAPTURE).mime)
            } else if (telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()) {
                Thread {
                    val result = TelegramBotSender(this@ScreenCaptureActivity).sendPhoto(telegramBotToken, telegramChatId, telegramCaption, file)
                    reportRoutineScreenshot(
                        success = result.isSuccess,
                        reason = result.exceptionOrNull()?.message,
                        details = baseDetails + ("TELEGRAM_SEND = " + if (result.isSuccess) "SUCCESS" else "FAILURE"),
                    )
                    runOnUiThread {
                        val message = if (result.isSuccess) {
                            local("Screenshot sent to Telegram", "تم إرسال لقطة الشاشة إلى تيليجرام")
                        } else {
                            val reason = result.exceptionOrNull()?.message.orEmpty()
                            local("Telegram send failed: ", "فشل الإرسال إلى تيليجرام: ") + reason
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                    file.delete()
                }.start()
                ToolResultActions.show(this, listOf(uri), ToolOutputResultPolicy.forTool(ToolId.SCREENSHOT_CAPTURE).mime)
            } else if (shareAnyApp) {
                reportRoutineScreenshotPrepared(baseDetails + "SHARE_ANY_APP = PREPARED")
                file.delete()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Shortcut screenshot", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    startActivity(Intent.createChooser(shareIntent, local("Share screenshot", "مشاركة لقطة الشاشة")))
                }.onFailure {
                    Toast.makeText(this, local("Could not open sharing", "تعذر فتح المشاركة"), Toast.LENGTH_LONG).show()
                }
                finish()
            } else if (normalTelegramShare) {
                reportRoutineScreenshotPrepared(baseDetails + "TELEGRAM_SHARE = PREPARED")
                file.delete()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (telegramCaption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, telegramCaption)
                    clipData = ClipData.newRawUri("Shortcut screenshot", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (packageManager.getLaunchIntentForPackage("org.telegram.messenger") != null) {
                        setPackage("org.telegram.messenger")
                    }
                }
                runCatching {
                    startActivity(Intent.createChooser(shareIntent, local("Choose Telegram chat", "اختر محادثة تيليجرام")))
                }.onFailure {
                    Toast.makeText(this, local("Could not open Telegram sharing", "تعذر فتح مشاركة تيليجرام"), Toast.LENGTH_LONG).show()
                }
                finish()
            } else {
                reportRoutineScreenshot(
                    success = true,
                    reason = null,
                    details = baseDetails,
                )
                file.delete()
                ToolResultActions.show(this, listOf(uri), ToolOutputResultPolicy.forTool(ToolId.SCREENSHOT_CAPTURE).mime)
            }
        }.onFailure {
            file.delete()
            reportRoutineScreenshot(
                success = false,
                reason = it.message ?: "Screenshot save failed",
                details = listOf("SCREENSHOT_SAVE = FAILURE", "DateTimeStamp = $stampDateTime"),
            )
            Toast.makeText(this, it.message ?: local("Screenshot error", "حدث خطأ أثناء التقاط الشاشة"), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun ocr(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
            finish()
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                bitmap.recycle()
                file.delete()
                deliverOcrText(result.text)
            }
            .addOnFailureListener {
                bitmap.recycle()
                file.delete()
                Toast.makeText(this, it.message ?: local("Text recognition error", "حدث خطأ أثناء التعرف على النص"), Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun deliverOcrText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut OCR", text))
        Toast.makeText(
            this,
            if (text.isBlank()) local("No text found", "لم يتم العثور على نص") else local("Text copied to clipboard", "تم نسخ النص إلى الحافظة"),
            Toast.LENGTH_LONG,
        ).show()

        val canNotify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (text.isNotBlank() && canNotify) {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(OCR_CHANNEL, local("Screenshot OCR", "نص لقطة الشاشة"), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(text.take(500))}")
            val searchIntent = Intent(Intent.ACTION_VIEW, searchUri)
            val pending = PendingIntent.getActivity(
                this,
                8842,
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, OCR_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle(local("Screenshot text copied", "تم نسخ نص لقطة الشاشة"))
                .setContentText(text.replace('\n', ' ').take(120))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .addAction(0, local("Search", "بحث"), pending)
                .build()
            manager.notify(8842, notification)
        }
        finish()
    }

    private fun reportRoutineScreenshot(
        success: Boolean,
        reason: String?,
        details: List<String>,
    ) {
        if (routineName.isBlank()) return
        val started = routineStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val taskName = "$routineName / SCREENSHOT"
        val result = if (success) {
            TaskExecutionResult.success(taskName, started, details = details)
        } else {
            TaskExecutionResult.failure(taskName, reason ?: "Screenshot failed", started, details = details)
        }
        TaskExecutionReporter(applicationContext).report(result, notifyUser = false)
    }

    private fun reportRoutineScreenshotPrepared(details: List<String>) {
        if (routineName.isBlank()) return
        val started = routineStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        TaskExecutionReporter(applicationContext).report(
            TaskExecutionResult.prepared("$routineName / SCREENSHOT", started, details = details),
            notifyUser = false,
        )
    }

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        const val EXTRA_OCR = "ocr"
        const val EXTRA_LAUNCH_PACKAGE = "launch_package"
        const val EXTRA_CAPTURE_DELAY_MS = "capture_delay_ms"
        const val EXTRA_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        const val EXTRA_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val EXTRA_TELEGRAM_PERSONAL_USERNAME = "telegram_personal_username"
        const val EXTRA_TELEGRAM_CAPTION = "telegram_caption"
        const val EXTRA_NORMAL_TELEGRAM_SHARE = "normal_telegram_share"
        const val EXTRA_SHARE_ANY_APP = "share_any_app"
        const val EXTRA_PERSISTENT_START_ONLY = "persistent_start_only"
        const val EXTRA_PERSISTENT_RESUME_CAPTURE = "persistent_resume_capture"
        const val EXTRA_STAMP_DATE_TIME = "stamp_date_time"
        const val EXTRA_STAMP_NAME = "stamp_name"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_ROUTINE_NAME = "routine_name"
        const val EXTRA_FLOATING_QUICK_CAPTURE = "floatingQuickCapture"
        private const val DEFAULT_CAPTURE_DELAY_MS = 3_000L
        private const val OCR_CHANNEL = "screen_ocr"
    }
}

class ScreenCaptureService : Service() {
    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler
    private var projection: MediaProjection? = null
    private var handleResultInService = false
    private var telegramBotToken = ""
    private var telegramChatId = ""
    private var telegramPersonalUsername = ""
    private var telegramCaption = ""
    private var normalTelegramShare = false
    private var shareAnyApp = false
    private var stampDateTime = false
    private var stampName = ""
    private var galleryOnly = false
    private var routineId = ""
    private var routineName = ""
    private var routineStartedAtMs = 0L
    private var lastFramesSeen = 0
    private var lastCaptureFailureReason = ""
    private var captureStartElapsedMs = 0L
    private var captureEncodeElapsedMs = 0L
    private var captureFirstFrameElapsedMs = -1L
    @Volatile private var captureBusy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        captureThread = HandlerThread("ShortcutOneShotCapture").apply { start() }
        captureHandler = Handler(captureThread.looper)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, local("Screen capture", "التقاط الشاشة"), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(local("Shortcut", "الاختصارات"))
            .setContentText(local("Capturing one screenshot…", "جارٍ التقاط صورة شاشة واحدة…"))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (captureBusy) {
            showResultNotification(
                local("Screenshot request ignored", "تم تجاهل طلب لقطة الشاشة"),
                local(
                    "Another screenshot is already being processed. Try again after it finishes.",
                    "توجد لقطة شاشة أخرى قيد المعالجة. حاول مرة أخرى بعد انتهائها.",
                ),
            )
            return START_NOT_STICKY
        }
        captureBusy = true
        captureStartElapsedMs = SystemClock.elapsedRealtime()
        captureEncodeElapsedMs = 0L
        captureFirstFrameElapsedMs = -1L

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            lastFramesSeen = 0
            lastCaptureFailureReason = "Screen capture permission data was unavailable"
            complete(null)
            return START_NOT_STICKY
        }
        val delayMs = (intent?.getLongExtra(EXTRA_CAPTURE_DELAY_MS, 650L) ?: 650L).coerceIn(500L, 10_000L)
        handleResultInService = intent?.getBooleanExtra(EXTRA_HANDLE_RESULT_IN_SERVICE, false) == true
        telegramBotToken = intent?.getStringExtra(EXTRA_TELEGRAM_BOT_TOKEN).orEmpty()
        telegramChatId = intent?.getStringExtra(EXTRA_TELEGRAM_CHAT_ID).orEmpty()
        telegramPersonalUsername = intent?.getStringExtra(EXTRA_TELEGRAM_PERSONAL_USERNAME).orEmpty()
        if (telegramPersonalUsername.isNotBlank()) TelegramTdlibSession.reconnect(applicationContext)
        telegramCaption = intent?.getStringExtra(EXTRA_TELEGRAM_CAPTION).orEmpty()
        normalTelegramShare = intent?.getBooleanExtra(EXTRA_NORMAL_TELEGRAM_SHARE, false) == true
        shareAnyApp = intent?.getBooleanExtra(EXTRA_SHARE_ANY_APP, false) == true
        stampDateTime = intent?.getBooleanExtra(EXTRA_STAMP_DATE_TIME, false) == true
        stampName = intent?.getStringExtra(EXTRA_STAMP_NAME).orEmpty()
        galleryOnly = intent?.getBooleanExtra(EXTRA_GALLERY_ONLY, false) == true
        routineId = intent?.getStringExtra(EXTRA_ROUTINE_ID).orEmpty()
        routineName = intent?.getStringExtra(EXTRA_ROUTINE_NAME).orEmpty()
        routineStartedAtMs = intent?.getLongExtra(EXTRA_ROUTINE_STARTED_AT_MS, System.currentTimeMillis()) ?: System.currentTimeMillis()
        capture(resultCode, data, delayMs)
        return START_NOT_STICKY
    }

    private fun capture(resultCode: Int, data: Intent, delayMs: Long) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = runCatching { manager.getMediaProjection(resultCode, data) }.getOrElse { error ->
            lastCaptureFailureReason = "MediaProjection request rejected: ${error.message}"
            complete(null)
            return
        }
        if (p == null) {
            lastFramesSeen = 0
            lastCaptureFailureReason = "MediaProjection was unavailable"
            complete(null)
            return
        }
        projection = p
        val handler = captureHandler
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        var display: android.hardware.display.VirtualDisplay? = null
        var completed = false
        val captureAfterMs = SystemClock.uptimeMillis() + delayMs
        val readyAtMs = captureAfterMs + 350L
        val failAtMs = captureAfterMs + 12_000L
        var framesSeen = 0
        var lastCandidate: Bitmap? = null
        lastFramesSeen = 0
        lastCaptureFailureReason = ""

        fun cleanup() {
            lastCandidate?.recycle()
            lastCandidate = null
            runCatching { display?.release() }
            runCatching { reader.close() }
            runCatching { p.stop() }
            projection = null
        }

        fun finishWithBitmap(bitmap: Bitmap, fallback: Boolean = false) {
            if (completed) {
                bitmap.recycle()
                return
            }
            completed = true
            lastCandidate?.takeIf { it !== bitmap }?.recycle()
            lastCandidate = null
            val file = File(cacheDir, "capture_${System.currentTimeMillis()}.png")
            val encodingStarted = SystemClock.elapsedRealtime()
            val saved = runCatching {
                if (stampDateTime) ScreenshotStamp.apply(bitmap, name = stampName)
                FileOutputStream(file).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "PNG encoding failed"
                    }
                }
                check(file.length() > 0L) { "Screenshot file is empty" }
            }
            captureEncodeElapsedMs = SystemClock.elapsedRealtime() - encodingStarted
            bitmap.recycle()
            if (saved.isFailure) {
                file.delete()
                lastCaptureFailureReason = saved.exceptionOrNull()?.message ?: "Screenshot encoding failed"
            } else if (fallback) {
                lastCaptureFailureReason = "Fallback frame accepted after waiting for app rendering"
            }
            cleanup()
            complete(if (saved.isSuccess) file.absolutePath else null)
        }

        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!completed) {
                    completed = true
                    cleanup()
                    complete(null)
                }
            }
        }, handler)

        reader.setOnImageAvailableListener({ imageReader ->
            if (completed) return@setOnImageAvailableListener
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            framesSeen++
            if (captureFirstFrameElapsedMs < 0L) captureFirstFrameElapsedMs = SystemClock.elapsedRealtime() - captureStartElapsedMs
            lastFramesSeen = framesSeen
            if (SystemClock.uptimeMillis() < captureAfterMs) {
                image.close()
                return@setOnImageAvailableListener
            }
            // Always release ImageReader frames, even if conversion fails on a device.
            // Keeping an acquired image open can stall the entire capture pipeline.
            val cropped = try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                check(pixelStride > 0 && rowStride >= pixelStride * width)
                val paddedWidth = rowStride / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                try {
                    padded.copyPixelsFromBuffer(plane.buffer)
                    Bitmap.createBitmap(padded, 0, 0, width, height)
                } finally {
                    padded.recycle()
                }
            } catch (error: Exception) {
                lastCaptureFailureReason = "Frame conversion failed: ${error.message}"
                null
            } finally {
                image.close()
            }
            if (cropped == null) return@setOnImageAvailableListener

            val sample = sampleCenterGrid(cropped)
            val likelyBlank = ScreenFrameReadiness.isLikelyBlank(sample)
            if (likelyBlank) {
                lastCandidate?.recycle()
                lastCandidate = cropped
                // A blank frame is not proof of the requested app's screenshot.
                return@setOnImageAvailableListener
            }
            if (SystemClock.uptimeMillis() < readyAtMs) {
                lastCandidate?.recycle()
                lastCandidate = cropped
                return@setOnImageAvailableListener
            }
            finishWithBitmap(cropped)
        }, handler)

        display = runCatching {
            p.createVirtualDisplay(
                "ShortcutScreenshot",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
        }.getOrElse { error ->
            completed = true
            lastCaptureFailureReason = "Virtual display creation failed: ${error.message}"
            cleanup()
            complete(null)
            return
        }

        if (display == null) {
            // Mark terminal state before cleanup: the timeout callback must not
            // attempt a second completion or create a duplicate history entry.
            completed = true
            lastCaptureFailureReason = "Virtual display could not be created"
            cleanup()
            complete(null)
            return
        }

        // A valid frame may arrive during the short settling window and no
        // further frames may be emitted. Finish at readiness instead of waiting
        // for the 12-second failure timeout.
        handler.postDelayed({
            if (!completed) {
                val candidate = lastCandidate
                if (candidate != null && !ScreenFrameReadiness.isLikelyBlank(sampleCenterGrid(candidate))) {
                    lastCandidate = null
                    finishWithBitmap(candidate)
                }
            }
        }, (readyAtMs - SystemClock.uptimeMillis()).coerceAtLeast(0L))

        handler.postDelayed({
            if (!completed && framesSeen == 0) {
                runCatching {
                    display?.setSurface(null)
                    display?.setSurface(reader.surface)
                }
            }
        }, delayMs + 4_000L)

        handler.postDelayed({
            if (!completed) {
                lastFramesSeen = framesSeen
                val fallback = lastCandidate
                if (fallback != null) {
                    lastCandidate = null
                    // Never count an unverified blank frame as a successful screenshot.
                    val sample = sampleCenterGrid(fallback)
                    if (ScreenFrameReadiness.isLikelyBlank(sample)) {
                        fallback.recycle()
                        completed = true
                        lastCaptureFailureReason = "Only blank or protected screen frames were received"
                        cleanup()
                        complete(null)
                    } else {
                        finishWithBitmap(fallback, fallback = true)
                    }
                } else {
                    completed = true
                    lastCaptureFailureReason = if (framesSeen == 0) {
                        "No screen frames were received before timeout"
                    } else {
                        "No usable screen frame was available before timeout"
                    }
                    cleanup()
                    complete(null)
                }
            }
        }, (failAtMs - SystemClock.uptimeMillis()).coerceAtLeast(1_000L))
    }

    private fun sampleCenterGrid(bitmap: Bitmap): IntArray {
        val columns = 11
        val rows = 15
        val left = (bitmap.width * 0.12f).toInt()
        val right = (bitmap.width * 0.88f).toInt().coerceAtLeast(left + 1)
        val top = (bitmap.height * 0.12f).toInt()
        val bottom = (bitmap.height * 0.88f).toInt().coerceAtLeast(top + 1)
        val pixels = IntArray(columns * rows)
        var index = 0
        for (row in 0 until rows) {
            val y = if (rows == 1) top else top + ((bottom - top - 1) * row / (rows - 1))
            for (column in 0 until columns) {
                val x = if (columns == 1) left else left + ((right - left - 1) * column / (columns - 1))
                pixels[index++] = bitmap.getPixel(
                    x.coerceIn(0, bitmap.width - 1),
                    y.coerceIn(0, bitmap.height - 1),
                )
            }
        }
        return pixels
    }

    private fun complete(path: String?) {
        if (handleResultInService) {
            handleAutomationResult(path)
        } else {
            sendBroadcast(
                Intent(ACTION_COMPLETE)
                    .setPackage(packageName)
                    .putExtra(EXTRA_PATH, path),
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createGalleryScreenshotUri(name: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Shortcut Screenshots")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            return requireNotNull(contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        }
        check(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            "Gallery storage permission missing"
        }
        val folder = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Shortcut Screenshots")
        check(folder.isDirectory || folder.mkdirs()) { "Could not create gallery folder" }
        return Uri.fromFile(File(folder, name))
    }

    private fun handleAutomationResult(path: String?) {
        if (path.isNullOrBlank()) {
            reportAutomationScreenshot(
                status = TaskExecutionStatus.FAILURE,
                reason = lastCaptureFailureReason.ifBlank { "Screen capture returned no image" },
                details = listOf(
                    "SCREENSHOT_CAPTURE = FAILURE",
                    "FramesSeen = $lastFramesSeen",
                    "DateTimeStamp = $stampDateTime",
                    "ShareAnyApp = $shareAnyApp",
                    "CaptureMode = ONE_SHOT",
                    "CaptureElapsedMs = ${SystemClock.elapsedRealtime() - captureStartElapsedMs}",
                ),
            )
            showResultNotification(
                local("Screenshot failed", "فشل التقاط لقطة الشاشة"),
                local("Shortcut could not capture the target app screen.", "تعذر على التطبيق التقاط شاشة التطبيق المطلوب."),
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val file = File(path)
        val savedUri = runCatching {
            val uri = if (galleryOnly) createGalleryScreenshotUri(ScreenshotStamp.fileName())
                else ToolOutputStore(this).create(ScreenshotStamp.fileName(), "image/png", true)
            if (galleryOnly && uri.scheme == "file") {
                FileOutputStream(requireNotNull(uri.path)).use { out -> file.inputStream().use { it.copyTo(out) } }
                android.media.MediaScannerConnection.scanFile(this, arrayOf(requireNotNull(uri.path)), arrayOf("image/png"), null)
            } else {
                contentResolver.openOutputStream(uri).use { out ->
                    requireNotNull(out)
                    file.inputStream().use { it.copyTo(out) }
                }
            }
            if (galleryOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }
                check(contentResolver.update(uri, published, null, null) > 0) {
                    "Failed to publish screenshot to gallery"
                }
            }
            // Verify the image contents rather than relying on a MediaStore URI.
            val validImage = (if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else contentResolver.openInputStream(uri))?.use { input ->
                BitmapFactory.decodeStream(input)?.let { bitmap ->
                    val valid = bitmap.width > 0 && bitmap.height > 0
                    bitmap.recycle()
                    valid
                }
            } ?: false
            check(validImage) { "Saved screenshot could not be decoded" }
            uri
        }.getOrNull()

        val baseDetails = buildList {
            add("SCREENSHOT_CAPTURE = SUCCESS")
            add("SCREENSHOT_SAVE = " + if (savedUri != null) "SUCCESS" else "FAILURE")
            add("FramesSeen = $lastFramesSeen")
            add("CaptureMode = ONE_SHOT")
            add("CaptureElapsedMs = ${SystemClock.elapsedRealtime() - captureStartElapsedMs}")
            add("CaptureFirstFrameMs = $captureFirstFrameElapsedMs")
            add("CaptureEncodeMs = $captureEncodeElapsedMs")
            add("ShareAnyApp = $shareAnyApp")
            add("DateTimeStamp = $stampDateTime")
            savedUri?.let { add("Uri = $it") }
        }
        if (savedUri == null) {
            file.delete()
            reportAutomationScreenshot(
                status = TaskExecutionStatus.FAILURE,
                reason = "Could not save screenshot",
                details = baseDetails,
            )
            showResultNotification(
                local("Screenshot failed", "فشل حفظ لقطة الشاشة"),
                local("Could not save the screenshot.", "تعذر حفظ لقطة الشاشة."),
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        when {
            telegramPersonalUsername.isNotBlank() -> {
                Thread {
                    val result = TelegramPersonalDeliveryCoordinator(TelegramTdlibSession).deliver(
                        PersonalPhotoRequest(
                            requestId = "capture-" + System.currentTimeMillis(),
                            username = telegramPersonalUsername,
                            file = file,
                            caption = telegramCaption,
                        ),
                    )
                    val status = when (result) {
                        PersonalDeliveryResult.Confirmed -> TaskExecutionStatus.SUCCESS
                        is PersonalDeliveryResult.Uncertain -> TaskExecutionStatus.PREPARED
                        else -> TaskExecutionStatus.FAILURE
                    }
                    reportAutomationScreenshot(
                        status = status,
                        reason = if (result == PersonalDeliveryResult.Confirmed) null else result.toString(),
                        details = baseDetails + "PERSONAL_TELEGRAM = $status",
                    )
                    file.delete()
                    showResultNotification(
                        if (status == TaskExecutionStatus.SUCCESS) local("Personal Telegram photo sent", "تم إرسال الصورة من الحساب الشخصي")
                        else local("Personal Telegram delivery not confirmed", "لم يتأكد الإرسال من حساب تيليجرام الشخصي"),
                        result.toString(),
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }.start()
            }
            telegramBotToken.isNotBlank() && telegramChatId.isNotBlank() -> {
                Thread {
                    val sent = TelegramBotSender(this@ScreenCaptureService).sendPhoto(
                        telegramBotToken,
                        telegramChatId,
                        telegramCaption,
                        file,
                    )
                    reportAutomationScreenshot(
                        status = if (sent.isSuccess) TaskExecutionStatus.SUCCESS else TaskExecutionStatus.FAILURE,
                        reason = sent.exceptionOrNull()?.message,
                        details = baseDetails + ("TELEGRAM_SEND = " + if (sent.isSuccess) "SUCCESS" else "FAILURE"),
                    )
                    file.delete()
                    showResultNotification(
                        if (sent.isSuccess) local("Screenshot sent to Telegram", "تم إرسال لقطة الشاشة إلى تيليجرام")
                        else local("Telegram send failed", "فشل الإرسال إلى تيليجرام"),
                        if (sent.isSuccess) local("The target app screenshot was captured and sent.", "تم التقاط شاشة التطبيق وإرسالها.")
                        else sent.exceptionOrNull()?.message.orEmpty(),
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }.start()
            }

            shareAnyApp && savedUri != null -> {
                reportAutomationScreenshot(
                    status = TaskExecutionStatus.PREPARED,
                    reason = null,
                    details = baseDetails + "SHARE_ANY_APP = PREPARED",
                )
                file.delete()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, savedUri)
                    clipData = ClipData.newRawUri("Shortcut screenshot", savedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(
                    shareIntent,
                    local("Share screenshot", "مشاركة لقطة الشاشة"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val sharePending = PendingIntent.getActivity(
                    this,
                    8835,
                    chooser,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                showResultNotification(
                    local("Screenshot saved — ready to share", "تم حفظ الصورة — جاهزة للمشاركة"),
                    local("Tap to choose any app.", "اضغط لاختيار أي تطبيق للمشاركة."),
                    sharePending,
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            normalTelegramShare && savedUri != null -> {
                reportAutomationScreenshot(
                    status = TaskExecutionStatus.PREPARED,
                    reason = null,
                    details = baseDetails + "TELEGRAM_SHARE = PREPARED",
                )
                file.delete()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, savedUri)
                    if (telegramCaption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, telegramCaption)
                    clipData = ClipData.newRawUri("Shortcut screenshot", savedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (packageManager.getLaunchIntentForPackage("org.telegram.messenger") != null) {
                        setPackage("org.telegram.messenger")
                    }
                }
                val chooser = Intent.createChooser(
                    shareIntent,
                    local("Choose Telegram chat", "اختر محادثة تيليجرام"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                val opened = runCatching {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        startActivity(chooser)
                    } else {
                        val pending = PendingIntent.getActivity(
                            this,
                            8833,
                            chooser,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        if (Build.VERSION.SDK_INT >= 34) {
                            val options = ActivityOptions.makeBasic().apply {
                                val mode = if (Build.VERSION.SDK_INT >= 36) {
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                                } else {
                                    @Suppress("DEPRECATION")
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                }
                                setPendingIntentBackgroundActivityStartMode(mode)
                            }
                            pending.send(this, 0, null, null, null, null, options.toBundle())
                        } else {
                            pending.send()
                        }
                    }
                    true
                }.getOrDefault(false)

                if (!opened) {
                    val fallbackPending = PendingIntent.getActivity(
                        this,
                        8833,
                        chooser,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    showResultNotification(
                        local("Screenshot ready for Telegram", "لقطة الشاشة جاهزة لتيليجرام"),
                        local("Tap to choose the Telegram conversation.", "اضغط لاختيار محادثة تيليجرام."),
                        fallbackPending,
                    )
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                if (savedUri != null) {
                    reportAutomationScreenshot(
                        status = TaskExecutionStatus.SUCCESS,
                        reason = null,
                        details = baseDetails,
                    )
                }
                file.delete()
                val viewPending = savedUri?.let { imageUri ->
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(imageUri, "image/png")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    PendingIntent.getActivity(
                        this, 8837, view,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
                showResultNotification(
                    if (savedUri != null) local("Screenshot saved", "تم حفظ لقطة الشاشة")
                    else local("Screenshot failed", "فشل حفظ لقطة الشاشة"),
                    if (savedUri != null) local("The target app screenshot was saved.", "تم حفظ لقطة شاشة التطبيق.")
                    else local("Could not save the screenshot.", "تعذر حفظ لقطة الشاشة."),
                    viewPending,
                    local("View image", "عرض الصورة"),
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun reportAutomationScreenshot(
        status: TaskExecutionStatus,
        reason: String?,
        details: List<String>,
    ) {
        if (routineName.isBlank()) return
        val started = routineStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val taskName = "$routineName / SCREENSHOT"
        val result = when (status) {
            TaskExecutionStatus.SUCCESS -> TaskExecutionResult.success(taskName, started, details = details)
            TaskExecutionStatus.PREPARED -> TaskExecutionResult.prepared(taskName, started, details = details)
            TaskExecutionStatus.FAILURE -> TaskExecutionResult.failure(taskName, reason ?: "Screenshot failed", started, details = details)
        }
        TaskExecutionReporter(applicationContext).report(result)
    }

    private fun showResultNotification(title: String, text: String, pending: PendingIntent? = null, actionLabel: String = local("Share image now", "مشاركة الصورة الآن")) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(RESULT_CHANNEL, local("Screenshot results", "نتائج لقطة الشاشة"), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val builder = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setContentText(text.take(180))
            .setAutoCancel(true)
        if (pending != null) {
            builder.setContentIntent(pending)
                .addAction(
                    android.R.drawable.ic_menu_share,
                    actionLabel,
                    pending,
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel("screenshot_share_actions", local("Screenshot sharing", "مشاركة لقطات الشاشة"), NotificationManager.IMPORTANCE_HIGH),
                )
                builder.setChannelId("screenshot_share_actions")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // The screenshot remains saved, but Android disallows the share notification.
            reportAutomationScreenshot(
                status = TaskExecutionStatus.FAILURE,
                reason = "Notification permission is denied; cannot present the share action",
                details = listOf("SHARE_NOTIFICATION = BLOCKED", "POST_NOTIFICATIONS = DENIED"),
            )
            return
        }
        manager.notify(RESULT_NOTIFICATION_ID, builder.build())
    }

    override fun onDestroy() {
        captureBusy = false
        runCatching { projection?.stop() }
        projection = null
        if (::captureHandler.isInitialized) captureHandler.removeCallbacksAndMessages(null)
        if (::captureThread.isInitialized) captureThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val ACTION_COMPLETE = "com.explapp.shortcut.SCREEN_CAPTURE_COMPLETE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_CAPTURE_DELAY_MS = "captureDelayMs"
        const val EXTRA_PATH = "path"
        const val EXTRA_HANDLE_RESULT_IN_SERVICE = "handleResultInService"
        const val EXTRA_TELEGRAM_BOT_TOKEN = "telegramBotToken"
        const val EXTRA_TELEGRAM_CHAT_ID = "telegramChatId"
        const val EXTRA_TELEGRAM_PERSONAL_USERNAME = "telegramPersonalUsername"
        const val EXTRA_TELEGRAM_CAPTION = "telegramCaption"
        const val EXTRA_NORMAL_TELEGRAM_SHARE = "normalTelegramShare"
        const val EXTRA_SHARE_ANY_APP = "shareAnyApp"
        const val EXTRA_STAMP_DATE_TIME = "stampDateTime"
        const val EXTRA_STAMP_NAME = "stampName"
        const val EXTRA_GALLERY_ONLY = "galleryOnly"
        const val EXTRA_ROUTINE_ID = "routineId"
        const val EXTRA_ROUTINE_NAME = "routineName"
        const val EXTRA_ROUTINE_STARTED_AT_MS = "routineStartedAtMs"
        private const val CHANNEL = "screen_capture"
        private const val RESULT_CHANNEL = "screen_capture_results"
        private const val NOTIFICATION_ID = 8831
        private const val RESULT_NOTIFICATION_ID = 8834
    }
}
