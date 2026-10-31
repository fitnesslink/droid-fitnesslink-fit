package com.fitnesslink.fit.health

import com.fitnesslink.fit.health.HealthConnectMath.Span
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectMathTests {
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private fun at(text: String): Instant = java.time.LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test fun overlappingSleepFromTwoSourcesCountsOnce() {
        val spans = listOf(
            Span(at("2026-10-30T23:00"), at("2026-10-31T06:00")), // watch
            Span(at("2026-10-31T05:00"), at("2026-10-31T07:00")), // phone, overlaps an hour
            Span(at("2026-10-31T13:00"), at("2026-10-31T13:30"))  // nap, separate
        )
        val nights = HealthConnectMath.sleepByNight(spans, zone)
        assertEquals(mapOf(LocalDate.of(2026, 10, 31) to 8.5 * 3600), nights)
    }

    @Test fun nightIsKeyedByTheMorningItEnded() {
        val nights = HealthConnectMath.sleepByNight(listOf(Span(at("2026-10-29T22:30"), at("2026-10-30T06:30"))), zone)
        assertEquals(setOf(LocalDate.of(2026, 10, 30)), nights.keys)
    }

    @Test fun containedSpanAddsNothing() {
        assertEquals(3600.0, HealthConnectMath.mergedSeconds(listOf(
            Span(at("2026-10-31T01:00"), at("2026-10-31T02:00")),
            Span(at("2026-10-31T01:15"), at("2026-10-31T01:45"))
        )), 0.001)
    }

    @Test fun dailyAveragesGroupByLocalDay() {
        val r = HealthConnectMath.dailyAverages(listOf(
            at("2026-10-31T07:00") to 40.0, at("2026-10-31T22:00") to 60.0, at("2026-10-30T07:00") to 30.0
        ), zone)
        assertEquals(50.0, r.getValue(LocalDate.of(2026, 10, 31)), 0.001)
        assertEquals(30.0, r.getValue(LocalDate.of(2026, 10, 30)), 0.001)
    }
}
