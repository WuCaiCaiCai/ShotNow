package com.shotnow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shotnow.settings.ThemeColors
import com.shotnow.ui.MainScreen
import com.shotnow.ui.MainViewModel
import com.shotnow.ui.theme.ShotNowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            val palette = ThemeColors.byId(state.themeId)
            ShotNowTheme(
                accent = Color(palette.accent),
                onAccent = Color(palette.onAccent),
                soft = Color(palette.soft),
            ) {
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    viewModel.refresh()
                }

                val lifecycleOwner = this@MainActivity
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refresh()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val openOverlaySettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }

                val openAccessibilitySettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }

                val command =
                    "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

                MainScreen(
                    state = state,
                    command = command,
                    snackbarHostState = snackbarHostState,
                    onToggleBall = {
                        when {
                            state.running -> viewModel.stopBall()
                            !state.overlayGranted -> {
                                openOverlaySettings()
                                scope.launch {
                                    snackbarHostState.showSnackbar("请先开启悬浮窗权限")
                                }
                            }

                            !state.accessibilityEnabled -> {
                                openAccessibilitySettings()
                                scope.launch {
                                    snackbarHostState.showSnackbar("请先开启 ShotNow 无障碍服务")
                                }
                            }

                            else -> {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    !state.notificationsGranted
                                ) {
                                    notificationLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                                viewModel.startBall()
                            }
                        }
                    },
                    onBallSize = viewModel::setBallSize,
                    onBallOpacity = viewModel::setBallOpacity,
                    onThemeColor = viewModel::setThemeId,
                    onSPenOnly = viewModel::setSPenOnly,
                    onAutoRepairToggle = viewModel::setAutoRepairEnabled,
                    onOpenOverlay = openOverlaySettings,
                    onOpenAccessibility = openAccessibilitySettings,
                    onCopyCommand = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("ShotNow", command))
                        scope.launch {
                            snackbarHostState.showSnackbar("命令已复制")
                        }
                    },
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}
