package com.example.mts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// 국내 증권 관례: 상승은 빨강, 하락은 파랑
val UpRed = Color(0xFFF04452)
val DownBlue = Color(0xFF3182F6)
val Brand = Color(0xFF3182F6)

fun priceColor(change: Long, neutral: Color): Color = when {
    change > 0 -> UpRed
    change < 0 -> DownBlue
    else -> neutral
}

/** Material 색 체계로 표현하기 어려운 화면 전용 색 */
@Immutable
data class MtsColors(
    val background: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val chip: Color,
)

private val LightMtsColors = MtsColors(
    background = Color(0xFFF2F4F6),
    card = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF191F28),
    textSecondary = Color(0xFF4E5968),
    textTertiary = Color(0xFF8B95A1),
    divider = Color(0xFFE5E8EB),
    chip = Color(0xFFF2F4F6),
)

private val DarkMtsColors = MtsColors(
    background = Color(0xFF101013),
    card = Color(0xFF1C1C21),
    textPrimary = Color(0xFFF2F4F6),
    textSecondary = Color(0xFFB0B8C1),
    textTertiary = Color(0xFF7E8590),
    divider = Color(0xFF2C2C35),
    chip = Color(0xFF2A2A31),
)

private val LocalMtsColors = staticCompositionLocalOf { LightMtsColors }

object Mts {
    val colors: MtsColors
        @Composable @ReadOnlyComposable get() = LocalMtsColors.current
}

private fun style(size: TextUnit, weight: FontWeight, spacing: Float = -0.2f, lineHeight: TextUnit = TextUnit.Unspecified) =
    // tnum: 숫자 폭을 고정해 시세가 바뀌어도 글자가 흔들리지 않게 한다
    TextStyle(fontSize = size, fontWeight = weight, letterSpacing = spacing.sp, lineHeight = lineHeight, fontFeatureSettings = "tnum")

private val MtsTypography = Typography(
    displaySmall = style(34.sp, FontWeight.Bold, -0.8f, 42.sp),
    headlineMedium = style(28.sp, FontWeight.Bold, -0.6f, 36.sp),
    headlineSmall = style(24.sp, FontWeight.Bold, -0.5f, 32.sp),
    titleLarge = style(20.sp, FontWeight.Bold, -0.4f, 28.sp),
    titleMedium = style(17.sp, FontWeight.Bold, -0.3f, 24.sp),
    titleSmall = style(15.sp, FontWeight.SemiBold, -0.2f, 22.sp),
    bodyLarge = style(16.sp, FontWeight.Medium, -0.2f, 24.sp),
    bodyMedium = style(15.sp, FontWeight.Normal, -0.2f, 22.sp),
    bodySmall = style(13.sp, FontWeight.Normal, -0.1f, 18.sp),
    labelLarge = style(15.sp, FontWeight.SemiBold, -0.2f, 20.sp),
    labelMedium = style(13.sp, FontWeight.Medium, -0.1f, 18.sp),
    labelSmall = style(12.sp, FontWeight.Medium, 0f, 16.sp),
)

@Composable
fun MtsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkMtsColors else LightMtsColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.card,
            onSurface = c.textPrimary,
            onSurfaceVariant = c.textSecondary,
            outline = c.textTertiary,
            outlineVariant = c.divider,
            surfaceContainer = c.card,
            surfaceContainerLow = c.card,
            surfaceContainerHigh = c.card,
            secondaryContainer = Brand.copy(alpha = 0.18f),
            onSecondaryContainer = Brand,
        )
    } else {
        lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.card,
            onSurface = c.textPrimary,
            onSurfaceVariant = c.textSecondary,
            outline = c.textTertiary,
            outlineVariant = c.divider,
            surfaceContainer = c.card,
            surfaceContainerLow = c.card,
            surfaceContainerHigh = c.card,
            secondaryContainer = Brand.copy(alpha = 0.12f),
            onSecondaryContainer = Brand,
        )
    }
    CompositionLocalProvider(LocalMtsColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = MtsTypography, content = content)
    }
}
