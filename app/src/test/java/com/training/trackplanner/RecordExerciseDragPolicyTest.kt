package com.training.trackplanner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordExerciseDragPolicyTest {
    @Test
    fun `moves first entry to last target and last entry to middle target`() {
        assertEquals(
            listOf(2L, 3L, 1L),
            RecordExerciseDragPolicy.reorderedEntryIds(listOf(1L, 2L, 3L), 1L, 3L)
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            RecordExerciseDragPolicy.reorderedEntryIds(listOf(1L, 2L, 3L), 3L, 2L)
        )
    }

    @Test
    fun `nearest visible card becomes the drop target`() {
        val visible = listOf(
            VisibleRecordEntry(1L, 0, 100),
            VisibleRecordEntry(2L, 110, 210),
            VisibleRecordEntry(3L, 220, 320)
        )

        assertEquals(2L, RecordExerciseDragPolicy.targetEntryId(visible, 175f))
        assertEquals(3L, RecordExerciseDragPolicy.targetEntryId(visible, 290f))
    }

    @Test
    fun `drag gesture stays on exercise header and uses existing lazy list`() {
        val screen = source("RecordScreen.kt")
        val card = source("RecordEntryCard.kt")

        assertTrue(screen.contains("detectDragGesturesAfterLongPress"))
        assertTrue(screen.contains("state = listState"))
        assertTrue(screen.contains("headerDragModifier = dragModifier"))
        assertTrue(card.contains(".then(headerDragModifier)"))
        assertFalse(card.substringAfter("\n    Card(").substringBefore(") {").contains("headerDragModifier"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8)
}
