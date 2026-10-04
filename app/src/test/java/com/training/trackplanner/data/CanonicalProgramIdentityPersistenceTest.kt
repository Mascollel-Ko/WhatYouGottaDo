package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalProgramIdentityPersistenceTest {
    private val databases = mutableListOf<TrainingDatabase>()

    @After fun close() = databases.forEach(TrainingDatabase::close)

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), TrainingDatabase::class.java
    ).allowMainThreadQueries().build().also(databases::add)

    @Test fun `canonical saved program is a lineage and regeneration keeps it`() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("bench-lineage", "Bench press", "STRENGTH"))
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
        val request = ProgramSkeletonRequest(
            name = "Canonical",
            goal = ProgramGoal.STRENGTH,
            weeklyTrainingDays = 3,
            sessionMinutes = 45,
            availableEquipment = setOf("BARBELL", "BENCH"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.4,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        )
        val item = ProgramSkeletonItem(
            localId = "bench-week-1", weekNumber = 1, dayOfWeek = 2, orderIndex = 1,
            exerciseStableKey = "bench-lineage", exerciseName = "Bench press", category = "STRENGTH",
            restSeconds = 120, prescription = "2 x 6", setCount = 2, reps = 6, weightKg = 50.0,
            seconds = 0, selectionReason = "B5", weightSource = "B6",
            selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
        )
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = "Canonical", durationDays = 14, request = request,
            periodizationType = ProgramPeriodizationType.AUTO, weekPlans = emptyList(), items = listOf(item),
            personalizedDecision = com.training.trackplanner.data.personalized.PersonalizedPlanningDecision(
                decisionId = "c19-decision",
                protocolVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL,
                generatedAtEpochMillis = 1L,
                historyCutoff = "2026-10-04",
                historyWindowDays = 56,
                planningHorizonWeeks = 2,
                adaptationIntentMinWeeks = 1,
                adaptationIntentMaxWeeks = 2,
                observedTrainingBehavior = "STRENGTH",
                strengthIntent = "STRENGTH_PRIORITY",
                strengthIntentProvenance = "TEST",
                badmintonIntent = "NONE",
                badmintonIntentProvenance = "TEST",
                primaryAdaptation = "MAINTAIN",
                secondaryTargets = emptyList(),
                strengthStyle = "BALANCED",
                strengthStyleProvenance = "TEST",
                weeklyFrequency = 3,
                confidence = "HIGH",
                reasonCodes = listOf("C19_TEST"),
                reasons = listOf("test decision"),
                constraints = emptyList(),
                metadataAuthorityVersion = "test"
            )
        )
        val firstLineage = "user_program_c19_existing"
        val firstId = db.programDao().insertProgram(TrainingProgram(
            stableKey = firstLineage,
            name = "Existing canonical",
            durationDays = 14,
            canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
            canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
        ))
        val regeneratedId = service.saveGeneratedProgram(firstId, skeleton)
        val regenerated = checkNotNull(db.programDao().findProgram(regeneratedId))
        assertEquals(firstId, regeneratedId)
        assertEquals(firstLineage, regenerated.stableKey)
        assertEquals(CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION, regenerated.canonicalBuilderProtocolVersion)
        assertEquals(com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL,
            regenerated.canonicalPlannerRuntimeVersion)
        val persistedItem = db.programDao().itemsForProgram(regeneratedId).single()
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", persistedItem.selectionRole)

        val index = service.canonicalIncumbentPlacementIndex(regeneratedId)
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, index.status)
        assertEquals(firstLineage, index.source?.lineageId?.value)
        assertEquals(2, index.placements.single().day)
        assertEquals(1, index.placements.single().order)

        val independentId = service.saveGeneratedProgram(null, skeleton)
        val independent = checkNotNull(db.programDao().findProgram(independentId))
        assertNotEquals(firstLineage, independent.stableKey)
        assertEquals(CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION, independent.canonicalBuilderProtocolVersion)
        assertEquals(com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL,
            independent.canonicalPlannerRuntimeVersion)
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE,
            service.canonicalIncumbentPlacementIndex(independentId).status)
    }

    @Test fun `role replacement and missing legacy role never inherit exact placement`() {
        val program = TrainingProgram(
            id = 51, stableKey = "lineage-51", name = "P", durationDays = 14,
            canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
            canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
        )
        val prior = TrainingProgramItem(
            id = 1, programId = 51, weekNumber = 1, dayOfWeek = 2, orderIndex = 1,
            exerciseStableKey = "bench", exerciseName = "Bench", category = "STRENGTH",
            selectionRole = "COVERAGE_HORIZONTAL_PUSH"
        )
        val exactIndex = CanonicalIncumbentPlacementIndex.fromPersistedProgram(program, 51, listOf(prior))
        val canonicalReplacement = ProgramSkeletonItem(
            localId = "strength", weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = "bench", exerciseName = "Bench", category = "STRENGTH",
            restSeconds = 90, prescription = "2x6", setCount = 2, reps = 6, weightKg = 50.0,
            seconds = 0, selectionReason = "B5", weightSource = "B6",
            selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
        )
        assertTrue(CanonicalIncumbentPlacementShadowEvaluator.evaluate(exactIndex, listOf(canonicalReplacement)).rows.isEmpty())

        val legacyIndex = CanonicalIncumbentPlacementIndex.fromPersistedProgram(program, 51, listOf(prior.copy(selectionRole = null)))
        assertTrue(legacyIndex.placements.isEmpty())
        assertEquals(1, legacyIndex.omittedRowsWithoutExactRole)
    }

}
