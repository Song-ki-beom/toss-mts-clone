package com.example.mts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingEngineTest {

    private val samsung = Stock("005930", "삼성전자", "KOSPI")

    private fun quote(price: Long) = mapOf(samsung.code to Quote(samsung.code, price, 70_000, 70_000, price, price, 0))

    @Test
    fun marketBuyFillsImmediatelyAndDeductsCash() {
        val engine = TradingEngine(initialCash = 1_000_000)
        val order = engine.placeOrder(samsung, Side.BUY, OrderType.MARKET, 0, 10, 70_000).getOrThrow()

        assertEquals(OrderStatus.FILLED, order.status)
        val acc = engine.account.value
        assertEquals(1_000_000 - Fees.buyCost(70_000, 10), acc.cash)
        assertEquals(10L, acc.holdings.getValue(samsung.code).quantity)
        assertEquals(70_000L, acc.holdings.getValue(samsung.code).avgPrice)
    }

    @Test
    fun limitBuyStaysPendingUntilPriceReachesLimit() {
        val engine = TradingEngine(initialCash = 1_000_000)
        val order = engine.placeOrder(samsung, Side.BUY, OrderType.LIMIT, 69_000, 5, 70_000).getOrThrow()
        assertEquals(OrderStatus.PENDING, order.status)

        assertTrue(engine.onQuotes(quote(69_500)).isEmpty())
        val filled = engine.onQuotes(quote(68_900))

        assertEquals(1, filled.size)
        assertEquals(69_000L, filled.first().filledPrice)
        assertEquals(5L, engine.account.value.holdings.getValue(samsung.code).quantity)
    }

    @Test
    fun cancelRefundsReservedCash() {
        val engine = TradingEngine(initialCash = 1_000_000)
        val order = engine.placeOrder(samsung, Side.BUY, OrderType.LIMIT, 60_000, 5, 70_000).getOrThrow()

        assertTrue(engine.cancel(order.id))
        assertEquals(1_000_000L, engine.account.value.cash)
        assertEquals(OrderStatus.CANCELED, engine.account.value.orders.first().status)
    }

    @Test
    fun rejectsSellingMoreThanHeldAndBuyingBeyondCash() {
        val engine = TradingEngine(initialCash = 100_000)

        assertTrue(engine.placeOrder(samsung, Side.SELL, OrderType.MARKET, 0, 1, 70_000).isFailure)
        assertTrue(engine.placeOrder(samsung, Side.BUY, OrderType.MARKET, 0, 2, 70_000).isFailure)
        assertEquals(1L, engine.maxBuyQuantity(70_000))
    }

    @Test
    fun sellAveragesOutAndRemovesHoldingWhenFullySold() {
        val engine = TradingEngine(initialCash = 1_000_000)
        engine.placeOrder(samsung, Side.BUY, OrderType.MARKET, 0, 2, 70_000)
        engine.placeOrder(samsung, Side.BUY, OrderType.MARKET, 0, 2, 72_000)
        assertEquals(71_000L, engine.account.value.holdings.getValue(samsung.code).avgPrice)

        engine.placeOrder(samsung, Side.SELL, OrderType.MARKET, 0, 4, 75_000)
        assertTrue(engine.account.value.holdings.isEmpty())
    }
}
