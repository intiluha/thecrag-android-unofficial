package org.thecragunofficial.android.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * theCrag emits numbers sometimes as JSON numbers and sometimes as strings
 * (`"id":"12873151"`, `childIDs:[11764447,"9091907751"]`). These serializers
 * accept either. Ids exceed 2^31, so they are Long.
 */
private fun Decoder.primitiveContent(): String? {
    val json = this as? JsonDecoder ?: return decodeString()
    val el = json.decodeJsonElement()
    return (el as? JsonPrimitive)?.contentOrNull
}

object LenientLong : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long =
        decoder.primitiveContent()?.trim()?.toDoubleOrNull()?.toLong() ?: 0L
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

@OptIn(ExperimentalSerializationApi::class)
object LenientLongOrNull : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLongOrNull", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long? =
        decoder.primitiveContent()?.trim()?.toDoubleOrNull()?.toLong()
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

@OptIn(ExperimentalSerializationApi::class)
object LenientIntOrNull : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientIntOrNull", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int? =
        decoder.primitiveContent()?.trim()?.toDoubleOrNull()?.toInt()
    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/** Any primitive (number, string, bool) as its string content; objects/arrays become null. */
@OptIn(ExperimentalSerializationApi::class)
object LenientStringOrNull : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientStringOrNull", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? = decoder.primitiveContent()
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** `[27,"m"]` / `["18","m"]` style value+unit pairs. */
fun JsonElement?.asMeasure(): Pair<Double, String>? {
    val arr = this as? JsonArray ?: return null
    if (arr.size < 2) return null
    val v = arr[0].jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: return null
    val u = arr[1].jsonPrimitive.contentOrNull ?: ""
    return v to u
}

fun JsonElement?.asPrimitiveString(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
