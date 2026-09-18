package com.explapp.shortcut.tools

import android.Manifest
import android.app.Activity
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
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.explapp.shortcut.automation.routines.TelegramBotSender
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
    private var telegramCaption: String = ""
    private var persistentStartOnly: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val path = intent.getStringExtra(ScreenCaptureService.EXTRA_PATH)
            if (path.isNullOrBlank()) {
                Toast.makeText(this@ScreenCaptureActivity, local("Could not capture screenshot", "تعذر التقاط الشاشة"), Toast.LENGTH_LONG).show()
                finish()
                return
            }
            val file = File(path)
            if (runOcr) ocr(file) else saveScreenshot(file)
        }
    }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            finish()
            return@registerForActivityResult
        }
        if (persistentStartOnly) {
            val service = Intent(this, PersistentScreenCaptureService::class.java)
                .setAction(PersistentScreenCaptureService.ACTION_START_SESSION)
                .putExtra(PersistentScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(PersistentScreenCaptureService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(this, service)
            Toast.makeText(
                this,
                local("Persistent screen-capture session started", "تم تشغيل جلسة تصوير الشاشة المستمرة"),
                Toast.LENGTH_LONG,
            ).show()
            finish()
        } else {
            val service = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                .putExtra(ScreenCaptureService.EXTRA_CAPTURE_DELAY_MS, captureDelayMs)
            ContextCompat.startForegroundService(this, service)

            val packageNameToOpen = launchPackage
            if (!packageNameToOpen.isNullOrBlank()) {
                packageManager.getLaunchIntentForPackage(packageNameToOpen)?.let { target ->
                    startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else {
                moveTaskToBack(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runOcr = intent.getBooleanExtra(EXTRA_OCR, false)
        launchPackage = intent.getStringExtra(EXTRA_LAUNCH_PACKAGE)?.takeIf { it.isNotBlank() }
        captureDelayMs = intent.getLongExtra(EXTRA_CAPTURE_DELAY_MS, DEFAULT_CAPTURE_DELAY_MS)
            .coerceIn(500L, 10_000L)
        telegramBotToken = intent.getStringExtra(EXTRA_TELEGRAM_BOT_TOKEN).orEmpty()
        telegramChatId = intent.getStringExtra(EXTRA_TELEGRAM_CHAT_ID).orEmpty()
        telegramCaption = intent.getStringExtra(EXTRA_TELEGRAM_CAPTION).orEmpty()
        persistentStartOnly = intent.getBooleanExtra(EXTRA_PERSISTENT_START_ONLY, false)
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ScreenCaptureService.ACTION_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        showCaptureInstructions()
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

            AlertDialog.Builder(this)
                .setTitle(local("Open app + screenshot", "فتح تطبيق + لقطة شاشة"))
                .setMessage(
                    local(
                        "Shortcut will ask Android for screen-capture permission, open $appLabel, wait ${captureDelayMs / 1000.0} seconds, take ONE screenshot of the whole visible screen, then stop automatically.",
                        "سيطلب Shortcut موافقة Android على تصوير الشاشة، ثم يفتح $appLabel، وينتظر ${captureDelayMs / 1000.0} ثانية، ثم يلتقط صورة شاشة واحدة كاملة ويتوقف تلقائيًا.",
                    ),
                )
                .setPositiveButton(local("Continue", "متابعة")) { _, _ -> launchConsent() }
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

    private fun launchConsent() {
        consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun saveScreenshot(file: File) {
        runCatching {
            val uri = ToolOutputStore(this).create(
                "Screenshot_${System.currentTimeMillis()}.png",
                "image/png",
                true,
            )
            contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output)
                file.inputStream().use { it.copyTo(output) }
            }
            uri
        }.onSuccess { uri ->
            if (telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()) {
                Thread {
                    val result = TelegramBotSender().sendPhoto(telegramBotToken, telegramChatId, telegramCaption, file)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (result.isSuccess) local("Screenshot sent to Telegram", "تم إرسال لقطة الشاشة إلى تيليجرام")
                            else local("Screenshot saved, but Telegram send failed", "تم حفظ الصورة لكن فشل إرسالها إلى تيليجرام"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    file.delete()
                }.start()
            } else {
                file.delete()
            }
            ToolResultActions.show(this, listOf(uri), ToolOutputResultPolicy.forTool(ToolId.SCREENSHOT_CAPTURE).mime)
        }.onFailure {
            file.delete()
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

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        const val EXTRA_OCR = "ocr"
        const val EXTRA_LAUNCH_PACKAGE = "launch_package"
        const val EXTRA_CAPTURE_DELAY_MS = "capture_delay_ms"
        const val EXTRA_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        const val EXTRA_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val EXTRA_TELEGRAM_CAPTION = "telegram_caption"
        const val EXTRA_PERSISTENT_START_ONLY = "persistent_start_only"
        private const val DEFAULT_CAPTURE_DELAY_MS = 3_000L
        private const val OCR_CHANNEL = "screen_ocr"
    }
}

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            complete(null)
            return START_NOT_STICKY
        }
        val delayMs = (intent?.getLongExtra(EXTRA_CAPTURE_DELAY_MS, 650L) ?: 650L).coerceIn(500L, 10_000L)
        Handler(Looper.getMainLooper()).postDelayed({ capture(resultCode, data) }, delayMs)
        return START_NOT_STICKY
    }

    private fun capture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = manager.getMediaProjection(resultCode, data)
        if (p == null) {
            complete(null)
            return
        }
        projection = p
        val handler = Handler(Looper.getMainLooper())
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        var display: android.hardware.display.VirtualDisplay? = null
        var completed = false
        val readyAtMs = SystemClock.uptimeMillis() + 350L
        var framesSeen = 0

        fun cleanup() {
            runCatching { display?.release() }
            runCatching { reader.close() }
            runCatching { p.stop() }
            projection = null
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
            if (framesSeen == 1 || SystemClock.uptimeMillis() < readyAtMs) {
                image.close()
                return@setOnImageAvailableListener
            }
            completed = true
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
            padded.recycle()
            image.close()
            val file = File(cacheDir, "capture_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()
            cleanup()
            complete(file.absolutePath)
        }, handler)

        display = p.createVirtualDisplay(
            "ShortcutScreenshot",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )

        handler.postDelayed({
            if (!completed) {
                completed = true
                cleanup()
                complete(null)
            }
        }, 4_000)
    }

    private fun complete(path: String?) {
        sendBroadcast(
            Intent(ACTION_COMPLETE)
                .setPackage(packageName)
                .putExtra(EXTRA_PATH, path),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { projection?.stop() }
        projection = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_COMPLETE = "com.explapp.shortcut.SCREEN_CAPTURE_COMPLETE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_CAPTURE_DELAY_MS = "captureDelayMs"
        const val EXTRA_PATH = "path"
        private const val CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 8831
    }
}
