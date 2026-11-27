package com.fitnesslink.fit.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure helpers behind [HealthConnectService] reads, kept apart from the SDK
 * so the rules are unit-testable.
 */
object HealthConnectMath {

    /** A time span read from Health Connect (a sleep session or one of its stages). */
    data class Span(val start: Instant, val end: Instant)

    /**
     * Seconds asleep per night, keyed by the local date the night ended on
     * (iOS `HealthKitService.sleepByNight`). Overlapping spans from different
     * sources (watch + phone + a third-party tracker) are merged before
     * summing so no minute is counted twice.
     */
    fun sleepByNight(asleep: List<Span>, zone: ZoneId): Map<LocalDate, Double> =
        asleep.filter { it.end.isAfter(it.start) }
            .groupBy { it.end.atZone(zone).toLocalDate() }
            .mapValues { (_, spans) -> mergedSeconds(spans) }

    /** Total seconds covered by [spans], counting overlaps once. */
    fun mergedSeconds(spans: List<Span>): Double {
        var total = 0L
        var openStart: Instant? = null
        var openEnd: Instant? = null
        for (span in spans.sortedBy { it.start }) {
            val end = openEnd
            if (end != null && !span.start.isAfter(end)) {
                if (span.end.isAfter(end)) openEnd = span.end
            } else {
                if (openStart != null && end != null) total += end.toEpochMilli() - openStart.toEpochMilli()
                openStart = span.start
                openEnd = span.end
            }
        }
        if (openStart != null && openEnd != null) total += openEnd.toEpochMilli() - openStart.toEpochMilli()
        return total / 1000.0
    }

    /** Per-day mean of discrete readings (HRV), keyed by local date. Days without readings are absent. */
    fun dailyAverages(readings: List<Pair<Instant, Double>>, zone: ZoneId): Map<LocalDate, Double> =
        readings.groupBy { it.first.atZone(zone).toLocalDate() }
            .mapValues { (_, values) -> values.sumOf { it.second } / values.size }
}
