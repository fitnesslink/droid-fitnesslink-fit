package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.EstimateMacrosRequest
import com.fitnesslink.fit.network.dto.FoodMacros
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * FTNSSLNK-202: state + validation + entry building for the manual food
 * entry form (QuickAddScreen and CustomFoodFormScreen), kept out of the
 * composables so the rules are unit-testable. Port of iOS `ManualFoodForm`.
 *
 * "Save for reuse" creates a SEPARATE `FoodEntry` row flagged
 * `isCustomTemplate` — the backend's food search merges those rows above
 * catalog results for the owner. The template row's `loggedAt` is a fixed
 * sentinel far in the past so it never lands in any day's diary.
 */
data class ManualFoodForm(
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val servingSize: String = "1",
    val servingUnit: String = "serving",
    /** Also save this food as a reusable custom food (separate `isCustomTemplate` row). */
    val saveAsCustomFood: Boolean = false,
    /** Scanned barcode carried in from a scan miss (FTNSSLNK-198) — feeds the local barcode cache only. */
    val barcode: String? = null,
    /** What the last Fill with AI wrote (FTNSSLNK-446/447/448/449). */
    val fill: AIFill? = null,
    val source: String? = null,
    val aiConfidence: String? = null,
    val aiGenerationId: String? = null
) {
    enum class Field { NAME, CALORIES, PROTEIN, FAT, CARBS, SERVING_SIZE, SERVING_UNIT }

    /** What an AI fill wrote and what it was anchored to. */
    data class AIFill(
        /** Estimate source: "logged" | "library" | "ai_estimate". */
        val estimateSource: String,
        val confidence: String?,
        /** Numeric fields the fill wrote (they were blank). Rescaling touches these. */
        val filledFields: Set<Field>,
        /** Numeric fields the user had typed before the fill — locked. */
        val lockedFields: Set<Field>,
        /** Serving size the filled values describe; a serving edit rescales relative to this. */
        val servingSize: Double
    ) {
        val isAiEstimate: Boolean get() = estimateSource == "ai_estimate"

        /** "AI estimate · medium confidence" / "From your history" / "From food library". */
        val badgeText: String
            get() = when (estimateSource) {
                "ai_estimate" -> if (confidence.isNullOrEmpty()) "AI estimate" else "AI estimate · $confidence confidence"
                "library" -> "From food library"
                "logged" -> "From your history"
                else -> "Filled in"
            }
    }

    val trimmedName: String get() = name.trim()
    val trimmedUnit: String get() = servingUnit.trim()

    // MARK: - Validation

    /**
     * Null when the field is acceptable. Required: name, calories, serving
     * size, serving unit. Macros are optional (blank = 0) but must parse as
     * non-negative numbers when present.
     */
    fun error(field: Field): String? = when (field) {
        Field.NAME -> if (trimmedName.isEmpty()) "Name is required" else null
        Field.CALORIES -> {
            val value = parse(calories)
            when {
                value == null -> if (calories.trim().isEmpty()) "Calories are required" else "Enter a number"
                value >= 0 -> null
                else -> "Must be 0 or more"
            }
        }
        Field.PROTEIN -> optionalMacroError(protein)
        Field.FAT -> optionalMacroError(fat)
        Field.CARBS -> optionalMacroError(carbs)
        Field.SERVING_SIZE -> {
            val value = parse(servingSize)
            when {
                value == null -> "Enter a number"
                value > 0 -> null
                else -> "Must be more than 0"
            }
        }
        Field.SERVING_UNIT -> if (trimmedUnit.isEmpty()) "Unit is required" else null
    }

    val isValid: Boolean get() = Field.entries.all { error(it) == null }

    // MARK: - Entry building

    /** The diary entry to log now. Null while the form is invalid. */
    fun loggedEntry(mealType: MealType, logDate: Date?): FoodEntry? =
        baseEntry(mealType)?.copy(loggedAt = FoodEntry.timestamp(logDate))

    /**
     * The separate reusable custom-food row. Null unless [saveAsCustomFood]
     * (or the form is invalid). Own id, sentinel `loggedAt`, `isCustomTemplate` set.
     */
    fun templateEntry(mealType: MealType): FoodEntry? {
        if (!saveAsCustomFood) return null
        return baseEntry(mealType)?.copy(loggedAt = templateSentinelLoggedAt, isCustomTemplate = true)
    }

    private fun baseEntry(mealType: MealType): FoodEntry? {
        if (!isValid) return null
        val caloriesValue = parse(calories) ?: return null
        val sizeValue = parse(servingSize) ?: return null
        return FoodEntry(
            id = UUID.randomUUID().toString().lowercase(Locale.US),
            name = trimmedName,
            calories = caloriesValue.roundToInt(),
            protein = parse(protein) ?: 0.0,
            fat = parse(fat) ?: 0.0,
            carbs = parse(carbs) ?: 0.0,
            servingSize = sizeValue,
            servingUnit = trimmedUnit,
            mealType = mealType,
            quantity = sizeValue,
            portionUnit = trimmedUnit,
            // Grams per ONE unit when the unit is a real weight/volume; null for freeform units.
            portionGramWeight = FoodNutrientBasis.servingGrams(1.0, trimmedUnit)?.first,
            source = source,
            aiConfidence = aiConfidence,
            aiGenerationId = aiGenerationId
        )
    }

    // MARK: - Barcode cache (FTNSSLNK-198 miss path)

    /**
     * When the entry came from a scan miss AND its serving unit converts to
     * grams, project the macros to per-100g and cache them against the
     * barcode locally, so a future scan of the same code resolves on this
     * device. Freeform units are skipped.
     */
    fun cacheableBarcodeProduct(): BarcodeProduct? {
        val code = barcode?.takeIf { it.isNotEmpty() } ?: return null
        if (!isValid) return null
        val caloriesValue = parse(calories) ?: return null
        val sizeValue = parse(servingSize) ?: return null
        val (grams, _) = FoodNutrientBasis.servingGrams(sizeValue, trimmedUnit) ?: return null
        if (grams <= 0) return null
        val factor = 100.0 / grams
        return BarcodeProduct(
            barcode = code,
            name = trimmedName,
            caloriesPer100g = caloriesValue * factor,
            proteinPer100g = (parse(protein) ?: 0.0) * factor,
            fatPer100g = (parse(fat) ?: 0.0) * factor,
            carbsPer100g = (parse(carbs) ?: 0.0) * factor,
            servingSizeGrams = grams,
            servingUnit = "g"
        )
    }

    // MARK: - AI fill (FTNSSLNK-446 / 447 / 448)

    private fun numericText(field: Field): String = when (field) {
        Field.CALORIES -> calories
        Field.PROTEIN -> protein
        Field.FAT -> fat
        Field.CARBS -> carbs
        else -> ""
    }

    private fun withNumeric(field: Field, value: Double): ManualFoodForm {
        val text = if (field == Field.CALORIES) value.roundToInt().toString() else format((value * 10).roundToInt() / 10.0)
        return when (field) {
            Field.CALORIES -> copy(calories = text)
            Field.PROTEIN -> copy(protein = text)
            Field.FAT -> copy(fat = text)
            Field.CARBS -> copy(carbs = text)
            else -> this
        }
    }

    /** Numeric fields the user has typed (non-blank) — sent as locked values and never overwritten by a fill. */
    val typedNumericFields: Set<Field>
        get() = numericFields.filter { numericText(it).trim().isNotEmpty() }.toSet()

    /**
     * The estimate request for the form as it stands: name, the serving as
     * free text, and every typed number as a locked value. A re-estimate
     * passes the fields the user typed BEFORE the last fill.
     */
    fun estimateRequest(locking: Set<Field>? = null): EstimateMacrosRequest {
        val locked = locking ?: typedNumericFields
        fun known(field: Field): Double? = if (field in locked) parse(numericText(field)) else null
        return EstimateMacrosRequest(
            name = trimmedName,
            brand = null,
            servingDescription = servingDescription,
            knownCalories = known(Field.CALORIES),
            knownProtein = known(Field.PROTEIN),
            knownFat = known(Field.FAT),
            knownCarbs = known(Field.CARBS)
        )
    }

    /** "150 g" / "1 serving"; null when either serving field is blank. */
    val servingDescription: String?
        get() {
            val size = parse(servingSize) ?: return null
            if (size <= 0 || trimmedUnit.isEmpty()) return null
            return "${format(size)} $trimmedUnit"
        }

    /** True while the serving fields still hold the form's defaults. */
    val servingIsDefault: Boolean
        get() = parse(servingSize) == 1.0 && trimmedUnit.lowercase(Locale.US) == "serving"

    /**
     * Apply an estimate to the form. Only BLANK numeric fields are written;
     * what the user typed stays (FTNSSLNK-446). When the user's unit is a
     * weight (g / oz) and the response carries per-100g values, the macros
     * are computed locally for the serving size they entered and the
     * serving fields are left alone (FTNSSLNK-448); otherwise the response's
     * stated serving is used, and the serving fields take it over only while
     * they are still the defaults.
     */
    fun applyFill(
        food: FoodMacros,
        source: String,
        confidence: String?,
        generationId: String?,
        overwriting: Set<Field> = emptySet()
    ): ManualFoodForm {
        val locked = typedNumericFields - overwriting
        var form = this
        val values: Map<Field, Double>
        val fillServingSize: Double

        val size = parse(servingSize)
        val gramsPerUnit = gramsPerUnit(trimmedUnit)
        if (size != null && size > 0 && gramsPerUnit != null &&
            food.caloriesPer100g != null && food.proteinPer100g != null && food.fatPer100g != null && food.carbsPer100g != null
        ) {
            val factor = size * gramsPerUnit / 100
            values = mapOf(
                Field.CALORIES to food.caloriesPer100g * factor,
                Field.PROTEIN to food.proteinPer100g * factor,
                Field.FAT to food.fatPer100g * factor,
                Field.CARBS to food.carbsPer100g * factor
            )
            fillServingSize = size
        } else {
            values = mapOf(
                Field.CALORIES to food.calories,
                Field.PROTEIN to food.protein,
                Field.FAT to food.fat,
                Field.CARBS to food.carbs
            )
            if (servingIsDefault || size == null) {
                form = form.copy(servingSize = format(food.servingSize), servingUnit = food.servingUnit)
            }
            fillServingSize = parse(form.servingSize) ?: food.servingSize
        }

        val filled = mutableSetOf<Field>()
        for (field in numericFields) {
            if (field in locked) continue
            values[field]?.let { form = form.withNumeric(field, it); filled.add(field) }
        }
        if (form.trimmedName.isEmpty()) form = form.copy(name = food.displayName)

        return form.copy(
            fill = AIFill(
                estimateSource = source, confidence = confidence,
                filledFields = filled, lockedFields = locked, servingSize = fillServingSize
            ),
            source = FoodEntry.Source.fromEstimate(source),
            aiConfidence = if (source == "ai_estimate") confidence else null,
            aiGenerationId = if (source == "ai_estimate") generationId else null
        )
    }

    /** Forget the fill (badge + provenance) but keep the numbers — used when the user renames the food after a fill. */
    fun clearFill(): ManualFoodForm = copy(fill = null, source = null, aiConfidence = null, aiGenerationId = null)

    /**
     * Rescale the AI-filled numbers to a new serving size, proportionally to
     * the serving the fill described (FTNSSLNK-448). Locked (typed) values
     * are included only when the caller says so. No-op without a fill.
     */
    fun rescale(toServingSize: Double, includingLocked: Boolean): ManualFoodForm {
        val current = fill ?: return this
        if (current.servingSize <= 0 || toServingSize <= 0) return this
        val factor = toServingSize / current.servingSize
        val targets = if (includingLocked) numericFields.toSet() else current.filledFields
        var form = this
        for (field in targets) {
            parse(numericText(field))?.let { form = form.withNumeric(field, it * factor) }
        }
        return form.copy(fill = current.copy(servingSize = toServingSize))
    }

    companion object {
        val numericFields: List<Field> = listOf(Field.CALORIES, Field.PROTEIN, Field.FAT, Field.CARBS)

        /** `loggedAt` stamped on template rows: far outside any real diary day. */
        val templateSentinelLoggedAt: Date = Date(0L)

        /** Numeric parse accepting a comma decimal separator. */
        fun parse(text: String): Double? {
            val normalized = text.trim().replace(',', '.')
            if (normalized.isEmpty()) return null
            return normalized.toDoubleOrNull()
        }

        private fun optionalMacroError(text: String): String? {
            if (text.trim().isEmpty()) return null
            val value = parse(text) ?: return "Enter a number"
            return if (value >= 0) null else "Must be 0 or more"
        }

        /** Grams-per-unit for the two weight units the local per-100g path handles. */
        private fun gramsPerUnit(unit: String): Double? = when (unit.lowercase(Locale.US)) {
            "g", "gram", "grams" -> 1.0
            "oz", "ounce", "ounces" -> 28.3495
            else -> null
        }

        /** Seed the form from an existing entry (CustomFoodFormScreen edit mode). */
        fun from(entry: FoodEntry): ManualFoodForm = ManualFoodForm(
            name = entry.name,
            calories = entry.calories.toString(),
            protein = format(entry.protein),
            fat = format(entry.fat),
            carbs = format(entry.carbs),
            servingSize = format(entry.servingSize),
            servingUnit = entry.servingUnit,
            saveAsCustomFood = entry.isCustomTemplate,
            source = entry.source,
            aiConfidence = entry.aiConfidence,
            aiGenerationId = entry.aiGenerationId
        )

        /** "2" not "2.0"; one decimal for halves, two for finer values. */
        fun format(value: Double): String = FoodPortionMath.formatQuantity(value)

        fun fieldLabel(field: Field): String = when (field) {
            Field.CALORIES -> "Calories"
            Field.PROTEIN -> "Protein"
            Field.FAT -> "Fat"
            Field.CARBS -> "Carbs"
            Field.NAME -> "Name"
            Field.SERVING_SIZE -> "Serving Size"
            Field.SERVING_UNIT -> "Serving Unit"
        }

        /** "Calories, Protein" in canonical field order. */
        fun lockedFieldNames(fields: Set<Field>): String =
            numericFields.filter { it in fields }.joinToString(", ") { fieldLabel(it) }
    }
}
