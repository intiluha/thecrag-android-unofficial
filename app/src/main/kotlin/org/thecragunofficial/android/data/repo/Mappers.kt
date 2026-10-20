package org.thecragunofficial.android.data.repo

import org.thecragunofficial.android.data.remote.dto.BetaDto
import org.thecragunofficial.android.data.remote.dto.NodeDto
import org.thecragunofficial.android.data.remote.dto.asMeasure
import org.thecragunofficial.android.domain.AreaDetail
import org.thecragunofficial.android.domain.AreaSummary
import org.thecragunofficial.android.domain.BetaSection
import org.thecragunofficial.android.domain.Crumb
import org.thecragunofficial.android.domain.LatLon
import org.thecragunofficial.android.domain.Measure
import org.thecragunofficial.android.domain.RouteDetail
import org.thecragunofficial.android.domain.RouteSummary

private fun NodeDto.crumbs(): List<Crumb> = ancestors.map { Crumb(it.id, it.name) }

private fun BetaDto.toSection(): BetaSection? {
    val text = markdown ?: markupHTML ?: return null
    return BetaSection(
        name = name,
        text = text,
        inheritedFromId = inheritedFrom?.id,
        publisher = attribute?.publication?.name ?: publisherID?.let { "publisher #$it" },
    )
}

fun NodeDto.toAreaSummary() = AreaSummary(
    id = id,
    name = name,
    subType = subType ?: "Area",
    numberRoutes = numberRoutes ?: 0,
    subAreaCount = subAreaCount ?: 0,
    hasTopo = (hasTopo ?: 0) != 0,
)

fun NodeDto.toRouteSummary() = RouteSummary(
    id = id,
    name = name,
    label = siblingLabel,
    grade = gradeInContext ?: grade,
    gradeBand = gradeBand ?: 0,
    style = style,
    stars = stars ?: 0,
    height = height.asMeasure()?.let { Measure(it.first, it.second) },
    bolts = bolts,
    pitches = pitches,
    ascentCount = ascentCount ?: 0,
)

fun NodeDto.toAreaDetail(subAreas: List<NodeDto>, routes: List<NodeDto>) = AreaDetail(
    id = id,
    name = name,
    subType = subType ?: "Area",
    urlStub = urlStub,
    ancestors = crumbs(),
    numberRoutes = numberRoutes ?: 0,
    numberTopos = numberTopos ?: 0,
    ascentCount = ascentCount ?: 0,
    averageHeight = averageHeight.asMeasure()?.let { Measure(it.first, it.second) },
    blurb = unique,
    beta = beta.mapNotNull { it.toSection() },
    location = geometry?.let { g ->
        val lat = g.lat ?: g.center?.getOrNull(1)
        val lon = g.long ?: g.center?.getOrNull(0)
        if (lat != null && lon != null) LatLon(lat, lon) else null
    },
    subAreas = subAreas.map { it.toAreaSummary() },
    routes = routes.map { it.toRouteSummary() },
)

fun NodeDto.toRouteDetail() = RouteDetail(
    id = id,
    name = name,
    urlAncestorStub = urlAncestorStub,
    ancestors = crumbs(),
    grade = gradeInContext ?: grade,
    gradeContext = context,
    gradeBand = gradeBand ?: 0,
    style = style,
    stars = stars ?: 0,
    height = height.asMeasure()?.let { Measure(it.first, it.second) },
    bolts = bolts,
    pitches = pitches,
    ascentCount = ascentCount ?: 0,
    popularity = popularity,
    firstAscent = history.firstOrNull { it.type.startsWith("First") }?.let { h ->
        listOfNotNull(h.climbers, h.date?.take(10)).joinToString(", ").ifBlank { null }
    },
    beta = beta.mapNotNull { it.toSection() },
)
