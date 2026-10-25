package com.example.mts.data

data class Stock(val code: String, val name: String, val market: String)

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

data class Candle(val open: Long, val high: Long, val low: Long, val close: Long, val volume: Long)

data class OrderBookLevel(val price: Long, val quantity: Long)

/** asks, bids 모두 가격 내림차순. 화면에 위에서부터 그대로 그리면 된다. */
data class OrderBook(val asks: List<OrderBookLevel>, val bids: List<OrderBookLevel>)

enum class Side(val label: String) { BUY("매수"), SELL("매도") }

enum class OrderType(val label: String) { LIMIT("지정가"), MARKET("시장가") }

enum class OrderStatus(val label: String) { PENDING("미체결"), FILLED("체결"), CANCELED("취소") }

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
