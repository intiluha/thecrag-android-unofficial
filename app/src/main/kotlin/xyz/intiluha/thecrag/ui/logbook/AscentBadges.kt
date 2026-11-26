package xyz.intiluha.thecrag.ui.logbook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.data.logbook.Ascent
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.Pitches
import xyz.intiluha.thecrag.data.logbook.TickRequest
import xyz.intiluha.thecrag.data.logbook.TickTypes

/**
 * A tick type as a small round picture: a glyph from the Material icon set on a
 * coloured disc for clean sends, on an outlined disc for everything else. Our own
 * vocabulary, deliberately unlike theCrag's tick icons. A clean ascent that was not led
 * (top rope, second) keeps the colour but only as an outline, so a top-rope flash is
 * told apart from a led one at a glance.
 */
data class TickGlyph(val icon: ImageVector, val color: Color?, val led: Boolean = true) {
    val send: Boolean get() = color != null

    companion object {
        private val ONSIGHT = Color(0xFF2E7D32)
        private val FLASH = Color(0xFFF9A825)
        private val REDPOINT = Color(0xFFD32F2F)
        private val PINKPOINT = Color(0xFFD81B60)
        private val GREENPOINT = Color(0xFF00897B)
        private val CLEAN = Color(0xFF3949AB)
        private val REPEAT = Color(0xFF1E88E5)
        private val SOLO = Color(0xFF6D4C41)
        private val FIRST = Color(0xFFEF6C00)

        /** Gear styles in which the climber did not lead; their sends are drawn as coloured outlines. */
        private val NOT_LED = setOf("toprope", "top-rope", "second")

        fun of(tick: String, gearStyle: String? = null): TickGlyph =
            base(tick).copy(led = gearStyle?.lowercase() !in NOT_LED)

        private fun base(tick: String): TickGlyph = when (tick.lowercase()) {
            "onsight" -> TickGlyph(Icons.Default.Visibility, ONSIGHT)
            "flash" -> TickGlyph(Icons.Default.Bolt, FLASH)
            "redpoint", "groundupredpoint" -> TickGlyph(Icons.Default.FiberManualRecord, REDPOINT)
            "pinkpoint" -> TickGlyph(Icons.Default.FiberManualRecord, PINKPOINT)
            "greenpoint" -> TickGlyph(Icons.Default.Eco, GREENPOINT)
            "greenpointonsight" -> TickGlyph(Icons.Default.Visibility, GREENPOINT)
            "greenpointflash" -> TickGlyph(Icons.Default.Bolt, GREENPOINT)
            "clean", "secondclean", "tick", "send" -> TickGlyph(Icons.Default.Check, CLEAN)
            "repeat" -> TickGlyph(Icons.Default.Repeat, REPEAT)
            "leadsolo", "ropedsolo", "aidsolo" -> TickGlyph(Icons.Default.Person, SOLO)
            "firstascent", "firstfreeascent" -> TickGlyph(Icons.Default.Star, FIRST)
            "dog" -> TickGlyph(Icons.Default.Pets, null)
            "allfreewithrest" -> TickGlyph(Icons.Default.Pause, null)
            "working" -> TickGlyph(Icons.Default.Construction, null)
            "attempt" -> TickGlyph(Icons.Default.Remove, null)
            "retreat" -> TickGlyph(Icons.Default.ArrowDownward, null)
            "dab" -> TickGlyph(Icons.Default.TouchApp, null)
            "ghost" -> TickGlyph(Icons.Default.VisibilityOff, null)
            "hit", "target", "mark" -> TickGlyph(Icons.Default.Flag, null)
            else -> if (TickTypes.isSend(tick)) TickGlyph(Icons.Default.Check, CLEAN) else TickGlyph(Icons.Default.Remove, null)
        }
    }
}

/**
 * The user's ascents of one route as small pictures, oldest first: at most three,
 * the third being "…" when there are more (see [LogbookRepository.badgeAscents]).
 */
@Composable
fun AscentBadges(ascents: List<Ascent>, modifier: Modifier = Modifier) {
    val (shown, truncated) = remember(ascents) { LogbookRepository.badgeAscents(ascents) }
    if (shown.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { AscentBadge(it.tick, it.gearStyle) }
        if (truncated) MoreBadge()
    }
}

/**
 * One tick type as a 22 dp disc: filled when led clean, coloured outline when clean on
 * top rope or second, grey outline otherwise; [pending] draws it in the error colour.
 */
@Composable
fun AscentBadge(tick: String, gearStyle: String?, modifier: Modifier = Modifier, pending: Boolean = false) {
    val glyph = TickGlyph.of(tick, gearStyle)
    val colors = MaterialTheme.colorScheme
    val tint = when {
        pending -> colors.error
        glyph.send && glyph.led -> Color.White
        glyph.send -> glyph.color!!
        else -> colors.onSurfaceVariant
    }
    val look = when {
        pending -> Modifier.border(1.5.dp, colors.error, CircleShape)
        glyph.send && glyph.led -> Modifier.background(glyph.color!!, CircleShape)
        glyph.send -> Modifier.border(1.5.dp, glyph.color!!, CircleShape)
        else -> Modifier.border(1.5.dp, colors.outlineVariant, CircleShape)
    }
    Box(modifier.size(22.dp).then(look), contentAlignment = Alignment.Center) {
        Icon(glyph.icon, contentDescription = TickTypes.name(tick), Modifier.size(14.dp), tint = tint)
    }
}

/** "…": more ascents than the row shows. */
@Composable
private fun MoreBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(22.dp).border(1.5.dp, colors.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MoreHoriz, contentDescription = "…", Modifier.size(14.dp), tint = colors.onSurfaceVariant)
    }
}

/**
 * One of the user's ascents on the route's own page: picture, tick name, date and gear style, comment. The route name is in the header already.
 * On a multi-pitch route ([routePitches] > 1) the line also says what was climbed: the whole route or which pitches.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun RouteAscentRow(a: Ascent, modifier: Modifier = Modifier, routePitches: Int? = null, onLongClick: (() -> Unit)? = null) {
    val pressable = if (onLongClick == null) Modifier else Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    Row(modifier.fillMaxWidth().then(pressable).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        AscentBadge(a.tick, a.gearStyle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(TickTypes.name(a.tick), a.date, a.gearStyle, Pitches.label(a.pitchNumbers, routePitches ?: a.routePitches)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Pitches.details(a.pitches)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            a.partners?.let { Text(stringResource(R.string.with_partners, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            a.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        }
    }
}

/** An outbox tick of the route being viewed, in the same layout as [RouteAscentRow] but in the error colour. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun RoutePendingRow(t: TickRequest, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val pressable = if (onLongClick == null) Modifier else Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    Row(modifier.fillMaxWidth().then(pressable).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        AscentBadge(t.tick, t.gearStyle, pending = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(
                    TickTypes.name(t.tick), t.date, t.gearStyle, Pitches.label(t.pitches, t.routePitches),
                    stringResource(if (t.ascentId != null) R.string.pending_edit else R.string.pending),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
            (t.error ?: t.comment.ifBlank { null })?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
