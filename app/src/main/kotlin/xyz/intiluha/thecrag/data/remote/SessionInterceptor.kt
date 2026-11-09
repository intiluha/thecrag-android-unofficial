package xyz.intiluha.thecrag.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

const val THECRAG_BASE = "https://www.thecrag.com/"
const val IMAGE_BASE = "https://image.thecrag.com/"

/**
 * A realistic desktop User-Agent: Cloudflare in front of thecrag.com rejects
 * curl's default UA and the bare "Mozilla/5.0" with 403.
 */
const val USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

/** Adds the headers that unlock the web UI's access levels. Cookies come from the client's CookieJar. */
class SessionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Referer", THECRAG_BASE + "en/climbing/world")
            .header("Accept", "application/json, text/html;q=0.5, */*;q=0.1")
            .build()
        return chain.proceed(req)
    }
}

/**
 * The API answers HTTP 400 `insufficient-api-permissions` when the session cookie
 * is missing or expired. This interceptor fetches `/home` to get a fresh cookie
 * and retries the call once. It also bootstraps proactively on the very first
 * API call when no session cookie is stored yet. A failed bootstrap (no network)
 * is ignored so the call itself can still be answered from the cache.
 *
 * Since 2026-11 the node endpoints answer anonymous sessions with that same error,
 * so browsing needs the logged-in session: the browse client shares the cookie
 * jar with the login client, and while an account is logged in a permission
 * error is returned as is instead of being "fixed" with an anonymous session.
 */
class SessionRecoveryInterceptor(
    private val cookies: PersistentCookieJar,
    /** True while an account is logged in: a permission error then means the login expired, and an anonymous session would not help. */
    private val loggedIn: () -> Boolean = { false },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isApiCall(request)) return chain.proceed(request)

        if (!cookies.hasSession() && !request.cacheControl.onlyIfCached) bootstrap(chain)

        val first = chain.proceed(request)
        if (first.code != 400 || loggedIn() || !first.isPermissionError()) return first
        first.close()
        bootstrap(chain)
        return chain.proceed(request)
    }

    private fun isApiCall(r: Request) = r.url.encodedPath.startsWith("/api/")

    private fun bootstrap(chain: Interceptor.Chain) {
        val home = Request.Builder().url(THECRAG_BASE + "home").get().build()
        try {
            chain.proceed(home).use { }
        } catch (e: IOException) {
            // Offline: let the real request fall through to the cache.
        }
    }

    private fun Response.isPermissionError(): Boolean {
        val peek = peekBody(4096).string()
        return peek.contains("insufficient-api-permissions")
    }
}
