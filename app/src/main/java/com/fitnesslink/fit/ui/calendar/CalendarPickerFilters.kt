package com.fitnesslink.fit.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.ui.components.AnatomyChipStrip
import com.fitnesslink.fit.ui.components.ClearFiltersButton
import com.fitnesslink.fit.ui.components.ContributorFilterMenu
import com.fitnesslink.fit.ui.components.EquipmentChipStrip
import com.fitnesslink.fit.ui.components.FavoritesFilterControl
import com.fitnesslink.fit.ui.components.FilterChip
import com.fitnesslink.fit.ui.components.SearchView
import java.util.Locale

/**
 * Sheet-local filter state shared by the calendar's workout and program
 * pickers (iOS FTNSSLNK-265/267: the same bar as the catalog lists). Every
 * filter runs client-side over cached rows; equipment / anatomy maps come
 * from a best-effort `list-view` fetch and are empty offline, which hides
 * the chip strips.
 */
class CalendarPickerFilterState {
    var searchText by mutableStateOf("")
    var favoritesOnly by mutableStateOf(false)
    /** "Mine" — user-authored content (`contributorId == null`); mutually exclusive with a coach. */
    var personalOnly by mutableStateOf(false)
    var selectedContributorId by mutableStateOf<String?>(null)
    var selectedEquipmentIds by mutableStateOf<Set<String>>(emptySet())
    var selectedAnatomyIds by mutableStateOf<Set<String>>(emptySet())
    /** contentId (lowercased) → equipment ids (lowercased). */
    var equipmentByContentId by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    var anatomyByContentId by mutableStateOf<Map<String, Set<String>>>(emptyMap())

    val hasActiveFilters: Boolean
        get() = favoritesOnly || personalOnly || selectedContributorId != null ||
            selectedEquipmentIds.isNotEmpty() || selectedAnatomyIds.isNotEmpty()

    fun clear() {
        favoritesOnly = false; personalOnly = false; selectedContributorId = null
        selectedEquipmentIds = emptySet(); selectedAnatomyIds = emptySet()
    }

    fun toggleMine() {
        personalOnly = !personalOnly
        if (personalOnly) selectedContributorId = null
    }

    fun selectContributor(id: String?) {
        selectedContributorId = id
        if (id != null) personalOnly = false
    }

    fun toggleEquipment(id: String) {
        val lc = id.lowercase(Locale.US)
        selectedEquipmentIds = if (lc in selectedEquipmentIds) selectedEquipmentIds - lc else selectedEquipmentIds + lc
    }

    fun toggleAnatomy(id: String) {
        val lc = id.lowercase(Locale.US)
        selectedAnatomyIds = if (lc in selectedAnatomyIds) selectedAnatomyIds - lc else selectedAnatomyIds + lc
    }

    /** Apply every filter to [items]; the lambdas pull the fields off the row type. */
    fun <T> apply(
        items: List<T>, id: (T) -> String, name: (T) -> String, isFavorite: (T) -> Boolean, contributorId: (T) -> String?
    ): List<T> {
        var out = items
        if (favoritesOnly) out = out.filter(isFavorite)
        if (personalOnly) out = out.filter { contributorId(it) == null }
        selectedContributorId?.let { cid -> out = out.filter { contributorId(it).equals(cid, ignoreCase = true) } }
        if (selectedEquipmentIds.isNotEmpty()) {
            out = out.filter { (equipmentByContentId[id(it).lowercase(Locale.US)] ?: emptySet()).any { e -> e in selectedEquipmentIds } }
        }
        if (selectedAnatomyIds.isNotEmpty()) {
            out = out.filter { (anatomyByContentId[id(it).lowercase(Locale.US)] ?: emptySet()).any { a -> a in selectedAnatomyIds } }
        }
        val q = searchText.trim().lowercase(Locale.US)
        if (q.isNotEmpty()) out = out.filter { name(it).lowercase(Locale.US).contains(q) }
        return out
    }
}

/** The filter bar itself: search, All/Favorites, Mine chip + contributor menu, equipment / anatomy strips, Clear. */
@Composable
fun CalendarPickerFilterBar(
    state: CalendarPickerFilterState,
    searchPlaceholder: String,
    hasPersonalContent: Boolean,
    presentContributorIds: Set<String>
) {
    val presentContributors = ContributorStore.present(presentContributorIds)
    val equipmentOptions = ClassificationStore.primaryEquipment.filter { opt ->
        state.equipmentByContentId.values.any { it.contains(opt.id.lowercase(Locale.US)) }
    }
    val anatomyOptions = ClassificationStore.primaryAnatomy.filter { opt ->
        state.anatomyByContentId.values.any { it.contains(opt.id.lowercase(Locale.US)) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SearchView(
            text = state.searchText, onTextChange = { state.searchText = it },
            placeholder = searchPlaceholder, modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FavoritesFilterControl(favoritesOnly = state.favoritesOnly, onChange = { state.favoritesOnly = it })
        }
        if (hasPersonalContent || presentContributors.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasPersonalContent) {
                    FilterChip(label = "Mine", isSelected = state.personalOnly, onClick = { state.toggleMine() })
                }
                if (presentContributors.size > 1) {
                    ContributorFilterMenu(
                        selectedContributorId = state.selectedContributorId,
                        mineSelected = state.personalOnly,
                        onSelectContributor = { state.selectContributor(it) },
                        onToggleMine = { state.toggleMine() }
                    )
                }
            }
        }
        if (equipmentOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            EquipmentChipStrip(options = equipmentOptions, selected = state.selectedEquipmentIds, onToggle = { state.toggleEquipment(it) })
        }
        if (anatomyOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            AnatomyChipStrip(options = anatomyOptions, selected = state.selectedAnatomyIds, onToggle = { state.toggleAnatomy(it) })
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            ClearFiltersButton(visible = state.hasActiveFilters, onClick = { state.clear() })
        }
    }
}
