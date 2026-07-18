package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.badminton.BadmintonTransferAxis
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
    private val removedAxisName = "LOWER_BODY" + "_STRENGTH"
    private val removedAxisLabel = "\uD558\uCCB4 \uAE30\uCD08\uADFC\uB825"

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
    }

    @Test
    fun coverageSummaryDoesNotExposeRemovedLowerBodyAxis() {
        val values = BadmintonTransferAxis.entries.associateWith { 10.0 }
        val recent = snapshot(values, sampleCount = 14)
        val summary = analyzer.analyze(recent, recent.copy(windowDays = 28), state())

        assertFalse(summary.statuses.any { status -> status.axis.name == removedAxisName })
        assertFalse(summary.statuses.any { status -> status.label.contains(removedAxisLabel) })
        assertFalse(summary.lowAxes.any { status -> status.label.contains(removedAxisLabel) })
        assertFalse(summary.cautionAxes.any { status -> status.label.contains(removedAxisLabel) })
    }

    @Test
    fun rawLowerBodyStrengthRecordsDoNotCreateCoverageAxis() {
        val today = LocalDate.of(2026, 6, 22)
        val exercise = Exercise(
            id = 99,
            name = "raw squat",
            category = "strength",
            stableKey = "raw_squat",
            movementPattern = "KNEE_DOMINANT|$removedAxisName|SQUAT_PATTERN",
            forceType = "LOWER_BODY",
            badmintonTransferStrength = "GENERAL",
            analysisEligibility = "BADMINTON_TRANSFER"
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

        assertFalse(summary.isDataSufficient)
        assertFalse(summary.statuses.any { status -> status.axis.name == removedAxisName })
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
            causes = listOf(CoachFatigueCause(1, "squat", "record", 10.0, listOf("local muscle"), CoachFatigueCauseType.EXERCISE)),
            headline = "pattern only",
            isDataSufficient = true
        )
        val transfer = BadmintonTransferCoverageSummary.insufficient()
        val combined = CoachAnalysisInsightBuilder.combine(fatigue, transfer)

        assertFalse(combined.combinedHeadline.orEmpty().contains("caused by"))
        assertFalse(combined.combinedHeadline.orEmpty().contains("because"))
    }

    private fun snapshot(
        values: Map<BadmintonTransferAxis, Double>,
        sampleCount: Int,
        entryCounts: Map<BadmintonTransferAxis, Int> = emptyMap(),
        windowDays: Int = 14
    ): BadmintonTransferWindowSnapshot {
        val completeValues = BadmintonTransferAxis.entries.associateWith { axis -> values[axis] ?: 0.0 }
        val completeCounts = BadmintonTransferAxis.entries.associateWith { axis ->
            entryCounts[axis] ?: if ((values[axis] ?: 0.0) > 0.0) 1 else 0
        }
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
        highForceNeuralFatigue = 0.0,
        systemicMuscularFatigue = 0.0,
        localMuscularFatigue = 0.0,
        highSpeedFatigue = 0.0,
        reactiveFatigue = 0.0,
        recoveryPressure = 0.0,
        highForceNeuralScore = 50,
        systemicMuscularScore = 50,
        localMuscularScore = 60,
        highSpeedScore = joint,
        reactiveScore = 60,
        recoveryPressureScore = 50,
        overallFatigueIndex = 60,
        readinessLabel = FatigueReadinessLabel.NORMAL,
        cautionReasons = emptyList(),
        confidence = FatigueConfidence.MEDIUM
    )
}
