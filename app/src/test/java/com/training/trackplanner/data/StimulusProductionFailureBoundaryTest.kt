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
import org.json.JSONObject
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
    fun typedExpectedFailureFallsBackToOriginalControlAndCompletes() = runBlocking {
        val fixture = fixture()
        try {
            val updates = mutableListOf<PersonalizedPlannerProgress>()
            val observations = mutableListOf<ProductionGenerationObservation>()
            var preparedRequest: ProgramSkeletonRequest? = null
            val result = fixture.service.generatePreparedProduction(
                preflight = fixture.preflight,
                answers = fixture.answers,
                metadata = emptyMap(),
                progress = reporter(updates),
                productionGenerationObserver = { observation ->
                    observations += observation
                    if (observation.phase == ProductionGenerationPhase.CANONICAL_PREPARED) {
                        preparedRequest = observation.context.resolvedRequest.request
                    }
                },
                controlGenerationOverride = { fixture.control.copy(request = requireNotNull(preparedRequest)) },
                experimentalProgramBuildOverride = {
                    throw StimulusCanonicalEvaluationFailure(
                        StimulusCanonicalEvaluationFailureReason.NO_EXECUTABLE_PLANNING_DEMAND,
                        detailCode = "wording-independent-detail"
                    )
                }
            )

            assertTrue("CONTROL fallback keeps the late materialized skeleton", result.program.items.isNotEmpty())
            assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
            assertFalse(result.routeDecision.productionRoutingActive)
            assertEquals(listOf("B9_UPSTREAM_EVALUATION_FAILED_CONTROL_FALLBACK"), result.routeDecision.reasonCodes)
            assertEquals(com.training.trackplanner.data.personalized.StimulusProductionFallbackStage.UPSTREAM_EVALUATION_FAILURE, result.diagnostics.primaryFallbackStage)
            assertTrue("NO_EXECUTABLE_PLANNING_DEMAND" in result.diagnostics.secondaryReasonCodes)
            assertTrue("wording-independent-detail" in result.diagnostics.secondaryReasonCodes)
            assertEquals(0, result.buildCounts.controlBuilds)
            assertEquals(1, result.buildCounts.experimentalBuilds)
            assertEquals(1, result.buildCounts.totalBuildInvocations)
            assertEquals(0, result.buildCounts.thirdBuilds)
            val phases = observations.map { it.phase }
            assertTrue(phases.indexOf(ProductionGenerationPhase.B6_PRE_AUTHORITY_COMPLETE) < phases.indexOf(ProductionGenerationPhase.EXPERIMENTAL_BUILD))
            assertTrue(phases.indexOf(ProductionGenerationPhase.EXPERIMENTAL_BUILD) < phases.indexOf(ProductionGenerationPhase.CONTROL_BUILD))
            assertFalse(ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE in phases)
            assertEquals(updates.map { it.percent }.sorted(), updates.map { it.percent })
            assertEquals(100, updates.last().percent)
            assertEquals(1, updates.count { it.percent == 100 })

            fixture.repository.saveGeneratedProgram(null, result.program)
            val persisted = fixture.db.appMetaDao()
                .latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")
                ?.value
                ?.let(::JSONObject)
                ?: JSONObject()
            val persistedJson = persisted.toString()
            listOf(
                "stimulusPrescriptionRealizationPlanShadow",
                "experimentalReadinessAudit",
                "productionCutoverAuthority",
                "productionRoutingDecision"
            ).forEach { key -> assertFalse("persisted decision must not contain $key", persistedJson.contains(key)) }
        } finally {
            fixture.db.close()
        }
    }

    @Test
    fun expectedExperimentalFailureBuildsExactlyOneLateControl() = runBlocking {
        val spec = StimulusProductionCoverageAuditTest.CoverageSpec(
            label = "c8_expected_experimental_failure",
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
                experimentalProgramBuildOverride = {
                    throw StimulusCanonicalEvaluationFailure(
                        StimulusCanonicalEvaluationFailureReason.NO_EXECUTABLE_PLANNING_DEMAND,
                        detailCode = "C8_EXPECTED_EXPERIMENTAL_FAILURE"
                    )
                }
            )
        })
        assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
        assertEquals(1, result.buildCounts.controlBuilds)
        assertEquals(1, result.buildCounts.experimentalBuilds)
        assertEquals(2, result.buildCounts.totalBuildInvocations)
        assertEquals(0, result.buildCounts.thirdBuilds)
        assertTrue("C8_EXPECTED_EXPERIMENTAL_FAILURE" in result.diagnostics.secondaryReasonCodes)
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
    fun controlFailurePropagatesAfterCanonicalArtifactCompletes() = runBlocking {
        val fixture = fixture()
        try {
            val observations = mutableListOf<ProductionGenerationObservation>()
            val controlFailure = IllegalStateException("CONTROL_BUILDER_FAILED")
            val thrown = captureThrowable {
                fixture.service.generatePreparedProduction(
                    fixture.preflight,
                    fixture.answers,
                    emptyMap(),
                    productionGenerationObserver = observations::add,
                    controlGenerationOverride = { throw controlFailure },
                    experimentalProgramBuildOverride = { fixture.control }
                )
            }
            assertSame(controlFailure, thrown)
            val phases = observations.map { it.phase }
            assertTrue(phases.indexOf(ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE) <
                phases.indexOf(ProductionGenerationPhase.CONTROL_BUILD))
        } finally {
            fixture.db.close()
        }
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
