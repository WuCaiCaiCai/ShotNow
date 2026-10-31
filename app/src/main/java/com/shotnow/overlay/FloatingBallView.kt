package com.shotnow.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.min

/**
 * A compact frosted-glass floating ball. Tap = start capture; drag = move.
 * The visible circle is slightly smaller than the touch window.
 */
class FloatingBallView(
    context: Context,
    private val params: WindowManager.LayoutParams,
    private val boundsProvider: () -> Point,
    private val onMoved: (Int, Int) -> Unit,
) : View(context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1F000000
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x80FFFFFF.toInt()
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        strokeCap = Paint.Cap.ROUND
        color = 0x4DFFFFFF
    }
    private val glyphShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x59000000
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.7f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glassPaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            intArrayOf(
                0x66FFFFFF,
                0x2EFFFFFF,
                0x0FFFFFFF,
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(5f)

        canvas.drawCircle(cx, cy, radius, basePaint)
        canvas.drawCircle(cx, cy, radius, glassPaint)
        canvas.drawCircle(cx, cy, radius, rimPaint)

        val highlightBounds = RectF(
            cx - radius * 0.82f,
            cy - radius * 0.82f,
            cx + radius * 0.82f,
            cy + radius * 0.82f,
        )
        canvas.drawArc(highlightBounds, 202f, 52f, false, highlightPaint)

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

        canvas.save()
        canvas.translate(dp(0.8f), dp(0.8f))
        canvas.drawPath(path, glyphShadowPaint)
        canvas.restore()
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
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
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
                animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                if (dragging) {
                    clampAndSave()
                } else {
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(110).start()
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
