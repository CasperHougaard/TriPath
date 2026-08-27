package com.tripath.ui.health.nutrition.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeScanViewModelTest {
    @Test
    fun `scales a per-100g value down to the grams eaten`() {
        assertEquals(155.6, scalePer100g(389.0, 40.0)!!, 0.01)
    }

    @Test
    fun `100g is a no-op scale`() {
        assertEquals(389.0, scalePer100g(389.0, 100.0)!!, 0.001)
    }

    @Test
    fun `zero grams scales to zero, not null`() {
        assertEquals(0.0, scalePer100g(389.0, 0.0)!!, 0.001)
    }

    @Test
    fun `an unknown per-100g value stays unknown regardless of grams`() {
        assertNull(scalePer100g(null, 250.0))
    }
}
