package com.shotnow

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.shotnow.capture.ShotAccessibilityService
import com.shotnow.service.FloatingBallService

class MainActivity : ComponentActivity() {

    private lateinit var statusView: TextView
    private lateinit var actionButton: TextView
    private lateinit var overlayButton: TextView
    private lateinit var accessibilityButton: TextView
    private lateinit var sizeValueView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    private var askedNotification = false
    private var pendingStart = false

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startFlow()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        if (pendingStart && !FloatingBallService.isRunning &&
            Settings.canDrawOverlays(this) && isAccessibilityEnabled()
        ) {
            pendingStart = false
            startFlow()
        }
    }

    private fun buildUi(): View {
        val bg = Color.parseColor("#0E1116")
        val muted = Color.parseColor("#8A93A6")
        val textColor = Color.parseColor("#E7EAF0")

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(44), dp(24), dp(32))
        }
        scroll.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        column.addView(label("ShotNow", 30f, Color.WHITE, bold = true))
        column.addView(label("悬浮球 · 拖框截图 · 自动复制", 14f, muted).apply {
            setPadding(0, dp(6), 0, dp(26))
        })

        val guide = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(Color.parseColor("#171B23"))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        guide.addView(label("使用方式", 13f, muted, bold = true).apply { letterSpacing = 0.08f })
        guide.addView(label(
            "1. 开启「ShotNow 截图服务」无障碍权限（只需一次）\n" +
                "2. 点「开启悬浮球」，回到任意界面点球\n" +
                "3. 屏幕变灰后拖出矩形，松手自动复制",
            15f,
            textColor,
        ).apply {
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(6).toFloat(), 1f)
        })
        column.addView(guide, fullWidth(bottom = 16))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(Color.parseColor("#171B23"))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        statusCard.addView(label("状态", 13f, muted, bold = true).apply { letterSpacing = 0.08f })
        statusView = label("", 15f, textColor).apply {
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        statusCard.addView(statusView)
        column.addView(statusCard, fullWidth(bottom = 16))

        val initialSize = FloatingBallService.readBallSize(this)
        val sizeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(Color.parseColor("#171B23"))
            setPadding(dp(18), dp(16), dp(18), dp(10))
        }
        val sizeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sizeHeader.addView(
            label("悬浮球大小", 13f, muted, bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        sizeValueView = label("$initialSize dp", 13f, Color.WHITE, bold = true)
        sizeHeader.addView(sizeValueView)
        sizeCard.addView(sizeHeader)

        val seekBar = SeekBar(this).apply {
            max = FloatingBallService.MAX_BALL_SIZE - FloatingBallService.MIN_BALL_SIZE
            progress = initialSize - FloatingBallService.MIN_BALL_SIZE
            progressTintList = ColorStateList.valueOf(Color.parseColor("#2F6BFF"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#8FBCFF"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A313D"))
            setPadding(0, dp(8), 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sizeValueView.text = "${FloatingBallService.MIN_BALL_SIZE + progress} dp"
                }

                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    val size = FloatingBallService.MIN_BALL_SIZE + (bar?.progress ?: 0)
                    FloatingBallService.writeBallSize(this@MainActivity, size)
                    if (FloatingBallService.isRunning) {
                        runCatching {
                            startService(
                                Intent(this@MainActivity, FloatingBallService::class.java)
                                    .setAction(FloatingBallService.ACTION_UPDATE_SIZE),
                            )
                        }
                    }
                }
            })
        }
        sizeCard.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        column.addView(sizeCard, fullWidth(bottom = 16))

        actionButton = button("开启悬浮球", primary = true)
        column.addView(actionButton, fullWidth(bottom = 10))

        overlayButton = button("去授予悬浮窗权限", primary = false)
        column.addView(overlayButton, fullWidth(bottom = 10))

        accessibilityButton = button("去开启无障碍截图", primary = false)
        column.addView(accessibilityButton, fullWidth(bottom = 10))

        column.addView(label(
            "无障碍权限只用于截屏，不会读取屏幕文字；开启一次后会一直有效。\n" +
                "如果开关是灰色并提示「受限设置」，请到系统设置 → 应用 → ShotNow → 右上角菜单 → 允许受限设置。",
            12f,
            Color.parseColor("#6F7789"),
        ).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(10), 0, 0)
        })

        actionButton.setOnClickListener { onActionClicked() }
        overlayButton.setOnClickListener { openOverlaySettings() }
        accessibilityButton.setOnClickListener { openAccessibilitySettings() }
        return scroll
    }

    private fun onActionClicked() {
        if (FloatingBallService.isRunning) {
            stopService(Intent(this, FloatingBallService::class.java))
            toast("已关闭悬浮球")
            mainHandler.postDelayed({ refreshUi() }, 300)
            return
        }
        startFlow()
    }

    private fun startFlow() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先开启「显示在其他应用上层」")
            openOverlaySettings()
            return
        }
        if (!isAccessibilityEnabled()) {
            toast("请开启「ShotNow 截图服务」")
            openAccessibilitySettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !askedNotification
        ) {
            askedNotification = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            val intent = Intent(this, FloatingBallService::class.java)
                .setAction(FloatingBallService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
            mainHandler.postDelayed({ refreshUi() }, 400)
        } catch (t: Throwable) {
            toast("启动失败：${t.message}")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (ShotAccessibilityService.isConnected()) return true
        val raw = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val targetClass = ShotAccessibilityService::class.java.name
        return raw.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry.trim())
            component != null &&
                component.packageName == packageName &&
                component.className == targetClass
        }
    }

    private fun openOverlaySettings() {
        pendingStart = true
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure { toast("请到系统设置中手动授权悬浮窗") }
    }

    private fun openAccessibilitySettings() {
        pendingStart = true
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            toast("请到系统设置 → 辅助功能中开启 ShotNow 截图服务")
        }
    }

    private fun refreshUi() {
        val running = FloatingBallService.isRunning
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = isAccessibilityEnabled()
        statusView.text = buildString {
            append("悬浮球  ")
            append(if (running) "运行中" else "未运行")
            append("\n无障碍截图  ")
            append(if (accessibilityOk) "已开启" else "未开启")
            append("\n悬浮窗权限  ")
            append(if (overlayOk) "已授予" else "未授予")
        }
        actionButton.text = if (running) "关闭悬浮球" else "开启悬浮球"
        overlayButton.visibility = if (overlayOk) View.GONE else View.VISIBLE
        accessibilityButton.visibility = if (accessibilityOk) View.GONE else View.VISIBLE
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun button(text: String, primary: Boolean): TextView {
        val background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) Color.parseColor("#2F6BFF") else Color.parseColor("#1C222D"))
        }
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            this.background = background
            isClickable = true
            isFocusable = true
        }
    }

    private fun card(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(color)
    }

    private fun fullWidth(bottom: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
