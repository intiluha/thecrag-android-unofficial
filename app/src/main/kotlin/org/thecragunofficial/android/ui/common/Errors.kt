package org.thecragunofficial.android.ui.common

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** One readable line per failure; network problems get wording the user can act on. */
fun Throwable.describe(): String = when (this) {
    is HttpException -> when (code()) {
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
