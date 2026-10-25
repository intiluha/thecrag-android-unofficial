package org.thecragunofficial.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thecragunofficial.android.data.offline.FavouriteDownloader
import org.thecragunofficial.android.data.offline.OfflineInterceptor
import org.thecragunofficial.android.data.offline.OfflineKeys
import org.thecragunofficial.android.data.offline.OfflineStore
import org.thecragunofficial.android.data.offline.Favourite
import org.thecragunofficial.android.data.remote.ApiFactory
import org.thecragunofficial.android.data.remote.InMemoryStore
import org.thecragunofficial.android.data.remote.NO_CACHE
import org.thecragunofficial.android.data.remote.PersistentCookieJar
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.data.repo.TopoRepository
import java.nio.file.Files

/**
 * A crag (id 100) with one direct route (1), one sector (200) with routes 2 and 3,
 * a topo on the crag and one on route 2. After the download the browse
 * repositories must answer with the server gone.
 */
class FavouriteDownloaderTest {
    private val h1 = "1111111111111111111111111111111111111111"
    private val h2 = "2222222222222222222222222222222222222222"

    private fun topo(id: Int, hash: String) = """{"id":$id,"hashID":"$hash","origWidth":800,"origHeight":600,"objects":[]}"""
    private fun area(id: Long, name: String, subType: String, subAreas: Int, routes: Int, topos: String = "") =
        """{"id":"$id","name":"$name","type":"area","subType":"$subType","subAreaCount":$subAreas,"numberRoutes":$routes,"numberTopos":1,"topos":[$topos]}"""
    private fun route(id: Long, topos: String = "") =
        """{"id":$id,"name":"R$id","type":"route","grade":"6a","gradeBand":3,"topos":[$topos]}"""

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val json = MockResponse().addHeader("Content-Type", "application/json")
            return when (request.path) {
                "/api/node/ids?id=100&show=ancestors,info,location,topo" -> json.setBody("""{"data":[${area(100, "Crag", "Crag", 1, 3, topo(1, h1))}]}""")
                "/api/node/id/100/children/area" -> json.setBody("""{"data":[${area(200, "Sector", "Sector", 0, 2)}]}""")
                "/api/node/id/100/children/route" -> json.setBody("""{"data":[${route(1)}]}""")
                "/api/node/ids?id=200&show=ancestors,info,location,topo" -> json.setBody("""{"data":[${area(200, "Sector", "Sector", 0, 2)}]}""")
                "/api/node/id/200/children/area" -> json.setBody("""{"data":[]}""")
                "/api/node/id/200/children/route" -> json.setBody("""{"data":[${route(2)},${route(3)}]}""")
                "/api/node/ids?id=1,2,3&show=ancestors,info,topo" -> json.setBody("""{"data":[${route(1)},${route(2, topo(2, h2))},${route(3)}]}""")
                "/api/node/id/100?show=ancestors&show=info&show=location" -> json.setBody("""{"data":${area(100, "Crag renamed", "Crag", 1, 3)}}""")
                "/1200x1600/11/11/$h1" -> MockResponse().addHeader("Content-Type", "image/jpeg").setBody("JPEG1")
                "/1200x1600/22/22/$h2" -> MockResponse().addHeader("Content-Type", "image/jpeg").setBody("JPEG2")
                else -> MockResponse().setResponseCode(404).setBody("no route for ${request.path}")
            }
        }
    }

    private fun client(server: MockWebServer, store: OfflineStore): OkHttpClient {
        val jar = PersistentCookieJar(InMemoryStore())
        val thecrag = HttpUrl.Builder().scheme("https").host("www.thecrag.com").build()
        jar.saveFromResponse(thecrag, listOf(Cookie.parse(thecrag, "ApacheSessionID=abc; Path=/; Max-Age=86400")!!))
        val base = ApiFactory.okHttp(jar, null, debug = false, offline = store)
        // Appended after the offline interceptor, so pinned keys are matched on the real thecrag URLs.
        return base.newBuilder().addInterceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }.build()
    }

    @Test
    fun downloadsSubtreeThenServesItWithoutNetwork() = runBlocking {
        val server = MockWebServer().apply { dispatcher = this@FavouriteDownloaderTest.dispatcher; start() }
        val store = OfflineStore(Files.createTempDirectory("offline").toFile(), ApiFactory.json)
        store.upsert(Favourite(100, "Crag", "Crag", 3, 1, addedAt = 1))
        val http = client(server, store)
        val downloader = FavouriteDownloader(http, store, ApiFactory.json)

        val progress = ArrayList<Pair<Int, Int>>()
        val result = downloader.download(100, refresh = false) { progress += it.done to it.total }
        assertEquals(3, result.routeCount)
        assertEquals(2, result.topoCount)
        assertEquals("1 root batch, 2 crag listings, 1 sector batch, 2 sector listings, 1 route batch, 2 images", 9, server.requestCount)
        assertEquals(9, progress.last().first)
        assertEquals(progress.last().first, progress.last().second)
        assertTrue(result.bytes > 0)

        // An explicit refresh of a pinned page goes to the network and updates the pinned copy.
        val api = ApiFactory.api(ApiFactory.retrofit(http))
        assertEquals("Crag renamed", api.node(100L, NodeRepository.AREA_SHOW, cacheControl = NO_CACHE).data.name)
        assertEquals(10, server.requestCount)
        assertTrue(store.read(OfflineKeys.area(100))!!.decodeToString().contains("Crag renamed"))

        server.shutdown()

        val nodes = NodeRepository(api)
        val crag = nodes.area(100)
        assertEquals("Crag renamed", crag.name)
        assertEquals(listOf("Sector"), crag.subAreas.map { it.name })
        assertEquals(listOf("R1"), crag.routes.map { it.name })
        assertEquals(listOf("R2", "R3"), nodes.area(200).routes.map { it.name })
        assertEquals("R2", nodes.route(2).name)
        val topos = TopoRepository(api)
        assertEquals(1, topos.toposFor(2).size)
        assertEquals(h2, topos.toposFor(2).single().hashId)
        assertEquals(0, topos.toposFor(3).size)
        http.newCall(Request.Builder().url(OfflineKeys.image(h2)).build()).execute().use {
            assertEquals("store", it.header(OfflineInterceptor.HEADER))
            assertEquals("JPEG2", it.body!!.string())
        }

        // A second run without refresh needs nothing from the (dead) network.
        val again = downloader.download(100, refresh = false) { }
        assertEquals(3, again.routeCount)
        assertEquals(2, again.topoCount)
    }
}
