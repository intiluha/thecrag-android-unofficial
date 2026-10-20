package org.thecragunofficial.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thecragunofficial.android.data.remote.ApiFactory
import org.thecragunofficial.android.data.remote.TheCragApi
import org.thecragunofficial.android.data.remote.dto.DataList
import org.thecragunofficial.android.data.remote.dto.DataObj
import org.thecragunofficial.android.data.remote.dto.LookupDto
import org.thecragunofficial.android.data.remote.dto.MapBboxDto
import org.thecragunofficial.android.data.remote.dto.MapNodeDto
import org.thecragunofficial.android.data.remote.dto.NodeDto
import org.thecragunofficial.android.data.repo.MapRepository
import retrofit2.Response
import java.io.File

class MapRepositoryTest {
    private fun sample(name: String) =
        listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name")).first { it.exists() }.readText()

    private class FakeApi(val tree: MapBboxDto, val seen: MutableList<Pair<String, String>> = ArrayList()) : TheCragApi {
        override suspend fun node(id: Long, show: List<String>, markupType: String?): DataObj<NodeDto> = throw UnsupportedOperationException()
        override suspend fun childAreas(id: Long): DataList<NodeDto> = throw UnsupportedOperationException()
        override suspend fun childRoutes(id: Long): DataList<NodeDto> = throw UnsupportedOperationException()
        override suspend fun lookup(search: String, ancestor: Long, pageSize: Int): DataList<LookupDto> = throw UnsupportedOperationException()
        override suspend fun mapBbox(s: String, v: String, f: String): DataObj<MapBboxDto> { seen += s to v; return DataObj(tree) }
        override suspend fun page(kind: String, id: Long): ResponseBody = throw UnsupportedOperationException()
        override suspend fun home(): Response<okhttp3.ResponseBody> = throw UnsupportedOperationException()
    }

    private val yosemite = MapNodeDto(id = 2, name = "Yosemite", center = listOf(-119.6, 37.7), bbox = listOf(-119.9, 37.5, -119.3, 37.9), numberRoutes = 3000, geometry = "[[-119.9,37.5],[-119.3,37.5],[-119.3,37.9]]")
    private val northAmerica = MapNodeDto(id = 1, name = "North America", center = listOf(-100.0, 45.0), bbox = listOf(-170.0, 10.0, -50.0, 80.0), numberRoutes = 200000, children = listOf(yosemite))
    private val world = MapBboxDto(visible = listOf(MapNodeDto(id = 0, name = "World", center = listOf(0.0, 0.0), bbox = listOf(-180.0, -90.0, 180.0, 90.0), children = listOf(northAmerica))))

    @Test
    fun sampleResponseParses() {
        val dto = ApiFactory.json.decodeFromString(DataObj.serializer(MapBboxDto.serializer()), sample("map_bbox_heirachy.json"))
        val root = dto.data.visible.single()
        assertEquals(7546063L, root.id)
        assertTrue(root.children.isNotEmpty())
        val leaf = generateSequence(root) { it.children.firstOrNull() }.last()
        assertTrue(leaf.geometry != null && leaf.center != null)
        val repo = MapRepository(FakeApi(dto.data), ApiFactory.json)
        assertTrue(repo.parsePolygon(leaf.geometry).size > 3)
    }

    @Test
    fun zoomedOutShowsContinentZoomedInShowsCrag() = runBlocking {
        val api = FakeApi(world)
        val repo = MapRepository(api, ApiFactory.json)
        val far = repo.markers(MapRepository.Viewport(-180.0, -85.0, 180.0, 85.0, zoom = 0.0))
        assertEquals(listOf("North America"), far.map { it.name })
        val near = repo.markers(MapRepository.Viewport(-121.0, 36.0, -118.0, 39.0, zoom = 9.0))
        assertEquals(listOf("Yosemite"), near.map { it.name })
        assertEquals(3, near.single().polygon.size)
        assertEquals(37.7, near.single().center.lat, 1e-9)
        // request formatting: bbox precision grows with zoom, v = 100 / 2^zoom
        assertEquals("-180,-85,180,85" to "100.000000", api.seen[0])
        assertEquals("-121.000,36.000,-118.000,39.000" to "0.195313", api.seen[1])
    }

    @Test
    fun nodesOutsideViewportAreSkipped() = runBlocking {
        val repo = MapRepository(FakeApi(world), ApiFactory.json)
        val europe = repo.markers(MapRepository.Viewport(0.0, 40.0, 20.0, 55.0, zoom = 5.0))
        assertTrue(europe.isEmpty())
    }
}
