package org.thecragunofficial.android.data

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thecragunofficial.android.data.html.TopoHtmlParser
import org.thecragunofficial.android.data.remote.ApiFactory
import org.thecragunofficial.android.domain.TopoPoint
import java.io.File

class TopoHtmlParserTest {
    private val parser = TopoHtmlParser(ApiFactory.json)
    private fun sample(name: String) =
        listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name")).first { it.exists() }.readText()

    @Test
    fun parsesToposFromRoutePage() {
        val topos = parser.parse(sample("route_12873151_page_topos.html"))
        assertEquals(2, topos.size)
        val t = topos.first { it.id == 119694216L }
        // data-width 349 / data-view-scale 0.6345 == JSON topo width 550
        assertEquals(550f, t.baseWidth, 0.5f)
        assertEquals(504f, t.baseHeight, 1f)
        assertEquals("928abc94d9e930b0d5fb58e0af620c1cd4924f6c", t.hashId)
        assertTrue(t.imageUrl().startsWith("https://image.thecrag.com/1200x1600/92/8a/928abc94"))
        assertEquals(9, t.routes.size)
        val r = t.routes.first { it.id == 12873151L }
        assertEquals("Have A Good Flight", r.name)
        assertEquals("25", r.grade)
        assertEquals(4, r.gradeBand)
        assertEquals(3, r.stars)
        assertTrue(r.points.size >= 3)
        assertTrue(r.points.any { it.type == "label" })
        assertTrue(r.points.all { it.x in 0f..t.baseWidth && it.y in 0f..t.baseHeight })
    }

    @Test
    fun parsesPointTypes() {
        val pts = TopoHtmlParser.parsePoints("46 268,45 224,79 193 lower, 10.5 11.25 bolt hidden")
        assertEquals(4, pts.size)
        assertEquals(TopoPoint(46f, 268f, null), pts[0])
        assertEquals("lower", pts[2].type)
        assertEquals(10.5f, pts[3].x, 0f)
        assertEquals("bolt", pts[3].type)
    }

    @Test
    fun parsesAreaShapes() {
        val html = """<div class="phototopo" data-tid="1" data-width="100" data-height="50" data-view-scale="0.5"
            data-topodata='[{"id":"11962651","type":"area","num":"5","name":"Agamemnon Area",
            "points":"327.5 327 ctvnd Agamemnon%0AArea,278 287,327.5 287,377 287,377 70,278 70"}]'>
            <div class="canvas"><img src="https://image.thecrag.com/240x320/2e/47/2e471fcf7e03a141d7b7b7a42d7fcc67ceb0dd7f"></div></div>"""
        val t = parser.parse(html).single()
        assertEquals(200f, t.baseWidth, 0f)
        val a = t.areas.single()
        assertEquals(11962651L, a.id)
        assertEquals("Agamemnon\nArea", a.label)
        assertNotNull(a.labelAt)
        assertEquals(327.5f, a.labelAt!!.x, 0f)
        assertEquals(5, a.polygon.size)
    }

    @Test
    fun gradeBandFromCssClass() {
        assertEquals(4, TopoHtmlParser.gradeBand("gb4"))
        assertEquals(0, TopoHtmlParser.gradeBand(null))
        assertEquals(0, TopoHtmlParser.gradeBand("other"))
    }
}
