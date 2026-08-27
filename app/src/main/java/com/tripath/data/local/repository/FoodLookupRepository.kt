package com.tripath.data.local.repository

/** Per-100g nutrition for one barcode, and how it was obtained. */
sealed class FoodLookupResult {
    data class Found(
        val name: String?,
        val kcalPer100g: Double?,
        val proteinPer100g: Double?,
        val isManualOverride: Boolean
    ) : FoodLookupResult()

    /** The barcode isn't in Open Food Facts and has no cached override — needs manual entry. */
    data object NotFound : FoodLookupResult()

    /** The lookup couldn't complete (no connectivity, timeout, server error) and there's no cache. */
    data object NetworkError : FoodLookupResult()
}

/**
 * Barcode -> per-100g kcal/protein lookup behind the nutrition barcode scanner. Checks the local
 * cache first (instant, offline-friendly), falling back to Open Food Facts on a cache miss.
 */
interface FoodLookupRepository {

    suspend fun lookup(barcode: String): FoodLookupResult

    /**
     * Persist a user-supplied or user-corrected per-100g value against [barcode], so this and
     * future scans of the same product use it instead of (or in the absence of) the API's data.
     */
    suspend fun saveOverride(barcode: String, name: String?, kcalPer100g: Double?, proteinPer100g: Double?)
}
