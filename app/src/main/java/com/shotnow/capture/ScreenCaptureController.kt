package com.shotnow.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import java.util.concurrent.Executors

/**
 * Grabs a screenshot through the accessibility API instead of MediaProjection.
 * That is what avoids the "start recording?" consent dialog on every session.
 */
class ScreenCaptureController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var busy = false

    fun capture(callback: (Result<Bitmap>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(Result.failure(IllegalStateException("需要 Android 11 及以上")))
            return
        }
        if (busy) {
            callback(Result.failure(IllegalStateException("上一张截图还未完成")))
            return
        }
        val service = ShotAccessibilityService.instance
        if (service == null) {
            callback(Result.failure(IllegalStateException("无障碍截图服务未开启")))
            return
        }
        busy = true

        fun deliver(result: Result<Bitmap>) {
            busy = false
            mainHandler.post { callback(result) }
        }

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                worker,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (t: Throwable) {
                            Log.e(TAG, "convert screenshot failed", t)
                            null
                        } finally {
                            buffer.close()
                        }
                        if (bitmap != null) {
                            deliver(Result.success(bitmap))
                        } else {
                            deliver(Result.failure(IllegalStateException("截图解码失败")))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        deliver(Result.failure(IllegalStateException(errorMessage(errorCode))))
                    }
                },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "takeScreenshot failed", t)
            busy = false
            callback(Result.failure(t))
        }
    }

    fun shutdown() {
        worker.shutdown()
    }

    private fun errorMessage(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "操作太快了，请稍后再试"

        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "无障碍服务已被关闭"

        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            "当前页面禁止截图"

        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
            "显示器无效"

        else -> "截图失败（错误码 $code）"
    }

    companion object {
        private const val TAG = "ShotNow"
    }
}
