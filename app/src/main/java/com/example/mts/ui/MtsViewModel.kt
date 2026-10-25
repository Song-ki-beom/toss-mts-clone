package com.example.mts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mts.data.Account
import com.example.mts.data.MarketDataSource
import com.example.mts.data.MarketState
import com.example.mts.data.Order
import com.example.mts.data.OrderStatus
import com.example.mts.data.OrderType
import com.example.mts.data.Side
import com.example.mts.data.Stock
import com.example.mts.data.TradingEngine
import com.example.mts.data.kis.createMarketDataSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MtsViewModel(application: Application) : AndroidViewModel(application) {

    private val market: MarketDataSource = createMarketDataSource(application, viewModelScope)
    private val trading = TradingEngine()

    val stocks: List<Stock> = market.stocks
    val marketState: StateFlow<MarketState> = market.state
    val marketStatus: StateFlow<String> = market.status
    val candleLabel: String = market.candleLabel
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
                    else "${order.name} ${order.quantity}주 ${side.label} 주문을 접수했어요 (${formatPrice(order.price)}원)"
                )
            }
            .onFailure { _messages.tryEmit(it.message ?: "주문하지 못했어요") }
    }

    fun cancel(orderId: Long) {
        if (trading.cancel(orderId)) _messages.tryEmit("주문을 취소했어요")
    }

    override fun onCleared() {
        market.close()
    }

    fun maxBuyQuantity(price: Long): Long = trading.maxBuyQuantity(price)

    fun sellableQuantity(code: String): Long = trading.sellableQuantity(code)

    private fun fillMessage(order: Order) =
        "${order.name} ${order.quantity}주 ${order.side.label} 완료 (${formatPrice(order.filledPrice ?: order.price)}원)"
}
