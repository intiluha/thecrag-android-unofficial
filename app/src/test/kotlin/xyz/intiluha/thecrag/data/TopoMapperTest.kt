package xyz.intiluha.thecrag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.dto.DataObj
import xyz.intiluha.thecrag.data.remote.dto.NodeDto
import xyz.intiluha.thecrag.data.repo.TopoMapper
import xyz.intiluha.thecrag.domain.Topo
import xyz.intiluha.thecrag.domain.TopoPoint
import java.io.File

class TopoMapperTest {
    private fun sample(name: String) =
        listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name")).first { it.exists() }.readText()

    private fun topos(name: String): List<Topo> =
        ApiFactory.json.decodeFromString(DataObj.serializer(NodeDto.serializer()), sample(name)).data.topos.mapNotNull(TopoMapper::fromDto)

    @Test
    fun routeToposFromJson() {
        val topos = topos("route_12873151_show_topo.json")
        assertEquals(2, topos.size)
        val t = topos.first { it.id == 119694216L }
        // 898x823 original fitted into 550: the same base the website's data-width / data-view-scale gives.
        assertEquals(550f, t.baseWidth, 0.6f)
        assertEquals(504f, t.baseHeight, 0.6f)
        assertEquals(40, t.hashId.length)
        assertTrue(t.imageUrl().startsWith("https://image.thecrag.com/1200x/" + t.hashId.substring(0, 2) + "/"))
        assertEquals(9, t.routes.size)
        val r = t.routes.first { it.id == 12873151L }
        assertEquals("Have A Good Flight", r.name)
        assertEquals("5", r.label)
        assertEquals("25", r.grade)
        assertEquals(4, r.gradeBand)
        assertEquals(3, r.stars)
        assertEquals(7, r.points.size)
        assertEquals("label", r.points.first().type)
        assertEquals("lower", r.points.last().type)
        assertTrue(r.points.all { it.x in 0f..t.baseWidth && it.y in 0f..t.baseHeight })

        val portrait = topos.first { it.id == 877390101L }
        assertEquals(336f, portrait.baseWidth, 0.6f)
        assertEquals(550f, portrait.baseHeight, 0.6f)
    }

    @Test
    fun areaOutlinesFromJson() {
        val t = topos("area_2306004858_show_topo.json").single()
        // 7000x5250 original; the JSON width/height is the original here, not the base.
        assertEquals(550f, t.baseWidth, 0.6f)
        assertEquals(412.5f, t.baseHeight, 0.6f)
        assertTrue(t.routes.isEmpty())
        assertEquals(listOf("West", "East"), t.areas.map { it.label })
        val west = t.areas.first()
        assertNotNull(west.labelAt)
        assertEquals(346.49f, west.labelAt!!.x, 0.01f)
        assertEquals(4, west.polygon.size)
        assertTrue(west.polygon.all { it.x in 0f..t.baseWidth && it.y in 0f..t.baseHeight })
    }

    @Test
    fun parsesPointTypes() {
        val pts = TopoMapper.parsePoints("46 268,45 224,79 193 lower, 10.5 11.25 bolt hidden")
        assertEquals(4, pts.size)
        assertEquals(TopoPoint(46f, 268f, null), pts[0])
        assertEquals("lower", pts[2].type)
        assertEquals(10.5f, pts[3].x, 0f)
        assertEquals("bolt", pts[3].type)
    }
}
