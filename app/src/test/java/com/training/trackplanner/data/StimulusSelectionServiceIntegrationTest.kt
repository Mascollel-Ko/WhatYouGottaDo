package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Exercises the real repository/service orchestration for the explicit B5 A/B path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusSelectionServiceIntegrationTest {
    @Test
    fun serviceComparisonUsesRealCanonicalMetadataAndPreservesControlFingerprint() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.seedIfNeeded()
            db.initialUserProfileDao().upsert(
                InitialUserProfile(
                    primaryGoal = "STRENGTH_GAIN",
                    strengthTrainingYears = 2.0,
                    badmintonTrainingYears = 0.0,
                    strengthSessionsPerWeek = 3.0,
                    strengthMinutesPerSession = 60,
                    habitualTrainingIntensity = "NORMAL"
                )
            )
            val cutoff = LocalDate.of(2026, 9, 20)
            listOf("barbell_back_squat", "barbell_bench_press", "barbell_deadlift").forEachIndexed { exerciseIndex, stableKey ->
                val historyExercise = requireNotNull(db.exerciseDao().findByStableKey(stableKey))
                listOf(7L, 14L, 21L, 28L).forEachIndexed { weekIndex, daysAgo ->
                    val historyEntryId = db.workoutDao().insertEntry(
                        WorkoutEntry(
                            date = cutoff.minusDays(daysAgo).toString(),
                            exerciseStableKey = historyExercise.stableKey,
                            exerciseName = historyExercise.name,
                            category = historyExercise.category,
                            sessionStableKey = "b5-service-history-$exerciseIndex-$weekIndex"
                        )
                    )
                    db.workoutDao().insertSet(
                        WorkoutSet(
                            entryId = historyEntryId,
                            setIndex = 1,
                            reps = 8,
                            weightKg = 40.0 + exerciseIndex,
                            confirmed = true
                        )
                    )
                }
            }
            val request = ProgramSkeletonRequest(
                name = "B5 service integration",
                goal = ProgramGoal.STRENGTH,
                weeklyTrainingDays = 3,
                sessionMinutes = 60,
                availableEquipment = emptySet(),
                excludedExerciseText = "",
                badmintonTransferRatio = 0.5,
                sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO,
                durationWeeks = 2
            )
            val constraints = PersonalizedGenerationConstraints(
                explicitGoal = ProgramGoal.STRENGTH,
                explicitWeeklyTrainingDays = 3,
                explicitDurationWeeks = 2,
                explicitSessionMinutes = 60
            )
            val preflight = repository.preparePersonalizedProgram(request, constraints, cutoff)
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question ->
                question.id to when (question.id) {
                    QUESTION_STRENGTH_INTENT -> StrengthIntent.STRENGTH_PRIORITY.name
                    QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.DISABLED.name
                    QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                    QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                    else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN"
                    else error("Unexpected personalized question: ${question.id}")
                }
            })
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            assertTrue("canonical metadata must be seeded", metadata.isNotEmpty())
            val standalone = repository.generatePreparedPersonalizedProgram(preflight, answers)
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val comparison = service.generatePreparedStimulusSelectionComparison(preflight, answers, metadata)

            assertEquals(
                personalizedProgramFingerprint(standalone.request, standalone.items),
                personalizedProgramFingerprint(comparison.control.request, comparison.control.items)
            )
            assertEquals(standalone.items, comparison.control.items)
            assertEquals(preflight.request.goal, comparison.control.request.goal)
            assertEquals(preflight.request.weeklyTrainingDays, comparison.control.request.weeklyTrainingDays)
            assertEquals(preflight.request.durationWeeks, comparison.control.request.durationWeeks)
            assertNotNull(comparison.targetPlan)
            assertNotNull(comparison.selectionPlan)
            assertNotNull(comparison.controlAudit)
            assertNotNull(comparison.experimentalAudit)
            assertTrue("fixture must exercise canonical B5 selection", comparison.selectionPlan.selectedCandidates.isNotEmpty())
            assertTrue(comparison.materializationTraces.isNotEmpty())
            assertTrue(comparison.experimental.items.isNotEmpty())
            assertTrue(comparison.winner == null)
            assertFalse(comparison.selectionPlan.productionSelectionAuthority)
            assertFalse(comparison.selectionPlan.prescriptionAuthority)
            comparison.materializationTraces.forEach { trace ->
                assertEquals(trace.selectedStableKey != null, trace.selectedAtB5)
                assertTrue(trace.finalWeeklyOccurrences >= 0)
                assertTrue(trace.finalTotalSetUnits >= 0)
            }
            println("B5_SERVICE_SELECTION selected=${comparison.selectionPlan.selectedCandidates.map { it.stableKey }} traces=${comparison.materializationTraces}")
        } finally {
            db.close()
        }
    }

    private fun field(target: Any, name: String): Any =
        requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))
}
