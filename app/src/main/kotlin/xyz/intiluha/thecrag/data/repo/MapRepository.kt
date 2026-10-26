package xyz.intiluha.thecrag.data.repo

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import xyz.intiluha.thecrag.data.remote.TheCragApi
import xyz.intiluha.thecrag.data.remote.dto.MapNodeDto
import xyz.intiluha.thecrag.domain.LatLon
import xyz.intiluha.thecrag.domain.MapMarker
import kotlin.math.max
import kotlin.math.pow

/**
 * Mirrors the website map (maps-1.1.62.js): the server returns the area tree
 * intersecting the viewport; `v = loadFov / 2^zoom` tells it how small an area
 * must be before its children are omitted; on the client, areas narrower than
 * `labelFov / 2^zoom` degrees are drawn as markers, wider ones are descended.
 */
class MapRepository(private val api: TheCragApi, private val json: Json) {
    data class Viewport(val west: Double, val south: Double, val east: Double, val north: Double, val zoom: Double)

    suspend fun markers(vp: Viewport): List<MapMarker> {
        val decimals = ((vp.zoom + 1) / 3).toInt().coerceIn(0, 6)
        val s = listOf(vp.west, vp.south, vp.east, vp.north).joinToString(",") { "%.${decimals}f".format(java.util.Locale.ROOT, it) }
        val v = LOAD_FOV / 2.0.pow(vp.zoom)
        val tree = api.mapBbox(s = s, v = "%.6f".format(java.util.Locale.ROOT, v)).data.visible
        val labelFov = LABEL_FOV / 2.0.pow(vp.zoom)
        val out = ArrayList<MapMarker>()
        tree.forEach { collect(it, labelFov, vp, out) }
        return out
    }

    private fun collect(n: MapNodeDto, labelFov: Double, vp: Viewport, out: MutableList<MapMarker>) {
        val bbox = n.bbox
        val fov = if (bbox != null && bbox.size == 4) max(bbox[2] - bbox[0], bbox[3] - bbox[1]) else 0.0
        if (bbox != null && bbox.size == 4 &&
            (bbox[2] < vp.west || bbox[0] > vp.east || bbox[3] < vp.south || bbox[1] > vp.north)
        ) return
        if (n.children.isNotEmpty() && fov > labelFov) {
            n.children.forEach { collect(it, labelFov, vp, out) }
            return
        }
        val c = n.center ?: return
        if (c.size < 2) return
        out += MapMarker(
            id = n.id,
            name = n.name,
            center = LatLon(c[1], c[0]),
            numberRoutes = n.numberRoutes ?: 0,
            fov = fov,
            polygon = parsePolygon(n.geometry),
        )
    }

    fun parsePolygon(geometry: String?): List<LatLon> {
        if (geometry.isNullOrBlank()) return emptyList()
        val ring = runCatching { json.decodeFromString(ListSerializer(ListSerializer(Double.serializer())), geometry) }.getOrNull()
            ?: return emptyList()
        return ring.filter { it.size >= 2 }.map { LatLon(it[1], it[0]) }
    }

    companion object {
        const val LOAD_FOV = 100.0
        const val LABEL_FOV = 200.0
    }
}
