package com.fitnesslink.fit.model.api

import com.fitnesslink.fit.model.TolerantAdapterFactory
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Date
import java.util.UUID

/** Server uses PascalCase enum names ("Program", "MealPlan"); decode case-insensitively. */
@JsonAdapter(ShareableContentKind.Adapter::class)
enum class ShareableContentKind(val serverName: String, val displayName: String) {
    PROGRAM("Program", "program"),
    WORKOUT("Workout", "workout"),
    MOVEMENT("Movement", "movement"),
    MEAL_PLAN("MealPlan", "meal plan");

    /** The `ContentTypeIds` GUID this kind maps to in the favorites table — null for non-favoritable kinds. */
    val contentTypeId: String?
        get() = when (this) {
            MOVEMENT -> ContentTypeIds.movement
            WORKOUT -> ContentTypeIds.workout
            PROGRAM -> ContentTypeIds.program
            MEAL_PLAN -> null
        }

    companion object {
        fun fromWire(raw: String?): ShareableContentKind? =
            raw?.let { r -> entries.firstOrNull { it.serverName.equals(r, true) || it.name.equals(r, true) } }
    }

    class Adapter : TypeAdapter<ShareableContentKind>() {
        override fun write(out: JsonWriter, value: ShareableContentKind?) {
            if (value == null) out.nullValue() else out.value(value.serverName)
        }

        override fun read(reader: JsonReader): ShareableContentKind? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            return fromWire(reader.nextString())
        }
    }
}

/** Recipient-side lifecycle of a share. Serialized PascalCase; unknown decodes as ACCEPTED (iOS). */
@JsonAdapter(ShareStatus.Adapter::class)
enum class ShareStatus(val serverName: String) {
    PENDING("Pending"), ACCEPTED("Accepted"), REJECTED("Rejected"), REMOVED("Removed");

    class Adapter : TypeAdapter<ShareStatus>() {
        override fun write(out: JsonWriter, value: ShareStatus?) {
            if (value == null) out.nullValue() else out.value(value.serverName)
        }

        override fun read(reader: JsonReader): ShareStatus? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            val raw = reader.nextString()
            return entries.firstOrNull { it.serverName.equals(raw, true) || it.name.equals(raw, true) } ?: ACCEPTED
        }
    }
}

@JsonAdapter(SharedContent.Adapter::class)
data class SharedContent(
    val id: EntityID = UUID(0L, 0L),
    val sharedByUserId: EntityID = UUID(0L, 0L),
    val sharedByUserName: String = "",
    val sharedWithUserId: EntityID = UUID(0L, 0L),
    val kind: ShareableContentKind = ShareableContentKind.WORKOUT,
    val contentId: EntityID = UUID(0L, 0L),
    val contentTitle: String = "",
    val imageId: EntityID? = null,
    val sharedAtUtc: Date = Date(),
    val status: ShareStatus = ShareStatus.PENDING,
    val audit: AuditFields? = null
) {
    class Adapter : TolerantAdapterFactory<SharedContent>(SharedContent::class.java)
}

data class ShareContentRequest(
    val contentId: String,
    val kind: ShareableContentKind,
    val sharedWithUserId: String
)

@JsonAdapter(UserSummary.Adapter::class)
data class UserSummary(
    val id: EntityID = UUID(0L, 0L),
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val profileImageId: EntityID? = null
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val displayName: String get() = fullName.ifEmpty { username }

    class Adapter : TolerantAdapterFactory<UserSummary>(UserSummary::class.java)
}
