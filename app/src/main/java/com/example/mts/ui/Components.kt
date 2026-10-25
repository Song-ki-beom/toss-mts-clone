package com.example.mts.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mts.ui.theme.DownBlue
import com.example.mts.ui.theme.Mts
import com.example.mts.ui.theme.UpRed
import kotlin.math.abs

/** 회색 배경 위에 놓이는 흰색 둥근 카드 */
@Composable
fun MtsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Mts.colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Mts.colors.textPrimary, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                "$trailing ›",
                style = MaterialTheme.typography.labelMedium,
                color = Mts.colors.textTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private val AvatarPalette = listOf(
    Color(0xFF3182F6), Color(0xFFF04452), Color(0xFF00B386), Color(0xFFFF9F00),
    Color(0xFF8B5CF6), Color(0xFF14B8C4), Color(0xFFE5508B), Color(0xFF4E5968),
)

/** 종목 로고 대신 쓰는 이니셜 원 */
@Composable
fun StockAvatar(name: String, size: Dp = 40.dp) {
    val color = AvatarPalette[abs(name.hashCode()) % AvatarPalette.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

/** 아바타 · 이름/부제 · 오른쪽 금액/등락 으로 이루어진 목록 한 줄 */
@Composable
fun StockListItem(
    name: String,
    caption: String,
    primary: String,
    secondary: String,
    secondaryColor: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    highlight: Color = Color.Transparent,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        StockAvatar(name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Mts.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = Mts.colors.textTertiary, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(highlight)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Mts.colors.textPrimary)
            Text(secondary, style = MaterialTheme.typography.labelMedium, color = secondaryColor)
        }
    }
}

/** 가격이 오르면 빨강, 내리면 파랑으로 잠깐 깜빡이는 배경색 */
@Composable
fun rememberPriceFlash(price: Long): Color {
    // 색 자체를 보간하면 중간에 회색이 끼므로 색은 고정하고 투명도만 줄인다
    val alpha = remember { Animatable(0f) }
    var tint by remember { mutableStateOf(UpRed) }
    var lastPrice by remember { mutableLongStateOf(price) }
    LaunchedEffect(price) {
        if (price != lastPrice) {
            tint = if (price > lastPrice) UpRed else DownBlue
            lastPrice = price
            alpha.snapTo(0.14f)
            alpha.animateTo(0f, tween(600))
        }
    }
    return tint.copy(alpha = alpha.value)
}

/** 둥근 알약 모양 토글 버튼 묶음 */
@Composable
fun <T> PillToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: ((T) -> Color)? = null,
) {
    val defaultActive = Mts.colors.textPrimary
    Row(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Mts.colors.chip).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Mts.colors.card else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) activeColor?.invoke(option) ?: defaultActive else Mts.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mts.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = if (valueColor == Color.Unspecified) Mts.colors.textPrimary else valueColor,
        )
    }
}

@Composable
fun EmptyMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Mts.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
fun SectionGap(height: Dp = 10.dp) {
    Spacer(Modifier.fillMaxWidth().height(height).background(Mts.colors.background))
}
