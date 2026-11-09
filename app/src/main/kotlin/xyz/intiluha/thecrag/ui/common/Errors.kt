package xyz.intiluha.thecrag.ui.common

import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.dto.ApiError
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** One readable line per failure; network problems get wording the user can act on. */
fun Throwable.describe(): String = when (this) {
    is AuthRepository.LoginFailed -> message ?: "Login failed."
    is HttpException -> apiError()?.let { e ->
        when {
            e.contains("minimum level authweb") -> "Not logged in, or the theCrag session has expired. Log in again."
            e.contains("insufficient access") -> "theCrag shows this only to logged-in accounts (since November 2026)."
            else -> "theCrag: $e"
        }
    } ?: when (code()) {
        400 -> "theCrag refused the request (HTTP 400). The session may need refreshing; try again."
        403 -> "Blocked by theCrag's edge (HTTP 403)."
        404 -> "Not found on theCrag (HTTP 404)."
        429 -> "Too many requests; wait a moment."
        502, 503, 504 -> "theCrag is temporarily unavailable (HTTP ${code()})."
        else -> "HTTP ${code()} from theCrag."
    }
    is UnknownHostException -> "Offline, and this page has not been opened before."
    is ConnectException -> "Could not reach theCrag. Offline?"
    is SocketTimeoutException -> "theCrag did not answer in time."
    is IOException -> "Network error: ${message ?: javaClass.simpleName}"
    else -> message ?: javaClass.simpleName
}

/**
 * theCrag's API errors are JSON `{"error": "...", "errorLabel": "..."}` with HTTP 400.
 * Peeks at the body instead of consuming it: [describe] and [needsLogin] both read it.
 */
private fun HttpException.apiError(): String? = try {
    response()?.errorBody()?.source()?.peek()?.readUtf8()
        ?.let { ApiFactory.json.decodeFromString(ApiError.serializer(), it) }
        ?.error?.takeIf { it.isNotBlank() }
} catch (e: Exception) {
    null
}

/** True when theCrag refused for lack of a logged-in session, so logging in (again) is the fix. */
fun Throwable.needsLogin(): Boolean =
    this is HttpException && apiError()?.let { it.contains("insufficient access") || it.contains("minimum level authweb") } == true
