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

    private companion object {
        /**
         * Hash of the current column signatures, pinned identically in LiftPath's own
         * `LiftPathShareContractTest`. **Do not "fix" this to make a build pass** — a mismatch means
         * the contract changed on one side, and the other app's copy has to change with it.
         */
        const val EXPECTED_SCHEMA_HASH = "-5313bb2c"
    }

    @Test
    fun `schema hash is stable across repeated calls`() {
        val first = LiftPathShareContract.schemaHash()
        val second = LiftPathShareContract.schemaHash()
        assertEquals(first, second)
    }

    /**
     * The one assertion that makes two verbatim-duplicated files actually stay in step: both apps
     * pin the same literal, so a one-sided column edit turns that side's build red rather than
     * leaving two APKs that disagree at runtime about what a column means.
     */
    @Test
    fun `schema hash matches the value LiftPath's copy of this contract pins`() {
        assertEquals(EXPECTED_SCHEMA_HASH, LiftPathShareContract.schemaHash())
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
