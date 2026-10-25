package com.example.mts.data.kis

import android.content.Context
import com.example.mts.BuildConfig

/** 한국투자증권 Open API 접속 정보. 키는 local.properties의 kis.appKey / kis.appSecret 에서 온다. */
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
        fun fromBuildConfig(): KisConfig? {
            val key = BuildConfig.KIS_APP_KEY
            val secret = BuildConfig.KIS_APP_SECRET
            if (key.isBlank() || secret.isBlank()) return null
            return KisConfig(key, secret, BuildConfig.KIS_MOCK)
        }
    }
}

interface TokenCache {
    fun load(appKey: String): String?
    fun save(appKey: String, token: String, expiresInSeconds: Long)
    fun clear()
}

/**
 * 접근토큰은 1분에 한 번만 발급되고 하루 동안 유효하므로 앱을 다시 켜도 재사용하도록 저장한다.
 * 키를 바꾸면 이전 토큰을 쓰지 않도록 App Key 끝자리를 함께 저장한다.
 */
class PrefsTokenCache(context: Context) : TokenCache {
    private val prefs = context.getSharedPreferences("kis_token", Context.MODE_PRIVATE)

    override fun load(appKey: String): String? {
        if (prefs.getString(KEY_OWNER, null) != appKey.takeLast(8)) return null
        val expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        if (System.currentTimeMillis() > expiresAt - EXPIRY_MARGIN_MS) return null
        return prefs.getString(KEY_TOKEN, null)
    }

    override fun save(appKey: String, token: String, expiresInSeconds: Long) {
        prefs.edit()
            .putString(KEY_OWNER, appKey.takeLast(8))
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresInSeconds * 1000)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_OWNER = "owner"
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES = "expires_at"
        const val EXPIRY_MARGIN_MS = 10 * 60 * 1000L
    }
}
