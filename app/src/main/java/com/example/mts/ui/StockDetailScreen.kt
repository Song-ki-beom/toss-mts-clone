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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.mts.ui.theme.Mts
import com.example.mts.ui.theme.UpRed
import com.example.mts.ui.theme.priceColor

data class OrderDraft(val side: Side, val price: Long)

private enum class ChartMode(val label: String) { LINE("라인"), CANDLE("캔들") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stock: Stock,
    quote: Quote,
    candles: List<Candle>,
    candleLabel: String,
    orderBook: OrderBook?,
    holding: Holding?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOrder: (Stock, Side, OrderType, Long, Long) -> Unit,
    maxBuyQuantity: (Long) -> Long,
    sellableQuantity: () -> Long,
) {
    var draft by remember { mutableStateOf<OrderDraft?>(null) }
    var chartMode by rememberSaveable { mutableStateOf(ChartMode.LINE) }

    Scaffold(
        containerColor = Mts.colors.card,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Mts.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mts.colors.card),
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Mts.colors.card)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OrderButton("판매하기", DownBlue, Modifier.weight(1f)) { draft = OrderDraft(Side.SELL, quote.price) }
                OrderButton("구매하기", UpRed, Modifier.weight(1f)) { draft = OrderDraft(Side.BUY, quote.price) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { PriceHeader(stock, quote, holding) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(candleLabel, style = MaterialTheme.typography.labelMedium, color = Mts.colors.textTertiary, modifier = Modifier.weight(1f))
                    PillToggle(
                        options = ChartMode.entries,
                        selected = chartMode,
                        label = { it.label },
                        onSelect = { chartMode = it },
                        modifier = Modifier.width(140.dp),
                    )
                }
                val chartModifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                when {
                    candles.isEmpty() -> EmptyMessage("차트 데이터가 없어요. 장중에 실시간 체결이 들어오면 그려져요.")
                    chartMode == ChartMode.LINE -> LineChart(candles, priceColor(quote.change, Mts.colors.textTertiary), chartModifier)
                    else -> CandleChart(candles, quote.price, chartModifier)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                SectionGap()
                MarketInfo(quote)
                SectionGap()
            }
            item {
                Text(
                    "호가",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mts.colors.textPrimary,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                )
                Text(
                    "가격을 누르면 그 가격으로 주문할 수 있어요",
                    style = MaterialTheme.typography.labelMedium,
                    color = Mts.colors.textTertiary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                )
                OrderBookView(orderBook, quote, onPriceClick = { draft = OrderDraft(Side.BUY, it) })
                Spacer(Modifier.height(20.dp))
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
private fun OrderButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(56.dp),
    ) { Text(text, style = MaterialTheme.typography.titleSmall, color = Color.White) }
}

@Composable
private fun PriceHeader(stock: Stock, quote: Quote, holding: Holding?) {
    val color = priceColor(quote.change, Mts.colors.textTertiary)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stock.name, style = MaterialTheme.typography.titleMedium, color = Mts.colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text("${stock.code} ${stock.market}", style = MaterialTheme.typography.labelMedium, color = Mts.colors.textTertiary)
        }
        Spacer(Modifier.height(4.dp))
        Text(formatWon(quote.price), style = MaterialTheme.typography.displaySmall, color = Mts.colors.textPrimary)
        Row {
            Text("어제보다 ", style = MaterialTheme.typography.labelLarge, color = Mts.colors.textTertiary)
            Text(formatChange(quote.change, quote.changeRate), style = MaterialTheme.typography.labelLarge, color = color)
        }
        if (holding != null) {
            val pnl = (quote.price - holding.avgPrice) * holding.quantity
            val rate = (quote.price - holding.avgPrice) * 100.0 / holding.avgPrice
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mts.colors.chip)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("내 주식 ${holding.quantity}주", style = MaterialTheme.typography.labelLarge, color = Mts.colors.textSecondary, modifier = Modifier.weight(1f))
                Text(formatChange(pnl, rate), style = MaterialTheme.typography.labelLarge, color = priceColor(pnl, Mts.colors.textTertiary))
            }
        }
    }
}

@Composable
private fun MarketInfo(quote: Quote) {
    Column(Modifier.padding(vertical = 16.dp)) {
        Text(
            "시세",
            style = MaterialTheme.typography.titleMedium,
            color = Mts.colors.textPrimary,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        InfoRow("시작가", formatWon(quote.open))
        InfoRow("최고가", formatWon(quote.high), UpRed)
        InfoRow("최저가", formatWon(quote.low), DownBlue)
        InfoRow("어제 종가", formatWon(quote.prevClose))
        InfoRow("거래량", "${formatPrice(quote.volume)}주")
    }
}

@Composable
private fun LineChart(candles: List<Candle>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val closes = candles.map { it.close }
        val max = closes.max()
        val min = closes.min()
        val range = (max - min).coerceAtLeast(1L).toFloat()
        val stepX = if (closes.size > 1) size.width / (closes.size - 1) else size.width
        fun point(i: Int) = Offset(i * stepX, size.height - (closes[i] - min).toFloat() / range * size.height * 0.9f - size.height * 0.05f)

        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until closes.size) lineTo(point(i).x, point(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(closes.lastIndex).x, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 4.dp.toPx(), center = point(closes.lastIndex))
    }
}

@Composable
private fun CandleChart(candles: List<Candle>, currentPrice: Long, modifier: Modifier = Modifier) {
    val lineColor = Mts.colors.textTertiary
    Canvas(modifier) {
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
    if (book == null || (book.asks.isEmpty() && book.bids.isEmpty())) {
        EmptyMessage("호가 정보가 없어요.")
        return
    }
    val maxQty = (book.asks + book.bids).maxOf { it.quantity }.toFloat().coerceAtLeast(1f)
    Column(Modifier.padding(horizontal = 16.dp)) {
        book.asks.forEach { OrderBookRow(it, maxQty, isAsk = true, quote = quote, onClick = onPriceClick) }
        book.bids.forEach { OrderBookRow(it, maxQty, isAsk = false, quote = quote, onClick = onPriceClick) }
    }
}

/** 왼쪽은 판매 대기 수량, 가운데는 가격, 오른쪽은 구매 대기 수량 */
@Composable
private fun OrderBookRow(level: OrderBookLevel, maxQty: Float, isAsk: Boolean, quote: Quote, onClick: (Long) -> Unit) {
    val base = if (isAsk) DownBlue else UpRed
    val change = level.price - quote.prevClose
    val textColor = priceColor(change, Mts.colors.textPrimary)
    val isCurrent = level.price == quote.price
    val fraction = (level.quantity / maxQty).coerceIn(0.04f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(base.copy(alpha = 0.05f))
            .then(if (isCurrent) Modifier.border(1.5.dp, Mts.colors.textPrimary, RoundedCornerShape(6.dp)) else Modifier)
            .clickable { onClick(level.price) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            if (isAsk) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().padding(vertical = 6.dp).background(base.copy(alpha = 0.16f), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                Text(formatPrice(level.quantity), style = MaterialTheme.typography.labelMedium, color = Mts.colors.textSecondary, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatPrice(level.price), color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp)
            Text(formatRate(change * 100.0 / quote.prevClose), color = textColor.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 12.sp)
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (!isAsk) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().padding(vertical = 6.dp).background(base.copy(alpha = 0.16f), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                Text(formatPrice(level.quantity), style = MaterialTheme.typography.labelMedium, color = Mts.colors.textSecondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
