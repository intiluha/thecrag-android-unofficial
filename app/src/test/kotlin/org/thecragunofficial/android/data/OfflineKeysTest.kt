package org.thecragunofficial.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.thecragunofficial.android.data.offline.OfflineKeys
import org.thecragunofficial.android.data.remote.ApiFactory
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.data.repo.TopoRepository
import org.thecragunofficial.android.domain.Topo

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
        assertEquals("https://image.thecrag.com/1200x1600/8e/ab/8eab551bbf6e736c31c985beddf724f41886fff2", topo.imageUrl())
    }
}
