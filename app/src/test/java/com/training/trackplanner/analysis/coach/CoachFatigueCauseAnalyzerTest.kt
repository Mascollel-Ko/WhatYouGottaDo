package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueAxisValues
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.analysis.fatigue.RecordFatigueContribution
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachFatigueCauseAnalyzerTest {
    private val today = LocalDate.of(2026, 6, 22)
    private val analyzer = CoachFatigueCauseAnalyzer()

    @Test
    fun topFiveAreSortedByDecayedContributionScore() {
        val history = (1..6).map { index ->
            result(
                date = today,
                contribution = contribution("운동 $index", index.toDouble() * 10.0, today)
            )
        }

        val summary = analyzer.analyze(today, history)

        assertEquals(5, summary.causes.size)
        assertEquals(listOf("운동 6", "운동 5", "운동 4", "운동 3", "운동 2"), summary.causes.map { it.label })
        assertTrue(summary.causes.zipWithNext().all { (first, second) -> first.contributionScore >= second.contributionScore })
    }

    @Test
    fun onlyRecentFourteenDaysAreIncluded() {
        val recent = result(today.minusDays(7), contribution("최근 운동", 20.0, today.minusDays(7), "VERY_LONG"))
        val old = result(today.minusDays(14), contribution("오래된 운동", 100.0, today.minusDays(14), "VERY_LONG"))

        val summary = analyzer.analyze(today, listOf(old, recent))

        assertTrue(summary.causes.any { it.label == "최근 운동" })
        assertFalse(summary.causes.any { it.label == "오래된 운동" })
    }

    @Test
    fun emptyConfirmedContributionHistoryReturnsSafeInsufficientSummary() {
        val summary = analyzer.analyze(today, listOf(result(today, null)))

        assertFalse(summary.isDataSufficient)
        assertTrue(summary.causes.isEmpty())
        assertTrue(summary.headline.contains("기록이 부족"))
    }

    @Test
    fun missingStableKeyDoesNotCrashAndUsesExerciseLabel() {
        val summary = analyzer.analyze(
            today,
            listOf(result(today, contribution("사용자 운동", 30.0, today, stableKey = "")))
        )

        assertEquals("사용자 운동", summary.causes.single().label)
    }

    @Test
    fun interpretationUsesPossibilityLanguageInsteadOfCausalClaim() {
        val summary = analyzer.analyze(today, listOf(result(today, contribution("스쿼트", 30.0, today))))

        assertTrue(summary.headline.contains("가능성이 큽니다"))
        assertFalse(summary.headline.contains("원인입니다"))
        assertFalse(summary.headline.contains("때문입니다"))
    }

    private fun result(date: LocalDate, contribution: RecordFatigueContribution?): DailyFatigueResult =
        DailyFatigueResult(
            state = state(date),
            groupStates = emptyList(),
            recordContributions = listOfNotNull(contribution)
        )

    private fun contribution(
        name: String,
        value: Double,
        date: LocalDate,
        duration: String = "MEDIUM",
        stableKey: String = name
    ) = RecordFatigueContribution(
        date = date,
        stableKey = stableKey,
        exerciseName = name,
        trainingLoad = value,
        axes = FatigueAxisValues(
            neuromuscular = value,
            systemicMuscular = value * 0.5,
            localMuscular = value * 0.8,
            jointTendonImpact = value * 0.3,
            movementFocus = value * 0.2,
            recoveryPressure = value * 0.6
        ),
        recoveryDurationClass = duration,
        jointRecoveryDurationClass = duration,
        strengthProgressionGroup = "TEST",
        redundancyGroup = "TEST",
        movementFamily = "TEST",
        programSlot = "TEST",
        confidence = FatigueConfidence.MEDIUM
    )

    private fun state(date: LocalDate) = DailyFatigueState(
        date = date,
        neuromuscularFatigue = 0.0,
        systemicMuscularFatigue = 0.0,
        localMuscularFatigue = 0.0,
        jointTendonImpactFatigue = 0.0,
        movementFocusFatigue = 0.0,
        recoveryPressure = 0.0,
        neuromuscularScore = 50,
        systemicMuscularScore = 50,
        localMuscularScore = 50,
        jointTendonImpactScore = 50,
        movementFocusScore = 50,
        recoveryPressureScore = 50,
        overallFatigueIndex = 50,
        readinessLabel = FatigueReadinessLabel.NORMAL,
        cautionReasons = emptyList(),
        confidence = FatigueConfidence.MEDIUM
    )
}
