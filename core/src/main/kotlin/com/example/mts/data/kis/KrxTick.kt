package com.example.mts.data.kis

/** KRX 주식 호가 단위 (2023.01 개편 기준, 코스피·코스닥 공통) */
fun krxTickSize(price: Long): Long = when {
    price < 2_000 -> 1
    price < 5_000 -> 5
    price < 20_000 -> 10
    price < 50_000 -> 50
    price < 200_000 -> 100
    price < 500_000 -> 500
    else -> 1_000
}

/** 가격이 호가 단위에 맞는지. 맞지 않으면 체결가로 나올 수 없는 값이다 */
fun isOnKrxTick(price: Long): Boolean = price > 0 && price % krxTickSize(price) == 0L
