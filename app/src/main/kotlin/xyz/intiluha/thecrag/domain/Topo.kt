package xyz.intiluha.thecrag.domain

import xyz.intiluha.thecrag.data.remote.IMAGE_BASE

/** A point in the topo's base coordinate space (0..baseWidth, 0..baseHeight). */
data class TopoPoint(val x: Float, val y: Float, val type: String? = null) {
    val hidden: Boolean get() = type == "hidden"
}

data class TopoRoute(
    val id: Long,
    val name: String,
    val label: String?,      // number on the topo, "?.5" when unnumbered
    val grade: String?,
    val gradeBand: Int,
    val stars: Int,
    val style: String?,
    val points: List<TopoPoint>,
)

data class TopoArea(
    val id: Long,
    val name: String,
    val label: String?,
    val labelAt: TopoPoint?,
    val polygon: List<TopoPoint>,
)

data class Topo(
    val id: Long,
    val hashId: String,
    val baseWidth: Float,
    val baseHeight: Float,
    val routes: List<TopoRoute>,
    val areas: List<TopoArea>,
) {
    val aspect: Float get() = if (baseHeight > 0f) baseWidth / baseHeight else 1f

    fun imageUrl(w: Int = TOPO_IMAGE_W): String = topoImageUrl(hashId, w)
}

/** The width every topo image is requested at; the offline downloader pins the same URL. */
const val TOPO_IMAGE_W = 1200

/**
 * `{w}x/` scales to the width keeping the aspect. Giving both sides (`{w}x{h}/`)
 * makes the server crop to fill that box: a 16:9 photo asked for as 1200x1600 came
 * back as its middle 42 percent (checked 2026-10-25). Without a size it serves the original.
 */
fun topoImageUrl(hashId: String, w: Int = TOPO_IMAGE_W): String =
    IMAGE_BASE + "${w}x/" + hashId.substring(0, 2) + "/" + hashId.substring(2, 4) + "/" + hashId
