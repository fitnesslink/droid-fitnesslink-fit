package com.fitnesslink.fit.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Gson support shared by the wire models and the local SQLite cache.
 *
 * iOS decodes every model with `decodeIfPresent(...) ?? default`, so a key
 * that is missing OR explicitly `null` on the wire lands on the struct
 * default. Gson applies Kotlin defaults for *missing* keys (it calls the
 * no-arg constructor every all-defaults data class has) but writes an
 * explicit `null` straight into the field — even a non-null `String` /
 * `List` — which then explodes on the first `.isEmpty()` or `copy()`.
 *
 * [TolerantAdapterFactory] closes that gap: it strips `null` members from
 * the object before delegating to the reflective adapter, then runs a
 * per-type [preprocess] (wire-shape fixes on the raw JSON) and
 * [postprocess] (iOS `init(from:)` back-compat rules on the decoded value).
 * Attach it with `@JsonAdapter(Foo.Adapter::class)` on the data class so it
 * applies to Retrofit's Gson and the cache Gson alike.
 */
abstract class TolerantAdapterFactory<T : Any>(private val target: Class<T>) : TypeAdapterFactory {

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any?> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        if (type.rawType != target) return null
        val delegate = gson.getDelegateAdapter(this, type) as TypeAdapter<T>
        val elementAdapter = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<R>() {
            override fun write(out: JsonWriter, value: R?) {
                if (value == null) { out.nullValue(); return }
                val tree = delegate.toJsonTree(value as T)
                if (tree.isJsonObject) postwrite(tree.asJsonObject)
                elementAdapter.write(out, tree)
            }

            override fun read(reader: JsonReader): R? {
                val element = elementAdapter.read(reader)
                if (element == null || element.isJsonNull) return null
                if (!element.isJsonObject) return delegate.fromJsonTree(element) as R
                val obj = element.asJsonObject
                stripNulls(obj)
                preprocess(obj)
                val decoded = delegate.fromJsonTree(obj)
                return postprocess(obj, decoded) as R
            }
        }
    }

    /** Mutate the raw object before decoding (rename keys, coerce shapes). */
    open fun preprocess(raw: JsonObject) {}

    /** Apply decode-time defaults / inference on the decoded value. */
    open fun postprocess(raw: JsonObject, decoded: T): T = decoded

    /** Mutate the encoded object before it is written (wire-shape fixes on output). */
    open fun postwrite(tree: JsonObject) {}

    companion object {
        fun stripNulls(obj: JsonObject) {
            val nullKeys = obj.entrySet().filter { it.value == null || it.value is JsonNull }.map { it.key }
            nullKeys.forEach { obj.remove(it) }
        }

        fun JsonObject.optInt(key: String): Int? =
            get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

        fun JsonObject.optString(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString

        fun JsonObject.optBool(key: String): Boolean? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
    }
}

/**
 * Gson instance for the local cache (`phasesJson`, sync payloads, queued
 * mutations). Dates serialize as ISO-8601 UTC so a cached payload decodes
 * with the same adapter that reads server payloads.
 */
object ModelGson {
    const val ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    val instance: Gson by lazy {
        GsonBuilder()
            .setDateFormat(ISO_DATE_TIME)
            .registerTypeAdapter(Date::class.java, LenientDateAdapter())
            .create()
    }

    /**
     * Server timestamps arrive with and without fractional seconds and
     * with `Z` or an explicit offset; cached rows store millis. Accept all
     * of them, write ISO-8601 UTC.
     */
    class LenientDateAdapter : TypeAdapter<Date>() {
        private val writer = SimpleDateFormat(ISO_DATE_TIME, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        private val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        /** Fractional seconds beyond millis (server sends 7 digits) are truncated. */
        private val fractionTrim = Regex("""(\.\d{3})\d+""")

        override fun write(out: JsonWriter, value: Date?) {
            if (value == null) out.nullValue() else synchronized(writer) { out.value(writer.format(value)) }
        }

        override fun read(reader: JsonReader): Date? {
            return when (reader.peek()) {
                com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); null }
                com.google.gson.stream.JsonToken.NUMBER -> Date(reader.nextLong())
                else -> parse(reader.nextString())
            }
        }

        fun parse(raw: String): Date? {
            val text = raw.trim().replace(fractionTrim, "$1")
            if (text.isEmpty()) return null
            text.toLongOrNull()?.let { return Date(it) }
            for (p in patterns) {
                val f = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                try { return f.parse(text) } catch (_: Exception) { }
            }
            return null
        }
    }
}
