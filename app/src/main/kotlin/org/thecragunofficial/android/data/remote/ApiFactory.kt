package org.thecragunofficial.android.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    fun okHttp(cookies: PersistentCookieJar, cacheDir: File?, debug: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SessionInterceptor())
            .addInterceptor(SessionRecoveryInterceptor(cookies))
            .addInterceptor(RateLimitInterceptor())
        if (cacheDir != null) b.cache(Cache(File(cacheDir, "http"), 64L * 1024 * 1024))
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
