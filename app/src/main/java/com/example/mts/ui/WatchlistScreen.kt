package com.example.mts.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mts.data.Quote
import com.example.mts.data.Stock
import com.example.mts.ui.theme.DownBlue
import com.example.mts.ui.theme.UpRed
import com.example.mts.ui.theme.priceColor

@Composable
fun WatchlistScreen(
    stocks: List<Stock>,
    quotes: Map<String, Quote>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(stocks, key = { it.code }) { stock ->
            val quote = quotes[stock.code] ?: return@items
            StockRow(stock, quote, onClick = { onSelect(stock.code) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun StockRow(stock: Stock, quote: Quote, onClick: () -> Unit) {
    val color = priceColor(quote.change, MaterialTheme.colorScheme.onSurface)

    // 가격이 바뀌면 오르면 빨강, 내리면 파랑으로 잠깐 깜빡인다
    val flash = remember { Animatable(Color.Transparent) }
    var lastPrice by remember { mutableLongStateOf(quote.price) }
    LaunchedEffect(quote.price) {
        if (quote.price != lastPrice) {
            val tint = if (quote.price > lastPrice) UpRed else DownBlue
            lastPrice = quote.price
            flash.snapTo(tint.copy(alpha = 0.18f))
            flash.animateTo(Color.Transparent, tween(600))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stock.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${stock.code} · ${stock.market}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .background(flash.value, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                formatPrice(quote.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(formatChange(quote.change, quote.changeRate), style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
