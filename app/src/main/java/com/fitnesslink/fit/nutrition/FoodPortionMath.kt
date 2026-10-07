package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.network.dto.FoodPortion
import com.fitnesslink.fit.network.dto.FoodSearchResult
import java.util.Locale

// FTNSSLNK-186/199: food portions + client-side conversion. Exact port of iOS
// `Models/API/FoodPortionModels.swift`, itself a mirror of the backend's
// `FoodPortionConverter` / `StandardFoodPortions`:
//
//   grams    = portion.gramWeight x quantity
//   nutrient = perGram x grams, where perGram = perServing / servingGrams
//
// The client always uses the per-serving basis (per-100g values are not on
// the wire); the Food Detail screen trues the preview up against
// `GET /foods/{id}/convert`, and `POST /food-entries` with foodId + unit +
// quantity makes the server recompute the stored macros authoritatively.

/** The standard weight/volume units available to EVERY food (same constants, labels and order as the backend). */
object StandardFoodPortions {
    const val GRAMS_PER_OZ = 28.349523125
    const val GRAMS_PER_LB = 453.59237
    const val ML_PER_TSP = 4.92892159375
    const val ML_PER_TBSP = 14.78676478125
    const val ML_PER_FL_OZ = 29.5735295625
    const val ML_PER_CUP = 236.5882365

    /** Water-density assumption used to turn a volume into grams when no real density exists. */
    const val FALLBACK_DENSITY_GRAMS_PER_ML = 1.0

    val all: List<FoodPortion> = listOf(
        weight("g", 1.0),
        weight("oz", GRAMS_PER_OZ),
        weight("lb", GRAMS_PER_LB),
        weight("kg", 1000.0),
        volume("mL", 1.0),
        volume("tsp", ML_PER_TSP),
        volume("tbsp", ML_PER_TBSP),
        volume("fl oz", ML_PER_FL_OZ),
        volume("cup", ML_PER_CUP),
        volume("L", 1000.0)
    )

    private fun weight(label: String, grams: Double) =
        FoodPortion(unit = label, gramWeight = grams, category = "Weight", isStandard = true, milliliterVolume = null)

    private fun volume(label: String, milliliters: Double) = FoodPortion(
        unit = label,
        gramWeight = milliliters * FALLBACK_DENSITY_GRAMS_PER_ML,
        category = "Volume",
        isStandard = true,
        milliliterVolume = milliliters
    )
}

/**
 * A food's nutrition expressed per gram, derived from its per-serving macros
 * and serving size — the client-side analogue of the backend's
 * `NutrientBasis` per-serving fallback path.
 */
data class FoodNutrientBasis(
    val caloriesPerGram: Double,
    val proteinPerGram: Double,
    val fatPerGram: Double,
    val carbsPerGram: Double,
    /** Grams for one stated serving of the food. */
    val servingGrams: Double,
    /** True when serving grams were derived through a volume unit at water density. */
    val densityAssumed: Boolean
) {
    companion object {
        /**
         * Build a per-gram basis from per-serving macros + serving size/unit.
         * Null when the serving unit is unknown or the size is zero — then
         * grams can't be derived and unit conversion isn't possible
         * client-side (the server's /convert may still succeed via per-100g).
         */
        fun from(
            calories: Double,
            protein: Double,
            fat: Double,
            carbs: Double,
            servingSize: Double,
            servingSizeUnit: String?
        ): FoodNutrientBasis? {
            val (grams, densityAssumed) = servingGrams(servingSize, servingSizeUnit) ?: return null
            if (grams <= 0) return null
            return FoodNutrientBasis(
                caloriesPerGram = calories / grams,
                proteinPerGram = protein / grams,
                fatPerGram = fat / grams,
                carbsPerGram = carbs / grams,
                servingGrams = grams,
                densityAssumed = densityAssumed
            )
        }

        /**
         * Grams for one serving from (size, unit). The synonym table is a
         * verbatim mirror of the backend's `NutrientBasis.ServingGrams`. The
         * second value is true when a volume unit was converted at water density.
         */
        fun servingGrams(size: Double, unit: String?): Pair<Double, Boolean>? {
            if (size <= 0) return null
            val fb = StandardFoodPortions.FALLBACK_DENSITY_GRAMS_PER_ML
            return when ((unit ?: "").trim().lowercase(Locale.US)) {
                "g", "gram", "grams", "gm" -> size to false
                "kg", "kilogram", "kilograms" -> size * 1000 to false
                "mg", "milligram", "milligrams" -> size / 1000 to false
                "oz", "ounce", "ounces" -> size * StandardFoodPortions.GRAMS_PER_OZ to false
                "lb", "lbs", "pound", "pounds" -> size * StandardFoodPortions.GRAMS_PER_LB to false
                "ml", "milliliter", "millilitre", "milliliters", "millilitres", "cc" -> size * fb to true
                "l", "liter", "litre", "liters", "litres" -> size * 1000 * fb to true
                "tsp", "teaspoon", "teaspoons" -> size * StandardFoodPortions.ML_PER_TSP * fb to true
                "tbsp", "tablespoon", "tablespoons" -> size * StandardFoodPortions.ML_PER_TBSP * fb to true
                "fl oz", "floz", "fluid ounce", "fluid ounces" -> size * StandardFoodPortions.ML_PER_FL_OZ * fb to true
                "cup", "cups" -> size * StandardFoodPortions.ML_PER_CUP * fb to true
                else -> null
            }
        }
    }
}

/** Result of converting (unit, quantity) to nutrition — the client analogue of the backend's `PortionConversion`. */
data class PortionConversion(
    val unit: String,
    val category: String,
    val quantity: Double,
    val grams: Double,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val usedDensityFallback: Boolean
)

/** Pure portion-to-nutrition converter mirroring the backend's `FoodPortionConverter.Convert`. */
object FoodPortionMath {
    /**
     * The portions a food exposes for the unit picker. The server already
     * composes food-specific + standard portions on the wire; this only
     * backfills the standard units when a payload predates FTNSSLNK-186
     * (or a decode dropped them), keeping the picker usable.
     */
    fun availablePortions(wirePortions: List<FoodPortion>?): List<FoodPortion> {
        if (wirePortions.isNullOrEmpty()) return StandardFoodPortions.all
        val claimed = wirePortions.map { it.unit.trim().lowercase(Locale.US) }.toSet()
        return wirePortions + StandardFoodPortions.all.filter { it.unit.lowercase(Locale.US) !in claimed }
    }

    /** Case-insensitive unit match, same as the server's lookup. */
    fun portion(matching: String, portions: List<FoodPortion>): FoodPortion? {
        val wanted = matching.trim().lowercase(Locale.US)
        if (wanted.isEmpty()) return null
        return portions.firstOrNull { it.unit.trim().lowercase(Locale.US) == wanted }
    }

    /**
     * Converts [quantity] of [unit] to nutrition, or null when the unit isn't
     * in [portions]. A standard Volume unit reaches grams via water density;
     * a food-specific portion (even a "cup") carries a real gram weight and
     * never triggers the flag on its own.
     */
    fun convert(basis: FoodNutrientBasis, portions: List<FoodPortion>, unit: String, quantity: Double): PortionConversion? {
        if (quantity <= 0) return null
        val portion = portion(unit, portions) ?: return null
        val grams = portion.gramWeight * quantity
        val usedFallback = (portion.isStandard && portion.isVolume) || basis.densityAssumed
        return PortionConversion(
            unit = portion.unit,
            category = portion.category,
            quantity = quantity,
            grams = grams,
            calories = basis.caloriesPerGram * grams,
            protein = basis.proteinPerGram * grams,
            fat = basis.fatPerGram * grams,
            carbs = basis.carbsPerGram * grams,
            usedDensityFallback = usedFallback
        )
    }

    /**
     * Trim a trailing ".0" so quantities render as "2" not "2.0", keep one
     * decimal for halves ("1.5") and two for finer prefills ("1.25").
     */
    fun formatQuantity(value: Double): String {
        if (value % 1.0 == 0.0) return value.toLong().toString()
        if ((value * 10) % 1.0 == 0.0) return String.format(Locale.US, "%.1f", value)
        return String.format(Locale.US, "%.2f", value)
    }

    /** Lenient numeric parse accepting a comma decimal separator. */
    fun parseQuantity(text: String): Double = text.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
}

/** Per-gram basis for a catalog food's stated serving (nil when the serving unit is unknown). */
val FoodSearchResult.nutrientBasis: FoodNutrientBasis?
    get() = FoodNutrientBasis.from(
        calories = calories, protein = protein, fat = fat, carbs = carbs,
        servingSize = servingSizeGrams ?: 0.0, servingSizeUnit = servingUnit
    )
