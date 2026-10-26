package xyz.intiluha.thecrag.data.repo

import xyz.intiluha.thecrag.data.remote.dto.TopoDto
import xyz.intiluha.thecrag.data.remote.dto.TopoObjectDto
import xyz.intiluha.thecrag.domain.Topo
import xyz.intiluha.thecrag.domain.TopoArea
import xyz.intiluha.thecrag.domain.TopoPoint
import xyz.intiluha.thecrag.domain.TopoRoute
import java.net.URLDecoder

/**
 * Topo overlays from the JSON API (`show=topo`). Route objects carry `points`
 * and area objects `shape`: the same strings the website embeds in its pages,
 * so no HTML page load is needed (page loads are throttled per IP; the API is not).
 *
 * Coordinates live in the topo's "max size" space: the original image fitted
 * into a [BASE_MAX] square. Observed on every topo checked: 898x823 -> 550x504,
 * 428x700 -> 336x550, 4624x3468 -> 550x412. The JSON `width`/`height` is
 * sometimes that size and sometimes the original, so it is not used. `origWidth`/
 * `origHeight` already describe the photo after the server-side `rotate`.
 */
object TopoMapper {
    const val BASE_MAX = 550f

    fun fromDto(dto: TopoDto): Topo? {
        if (dto.id == 0L || dto.hashID.length < 40) return null
        val ow = (dto.origWidth ?: dto.width)?.takeIf { it > 0 } ?: return null
        val oh = (dto.origHeight ?: dto.height)?.takeIf { it > 0 } ?: return null
        val scale = BASE_MAX / maxOf(ow, oh)
        val routes = dto.objects.filter { it.objectType.equals("Route", ignoreCase = true) }.map { it.toRoute() }
        val areas = dto.objects.filter { it.objectType.equals("Area", ignoreCase = true) }.mapNotNull { it.toArea() }
        val rotate = ((dto.rotate ?: 0) % 360 + 360) % 360
        return Topo(dto.id, dto.hashID, ow * scale, oh * scale, routes, areas, rotate)
    }

    /** `"46 268,45 224,79 193 lower"` -> points; an extra trailing token marks the type. */
    fun parsePoints(spec: String): List<TopoPoint> =
        spec.split(',').mapNotNull { token ->
            val parts = token.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@mapNotNull null
            val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
            TopoPoint(x, y, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        }

    private fun TopoObjectDto.toRoute() = TopoRoute(
        id = objectID,
        name = name,
        label = siblingLabel,
        grade = grade,
        gradeBand = gradeBand ?: 0,
        stars = stars ?: 0,
        style = style,
        points = parsePoints(points ?: ""),
    )

    /**
     * Area shape: first token `x y <opts> <url-encoded label>` (opts = halign, valign,
     * visibility, line, expand flags), remaining tokens are polygon vertices.
     */
    private fun TopoObjectDto.toArea(): TopoArea? {
        val tokens = (shape ?: points ?: return null).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val head = tokens.first().split(Regex("\\s+"))
        val labelAt = if (head.size >= 2) {
            val x = head[0].toFloatOrNull(); val y = head[1].toFloatOrNull()
            if (x != null && y != null) TopoPoint(x, y, head.getOrNull(2)) else null
        } else null
        val label = head.getOrNull(3)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        val polygon = parsePoints(tokens.drop(1).joinToString(","))
        return TopoArea(objectID, name, label ?: name, labelAt, polygon)
    }
}
