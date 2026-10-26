package com.example.mts.data

import android.content.Context
import com.example.mts.BuildConfig
import com.example.mts.data.kis.KisConfig
import com.example.mts.data.kis.TokenCache
import com.example.mts.data.kis.createKisMarketDataSource
import com.example.mts.data.remote.RemoteMarketDataSource
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 시세 공급원 선택 (local.properties):
 * 1. mts.serverUrl 이 있으면 중계 서버 (앱에 KIS 키가 없는 증권사식 구조, 권장)
 * 2. kis.appKey / kis.appSecret 이 있으면 KIS 직접 연결 (학습용)
 * 3. 둘 다 없으면 모의 시세
 */
fun createMarketDataSource(context: Context, scope: CoroutineScope): MarketDataSource {
    if (BuildConfig.MTS_SERVER_URL.isNotBlank()) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return RemoteMarketDataSource(BuildConfig.MTS_SERVER_URL, client, scope)
    }
    val config = KisConfig.of(BuildConfig.KIS_APP_KEY, BuildConfig.KIS_APP_SECRET, BuildConfig.KIS_MOCK)
        ?: return MarketSimulator(scope)
    return createKisMarketDataSource(config, PrefsTokenCache(context.applicationContext), scope)
}

/**
 * 접근토큰을 SharedPreferences에 저장해 앱을 다시 켜도 재사용한다.
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
