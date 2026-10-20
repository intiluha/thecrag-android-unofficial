package org.thecragunofficial.android.ui.topo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.thecragunofficial.android.domain.Topo
import org.thecragunofficial.android.domain.TopoPoint
import org.thecragunofficial.android.domain.TopoRoute
import org.thecragunofficial.android.ui.theme.gradeBandColor
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A topo image with its route lines and area outlines drawn on top, in the topo's
 * base coordinate space scaled to the displayed width. Inline it is static with tap
 * handling; the expand button opens a zoomable full-screen copy.
 */
@Composable
fun TopoView(
    topo: Topo,
    selectedRouteId: Long?,
    onRouteTap: (Long) -> Unit,
    onAreaTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fullscreen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().aspectRatio(topo.aspect).clipToBounds()) {
        TopoCanvas(topo, selectedRouteId, onRouteTap, onAreaTap, Modifier.fillMaxSize())
        IconButton(onClick = { fullscreen = true }, Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Fullscreen, "Expand", tint = Color.White)
        }
    }
    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ZoomableTopo(topo, selectedRouteId, onRouteTap, onAreaTap)
                IconButton(onClick = { fullscreen = false }, Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableTopo(topo: Topo, selectedRouteId: Long?, onRouteTap: (Long) -> Unit, onAreaTap: (Long) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 8f)
                // Keep the point under the fingers fixed while zooming.
                offset = (offset - centroid) * (newScale / scale) + centroid + pan
                scale = newScale
                if (scale == 1f) offset = Offset.Zero
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(topo.aspect).graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            TopoCanvas(topo, selectedRouteId, onRouteTap, onAreaTap, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TopoCanvas(
    topo: Topo,
    selectedRouteId: Long?,
    onRouteTap: (Long) -> Unit,
    onAreaTap: (Long) -> Unit,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        AsyncImage(
            model = topo.imageUrl(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier.fillMaxSize().pointerInput(topo, selectedRouteId) {
                detectTapGestures { pos ->
                    val s = size.width / topo.baseWidth
                    val base = Offset(pos.x / s, pos.y / s)
                    val threshold = 18.dp.toPx() / s
                    val route = topo.routes.minByOrNull { distanceToPolyline(base, it.points) }
                    if (route != null && distanceToPolyline(base, route.points) <= threshold) {
                        onRouteTap(route.id)
                    } else {
                        topo.areas.firstOrNull { it.polygon.size >= 3 && pointInPolygon(base, it.polygon) }?.let { onAreaTap(it.id) }
                    }
                }
            },
        ) {
            val s = size.width / topo.baseWidth
            drawAreas(topo, s, measurer)
            val ordered = topo.routes.sortedBy { if (it.id == selectedRouteId) 1 else 0 }
            for (r in ordered) drawRoute(r, s, selected = r.id == selectedRouteId, dim = selectedRouteId != null && r.id != selectedRouteId, measurer)
        }
    }
}

/** Site tunes the curve with SHARPNESS = 0.27; this Catmull-Rom tension approximates it. */
private const val TENSION = 0.25f

private fun buildPath(points: List<TopoPoint>, s: Float): List<Path> {
    val paths = ArrayList<Path>()
    var run = ArrayList<TopoPoint>()
    fun flush() {
        if (run.size >= 2) paths += smoothPath(run, s)
        run = ArrayList()
    }
    for (p in points) {
        run += p
        if (p.hidden) flush().also { run += p }
    }
    flush()
    return paths
}

private fun smoothPath(pts: List<TopoPoint>, s: Float): Path {
    val path = Path()
    path.moveTo(pts[0].x * s, pts[0].y * s)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[max(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[min(i + 2, pts.lastIndex)]
        val c1x = p1.x + (p2.x - p0.x) * TENSION; val c1y = p1.y + (p2.y - p0.y) * TENSION
        val c2x = p2.x - (p3.x - p1.x) * TENSION; val c2y = p2.y - (p3.y - p1.y) * TENSION
        path.cubicTo(c1x * s, c1y * s, c2x * s, c2y * s, p2.x * s, p2.y * s)
    }
    return path
}

private fun DrawScope.drawRoute(r: TopoRoute, s: Float, selected: Boolean, dim: Boolean, measurer: TextMeasurer) {
    if (r.points.isEmpty()) return
    val color = gradeBandColor(r.gradeBand).let { if (dim) it.copy(alpha = 0.45f) else it }
    val width = (if (selected) 4.dp else 2.5f.dp).toPx()
    val paths = buildPath(r.points, s)
    for (p in paths) {
        if (selected) drawPath(p, Color.White, style = Stroke(width + 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    for (pt in r.points) drawMarker(pt, s, color, dim)
    val labelAt = r.points.firstOrNull { it.type == "label" } ?: r.points.first()
    r.label?.takeIf { it.isNotBlank() }?.let { drawLabel(it, Offset(labelAt.x * s, labelAt.y * s), color, measurer) }
}

private fun DrawScope.drawMarker(pt: TopoPoint, s: Float, color: Color, dim: Boolean) {
    val c = Offset(pt.x * s, pt.y * s)
    val r = 4.dp.toPx()
    val outline = if (dim) Color.Black.copy(alpha = 0.4f) else Color.Black
    when (pt.type) {
        "bolt", "draw", "piton", "thread" -> {
            drawCircle(Color.White, r, c); drawCircle(outline, r, c, style = Stroke(1.5.dp.toPx()))
        }
        "lower", "anchor" -> {
            drawCircle(Color.White, r * 1.6f, c); drawCircle(outline, r * 1.6f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(outline, r * 0.7f, c, style = Stroke(1.5.dp.toPx()))
        }
        "belay", "belayfixed", "belaynatural", "belaysemi", "belayhanging" -> {
            val p = Path().apply {
                moveTo(c.x, c.y - r * 1.6f); lineTo(c.x + r * 1.5f, c.y + r); lineTo(c.x - r * 1.5f, c.y + r); close()
            }
            drawPath(p, Color.White); drawPath(p, outline, style = Stroke(1.5.dp.toPx()))
        }
        "crux" -> {
            val d = r * 1.2f; val w = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.Yellow, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), w.width, cap = StrokeCap.Round)
            drawLine(Color.Yellow, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), w.width, cap = StrokeCap.Round)
        }
        "warning" -> {
            drawCircle(Color(0xFFFFC107), r * 1.4f, c); drawCircle(outline, r * 1.4f, c, style = Stroke(1.5.dp.toPx()))
        }
        "jumpoff", "endarrow" -> {
            val p = Path().apply {
                moveTo(c.x, c.y + r * 1.6f); lineTo(c.x + r * 1.3f, c.y - r); lineTo(c.x - r * 1.3f, c.y - r); close()
            }
            drawPath(p, color); drawPath(p, outline, style = Stroke(1.dp.toPx()))
        }
        else -> Unit
    }
}

private fun DrawScope.drawLabel(text: String, at: Offset, color: Color, measurer: TextMeasurer) {
    val layout = measurer.measure(text, TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    val pad = 3.dp.toPx()
    val w = layout.size.width + pad * 2; val h = layout.size.height + pad
    val topLeft = Offset(at.x - w / 2, at.y - h / 2)
    drawRoundRect(color, topLeft, Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
    drawRoundRect(Color.Black, topLeft, Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
    drawText(layout, topLeft = Offset(topLeft.x + pad, topLeft.y + pad / 2))
}

private fun DrawScope.drawAreas(topo: Topo, s: Float, measurer: TextMeasurer) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    for (a in topo.areas) {
        if (a.polygon.size >= 3) {
            val p = Path()
            p.moveTo(a.polygon[0].x * s, a.polygon[0].y * s)
            for (pt in a.polygon.drop(1)) p.lineTo(pt.x * s, pt.y * s)
            p.close()
            drawPath(p, Color.White.copy(alpha = 0.12f))
            drawPath(p, Color.Black, style = Stroke(3.dp.toPx(), pathEffect = dash))
            drawPath(p, Color.White, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        }
        val at = a.labelAt ?: a.polygon.firstOrNull() ?: continue
        val text = (a.label ?: a.name).replace('\n', ' ')
        val layout = measurer.measure(text, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        val pos = Offset(at.x * s - layout.size.width / 2, at.y * s - layout.size.height / 2)
        drawRoundRect(Color.Black.copy(alpha = 0.6f), pos - Offset(4f, 2f), Size(layout.size.width + 8f, layout.size.height + 4f))
        drawText(layout, topLeft = pos)
    }
}

internal fun distanceToPolyline(p: Offset, pts: List<TopoPoint>): Float {
    if (pts.isEmpty()) return Float.MAX_VALUE
    if (pts.size == 1) return hypot(p.x - pts[0].x, p.y - pts[0].y)
    var best = Float.MAX_VALUE
    for (i in 0 until pts.size - 1) {
        val a = pts[i]; val b = pts[i + 1]
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        val d = hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
        if (d < best) best = d
    }
    return best
}

internal fun pointInPolygon(p: Offset, poly: List<TopoPoint>): Boolean {
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y)) {
            val x = (b.x - a.x) * (p.y - a.y) / (b.y - a.y + if (abs(b.y - a.y) < 1e-6f) 1e-6f else 0f) + a.x
            if (p.x < x) inside = !inside
        }
        j = i
    }
    return inside
}
