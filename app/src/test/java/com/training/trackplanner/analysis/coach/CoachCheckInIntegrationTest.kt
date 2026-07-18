package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.data.DailyCheckIn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachCheckInIntegrationTest {
    private val today = LocalDate.of(2026, 6, 23)

    @Test
    fun highSubjectiveSignalsAddConservativeCoachGuidance() {
        val guidance = CoachCheckInInterpreter().guidance(
            DailyCheckIn(
                date = today.toString(),
                sleepHours = 5.5,
                overallFatigue = 5,
                lowerBodyFatigue = 4,
                jointTendonDiscomfort = 4,
                focusMotivation = 2
            ),
            normalFatigueState()
        )

        assertTrue(guidance.any { it.contains("객관적 운동 부하는 평소 수준") })
        assertTrue(guidance.any { it.contains("점프·방향전환") })
        assertTrue(guidance.any { it.contains("관절/건 불편감") })
        assertTrue(guidance.any { it.contains("수면시간") })
    }

    @Test
    fun missingCheckInLeavesExistingCoachBehaviorUntouched() {
        assertTrue(CoachCheckInInterpreter().guidance(null, normalFatigueState()).isEmpty())
        val summary = CoachFatigueCauseAnalyzer().analyze(today, history = emptyList(), checkIns = emptyList())
        assertFalse(summary.isDataSufficient)
    }

    @Test
    fun lowMotivationIsBadWhileHighMotivationIsNotAWarning() {
        val low = CoachCheckInInterpreter().guidance(
            DailyCheckIn(date = today.toString(), focusMotivation = 1),
            normalFatigueState()
        )
        val high = CoachCheckInInterpreter().guidance(
            DailyCheckIn(date = today.toString(), focusMotivation = 5),
            normalFatigueState()
        )

        assertTrue(low.any { it.contains("집중력/의욕") })
        assertTrue(high.isEmpty())
    }

    @Test
    fun recoveryInputCanAppearInTopCausesWithoutWorkoutHistory() {
        val summary = CoachFatigueCauseAnalyzer().analyze(
            today = today,
            history = emptyList(),
            checkIns = listOf(DailyCheckIn(date = today.toString(), lowerBodyFatigue = 5))
        )

        assertTrue(summary.isDataSufficient)
        assertEquals(CoachFatigueCauseType.RECOVERY_INPUT, summary.causes.first().sourceType)
    }

    private fun normalFatigueState() = DailyFatigueState(
        date = today,
        highForceNeuralFatigue = 0.0,
        systemicMuscularFatigue = 0.0,
        localMuscularFatigue = 0.0,
        highSpeedFatigue = 0.0,
        reactiveFatigue = 0.0,
        recoveryPressure = 0.0,
        highForceNeuralScore = 50,
        systemicMuscularScore = 50,
        localMuscularScore = 50,
        highSpeedScore = 50,
        reactiveScore = 50,
        recoveryPressureScore = 50,
        overallFatigueIndex = 50,
        readinessLabel = FatigueReadinessLabel.NORMAL,
        cautionReasons = emptyList(),
        confidence = FatigueConfidence.MEDIUM
    )
}
