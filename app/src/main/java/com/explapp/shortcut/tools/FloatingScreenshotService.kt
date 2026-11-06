package com.explapp.shortcut.tools

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class FloatingScreenshotService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var bubble: TextView? = null
    private val expiry = Runnable {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_MODE, "off").remove(KEY_EXPIRES_AT).apply()
        stopSelf()
    }
    private val safetyRestore = Runnable {
        bubble?.visibility = android.view.View.VISIBLE
        chooserOpening = false
    }
    private var chooserOpening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_BUBBLE) {
            if (bubble == null || !Settings.canDrawOverlays(this)) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            main.removeCallbacks(safetyRestore)
            bubble?.visibility = android.view.View.VISIBLE
            chooserOpening = false
            return START_STICKY
        }
        if (intent?.action == ACTION_OFF) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_MODE, "off").remove(KEY_EXPIRES_AT).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mode = intent?.getStringExtra(EXTRA_MODE)
            ?: getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_MODE, "off")
            ?: "off"
        if (mode !in setOf("always", "temporary")) {
            stopSelf()
            return START_NOT_STICKY
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply()
        main.removeCallbacks(expiry)
        if (mode == "temporary") {
            val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_EXPIRES_AT, 0L)
            val remaining = if (intent?.hasExtra(EXTRA_DURATION_MS) == true) {
                val duration = intent.getLongExtra(EXTRA_DURATION_MS, 300_000L).coerceIn(10_000L, 86_400_000L)
                val expires = System.currentTimeMillis() + duration
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_EXPIRES_AT, expires).apply()
                duration
            } else (saved - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining == 0L) {
                expiry.run()
                return START_NOT_STICKY
            }
            main.postDelayed(expiry, remaining)
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_EXPIRES_AT).apply()
        }
        if (bubble == null) createBubble()
        return START_STICKY
    }

    private fun createBubble() {
        windows = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            text = if (resources.configuration.locales[0].language == "ar") "لقطة شاشة" else "Screenshot"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 100, 148))
                cornerRadius = 28 * density
            }
            elevation = 8 * density
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - 145 * density).toInt().coerceAtLeast(0)
            y = (resources.displayMetrics.heightPixels / 3)
        }
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        view.contentDescription = if (resources.configuration.locales[0].language == "ar") "زر لقطة شاشة: اضغط لاختيار الحفظ أو المشاركة" else "Screenshot button: tap to choose save or share"
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    downX = event.rawX; downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + (event.rawX - downX).toInt()).coerceIn(0, (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0))
                    params.y = (startY + (event.rawY - downY).toInt()).coerceIn(0, (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0))
                    runCatching { windows.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!chooserOpening && kotlin.math.abs(event.rawX - downX) < 12 * density &&
                        kotlin.math.abs(event.rawY - downY) < 12 * density) {
                        chooserOpening = true
                        // Keep the overlay out of the captured frame; the capture activity restores it.
                        view.visibility = android.view.View.INVISIBLE
                        main.removeCallbacks(safetyRestore)
                        main.postDelayed(safetyRestore, 90_000L)
                        main.postDelayed({
                            // A floating-button tap opens a concise three-choice dialog.
                            // The activity reuses an authorized projection where available.
                            val launch = Intent(this, ScreenCaptureActivity::class.java)
                                .putExtra(ScreenCaptureActivity.EXTRA_FLOATING_QUICK_CAPTURE, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { startActivity(launch) }.onFailure {
                                main.removeCallbacks(safetyRestore)
                                view.visibility = android.view.View.VISIBLE
                                chooserOpening = false
                            }
                        }, 200L)
                    }
                    true
                }
                else -> false
            }
        }
        windows.addView(view, params)
        bubble = view
    }

    override fun onDestroy() {
        main.removeCallbacks(expiry)
        main.removeCallbacks(safetyRestore)
        bubble?.let { runCatching { windows.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.explapp.shortcut.FLOATING_SCREENSHOT_SHOW"
        const val ACTION_OFF = "com.explapp.shortcut.FLOATING_SCREENSHOT_OFF"
        const val EXTRA_MODE = "mode"
        const val EXTRA_DURATION_MS = "durationMs"
        const val PREFS = "floating_screenshot"
        const val KEY_MODE = "mode"
        const val KEY_EXPIRES_AT = "expiresAtWallMs"
    }
}
