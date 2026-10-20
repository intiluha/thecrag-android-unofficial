package org.thecragunofficial.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.thecragunofficial.android.data.remote.ApiFactory
import org.thecragunofficial.android.data.remote.InMemoryStore
import org.thecragunofficial.android.data.remote.NO_CACHE
import org.thecragunofficial.android.data.remote.PersistentCookieJar
import org.thecragunofficial.android.data.remote.TheCragApi
import java.io.IOException
import java.nio.file.Files

class CacheTest {
    private val node = """{"data":{"id":"11962579","name":"Arapiles","type":"area"}}"""

    /** Client with a disk cache, a stored session (no bootstrap), and every request redirected to [server]. */
    private fun client(server: MockWebServer): Pair<OkHttpClient, TheCragApi> {
        val jar = PersistentCookieJar(InMemoryStore())
        val thecrag = HttpUrl.Builder().scheme("https").host("www.thecrag.com").build()
        jar.saveFromResponse(thecrag, listOf(Cookie.parse(thecrag, "ApacheSessionID=abc; Path=/; Max-Age=86400")!!))
        val base = ApiFactory.okHttp(jar, Files.createTempDirectory("okcache").toFile(), debug = false)
        val rewriting = base.newBuilder().addInterceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }.build()
        return rewriting to ApiFactory.api(ApiFactory.retrofit(rewriting, server.url("/").toString()))
    }

    @Test
    fun apiResponsesAreCachedDespiteMissingHeaders() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(node).addHeader("Content-Type", "application/json"))
        server.start()
        val (client, api) = client(server)

        assertEquals("Arapiles", api.node(11962579L).data.name)
        assertEquals("Arapiles", api.node(11962579L).data.name)
        assertEquals("second call must be served from the cache", 1, server.requestCount)
        assertEquals(1, client.cache!!.hitCount())
        server.shutdown()
    }

    @Test
    fun fallsBackToCacheWhenNetworkIsDown() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(node).addHeader("Content-Type", "application/json"))
        server.start()
        val (_, api) = client(server)
        api.node(11962579L)
        server.shutdown()

        // A forced refresh must hit the network, fail, and then be answered from the cache.
        assertEquals("Arapiles", api.node(11962579L, cacheControl = NO_CACHE).data.name)

        // Nothing cached for this id: the network failure surfaces.
        try {
            api.node(1L)
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e !is retrofit2.HttpException)
        }
    }
}
