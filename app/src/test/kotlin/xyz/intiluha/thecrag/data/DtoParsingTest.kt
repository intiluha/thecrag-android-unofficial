package xyz.intiluha.thecrag.data

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.dto.DataList
import xyz.intiluha.thecrag.data.remote.dto.LenientLong
import xyz.intiluha.thecrag.data.remote.dto.NodeDto
import xyz.intiluha.thecrag.data.repo.toAreaDetail
import xyz.intiluha.thecrag.data.repo.toRouteDetail
import java.io.File

/** Parses the real responses captured in docs/api-samples (the `data` member of each). */
class DtoParsingTest {
    private val json = ApiFactory.json
    private fun sample(name: String): String {
        val candidates = listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name"))
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun areaWithAncestorsParses() {
        val n = json.decodeFromString(NodeDto.serializer(), sample("area_11740915_show_ancestors.json"))
        assertEquals(11740915L, n.id)
        assertEquals("Arapiles", n.name)
        assertEquals("area", n.type)
        assertEquals(3, n.ancestors.size)
        assertEquals("World", n.ancestors.first().name)
        assertTrue(n.childIDs.isNotEmpty())
    }

    @Test
    fun mixedStringAndNumberIdsParse() {
        val ids = json.decodeFromString(ListSerializer(LenientLong), """[11764447, "9091907751", 1.0]""")
        assertEquals(listOf(11764447L, 9091907751L, 1L), ids)
    }

    @Test
    fun routeWithEverythingMapsToDetail() {
        val n = json.decodeFromString(NodeDto.serializer(), sample("route_12873151_show_all.json"))
        assertEquals(12873151L, n.id)
        val r = n.toRouteDetail()
        assertEquals("Have A Good Flight", r.name)
        assertEquals("25", r.grade)
        assertEquals("AU", r.gradeContext)
        assertEquals(3, r.stars)
        assertEquals("Sport", r.style)
        assertEquals(18.0, r.height!!.value, 0.0)
        assertEquals("m", r.height!!.unit)
        assertEquals(5, r.bolts)
        assertNotNull(r.firstAscent)
        assertTrue(r.firstAscent!!.contains("Chris Shepherd"))
        assertTrue(r.beta.any { it.name == "Description" && it.publisher != null })
        assertTrue(r.beta.any { it.name == "Access Issues" && it.inheritedFromId == 11740915L })
        assertEquals("https://www.thecrag.com/en/climbing/australia/arapiles/atridae/route/12873151", r.webUrl)
    }

    @Test
    fun topoObjectsParse() {
        val n = json.decodeFromString(NodeDto.serializer(), sample("area_11962579_show_topo.json"))
        val t = n.topos.single()
        assertEquals("2e471fcf7e03a141d7b7b7a42d7fcc67ceb0dd7f", t.hashID)
        assertEquals(413, t.width)
        assertEquals(550, t.height)
        assertEquals("Area", t.objects.first().objectType)
        assertNotNull(t.objects.first().shape)
    }

    @Test
    fun locationGeometryParses() {
        val n = json.decodeFromString(NodeDto.serializer(), sample("area_11962579_show_location_geometry.json"))
        val g = n.geometry!!
        assertEquals(-36.762368, g.lat!!, 1e-9)
        assertEquals(141.845805, g.long!!, 1e-9)
        assertEquals(4, g.bbox!!.size)
        assertTrue(g.boundary!!.first().size > 3)
    }

    @Test
    fun childrenListsMapToSummaries() {
        val areas = json.decodeFromString(DataList.serializer(NodeDto.serializer()), sample("area_11764567_children_area.json")).data
        val routes = json.decodeFromString(DataList.serializer(NodeDto.serializer()), sample("area_11962579_children_route.json")).data
        val crag = json.decodeFromString(NodeDto.serializer(), sample("area_11740915_show_ancestors.json"))
        val detail = crag.toAreaDetail(areas, routes)
        assertEquals(2, detail.subAreas.size)
        assertEquals(2, detail.routes.size)
        assertNull(detail.location)
        assertTrue(detail.routes.all { it.grade != null })
    }
}
