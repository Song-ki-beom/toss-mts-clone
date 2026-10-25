package com.example.mts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 국내 증권 관례: 상승은 빨강, 하락은 파랑
val UpRed = Color(0xFFE53935)
val DownBlue = Color(0xFF1E6FE0)

fun priceColor(change: Long, neutral: Color): Color = when {
    change > 0 -> UpRed
    change < 0 -> DownBlue
    else -> neutral
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B3A6B),
    secondary = Color(0xFF3D5A80),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC1FF),
    secondary = Color(0xFFB4C8E8),
)

@Composable
fun MtsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
