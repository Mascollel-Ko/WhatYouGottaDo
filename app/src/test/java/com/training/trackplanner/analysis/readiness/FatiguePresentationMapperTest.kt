package com.training.trackplanner.analysis.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FatiguePresentationMapperTest {
    private val mapper = FatiguePresentationMapper()

    @Test
    fun emptyPressureMapsProduceZeroScoresAndNoRestrictions() {
        val result = mapper.map(snapshot())

        assertEquals(0, result.overallScore)
        assertEquals(0, result.highForceNeuralScore)
        assertEquals(0, result.localMuscularScore)
        assertEquals(0, result.highSpeedScore)
        assertEquals(0, result.systemicMuscularScore)
        assertEquals(0, result.reactiveScore)
        assertTrue(result.highCategories.isEmpty())
        assertTrue(result.highBodyParts.isEmpty())
        assertFalse(result.gate.heavyLowerRestricted)
        assertFalse(result.gate.highImpactRestricted)
        assertFalse(result.gate.codReactiveRestricted)
        assertFalse(result.gate.upperPushRestricted)
        assertFalse(result.gate.overheadRestricted)
        assertFalse(result.gate.gripForearmRestricted)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun highNeuralHeavyProducesNeuralScoreAndHeavyLowerRestriction() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.NEURAL_HEAVY to highPressure("NEURAL_HEAVY")))
        )

        assertTrue(result.highForceNeuralScore >= 70)
        assertTrue(result.gate.heavyLowerRestricted)
        assertEquals(7, result.gate.rpeCap)
        assertTrue(result.reasons.contains("High neural fatigue pressure"))
    }

    @Test
    fun highNeuralSpeedContributesToHighSpeedWithoutReactiveCoupling() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.NEURAL_SPEED to highPressure("NEURAL_SPEED")))
        )

        assertTrue(result.highSpeedScore >= 70)
        assertEquals(0, result.highForceNeuralScore)
        assertEquals(0, result.reactiveScore)
        assertTrue(result.gate.codReactiveRestricted)
        assertTrue(result.reasons.contains("High neural speed pressure"))
    }

    @Test
    fun highDecelerationProducesReactiveScoreAndHighImpactCodRestriction() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.DECELERATION to highPressure("DECELERATION")))
        )

        assertTrue(result.reactiveScore >= 70)
        assertEquals(0, result.highSpeedScore)
        assertTrue(result.gate.highImpactRestricted)
        assertTrue(result.gate.codReactiveRestricted)
        assertTrue(result.reasons.contains("High deceleration / change-of-direction load"))
    }

    @Test
    fun highElasticSscProducesJointTendonScoreAndHighImpactRestriction() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.ELASTIC_SSC to highPressure("ELASTIC_SSC")))
        )

        assertTrue(result.highSpeedScore >= 70)
        assertTrue(result.gate.highImpactRestricted)
        assertTrue(result.gate.codReactiveRestricted)
        assertTrue(result.reasons.contains("High elastic SSC / landing load"))
    }

    @Test
    fun highLocalMuscleProducesLocalMuscleScore() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.LOCAL_MUSCLE to highPressure("LOCAL_MUSCLE")))
        )

        assertTrue(result.localMuscularScore >= 70)
    }

    @Test
    fun highSystemicProducesSystemicScore() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.SYSTEMIC to highPressure("SYSTEMIC")))
        )

        assertTrue(result.systemicMuscularScore >= 70)
        assertTrue(result.reasons.contains("High systemic fatigue pressure"))
    }

    @Test
    fun highBadmintonCourtContributesToSystemicHighSpeedAndReactiveAxes() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.BADMINTON_COURT to highPressure("BADMINTON_COURT")))
        )

        assertTrue(result.systemicMuscularScore in 1..69)
        assertTrue(result.highSpeedScore in 1..69)
        assertTrue(result.reactiveScore >= 70)
        assertTrue(result.highSpeedScore != result.reactiveScore)
        assertTrue(result.gate.codReactiveRestricted)
        assertTrue(result.highCategories.any { item -> item.category == FatigueCategoryKey.BADMINTON_COURT })
        assertTrue(result.reasons.contains("High badminton court pressure"))
    }

    @Test
    fun highCalvesAchillesBodyPartRestrictsHighImpactAndCod() {
        val result = mapper.map(
            snapshot(bodyParts = mapOf("calves_achilles" to highPressure("calves_achilles")))
        )

        assertTrue(result.gate.highImpactRestricted)
        assertTrue(result.gate.codReactiveRestricted)
        assertTrue(result.highBodyParts.any { item -> item.key == "calves_achilles" })
        assertTrue(result.reasons.contains("High calves-achilles pressure"))
    }

    @Test
    fun highShoulderBodyPartRestrictsUpperPushAndOverhead() {
        val result = mapper.map(
            snapshot(bodyParts = mapOf("shoulders" to highPressure("shoulders")))
        )

        assertTrue(result.gate.upperPushRestricted)
        assertTrue(result.gate.overheadRestricted)
        assertTrue(result.reasons.contains("High shoulder / rotator cuff pressure"))
    }

    @Test
    fun highForearmGripBodyPartRestrictsGripForearm() {
        val result = mapper.map(
            snapshot(bodyParts = mapOf("forearm_grip" to highPressure("forearm_grip")))
        )

        assertTrue(result.gate.gripForearmRestricted)
        assertTrue(result.reasons.contains("High grip / forearm pressure"))
    }

    @Test
    fun publicScoresAreClampedToZeroToOneHundred() {
        val result = mapper.map(
            snapshot(
                categories = mapOf(
                    FatigueCategoryKey.NEURAL_HEAVY to extremePressure("NEURAL_HEAVY"),
                    FatigueCategoryKey.LOCAL_MUSCLE to extremePressure("LOCAL_MUSCLE"),
                    FatigueCategoryKey.DECELERATION to extremePressure("DECELERATION"),
                    FatigueCategoryKey.SYSTEMIC to extremePressure("SYSTEMIC"),
                    FatigueCategoryKey.BADMINTON_COURT to extremePressure("BADMINTON_COURT")
                ),
                bodyParts = mapOf("calves_achilles" to extremePressure("calves_achilles"))
            )
        )

        listOf(
            result.overallScore,
            result.highForceNeuralScore,
            result.localMuscularScore,
            result.highSpeedScore,
            result.systemicMuscularScore,
            result.reactiveScore
        ).forEach { score ->
            assertTrue(score in 0..100)
        }
        assertTrue(result.highCategories.all { item -> item.score in 0..100 })
        assertTrue(result.highBodyParts.all { item -> item.score in 0..100 })
    }

    @Test
    fun elevatedPresentationScoresUseRelaxedCutoffs() {
        val result = mapper.map(
            snapshot(categories = mapOf(FatigueCategoryKey.SYSTEMIC to elevatedFloorPressure("SYSTEMIC")))
        )

        assertEquals(72, result.systemicMuscularScore)
        assertFalse(result.gate.heavyLowerRestricted)
        assertFalse(result.gate.highImpactRestricted)
        assertFalse(result.gate.codReactiveRestricted)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun fatigueThresholdsKeepOfiStableAndRelaxReadinessGates() {
        assertEquals(75, com.training.trackplanner.analysis.fatigue.FatigueThresholds.OFI_ELEVATED_START)
        assertEquals(87, com.training.trackplanner.analysis.fatigue.FatigueThresholds.OFI_CAUTION_START)
        assertEquals(98, com.training.trackplanner.analysis.fatigue.FatigueThresholds.OFI_HIGH_START)
        assertEquals(72, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESENTATION_ELEVATED_SCORE)
        assertEquals(84, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESENTATION_RESTRICTED_SCORE)
        assertEquals(54, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESENTATION_VOLUME_YELLOW_START)
        assertEquals(72, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESENTATION_VOLUME_ORANGE_START)
        assertEquals(90, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESENTATION_VOLUME_RED_START)
        assertEquals(1.3754, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESSURE_ELEVATED_RATIO, 0.00001)
        assertEquals(1.6146, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESSURE_HIGH_RATIO, 0.00001)
        assertEquals(1.9136, com.training.trackplanner.analysis.fatigue.FatigueThresholds.PRESSURE_VERY_HIGH_RATIO, 0.00001)
        assertEquals(1.196, com.training.trackplanner.analysis.fatigue.FatigueThresholds.Z_ELEVATED, 0.00001)
        assertEquals(1.794, com.training.trackplanner.analysis.fatigue.FatigueThresholds.Z_HIGH, 0.00001)
        assertEquals(2.392, com.training.trackplanner.analysis.fatigue.FatigueThresholds.Z_VERY_HIGH, 0.00001)
    }

    private fun snapshot(
        categories: Map<FatigueCategoryKey, FatiguePressure> = emptyMap(),
        bodyParts: Map<String, FatiguePressure> = emptyMap()
    ): FatiguePressureSnapshot =
        FatiguePressureSnapshot(
            categoryPressures = categories,
            baselineGroupPressures = emptyMap(),
            bodyPartPressures = bodyParts
        )

    private fun highPressure(key: String): FatiguePressure =
        pressure(
            key = key,
            ratio = 1.45,
            zScore = 1.7,
            percentile = 90.0,
            level = FatigueLevel.HIGH
        )

    private fun extremePressure(key: String): FatiguePressure =
        pressure(
            key = key,
            ratio = 5.0,
            zScore = 8.0,
            percentile = 150.0,
            level = FatigueLevel.VERY_HIGH
        )

    private fun elevatedFloorPressure(key: String): FatiguePressure =
        pressure(
            key = key,
            ratio = 1.0,
            zScore = 0.0,
            percentile = 0.0,
            level = FatigueLevel.ELEVATED
        )

    private fun pressure(
        key: String,
        ratio: Double,
        zScore: Double,
        percentile: Double,
        level: FatigueLevel
    ): FatiguePressure =
        FatiguePressure(
            key = key,
            currentResidualLoad = ratio * 100.0,
            adaptiveTolerance = 100.0,
            rollingMean = 80.0,
            rollingStd = 10.0,
            zScore = zScore,
            percentile = percentile,
            pressure = ratio,
            level = level,
            confidence = AnalysisConfidence.MEDIUM,
            baselineTrend = BaselineTrend.STABLE
        )
}
