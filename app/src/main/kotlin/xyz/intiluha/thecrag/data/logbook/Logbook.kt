package xyz.intiluha.thecrag.data.logbook

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import xyz.intiluha.thecrag.data.remote.dto.array
import xyz.intiluha.thecrag.data.remote.dto.int
import xyz.intiluha.thecrag.data.remote.dto.long
import xyz.intiluha.thecrag.data.remote.dto.obj
import xyz.intiluha.thecrag.data.remote.dto.string
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
    /** `numberAttempts`: tries in the session, when recorded. */
    val attempts: Int? = null,
    /** The grade the climber proposed, as text (`7a`, or `E1 5b` for a compound grade); null when none. */
    val proposedGrade: String? = null,
    /** `with`: partners as the website stores them, one comma-separated string, members as `@login`. */
    val partners: String? = null,
    /**
     * The pitches this ascent records, by number. Empty when theCrag holds no pitch details, which is
     * a tick of the route as a whole. See [Pitches] for what a non-empty list means against the route.
     */
    val pitches: List<PitchTick> = emptyList(),
    /** How many pitches the route has, when known: from the tick sheet for ascents logged here, from the listing if it says. */
    val routePitches: Int? = null,
) {
    val pitchNumbers: List<Int> get() = pitches.map { it.number }
}

/** One pitch of an ascent: its number on the route and, when theCrag recorded them, the tick of that pitch and who led it. */
@Serializable
data class PitchTick(val number: Int, val tick: String? = null, val leadBy: String? = null)

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
    /** Optional `numberAttempts`. */
    val attempts: Int? = null,
    /** Grade the climber proposes, as typed; resolved to `gradeSystem` + `grade` through theCrag's parser when sent. */
    val proposedGrade: String? = null,
    /** The route's grade context (`AU`, `FR`, …) and style (`Free`, `Boulder`), which disambiguate the typed grade. */
    val gradeContext: String? = null,
    val gradeStyle: String? = null,
    /** When set, this is an edit of that ascent waiting to go out through `/api/ascent/update`, not a new tick. */
    val ascentId: Long? = null,
    /** Partners for `with`, comma separated, members as `@login`; empty for none. */
    val partners: String = "",
    /** Pitch numbers climbed, for `pitch[]`; empty for the whole route (no pitch details sent). */
    val pitches: List<Int> = emptyList(),
    /** The route's pitch count, so the logged ascent can be told apart from a full one without the route at hand. */
    val routePitches: Int? = null,
)

/**
 * What an ascent's pitch list means against the route's pitch count. An ascent without pitch
 * details is of the whole route; one that lists pitches covers just those, and is partial when
 * they fall short of `1..routePitches`. Without the count a listed ascent cannot be judged, so
 * it is only described ("pitch 1"), never called partial.
 */
object Pitches {
    /** True when [numbers] are known to leave out part of a route of [routePitches] pitches. */
    fun isPartial(numbers: List<Int>, routePitches: Int?): Boolean =
        numbers.isNotEmpty() && routePitches != null && routePitches > 1 && !numbers.containsAll((1..routePitches).toList())

    /** `1`, `1–3`, `1, 3–4`: sorted, runs collapsed. */
    fun ranges(numbers: List<Int>): String {
        val runs = mutableListOf<IntRange>()
        for (n in numbers.distinct().sorted()) {
            val last = runs.lastOrNull()
            if (last != null && n == last.last + 1) runs[runs.size - 1] = last.first..n else runs.add(n..n)
        }
        return runs.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" }
    }

    /**
     * How a row says what was climbed: `pitch 1 of 3`, `pitches 1–2 of 3`, `all 3 pitches`; `whole route`
     * for an ascent without pitch details on a route known to be multi-pitch; `pitch 1` when the count is
     * unknown. Null when there is nothing to say (single-pitch route, or no details and no count).
     */
    fun label(numbers: List<Int>, routePitches: Int?): String? {
        val multi = routePitches != null && routePitches > 1
        if (numbers.isEmpty()) return if (multi) "whole route" else null
        if (routePitches == 1 && numbers.all { it == 1 }) return null
        val distinct = numbers.distinct()
        val what = (if (distinct.size == 1) "pitch " else "pitches ") + ranges(distinct)
        return when {
            !multi -> what
            isPartial(distinct, routePitches) -> "$what of $routePitches"
            else -> "all $routePitches pitches"
        }
    }

    /** Per-pitch ticks and leaders, when theCrag recorded any: `P1 Onsight (led by Ann) · P2 Hang dog`; null otherwise. */
    fun details(pitches: List<PitchTick>): String? =
        if (pitches.none { it.tick != null || it.leadBy != null }) null
        else pitches.sortedBy { it.number }.joinToString(" · ") { p ->
            listOfNotNull("P${p.number}", p.tick?.let(TickTypes::name), p.leadBy?.let { "(led by $it)" }).joinToString(" ")
        }
}

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
            attempts = o.int("numberAttempts"),
            partners = o.string("with")?.ifBlank { null },
            pitches = pitches(o),
            routePitches = route?.int("pitches") ?: route?.int("numberPitches"),
            // A top-level grade is the climber's own only when it comes with its system; a plain ascent has no `gradeSystem`.
            proposedGrade = if (o.containsKey("gradeSystem")) listOfNotNull(o.string("grade"), o.string("altGrade")).joinToString(" ").ifBlank { null } else null,
        )
    }

    /**
     * The ascent's pitch list. How the listing spells it has not been seen live (no multi-pitch ascent
     * in the samples), so this reads what the documentation suggests: the key `pitch` (create schema,
     * personal-key logbook) or `pitches`, holding objects as create takes them (`number`, `tick` as a
     * label or a `{label, name}` object, `leadby`), the facet API's arrays (`[number, leadby, …]`) or
     * bare numbers. Entries without a positive number are dropped.
     */
    private fun pitches(o: JsonObject): List<PitchTick> {
        val items = o.array("pitch") ?: o.array("pitches") ?: return emptyList()
        return items.mapNotNull { e ->
            when (e) {
                is JsonObject -> (e.int("number") ?: e.int("pitch") ?: e.int("pitchNumber"))?.let { n ->
                    PitchTick(n, e.obj("tick")?.string("label") ?: e.string("tick"), leader(e["leadby"] ?: e["leadBy"]))
                }
                is JsonArray -> (e.getOrNull(0) as? JsonPrimitive)?.content?.toIntOrNull()?.let { n -> PitchTick(n, leadBy = leader(e.getOrNull(1))) }
                is JsonPrimitive -> e.content.toIntOrNull()?.let { PitchTick(it) }
                else -> null
            }
        }.filter { it.number > 0 }.distinctBy { it.number }.sortedBy { it.number }
    }

    /** Who led a pitch: free text, or an account object by its name or login. */
    private fun leader(e: JsonElement?): String? = when (e) {
        is JsonObject -> e.string("name") ?: e.string("login")?.let { "@$it" }
        is JsonNull -> null
        is JsonPrimitive -> e.content.takeIf { it.isNotBlank() }
        else -> null
    }
}

/** Tick vocabulary per gear style, from the official create-ascent documentation. */
object TickTypes {
    /**
     * Gear styles as `climbedGearStyle` values; the route's own `styleStub` always comes first in the UI.
     * These are theCrag's gear style stubs, the ones a route carries as `styleStub` and the map lists under
     * `gearStyles`: top rope is `top-rope` and via ferrata `via-ferrata`; `toprope` is refused with HTTP 400.
     */
    val gearStyles = listOf("sport", "trad", "boulder", "top-rope", "second", "solo", "dws", "aid", "ice", "alpine", "via-ferrata")

    /** The stub theCrag accepts for [gearStyle]; maps the spellings this app used to send and store. */
    fun apiGearStyle(gearStyle: String): String = when (val s = gearStyle.trim().lowercase()) {
        "toprope", "top rope" -> "top-rope"
        "ferrata", "viaferrata", "via ferrata" -> "via-ferrata"
        else -> s
    }

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

    /**
     * Ticks that mean the route was climbed clean: onsight, flash, redpoint and their
     * variants, top-rope/second "clean", boulder sends and repeats, plain "tick" (climbed,
     * no details). Hang dog, working, attempt, retreat, dab and ghost do not count.
     */
    val SENDS = setOf(
        "onsight", "flash", "redpoint", "pinkpoint", "greenpoint", "greenpointonsight", "greenpointflash",
        "groundupredpoint", "leadsolo", "ropedsolo", "aidsolo", "clean", "secondclean", "send", "repeat", "tick",
        "firstascent", "firstfreeascent",
    )

    fun isSend(label: String): Boolean = label.lowercase() in SENDS
}

/** A theCrag account as the climber lookup returns it; `login` is what goes into `with` as `@login`. */
@Serializable
data class Climber(val id: Long, val login: String, val name: String)

object ClimberParser {
    /** `/api/lookup/climber` answers `{"data":[{id, login, name, grouplabel, …}]}`. */
    fun parseList(root: JsonObject): List<Climber> = root.array("data").orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val login = o.string("login") ?: return@mapNotNull null
        Climber(o.long("id") ?: return@mapNotNull null, login, o.string("name") ?: login)
    }
}
