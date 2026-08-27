package com.tripath.data.local.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract is duplicated verbatim in LiftPath (`com.liftpath.helpers.TriPathContract`). Nothing
 * in either build system can enforce that, so these tests pin the values a one-sided edit changes.
 *
 * [EXPECTED_SCHEMA_HASH] is the load-bearing one: LiftPath's copy pins the same literal, so editing
 * a column on one side turns that side's build red instead of leaving two APKs that agree on the
 * version number and disagree about what `expenditure_kcal` contains.
 */
class TriPathShareContractTest {

    private companion object {
        /**
         * Hash of the current column signatures, pinned identically in LiftPath's
         * `TriPathContractTest`. **Do not "fix" this to make a build pass** — a mismatch means the
         * contract changed, and the other app's copy has to change with it.
         */
        const val EXPECTED_SCHEMA_HASH = "1f50c308"
    }

    @Test
    fun `schema hash is stable across repeated calls`() {
        assertEquals(TriPathShareContract.schemaHash(), TriPathShareContract.schemaHash())
    }

    @Test
    fun `schema hash matches the value LiftPath's copy of this contract pins`() {
        assertEquals(EXPECTED_SCHEMA_HASH, TriPathShareContract.schemaHash())
    }

    @Test
    fun `contract version is 2 now that readiness is served`() {
        assertEquals(2, TriPathShareContract.CONTRACT_VERSION)
    }

    @Test
    fun `schema hash changes when a column is renamed`() {
        val renamed = TriPathShareContract.Readiness.SPEC.map {
            if (it.name == TriPathShareContract.Readiness.SCORE) it.copy(name = "readiness_score") else it
        }
        assertNotEquals(
            signatureFor(TriPathShareContract.Readiness.SPEC),
            signatureFor(renamed)
        )
    }

    /** A `kcal` column that quietly became `kJ` reads fine and is wrong by a factor of four. */
    @Test
    fun `schema hash changes when a column is re-typed`() {
        val retyped = TriPathShareContract.Days.SPEC.map {
            if (it.name == TriPathShareContract.Days.TARGET_KCAL) it.copy(type = "INTEGER") else it
        }
        assertNotEquals(signatureFor(TriPathShareContract.Days.SPEC), signatureFor(retyped))
    }

    @Test
    fun `schema hash changes when a column changes nullability`() {
        val tightened = TriPathShareContract.Days.SPEC.map {
            if (it.name == TriPathShareContract.Days.INTAKE_KCAL) it.copy(nullable = false) else it
        }
        assertNotEquals(signatureFor(TriPathShareContract.Days.SPEC), signatureFor(tightened))
    }

    @Test
    fun `every path's COLUMNS and SPEC describe the same columns`() {
        assertEquals(
            TriPathShareContract.Days.COLUMNS.toSet(),
            TriPathShareContract.Days.SPEC.map { it.name }.toSet()
        )
        assertEquals(
            TriPathShareContract.Workouts.COLUMNS.toSet(),
            TriPathShareContract.Workouts.SPEC.map { it.name }.toSet()
        )
        assertEquals(
            TriPathShareContract.Readiness.COLUMNS.toSet(),
            TriPathShareContract.Readiness.SPEC.map { it.name }.toSet()
        )
    }

    /**
     * A JSON payload can be reshaped without its column name moving, which the schema hash cannot
     * see. The capability token is the only thing guarding that gap, so it has to be advertised.
     */
    @Test
    fun `every JSON payload has a capability token guarding its shape`() {
        assertTrue(TriPathShareContract.CAPABILITIES.contains(TriPathShareContract.CAP_DRIVERS_JSON_V1))
        assertTrue(
            TriPathShareContract.CAPABILITIES
                .contains(TriPathShareContract.CAP_DISCIPLINE_VERDICTS_JSON_V1)
        )
        assertTrue(
            TriPathShareContract.CAPABILITIES.contains(TriPathShareContract.CAP_MUSCLE_FRESHNESS_V1)
        )
    }

    @Test
    fun `capabilities advertise readiness and nutrition targets`() {
        assertTrue(TriPathShareContract.CAPABILITIES.contains(TriPathShareContract.CAP_READINESS_V1))
        assertTrue(
            TriPathShareContract.CAPABILITIES.contains(TriPathShareContract.CAP_NUTRITION_TARGETS_V1)
        )
    }

    /** Mirrors the per-path building block inside [TriPathShareContract.schemaHash]. */
    private fun signatureFor(spec: List<TriPathShareContract.ColumnSpec>): String =
        spec.sortedBy { it.name }
            .joinToString(",") { "${it.name}:${it.type}:${if (it.nullable) "1" else "0"}" }
}
