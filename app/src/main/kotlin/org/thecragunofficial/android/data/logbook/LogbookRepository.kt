package org.thecragunofficial.android.data.logbook

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.thecragunofficial.android.data.auth.AuthApi
import org.thecragunofficial.android.data.auth.AuthRepository
import org.thecragunofficial.android.data.remote.dto.long
import org.thecragunofficial.android.data.remote.dto.obj
import org.thecragunofficial.android.data.remote.dto.string
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

    /** Replaces the local copy with everything the server lists. Returns the count. */
    suspend fun refresh(): Int {
        check(auth.account != null) { "Not logged in" }
        val all = ArrayList<Ascent>()
        var page = 1
        while (page <= MAX_PAGES) {
            val batch = AscentParser.parseList(api.ascents(page, pageSize))
            all += batch
            if (batch.size < pageSize) break
            page++
        }
        _ascents.value = all.distinctBy { it.id }
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

    private suspend fun send(req: TickRequest): String {
        val res = api.createAscent(body(req))
        val id = res.obj("ok")?.long("ascentID") ?: res.obj("data")?.long("id") ?: res.obj("data")?.long("ascentID") ?: clock()
        val ascent = res.obj("data")?.let(AscentParser::parse)?.copy(id = id) ?: Ascent(
            id = id, routeId = req.routeId, routeName = req.routeName, grade = req.grade, gradeBand = req.gradeBand,
            tick = req.tick, tickName = TickTypes.name(req.tick), gearStyle = req.gearStyle, date = req.date,
            comment = req.comment.ifBlank { null }, cragName = req.cragName,
        )
        _ascents.value = listOf(ascent) + _ascents.value.filterNot { it.id == ascent.id }
        save(logbookFile, Ascent.serializer(), _ascents.value)
        return "Logged as ascent $id"
    }

    private fun body(req: TickRequest): JsonObject {
        val account = auth.account ?: throw IllegalStateException("Not logged in")
        return buildJsonObject {
            put("data", buildJsonObject {
                put("account", account.id)
                put("node", req.routeId)
                put("version", 2)
                put("climbedGearStyle", req.gearStyle)
                put("tick", req.tick)
                put("date", req.date)
                if (req.comment.isNotBlank()) put("comment", req.comment)
            })
        }
    }

    /** validate answers 202 with the data echoed back (no "ok"); rejections are HTTP 400 and never reach here. */
    private fun summarize(res: JsonObject): String = when {
        res.containsKey("ok") || res.containsKey("data") -> "theCrag would accept this tick."
        res.containsKey("error") -> "Rejected: ${res.string("error")}"
        else -> res.toString().take(300)
    }

    private fun <T> load(file: File, s: KSerializer<T>): List<T> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(s), file.readText()) }.getOrDefault(emptyList())

    private fun <T> save(file: File, s: KSerializer<T>, items: List<T>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(s), items))
    }

    private companion object {
        const val MAX_PAGES = 40
    }
}
