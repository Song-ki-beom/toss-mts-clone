package com.example.mts.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToLong

/** 매매 수수료. 세금은 단순화를 위해 생략했다. */
object Fees {
    const val RATE = 0.00015

    fun of(amount: Long): Long = (amount * RATE).roundToLong()

    fun buyCost(price: Long, quantity: Long): Long {
        val amount = price * quantity
        return amount + of(amount)
    }
}

/**
 * 모의 계좌와 주문 체결을 담당한다.
 * 매수 주문은 접수 시점에 대금을 묶어두고, 체결가가 더 낮으면 차액을 돌려준다.
 */
class TradingEngine(initialCash: Long = 10_000_000L) {

    private val _account = MutableStateFlow(Account(initialCash, emptyMap(), emptyList()))
    val account: StateFlow<Account> = _account.asStateFlow()

    private var nextId = 1L

    @Synchronized
    fun placeOrder(
        stock: Stock,
        side: Side,
        type: OrderType,
        limitPrice: Long,
        quantity: Long,
        marketPrice: Long,
    ): Result<Order> {
        if (quantity <= 0) return failure("수량을 입력해 주세요")
        val orderPrice = if (type == OrderType.MARKET) marketPrice else limitPrice
        if (orderPrice <= 0) return failure("가격을 확인해 주세요")

        val acc = _account.value
        when (side) {
            Side.BUY -> if (Fees.buyCost(orderPrice, quantity) > acc.cash) return failure("주문 가능 금액이 부족해요")
            Side.SELL -> if (quantity > sellableQuantity(acc, stock.code)) return failure("판매 가능 수량이 부족해요")
        }

        val order = Order(
            id = nextId++,
            code = stock.code,
            name = stock.name,
            side = side,
            type = type,
            price = orderPrice,
            quantity = quantity,
            status = OrderStatus.PENDING,
            filledPrice = null,
            time = System.currentTimeMillis(),
        )
        val cash = if (side == Side.BUY) acc.cash - Fees.buyCost(orderPrice, quantity) else acc.cash
        var next = acc.copy(cash = cash, orders = listOf(order) + acc.orders)

        val marketable = type == OrderType.MARKET ||
            (side == Side.BUY && marketPrice <= orderPrice) ||
            (side == Side.SELL && marketPrice >= orderPrice)
        if (marketable) next = fill(next, order, marketPrice)

        _account.value = next
        return Result.success(next.orders.first { it.id == order.id })
    }

    /** 새 시세가 들어올 때마다 미체결 지정가 주문을 확인해 체결한다. 이번에 체결된 주문을 돌려준다. */
    @Synchronized
    fun onQuotes(quotes: Map<String, Quote>): List<Order> {
        var acc = _account.value
        val filled = mutableListOf<Order>()
        for (order in acc.orders.filter { it.status == OrderStatus.PENDING }) {
            val price = quotes[order.code]?.price ?: continue
            val hit = if (order.side == Side.BUY) price <= order.price else price >= order.price
            if (hit) {
                acc = fill(acc, order, order.price)
                filled += acc.orders.first { it.id == order.id }
            }
        }
        if (filled.isNotEmpty()) _account.value = acc
        return filled
    }

    @Synchronized
    fun cancel(orderId: Long): Boolean {
        val acc = _account.value
        val order = acc.orders.find { it.id == orderId && it.status == OrderStatus.PENDING } ?: return false
        val cash = if (order.side == Side.BUY) acc.cash + Fees.buyCost(order.price, order.quantity) else acc.cash
        _account.value = acc.copy(
            cash = cash,
            orders = acc.orders.map { if (it.id == orderId) it.copy(status = OrderStatus.CANCELED) else it },
        )
        return true
    }

    fun maxBuyQuantity(price: Long): Long {
        if (price <= 0) return 0
        var qty = (_account.value.cash / (price * (1 + Fees.RATE))).toLong()
        while (qty > 0 && Fees.buyCost(price, qty) > _account.value.cash) qty--
        return qty
    }

    fun sellableQuantity(code: String): Long = sellableQuantity(_account.value, code)

    private fun sellableQuantity(acc: Account, code: String): Long {
        val held = acc.holdings[code]?.quantity ?: 0L
        val pendingSell = acc.orders
            .filter { it.code == code && it.side == Side.SELL && it.status == OrderStatus.PENDING }
            .sumOf { it.quantity }
        return held - pendingSell
    }

    private fun fill(acc: Account, order: Order, price: Long): Account {
        val holdings = acc.holdings.toMutableMap()
        val holding = holdings[order.code]
        var cash = acc.cash
        when (order.side) {
            Side.BUY -> {
                cash += Fees.buyCost(order.price, order.quantity) - Fees.buyCost(price, order.quantity)
                val heldQty = holding?.quantity ?: 0L
                val qty = heldQty + order.quantity
                val avg = ((holding?.avgPrice ?: 0L) * heldQty + price * order.quantity) / qty
                holdings[order.code] = Holding(order.code, order.name, qty, avg)
            }
            Side.SELL -> {
                val amount = price * order.quantity
                cash += amount - Fees.of(amount)
                val qty = (holding?.quantity ?: 0L) - order.quantity
                if (holding != null && qty > 0) holdings[order.code] = holding.copy(quantity = qty) else holdings.remove(order.code)
            }
        }
        val orders = acc.orders.map {
            if (it.id == order.id) it.copy(status = OrderStatus.FILLED, filledPrice = price) else it
        }
        return Account(cash, holdings, orders)
    }

    private fun failure(message: String): Result<Order> = Result.failure(IllegalStateException(message))
}
