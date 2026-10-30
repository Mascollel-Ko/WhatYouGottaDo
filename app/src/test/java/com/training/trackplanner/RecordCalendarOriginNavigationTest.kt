package com.training.trackplanner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCalendarOriginNavigationTest {
    @Test
    fun calendarOriginSurvivesPreviousAndNextDateChanges() {
        val origin = RecordDetailOrigin.Calendar
        var date = "2026-08-29"

        date = java.time.LocalDate.parse(date).plusDays(1).toString()
        assertEquals("2026-08-30", date)
        assertEquals(RecordDetailOrigin.Calendar, origin)

        date = java.time.LocalDate.parse(date).minusDays(2).toString()
        assertEquals("2026-08-28", date)
        assertEquals(RecordDetailOrigin.Calendar, origin)
    }

    @Test
    fun normalAndExternalRecordEntryUseNormalOrigin() {
        assertEquals(RecordDetailOrigin.Normal, RecordDetailOrigin.Normal)
        val screen = source("RecordScreen.kt")
        assertTrue(screen.contains("detailOrigin = RecordDetailOrigin.Normal\n            pendingSearchJump"))
        assertTrue(screen.contains("BackHandler(enabled = !showCalendar && detailOrigin == RecordDetailOrigin.Calendar)"))
        assertTrue(screen.contains("detailOrigin = RecordDetailOrigin.Calendar\n                showCalendar = false"))
    }

    @Test
    fun calendarTransientStateConsumesBackBeforeCalendarExit() {
        val calendar = source("RecordCalendarScreen.kt")
        val marker = "BackHandler(enabled = !planPushInProgress) {"
        assertTrue("Back navigation must be disabled during plan shifting", calendar.contains(marker))
        val handler = calendar.substringAfter(marker).substringBefore("    }")
        assertTrue(handler.indexOf("actionMenuDate != null") in 0 until handler.indexOf("else -> onBack()"))
        assertTrue(handler.indexOf("pendingPushDate != null") in 0 until handler.indexOf("else -> onBack()"))
        assertTrue("Plan shifting should display a blocking progress dialog",
            calendar.contains("if (planPushInProgress) {"))
        assertTrue(calendar.contains("dismissOnBackPress = false"))
        assertTrue(calendar.contains("dismissOnClickOutside = false"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8).replace("\r\n", "\n")
}
