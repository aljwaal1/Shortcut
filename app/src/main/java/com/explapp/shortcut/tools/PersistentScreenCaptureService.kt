package com.explapp.shortcut.tools

import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import android.content.ClipData
import android.net.Uri
import com.explapp.shortcut.automation.routines.TelegramBotSender
import com.explapp.shortcut.execution.TaskExecutionReporter
import com.explapp.shortcut.execution.TaskExecutionResult
import java.io.File
import java.io.FileOutputStream

class PersistentScreenCaptureService : Service() {
    private lateinit var captureThread: HandlerThread
    private lateinit var handler: Handler
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var captureRequested = false
    private var captureInFlight = false
    private var captureGeneration = 0L
    private var pendingToken = ""
    private var pendingChatId = ""
    private var pendingCaption = ""
    private var pendingNormalTelegramShare = false
    private var pendingStampDateTime = false
    private var pendingRoutineId = ""
    private var pendingRoutineName = ""
    private var pendingRoutineStartedAtMs = 0L
    private var blankFrameRetryUntilMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        captureThread = HandlerThread("ShortcutPersistentCapture").apply { start() }
        handler = Handler(captureThread.looper)
        setActive(false)
        createChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            when (intent?.action) {
                ACTION_START_SESSION -> startSession(intent)
                ACTION_CAPTURE -> requestCapture(intent)
                ACTION_STOP_SESSION -> stopSession()
            }
        }.onFailure { error ->
            setActive(false)
            notifyResult(
                saved = false,
                sent = false,
                reason = error.message ?: error.javaClass.simpleName,
            )
            cleanupProjection()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startSession(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            setActive(false)
            stopSelf()
            return
        }

        cleanupProjection()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = manager.getMediaProjection(resultCode, data) ?: run {
            setActive(false)
            stopSelf()
            return
        }
        projection = p

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader = imageReader

        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                setActive(false)
                cleanupProjection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, handler)

        imageReader.setOnImageAvailableListener({ source ->
            // Do not continuously consume display frames while the persistent session is idle.
            // Let ImageReader backpressure pause production; requestCapture() drains the stale
            // buffered frame immediately before asking for a fresh screenshot.
            if (!captureRequested) return@setOnImageAvailableListener
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            runCatching {
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

                val sample = sampleCenterGrid(cropped)
                val likelyBlank = ScreenFrameReadiness.isLikelyBlank(sample)
                if (likelyBlank && SystemClock.uptimeMillis() < blankFrameRetryUntilMs) {
                    cropped.recycle()
                    return@setOnImageAvailableListener
                }

                captureRequested = false
                if (pendingStampDateTime) ScreenshotStamp.apply(cropped)
                val file = File(cacheDir, "persistent_capture_" + System.currentTimeMillis() + ".png")
                FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                file
            }.onSuccess { file ->
                captureInFlight = false
                val saved = runCatching {
                    val output = ToolOutputStore(this).create(
                        ScreenshotStamp.fileName(),
                        "image/png",
                        true,
                    )
                    contentResolver.openOutputStream(output).use { out ->
                        requireNotNull(out)
                        file.inputStream().use { it.copyTo(out) }
                    }
                    output
                }

                val token = pendingToken
                val chatId = pendingChatId
                val caption = pendingCaption
                val normalShare = pendingNormalTelegramShare
                if (token.isNotBlank() && chatId.isNotBlank()) {
                    Thread {
                        val sent = TelegramBotSender().sendPhoto(token, chatId, caption, file)
                        file.delete()
                        notifyResult(
                            saved = saved.isSuccess,
                            sent = sent.isSuccess,
                            reason = sent.exceptionOrNull()?.message,
                        )
                    }.start()
                } else if (normalShare && saved.isSuccess) {
                    file.delete()
                    notifyResult(saved = true, sent = false, reason = null)
                    notifyNormalTelegramShare(saved.getOrThrow(), caption)
                } else {
                    file.delete()
                    notifyResult(
                        saved = saved.isSuccess,
                        sent = false,
                        reason = saved.exceptionOrNull()?.message,
                    )
                }
            }.onFailure {
                captureInFlight = false
                runCatching { image.close() }
                notifyResult(saved = false, sent = false, reason = it.message)
            }
        }, handler)

        display = p.createVirtualDisplay(
            "ShortcutPersistentCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )
        setActive(true)
        updateNotification()
    }

    private fun requestCapture(intent: Intent) {
        if (!isSessionActive(this) || projection == null || reader == null) {
            notifyResult(
                saved = false,
                sent = false,
                reason = local("Screen-capture session is not active.", "جلسة تصوير الشاشة غير نشطة."),
            )
            return
        }
        if (captureInFlight) {
            notifyResult(
                saved = false,
                sent = false,
                reason = local("A screenshot is already being processed. Try again in a few seconds.", "توجد لقطة شاشة قيد المعالجة. حاول مرة أخرى بعد بضع ثوانٍ."),
            )
            return
        }
        captureInFlight = true
        val generation = ++captureGeneration

        pendingToken = intent.getStringExtra(EXTRA_TELEGRAM_BOT_TOKEN).orEmpty()
        pendingChatId = intent.getStringExtra(EXTRA_TELEGRAM_CHAT_ID).orEmpty()
        pendingCaption = intent.getStringExtra(EXTRA_TELEGRAM_CAPTION).orEmpty()
        pendingNormalTelegramShare = intent.getBooleanExtra(EXTRA_NORMAL_TELEGRAM_SHARE, false)
        pendingStampDateTime = intent.getBooleanExtra(EXTRA_STAMP_DATE_TIME, false)
        pendingRoutineId = intent.getStringExtra(EXTRA_ROUTINE_ID).orEmpty()
        pendingRoutineName = intent.getStringExtra(EXTRA_ROUTINE_NAME).orEmpty()
        pendingRoutineStartedAtMs = System.currentTimeMillis()
        val delayMs = intent.getLongExtra(EXTRA_CAPTURE_DELAY_MS, 3_000L).coerceIn(500L, 15_000L)
        val packageNameToOpen = intent.getStringExtra(EXTRA_LAUNCH_PACKAGE).orEmpty()
        val skipAppLaunch = intent.getBooleanExtra(EXTRA_SKIP_APP_LAUNCH, false)

        var launchSucceeded = true
        if (!skipAppLaunch && packageNameToOpen.isNotBlank()) {
            val target = packageManager.getLaunchIntentForPackage(packageNameToOpen)
            launchSucceeded = if (target == null) {
                false
            } else {
                runCatching {
                    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        startActivity(target)
                    } else {
                        val pending = PendingIntent.getActivity(
                            this,
                            packageNameToOpen.hashCode(),
                            target,
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
            }
        }

        if (!launchSucceeded) {
            captureInFlight = false
            notifyResult(
                saved = false,
                sent = false,
                reason = local("Android blocked opening the target app from background.", "منع أندرويد فتح التطبيق المطلوب من الخلفية."),
            )
            return
        }

        handler.postDelayed({
            if (projection != null) {
                // Drain any frame buffered while idle so the next callback represents
                // the screen after the requested delay, not an old frame.
                runCatching { reader?.acquireLatestImage()?.close() }
                blankFrameRetryUntilMs = SystemClock.uptimeMillis() + BLANK_FRAME_RETRY_MS
                captureRequested = true
                handler.postDelayed({
                    if (generation == captureGeneration && captureInFlight && captureRequested) {
                        captureRequested = false
                        captureInFlight = false
                        notifyResult(
                            saved = false,
                            sent = false,
                            reason = local(
                                "Timed out waiting for a fresh screen frame. Try reactivating the capture session.",
                                "انتهت مهلة انتظار لقطة شاشة جديدة. حاول إعادة تفعيل جلسة التصوير.",
                            ),
                        )
                    }
                }, CAPTURE_FRAME_TIMEOUT_MS)
            } else {
                captureInFlight = false
            }
        }, delayMs)
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

    private fun stopSession() {
        setActive(false)
        cleanupProjection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupProjection() {
        captureRequested = false
        captureInFlight = false
        runCatching { display?.release() }
        display = null
        runCatching { reader?.close() }
        reader = null
        val current = projection
        projection = null
        runCatching { current?.stop() }
    }

    override fun onDestroy() {
        setActive(false)
        cleanupProjection()
        if (::handler.isInitialized) handler.removeCallbacksAndMessages(null)
        if (::captureThread.isInitialized) captureThread.quitSafely()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, PersistentScreenCaptureService::class.java).setAction(ACTION_STOP_SESSION)
        val stopPending = PendingIntent.getService(
            this,
            9201,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(local("Screen capture session active", "جلسة تصوير الشاشة نشطة"))
            .setContentText(local("Shortcut can capture scheduled screenshots until you stop this session.", "يمكن للتطبيق التقاط الصور المجدولة حتى توقف هذه الجلسة."))
            .setOngoing(true)
            .addAction(0, local("Stop", "إيقاف"), stopPending)
            .build()
    }

    private fun notifyNormalTelegramShare(uri: Uri, caption: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newRawUri("Shortcut screenshot", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageManager.getLaunchIntentForPackage("org.telegram.messenger") != null) {
                setPackage("org.telegram.messenger")
            }
        }
        val chooser = Intent.createChooser(
            sendIntent,
            local("Choose Telegram chat", "اختر محادثة تيليجرام"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val opened = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                startActivity(chooser)
            } else {
                val pending = PendingIntent.getActivity(
                    this,
                    9203,
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
            val pending = PendingIntent.getActivity(
                this,
                9203,
                chooser,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            getSystemService(NotificationManager::class.java).notify(
                RESULT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, RESULT_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_send)
                    .setContentTitle(local("Screenshot ready for Telegram", "لقطة الشاشة جاهزة لتيليجرام"))
                    .setContentText(local("Tap to choose the normal Telegram conversation and send.", "اضغط لاختيار محادثة تيليجرام العادية ثم الإرسال."))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .addAction(0, local("Open Telegram", "فتح تيليجرام"), pending)
                    .build(),
            )
        }
    }

    private fun notifyResult(saved: Boolean, sent: Boolean, reason: String? = null) {
        val cleanReason = reason?.trim().orEmpty()
        if (pendingRoutineName.isNotBlank()) {
            val details = buildList {
                add("PERSISTENT_CAPTURE = " + if (saved) "SUCCESS" else "FAILURE")
                add("SCREENSHOT_SAVE = " + if (saved) "SUCCESS" else "FAILURE")
                if (pendingToken.isNotBlank() || pendingChatId.isNotBlank()) {
                    add("TELEGRAM_SEND = " + if (sent) "SUCCESS" else "FAILURE")
                }
                if (pendingNormalTelegramShare) add("TELEGRAM_SHARE = PREPARED")
                add("DateTimeStamp = $pendingStampDateTime")
                if (pendingRoutineId.isNotBlank()) add("RoutineId = $pendingRoutineId")
            }
            val taskName = "$pendingRoutineName / SCREENSHOT"
            val started = pendingRoutineStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val result = if (saved) {
                TaskExecutionResult.success(taskName, started, details = details)
            } else {
                TaskExecutionResult.failure(
                    taskName,
                    cleanReason.ifBlank { "Persistent screenshot failed" },
                    started,
                    details = details,
                )
            }
            TaskExecutionReporter(applicationContext).report(result)
            pendingRoutineId = ""
            pendingRoutineName = ""
            pendingRoutineStartedAtMs = 0L
        }
        val title = when {
            saved && sent -> local("Screenshot sent to Telegram", "تم إرسال لقطة الشاشة إلى تيليجرام")
            saved && cleanReason.isNotBlank() -> local("Telegram send failed", "فشل الإرسال إلى تيليجرام")
            saved -> local("Screenshot saved", "تم حفظ لقطة الشاشة")
            else -> local("Screenshot failed", "فشل التقاط لقطة الشاشة")
        }
        val text = when {
            saved && sent -> local(
                "The screenshot was saved and sent to the selected Telegram chat.",
                "تم حفظ لقطة الشاشة وإرسالها إلى محادثة تيليجرام المحددة.",
            )
            cleanReason.isNotBlank() -> cleanReason
            saved -> local(
                "The screenshot was saved successfully.",
                "تم حفظ لقطة الشاشة بنجاح.",
            )
            else -> local(
                "Shortcut could not create the scheduled screenshot.",
                "تعذر على التطبيق إنشاء لقطة الشاشة المجدولة.",
            )
        }
        getSystemService(NotificationManager::class.java).notify(
            RESULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, RESULT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(title)
                .setContentText(text.take(220))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, local("Persistent screen capture", "جلسة تصوير الشاشة المستمرة"), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(RESULT_CHANNEL, local("Automation results", "نتائج الأتمتة"), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun setActive(active: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        const val ACTION_START_SESSION = "com.explapp.shortcut.START_PERSISTENT_CAPTURE"
        const val ACTION_CAPTURE = "com.explapp.shortcut.PERSISTENT_CAPTURE"
        const val ACTION_STOP_SESSION = "com.explapp.shortcut.STOP_PERSISTENT_CAPTURE"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_LAUNCH_PACKAGE = "launchPackage"
        const val EXTRA_CAPTURE_DELAY_MS = "captureDelayMs"
        const val EXTRA_SKIP_APP_LAUNCH = "skipAppLaunch"
        const val EXTRA_TELEGRAM_BOT_TOKEN = "telegramBotToken"
        const val EXTRA_TELEGRAM_CHAT_ID = "telegramChatId"
        const val EXTRA_TELEGRAM_CAPTION = "telegramCaption"
        const val EXTRA_NORMAL_TELEGRAM_SHARE = "normalTelegramShare"
        const val EXTRA_STAMP_DATE_TIME = "stampDateTime"
        const val EXTRA_ROUTINE_ID = "routineId"
        const val EXTRA_ROUTINE_NAME = "routineName"

        private const val CHANNEL = "persistent_screen_capture"
        private const val NOTIFICATION_ID = 9200
        private const val RESULT_NOTIFICATION_ID = 9202
        private const val RESULT_CHANNEL = "persistent_screen_capture_results"
        private const val CAPTURE_FRAME_TIMEOUT_MS = 6_000L
        private const val BLANK_FRAME_RETRY_MS = 3_000L
        private const val PREFS = "persistent_screen_capture_state"
        private const val KEY_ACTIVE = "active"

        fun isSessionActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)
    }
}
