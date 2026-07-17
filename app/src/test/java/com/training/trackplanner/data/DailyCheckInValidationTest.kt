package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DailyCheckInValidationTest {
    @Test
    fun nullableValuesRemainNull() {
        val checkIn = DailyCheckIn(date = "2026-06-23").validated()

        assertNull(checkIn.sleepHours)
        assertNull(checkIn.bodyWeightKg)
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

    @Test
    fun decimalBodyWeightIsAccepted() {
        val checkIn = DailyCheckIn(date = "2026-06-23", bodyWeightKg = 80.5).validated()

        assertEquals(80.5, checkIn.bodyWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun invalidBodyWeightsAreRejected() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { bodyWeightKg ->
                assertThrows(IllegalArgumentException::class.java) {
                    DailyCheckIn(date = "2026-06-23", bodyWeightKg = bodyWeightKg).validated()
                }
            }
    }
}
