package com.tripath.data.local.repository

import com.tripath.data.local.database.dao.ScannedFoodDao
import com.tripath.data.local.database.entities.ScannedFoodCache
import com.tripath.data.remote.openfoodfacts.OpenFoodFactsApi
import com.tripath.data.remote.openfoodfacts.toLookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class FoodLookupRepositoryImpl @Inject constructor(
    private val scannedFoodDao: ScannedFoodDao,
    private val api: OpenFoodFactsApi
) : FoodLookupRepository {

    override suspend fun lookup(barcode: String): FoodLookupResult = withContext(Dispatchers.IO) {
        scannedFoodDao.getByBarcode(barcode)?.let { cached ->
            return@withContext FoodLookupResult.Found(
                name = cached.name,
                kcalPer100g = cached.kcalPer100g,
                proteinPer100g = cached.proteinPer100g,
                isManualOverride = cached.isManualOverride
            )
        }

        try {
            when (val result = api.getProduct(barcode).toLookupResult()) {
                is FoodLookupResult.Found -> {
                    scannedFoodDao.upsert(
                        ScannedFoodCache(
                            barcode = barcode,
                            name = result.name,
                            kcalPer100g = result.kcalPer100g,
                            proteinPer100g = result.proteinPer100g,
                            isManualOverride = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    result
                }
                else -> result
            }
        } catch (e: IOException) {
            FoodLookupResult.NetworkError
        } catch (e: retrofit2.HttpException) {
            FoodLookupResult.NetworkError
        }
    }

    override suspend fun saveOverride(barcode: String, name: String?, kcalPer100g: Double?, proteinPer100g: Double?) {
        withContext(Dispatchers.IO) {
            scannedFoodDao.upsert(
                ScannedFoodCache(
                    barcode = barcode,
                    name = name,
                    kcalPer100g = kcalPer100g,
                    proteinPer100g = proteinPer100g,
                    isManualOverride = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
