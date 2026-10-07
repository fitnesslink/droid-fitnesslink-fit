package com.fitnesslink.fit.progress

import com.fitnesslink.fit.model.BodyMeasurementValue
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.MeasurementUnit

/**
 * Pure measurement arithmetic shared by the log, the entry detail and the
 * photo comparison (iOS `MeasurementBreakdownView.change(for:)` plus the
 * `BodyTrackingService` anatomy mapping).
 */
object MeasurementMath {

    /** A stored value expressed in [unit]. */
    fun displayValue(m: BodyMeasurementValue, unit: MeasurementUnit): Double = m.unit.convert(m.value, unit)

    /**
     * Change for [part] between [entry] and the chronologically previous
     * [previous], in [unit]; null when either side lacks the part.
     */
    fun delta(entry: MeasurementEntry, previous: MeasurementEntry?, part: BodyPart, unit: MeasurementUnit): Double? {
        val current = entry.measurement(part) ?: return null
        val prior = previous?.measurement(part) ?: return null
        return displayValue(current, unit) - displayValue(prior, unit)
    }

    /** Core / Arms / Legs / Other groups in iOS order, dropping empty categories. */
    fun grouped(entry: MeasurementEntry): List<Pair<String, List<BodyMeasurementValue>>> {
        val byCategory = entry.measurements.groupBy { it.bodyPart.category }
        return BodyPart.categoryOrder.mapNotNull { category ->
            byCategory[category]?.takeIf { it.isNotEmpty() }?.let { category to it.sortedBy { m -> m.bodyPart.ordinal } }
        }
    }

    /** The entry logged just before [entryId] in a newest-first list; null for the oldest. */
    fun previousOf(entriesDesc: List<MeasurementEntry>, entryId: String): MeasurementEntry? {
        val index = entriesDesc.indexOfFirst { it.id.equals(entryId, ignoreCase = true) }
        if (index < 0) return null
        return entriesDesc.getOrNull(index + 1)
    }

    /**
     * iOS `ClassificationStore.anatomyId(for:)`: walk the part's candidate
     * names most-specific first and return the first taxonomy hit.
     */
    fun anatomyIdFor(part: BodyPart, lookup: (String) -> String?): String? {
        for (name in part.anatomyNameCandidates) lookup(name)?.let { return it }
        return null
    }

    /** iOS `ClassificationStore.bodyPart(for:)` once the anatomy id has been turned into a name. */
    fun bodyPartFor(anatomyName: String?): BodyPart? = BodyPart.fromAnatomyName(anatomyName)
}
