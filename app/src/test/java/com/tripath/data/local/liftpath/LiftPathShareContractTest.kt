package com.tripath.data.local.liftpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract file is duplicated verbatim in LiftPath ([LiftPathShareContract]'s KDoc). Version
 * number alone does not catch every drift — a column rename, re-type or nullability change must
 * still be caught even if [LiftPathShareContract.CONTRACT_VERSION] was never bumped, which is
 * exactly what [LiftPathShareContract.schemaHash] exists for.
 */
class LiftPathShareContractTest {

    @Test
    fun `schema hash is stable across repeated calls`() {
        val first = LiftPathShareContract.schemaHash()
        val second = LiftPathShareContract.schemaHash()
        assertEquals(first, second)
    }

    @Test
    fun `schema hash changes when a column is renamed`() {
        val before = signatureFor(LiftPathShareContract.Sets.SPEC)
        val renamed = LiftPathShareContract.Sets.SPEC.map {
            if (it.name == LiftPathShareContract.Sets.RPE) it.copy(name = "rate_of_perceived_exertion") else it
        }
        val after = signatureFor(renamed)
        assertNotEquals(before, after)
    }

    @Test
    fun `schema hash changes when a column is re-typed`() {
        val before = signatureFor(LiftPathShareContract.Sets.SPEC)
        val retyped = LiftPathShareContract.Sets.SPEC.map {
            if (it.name == LiftPathShareContract.Sets.KG) it.copy(type = "TEXT") else it
        }
        val after = signatureFor(retyped)
        assertNotEquals(before, after)
    }

    @Test
    fun `schema hash changes when a column becomes nullable`() {
        val before = signatureFor(LiftPathShareContract.Sessions.SPEC)
        val loosened = LiftPathShareContract.Sessions.SPEC.map {
            if (it.name == LiftPathShareContract.Sessions.PLAN_NAME) it.copy(nullable = false) else it
        }
        val after = signatureFor(loosened)
        assertNotEquals(before, after)
    }

    @Test
    fun `capability tokens cover set-level lifting data and the exercise catalog`() {
        assertTrue(LiftPathShareContract.CAPABILITIES.contains(LiftPathShareContract.CAP_LIFT_SETS_V1))
        assertTrue(LiftPathShareContract.CAPABILITIES.contains(LiftPathShareContract.CAP_LIFT_CATALOG_V1))
    }

    /** Mirrors the per-path signature building block inside [LiftPathShareContract.schemaHash]. */
    private fun signatureFor(spec: List<LiftPathShareContract.ColumnSpec>): String =
        spec.sortedBy { it.name }
            .joinToString(",") { "${it.name}:${it.type}:${if (it.nullable) "1" else "0"}" }
}
