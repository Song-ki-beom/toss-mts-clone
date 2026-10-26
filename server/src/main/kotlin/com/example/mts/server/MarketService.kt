package com.example.mts.server

import com.example.mts.data.Candle
import com.example.mts.data.KST
import com.example.mts.data.MarketDataSource
import com.example.mts.data.MarketSimulator
import com.example.mts.data.Period
import com.example.mts.data.kis.InMemoryTokenCache
import com.example.mts.data.kis.KisConfig
import com.example.mts.data.kis.createKisMarketDataSource
import com.example.mts.data.remote.MarketSnapshot
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class BadRequestException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
class UnavailableException(message: String) : RuntimeException(message)

/** 환경 변수 KIS_APP_KEY, KIS_APP_SECRET가 있으면 KIS 실시세, 없으면 모의 시세. KIS_MOCK=false면 실전 서버 */
fun createMarketFromEnv(scope: CoroutineScope, env: (String) -> String? = System::getenv): MarketDataSource {
    val config = KisConfig.of(env("KIS_APP_KEY"), env("KIS_APP_SECRET"), isMock = env("KIS_MOCK")?.lowercase() != "false")
        ?: return MarketSimulator(scope)
    val log = LoggerFactory.getLogger("KisMarket")
    return createKisMarketDataSource(config, InMemoryTokenCache(), scope, log = log::warn)
}

/** 라우트에서 쓰는 조회 로직. 기간별 봉은 KIS 호출 한도를 아끼기 위해 잠시 캐시한다 */
class MarketService(
    private val market: MarketDataSource,
    private val historyTtlMs: Long = 10 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val code: String, val period: Period, val date: LocalDate)
    private class Entry(val candles: List<Candle>, val savedAt: Long)

    private val historyCache = ConcurrentHashMap<Key, Entry>()

    fun snapshot() = MarketSnapshot(
        stocks = market.stocks,
        state = market.state.value,
        status = market.status.value,
        candleLabel = market.candleLabel,
    )

    suspend fun history(code: String, periodName: String): List<Candle> {
        if (market.stocks.none { it.code == code }) throw NotFoundException("종목 $code 을(를) 찾을 수 없어요")
        val period = Period.entries.find { it.name.equals(periodName, ignoreCase = true) && it != Period.LIVE }
            ?: throw BadRequestException("period는 DAY, WEEK, MONTH, YEAR 중 하나여야 해요")
        val quote = market.state.value.quotes[code] ?: throw UnavailableException("아직 시세를 불러오는 중이에요")

        val key = Key(code, period, LocalDate.now(KST))
        historyCache[key]?.takeIf { now() - it.savedAt < historyTtlMs }?.let { return it.candles }
        val candles = market.history(code, period, quote)
        if (candles.isNotEmpty()) historyCache[key] = Entry(candles, now())
        return candles
    }
}
