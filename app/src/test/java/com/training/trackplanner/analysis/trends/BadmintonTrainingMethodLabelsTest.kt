package com.training.trackplanner.analysis.trends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadmintonTrainingMethodLabelsTest {
    @Test
    fun labelsKnownBadmintonMethodKeysInKorean() {
        assertEquals("리액션", BadmintonTrainingMethodLabels.label("REACTION"))
        assertEquals("감속", BadmintonTrainingMethodLabels.label("DECELERATION"))
        assertEquals("회전 생성", BadmintonTrainingMethodLabels.label("ROTATION_POWER"))
        assertEquals("항회전/몸통제어", BadmintonTrainingMethodLabels.label("ANTI_ROTATION"))
    }

    @Test
    fun descriptionsAreAvailableForUserFacingLabels() {
        assertTrue(BadmintonTrainingMethodLabels.description("ACCELERATION").isNotBlank())
        assertTrue(BadmintonTrainingMethodLabels.description("ANTI_ROTATION").contains("몸통"))
        assertTrue(BadmintonTrainingMethodLabels.description("REACTION").contains("반응"))
    }

    @Test
    fun keysFromKeepsOnlyTransferObjectiveKeys() {
        val keys = BadmintonTrainingMethodLabels.keysFrom(
            courtMovementTypes = setOf("REACTION_RANDOM", "NONE", "FIRST_STEP"),
            transferRoles = setOf("DECELERATION", "GRIP_FOREARM", "SHOULDER_CARE"),
            skillTargets = setOf("ROTATION_SEQUENCING", "ANTI_ROTATION_STABILITY", "CONDITIONING")
        )

        assertTrue("REACTION" in keys)
        assertTrue("ACCELERATION" in keys)
        assertTrue("DECELERATION" in keys)
        assertTrue("ROTATION_POWER" in keys)
        assertTrue("ANTI_ROTATION" in keys)
        assertTrue("CONDITIONING" in keys)
        assertFalse("NONE" in keys)
        assertFalse("GRIP_FOREARM" in keys)
        assertFalse("SHOULDER_CARE" in keys)
    }
}
