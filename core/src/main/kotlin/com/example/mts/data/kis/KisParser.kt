package com.example.mts.data.kis

import com.example.mts.data.Candle
import com.example.mts.data.KST
import com.example.mts.data.OrderBook
import com.example.mts.data.OrderBookLevel
import com.example.mts.data.Quote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class KisException(val code: String, message: String) : Exception(if (code.isBlank()) message else "[$code] $message")

/** 분봉 하나. time은 체결시각 HHMMSS */
data class TimedCandle(val time: String, val candle: Candle)

/** 실시간 WebSocket으로 들어오는 메시지 */
sealed interface KisFrame {
    data class Trade(
        val code: String,
        val time: String,
        val price: Long,
        val change: Long,
        val open: Long,
        val high: Long,
        val low: Long,
        val tradeVolume: Long,
        val accVolume: Long,
        val askPrice: Long = 0,
    ) : KisFrame {
        /**
         * KIS 모의 서버는 매수 체결가를 매도·매수 1호가의 중간값으로 보내 호가 단위에 맞지 않을 때가 있다.
         * 그런 체결은 매수 체결이 일어나는 매도 1호가로 맞춘다. 전일 종가가 바뀌지 않도록 대비도 같이 옮긴다.
         */
        fun snappedToAsk(): Trade {
            if (isOnKrxTick(price) || askPrice <= 0) return this
            return copy(
                price = askPrice,
                change = change + (askPrice - price),
                high = maxOf(high, askPrice),
                low = minOf(low, askPrice),
            )
        }
    }

    data class Asking(val code: String, val book: OrderBook) : KisFrame

    data object PingPong : KisFrame

    data class Control(val trId: String, val code: String, val ok: Boolean, val message: String) : KisFrame
}

/** KIS 응답(JSON, 실시간 '|'·'^' 구분 문자열)을 앱 모델로 바꾼다. 네트워크와 분리해 단위 테스트할 수 있게 했다. */
object KisParser {
    const val TR_TRADE = "H0STCNT0"
    const val TR_ASKING = "H0STASP0"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseObject(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    fun checkOk(body: JsonObject) {
        val rtCd = body.str("rt_cd")
        if (rtCd.isNotEmpty() && rtCd != "0") throw KisException(body.str("msg_cd"), body.str("msg1"))
    }

    /** 주식현재가 시세 (FHKST01010100) */
    fun parseQuote(code: String, body: JsonObject): Quote {
        val o = body.obj("output")
        val price = o.long("stck_prpr")
        val change = o.long("prdy_vrss")
        return Quote(
            code = code,
            price = price,
            prevClose = price - change,
            open = o.long("stck_oprc"),
            high = o.long("stck_hgpr"),
            low = o.long("stck_lwpr"),
            volume = o.long("acml_vol"),
        )
    }

    /** 주식현재가 호가 (FHKST01010200). 매도1~10, 매수1~10 */
    fun parseOrderBook(body: JsonObject): OrderBook {
        val o = body.obj("output1")
        val asks = (10 downTo 1).map { OrderBookLevel(o.long("askp$it"), o.long("askp_rsqn$it")) }
        val bids = (1..10).map { OrderBookLevel(o.long("bidp$it"), o.long("bidp_rsqn$it")) }
        return OrderBook(asks.filter { it.price > 0 }, bids.filter { it.price > 0 })
    }

    /** 주식당일분봉조회 (FHKST03010200). 응답은 최신순이라 오래된 순으로 뒤집는다 */
    fun parseMinuteCandles(body: JsonObject): List<TimedCandle> {
        val rows = body["output2"] as? JsonArray ?: return emptyList()
        return rows.map { it.jsonObject }
            .filter { it.long("stck_prpr") > 0 }
            .map {
                TimedCandle(
                    time = it.str("stck_cntg_hour"),
                    candle = Candle(it.long("stck_oprc"), it.long("stck_hgpr"), it.long("stck_lwpr"), it.long("stck_prpr"), it.long("cntg_vol")),
                )
            }
            .reversed()
    }

    /** 국내주식 기간별 시세 (FHKST03010100). 응답은 최신순이라 오래된 순으로 뒤집는다 */
    fun parsePeriodCandles(body: JsonObject): List<Candle> {
        val rows = body["output2"] as? JsonArray ?: return emptyList()
        return rows.map { it.jsonObject }
            .filter { it.long("stck_clpr") > 0 && it.str("stck_bsop_date").length == 8 }
            .map {
                Candle(
                    open = it.long("stck_oprc"),
                    high = it.long("stck_hgpr"),
                    low = it.long("stck_lwpr"),
                    close = it.long("stck_clpr"),
                    volume = it.long("acml_vol"),
                    time = LocalDate.parse(it.str("stck_bsop_date"), DateTimeFormatter.BASIC_ISO_DATE)
                        .atStartOfDay(KST).toInstant().toEpochMilli(),
                )
            }
            .reversed()
    }

    fun subscribeMessage(approvalKey: String, trId: String, code: String, subscribe: Boolean = true): String =
        buildJsonObject {
            putJsonObject("header") {
                put("approval_key", approvalKey)
                put("custtype", "P")
                put("tr_type", if (subscribe) "1" else "2")
                put("content-type", "utf-8")
            }
            putJsonObject("body") {
                putJsonObject("input") {
                    put("tr_id", trId)
                    put("tr_key", code)
                }
            }
        }.toString()

    /**
     * 실시간 데이터는 "0|H0STCNT0|001|필드^필드^..." 형태다. 세 번째 값은 묶여 온 건수.
     * 그 밖의 메시지(구독 응답, PINGPONG)는 JSON이다.
     */
    fun parseFrame(text: String): List<KisFrame> {
        if (text.isEmpty()) return emptyList()
        if (text[0] == '0' || text[0] == '1') {
            val parts = text.split('|', limit = 4)
            // '1'은 암호화된 체결통보로, 시세 화면에는 쓰지 않는다
            if (parts.size < 4 || parts[0] != "0") return emptyList()
            val trId = parts[1]
            val count = (parts[2].toIntOrNull() ?: 1).coerceAtLeast(1)
            val fields = parts[3].split('^')
            val size = fields.size / count
            if (size == 0) return emptyList()
            return (0 until count).mapNotNull { i ->
                val record = fields.subList(i * size, (i + 1) * size)
                when (trId) {
                    TR_TRADE -> parseTrade(record)
                    TR_ASKING -> parseAsking(record)
                    else -> null
                }
            }
        }

        val obj = parseObject(text)
        val header = obj["header"] as? JsonObject ?: return emptyList()
        val trId = header.str("tr_id")
        if (trId == "PINGPONG") return listOf(KisFrame.PingPong)
        val body = obj["body"] as? JsonObject
        return listOf(
            KisFrame.Control(
                trId = trId,
                code = header.str("tr_key"),
                ok = body?.str("rt_cd") == "0",
                message = body?.str("msg1").orEmpty(),
            )
        )
    }

    // H0STCNT0: 0 종목코드, 1 체결시간, 2 현재가, 3 전일대비부호, 4 전일대비, 5 등락률,
    //           7 시가, 8 고가, 9 저가, 10 매도호가1, 12 체결거래량, 13 누적거래량
    private fun parseTrade(f: List<String>): KisFrame.Trade? {
        if (f.size < 14) return null
        var change = f[4].num()
        // 부호 4(하한), 5(하락)인데 값에 '-'가 없으면 음수로 맞춘다
        if (change > 0 && (f[3] == "4" || f[3] == "5")) change = -change
        return KisFrame.Trade(
            code = f[0],
            time = f[1],
            price = f[2].num(),
            change = change,
            open = f[7].num(),
            high = f[8].num(),
            low = f[9].num(),
            tradeVolume = f[12].num(),
            accVolume = f[13].num(),
            askPrice = f[10].num(),
        )
    }

    // H0STASP0: 0 종목코드, 3~12 매도호가1~10, 13~22 매수호가1~10, 23~32 매도잔량1~10, 33~42 매수잔량1~10
    private fun parseAsking(f: List<String>): KisFrame.Asking? {
        if (f.size < 43) return null
        val asks = (9 downTo 0).map { OrderBookLevel(f[3 + it].num(), f[23 + it].num()) }
        val bids = (0..9).map { OrderBookLevel(f[13 + it].num(), f[33 + it].num()) }
        return KisFrame.Asking(f[0], OrderBook(asks.filter { it.price > 0 }, bids.filter { it.price > 0 }))
    }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw KisException("", "응답에 $key 가 없습니다")
}

private fun String.num(): Long = trim().toLongOrNull() ?: trim().toDoubleOrNull()?.toLong() ?: 0L

internal fun JsonObject.str(key: String): String = (this[key]?.jsonPrimitive?.contentOrNull).orEmpty()

internal fun JsonObject.long(key: String): Long = str(key).num()
