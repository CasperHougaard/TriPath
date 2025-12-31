package com.tripath.data.model

import kotlinx.serialization.Serializable

/**
 * Data class for GPS route points (for JSON serialization).
 */
@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val t: Long // epoch millis
)





