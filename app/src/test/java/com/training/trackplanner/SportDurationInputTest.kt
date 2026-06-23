package com.training.trackplanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportDurationInputTest {
    @Test
    fun storedSecondsDisplayAsHoursAndMinutes() {
        assertEquals(SportDurationParts(hours = 1, minutes = 35), totalDurationToHoursMinutes(5_700))
        assertEquals(SportDurationParts(hours = 0, minutes = 45), totalDurationToHoursMinutes(2_700))
        assertEquals(SportDurationParts(hours = 2, minutes = 0), totalDurationToHoursMinutes(7_200))
    }

    @Test
    fun hoursAndMinutesSaveAsStoredSeconds() {
        assertEquals(5_700, hoursMinutesToStoredDuration(hours = 1, minutes = 35))
        assertEquals(2_700, hoursMinutesToStoredDuration(hours = 0, minutes = 45))
        assertEquals(7_200, hoursMinutesToStoredDuration(hours = 2, minutes = 0))
    }

    @Test
    fun minutesOverSixtyNormalizeIntoHours() {
        assertEquals(SportDurationParts(hours = 1, minutes = 30), normalizeHoursMinutes(hours = 0, minutes = 90))
        assertEquals(SportDurationParts(hours = 2, minutes = 15), normalizeHoursMinutes(hours = 1, minutes = 75))
    }

    @Test
    fun zeroDurationIsNotPositive() {
        assertFalse(normalizeHoursMinutes(hours = 0, minutes = 0).isPositive)
        assertTrue(normalizeHoursMinutes(hours = 0, minutes = 1).isPositive)
    }
}
