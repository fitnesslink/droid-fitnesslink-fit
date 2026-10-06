package com.fitnesslink.fit.network.dto

import java.util.Date

// Wire shapes for /program-enrollments (iOS `ProgramEnrollmentService.swift`,
// `UserProgramStore.EnrollmentWire`). The `POST/PUT /programs` body is
// `com.fitnesslink.fit.model.ProgramWriteRequest`.

/**
 * `POST /program-enrollments` body: an immutable, denormalized snapshot of
 * a "user added this program to their calendar" event (FTNSSLNK-164/166).
 * Append-only and payout-critical — queue durably, replay until 2xx.
 */
data class ProgramEnrollmentRecord(
    val enrollmentId: String,
    val userId: String,
    val programId: String,
    val contributorId: String? = null,
    val startDate: Date,
    val programName: String,
    val splitId: String? = null,
    val weeks: Int? = null,
    val workoutCount: Int,
    val source: String = "Android",
    val appVersion: String? = null,
    val status: String = "active"
)

/** `POST /program-enrollments/{id}/removal` body (the server flips the row to removed). */
data class ProgramEnrollmentRemovalRequest(
    val enrollmentId: String
)

/** `GET /program-enrollments/me` row (iOS `UserProgramStore.EnrollmentWire`). */
data class ProgramEnrollmentDto(
    val enrollmentId: String,
    val programId: String,
    val startDate: Date,
    val programName: String? = null,
    val weeks: Int? = null,
    /** "active" | "removed". */
    val status: String = ""
)
