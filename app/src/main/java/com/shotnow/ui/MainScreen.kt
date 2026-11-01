package com.shotnow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shotnow.settings.ShotPrefs
import com.shotnow.settings.ThemeColors
import com.shotnow.ui.theme.CardWhite
import com.shotnow.ui.theme.Divider
import com.shotnow.ui.theme.Faint
import com.shotnow.ui.theme.Ink
import com.shotnow.ui.theme.Muted
import com.shotnow.ui.theme.Paper
import com.shotnow.ui.theme.ShotNowTheme
import com.shotnow.ui.theme.Success
import com.shotnow.ui.theme.SurfaceMuted
import com.shotnow.ui.theme.Warning
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    state: MainUiState,
    command: String,
    snackbarHostState: SnackbarHostState,
    onToggleBall: () -> Unit,
    onBallSize: (Int) -> Unit,
    onBallOpacity: (Int) -> Unit,
    onThemeColor: (String) -> Unit,
    onSPenOnly: (Boolean) -> Unit,
    onAutoRepairToggle: (Boolean) -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onCopyCommand: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showRepair by rememberSaveable { mutableStateOf(false) }
    var showHowTo by rememberSaveable { mutableStateOf(false) }
    var sizePreview by remember(state.ballSizeDp) {
        mutableFloatStateOf(state.ballSizeDp.toFloat())
    }
    var opacityPreview by remember(state.ballOpacity) {
        mutableFloatStateOf(state.ballOpacity.toFloat())
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                Header(state)
                Spacer(Modifier.height(24.dp))
                BallCard(
                    state = state,
                    shownSize = sizePreview.roundToInt(),
                    shownOpacity = opacityPreview.roundToInt(),
                    onToggleBall = onToggleBall,
                    onSizeChange = { sizePreview = it },
                    onSizeCommit = { onBallSize(sizePreview.roundToInt()) },
                )
                SectionLabel("服务")
                ServiceSection(
                    state = state,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlay = onOpenOverlay,
                    onShowRepair = { showRepair = true },
                )
                SectionLabel("外观")
                AppearanceSection(
                    state = state,
                    shownOpacity = opacityPreview.roundToInt(),
                    onOpacityChange = { opacityPreview = it },
                    onOpacityCommit = { onBallOpacity(opacityPreview.roundToInt()) },
                    onThemeColor = onThemeColor,
                )
                SectionLabel("行为")
                BehaviorSection(
                    state = state,
                    showHowTo = showHowTo,
                    onToggleHowTo = { showHowTo = !showHowTo },
                    onSPenOnly = onSPenOnly,
                    onAutoRepairToggle = onAutoRepairToggle,
                    onLockedAutoRepair = { showRepair = true },
                )
                Footer(state)
            }
        }
    }

    if (showRepair) {
        RepairDialog(
            command = command,
            onCopy = onCopyCommand,
            onDismiss = {
                showRepair = false
                onRefresh()
            },
        )
    }
}

@Composable
private fun Header(state: MainUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "ShotNow",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "悬浮球截图工具",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        StatusPill(state)
    }
}

@Composable
private fun StatusPill(state: MainUiState) {
    val (label, color) = when {
        state.running && !state.accessibilityConnected ->
            if (state.accessibilityEnabled) "连接中" to Warning else "待修复" to Warning

        state.running -> "运行中" to Success
        else -> "未运行" to Faint
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SurfaceMuted,
        border = BorderStroke(1.dp, Divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun BallCard(
    state: MainUiState,
    shownSize: Int,
    shownOpacity: Int,
    onToggleBall: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onSizeCommit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Divider),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BallPreview(shownSize, shownOpacity)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "悬浮球",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "点一下球，拖出矩形",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                Switch(
                    checked = state.running,
                    onCheckedChange = { onToggleBall() },
                    colors = shotSwitchColors(),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Divider)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "大小",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = shownSize.toFloat(),
                    onValueChange = onSizeChange,
                    onValueChangeFinished = onSizeCommit,
                    valueRange = ShotPrefs.MIN_BALL_SIZE.toFloat()..ShotPrefs.MAX_BALL_SIZE.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = shotSliderColors(),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "$shownSize dp",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Muted,
                    modifier = Modifier.widthIn(min = 52.dp),
                )
            }
        }
    }
}

@Composable
private fun BallPreview(sizeDp: Int, opacityPercent: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Canvas(
        Modifier
            .size(76.dp)
            .alpha(opacityPercent.coerceIn(20, 100) / 100f),
    ) {
        val limit = min(size.width, size.height) - 8.dp.toPx()
        val visible = sizeDp.dp.toPx().coerceAtMost(limit)
        val radius = visible / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = accent, radius = radius, center = center)
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        val half = radius * 0.40f
        val arm = half * 0.58f
        val glyph = Path().apply {
            moveTo(center.x - half, center.y - half + arm)
            lineTo(center.x - half, center.y - half)
            lineTo(center.x - half + arm, center.y - half)

            moveTo(center.x + half - arm, center.y - half)
            lineTo(center.x + half, center.y - half)
            lineTo(center.x + half, center.y - half + arm)

            moveTo(center.x + half, center.y + half - arm)
            lineTo(center.x + half, center.y + half)
            lineTo(center.x + half - arm, center.y + half)

            moveTo(center.x - half + arm, center.y + half)
            lineTo(center.x - half, center.y + half)
            lineTo(center.x - half, center.y + half - arm)
        }
        drawPath(
            path = glyph,
            color = onAccent,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun ServiceSection(
    state: MainUiState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onShowRepair: () -> Unit,
) {
    Column {
        SettingRow(
            title = "无障碍服务",
            value = when {
                state.accessibilityConnected -> "已连接"
                state.accessibilityEnabled -> "未连接"
                else -> "未开启"
            },
            dotColor = when {
                state.accessibilityConnected -> Success
                state.accessibilityEnabled -> Warning
                else -> Faint
            },
            onClick = if (state.accessibilityConnected) null else onOpenAccessibility,
        )
        HorizontalDivider(color = Divider)
        SettingRow(
            title = "悬浮窗权限",
            value = if (state.overlayGranted) "已授予" else "未授予",
            dotColor = if (state.overlayGranted) Success else Faint,
            onClick = if (state.overlayGranted) null else onOpenOverlay,
        )
        HorizontalDivider(color = Divider)
        SettingRow(
            title = "自动修复",
            value = if (state.autoRepairGranted) "已授权" else "未授权",
            dotColor = if (state.autoRepairGranted) Success else Faint,
            onClick = if (state.autoRepairGranted) null else onShowRepair,
        )
    }
}

@Composable
private fun AppearanceSection(
    state: MainUiState,
    shownOpacity: Int,
    onOpacityChange: (Float) -> Unit,
    onOpacityCommit: () -> Unit,
    onThemeColor: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "主题色",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Row {
                ThemeColors.all.forEach { palette ->
                    val selected = palette.id == state.themeId
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .selectable(
                                selected = selected,
                                onClick = { onThemeColor(palette.id) },
                                role = Role.RadioButton,
                            )
                            .semantics { contentDescription = palette.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(Color(palette.accent), CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Ink else Divider,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "透明度",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = shownOpacity.toFloat(),
                onValueChange = onOpacityChange,
                onValueChangeFinished = onOpacityCommit,
                valueRange = ShotPrefs.MIN_BALL_OPACITY.toFloat()..ShotPrefs.MAX_BALL_OPACITY.toFloat(),
                modifier = Modifier.weight(1f),
                colors = shotSliderColors(),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$shownOpacity%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Muted,
                modifier = Modifier.widthIn(min = 44.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    dotColor: Color?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        if (dotColor != null) {
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
            )
        }
    }
}

@Composable
private fun BehaviorSection(
    state: MainUiState,
    showHowTo: Boolean,
    onToggleHowTo: () -> Unit,
    onSPenOnly: (Boolean) -> Unit,
    onAutoRepairToggle: (Boolean) -> Unit,
    onLockedAutoRepair: () -> Unit,
) {
    Column {
        SwitchRow(
            title = "仅 S Pen 框选",
            subtitle = "只识别 S Pen，忽略手指和手掌误触",
            checked = state.sPenOnly,
            enabled = true,
            onCheckedChange = onSPenOnly,
        )
        HorizontalDivider(color = Divider)
        SwitchRow(
            title = "掉线自动恢复",
            subtitle = if (state.autoRepairGranted) {
                "无障碍被系统关闭时尝试自动打开"
            } else {
                "需要先完成自动修复授权"
            },
            checked = state.autoRepairEnabled && state.autoRepairGranted,
            enabled = state.autoRepairGranted,
            onCheckedChange = onAutoRepairToggle,
            onClickWhenLocked = onLockedAutoRepair,
        )
        HorizontalDivider(color = Divider)

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onToggleHowTo() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "怎么用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                val rotation by animateFloatAsState(
                    targetValue = if (showHowTo) 90f else 0f,
                    animationSpec = tween(180),
                    label = "howto-chevron",
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.rotate(rotation),
                )
            }
            AnimatedVisibility(
                visible = showHowTo,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
            ) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "1. 点一下悬浮球",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Text(
                        text = "2. 屏幕变灰后拖出矩形",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Text(
                        text = "3. 松手，图片已复制到剪贴板",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClickWhenLocked: (() -> Unit)? = null,
) {
    val rowClickable = enabled || onClickWhenLocked != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (rowClickable) {
                    Modifier.clickable {
                        if (enabled) onCheckedChange(!checked) else onClickWhenLocked?.invoke()
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = shotSwitchColors(),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.04.sp),
        color = Muted,
        modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
    )
}

@Composable
private fun Footer(state: MainUiState) {
    Text(
        text = "v${state.versionName} · MIT · 截图只在本地处理",
        style = MaterialTheme.typography.labelSmall,
        color = Faint,
        modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
    )
}

@Composable
private fun RepairDialog(
    command: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "开启自动修复",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
        },
        text = {
            Column {
                Text(
                    text = "系统关掉无障碍时，ShotNow 会尝试自动恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Paper,
                    border = BorderStroke(1.dp, Divider),
                ) {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = Ink,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "在电脑上执行一次即可，之后可以关闭 USB 调试和开发者选项。卸载重装后需要重新授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCopy,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "复制命令",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                )
            }
        },
    )
}

@Composable
private fun shotSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFE2E1DC),
    uncheckedBorderColor = Color(0xFFCFCEC8),
)

@Composable
private fun shotSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = Divider,
)

@Preview(showBackground = true, widthDp = 800, heightDp = 1400)
@Composable
private fun MainScreenPreview() {
    ShotNowTheme {
        MainScreen(
            state = MainUiState(
                running = true,
                overlayGranted = true,
                accessibilityEnabled = true,
                accessibilityConnected = true,
                autoRepairGranted = true,
                autoRepairEnabled = true,
                sPenOnly = true,
                ballSizeDp = 44,
                ballOpacity = 100,
                themeId = "orange",
                versionName = "1.1.0",
            ),
            command = "adb shell pm grant com.shotnow android.permission.WRITE_SECURE_SETTINGS",
            snackbarHostState = remember { SnackbarHostState() },
            onToggleBall = {},
            onBallSize = {},
            onBallOpacity = {},
            onThemeColor = {},
            onSPenOnly = {},
            onAutoRepairToggle = {},
            onOpenOverlay = {},
            onOpenAccessibility = {},
            onCopyCommand = {},
            onRefresh = {},
        )
    }
}
