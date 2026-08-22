package com.training.trackplanner

import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSearchNavigationTest {
    @Test
    fun `active matching calendar result creates one explicit jump request`() {
        assertEquals(
            RecordSearchJumpRequest("2026-08-22", "데드리프트"),
            RecordSearchNavigation.request("2026-08-22", "  데드리프트  ", true)
        )
        assertNull(RecordSearchNavigation.request("2026-08-22", "", true))
        assertNull(RecordSearchNavigation.request("2026-08-22", "데드리프트", false))
    }

    @Test
    fun `jump chooses first confirmed matching entry and ignores planned match`() {
        val records = listOf(
            record(1, "planned", "데드리프트", confirmed = false),
            record(2, "confirmed", "데드리프트", confirmed = true),
            record(3, "other", "스쿼트", confirmed = true)
        )

        assertEquals(
            2L,
            RecordSearchNavigation.firstConfirmedMatch(
                RecordSearchJumpRequest("2026-08-22", "데드리프트"),
                "2026-08-22",
                records,
                emptyMap()
            )
        )
        assertNull(
            RecordSearchNavigation.firstConfirmedMatch(
                RecordSearchJumpRequest("2026-08-22", "데드리프트"),
                "2026-08-23",
                records,
                emptyMap()
            )
        )
    }

    @Test
    fun `current canonical exercise name uses the same confirmed matching semantics`() {
        val records = listOf(record(7, "barbell_deadlift", "예전 이름", confirmed = true))

        assertEquals(
            7L,
            RecordSearchNavigation.firstConfirmedMatch(
                RecordSearchJumpRequest("2026-08-22", "데드리프트"),
                "2026-08-22",
                records,
                mapOf("barbell_deadlift" to "루마니안 데드리프트")
            )
        )
    }

    @Test
    fun `record screen owns saved search and consumes one shot jump before highlight`() {
        val source = source("RecordScreen.kt")

        assertTrue(source.contains("var calendarSearchQuery by rememberSaveable"))
        assertTrue(source.contains("exerciseSearchQuery = calendarSearchQuery"))
        assertTrue(source.contains("pendingSearchJump = null\n        val entryIndex"))
        assertTrue(source.contains("highlighted = highlightedEntryId == entryWithSets.entry.id"))
    }

    private fun record(id: Long, stableKey: String, name: String, confirmed: Boolean) =
        WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = id,
                date = "2026-08-22",
                exerciseStableKey = stableKey,
                exerciseName = name,
                category = "근력운동",
                displayOrder = id.toInt()
            ),
            sets = listOf(WorkoutSet(id = id, entryId = id, setIndex = 1, confirmed = confirmed))
        )

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8).replace("\r\n", "\n")
}
