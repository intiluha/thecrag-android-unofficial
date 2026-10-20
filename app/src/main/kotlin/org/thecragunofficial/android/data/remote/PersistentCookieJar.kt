package org.thecragunofficial.android.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Minimal key/value persistence so the jar can be unit-tested without Android. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class InMemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

/**
 * Keeps the thecrag.com cookies (ApacheSessionID, userstatus, acceptedterms, NavCtx)
 * across process restarts. Cookies are stored by name as their Set-Cookie string.
 */
class PersistentCookieJar(private val store: KeyValueStore, private val key: String = KEY) : CookieJar {
    private val host = "www.thecrag.com"
    private val url: HttpUrl = HttpUrl.Builder().scheme("https").host(host).build()
    private val cache = HashMap<String, Cookie>()

    init {
        store.get(key)?.lineSequence()?.filter { it.isNotBlank() }?.forEach { line ->
            Cookie.parse(url, line)?.let { cache[it.name] = it }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.endsWith("thecrag.com")) return
        for (c in cookies) cache[c.name] = c
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.endsWith("thecrag.com")) return emptyList()
        val now = System.currentTimeMillis()
        return cache.values.filter { it.expiresAt > now }
    }

    @Synchronized
    fun hasSession(): Boolean =
        cache["ApacheSessionID"]?.let { it.expiresAt > System.currentTimeMillis() } == true

    @Synchronized
    fun clear() { cache.clear(); persist() }

    private fun persist() {
        store.put(key, cache.values.joinToString("\n") { it.toString() })
    }

    companion object {
        const val KEY = "thecrag.cookies"
        /** The logged-in session lives in its own jar: browsing never sends it. */
        const val AUTH_KEY = "thecrag.auth.cookies"
    }
}
