package com.explapp.shortcut.tools

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.abs

/**
 * A separate floating video button, modelled on the floating screenshot
 * control. Requires Android overlay permission and per-session MediaProjection
 * consent. Does not capture the microphone or other private audio.
 */
class FloatingVideoSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ar = resources.configuration.locales[0].language == "ar"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = if (ar) "زر تسجيل فيديو الشاشة العائم" else "Floating screen video recorder"
            textSize = 22f
        })
        layout.addView(TextView(this).apply {
            text = if (ar) "زر التحكم لا يظهر في الفيديو. أثناء التسجيل استخدم إشعار Android للإيقاف المؤقت والاستئناف والإنهاء والحفظ. بعد النجاح يصلك إشعار لفتح MP4 أو مشاركته. التسجيل بلا صوت."
                else "The control bubble stays out of the video. Use the Android recording notification to pause, resume, finish and save. A saved notification lets you open or share the silent MP4."
            setPadding(0, 18, 0, 18)
        })
        val pointerOption = CheckBox(this).apply {
            text = if (ar) "إظهار مؤشر شرح قابل للسحب داخل الفيديو" else "Show draggable presentation pointer in video"
            isChecked = getSharedPreferences("screen_video", MODE_PRIVATE).getBoolean("pointer", false)
        }
        layout.addView(pointerOption)
        fun savePointerChoice(): Boolean {
            if (pointerOption.isChecked && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, if (ar) "فعّل الظهور فوق التطبيقات لاستخدام المؤشر، ثم عد إلى هنا" else "Allow display over other apps for the pointer, then return", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return false
            }
            getSharedPreferences("screen_video", MODE_PRIVATE).edit().putBoolean("pointer", pointerOption.isChecked).apply()
            return true
        }
        fun add(text: String, act: () -> Unit) {
            layout.addView(Button(this).apply { this.text = text; setOnClickListener { act() } })
        }
        add(if (ar) "تشغيل الزر العائم" else "Enable floating button") {
            if (!savePointerChoice()) return@add
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, if (ar) "اسمح بالظهور فوق التطبيقات، ثم عد إلى هنا" else "Allow display over other apps, then return", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, FloatingVideoBubbleService::class.java))
                finish()
            }
        }
        add(if (ar) "بدء تسجيل بدون زر عائم" else "Start recording without bubble") {
            if (!savePointerChoice()) return@add
            startActivity(Intent(this, VideoRecordingPermissionActivity::class.java))
            finish()
        }
        add(if (ar) "إيقاف الزر العائم" else "Disable floating button") {
            startService(Intent(this, FloatingVideoBubbleService::class.java).setAction(FloatingVideoBubbleService.ACTION_DISABLE))
            finish()
        }
        setContentView(layout)
    }
}

class VideoRecordingPermissionActivity : AppCompatActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this,
            "Allow notifications in Android settings to see saved video and Share/Open actions",
            Toast.LENGTH_LONG).show()
        requestScreenConsent()
    }
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (FloatingVideoBubbleService.isActive) {
                startService(Intent(this, FloatingVideoBubbleService::class.java).setAction(FloatingVideoBubbleService.ACTION_HIDE))
            }
            val intent = Intent(this, ScreenVideoRecordingService::class.java)
                .setAction(ScreenVideoRecordingService.ACTION_START)
                .putExtra(ScreenVideoRecordingService.EXTRA_RESULT, result.resultCode)
                .putExtra(ScreenVideoRecordingService.EXTRA_DATA, result.data)
            try {
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            } catch (error: Exception) {
                restoreBubble()
                Toast.makeText(this, error.message ?: "Cannot start recording", Toast.LENGTH_LONG).show()
            }
        } else {
            restoreBubble()
        }
        finish()
    }

    private fun restoreBubble() {
        if (FloatingVideoBubbleService.isActive) {
            startService(Intent(this, FloatingVideoBubbleService::class.java).setAction(FloatingVideoBubbleService.ACTION_SHOW))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else requestScreenConsent()
    }

    private fun requestScreenConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val request = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(
                android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else manager.createScreenCaptureIntent()
        consent.launch(request)
    }
}

class FloatingVideoBubbleService : Service() {
    private var window: WindowManager? = null
    private var bubble: LinearLayout? = null
    private var pauseButton: Button? = null
    private var stopButton: Button? = null
    private var startButton: Button? = null
    private var hiddenForCapture = false
    private val main = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            bubble?.let {
                val recording = ScreenVideoRecordingService.isRecording
                // Remove ALL controls from recorded frames, including Pause and Stop.
                // Recording remains controllable from the foreground notification.
                it.visibility = if (VideoOverlayPolicy.showControlBubble(recording, hiddenForCapture))
                    android.view.View.VISIBLE else android.view.View.GONE
            }
            if (bubble != null) main.postDelayed(this, 750L)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_HIDE) {
            hiddenForCapture = true
            bubble?.visibility = android.view.View.GONE
            return START_STICKY
        }
        if (intent?.action == ACTION_SHOW) {
            hiddenForCapture = false
        }
        if (bubble == null) showBubble()
        return START_STICKY
    }
    private fun local(en: String, ar: String) =
        if (resources.configuration.locales[0].language == "ar") ar else en

    private fun showBubble() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        window = wm
        val d = resources.displayMetrics.density
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((7*d).toInt(), (7*d).toInt(), (7*d).toInt(), (7*d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16*d
                setColor(Color.rgb(28, 46, 68))
            }
            elevation = 8*d
        }
        fun button(text: String, click: () -> Unit): Button = Button(this).apply {
            this.text = text
            textSize = 12f
            setOnClickListener { click() }
            view.addView(this)
        }
        startButton = button(local("● Record video", "● تسجيل فيديو")) {
            startActivity(Intent(this, VideoRecordingPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        pauseButton = button(local("Ⅱ Pause", "Ⅱ إيقاف مؤقت")) {
            val action = if (ScreenVideoRecordingService.isPaused)
                ScreenVideoRecordingService.ACTION_RESUME else ScreenVideoRecordingService.ACTION_PAUSE
            startService(Intent(this, ScreenVideoRecordingService::class.java).setAction(action))
        }
        stopButton = button(local("■ Stop and save", "■ إنهاء وحفظ")) {
            startService(Intent(this, ScreenVideoRecordingService::class.java)
                .setAction(ScreenVideoRecordingService.ACTION_STOP))
        }
        pauseButton?.visibility = android.view.View.GONE
        stopButton?.visibility = android.view.View.GONE
        val options = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - 165*d).toInt().coerceAtLeast(0)
            y = (resources.displayMetrics.heightPixels / 2)
        }
        var x0 = 0
        var y0 = 0
        var touchX = 0f
        var touchY = 0f
        // Drag the empty container; actual control buttons handle taps.
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = options.x; y0 = options.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    options.x = (x0 + (event.rawX - touchX).toInt()).coerceAtLeast(0)
                    options.y = (y0 + (event.rawY - touchY).toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(view, options) }
                    true
                }
                else -> true
            }
        }
        wm.addView(view, options)
        bubble=view
        isActive = true
        main.post(refresh)
    }
    override fun onDestroy() {
        main.removeCallbacks(refresh)
        bubble?.let { view -> runCatching { window?.removeView(view) } }
        bubble=null
        isActive = false
        startButton=null
        pauseButton=null
        stopButton=null
        super.onDestroy()
    }
    companion object {
        const val ACTION_DISABLE = "com.explapp.shortcut.VIDEO_BUBBLE_DISABLE"
        const val ACTION_HIDE = "com.explapp.shortcut.VIDEO_BUBBLE_HIDE"
        const val ACTION_SHOW = "com.explapp.shortcut.VIDEO_BUBBLE_SHOW"
        @Volatile var isActive: Boolean = false
            private set
    }
}

/** Optional non-secure dot: deliberately visible in the recorded video. */
class VideoExplanationPointerService : Service() {
    private var wm: WindowManager? = null
    private var marker: TextView? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (marker != null) return START_NOT_STICKY
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val density = resources.displayMetrics.density
        val diameter = (52 * density).toInt()
        val dot = TextView(this).apply {
            text = "●"
            textSize = 34f
            setTextColor(Color.rgb(255, 48, 48))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(90, 255, 255, 255))
                setStroke((2 * density).toInt(), Color.rgb(255, 80, 80))
            }
        }
        val params = WindowManager.LayoutParams(
            diameter, diameter, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels / 2
            y = resources.displayMetrics.heightPixels / 3
        }
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        dot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    downX = event.rawX; downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + (event.rawX - downX).toInt()).coerceIn(0,
                        (resources.displayMetrics.widthPixels - diameter).coerceAtLeast(0))
                    params.y = (startY + (event.rawY - downY).toInt()).coerceIn(0,
                        (resources.displayMetrics.heightPixels - diameter).coerceAtLeast(0))
                    runCatching { manager.updateViewLayout(dot, params) }
                    true
                }
                else -> true
            }
        }
        manager.addView(dot, params)
        marker = dot
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        marker?.let { runCatching { wm?.removeView(it) } }
        marker = null
        super.onDestroy()
    }

    companion object { const val ACTION_STOP = "com.explapp.shortcut.VIDEO_POINTER_STOP" }
}

class ScreenVideoRecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var output: Uri? = null
    private val main = Handler(Looper.getMainLooper())
    private var startElapsed: Long = 0L
    private val maxDuration = Runnable { stopRecording() }
    private var latestSavedUri: Uri? = null
    private var pointerEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE) {
            if (isRecording && !isPaused && Build.VERSION.SDK_INT >= 24) {
                runCatching { recorder?.pause(); isPaused = true; updateRecordingNotification() }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESUME) {
            if (isRecording && isPaused && Build.VERSION.SDK_INT >= 24) {
                runCatching { recorder?.resume(); isPaused = false; updateRecordingNotification() }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TOGGLE_POINTER) {
            if (isRecording) {
                pointerEnabled = !pointerEnabled
                updatePointer()
                updateRecordingNotification()
            }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || isRecording) return START_NOT_STICKY
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null || intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val startingNotice = recordingNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2217, startingNotice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(2217, startingNotice)
            startRecording(intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED), data)
        } catch (error: Exception) {
            notifyResult(false, error.message)
            cleanup(false)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun local(en: String, ar: String) =
        if (resources.configuration.locales[0].language == "ar") ar else en

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, local("Screen recordings", "تسجيلات الشاشة"), NotificationManager.IMPORTANCE_LOW)
            )
            notifications.createNotificationChannel(
                NotificationChannel(RESULT_CHANNEL, local("Saved screen videos", "مقاطع الشاشة المحفوظة"),
                    NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
    private fun startRecording(code: Int, consentData: Intent) {
        val metrics = resources.displayMetrics
        val sourceWidth = metrics.widthPixels
        val sourceHeight = metrics.heightPixels
        // H.264 requires even dimensions and manageable hardware encoder sizes.
        val scale = (1280f / maxOf(sourceWidth, sourceHeight)).coerceAtMost(1f)
        val width = ((sourceWidth*scale).toInt() / 2 * 2).coerceAtLeast(2)
        val height = ((sourceHeight*scale).toInt() / 2 * 2).coerceAtLeast(2)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Shortcut_video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Shortcut")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Cannot create video output")
        } else {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "Shortcut_video_${System.currentTimeMillis()}.mp4")
            Uri.fromFile(file)
        }
        output = uri
        descriptor = if (uri.scheme == "content") contentResolver.openFileDescriptor(uri, "w")
            else ParcelFileDescriptor.open(File(requireNotNull(uri.path)),
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)
        val fd = descriptor ?: error("Cannot open video output")
        val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = media
        media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        media.setVideoSize(width, height)
        media.setVideoFrameRate(24)
        media.setVideoEncodingBitRate(3_000_000)
        media.setOutputFile(fd.fileDescriptor)
        media.prepare()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = manager.getMediaProjection(code, consentData) ?: error("Screen consent failed")
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { if (isRecording) stopRecording() } }
        }, main)
        display = p.createVirtualDisplay(
            "ShortcutVideo", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, media.surface, null, null
        ) ?: error("Could not create video display")
        media.start()
        pointerEnabled = getSharedPreferences("screen_video", MODE_PRIVATE).getBoolean("pointer", false)
        startElapsed = android.os.SystemClock.elapsedRealtime()
        isRecording = true
        isPaused = false
        updatePointer()
        updateRecordingNotification()
        main.postDelayed(maxDuration, 30 * 60 * 1000L)
    }
    private fun stopRecording() {
        main.removeCallbacks(maxDuration)
        val hadRecording = isRecording
        isRecording = false
        isPaused = false
        latestSavedUri = output
        val seconds = android.os.SystemClock.elapsedRealtime() - startElapsed
        var saved = false
        if (hadRecording) {
            saved = runCatching { recorder?.stop(); seconds > 500L }.getOrDefault(false)
        }
        cleanup(saved)
        notifyResult(saved, if (saved) null else "Recording stopped before a valid video could be saved")
        stopSelf()
    }
    private fun cleanup(keep: Boolean) {
        stopPointer()
        runCatching { display?.release() }; display=null
        runCatching { projection?.stop() }; projection=null
        runCatching { recorder?.reset() }; runCatching { recorder?.release() }; recorder=null
        runCatching { descriptor?.close() }; descriptor=null
        output?.let { uri ->
            if (keep && Build.VERSION.SDK_INT >= 29 && uri.scheme=="content") {
                runCatching {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                }
            } else if (!keep) {
                if (uri.scheme=="content") runCatching { contentResolver.delete(uri, null, null) }
                else runCatching { File(uri.path.orEmpty()).delete() }
            }
        }
        output=null
        isRecording=false
        isPaused=false
        if (FloatingVideoBubbleService.isActive) {
            startService(Intent(this, FloatingVideoBubbleService::class.java)
                .setAction(FloatingVideoBubbleService.ACTION_SHOW))
        }
    }

    private fun updatePointer() {
        if (VideoOverlayPolicy.showPointer(isRecording, pointerEnabled, Settings.canDrawOverlays(this))) {
            startService(Intent(this, VideoExplanationPointerService::class.java))
        } else stopPointer()
    }

    private fun stopPointer() {
        stopService(Intent(this, VideoExplanationPointerService::class.java))
    }
    private fun updateRecordingNotification() {
        createChannel()
        getSystemService(NotificationManager::class.java).notify(2217, recordingNotification())
    }

    private fun recordingNotification(): Notification {
        createChannel()
        val stop = PendingIntent.getService(
            this, 21, Intent(this, ScreenVideoRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val toggle = PendingIntent.getService(
            this, 22, Intent(this, ScreenVideoRecordingService::class.java).setAction(pauseAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pointerToggle = PendingIntent.getService(
            this, 23, Intent(this, ScreenVideoRecordingService::class.java).setAction(ACTION_TOGGLE_POINTER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(if (isPaused) local("Screen recording paused", "تسجيل الشاشة متوقف مؤقتًا")
                else local("Recording screen", "جارٍ تسجيل الشاشة"))
            .setContentText(local("Use Pause / Resume / Finish and save", "إيقاف مؤقت / استئناف / إنهاء وحفظ"))
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, if (isPaused)
                local("Resume", "استئناف") else local("Pause", "إيقاف مؤقت"), toggle)
            .addAction(android.R.drawable.ic_menu_save,
                local("Finish and save", "إنهاء وحفظ"), stop)
            .addAction(android.R.drawable.ic_menu_edit,
                if (pointerEnabled) local("Hide pointer", "إخفاء المؤشر") else local("Show pointer", "إظهار المؤشر"), pointerToggle)
            .build()
        return notice
    }

    private fun notifyResult(saved: Boolean, detail: String?) {
        val uri = latestSavedUri
        val builder = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(if (saved) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (saved) local("Video saved", "تم حفظ الفيديو") else local("Screen recording failed", "فشل تسجيل الشاشة"))
            .setContentText(if (saved) local("Open or share your MP4", "فتح أو مشاركة ملف MP4")
                else detail.orEmpty().take(120))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (saved && uri != null) {
            // File:// URIs cannot be shared across apps on Android 7-9.
            // Export them through the app's private FileProvider instead.
            val shareUri = if (uri.scheme == "file") {
                runCatching {
                    FileProvider.getUriForFile(
                        this, "${packageName}.fileprovider",
                        File(requireNotNull(uri.path)),
                    )
                }.getOrNull()
            } else uri
            if (shareUri == null) {
                builder.setContentText(local(
                    "Saved in Shortcut Movies, but file sharing is not available",
                    "حُفظ في مجلد فيديو Shortcut، لكن المشاركة غير متاحة",
                ))
            } else {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shareUri, "video/mp4")
                clipData = android.content.ClipData.newRawUri("Shortcut video", shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                clipData = android.content.ClipData.newRawUri("Shortcut video", shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val openPending = PendingIntent.getActivity(
                this, 223, Intent.createChooser(view, local("Open video", "فتح الفيديو"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val sharePending = PendingIntent.getActivity(
                this, 224, Intent.createChooser(share, local("Share video", "مشاركة الفيديو"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_menu_view,
                local("Open", "فتح"), openPending)
            builder.addAction(android.R.drawable.ic_menu_share,
                local("Share", "مشاركة"), sharePending)
            builder.setContentIntent(openPending)
            }
        }
        getSystemService(NotificationManager::class.java).notify(2218, builder.build())
        latestSavedUri = null
    }
    override fun onDestroy() {
        main.removeCallbacks(maxDuration)
        if (isRecording) stopRecording() else cleanup(false)
        super.onDestroy()
    }
    companion object {
        const val ACTION_START = "com.explapp.shortcut.VIDEO_START"
        const val ACTION_STOP = "com.explapp.shortcut.VIDEO_STOP"
        const val ACTION_PAUSE = "com.explapp.shortcut.VIDEO_PAUSE"
        const val ACTION_RESUME = "com.explapp.shortcut.VIDEO_RESUME"
        const val ACTION_TOGGLE_POINTER = "com.explapp.shortcut.VIDEO_TOGGLE_POINTER"
        const val EXTRA_RESULT = "result"
        const val EXTRA_DATA = "consent"
        private const val CHANNEL = "shortcut_video_capture"
        private const val RESULT_CHANNEL = "shortcut_video_results"
        @Volatile var isRecording = false
            private set
        @Volatile var isPaused = false
            private set
    }
}
