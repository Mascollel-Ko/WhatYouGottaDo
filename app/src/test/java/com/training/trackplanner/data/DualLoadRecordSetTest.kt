package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DualLoadRecordSetTest {
    private val original = WorkoutSet(
        id = 7L,
        entryId = 3L,
        setIndex = 1,
        reps = 10,
        weightKg = 25.0,
        secondaryWeightKg = 12.0,
        confirmed = true
    )

    @Test fun editingDumbbellLoadDoesNotOverwriteCableOrConfirmation() {
        val edited = original.copy(secondaryWeightKg = 14.0)
            .edit(RecordSetField.SECONDARY_WEIGHT)
            .applyTo(original)
        assertEquals(25.0, edited.weightKg, 0.0)
        assertEquals(14.0, edited.secondaryWeightKg!!, 0.0)
        assertEquals(10, edited.reps)
        assertEquals(true, edited.confirmed)
    }

    @Test fun staleCableEditCannotEraseNewerDumbbellLoad() {
        val newer = original.copy(secondaryWeightKg = 16.0)
        val edited = original.copy(weightKg = 27.5)
            .edit(RecordSetField.WEIGHT)
            .applyTo(newer)
        assertEquals(27.5, edited.weightKg, 0.0)
        assertEquals(16.0, edited.secondaryWeightKg!!, 0.0)
    }

    @Test fun legacySingleLoadRemainsNull() {
        val legacy = WorkoutSet(entryId = 3L, setIndex = 1, weightKg = 40.0)
        assertNull(legacy.secondaryWeightKg)
        assertNull(legacy.copy(reps = 8).secondaryWeightKg)
    }
}
