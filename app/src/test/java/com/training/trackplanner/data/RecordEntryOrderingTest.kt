package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordEntryOrderingTest {
    private fun row(id: Long, order: Int, first: Long? = null) = WorkoutEntryWithSets(
        WorkoutEntry(id=id,date="2026-09-12",exerciseStableKey="key$id",exerciseName="name$id",category="test",
            displayOrder=order,firstConfirmedAt=first), listOf(WorkoutSet(entryId=id,setIndex=1,reps=5,weightKg=40.0)))

    @Test fun firstPerformedSquatMovesAbovePushUpWithoutChangingContent() {
        val pushUp=row(1,1)
        val squat=row(2,2,100)
        assertEquals(listOf(squat,pushUp),RecordEntryOrdering.insertNewlyPerformed(listOf(pushUp,squat),2))
    }

    @Test fun manualPerformedOrderAndUnperformedRelativeOrderSurviveInsertion() {
        val pullUp=row(1,1,200)
        val squat=row(2,2,100)
        val pushUp=row(3,3)
        val curl=row(4,4,300)
        val other=row(5,5)
        assertEquals(listOf(pullUp,squat,curl,pushUp,other),
            RecordEntryOrdering.insertNewlyPerformed(listOf(other,curl,pushUp,squat,pullUp),4))
    }

    @Test fun insertionPreservesInterleavedUnperformedRowsRatherThanRebuildingGroups() {
        val rows=listOf(row(1,1),row(2,2,200),row(3,3),row(4,4,100),row(5,5,300))
        assertEquals(rows,RecordEntryOrdering.insertNewlyPerformed(rows,5))
        assertEquals(rows,RecordEntryOrdering.insertNewlyPerformed(rows,999))
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
