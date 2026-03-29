package com.fitnesslink.fit.model

import java.util.Date
import java.util.Locale
import java.util.UUID

enum class WeightUnit(val label: String) {
    LBS("lbs"),
    KG("kg");

    companion object {
        val defaultUnit: WeightUnit
            get() {
                val country = Locale.getDefault().country
                return if (country in listOf("US", "LR", "MM")) LBS else KG
            }
    }

    fun convert(value: Double, to: WeightUnit): Double {
        if (this == to) return value
        return when (this to to) {
            LBS to KG -> value * 0.453592
            KG to LBS -> value / 0.453592
            else -> value
        }
    }
}

data class WeightEntry(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val weight: Double = 0.0,
    val unit: WeightUnit = WeightUnit.defaultUnit,
    val date: Date = Date(),
    val notes: String = "",
    val createdAt: Date = Date()
)

data class WeightChartPoint(
    val id: String = UUID.randomUUID().toString(),
    val date: Date = Date(),
    val weight: Double = 0.0
)
