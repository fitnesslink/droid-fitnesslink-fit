package com.fitnesslink.fit.classification

import android.util.Log
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.api.Anatomy
import com.fitnesslink.fit.model.api.ContentStatus
import com.fitnesslink.fit.model.api.Equipment
import com.fitnesslink.fit.model.api.FLTrainingLevel
import com.fitnesslink.fit.model.api.RestTypeId
import com.fitnesslink.fit.model.api.Rpe
import com.fitnesslink.fit.model.api.WorkoutSessionStatusId
import com.fitnesslink.fit.model.api.WorkoutTaskTypeId
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.ClassificationFallbackIds
import com.fitnesslink.fit.network.dto.RestTypeDto
import com.fitnesslink.fit.network.dto.WorkoutTaskTypeDto
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.components.TagOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * In-memory cache of the small, slow-changing classification lookup tables
 * (iOS `ClassificationStore`): anatomy + equipment taxonomies (also persisted
 * via `DatabaseManager.replaceAnatomy/replaceEquipment` so the chip strips
 * hydrate instantly on the next launch), and the name <-> id maps the write
 * paths resolve at save time (workout task types, rest types, content
 * statuses, session statuses). Lookup tables are enums server-side, so names
 * are stable and matched case-insensitively; when the cache is cold the
 * hard-coded fallback GUIDs in [ClassificationFallbackIds] / `LookupIds.kt`
 * answer instead.
 *
 * `refresh()` is best-effort and fetches each table once per process (pass
 * `force = true` to bypass). Failures leave the current cache intact.
 */
object ClassificationStore {
    private const val TAG = "ClassificationStore"

    private val _anatomy = MutableStateFlow<List<Anatomy>>(emptyList())
    val anatomy: StateFlow<List<Anatomy>> get() { ensureHydrated(); return _anatomy.asStateFlow() }

    private val _equipment = MutableStateFlow<List<Equipment>>(emptyList())
    val equipment: StateFlow<List<Equipment>> get() { ensureHydrated(); return _equipment.asStateFlow() }

    private val _trainingLevels = MutableStateFlow<List<FLTrainingLevel>>(emptyList())
    val trainingLevelsFlow: StateFlow<List<FLTrainingLevel>> = _trainingLevels.asStateFlow()

    private val _rpeScales = MutableStateFlow<List<Rpe>>(emptyList())
    val rpeScales: StateFlow<List<Rpe>> = _rpeScales.asStateFlow()

    private val _workoutTaskTypes = MutableStateFlow<List<WorkoutTaskTypeDto>>(emptyList())
    val workoutTaskTypes: StateFlow<List<WorkoutTaskTypeDto>> = _workoutTaskTypes.asStateFlow()

    private val _restTypes = MutableStateFlow<List<RestTypeDto>>(emptyList())
    val restTypes: StateFlow<List<RestTypeDto>> = _restTypes.asStateFlow()

    private val _contentStatuses = MutableStateFlow<List<ContentStatus>>(emptyList())
    val contentStatuses: StateFlow<List<ContentStatus>> = _contentStatuses.asStateFlow()

    // Lower-cased name -> lower-cased id (and the reverse where reads need it).
    @Volatile private var workoutTaskTypesByName: Map<String, String> = emptyMap()
    @Volatile private var contentStatusesByName: Map<String, String> = emptyMap()
    /** Stays empty until the backend exposes session statuses via a classification endpoint. */
    @Volatile private var sessionStatusesByName: Map<String, String> = emptyMap()
    @Volatile private var restTypesByName: Map<String, String> = emptyMap()
    @Volatile private var restTypeNamesById: Map<String, String> = emptyMap()

    private var hasFetchedTaskTypes = false
    private var hasFetchedContentStatuses = false
    private var hasFetchedAnatomy = false
    private var hasFetchedEquipment = false
    private var hasFetchedRestTypes = false
    private var hasFetchedTrainingLevels = false
    private var hasFetchedRpeScales = false

    @Volatile private var hydrated = false

    init {
        // Wire the enum's resolver hooks so `RestType.resolve(id)` / `RestType.id(...)`
        // consult the server cache before the hard-coded fallbacks.
        RestType.nameResolver = { id -> restTypeName(id) }
        RestType.idResolver = { name -> restTypeId(name) }
    }

    /**
     * Hydrate anatomy/equipment from the local DB so the chip strips have data on
     * first render, before the server fetch lands. Safe when the DB isn't
     * initialised (unit tests, previews) — it simply stays empty.
     */
    private fun ensureHydrated() {
        if (hydrated) return
        synchronized(this) {
            if (hydrated) return
            hydrated = true
            runCatching { DatabaseManager.allAnatomy() }.onSuccess { if (_anatomy.value.isEmpty()) _anatomy.value = it }
            runCatching { DatabaseManager.allEquipment() }.onSuccess { if (_equipment.value.isEmpty()) _equipment.value = it }
        }
    }

    // MARK: - Refresh

    /** Fetch every classification table that hasn't been fetched yet this process. */
    suspend fun refresh() = refresh(force = false)

    suspend fun refresh(force: Boolean) {
        ensureHydrated()
        val online = runCatching { NetworkMonitor.isConnected.value }.getOrDefault(true)
        if (!online) return
        fetchWorkoutTaskTypes(force)
        fetchContentStatuses(force)
        fetchAnatomy(force)
        fetchEquipment(force)
        fetchRestTypes(force)
        fetchTrainingLevels(force)
        fetchRpeScales(force)
    }

    private suspend fun fetchWorkoutTaskTypes(force: Boolean) {
        if (hasFetchedTaskTypes && !force) return
        val rows = runCatching { ApiClient.classificationApi.getWorkoutTaskTypes() }
            .onFailure { Log.w(TAG, "workout-task-types fetch failed: ${it.message}") }
            .getOrNull() ?: return
        seedWorkoutTaskTypes(rows)
        hasFetchedTaskTypes = true
    }

    private suspend fun fetchContentStatuses(force: Boolean) {
        if (hasFetchedContentStatuses && !force) return
        val rows = runCatching { ApiClient.classificationApi.getContentStatuses() }
            .onFailure { Log.w(TAG, "content-statuses fetch failed: ${it.message}") }
            .getOrNull() ?: return
        seedContentStatuses(rows)
        hasFetchedContentStatuses = true
    }

    private suspend fun fetchAnatomy(force: Boolean) {
        if (hasFetchedAnatomy && !force) return
        val rows = runCatching { ApiClient.classificationApi.getAnatomy() }
            .onFailure { Log.w(TAG, "anatomy fetch failed: ${it.message}") }
            .getOrNull() ?: return
        runCatching { DatabaseManager.replaceAnatomy(rows) }
            .onFailure { Log.w(TAG, "anatomy persist failed: ${it.message}") }
        _anatomy.value = rows
        hasFetchedAnatomy = true
    }

    private suspend fun fetchEquipment(force: Boolean) {
        if (hasFetchedEquipment && !force) return
        val rows = runCatching { ApiClient.classificationApi.getEquipment() }
            .onFailure { Log.w(TAG, "equipment fetch failed: ${it.message}") }
            .getOrNull() ?: return
        runCatching { DatabaseManager.replaceEquipment(rows) }
            .onFailure { Log.w(TAG, "equipment persist failed: ${it.message}") }
        _equipment.value = rows
        hasFetchedEquipment = true
    }

    private suspend fun fetchRestTypes(force: Boolean) {
        if (hasFetchedRestTypes && !force) return
        val rows = runCatching { ApiClient.classificationApi.getRestTypes() }
            .onFailure { Log.w(TAG, "rest-types fetch failed: ${it.message}") }
            .getOrNull() ?: return
        seedRestTypes(rows)
        hasFetchedRestTypes = true
    }

    private suspend fun fetchTrainingLevels(force: Boolean) {
        if (hasFetchedTrainingLevels && !force) return
        val rows = runCatching { ApiClient.classificationApi.getTrainingLevels() }
            .onFailure { Log.w(TAG, "training-levels fetch failed: ${it.message}") }
            .getOrNull() ?: return
        _trainingLevels.value = rows
        hasFetchedTrainingLevels = true
    }

    private suspend fun fetchRpeScales(force: Boolean) {
        if (hasFetchedRpeScales && !force) return
        val rows = runCatching { ApiClient.classificationApi.getRpeScales() }
            .onFailure { Log.w(TAG, "rpe-scales fetch failed: ${it.message}") }
            .getOrNull() ?: return
        _rpeScales.value = rows
        hasFetchedRpeScales = true
    }

    // MARK: - Anatomy / equipment accessors

    /** Curated chip-strip anatomy (server `isPrimary`), sorted by server `sortOrder`. */
    val primaryAnatomy: List<TagOption>
        get() = anatomy.value
            .filter { it.isPrimary == true }
            .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
            .map { TagOption(it.id.toString(), it.name) }

    /** Curated chip-strip equipment (server `isPrimary`), sorted by server `sortOrder`. */
    val primaryEquipment: List<TagOption>
        get() = equipment.value
            .filter { it.isPrimary == true }
            .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
            .map { TagOption(it.id.toString(), it.name) }

    /** All anatomy as chip options in server order (for drawers that show the full taxonomy). */
    val allAnatomyOptions: List<TagOption>
        get() = anatomy.value.sortedBy { it.sortOrder ?: Int.MAX_VALUE }.map { TagOption(it.id.toString(), it.name) }

    val allEquipmentOptions: List<TagOption>
        get() = equipment.value.sortedBy { it.sortOrder ?: Int.MAX_VALUE }.map { TagOption(it.id.toString(), it.name) }

    /**
     * Anatomy grouped by `category` (server order inside each group; missing
     * category buckets under "Other"), groups sorted alphabetically.
     */
    val anatomyByCategory: List<Pair<String, List<Anatomy>>>
        get() = groupedByCategory(anatomy.value, { it.category }, { it.sortOrder })

    val equipmentByCategory: List<Pair<String, List<Equipment>>>
        get() = groupedByCategory(equipment.value, { it.category }, { it.sortOrder })

    private fun <T> groupedByCategory(
        rows: List<T>,
        category: (T) -> String?,
        sortOrder: (T) -> Int?
    ): List<Pair<String, List<T>>> =
        rows.groupBy { category(it)?.takeIf { c -> c.isNotBlank() } ?: "Other" }
            .map { (cat, items) -> cat to items.sortedBy { sortOrder(it) ?: Int.MAX_VALUE } }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

    /** Anatomy display name for a GUID (case-insensitive); null when unknown or cache cold. */
    fun anatomyName(id: String): String? =
        anatomy.value.firstOrNull { it.id.toString().equals(id, ignoreCase = true) }?.name

    fun equipmentName(id: String): String? =
        equipment.value.firstOrNull { it.id.toString().equals(id, ignoreCase = true) }?.name

    /** Anatomy GUID for a display name (case-insensitive); null when unknown or cache cold. */
    fun anatomyId(name: String): String? =
        anatomy.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id?.toString()

    fun equipmentId(name: String): String? =
        equipment.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id?.toString()

    // MARK: - Lookup resolvers (name <-> id)

    /** `WorkoutTaskType` GUID by name ("Movement" / "Rest" / "Interval"); falls back to the seeded constants. */
    fun workoutTaskTypeId(name: String): String =
        workoutTaskTypesByName[name.lowercase(Locale.US)] ?: WorkoutTaskTypeId.fallbackByName(name)

    /** `ContentStatus` GUID by name; falls back to the "Active" constant. */
    fun contentStatusId(name: String): String =
        contentStatusesByName[name.lowercase(Locale.US)] ?: ClassificationFallbackIds.CONTENT_STATUS_ACTIVE

    /** `SessionStatus` GUID by name ("InProgress" / "Completed" / "Abandoned"); falls back to the seeded constants. */
    fun sessionStatusId(name: String): String =
        sessionStatusesByName[name.lowercase(Locale.US)] ?: WorkoutSessionStatusId.fallbackByName(name)

    /** `RestType` GUID by name ("Passive" / "Active Recovery"); falls back to the seeded constants. */
    fun restTypeId(name: String): String =
        restTypesByName[name.lowercase(Locale.US)] ?: RestTypeId.fallbackByName(name)

    /** Reverse of [restTypeId]; null when the id is unknown to both cache and fallbacks. */
    fun restTypeName(id: String): String? {
        val lc = id.lowercase(Locale.US)
        return restTypeNamesById[lc] ?: RestTypeId.fallbackName(lc)
    }

    fun trainingLevels(): List<FLTrainingLevel> = _trainingLevels.value

    /** Training-level GUID by name (case-insensitive); null when unknown or cache cold. */
    fun trainingLevelId(name: String): String? =
        _trainingLevels.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id?.toString()

    fun trainingLevelName(id: String): String? =
        _trainingLevels.value.firstOrNull { it.id.toString().equals(id, ignoreCase = true) }?.name

    // MARK: - Seeding (shared by the fetchers and unit tests)

    private fun seedWorkoutTaskTypes(rows: List<WorkoutTaskTypeDto>) {
        _workoutTaskTypes.value = rows
        workoutTaskTypesByName = rows.associate { it.name.lowercase(Locale.US) to it.id.lowercase(Locale.US) }
    }

    private fun seedContentStatuses(rows: List<ContentStatus>) {
        _contentStatuses.value = rows
        contentStatusesByName = rows.associate { it.name.lowercase(Locale.US) to it.id.toString().lowercase(Locale.US) }
    }

    private fun seedRestTypes(rows: List<RestTypeDto>) {
        _restTypes.value = rows
        val byName = LinkedHashMap<String, String>()
        val byId = LinkedHashMap<String, String>()
        rows.forEach { row ->
            val id = row.id.lowercase(Locale.US)
            byName[row.name.lowercase(Locale.US)] = id
            byId[id] = row.name
        }
        restTypesByName = byName
        restTypeNamesById = byId
    }

    /** Test seam: populate the cache without the network or the DB. */
    internal fun seedForTesting(
        anatomy: List<Anatomy> = emptyList(),
        equipment: List<Equipment> = emptyList(),
        workoutTaskTypes: List<WorkoutTaskTypeDto> = emptyList(),
        restTypes: List<RestTypeDto> = emptyList(),
        contentStatuses: List<ContentStatus> = emptyList(),
        trainingLevels: List<FLTrainingLevel> = emptyList()
    ) {
        hydrated = true
        _anatomy.value = anatomy
        _equipment.value = equipment
        seedWorkoutTaskTypes(workoutTaskTypes)
        seedRestTypes(restTypes)
        seedContentStatuses(contentStatuses)
        _trainingLevels.value = trainingLevels
    }

    /** Test seam: back to the cold-cache state. */
    internal fun resetForTesting() {
        seedForTesting()
        hydrated = true
        hasFetchedTaskTypes = false
        hasFetchedContentStatuses = false
        hasFetchedAnatomy = false
        hasFetchedEquipment = false
        hasFetchedRestTypes = false
        hasFetchedTrainingLevels = false
        hasFetchedRpeScales = false
    }
}
