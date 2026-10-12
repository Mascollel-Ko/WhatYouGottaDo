package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.BadmintonPlanningIntent
import com.training.trackplanner.data.personalized.PersonalizedGenerationConstraints
import com.training.trackplanner.data.personalized.PersonalizedPlannerProgress
import com.training.trackplanner.data.personalized.PersonalizedPlannerProgressReporter
import com.training.trackplanner.data.personalized.PersonalizedPlanningAnswers
import com.training.trackplanner.data.personalized.PersonalizedPlanningPreflight
import com.training.trackplanner.data.personalized.StimulusCanonicalEvaluationFailure
import com.training.trackplanner.data.personalized.StimulusCanonicalEvaluationFailureReason
import com.training.trackplanner.data.personalized.StimulusProductionExecutionRejectedException
import com.training.trackplanner.data.personalized.StimulusProductionProgramSource
import com.training.trackplanner.data.personalized.StrengthIntent
import com.training.trackplanner.data.personalized.FreeWeightWillingness
import com.training.trackplanner.data.personalized.StimulusProductionRoutingMode
import com.training.trackplanner.data.personalized.ProductionGenerationObservation
import com.training.trackplanner.data.personalized.ProductionGenerationPhase
import com.training.trackplanner.data.personalized.QUESTION_STRENGTH_INTENT
import com.training.trackplanner.data.personalized.QUESTION_BADMINTON_INTENT
import com.training.trackplanner.data.personalized.QUESTION_FREE_WEIGHT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusProductionFailureBoundaryTest {
    @Test
    fun unsafeCanonicalGenerationReturnsExplicitFailureWithoutControlFallback() = runBlocking {
        val fixture = fixture()
        try {
            val updates = mutableListOf<PersonalizedPlannerProgress>()
            val observations = mutableListOf<ProductionGenerationObservation>()
            var controlInvocations = 0
            val failure = captureThrowable {
                fixture.service.generatePreparedProduction(
                    preflight = fixture.preflight,
                    answers = fixture.answers,
                    metadata = emptyMap(),
                    progress = reporter(updates),
                    includeComparisonDiagnostics = false,
                    productionGenerationObserver = observations::add,
                    controlGenerationOverride = { controlInvocations++; fixture.control },
                    experimentalProgramBuildOverride = {
                        throw StimulusCanonicalEvaluationFailure(
                            StimulusCanonicalEvaluationFailureReason.NO_EXECUTABLE_PLANNING_DEMAND,
                            detailCode = "wording-independent-detail"
                        )
                    }
                )
            }

            assertTrue(failure is StimulusProductionExecutionRejectedException)
            val rejected = failure as StimulusProductionExecutionRejectedException
            assertTrue("NO_EXECUTABLE_PLANNING_DEMAND" in rejected.reasonCodes)
            assertTrue("wording-independent-detail" in rejected.reasonCodes)
            assertEquals("a failed B1–B6 attempt must never invoke legacy CONTROL", 0, controlInvocations)
            val phases = observations.map { it.phase }
            assertTrue(ProductionGenerationPhase.EXPERIMENTAL_BUILD in phases)
            assertFalse(ProductionGenerationPhase.CONTROL_BUILD in phases)
            assertFalse(ProductionGenerationPhase.B7 in phases)
            assertFalse(ProductionGenerationPhase.B8 in phases)
            assertFalse(ProductionGenerationPhase.B9 in phases)
            assertFalse(ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE in phases)
            assertEquals(updates.map { it.percent }.sorted(), updates.map { it.percent })
            assertTrue(updates.none { it.percent == 100 })
        } finally {
            fixture.db.close()
        }
    }

    @Test
    fun ordinaryNewGeneratorInvocationBuildsNoControlAndReturnsValidatedOutput() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            label = "c39_default_new_generator",
            quality = com.training.trackplanner.data.TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 3,
            minutes = 60,
            equipment = emptySet(),
            isolateOwner = true
        )
        val result = requireNotNull(StimulusProductionCoverageAuditTest().runCase(spec) { service, preflight, answers, metadata ->
            service.generatePreparedProduction(
                preflight = preflight,
                answers = answers,
                metadata = metadata,
                includeComparisonDiagnostics = false
            )
        })
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertEquals(0, result.buildCounts.controlBuilds)
        assertEquals(1, result.buildCounts.experimentalBuilds)
        assertEquals(1, result.buildCounts.totalBuildInvocations)
        assertEquals(0, result.buildCounts.thirdBuilds)
        assertTrue(requireNotNull(result.executionValidation).executable)
        assertTrue(result.program.items.isNotEmpty())
        assertEquals(null, result.comparison)
    }

    @Test
    fun b6PrescriptionMutationIsRejectedWithoutControlFallback() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            label = "c38_3_unauthorized_b6_load",
            quality = com.training.trackplanner.data.TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 3,
            minutes = 60,
            equipment = emptySet(),
            isolateOwner = true
        )
        var controlInvocations = 0
        val result = requireNotNull(StimulusProductionCoverageAuditTest().runCase(spec) { service, preflight, answers, metadata ->
            val valid = service.generatePreparedProduction(
                preflight = preflight,
                answers = answers,
                metadata = metadata,
                includeComparisonDiagnostics = false
            )
            val ownerIndex = valid.program.items.indexOfFirst { it.setPrescriptions.isNotEmpty() }
            assertTrue("the B1–B6 fixture must contain a materialized physical set", ownerIndex >= 0)
            val original = valid.program.items[ownerIndex]
            val unauthorized = valid.program.copy(items = valid.program.items.toMutableList().also { items ->
                items[ownerIndex] = original.copy(setPrescriptions = original.setPrescriptions.mapIndexed { index, set ->
                    if (index == 0) set.copy(weightKg = set.weightKg + 17.5) else set
                })
            })
            val rejected = captureThrowable {
                service.generatePreparedProduction(
                    preflight = preflight,
                    answers = answers,
                    metadata = metadata,
                    includeComparisonDiagnostics = false,
                    controlGenerationOverride = { controlInvocations++; error("CONTROL_MUST_NOT_RUN") },
                    experimentalProgramBuildOverride = { unauthorized }
                )
            }
            assertTrue(rejected is StimulusProductionExecutionRejectedException)
            val executionFailure = rejected as StimulusProductionExecutionRejectedException
            assertTrue(executionFailure.reasonCodes.contains("B6_PHYSICAL_SET_INTEGRITY_FAILURE"))
            assertEquals("an unauthorized prescription must fail closed without returning CONTROL", 0, controlInvocations)
            valid
        })
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertTrue(requireNotNull(result.executionValidation).executable)
    }

    @Test
    fun cancellationPropagatesAndDoesNotComplete() = runBlocking {
        val fixture = fixture()
        try {
            val updates = mutableListOf<PersonalizedPlannerProgress>()
            val observations = mutableListOf<ProductionGenerationObservation>()
            val cancellation = CancellationException("cancel canonical evaluation")
            val thrown = captureThrowable {
                fixture.service.generatePreparedProduction(
                    fixture.preflight,
                    fixture.answers,
                    emptyMap(),
                    reporter(updates),
                    productionGenerationObserver = observations::add,
                    experimentalProgramBuildOverride = { throw cancellation }
                )
            }
            assertSame(cancellation, thrown)
            assertTrue(ProductionGenerationPhase.EXPERIMENTAL_BUILD in observations.map { it.phase })
            assertFalse(ProductionGenerationPhase.CONTROL_BUILD in observations.map { it.phase })
            assertTrue(updates.none { it.percent == 100 })
        } finally {
            fixture.db.close()
        }
    }

    @Test
    fun unexpectedIllegalStateExceptionPropagates() = runBlocking {
        assertUnexpected(IllegalStateException("BROKEN_INTERNAL_INVARIANT"))
    }

    @Test
    fun unexpectedIllegalArgumentExceptionPropagates() = runBlocking {
        assertUnexpected(IllegalArgumentException("BROKEN_INTERNAL_ARGUMENT"))
    }

    @Test
    fun optionalB7B8DiagnosticFailureCannotBlockNewGeneratorResult() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            label = "c38_3_diagnostic_control_failure",
            quality = com.training.trackplanner.data.TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 3,
            minutes = 60,
            equipment = emptySet(),
            isolateOwner = true
        )
        val controlFailure = IllegalStateException("CONTROL_BUILDER_FAILED")
        val observations = mutableListOf<ProductionGenerationObservation>()
        val result = requireNotNull(StimulusProductionCoverageAuditTest().runCase(spec) { service, preflight, answers, metadata ->
            service.generatePreparedProduction(
                preflight = preflight,
                answers = answers,
                metadata = metadata,
                productionGenerationObserver = observations::add,
                controlGenerationOverride = { throw controlFailure }
            )
        })
        val phases = observations.map { it.phase }
        assertTrue(phases.indexOf(ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE) < phases.indexOf(ProductionGenerationPhase.CONTROL_BUILD))
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertEquals(1, result.buildCounts.controlBuilds)
        assertEquals(1, result.buildCounts.experimentalBuilds)
        assertEquals("B7_B8_DIAGNOSTIC_FAILED_IllegalStateException", result.diagnosticFailureReason)
    }

    private suspend fun assertUnexpected(failure: RuntimeException) {
        val fixture = fixture()
        try {
            val thrown = captureThrowable {
                fixture.service.generatePreparedProduction(
                    fixture.preflight,
                    fixture.answers,
                    emptyMap(),
                    experimentalProgramBuildOverride = { throw failure }
                )
            }
            assertSame(failure, thrown)
        } finally {
            fixture.db.close()
        }
    }

    private fun reporter(updates: MutableList<PersonalizedPlannerProgress>) =
        object : PersonalizedPlannerProgressReporter {
            override fun report(stage: com.training.trackplanner.data.personalized.PersonalizedPlannerStage) = Unit
            override fun report(update: PersonalizedPlannerProgress) { updates += update }
        }

    private suspend fun captureThrowable(block: suspend () -> Unit): Throwable = try {
        block()
        error("expected failure")
    } catch (error: Throwable) {
        error
    }

    private data class Fixture(
        val db: TrainingDatabase,
        val repository: TrainingRepository,
        val service: PersonalizedProgramPlanningService,
        val preflight: PersonalizedPlanningPreflight,
        val answers: PersonalizedPlanningAnswers,
        val control: GeneratedProgramSkeleton
    )

    private suspend fun fixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build()
        db.exerciseDao().insertExercise(
            Exercise(
                stableKey = "barbell_back_squat",
                name = "Back Squat",
                category = "STRENGTH"
            )
        )
        db.initialUserProfileDao().upsert(
            InitialUserProfile(
                primaryGoal = "STRENGTH_GAIN",
                strengthTrainingYears = 2.0,
                strengthSessionsPerWeek = 3.0,
                strengthMinutesPerSession = 60,
                habitualTrainingIntensity = "NORMAL"
            )
        )
        val historyEntryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = LocalDate.of(2026, 9, 13).toString(),
                exerciseStableKey = "barbell_back_squat",
                exerciseName = "Back Squat",
                category = "STRENGTH",
                sessionStableKey = "c8-production-failure-fixture"
            )
        )
        (1..3).forEach { setIndex ->
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = historyEntryId,
                    setIndex = setIndex,
                    reps = 5,
                    weightKg = 70.0,
                    confirmed = true,
                    rpe = 8.0
                )
            )
        }
        val repository = TrainingRepository(db, context)
        val service = repository.javaClass.getDeclaredField("personalizedProgramPlanningService")
            .apply { isAccessible = true }
            .get(repository) as PersonalizedProgramPlanningService
        val request = ProgramSkeletonRequest(
            name = "B9.2 failure boundary",
            goal = ProgramGoal.STRENGTH,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.5,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 1
        )
        val preflight = PersonalizedPlanningPreflight(
            preparationId = "b9.2-test",
            cutoff = LocalDate.of(2026, 9, 20),
            request = request,
            constraints = PersonalizedGenerationConstraints(
                explicitGoal = ProgramGoal.STRENGTH,
                explicitWeeklyTrainingDays = 3,
                explicitDurationWeeks = 1,
                explicitSessionMinutes = 60
            ),
            questions = emptyList(),
            preparedAtEpochMillis = 1L
        )
        val control = GeneratedProgramSkeleton(
            suggestedName = request.name,
            durationDays = 7,
            request = request,
            periodizationType = request.periodizationType,
            weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 1, 8.0, 1, 0, false)),
            items = listOf(
                ProgramSkeletonItem(
                    localId = "b9.2-control",
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 1,
                    exerciseStableKey = "barbell_back_squat",
                    exerciseName = "Back Squat",
                    category = "STRENGTH",
                    restSeconds = 90,
                    prescription = "5 reps",
                    setCount = 1,
                    reps = 5,
                    weightKg = 80.0,
                    seconds = 0,
                    selectionReason = "test",
                    weightSource = "TEST",
                    selectionRole = "PRIMARY",
                    setPrescriptions = listOf(ProgramSetPrescription(1, 5, 80.0, 0))
                )
            ),
            weekDaySchedule = mapOf(1 to setOf(1))
        )
        val answers = PersonalizedPlanningAnswers(
            mapOf(
                QUESTION_STRENGTH_INTENT to StrengthIntent.STRENGTH_PRIORITY.name,
                QUESTION_BADMINTON_INTENT to BadmintonPlanningIntent.DISABLED.name,
                QUESTION_FREE_WEIGHT to FreeWeightWillingness.WILLING.name
            )
        )
        return Fixture(db, repository, service, preflight, answers, control)
    }
}
