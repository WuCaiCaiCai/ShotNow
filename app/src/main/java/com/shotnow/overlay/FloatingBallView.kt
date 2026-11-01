package com.shotnow.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * A compact floating ball. Tap = start capture; drag = move.
 * The visible circle is inset from the touch window so it stays comfortable
 * to hit without looking oversized.
 */
class FloatingBallView(
    context: Context,
    private val params: WindowManager.LayoutParams,
    private val boundsProvider: () -> Point,
    private val onMoved: (Int, Int) -> Unit,
    accentColor: Int,
    onAccentColor: Int,
    opacityPercent: Int,
) : View(context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private var accentColor: Int = accentColor
    private var onAccentColor: Int = onAccentColor
    private var opacityPercent: Int = opacityPercent.coerceIn(20, 100)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.WHITE
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        alpha = this.opacityPercent / 100f
    }

    fun applyAppearance(accent: Int, onAccent: Int, opacity: Int) {
        accentColor = accent
        onAccentColor = onAccent
        opacityPercent = opacity.coerceIn(20, 100)
        alpha = opacityPercent / 100f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(4f)

        fillPaint.color = accentColor
        glyphPaint.color = onAccentColor

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, rimPaint)

        val half = radius * 0.40f
        val arm = half * 0.58f
        val path = Path().apply {
            moveTo(cx - half, cy - half + arm)
            lineTo(cx - half, cy - half)
            lineTo(cx - half + arm, cy - half)

            moveTo(cx + half - arm, cy - half)
            lineTo(cx + half, cy - half)
            lineTo(cx + half, cy - half + arm)

            moveTo(cx + half, cy + half - arm)
            lineTo(cx + half, cy + half)
            lineTo(cx + half - arm, cy + half)

            moveTo(cx - half + arm, cy + half)
            lineTo(cx - half, cy + half)
            lineTo(cx - half, cy + half - arm)
        }
        canvas.drawPath(path, glyphPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(100)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                    dragging = true
                }
                if (dragging) {
                    params.x = (startX + dx).toInt()
                    params.y = (startY + dy).toInt()
                    applyPosition()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                releasePress()
                if (dragging) {
                    clampAndSave()
                } else {
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                releasePress()
                if (dragging) clampAndSave()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun releasePress() {
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun applyPosition() {
        runCatching { windowManager.updateViewLayout(this, params) }
    }

    private fun clampAndSave() {
        val bounds = boundsProvider()
        val maxX = (bounds.x - width).coerceAtLeast(0)
        val maxY = (bounds.y - height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
        applyPosition()
        onMoved(params.x, params.y)
    }

    private fun dp(value: Float): Float = value * density
}
