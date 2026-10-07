package com.fitnesslink.fit.progress

import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.network.dto.ProgressPhotoDto
import com.fitnesslink.fit.network.dto.toProgressPhotoEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PhotoCaptureFlowTests {

    // MARK: - angle inference (iOS `BodyTrackingService.entries(from:)`)

    @Test
    fun `persisted angles win regardless of position`() {
        assertEquals(listOf(PhotoAngle.BACK, PhotoAngle.FRONT), PhotoCaptureFlow.inferAngles(listOf("Back", "front")))
    }

    @Test
    fun `missing angles fall back to upload order front side back`() {
        assertEquals(listOf(PhotoAngle.FRONT, PhotoAngle.SIDE, PhotoAngle.BACK), PhotoCaptureFlow.inferAngles(listOf(null, null, null)))
        assertEquals(listOf(PhotoAngle.FRONT, PhotoAngle.SIDE, PhotoAngle.BACK, PhotoAngle.FRONT), PhotoCaptureFlow.inferAngles(listOf(null, null, "bogus", null)))
    }

    @Test
    fun `mixed rows infer only the gaps`() {
        assertEquals(listOf(PhotoAngle.FRONT, PhotoAngle.BACK, PhotoAngle.BACK), PhotoCaptureFlow.inferAngles(listOf(null, "Back", null)))
    }

    @Test
    fun `PhotoAngle fromWire accepts display and enum spellings`() {
        assertEquals(PhotoAngle.SIDE, PhotoAngle.fromWire("Side"))
        assertEquals(PhotoAngle.SIDE, PhotoAngle.fromWire("SIDE"))
        assertNull(PhotoAngle.fromWire(null))
        assertNull(PhotoAngle.fromWire("Diagonal"))
    }

    @Test
    fun `server rows group by day and infer angles in createdAt order`() {
        val day = 1_760_000_000_000L
        val rows = listOf(
            ProgressPhotoDto(id = "B", beforeImageId = "m2", logDate = Date(day), angle = null, createdAt = Date(day + 2_000)),
            ProgressPhotoDto(id = "A", beforeImageId = "m1", logDate = Date(day), angle = null, createdAt = Date(day + 1_000)),
            ProgressPhotoDto(id = "C", beforeImageId = "m3", logDate = Date(day + 86_400_000L), angle = "Back", createdAt = Date(day + 86_400_000L)),
            ProgressPhotoDto(id = "D", beforeImageId = null, logDate = null, angle = "Front", createdAt = Date(day))
        )
        val entries = rows.toProgressPhotoEntries()
        assertEquals(2, entries.size)
        val older = entries[1]
        assertEquals("a", older.id)
        assertEquals(listOf(PhotoAngle.FRONT, PhotoAngle.SIDE), older.photos.map { it.angle })
        assertEquals("m1", older.photo(PhotoAngle.FRONT)!!.imageId)
        assertEquals(PhotoAngle.BACK, entries[0].photos.single().angle)
    }

    // MARK: - capture state machine (iOS `PhotoCaptureViewModel`)

    @Test
    fun `flow walks front side back and completes after back`() {
        var s = PhotoCaptureState()
        assertEquals("Next: Side", s.advanceLabel)
        s = PhotoCaptureFlow.capture(s)
        assertTrue(s.hasCurrentPhoto)
        s = PhotoCaptureFlow.advance(s)
        assertEquals(PhotoAngle.SIDE, s.currentAngle)
        s = PhotoCaptureFlow.advance(PhotoCaptureFlow.capture(s))
        assertEquals(PhotoAngle.BACK, s.currentAngle)
        assertEquals("Finish", s.advanceLabel)
        s = PhotoCaptureFlow.advance(PhotoCaptureFlow.capture(s))
        assertTrue(s.isComplete)
        assertEquals(3, s.anglesCompleted)
    }

    @Test
    fun `skip advances without a photo and cannot skip the last angle with nothing captured`() {
        var s = PhotoCaptureFlow.skip(PhotoCaptureState())
        assertEquals(PhotoAngle.SIDE, s.currentAngle)
        assertEquals(0, s.anglesCompleted)
        s = PhotoCaptureFlow.skip(s)
        assertEquals(PhotoAngle.BACK, s.currentAngle)
        val stuck = PhotoCaptureFlow.skip(s)
        assertEquals(s, stuck)
        assertFalse(stuck.isComplete)
    }

    @Test
    fun `finish early needs one capture and is offered before the back step only`() {
        val empty = PhotoCaptureState()
        assertFalse(empty.canFinishEarly)
        assertFalse(PhotoCaptureFlow.finishNow(empty).isComplete)
        val one = PhotoCaptureFlow.capture(empty)
        assertTrue(one.canFinishEarly)
        assertTrue(PhotoCaptureFlow.finishNow(one).isComplete)
        val onBack = PhotoCaptureFlow.advance(PhotoCaptureFlow.advance(one))
        assertEquals(PhotoAngle.BACK, onBack.currentAngle)
        assertFalse(onBack.canFinishEarly)
    }

    @Test
    fun `retake clears only the current angle and segments reflect state`() {
        var s = PhotoCaptureFlow.advance(PhotoCaptureFlow.capture(PhotoCaptureState()))
        s = PhotoCaptureFlow.capture(s)
        s = PhotoCaptureFlow.retake(s)
        assertEquals(setOf(PhotoAngle.FRONT), s.captured)
        assertEquals(listOf(CaptureSegment.DONE, CaptureSegment.CURRENT, CaptureSegment.PENDING), s.segments())
    }

    @Test
    fun `reopen after a failed upload clears completion but keeps captures`() {
        val done = PhotoCaptureFlow.finishNow(PhotoCaptureFlow.capture(PhotoCaptureState()))
        val reopened = PhotoCaptureFlow.reopen(done)
        assertFalse(reopened.isComplete)
        assertEquals(1, reopened.anglesCompleted)
    }
}
