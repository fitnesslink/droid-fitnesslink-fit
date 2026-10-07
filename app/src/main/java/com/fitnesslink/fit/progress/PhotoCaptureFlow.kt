package com.fitnesslink.fit.progress

import com.fitnesslink.fit.model.PhotoAngle

/** One segment of the 3-step progress bar at the top of the capture flow. */
enum class CaptureSegment { DONE, CURRENT, PENDING }

/**
 * Immutable state of the Front → Side → Back capture flow (iOS
 * `PhotoCaptureViewModel`): which angle is up, which have a photo, and
 * whether the user has asked to finish. Kept free of Android types so the
 * transitions are unit-testable; the view model holds the URIs.
 */
data class PhotoCaptureState(
    val currentAngle: PhotoAngle = PhotoAngle.FRONT,
    val captured: Set<PhotoAngle> = emptySet(),
    val isComplete: Boolean = false
) {
    val anglesCompleted: Int get() = captured.size
    val hasCurrentPhoto: Boolean get() = currentAngle in captured

    /** "Save N Photos Now" is offered once something is captured and before the last step (iOS). */
    val canFinishEarly: Boolean get() = anglesCompleted >= 1 && currentAngle != PhotoAngle.BACK

    /** iOS: "Finish" on the back angle, otherwise "Next: {angle}". */
    val advanceLabel: String
        get() = currentAngle.next?.let { "Next: ${it.displayName}" } ?: "Finish"

    fun segments(): List<CaptureSegment> = PhotoAngle.entries.map { angle ->
        when {
            angle in captured -> CaptureSegment.DONE
            angle == currentAngle -> CaptureSegment.CURRENT
            else -> CaptureSegment.PENDING
        }
    }
}

object PhotoCaptureFlow {
    fun capture(state: PhotoCaptureState, angle: PhotoAngle = state.currentAngle): PhotoCaptureState =
        state.copy(captured = state.captured + angle)

    fun retake(state: PhotoCaptureState): PhotoCaptureState =
        state.copy(captured = state.captured - state.currentAngle)

    /** Next angle, or complete after Back (iOS `moveToNextAngle`). */
    fun advance(state: PhotoCaptureState): PhotoCaptureState {
        val next = state.currentAngle.next
        return if (next != null) state.copy(currentAngle = next) else state.copy(isComplete = true)
    }

    /** Skip is just advance without a photo (iOS `skipAngle`). Skipping the last angle with nothing captured stays put. */
    fun skip(state: PhotoCaptureState): PhotoCaptureState {
        if (state.currentAngle == PhotoAngle.BACK && state.anglesCompleted == 0) return state
        return advance(state)
    }

    /** iOS `finishNow`: needs at least one captured angle. */
    fun finishNow(state: PhotoCaptureState): PhotoCaptureState =
        if (state.anglesCompleted >= 1) state.copy(isComplete = true) else state

    /** Reopen the flow after a failed upload (iOS sets `isComplete = false`). */
    fun reopen(state: PhotoCaptureState): PhotoCaptureState = state.copy(isComplete = false)

    /**
     * iOS `BodyTrackingService.entries(from:)` angle rule for one day's rows
     * in upload order: a persisted angle wins, otherwise position → front,
     * side, back (wrapping for legacy over-long days).
     */
    fun inferAngles(wireAngles: List<String?>): List<PhotoAngle> =
        wireAngles.mapIndexed { index, raw ->
            PhotoAngle.fromWire(raw) ?: PhotoAngle.entries[index % PhotoAngle.entries.size]
        }
}
