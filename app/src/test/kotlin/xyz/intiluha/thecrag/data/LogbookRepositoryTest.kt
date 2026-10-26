package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
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
