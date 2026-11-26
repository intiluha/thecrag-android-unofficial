package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.auth.Account
import xyz.intiluha.thecrag.data.auth.AuthApi
import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.logbook.AscentParser
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.PitchTick
import xyz.intiluha.thecrag.data.logbook.TickRequest
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.InMemoryStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import xyz.intiluha.thecrag.data.remote.authHttp
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Pitches of an ascent: what goes out in `pitch[]` on create and update, and what is read back.
 * The request side follows the official create documentation; the reply shapes are the documented
 * candidates, none of them seen live.
 */
class MultipitchTest {
    private val json = ApiFactory.json
    private val req = TickRequest(
        routeId = 555, routeName = "The Bard", gearStyle = "trad", tick = "onsight", date = "2026-11-20", comment = "",
        grade = "12", gradeBand = 1, cragName = "Arapiles", createdAt = 1000L, routePitches = 5,
    )

    private fun repo(server: MockWebServer, dir: File = Files.createTempDirectory("logbook").toFile()): LogbookRepository {
        val store = InMemoryStore().apply { put(AuthRepository.KEY, json.encodeToString(Account.serializer(), Account(9001, "Tester", 1))) }
        val jar = PersistentCookieJar(store, PersistentCookieJar.AUTH_KEY)
        val api = ApiFactory.retrofit(ApiFactory.authHttp(jar, debug = false), server.url("/").toString()).create(AuthApi::class.java)
        return LogbookRepository(api, AuthRepository(api, jar, store, json), dir, json, clock = { 5000L })
    }

    private fun ok(body: String) = MockResponse().setResponseCode(202).setBody(body).addHeader("Content-Type", "application/json")
    private fun MockWebServer.sentData(): JsonObject = json.parseToJsonElement(takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
    private fun parse(item: String) = AscentParser.parse(json.parseToJsonElement(item).jsonObject)!!

    @Test
    fun firstPitchOnlyGoesOutAsPitchArray() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"801"}}""")); start() }
        val repo = repo(server)
        repo.tick(req.copy(pitches = listOf(1)))
        val data = server.sentData()
        assertEquals("""[{"number":1}]""", data["pitch"].toString())
        assertEquals("onsight", data["tick"]!!.jsonPrimitive.content)

        val mine = repo.ascentsOf(555).single()
        assertEquals(listOf(PitchTick(1)), mine.pitches)
        assertEquals(5, mine.routePitches)
        assertTrue("a first-pitch send leaves the route unclimbed", LogbookRepository.sentRouteIds(repo.ascents.value).isEmpty())
        server.shutdown()
    }

    @Test
    fun chosenPitchesAreSentSortedAndOnce() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"802"}}""")); start() }
        repo(server).tick(req.copy(pitches = listOf(3, 1, 3, 2)))
        assertEquals("""[{"number":1},{"number":2},{"number":3}]""", server.sentData()["pitch"].toString())
        server.shutdown()
    }

    @Test
    fun wholeRouteSendsNoPitchKey() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"803"}}""")); start() }
        val repo = repo(server)
        repo.tick(req)
        assertEquals(null, server.sentData()["pitch"])
        assertTrue(repo.ascentsOf(555).single().pitches.isEmpty())
        assertEquals(setOf(555L), LogbookRepository.sentRouteIds(repo.ascents.value))
        server.shutdown()
    }

    @Test
    fun dryRunCarriesThePitchesToo() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"data":{"node":555}}""")); start() }
        repo(server).tick(req.copy(pitches = listOf(1, 2)), dryRun = true)
        val r = server.takeRequest()
        assertEquals("/api/ascent/create/validate?cookieAuth=1", r.path)
        val data = json.parseToJsonElement(r.body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals(2, data["pitch"]!!.jsonArray.size)
        server.shutdown()
    }

    @Test
    fun anEchoedReplyDoesNotLoseThePitchesSent() = runBlocking {
        // create may echo `data` in a spelling the parser does not read; the local copy still has what was sent.
        val server = MockWebServer().apply { enqueue(ok("""{"data":{"node":555,"tick":"onsight"},"ok":{"ascentID":"804"}}""")); start() }
        val repo = repo(server)
        repo.tick(req.copy(pitches = listOf(1)))
        assertEquals(listOf(1), repo.ascentsOf(555).single().pitchNumbers)
        server.shutdown()
    }

    @Test
    fun updateSendsPitchesOnlyWhenTheChoiceChanged() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok("""{"ok":{"ascentID":"805"}}"""))
            repeat(3) { enqueue(ok("""{"data":{"ascent":805}}""")) }
            start()
        }
        val repo = repo(server)
        repo.tick(req.copy(pitches = listOf(1)))
        server.takeRequest()

        // Same pitches, another tick: `pitch` stays out, so details held only by theCrag are not overwritten.
        repo.update(805, req.copy(tick = "redpoint", pitches = listOf(1)))
        val same = server.sentData()
        assertEquals("redpoint", same["tick"]!!.jsonPrimitive.content)
        assertTrue("pitch" !in same)

        // One more pitch.
        repo.update(805, req.copy(pitches = listOf(1, 2)))
        assertEquals("""[{"number":1},{"number":2}]""", server.sentData()["pitch"].toString())
        assertEquals(listOf(1, 2), repo.ascentsOf(555).single().pitchNumbers)

        // Back to the whole route: an empty array clears the pitch details.
        repo.update(805, req)
        assertEquals(JsonArray(emptyList()), server.sentData()["pitch"])
        assertTrue(repo.ascentsOf(555).single().pitches.isEmpty())
        assertEquals(setOf(555L), LogbookRepository.sentRouteIds(repo.ascents.value))
        server.shutdown()
    }

    @Test
    fun updateKeepsTheTickTheCragRecordedForAPitch() = runBlocking {
        val item = """{"id":"806","date":"2026-11-20","tick":{"label":"onsight"},"climbedGearStyle":"trad",
            "pitch":[{"number":1,"tick":{"label":"onsight","name":"Onsight"},"leadby":"me"},{"number":2,"tick":"dog"}],
            "route":{"id":"555","name":"The Bard","pitches":5}}"""
        val server = MockWebServer().apply {
            enqueue(ok("""{"data":[$item]}"""))
            enqueue(ok("""{"data":{"ascent":806}}"""))
            start()
        }
        val repo = repo(server)
        repo.refresh()
        server.takeRequest()
        repo.update(806, req.copy(pitches = listOf(1, 3)))
        assertEquals("""[{"number":1,"tick":"onsight"},{"number":3}]""", server.sentData()["pitch"].toString())
        assertEquals(listOf(PitchTick(1, "onsight", "me"), PitchTick(3)), repo.ascentsOf(555).single().pitches)
        server.shutdown()
    }

    @Test
    fun offlinePartialTickKeepsItsPitchesInTheOutbox() = runBlocking {
        val dir = Files.createTempDirectory("logbook").toFile()
        val down = MockWebServer().apply { start(); shutdown() }
        try { repo(down, dir).tick(req.copy(pitches = listOf(1))) } catch (e: IOException) { /* parked */ }

        val up = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"807"}}""")); start() }
        val later = repo(up, dir)
        assertEquals(listOf(1), later.outbox.value.single().pitches)
        later.editPending(1000L, req.copy(pitches = listOf(1, 2)))
        assertEquals(1, later.retryOutbox())
        assertEquals("""[{"number":1},{"number":2}]""", up.sentData()["pitch"].toString())
        assertEquals(listOf(1, 2), later.ascentsOf(555).single().pitchNumbers)
        up.shutdown()
    }

    @Test
    fun parserReadsThePitchObjectsOfTheCreateSchema() {
        val a = parse(
            """{"id":1,"tick":{"label":"redpoint"},"route":{"id":5,"name":"R","pitches":3},
               "pitch":[{"number":"2","tick":{"label":"dog","name":"Hang dog"},"leadby":{"id":7,"login":"ann","name":"Ann"}},{"number":1,"tick":"onsight","leadby":""}]}""",
        )
        assertEquals(listOf(PitchTick(1, "onsight"), PitchTick(2, "dog", "Ann")), a.pitches)
        assertEquals(3, a.routePitches)
    }

    @Test
    fun parserReadsTheOtherDocumentedSpellings() {
        // Facet API: each pitch an array [number, leadby, rating system, grade, alt rating system, alt grade, linkup].
        val facet = parse("""{"id":2,"tick":"tick","node":5,"pitch":[[1,"me","AU","12",null,null,null],[2,null,"AU","14",null,null,null]]}""")
        assertEquals(listOf(PitchTick(1, leadBy = "me"), PitchTick(2)), facet.pitches)
        // Bare numbers, under `pitches`; junk and repeats are dropped.
        val bare = parse("""{"id":3,"tick":"tick","node":5,"pitches":[2,"1",2,0,"x",null]}""")
        assertEquals(listOf(1, 2), bare.pitchNumbers)
        assertEquals(null, bare.routePitches)
    }

    @Test
    fun anAscentWithoutPitchDetailsHasNone() {
        assertTrue(parse("""{"id":4,"tick":{"label":"flash"},"route":{"id":5,"name":"R","pitches":2}}""").pitches.isEmpty())
        // A scalar under `pitches` is not a pitch list.
        assertTrue(parse("""{"id":5,"tick":{"label":"flash"},"pitches":3,"route":{"id":5,"name":"R"}}""").pitches.isEmpty())
        // The live single-pitch samples parse as before.
        val listed = AscentParser.parseList(json.parseToJsonElement(sample("account_me_ascents.json")) as JsonObject).single()
        assertTrue(listed.pitches.isEmpty())
        assertEquals(null, listed.routePitches)
    }

    @Test
    fun refreshKeepsThePitchCountLearntFromTheTickSheet() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok("""{"ok":{"ascentID":"808"}}"""))
            enqueue(ok("""{"data":[{"id":"808","tick":{"label":"onsight"},"pitch":[{"number":1}],"route":{"id":"555","name":"The Bard"}}]}"""))
            start()
        }
        val repo = repo(server)
        repo.tick(req.copy(pitches = listOf(1)))
        repo.refresh()
        val mine = repo.ascentsOf(555).single()
        assertEquals(listOf(1), mine.pitchNumbers)
        assertEquals(5, mine.routePitches)
        server.shutdown()
    }

    @Test
    fun storedLogbooksFromBeforePitchesStillLoad() {
        val dir = Files.createTempDirectory("logbook").toFile()
        File(dir, "logbook.json").writeText(
            """[{"id":1,"routeId":5,"routeName":"R","grade":"20","gradeBand":3,"tick":"flash","tickName":"Flash","gearStyle":"sport","date":"2026-10-01","comment":null,"cragName":null}]""",
        )
        val server = MockWebServer().apply { start() }
        val a = repo(server, dir).ascents.value.single()
        assertTrue(a.pitches.isEmpty())
        server.shutdown()
    }

    private fun sample(name: String) =
        listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name")).first { it.exists() }.readText()
}
