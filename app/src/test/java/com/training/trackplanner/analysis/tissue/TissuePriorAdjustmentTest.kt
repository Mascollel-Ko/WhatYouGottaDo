package com.training.trackplanner.analysis.tissue

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TissuePriorAdjustmentTest {
    @Test
    fun referenceProfileIsNeutralAndUsesOneMultiplierWithoutScalingFloor() {
        val fixture = fixture()
        val result = TissuePriorAdjustment.adjust(
            fixture.base,
            fixture.profile,
            TissuePriorUserProfileInputs(
                bodyWeightKg = 75.0,
                strengthTrainingExperienceYears = 3.0,
                racketSportExperienceYears = 3.0,
                habitualTrainingIntensity = TissueHabitualTrainingIntensity.NORMAL
            )
        )

        assertEquals(1.0, result.multiplier, 1e-12)
        assertEquals(fixture.base.meaningfulFloor, result.boundaries.meaningfulFloor, 0.0)
        assertEquals(result.multiplier, result.boundaries.q30 / fixture.base.q30, 1e-12)
        assertEquals(result.multiplier, result.boundaries.q80 / fixture.base.q80, 1e-12)
        assertEquals(result.multiplier, result.boundaries.q95 / fixture.base.q95, 1e-12)
        assertTrue(result.missingInputs.isEmpty())
    }

    @Test
    fun missingInputsAreNeutralAndPreserveExplicitProvenance() {
        val fixture = fixture()
        val result = TissuePriorAdjustment.adjust(
            fixture.base,
            fixture.profile,
            TissuePriorUserProfileInputs(null, null, null, null)
        )

        assertEquals(1.0, result.multiplier, 1e-12)
        assertEquals(TissuePriorMissingInput.entries.toSet(), result.missingInputs)
        assertEquals(1.0, result.bodyMassContribution, 0.0)
        assertEquals(1.0, result.habitualIntensityContribution, 0.0)
        assertEquals(1.0, result.strengthExperienceContribution, 0.0)
        assertEquals(1.0, result.racketExperienceContribution, 0.0)
    }

    @Test
    fun relevanceZeroMakesThatExperienceDomainNeutral() {
        val fixture = fixture()
        val profile = fixture.profile.copy(
            strengthExperienceRelevance = 0.0,
            racketExperienceRelevance = 0.0
        )
        val novice = TissuePriorAdjustment.adjust(
            fixture.base,
            profile,
            TissuePriorUserProfileInputs(75.0, 0.0, 0.0, TissueHabitualTrainingIntensity.NORMAL)
        )
        val expert = TissuePriorAdjustment.adjust(
            fixture.base,
            profile,
            TissuePriorUserProfileInputs(75.0, 50.0, 50.0, TissueHabitualTrainingIntensity.NORMAL)
        )

        assertEquals(novice, expert)
        assertEquals(1.0, novice.combinedExperienceContribution, 0.0)
    }

    @Test
    fun normalAndHardClampsBoundExtremeButFiniteInputs() {
        val fixture = fixture()
        val extreme = fixture.profile.copy(bodyMassBeta = 10.0)
        val result = TissuePriorAdjustment.adjust(
            fixture.base,
            extreme,
            TissuePriorUserProfileInputs(105.0, 50.0, 50.0, TissueHabitualTrainingIntensity.HARD)
        )

        assertEquals(1.15, result.multiplier, 1e-12)
        assertTrue(result.normalClampApplied)
        assertTrue(result.hardClampApplied)
        assertTrue(result.multiplier in extreme.normalClampMin..extreme.normalClampMax)
        assertTrue(result.multiplier in extreme.hardClampMin..extreme.hardClampMax)
    }

    @Test
    fun invalidNumericInputsAreRejectedAndExperienceScoreCapsAtTenYears() {
        val fixture = fixture()
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                TissuePriorAdjustment.adjust(
                    fixture.base,
                    fixture.profile,
                    TissuePriorUserProfileInputs(invalid, 3.0, 3.0, TissueHabitualTrainingIntensity.NORMAL)
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) { TissuePriorAdjustment.experienceScore(-0.1) }
        assertEquals(-1.0, TissuePriorAdjustment.experienceScore(0.0), 0.0)
        assertEquals(-0.5, TissuePriorAdjustment.experienceScore(0.5), 0.0)
        assertEquals(0.0, TissuePriorAdjustment.experienceScore(2.0), 0.0)
        assertEquals(0.5, TissuePriorAdjustment.experienceScore(5.0), 0.0)
        assertEquals(1.0, TissuePriorAdjustment.experienceScore(10.0), 0.0)
        assertEquals(1.0, TissuePriorAdjustment.experienceScore(1_000_000.0), 0.0)
    }

    @Test
    fun everySupportedProfileInputCombinationIsFiniteOrderedAndDeterministic() {
        registry.getJSONArray("profiles").objects().forEach { profileJson ->
            val fixture = fixture(profileJson)
            listOf<Double?>(null, 50.0, 65.0, 75.0, 90.0, 105.0).forEach { bodyWeight ->
                listOf<Double?>(null, 0.0, 1.0, 3.0, 7.0, 12.0).forEach { strengthYears ->
                    listOf<Double?>(null, 0.0, 3.0, 12.0).forEach { racketYears ->
                        listOf<TissueHabitualTrainingIntensity?>(
                            null,
                            TissueHabitualTrainingIntensity.LIGHT,
                            TissueHabitualTrainingIntensity.NORMAL,
                            TissueHabitualTrainingIntensity.HARD
                        ).forEach { intensity ->
                            val inputs = TissuePriorUserProfileInputs(
                                bodyWeight,
                                strengthYears,
                                racketYears,
                                intensity
                            )
                            val first = TissuePriorAdjustment.adjust(fixture.base, fixture.profile, inputs)
                            val second = TissuePriorAdjustment.adjust(fixture.base, fixture.profile, inputs)
                            assertEquals(first, second)
                            assertTrue(first.multiplier.isFinite() && first.multiplier > 0.0)
                            assertTrue(first.multiplier in 0.85..1.15)
                            assertEquals(fixture.base.meaningfulFloor, first.boundaries.meaningfulFloor, 0.0)
                            assertTrue(
                                first.boundaries.meaningfulFloor < first.boundaries.q30 &&
                                    first.boundaries.q30 < first.boundaries.q80 &&
                                    first.boundaries.q80 < first.boundaries.q95
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun adjustmentContractHasNoCurrentLoadOrRecoveryInput() {
        val parameterTypes = TissuePriorAdjustment::class.java.declaredMethods
            .filter { it.name == "adjust" }
            .flatMap { it.parameterTypes.toList() }
            .map(Class<*>::getSimpleName)

        assertFalse(parameterTypes.any {
            it.contains("Exposure") || it.contains("Residual") || it.contains("Recovery") || it.contains("Current")
        })
    }

    private fun fixture(profileJson: JSONObject = registry.getJSONArray("profiles").getJSONObject(0)): Fixture {
        val bucket = profileJson.getJSONArray("evaluationBuckets").getJSONObject(0)
        val body = profileJson.getJSONObject("bodyMass")
        val habitual = profileJson.getJSONObject("habitualIntensity")
        val experience = profileJson.getJSONObject("experience")
        return Fixture(
            base = TissuePriorBoundaries(
                meaningfulFloor = bucket.getDouble("meaningfulFloor"),
                q30 = bucket.getDouble("q30"),
                q80 = bucket.getDouble("q80"),
                q95 = bucket.getDouble("q95")
            ),
            profile = TissuePriorProfileAdjustment(
                priorProfileId = profileJson.getString("priorProfileId"),
                bodyMassBeta = body.getDouble("beta"),
                bodyMassSource = enumValueOf(body.getString("source")),
                lightIntensityLogOffset = habitual.getDouble("lightLogOffset"),
                hardIntensityLogOffset = habitual.getDouble("hardLogOffset"),
                habitualIntensitySource = enumValueOf(habitual.getString("source")),
                strengthExperienceLogCoefficient = experience.getDouble("strengthExperienceLogCoefficient"),
                strengthExperienceRelevance = experience.getDouble("strengthExperienceRelevance"),
                strengthExperienceSource = enumValueOf(experience.getString("strengthExperienceSource")),
                racketExperienceLogCoefficient = experience.getDouble("racketExperienceLogCoefficient"),
                racketExperienceRelevance = experience.getDouble("racketExperienceRelevance"),
                racketExperienceSource = enumValueOf(experience.getString("racketExperienceSource"))
            )
        )
    }

    private data class Fixture(
        val base: TissuePriorBoundaries,
        val profile: TissuePriorProfileAdjustment
    )

    companion object {
        private val repoRoot: File = sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .first { File(it, ConnectiveTissuePriorGenerator.CANONICAL_PATH).isFile }
        private val registry = JSONObject(
            File(repoRoot, ConnectiveTissuePriorGenerator.CANONICAL_PATH).readText(Charsets.UTF_8)
        )
    }
}

private fun org.json.JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
