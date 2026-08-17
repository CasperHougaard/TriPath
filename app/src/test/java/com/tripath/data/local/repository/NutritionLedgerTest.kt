package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.NutritionEntryKind
import com.tripath.data.local.database.entities.NutritionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionLedgerTest {

    private val date = LocalDate.of(2026, 8, 17)

    @Test
    fun `quick add touches only its own field`() {
        val deltas = quickAddDeltas(NutritionMacro.PROTEIN, 10.0)
        assertEquals(10.0, deltas.proteinG!!, 0.001)
        assertNull(deltas.kcal)
        assertNull(deltas.carbsG)
        assertNull(deltas.fatG)
    }

    @Test
    fun `setting a value on an unlogged field is the full amount`() {
        val deltas = adjustmentDeltas(null, kcal = 2000.0, protein = null, carbs = null, fat = null)
        assertEquals(2000.0, deltas.kcal!!, 0.001)
        // A field that was unlogged and stays unlogged is untouched, not zeroed.
        assertNull(deltas.proteinG)
    }

    @Test
    fun `editing a total records the difference`() {
        val old = NutritionLog(date = date, energyKcal = 1840.0, proteinG = 112.0)
        val deltas = adjustmentDeltas(old, kcal = 2000.0, protein = 112.0, carbs = null, fat = null)
        assertEquals(160.0, deltas.kcal!!, 0.001)
        // Protein was re-submitted unchanged, so it contributes nothing to undo.
        assertNull(deltas.proteinG)
    }

    @Test
    fun `clearing a logged field is a negative delta that undo can restore`() {
        val old = NutritionLog(date = date, energyKcal = 500.0)
        val deltas = adjustmentDeltas(old, kcal = null, protein = null, carbs = null, fat = null)
        assertEquals(-500.0, deltas.kcal!!, 0.001)
        // Applying then subtracting the delta returns the original value.
        assertEquals(500.0, (old.energyKcal!! + deltas.kcal!!) - deltas.kcal!!, 0.001)
    }

    @Test
    fun `an edit that changes nothing produces no entry`() {
        val old = NutritionLog(date = date, energyKcal = 1840.0, creatineTaken = true)
        val deltas = adjustmentDeltas(old, kcal = 1840.0, protein = null, carbs = null, fat = null)
        assertTrue(deltas.isEmpty)

        val entry = adjustmentEntry(date, old, deltas, creatineTaken = true, now = 1L)
        assertTrue(entry.isNoOp)
    }

    @Test
    fun `an edit that only flips creatine is still worth recording`() {
        val old = NutritionLog(date = date, energyKcal = 1840.0, creatineTaken = false)
        val deltas = adjustmentDeltas(old, kcal = 1840.0, protein = null, carbs = null, fat = null)

        val entry = adjustmentEntry(date, old, deltas, creatineTaken = true, now = 1L)
        assertFalse(entry.isNoOp)
        assertEquals(false, entry.creatineFrom)
        assertEquals(true, entry.creatineTo)
    }

    @Test
    fun `adjustment keeps the previous totals for display`() {
        val old = NutritionLog(date = date, energyKcal = 1840.0, proteinG = 112.0)
        val deltas = adjustmentDeltas(old, kcal = 2000.0, protein = 120.0, carbs = null, fat = null)

        val entry = adjustmentEntry(date, old, deltas, creatineTaken = false, now = 42L)
        assertEquals(NutritionEntryKind.ADJUSTMENT, entry.kind)
        assertEquals(1840.0, entry.prevKcal!!, 0.001)
        assertEquals(112.0, entry.prevProteinG!!, 0.001)
        assertEquals(42L, entry.loggedAt)
        assertNull(entry.creatineFrom)
    }

    @Test
    fun `a blank custom-add label is stored as null rather than an empty title`() {
        val entry = addEntry(date, NutritionEntryKind.CUSTOM_ADD, NutritionDeltas(kcal = 620.0), "  ", now = 1L)
        assertNull(entry.label)

        val labelled = addEntry(date, NutritionEntryKind.CUSTOM_ADD, NutritionDeltas(kcal = 620.0), " Chicken & rice ", now = 1L)
        assertEquals("Chicken & rice", labelled.label)
    }

    @Test
    fun `a day row with nothing logged is empty and one with creatine is not`() {
        assertTrue(NutritionLog(date = date).isEmpty())
        assertFalse(NutritionLog(date = date, creatineTaken = true).isEmpty())
        assertFalse(NutritionLog(date = date, energyKcal = 0.0).isEmpty())
    }
}
