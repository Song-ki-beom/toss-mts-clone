package com.example.mts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mts.data.Account
import com.example.mts.data.MarketDataSource
import com.example.mts.data.MarketSimulator
import com.example.mts.data.MarketState
import com.example.mts.data.Order
import com.example.mts.data.OrderStatus
import com.example.mts.data.OrderType
import com.example.mts.data.Side
import com.example.mts.data.Stock
import com.example.mts.data.TradingEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MtsViewModel : ViewModel() {

    private val market: MarketDataSource = MarketSimulator(viewModelScope)
    private val trading = TradingEngine()

    val stocks: List<Stock> = market.stocks
    val marketState: StateFlow<MarketState> = market.state
    val account: StateFlow<Account> = trading.account

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch {
            market.state.collect { state ->
                trading.onQuotes(state.quotes).forEach { _messages.tryEmit(fillMessage(it)) }
            }
        }
    }

    fun placeOrder(stock: Stock, side: Side, type: OrderType, price: Long, quantity: Long) {
        val marketPrice = marketState.value.quotes[stock.code]?.price ?: return
        trading.placeOrder(stock, side, type, price, quantity, marketPrice)
            .onSuccess { order ->
                _messages.tryEmit(
                    if (order.status == OrderStatus.FILLED) fillMessage(order)
                    else "${order.name} ${order.quantity}주 ${side.label} 주문 접수 (${formatPrice(order.price)}원)"
                )
            }
            .onFailure { _messages.tryEmit(it.message ?: "주문에 실패했습니다") }
    }

    fun cancel(orderId: Long) {
        if (trading.cancel(orderId)) _messages.tryEmit("주문이 취소되었습니다")
    }

    fun maxBuyQuantity(price: Long): Long = trading.maxBuyQuantity(price)

    fun sellableQuantity(code: String): Long = trading.sellableQuantity(code)

    private fun fillMessage(order: Order) =
        "${order.name} ${order.quantity}주 ${order.side.label} 체결 (${formatPrice(order.filledPrice ?: order.price)}원)"
}
