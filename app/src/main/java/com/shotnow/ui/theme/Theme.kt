package com.shotnow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFFE97845)
val BrandStrong = Color(0xFFB85427)
val OnBrand = Color(0xFF252525)
val Ink = Color(0xFF252525)
val Muted = Color(0xFF6F6F69)
val Faint = Color(0xFF92928E)
val Paper = Color(0xFFF7F7F5)
val CardWhite = Color(0xFFFFFFFF)
val Divider = Color(0xFFEAEAE7)
val SurfaceMuted = Color(0xFFF0EFEA)
val Success = Color(0xFF2F7A58)
val Warning = Color(0xFFB26A1E)

private val ShotTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
)

private val ShotShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun ShotNowTheme(
    accent: Color = Brand,
    onAccent: Color = OnBrand,
    soft: Color = Color(0xFFFBE3D8),
    content: @Composable () -> Unit,
) {
    val colors = lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = soft,
        onPrimaryContainer = Ink,
        secondary = accent,
        onSecondary = onAccent,
        background = Paper,
        onBackground = Ink,
        surface = CardWhite,
        onSurface = Ink,
        surfaceVariant = SurfaceMuted,
        onSurfaceVariant = Muted,
        outline = Divider,
        outlineVariant = Divider,
        error = Color(0xFFB3261E),
        onError = Color.White,
    )
    MaterialTheme(
        colorScheme = colors,
        typography = ShotTypography,
        shapes = ShotShapes,
        content = content,
    )
}
