package com.training.trackplanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramProgressionLinkLabelsTest {
    @Test fun `four connected squats appear in chronological program order`() {
        val path = progressionOccurrencePath(
            listOf(4 to 1, 2 to 1, 1 to 1, 3 to 1)
        )
        assertEquals(
            "1주차 월 → 2주차 월 → 3주차 월 → 4주차 월", path
        )
    }

    @Test fun `different weekdays are distinguishable within the same week`() {
        assertEquals(
            "1주차 월 → 1주차 목 → 3주차 금",
            progressionOccurrencePath(listOf(3 to 5, 1 to 4, 1 to 1))
        )
    }

    @Test fun `all seven program weekdays have human readable labels`() {
        assertEquals(
            listOf("1주차 월", "1주차 화", "1주차 수", "1주차 목", "1주차 금", "1주차 토", "1주차 일"),
            (1..7).map { progressionOccurrenceLabel(1,it) }
        )
    }

    @Test fun `no made up exact calendar date in un-applied template`() {
        assertEquals("4주차 수", progressionOccurrenceLabel(4,3))
    }
}
