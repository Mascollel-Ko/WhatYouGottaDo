package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueEffectiveBaselineRuntimeTest {
    private val registry by lazy { TissuePriorRegistryParser.parse(asset()) }

    @Test
    fun committedPriorRegistryParsesAllStableKeysProfilesAndHours() {
        assertEquals(77, registry.profileIdByLoadUnitStableKey.size)
        assertEquals(13, registry.profiles.size)
        assertTrue(registry.profiles.values.all { it.boundariesByLocalHour.keys == (0..23).toSet() })
        assertEquals(
            "5303516c1b972ce3bdf08eaffcd7c5fe6448bfa64c50abae9790d8d81af0c58e",
            registry.deterministicOutputChecksum
        )
        assertEquals(
            "8ab9bc79ce452c6f80870cfb30973291bc85749e0d0538dacf4c6ccf9fbbbf6a",
            registry.recoveryEngineFingerprint
        )
    }

    @Test
    fun malformedOrIncompleteRegistryIsRejectedWithoutDisplayNameFallback() {
        val malformed = asset().replaceFirst("\"loadUnitCount\": 77", "\"loadUnitCount\": 76")
        val displayNameMapping = asset().replaceFirst(
            Regex("\\\"loadUnitStableKey\\\": \\\"lu_[0-9a-f]{10}\\\""),
            "\"loadUnitStableKey\": \"display-name-only\""
        )

        assertTrue(runCatching { TissuePriorRegistryParser.parse(malformed) }.isFailure)
        assertTrue(runCatching { TissuePriorRegistryParser.parse(displayNameMapping) }.isFailure)
        assertFalse(registry.profileIdByLoadUnitStableKey.containsKey("display-name-only"))
    }

    @Test
    fun linearMixingHasExactEndpointsMidpointAndUnchangedFloor() {
        val stableKey = registry.profileIdByLoadUnitStableKey.keys.first()
        val adjusted = adjusted(stableKey, profileInputs())
        val personalBoundaries = TissuePriorBoundaries(
            adjusted.boundaries.meaningfulFloor,
            adjusted.boundaries.q30 * 2.0,
            adjusted.boundaries.q80 * 2.0,
            adjusted.boundaries.q95 * 2.0
        )
        val personal = TissuePersonalBaseline(stableKey, personalBoundaries, emptyList(), emptyList())

        val priorOnly = TissueEffectiveBaselinePolicy.mix(adjusted, personal, weight(stableKey, 0.0))
        val midpoint = TissueEffectiveBaselinePolicy.mix(adjusted, personal, weight(stableKey, 0.5))
        val personalOnly = TissueEffectiveBaselinePolicy.mix(adjusted, personal, weight(stableKey, 1.0))

        assertEquals(adjusted.boundaries, priorOnly.boundaries)
        assertEquals(personalBoundaries, personalOnly.boundaries)
        assertEquals((adjusted.boundaries.q30 + personalBoundaries.q30) / 2.0, midpoint.boundaries.q30, 1e-12)
        assertEquals((adjusted.boundaries.q80 + personalBoundaries.q80) / 2.0, midpoint.boundaries.q80, 1e-12)
        assertEquals((adjusted.boundaries.q95 + personalBoundaries.q95) / 2.0, midpoint.boundaries.q95, 1e-12)
        assertEquals(adjusted.boundaries.meaningfulFloor, midpoint.boundaries.meaningfulFloor, 0.0)
    }

    @Test
    fun classifierUsesInclusiveQuantileBoundariesAndSymptomMinimums() {
        val baseline = effective("unit", TissuePriorBoundaries(1.0, 2.0, 3.0, 4.0))

        assertEquals(TissueCanonicalStatus.LOW, classify(1.0, baseline))
        assertEquals(TissueCanonicalStatus.LOW, classify(1.5, baseline))
        assertEquals(TissueCanonicalStatus.MODERATE, classify(2.0, baseline))
        assertEquals(TissueCanonicalStatus.HIGH, classify(3.0, baseline))
        assertEquals(TissueCanonicalStatus.VERY_HIGH, classify(4.0, baseline))
        assertEquals(
            TissueCanonicalStatus.HIGH,
            TissueRelativeStateClassifier.classify(1.0, baseline, TissueSymptomOverride.CAUTION).status
        )
        assertEquals(
            TissueCanonicalStatus.VERY_HIGH,
            TissueRelativeStateClassifier.classify(1.0, baseline, TissueSymptomOverride.BLOCK).status
        )
        assertEquals(
            TissueCanonicalStatus.UNAVAILABLE,
            TissueRelativeStateClassifier.classify(1.0, null, TissueSymptomOverride.NONE).status
        )
    }

    @Test
    fun relativeBandPositionIsFiniteMonotonicAndInternal() {
        val boundaries = TissuePriorBoundaries(1.0, 2.0, 3.0, 4.0)
        val positions = listOf(0.0, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 8.0).map {
            TissueRelativeStateClassifier.relativeBandPosition(it, boundaries)
        }

        assertTrue(positions.all(Double::isFinite))
        assertEquals(positions.sorted(), positions)
        assertEquals(0.0, positions.first(), 0.0)
        assertTrue(positions.last() < 4.0)
    }

    @Test
    fun bodyMassFitIsNeutralAndProfileCannotChangePersonalOnlyBoundary() {
        val stableKey = registry.profileIdByLoadUnitStableKey.keys.first()
        val lightUser = adjusted(
            stableKey,
            TissuePriorUserProfileInputs(50.0, 0.1, 0.1, TissueHabitualTrainingIntensity.LIGHT)
        )
        val experiencedUser = adjusted(
            stableKey,
            TissuePriorUserProfileInputs(105.0, 20.0, 20.0, TissueHabitualTrainingIntensity.HARD)
        )
        assertEquals(1.0, lightUser.result.bodyMassContribution, 0.0)
        assertEquals(1.0, experiencedUser.result.bodyMassContribution, 0.0)
        val personal = TissuePersonalBaseline(
            stableKey,
            TissuePriorBoundaries(lightUser.boundaries.meaningfulFloor, 1.0, 2.0, 3.0),
            emptyList(),
            emptyList()
        )

        assertEquals(
            TissueEffectiveBaselinePolicy.mix(lightUser, personal, weight(stableKey, 1.0)).boundaries,
            TissueEffectiveBaselinePolicy.mix(experiencedUser, personal, weight(stableKey, 1.0)).boundaries
        )
    }

    private fun classify(value: Double, baseline: TissueEffectiveBaseline): TissueCanonicalStatus =
        TissueRelativeStateClassifier.classify(value, baseline, TissueSymptomOverride.NONE).status

    private fun adjusted(stableKey: String, inputs: TissuePriorUserProfileInputs): TissueAdjustedPriorBaseline =
        requireNotNull(TissueEffectiveBaselinePolicy.adjustedPrior(registry, stableKey, 12, inputs))

    private fun profileInputs() = TissuePriorUserProfileInputs(null, null, null, null)

    private fun effective(stableKey: String, boundaries: TissuePriorBoundaries): TissueEffectiveBaseline {
        val adjusted = TissueAdjustedPriorBaseline(stableKey, "test", adjustedResult(boundaries))
        return TissueEffectiveBaselinePolicy.mix(adjusted, null, weight(stableKey, 0.0))
    }

    private fun weight(stableKey: String, value: Double) = TissuePerUnitCalibrationWeight(
        loadUnitStableKey = stableKey,
        weightedValidObservationDays = 56.0 * value,
        weightedDistinctExposureDays = 3.0 + 9.0 * value,
        spanWeight = value,
        exposureWeight = value,
        value = value,
        history = TissueCalibrationHistory(null, null, emptyList())
    )

    private fun adjustedResult(boundaries: TissuePriorBoundaries) = TissueAdjustedPriorResult(
        boundaries = boundaries,
        multiplier = 1.0,
        bodyMassContribution = 1.0,
        habitualIntensityContribution = 1.0,
        strengthExperienceContribution = 1.0,
        racketExperienceContribution = 1.0,
        combinedExperienceContribution = 1.0,
        combinedExperienceClampApplied = false,
        normalClampApplied = false,
        hardClampApplied = false,
        missingInputs = emptySet(),
        coefficientSources = emptyMap()
    )

    private fun asset(): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json"),
        File("app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json")
    ).first(File::exists).readText(Charsets.UTF_8)
}
