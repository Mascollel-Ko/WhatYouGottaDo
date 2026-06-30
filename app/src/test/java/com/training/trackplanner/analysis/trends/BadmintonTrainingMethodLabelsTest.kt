package com.training.trackplanner.analysis.trends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadmintonTrainingMethodLabelsTest {
    @Test
    fun labelsKnownBadmintonMethodKeysInKorean() {
        assertEquals("리액션", BadmintonTrainingMethodLabels.label("REACTION_RANDOM"))
        assertEquals("감속", BadmintonTrainingMethodLabels.label("DECELERATION"))
        assertEquals("오버헤드", BadmintonTrainingMethodLabels.label("OVERHEAD_POWER"))
        assertEquals("반응/민첩", BadmintonTrainingMethodLabels.label("REACTIVE"))
        assertEquals("보조운동", BadmintonTrainingMethodLabels.label("ACCESSORY"))
        assertEquals("기초지원", BadmintonTrainingMethodLabels.label("SUPPORT"))
    }

    @Test
    fun descriptionsAreAvailableForUserFacingLabels() {
        assertTrue(BadmintonTrainingMethodLabels.description("ACCESSORY").isNotBlank())
        assertTrue(BadmintonTrainingMethodLabels.description("SUPPORT").isNotBlank())
        assertTrue(BadmintonTrainingMethodLabels.description("REACTION_RANDOM").contains("반응"))
    }

    @Test
    fun keysFromUsesExistingMetadataAndDropsEmptyNoise() {
        val keys = BadmintonTrainingMethodLabels.keysFrom(
            courtMovementTypes = setOf("REACTION_RANDOM", "NONE"),
            transferRoles = setOf("DECELERATION"),
            sportContextTags = setOf("BADMINTON_DIRECT_TRANSFER"),
            movementCategory = "SKILL_DRILL"
        )

        assertTrue("REACTION_RANDOM" in keys)
        assertTrue("DECELERATION" in keys)
        assertTrue("BADMINTON_DIRECT_TRANSFER" in keys)
        assertFalse("NONE" in keys)
        assertFalse("SKILL_DRILL" in keys)
    }
}
