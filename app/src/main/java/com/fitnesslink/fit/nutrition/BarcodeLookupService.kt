package com.fitnesslink.fit.nutrition

import android.util.Log
import com.fitnesslink.fit.model.BarcodeProduct
import com.fitnesslink.fit.model.BarcodeScanError
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.NetworkMonitor
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.persistence.DatabaseManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FTNSSLNK-198 barcode resolution (iOS `Services/BarcodeLookupService.swift`):
 * backend catalog (`GET /barcode/{code}` → upgraded to `GET /foods/{id}` so
 * portions ride along) → device cache (`barcode_products`) → OpenFoodFacts
 * (client-side, cached locally on success). The cascade itself lives in
 * [BarcodeLookupCascade] so it is unit-testable without a network.
 */
object BarcodeLookupService {
    private const val TAG = "BarcodeLookup"

    /** Plain client — OpenFoodFacts must NOT receive the Firebase bearer token the API client attaches. */
    private val offClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lookup(barcode: String): BarcodeLookupOutcome = BarcodeLookupCascade.resolve(
        barcode = barcode,
        online = NetworkMonitor.isConnected.value,
        catalog = { code -> catalogLookup(code) },
        cache = { code -> DatabaseManager.barcodeProduct(code) },
        openFoodFacts = { code -> lookupOpenFoodFacts(code) }
    )

    /** Catalog match → full food (portions) when reachable; null on 404; rethrows other failures (cascade swallows them). */
    private suspend fun catalogLookup(barcode: String): FoodSearchResult? {
        val match = try {
            ApiClient.nutritionApi.lookupBarcode(barcode)
        } catch (_: ApiError.NotFound) {
            return null
        }
        return try {
            ApiClient.nutritionApi.getFood(match.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No serving/portions on the match DTO — the detail screen previews via linear scaling + /convert.
            match.asSearchResult()
        }
    }

    // MARK: - OpenFoodFacts fallback

    private suspend fun lookupOpenFoodFacts(barcode: String): BarcodeProduct = withContext(Dispatchers.IO) {
        if (!NetworkMonitor.isConnected.value) throw BarcodeScanError.NetworkError("You appear to be offline.")
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            .header("User-Agent", "FitnessLink Android App - github.com/intrisia")
            .build()
        val body = try {
            offClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) throw BarcodeScanError.NetworkError("HTTP ${response.code}")
                response.body?.string() ?: ""
            }
        } catch (e: BarcodeScanError) {
            throw e
        } catch (e: IOException) {
            throw BarcodeScanError.NetworkError(e.message ?: "Network error")
        }
        parseResponse(body, barcode).also { DatabaseManager.insertBarcodeProduct(it) }
    }

    /** Parse an OpenFoodFacts v2 product payload. Internal for tests. */
    internal fun parseResponse(json: String, barcode: String): BarcodeProduct {
        val root = try { JsonParser.parseString(json).asJsonObject } catch (_: Exception) { throw BarcodeScanError.InvalidResponse }
        val status = root.get("status")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (status != 1) throw BarcodeScanError.ProductNotFound(barcode)
        val product = root.getAsJsonObject("product") ?: throw BarcodeScanError.InvalidResponse

        val name = bestString(product, "product_name", "product_name_en", "generic_name") ?: "Unknown Product"
        val brand = bestString(product, "brands", "brand_owner") ?: ""
        val imageUrl = bestString(product, "image_front_small_url", "image_front_url", "image_url") ?: ""
        val nutriments = product.getAsJsonObject("nutriments") ?: JsonObject()

        val (servingGrams, servingUnit) = parseServingSize(product)
        return BarcodeProduct(
            barcode = barcode,
            name = name,
            brand = brand,
            imageUrl = imageUrl,
            caloriesPer100g = bestDouble(nutriments, "energy-kcal_100g", "energy_100g"),
            proteinPer100g = bestDouble(nutriments, "proteins_100g"),
            fatPer100g = bestDouble(nutriments, "fat_100g"),
            carbsPer100g = bestDouble(nutriments, "carbohydrates_100g"),
            servingSizeGrams = servingGrams,
            servingUnit = servingUnit
        )
    }

    private fun bestString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun bestDouble(obj: JsonObject, vararg keys: String): Double {
        for (key in keys) {
            val el = obj.get(key)?.takeIf { it.isJsonPrimitive } ?: continue
            val parsed = if (el.asJsonPrimitive.isNumber) el.asDouble else el.asString.toDoubleOrNull()
            if (parsed != null) return parsed
        }
        return 0.0
    }

    private fun parseServingSize(product: JsonObject): Pair<Double, String> {
        val raw = product.get("serving_quantity")?.takeIf { it.isJsonPrimitive }
        val quantity = raw?.let { if (it.asJsonPrimitive.isNumber) it.asDouble else it.asString.toDoubleOrNull() }
        if (quantity != null && quantity > 0) {
            val unit = product.get("serving_quantity_unit")?.takeIf { it.isJsonPrimitive }?.asString ?: "g"
            return quantity to unit
        }
        return 100.0 to "g"
    }

    @Suppress("unused")
    private fun log(message: String) = Log.d(TAG, message)
}
