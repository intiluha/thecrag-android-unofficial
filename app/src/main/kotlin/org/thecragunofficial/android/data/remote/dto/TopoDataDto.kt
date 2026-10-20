package org.thecragunofficial.android.data.remote.dto

import kotlinx.serialization.Serializable

/** One entry of the `data-topodata` JSON attribute on a `div.phototopo` in page HTML. */
@Serializable
data class TopoDataEntryDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    val type: String = "",            // "route" | "area"
    @Serializable(with = LenientStringOrNull::class) val num: String? = null,
    val grade: String? = null,
    @Serializable(with = LenientStringOrNull::class) val `class`: String? = null,   // "gb4"
    val name: String = "",
    val stars: String? = null,        // "★★"
    val style: String? = null,
    val points: String = "",
    val url: String? = null,
)
