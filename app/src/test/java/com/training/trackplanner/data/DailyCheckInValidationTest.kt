package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyCheckInValidationTest {
    @Test
    fun nullableValuesRemainNull() {
        val checkIn = DailyCheckIn(date = "2026-06-23").validated()

        assertNull(checkIn.sleepHours)
        assertNull(checkIn.overallFatigue)
        assertNull(checkIn.focusMotivation)
    }

    @Test
    fun valuesWithinOneToFiveAreAccepted() {
        val checkIn = DailyCheckIn(
            date = "2026-06-23",
            overallFatigue = 1,
            lowerBodyFatigue = 5,
            jointTendonDiscomfort = 3,
            focusMotivation = 5
        ).validated()

        assertEquals(5, checkIn.focusMotivation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scoreOutsideOneToFiveIsRejected() {
        DailyCheckIn(date = "2026-06-23", overallFatigue = 0).validated()
    }
}
