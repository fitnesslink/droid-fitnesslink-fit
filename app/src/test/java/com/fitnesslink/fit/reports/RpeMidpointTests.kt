package com.fitnesslink.fit.reports

import com.fitnesslink.fit.model.api.Rpe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** RPE id → numeric midpoint, the value hydrated rows store in `rpeValue`. */
class RpeMidpointTests {

    private fun scale(from: Int, to: Int?, id: UUID = UUID.randomUUID()) =
        Rpe(id = id, name = "$from-$to", fromValue = from, toValue = to)

    @Test
    fun `range scale maps to its midpoint`() {
        assertEquals(7.5, SessionHistoryHydrator.rpeMidpoint(scale(7, 8)), 0.0001)
        assertEquals(2.0, SessionHistoryHydrator.rpeMidpoint(scale(1, 3)), 0.0001)
    }

    @Test
    fun `single-value scale maps to itself`() {
        assertEquals(10.0, SessionHistoryHydrator.rpeMidpoint(scale(10, null)), 0.0001)
        assertEquals(0.0, SessionHistoryHydrator.rpeMidpoint(scale(0, 0)), 0.0001)
    }

    @Test
    fun `map is keyed by lower-cased id so session rpeId lookups match`() {
        val id = UUID.fromString("5F2B1C3D-0000-4000-8000-000000000001")
        val map = SessionHistoryHydrator.rpeMidpoints(listOf(scale(5, 6, id), scale(9, 9)))
        assertEquals(2, map.size)
        assertEquals(5.5, map[id.toString().lowercase()]!!, 0.0001)
        // The session payload carries the same UUID; `toString()` on both sides is lower-case.
        assertEquals(5.5, map[id.toString()]!!, 0.0001)
        assertNull(map["not-an-id"])
    }

    @Test
    fun `empty scales yield an empty map`() {
        assertEquals(emptyMap<String, Double>(), SessionHistoryHydrator.rpeMidpoints(emptyList()))
    }
}
