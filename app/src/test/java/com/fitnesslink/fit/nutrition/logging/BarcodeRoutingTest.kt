package com.fitnesslink.fit.nutrition.logging

import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.BarcodeScanError
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.nutrition.BarcodeLookupCascade
import com.fitnesslink.fit.nutrition.BarcodeLookupOutcome
import com.fitnesslink.fit.nutrition.BarcodeScanDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Resolution order + routing of iOS `BarcodeLookupService` / `BarcodeScannerContainerView`. */
class BarcodeRoutingTest {

    private val catalogFood = FoodSearchResult(id = "fdc-1", name = "Cheerios", calories = 100.0)
    private val cached = BarcodeProduct(barcode = "016000275287", name = "Cached", caloriesPer100g = 350.0, servingSizeGrams = 28.0)
    private val off = BarcodeProduct(barcode = "016000275287", name = "OFF", caloriesPer100g = 370.0)

    private fun resolve(
        online: Boolean,
        catalog: suspend (String) -> FoodSearchResult?,
        cache: (String) -> BarcodeProduct? = { null },
        openFoodFacts: suspend (String) -> BarcodeProduct = { throw BarcodeScanError.ProductNotFound(it) }
    ) = runBlocking { BarcodeLookupCascade.resolve("016000275287", online, catalog, cache, openFoodFacts) }

    @Test fun `catalog hit wins and skips every other tier`() {
        var cacheAsked = false
        val outcome = resolve(online = true, catalog = { catalogFood }, cache = { cacheAsked = true; cached })
        assertEquals(BarcodeLookupOutcome.Catalog(catalogFood), outcome)
        assertFalse(cacheAsked)
    }

    @Test fun `catalog miss falls through to the device cache`() {
        val outcome = resolve(online = true, catalog = { null }, cache = { cached })
        assertEquals(BarcodeLookupOutcome.Product(cached), outcome)
    }

    @Test fun `catalog failure other than 404 still lets the cache answer`() {
        val outcome = resolve(online = true, catalog = { throw IllegalStateException("500") }, cache = { cached })
        assertEquals(BarcodeLookupOutcome.Product(cached), outcome)
    }

    @Test fun `cache miss goes to OpenFoodFacts`() {
        val outcome = resolve(online = true, catalog = { null }, openFoodFacts = { off })
        assertEquals(BarcodeLookupOutcome.Product(off), outcome)
    }

    @Test fun `every tier missing is a product-not-found miss`() {
        try {
            resolve(online = true, catalog = { null })
            fail("expected ProductNotFound")
        } catch (e: BarcodeScanError.ProductNotFound) {
            assertEquals("016000275287", e.barcode)
        }
    }

    @Test fun `offline skips the catalog, serves the cache, and reports a network error without one`() {
        var catalogAsked = false
        val fromCache = resolve(online = false, catalog = { catalogAsked = true; catalogFood }, cache = { cached })
        assertEquals(BarcodeLookupOutcome.Product(cached), fromCache)
        assertFalse(catalogAsked)
        try {
            resolve(online = false, catalog = { catalogFood })
            fail("expected NetworkError")
        } catch (e: BarcodeScanError.NetworkError) {
            assertTrue(e.detail.contains("offline"))
        }
    }

    @Test fun `catalog hits route to the catalog detail path and products to the template path`() {
        val catalog = BarcodeLookupCascade.destination(BarcodeLookupOutcome.Catalog(catalogFood), MealType.LUNCH)
        assertEquals(BarcodeScanDestination.CatalogDetail(catalogFood), catalog)

        val product = BarcodeLookupCascade.destination(BarcodeLookupOutcome.Product(cached), MealType.LUNCH)
        val entry = (product as BarcodeScanDestination.TemplateDetail).entry
        assertEquals("Cached", entry.name)
        assertEquals(MealType.LUNCH, entry.mealType)
        assertEquals(98, entry.calories)             // 350 per 100 g × 28 g serving
        assertEquals(28.0, entry.servingSize, 1e-9)
        assertEquals(null, entry.foodId)             // never a catalog id → never hits /convert
    }
}
