package com.example.mts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mts.data.Candle
import com.example.mts.data.Holding
import com.example.mts.data.OrderBook
import com.example.mts.data.OrderBookLevel
import com.example.mts.data.OrderType
import com.example.mts.data.Quote
import com.example.mts.data.Side
import com.example.mts.data.Stock
import com.example.mts.ui.theme.DownBlue
import com.example.mts.ui.theme.UpRed
import com.example.mts.ui.theme.priceColor

data class OrderDraft(val side: Side, val price: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stock: Stock,
    quote: Quote,
    candles: List<Candle>,
    orderBook: OrderBook?,
    holding: Holding?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOrder: (Stock, Side, OrderType, Long, Long) -> Unit,
    maxBuyQuantity: (Long) -> Long,
    sellableQuantity: () -> Long,
) {
    var draft by remember { mutableStateOf<OrderDraft?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stock.name, fontWeight = FontWeight.SemiBold)
                        Text("${stock.code} · ${stock.market}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { draft = OrderDraft(Side.SELL, quote.price) },
                        colors = ButtonDefaults.buttonColors(containerColor = DownBlue),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("매도", fontSize = 17.sp) }
                    Button(
                        onClick = { draft = OrderDraft(Side.BUY, quote.price) },
                        colors = ButtonDefaults.buttonColors(containerColor = UpRed),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("매수", fontSize = 17.sp) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { PriceHeader(quote, holding) }
            item {
                SectionTitle("차트 (봉당 5초)")
                CandleChart(
                    candles = candles,
                    currentPrice = quote.price,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                SectionTitle("호가 (가격을 누르면 지정가 주문)")
                OrderBookView(orderBook, quote, onPriceClick = { draft = OrderDraft(Side.BUY, it) })
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    draft?.let { d ->
        OrderSheet(
            stock = stock,
            quote = quote,
            draft = d,
            maxBuyQuantity = maxBuyQuantity,
            sellableQuantity = sellableQuantity,
            onDismiss = { draft = null },
            onSubmit = { side, type, price, qty ->
                onOrder(stock, side, type, price, qty)
                draft = null
            },
        )
    }
}

@Composable
private fun PriceHeader(quote: Quote, holding: Holding?) {
    val color = priceColor(quote.change, MaterialTheme.colorScheme.onSurface)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            formatPrice(quote.price),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(formatChange(quote.change, quote.changeRate), color = color, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("시가", formatPrice(quote.open))
            Stat("고가", formatPrice(quote.high), UpRed)
            Stat("저가", formatPrice(quote.low), DownBlue)
            Stat("거래량", formatPrice(quote.volume))
        }
        if (holding != null) {
            val pnl = (quote.price - holding.avgPrice) * holding.quantity
            Spacer(Modifier.height(8.dp))
            Text(
                "보유 ${holding.quantity}주 · 평균 ${formatPrice(holding.avgPrice)}원 · 평가손익 ${formatSigned(pnl)}원",
                style = MaterialTheme.typography.bodySmall,
                color = priceColor(pnl, MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = Color.Unspecified) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
fun CandleChart(candles: List<Candle>, currentPrice: Long, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        if (candles.isEmpty()) return@Canvas
        val max = candles.maxOf { it.high }
        val min = candles.minOf { it.low }
        val range = (max - min).coerceAtLeast(1L).toFloat()
        fun y(price: Long) = size.height - (price - min).toFloat() / range * size.height

        val slot = size.width / candles.size
        val bodyWidth = slot * 0.6f
        candles.forEachIndexed { i, c ->
            val color = if (c.close >= c.open) UpRed else DownBlue
            val cx = slot * i + slot / 2
            drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), strokeWidth = 1.dp.toPx())
            val top = y(maxOf(c.open, c.close))
            val bottom = y(minOf(c.open, c.close))
            drawRect(color, Offset(cx - bodyWidth / 2, top), Size(bodyWidth, (bottom - top).coerceAtLeast(1.dp.toPx())))
        }

        val py = y(currentPrice)
        drawLine(
            lineColor,
            Offset(0f, py),
            Offset(size.width, py),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}

@Composable
private fun OrderBookView(book: OrderBook?, quote: Quote, onPriceClick: (Long) -> Unit) {
    if (book == null) return
    val maxQty = (book.asks + book.bids).maxOf { it.quantity }.toFloat()
    Column(Modifier.padding(horizontal = 16.dp)) {
        book.asks.forEach { OrderBookRow(it, maxQty, isAsk = true, quote = quote, onClick = onPriceClick) }
        book.bids.forEach { OrderBookRow(it, maxQty, isAsk = false, quote = quote, onClick = onPriceClick) }
    }
}

/** 왼쪽은 매도잔량, 가운데는 가격, 오른쪽은 매수잔량 */
@Composable
private fun OrderBookRow(level: OrderBookLevel, maxQty: Float, isAsk: Boolean, quote: Quote, onClick: (Long) -> Unit) {
    val base = if (isAsk) DownBlue else UpRed
    val change = level.price - quote.prevClose
    val priceColor = priceColor(change, MaterialTheme.colorScheme.onSurface)
    val isCurrent = level.price == quote.price
    val fraction = (level.quantity / maxQty).coerceIn(0.05f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(base.copy(alpha = 0.06f))
            .then(if (isCurrent) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface) else Modifier)
            .clickable { onClick(level.price) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            if (isAsk) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().padding(vertical = 4.dp).background(base.copy(alpha = 0.22f)))
                Text(formatPrice(level.quantity), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatPrice(level.price), color = priceColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp)
            Text(formatRate(change * 100.0 / quote.prevClose), color = priceColor, fontSize = 10.sp, lineHeight = 12.sp)
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (!isAsk) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().padding(vertical = 4.dp).background(base.copy(alpha = 0.22f)))
                Text(formatPrice(level.quantity), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
