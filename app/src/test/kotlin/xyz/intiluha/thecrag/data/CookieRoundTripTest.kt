package xyz.intiluha.thecrag.data

import okhttp3.Cookie
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.remote.InMemoryStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import java.util.concurrent.TimeUnit

/**
 * theCrag's login reply carries two `Set-Cookie` headers in its own date format
 * (single-digit day and time fields): one deleting the anonymous session, one with
 * the login session valid for about two years. The jar must keep the second one,
 * through persistence and reload, or the app would bootstrap an anonymous session
 * over the login the next time it asks whether it has one.
 */
class CookieRoundTripTest {
    private val url = HttpUrl.Builder().scheme("https").host("www.thecrag.com").build()
    private val api = HttpUrl.Builder().scheme("https").host("www.thecrag.com").addPathSegments("api/node/id/1").build()
    private val image = HttpUrl.Builder().scheme("https").host("image.thecrag.com").addPathSegments("x1200/aa/bb/cc").build()

    private val deleting = Cookie.parse(url, "ApacheSessionID=old; path=/; expires=Tue, 11-Nov-2025 7:6:52 GMT;")!!
    private val login = Cookie.parse(url, "ApacheSessionID=fresh; path=/; expires=Tue, 7-Nov-2028 7:6:52 GMT;")!!

    @Test
    fun theCragsDateFormatParsesAsPersistentYearsAhead() {
        assertTrue(login.persistent)
        val years = (login.expiresAt - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(365)
        assertTrue("expires in $years years", years in 1..3)
        assertTrue(deleting.expiresAt < System.currentTimeMillis())
    }

    @Test
    fun loginCookieSurvivesTheJarAndAReload() {
        val store = InMemoryStore()
        val jar = PersistentCookieJar(store)
        jar.saveFromResponse(url, listOf(deleting, login))
        assertTrue(jar.hasSession())
        assertEquals(listOf("fresh"), jar.loadForRequest(api).map { it.value })
        // Sent to the image host as well (same cookie jar, same site) and nowhere else.
        assertEquals(listOf("fresh"), jar.loadForRequest(image).map { it.value })
        assertTrue(jar.loadForRequest(HttpUrl.Builder().scheme("https").host("tiles.openfreemap.org").build()).isEmpty())

        val reloaded = PersistentCookieJar(store)
        assertTrue("persisted form must parse back as a live session", reloaded.hasSession())
        val back = reloaded.loadForRequest(api).single()
        assertEquals("fresh", back.value)
        assertEquals(login.expiresAt / 1000, back.expiresAt / 1000)
        assertFalse(back.expiresAt < System.currentTimeMillis())
    }

    @Test
    fun aDeletingCookieAfterTheLoginRemovesTheSession() {
        // If theCrag ever answers with an expired ApacheSessionID, the jar must honour it, not keep the old value.
        val jar = PersistentCookieJar(InMemoryStore())
        jar.saveFromResponse(url, listOf(login))
        jar.saveFromResponse(url, listOf(deleting))
        assertFalse(jar.hasSession())
    }
}
