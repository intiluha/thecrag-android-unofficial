package org.thecragunofficial.android.data.remote

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Request header value that bypasses the cache (used by "Refresh"). */
const val NO_CACHE = "no-cache"

/** How long a thecrag response is reused without asking the server. */
const val CACHE_MAX_AGE_SECONDS = 3600

/** How old a cached copy may be when the network is unreachable. */
const val OFFLINE_MAX_STALE_DAYS = 90

/**
 * theCrag sends no Cache-Control on its API or page responses, so OkHttp would
 * not keep them. This network interceptor stamps one on successful GETs; the
 * disk cache then makes back navigation free and lets recently opened crags,
 * routes and topos open without a network. `/home` is left alone: its only job
 * is to set the session cookie.
 */
class CacheHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val res = chain.proceed(req)
        if (req.method != "GET" || !res.isSuccessful || !isCacheable(req.url.encodedPath)) return res
        return res.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", "public, max-age=$CACHE_MAX_AGE_SECONDS")
            .build()
    }

    private fun isCacheable(path: String) = path.startsWith("/api/") || path.startsWith("/en/climbing/")
}

/**
 * When the network fails, retries the request against the cache only, so a
 * crag that was open before still opens on the train. Rethrows the original
 * failure when nothing is cached (OkHttp answers an unsatisfiable
 * only-if-cached request with a synthetic 504).
 */
class OfflineFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.method != "GET" || req.cacheControl.onlyIfCached) return chain.proceed(req)
        return try {
            chain.proceed(req)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            val cached = chain.proceed(req.newBuilder().cacheControl(OFFLINE).build())
            if (cached.code == 504) { cached.close(); throw e }
            cached
        }
    }

    private companion object {
        val OFFLINE: CacheControl = CacheControl.Builder()
            .onlyIfCached()
            .maxStale(OFFLINE_MAX_STALE_DAYS, TimeUnit.DAYS)
            .build()
    }
}
