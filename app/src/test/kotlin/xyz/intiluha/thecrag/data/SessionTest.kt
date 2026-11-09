package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.InMemoryStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import xyz.intiluha.thecrag.data.remote.RateLimitInterceptor
import xyz.intiluha.thecrag.data.remote.USER_AGENT

class SessionTest {
    private val permissionError =
        """{"error":"insufficient","errorLabel":"insufficient-api-permissions","method":"GET"}"""
    private val node = """{"data":{"id":"7546063","name":"World","type":"area"}}"""

    @Test
    fun bootstrapsSessionThenRetriesOnPermissionError() = runBlocking {
        val server = MockWebServer()
        // 1: proactive /home bootstrap (no cookie yet)  2: API call rejected  3: /home again  4: API ok
        server.enqueue(MockResponse().setBody("<html>").addHeader("Set-Cookie", "ApacheSessionID=abc; Path=/; Max-Age=86400"))
        server.enqueue(MockResponse().setResponseCode(400).setBody(permissionError))
        server.enqueue(MockResponse().setBody("<html>").addHeader("Set-Cookie", "ApacheSessionID=def; Path=/; Max-Age=86400"))
        server.enqueue(MockResponse().setBody(node).addHeader("Content-Type", "application/json"))
        server.start()

        val jar = PersistentCookieJar(InMemoryStore())
        val client = ApiFactory.okHttp(jar, null, debug = false)
        // Point the bootstrap at the mock server by rewriting the host of every request.
        val rewriting = client.newBuilder().addInterceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }.build()
        val api = ApiFactory.api(ApiFactory.retrofit(rewriting, server.url("/").toString()))

        val world = api.node(7546063L).data
        assertEquals("World", world.name)
        assertEquals(4, server.requestCount)

        val r1 = server.takeRequest(); assertEquals("/home", r1.path)
        val r2 = server.takeRequest()
        assertTrue(r2.path!!.startsWith("/api/node/id/7546063"))
        assertEquals(USER_AGENT, r2.getHeader("User-Agent"))
        assertTrue(r2.getHeader("Referer")!!.startsWith("https://www.thecrag.com/"))
        server.shutdown()
    }

    @Test
    fun loggedInSessionIsNotReplacedByAnAnonymousOne() = runBlocking {
        val server = MockWebServer()
        // The login cookie is there, the API still refuses: no /home fetch, the error comes back as is.
        server.enqueue(MockResponse().setResponseCode(400).setBody(permissionError))
        server.start()

        val jar = PersistentCookieJar(InMemoryStore())
        jar.saveFromResponse(okhttp3.HttpUrl.Builder().scheme("https").host("www.thecrag.com").build(), listOf(okhttp3.Cookie.parse(okhttp3.HttpUrl.Builder().scheme("https").host("www.thecrag.com").build(), "ApacheSessionID=login; Path=/; Max-Age=86400")!!))
        val client = ApiFactory.okHttp(jar, null, debug = false, loggedIn = { true })
        val rewriting = client.newBuilder().addInterceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }.build()
        val api = ApiFactory.api(ApiFactory.retrofit(rewriting, server.url("/").toString()))

        val failure = runCatching { api.node(7546063L) }.exceptionOrNull()
        assertTrue(failure is retrofit2.HttpException && failure.code() == 400)
        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().path!!.startsWith("/api/node/id/7546063"))
        server.shutdown()
    }

    @Test
    fun cookieJarPersistsAcrossInstances() {
        val store = InMemoryStore()
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("www.thecrag.com").build()
        val jar = PersistentCookieJar(store)
        jar.saveFromResponse(url, listOf(okhttp3.Cookie.parse(url, "ApacheSessionID=xyz; Path=/; Max-Age=3600")!!))
        assertTrue(jar.hasSession())
        val again = PersistentCookieJar(store)
        assertTrue(again.hasSession())
        assertEquals("xyz", again.loadForRequest(url).single { it.name == "ApacheSessionID" }.value)
    }

    @Test
    fun rateLimiterSpacesRequests() {
        var now = 0L
        val sleeps = ArrayList<Long>()
        val limiter = RateLimitInterceptor(burst = 2, intervalMs = 100, clock = { now }, sleep = { ms -> sleeps += ms; now += ms })
        val acquire = RateLimitInterceptor::class.java.getDeclaredMethod("acquire").apply { isAccessible = true }
        repeat(4) { acquire.invoke(limiter) }
        assertEquals(2, sleeps.size)
        assertTrue(sleeps.all { it in 1..100 })
    }
}
