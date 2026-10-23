package com.explapp.shortcut.tools

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
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
            text = if (ar) "اضغط الزر العائم لطلب موافقة تصوير الشاشة. ثم اضغطه مرة ثانية لإيقاف التسجيل وحفظ MP4 دون صوت. لا يمكن تجاوز موافقة أندرويد."
                else "Tap the floating button to approve screen capture; tap again to stop and save a silent MP4. Android requires consent."
            setPadding(0, 18, 0, 18)
        })
        fun add(text: String, act: () -> Unit) {
            layout.addView(Button(this).apply { this.text = text; setOnClickListener { act() } })
        }
        add(if (ar) "تشغيل الزر العائم" else "Enable floating button") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, if (ar) "اسمح بالظهور فوق التطبيقات، ثم عد إلى هنا" else "Allow display over other apps, then return", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, FloatingVideoBubbleService::class.java))
                finish()
            }
        }
        add(if (ar) "بدء تسجيل بدون زر عائم" else "Start recording without bubble") {
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
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenVideoRecordingService::class.java)
                .setAction(ScreenVideoRecordingService.ACTION_START)
                .putExtra(ScreenVideoRecordingService.EXTRA_RESULT, result.resultCode)
                .putExtra(ScreenVideoRecordingService.EXTRA_DATA, result.data)
            try {
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            } catch (error: Exception) {
                Toast.makeText(this, error.message ?: "Cannot start recording", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    private var bubble: TextView? = null
    private val main = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            bubble?.let { view ->
                val recording = ScreenVideoRecordingService.isRecording
                view.text = if (recording) "■ " + local("Stop video", "إيقاف الفيديو")
                    else "● " + local("Record video", "تسجيل فيديو")
                (view.background as? GradientDrawable)?.setColor(
                    if (recording) Color.rgb(181, 38, 50) else Color.rgb(32, 111, 178)
                )
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
        if (bubble == null) showBubble()
        return START_STICKY
    }
    private fun local(en: String, ar: String) =
        if (resources.configuration.locales[0].language == "ar") ar else en

    private fun showBubble() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        window = wm
        val d = resources.displayMetrics.density
        val view = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding((13*d).toInt(), (10*d).toInt(), (13*d).toInt(), (10*d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 28*d
                setColor(Color.rgb(32,111,178))
            }
            elevation = 8*d
            contentDescription = local("Floating screen recording: tap to start or stop", "تسجيل الشاشة العائم: اضغط للبدء أو الإيقاف")
        }
        val options = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0=options.x; y0=options.y; touchX=event.rawX; touchY=event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    options.x = (x0 + (event.rawX-touchX).toInt()).coerceAtLeast(0)
                    options.y = (y0 + (event.rawY-touchY).toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(view, options) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX-touchX) < 12*d && abs(event.rawY-touchY) < 12*d) {
                        if (ScreenVideoRecordingService.isRecording) {
                            startService(Intent(this, ScreenVideoRecordingService::class.java)
                                .setAction(ScreenVideoRecordingService.ACTION_STOP))
                        } else {
                            startActivity(Intent(this, VideoRecordingPermissionActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(view, options)
        bubble=view
        main.post(refresh)
    }
    override fun onDestroy() {
        main.removeCallbacks(refresh)
        bubble?.let { view -> runCatching { window?.removeView(view) } }
        bubble=null
        super.onDestroy()
    }
    companion object { const val ACTION_DISABLE = "com.explapp.shortcut.VIDEO_BUBBLE_DISABLE" }
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

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
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
        val stopIntent = PendingIntent.getService(
            this, 21,
            Intent(this, ScreenVideoRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(local("Recording screen", "جارٍ تسجيل الشاشة"))
            .setContentText(local("Tap Stop to save MP4", "اضغط إيقاف لحفظ MP4"))
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, local("Stop and save", "إيقاف وحفظ"), stopIntent)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2217, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(2217, notice)
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, local("Screen recordings", "تسجيلات الشاشة"), NotificationManager.IMPORTANCE_LOW)
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
        startElapsed = android.os.SystemClock.elapsedRealtime()
        isRecording = true
        main.postDelayed(maxDuration, 30 * 60 * 1000L)
    }
    private fun stopRecording() {
        main.removeCallbacks(maxDuration)
        val hadRecording = isRecording
        isRecording = false
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
    }
    private fun notifyResult(saved: Boolean, detail: String?) {
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (saved) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (saved) local("Video saved", "تم حفظ الفيديو") else local("Screen recording failed", "فشل تسجيل الشاشة"))
            .setContentText(if (saved) local("Find your MP4 in Movies/Shortcut", "ستجد MP4 في مجلد Movies/Shortcut")
                else detail.orEmpty().take(120))
            .setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(2218, notice)
    }
    override fun onDestroy() {
        main.removeCallbacks(maxDuration)
        if (isRecording) stopRecording() else cleanup(false)
        super.onDestroy()
    }
    companion object {
        const val ACTION_START = "com.explapp.shortcut.VIDEO_START"
        const val ACTION_STOP = "com.explapp.shortcut.VIDEO_STOP"
        const val EXTRA_RESULT = "result"
        const val EXTRA_DATA = "consent"
        private const val CHANNEL = "shortcut_video_capture"
        @Volatile var isRecording = false
            private set
    }
}
