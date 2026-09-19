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
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.content.ClipData
import android.net.Uri
import com.explapp.shortcut.automation.routines.TelegramBotSender
import java.io.File
import java.io.FileOutputStream

class PersistentScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var captureRequested = false
    private var pendingToken = ""
    private var pendingChatId = ""
    private var pendingCaption = ""
    private var pendingNormalTelegramShare = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setActive(false)
        createChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> startSession(intent)
            ACTION_CAPTURE -> requestCapture(intent)
            ACTION_STOP_SESSION -> stopSession()
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
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (!captureRequested) {
                image.close()
                return@setOnImageAvailableListener
            }
            captureRequested = false
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

                val file = File(cacheDir, "persistent_capture_" + System.currentTimeMillis() + ".png")
                FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                file
            }.onSuccess { file ->
                val saved = runCatching {
                    val output = ToolOutputStore(this).create(
                        "Screenshot_" + System.currentTimeMillis() + ".png",
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
                        )
                    }.start()
                } else if (normalShare && saved.isSuccess) {
                    file.delete()
                    notifyNormalTelegramShare(saved.getOrThrow(), caption)
                } else {
                    file.delete()
                    notifyResult(saved = saved.isSuccess, sent = false)
                }
            }.onFailure {
                runCatching { image.close() }
                notifyResult(saved = false, sent = false)
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
        if (!isSessionActive(this) || projection == null || reader == null) return

        pendingToken = intent.getStringExtra(EXTRA_TELEGRAM_BOT_TOKEN).orEmpty()
        pendingChatId = intent.getStringExtra(EXTRA_TELEGRAM_CHAT_ID).orEmpty()
        pendingCaption = intent.getStringExtra(EXTRA_TELEGRAM_CAPTION).orEmpty()
        pendingNormalTelegramShare = intent.getBooleanExtra(EXTRA_NORMAL_TELEGRAM_SHARE, false)
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
            notifyResult(saved = false, sent = false)
            return
        }

        handler.postDelayed({
            if (projection != null) captureRequested = true
        }, delayMs)
    }

    private fun stopSession() {
        setActive(false)
        cleanupProjection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupProjection() {
        captureRequested = false
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageManager.getLaunchIntentForPackage("org.telegram.messenger") != null) {
                setPackage("org.telegram.messenger")
            }
        }
        val pending = PendingIntent.getActivity(
            this,
            9203,
            Intent.createChooser(sendIntent, local("Choose Telegram chat", "اختر محادثة تيليجرام"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java).notify(
            RESULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle(local("Screenshot ready for Telegram", "لقطة الشاشة جاهزة لتيليجرام"))
                .setContentText(local("Tap to choose the normal Telegram conversation and send.", "اضغط لاختيار محادثة تيليجرام العادية ثم الإرسال."))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .addAction(0, local("Open Telegram", "فتح تيليجرام"), pending)
                .build(),
        )
    }

    private fun notifyResult(saved: Boolean, sent: Boolean) {
        val title = when {
            saved && sent -> local("Screenshot sent to Telegram", "تم إرسال لقطة الشاشة إلى تيليجرام")
            saved -> local("Screenshot saved", "تم حفظ لقطة الشاشة")
            else -> local("Screenshot failed", "فشل التقاط لقطة الشاشة")
        }
        val text = when {
            saved && sent -> local("The screenshot was saved and sent to the selected Telegram chat.", "تم حفظ لقطة الشاشة وإرسالها إلى محادثة تيليجرام المحددة.")
            saved -> local("The screenshot was saved, but Telegram sending did not complete.", "تم حفظ لقطة الشاشة، لكن لم يكتمل الإرسال إلى تيليجرام.")
            else -> local("Shortcut could not create the scheduled screenshot.", "تعذر على التطبيق إنشاء لقطة الشاشة المجدولة.")
        }
        getSystemService(NotificationManager::class.java).notify(
            RESULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, local("Persistent screen capture", "جلسة تصوير الشاشة المستمرة"), NotificationManager.IMPORTANCE_LOW),
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

        private const val CHANNEL = "persistent_screen_capture"
        private const val NOTIFICATION_ID = 9200
        private const val RESULT_NOTIFICATION_ID = 9202
        private const val PREFS = "persistent_screen_capture_state"
        private const val KEY_ACTIVE = "active"

        fun isSessionActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)
    }
}
