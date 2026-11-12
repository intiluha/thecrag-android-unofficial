package xyz.intiluha.thecrag.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.intiluha.thecrag.data.remote.CookieAuthInterceptor

class CookieAuthTest {
    @Test
    fun apiCallsCarryCookieAuthOnceAndPagesDoNot() {
        val server = MockWebServer()
        repeat(4) { server.enqueue(MockResponse().setBody("{}")) }
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(CookieAuthInterceptor()).build()
        fun get(path: String): String {
            client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
            return server.takeRequest().path!!
        }
        assertEquals("/api/node/id/7546063?show=ancestors&cookieAuth=1", get("/api/node/id/7546063?show=ancestors"))
        assertEquals("/api/node/id/7546063/children/area?cookieAuth=1", get("/api/node/id/7546063/children/area"))
        assertEquals("/api/account/id/me?cookieAuth=1", get("/api/account/id/me?cookieAuth=1"))
        assertEquals("/home", get("/home"))
        server.shutdown()
    }
}
