package org.thecragunofficial.android.domain

import org.thecragunofficial.android.data.remote.IMAGE_BASE

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

    fun imageUrl(w: Int = TOPO_IMAGE_W, h: Int = TOPO_IMAGE_H): String = topoImageUrl(hashId, w, h)
}

/** The size every topo image is requested at; the offline downloader pins the same URL. */
const val TOPO_IMAGE_W = 1200
const val TOPO_IMAGE_H = 1600

/** theCrag scales to fit [w]x[h] keeping aspect; without a size it serves the original. */
fun topoImageUrl(hashId: String, w: Int = TOPO_IMAGE_W, h: Int = TOPO_IMAGE_H): String =
    IMAGE_BASE + "${w}x$h/" + hashId.substring(0, 2) + "/" + hashId.substring(2, 4) + "/" + hashId
