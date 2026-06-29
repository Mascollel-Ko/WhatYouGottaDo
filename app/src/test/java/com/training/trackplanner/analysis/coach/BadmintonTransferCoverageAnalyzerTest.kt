package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.badminton.BadmintonTransferAxis
import com.training.trackplanner.analysis.badminton.BadmintonTransferConstants
import com.training.trackplanner.analysis.badminton.BadmintonTransferWindowSnapshot
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BadmintonTransferCoverageAnalyzerTest {
    private val analyzer = BadmintonTransferCoverageAnalyzer()

    @Test
    fun absentAxisIsClassifiedAsMissing() {
        val recent = snapshot(mapOf(BadmintonTransferAxis.LATERAL_MOVEMENT to 100.0), sampleCount = 6)
        val summary = analyzer.analyze(recent, recent.copy(windowDays = 28), state())

        val rotation = summary.statuses.single { it.axis == BadmintonTransferAxis.ROTATION_CONTROL }
        assertEquals(TransferAxisStatusType.MISSING, rotation.status)
        assertTrue(rotation in summary.lowAxes)
    }

    @Test
    fun highRepeatedShareAndRelatedFatigueBecomesOverloaded() {
        val recent = snapshot(
            values = mapOf(
                BadmintonTransferAxis.DECELERATION_LANDING to 70.0,
                BadmintonTransferAxis.ROTATION_CONTROL to 30.0
            ),
            sampleCount = 6,
            entryCounts = mapOf(BadmintonTransferAxis.DECELERATION_LANDING to 4)
        )
        val baseline = snapshot(
            values = mapOf(
                BadmintonTransferAxis.DECELERATION_LANDING to 20.0,
                BadmintonTransferAxis.ROTATION_CONTROL to 80.0
            ),
            sampleCount = 12,
            windowDays = 28
        )

        val summary = analyzer.analyze(recent, baseline, state(joint = 88))

        assertEquals(
            TransferAxisStatusType.OVERLOADED,
            summary.statuses.single { it.axis == BadmintonTransferAxis.DECELERATION_LANDING }.status
        )
    }

    @Test
    fun evenlyDistributedAxesAreBalanced() {
        val values = BadmintonTransferAxis.entries.associateWith { 12.5 }
        val counts = BadmintonTransferAxis.entries.associateWith { 2 }
        val recent = snapshot(values, sampleCount = 16, entryCounts = counts)
        val baseline = snapshot(values, sampleCount = 24, entryCounts = counts, windowDays = 28)

        val summary = analyzer.analyze(recent, baseline, state())

        assertTrue(summary.statuses.all { it.status == TransferAxisStatusType.BALANCED })
        assertTrue(summary.lowAxes.isEmpty())
        assertTrue(summary.cautionAxes.isEmpty())
        assertTrue(summary.headline.contains("균형적"))
    }

    @Test
    fun lowerBodyAbsoluteStimulusSuppressesLowShareWarning() {
        val recent = snapshot(
            values = mapOf(
                BadmintonTransferAxis.LOWER_BODY_STRENGTH to
                    BadmintonTransferConstants.LOWER_BODY_FOUNDATION_ABSOLUTE_STIMULUS_THRESHOLD,
                BadmintonTransferAxis.LATERAL_MOVEMENT to 260.0,
                BadmintonTransferAxis.DECELERATION_LANDING to 120.0
            ),
            sampleCount = 8,
            entryCounts = mapOf(BadmintonTransferAxis.LOWER_BODY_STRENGTH to 2)
        )
        val summary = analyzer.analyze(recent, recent.copy(windowDays = 28), state())

        val lowerBody = summary.statuses.single { it.axis == BadmintonTransferAxis.LOWER_BODY_STRENGTH }
        assertEquals(TransferAxisStatusType.BALANCED, lowerBody.status)
        assertFalse(lowerBody in summary.lowAxes)
    }

    @Test
    fun lowerBodyLowShareStillWarnsWhenAbsoluteStimulusIsLow() {
        val recent = snapshot(
            values = mapOf(
                BadmintonTransferAxis.LOWER_BODY_STRENGTH to
                    BadmintonTransferConstants.LOWER_BODY_FOUNDATION_ABSOLUTE_STIMULUS_THRESHOLD - 1.0,
                BadmintonTransferAxis.LATERAL_MOVEMENT to 260.0,
                BadmintonTransferAxis.DECELERATION_LANDING to 120.0
            ),
            sampleCount = 8,
            entryCounts = mapOf(BadmintonTransferAxis.LOWER_BODY_STRENGTH to 1)
        )
        val summary = analyzer.analyze(recent, recent.copy(windowDays = 28), state())

        val lowerBody = summary.statuses.single { it.axis == BadmintonTransferAxis.LOWER_BODY_STRENGTH }
        assertEquals(TransferAxisStatusType.LOW, lowerBody.status)
    }

    @Test
    fun rawLowerFoundationRecordsAreNotReportedAsMissing() {
        val today = LocalDate.of(2026, 6, 22)
        val exercise = Exercise(
            id = 99,
            name = "raw squat",
            category = "strength",
            stableKey = "raw_squat",
            movementPattern = "KNEE_DOMINANT|LOWER_BODY_STRENGTH|SQUAT_PATTERN",
            forceType = "LOWER_BODY"
        )
        val entry = WorkoutEntry(
            id = 990,
            date = today.minusDays(1).toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category
        )
        val record = WorkoutEntryWithSets(
            entry = entry,
            sets = listOf(
                WorkoutSet(entryId = entry.id, setIndex = 1, reps = 5, weightKg = 80.0, confirmed = true)
            )
        )

        val summary = analyzer.analyze(today, listOf(exercise), listOf(record), state())
        val lowerBody = summary.statuses.single { it.axis == BadmintonTransferAxis.LOWER_BODY_STRENGTH }

        assertTrue(summary.isDataSufficient)
        assertFalse(lowerBody.status == TransferAxisStatusType.MISSING)
    }

    @Test
    fun noTransferRecordsReturnsSafeInsufficientSummary() {
        val empty = snapshot(emptyMap(), sampleCount = 0)
        val summary = analyzer.analyze(empty, empty.copy(windowDays = 28), null)

        assertFalse(summary.isDataSufficient)
        assertTrue(summary.statuses.isEmpty())
    }

    @Test
    fun combinedCoachWordingDoesNotClaimCausality() {
        val fatigue = CoachFatigueCauseSummary(
            windowDays = 14,
            causes = listOf(CoachFatigueCause(1, "스쿼트", "누적 기록", 10.0, listOf("국소 근육"), CoachFatigueCauseType.EXERCISE)),
            headline = "가능성이 큽니다.",
            isDataSufficient = true
        )
        val transfer = BadmintonTransferCoverageSummary.insufficient()
        val combined = CoachAnalysisInsightBuilder.combine(fatigue, transfer)

        assertFalse(combined.combinedHeadline.orEmpty().contains("원인입니다"))
        assertFalse(combined.combinedHeadline.orEmpty().contains("때문입니다"))
    }

    private fun snapshot(
        values: Map<BadmintonTransferAxis, Double>,
        sampleCount: Int,
        entryCounts: Map<BadmintonTransferAxis, Int> = emptyMap(),
        windowDays: Int = 14
    ): BadmintonTransferWindowSnapshot {
        val completeValues = BadmintonTransferAxis.entries.associateWith { values[it] ?: 0.0 }
        val completeCounts = BadmintonTransferAxis.entries.associateWith { entryCounts[it] ?: if ((values[it] ?: 0.0) > 0.0) 1 else 0 }
        return BadmintonTransferWindowSnapshot(
            windowDays = windowDays,
            totalStimulus = completeValues.values.sum(),
            axisStimulus = completeValues,
            axisEntryCounts = completeCounts,
            sampleEntryCount = sampleCount
        )
    }

    private fun state(joint: Int = 50) = DailyFatigueState(
        date = LocalDate.of(2026, 6, 22),
        neuromuscularFatigue = 0.0,
        systemicMuscularFatigue = 0.0,
        localMuscularFatigue = 0.0,
        jointTendonImpactFatigue = 0.0,
        movementFocusFatigue = 0.0,
        recoveryPressure = 0.0,
        neuromuscularScore = 50,
        systemicMuscularScore = 50,
        localMuscularScore = 60,
        jointTendonImpactScore = joint,
        movementFocusScore = 60,
        recoveryPressureScore = 50,
        overallFatigueIndex = 60,
        readinessLabel = FatigueReadinessLabel.NORMAL,
        cautionReasons = emptyList(),
        confidence = FatigueConfidence.MEDIUM
    )
}
