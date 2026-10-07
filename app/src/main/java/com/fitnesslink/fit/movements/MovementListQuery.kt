package com.fitnesslink.fit.movements

import com.fitnesslink.fit.model.BrowserTab
import com.fitnesslink.fit.model.Personalization
import com.fitnesslink.fit.network.dto.MovementAnatomyRole
import com.fitnesslink.fit.ui.components.TagOption
import java.util.Locale

/**
 * The parameter set for `GET /movements/list-view` (iOS
 * `MovementService.listView`). Every id list ORs within itself and ANDs
 * against the other filters. Values are normalized to lowercase GUIDs so the
 * same filter state always produces the same request, and empty lists become
 * `null` so Retrofit omits the key instead of sending `anatomyIds=`.
 */
data class MovementListQuery(
    val query: String? = null,
    val anatomyIds: Set<String> = emptySet(),
    val equipmentIds: Set<String> = emptySet(),
    val role: String = MovementAnatomyRole.PRIMARY,
    val difficultyIds: Set<String> = emptySet(),
    val goalIds: Set<String> = emptySet(),
    val movementPatternIds: Set<String> = emptySet(),
    val locationIds: Set<String> = emptySet(),
    val contributorId: String? = null,
    val personalOnly: Boolean? = null,
    val favoritesOnly: Boolean? = null
) {
    /** True when any of the drawer-only tag dimensions is active. */
    val isTagFilterActive: Boolean
        get() = difficultyIds.isNotEmpty() || goalIds.isNotEmpty() ||
            movementPatternIds.isNotEmpty() || locationIds.isNotEmpty()

    /** Wire-ready parameters: trimmed query, lowercased sorted ids, `null` for "not set". */
    fun toParams(): MovementListParams = MovementListParams(
        query = query?.trim()?.takeIf { it.isNotEmpty() },
        anatomyIds = anatomyIds.wire(),
        equipmentIds = equipmentIds.wire(),
        role = role,
        difficultyIds = difficultyIds.wire(),
        goalIds = goalIds.wire(),
        movementPatternIds = movementPatternIds.wire(),
        locationIds = locationIds.wire(),
        contributorId = contributorId?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() },
        personalOnly = personalOnly?.takeIf { it },
        favoritesOnly = favoritesOnly?.takeIf { it }
    )

    private fun Set<String>.wire(): List<String>? =
        map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }.distinct().sorted().takeIf { it.isNotEmpty() }

    companion object {
        /**
         * Browser mapping (iOS `ExerciseBrowserViewModel.refreshMovementsFromServer`):
         * the server call always carries the search + tag filters; the tab
         * narrows with `personalOnly` (Mine) / `favoritesOnly` (Favorites).
         * Recent is a local-only slice (`GET /movements/recents` owns ordering),
         * so it fetches the unfiltered-by-tab set like All.
         */
        fun forBrowser(filters: BrowserFilterState, tab: BrowserTab): MovementListQuery = MovementListQuery(
            query = filters.searchText,
            anatomyIds = filters.anatomyIds,
            equipmentIds = filters.equipmentIds,
            difficultyIds = filters.difficultyIds,
            goalIds = filters.goalIds,
            movementPatternIds = filters.movementPatternIds,
            locationIds = filters.locationIds,
            personalOnly = if (tab == BrowserTab.Mine) true else null,
            favoritesOnly = if (tab == BrowserTab.Favorites) true else null
        )
    }
}

/** Exactly what goes on the wire — see [MovementListQuery.toParams]. */
data class MovementListParams(
    val query: String?,
    val anatomyIds: List<String>?,
    val equipmentIds: List<String>?,
    val role: String,
    val difficultyIds: List<String>?,
    val goalIds: List<String>?,
    val movementPatternIds: List<String>?,
    val locationIds: List<String>?,
    val contributorId: String?,
    val personalOnly: Boolean?,
    val favoritesOnly: Boolean?
)

/**
 * The exercise browser's filter state, independent of Compose so the
 * query mapping and the "More filters" count badge are unit-testable.
 */
data class BrowserFilterState(
    val searchText: String = "",
    val anatomyIds: Set<String> = emptySet(),
    val equipmentIds: Set<String> = emptySet(),
    val difficultyIds: Set<String> = emptySet(),
    val goalIds: Set<String> = emptySet(),
    val movementPatternIds: Set<String> = emptySet(),
    val locationIds: Set<String> = emptySet()
) {
    val isTagFilterActive: Boolean
        get() = difficultyIds.isNotEmpty() || goalIds.isNotEmpty() ||
            movementPatternIds.isNotEmpty() || locationIds.isNotEmpty()

    val hasAnyFilter: Boolean
        get() = searchText.isNotBlank() || anatomyIds.isNotEmpty() || equipmentIds.isNotEmpty() || isTagFilterActive

    /**
     * Number of selected filters that are NOT in the chip strip's curated
     * primary sets — i.e. came from the "More filters" drawer (iOS
     * `nonPrimaryFilterCount`). Every tag dimension lives only in the drawer,
     * so all of those count.
     */
    fun nonPrimaryFilterCount(primaryAnatomyIds: Collection<String>, primaryEquipmentIds: Collection<String>): Int {
        val primaryA = primaryAnatomyIds.mapTo(HashSet()) { it.lowercase(Locale.US) }
        val primaryE = primaryEquipmentIds.mapTo(HashSet()) { it.lowercase(Locale.US) }
        val extraAnatomy = anatomyIds.count { it.lowercase(Locale.US) !in primaryA }
        val extraEquipment = equipmentIds.count { it.lowercase(Locale.US) !in primaryE }
        return extraAnatomy + extraEquipment +
            difficultyIds.size + goalIds.size + movementPatternIds.size + locationIds.size
    }

    fun cleared(): BrowserFilterState = BrowserFilterState()
}

/**
 * Options for the drawer's tag-filter sections (Difficulty / Goal / Movement
 * Pattern / Location), resolved from the personalization taxonomy. A
 * dimension whose group doesn't exist resolves to an empty list and its
 * section is hidden.
 */
data class TagFilterOptions(
    val difficulty: List<TagOption> = emptyList(),
    val goal: List<TagOption> = emptyList(),
    val movementPattern: List<TagOption> = emptyList(),
    val location: List<TagOption> = emptyList()
) {
    companion object {
        /**
         * iOS `ExerciseBrowserViewModel.applyTaxonomy`: prod group names are
         * onboarding questions ("What is your training level?"), not clean
         * labels, so match loosely on name + alias. Each group feeds at most
         * one dimension — first match wins, most specific keyword set first.
         */
        fun from(groups: List<Personalization>): TagFilterOptions {
            var difficulty: List<TagOption> = emptyList()
            var goal: List<TagOption> = emptyList()
            var pattern: List<TagOption> = emptyList()
            var location: List<TagOption> = emptyList()

            for (group in groups) {
                val hay = "${group.name} ${group.alias.orEmpty()}".lowercase(Locale.US)
                val items = group.options.map { TagOption(it.id.lowercase(Locale.US), it.text) }
                if (items.isEmpty()) continue
                when {
                    pattern.isEmpty() && (hay.contains("movement pattern") || hay.contains("pattern")) -> pattern = items
                    difficulty.isEmpty() && (hay.contains("training level") || hay.contains("difficulty")) -> difficulty = items
                    goal.isEmpty() && hay.contains("goal") -> goal = items
                    location.isEmpty() && (hay.contains("location") || hay.contains("work out") || hay.contains("workout location")) -> location = items
                }
            }
            return TagFilterOptions(difficulty, goal, pattern, location)
        }
    }
}

/**
 * Case-insensitive taxonomy search (iOS `ClassificationPickerView.matches`).
 * A query with no wildcard is a substring match; `*` (any run) and `?` (one
 * character) compile to an anchored pattern so the wildcards bind to the
 * whole name ("*bell" → ends with "bell", "bar*bell" → barbell).
 */
fun classificationNameMatches(name: String, query: String): Boolean {
    val q = query.trim().lowercase(Locale.US)
    if (q.isEmpty()) return true
    val n = name.lowercase(Locale.US)
    if (!q.contains('*') && !q.contains('?')) return n.contains(q)
    val pattern = StringBuilder("^")
    for (ch in q) {
        when (ch) {
            '*' -> pattern.append(".*")
            '?' -> pattern.append('.')
            else -> pattern.append(Regex.escape(ch.toString()))
        }
    }
    pattern.append('$')
    return Regex(pattern.toString()).matches(n)
}
