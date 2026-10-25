package com.example.mts.data.kis

import android.content.Context
import com.example.mts.data.Candle
import com.example.mts.data.DEFAULT_WATCHLIST
import com.example.mts.data.MarketDataSource
import com.example.mts.data.MarketSimulator
import com.example.mts.data.MarketState
import com.example.mts.data.Quote
import com.example.mts.data.Stock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** local.properties에 KIS 키가 있으면 실시세, 없으면 모의 시세를 쓴다 */
fun createMarketDataSource(context: Context, scope: CoroutineScope): MarketDataSource {
    val config = KisConfig.fromBuildConfig() ?: return MarketSimulator(scope)
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    return KisMarketDataSource(config, KisApi(config, PrefsTokenCache(context.applicationContext), client), client, scope)
}

/**
 * KIS 실시세. 시작할 때 REST로 현재가·호가·분봉을 받아두고,
 * 이후에는 WebSocket으로 체결가(H0STCNT0)와 호가(H0STASP0)를 받아 갱신한다.
 */
class KisMarketDataSource(
    private val config: KisConfig,
    private val api: KisApi,
    private val client: OkHttpClient,
    scope: CoroutineScope,
    override val stocks: List<Stock> = DEFAULT_WATCHLIST,
) : MarketDataSource {

    private val _state = MutableStateFlow(MarketState(emptyMap(), emptyMap(), emptyMap()))
    override val state: StateFlow<MarketState> = _state.asStateFlow()

    private val _status = MutableStateFlow("${config.modeLabel} · 연결 중")
    override val status: StateFlow<String> = _status.asStateFlow()

    override val candleLabel = "1분봉"

    private val lastMinute = ConcurrentHashMap<String, String>()
    private var lastError: Exception? = null
    @Volatile private var socket: WebSocket? = null
    private val job: Job = scope.launch { run() }

    override fun close() {
        job.cancel()
        socket?.close(1000, null)
    }

    private suspend fun run() {
        try {
            loadSnapshots()
            streamRealtime()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = "${config.modeLabel} · 오류: ${e.message}"
        }
    }

    private suspend fun loadSnapshots() {
        _status.value = "${config.modeLabel} · 현재가 조회 중"
        for (stock in stocks) {
            val quote = attempt { api.inquirePrice(stock.code) } ?: continue
            _state.update { it.copy(quotes = it.quotes + (stock.code to quote)) }
        }
        if (_state.value.quotes.isEmpty()) throw lastError ?: IllegalStateException("시세를 불러오지 못했습니다")

        _status.value = "${config.modeLabel} · 호가·차트 조회 중"
        for (stock in stocks) {
            val book = attempt { api.askingPrice(stock.code) } ?: continue
            _state.update { it.copy(orderBooks = it.orderBooks + (stock.code to book)) }
        }
        val hour = chartHour()
        for (stock in stocks) {
            val candles = attempt { api.minuteCandles(stock.code, hour) } ?: continue
            candles.lastOrNull()?.let { lastMinute[stock.code] = it.time.take(4) }
            _state.update { it.copy(candles = it.candles + (stock.code to candles.map(TimedCandle::candle))) }
        }
    }

    private suspend fun streamRealtime() {
        val approvalKey = api.approvalKey()
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            val session = Session(approvalKey)
            socket = client.newWebSocket(Request.Builder().url(config.webSocketUrl).build(), session)
            val error = session.closed.await()
            socket = null
            if (session.opened) backoff = 1_000L
            _status.value = "${config.modeLabel} · 재연결 대기" + (error?.message?.let { " ($it)" } ?: "")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private inner class Session(private val approvalKey: String) : WebSocketListener() {
        val closed = CompletableDeferred<Throwable?>()
        @Volatile var opened = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened = true
            _status.value = "${config.modeLabel} · " + if (isMarketOpen()) "실시간" else "장 마감 · 최근 시세"
            for (stock in stocks) {
                webSocket.send(KisParser.subscribeMessage(approvalKey, KisParser.TR_TRADE, stock.code))
                webSocket.send(KisParser.subscribeMessage(approvalKey, KisParser.TR_ASKING, stock.code))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frames = runCatching { KisParser.parseFrame(text) }.getOrDefault(emptyList())
            for (frame in frames) {
                when (frame) {
                    KisFrame.PingPong -> webSocket.send(text)
                    is KisFrame.Trade -> onTrade(frame)
                    is KisFrame.Asking -> _state.update { it.copy(orderBooks = it.orderBooks + (frame.code to frame.book)) }
                    is KisFrame.Control -> if (!frame.ok && frame.message.isNotBlank()) {
                        _status.value = "${config.modeLabel} · ${frame.message}"
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            closed.complete(t)
        }
    }

    private fun onTrade(t: KisFrame.Trade) {
        val minute = t.time.take(4)
        val newMinute = lastMinute.put(t.code, minute) != minute
        _state.update { s ->
            val quote = Quote(t.code, t.price, t.price - t.change, t.open, t.high, t.low, t.accVolume)
            val list = s.candles[t.code].orEmpty()
            val candles = if (newMinute || list.isEmpty()) {
                (list + Candle(t.price, t.price, t.price, t.price, t.tradeVolume)).takeLast(MAX_CANDLES)
            } else {
                val last = list.last()
                list.dropLast(1) + last.copy(
                    high = maxOf(last.high, t.price),
                    low = minOf(last.low, t.price),
                    close = t.price,
                    volume = last.volume + t.tradeVolume,
                )
            }
            s.copy(quotes = s.quotes + (t.code to quote), candles = s.candles + (t.code to candles))
        }
    }

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        lastError = e
        null
    }

    private fun nowKst(): ZonedDateTime = ZonedDateTime.now(KST)

    private fun isMarketOpen(): Boolean {
        val now = nowKst()
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false
        val t = now.toLocalTime()
        return !t.isBefore(MARKET_OPEN) && !t.isAfter(MARKET_CLOSE)
    }

    /** 장중이면 현재 시각, 장 마감 후면 15:30 기준으로 분봉을 요청한다 */
    private fun chartHour(): String {
        val t = nowKst().toLocalTime()
        return (if (t.isAfter(MARKET_CLOSE)) MARKET_CLOSE else t).format(HHMMSS)
    }

    private companion object {
        const val MAX_CANDLES = 60
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val MARKET_OPEN: LocalTime = LocalTime.of(9, 0)
        val MARKET_CLOSE: LocalTime = LocalTime.of(15, 30)
        val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
    }
}
