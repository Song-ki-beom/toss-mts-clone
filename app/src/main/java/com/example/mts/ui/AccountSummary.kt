package com.example.mts.ui

import com.example.mts.data.Account
import com.example.mts.data.Fees
import com.example.mts.data.OrderStatus
import com.example.mts.data.Quote
import com.example.mts.data.Side

/** 홈과 계좌 화면이 함께 쓰는 자산 요약 */
data class AccountSummary(
    val cash: Long,
    val reserved: Long,
    val evalAmount: Long,
    val buyAmount: Long,
) {
    val total: Long get() = cash + reserved + evalAmount
    val pnl: Long get() = evalAmount - buyAmount
    val pnlRate: Double get() = if (buyAmount > 0) pnl * 100.0 / buyAmount else 0.0
}

fun summarize(account: Account, quotes: Map<String, Quote>): AccountSummary {
    val holdings = account.holdings.values
    return AccountSummary(
        cash = account.cash,
        reserved = account.orders
            .filter { it.status == OrderStatus.PENDING && it.side == Side.BUY }
            .sumOf { Fees.buyCost(it.price, it.quantity) },
        evalAmount = holdings.sumOf { (quotes[it.code]?.price ?: it.avgPrice) * it.quantity },
        buyAmount = holdings.sumOf { it.avgPrice * it.quantity },
    )
}
