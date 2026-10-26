package com.example.mts.data.kis

import com.example.mts.data.Candle
import com.example.mts.data.OrderBook
import com.example.mts.data.Period
import com.example.mts.data.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** KIS REST API. 호출은 순서대로 하나씩, 서버 초당 한도에 맞춰 간격을 두고 보낸다. */
class KisApi(
    private val config: KisConfig,
    private val tokenCache: TokenCache,
    private val client: OkHttpClient,
) {
    private val mutex = Mutex()
    private var lastCallAt = 0L
    private var accessToken: String? = null

    suspend fun approvalKey(): String {
        val body = buildJsonObject {
            put("grant_type", "client_credentials")
            put("appkey", config.appKey)
            put("secretkey", config.appSecret)
        }
        val res = post("/oauth2/Approval", body)
        return res.str("approval_key").ifEmpty { throw KisException(res.str("error_code"), "실시간 접속키 발급 실패: ${res.str("error_description")}") }
    }

    suspend fun inquirePrice(code: String): Quote =
        KisParser.parseQuote(code, get(PATH_PRICE, "FHKST01010100", stockParams(code)))

    suspend fun askingPrice(code: String): OrderBook =
        KisParser.parseOrderBook(get(PATH_ASKING, "FHKST01010200", stockParams(code)))

    /** hhmmss 시각 이전 30개의 당일 1분봉 */
    suspend fun minuteCandles(code: String, hhmmss: String): List<TimedCandle> {
        val params = linkedMapOf(
            "FID_ETC_CLS_CODE" to "",
            "FID_COND_MRKT_DIV_CODE" to "J",
            "FID_INPUT_ISCD" to code,
            "FID_INPUT_HOUR_1" to hhmmss,
            "FID_PW_DATA_INCU_YN" to "N",
        )
        return KisParser.parseMinuteCandles(get(PATH_MINUTE_CHART, "FHKST03010200", params))
    }

    /** 국내주식 기간별 시세 (일·주·월·년봉, 수정주가). 한 번에 최대 100개, 오래된 순으로 돌려준다 */
    suspend fun periodCandles(code: String, period: Period, today: LocalDate): List<Candle> {
        val (divCode, from) = when (period) {
            Period.DAY -> "D" to today.minusDays(180)
            Period.WEEK -> "W" to today.minusYears(2)
            Period.MONTH -> "M" to today.minusYears(5)
            Period.YEAR -> "Y" to today.minusYears(15)
            Period.LIVE -> error("실시간은 기간별 시세가 아니다")
        }
        val params = linkedMapOf(
            "FID_COND_MRKT_DIV_CODE" to "J",
            "FID_INPUT_ISCD" to code,
            "FID_INPUT_DATE_1" to from.format(DateTimeFormatter.BASIC_ISO_DATE),
            "FID_INPUT_DATE_2" to today.format(DateTimeFormatter.BASIC_ISO_DATE),
            "FID_PERIOD_DIV_CODE" to divCode,
            "FID_ORG_ADJ_PRC" to "0",
        )
        return KisParser.parsePeriodCandles(get(PATH_PERIOD_CHART, "FHKST03010100", params))
    }

    private suspend fun token(): String =
        accessToken ?: tokenCache.load(config.appKey)?.also { accessToken = it } ?: issueToken()

    private suspend fun issueToken(): String {
        val body = buildJsonObject {
            put("grant_type", "client_credentials")
            put("appkey", config.appKey)
            put("appsecret", config.appSecret)
        }
        val res = post("/oauth2/tokenP", body)
        val token = res.str("access_token").ifEmpty {
            throw KisException(res.str("error_code"), "접근토큰 발급 실패: ${res.str("error_description")}")
        }
        tokenCache.save(config.appKey, token, res.long("expires_in").takeIf { it > 0 } ?: 86_400L)
        accessToken = token
        return token
    }

    private suspend fun get(path: String, trId: String, params: Map<String, String>, retry: Boolean = true): JsonObject {
        val url = (config.restBaseUrl + path).toHttpUrl().newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("content-type", "application/json; charset=utf-8")
            .header("authorization", "Bearer ${token()}")
            .header("appkey", config.appKey)
            .header("appsecret", config.appSecret)
            .header("tr_id", trId)
            .header("custtype", "P")
            .build()
        val res = execute(request)

        if (retry) {
            when (res.str("msg_cd")) {
                // 초당 거래건수 초과
                "EGW00201" -> {
                    delay(1_000)
                    return get(path, trId, params, retry = false)
                }
                // 토큰 만료 또는 무효
                "EGW00121", "EGW00123" -> {
                    accessToken = null
                    tokenCache.clear()
                    return get(path, trId, params, retry = false)
                }
            }
        }
        KisParser.checkOk(res)
        return res
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val request = Request.Builder()
            .url(config.restBaseUrl + path)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): JsonObject = mutex.withLock {
        val wait = lastCallAt + config.minRequestIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (text.isBlank()) throw KisException("HTTP ${response.code}", "빈 응답")
                    runCatching { KisParser.parseObject(text) }
                        .getOrElse { throw KisException("HTTP ${response.code}", text.take(120)) }
                }
            }
        } finally {
            lastCallAt = System.currentTimeMillis()
        }
    }

    private fun stockParams(code: String) = linkedMapOf(
        "FID_COND_MRKT_DIV_CODE" to "J",
        "FID_INPUT_ISCD" to code,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val PATH_PRICE = "/uapi/domestic-stock/v1/quotations/inquire-price"
        const val PATH_ASKING = "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn"
        const val PATH_MINUTE_CHART = "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
        const val PATH_PERIOD_CHART = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
    }
}
