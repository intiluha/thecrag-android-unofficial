package org.thecragunofficial.android.domain

const val WORLD_ID = 7546063L

enum class NodeKind { AREA, ROUTE }

data class Crumb(val id: Long, val name: String)

data class Measure(val value: Double, val unit: String) {
    override fun toString(): String =
        if (value == value.toLong().toDouble()) "${value.toLong()}$unit" else "$value$unit"
}

data class BetaSection(
    val name: String,
    val text: String,
    val inheritedFromId: Long?,
    val publisher: String?,
)

data class LatLon(val lat: Double, val lon: Double)

data class AreaSummary(
    val id: Long,
    val name: String,
    val subType: String,
    val numberRoutes: Int,
    val subAreaCount: Int,
    val hasTopo: Boolean,
)

data class RouteSummary(
    val id: Long,
    val name: String,
    val label: String?,
    val grade: String?,
    val gradeBand: Int,
    val style: String?,
    val stars: Int,
    val height: Measure?,
    val bolts: Int?,
    val pitches: Int?,
    val ascentCount: Int,
)

data class AreaDetail(
    val id: Long,
    val name: String,
    val subType: String,
    val urlStub: String?,
    val ancestors: List<Crumb>,
    val numberRoutes: Int,
    val numberTopos: Int,
    val ascentCount: Int,
    val averageHeight: Measure?,
    val blurb: String?,
    val beta: List<BetaSection>,
    val location: LatLon?,
    val subAreas: List<AreaSummary>,
    val routes: List<RouteSummary>,
)

data class RouteDetail(
    val id: Long,
    val name: String,
    val urlAncestorStub: String?,
    val ancestors: List<Crumb>,
    val grade: String?,
    val gradeContext: String?,
    val gradeBand: Int,
    val style: String?,
    /** Lower-case gear style as the API names it (`sport`, `trad`, `boulder`); the default `climbedGearStyle` of a tick. */
    val styleStub: String?,
    val stars: Int,
    val height: Measure?,
    val bolts: Int?,
    val pitches: Int?,
    val ascentCount: Int,
    val popularity: Int?,
    val firstAscent: String?,
    val beta: List<BetaSection>,
) {
    /** Web page for this route, used for "open on theCrag". */
    val webUrl: String
        get() = "https://www.thecrag.com/en/climbing/${urlAncestorStub ?: "world"}/route/$id"
}

data class SearchHit(val id: Long, val name: String, val qualifier: String?, val kind: NodeKind)
