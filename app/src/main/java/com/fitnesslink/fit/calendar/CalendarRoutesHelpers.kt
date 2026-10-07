package com.fitnesslink.fit.calendar

import com.fitnesslink.fit.model.FitnessContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iOS `FitnessContent.tapRoute` (Models/FitnessContent.swift): a completed
 * entry opens its workout report for that day, anything else opens the
 * workout. Route strings match `SessionRoutes` (`completedWorkoutReport/{workoutId}/{day}`,
 * day = `yyyy-MM-dd` local) and the existing `workoutDetail/{workoutId}`.
 */
fun FitnessContent.tapRoute(): String {
    val day = scheduledDate
    if (isCompleted && workoutId.isNotEmpty() && day != null) {
        return "completedWorkoutReport/$workoutId/${CalendarDayKeys.dayKey(day)}"
    }
    return "workoutDetail/$workoutId"
}

/** Local-calendar day keys shared by the route helper and the pickers. */
object CalendarDayKeys {
    fun dayKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
    fun dayKey(date: Date): String = dayKey(date.time)

    /** iOS `CalendarAddMenuPopover.dayLabel(for:)`, e.g. "Thu, Sep 10". */
    fun dayLabel(millis: Long): String = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
}
