package xyz.intiluha.thecrag.domain

data class MapMarker(
    val id: Long,
    val name: String,
    val center: LatLon,
    val numberRoutes: Int,
    /** Degrees: the larger side of the node's bounding box. */
    val fov: Double,
    val polygon: List<LatLon>,
)
