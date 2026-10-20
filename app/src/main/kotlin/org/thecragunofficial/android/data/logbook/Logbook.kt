package org.thecragunofficial.android.data.logbook

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.thecragunofficial.android.data.remote.dto.array
import org.thecragunofficial.android.data.remote.dto.int
import org.thecragunofficial.android.data.remote.dto.long
import org.thecragunofficial.android.data.remote.dto.obj
import org.thecragunofficial.android.data.remote.dto.string
import java.time.Instant
import java.time.ZoneOffset

@Serializable
data class Ascent(
    val id: Long,
    val routeId: Long?,
    val routeName: String,
    val grade: String?,
    val gradeBand: Int,
    val tick: String,
    val tickName: String,
    val gearStyle: String?,
    /** YYYY-MM-DD when known. */
    val date: String?,
    val comment: String?,
    val cragName: String?,
)

/** A tick to send; kept in the outbox while the network is down. */
@Serializable
data class TickRequest(
    val routeId: Long,
    val routeName: String,
    val gearStyle: String,
    val tick: String,
    val date: String,
    val comment: String,
    val grade: String?,
    val gradeBand: Int,
    val cragName: String?,
    val createdAt: Long,
    val error: String? = null,
)

/**
 * Reads ascents out of theCrag's JSON defensively: the authenticated listing is
 * undocumented, so every field has fallbacks taken from the official create
 * schema and the personal-key logbook format.
 */
object AscentParser {
    fun parseList(root: JsonObject): List<Ascent> {
        val data = root["data"]
        val items: JsonArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data.array("ascents") ?: data.array("data") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return items.mapNotNull { (it as? JsonObject)?.let(::parse) }
    }

    fun parse(o: JsonObject): Ascent? {
        val id = o.long("id") ?: o.long("ascentID") ?: return null
        val route = o.obj("route") ?: o.obj("node")
        val tick = o.obj("tick")
        val gradeField = o["grade"]
        val grade = when (gradeField) {
            is JsonPrimitive -> o.string("grade")
            is JsonObject -> gradeField.string("grade") ?: gradeField.string("label")
            else -> null
        } ?: route?.string("grade")
        val date = o.string("date") ?: o.string("logDate")
            ?: o.long("epoch")?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
        val crag = route?.array("ancestors")?.lastOrNull()?.let { it as? JsonObject }?.string("name")
            ?: o.string("cragName") ?: o.array("ancestors")?.lastOrNull()?.let { it as? JsonObject }?.string("name")
        return Ascent(
            id = id,
            routeId = route?.long("id") ?: o.long("node") ?: o.long("nodeID") ?: o.long("route"),
            routeName = route?.string("name") ?: o.string("label") ?: o.string("name") ?: o.string("nodeName") ?: "?",
            grade = grade,
            gradeBand = o.int("gradeBand") ?: route?.int("gradeBand") ?: 0,
            tick = tick?.string("label") ?: o.string("tick") ?: o.string("tickType") ?: "tick",
            tickName = tick?.string("name") ?: TickTypes.name(tick?.string("label") ?: o.string("tick") ?: "tick"),
            gearStyle = o.string("climbedGearStyle") ?: o.string("gearStyle"),
            date = date?.take(10),
            comment = o.string("markdown") ?: o.string("comment"),
            cragName = crag,
        )
    }
}

/** Tick vocabulary per gear style, from the official create-ascent documentation. */
object TickTypes {
    /** Gear styles as `climbedGearStyle` values; the route's own `styleStub` always comes first in the UI. */
    val gearStyles = listOf("sport", "trad", "boulder", "toprope", "second", "solo", "dws", "aid", "ice", "alpine", "ferrata")

    private val LEAD = listOf("onsight", "flash", "redpoint", "pinkpoint", "dog", "allfreewithrest", "groundupredpoint", "leadsolo", "working", "attempt", "retreat", "tick")

    fun ticksFor(gearStyle: String): List<String> = when (gearStyle.lowercase()) {
        "trad" -> listOf("onsight", "flash", "redpoint", "pinkpoint", "greenpointonsight", "greenpointflash", "greenpoint") + LEAD.drop(4)
        "sport" -> LEAD
        "boulder" -> listOf("flash", "send", "onsight", "repeat", "dab", "working", "attempt")
        "toprope", "top-rope" -> listOf("onsight", "flash", "clean", "dog", "ropedsolo", "attempt", "tick")
        "second" -> listOf("onsight", "flash", "clean", "dog", "attempt", "tick")
        "solo" -> listOf("onsight", "flash", "redpoint", "attempt", "retreat", "tick")
        "dws", "deepwater", "deep-water-solo" -> listOf("onsight", "flash", "redpoint", "working", "attempt", "retreat", "tick")
        "aid" -> listOf("onsight", "flash", "tick", "aidsolo", "working", "attempt", "retreat")
        "ice" -> listOf("onsight", "flash", "redpoint", "working", "attempt", "retreat", "tick")
        "alpine", "ferrata", "via-ferrata" -> listOf("tick", "attempt", "retreat")
        else -> listOf("tick", "attempt")
    }

    private val NAMES = mapOf(
        "onsight" to "Onsight", "flash" to "Flash", "redpoint" to "Redpoint", "pinkpoint" to "Pinkpoint",
        "greenpointonsight" to "Greenpoint onsight", "greenpointflash" to "Greenpoint flash", "greenpoint" to "Greenpoint",
        "dog" to "Hang dog", "allfreewithrest" to "All free with rest", "groundupredpoint" to "Ground up redpoint",
        "leadsolo" to "Lead solo", "working" to "Working", "attempt" to "Attempt", "retreat" to "Retreat", "tick" to "Tick",
        "send" to "Send", "repeat" to "Repeat", "dab" to "Dab", "clean" to "Clean", "ropedsolo" to "Roped solo", "aidsolo" to "Aid solo",
        "secondclean" to "Second clean", "secondrest" to "Second with rest", "toproperest" to "Top rope with rest", "ghost" to "Ghost",
        "firstascent" to "First ascent", "firstfreeascent" to "First free ascent", "hit" to "Hit", "target" to "Target", "mark" to "Mark",
    )

    fun name(label: String): String = NAMES[label] ?: label.replaceFirstChar { it.uppercase() }
}
