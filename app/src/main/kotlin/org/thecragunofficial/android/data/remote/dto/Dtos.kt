package org.thecragunofficial.android.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DataObj<T>(val data: T)

@Serializable
data class DataList<T>(val data: List<T> = emptyList())

@Serializable
data class ApiError(val error: String = "", val errorLabel: String = "")

/** One DTO for both areas and routes; `type` is "area" or "route". */
@Serializable
data class NodeDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val subType: String? = null,
    val urlStub: String? = null,
    val urlAncestorStub: String? = null,
    @Serializable(with = LenientLongOrNull::class) val parentID: Long? = null,
    val depth: Int? = null,
    val childIDs: List<@Serializable(with = LenientLong::class) Long> = emptyList(),
    @Serializable(with = LenientIntOrNull::class) val subAreaCount: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val numberRoutes: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val numberTopos: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val numberPhotos: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val ascentCount: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val hasTopo: Int? = null,
    val averageHeight: JsonElement? = null,
    val height: JsonElement? = null,
    val unique: String? = null,
    // route-only
    val grade: String? = null,
    val gradeInContext: String? = null,
    val context: String? = null,
    @Serializable(with = LenientIntOrNull::class) val gradeBand: Int? = null,
    val style: String? = null,
    val styleStub: String? = null,
    @Serializable(with = LenientIntOrNull::class) val stars: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val bolts: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val pitches: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val popularity: Int? = null,
    @Serializable(with = LenientStringOrNull::class) val siblingLabel: String? = null,
    // show=…
    val ancestors: List<NodeDto> = emptyList(),
    val beta: List<BetaDto> = emptyList(),
    val history: List<HistoryDto> = emptyList(),
    val geometry: GeometryDto? = null,
    val topos: List<TopoDto> = emptyList(),
)

@Serializable
data class BetaDto(
    val name: String = "",
    val markdown: String? = null,
    val markupHTML: String? = null,
    val inheritedFrom: InheritedFromDto? = null,
    @Serializable(with = LenientStringOrNull::class) val copyrightID: String? = null,
    @Serializable(with = LenientStringOrNull::class) val publisherID: String? = null,
    val attribute: BetaAttributeDto? = null,
)

@Serializable
data class InheritedFromDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    val urlStub: String? = null,
)

@Serializable
data class BetaAttributeDto(val publication: PublicationDto? = null, val url: String? = null)

@Serializable
data class PublicationDto(@Serializable(with = LenientLong::class) val id: Long = 0, val name: String = "")

@Serializable
data class HistoryDto(
    val type: String = "",
    val climbers: String? = null,
    val date: String? = null,
)

@Serializable
data class GeometryDto(
    val center: List<Double>? = null,          // [lon, lat]
    val lat: Double? = null,
    val long: Double? = null,
    val bbox: List<Double>? = null,            // [minLon, minLat, maxLon, maxLat]
    val boundary: List<List<List<Double>>>? = null,
    val altitude: AltitudeDto? = null,
)

@Serializable
data class AltitudeDto(val avg: Double? = null, val min: Double? = null, val max: Double? = null, val unit: String? = null)

@Serializable
data class TopoDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    val hashID: String = "",
    val name: String? = null,
    @Serializable(with = LenientLongOrNull::class) val node: Long? = null,
    @Serializable(with = LenientIntOrNull::class) val width: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val height: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val origWidth: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val origHeight: Int? = null,
    val objects: List<TopoObjectDto> = emptyList(),
)

@Serializable
data class TopoObjectDto(
    @Serializable(with = LenientLong::class) val objectID: Long = 0,
    val objectType: String = "",
    val shape: String? = null,
    val name: String = "",
    val grade: String? = null,
    @Serializable(with = LenientIntOrNull::class) val gradeBand: Int? = null,
    @Serializable(with = LenientIntOrNull::class) val stars: Int? = null,
    val style: String? = null,
    @Serializable(with = LenientStringOrNull::class) val siblingLabel: String? = null,
)

@Serializable
data class LookupDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    val name: String = "",
    val qualifier: String? = null,
    val type: String = "",
)
