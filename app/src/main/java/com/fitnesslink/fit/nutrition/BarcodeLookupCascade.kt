package com.fitnesslink.fit.nutrition

import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.BarcodeScanError
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.FoodSearchResult

/** What a barcode resolved to (FTNSSLNK-198). */
sealed class BarcodeLookupOutcome {
    /** The backend matched the code to a catalog food: full search-shaped food, safe for the catalog detail path and /convert. */
    data class Catalog(val food: FoodSearchResult) : BarcodeLookupOutcome()

    /** A device-cached or OpenFoodFacts product — per-100g macros with no catalog id. Routed through the template path. */
    data class Product(val product: BarcodeProduct) : BarcodeLookupOutcome()
}

/** Where the scanner lands after a hit. */
sealed class BarcodeScanDestination {
    data class CatalogDetail(val food: FoodSearchResult) : BarcodeScanDestination()
    data class TemplateDetail(val entry: com.fitnesslink.fit.model.FoodEntry) : BarcodeScanDestination()
}

/**
 * The pure resolution order behind [BarcodeLookupService]: backend catalog →
 * device cache → OpenFoodFacts. Kept free of Android / network types so the
 * routing decision is unit-testable; the service supplies the tiers.
 *
 * - [catalog] returns the catalog food or null for a 404 (not in the catalog);
 *   any other failure is swallowed (server unreachable → the cache can still answer).
 * - Throws [BarcodeScanError.ProductNotFound] when every tier misses,
 *   [BarcodeScanError.NetworkError] when connectivity (not the product) is the problem.
 */
object BarcodeLookupCascade {
    suspend fun resolve(
        barcode: String,
        online: Boolean,
        catalog: suspend (String) -> FoodSearchResult?,
        cache: (String) -> BarcodeProduct?,
        openFoodFacts: suspend (String) -> BarcodeProduct
    ): BarcodeLookupOutcome {
        if (online) {
            val food = try { catalog(barcode) } catch (_: Exception) { null }
            if (food != null) return BarcodeLookupOutcome.Catalog(food)
        }
        cache(barcode)?.let { return BarcodeLookupOutcome.Product(it) }
        if (!online) throw BarcodeScanError.NetworkError("You appear to be offline.")
        return BarcodeLookupOutcome.Product(openFoodFacts(barcode))
    }

    /** Catalog hits take the `/convert`-backed catalog path; products take the client-side template path. */
    fun destination(outcome: BarcodeLookupOutcome, mealType: MealType): BarcodeScanDestination = when (outcome) {
        is BarcodeLookupOutcome.Catalog -> BarcodeScanDestination.CatalogDetail(outcome.food)
        is BarcodeLookupOutcome.Product -> BarcodeScanDestination.TemplateDetail(outcome.product.toFoodEntry(mealType))
    }
}
