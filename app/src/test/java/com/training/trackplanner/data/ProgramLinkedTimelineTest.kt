package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramLinkedTimelineTest {
    private fun link(
        entryId: Long, sequence: Int, application: String = "applied-1",
        track: String = "track-1", mode: ProgressionMode = ProgressionMode.APP
    ) = ProgramWorkoutLink(
        entryId = entryId, applicationId = application,
        sourceProgramStableKey = "program-1", sourceItemId = "item-" + entryId,
        programName = "Demo", weekNumber = sequence, dayOfWeek = 1,
        trackId = track, trackLabel = "Squat", sequence = sequence,
        role = ProgressionRole.MAIN, mode = mode,
        basePolicy = ProgressionBase.REVIEW, anchorSetIndex = null,
        needsReview = true, rule = ProgressionRule()
    )
    private fun entry(id: Long, date: String) = WorkoutEntry(
        id = id, date = date, exerciseStableKey = "barbell_back_squat",
        exerciseName = "Squat", category = "STRENGTH", createdAt = id,
        sessionStableKey = "session-" + id
    )
    @Test fun nextAndPreviousShowActualMovedDatesInsteadOfEstimatedWeek() {
        val links = listOf(link(11,1), link(22,5), link(33,8))
        val dates = listOf(entry(11,"2026-11-13"),entry(22,"2026-11-25"),entry(33,"2026-12-04"))
        val result = ProgramLinkedTimeline.build(links,dates)
        assertEquals(1, result.getValue(11).position)
        assertEquals(3, result.getValue(11).total)
        assertEquals("2026-11-25",result.getValue(11).next?.date)
        assertEquals("2026-11-13",result.getValue(22).previous?.date)
        assertEquals("2026-12-04",result.getValue(22).next?.date)
        assertEquals("11월 25일",programLinkedDateLabel("2026-11-25"))
        val shifted = dates.map { if (it.id == 22L) it.copy(date="2026-12-01") else it }
        assertEquals("2026-12-01",ProgramLinkedTimeline.build(links,shifted).getValue(11).next?.date)
    }
    @Test fun separatedSessionsAndOtherApplicationsNeverGetRelatedDates() {
        val result = ProgramLinkedTimeline.build(
            listOf(link(1,1),link(2,2,track="separate"),link(3,3,application="other")),
            listOf(entry(1,"2026-11-13"),entry(2,"2026-11-14"),entry(3,"2026-11-15"))
        )
        assertEquals(1,result.getValue(1).total)
        assertNull(result.getValue(1).next)
        assertNull(result.getValue(2).previous)
    }
    @Test fun disabledMissingAndAmbiguousLinksDoNotInventConnections() {
        val l=listOf(link(1,1),link(2,2,mode=ProgressionMode.OFF),link(3,1))
        val entries=listOf(entry(1,"2026-11-13"),entry(2,"2026-11-20"),entry(3,"2026-11-27"))
        val out=ProgramLinkedTimeline.build(l,entries)
        assertFalse(2L in out)
        assertEquals(0,out.getValue(1).position)
        assertNull(out.getValue(1).next)
        assertEquals(2,out.getValue(3).total)
    }
}
