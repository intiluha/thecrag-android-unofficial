package org.thecragunofficial.android.data.remote.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Defensive readers for replies whose exact shape is not documented. */
fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotBlank() }
fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
fun JsonObject.int(key: String): Int? = string(key)?.toDoubleOrNull()?.toInt()
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
