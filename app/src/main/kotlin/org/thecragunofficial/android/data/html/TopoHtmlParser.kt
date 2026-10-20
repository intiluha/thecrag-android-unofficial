package org.thecragunofficial.android.data.html

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.thecragunofficial.android.data.remote.dto.TopoDataEntryDto
import org.thecragunofficial.android.domain.Topo
import org.thecragunofficial.android.domain.TopoArea
import org.thecragunofficial.android.domain.TopoPoint
import org.thecragunofficial.android.domain.TopoRoute
import java.net.URLDecoder

/**
 * Extracts topos from a thecrag.com node page. Route lines exist only here:
 * `<div class="phototopo" data-tid data-width data-height data-view-scale data-topodata='[…]'>`
 * with the image under `.canvas img[src]`. The overlay coordinate space is
 * `data-width / data-view-scale` by `data-height / data-view-scale`, which equals the
 * `width`/`height` of the same topo in the JSON API (verified in docs/research/api.md).
 */
class TopoHtmlParser(private val json: Json) {
    private val listSerializer = ListSerializer(TopoDataEntryDto.serializer())

    fun parse(html: String, baseUri: String = "https://www.thecrag.com/"): List<Topo> {
        val doc = Jsoup.parse(html, baseUri)
        return doc.select("div.phototopo").mapNotNull { div ->
            val id = div.attr("data-tid").toLongOrNull() ?: return@mapNotNull null
            val width = div.attr("data-width").toFloatOrNull() ?: return@mapNotNull null
            val height = div.attr("data-height").toFloatOrNull() ?: return@mapNotNull null
            val scale = div.attr("data-view-scale").toFloatOrNull()?.takeIf { it > 0f } ?: 1f
            val src = div.selectFirst("img[src]")?.attr("src") ?: div.selectFirst("img[data-big]")?.attr("data-big")
            val hash = src?.let(::hashFromImageUrl) ?: return@mapNotNull null
            val entries = runCatching { json.decodeFromString(listSerializer, div.attr("data-topodata")) }.getOrElse { emptyList() }
            val routes = entries.filter { it.type == "route" }.map { it.toRoute() }
            val areas = entries.filter { it.type == "area" }.mapNotNull { it.toArea() }
            Topo(id, hash, width / scale, height / scale, routes, areas)
        }
    }

    companion object {
        private val HASH = Regex("([0-9a-f]{40})")

        fun hashFromImageUrl(url: String): String? = HASH.find(url)?.value

        /** `"46 268,45 224,79 193 lower"` -> points; an extra trailing token marks the type. */
        fun parsePoints(spec: String): List<TopoPoint> =
            spec.split(',').mapNotNull { token ->
                val parts = token.trim().split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
                val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
                TopoPoint(x, y, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }

        fun gradeBand(cssClass: String?): Int =
            cssClass?.let { Regex("gb(\\d)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

        private fun TopoDataEntryDto.toRoute() = TopoRoute(
            id = id,
            name = name,
            label = num,
            grade = grade,
            gradeBand = gradeBand(`class`),
            stars = stars?.count { it == '★' } ?: 0,
            style = style,
            points = parsePoints(points),
        )

        /**
         * Area shape: first token `x y <opts> <url-encoded label>` (opts = halign, valign,
         * visibility, line, expand flags), remaining tokens are polygon vertices.
         */
        private fun TopoDataEntryDto.toArea(): TopoArea? {
            val tokens = points.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            val head = tokens.first().split(Regex("\\s+"))
            val labelAt = if (head.size >= 2) {
                val x = head[0].toFloatOrNull(); val y = head[1].toFloatOrNull()
                if (x != null && y != null) TopoPoint(x, y, head.getOrNull(2)) else null
            } else null
            val label = head.getOrNull(3)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            val polygon = parsePoints(tokens.drop(1).joinToString(","))
            return TopoArea(id, name, label ?: name, labelAt, polygon)
        }
    }
}
