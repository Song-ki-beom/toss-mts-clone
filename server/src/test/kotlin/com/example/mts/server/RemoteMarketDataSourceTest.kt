package com.example.mts.server

import com.example.mts.data.MarketSimulator
import com.example.mts.data.Period
import com.example.mts.data.remote.RemoteMarketDataSource
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 실제 서버를 임의 포트로 띄우고, 앱이 쓰는 RemoteMarketDataSource로 접속하는 종단 간 테스트 */
class RemoteMarketDataSourceTest {

    @Test
    fun receivesQuotesAndHistoryFromRealServer() = runBlocking {
        val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            module(MarketSimulator(this, tickIntervalMs = 50), pushIntervalMs = 50)
        }.start(wait = false)
        val port = server.engine.resolvedConnectors().first().port
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val remote = RemoteMarketDataSource("http://127.0.0.1:$port/", OkHttpClient(), scope)
        try {
            // 스냅샷으로 8종목 시세를 받는다
            val first = withTimeout(10_000) { remote.state.first { it.quotes.size == 8 } }
            assertEquals("서버 · 모의 시세", remote.status.value)

            // 이후 시세가 갱신된다
            withTimeout(10_000) { remote.state.first { it !== first } }

            val quote = remote.state.value.quotes.getValue("005930")
            val weeks = remote.history("005930", Period.WEEK, quote)
            assertEquals(104, weeks.size)
            assertTrue(weeks.all { it.time != null })

            val error = runCatching { remote.history("999999", Period.DAY, quote) }.exceptionOrNull()
            assertTrue(error is IOException)
            assertTrue(error!!.message!!.contains("999999"))
        } finally {
            remote.close()
            scope.cancel()
            server.stop(100, 1_000)
        }
    }
}
