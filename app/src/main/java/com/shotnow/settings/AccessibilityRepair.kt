package com.shotnow.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.shotnow.capture.ShotAccessibilityService

/**
 * Writes accessibility settings when the app holds WRITE_SECURE_SETTINGS.
 * That permission has to be granted once through ADB:
 *
 * adb shell pm grant com.shotnow android.permission.WRITE_SECURE_SETTINGS
 */
object AccessibilityRepair {

    private const val TAG = "ShotNow"

    fun serviceComponent(context: Context): String =
        ComponentName(context, ShotAccessibilityService::class.java).flattenToString()

    fun hasWritePermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun enabledServices(context: Context): List<String> {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return emptyList()
        return raw.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
    }

    fun isServiceRegistered(context: Context): Boolean {
        val component = serviceComponent(context)
        return enabledServices(context).any { it.equals(component, ignoreCase = true) }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
    }

    fun isReady(context: Context): Boolean =
        isAccessibilityEnabled(context) && isServiceRegistered(context)

    /**
     * Makes sure our service is in the enabled list and the global switch is on.
     * Other accessibility services are preserved.
     */
    fun ensureEnabled(context: Context): Boolean {
        if (!hasWritePermission(context)) return false
        return try {
            val resolver = context.contentResolver
            val component = serviceComponent(context)
            val services = enabledServices(context).toMutableList()
            if (services.none { it.equals(component, ignoreCase = true) }) {
                services.add(component)
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    services.joinToString(":"),
                )
            }
            if (!isAccessibilityEnabled(context)) {
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ensure accessibility failed", t)
            false
        }
    }

    fun setGlobalEnabled(context: Context, enabled: Boolean): Boolean {
        if (!hasWritePermission(context)) return false
        return try {
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (enabled) 1 else 0,
            )
            true
        } catch (t: Throwable) {
            Log.e(TAG, "toggle accessibility failed", t)
            false
        }
    }
}
