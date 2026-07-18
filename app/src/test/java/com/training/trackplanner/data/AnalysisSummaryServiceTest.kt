package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.badminton.BadmintonTransferAxis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnalysisSummaryServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun fatigueAnalysisHistoryReturnsRepresentativeSeriesForConfirmedRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val exerciseId = insertFatigueExercise(db, "analysis.fatigue", "Fatigue lift")
        insertEntryWithSet(db, today.toString(), exerciseId, "Fatigue lift", confirmed = true)

        val history = service.fatigueAnalysisHistory(days = 7)

        assertEquals(7, history.size)
        assertEquals(today, history.last().state.date)
        assertTrue(history.last().recordContributions.isNotEmpty())
        history.flatMap { it.axisValues() }.forEach { value ->
            assertFalse(value.isNaN())
            assertFalse(value.isInfinite())
        }
    }

    @Test
    fun fatigueAnalysisHistoryEmptyDataKeepsSafeFallbackSeries() = runBlocking {
        val db = newDatabase()
        val service = service(db)

        val history = service.fatigueAnalysisHistory(days = 3)

        assertEquals(3, history.size)
        assertTrue(history.all { it.recordContributions.isEmpty() })
        assertTrue(history.all { it.state.confirmedTrainingLoad == 0.0 })
    }

    @Test
    fun badmintonTransferSummaryUsesWindowedConfirmedTransferRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val exerciseId = insertBadmintonExercise(db, "analysis.badminton", "Footwork drill")
        insertEntryWithSet(db, today.minusDays(1).toString(), exerciseId, "Footwork drill", confirmed = true)
        insertEntryWithSet(db, today.minusDays(35).toString(), exerciseId, "Footwork drill", confirmed = true)

        val summary = service.badmintonTransferSummary()

        assertEquals(1, summary.metrics.topTransferExercises7d.size)
        assertTrue(summary.metrics.totalTransferStimulus7d > 0.0)
        assertEquals(
            summary.metrics.totalTransferStimulus7d,
            summary.metrics.totalTransferStimulus28d,
            0.001
        )
        assertTrue((summary.metrics.axisShare7d[BadmintonTransferAxis.LATERAL_MOVEMENT] ?: 0.0) > 0.0)
    }

    @Test
    fun badmintonTransferCoverageSummaryReturnsNormalPathForTransferRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val exerciseId = insertBadmintonExercise(db, "analysis.coverage", "Coverage drill")
        insertEntryWithSet(db, today.minusDays(1).toString(), exerciseId, "Coverage drill", confirmed = true)

        val summary = service.badmintonTransferCoverageSummary(latestFatigueState = null)

        assertTrue(summary.isDataSufficient)
        assertEquals(14, summary.recentWindowDays)
        assertEquals(28, summary.baselineWindowDays)
        assertTrue(summary.statuses.any { it.axis == BadmintonTransferAxis.LATERAL_MOVEMENT })
    }

    @Test
    fun persistedRuntimeMetadataOverrideEnablesBadmintonTransferSummary() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val stableKey = "analysis.override"
        val exerciseId = db.exerciseDao().insertExercise(
            Exercise(
                name = "Override source",
                category = "Strength",
                stableKey = stableKey,
                analysisEligibility = "",
                badmintonTransferStrength = "",
                badmintonTransferRoles = "",
                courtMovementTypes = ""
            )
        )
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forIdentity(stableKey, "Override source")
                .copy(
                    analysisEligibility = MetadataTokenField.parse("BADMINTON_TRANSFER|FATIGUE"),
                    movementFamily = "FOOTWORK",
                    movementSubtype = "SKILL_DRILL",
                    badmintonTransferLevel = "DIRECT",
                    badmintonTransferType = MetadataTokenField.parse("FOOTWORK|ACCELERATION"),
                    badmintonPhysicalQualities = MetadataTokenField.parse("FIRST_STEP|LATERAL_MOVE"),
                    badmintonSkillTargets = MetadataTokenField.parse("FOOTWORK_SPEED"),
                    neuromuscularStressLevel = "MODERATE",
                    systemicMuscularStressLevel = "LOW",
                    localMuscularStressLevel = "LOW",
                    jointTendonImpactStressLevel = "LOW",
                    movementFocusDemandLevel = "MODERATE",
                    recoveryDurationClass = "SHORT"
                )
                .toEntity()
        )
        insertEntryWithSet(db, today.minusDays(1).toString(), exerciseId, "Override source", confirmed = true)

        val summary = service.badmintonTransferSummary()
        val coverage = service.badmintonTransferCoverageSummary(latestFatigueState = null)

        assertTrue(summary.metrics.totalTransferStimulus7d > 0.0)
        assertTrue((summary.metrics.axisShare7d[BadmintonTransferAxis.LATERAL_MOVEMENT] ?: 0.0) > 0.0)
        assertTrue(coverage.isDataSufficient)
    }

    @Test
    fun analysisSummariesIgnoreFutureAndOutOfWindowRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val exerciseId = insertBadmintonExercise(db, "analysis.window", "Window drill")
        insertEntryWithSet(db, today.minusDays(2).toString(), exerciseId, "Window drill", confirmed = true)
        insertEntryWithSet(db, today.plusDays(1).toString(), exerciseId, "Window drill", confirmed = true)
        insertEntryWithSet(db, today.minusDays(40).toString(), exerciseId, "Window drill", confirmed = true)

        val fatigueHistory = service.fatigueAnalysisHistory(days = 3)
        val transferSummary = service.badmintonTransferSummary()

        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), fatigueHistory.map { it.state.date })
        assertEquals(1, transferSummary.metrics.topTransferExercises7d.size)
        assertEquals(
            transferSummary.metrics.totalTransferStimulus7d,
            transferSummary.metrics.totalTransferStimulus28d,
            0.001
        )
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase): AnalysisSummaryService =
        AnalysisSummaryService(
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            dailyMetricDao = db.dailyMetricDao(),
            initialUserProfileDao = db.initialUserProfileDao(),
            runtimeExerciseMetadataDao = db.runtimeExerciseMetadataDao(),
            canonicalRuntimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
        )

    private suspend fun insertFatigueExercise(
        db: TrainingDatabase,
        stableKey: String,
        name: String
    ): Long =
        db.exerciseDao().insertExercise(
            Exercise(
                name = name,
                category = "Strength",
                stableKey = stableKey,
                analysisEligibility = "FATIGUE|STRENGTH_PROGRESS",
                progressMetricType = "ESTIMATED_1RM",
                strengthProgressionGroup = "SQUAT",
                movementPattern = "SQUAT",
                movementCategory = "LOWER_STRENGTH",
                primaryMuscles = "QUADS|GLUTES",
                trainingRole = "MAIN_STRENGTH",
                systemicLoadWeight = 0.8,
                neuralHeavyWeight = 0.7,
                localLoadWeight = 0.6,
                decelerationWeight = 0.2,
                stabilityDemandLevel = "MODERATE",
                recoveryDecayProfile = "LONG",
                estimated1RmEligible = true,
                volumeLoadEligible = true
            )
        )

    private suspend fun insertBadmintonExercise(
        db: TrainingDatabase,
        stableKey: String,
        name: String
    ): Long =
        db.exerciseDao().insertExercise(
            Exercise(
                name = name,
                category = "Badminton",
                stableKey = stableKey,
                analysisEligibility = "BADMINTON_TRANSFER|FATIGUE",
                movementPattern = "FOOTWORK",
                movementCategory = "SKILL_DRILL",
                trainingRole = "SKILL",
                badmintonTransferStrength = "DIRECT",
                badmintonTransferRoles = "FOOTWORK|ACCELERATION",
                courtMovementTypes = "FIRST_STEP|LATERAL_MOVE",
                badmintonSkillTargets = "FOOTWORK_SPEED",
                systemicLoadWeight = 0.3,
                neuralSpeedWeight = 0.5,
                localLoadWeight = 0.2,
                stabilityDemandLevel = "MODERATE",
                recoveryDecayProfile = "SHORT"
            )
        )

    private suspend fun insertEntryWithSet(
        db: TrainingDatabase,
        date: String,
        exerciseId: Long,
        exerciseName: String,
        confirmed: Boolean
    ) {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                category = "Test",
                rpe = 8.0,
                completedAt = if (confirmed) 1_000L else null,
                firstConfirmedAt = if (confirmed) 1_000L else null,
                displayOrder = 1
            )
        )
        db.workoutDao().insertSet(
            WorkoutSet(
                entryId = entryId,
                setIndex = 1,
                reps = 10,
                weightKg = 60.0,
                seconds = 0,
                confirmed = confirmed,
                rpe = 8.0
            )
        )
    }
}

private fun com.training.trackplanner.analysis.fatigue.DailyFatigueResult.axisValues(): List<Double> =
    listOf(
        state.highForceNeuralFatigue,
        state.systemicMuscularFatigue,
        state.localMuscularFatigue,
        state.highSpeedFatigue,
        state.reactiveFatigue,
        state.recoveryPressure,
        state.confirmedTrainingLoad
    )
