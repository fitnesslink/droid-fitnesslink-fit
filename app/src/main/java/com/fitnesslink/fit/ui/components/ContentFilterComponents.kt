package com.fitnesslink.fit.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.BlendGray
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.HighlightState
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

// Port of iOS `Views/Components/ContentFilterComponents.swift` + the
// `ContentLayout` / `ContentLayoutToggle` pair from `WorkoutItemView.swift`.
// Shared by the Workouts / Programs / Movements / My Content lists and the
// calendar scheduling pickers so every filter bar looks and behaves the same.

/** A taxonomy row reduced to what a chip needs. `id` is the server GUID string. */
data class TagOption(val id: String, val name: String)

/** Card vs. compact-row layout for the catalog "See All" lists; persisted via [ContentLayoutPrefs]. */
enum class ContentLayout {
    CARD, LIST;

    fun toggled(): ContentLayout = if (this == CARD) LIST else CARD
}

/**
 * Pill-shaped selectable filter chip — the one chip style for every filter
 * bar (iOS `FilterChip`). Not Material's `FilterChip`; import this one.
 */
@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) FLPrimary else BackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) White else TextPrimaryColor,
            maxLines = 1
        )
    }
}

/**
 * Two-state All / Favorites segmented control (iOS `FavoritesFilterControl`).
 * Favorites filtering is applied client-side against cached rows so it works
 * offline — the control only reports the choice.
 */
@Composable
fun FavoritesFilterControl(favoritesOnly: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BlendGray)
            .padding(2.dp)
    ) {
        Segment(label = "All", selected = !favoritesOnly, modifier = Modifier.weight(1f)) { onChange(false) }
        Segment(label = "Favorites", selected = favoritesOnly, modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = TextPrimaryColor,
            maxLines = 1
        )
    }
}

/**
 * Horizontal strip of primary-equipment chips (iOS `EquipmentChipStrip`).
 * Selection is OR within the strip, AND with the other filters. Renders
 * nothing when [options] is empty (taxonomy not loaded yet). Feed it
 * `ClassificationStore.primaryEquipment`, optionally narrowed to the ids the
 * current list actually uses.
 */
@Composable
fun EquipmentChipStrip(options: List<TagOption>, selected: Set<String>, onToggle: (String) -> Unit) {
    TagChipStrip(options = options, selected = selected, onToggle = onToggle)
}

/**
 * Horizontal strip of primary-anatomy (body focus) chips (iOS
 * `AnatomyChipStrip`). Same composition rule as [EquipmentChipStrip];
 * vocabulary and ordering come from `ClassificationStore.primaryAnatomy`.
 */
@Composable
fun AnatomyChipStrip(options: List<TagOption>, selected: Set<String>, onToggle: (String) -> Unit) {
    TagChipStrip(options = options, selected = selected, onToggle = onToggle)
}

@Composable
private fun TagChipStrip(options: List<TagOption>, selected: Set<String>, onToggle: (String) -> Unit) {
    if (options.isEmpty()) return
    val selectedLower = remember(selected) { selected.map { it.lowercase() }.toSet() }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.id }) { option ->
            FilterChip(
                label = option.name,
                isSelected = selectedLower.contains(option.id.lowercase()),
                onClick = { onToggle(option.id) }
            )
        }
    }
}

/**
 * Contributor (coach) filter as a dropdown pill (iOS `ContributorFilterMenu`
 * plus the "Mine" chip the calendar pickers sit next to it). Lists
 * `ContributorStore.contributors` under "All coaches" and "Mine"
 * (user-authored content, `contributorId == null`). "Mine" and a coach are
 * mutually exclusive: picking one clears the other through the callbacks, so
 * callers only need to store the two values.
 */
@Composable
fun ContributorFilterMenu(
    selectedContributorId: String?,
    mineSelected: Boolean,
    onSelectContributor: (String?) -> Unit,
    onToggleMine: () -> Unit
) {
    val contributors by ContributorStore.contributors.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val isActive = mineSelected || selectedContributorId != null
    val selectedLabel = when {
        mineSelected -> "Mine"
        selectedContributorId != null ->
            contributors.firstOrNull { it.id.equals(selectedContributorId, ignoreCase = true) }?.label
                ?: ContributorStore.name(selectedContributorId)
                ?: "Coach"
        else -> "All coaches"
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isActive) HighlightState else BackgroundColor)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (isActive) FLPrimary else TextPrimaryColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = selectedLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isActive) FLPrimary else TextPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isActive) FLPrimary else TextPrimaryColor,
                modifier = Modifier.size(12.dp)
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuEntry(label = "All coaches", checked = !isActive) {
                expanded = false
                if (mineSelected) onToggleMine()
                if (selectedContributorId != null) onSelectContributor(null)
            }
            MenuEntry(label = "Mine", checked = mineSelected) {
                expanded = false
                // "Mine" means no contributor, so a coach can't stay selected.
                if (!mineSelected && selectedContributorId != null) onSelectContributor(null)
                onToggleMine()
            }
            contributors.forEach { contributor ->
                val checked = selectedContributorId?.equals(contributor.id, ignoreCase = true) == true
                MenuEntry(label = contributor.label, checked = checked) {
                    expanded = false
                    if (mineSelected) onToggleMine()
                    onSelectContributor(contributor.id)
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp, color = TextPrimaryColor) },
        leadingIcon = {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = FLPrimary, modifier = Modifier.size(16.dp))
            } else {
                Spacer(Modifier.size(16.dp))
            }
        },
        onClick = onClick
    )
}

/** Small "Clear filters" affordance (iOS `ClearFiltersButton`); renders nothing unless [visible]. */
@Composable
fun ClearFiltersButton(visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = TextSecondaryColor,
            modifier = Modifier.size(12.dp)
        )
        Text("Clear filters", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
    }
}

/**
 * Toolbar button that flips the catalog layout (iOS `ContentLayoutToggle`).
 * The glyph shows the layout the user will switch *to*: a list glyph while
 * cards are showing, a grid glyph while the list is showing.
 */
@Composable
fun ContentLayoutToggle(layout: ContentLayout, onChange: (ContentLayout) -> Unit) {
    val description = if (layout == ContentLayout.CARD) "Switch to list view" else "Switch to card view"
    IconButton(
        onClick = { onChange(layout.toggled()) },
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        if (layout == ContentLayout.CARD) ListGlyph() else GridGlyph()
    }
}

@Composable
private fun ListGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = size.height * 0.11f
        val rows = listOf(0.2f, 0.5f, 0.8f)
        rows.forEach { fy ->
            val y = size.height * fy
            drawCircle(FLPrimary, radius = stroke * 0.75f, center = Offset(stroke, y))
            drawLine(
                color = FLPrimary,
                start = Offset(size.width * 0.28f, y),
                end = Offset(size.width, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun GridGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val gap = size.width * 0.14f
        val cell = (size.width - gap) / 2f
        val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
        listOf(
            Offset(0f, 0f), Offset(cell + gap, 0f),
            Offset(0f, cell + gap), Offset(cell + gap, cell + gap)
        ).forEach { origin ->
            drawRoundRect(FLPrimary, topLeft = origin, size = Size(cell, cell), cornerRadius = radius)
        }
    }
}

/**
 * Persists the catalog layout choice across the Workouts / Programs /
 * Movements / My Content lists (iOS `@AppStorage("catalogContentLayout")`).
 * Call [initialize] once from the Application (or let any composable that
 * uses [rememberContentLayout] / [ContentLayoutToggle] attach the context);
 * before that, [get] / [set] work against an in-memory value.
 */
object ContentLayoutPrefs {
    private const val PREFS_NAME = "fitnesslink_prefs"
    const val KEY = "catalogContentLayout"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var memory: ContentLayout = ContentLayout.CARD

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun get(): ContentLayout {
        val raw = prefs?.getString(KEY, null) ?: return memory
        return parse(raw) ?: ContentLayout.CARD
    }

    fun set(layout: ContentLayout) {
        memory = layout
        prefs?.edit()?.putString(KEY, layout.name.lowercase())?.apply()
    }

    /** Accepts the iOS raw values ("card" / "list") and the enum names. */
    internal fun parse(raw: String?): ContentLayout? = when (raw?.trim()?.lowercase()) {
        "card" -> ContentLayout.CARD
        "list" -> ContentLayout.LIST
        else -> null
    }
}

/**
 * Compose-friendly handle on the persisted layout: reads [ContentLayoutPrefs]
 * on first composition and writes through on every change.
 */
@Composable
fun rememberContentLayout(): MutableState<ContentLayout> {
    val context = LocalContext.current
    ContentLayoutPrefs.initialize(context)
    val state = remember { mutableStateOf(ContentLayoutPrefs.get()) }
    return remember(state) {
        object : MutableState<ContentLayout> {
            override var value: ContentLayout
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    ContentLayoutPrefs.set(newValue)
                }

            override fun component1(): ContentLayout = value
            override fun component2(): (ContentLayout) -> Unit = { value = it }
        }
    }
}
