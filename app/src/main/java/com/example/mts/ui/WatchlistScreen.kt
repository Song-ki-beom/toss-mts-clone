package com.example.mts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mts.data.Quote
import com.example.mts.data.Stock
import com.example.mts.ui.theme.Mts
import com.example.mts.ui.theme.priceColor

@Composable
fun WatchlistScreen(
    stocks: List<Stock>,
    quotes: Map<String, Quote>,
    status: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quotes.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(status, color = Mts.colors.textTertiary)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        item {
            MtsCard {
                CardTitle("관심 종목 ${stocks.size}")
                stocks.forEach { stock ->
                    val quote = quotes[stock.code] ?: return@forEach
                    StockListItem(
                        name = stock.name,
                        caption = "${stock.code} · ${stock.market}",
                        primary = formatWon(quote.price),
                        secondary = formatChange(quote.change, quote.changeRate),
                        secondaryColor = priceColor(quote.change, Mts.colors.textTertiary),
                        onClick = { onSelect(stock.code) },
                        highlight = rememberPriceFlash(quote.price),
                    )
                }
            }
        }
    }
}
