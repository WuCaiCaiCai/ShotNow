package com.shotnow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.shotnow.MainActivity
import com.shotnow.R
import com.shotnow.capture.ScreenCaptureController
import com.shotnow.capture.ShotAccessibilityService
import com.shotnow.clipboard.ClipboardHelper
import com.shotnow.overlay.FloatingBallView
import com.shotnow.overlay.SelectionOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

class FloatingBallService : Service() {

    companion object {
        const val ACTION_START = "com.shotnow.action.START"
        const val ACTION_STOP = "com.shotnow.action.STOP"
        const val ACTION_UPDATE_SIZE = "com.shotnow.action.UPDATE_SIZE"

        const val MIN_BALL_SIZE = 34
        const val MAX_BALL_SIZE = 72
        const val DEFAULT_BALL_SIZE = 48

        private const val CHANNEL_ID = "shotnow_ball"
        private const val NOTIFICATION_ID = 1001
        private const val SETTINGS_PREFS = "shotnow_settings"
        private const val KEY_BALL_SIZE = "ball_size_dp"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun readBallSize(context: android.content.Context): Int {
            return context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
                .getInt(KEY_BALL_SIZE, DEFAULT_BALL_SIZE)
                .coerceIn(MIN_BALL_SIZE, MAX_BALL_SIZE)
        }

        fun writeBallSize(context: android.content.Context, sizeDp: Int) {
            context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BALL_SIZE, sizeDp.coerceIn(MIN_BALL_SIZE, MAX_BALL_SIZE))
                .apply()
        }
    }

    private enum class State { IDLE, CAPTURING, SELECTING, COPYING }

    private lateinit var windowManager: WindowManager
    private lateinit var mainHandler: Handler
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var ballView: FloatingBallView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var selectionOverlay: SelectionOverlay? = null
    private var captureController: ScreenCaptureController? = null
    private var state = State.IDLE
    private var destroying = false

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (state == State.SELECTING) {
                selectionOverlay?.dismiss()
                selectionOverlay = null
                restoreIdle()
                toast("屏幕方向改变，已取消本次截图")
            }
            clampBall()
        }

        override fun onLowMemory() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        mainHandler = Handler(Looper.getMainLooper())
        createChannel()
        application.registerComponentCallbacks(configCallbacks)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startAsForeground()
                if (!Settings.canDrawOverlays(this)) {
                    toast("请先授予悬浮窗权限")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!ShotAccessibilityService.isConnected()) {
                    toast("请先在设置中开启 ShotNow 截图服务")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (isRunning) return START_NOT_STICKY

                captureController = ScreenCaptureController()
                if (!showBall()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                isRunning = true
            }

            ACTION_UPDATE_SIZE -> {
                if (isRunning) updateBallSize() else stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingBallService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shot)
            .setContentTitle("ShotNow 悬浮球运行中")
            .setContentText("点悬浮球 → 拖出矩形 → 自动复制")
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "悬浮球",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持悬浮球运行"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showBall(): Boolean {
        val size = dp(readBallSize(this))
        val bounds = screenMetrics()
        val prefs = getSharedPreferences("ball", MODE_PRIVATE)
        val maxX = (bounds.x - size).coerceAtLeast(0)
        val maxY = (bounds.y - size).coerceAtLeast(0)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", maxX).coerceIn(0, maxX)
            y = prefs.getInt("y", bounds.y / 3).coerceIn(0, maxY)
        }
        val view = FloatingBallView(this, params, ::screenMetrics) { x, y ->
            prefs.edit().putInt("x", x).putInt("y", y).apply()
        }
        view.setOnClickListener { onBallTapped() }
        return try {
            windowManager.addView(view, params)
            ballView = view
            ballParams = params
            true
        } catch (t: Throwable) {
            toast("无法显示悬浮球：${t.message}")
            false
        }
    }

    private fun onBallTapped() {
        if (state != State.IDLE) return
        val controller = captureController ?: run {
            toast("截图服务未就绪")
            return
        }
        state = State.CAPTURING
        ballView?.visibility = View.GONE

        mainHandler.postDelayed({
            if (state != State.CAPTURING) return@postDelayed
            controller.capture { result ->
                val bitmap = result.getOrNull()
                if (bitmap == null) {
                    restoreIdle()
                    toast(result.exceptionOrNull()?.message ?: "截图失败")
                    if (!ShotAccessibilityService.isConnected()) {
                        mainHandler.postDelayed({ stopSelf() }, 600)
                    }
                } else {
                    showSelection(bitmap)
                }
            }
        }, 160)
    }

    private fun showSelection(bitmap: Bitmap) {
        state = State.SELECTING
        val overlay = SelectionOverlay(this, windowManager)
        selectionOverlay = overlay
        try {
            overlay.show(
                bitmap = bitmap,
                onSelected = { rect ->
                    selectionOverlay = null
                    copyRegion(bitmap, rect)
                },
                onCancel = {
                    selectionOverlay = null
                    restoreIdle()
                },
                onRejected = {
                    toast("选区太小，请重新拖动")
                },
            )
        } catch (t: Throwable) {
            selectionOverlay = null
            restoreIdle()
            toast("无法显示选区：${t.message}")
        }
    }

    private fun copyRegion(source: Bitmap, rect: Rect) {
        if (rect.width() < 1 || rect.height() < 1) {
            restoreIdle()
            return
        }
        state = State.COPYING
        val width = rect.width()
        val height = rect.height()

        ioExecutor.execute {
            val crop = try {
                Bitmap.createBitmap(source, rect.left, rect.top, width, height)
            } catch (t: Throwable) {
                null
            }
            if (crop == null) {
                mainHandler.post {
                    if (destroying) return@post
                    restoreIdle()
                    toast("裁剪失败")
                }
                return@execute
            }

            val directory = File(cacheDir, "shots").apply { mkdirs() }
            val name = UUID.randomUUID().toString().replace("-", "")
            val file = File(directory, "$name.png")
            val saved = try {
                FileOutputStream(file).use { output ->
                    crop.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            } catch (t: Throwable) {
                false
            }
            cleanupShots(directory, file)
            mainHandler.post {
                if (destroying) return@post
                if (saved) {
                    val copied = ClipboardHelper.copyImage(this, file)
                    toast(if (copied) "已复制 $width × $height" else "写入剪贴板失败")
                } else {
                    toast("保存截图失败")
                }
                restoreIdle()
            }
        }
    }

    private fun cleanupShots(directory: File, keep: File) {
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (file != keep && now - file.lastModified() > 60 * 60 * 1000L) {
                runCatching { file.delete() }
            }
        }
    }

    private fun restoreIdle() {
        if (destroying) return
        state = State.IDLE
        ballView?.visibility = View.VISIBLE
    }

    private fun updateBallSize() {
        val view = ballView ?: return
        val params = ballParams ?: return
        val newSize = dp(readBallSize(this))
        if (params.width == newSize) return
        val delta = (newSize - params.width) / 2
        params.width = newSize
        params.height = newSize
        params.x -= delta
        params.y -= delta
        runCatching { windowManager.updateViewLayout(view, params) }
        clampBall()
    }

    private fun clampBall() {
        val view = ballView ?: return
        val params = ballParams ?: return
        val bounds = screenMetrics()
        val width = if (view.width > 0) view.width else params.width
        val height = if (view.height > 0) view.height else params.height
        params.x = params.x.coerceIn(0, (bounds.x - width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (bounds.y - height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun screenMetrics(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Point(metrics.widthPixels, metrics.heightPixels)
        }
    }

    override fun onDestroy() {
        destroying = true
        runCatching { application.unregisterComponentCallbacks(configCallbacks) }
        selectionOverlay?.dismiss()
        selectionOverlay = null
        ballView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        ballView = null
        ballParams = null
        captureController?.shutdown()
        captureController = null
        ioExecutor.shutdown()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
