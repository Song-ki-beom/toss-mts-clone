package com.example.mts.data.kis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KisParserTest {

    @Test
    fun parsesCurrentPrice() {
        val body = KisParser.parseObject(
            """{"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.",
               "output":{"stck_prpr":"71900","prdy_vrss":"-600","prdy_ctrt":"-0.83",
                         "stck_oprc":"72300","stck_hgpr":"72500","stck_lwpr":"71600","acml_vol":"12345678"}}"""
        )
        val q = KisParser.parseQuote("005930", body)

        assertEquals(71_900L, q.price)
        assertEquals(72_500L, q.prevClose)
        assertEquals(-600L, q.change)
        assertEquals(12_345_678L, q.volume)
    }

    @Test
    fun throwsOnErrorResponse() {
        val body = KisParser.parseObject("""{"rt_cd":"1","msg_cd":"EGW00201","msg1":"초당 거래건수를 초과하였습니다."}""")
        val error = runCatching { KisParser.checkOk(body) }.exceptionOrNull()

        assertTrue(error is KisException)
        assertEquals("EGW00201", (error as KisException).code)
    }

    @Test
    fun parsesOrderBookAndDropsEmptyLevels() {
        val levels = (1..10).joinToString(",") { i ->
            val ask = if (i <= 5) 72_000 + i * 100 else 0
            val bid = if (i <= 5) 72_000 - (i - 1) * 100 else 0
            """"askp$i":"$ask","askp_rsqn$i":"${i * 10}","bidp$i":"$bid","bidp_rsqn$i":"${i * 20}""""
        }
        val book = KisParser.parseOrderBook(KisParser.parseObject("""{"rt_cd":"0","output1":{$levels}}"""))

        assertEquals(listOf(72_500L, 72_400L, 72_300L, 72_200L, 72_100L), book.asks.map { it.price })
        assertEquals(listOf(72_000L, 71_900L, 71_800L, 71_700L, 71_600L), book.bids.map { it.price })
        assertEquals(10L, book.asks.last().quantity)
    }

    @Test
    fun parsesMinuteCandlesOldestFirst() {
        val body = KisParser.parseObject(
            """{"rt_cd":"0","output2":[
                {"stck_cntg_hour":"153000","stck_prpr":"71900","stck_oprc":"71800","stck_hgpr":"72000","stck_lwpr":"71700","cntg_vol":"500"},
                {"stck_cntg_hour":"152900","stck_prpr":"71800","stck_oprc":"71700","stck_hgpr":"71900","stck_lwpr":"71600","cntg_vol":"300"}
            ]}"""
        )
        val candles = KisParser.parseMinuteCandles(body)

        assertEquals(listOf("152900", "153000"), candles.map { it.time })
        assertEquals(71_900L, candles.last().candle.close)
    }

    @Test
    fun parsesPeriodCandlesOldestFirstWithDates() {
        val body = KisParser.parseObject(
            """{"rt_cd":"0","output1":{"stck_prpr":"71900"},"output2":[
                {"stck_bsop_date":"20261026","stck_oprc":"71800","stck_hgpr":"72600","stck_lwpr":"71400","stck_clpr":"72300","acml_vol":"12345678"},
                {"stck_bsop_date":"20261023","stck_oprc":"70900","stck_hgpr":"71700","stck_lwpr":"70800","stck_clpr":"71500","acml_vol":"9876543"},
                {"stck_bsop_date":"","stck_oprc":"","stck_hgpr":"","stck_lwpr":"","stck_clpr":"","acml_vol":""}
            ]}"""
        )
        val candles = KisParser.parsePeriodCandles(body)

        assertEquals(2, candles.size)
        assertEquals(71_500L, candles.first().close)
        assertEquals(72_300L, candles.last().close)
        assertEquals(12_345_678L, candles.last().volume)
        val date = java.time.Instant.ofEpochMilli(candles.last().time!!).atZone(com.example.mts.data.KST).toLocalDate()
        assertEquals(java.time.LocalDate.of(2026, 10, 26), date)
    }

    @Test
    fun parsesRealtimeTradeFrames() {
        fun record(code: String, price: Int, signCode: String, change: String) =
            MutableList(46) { "0" }.apply {
                this[0] = code; this[1] = "093015"; this[2] = "$price"; this[3] = signCode; this[4] = change
                this[7] = "72000"; this[8] = "72500"; this[9] = "71500"; this[12] = "15"; this[13] = "1000000"
            }.joinToString("^")

        val single = KisParser.parseFrame("0|H0STCNT0|001|" + record("005930", 71_900, "5", "-600"))
        val trade = single.single() as KisFrame.Trade
        assertEquals("005930", trade.code)
        assertEquals(71_900L, trade.price)
        assertEquals(-600L, trade.change)
        assertEquals(15L, trade.tradeVolume)

        // 묶음 전송(건수 002)과 부호 없는 하락값
        val batch = KisParser.parseFrame("0|H0STCNT0|002|" + record("005930", 71_900, "5", "600") + "^" + record("000660", 198_000, "2", "1000"))
        assertEquals(listOf(-600L, 1_000L), batch.map { (it as KisFrame.Trade).change })
    }

    @Test
    fun snapsMockMidpointTradeToAsk() {
        // KIS 모의 서버에서 실제로 받은 프레임: 매도1호가 215,500 · 매수1호가 215,000 사이 중간값 215,250
        val raw = "0|H0STCNT0|001|005930^140341^215250^5^-250^-0.12^213691.93^213500^216000^211500^215500^215000^5^8060134^1722386067500^42963^51210^8247^92.15^4011957^3697017^1^0.46^49.92^090008^2^1750^090847^5^-750^090451^2^3750^20261026^20^N^119255^1492^552689^508958^0.14^11642683^69.23^0^^213500^2"
        val trade = KisParser.parseFrame(raw).single() as KisFrame.Trade
        assertEquals(215_250L, trade.price)
        assertEquals(215_500L, trade.askPrice)

        val snapped = trade.snappedToAsk()
        assertEquals(215_500L, snapped.price)
        assertEquals(0L, snapped.change)  // 전일 종가 215,500은 그대로
        assertEquals(trade.price - trade.change, snapped.price - snapped.change)

        // 호가 단위에 맞는 체결가는 건드리지 않는다
        val onTick = trade.copy(price = 215_000, change = -500)
        assertEquals(onTick, onTick.snappedToAsk())
    }

    @Test
    fun parsesRealtimeAskingFrame() {
        val f = MutableList(59) { "0" }
        f[0] = "005930"
        for (i in 0..9) {
            f[3 + i] = "${72_000 + (i + 1) * 100}"   // 매도호가1~10
            f[13 + i] = "${72_000 - i * 100}"         // 매수호가1~10
            f[23 + i] = "${(i + 1) * 10}"
            f[33 + i] = "${(i + 1) * 20}"
        }
        val asking = KisParser.parseFrame("0|H0STASP0|001|" + f.joinToString("^")).single() as KisFrame.Asking

        assertEquals(73_000L, asking.book.asks.first().price)
        assertEquals(72_100L, asking.book.asks.last().price)
        assertEquals(72_000L, asking.book.bids.first().price)
        assertEquals(20L, asking.book.bids.first().quantity)
    }

    @Test
    fun parsesControlMessages() {
        assertEquals(
            listOf(KisFrame.PingPong),
            KisParser.parseFrame("""{"header":{"tr_id":"PINGPONG","datetime":"20261026093000"}}"""),
        )
        val control = KisParser.parseFrame(
            """{"header":{"tr_id":"H0STCNT0","tr_key":"005930","encrypt":"N"},"body":{"rt_cd":"0","msg_cd":"OPSP0000","msg1":"SUBSCRIBE SUCCESS"}}"""
        ).single() as KisFrame.Control
        assertTrue(control.ok)
        assertEquals("SUBSCRIBE SUCCESS", control.message)

        assertTrue(KisParser.parseFrame("1|H0STCNI9|001|encrypted").isEmpty())
    }

    @Test
    fun buildsSubscribeMessage() {
        val msg = KisParser.parseObject(KisParser.subscribeMessage("approval-123", KisParser.TR_TRADE, "005930"))
        assertEquals("approval-123", (msg["header"] as kotlinx.serialization.json.JsonObject).str("approval_key"))
        assertTrue(msg.toString().contains("\"tr_key\":\"005930\""))
    }
}
