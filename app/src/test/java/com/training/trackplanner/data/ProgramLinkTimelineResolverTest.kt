package com.training.trackplanner.data

import org.junit.Assert.*
import org.junit.Test

class ProgramLinkTimelineResolverTest {
    private fun link(id: Long, seq: Int,
        track: String="track-A", app: String="application-A") = ProgramWorkoutLink(
        entryId=id, applicationId=app, sourceProgramStableKey="program-A",
        sourceItemId="item-$id", programName="Demo", weekNumber=seq,
        dayOfWeek=1,trackId=track,trackLabel="Squat",sequence=seq,
        role=ProgressionRole.MAIN,mode=ProgressionMode.APP,
        basePolicy=ProgressionBase.REVIEW,anchorSetIndex=null,
        needsReview=true,rule=ProgressionRule()
    )
    private fun record(id: Long, date: String) = WorkoutEntry(
        id=id, date=date, exerciseStableKey="barbell_back_squat",
        exerciseName="Squat",category="STRENGTH",createdAt=id
    )

    @Test fun `actual moved dates are shown rather than calculated program weekdays`() {
        val links=listOf(link(1,1),link(2,2),link(3,3))
        val entries=listOf(record(1,"2026-11-13"),record(2,"2026-11-20"),
            record(3,"2026-12-07"))
        val chain=checkNotNull(ProgramLinkTimelineResolver.resolve(2,links,entries))
        assertEquals(2,chain.ordinal)
        assertEquals(3,chain.total)
        assertEquals("2026-11-13",chain.previousDate)
        assertEquals("2026-12-07",chain.nextDate)
        assertEquals("12월 7일",ProgramLinkTimelineResolver.koreanDate(chain.nextDate!!))
    }

    @Test fun `manual separate track never appears as a neighbor`() {
        val a=link(1,1)
        val other=link(2,2,track="separated")
        val chain=checkNotNull(ProgramLinkTimelineResolver.resolve(1,
            listOf(a,other),listOf(record(1,"2026-11-13"),record(2,"2026-11-20"))))
        assertEquals(1,chain.total)
        assertNull(chain.nextDate)
        assertNull(chain.nextEntryId)
    }

    @Test fun `identical track but another application stays isolated`() {
        val chain=checkNotNull(ProgramLinkTimelineResolver.resolve(1,
            listOf(link(1,1),link(2,2,app="different")),
            listOf(record(1,"2026-11-13"),record(2,"2026-11-20"))))
        assertEquals(1,chain.total)
    }

    @Test fun `dangling linked workout keeps identity but does not invent a date`() {
        val chain=checkNotNull(ProgramLinkTimelineResolver.resolve(1,
            listOf(link(1,1),link(2,2)),listOf(record(1,"2026-11-13"))))
        assertEquals(2L,chain.nextEntryId)
        assertNull(chain.nextDate)
    }

    @Test fun `duplicate sequence is ambiguous and not displayed as reliable`() {
        assertNull(ProgramLinkTimelineResolver.resolve(1,
            listOf(link(1,1),link(2,2),link(3,2)),
            listOf(record(1,"2026-11-13"),record(2,"2026-11-20"),record(3,"2026-11-21"))))
    }

    @Test fun `untracked and unlinked entries have no timeline`() {
        assertNull(ProgramLinkTimelineResolver.resolve(1,emptyList(),
            listOf(record(1,"2026-11-13"))))
        assertNull(ProgramLinkTimelineResolver.resolve(1,
            listOf(link(1,1,track="untracked")),listOf(record(1,"2026-11-13"))))
    }
}
