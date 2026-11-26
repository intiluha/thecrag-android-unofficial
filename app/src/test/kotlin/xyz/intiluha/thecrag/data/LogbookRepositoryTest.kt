package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xyz.intiluha.thecrag.data.auth.Account
import xyz.intiluha.thecrag.data.auth.AuthApi
import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.logbook.AscentParser
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.TickRequest
import xyz.intiluha.thecrag.data.logbook.TickTypes
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.InMemoryStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import xyz.intiluha.thecrag.data.remote.authHttp
import java.io.File
import java.io.IOException
import java.nio.file.Files

class LogbookRepositoryTest {
    private val json = ApiFactory.json
    private val req = TickRequest(
        routeId = 555, routeName = "Kachoong", gearStyle = "sport", tick = "redpoint", date = "2026-10-20", comment = "pumpy",
        grade = "21", gradeBand = 3, cragName = "Arapiles", createdAt = 1000L,
    )

    private fun repo(server: MockWebServer, dir: File, pageSize: Int = 250): LogbookRepository {
        val store = InMemoryStore().apply { put(AuthRepository.KEY, json.encodeToString(Account.serializer(), Account(9001, "Tester", 1))) }
        val jar = PersistentCookieJar(store, PersistentCookieJar.AUTH_KEY)
        val api = ApiFactory.retrofit(ApiFactory.authHttp(jar, debug = false), server.url("/").toString()).create(AuthApi::class.java)
        val auth = AuthRepository(api, jar, store, json)
        return LogbookRepository(api, auth, dir, json, clock = { 5000L }, pageSize = pageSize)
    }

    private fun ok(body: String) = MockResponse().setBody(body).addHeader("Content-Type", "application/json")

    @Test
    fun topRopeGoesOutAsTheStubTheCragAccepts() = runBlocking {
        assertTrue("top-rope" in TickTypes.gearStyles)
        assertTrue("flash" in TickTypes.ticksFor("top-rope") && "dog" in TickTypes.ticksFor("top-rope"))
        // An entry parked in the outbox by an older version still says "toprope".
        for (style in listOf("top-rope", "toprope")) {
            val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"778"}}""")); start() }
            repo(server, Files.createTempDirectory("logbook").toFile()).tick(req.copy(gearStyle = style, tick = "flash"))
            val data = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
            assertEquals("top-rope", data["climbedGearStyle"]!!.jsonPrimitive.content)
            assertEquals("flash", data["tick"]!!.jsonPrimitive.content)
            server.shutdown()
        }
        assertEquals("via-ferrata", TickTypes.apiGearStyle("ferrata"))
        assertEquals("sport", TickTypes.apiGearStyle("Sport"))
    }

    @Test
    fun tickPostsOfficialSchemaAndKeepsTheAscent() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"777","uri":"/ascent/777"}}""")); start() }
        val dir = Files.createTempDirectory("logbook").toFile()
        val repo = repo(server, dir)

        assertEquals("Logged as ascent 777", repo.tick(req))
        val r = server.takeRequest()
        assertEquals("/api/ascent/create?cookieAuth=1", r.path)
        val data = json.parseToJsonElement(r.body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals("9001", data["account"]!!.jsonPrimitive.content)
        assertEquals("555", data["node"]!!.jsonPrimitive.content)
        assertEquals("2", data["version"]!!.jsonPrimitive.content)
        assertEquals("redpoint", data["tick"]!!.jsonPrimitive.content)
        assertEquals("sport", data["climbedGearStyle"]!!.jsonPrimitive.content)
        assertEquals("2026-10-20", data["date"]!!.jsonPrimitive.content)
        assertEquals("pumpy", data["comment"]!!.jsonPrimitive.content)

        val mine = repo.ascentsOf(555).single()
        assertEquals(777L, mine.id)
        assertEquals("Redpoint", mine.tickName)
        assertTrue(repo.outbox.value.isEmpty())
        // Persisted: a fresh instance over the same directory sees it.
        assertEquals(777L, repo(server, dir).ascents.value.single().id)
        server.shutdown()
    }

    @Test
    fun dryRunValidatesWithoutStoring() = runBlocking {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(202).setBody("""{"data":{"node":555}}""").addHeader("Content-Type", "application/json")); start() }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        val msg = repo.tick(req, dryRun = true)
        assertEquals("/api/ascent/create/validate?cookieAuth=1", server.takeRequest().path)
        assertTrue(msg, msg.contains("accept"))
        assertTrue(repo.ascents.value.isEmpty())
        server.shutdown()
    }

    @Test
    fun offlineTickWaitsInOutboxAndIsSentOnRetry() = runBlocking {
        val dir = Files.createTempDirectory("logbook").toFile()
        val down = MockWebServer().apply { start(); shutdown() }
        val repo = repo(down, dir)
        try {
            repo.tick(req)
            fail("expected IOException")
        } catch (e: IOException) {
            // expected: parked
        }
        assertEquals(1, repo.outbox.value.size)

        val up = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"778"}}""")); start() }
        val later = repo(up, dir)
        assertEquals("outbox survives restart", 1, later.outbox.value.size)
        assertEquals(1, later.retryOutbox())
        assertTrue(later.outbox.value.isEmpty())
        assertEquals(778L, later.ascents.value.single().id)
        assertEquals("/api/ascent/create?cookieAuth=1", up.takeRequest().path)
        up.shutdown()
    }

    @Test
    fun refreshPagesUntilAShortPage() = runBlocking {
        val a = { id: Int -> """{"id":"$id","date":"2026-10-1$id","tick":{"label":"flash","name":"Flash"},"route":{"id":"${500 + id}","name":"R$id","grade":"20","gradeBand":3}}""" }
        val server = MockWebServer().apply {
            enqueue(ok("""{"data":[${a(1)},${a(2)}]}"""))
            enqueue(ok("""{"data":[${a(3)}]}"""))
            start()
        }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile(), pageSize = 2)
        assertEquals(3, repo.refresh())
        assertEquals(2, server.requestCount)
        val r1 = server.takeRequest()
        assertTrue(r1.path!!.startsWith("/api/climber/id/me/ascents?cookieAuth=1"))
        assertTrue(r1.path!!.contains("page=1") && r1.path!!.contains("page-size=2"))
        assertEquals(listOf(501L, 502L, 503L), repo.ascents.value.map { it.routeId })
        assertEquals(5000L, repo.refreshedAt.value)
        server.shutdown()
    }

    @Test
    fun parserCopesWithBothKnownShapes() {
        val nested = json.parseToJsonElement(
            """{"data":[{"id":"1","date":"2026-10-20","tick":{"label":"redpoint","name":"Redpoint"},"climbedGearStyle":"sport","markdown":"nice",
                 "route":{"id":"555","name":"X","grade":"25","gradeBand":4,"ancestors":[{"name":"Victoria"},{"name":"Arapiles"}]}}]}""",
        ) as JsonObject
        val a = AscentParser.parseList(nested).single()
        assertEquals(555L, a.routeId)
        assertEquals("X", a.routeName)
        assertEquals("25", a.grade)
        assertEquals("redpoint", a.tick)
        assertEquals("Arapiles", a.cragName)
        assertEquals("nice", a.comment)

        val flat = json.parseToJsonElement(
            """{"data":{"ascents":[{"id":2,"epoch":1792454400,"tick":"flash","node":"556","label":"Y","grade":{"grade":"6a"},"comment":"c"}]}}""",
        ) as JsonObject
        val b = AscentParser.parseList(flat).single()
        assertEquals(556L, b.routeId)
        assertEquals("Y", b.routeName)
        assertEquals("6a", b.grade)
        assertEquals("Flash", b.tickName)
        assertNotNull(b.date)
        assertEquals("2026-10-20", b.date)
    }

    private fun sample(name: String) =
        listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name")).first { it.exists() }.readText()

    @Test
    fun attemptsAndProposedGradeGoThroughTheGradeParser() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok(sample("grade_parse_reply.json")))
            enqueue(ok("""{"ok":{"ascentID":"779"}}"""))
            start()
        }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        repo.tick(req.copy(attempts = 3, proposedGrade = " 23 ", gradeContext = "AU", gradeStyle = "Free"))
        val parse = server.takeRequest()
        assertEquals("/api/grade?cookieAuth=1", parse.path)
        val asked = json.parseToJsonElement(parse.body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals("23", asked["text"]!!.jsonPrimitive.content)
        assertEquals("AU", asked["context"]!!.jsonPrimitive.content)
        assertEquals("Free", asked["style"]!!.jsonPrimitive.content)
        val data = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals("3", data["numberAttempts"]!!.jsonPrimitive.content)
        assertEquals("AU", data["gradeSystem"]!!.jsonPrimitive.content)
        assertEquals("23", data["grade"]!!.jsonPrimitive.content)
        assertEquals(null, data["altGrade"])
        server.shutdown()
    }

    @Test
    fun plainTickSendsNeitherAttemptsNorGrade() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"780"}}""")); start() }
        repo(server, Files.createTempDirectory("logbook").toFile()).tick(req)
        val data = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals(1, server.requestCount)
        assertTrue(data["numberAttempts"] == null && data["grade"] == null && data["gradeSystem"] == null)
        server.shutdown()
    }

    @Test
    fun unknownGradeIsRejectedBeforeAnythingIsSent() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok("""{"data":{"components":{},"context":"AU","grade":"zz","input":"zz","score":null}}"""))
            start()
        }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        try {
            repo.tick(req.copy(proposedGrade = "zz", gradeContext = "AU"))
            fail("expected a rejection")
        } catch (e: LogbookRepository.GradeNotRecognised) {
            assertTrue(e.message!!, e.message!!.contains("zz") && e.message!!.contains("AU"))
        }
        assertEquals(1, server.requestCount)
        assertTrue(repo.outbox.value.isEmpty())
        server.shutdown()
    }

    @Test
    fun dryRunReportsTheServersFieldErrors() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(202).setBody("""{"data":{"grade":"zz","gradeSystem":"FR"},"error":{"gradeError":"invalid grade"}}""").addHeader("Content-Type", "application/json"))
            start()
        }
        val msg = repo(server, Files.createTempDirectory("logbook").toFile()).tick(req, dryRun = true)
        assertTrue(msg, msg.startsWith("Rejected") && msg.contains("grade: invalid grade"))
        server.shutdown()
    }

    @Test
    fun ascentReadBackCarriesAttemptsAndProposedGrade() {
        val a = AscentParser.parse(json.parseToJsonElement(sample("ascent_id_reply.json")).jsonObject["data"]!!.jsonObject)!!
        assertEquals(3, a.attempts)
        assertEquals("7a", a.grade)
        assertEquals("7a", a.proposedGrade)
        assertEquals("redpoint", a.tick)
    }

    @Test
    fun plainAscentHasNoProposedGrade() {
        val a = AscentParser.parse(json.parseToJsonElement("""{"id":1,"tick":{"label":"attempt"},"route":{"id":5,"name":"R","grade":"21"}}""").jsonObject)!!
        assertEquals("21", a.grade)
        assertEquals(null, a.proposedGrade)
    }

    @Test
    fun updateSendsTheAscentIdAndClearsEmptyFields() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok("""{"ok":{"ascentID":"777"}}"""))
            enqueue(MockResponse().setResponseCode(202).setBody("""{"data":{"ascent":777}}""").addHeader("Content-Type", "application/json"))
            start()
        }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        repo.tick(req.copy(attempts = 3))
        server.takeRequest()

        assertEquals("Ascent updated.", repo.update(777, req.copy(tick = "onsight", date = "2026-10-21", comment = " ", attempts = null)))
        val r = server.takeRequest()
        assertEquals("/api/ascent/update?cookieAuth=1", r.path)
        val data = json.parseToJsonElement(r.body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals("777", data["ascent"]!!.jsonPrimitive.content)
        assertEquals("onsight", data["tick"]!!.jsonPrimitive.content)
        assertEquals("2026-10-21", data["date"]!!.jsonPrimitive.content)
        assertEquals("sport", data["climbedGearStyle"]!!.jsonPrimitive.content)
        // Clearing: null for comment and grade, "" for attempts; create's own keys are not sent.
        assertEquals(JsonNull, data["comment"])
        assertEquals("", data["numberAttempts"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, data["grade"])
        assertEquals(JsonNull, data["gradeSystem"])
        assertEquals(JsonNull, data["altGrade"])
        assertTrue("account" !in data && "node" !in data && "version" !in data)

        val mine = repo.ascentsOf(555).single()
        assertEquals("onsight", mine.tick)
        assertEquals("2026-10-21", mine.date)
        assertEquals(null, mine.comment)
        assertEquals(null, mine.attempts)
        server.shutdown()
    }

    @Test
    fun editingAnOutboxTickReplacesItInPlaceWithoutSending() = runBlocking {
        val dir = Files.createTempDirectory("logbook").toFile()
        val down = MockWebServer().apply { start(); shutdown() }
        val repo = repo(down, dir)
        try { repo.tick(req) } catch (e: IOException) { /* parked */ }
        try { repo.tick(req.copy(routeId = 556, createdAt = 2000L)) } catch (e: IOException) { /* parked */ }

        repo.editPending(1000L, req.copy(tick = "flash", comment = "fixed", createdAt = 99L, error = "stale"))
        val pending = repo.outbox.value
        assertEquals(listOf(1000L, 2000L), pending.map { it.createdAt })
        assertEquals("flash", pending[0].tick)
        assertEquals("fixed", pending[0].comment)
        assertEquals(null, pending[0].error)
        assertEquals("redpoint", pending[1].tick)
        // Persisted, and still a create when it finally goes out.
        assertEquals("flash", repo(down, dir).outbox.value[0].tick)
        assertEquals(null, pending[0].ascentId)
    }

    @Test
    fun offlineEditIsParkedAndRetrySendsAnUpdate() = runBlocking {
        val dir = Files.createTempDirectory("logbook").toFile()
        val up = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"777"}}""")); start() }
        repo(up, dir).tick(req)
        up.shutdown()

        val offline = repo(MockWebServer().apply { start(); shutdown() }, dir)
        try {
            offline.update(777, req.copy(tick = "flash"))
            fail("expected IOException")
        } catch (e: IOException) {
            // expected: parked
        }
        // A second offline edit of the same ascent replaces the first; the local copy still shows the server's state.
        try { offline.update(777, req.copy(tick = "onsight", createdAt = 3000L)) } catch (e: IOException) { /* parked */ }
        assertEquals(1, offline.outbox.value.size)
        assertEquals(777L, offline.outbox.value.single().ascentId)
        assertEquals("redpoint", offline.ascentsOf(555).single().tick)

        val later = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(202).setBody("""{"data":{"ascent":777}}""").addHeader("Content-Type", "application/json"))
            start()
        }
        val retry = repo(later, dir)
        assertEquals(1, retry.retryOutbox())
        val r = later.takeRequest()
        assertEquals("/api/ascent/update?cookieAuth=1", r.path)
        val data = json.parseToJsonElement(r.body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals("777", data["ascent"]!!.jsonPrimitive.content)
        assertEquals("onsight", data["tick"]!!.jsonPrimitive.content)
        assertTrue(retry.outbox.value.isEmpty())
        assertEquals("onsight", retry.ascentsOf(555).single().tick)
        later.shutdown()
    }

    @Test
    fun partnersGoOutAsWithAndComeBack() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok("""{"data":{"node":555,"with":"@a_john, Dave"},"ok":{"ascentID":"790"}}"""))
            enqueue(MockResponse().setResponseCode(202).setBody("""{"data":{"ascent":790}}""").addHeader("Content-Type", "application/json"))
            start()
        }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        repo.tick(req.copy(partners = "@a_john, Dave"))
        val created = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals("@a_john, Dave", created["with"]!!.jsonPrimitive.content)
        assertEquals("@a_john, Dave", repo.ascentsOf(555).single().partners)

        repo.update(790, req.copy(partners = ""))
        val updated = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals(JsonNull, updated["with"])
        assertEquals(null, repo.ascentsOf(555).single().partners)
        server.shutdown()
    }

    @Test
    fun plainTickSendsNoWith() = runBlocking {
        val server = MockWebServer().apply { enqueue(ok("""{"ok":{"ascentID":"791"}}""")); start() }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        repo.tick(req)
        val data = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["data"]!!.jsonObject
        assertEquals(null, data["with"])
        assertEquals(null, repo.ascentsOf(555).single().partners)
        server.shutdown()
    }

    @Test
    fun partnerSuggestionsMergeFollowedAndPastPartnersOnce() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(ok("""{"data":[{"id":"7994503","login":"a_john","name":"A John","grouplabel":"follow"}]}"""))
            enqueue(ok("""{"data":[{"id":"7994503","login":"a_john","name":"A John"},{"id":"42","login":"dave","name":"Dave"}]}"""))
            start()
        }
        val repo = repo(server, Files.createTempDirectory("logbook").toFile())
        assertEquals(listOf("a_john", "dave"), repo.partnerSuggestions().map { it.login })
        assertEquals(listOf("a_john", "dave"), repo.partnerSuggestions().map { it.login })
        assertEquals(2, server.requestCount)
        val first = server.takeRequest().path!!
        assertTrue(first, first.startsWith("/api/lookup/climber?cookieAuth=1") && "mode=follow" in first && "search=" in first)
        assertTrue("mode=history" in server.takeRequest().path!!)
        server.shutdown()
    }

    @Test
    fun listingItemCarriesPartners() {
        val a = AscentParser.parse(json.parseToJsonElement("""{"id":1,"tick":{"label":"dog"},"route":{"id":5,"name":"R"},"with":"@a_john, Dave Smith"}""").jsonObject)!!
        assertEquals("@a_john, Dave Smith", a.partners)
    }
}

class LogbookLiveShapesTest {
    private val json = ApiFactory.json
    private fun sample(name: String) =
        listOf(File("../docs/api-samples/$name"), File("docs/api-samples/$name")).first { it.exists() }.readText()

    @Test
    fun parsesTheRealAscentsListing() {
        val a = AscentParser.parseList(json.parseToJsonElement(sample("account_me_ascents.json")) as JsonObject).single()
        assertEquals(14955329028L, a.id)
        assertEquals(6135933117L, a.routeId)
        assertEquals("test route 4", a.routeName)
        assertEquals("2026-10-20", a.date)
        assertEquals("redpoint", a.tick)
        assertEquals("Red point", a.tickName)
        assertEquals("sport", a.gearStyle)
        assertEquals("app test tick, will be deleted", a.comment)
        assertEquals("test sector without area above", a.cragName)
        assertEquals(0, a.gradeBand)
    }

    @Test
    fun createReplyCarriesTheAscentId() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(202).setBody(sample("ascent_create_reply.json")).addHeader("Content-Type", "application/json"))
            enqueue(MockResponse().setResponseCode(202).setBody(sample("ascent_delete_reply.json")).addHeader("Content-Type", "application/json"))
            start()
        }
        val store = InMemoryStore().apply { put(AuthRepository.KEY, json.encodeToString(Account.serializer(), Account(14955324951, "Tester", 1))) }
        val jar = PersistentCookieJar(store, PersistentCookieJar.AUTH_KEY)
        val api = ApiFactory.retrofit(ApiFactory.authHttp(jar, debug = false), server.url("/").toString()).create(AuthApi::class.java)
        val repo = LogbookRepository(api, AuthRepository(api, jar, store, json), Files.createTempDirectory("logbook").toFile(), json)
        val req = TickRequest(6135933117, "test route 4", "sport", "redpoint", "2026-10-20", "x", null, 0, null, 1L)
        assertEquals("Logged as ascent 14955329028", repo.tick(req))
        server.takeRequest()
        repo.delete(14955329028)
        val del = server.takeRequest()
        assertEquals("/api/ascent/delete?cookieAuth=1", del.path)
        assertEquals("""{"data":{"ascent":14955329028}}""", del.body.readUtf8())
        assertTrue(repo.ascents.value.isEmpty())
        server.shutdown()
    }

    @Test
    fun validateEchoIsAcceptance() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(202).setBody(sample("ascent_validate_reply.json")).addHeader("Content-Type", "application/json"))
            start()
        }
        val store = InMemoryStore().apply { put(AuthRepository.KEY, json.encodeToString(Account.serializer(), Account(1, "T", 1))) }
        val jar = PersistentCookieJar(store, PersistentCookieJar.AUTH_KEY)
        val api = ApiFactory.retrofit(ApiFactory.authHttp(jar, debug = false), server.url("/").toString()).create(AuthApi::class.java)
        val repo = LogbookRepository(api, AuthRepository(api, jar, store, json), Files.createTempDirectory("logbook").toFile(), json)
        val msg = repo.tick(TickRequest(6135933117, "r", "sport", "redpoint", "2026-10-20", "", null, 0, null, 1L), dryRun = true)
        assertTrue(msg, msg.contains("accept"))
        server.shutdown()
    }
}
