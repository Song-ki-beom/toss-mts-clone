package com.example.mts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mts.data.Account
import com.example.mts.data.Quote
import com.example.mts.data.Stock
import com.example.mts.ui.theme.Brand
import com.example.mts.ui.theme.DownBlue
import com.example.mts.ui.theme.Mts
import com.example.mts.ui.theme.UpRed
import com.example.mts.ui.theme.priceColor

private enum class Movers(val label: String) { UP("상승"), DOWN("하락") }

@Composable
fun HomeScreen(
    stocks: List<Stock>,
    quotes: Map<String, Quote>,
    account: Account,
    status: String,
    onSelect: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = summarize(account, quotes)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AssetCard(summary, onClick = onOpenAccount) }
        item { HoldingsCard(account, quotes, onSelect, onOpenAccount) }
        item { MoversCard(stocks, quotes, onSelect) }
        item {
            Text(
                "시세 · $status",
                style = MaterialTheme.typography.labelSmall,
                color = Mts.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AssetCard(summary: AccountSummary, onClick: () -> Unit) {
    MtsCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)) {
        Text("내 투자", style = MaterialTheme.typography.bodyMedium, color = Mts.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(formatWon(summary.total), style = MaterialTheme.typography.headlineMedium, color = Mts.colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(
            if (summary.buyAmount == 0L) "아직 투자한 주식이 없어요" else formatChange(summary.pnl, summary.pnlRate),
            style = MaterialTheme.typography.labelLarge,
            color = priceColor(summary.pnl, Mts.colors.textTertiary),
        )
        Spacer(Modifier.height(18.dp))
        Text("주문 가능 금액", style = MaterialTheme.typography.labelMedium, color = Mts.colors.textTertiary)
        Text(formatWon(summary.cash), style = MaterialTheme.typography.titleSmall, color = Mts.colors.textPrimary)
    }
}

@Composable
private fun HoldingsCard(account: Account, quotes: Map<String, Quote>, onSelect: (String) -> Unit, onOpenAccount: () -> Unit) {
    val holdings = account.holdings.values.toList()
    MtsCard {
        CardTitle("내 주식", trailing = if (holdings.isEmpty()) null else "전체", onTrailingClick = onOpenAccount)
        if (holdings.isEmpty()) {
            EmptyMessage("관심 종목에서 마음에 드는 주식을 골라 구매해 보세요.")
        }
        holdings.forEach { h ->
            val price = quotes[h.code]?.price ?: h.avgPrice
            val pnl = (price - h.avgPrice) * h.quantity
            StockListItem(
                name = h.name,
                caption = "${h.quantity}주",
                primary = formatWon(price * h.quantity),
                secondary = formatChange(pnl, (price - h.avgPrice) * 100.0 / h.avgPrice),
                secondaryColor = priceColor(pnl, Mts.colors.textTertiary),
                onClick = { onSelect(h.code) },
            )
        }
    }
}

@Composable
private fun MoversCard(stocks: List<Stock>, quotes: Map<String, Quote>, onSelect: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(Movers.UP) }
    val ranked = stocks
        .mapNotNull { s -> quotes[s.code]?.let { s to it } }
        .filter { (_, q) -> if (mode == Movers.UP) q.change > 0 else q.change < 0 }
        .sortedBy { (_, q) -> if (mode == Movers.UP) -q.changeRate else q.changeRate }
        .take(5)

    MtsCard {
        CardTitle("오늘 많이 ${if (mode == Movers.UP) "오른" else "내린"} 주식")
        PillToggle(
            options = Movers.entries,
            selected = mode,
            label = { it.label },
            onSelect = { mode = it },
            activeColor = { if (it == Movers.UP) UpRed else DownBlue },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).width(160.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (ranked.isEmpty()) {
            EmptyMessage("지금은 ${if (mode == Movers.UP) "오른" else "내린"} 주식이 없어요.")
        }
        ranked.forEachIndexed { i, (stock, q) ->
            StockListItem(
                name = stock.name,
                caption = stock.market,
                primary = formatWon(q.price),
                secondary = "%+.2f%%".format(q.changeRate),
                secondaryColor = priceColor(q.change, Mts.colors.textTertiary),
                onClick = { onSelect(stock.code) },
                highlight = rememberPriceFlash(q.price),
                leading = {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Brand,
                        modifier = Modifier.width(14.dp),
                    )
                },
            )
        }
    }
}
