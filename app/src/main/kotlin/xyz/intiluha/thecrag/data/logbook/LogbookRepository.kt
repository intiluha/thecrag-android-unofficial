package xyz.intiluha.thecrag.data.logbook

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import xyz.intiluha.thecrag.data.auth.AuthApi
import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.remote.dto.int
import xyz.intiluha.thecrag.data.remote.dto.long
import xyz.intiluha.thecrag.data.remote.dto.obj
import xyz.intiluha.thecrag.data.remote.dto.string
import java.io.File
import java.io.IOException

/**
 * The own logbook: a local copy refreshed only on request (a few paged calls),
 * plus an outbox of ticks that could not be sent. Both live as JSON files in
 * the app's private storage.
 */
class LogbookRepository(
    private val api: AuthApi,
    private val auth: AuthRepository,
    dir: File,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageSize: Int = 250,
) {
    private val logbookFile = File(dir, "logbook.json")
    private val outboxFile = File(dir, "outbox.json")

    private val _ascents = MutableStateFlow(load(logbookFile, Ascent.serializer()))
    val ascents: StateFlow<List<Ascent>> = _ascents
    private val _outbox = MutableStateFlow(load(outboxFile, TickRequest.serializer()))
    val outbox: StateFlow<List<TickRequest>> = _outbox
    val refreshedAt = MutableStateFlow(logbookFile.takeIf { it.exists() }?.lastModified())

    fun ascentsOf(routeId: Long): List<Ascent> = _ascents.value.filter { it.routeId == routeId }
    fun pendingFor(routeId: Long): List<TickRequest> = _outbox.value.filter { it.routeId == routeId }

    /**
     * Replaces the local copy with everything the server lists. Returns the count.
     * The reply carries no paging info, so pages are read until one comes back
     * short or adds no ascent not seen before (in case `page` were ignored).
     */
    suspend fun refresh(): Int {
        check(auth.account != null) { "Not logged in" }
        val all = LinkedHashMap<Long, Ascent>()
        var page = 1
        while (page <= MAX_PAGES) {
            val batch = AscentParser.parseList(api.ascents(page, pageSize))
            val before = all.size
            batch.forEach { all[it.id] = it }
            if (batch.size < pageSize || all.size == before) break
            page++
        }
        _ascents.value = all.values.toList()
        save(logbookFile, Ascent.serializer(), _ascents.value)
        refreshedAt.value = clock()
        return _ascents.value.size
    }

    /**
     * Sends a tick. With [dryRun] the server validates without writing and the
     * reply is returned as text. A network failure (not a rejection) parks the
     * tick in the outbox for [retryOutbox].
     */
    suspend fun tick(req: TickRequest, dryRun: Boolean = false): String {
        if (dryRun) return summarize(api.validateAscent(body(req)))
        return try {
            send(req)
        } catch (e: IOException) {
            _outbox.value = _outbox.value.filterNot { it.createdAt == req.createdAt } + req
            save(outboxFile, TickRequest.serializer(), _outbox.value)
            throw e
        }
    }

    /** Tries each parked tick in order; stops at the first network failure, keeps rejections with their reason. Returns how many were sent. */
    suspend fun retryOutbox(): Int {
        var sent = 0
        for (req in _outbox.value.toList()) {
            try {
                send(req)
                sent++
                removeFromOutbox(req)
            } catch (e: IOException) {
                break
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                _outbox.value = _outbox.value.map { if (it.createdAt == req.createdAt) it.copy(error = msg) else it }
                save(outboxFile, TickRequest.serializer(), _outbox.value)
            }
        }
        return sent
    }

    private var partnerCache: List<Climber>? = null

    /**
     * Who to suggest for "climbed with": the accounts you follow and your past partners, as the
     * website's completion lists them. Fetched once per process; a failure is the caller's to swallow.
     */
    suspend fun partnerSuggestions(): List<Climber> = partnerCache ?: listOf("follow", "history")
        .flatMap { mode -> ClimberParser.parseList(api.lookupClimber("", mode)) }
        .distinctBy { it.id }
        .also { partnerCache = it }

    /** Deletes an ascent on theCrag and locally. */
    suspend fun delete(id: Long) {
        api.deleteAscent(buildJsonObject { put("data", buildJsonObject { put("ascent", id) }) })
        _ascents.value = _ascents.value.filterNot { it.id == id }
        save(logbookFile, Ascent.serializer(), _ascents.value)
    }

    fun removeFromOutbox(req: TickRequest) {
        _outbox.value = _outbox.value.filterNot { it.createdAt == req.createdAt }
        save(outboxFile, TickRequest.serializer(), _outbox.value)
    }

    fun clearLocal() {
        _ascents.value = emptyList()
        _outbox.value = emptyList()
        logbookFile.delete()
        outboxFile.delete()
        refreshedAt.value = null
    }

    /** Creates the ascent, or updates [TickRequest.ascentId] when the request is an edit, and mirrors the result locally. */
    private suspend fun send(req: TickRequest): String {
        val editOf = req.ascentId
        if (editOf != null) {
            api.updateAscent(updateBody(editOf, req))
            val proposed = req.proposedGrade?.trim()?.ifBlank { null }
            _ascents.value = _ascents.value.map { old ->
                if (old.id != editOf) old else old.copy(
                    tick = req.tick, tickName = TickTypes.name(req.tick), gearStyle = req.gearStyle, date = req.date,
                    comment = req.comment.trim().ifBlank { null }, attempts = req.attempts, partners = req.partners.trim().ifBlank { null },
                    proposedGrade = proposed, grade = proposed ?: req.grade,
                )
            }
            save(logbookFile, Ascent.serializer(), _ascents.value)
            return "Ascent updated."
        }
        val res = api.createAscent(body(req))
        val id = res.obj("ok")?.long("ascentID") ?: res.obj("data")?.long("id") ?: res.obj("data")?.long("ascentID") ?: clock()
        val ascent = res.obj("data")?.let(AscentParser::parse)?.copy(id = id) ?: Ascent(
            id = id, routeId = req.routeId, routeName = req.routeName, grade = req.grade, gradeBand = req.gradeBand,
            tick = req.tick, tickName = TickTypes.name(req.tick), gearStyle = req.gearStyle, date = req.date,
            comment = req.comment.ifBlank { null }, cragName = req.cragName, attempts = req.attempts, partners = req.partners.ifBlank { null },
        )
        _ascents.value = listOf(ascent) + _ascents.value.filterNot { it.id == ascent.id }
        save(logbookFile, Ascent.serializer(), _ascents.value)
        return "Logged as ascent $id"
    }

    private suspend fun body(req: TickRequest): JsonObject {
        val account = auth.account ?: throw IllegalStateException("Not logged in")
        val grade = resolvedGrade(req)
        return buildJsonObject {
            put("data", buildJsonObject {
                put("account", account.id)
                put("node", req.routeId)
                put("version", 2)
                putTickFields(req)
                if (req.comment.isNotBlank()) put("comment", req.comment)
                if (req.partners.isNotBlank()) put("with", req.partners.trim())
                req.attempts?.let { put("numberAttempts", it) }
                putGrade(grade, clear = false)
            })
        }
    }

    /**
     * Edits ascent [id]: the whole form goes to `/api/ascent/update`, so an empty comment,
     * attempts or grade clears the stored one (`null` for comment and grade, `""` for attempts:
     * measured 2026-11-15). A network failure parks the edit in the outbox for [retryOutbox],
     * replacing any earlier pending edit of the same ascent; the local copy changes once it is sent.
     */
    suspend fun update(id: Long, req: TickRequest): String {
        val edit = req.copy(ascentId = id)
        return try {
            send(edit)
        } catch (e: IOException) {
            _outbox.value = _outbox.value.filterNot { it.createdAt == edit.createdAt || it.ascentId == id } + edit
            save(outboxFile, TickRequest.serializer(), _outbox.value)
            throw e
        }
    }

    /**
     * Replaces the outbox entry created at [createdAt] with the edited form [req]; nothing is sent.
     * The entry keeps its place, its creation time and, for a pending edit, the ascent it edits.
     */
    fun editPending(createdAt: Long, req: TickRequest) {
        _outbox.value = _outbox.value.map { if (it.createdAt == createdAt) req.copy(createdAt = createdAt, ascentId = it.ascentId, error = null) else it }
        save(outboxFile, TickRequest.serializer(), _outbox.value)
    }

    private suspend fun updateBody(id: Long, req: TickRequest): JsonObject {
        val grade = resolvedGrade(req)
        return buildJsonObject {
            put("data", buildJsonObject {
                put("ascent", id)
                putTickFields(req)
                put("comment", req.comment.trim().ifBlank { null })
                put("with", req.partners.trim().ifBlank { null })
                if (req.attempts != null) put("numberAttempts", req.attempts) else put("numberAttempts", "")
                putGrade(grade, clear = true)
            })
        }
    }

    private fun JsonObjectBuilder.putTickFields(req: TickRequest) {
        put("climbedGearStyle", TickTypes.apiGearStyle(req.gearStyle))
        put("tick", req.tick)
        put("date", req.date)
    }

    /** `gradeSystem`/`grade` for the first part of a parsed grade, `altGradeSystem`/`altGrade` for a second; nulls when [clear] and absent. */
    private fun JsonObjectBuilder.putGrade(parts: List<Pair<String, String>>?, clear: Boolean) {
        fun part(system: String, grade: String, value: Pair<String, String>?) {
            if (value != null) { put(system, value.first); put(grade, value.second) }
            else if (clear) { put(system, null as String?); put(grade, null as String?) }
        }
        part("gradeSystem", "grade", parts?.getOrNull(0))
        part("altGradeSystem", "altGrade", parts?.getOrNull(1))
    }

    private suspend fun resolvedGrade(req: TickRequest): List<Pair<String, String>>? =
        req.proposedGrade?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveGrade(it, req.gradeContext, req.gradeStyle) }

    /**
     * Asks theCrag's grade parser what [text] means in the route's grade [context]
     * (`AU`, `FR`, …) and [style] (`Free`, `Boulder`). It answers one component per
     * part of the grade (`E1 5b` is two) with the system each belongs to; create
     * wants the pair `gradeSystem`/`grade` and takes a second part as
     * `altGradeSystem`/`altGrade`. Returns (system, grade) pairs in sequence order.
     */
    private suspend fun resolveGrade(text: String, context: String?, style: String?): List<Pair<String, String>> {
        val reply = api.parseGrade(buildJsonObject {
            put("data", buildJsonObject {
                put("text", text)
                context?.let { put("context", it) }
                style?.let { put("style", it) }
            })
        })
        val parts = reply.obj("data")?.obj("components")?.values
            ?.mapNotNull { it as? JsonObject }
            ?.sortedBy { it.int("sequence") ?: Int.MAX_VALUE }
            ?.mapNotNull { c -> c.string("systemLabel")?.let { system -> (c.string("grade") ?: c.string("display"))?.let { system to it } } }
            .orEmpty()
        if (parts.isEmpty()) throw GradeNotRecognised(text, context)
        return parts
    }

    /** The proposed grade meant nothing to theCrag's parser; a rejection, not a network failure, so the tick is not parked. */
    class GradeNotRecognised(text: String, context: String?) : IllegalArgumentException(
        "theCrag does not recognise \"$text\" as a grade" + (context?.let { " in the $it grading context" } ?: "") + ".",
    )

    /**
     * validate answers 202 with the data echoed back (no "ok") and, for a field
     * it dislikes, an `error` object such as `{"gradeError":"invalid grade"}`
     * alongside; HTTP 400 rejections never reach here.
     */
    private fun summarize(res: JsonObject): String = when {
        res.containsKey("error") -> "Rejected: " + (res.obj("error")?.entries?.joinToString("; ") { (field, reason) ->
            "${field.removeSuffix("Error")}: ${(reason as? JsonPrimitive)?.content ?: reason}"
        } ?: res.string("error"))
        res.containsKey("ok") || res.containsKey("data") -> "theCrag would accept this tick."
        else -> res.toString().take(300)
    }

    private fun <T> load(file: File, s: KSerializer<T>): List<T> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(s), file.readText()) }.getOrDefault(emptyList())

    private fun <T> save(file: File, s: KSerializer<T>, items: List<T>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(s), items))
    }

    companion object {
        private const val MAX_PAGES = 40

        /** Routes with at least one clean ascent ([TickTypes.isSend]); what the area screen can hide. */
        fun sentRouteIds(ascents: List<Ascent>): Set<Long> =
            ascents.asSequence().filter { TickTypes.isSend(it.tick) }.mapNotNull { it.routeId }.toSet()

        /** Oldest first; ascents without a date last; ties broken by id so the order is stable. */
        val CHRONOLOGICAL: Comparator<Ascent> = compareBy<Ascent>({ it.date == null }, { it.date ?: "" }, { it.id })

        /** The ascents of each route in [CHRONOLOGICAL] order; ascents without a route id are left out. */
        fun ascentsByRoute(ascents: List<Ascent>): Map<Long, List<Ascent>> =
            ascents.filter { it.routeId != null }.sortedWith(CHRONOLOGICAL).groupBy { it.routeId!! }

        /**
         * Which ascents a route row shows as badges: all of them up to three, otherwise the
         * first two in [CHRONOLOGICAL] order. The flag says whether any were left out, so the
         * row can add a truncation mark and never shows more than three badges.
         */
        fun badgeAscents(ascents: List<Ascent>): Pair<List<Ascent>, Boolean> {
            val sorted = ascents.sortedWith(CHRONOLOGICAL)
            return if (sorted.size <= 3) sorted to false else sorted.take(2) to true
        }
    }
}
