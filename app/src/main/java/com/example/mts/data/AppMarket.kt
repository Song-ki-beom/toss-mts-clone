package com.example.mts.data

import android.content.Context
import com.example.mts.BuildConfig
import com.example.mts.data.kis.KisConfig
import com.example.mts.data.kis.TokenCache
import com.example.mts.data.kis.createKisMarketDataSource
import kotlinx.coroutines.CoroutineScope

/** local.properties에 KIS 키가 있으면 실시세, 없으면 모의 시세를 쓴다 */
fun createMarketDataSource(context: Context, scope: CoroutineScope): MarketDataSource {
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
