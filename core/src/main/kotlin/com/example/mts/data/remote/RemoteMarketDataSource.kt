package com.example.mts.data.remote

import com.example.mts.data.Candle
import com.example.mts.data.DEFAULT_WATCHLIST
import com.example.mts.data.MarketDataSource
import com.example.mts.data.MarketState
import com.example.mts.data.Period
import com.example.mts.data.Quote
import com.example.mts.data.Stock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

/**
 * Ktor 중계 서버에 붙는 시세 공급원. 앱에는 KIS 키가 없고 서버만 KIS에 연결한다.
 * WebSocket /ws/market 으로 시세를 받고, REST /api/stocks/{code}/history 로 기간별 봉을 받는다.
 */
class RemoteMarketDataSource(
    baseUrl: String,
    private val client: OkHttpClient,
    scope: CoroutineScope,
) : MarketDataSource {

    private val baseUrl = baseUrl.trimEnd('/')

    // 서버도 같은 기본 관심종목을 쓴다
    override val stocks: List<Stock> = DEFAULT_WATCHLIST

    @Volatile private var label = "봉당 5초"
    override val candleLabel: String get() = label

    private val _state = MutableStateFlow(MarketState(emptyMap(), emptyMap(), emptyMap()))
    override val state: StateFlow<MarketState> = _state.asStateFlow()

    private val _status = MutableStateFlow("서버 연결 중")
    override val status: StateFlow<String> = _status.asStateFlow()

    @Volatile private var socket: WebSocket? = null
    private val job: Job = scope.launch { connectLoop() }

    override suspend fun history(code: String, period: Period, quote: Quote): List<Candle> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/stocks/$code/history?period=${period.name}").build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching { MarketJson.decodeFromString<ErrorResponse>(body).error }.getOrNull()
                throw IOException(message ?: "HTTP ${response.code}")
            }
            MarketJson.decodeFromString<List<Candle>>(body)
        }
    }

    override fun close() {
        job.cancel()
        socket?.close(1000, null)
    }

    /** 서버 메시지 하나를 반영한다 */
    internal fun handle(text: String) {
        when (val message = MarketJson.decodeFromString<MarketMessage>(text)) {
            is MarketMessage.Snapshot -> {
                label = message.snapshot.candleLabel
                _state.value = message.snapshot.state
                _status.value = "서버 · ${message.snapshot.status}"
            }
            is MarketMessage.State -> {
                _state.value = message.state
                _status.value = "서버 · ${message.status}"
            }
        }
    }

    private suspend fun connectLoop() {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            val closed = CompletableDeferred<Throwable?>()
            var opened = false
            socket = client.newWebSocket(
                Request.Builder().url("$baseUrl/ws/market").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened = true
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching { handle(text) }
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
                },
            )
            val error = closed.await()
            socket = null
            if (opened) backoff = 1_000L
            _status.value = "서버 연결 끊김 · ${backoff / 1000}초 뒤 다시 연결" + (error?.message?.let { " ($it)" } ?: "")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }
}
