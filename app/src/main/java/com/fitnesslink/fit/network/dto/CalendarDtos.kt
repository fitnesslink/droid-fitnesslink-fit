package com.fitnesslink.fit.network.dto

import java.util.Date

// Wire shapes for /calendar. Mirrors iOS `CalendarService.swift`
// (`CreateCalendarWire` / `UpdateCalendarWire` / `CalendarReadWire`).

/**
 * `POST /calendar` (204). The client owns `id`. Empty strings are sent as
 * null so the server stores proper nulls. `durationMinutes` is the planned
 * length used for the device-calendar mirror; `programEnrollmentId` +
 * `programWeekNumber` / `programDayNumber` tie a row to a program instance.
 */
data class CreateCalendarEntryRequest(
    val id: String,
    val scheduledDate: Date,
    val workoutId: String,
    val programId: String? = null,
    val mealPlanId: String? = null,
    val title: String? = null,
    /** "scheduled" | "completed" | … */
    val status: String? = null,
    val programEnrollmentId: String? = null,
    val durationMinutes: Int? = null,
    val programWeekNumber: Int? = null,
    val programDayNumber: Int? = null
)

/**
 * `PUT /calendar/{id}` (204). Null fields are left unchanged, so a
 * reschedule sends only `scheduledDate`.
 */
data class UpdateCalendarEntryRequest(
    val scheduledDate: Date? = null,
    val title: String? = null,
    val status: String? = null,
    val durationMinutes: Int? = null
)

/** `GET /calendar/me?startDate=&endDate=` row (server `CalendarEntryDto`). */
data class CalendarEntryDto(
    val id: String,
    val scheduledDate: Date? = null,
    val workoutId: String? = null,
    val programId: String? = null,
    val mealPlanId: String? = null,
    val title: String? = null,
    val status: String? = null,
    val workoutImageId: String? = null,
    val workoutThumbnailId: String? = null,
    val programImageId: String? = null,
    val programThumbnailId: String? = null,
    val programEnrollmentId: String? = null,
    val durationMinutes: Int? = null,
    val programWeekNumber: Int? = null,
    val programDayNumber: Int? = null
)
