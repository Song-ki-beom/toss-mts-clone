package com.example.mts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mts.data.Account
import com.example.mts.data.Fees
import com.example.mts.data.Holding
import com.example.mts.data.Order
import com.example.mts.data.OrderStatus
import com.example.mts.data.Quote
import com.example.mts.data.Side
import com.example.mts.ui.theme.DownBlue
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
    val holdings = account.holdings.values.toList()
    val evalAmount = holdings.sumOf { (quotes[it.code]?.price ?: it.avgPrice) * it.quantity }
    val buyAmount = holdings.sumOf { it.avgPrice * it.quantity }
    val reserved = account.orders
        .filter { it.status == OrderStatus.PENDING && it.side == Side.BUY }
        .sumOf { Fees.buyCost(it.price, it.quantity) }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            SummaryCard(
                total = account.cash + reserved + evalAmount,
                cash = account.cash,
                reserved = reserved,
                evalAmount = evalAmount,
                pnl = evalAmount - buyAmount,
                buyAmount = buyAmount,
            )
        }

        item { SectionTitle("보유종목 ${holdings.size}") }
        if (holdings.isEmpty()) {
            item { EmptyText("보유 종목이 없습니다. 관심종목에서 종목을 눌러 주문해 보세요.") }
        }
        items(holdings, key = { "h-${it.code}" }) { h ->
            HoldingRow(h, quotes[h.code]?.price ?: h.avgPrice, onClick = { onSelect(h.code) })
            HorizontalDivider()
        }

        item { SectionTitle("주문내역") }
        if (account.orders.isEmpty()) {
            item { EmptyText("아직 주문이 없습니다.") }
        }
        items(account.orders, key = { "o-${it.id}" }) { order ->
            OrderRow(order, onCancel = { onCancel(order.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SummaryCard(total: Long, cash: Long, reserved: Long, evalAmount: Long, pnl: Long, buyAmount: Long) {
    val rate = if (buyAmount > 0) pnl * 100.0 / buyAmount else 0.0
    val pnlColor = priceColor(pnl, MaterialTheme.colorScheme.onSurface)
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("총 자산", style = MaterialTheme.typography.labelMedium)
            Text("${formatPrice(total)}원", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("평가손익 ${formatSigned(pnl)}원 (${formatRate(rate)})", color = pnlColor)
            Spacer(Modifier.height(4.dp))
            SummaryLine("예수금", cash)
            if (reserved > 0) SummaryLine("주문 대기금", reserved)
            SummaryLine("주식 평가금액", evalAmount)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatPrice(value)}원", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HoldingRow(holding: Holding, price: Long, onClick: () -> Unit) {
    val pnl = (price - holding.avgPrice) * holding.quantity
    val rate = (price - holding.avgPrice) * 100.0 / holding.avgPrice
    val color = priceColor(pnl, MaterialTheme.colorScheme.onSurface)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(holding.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${holding.quantity}주 · 평균 ${formatPrice(holding.avgPrice)}원",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatPrice(price * holding.quantity)}원", fontWeight = FontWeight.SemiBold)
            Text("${formatSigned(pnl)} (${formatRate(rate)})", style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun OrderRow(order: Order, onCancel: () -> Unit) {
    val sideColor = if (order.side == Side.BUY) UpRed else DownBlue
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(order.side.label, color = sideColor, fontWeight = FontWeight.Bold)
                Text(order.name, fontWeight = FontWeight.SemiBold)
                Text(order.type.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${formatTime(order.time)} · ${formatPrice(order.filledPrice ?: order.price)}원 × ${order.quantity}주",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val statusColor = when (order.status) {
            OrderStatus.FILLED -> MaterialTheme.colorScheme.primary
            OrderStatus.PENDING -> Color(0xFFF57C00)
            OrderStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(order.status.label, color = statusColor, style = MaterialTheme.typography.labelLarge)
        if (order.status == OrderStatus.PENDING) {
            TextButton(onClick = onCancel) { Text("취소") }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
