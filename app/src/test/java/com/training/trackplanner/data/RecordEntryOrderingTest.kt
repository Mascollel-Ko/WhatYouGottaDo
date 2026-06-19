package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordEntryOrderingTest {
    @Test
    fun firstConfirmationSequenceRemainsAThenCThenB() {
        val planned = listOf(1L, 2L, 3L)
        val afterA = RecordEntryOrdering.moveAfter(planned, movingEntryId = 1L, anchorEntryId = null)
        val afterC = RecordEntryOrdering.moveAfter(afterA, movingEntryId = 3L, anchorEntryId = 1L)
        val afterB = RecordEntryOrdering.moveAfter(afterC, movingEntryId = 2L, anchorEntryId = 3L)

        assertEquals(listOf(1L, 2L, 3L), afterA)
        assertEquals(listOf(1L, 3L, 2L), afterC)
        assertEquals(listOf(1L, 3L, 2L), afterB)
    }

    @Test
    fun addedEntryCanBeInsertedImmediatelyBelowLatestConfirmedEntry() {
        assertEquals(
            listOf(1L, 3L, 4L, 2L),
            RecordEntryOrdering.moveAfter(
                orderedEntryIds = listOf(1L, 3L, 2L, 4L),
                movingEntryId = 4L,
                anchorEntryId = 3L
            )
        )
    }
}
