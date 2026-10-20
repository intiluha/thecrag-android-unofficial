package org.thecragunofficial.android.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /api/map/bbox/heirachy` (sic): a tree of areas intersecting the requested bbox. */
@Serializable
data class MapBboxDto(
    val visible: List<MapNodeDto> = emptyList(),
)

@Serializable
data class MapNodeDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    val name: String = "",
    val stub: String? = null,
    val center: List<Double>? = null,       // [lon, lat]
    val bbox: List<Double>? = null,         // [minLon, minLat, maxLon, maxLat]
    val geometry: String? = null,           // JSON string: [[lon,lat], ...] polygon ring
    @Serializable(with = LenientIntOrNull::class) val numberRoutes: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val hereCount: Int? = null,
    val children: List<MapNodeDto> = emptyList(),
)
