package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionBase
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BCoachProgramLinkChainTest {
    private fun link(
        id: Long, seq: Int, week: Int, day: Int,
        track: String = "plan-explicit-session",
        application: String = "applied-program-1",
        mode: ProgressionMode = ProgressionMode.APP
    ) = ProgramWorkoutLink(
        entryId = id, applicationId = application,
        sourceProgramStableKey = "program-1", sourceItemId = "item-$id",
        programName = "Demo", weekNumber = week, dayOfWeek = day,
        trackId = track, trackLabel = "Squat session",
        sequence = seq, role = ProgressionRole.MAIN,
        mode = mode, basePolicy = ProgressionBase.REVIEW,
        anchorSetIndex = null, needsReview = true, rule = ProgressionRule()
    )

    @Test fun `program track and sequence choose successor across weekday and month`() {
        val first = link(1, 1, 1, 1)
        val unrelated = link(3, 2, 1, 2, track = "separate")
        val later = link(2, 17, 4, 5)
        val result = BCoachProgramLinkChain.successor(first, listOf(first, unrelated, later))
        assertTrue(result.found)
        assertEquals(2L, result.link?.entryId)
        assertEquals(1L, BCoachProgramLinkChain.predecessor(later,
            listOf(first, unrelated, later)).link?.entryId)
    }

    @Test fun `closest connected workout wins without skipping middle members`() {
        val a = link(1, 1, 1, 1)
        val b = link(2, 4, 2, 1)
        val c = link(3, 19, 4, 7)
        val links=listOf(a,b,c)
        assertEquals(2L,BCoachProgramLinkChain.successor(a,links).link?.entryId)
        assertEquals(2L,BCoachProgramLinkChain.predecessor(c,links).link?.entryId)
    }

    @Test fun `manually separate link does not join same day same stableKey`() {
        val a=link(1, 1, 1, 1, track="track-A")
        val b=link(2, 2, 1, 1, track="track-B")
        val result=BCoachProgramLinkChain.successor(a,listOf(a,b))
        assertFalse(result.found)
        assertEquals("NO_NEXT_LINKED_EXERCISE",result.status)
    }

    @Test fun `separate applied program cannot leak load updates`() {
        val a=link(1, 1, 1, 1)
        val b=link(2, 2, 2, 1, application="different-application")
        assertEquals("NO_NEXT_LINKED_EXERCISE",
            BCoachProgramLinkChain.successor(a,listOf(a,b)).status)
    }

    @Test fun `off and direct sessions remain protected even when trackId matches`() {
        val a=link(1,1,1,1)
        val b=link(2,2,2,1,mode=ProgressionMode.OFF)
        assertEquals("LINK_MODE_NOT_ELIGIBLE",
            BCoachProgramLinkChain.successor(a,listOf(a,b)).status)
        assertEquals("LINK_MODE_NOT_ELIGIBLE",
            BCoachProgramLinkChain.predecessor(b,listOf(a,b)).status)
        assertEquals("LINK_MODE_NOT_ELIGIBLE",
            BCoachProgramLinkChain.successor(a.copy(mode=ProgressionMode.DIRECT),listOf(a,b)).status)
    }

    @Test fun `ambiguous sequence is not guessed`() {
        val a=link(1,1,1,1)
        val b=link(2,2,2,1)
        val c=link(3,2,2,4)
        val result=BCoachProgramLinkChain.successor(a,listOf(a,b,c))
        assertEquals("AMBIGUOUS_LINKED_SEQUENCE",result.status)
        assertFalse(result.found)
    }

    @Test fun `no preceding linked item is explicitly reported`() {
        val a=link(1,1,1,1)
        assertEquals("NO_LINKED_PREDECESSOR",
            BCoachProgramLinkChain.predecessor(a,listOf(a)).status)
    }
}
