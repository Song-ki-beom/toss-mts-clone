package com.example.mts.data

import kotlinx.serialization.Serializable

// @Serializable 이 붙은 모델은 중계 서버와 앱·웹 사이의 JSON 형식이기도 하다

@Serializable
data class Stock(val code: String, val name: String, val market: String)

val DEFAULT_WATCHLIST = listOf(
    Stock("005930", "삼성전자", "KOSPI"),
    Stock("000660", "SK하이닉스", "KOSPI"),
    Stock("035420", "NAVER", "KOSPI"),
    Stock("035720", "카카오", "KOSPI"),
    Stock("005380", "현대차", "KOSPI"),
    Stock("373220", "LG에너지솔루션", "KOSPI"),
    Stock("068270", "셀트리온", "KOSPI"),
    Stock("247540", "에코프로비엠", "KOSDAQ"),
)

@Serializable
data class Quote(
    val code: String,
    val price: Long,
    val prevClose: Long,
    val open: Long,
    val high: Long,
    val low: Long,
    val volume: Long,
) {
    val change: Long get() = price - prevClose
    val changeRate: Double get() = change * 100.0 / prevClose
}

/** time은 봉의 기준 시각(epoch ms). 실시간 봉에는 없다 */
@Serializable
data class Candle(val open: Long, val high: Long, val low: Long, val close: Long, val volume: Long, val time: Long? = null)

@Serializable
data class OrderBookLevel(val price: Long, val quantity: Long)

/** asks, bids 모두 가격 내림차순. 화면에 위에서부터 그대로 그리면 된다. */
@Serializable
data class OrderBook(val asks: List<OrderBookLevel>, val bids: List<OrderBookLevel>)

enum class Side(val label: String) { BUY("구매"), SELL("판매") }

enum class OrderType(val label: String) { LIMIT("지정가"), MARKET("시장가") }

enum class OrderStatus(val label: String) { PENDING("대기"), FILLED("체결"), CANCELED("취소") }

data class Order(
    val id: Long,
    val code: String,
    val name: String,
    val side: Side,
    val type: OrderType,
    val price: Long,
    val quantity: Long,
    val status: OrderStatus,
    val filledPrice: Long?,
    val time: Long,
)

data class Holding(val code: String, val name: String, val quantity: Long, val avgPrice: Long)

data class Account(
    val cash: Long,
    val holdings: Map<String, Holding>,
    val orders: List<Order>,
)

/** 한국거래소 호가단위 (2023년 개편 기준) */
fun tickSize(price: Long): Long = when {
    price < 2_000L -> 1L
    price < 5_000L -> 5L
    price < 20_000L -> 10L
    price < 50_000L -> 50L
    price < 200_000L -> 100L
    price < 500_000L -> 500L
    else -> 1_000L
}

fun roundToTick(price: Long): Long {
    val tick = tickSize(price)
    return price / tick * tick
}
