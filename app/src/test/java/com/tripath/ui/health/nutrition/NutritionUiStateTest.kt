package com.tripath.ui.health.nutrition

import com.tripath.domain.health.HealthReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionUiStateTest {

    @Test
    fun `user protein target takes priority over derived band`() {
        val state = NutritionUiState(
            userProteinTargetG = 160f,
            proteinTarget = HealthReference.Band(120.0, 140.0)
        )
        assertEquals(160.0, state.effectiveProteinTargetG!!, 0.001)
    }

    @Test
    fun `protein target falls back to derived band minimum when user has none`() {
        val state = NutritionUiState(
            userProteinTargetG = null,
            proteinTarget = HealthReference.Band(120.0, 140.0)
        )
        assertEquals(120.0, state.effectiveProteinTargetG!!, 0.001)
    }

    @Test
    fun `protein target is null when neither user nor derived value exists`() {
        val state = NutritionUiState(userProteinTargetG = null, proteinTarget = null)
        assertNull(state.effectiveProteinTargetG)
    }

    @Test
    fun `user calorie target takes priority over maintenance estimate`() {
        val state = NutritionUiState(userCalorieTarget = 2500f, maintenanceCalories = 2200.0)
        assertEquals(2500.0, state.effectiveCalorieTarget!!, 0.001)
    }

    @Test
    fun `calorie target falls back to maintenance when user has none`() {
        val state = NutritionUiState(userCalorieTarget = null, maintenanceCalories = 2200.0)
        assertEquals(2200.0, state.effectiveCalorieTarget!!, 0.001)
    }

    @Test
    fun `calorie target is null when nothing is set`() {
        val state = NutritionUiState(userCalorieTarget = null, maintenanceCalories = null)
        assertNull(state.effectiveCalorieTarget)
    }

    @Test
    fun `soft progress is proportional below target`() {
        assertEquals(0.5f, softProgressFraction(80.0, 160.0), 0.0001f)
    }

    @Test
    fun `soft progress clamps to full and never fails when over target`() {
        // Over target reads as full (1f), not an error state or a value greater than 1.
        assertEquals(1f, softProgressFraction(200.0, 160.0), 0.0001f)
    }

    @Test
    fun `soft progress is zero when target is missing or non-positive`() {
        assertEquals(0f, softProgressFraction(100.0, null), 0.0001f)
        assertEquals(0f, softProgressFraction(100.0, 0.0), 0.0001f)
    }
}
