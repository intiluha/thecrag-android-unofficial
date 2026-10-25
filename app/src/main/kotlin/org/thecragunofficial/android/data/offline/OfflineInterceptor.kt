package org.thecragunofficial.android.data.offline

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import java.io.IOException

/** Request tag that marks the downloader's own requests, which must reach the network. */
object OfflineDownload

/**
 * Answers GETs whose URL is pinned by a starred area from the [OfflineStore],
 * without touching the network. An explicit refresh (`Cache-Control: no-cache`)
 * goes to the network and the fresh body replaces the pinned copy, so a starred
 * area never shows older data than the user has just seen; if that network call
 * fails the pinned copy is served instead.
 */
class OfflineInterceptor(private val store: OfflineStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.method != "GET" || req.tag(OfflineDownload::class.java) != null) return chain.proceed(req)
        val url = req.url.toString()
        val entry = store.entry(url) ?: return chain.proceed(req)
        val file = store.file(url)
        if (!file.exists()) return chain.proceed(req)

        if (!req.cacheControl.noCache) return serve(chain, entry)
        val res = try {
            chain.proceed(req)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            return serve(chain, entry)
        }
        val body = res.body
        if (!res.isSuccessful || body == null) return res
        val bytes = body.bytes()
        store.put(url, bytes, body.contentType()?.toString() ?: entry.contentType)
        return res.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
    }

    private fun serve(chain: Interceptor.Chain, entry: Entry): Response {
        val file = store.file(entry.url)
        val type = entry.contentType.toMediaTypeOrNull()
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", entry.contentType)
            .header(HEADER, "store")
            .body(file.source().buffer().asResponseBody(type, file.length()))
            .build()
    }

    companion object {
        /** Marks responses that came from the pinned store (handy in tests and logs). */
        const val HEADER = "X-Offline"
    }
}
