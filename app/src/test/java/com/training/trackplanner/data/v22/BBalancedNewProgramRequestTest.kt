package com.training.trackplanner.data.v22

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * New-program UI uses B exclusively, while A/C are deliberately preserved for
 * previously generated programs and the B legacy baseline comparator.
 */
class BBalancedNewProgramRequestTest {
    private val date = LocalDate.of(2026, 10, 23)

    @Test
    fun newProgramAlwaysUsesTwelveWeekBWithUserPreferences() {
        val importance = BBalancedGoalImportance(
            strength = 5,
            hypertrophy = 3,
            core = 2,
            sportSupport = 4
        )
        val request = newBBalancedGenerationRequest(
            name = "balanced",
            days = 3,
            asOf = date,
            sessionMinutes = 60,
            allowFourthDay = true,
            importance = importance
        )
        assertEquals(V22Mode.B, request.mode)
        assertEquals(12, request.weeks)
        assertEquals(3, request.days)
        assertEquals(4, request.bMaxTrainingDays)
        assertEquals(60, request.sessionMinutes)
        assertEquals(date, request.asOf)
        assertEquals(importance, request.bGoalImportance)
        assertNull(request.focusStableKey)
    }

    @Test
    fun fixedDayRequestAndFourDayRequestDoNotExpandWithoutPermission() {
        val importance = BBalancedGoalImportance()
        val fixed = newBBalancedGenerationRequest(
            "fixed", 3, date, 45, false, importance
        )
        val four = newBBalancedGenerationRequest(
            "four", 4, date, 45, true, importance
        )
        assertEquals(V22Mode.B, fixed.mode)
        assertEquals(V22Mode.B, four.mode)
        assertEquals(3, fixed.days)
        assertEquals(4, four.days)
        assertNull(fixed.bMaxTrainingDays)
        assertNull(four.bMaxTrainingDays)
    }

    @Test
    fun legacyModesRemainAvailableForExistingPlansAndParityEngine() {
        assertTrue(V22Mode.entries.contains(V22Mode.A))
        assertTrue(V22Mode.entries.contains(V22Mode.C))
    }
}
