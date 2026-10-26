package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xyz.intiluha.thecrag.data.auth.AuthApi
import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.InMemoryStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import xyz.intiluha.thecrag.data.remote.authHttp

class AuthRepositoryTest {
    private val form = """<html><form action="/CIDS/cgi-bin/cids.cgi" method="post">
        <input name="D:Login" type="text" /><input type="hidden" name="D:CSRF" value="tok123" /></form></html>"""

    private fun repo(server: MockWebServer, store: InMemoryStore = InMemoryStore()): Pair<AuthRepository, InMemoryStore> {
        val jar = PersistentCookieJar(store, PersistentCookieJar.AUTH_KEY)
        val api = ApiFactory.retrofit(ApiFactory.authHttp(jar, debug = false), server.url("/").toString()).create(AuthApi::class.java)
        return AuthRepository(api, jar, store, ApiFactory.json, clock = { 42L }) to store
    }

    @Test
    fun loginPostsCsrfAndCredentialsThenConfirmsWithMe() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(form))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://www.thecrag.com/home"))
        server.enqueue(MockResponse().setBody("""{"data":{"id":"9001","name":"Tester","login":"alice"}}""").addHeader("Content-Type", "application/json"))
        server.start()
        val (repo, store) = repo(server)

        val account = repo.login("alice", "s3cret")
        assertEquals(9001L, account.id)
        assertEquals("Tester", account.name)
        assertEquals(42L, account.loggedInAt)
        assertEquals(3, server.requestCount)

        val r1 = server.takeRequest()
        assertTrue(r1.path!!.startsWith("/CIDS/cgi-bin/cids.cgi?"))
        assertTrue(r1.path!!.contains("13216"))
        val r2 = server.takeRequest()
        assertEquals("POST", r2.method)
        val body = r2.body.readUtf8()
        assertTrue(body, body.contains("D%3ACSRF=tok123"))
        assertTrue(body, body.contains("D%3ALogin=alice"))
        assertTrue(body, body.contains("D%3APassword=s3cret"))
        assertTrue(body, body.contains("State%3ALogin=Login"))
        val r3 = server.takeRequest()
        assertEquals("/api/account/id/me?cookieAuth=1", r3.path)

        // The account survives a restart; the password is nowhere.
        val (again, _) = repo(server, store)
        assertEquals(account, again.state.value)
        assertTrue(store.get(AuthRepository.KEY)!!.contains("Tester"))
        assertTrue(!store.get(AuthRepository.KEY)!!.contains("s3cret"))
        server.shutdown()
    }

    @Test
    fun rejectedLoginSurfacesTheSitesMessage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(form))
        server.enqueue(MockResponse().setBody("""<html><div class="alert alert-danger">
            Your username is not listed
          </div>$form</html>"""))
        server.start()
        val (repo, _) = repo(server)
        try {
            repo.login("nobody", "x")
            fail("expected LoginFailed")
        } catch (e: AuthRepository.LoginFailed) {
            assertEquals("Your username is not listed", e.message)
        }
        assertNull(repo.state.value)
        assertEquals(2, server.requestCount)
        server.shutdown()
    }
}
