package com.training.trackplanner.data.v22

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BBlockPeriodizationTest {
    @Test fun defaultIsOneFourWeekSteadyBlock() {
        val p = BBlockProgramDefinition()
        assertEquals(1, p.blocks.size)
        assertEquals(4, p.trainingWeeks)
        assertEquals(28, p.durationDays)
        assertEquals(listOf(0, 0, 0, 0), BBlockPeriodization.steps(p.blocks.single()))
    }

    @Test fun supportedWaveLengthsHaveTheAgreedShapes() {
        val expected = mapOf(
            3 to listOf(0, 2, 1),
            4 to listOf(0, 2, 0, 2),
            5 to listOf(0, 1, 2, 0, 2),
            6 to listOf(0, 1, 2, 0, 1, 2)
        )
        expected.forEach { (weeks, steps) ->
            assertEquals(steps, BBlockPeriodization.steps(BBlockDefinition(weeks, pattern = BVolumePattern.WAVE)))
        }
    }

    @Test fun shortBlocksRejectInvalidPeriodization() {
        for (weeks in 1..2) for (pattern in listOf(BVolumePattern.WAVE, BVolumePattern.ACCUMULATE_RECOVER)) {
            val result = runCatching { BBlockDefinition(weeks, pattern = pattern) }
            assertTrue(result.isFailure)
        }
        assertTrue(runCatching { BBlockDefinition(1, pattern = BVolumePattern.PROGRESSIVE) }.isFailure)
        assertEquals(listOf(0, 1), BBlockPeriodization.steps(BBlockDefinition(2, pattern = BVolumePattern.PROGRESSIVE)))
    }

    @Test fun accumulateThenRecoverOnlyInFinalWeek() {
        (3..6).forEach { weeks ->
            val steps = BBlockPeriodization.steps(BBlockDefinition(weeks, pattern = BVolumePattern.ACCUMULATE_RECOVER))
            assertEquals((0 until weeks - 1).toList(), steps.dropLast(1))
            assertEquals(-1, steps.last())
        }
    }

    @Test fun increaseAndDecreaseHaveIndependentOrderings() {
        assertEquals(
            listOf(BProgressionField.LOAD, BProgressionField.SETS, BProgressionField.REPS),
            BBlockPeriodization.increasePriority(BBlockGoal.STRENGTH)
        )
        assertEquals(
            listOf(BProgressionField.REPS, BProgressionField.LOAD, BProgressionField.SETS),
            BBlockPeriodization.increasePriority(BBlockGoal.HYPERTROPHY)
        )
        assertEquals(
            listOf(BProgressionField.SETS, BProgressionField.REPS, BProgressionField.LOAD),
            BBlockPeriodization.decreasePriority()
        )
    }

    @Test fun manualLoadCalibrationNeverInventsInitialKg() {
        assertNull(BBlockPeriodization.proposedLoad(null, 2, 1.25))
        assertNull(BBlockPeriodization.proposedLoad(0.0, 2, 1.25))
        assertEquals(105.0, BBlockPeriodization.proposedLoad(100.0, 2, 1.25)!!, 0.001)
        assertEquals(6, BBlockPeriodization.proposedReps(5, 1))
        assertEquals(4, BBlockPeriodization.proposedMainSets(3, 1))
    }

    @Test fun recoveryOnlyOccupiesBlockBoundaries() {
        val p = BBlockProgramDefinition(
            blocks = listOf(BBlockDefinition(), BBlockDefinition(weeks = 3)),
            recoveryDaysBetween = listOf(4)
        )
        assertEquals(7, p.trainingWeeks)
        assertEquals(53, p.durationDays)
        assertTrue(runCatching { p.copy(recoveryDaysBetween = listOf(3, 4)) }.isFailure)
    }
}
