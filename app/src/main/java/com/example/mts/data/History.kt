package com.example.mts.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlin.random.Random

val KST: ZoneId = ZoneId.of("Asia/Seoul")

enum class Period(val label: String, val caption: String, val count: Int, val volatility: Double) {
    LIVE("실시간", "", 0, 0.0),
    DAY("일", "최근 120일", 120, 0.022),
    WEEK("주", "최근 2년", 104, 0.045),
    MONTH("월", "최근 5년", 60, 0.085),
    YEAR("년", "최근 15년", 15, 0.22);

    /** today에서 n봉 전의 날짜 */
    fun back(today: LocalDate, n: Int): LocalDate = when (this) {
        LIVE -> today
        DAY -> minusBusinessDays(today, n)
        WEEK -> today.minusWeeks(n.toLong())
        MONTH -> today.withDayOfMonth(1).minusMonths(n.toLong())
        YEAR -> today.withDayOfYear(1).minusYears(n.toLong())
    }

    fun formatAxis(millis: Long): String {
        val pattern = when (this) {
            MONTH -> "yyyy.MM"
            YEAR -> "yyyy"
            else -> "yy.MM.dd"
        }
        return DateTimeFormatter.ofPattern(pattern).format(java.time.Instant.ofEpochMilli(millis).atZone(KST))
    }
}

/**
 * 모의 서비스용 과거 봉 생성기 (웹의 domain/history.ts와 같은 방식).
 * 종목·기간마다 항상 같은 결과를 내고, 마지막 봉은 실시간 시세로 채운다.
 */
object HistoryGenerator {

    fun build(code: String, period: Period, quote: Quote, today: LocalDate = LocalDate.now(KST)): List<Candle> {
        require(period != Period.LIVE) { "실시간은 과거 봉이 아니다" }
        val random = Random("$code:${period.name}".hashCode())
        val vol = period.volatility
        val pastCount = period.count - 1

        // 일봉은 직전 봉 종가가 어제 종가다. 주·월·연봉은 이번 봉이 더 일찍 시작했으므로 한 걸음 거슬러 올라간 값에서 시작한다
        val closes = DoubleArray(pastCount)
        var p = if (period == Period.DAY) quote.prevClose.toDouble() else quote.prevClose / (1 + (random.nextDouble() - 0.48) * vol)
        for (i in pastCount - 1 downTo 0) {
            closes[i] = p
            p /= 1 + (random.nextDouble() - 0.48) * vol
        }

        fun tick(v: Double) = maxOf(1L, roundToTick(v.roundToLong()))
        fun timeOf(i: Int) = period.back(today, period.count - 1 - i).atStartOfDay(KST).toInstant().toEpochMilli()

        val candles = closes.mapIndexed { i, close ->
            val open = if (i == 0) close / (1 + (random.nextDouble() - 0.5) * vol) else closes[i - 1]
            Candle(
                open = tick(open),
                high = tick(maxOf(open, close) * (1 + random.nextDouble() * vol * 0.4)),
                low = tick(minOf(open, close) * (1 - random.nextDouble() * vol * 0.4)),
                close = tick(close),
                volume = ((0.5 + random.nextDouble()) * 1_000_000 * (vol / 0.022)).roundToLong(),
                time = timeOf(i),
            )
        }

        val open = if (period == Period.DAY) quote.open else candles.last().close
        val current = Candle(
            open = open,
            high = maxOf(open, quote.high, quote.price),
            low = minOf(open, quote.low, quote.price),
            close = quote.price,
            volume = quote.volume,
            time = timeOf(pastCount),
        )
        return candles + current
    }
}

/** 진행 중인 마지막 봉에 실시간 가격을 반영한다 */
fun List<Candle>.withLivePrice(price: Long): List<Candle> {
    if (isEmpty()) return this
    val last = last()
    return dropLast(1) + last.copy(high = maxOf(last.high, price), low = minOf(last.low, price), close = price)
}

/** 첫 봉 시가 대비 마지막 봉 종가의 등락률(%) */
fun List<Candle>.periodReturn(): Double {
    if (isEmpty()) return 0.0
    val first = first().open
    return (last().close - first) * 100.0 / first
}

private fun minusBusinessDays(date: LocalDate, n: Int): LocalDate {
    var d = date
    while (d.isWeekend()) d = d.minusDays(1)
    var left = n
    while (left > 0) {
        d = d.minusDays(1)
        if (!d.isWeekend()) left--
    }
    return d
}

private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
