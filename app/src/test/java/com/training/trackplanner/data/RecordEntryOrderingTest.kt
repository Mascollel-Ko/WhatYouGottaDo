package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordEntryOrderingTest {
    @Test fun performedChronologyPrecedesUnperformedPresentationOrderWithoutContentMutation() {
        fun row(id: Long, order: Int, first: Long?) = WorkoutEntryWithSets(
            WorkoutEntry(id=id,date="2026-09-11",exerciseStableKey="key$id",exerciseName="name$id",category="test",
                displayOrder=order,firstConfirmedAt=first), emptyList())
        val pushUp=row(1,1,null)
        val squat=row(2,2,100)
        val pullUp=row(3,3,null)
        assertEquals(listOf(squat,pushUp,pullUp),RecordEntryOrdering.firstConfirmationOrder(listOf(pushUp,squat,pullUp)))
        val startedPullUp=pullUp.copy(entry=pullUp.entry.copy(firstConfirmedAt=200))
        val another=row(4,4,null)
        assertEquals(listOf(squat,startedPullUp,pushUp,another),
            RecordEntryOrdering.firstConfirmationOrder(listOf(another,startedPullUp,pushUp,squat)))
        // A later drag may reverse performed rows; the next first-confirmation event repairs chronology.
        assertEquals(listOf(squat,startedPullUp,pushUp),RecordEntryOrdering.firstConfirmationOrder(listOf(
            startedPullUp.copy(entry=startedPullUp.entry.copy(displayOrder=1)),
            squat.copy(entry=squat.entry.copy(displayOrder=2)),pushUp.copy(entry=pushUp.entry.copy(displayOrder=3))))
            .map { original -> listOf(squat,startedPullUp,pushUp).first { it.entry.id==original.entry.id } })
    }
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
