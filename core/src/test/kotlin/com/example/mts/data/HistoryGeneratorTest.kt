package com.example.mts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class HistoryGeneratorTest {

    private val quote = Quote("005930", price = 72_300, prevClose = 71_500, open = 71_800, high = 72_600, low = 71_400, volume = 120_000)
    private val today = LocalDate.of(2026, 10, 26) // 월요일
    private val periods = listOf(Period.DAY, Period.WEEK, Period.MONTH, Period.YEAR)

    private fun dateOf(c: Candle) = Instant.ofEpochMilli(c.time!!).atZone(KST).toLocalDate()

    @Test
    fun buildsExpectedCountAndEndsWithLiveQuote() {
        for (period in periods) {
            val candles = HistoryGenerator.build("005930", period, quote, today)
            assertEquals(period.name, period.count, candles.size)
            assertEquals(quote.price, candles.last().close)
            assertTrue(candles.last().high >= quote.high)
            assertTrue(candles.last().low <= quote.low)
        }
    }

    @Test
    fun dailyStartsFromYesterdayCloseAndTodaysOpen() {
        val days = HistoryGenerator.build("005930", Period.DAY, quote, today)
        assertEquals(quote.prevClose, days[days.size - 2].close)
        assertEquals(quote.open, days.last().open)
    }

    @Test
    fun pricesAreTickAlignedAndWrapOpenClose() {
        for (period in periods) {
            for (c in HistoryGenerator.build("000660", period, quote.copy(code = "000660"), today).dropLast(1)) {
                for (v in listOf(c.open, c.high, c.low, c.close)) {
                    assertTrue(v > 0)
                    assertEquals(0L, v % tickSize(v))
                }
                assertTrue(c.high >= maxOf(c.open, c.close))
                assertTrue(c.low <= minOf(c.open, c.close))
            }
        }
    }

    @Test
    fun deterministicPerStockAndPeriod() {
        val a = HistoryGenerator.build("005930", Period.MONTH, quote, today)
        assertEquals(a, HistoryGenerator.build("005930", Period.MONTH, quote, today))
        assertNotEquals(a, HistoryGenerator.build("035720", Period.MONTH, quote, today))
    }

    @Test
    fun datesIncreaseAndDailySkipsWeekends() {
        val days = HistoryGenerator.build("005930", Period.DAY, quote, today)
        val times = days.map { it.time!! }
        assertEquals(times.sorted(), times)
        assertTrue(days.none { dateOf(it).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })
        assertEquals(today, dateOf(days.last()))

        val years = HistoryGenerator.build("005930", Period.YEAR, quote, today)
        assertEquals(2012, dateOf(years.first()).year)
        assertEquals(2026, dateOf(years.last()).year)
    }

    @Test
    fun livePriceUpdatesOnlyTheLastCandle() {
        val candles = listOf(Candle(100, 110, 90, 105, 0), Candle(105, 112, 100, 108, 0))
        val updated = candles.withLivePrice(120)
        assertEquals(candles.first(), updated.first())
        assertEquals(Candle(105, 120, 100, 120, 0), updated.last())
        assertEquals(20.0, updated.periodReturn(), 1e-9)
    }
}
