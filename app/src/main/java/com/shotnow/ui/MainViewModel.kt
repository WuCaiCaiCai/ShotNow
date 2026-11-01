package com.shotnow.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shotnow.service.FloatingBallService
import com.shotnow.settings.AccessibilityRepair
import com.shotnow.settings.ShotPrefs
import com.shotnow.settings.ThemeColors
import com.shotnow.state.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val running: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val autoRepairGranted: Boolean = false,
    val autoRepairEnabled: Boolean = true,
    val sPenOnly: Boolean = false,
    val ballSizeDp: Int = ShotPrefs.DEFAULT_BALL_SIZE,
    val ballOpacity: Int = ShotPrefs.DEFAULT_BALL_OPACITY,
    val themeId: String = ThemeColors.default.id,
    val versionName: String = "",
    val notificationsGranted: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState(versionName = readVersion()))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            AppState.running.collect { running ->
                _uiState.update { it.copy(running = running) }
            }
        }
        viewModelScope.launch {
            AppState.accessibilityConnected.collect { connected ->
                _uiState.update { it.copy(accessibilityConnected = connected) }
            }
        }
        refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        val autoRepairGranted = AccessibilityRepair.hasWritePermission(app)
        val autoRepairEnabled = ShotPrefs.autoRepairEnabled(app)

        if (autoRepairGranted && autoRepairEnabled && !AppState.accessibilityConnected.value) {
            AccessibilityRepair.ensureEnabled(app)
        }

        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        _uiState.value = MainUiState(
            running = AppState.running.value,
            overlayGranted = Settings.canDrawOverlays(app),
            accessibilityEnabled = AccessibilityRepair.isReady(app),
            accessibilityConnected = AppState.accessibilityConnected.value,
            autoRepairGranted = autoRepairGranted,
            autoRepairEnabled = autoRepairEnabled,
            sPenOnly = ShotPrefs.sPenOnly(app),
            ballSizeDp = ShotPrefs.ballSize(app),
            ballOpacity = ShotPrefs.ballOpacity(app),
            themeId = ShotPrefs.themeId(app),
            versionName = readVersion(),
            notificationsGranted = notificationsGranted,
        )
    }

    fun startBall() {
        if (AppState.running.value) return
        val app = getApplication<Application>()
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, FloatingBallService::class.java)
                    .setAction(FloatingBallService.ACTION_START),
            )
        }
    }

    fun stopBall() {
        val app = getApplication<Application>()
        app.stopService(Intent(app, FloatingBallService::class.java))
    }

    fun setBallSize(size: Int) {
        val app = getApplication<Application>()
        ShotPrefs.setBallSize(app, size)
        _uiState.update {
            it.copy(
                ballSizeDp = size.coerceIn(ShotPrefs.MIN_BALL_SIZE, ShotPrefs.MAX_BALL_SIZE),
            )
        }
        if (AppState.running.value) {
            runCatching {
                app.startService(
                    Intent(app, FloatingBallService::class.java)
                        .setAction(FloatingBallService.ACTION_UPDATE_SIZE),
                )
            }
        }
    }

    fun setSPenOnly(value: Boolean) {
        ShotPrefs.setSPenOnly(getApplication(), value)
        _uiState.update { it.copy(sPenOnly = value) }
    }

    fun setBallOpacity(value: Int) {
        val app = getApplication<Application>()
        ShotPrefs.setBallOpacity(app, value)
        _uiState.update {
            it.copy(
                ballOpacity = value.coerceIn(
                    ShotPrefs.MIN_BALL_OPACITY,
                    ShotPrefs.MAX_BALL_OPACITY,
                ),
            )
        }
        notifyBallAppearance()
    }

    fun setThemeId(id: String) {
        val app = getApplication<Application>()
        ShotPrefs.setThemeId(app, id)
        val theme = ThemeColors.byId(id)
        _uiState.update { it.copy(themeId = theme.id) }
        notifyBallAppearance()
    }

    private fun notifyBallAppearance() {
        if (!AppState.running.value) return
        val app = getApplication<Application>()
        runCatching {
            app.startService(
                Intent(app, FloatingBallService::class.java)
                    .setAction(FloatingBallService.ACTION_UPDATE_APPEARANCE),
            )
        }
    }

    fun setAutoRepairEnabled(value: Boolean) {
        ShotPrefs.setAutoRepairEnabled(getApplication(), value)
        _uiState.update { it.copy(autoRepairEnabled = value) }
    }

    private fun readVersion(): String {
        val app = getApplication<Application>()
        return runCatching {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.1.0"
        }.getOrDefault("1.1.0")
    }
}
