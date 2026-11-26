package xyz.intiluha.thecrag.ui.common

import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.remote.ApiFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            e.contains("insufficient access") -> "theCrag shows this only to logged-in accounts. Log in, or log in again if your session has expired."
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
 * theCrag's API errors are JSON with HTTP 400: `{"error": "...", "errorLabel": "..."}`, or, when a
 * write is rejected for its fields, `{"error": {"tickError": "reason", ...}}`; the latter reads as
 * `tick: reason`. Peeks at the body instead of consuming it: [describe] and [needsLogin] both read it.
 */
private fun HttpException.apiError(): String? = try {
    val error = response()?.errorBody()?.source()?.peek()?.readUtf8()
        ?.let { ApiFactory.json.parseToJsonElement(it) as? JsonObject }?.get("error")
    when (error) {
        is JsonObject -> error.entries.joinToString("; ") { (field, reason) ->
            "${field.removeSuffix("Error")}: ${(reason as? JsonPrimitive)?.content ?: reason}"
        }
        is JsonPrimitive -> error.content
        else -> null
    }?.takeIf { it.isNotBlank() }
} catch (e: Exception) {
    null
}

/** True when theCrag refused for lack of a logged-in session, so logging in (again) is the fix. */
fun Throwable.needsLogin(): Boolean =
    this is HttpException && apiError()?.let { it.contains("insufficient access") || it.contains("minimum level authweb") } == true
