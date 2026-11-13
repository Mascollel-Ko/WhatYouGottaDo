package com.training.trackplanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionSessionLabelsTest {
    @Test fun fourWeekSquatShowsEveryMemberInActualProgramOrder() {
        val positions = listOf(
            Triple(4, 1, 1), Triple(2, 1, 1),
            Triple(1, 1, 1), Triple(3, 1, 1)
        )
        assertEquals(
            "1주차 월요일 → 2주차 월요일 → 3주차 월요일 → 4주차 월요일",
            ProgressionSessionLabels.members(positions)
        )
        assertEquals("스쿼트 · 1주차 월요일 시작 · 4개",
            ProgressionSessionLabels.title("스쿼트",positions))
    }

    @Test fun sameWeekDifferentDayAndExerciseOrderAreUnambiguous() {
        val positions = listOf(
            Triple(2, 5, 2), Triple(2, 5, 1), Triple(1, 3, 1)
        )
        assertEquals("1주차 수요일 → 2주차 금요일 → 2주차 금요일 · 2번째 운동",
            ProgressionSessionLabels.members(positions))
    }

    @Test fun singleMemberIsVisiblyOneSessionAndNeverInventsCalendarDate() {
        val label=ProgressionSessionLabels.title("데드리프트",listOf(Triple(3,1,1)))
        assertEquals("데드리프트 · 3주차 월요일 시작 · 1개",label)
        assertTrue(!label.contains("11월"))
    }
}
