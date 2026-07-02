package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleGroupKeyNormalizerTest {
    @Test
    fun quadAliasesUseProjectCanonicalKey() {
        assertEquals("QUADRICEPS", MuscleGroupKeyNormalizer.canonicalKey("quadriceps"))
        assertEquals("QUADRICEPS", MuscleGroupKeyNormalizer.canonicalKey("대퇴사두근"))
        assertEquals("QUADRICEPS", MuscleGroupKeyNormalizer.canonicalKey("대퇴근"))
    }

    @Test
    fun posteriorChainAliasesUseErectorCanonicalKey() {
        assertEquals("ERECTOR_SPINAE", MuscleGroupKeyNormalizer.canonicalKey("erector_spinae"))
        assertEquals("ERECTOR_SPINAE", MuscleGroupKeyNormalizer.canonicalKey("spinal_erectors"))
        assertEquals("ERECTOR_SPINAE", MuscleGroupKeyNormalizer.canonicalKey("척추기립근"))
    }

    @Test
    fun unknownMuscleKeyStaysUnknown() {
        assertNull(MuscleGroupKeyNormalizer.canonicalKey("not_a_muscle"))
    }
}
