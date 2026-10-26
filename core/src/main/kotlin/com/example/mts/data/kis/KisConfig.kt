package com.example.mts.data.kis

/** 한국투자증권 Open API 접속 정보. 키는 실행 환경(앱의 local.properties, 서버의 환경 변수)에서 넣어 준다. */
data class KisConfig(val appKey: String, val appSecret: String, val isMock: Boolean) {

    val restBaseUrl: String
        get() = if (isMock) "https://openapivts.koreainvestment.com:29443" else "https://openapi.koreainvestment.com:9443"

    val webSocketUrl: String
        get() = if (isMock) "ws://ops.koreainvestment.com:31000" else "ws://ops.koreainvestment.com:21000"

    /** 모의투자 서버는 초당 호출 한도가 낮아서 호출 간격을 넉넉히 둔다 */
    val minRequestIntervalMs: Long
        get() = if (isMock) 520L else 60L

    val modeLabel: String
        get() = if (isMock) "KIS 모의" else "KIS 실전"

    companion object {
        /** 키가 비어 있으면 null (모의 시세를 쓰라는 뜻) */
        fun of(appKey: String?, appSecret: String?, isMock: Boolean): KisConfig? {
            if (appKey.isNullOrBlank() || appSecret.isNullOrBlank()) return null
            return KisConfig(appKey.trim(), appSecret.trim(), isMock)
        }
    }
}

/**
 * 접근토큰은 1분에 한 번만 발급되고 하루 동안 유효하므로 재사용할 수 있게 보관한다.
 * 키를 바꾸면 이전 토큰을 쓰지 않도록 App Key 끝자리를 함께 저장하는 것을 권장한다.
 */
interface TokenCache {
    fun load(appKey: String): String?
    fun save(appKey: String, token: String, expiresInSeconds: Long)
    fun clear()
}

/** 프로세스가 살아 있는 동안만 보관하는 토큰 저장소. 계속 떠 있는 서버에 알맞다 */
class InMemoryTokenCache(private val now: () -> Long = System::currentTimeMillis) : TokenCache {
    private var owner: String? = null
    private var token: String? = null
    private var expiresAt = 0L

    @Synchronized
    override fun load(appKey: String): String? {
        if (owner != appKey.takeLast(8) || now() > expiresAt - EXPIRY_MARGIN_MS) return null
        return token
    }

    @Synchronized
    override fun save(appKey: String, token: String, expiresInSeconds: Long) {
        owner = appKey.takeLast(8)
        this.token = token
        expiresAt = now() + expiresInSeconds * 1000
    }

    @Synchronized
    override fun clear() {
        owner = null
        token = null
        expiresAt = 0L
    }

    companion object {
        const val EXPIRY_MARGIN_MS = 10 * 60 * 1000L
    }
}
