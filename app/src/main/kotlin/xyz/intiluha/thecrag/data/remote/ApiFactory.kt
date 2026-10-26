package xyz.intiluha.thecrag.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import xyz.intiluha.thecrag.data.offline.OfflineInterceptor
import xyz.intiluha.thecrag.data.offline.OfflineStore
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object ApiFactory {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Interceptor order matters: headers first, then the offline fallback wraps
     * the session recovery so a failed bootstrap still ends in a cache lookup.
     * The pinned store of starred areas answers before any of that. Rate limiting
     * and cache stamping are network interceptors, so cache hits
     * cost no tokens.
     */
    fun okHttp(cookies: PersistentCookieJar, cacheDir: File?, debug: Boolean, offline: OfflineStore? = null): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SessionInterceptor())
        if (offline != null) b.addInterceptor(OfflineInterceptor(offline))
        b.addInterceptor(OfflineFallbackInterceptor())
            .addInterceptor(SessionRecoveryInterceptor(cookies))
            .addNetworkInterceptor(RateLimitInterceptor())
            .addNetworkInterceptor(CacheHeaderInterceptor())
        if (cacheDir != null) b.cache(Cache(File(cacheDir, "http"), 128L * 1024 * 1024))
        if (debug) b.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        return b.build()
    }

    fun retrofit(client: OkHttpClient, baseUrl: String = THECRAG_BASE): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun api(retrofit: Retrofit): TheCragApi = retrofit.create(TheCragApi::class.java)
}

/**
 * Client for the logged-in session: its own cookie jar, no redirects (a login
 * success is the 302 itself, the target page is never loaded), no disk cache,
 * same rate limiter. Nothing else ever uses this client.
 */
fun ApiFactory.authHttp(cookies: PersistentCookieJar, debug: Boolean): OkHttpClient {
    val b = OkHttpClient.Builder()
        .cookieJar(cookies)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(SessionInterceptor())
        .addNetworkInterceptor(RateLimitInterceptor())
    if (debug) b.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    return b.build()
}
