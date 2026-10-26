package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.intiluha.thecrag.data.offline.OfflineKeys
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.repo.NodeRepository
import xyz.intiluha.thecrag.data.repo.TopoRepository
import xyz.intiluha.thecrag.domain.Topo

/** The downloader pins responses under URLs it builds by hand; they must be the ones Retrofit sends. */
class OfflineKeysTest {
    @Test
    fun keysMatchRetrofitUrls() = runBlocking {
        val seen = ArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            seen += url.toString()
            val body = if (url.encodedPath.contains("/children/")) """{"data":[]}""" else """{"data":{"id":5,"name":"x","type":"area"}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = ApiFactory.api(ApiFactory.retrofit(client))

        api.node(5L, NodeRepository.AREA_SHOW)
        api.node(5L, NodeRepository.ROUTE_SHOW)
        api.node(5L, TopoRepository.TOPO_SHOW)
        api.childAreas(5L)
        api.childRoutes(5L)

        assertEquals(
            listOf(OfflineKeys.area(5), OfflineKeys.route(5), OfflineKeys.topos(5), OfflineKeys.childAreas(5), OfflineKeys.childRoutes(5)),
            seen,
        )
    }

    @Test
    fun imageKeyMatchesTheTopoModel() {
        val topo = Topo(1, "8eab551bbf6e736c31c985beddf724f41886fff2", 550f, 400f, emptyList(), emptyList())
        assertEquals(topo.imageUrl(), OfflineKeys.image(topo.hashId))
        assertEquals("https://image.thecrag.com/1200x/8e/ab/8eab551bbf6e736c31c985beddf724f41886fff2", topo.imageUrl())
    }

    @Test
    fun rotatedImageKeyMatchesTheTopoModel() {
        // Stored sideways: the server resizes first and turns second, so the width is asked for as the height.
        val topo = Topo(1, "c9fb7a30fe8afa8dc1ed2542c814b161c6c9e9c4", 412f, 550f, emptyList(), emptyList(), rotate = 270)
        assertEquals(topo.imageUrl(), OfflineKeys.image(topo.hashId, 270))
        assertEquals("https://image.thecrag.com/x1200/filters:rotate(270)/c9/fb/c9fb7a30fe8afa8dc1ed2542c814b161c6c9e9c4", topo.imageUrl())
        assertEquals("https://image.thecrag.com/1200x/filters:rotate(180)/c9/fb/c9fb7a30fe8afa8dc1ed2542c814b161c6c9e9c4", topo.copy(rotate = 180).imageUrl())
    }
}
