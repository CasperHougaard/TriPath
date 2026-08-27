package com.tripath.data.remote.openfoodfacts

import com.tripath.data.local.repository.FoodLookupResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for `GET /api/v2/product/{barcode}.json`. `status == 0` means the barcode is
 * not in Open Food Facts' database — [product] is then absent rather than empty.
 */
@Serializable
data class OpenFoodFactsResponse(
    val status: Int = 0,
    @SerialName("product") val product: OpenFoodFactsProduct? = null
)

@Serializable
data class OpenFoodFactsProduct(
    @SerialName("product_name") val productName: String? = null,
    val nutriments: OpenFoodFactsNutriments? = null
)

/** Per-100g figures. Either field can be absent for a given product, even when [status] is 1. */
@Serializable
data class OpenFoodFactsNutriments(
    @SerialName("energy-kcal_100g") val energyKcalPer100g: Double? = null,
    @SerialName("proteins_100g") val proteinsPer100g: Double? = null
)

/**
 * Pure mapping from the API response to the domain result — kept free of I/O so it's testable
 * without mocking Retrofit. `status != 1` (or a missing product) means the barcode isn't in Open
 * Food Facts; either nutriment field can still be individually absent even when the product is.
 */
fun OpenFoodFactsResponse.toLookupResult(): FoodLookupResult {
    val found = product?.takeIf { status == 1 } ?: return FoodLookupResult.NotFound
    return FoodLookupResult.Found(
        name = found.productName,
        kcalPer100g = found.nutriments?.energyKcalPer100g,
        proteinPer100g = found.nutriments?.proteinsPer100g,
        isManualOverride = false
    )
}
