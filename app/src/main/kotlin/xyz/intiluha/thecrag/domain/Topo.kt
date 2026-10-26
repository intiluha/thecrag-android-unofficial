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
    /** Degrees (0, 90, 180, 270) the image server turns the stored photo by before it matches the overlay. */
    val rotate: Int = 0,
) {
    val aspect: Float get() = if (baseHeight > 0f) baseWidth / baseHeight else 1f

    fun imageUrl(w: Int = TOPO_IMAGE_W): String = topoImageUrl(hashId, rotate, w)
}

/** The width every topo image is requested at; the offline downloader pins the same URL. */
const val TOPO_IMAGE_W = 1200

/**
 * `{w}x/` scales to the width keeping the aspect. Giving both sides (`{w}x{h}/`)
 * makes the server crop to fill that box: a 16:9 photo asked for as 1200x1600 came
 * back as its middle 42 percent (checked 2026-10-25). Without a size it serves the original.
 *
 * Some photos are stored sideways and the topo carries `rotate`; the website asks for
 * `filters:rotate(N)` (Thumbor syntax), which the server applies *after* resizing the
 * stored photo. For a quarter turn the stored width becomes the height, so the size is
 * requested as `x{w}/` to end up [w] wide (checked 2026-10-26 on Vedi / The Fortress).
 */
fun topoImageUrl(hashId: String, rotate: Int = 0, w: Int = TOPO_IMAGE_W): String {
    val quarterTurn = rotate % 180 != 0
    val size = if (quarterTurn) "x${w}/" else "${w}x/"
    val filter = if (rotate != 0) "filters:rotate($rotate)/" else ""
    return IMAGE_BASE + size + filter + hashId.substring(0, 2) + "/" + hashId.substring(2, 4) + "/" + hashId
}
