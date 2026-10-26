package com.example.mts.data.remote

import com.example.mts.data.MarketState
import com.example.mts.data.Stock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 중계 서버와 앱·웹이 주고받는 JSON 형식. 서버와 클라이언트가 이 정의를 함께 쓴다 */
val MarketJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

/** GET /api/market 응답, WebSocket 첫 메시지 */
@Serializable
data class MarketSnapshot(
    val stocks: List<Stock>,
    val state: MarketState,
    val status: String,
    val candleLabel: String,
)

/** WebSocket /ws/market 메시지. "type" 필드로 종류를 구분한다 */
@Serializable
sealed interface MarketMessage {
    /** 연결 직후 한 번: 종목 목록과 전체 시세 */
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(val snapshot: MarketSnapshot) : MarketMessage

    /** 이후 주기적으로: 바뀐 시세 */
    @Serializable
    @SerialName("state")
    data class State(val state: MarketState, val status: String) : MarketMessage
}

@Serializable
data class HealthResponse(val status: String, val market: String)

@Serializable
data class ErrorResponse(val error: String)
