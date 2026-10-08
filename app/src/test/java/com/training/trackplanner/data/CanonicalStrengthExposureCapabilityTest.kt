package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.RealizedStimulusClass
import com.training.trackplanner.data.personalized.RealizedStimulusClassifier
import com.training.trackplanner.data.personalized.RealizedStimulusInput
import com.training.trackplanner.data.personalized.RealizedStimulusKind
import com.training.trackplanner.data.personalized.provisionalRealizedStimulusClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanonicalStrengthExposureCapabilityTest {
    private val approved = setOf(
        "barbell_back_squat", "ex_c5043892", "barbell_deadlift", "ex_e41f4c2b", "ex_e41e8dcf",
        "barbell_bench_press", "ex_3a7d3eda", "ex_32219f7a", "ex_79f3bdbe", "ex_bb4b4276"
    )

    @Test
    fun onlyTheTenApprovedCanonicalIdentitiesHaveStrengthCapability() {
        assertEquals("USER_APPROVED_PROJECT_POLICY", CanonicalStrengthExposureCapability.policyProvenance)
        assertEquals("C32_STRENGTH_EXPOSURE_V1", CanonicalStrengthExposureCapability.policyVersion)
        assertEquals(approved, CanonicalStrengthExposureCapability.approvedStableKeys)
        approved.forEach { stableKey ->
            assertTrue(stableKey, RuntimeExerciseMetadataDefaults.forIdentity(stableKey, stableKey).strengthPossible)
            assertTrue(stableKey, CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, 1))
            assertTrue(stableKey, CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, 6))
            assertFalse(stableKey, CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, 0))
            assertFalse(stableKey, CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, 7))
        }
    }

    @Test
    fun approvedStrengthIdentitiesArePresentInTheCurrentCanonicalMetadata() {
        val asset = sequenceOf(
            File("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}"),
            File("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
        ).firstOrNull(File::isFile) ?: error("Missing canonical runtime metadata asset")
        val canonicalRows = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(asset.readText(Charsets.UTF_8))
            .associateBy(RuntimeExerciseMetadata::stableKey)

        assertTrue(approved.all(canonicalRows::containsKey))
        assertEquals(approved, canonicalRows.values.filter(RuntimeExerciseMetadata::strengthPossible)
            .map(RuntimeExerciseMetadata::stableKey).toSet())
        approved.forEach { stableKey ->
            val row = canonicalRows.getValue(stableKey)
            assertEquals("EXERCISE", row.activityKind)
            assertEquals("PROGRAM_SELECTABLE", row.planningEligibility)
            assertTrue(row.strengthPossible)
        }
    }

    @Test
    fun legacyStrengthTokensDoNotGrantTheNewCapability() {
        val legacyStrengthRow = RuntimeExerciseMetadataDefaults.forIdentity("ex_8e4bf08e", "Yates Row").copy(
            analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"),
            strengthProgressionGroup = "MAIN_UPPER_STRENGTH",
            programSlot = "MAIN_UPPER_STRENGTH"
        )
        assertFalse(legacyStrengthRow.strengthPossible)
        assertFalse(CanonicalStrengthExposureCapability.strengthExposureEligible("ex_8e4bf08e", 5))
    }

    @Test
    fun explicitNegativeIdentitiesRemainOutsideStrengthAtFiveReps() {
        val excluded = setOf(
            "barbell_romanian_deadlift", "pull_up", "ex_6466fe77", "ex_7814843a",
            "machine_chest_press", "machine_shoulder_press", "lat_pulldown", "reverse_curl",
            "leg_press", "ex_de46b7f6"
        )
        excluded.forEach { stableKey ->
            assertFalse(stableKey, RuntimeExerciseMetadataDefaults.forIdentity(stableKey, stableKey).strengthPossible)
            assertFalse(stableKey, CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, 5))
            assertEquals(RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS,
                provisionalRealizedStimulusClass(stableKey, 5))
        }
    }

    @Test
    fun mixedPrescribedSetRepetitionsAreClassifiedIndividually() {
        val stableKey = "ex_e41f4c2b"
        assertEquals(
            listOf(RealizedStimulusClass.STRENGTH_LIKE, RealizedStimulusClass.STRENGTH_LIKE,
                RealizedStimulusClass.HYPERTROPHY_LIKE),
            listOf(5, 5, 8).map { provisionalRealizedStimulusClass(stableKey, it) }
        )
    }

    @Test
    fun powerAndReactiveRowsCannotBecomeStrengthBecauseTheirRepsAreLow() {
        val power = RealizedStimulusClassifier.classify(
            RealizedStimulusInput(
                stableKey = "power_clean",
                date = java.time.LocalDate.of(2026, 9, 23),
                activityKind = com.training.trackplanner.data.personalized.PlannedActivityKind.RESISTANCE,
                reps = 3,
                resolvedLoadKg = 40.0,
                rpe = 8.0,
                directQualities = setOf(com.training.trackplanner.data.TrainableQuality.POWER),
                reviewedIdentity = true,
                reference1RmKg = 100.0
            )
        )
        assertFalse(power.kind == RealizedStimulusKind.STRENGTH_LIKE)
        assertFalse(CanonicalStrengthExposureCapability.strengthExposureEligible("power_clean", 3))
    }
}
