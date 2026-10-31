package com.shotnow.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen overlay shown on top of the frozen screenshot. The user drags
 * a rectangle and releasing it copies immediately; back cancels.
 */
class SelectionOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var root: FrameLayout? = null
    private var showing = false

    fun show(
        bitmap: Bitmap,
        onSelected: (Rect) -> Unit,
        onCancel: () -> Unit,
        onRejected: () -> Unit,
    ) {
        if (showing) return
        val density = context.resources.displayMetrics.density

        fun dp(value: Float): Int = (value * density).toInt()

        fun closeThen(action: () -> Unit) {
            dismiss()
            action()
        }

        val frame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) closeThen(onCancel)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setBackgroundColor(Color.BLACK)
        frame.isFocusable = true
        frame.isFocusableInTouchMode = true

        val regionSelector = RegionSelectorView(context).apply {
            setImage(bitmap)
            onSelectionFinished = { rect -> closeThen { onSelected(rect) } }
            onSelectionRejected = onRejected
        }
        frame.addView(
            regionSelector,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val hint = TextView(context).apply {
            text = "截图模式 · 拖出矩形，松手自动复制\n按返回键取消"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setLineSpacing(dp(4f).toFloat(), 1f)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        }
        frame.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(28f) },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowManager.addView(frame, params)
        showing = true
        root = frame

        frame.alpha = 0f
        frame.animate().alpha(1f).setDuration(110).start()
        frame.post { frame.requestFocus() }
    }

    fun dismiss() {
        val frame = root ?: return
        showing = false
        root = null
        runCatching { windowManager.removeViewImmediate(frame) }
    }
}
