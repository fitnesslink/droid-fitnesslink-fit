package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.IntervalMode
import com.fitnesslink.fit.model.api.RestTypeId
import com.fitnesslink.fit.model.WeightUnit
import com.fitnesslink.fit.model.WorkoutTask
import com.fitnesslink.fit.session.SessionFixtures.group
import com.fitnesslink.fit.session.SessionFixtures.indexed
import com.fitnesslink.fit.session.SessionFixtures.intervalBlock
import com.fitnesslink.fit.session.SessionFixtures.movement
import com.fitnesslink.fit.session.SessionFixtures.restTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Prescription lines (FTNSSLNK-398 / -392 / -396 / -428 / -393). */
class SessionPrescriptionTests {

    @Before fun setUp() { WeightUnit.current = WeightUnit.LBS }

    @Test fun testPrepLineAdaptsToTheTask() {
        assertEquals("3 SETS × 10 REPS", SessionPrescription.line(movement("A", sets = 3, reps = 10)))
        assertEquals("30 SECONDS", SessionPrescription.line(movement("P", sets = 1, seconds = 30)))
        assertEquals("3 SETS × 30 SECONDS", SessionPrescription.line(movement("P", sets = 3, seconds = 30)))
        assertEquals("2 MINUTES", SessionPrescription.line(movement("P", sets = 1, seconds = 120)))
        assertEquals("8 REPS", SessionPrescription.line(movement("A", sets = 1, reps = 8)))
        assertEquals("3 SETS", SessionPrescription.line(movement("A", sets = 3, reps = 0)))
        assertNull(SessionPrescription.line(restTask("R", 60)))
        assertEquals("4 ROUNDS × 12 REPS", SessionPrescription.line(movement("B", reps = 12, group = "g"), groupRounds = 4))
        val hiit = intervalBlock(listOf("X"), group = "i", mode = IntervalMode.HIIT, work = 45, rest = 15, rounds = 6)
        assertEquals("HIIT · 5:45", SessionPrescription.line(hiit[0]))
    }

    @Test fun testCompactAndUnit() {
        assertEquals("3 × 8", SessionPrescription.compact(movement("A", sets = 3, reps = 8)))
        assertEquals("3 × 30s", SessionPrescription.compact(movement("A", sets = 3, seconds = 30)))
        assertEquals("8 reps", SessionPrescription.compact(movement("A", sets = 1, reps = 8)))
        assertEquals("30s", SessionPrescription.compact(movement("A", sets = 1, seconds = 30)))
        assertEquals("4 rounds × 8", SessionPrescription.compact(movement("A", reps = 8, group = "g"), groupRounds = 4))
        assertEquals("10 reps", SessionPrescription.unit(movement("A", reps = 10)))
        assertEquals("30s", SessionPrescription.unit(movement("A", seconds = 30)))
        assertNull(SessionPrescription.unit(movement("A", reps = 0)))
    }

    @Test fun testActiveTargetAdaptsToSetType() {
        assertEquals("12 REPS", SessionPrescription.target(movement("A", reps = 12), 1))
        assertEquals("12 REPS @ 135 lbs", SessionPrescription.target(movement("A", reps = 12, weight = 135), 1))
        assertEquals("30 SEC", SessionPrescription.target(movement("A", seconds = 30), 1))
        assertEquals("1:30", SessionPrescription.target(movement("A", seconds = 90), 1))
        assertNull(SessionPrescription.target(restTask("R", 30), 1))
        assertNull(SessionPrescription.target(intervalBlock(listOf("X"), group = "i")[0], 1))
        assertEquals("2 KM 10 MIN", SessionPrescription.target(movement("Run", seconds = 600).copy(distance = 2.0, distanceUnit = "km"), 1))
    }

    @Test fun testBlockSummaryForSupersetAndIntervals() {
        val superset = indexed(group(listOf("Bench", "Row"), group = "s", rounds = 3))
        val block = SessionPrescription.block(superset, 1)!!
        assertEquals(PreparationBlockSummary.Kind.Superset, block.kind)
        assertEquals("SUPERSET", block.title)
        assertEquals("3 ROUNDS", block.detail)
        assertEquals(listOf("Bench", "Row"), block.members.map { it.name })
        assertEquals(2, block.currentOrdinal)
        assertEquals("Superset, 3 rounds. First exercise: Bench, 10 reps. Second exercise: Row, 10 reps.", block.spoken)

        val hiit = indexed(intervalBlock(listOf("Burpees", "Squats"), group = "i", mode = IntervalMode.HIIT, work = 30, rest = 15, rounds = 4))
        val hb = SessionPrescription.block(hiit, 0)!!
        assertEquals("HIIT", hb.title)
        assertEquals("4 ROUNDS · 30 SEC WORK / 15 SEC REST", hb.detail)
        assertEquals("HIIT, 4 rounds, 30 seconds work, 15 seconds rest. Exercises: Burpees, Squats.", hb.spoken)

        assertNull(SessionPrescription.block(indexed(listOf(movement("Loose"))), 0))
    }

    @Test fun testRestHeadline() {
        assertEquals(SessionRestHeadline.Rest, SessionRestHeadline.headline(restTask("R", 60), isTransientRest = false))
        assertEquals("REST", SessionRestHeadline.Rest.title)
        val active = restTask("R", 60).copy(restTypeId = RestTypeId.activeRecovery, restMovementName = "Wall Sit")
        val headline = SessionRestHeadline.headline(active, isTransientRest = false)
        assertEquals("ACTIVE RECOVERY", headline.title)
        assertEquals("Wall Sit", headline.movementName)
        assertEquals("transient rests are always passive", SessionRestHeadline.Rest, SessionRestHeadline.headline(active, isTransientRest = true))
    }

    @Test fun testFinishAndDiscardRuleWording() {
        assertEquals("Finish Workout?", SessionFinishRule.title)
        assertEquals("You have 1 incomplete exercise remaining.", SessionFinishRule.message(1))
        assertEquals("You have 3 incomplete exercises remaining.", SessionFinishRule.message(3))
        assertEquals("Discard this session?", SessionDiscardRule.title)
        assertEquals(
            "Only this session is removed — your progress and logged sets so far. “Leg Day” stays in your library and on your calendar, ready to start again.",
            SessionDiscardRule.message("Leg Day")
        )
        assertEquals(
            "Only this session is removed — your progress and logged sets so far. The workout stays in your library and on your calendar, ready to start again.",
            SessionDiscardRule.message("")
        )
    }
}
