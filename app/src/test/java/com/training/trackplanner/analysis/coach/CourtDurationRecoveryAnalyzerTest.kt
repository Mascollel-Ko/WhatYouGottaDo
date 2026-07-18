package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CourtDurationRecoveryAnalyzerTest {
    private val today = LocalDate.of(2026, 6, 23)
    private val badminton = Exercise(id = 1, name = "배드민턴 세션", category = "스포츠", stableKey = "badminton_session")
    private val sleep = SleepRecoverySignal(null, null, null, CoachingSignalSeverity.INFO, "", "")

    @Test
    fun longCourtSessionPairsOnlyWithNextDayDataByDate() {
        val sessionDate = today.minusDays(3)
        val signal = CourtDurationRecoveryAnalyzer().analyze(
            today = today,
            entriesWithSets = listOf(courtRecord(sessionDate, minutes = 120)),
            exercises = listOf(badminton),
            runtimeMetadataCatalog = catalog(),
            checkIns = listOf(
                DailyCheckIn(sessionDate.toString(), overallFatigue = 5),
                DailyCheckIn(sessionDate.plusDays(1).toString(), overallFatigue = 4)
            ),
            history = listOf(fatigue(sessionDate.plusDays(1), ofi = 80)),
            sleepSignal = sleep
        )

        assertNotNull(signal)
        assertEquals(1, signal!!.sampleSize)
        assertEquals(CoachingSignalSeverity.INFO, signal.severity)
    }

    @Test
    fun enoughSamplesCanCreateNinetyMinuteWarning() {
        val first = today.minusDays(8)
        val second = today.minusDays(3)
        val signal = CourtDurationRecoveryAnalyzer().analyze(
            today = today,
            entriesWithSets = listOf(courtRecord(first, 95), courtRecord(second, 100)),
            exercises = listOf(badminton),
            runtimeMetadataCatalog = catalog(),
            checkIns = listOf(
                DailyCheckIn(first.plusDays(1).toString(), lowerBodyFatigue = 4),
                DailyCheckIn(second.plusDays(1).toString(), overallFatigue = 3)
            ),
            history = emptyList(),
            sleepSignal = sleep
        )

        assertNotNull(signal)
        assertEquals(90, signal!!.observedThresholdMinutes)
        assertEquals(CoachingSignalSeverity.WATCH, signal.severity)
    }

    @Test
    fun nonSportSessionDoesNotCreateCourtSignal() {
        val signal = CourtDurationRecoveryAnalyzer().analyze(
            today = today,
            entriesWithSets = listOf(courtRecord(today.minusDays(2), 120)),
            exercises = listOf(badminton),
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            checkIns = listOf(DailyCheckIn(today.minusDays(1).toString(), overallFatigue = 5)),
            history = emptyList(),
            sleepSignal = sleep
        )

        assertNull(signal)
    }

    private fun catalog(): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(
            listOf(
                RuntimeExerciseMetadataDefaults.forIdentity(badminton.stableKey, badminton.name).copy(
                    activityKind = "SPORT_SESSION",
                    badmintonTransferLevel = "DIRECT",
                    sportContextTags = MetadataTokenField.parse("BADMINTON_COURT")
                )
            )
        )

    private fun courtRecord(date: LocalDate, minutes: Int): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = date.toEpochDay(),
                date = date.toString(),
                exerciseId = badminton.id,
                exerciseName = badminton.name,
                category = badminton.category
            ),
            sets = listOf(
                WorkoutSet(
                    entryId = date.toEpochDay(),
                    setIndex = 1,
                    seconds = minutes * 60,
                    confirmed = true
                )
            )
        )

    private fun fatigue(date: LocalDate, ofi: Int): DailyFatigueResult =
        DailyFatigueResult(
            state = DailyFatigueState(
                date = date,
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
                overallFatigueIndex = ofi,
                readinessLabel = FatigueReadinessLabel.NORMAL,
                cautionReasons = emptyList(),
                confidence = FatigueConfidence.MEDIUM
            ),
            groupStates = emptyList(),
            recordContributions = emptyList()
        )
}
