package com.training.trackplanner.data

import com.training.trackplanner.analysis.tissue.TissueSymptomOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TissueSymptomOverridePlanTest {
    private val units = mapOf(
        "ankle_tendon" to "ankle",
        "ankle_ligament" to "ankle",
        "shoulder_tendon" to "shoulder"
    )

    @Test
    fun highDiscomfortOverridesOnlySelectedJointComplex() {
        val plan = tissueSymptomOverridePlan(5, "ankle", units)

        assertEquals(
            mapOf(
                "ankle_tendon" to TissueSymptomOverride.BLOCK,
                "ankle_ligament" to TissueSymptomOverride.BLOCK
            ),
            plan.overridesByLoadUnit
        )
        assertFalse(plan.unscopedHighDiscomfort)
    }

    @Test
    fun highDiscomfortWithoutKnownLocationDoesNotOverrideAllTissues() {
        val plan = tissueSymptomOverridePlan(4, null, units)

        assertTrue(plan.overridesByLoadUnit.isEmpty())
        assertTrue(plan.unscopedHighDiscomfort)
    }

    @Test
    fun locationWithoutHighDiscomfortDoesNotCreateOverride() {
        val plan = tissueSymptomOverridePlan(3, "ankle", units)

        assertTrue(plan.overridesByLoadUnit.isEmpty())
        assertFalse(plan.unscopedHighDiscomfort)
    }
}
