package com.fitnesslink.fit.session

import com.fitnesslink.fit.session.UpNextDescriptor.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of the pure half of iOS `UpNextDescriptorTests` (FTNSSLNK-392). */
class UpNextDescriptorTests {

    @Test fun testTitleForms() {
        val set = UpNextDescriptor(taskId = "1", name = "Ab Crunch", kind = Kind.NextSet(2, 3))
        assertEquals("Set 2 — Ab Crunch", set.title)

        val exercise = UpNextDescriptor(taskId = "2", name = "Barbell Squat", prescription = "3 × 8", kind = Kind.NextExercise)
        assertEquals("Barbell Squat · 3 × 8", exercise.title)
        assertEquals("Barbell Squat", UpNextDescriptor(taskId = "2", name = "Barbell Squat", kind = Kind.NextExercise).title)

        val round = UpNextDescriptor(taskId = "3", name = "Goblet Squat", kind = Kind.NextRound(2, 4, "A1"))
        assertEquals("Round 2 — A1 Goblet Squat", round.title)
        assertEquals("Round 2 — Goblet Squat", UpNextDescriptor(taskId = "3", name = "Goblet Squat", kind = Kind.NextRound(2, 4, null)).title)

        val member = UpNextDescriptor(taskId = "4", name = "Bench Press", prescription = "10 reps", kind = Kind.NextMember("A2"))
        assertEquals("A2 Bench Press · 10 reps", member.title)

        val work = UpNextDescriptor(taskId = "5", name = "Burpees", kind = Kind.Interval("Work", 20))
        assertEquals("Work · Burpees · 20 s", work.title)
        assertEquals("Work · Burpees · 10:00", UpNextDescriptor(taskId = "5", name = "Burpees", kind = Kind.Interval("Work", 600)).title)

        assertEquals("Starts in 12s", UpNextDescriptor(taskId = "1", name = "X", kind = Kind.NextExercise, startsInSeconds = 12).startsInText)
        assertEquals("Starts in 1:30", UpNextDescriptor(taskId = "1", name = "X", kind = Kind.NextExercise, startsInSeconds = 90).startsInText)
        assertNull(UpNextDescriptor(taskId = "1", name = "X", kind = Kind.NextExercise, startsInSeconds = 0).startsInText)
    }
}
