package com.fitnesslink.fit.network.dto

import com.fitnesslink.fit.model.AnglePhoto
import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.BodyMeasurementValue
import com.fitnesslink.fit.model.BodyPart
import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.model.MealSlot
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.model.MeasurementEntry
import com.fitnesslink.fit.model.PhotoAngle
import com.fitnesslink.fit.model.ProgressPhotoEntry
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Wire DTO ⇄ local model mappers shared by view models. The DTOs mirror the
 * iOS Codable types exactly; the local models are what the screens and the
 * SQLite cache use. Keep conversions here so the two never drift apart.
 */

// MARK: - Calendar

fun CalendarEntryDto.toFitnessContent(): FitnessContent = FitnessContent(
    id = id.lowercase(Locale.US),
    title = title ?: "",
    programId = programId ?: "",
    workoutId = workoutId ?: "",
    mealPlanId = mealPlanId ?: "",
    status = status ?: "scheduled",
    scheduledDate = scheduledDate?.time,
    workoutImageId = workoutImageId,
    workoutThumbnailId = workoutThumbnailId,
    programImageId = programImageId,
    programThumbnailId = programThumbnailId,
    programEnrollmentId = programEnrollmentId,
    durationMinutes = durationMinutes,
    programWeekNumber = programWeekNumber,
    programDayNumber = programDayNumber
)

/** iOS `CalendarService.create` sends nulls, never empty strings. */
fun FitnessContent.toCreateRequest(): CreateCalendarEntryRequest = CreateCalendarEntryRequest(
    id = id,
    scheduledDate = Date(scheduledDate ?: System.currentTimeMillis()),
    workoutId = workoutId,
    programId = programId.ifBlank { null },
    mealPlanId = mealPlanId.ifBlank { null },
    title = title.ifBlank { null },
    status = status.ifBlank { null },
    programEnrollmentId = programEnrollmentId,
    durationMinutes = durationMinutes,
    programWeekNumber = programWeekNumber,
    programDayNumber = programDayNumber
)

// MARK: - Nutrition

fun MealSlotIngredientDto.toGroceryItem(): GroceryItem = GroceryItem(
    id = id?.lowercase(Locale.US) ?: java.util.UUID.randomUUID().toString().lowercase(Locale.US),
    name = name ?: "",
    quantity = quantity ?: "",
    unit = unit ?: "",
    category = category?.let { GroceryCategory.fromBackendName(it) } ?: GroceryCategory.fromWire(category),
    sortOrder = sortOrder
)

fun MealSlotDto.toMealSlot(): MealSlot = MealSlot(
    id = id.lowercase(Locale.US),
    date = day ?: MealSlot().date,
    mealType = MealType.fromWire(mealType) ?: MealType.BREAKFAST,
    recipeName = recipeName ?: "",
    calories = (calories ?: 0.0).let { Math.round(it).toInt() },
    protein = protein ?: 0.0,
    fat = fat ?: 0.0,
    carbs = carbs ?: 0.0,
    isAISuggestion = isAISuggestion ?: false,
    ingredients = ingredientItems?.sortedBy { it.sortOrder ?: Int.MAX_VALUE }?.map { it.toGroceryItem() } ?: emptyList(),
    ingredientsText = ingredients,
    rowVersion = rowVersion ?: ""
)

fun GroceryItemDto.toGroceryItem(): GroceryItem = GroceryItem(
    id = id.lowercase(Locale.US),
    name = name ?: "",
    quantity = quantity?.let { q -> if (q == Math.floor(q)) q.toLong().toString() else q.toString() } ?: "",
    unit = unit ?: "",
    category = category?.let { GroceryCategory.fromBackendName(it) } ?: GroceryCategory.fromWire(category),
    isChecked = isChecked ?: false,
    rowVersion = rowVersion ?: "",
    groceryListId = groceryListId,
    sortOrder = sortOrder
)

/**
 * A catalog barcode hit. USDA branded rows report macros per 100 g, which is
 * exactly `BarcodeProduct`'s basis; the serving defaults to 100 g until the
 * food detail flow (Wave 3) pulls real portions from `GET /foods/{id}`.
 */
fun BarcodeMatchDto.toBarcodeProduct(barcode: String): BarcodeProduct = BarcodeProduct(
    id = id.lowercase(Locale.US),
    barcode = gtinUpc ?: barcode,
    name = description,
    brand = brandOwner ?: "",
    caloriesPer100g = calories,
    proteinPer100g = protein,
    fatPer100g = fat,
    carbsPer100g = carbs
)

// MARK: - Body tracking

private fun Date.dayKey(): String {
    val c = Calendar.getInstance().apply { time = this@dayKey }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/**
 * Server measurements arrive one row per anatomy part; iOS `coalesceMeasurements`
 * groups them by calendar day into one `MeasurementEntry`. `resolvePart` maps an
 * anatomy id to a `BodyPart`; rows that cannot be resolved are dropped.
 */
fun List<BodyMeasurementDto>.toMeasurementEntries(resolvePart: (String) -> BodyPart?): List<MeasurementEntry> =
    groupBy { it.logDate.dayKey() }
        .map { (_, rows) ->
            val first = rows.minBy { it.logDate.time }
            MeasurementEntry(
                id = first.id.lowercase(Locale.US),
                userId = first.userId,
                date = first.logDate,
                measurements = rows.mapNotNull { row ->
                    val part = resolvePart(row.anatomyId) ?: return@mapNotNull null
                    BodyMeasurementValue(id = row.id.lowercase(Locale.US), bodyPart = part, value = row.value)
                },
                createdAt = first.createdAt ?: first.logDate
            )
        }
        .sortedByDescending { it.date }

/**
 * Server progress photos arrive one row per angle; iOS groups them by day into a
 * multi-angle entry, inferring the angle from upload order (front → side → back)
 * when the server did not record one.
 */
fun List<ProgressPhotoDto>.toProgressPhotoEntries(): List<ProgressPhotoEntry> =
    filter { it.logDate != null }
        .groupBy { it.logDate!!.dayKey() }
        .map { (_, rows) ->
            val ordered = rows.sortedBy { it.createdAt?.time ?: it.logDate!!.time }
            val first = ordered.first()
            val photos = ordered.mapIndexed { index, row ->
                val angle = row.angle?.let { a -> PhotoAngle.entries.firstOrNull { it.displayName.equals(a, true) || it.name.equals(a, true) } }
                    ?: PhotoAngle.entries.getOrElse(index) { PhotoAngle.BACK }
                AnglePhoto(
                    id = row.id.lowercase(Locale.US),
                    angle = angle,
                    imageId = row.beforeImageId ?: row.afterImageId,
                    createdAt = row.createdAt ?: row.logDate!!
                )
            }
            ProgressPhotoEntry(
                id = first.id.lowercase(Locale.US),
                userId = first.userId,
                date = first.logDate!!,
                photos = photos,
                createdAt = first.createdAt ?: first.logDate!!
            )
        }
        .sortedByDescending { it.date }
