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
 */
class RegionSelectorView(context: Context) : View(context) {

    var onSelectionFinished: ((Rect) -> Unit)? = null
    var onSelectionRejected: (() -> Unit)? = null

    private var bitmap: Bitmap? = null
    private val selection = RectF()
    private var dragging = false
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
        color = 0xFF4C8DFF.toInt()
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
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                dragging = true
                selection.set(x, y, x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    selection.set(
                        min(startX, x),
                        min(startY, y),
                        max(startX, x),
                        max(startY, y),
                    )
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    if (selection.width() < minSize || selection.height() < minSize) {
                        selection.setEmpty()
                        invalidate()
                        onSelectionRejected?.invoke()
                    } else {
                        val rect = selectionInBitmap()
                        if (rect == null) {
                            selection.setEmpty()
                            invalidate()
                            onSelectionRejected?.invoke()
                        } else {
                            onSelectionFinished?.invoke(rect)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                selection.setEmpty()
                invalidate()
                return true
            }
        }
        return true
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
