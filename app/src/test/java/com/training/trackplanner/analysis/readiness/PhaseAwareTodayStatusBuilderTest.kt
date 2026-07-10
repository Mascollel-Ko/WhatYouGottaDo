package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhaseAwareTodayStatusBuilderTest {
    private val today = LocalDate.parse("2026-06-23")
    private val exercise = heavyExercise()

    @Test
    fun unconfirmedSetsUseRemainingPlanPhaseAndProjectedReadiness() {
        val status = PhaseAwareTodayStatusBuilder().build(
            input(
                entries = listOf(
                    record(
                        confirmedSets = emptyList(),
                        plannedSets = listOf(set(reps = 8, weightKg = 260.0, confirmed = false, rpe = 9.0))
                    )
                )
            )
        )

        assertEquals(TodayStatusPhase.REMAINING_PLAN, status.phase)
        assertEquals(ReadinessStatus.READY, status.current.status)
        assertNotNull(status.projected)
        assertTrue(status.projected!!.status.ordinal >= status.current.status.ordinal)
        assertEquals("끝나면 예상 피로도", status.phaseLabel)
        assertTrue(status.headline.contains("예상 피로도"))
    }

    @Test
    fun allConfirmedSetsUseCompletedPhaseAndRecoveryWording() {
        val status = PhaseAwareTodayStatusBuilder().build(
            input(
                entries = listOf(
                    record(
                        confirmedSets = listOf(set(reps = 8, weightKg = 160.0, confirmed = true, rpe = 8.0))
                    )
                )
            )
        )

        assertEquals(TodayStatusPhase.COMPLETED, status.phase)
        assertNull(status.projected)
        assertEquals("현재 회복 판단", status.phaseLabel)
        assertFalse(status.detail.contains("주의하세요"))
        assertFalse(status.detail.contains("남은 계획"))
        assertFalse(status.detail.contains("계획 완료"))
        assertFalse(status.detail.contains("완료 예상"))
    }

    @Test
    fun completedPhaseUsesCurrentRecoveryWordingOnly() {
        val status = PhaseAwareTodayStatusBuilder().build(
            input(
                entries = listOf(
                    record(
                        confirmedSets = listOf(set(reps = 8, weightKg = 260.0, confirmed = true, rpe = 9.0))
                    )
                )
            )
        )
        val text = listOf(status.headline, status.detail, status.actionLabel).joinToString(" ")

        assertEquals(TodayStatusPhase.COMPLETED, status.phase)
        assertNull(status.projected)
        assertFalse(text.contains("남은 계획"))
        assertFalse(text.contains("계획 완료 시"))
        assertFalse(text.contains("완료 예상"))
        assertTrue(text.contains("현재") || text.contains("오늘 기록된 운동"))
    }

    @Test
    fun remainingPlanWordingSeparatesProjectedLoadFromUnrecoveredFatigue() {
        val status = PhaseAwareTodayStatusBuilder().build(
            input(
                entries = listOf(
                    record(
                        confirmedSets = listOf(set(reps = 8, weightKg = 180.0, confirmed = true, rpe = 8.0)),
                        plannedSets = List(6) { set(reps = 8, weightKg = 300.0, confirmed = false, rpe = 9.0) }
                    )
                )
            )
        )
        val text = listOf(status.phaseLabel, status.headline, status.detail, status.actionLabel).joinToString(" ")

        assertEquals(TodayStatusPhase.REMAINING_PLAN, status.phase)
        assertTrue(text.contains("예상 피로도"))
        assertTrue(text.contains("다음 날"))
        listOf("회복이 안 됐습니다", "과훈련", "진단", "주의하세요").forEach { banned ->
            assertFalse(text.contains(banned))
        }
    }

    @Test
    fun jointTendonCheckInFeedsExistingPainGate() {
        val summary = TodayReadinessEngine().analyze(
            input(checkIns = listOf(DailyCheckIn(today.toString(), jointTendonDiscomfort = 5)))
        )

        assertEquals(ReadinessStatus.LIMITED, summary.status)
        assertTrue(summary.primaryReasons.joinToString().contains("불편감"))
        assertTrue(summary.restrictedModes.joinToString().contains("관절/건"))
    }

    @Test
    fun missingCheckInDoesNotBlockReadiness() {
        val summary = TodayReadinessEngine().analyze(input())

        assertEquals(ReadinessStatus.READY, summary.status)
    }

    @Test
    fun sleepUsesDailyMetricRatherThanDailyCheckInSleep() {
        val summary = TodayReadinessEngine().analyze(
            input(
                metrics = listOf(DailyMetric(today.toString(), sleepHours = 8.0)),
                checkIns = listOf(DailyCheckIn(today.toString(), sleepHours = 4.0))
            )
        )

        assertEquals(ReadinessStatus.READY, summary.status)
        assertFalse(summary.primaryReasons.joinToString().contains("수면"))
    }

    @Test
    fun generatedTextAvoidsForbiddenPostWorkoutMedicalWording() {
        val status = PhaseAwareTodayStatusBuilder().build(
            input(
                entries = listOf(
                    record(
                        confirmedSets = listOf(set(reps = 8, weightKg = 160.0, confirmed = true, rpe = 8.0))
                    )
                ),
                checkIns = listOf(DailyCheckIn(today.toString(), overallFatigue = 4))
            )
        )
        val text = listOf(status.headline, status.detail, status.actionLabel).joinToString(" ")

        listOf("부상 위험", "이 운동 때문에 다쳤습니다", "치료", "진단", "주의하세요").forEach { banned ->
            assertFalse(text.contains(banned))
        }
    }

    private fun input(
        entries: List<WorkoutEntryWithSets> = emptyList(),
        metrics: List<DailyMetric> = emptyList(),
        checkIns: List<DailyCheckIn> = emptyList()
    ): TodayReadinessEngineInput =
        TodayReadinessEngineInput(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = entries,
            dailyMetrics = metrics,
            dailyCheckIns = checkIns
        )

    private fun record(
        confirmedSets: List<WorkoutSet>,
        plannedSets: List<WorkoutSet> = emptyList()
    ): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = 100,
            date = today.toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category
        )
        val sets = (confirmedSets + plannedSets).mapIndexed { index, set ->
            set.copy(id = index.toLong() + 1, entryId = entry.id, setIndex = index + 1)
        }
        return WorkoutEntryWithSets(entry = entry, sets = sets)
    }

    private fun set(
        reps: Int = 0,
        weightKg: Double = 0.0,
        seconds: Int = 0,
        confirmed: Boolean,
        rpe: Double? = null
    ): WorkoutSet =
        WorkoutSet(
            entryId = 0,
            setIndex = 1,
            reps = reps,
            weightKg = weightKg,
            seconds = seconds,
            confirmed = confirmed,
            rpe = rpe
        )

    private fun heavyExercise(): Exercise =
        Exercise(
            id = 1,
            name = "Heavy fixture",
            category = "근력운동",
            stableKey = "heavy_fixture",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            primaryMuscles = "QUADS|GLUTES",
            secondaryMuscles = "HAMSTRING",
            equipment = "BARBELL",
            compoundType = "COMPOUND",
            forceType = "SQUAT",
            plane = "SAGITTAL",
            laterality = "BILATERAL",
            axialLoadLevel = "HIGH",
            trainingRole = "MAIN_STRENGTH",
            fatigueCategories = "SYSTEMIC|NEURAL_HEAVY|LOCAL_MUSCLE",
            adaptiveBaselineGroups = "SYSTEMIC|HEAVY_LOWER|SQUAT_PATTERN",
            recoveryDecayProfile = "LONG",
            systemicLoadWeight = 0.8,
            neuralHeavyWeight = 0.75,
            localLoadWeight = 0.7,
            progressMetricType = "ESTIMATED_1RM",
            strengthProgressionGroup = "SQUAT",
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            badmintonTransferStrength = "GENERAL",
            analysisEligibility = "FATIGUE|STRENGTH_PROGRESS",
            metadataConfidence = "HIGH"
        )
}
