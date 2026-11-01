package com.shotnow.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Frozen screenshot with a drag-to-draw rectangle. Releasing a valid
 * rectangle reports it immediately; there is no confirm step.
 *
 * When [stylusOnly] is true, finger and palm events are consumed but ignored.
 */
class RegionSelectorView(context: Context) : View(context) {

    var onSelectionFinished: ((Rect) -> Unit)? = null
    var onSelectionRejected: (() -> Unit)? = null
    var stylusOnly: Boolean = false

    var accentColor: Int = 0xFFE97845.toInt()
        set(value) {
            field = value
            accentPaint.color = value
            invalidate()
        }

    private var bitmap: Bitmap? = null
    private val selection = RectF()
    private var dragging = false
    private var activePointerId = -1
    private var startX = 0f
    private var startY = 0f

    private val density = resources.displayMetrics.density
    private val minSize = dp(10f)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val grayPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val scrimPaint = Paint().apply { color = 0x66000000 }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.WHITE
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        color = 0xFFE97845.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(dp(4f), 0f, dp(1f), Color.BLACK)
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3000000.toInt()
    }

    fun setImage(image: Bitmap) {
        bitmap = image
        selection.setEmpty()
        dragging = false
        activePointerId = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Screenshot mode: whole frozen frame is desaturated and dimmed.
        canvas.drawBitmap(image, null, dst, grayPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        if (selection.width() < 1f || selection.height() < 1f) return

        // The selected region shows the original colors again.
        val s = selection
        canvas.save()
        canvas.clipRect(s)
        canvas.drawBitmap(image, null, dst, bitmapPaint)
        canvas.restore()

        canvas.drawRect(s, borderPaint)
        drawCorners(canvas, s)
        drawSizeLabel(canvas, image)
    }

    private fun drawCorners(canvas: Canvas, s: RectF) {
        val arm = dp(16f).coerceAtMost(min(s.width(), s.height()) / 3f)
        val path = android.graphics.Path().apply {
            moveTo(s.left, s.top + arm)
            lineTo(s.left, s.top)
            lineTo(s.left + arm, s.top)

            moveTo(s.right - arm, s.top)
            lineTo(s.right, s.top)
            lineTo(s.right, s.top + arm)

            moveTo(s.right, s.bottom - arm)
            lineTo(s.right, s.bottom)
            lineTo(s.right - arm, s.bottom)

            moveTo(s.left + arm, s.bottom)
            lineTo(s.left, s.bottom)
            lineTo(s.left, s.bottom - arm)
        }
        canvas.drawPath(path, accentPaint)
    }

    private fun drawSizeLabel(canvas: Canvas, image: Bitmap) {
        if (width <= 0 || height <= 0) return
        val pxWidth = (selection.width() / width * image.width).roundToInt()
        val pxHeight = (selection.height() / height * image.height).roundToInt()
        val label = "$pxWidth × $pxHeight"
        val textWidth = labelPaint.measureText(label)
        val textHeight = labelPaint.textSize
        val padding = dp(7f)
        var left = selection.left
        var baseline = selection.top - dp(9f)
        if (baseline - textHeight < 0f) {
            baseline = selection.top + textHeight + dp(9f)
        }
        if (left + textWidth + padding * 2 > width) {
            left = width - textWidth - padding * 2
        }
        left = left.coerceAtLeast(0f)
        val background = RectF(
            left,
            baseline - textHeight - dp(4f),
            left + textWidth + padding * 2,
            baseline + dp(4f),
        )
        canvas.drawRoundRect(background, dp(6f), dp(6f), labelBackground)
        canvas.drawText(label, left + padding, baseline, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        return if (stylusOnly) handleStylusTouch(event) else handleAnyTouch(event)
    }

    private fun handleAnyTouch(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startAt(x, y)
                dragging = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    updateAt(x, y)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    updateAt(x, y)
                    finishSelection()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelSelection()
                return true
            }
        }
        return true
    }

    private fun handleStylusTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPen(event, 0)) {
                    activePointerId = event.getPointerId(0)
                    startAt(
                        event.getX(0).coerceIn(0f, width.toFloat()),
                        event.getY(0).coerceIn(0f, height.toFloat()),
                    )
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!dragging) {
                    val index = findPenPointer(event)
                    if (index >= 0) {
                        activePointerId = event.getPointerId(index)
                        startAt(
                            event.getX(index).coerceIn(0f, width.toFloat()),
                            event.getY(index).coerceIn(0f, height.toFloat()),
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateActivePointer(event)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (dragging && event.getPointerId(index) == activePointerId) {
                    updateAt(
                        event.getX(index).coerceIn(0f, width.toFloat()),
                        event.getY(index).coerceIn(0f, height.toFloat()),
                    )
                    finishSelection()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    updateActivePointer(event)
                    finishSelection()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelSelection()
                return true
            }
        }
        return true
    }

    private fun updateActivePointer(event: MotionEvent) {
        if (!dragging) return
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        updateAt(
            event.getX(index).coerceIn(0f, width.toFloat()),
            event.getY(index).coerceIn(0f, height.toFloat()),
        )
    }

    private fun isPen(event: MotionEvent, index: Int): Boolean {
        val tool = event.getToolType(index)
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun findPenPointer(event: MotionEvent): Int {
        for (index in 0 until event.pointerCount) {
            if (isPen(event, index)) return index
        }
        return -1
    }

    private fun startAt(x: Float, y: Float) {
        startX = x
        startY = y
        dragging = true
        selection.set(x, y, x, y)
        invalidate()
    }

    private fun updateAt(x: Float, y: Float) {
        selection.set(
            min(startX, x),
            min(startY, y),
            max(startX, x),
            max(startY, y),
        )
        invalidate()
    }

    private fun finishSelection() {
        dragging = false
        activePointerId = -1
        if (selection.width() < minSize || selection.height() < minSize) {
            selection.setEmpty()
            invalidate()
            onSelectionRejected?.invoke()
            return
        }
        val rect = selectionInBitmap()
        if (rect == null) {
            selection.setEmpty()
            invalidate()
            onSelectionRejected?.invoke()
        } else {
            onSelectionFinished?.invoke(rect)
        }
    }

    private fun cancelSelection() {
        dragging = false
        activePointerId = -1
        selection.setEmpty()
        invalidate()
    }

    private fun selectionInBitmap(): Rect? {
        val image = bitmap ?: return null
        if (width <= 0 || height <= 0) return null
        val scaleX = image.width / width.toFloat()
        val scaleY = image.height / height.toFloat()
        val left = floor(selection.left * scaleX).toInt().coerceIn(0, image.width - 1)
        val top = floor(selection.top * scaleY).toInt().coerceIn(0, image.height - 1)
        val right = ceil(selection.right * scaleX).toInt().coerceIn(left + 1, image.width)
        val bottom = ceil(selection.bottom * scaleY).toInt().coerceIn(top + 1, image.height)
        if (right - left < 2 || bottom - top < 2) return null
        return Rect(left, top, right, bottom)
    }

    private fun dp(value: Float): Float = value * density
}
