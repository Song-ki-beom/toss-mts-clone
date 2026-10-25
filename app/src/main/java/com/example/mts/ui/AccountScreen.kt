package com.example.mts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mts.data.Account
import com.example.mts.data.Order
import com.example.mts.data.OrderStatus
import com.example.mts.data.Quote
import com.example.mts.data.Side
import com.example.mts.ui.theme.Brand
import com.example.mts.ui.theme.DownBlue
import com.example.mts.ui.theme.Mts
import com.example.mts.ui.theme.UpRed
import com.example.mts.ui.theme.priceColor

@Composable
fun AccountScreen(
    account: Account,
    quotes: Map<String, Quote>,
    onSelect: (String) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = summarize(account, quotes)
    val holdings = account.holdings.values.toList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MtsCard(contentPadding = PaddingValues(vertical = 22.dp)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("총 자산", style = MaterialTheme.typography.bodyMedium, color = Mts.colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(formatWon(summary.total), style = MaterialTheme.typography.headlineMedium, color = Mts.colors.textPrimary)
                    Text(
                        if (summary.buyAmount == 0L) "수익 0원 (0.00%)" else "수익 " + formatChange(summary.pnl, summary.pnlRate),
                        style = MaterialTheme.typography.labelLarge,
                        color = priceColor(summary.pnl, Mts.colors.textTertiary),
                    )
                }
                Spacer(Modifier.height(14.dp))
                InfoRow("주문 가능 금액", formatWon(summary.cash))
                if (summary.reserved > 0) InfoRow("주문 대기 금액", formatWon(summary.reserved))
                InfoRow("주식 평가 금액", formatWon(summary.evalAmount))
                InfoRow("투자 원금", formatWon(summary.buyAmount))
            }
        }

        item {
            MtsCard {
                CardTitle("내 주식 ${holdings.size}")
                if (holdings.isEmpty()) EmptyMessage("보유한 주식이 없어요.")
                holdings.forEach { h ->
                    val price = quotes[h.code]?.price ?: h.avgPrice
                    val pnl = (price - h.avgPrice) * h.quantity
                    StockListItem(
                        name = h.name,
                        caption = "${h.quantity}주 · 평균 ${formatWon(h.avgPrice)}",
                        primary = formatWon(price * h.quantity),
                        secondary = formatChange(pnl, (price - h.avgPrice) * 100.0 / h.avgPrice),
                        secondaryColor = priceColor(pnl, Mts.colors.textTertiary),
                        onClick = { onSelect(h.code) },
                    )
                }
            }
        }

        item {
            MtsCard {
                CardTitle("주문 내역")
                if (account.orders.isEmpty()) EmptyMessage("아직 주문한 내역이 없어요.")
                account.orders.forEach { order -> OrderRow(order, onCancel = { onCancel(order.id) }) }
            }
        }
    }
}

@Composable
private fun OrderRow(order: Order, onCancel: () -> Unit) {
    val sideColor = if (order.side == Side.BUY) UpRed else DownBlue
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Mts.colors.textPrimary)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${order.type.label} ${order.side.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = sideColor,
                )
            }
            Text(
                "${formatTime(order.time)} · ${formatWon(order.filledPrice ?: order.price)} · ${order.quantity}주",
                style = MaterialTheme.typography.labelMedium,
                color = Mts.colors.textTertiary,
            )
        }
        if (order.status == OrderStatus.PENDING) {
            Text(
                "취소",
                style = MaterialTheme.typography.labelLarge,
                color = Mts.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Mts.colors.chip)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        val statusColor = when (order.status) {
            OrderStatus.FILLED -> Brand
            OrderStatus.PENDING -> Color(0xFFFF9F00)
            OrderStatus.CANCELED -> Mts.colors.textTertiary
        }
        Text(order.status.label, style = MaterialTheme.typography.labelLarge, color = statusColor)
    }
}
