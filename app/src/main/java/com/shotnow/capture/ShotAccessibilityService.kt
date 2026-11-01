package com.shotnow.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.shotnow.state.AppState

/**
 * Empty accessibility service whose only job is to expose takeScreenshot().
 * The user enables it once in system settings; no per-capture consent.
 */
class ShotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppState.setAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        AppState.setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        AppState.setAccessibilityConnected(false)
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ShotAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
