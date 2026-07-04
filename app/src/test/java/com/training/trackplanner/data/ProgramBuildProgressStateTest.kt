package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramBuildProgressStateTest {
    @Test
    fun runningCompletedAndFailedStatesCarryUiData() {
        val running: ProgramBuildProgressState = ProgramBuildProgressState.Running(
            progressPercent = 40,
            message = "1차 프로그램을 구성하는 중입니다."
        )
        val completed: ProgramBuildProgressState = ProgramBuildProgressState.Completed(
            skeleton = emptyProgramSkeleton(request(), emptyMap()),
            summary = ProgramOptimizationSummary(messages = listOf("45분 세션에 맞게 운동 수를 조정했습니다."))
        )
        val failed: ProgramBuildProgressState = ProgramBuildProgressState.Failed("자동 골자 생성에 실패했습니다.")

        assertEquals(40, (running as ProgramBuildProgressState.Running).progressPercent)
        assertTrue((completed as ProgramBuildProgressState.Completed).summary.messages.isNotEmpty())
        assertTrue((failed as ProgramBuildProgressState.Failed).message.contains("실패"))
    }

    private fun request(): ProgramSkeletonRequest = ProgramSkeletonRequest(
        name = "progress",
        goal = ProgramGoal.BADMINTON_SUPPORT,
        weeklyTrainingDays = 3,
        sessionMinutes = 45,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = 0.60,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = 4
    )
}
