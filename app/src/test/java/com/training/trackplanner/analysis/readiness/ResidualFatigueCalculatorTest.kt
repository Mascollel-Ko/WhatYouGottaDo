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

    private fun dailyLoad(
        date: LocalDate,
        category: FatigueCategoryKey,
        load: Double,
        averageRpe: Double? = null,
        profile: String = "SHORT"
    ): DailyAnalysisLoad {
        val contribution = DailyLoadContribution(
            date = date,
            exerciseId = 1L,
            entryId = 1L,
            exerciseName = "Fixture",
            recoveryDecayProfile = profile,
            categoryLoads = mapOf(category to load),
            bodyPartLoads = mapOf("quads" to load),
            baselineGroupLoads = mapOf("fixture_group" to load),
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
