package com.training.trackplanner

import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordBulkEditTest {
    private val entry = WorkoutEntry(
        id = 1,
        date = "2026-06-14",
        exerciseId = 1,
        exerciseName = "스쿼트",
        category = "근력",
        restSeconds = 90
    )

    @Test
    fun repsDeltaAccumulatesAndClampsAtZero() {
        val set = set(reps = 5)
        val plus = BulkOperation("횟수 +", BulkField.Reps, BulkMode.Increase)
        val minus = BulkOperation("횟수 -", BulkField.Reps, BulkMode.Decrease)

        assertEquals("6", addBulkDelta(addBulkDelta(addBulkDelta("0", "2"), "2"), "2"))
        assertEquals(11, applyBulkOperation(entry, set, plus, 6.0).reps)
        assertEquals(0, applyBulkOperation(entry, set, minus, 20.0).reps)
    }

    @Test
    fun weightDeltaSupportsCompactOptionsAndClampsAtZero() {
        val set = set(weightKg = 4.0)
        val plus = BulkOperation("kg +", BulkField.Weight, BulkMode.Increase)
        val minus = BulkOperation("kg -", BulkField.Weight, BulkMode.Decrease)

        assertEquals("6", addBulkDelta(addBulkDelta(addBulkDelta("0", "2.5"), "2.5"), "1"))
        assertEquals(10.0, applyBulkOperation(entry, set, plus, 6.0).weightKg, 0.001)
        assertEquals(0.0, applyBulkOperation(entry, set, minus, 5.0).weightKg, 0.001)
    }

    @Test
    fun rpeBulkValidationAllowsValueOrClearWithoutChangingWeightOrReps() {
        val operation = BulkOperation("RPE 설정", BulkField.Rpe, BulkMode.Set)

        assertTrue(canApplyBulkOperation(operation, "8", set()))
        assertTrue(canApplyBulkOperation(operation, "__CLEAR_RPE__", set()))
        assertFalse(canApplyBulkOperation(operation, "11", set()))
    }

    @Test
    fun copyWithoutStatusKeepsTargetConfirmedState() {
        val source = set(reps = 5, weightKg = 100.0, rpe = 8.0, confirmed = true)
        val target = set(reps = 1, weightKg = 20.0, rpe = null, confirmed = false)

        val copied = copyBulkSetValues(source, target, includeStatus = false)

        assertEquals(5, copied.reps)
        assertEquals(100.0, copied.weightKg, 0.001)
        assertEquals(8.0, copied.rpe ?: 0.0, 0.001)
        assertFalse(copied.confirmed)
    }

    @Test
    fun copyWithStatusCopiesConfirmedState() {
        val source = set(reps = 5, weightKg = 100.0, confirmed = true)
        val target = set(reps = 1, weightKg = 20.0, confirmed = false)

        val copied = copyBulkSetValues(source, target, includeStatus = true)

        assertTrue(copied.confirmed)
    }

    private fun set(
        reps: Int = 0,
        weightKg: Double = 0.0,
        rpe: Double? = null,
        confirmed: Boolean = false
    ): WorkoutSet =
        WorkoutSet(
            id = 10,
            entryId = 1,
            setIndex = 1,
            reps = reps,
            weightKg = weightKg,
            rpe = rpe,
            confirmed = confirmed
        )
}
