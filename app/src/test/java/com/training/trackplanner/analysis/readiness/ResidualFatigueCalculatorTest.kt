package com.training.trackplanner.analysis.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ResidualFatigueCalculatorTest {
    private val today: LocalDate = LocalDate.parse("2026-06-15")
    private val calculator = ResidualFatigueCalculator()

    @Test
    fun decayFactorsAreClampedAndFutureLoadsAreIgnored() {
        assertEquals(1.0, calculator.decayFactor("SHORT", 0), 0.0001)
        assertEquals(0.0, calculator.decayFactor("SHORT", -1), 0.0001)

        val residual = calculator.calculate(
            dailyLoads = listOf(
                dailyLoad(
                    date = today.plusDays(1),
                    category = FatigueCategoryKey.NEURAL_HEAVY,
                    load = 100.0,
                    averageRpe = 9.0
                )
            ),
            today = today
        )

        assertEquals(0.0, residual.residualByCategory[FatigueCategoryKey.NEURAL_HEAVY] ?: 0.0, 0.0001)
    }

    @Test
    fun sameDayResidualDoesNotExceedOriginalLoad() {
        val load = 100.0

        val residual = residualLoad(
            category = FatigueCategoryKey.NEURAL_HEAVY,
            ageDays = 0,
            load = load,
            averageRpe = 9.0
        )

        assertEquals(load, residual, 0.0001)
    }

    @Test
    fun neuralHeavyPersistsLongerThanLocalMuscle() {
        val neural = residualLoad(FatigueCategoryKey.NEURAL_HEAVY, ageDays = 3)
        val local = residualLoad(FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(neural > local)
    }

    @Test
    fun decelerationPersistsLongerThanLocalMuscle() {
        val deceleration = residualLoad(FatigueCategoryKey.DECELERATION, ageDays = 3)
        val local = residualLoad(FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(deceleration > local)
    }

    @Test
    fun elasticSscPersistsLongerThanLocalMuscle() {
        val elastic = residualLoad(FatigueCategoryKey.ELASTIC_SSC, ageDays = 3)
        val local = residualLoad(FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(elastic > local)
    }

    @Test
    fun rpeNineNeuralHeavyPersistsLongerThanRpeSeven() {
        val highRpe = residualLoad(FatigueCategoryKey.NEURAL_HEAVY, ageDays = 3, averageRpe = 9.0)
        val normalRpe = residualLoad(FatigueCategoryKey.NEURAL_HEAVY, ageDays = 3, averageRpe = 7.0)

        assertTrue(highRpe > normalRpe)
    }

    @Test
    fun rpeNineDecelerationPersistsLongerThanRpeSeven() {
        val highRpe = residualLoad(FatigueCategoryKey.DECELERATION, ageDays = 3, averageRpe = 9.0)
        val normalRpe = residualLoad(FatigueCategoryKey.DECELERATION, ageDays = 3, averageRpe = 7.0)

        assertTrue(highRpe > normalRpe)
    }

    @Test
    fun rpeEightNeuralSpeedPersistsLongerThanRpeSeven() {
        val highRpe = residualLoad(FatigueCategoryKey.NEURAL_SPEED, ageDays = 3, averageRpe = 8.0)
        val normalRpe = residualLoad(FatigueCategoryKey.NEURAL_SPEED, ageDays = 3, averageRpe = 7.0)

        assertTrue(highRpe > normalRpe)
    }

    @Test
    fun sameDayBodyPartResidualDoesNotExceedOriginalLoad() {
        val load = 100.0

        val residual = bodyPartResidual(
            part = "calves_achilles",
            category = FatigueCategoryKey.ELASTIC_SSC,
            ageDays = 0,
            load = load,
            averageRpe = 9.0
        )

        assertEquals(load, residual, 0.0001)
    }

    @Test
    fun futureDatedBodyPartContributionIsIgnored() {
        val residual = calculator.calculate(
            dailyLoads = listOf(
                dailyLoad(
                    date = today.plusDays(1),
                    categoryLoads = mapOf(FatigueCategoryKey.ELASTIC_SSC to 100.0),
                    bodyPartLoads = mapOf("calves_achilles" to 100.0),
                    averageRpe = 9.0
                )
            ),
            today = today
        )

        assertEquals(0.0, residual.residualByBodyPart["calves_achilles"] ?: 0.0, 0.0001)
    }

    @Test
    fun calvesAchillesPersistsLongerWithElasticSscThanLocalMuscle() {
        val elastic = bodyPartResidual("calves_achilles", FatigueCategoryKey.ELASTIC_SSC, ageDays = 3)
        val local = bodyPartResidual("calves_achilles", FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(elastic > local)
    }

    @Test
    fun calvesAchillesPersistsLongerWithDecelerationThanLocalMuscle() {
        val deceleration = bodyPartResidual("calves_achilles", FatigueCategoryKey.DECELERATION, ageDays = 3)
        val local = bodyPartResidual("calves_achilles", FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(deceleration > local)
    }

    @Test
    fun rpeNineCalvesAchillesElasticPersistsLongerThanRpeSeven() {
        val highRpe = bodyPartResidual(
            part = "calves_achilles",
            category = FatigueCategoryKey.ELASTIC_SSC,
            ageDays = 3,
            averageRpe = 9.0
        )
        val normalRpe = bodyPartResidual(
            part = "calves_achilles",
            category = FatigueCategoryKey.ELASTIC_SSC,
            ageDays = 3,
            averageRpe = 7.0
        )

        assertTrue(highRpe > normalRpe)
    }

    @Test
    fun rotatorCuffPersistsLongerWithOverheadRepetition() {
        val overhead = bodyPartResidual("rotator_cuff", FatigueCategoryKey.OVERHEAD_REPETITION, ageDays = 3)
        val local = bodyPartResidual("rotator_cuff", FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(overhead > local)
    }

    @Test
    fun forearmGripPersistsLongerWithGripForearm() {
        val grip = bodyPartResidual("forearm_grip", FatigueCategoryKey.GRIP_FOREARM, ageDays = 3)
        val local = bodyPartResidual("forearm_grip", FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(grip > local)
    }

    @Test
    fun erectorsLowBackPersistsLongerWithNeuralHeavy() {
        val neuralHeavy = bodyPartResidual("erectors_low_back", FatigueCategoryKey.NEURAL_HEAVY, ageDays = 3)
        val local = bodyPartResidual("erectors_low_back", FatigueCategoryKey.LOCAL_MUSCLE, ageDays = 3)

        assertTrue(neuralHeavy > local)
    }

    @Test
    fun bodyPartDecayIsClamped() {
        val load = 100.0

        val residual = bodyPartResidual(
            part = "elbow_extensors",
            categoryLoads = mapOf(
                FatigueCategoryKey.OVERHEAD_REPETITION to load,
                FatigueCategoryKey.GRIP_FOREARM to load
            ),
            ageDays = 0,
            bodyPartLoad = load,
            averageRpe = 10.0,
            profile = "VERY_LONG"
        )

        assertTrue(residual in 0.0..load)
    }

    @Test
    fun baselineGroupResidualKeepsBaseProfile() {
        val residual = calculator.calculate(
            dailyLoads = listOf(
                dailyLoad(
                    date = today.minusDays(3),
                    categoryLoads = mapOf(FatigueCategoryKey.ELASTIC_SSC to 100.0),
                    bodyPartLoads = mapOf("calves_achilles" to 100.0),
                    averageRpe = 9.0,
                    profile = "SHORT"
                )
            ),
            today = today
        )

        assertEquals(0.0, residual.residualByAdaptiveBaselineGroup["fixture_group"] ?: 0.0, 0.0001)
    }

    private fun residualLoad(
        category: FatigueCategoryKey,
        ageDays: Int,
        load: Double = 100.0,
        averageRpe: Double? = 7.0
    ): Double =
        calculator.calculate(
            dailyLoads = listOf(
                dailyLoad(
                    date = today.minusDays(ageDays.toLong()),
                    category = category,
                    load = load,
                    averageRpe = averageRpe
                )
            ),
            today = today
        ).residualByCategory[category] ?: 0.0

    private fun bodyPartResidual(
        part: String,
        category: FatigueCategoryKey,
        ageDays: Int,
        load: Double = 100.0,
        averageRpe: Double? = 7.0,
        profile: String = "SHORT"
    ): Double =
        bodyPartResidual(
            part = part,
            categoryLoads = mapOf(category to load),
            ageDays = ageDays,
            bodyPartLoad = load,
            averageRpe = averageRpe,
            profile = profile
        )

    private fun bodyPartResidual(
        part: String,
        categoryLoads: Map<FatigueCategoryKey, Double>,
        ageDays: Int,
        bodyPartLoad: Double = 100.0,
        averageRpe: Double? = 7.0,
        profile: String = "SHORT"
    ): Double =
        calculator.calculate(
            dailyLoads = listOf(
                dailyLoad(
                    date = today.minusDays(ageDays.toLong()),
                    categoryLoads = categoryLoads,
                    bodyPartLoads = mapOf(part to bodyPartLoad),
                    averageRpe = averageRpe,
                    profile = profile
                )
            ),
            today = today
        ).residualByBodyPart[part] ?: 0.0

    private fun dailyLoad(
        date: LocalDate,
        category: FatigueCategoryKey,
        load: Double,
        averageRpe: Double? = null,
        profile: String = "SHORT"
    ): DailyAnalysisLoad =
        dailyLoad(
            date = date,
            categoryLoads = mapOf(category to load),
            bodyPartLoads = mapOf("quads" to load),
            averageRpe = averageRpe,
            profile = profile
        )

    private fun dailyLoad(
        date: LocalDate,
        categoryLoads: Map<FatigueCategoryKey, Double>,
        bodyPartLoads: Map<String, Double>,
        averageRpe: Double? = null,
        profile: String = "SHORT"
    ): DailyAnalysisLoad {
        val contribution = DailyLoadContribution(
            date = date,
            exerciseId = 1L,
            entryId = 1L,
            exerciseName = "Fixture",
            recoveryDecayProfile = profile,
            categoryLoads = categoryLoads,
            bodyPartLoads = bodyPartLoads,
            baselineGroupLoads = mapOf("fixture_group" to categoryLoads.values.sum()),
            completedSets = 1,
            totalReps = 5,
            durationMinutes = null,
            averageRpe = averageRpe
        )

        return DailyAnalysisLoad(
            date = date,
            categoryLoads = contribution.categoryLoads,
            bodyPartLoads = contribution.bodyPartLoads,
            baselineGroupLoads = contribution.baselineGroupLoads,
            completedEntryCount = 1,
            completedSetCount = 1,
            contributions = listOf(contribution)
        )
    }
}
