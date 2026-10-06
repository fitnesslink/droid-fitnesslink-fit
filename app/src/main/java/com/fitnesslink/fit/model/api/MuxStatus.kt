package com.fitnesslink.fit.model.api

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Mirrors backend `MuxAssetStatus`. [UNKNOWN] is a forward-compat slot for
 * values the backend might add later — an unfamiliar string lands there
 * instead of failing the whole decode (iOS `MuxStatus(wire:)`).
 */
@JsonAdapter(MuxStatus.Adapter::class)
enum class MuxStatus(val wire: String) {
    NONE("None"),
    UPLOADING("Uploading"),
    PREPARING("Preparing"),
    READY("Ready"),
    ERRORED("Errored"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(raw: String?): MuxStatus? {
            if (raw == null) return null
            return entries.firstOrNull { it.wire == raw }
                ?: entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: UNKNOWN
        }
    }

    class Adapter : TypeAdapter<MuxStatus>() {
        override fun write(out: JsonWriter, value: MuxStatus?) {
            if (value == null) out.nullValue() else out.value(value.wire)
        }

        override fun read(reader: JsonReader): MuxStatus? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            return fromWire(reader.nextString())
        }
    }
}

/**
 * Detail payload returned by `GET /media/{id}`. Admin-only fields come back
 * null for non-admin callers — expected for the consumer flow.
 */
data class MediaAssetDetail(
    val mediaAssetId: String = "",
    val kind: String = "",
    val status: String = "",
    val ownerId: String? = null,
    val container: String? = null,
    val blobName: String? = null,
    val muxPlaybackId: String? = null,
    val muxAssetId: String? = null,
    val videoUrl: String? = null,
    val muxStatus: String = "",
    val muxDurationMs: Int? = null,
    val muxAspectRatio: String? = null,
    val muxErrors: List<String>? = null
) {
    val muxStatusEnum: MuxStatus get() = MuxStatus.fromWire(muxStatus) ?: MuxStatus.UNKNOWN

    val isTerminal: Boolean
        get() = when (muxStatusEnum) {
            MuxStatus.READY, MuxStatus.ERRORED -> true
            MuxStatus.NONE, MuxStatus.UPLOADING, MuxStatus.PREPARING, MuxStatus.UNKNOWN -> false
        }
}
