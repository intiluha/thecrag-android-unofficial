package org.thecragunofficial.android.ui.common

import retrofit2.HttpException
import java.io.IOException

fun Throwable.describe(): String = when (this) {
    is HttpException -> when (code()) {
        400 -> "theCrag refused the request (HTTP 400). The session may need refreshing; try again."
        403 -> "Blocked by theCrag's edge (HTTP 403)."
        429 -> "Too many requests; wait a moment."
        else -> "HTTP ${code()} from theCrag."
    }
    is IOException -> "Network error: ${message ?: javaClass.simpleName}"
    else -> message ?: javaClass.simpleName
}
