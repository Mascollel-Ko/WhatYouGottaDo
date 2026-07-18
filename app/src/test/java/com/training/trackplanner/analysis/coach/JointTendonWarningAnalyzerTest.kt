package com.training.trackplanner.analysis.coach

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JointTendonWarningAnalyzerTest {
    private val today = LocalDate.of(2026, 6, 23)
    private val exercise = Exercise(id = 1, name = "점프 테스트", category = "근력운동", stableKey = "jump_test")
    private val sleep = SleepRecoverySignal(null, null, null, CoachingSignalSeverity.INFO, "", "")

    @Test
    fun discomfortWithHighMetadataStressCreatesWarning() {
        val signal = JointTendonWarningAnalyzer().analyze(
            today = today,
            checkIns = listOf(DailyCheckIn(today.toString(), jointTendonDiscomfort = 4)),
            entriesWithSets = listOf(record(today)),
            exercises = listOf(exercise),
            runtimeMetadataCatalog = catalog(jointTags = "PLYOMETRIC_LANDING"),
            sleepSignal = sleep
        )

        assertNotNull(signal)
        assertEquals(CoachingSignalSeverity.WATCH, signal!!.severity)
        assertTrue(signal.relatedStressLabels.isNotEmpty())
    }

    @Test
    fun highDiscomfortWithoutMetadataUsesCautiousNonCausalWording() {
        val signal = JointTendonWarningAnalyzer().analyze(
            today = today,
            checkIns = listOf(DailyCheckIn(today.toString(), jointTendonDiscomfort = 5)),
            entriesWithSets = emptyList(),
            exercises = emptyList(),
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            sleepSignal = sleep
        )

        assertNotNull(signal)
        assertEquals(CoachingSignalSeverity.CAUTION, signal!!.severity)
        assertFalse(signal.detail.contains("진단"))
        assertFalse(signal.detail.contains("치료"))
        assertFalse(signal.detail.contains("때문"))
    }

    private fun catalog(jointTags: String): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(
            listOf(
                RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name).copy(
                    jointTendonImpactStressLevel = "HIGH",
                    jointImpactStressTags = MetadataTokenField.parse(jointTags)
                )
            )
        )

    private fun record(date: LocalDate): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = date.toEpochDay(),
                date = date.toString(),
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category
            ),
            sets = listOf(WorkoutSet(entryId = date.toEpochDay(), setIndex = 1, reps = 5, confirmed = true))
        )

}
