package com.example.mts.server

import com.example.mts.data.MarketDataSource
import com.example.mts.data.remote.ErrorResponse
import com.example.mts.data.remote.HealthResponse
import com.example.mts.data.remote.MarketJson
import com.example.mts.data.remote.MarketMessage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(createMarketFromEnv(this), allowedOrigins = System.getenv("ALLOWED_ORIGINS"))
    }.start(wait = true)
}

/**
 * 중계 서버 본체. 테스트에서는 모의 시세를 넣어 같은 모듈을 띄운다.
 *
 * @param allowedOrigins 브라우저 접근을 허용할 호스트 목록(쉼표 구분, 예: "toss-mts-clone.vercel.app,localhost:5173").
 *                       비어 있거나 "*"이면 모두 허용한다.
 * @param pushIntervalMs WebSocket으로 시세를 보내는 최소 간격
 */
@OptIn(FlowPreview::class)
fun Application.module(market: MarketDataSource, allowedOrigins: String? = null, pushIntervalMs: Long = 500) {
    install(ContentNegotiation) { json(MarketJson) }
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 30.seconds
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        val hosts = allowedOrigins?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (hosts.isEmpty() || hosts == listOf("*")) anyHost() else hosts.forEach { allowHost(it, schemes = listOf("http", "https")) }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "잘못된 요청")) }
        exception<NotFoundException> { call, e -> call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "없음")) }
        exception<UnavailableException> { call, e -> call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(e.message ?: "준비 중")) }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("요청 처리 실패", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("서버 오류: ${e.message}"))
        }
    }
    monitor.subscribe(ApplicationStopped) { market.close() }

    val service = MarketService(market)

    routing {
        get("/api/health") {
            call.respond(HealthResponse("ok", market.status.value))
        }

        get("/api/market") {
            call.respond(service.snapshot())
        }

        get("/api/stocks/{code}/history") {
            val code = call.parameters["code"].orEmpty()
            val period = call.request.queryParameters["period"].orEmpty()
            call.respond(service.history(code, period))
        }

        // 연결 직후 스냅샷을 보내고, 이후 시세가 바뀌면 pushIntervalMs 간격으로 최신 상태만 보낸다
        webSocket("/ws/market") {
            send(Frame.Text(MarketJson.encodeToString<MarketMessage>(MarketMessage.Snapshot(service.snapshot()))))
            market.state
                .drop(1)
                .sample(pushIntervalMs.milliseconds)
                .collect { state ->
                    send(Frame.Text(MarketJson.encodeToString<MarketMessage>(MarketMessage.State(state, market.status.value))))
                }
        }
    }
}
