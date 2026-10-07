package com.fitnesslink.fit.session

import com.fitnesslink.fit.session.SessionCueOutput.Beep
import com.fitnesslink.fit.session.SessionCueOutput.Chime
import com.fitnesslink.fit.session.SessionCueOutput.Ding
import com.fitnesslink.fit.session.SessionCueOutput.Haptic
import com.fitnesslink.fit.session.SessionCueOutput.Speak
import com.fitnesslink.fit.session.SessionCueOutput.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `SessionCueTests` (FTNSSLNK-415) minus the AVSpeech-specific voice checks. */
class SessionCueTests {

    private val allOn = SessionCuePolicy()

    @Test fun testPrepStartSpeaksUpNextAfterAnyPhaseChange() {
        val plain = allOn.outputs(SessionCueEvent.PrepStarted("Barbell Squat", "3 sets of 10 reps", null))
        assertEquals(listOf(Speak(VoicePhrase.UpNext("Barbell Squat", "3 sets of 10 reps"))), plain)
        assertEquals("Up next: Barbell Squat, 3 sets of 10 reps. Get ready.", VoicePhrase.UpNext("Barbell Squat", "3 sets of 10 reps").text)
        assertEquals("Up next: Rest. Get ready.", VoicePhrase.UpNext("Rest", null).text)

        val boundary = allOn.outputs(SessionCueEvent.PrepStarted("Plank", "30 seconds", PhaseChangeCue("Warm-Up", "Exercises")))
        assertEquals(listOf(
            Speak(VoicePhrase.PhaseTransition("Warm-Up", "Exercises")),
            Speak(VoicePhrase.UpNext("Plank", "30 seconds"))
        ), boundary)
        assertEquals("Warm-Up complete. Next phase: Exercises.", VoicePhrase.PhaseTransition("Warm-Up", "Exercises").text)
    }

    @Test fun testModeEntryIsAnnouncedFirstThenPhaseThenStructure() {
        val structure = "Superset, 3 rounds. First exercise: Bench Press, 10 reps. Second exercise: Row, 10 reps."
        val entry = allOn.outputs(SessionCueEvent.PrepStarted("Bench Press", null, PhaseChangeCue("Warm-Up", "Strength"), structure, modeEntry = true))
        assertEquals(listOf(
            Speak(VoicePhrase.InteractiveModeEntered),
            Speak(VoicePhrase.PhaseTransition("Warm-Up", "Strength")),
            Speak(VoicePhrase.UpNext("Bench Press", null, structure))
        ), entry)
        assertEquals("You have entered Interactive Mode.", VoicePhrase.InteractiveModeEntered.text)
        assertEquals(
            "Up next: Superset, 3 rounds. First exercise: Bench Press, 10 reps. Get ready.",
            VoicePhrase.UpNext("Bench Press", null, "Superset, 3 rounds. First exercise: Bench Press, 10 reps.").text
        )
    }

    @Test fun testRestBlockNamesTheNextPhaseAndASetAdvanceIsSpoken() {
        assertEquals("Rest, 1 minute. Next phase: Core. Up next: Ab Crunch, 3 of 10.", VoicePhrase.RestFor(60, "Ab Crunch, 3 of 10", "Core").text)
        assertEquals(
            listOf(Ding, Haptic(SessionHaptic.TRANSITION), Speak(VoicePhrase.RestFor(60, "Ab Crunch", "Core"))),
            allOn.outputs(SessionCueEvent.RestStarted(60, "Ab Crunch", "Core"))
        )
        assertEquals(listOf(Haptic(SessionHaptic.TRANSITION), Speak(VoicePhrase.SetAdvanced(2, 3))), allOn.outputs(SessionCueEvent.SetAdvanced(2, 3)))
        assertEquals("Set 2 of 3", VoicePhrase.SetAdvanced(2, 3).text)
        assertEquals(VoicePhrase.Priority.QUEUE, VoicePhrase.SetAdvanced(2, 3).priority)
    }

    @Test fun testPrepCountdownBeepsAndSpeaksThreeTwoOneThenGo() {
        assertEquals(emptyList<SessionCueOutput>(), allOn.outputs(SessionCueEvent.PrepTick(5)))
        assertEquals(listOf(Beep, Haptic(SessionHaptic.TICK), Speak(VoicePhrase.Three)), allOn.outputs(SessionCueEvent.PrepTick(3)))
        assertEquals(listOf(Beep, Haptic(SessionHaptic.TICK), Speak(VoicePhrase.One)), allOn.outputs(SessionCueEvent.PrepTick(1)))
        assertEquals(listOf(Ding, Haptic(SessionHaptic.TRANSITION), Speak(VoicePhrase.Go)), allOn.outputs(SessionCueEvent.PrepEnded))
        assertEquals("Go", VoicePhrase.Go.text)
    }

    @Test fun testRestAnnouncesLengthAndUpNextThenCountsDown() {
        assertEquals(
            listOf(Ding, Haptic(SessionHaptic.TRANSITION), Speak(VoicePhrase.RestFor(30, "Set 2, Barbell Squat"))),
            allOn.outputs(SessionCueEvent.RestStarted(30, "Set 2, Barbell Squat"))
        )
        assertEquals("Rest, 30 seconds. Up next: Set 2, Barbell Squat.", VoicePhrase.RestFor(30, "Set 2, Barbell Squat").text)
        assertEquals("Rest, 1 minute 30 seconds.", VoicePhrase.RestFor(90, null).text)
        assertEquals("Rest, 2 minutes. Up next: Bench.", VoicePhrase.RestFor(120, "Bench").text)
        assertEquals(listOf(Beep, Haptic(SessionHaptic.TICK), Speak(VoicePhrase.Two)), allOn.outputs(SessionCueEvent.RestTick(2)))
        assertEquals(emptyList<SessionCueOutput>(), allOn.outputs(SessionCueEvent.RestTick(10)))
    }

    @Test fun testTimedSetHalfwayThenBeepsThenDone() {
        assertEquals(listOf(Speak(VoicePhrase.Halfway)), allOn.outputs(SessionCueEvent.TimedSetTick(15, 30)))
        assertEquals(emptyList<SessionCueOutput>(), allOn.outputs(SessionCueEvent.TimedSetTick(14, 30)))
        assertEquals("no spoken count into a rest", listOf(Beep, Haptic(SessionHaptic.TICK)), allOn.outputs(SessionCueEvent.TimedSetTick(3, 30)))
        assertEquals("too short for a halfway call", emptyList<SessionCueOutput>(), allOn.outputs(SessionCueEvent.TimedSetTick(4, 8)))
        assertEquals(listOf(Ding, Haptic(SessionHaptic.TRANSITION), Speak(VoicePhrase.Done)), allOn.outputs(SessionCueEvent.TimedSetEnded))
        assertEquals("Done", VoicePhrase.Done.text)
    }

    @Test fun testSpokenTextSoftensPunctuationButLeavesCanonicalTextAlone() {
        val prep = VoicePhrase.UpNext("Barbell Squat", "3 sets of 10 reps")
        assertEquals("Up next: Barbell Squat, 3 sets of 10 reps. Get ready.", prep.text)
        assertEquals("Up next, Barbell Squat, 3 sets of 10 reps. Get ready.", prep.spokenText)
        assertEquals("Rest, 1 minute. Next phase, Core. Up next, Set 2, Ab Crunch.", VoicePhrase.RestFor(60, "Set 2 — Ab Crunch", "Core").spokenText)
        assertEquals("3", VoicePhrase.Three.spokenText)
    }

    @Test fun testLongPhrasesReadSlowerThanShortCues() {
        assertEquals(1.0f, VoicePhrase.Three.speechRate)
        assertEquals(1.0f, VoicePhrase.Begin.speechRate)
        assertTrue(VoicePhrase.UpNext("Squat", null).speechRate < 1.0f)
        assertTrue(VoicePhrase.RestFor(30, null).speechRate < 1.0f)
    }

    @Test fun testSkipIsAToneWithoutSpeechAndCompleteIsAChime() {
        assertEquals(listOf(Tone, Haptic(SessionHaptic.SELECTION)), allOn.outputs(SessionCueEvent.Skipped))
        assertEquals(listOf(Chime, Haptic(SessionHaptic.SUCCESS), Speak(VoicePhrase.WorkoutComplete)), allOn.outputs(SessionCueEvent.WorkoutComplete))
    }

    @Test fun testMasterMuteSilencesAudioButKeepsHapticsAndNeverBlocksEvents() {
        val muted = SessionCuePolicy(isMasterMuted = true)
        assertEquals(emptyList<SessionCueOutput>(), muted.outputs(SessionCueEvent.PrepStarted("X", null, null)))
        assertEquals(listOf(Haptic(SessionHaptic.TICK)), muted.outputs(SessionCueEvent.PrepTick(3)))
        assertEquals(listOf(Haptic(SessionHaptic.TRANSITION)), muted.outputs(SessionCueEvent.RestStarted(30, null)))
        assertEquals(listOf(Haptic(SessionHaptic.SELECTION)), muted.outputs(SessionCueEvent.Skipped))
        assertEquals(listOf(Haptic(SessionHaptic.SUCCESS)), muted.outputs(SessionCueEvent.WorkoutComplete))
    }

    @Test fun testPerChannelPreferences() {
        val noVoice = SessionCuePolicy(voiceEnabled = false)
        assertEquals(listOf(Beep, Haptic(SessionHaptic.TICK)), noVoice.outputs(SessionCueEvent.PrepTick(3)))
        assertEquals(listOf(Ding, Haptic(SessionHaptic.TRANSITION)), noVoice.outputs(SessionCueEvent.RestStarted(30, "X")))

        val noSounds = SessionCuePolicy(soundsEnabled = false)
        assertEquals(listOf(Haptic(SessionHaptic.TICK), Speak(VoicePhrase.Three)), noSounds.outputs(SessionCueEvent.PrepTick(3)))
        assertEquals(listOf(Haptic(SessionHaptic.SELECTION)), noSounds.outputs(SessionCueEvent.Skipped))

        val noHaptics = SessionCuePolicy(hapticsEnabled = false)
        assertEquals(listOf(Tone), noHaptics.outputs(SessionCueEvent.Skipped))
        assertEquals(listOf(Ding, Speak(VoicePhrase.Go)), noHaptics.outputs(SessionCueEvent.PrepEnded))

        val silent = SessionCuePolicy(soundsEnabled = false, voiceEnabled = false, hapticsEnabled = false)
        assertEquals(emptyList<SessionCueOutput>(), silent.outputs(SessionCueEvent.WorkoutComplete))
    }

    @Test fun testSchedulerAnnouncesHalfwayOncePerSetAndOneCuePerSecond() {
        var now = 1_700_000_000_000L
        val scheduler = SessionCueScheduler(allOn) { now }
        assertEquals(listOf(Speak(VoicePhrase.Halfway)), scheduler.cues(SessionCueEvent.TimedSetTick(15, 30)))
        assertEquals("same second, no repeat", emptyList<SessionCueOutput>(), scheduler.cues(SessionCueEvent.TimedSetTick(15, 30)))
        assertEquals(listOf(Beep, Haptic(SessionHaptic.TICK)), scheduler.cues(SessionCueEvent.TimedSetTick(3, 30)))
        assertEquals(emptyList<SessionCueOutput>(), scheduler.cues(SessionCueEvent.TimedSetTick(3, 30)))
        assertEquals(emptyList<SessionCueOutput>(), scheduler.cues(SessionCueEvent.TimedSetTick(15, 30)))
        scheduler.resetCountdown()
        assertEquals(listOf(Speak(VoicePhrase.Halfway)), scheduler.cues(SessionCueEvent.TimedSetTick(15, 30)))
        now += 1000
        assertEquals(3, scheduler.cues(SessionCueEvent.RestStarted(30, null)).size)
        assertEquals(3, scheduler.cues(SessionCueEvent.RestTick(3)).size)
        assertEquals(emptyList<SessionCueOutput>(), scheduler.cues(SessionCueEvent.RestTick(3)))
    }

    @Test fun testSchedulerRateLimitsSkipTonesOnTheClock() {
        var now = 1_700_000_000_000L
        val scheduler = SessionCueScheduler(allOn) { now }
        assertEquals(listOf(Tone, Haptic(SessionHaptic.SELECTION)), scheduler.cues(SessionCueEvent.Skipped))
        now += 100
        assertEquals("a double-tap within 0.3 s plays once", emptyList<SessionCueOutput>(), scheduler.cues(SessionCueEvent.Skipped))
        now += 500
        assertEquals(listOf(Tone, Haptic(SessionHaptic.SELECTION)), scheduler.cues(SessionCueEvent.Skipped))

        val muted = SessionCueScheduler(SessionCuePolicy(isMasterMuted = true)) { now }
        assertEquals("mute drops the tone, keeps the tap", listOf(Haptic(SessionHaptic.SELECTION)), muted.cues(SessionCueEvent.Skipped))
    }

    @Test fun testSpokenPrescriptionAndUpNext() {
        assertEquals("3 sets of 10 reps", SessionCuePhrasing.spokenPrescription("3 SETS × 10 REPS"))
        assertEquals("30 seconds", SessionCuePhrasing.spokenPrescription("30 SECONDS"))
        assertEquals("4 rounds of 12 reps", SessionCuePhrasing.spokenPrescription("4 ROUNDS × 12 REPS"))
        assertEquals("HIIT, 5:45", SessionCuePhrasing.spokenPrescription("HIIT · 5:45"))
        assertEquals("Tabata, 3:50", SessionCuePhrasing.spokenPrescription("TABATA · 3:50"))
        assertNull(SessionCuePhrasing.spokenPrescription(null))
        assertNull(SessionCuePhrasing.spokenPrescription("  "))
        assertEquals("Set 2, Ab Crunch", SessionCuePhrasing.spokenUpNext("Set 2 — Ab Crunch"))
        assertEquals("A2 Bench Press, 10 reps", SessionCuePhrasing.spokenUpNext("A2 Bench Press · 10 reps"))
        assertEquals("Barbell Squat, 3 of 8", SessionCuePhrasing.spokenUpNext("Barbell Squat · 3 × 8"))
        assertNull(SessionCuePhrasing.spokenUpNext(null))
    }

    @Test fun testPhrasePrioritiesKeepShortCuesIntact() {
        assertEquals(VoicePhrase.Priority.DROP_IF_BUSY, VoicePhrase.Three.priority)
        assertEquals(VoicePhrase.Priority.QUEUE, VoicePhrase.RestFor(30, null).priority)
        assertEquals(VoicePhrase.Priority.QUEUE, VoicePhrase.UpNext("X", null).priority)
        assertEquals(VoicePhrase.Priority.INTERRUPT, VoicePhrase.Go.priority)
        assertTrue(VoicePhrase.Done.isShort)
        assertFalse(VoicePhrase.RestFor(30, null).isShort)
        assertFalse(VoicePhrase.One.interruptsSpeech)
        assertTrue(VoicePhrase.WorkoutComplete.interruptsSpeech)
        assertEquals(VoicePhrase.Two, VoicePhrase.countdown(2))
        assertNull(VoicePhrase.countdown(4))
        assertEquals("1 second", VoicePhrase.spokenSeconds(1))
        assertEquals("1 minute", VoicePhrase.spokenSeconds(60))
        assertEquals("1 minute 1 second", VoicePhrase.spokenSeconds(61))
    }

    @Test fun testIntervalRunnerEventsRouteThroughThePolicy() {
        assertEquals(
            listOf(Ding, Haptic(SessionHaptic.TRANSITION), Speak(VoicePhrase.FinalRound)),
            IntervalCueRouter.outputs(IntervalRunner.Event.TransitionToWork, finalRound = true, leadsToWork = false, policy = allOn)
        )
        assertEquals(
            listOf(Beep, Haptic(SessionHaptic.TICK), Speak(VoicePhrase.Two)),
            IntervalCueRouter.outputs(IntervalRunner.Event.Tick(2), finalRound = false, leadsToWork = true, policy = allOn)
        )
        assertEquals(
            listOf(Beep, Haptic(SessionHaptic.TICK)),
            IntervalCueRouter.outputs(IntervalRunner.Event.Tick(2), finalRound = false, leadsToWork = false, policy = allOn)
        )
        assertEquals(listOf(Speak(VoicePhrase.GetReady)), IntervalCueRouter.outputs(IntervalRunner.Event.PrepareStarted, false, true, allOn))
    }
}
