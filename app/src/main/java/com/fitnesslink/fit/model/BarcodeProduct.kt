package com.fitnesslink.fit.model

import java.util.UUID

data class BarcodeProduct(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String = "",
    val name: String = "",
    val brand: String = "",
    val imageUrl: String = "",
    val caloriesPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val servingSizeGrams: Double = 100.0,
    val servingUnit: String = "g"
) {
    fun toFoodEntry(mealType: MealType, servingMultiplier: Double = 1.0): FoodEntry {
        val factor = (servingSizeGrams * servingMultiplier) / 100.0
        return FoodEntry(
            name = if (brand.isEmpty()) name else "$name ($brand)",
            calories = (caloriesPer100g * factor).toInt(),
            protein = proteinPer100g * factor,
            fat = fatPer100g * factor,
            carbs = carbsPer100g * factor,
            servingSize = servingSizeGrams * servingMultiplier,
            servingUnit = servingUnit,
            mealType = mealType
        )
    }
}

/**
 * FTNSSLNK-198: recoverable barcode-scan failures (iOS `BarcodeScanError`).
 * The scanner container maps each to a distinct state — never a dead end.
 */
sealed class BarcodeScanError(message: String) : Exception(message) {
    data class ProductNotFound(val barcode: String) : BarcodeScanError("No product found for barcode $barcode")
    data class NetworkError(val detail: String) : BarcodeScanError(detail)
    data object InvalidResponse : BarcodeScanError("The lookup returned an unexpected response.") {
        private fun readResolve(): Any = InvalidResponse
    }
    data object PermissionDenied : BarcodeScanError("Camera access is needed to scan barcodes.") {
        private fun readResolve(): Any = PermissionDenied
    }
    data object CameraUnavailable : BarcodeScanError("No camera is available on this device.") {
        private fun readResolve(): Any = CameraUnavailable
    }

    val title: String
        get() = when (this) {
            is ProductNotFound -> "No Match Found"
            is NetworkError -> "Lookup Failed"
            InvalidResponse -> "Lookup Failed"
            PermissionDenied -> "Camera Access Needed"
            CameraUnavailable -> "Camera Unavailable"
        }

    val userMessage: String
        get() = when (this) {
            is ProductNotFound -> "Barcode $barcode isn't in the food database yet."
            is NetworkError -> "Could not look up the barcode. Check your connection and try again."
            InvalidResponse -> "The product database returned something unexpected. Try again."
            PermissionDenied -> "Allow camera access in Settings to scan barcodes, or enter the code manually."
            CameraUnavailable -> "This device has no usable camera. Enter the barcode manually instead."
        }
}
