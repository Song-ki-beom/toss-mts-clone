package com.example.mts.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.random.Random

@kotlinx.serialization.Serializable
data class MarketState(
    val quotes: Map<String, Quote>,
    val candles: Map<String, List<Candle>>,
    val orderBooks: Map<String, OrderBook>,
)

/** 시세 공급원. [MarketSimulator](모의 시세)와 KIS API 구현체가 있다. */
interface MarketDataSource {
    val stocks: List<Stock>
    val state: StateFlow<MarketState>

    /** 화면 상단에 보여줄 연결 상태 */
    val status: StateFlow<String>

    /** 차트 제목에 붙일 봉 단위 설명 */
    val candleLabel: String

    /** 일·주·월·년봉. 마지막 봉은 현재 진행 중인 기간이다. 기본은 모의 과거 시세 */
    suspend fun history(code: String, period: Period, quote: Quote): List<Candle> =
        HistoryGenerator.build(code, period, quote)

    fun close() {}
}

/** 랜덤워크로 시세, 분봉, 호가를 만들어내는 모의 시세 엔진 */
class MarketSimulator(
    scope: CoroutineScope,
    private val tickIntervalMs: Long = 500L,
    private val ticksPerCandle: Int = 10,
    private val random: Random = Random.Default,
) : MarketDataSource {

    private val basePrices = mapOf(
        "005930" to 71_500L,
        "000660" to 198_000L,
        "035420" to 187_300L,
        "035720" to 41_250L,
        "005380" to 236_500L,
        "373220" to 352_000L,
        "068270" to 181_200L,
        "247540" to 168_900L,
    )

    override val stocks: List<Stock> = DEFAULT_WATCHLIST
    override val status: StateFlow<String> = MutableStateFlow("모의 시세").asStateFlow()
    override val candleLabel = "봉당 ${tickIntervalMs * ticksPerCandle / 1000}초"

    private var tickCount = 0
    private val _state: MutableStateFlow<MarketState>
    override val state: StateFlow<MarketState>

    init {
        val quotes = LinkedHashMap<String, Quote>()
        val candles = LinkedHashMap<String, List<Candle>>()
        for (stock in stocks) {
            val prevClose = basePrices[stock.code] ?: 50_000L
            val history = generateHistory(prevClose)
            candles[stock.code] = history
            quotes[stock.code] = Quote(
                code = stock.code,
                price = history.last().close,
                prevClose = prevClose,
                open = history.first().open,
                high = history.maxOf { it.high },
                low = history.minOf { it.low },
                volume = history.sumOf { it.volume },
            )
        }
        _state = MutableStateFlow(MarketState(quotes, candles, quotes.mapValues { buildOrderBook(it.value.price) }))
        state = _state.asStateFlow()

        scope.launch {
            while (true) {
                delay(tickIntervalMs)
                tick()
            }
        }
    }

    private fun tick() {
        tickCount++
        val startNewCandle = tickCount % ticksPerCandle == 0
        val current = _state.value
        val quotes = LinkedHashMap<String, Quote>()
        val candles = LinkedHashMap<String, List<Candle>>()

        for ((code, q) in current.quotes) {
            val price = nextPrice(q.price, q.prevClose)
            val volume = randomVolume()
            quotes[code] = q.copy(
                price = price,
                high = maxOf(q.high, price),
                low = minOf(q.low, price),
                volume = q.volume + volume,
            )

            val list = current.candles[code].orEmpty()
            candles[code] = if (startNewCandle || list.isEmpty()) {
                (list + Candle(price, price, price, price, volume)).takeLast(MAX_CANDLES)
            } else {
                val last = list.last()
                list.dropLast(1) + last.copy(
                    high = maxOf(last.high, price),
                    low = minOf(last.low, price),
                    close = price,
                    volume = last.volume + volume,
                )
            }
        }

        _state.value = MarketState(quotes, candles, quotes.mapValues { buildOrderBook(it.value.price) })
    }

    private fun generateHistory(prevClose: Long): List<Candle> {
        var price = prevClose
        return List(HISTORY_SIZE) {
            val open = price
            var high = price
            var low = price
            var volume = 0L
            repeat(ticksPerCandle) {
                price = nextPrice(price, prevClose)
                high = maxOf(high, price)
                low = minOf(low, price)
                volume += randomVolume()
            }
            Candle(open, high, low, price, volume)
        }
    }

    /** 현재가에서 호가단위로 움직이되 상·하한가(전일 종가 ±30%)를 넘지 않는다 */
    private fun nextPrice(price: Long, prevClose: Long): Long {
        val tick = tickSize(price)
        val move = (random.nextDouble() - 0.5) * price * VOLATILITY
        val next = roundToTick(price + (move / tick).roundToLong() * tick)
        val upper = roundToTick(prevClose * 13 / 10)
        val lower = roundToTick(prevClose * 7 / 10)
        return next.coerceIn(lower, upper)
    }

    private fun buildOrderBook(price: Long): OrderBook {
        val tick = tickSize(price)
        val asks = (ORDER_BOOK_DEPTH downTo 1).map { i -> OrderBookLevel(price + i * tick, randomQuantity(i)) }
        val bids = (0 until ORDER_BOOK_DEPTH).map { i -> OrderBookLevel(price - i * tick, randomQuantity(i + 1)) }
        return OrderBook(asks, bids)
    }

    private fun randomQuantity(level: Int): Long = random.nextLong(50, 800) * (1 + level / 3)

    private fun randomVolume(): Long = random.nextLong(10, 500)

    private companion object {
        const val HISTORY_SIZE = 40
        const val MAX_CANDLES = 60
        const val ORDER_BOOK_DEPTH = 10
        const val VOLATILITY = 0.004
    }
}
