package com.tripath.data.remote.openfoodfacts

import com.tripath.data.local.repository.FoodLookupResult
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenFoodFactsDtosTest {
    @Test
    fun `status 1 with full nutriments maps to Found`() {
        val response = OpenFoodFactsResponse(
            status = 1,
            product = OpenFoodFactsProduct(
                productName = "Rolled Oats",
                nutriments = OpenFoodFactsNutriments(energyKcalPer100g = 389.0, proteinsPer100g = 13.5)
            )
        )

        val result = response.toLookupResult()

        assertEquals(
            FoodLookupResult.Found(name = "Rolled Oats", kcalPer100g = 389.0, proteinPer100g = 13.5, isManualOverride = false),
            result
        )
    }

    @Test
    fun `status 1 with a missing nutriment leaves that field null rather than dropping the product`() {
        val response = OpenFoodFactsResponse(
            status = 1,
            product = OpenFoodFactsProduct(
                productName = "Mystery Bar",
                nutriments = OpenFoodFactsNutriments(energyKcalPer100g = 250.0, proteinsPer100g = null)
            )
        )

        val result = response.toLookupResult() as FoodLookupResult.Found

        assertEquals(250.0, result.kcalPer100g)
        assertEquals(null, result.proteinPer100g)
    }

    @Test
    fun `status 0 maps to NotFound even if a product happens to be present`() {
        val response = OpenFoodFactsResponse(
            status = 0,
            product = OpenFoodFactsProduct(productName = "Stale cache artifact")
        )

        assertEquals(FoodLookupResult.NotFound, response.toLookupResult())
    }

    @Test
    fun `missing product maps to NotFound`() {
        val response = OpenFoodFactsResponse(status = 1, product = null)

        assertEquals(FoodLookupResult.NotFound, response.toLookupResult())
    }
}
