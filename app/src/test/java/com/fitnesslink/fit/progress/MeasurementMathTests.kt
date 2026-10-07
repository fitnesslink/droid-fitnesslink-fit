package com.fitnesslink.fit.progress

import com.fitnesslink.fit.model.BodyMeasurementValue
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit
import com.fitnesslink.fit.network.dto.BodyMeasurementDto
import com.fitnesslink.fit.network.dto.toMeasurementEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class MeasurementMathTests {

    private fun entry(id: String, dayOffset: Int, vararg parts: Pair<BodyPart, Double>, unit: MeasurementUnit = MeasurementUnit.CM) = MeasurementEntry(
        id = id,
        date = Date(1_760_000_000_000L + dayOffset * 86_400_000L),
        measurements = parts.map { (p, v) -> BodyMeasurementValue(bodyPart = p, value = v, unit = unit) }
    )

    // MARK: - delta

    @Test
    fun `delta is current minus previous in the display unit`() {
        val latest = entry("b", 1, BodyPart.WAIST to 80.0)
        val previous = entry("a", 0, BodyPart.WAIST to 82.5)
        assertEquals(-2.5, MeasurementMath.delta(latest, previous, BodyPart.WAIST, MeasurementUnit.CM)!!, 0.0001)
        assertEquals(-2.5 / 2.54, MeasurementMath.delta(latest, previous, BodyPart.WAIST, MeasurementUnit.INCHES)!!, 0.0001)
    }

    @Test
    fun `delta converts mixed units before subtracting`() {
        val latest = entry("b", 1, BodyPart.CHEST to 40.0, unit = MeasurementUnit.INCHES)   // 101.6 cm
        val previous = entry("a", 0, BodyPart.CHEST to 100.0)                                // cm
        assertEquals(1.6, MeasurementMath.delta(latest, previous, BodyPart.CHEST, MeasurementUnit.CM)!!, 0.0001)
    }

    @Test
    fun `delta is null when either side lacks the part`() {
        val latest = entry("b", 1, BodyPart.WAIST to 80.0)
        val previous = entry("a", 0, BodyPart.HIPS to 95.0)
        assertNull(MeasurementMath.delta(latest, previous, BodyPart.WAIST, MeasurementUnit.CM))
        assertNull(MeasurementMath.delta(latest, null, BodyPart.WAIST, MeasurementUnit.CM))
    }

    @Test
    fun `previousOf walks a newest-first list`() {
        val list = listOf(entry("c", 2), entry("b", 1), entry("a", 0))
        assertEquals("b", MeasurementMath.previousOf(list, "c")!!.id)
        assertEquals("a", MeasurementMath.previousOf(list, "B")!!.id)
        assertNull(MeasurementMath.previousOf(list, "a"))
        assertNull(MeasurementMath.previousOf(list, "zzz"))
    }

    // MARK: - grouping

    @Test
    fun `grouped follows Core Arms Legs Other order and drops empty categories`() {
        val e = entry("x", 0, BodyPart.CALVES to 38.0, BodyPart.NECK to 39.0, BodyPart.WAIST to 80.0, BodyPart.LEFT_BICEP to 35.0)
        val groups = MeasurementMath.grouped(e)
        assertEquals(listOf("Core", "Arms", "Legs", "Other"), groups.map { it.first })
        assertEquals(listOf(BodyPart.WAIST), groups[0].second.map { it.bodyPart })
        assertEquals(listOf(BodyPart.LEFT_BICEP), groups[1].second.map { it.bodyPart })
        assertEquals(listOf(BodyPart.CALVES), groups[2].second.map { it.bodyPart })
        val armsOnly = MeasurementMath.grouped(entry("y", 0, BodyPart.FOREARMS to 28.0))
        assertEquals(listOf("Arms"), armsOnly.map { it.first })
    }

    // MARK: - anatomy mapping (iOS `anatomyNameCandidates`)

    @Test
    fun `anatomy id resolves through candidates most specific first`() {
        val taxonomy = mapOf("glutes" to "g-1", "core" to "c-1", "biceps brachii" to "b-1")
        val lookup: (String) -> String? = { taxonomy[it.lowercase()] }
        assertEquals("g-1", MeasurementMath.anatomyIdFor(BodyPart.HIPS, lookup))      // Hips → Glutes fallback
        assertEquals("c-1", MeasurementMath.anatomyIdFor(BodyPart.WAIST, lookup))     // Waist → Core fallback
        assertEquals("b-1", MeasurementMath.anatomyIdFor(BodyPart.RIGHT_BICEP, lookup))
        assertNull(MeasurementMath.anatomyIdFor(BodyPart.NECK, lookup))
    }

    @Test
    fun `anatomy names map back to body parts case-insensitively`() {
        assertEquals(BodyPart.HIPS, MeasurementMath.bodyPartFor("Gluteus Maximus"))
        assertEquals(BodyPart.WAIST, MeasurementMath.bodyPartFor("core"))
        assertEquals(BodyPart.LEFT_BICEP, MeasurementMath.bodyPartFor("Biceps"))   // laterality lost, as on iOS
        assertEquals(BodyPart.CALVES, MeasurementMath.bodyPartFor("Gastrocnemius"))
        assertNull(MeasurementMath.bodyPartFor("Trapezius"))
        assertNull(MeasurementMath.bodyPartFor(null))
    }

    @Test
    fun `server rows group by day into one entry per day`() {
        val d0 = Date(1_760_000_000_000L)
        val d0Later = Date(1_760_000_000_000L + 3_600_000L)
        val d1 = Date(1_760_000_000_000L + 86_400_000L)
        val rows = listOf(
            BodyMeasurementDto(id = "R1", anatomyId = "core", value = 80.0, logDate = d0),
            BodyMeasurementDto(id = "R2", anatomyId = "glutes", value = 95.0, logDate = d0Later),
            BodyMeasurementDto(id = "R3", anatomyId = "unknown", value = 1.0, logDate = d0Later),
            BodyMeasurementDto(id = "R4", anatomyId = "core", value = 79.0, logDate = d1)
        )
        val entries = rows.toMeasurementEntries { MeasurementMath.bodyPartFor(it) }
        assertEquals(2, entries.size)
        assertEquals("r4", entries[0].id)                       // newest first
        assertEquals(setOf(BodyPart.WAIST, BodyPart.HIPS), entries[1].measurements.map { it.bodyPart }.toSet())
        assertEquals("r1", entries[1].id)                       // earliest row of the day keys the entry
    }
}
