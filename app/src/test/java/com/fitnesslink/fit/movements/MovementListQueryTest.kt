package com.fitnesslink.fit.movements

import com.fitnesslink.fit.model.BrowserTab
import com.fitnesslink.fit.network.dto.MovementAnatomyRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `GET /movements/list-view` parameter building (iOS `MovementService.listView` semantics). */
class MovementListQueryTest {

    @Test
    fun `empty query sends no filter params and defaults role to primary`() {
        val p = MovementListQuery().toParams()
        assertNull(p.query)
        assertNull(p.anatomyIds)
        assertNull(p.equipmentIds)
        assertNull(p.difficultyIds)
        assertNull(p.goalIds)
        assertNull(p.movementPatternIds)
        assertNull(p.locationIds)
        assertNull(p.contributorId)
        assertNull(p.personalOnly)
        assertNull(p.favoritesOnly)
        assertEquals(MovementAnatomyRole.PRIMARY, p.role)
    }

    @Test
    fun `blank search text is omitted and non-blank text is trimmed`() {
        assertNull(MovementListQuery(query = "   ").toParams().query)
        assertEquals("bench press", MovementListQuery(query = "  bench press ").toParams().query)
    }

    @Test
    fun `id lists are lowercased, de-duplicated and sorted`() {
        val p = MovementListQuery(
            anatomyIds = setOf("B2E4D3C1-5F6A-4B7E-8D9C-2A1F0E9D8C7B", "a1f3c2d0-4e5b-4a6f-9c7d-3b2a1f0e9d8c", "b2e4d3c1-5f6a-4b7e-8d9c-2a1f0e9d8c7b", " ")
        ).toParams()
        assertEquals(
            listOf("a1f3c2d0-4e5b-4a6f-9c7d-3b2a1f0e9d8c", "b2e4d3c1-5f6a-4b7e-8d9c-2a1f0e9d8c7b"),
            p.anatomyIds
        )
    }

    @Test
    fun `false booleans are omitted so the server default applies`() {
        val p = MovementListQuery(personalOnly = false, favoritesOnly = false).toParams()
        assertNull(p.personalOnly)
        assertNull(p.favoritesOnly)
        val q = MovementListQuery(personalOnly = true, favoritesOnly = true).toParams()
        assertEquals(true, q.personalOnly)
        assertEquals(true, q.favoritesOnly)
    }

    @Test
    fun `contributor id is lowercased and blank contributor is dropped`() {
        assertEquals("abc", MovementListQuery(contributorId = " ABC ").toParams().contributorId)
        assertNull(MovementListQuery(contributorId = "  ").toParams().contributorId)
    }

    @Test
    fun `tag filter detection covers only the drawer dimensions`() {
        assertFalse(MovementListQuery(anatomyIds = setOf("x"), equipmentIds = setOf("y")).isTagFilterActive)
        assertTrue(MovementListQuery(difficultyIds = setOf("d")).isTagFilterActive)
        assertTrue(MovementListQuery(goalIds = setOf("g")).isTagFilterActive)
        assertTrue(MovementListQuery(movementPatternIds = setOf("p")).isTagFilterActive)
        assertTrue(MovementListQuery(locationIds = setOf("l")).isTagFilterActive)
    }

    @Test
    fun `browser tabs map to personalOnly and favoritesOnly`() {
        val filters = BrowserFilterState(searchText = "squat")
        assertNull(MovementListQuery.forBrowser(filters, BrowserTab.All).personalOnly)
        assertNull(MovementListQuery.forBrowser(filters, BrowserTab.All).favoritesOnly)
        assertEquals(true, MovementListQuery.forBrowser(filters, BrowserTab.Mine).personalOnly)
        assertNull(MovementListQuery.forBrowser(filters, BrowserTab.Mine).favoritesOnly)
        assertEquals(true, MovementListQuery.forBrowser(filters, BrowserTab.Favorites).favoritesOnly)
        assertNull(MovementListQuery.forBrowser(filters, BrowserTab.Favorites).personalOnly)
        // Recent is a local slice ordered by the recents endpoint; the server call is the All shape.
        val recent = MovementListQuery.forBrowser(filters, BrowserTab.Recent)
        assertNull(recent.personalOnly)
        assertNull(recent.favoritesOnly)
        assertEquals("squat", recent.toParams().query)
    }
}
