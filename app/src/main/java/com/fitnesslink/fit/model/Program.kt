package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.TolerantAdapterFactory.Companion.optString
import com.fitnesslink.fit.model.api.AuditFields
import com.fitnesslink.fit.model.api.EntityID
import com.fitnesslink.fit.model.api.ProgramContributor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter

@JsonAdapter(Program.Adapter::class)
data class Program(
    val id: String = "",
    val name: String = "",
    val time: String = "",
    val location: String = "",
    /** Legacy single level — mirrors `trainingLevels.first()` (or ""). */
    val trainingLevel: String = "",
    /** The editor's explicit picks, or the levels derived from the scheduled workouts. */
    val trainingLevels: List<String> = emptyList(),
    /** Server flags: whether `location` / `trainingLevels` are explicit rather than derived. */
    val hasExplicitLocation: Boolean = false,
    val hasExplicitTrainingLevels: Boolean = false,
    val description: String = "",

    // MARK: - API-aligned fields

    val statusId: EntityID? = null,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    /** Pre-resolved SAS URLs from the backend payload. */
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val splitId: EntityID? = null,
    val weeks: Int? = null,
    val estimatedTime: Int? = null,
    val showInLibrary: Boolean = true,
    val preBuiltTemplateId: EntityID? = null,
    val audit: AuditFields? = null,

    /** Sharing flags from /programs/list-view + /programs/{id}. Live-only. */
    val isSharedWithMe: Boolean = false,
    val sharedByUserId: EntityID? = null,

    /** Equipment / anatomy ids derived server-side from the program's workouts' movements. Live-only. */
    val equipmentIds: List<EntityID> = emptyList(),
    val anatomyIds: List<EntityID> = emptyList(),
    /** Override-with-fallback tags contract. Detail payloads only; absent → false. */
    val hasExplicitEquipment: Boolean = false,
    val hasExplicitAnatomy: Boolean = false,

    /** "About the Coach" card. Detail payload only. Live-only. */
    val contributor: ProgramContributor? = null,

    /** "Who this program is for" paragraph + optional "Best for" bullets. Detail payload; cached. */
    val whoIsFor: String = "",
    val bestFor: List<String> = emptyList(),
    /** Phase name per week, index 0 = Week 1 ("Base", "Build", "Peak", …). */
    val weekPhases: List<String> = emptyList(),

    /** Server-order rank from the last list-view snapshot (local only). */
    val sortIndex: Int? = null,
    /** Local favorite flag (local only). */
    val isFavorite: Boolean = false
) {
    val createdAt get() = audit?.timestamp

    /**
     * Back-compat: older payloads / cache rows carry only the single level
     * string; the paged list sends the detail-only fields as null.
     */
    class Adapter : TolerantAdapterFactory<Program>(Program::class.java) {
        override fun preprocess(raw: JsonObject) {
            val level = raw.optString("trainingLevel") ?: ""
            val levels = raw.get("trainingLevels")?.takeIf { it.isJsonArray }?.asJsonArray
            if ((levels == null || levels.size() == 0) && level.isNotEmpty()) {
                raw.add("trainingLevels", JsonArray().apply { add(level) })
            } else if (level.isEmpty() && levels != null && levels.size() > 0) {
                raw.addProperty("trainingLevel", levels[0].asString)
            }
            raw.get("contributor")?.let { if (!it.isJsonObject) raw.remove("contributor") }
            raw.get("bestFor")?.let { if (!it.isJsonArray) raw.remove("bestFor") }
            raw.get("weekPhases")?.let { if (!it.isJsonArray) raw.remove("weekPhases") }
            raw.get("whoIsFor")?.let { if (!it.isJsonPrimitive) raw.remove("whoIsFor") }
        }
    }
}

/**
 * Body for `POST /programs` and `PUT /programs/{id}`: the program's writable
 * fields plus the optional explicit tag sets. `null` = leave unchanged
 * (omitted), `[]` = clear the explicit set, `[...]` = replace it.
 * `Program.equipmentIds` / `anatomyIds` are read-only projections and MUST
 * NOT be echoed back on a write.
 */
data class ProgramWriteRequest(
    val id: String,
    val name: String,
    val time: String,
    val location: String,
    val trainingLevel: String,
    val trainingLevels: List<String>,
    val description: String,
    val statusId: EntityID? = null,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    val splitId: EntityID? = null,
    val weeks: Int? = null,
    val estimatedTime: Int? = null,
    val showInLibrary: Boolean = true,
    val preBuiltTemplateId: EntityID? = null,
    val equipmentIds: List<String>? = null,
    val anatomyIds: List<String>? = null
) {
    constructor(program: Program, equipmentIds: List<String>? = null, anatomyIds: List<String>? = null) : this(
        id = program.id, name = program.name, time = program.time, location = program.location,
        trainingLevel = program.trainingLevel, trainingLevels = program.trainingLevels,
        description = program.description, statusId = program.statusId, contributorId = program.contributorId,
        imageId = program.imageId, thumbnailId = program.thumbnailId, splitId = program.splitId,
        weeks = program.weeks, estimatedTime = program.estimatedTime, showInLibrary = program.showInLibrary,
        preBuiltTemplateId = program.preBuiltTemplateId, equipmentIds = equipmentIds, anatomyIds = anatomyIds
    )
}
