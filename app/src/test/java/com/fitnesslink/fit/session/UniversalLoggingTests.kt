package com.fitnesslink.fit.session

import com.fitnesslink.fit.model.DistanceUnit
import com.fitnesslink.fit.model.DistanceValue
import com.fitnesslink.fit.model.TaskSetConfig
import com.fitnesslink.fit.session.SessionFixtures.movement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Universal Workout Logging (PRD v2.0): the pure rules behind the shared logging screen. */
class UniversalLoggingTests {

    // MARK: - Metric profile (§5.4)

    @Test fun testStrengthProfileIsRepsAndWeight() {
        val p = LogMetricProfile.forTask(movement("A", name = "Dumbbell Squat"))
        assertEquals(listOf(LogMetric.REPS, LogMetric.WEIGHT), p.defaults)
        assertEquals(listOf(LogMetric.DURATION, LogMetric.DISTANCE), p.compatible)
    }

    @Test fun testTimedTaskLeadsWithDuration() {
        val p = LogMetricProfile.forTask(movement("P", name = "Plank", seconds = 45))
        assertEquals(listOf(LogMetric.DURATION), p.defaults)
        assertEquals(listOf(LogMetric.WEIGHT, LogMetric.REPS, LogMetric.DISTANCE), p.compatible)
    }

    @Test fun testCardioDistanceWorkIsDurationAndDistanceWithSpeedAndIncline() {
        val p = LogMetricProfile.forTask(movement("T", name = "Treadmill Walking"))
        assertEquals(listOf(LogMetric.DURATION, LogMetric.DISTANCE), p.defaults)
        assertTrue(p.compatible.containsAll(listOf(LogMetric.SPEED, LogMetric.INCLINE)))
        // Programmed speed makes it cardio whatever the name.
        val programmed = movement("X", name = "Station 3").copy(speed = 3.5)
        assertEquals(listOf(LogMetric.DURATION, LogMetric.DISTANCE), LogMetricProfile.forTask(programmed).defaults)
    }

    @Test fun testStrengthThatTravelsStaysStrength() {
        val p = LogMetricProfile.forTask(movement("L", name = "Walking Lunges"))
        assertEquals(listOf(LogMetric.REPS, LogMetric.WEIGHT), p.defaults)
        assertEquals(listOf(LogMetric.DISTANCE, LogMetric.DURATION), p.compatible)
        assertFalse(LogMetricProfile.isCardio(movement("F", name = "Farmer Carry Walk")))
        assertTrue(LogMetricProfile.isCardio(movement("R", name = "Easy Run")))
        assertTrue("Cardio anatomy tag", LogMetricProfile.isCardio(movement("B", name = "Assault Session"), listOf("Cardio")))
    }

    @Test fun testVisibleColumnsFollowDisplayOrderAndAddableExcludesShown() {
        val p = LogMetricProfile.forTask(movement("A", name = "Dumbbell Squat"))
        val visible = p.visible(added = listOf(LogMetric.DISTANCE), withValues = setOf(LogMetric.DURATION))
        assertEquals(listOf(LogMetric.DURATION, LogMetric.REPS, LogMetric.WEIGHT, LogMetric.DISTANCE), visible)
        assertTrue("All added → empty selector", p.addable(visible).isEmpty())
        assertEquals(listOf(LogMetric.DURATION, LogMetric.DISTANCE), p.addable(p.visible(emptyList(), emptySet())))
    }

    // MARK: - Values + validation (§11.1, §16.7)

    @Test fun testHasAnyValueAndMetricValues() {
        assertFalse(UniversalSetValues.hasAnyValue(TaskSetConfig(setNumber = 1, reps = 0)))
        assertTrue(UniversalSetValues.hasAnyValue(TaskSetConfig(setNumber = 1, reps = 0, rpe = 7)))
        assertTrue(UniversalSetValues.hasAnyValue(TaskSetConfig(setNumber = 1, reps = 0, incline = 2.0)))
        val c = TaskSetConfig(setNumber = 1, reps = 0, weightKg = 0.0, durationSeconds = 30)
        assertNull("0 reps is unset", UniversalSetValues.value(LogMetric.REPS, c))
        assertNull(UniversalSetValues.value(LogMetric.WEIGHT, c))
        assertEquals(30.0, UniversalSetValues.value(LogMetric.DURATION, c)!!, 0.0)
        assertEquals(setOf(LogMetric.DURATION), UniversalSetValues.metricsWithValues(listOf(c)))
    }

    @Test fun testClearingAndCarryForward() {
        val c = TaskSetConfig(setNumber = 2, reps = 8, weightKg = 50.0, distance = 1.0, distanceUnit = "km", rpe = 8, speedMph = 3.0, incline = 4.0)
        val noDistance = UniversalSetValues.clearing(LogMetric.DISTANCE, c)
        assertNull(noDistance.distance)
        assertNull(noDistance.distanceUnit)
        assertEquals(8, noDistance.reps)
        val next = UniversalSetValues.carryForward(c, 3)
        assertEquals(3, next.setNumber)
        assertEquals(8, next.reps)
        assertEquals(50.0, next.weightKg!!, 0.0)
        assertEquals(3.0, next.speedMph!!, 0.0)
        assertEquals(4.0, next.incline!!, 0.0)
        assertNull("Set RPE is not inherited", next.rpe)
    }

    @Test fun testParsingAndSanitizing() {
        assertEquals(0, UniversalSetValues.parseReps(""))
        assertEquals(12, UniversalSetValues.parseReps(" 12 "))
        assertNull(UniversalSetValues.parseReps("1.5"))
        assertNull(UniversalSetValues.parseReps("-3"))
        assertNull(UniversalSetValues.parseReps("abc"))
        assertEquals(22.5, UniversalSetValues.parseDecimal("22,5")!!, 0.0)
        assertEquals(0.0, UniversalSetValues.parseDecimal("")!!, 0.0)
        assertNull(UniversalSetValues.parseDecimal("1.2.3"))
        assertNull(UniversalSetValues.parseDecimal("-1"))
        assertNull(UniversalSetValues.parseDecimal("."))
        assertEquals("12.5", UniversalSetValues.sanitize("1a2,5.3", decimal = true).let { it.substring(0, 4) })
        assertEquals("12.53", UniversalSetValues.sanitize("1a2,5.3", decimal = true))
        assertEquals("1253", UniversalSetValues.sanitize("1a2,5.3", decimal = false))
        assertEquals("1:30", UniversalSetValues.formatDuration(90))
        assertEquals("0:05", UniversalSetValues.formatDuration(5))
        assertEquals("25", UniversalSetValues.formatNumber(25.0))
        assertEquals("2.5", UniversalSetValues.formatNumber(2.50))
        assertEquals("1.25", UniversalSetValues.formatNumber(1.249999))
    }

    // MARK: - Session set plan / prescribed targets (§6.6, §10.3)

    @Test fun testSetPlanMapping() {
        assertEquals(listOf(1, 2, 3), SessionSetPlan.programmed(3))
        assertTrue(SessionSetPlan.programmed(0).isEmpty())
        val plan = listOf(1, 3, 0)
        assertEquals(1, SessionSetPlan.templateSetNumber(plan, 1))
        assertEquals("Set 2 was deleted: session set 2 is programmed set 3", 3, SessionSetPlan.templateSetNumber(plan, 2))
        assertNull("Added set", SessionSetPlan.templateSetNumber(plan, 3))
        assertNull(SessionSetPlan.templateSetNumber(plan, 4))
    }

    @Test fun testPrescribedUsesPerSetConfigElseTaskPlan() {
        val varied = movement("A", sets = 3, reps = 10, weight = 50).copy(setConfigs = listOf(
            TaskSetConfig(setNumber = 1, reps = 12), TaskSetConfig(setNumber = 2, reps = 10, weightKg = 60.0), TaskSetConfig(setNumber = 3, reps = 8, weightKg = 70.0)
        ))
        val first = SessionSetPlan.prescribed(varied, listOf(1, 2, 3), 1)!!
        assertEquals(12, first.reps)
        assertEquals("Task weight fills a per-set gap", 50.0, first.weightKg!!, 0.0)
        val mapped = SessionSetPlan.prescribed(varied, listOf(1, 3), 2)!!
        assertEquals(2, mapped.setNumber)
        assertEquals(8, mapped.reps)
        assertNull(SessionSetPlan.prescribed(varied, listOf(1, 2, 3, 0), 4))

        val plain = SessionSetPlan.prescribed(movement("B", sets = 2, reps = 10), listOf(1, 2), 2)!!
        assertEquals(10, plain.reps)
        assertNull(plain.weightKg)
        assertNull(plain.rpe)

        val timed = SessionSetPlan.prescribed(movement("P", seconds = 45), listOf(1), 1)!!
        assertEquals(0, timed.reps)
        assertEquals(45, timed.durationSeconds)

        val treadmill = SessionSetPlan.prescribed(movement("T", reps = 0).copy(speed = 3.5, incline = 2.0, distance = 1.0, distanceUnit = "mi"), listOf(1), 1)!!
        assertEquals(3.5, treadmill.speedMph!!, 0.0)
        assertEquals(2.0, treadmill.incline!!, 0.0)
        assertEquals(1.0, treadmill.distance!!, 0.0)
        assertEquals("mi", treadmill.distanceUnit)

        assertNull("No target at all", SessionSetPlan.prescribed(movement("E", reps = 0), listOf(1), 1))
    }

    // MARK: - RPE scale (§7.4)

    @Test fun testRpeScale() {
        assertEquals(10, RPEScale.labels.size)
        assertEquals("Minimal Effort", RPEScale.level(1).label)
        assertEquals("Maximal Effort", RPEScale.level(10).label)
        assertEquals("Hard effort; could do 2–3 more reps.", RPEScale.level(7).explanation)
        assertTrue(RPEScale.level(7, cardio = true).explanation.contains("pace"))
        assertEquals(1, RPEScale.clamp(0))
        assertEquals(10, RPEScale.clamp(14))
        assertEquals(RPEScale.level(10), RPEScale.level(42))
    }

    // MARK: - Performance summary (§4, §16.9)

    private fun h(session: String, date: Long, set: Int, reps: Int, lbs: Double, seconds: Int? = null, distance: DistanceValue? = null) =
        HistoricalSet(session, date, set, reps, lbs, seconds, distance)

    @Test fun testLastTimeIsHeaviestSetOfMostRecentSessionTiesMostReps() {
        val sets = listOf(
            h("old", 100, 1, 5, 200.0),
            h("new", 200, 1, 8, 30.0), h("new", 200, 2, 10, 30.0), h("new", 200, 3, 6, 25.0)
        )
        val s = ExercisePerformanceSummary.build(sets)
        assertEquals("new", s.lastTime!!.sessionId)
        assertEquals(10, s.lastTime!!.reps)
        assertEquals("Top set across all history", 200.0, s.topSet!!.weightLbs, 0.0)
        assertEquals("30 lbs × 10", ExercisePerformanceSummary.weightRepsLine(s.lastTime!!))
    }

    @Test fun testTopSetTiesBreakOnRepsThenRecency() {
        val s = ExercisePerformanceSummary.build(listOf(h("a", 100, 1, 8, 100.0), h("b", 300, 1, 8, 100.0), h("c", 200, 1, 6, 100.0)))
        assertEquals("b", s.topSet!!.sessionId)
        val reps = ExercisePerformanceSummary.build(listOf(h("a", 100, 1, 9, 100.0), h("b", 300, 1, 8, 100.0)))
        assertEquals("a", reps.topSet!!.sessionId)
    }

    @Test fun testTimeAndDistanceCards() {
        val km = DistanceUnit.KM
        val sets = listOf(
            h("a", 100, 1, 0, 0.0, seconds = 90, distance = DistanceValue(2.0, DistanceUnit.MI)),
            h("b", 200, 1, 0, 0.0, seconds = 60, distance = DistanceValue(3.0, DistanceUnit.KM)),
            h("b", 200, 2, 0, 0.0, seconds = 75, distance = DistanceValue(1.0, DistanceUnit.KM))
        )
        val s = ExercisePerformanceSummary.build(sets, km)
        assertEquals(90, s.bestTime!!.durationSeconds)
        assertEquals("2 mi (3.22 km) beats 3 km", "a", s.bestDistance!!.sessionId)
        assertEquals("Longest duration of the latest session", 75, s.lastDuration!!.durationSeconds)
        assertEquals(3.0, s.lastDistance!!.distance!!.value, 0.0)
        assertNull("No weighted sets", s.lastTime)
        assertTrue(ExercisePerformanceSummary.build(emptyList()).isEmpty)
    }

    @Test fun testHistoryGroupsNewestFirstWithVolumeAndNotes() {
        val sets = listOf(h("S1", 100, 2, 10, 20.0), h("S1", 100, 1, 8, 25.0), h("S2", 300, 1, 5, 0.0))
        val grouped = ExerciseHistorySession.group(sets, mapOf("S1" to "Leg Day"), mapOf("s1" to ExerciseHistorySession.Note(7, "Felt good")))
        assertEquals(listOf("S2", "S1"), grouped.map { it.sessionId })
        assertEquals("Workout", grouped[0].workoutName)
        assertEquals("Leg Day", grouped[1].workoutName)
        assertEquals(listOf(1, 2), grouped[1].sets.map { it.setNumber })
        assertEquals(400.0, grouped[1].volumeLbs, 0.0)
        assertEquals(7, grouped[1].exerciseRPE)
        assertEquals("Felt good", grouped[1].trainingNotes)
        assertNull(grouped[0].exerciseRPE)
    }

    @Test fun testUnconfirmedCopy() {
        assertEquals("12 reps · 25 lbs", UnconfirmedSetItem.line(TaskSetConfig(setNumber = 1, reps = 12, weightKg = 25.0)))
        assertTrue(UnconfirmedSetItem.message(1).contains("1 set with recorded performance that hasn't"))
        assertTrue(UnconfirmedSetItem.message(3).contains("3 sets with recorded performance that haven't"))
    }

    // MARK: - SessionProgress drafts / plans / metrics (§6, §11.3)

    @Test fun testDraftUserModifiedLatchesAndClears() {
        val p = SessionProgress()
        val values = TaskSetConfig(setNumber = 1, reps = 10)
        p.setDraft("A", 1, values, userModified = false)
        assertFalse(p.isUserModified("A", 1))
        assertEquals(values, p.draft("A", 1))
        p.setDraft("A", 1, values.copy(reps = 12), userModified = true)
        p.setDraft("A", 1, values.copy(reps = 11), userModified = false)
        assertTrue("Once typed into, stays modified", p.isUserModified("A", 1))
        p.setDraft("A", 1, null, userModified = false)
        assertTrue("A cleared draft on an untouched set leaves nothing", p.entries.isEmpty())
        assertTrue(p.isEmpty)
    }

    @Test fun testUnconfirmedSetsOnlyUserModifiedIncompleteWithValues() {
        val p = SessionProgress()
        p.setDraft("B", 2, TaskSetConfig(setNumber = 2, reps = 8), userModified = true)
        p.setDraft("A", 3, TaskSetConfig(setNumber = 3, reps = 5), userModified = true)
        p.setDraft("A", 1, TaskSetConfig(setNumber = 1, reps = 5), userModified = false) // carry-forward
        p.setDraft("A", 2, TaskSetConfig(setNumber = 2, reps = 0), userModified = true) // blank
        p.setDraft("C", 1, TaskSetConfig(setNumber = 1, reps = 9), userModified = true)
        p.complete("C", 1) // completed sets never count
        assertEquals(
            listOf(SessionProgress.TaskIteration("A", 3), SessionProgress.TaskIteration("B", 2)),
            p.unconfirmedSets()
        )
        assertEquals(setOf(1, 2, 3), p.drafts("A").keys)
    }

    @Test fun testClearLogKeepsDraftedEntry() {
        val p = SessionProgress()
        p.log("A", 1)
        p.setDraft("A", 1, TaskSetConfig(setNumber = 1, reps = 6), userModified = true)
        p.clearLog("A", 1)
        assertEquals(SetState.UPCOMING, p.state("A", 1))
        assertNotNull("The draft survives unchecking", p.draft("A", 1))
        p.revertExercise("A", 1)
        assertNotNull(p.draft("A", 1))
    }

    @Test fun testRemoveIterationShiftsLaterSetsDown() {
        val p = SessionProgress()
        p.log("A", 1)
        p.skipSet("A", 2)
        p.setDraft("A", 3, TaskSetConfig(setNumber = 3, reps = 4), userModified = true)
        p.complete("A", 4)
        p.log("B", 2)
        p.removeIteration("A", 2, 4)
        assertEquals(SetState.COMPLETED, p.state("A", 1))
        assertEquals(4, p.draft("A", 2)!!.reps)
        assertEquals(SetState.COMPLETED, p.state("A", 3))
        assertEquals(SetState.UPCOMING, p.state("A", 4))
        assertTrue("Other tasks untouched", p.isLogged("B", 2))
        // Out of range is a no-op.
        val before = p.copy()
        p.removeIteration("A", 9, 3)
        assertEquals(before, p)
    }

    @Test fun testSetPlansAndAddedMetricsRoundTripAndOldJsonDecodes() {
        val p = SessionProgress()
        assertNull(p.encodedJSON())
        p.setSetPlan("A", listOf(1, 2, 0))
        p.setAddedMetrics("A", listOf("distance", "speed"))
        assertFalse("Plans alone keep the row", p.isEmpty)
        p.setDraft("A", 3, TaskSetConfig(setNumber = 3, reps = 7, rpe = 8, speedMph = 3.0), userModified = true)
        val decoded = SessionProgress.decode(p.encodedJSON())!!
        assertEquals(listOf(1, 2, 0), decoded.setPlan("A"))
        assertEquals(listOf("distance", "speed"), decoded.addedMetrics("A"))
        assertEquals(8, decoded.draft("A", 3)!!.rpe)
        assertEquals(3.0, decoded.draft("A", 3)!!.speedMph!!, 0.0)
        assertTrue(decoded.isUserModified("A", 3))

        val old = SessionProgress.decode("""{"entries":{"A#1":{"state":"completed","metricsLogged":true}}}""")!!
        assertEquals(SetState.COMPLETED, old.state("A", 1))
        assertNull(old.setPlan("A"))
        assertTrue(old.addedMetrics("A").isEmpty())
        assertNull(old.draft("A", 1))

        p.setAddedMetrics("A", emptyList())
        p.setSetPlan("A", null)
        assertTrue(p.setPlans.isEmpty())
        p.removeAll()
        assertTrue(p.isEmpty)
    }

    // MARK: - History rows (§11.3)

    @Test fun testHistoryRowsRecordUserModifiedDraftAsIncomplete() {
        val task = movement("A", sets = 3, reps = 10)
        val p = SessionProgress()
        p.setDraft("A", 1, TaskSetConfig(setNumber = 1, reps = 9), userModified = true)
        p.setDraft("A", 2, TaskSetConfig(setNumber = 2, reps = 9), userModified = false) // untouched carry-forward
        val rows = SessionHistoryRows.build(listOf(task), emptyMap(), p, mapOf("A" to 3))
        assertEquals(listOf(1), rows.map { it.setNumber })
        assertEquals(SessionSetStatus.INCOMPLETE, rows[0].status)
        assertFalse(rows[0].isPerformed)
        assertNull(rows[0].request("w", "u", java.util.Date(0)).reps)
    }

    @Test fun testHistoryRowsCarryUniversalExtrasAndRepTaskDurationRule() {
        val reps = movement("A", sets = 1, reps = 10)
        val timed = movement("P", sets = 1, seconds = 60)
        val p = SessionProgress()
        p.log("A", 1, durationSeconds = 42)
        p.log("P", 1, durationSeconds = 55)
        val logs = mapOf(
            "A" to mapOf(1 to TaskSetConfig(setNumber = 1, reps = 8, weightKg = 100.0, rpe = 8, speedMph = 2.0, incline = 1.5)),
            "P" to mapOf(1 to TaskSetConfig(setNumber = 1, reps = 0))
        )
        val rows = SessionHistoryRows.build(listOf(reps, timed), logs, p, mapOf("A" to 1, "P" to 1))
        val a = rows.first { it.task.id == "A" }
        assertEquals(8, a.setRpe)
        assertEquals(2.0, a.speedMph!!, 0.0)
        assertEquals(1.5, a.incline!!, 0.0)
        assertNull("A rep task's set timer isn't a logged duration", a.durationSeconds)
        assertNull(a.intervalSeconds)
        val pRow = rows.first { it.task.id == "P" }
        assertEquals("Timed task falls back to the set timer", 55, pRow.durationSeconds)
        assertEquals(55, pRow.intervalSeconds)

        // A rep task with a logged Duration (+ Add Metric) posts it.
        val withTime = SessionHistoryRows.build(listOf(reps), mapOf("A" to mapOf(1 to TaskSetConfig(setNumber = 1, reps = 8, durationSeconds = 30))), p, mapOf("A" to 1))
        assertEquals(30, withTime[0].intervalSeconds)
    }
}
