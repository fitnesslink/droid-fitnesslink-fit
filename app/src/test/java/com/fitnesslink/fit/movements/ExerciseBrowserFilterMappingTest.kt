package com.fitnesslink.fit.movements

import com.fitnesslink.fit.model.BrowserTab
import com.fitnesslink.fit.model.Personalization
import com.fitnesslink.fit.model.PersonalizationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise-browser filter state → list-view params, drawer badge count, taxonomy resolution, wildcard search. */
class ExerciseBrowserFilterMappingTest {

    private val chest = "11111111-1111-1111-1111-111111111111"
    private val back = "22222222-2222-2222-2222-222222222222"
    private val obliques = "33333333-3333-3333-3333-333333333333"
    private val barbell = "44444444-4444-4444-4444-444444444444"
    private val sled = "55555555-5555-5555-5555-555555555555"

    @Test
    fun `every browser filter dimension reaches the wire`() {
        val filters = BrowserFilterState(
            searchText = " row ",
            anatomyIds = setOf(chest.uppercase()),
            equipmentIds = setOf(barbell),
            difficultyIds = setOf("d1"),
            goalIds = setOf("g1"),
            movementPatternIds = setOf("p1"),
            locationIds = setOf("l1")
        )
        val p = MovementListQuery.forBrowser(filters, BrowserTab.All).toParams()
        assertEquals("row", p.query)
        assertEquals(listOf(chest), p.anatomyIds)
        assertEquals(listOf(barbell), p.equipmentIds)
        assertEquals(listOf("d1"), p.difficultyIds)
        assertEquals(listOf("g1"), p.goalIds)
        assertEquals(listOf("p1"), p.movementPatternIds)
        assertEquals(listOf("l1"), p.locationIds)
        assertEquals("primary", p.role)
    }

    @Test
    fun `non-primary count ignores chip-strip picks and counts every drawer pick`() {
        val filters = BrowserFilterState(
            anatomyIds = setOf(chest, obliques),       // chest is primary, obliques is drawer-only
            equipmentIds = setOf(barbell, sled),       // barbell primary, sled drawer-only
            difficultyIds = setOf("d1"),
            goalIds = setOf("g1", "g2"),
            locationIds = setOf("l1")
        )
        val count = filters.nonPrimaryFilterCount(
            primaryAnatomyIds = listOf(chest.uppercase(), back),
            primaryEquipmentIds = listOf(barbell)
        )
        // obliques + sled + d1 + g1 + g2 + l1
        assertEquals(6, count)
    }

    @Test
    fun `cleared state has no filters`() {
        val cleared = BrowserFilterState(searchText = "x", anatomyIds = setOf(chest), goalIds = setOf("g")).cleared()
        assertFalse(cleared.hasAnyFilter)
        assertFalse(cleared.isTagFilterActive)
    }

    @Test
    fun `taxonomy groups resolve by loose keyword with one dimension per group`() {
        val groups = listOf(
            Personalization(id = "1", name = "What is your training level?", options = listOf(PersonalizationItem("A-1", "Beginner"), PersonalizationItem("A-2", "Advanced"))),
            Personalization(id = "2", name = "What are your goals?", options = listOf(PersonalizationItem("B-1", "Strength"))),
            Personalization(id = "3", name = "Where do you work out?", alias = "location", options = listOf(PersonalizationItem("C-1", "Gym"))),
            Personalization(id = "4", name = "Equipment", options = listOf(PersonalizationItem("D-1", "Bands"))),
            Personalization(id = "5", name = "Secondary goal", options = listOf(PersonalizationItem("E-1", "Mobility")))
        )
        val options = TagFilterOptions.from(groups)
        assertEquals(listOf("a-1", "a-2"), options.difficulty.map { it.id })
        assertEquals(listOf("Beginner", "Advanced"), options.difficulty.map { it.name })
        // First goal group wins; the second never overrides it.
        assertEquals(listOf("b-1"), options.goal.map { it.id })
        assertEquals(listOf("c-1"), options.location.map { it.id })
        // No movement-pattern group in prod → hidden section.
        assertTrue(options.movementPattern.isEmpty())
    }

    @Test
    fun `empty option lists never claim a dimension`() {
        val options = TagFilterOptions.from(listOf(Personalization(id = "1", name = "training level", options = emptyList())))
        assertTrue(options.difficulty.isEmpty())
    }

    @Test
    fun `classification search is substring without wildcards and anchored with them`() {
        assertTrue(classificationNameMatches("Bench Press", "press"))
        assertTrue(classificationNameMatches("Bench Press", "  PRESS "))
        assertFalse(classificationNameMatches("Bench Press", "row"))
        assertTrue(classificationNameMatches("Dumbbell", "*bell"))
        assertTrue(classificationNameMatches("Barbell", "bar*bell"))
        assertFalse(classificationNameMatches("Kettlebell swing", "*bell"))
        assertTrue(classificationNameMatches("Band", "b?nd"))
        assertFalse(classificationNameMatches("Bands", "b?nd"))
        assertTrue(classificationNameMatches("Anything", ""))
    }
}
