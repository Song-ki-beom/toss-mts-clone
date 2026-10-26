package com.example.mts.server

import com.example.mts.data.Candle
import com.example.mts.data.MarketSimulator
import com.example.mts.data.remote.MarketJson
import com.example.mts.data.remote.MarketMessage
import com.example.mts.data.remote.MarketSnapshot
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationTest {

    private fun ApplicationTestBuilder.simulatorApp(allowedOrigins: String? = null) {
        application { module(MarketSimulator(this, tickIntervalMs = 50), allowedOrigins = allowedOrigins, pushIntervalMs = 50) }
    }

    @Test
    fun healthAndSnapshot() = testApplication {
        simulatorApp()

        val health = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.bodyAsText().contains("\"status\":\"ok\""))

        val snapshot = MarketJson.decodeFromString<MarketSnapshot>(client.get("/api/market").bodyAsText())
        assertEquals(8, snapshot.stocks.size)
        assertEquals(8, snapshot.state.quotes.size)
        assertEquals(10, snapshot.state.orderBooks.getValue("005930").asks.size)
        assertEquals("모의 시세", snapshot.status)
    }

    @Test
    fun historyReturnsCandlesAndValidatesInput() = testApplication {
        simulatorApp()

        val days = client.get("/api/stocks/005930/history?period=day")
        assertEquals(HttpStatusCode.OK, days.status)
        val candles = MarketJson.decodeFromString<List<Candle>>(days.bodyAsText())
        assertEquals(120, candles.size)
        assertTrue(candles.all { it.time != null })

        assertEquals(HttpStatusCode.BadRequest, client.get("/api/stocks/005930/history?period=LIVE").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/stocks/005930/history").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/stocks/999999/history?period=DAY").status)
    }

    @Test
    fun webSocketSendsSnapshotThenUpdates() = testApplication {
        simulatorApp()
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/ws/market") {
            val first = MarketJson.decodeFromString<MarketMessage>((incoming.receive() as Frame.Text).readText())
            assertTrue(first is MarketMessage.Snapshot)
            assertEquals(8, (first as MarketMessage.Snapshot).snapshot.stocks.size)

            val second = MarketJson.decodeFromString<MarketMessage>((incoming.receive() as Frame.Text).readText())
            assertTrue(second is MarketMessage.State)
            assertEquals(8, (second as MarketMessage.State).state.quotes.size)
        }
    }

    @Test
    fun corsAllowsOnlyConfiguredHosts() = testApplication {
        simulatorApp(allowedOrigins = "toss-mts-clone.vercel.app, localhost:5173")

        val allowed = client.get("/api/health") { header(HttpHeaders.Origin, "https://toss-mts-clone.vercel.app") }
        assertEquals("https://toss-mts-clone.vercel.app", allowed.headers[HttpHeaders.AccessControlAllowOrigin])

        val local = client.get("/api/health") { header(HttpHeaders.Origin, "http://localhost:5173") }
        assertEquals("http://localhost:5173", local.headers[HttpHeaders.AccessControlAllowOrigin])

        val other = client.get("/api/health") { header(HttpHeaders.Origin, "https://evil.example.com") }
        assertNull(other.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
