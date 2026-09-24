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
import kotlin.math.ln

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
            val posteriorDao = db.strengthPosteriorDao()
            val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
            if (posteriorDao.revision(revisionKey) == null) {
                posteriorDao.insertRevisionStrict(
                    StrengthModelRevisionPolicy.current(1L, null).copy(
                        status = StrengthModelRevisionPolicy.STATUS_ACTIVE,
                        rebuildCompletedAt = 1L
                    )
                )
            } else {
                posteriorDao.updateRevisionStatus(
                    revisionKey, StrengthModelRevisionPolicy.STATUS_ACTIVE, 1L, null, null
                )
            }
            db.initialUserProfileDao().upsert(
                InitialUserProfile(
                    primaryGoal = "STRENGTH_GAIN",
                    strengthTrainingYears = 2.0,
                    badmintonTrainingYears = 2.0,
                    strengthSessionsPerWeek = 3.0,
                    strengthMinutesPerSession = 60,
                    habitualTrainingIntensity = "NORMAL"
                )
            )
            val cutoff = LocalDate.of(2026, 9, 20)
            listOf("barbell_back_squat").forEachIndexed { exerciseIndex, stableKey ->
                val historyExercise = requireNotNull(db.exerciseDao().findByStableKey(stableKey))
                listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L, 49L, 51L).forEachIndexed { weekIndex, daysAgo ->
                    val historyEntryId = db.workoutDao().insertEntry(
                        WorkoutEntry(
                            date = cutoff.minusDays(daysAgo).toString(),
                            exerciseStableKey = historyExercise.stableKey,
                            exerciseName = historyExercise.name,
                            category = historyExercise.category,
                            sessionStableKey = "b5-service-history-$exerciseIndex-$weekIndex"
                        )
                    )
                    (1..3).forEach { setIndex ->
                        db.workoutDao().insertSet(
                            WorkoutSet(
                                entryId = historyEntryId,
                                setIndex = setIndex,
                                // Keep two older completed weeks in the reviewed Strength band;
                                // recent 8-rep sets are reviewed non-realization for Strength,
                                // leaving the materialized 40 kg load compatible by intensity
                                // while making its rep prescription incompatible.
                            reps = if (daysAgo >= 42L) 5 else 8,
                                weightKg = 40.0 + exerciseIndex,
                                confirmed = true,
                                rpe = 8.0
                            )
                        )
                    }
                }
            }
            posteriorDao.insertLocalHistoryStrict(
                listOf("barbell_back_squat").map { stableKey ->
                    StrengthExercisePerformanceHistoryEntity(
                        revisionKey = revisionKey,
                        eventUuid = "b6-service-reference",
                        sessionKey = "b6-service-reference-session",
                        // The reviewed reference must precede every observed session so the
                        // canonical classifier can independently validate the historical loads.
                        sessionDate = cutoff.minusDays(55).toString(),
                        exerciseStableKey = stableKey,
                        priorLogMean = ln(50.0),
                        priorLogVariance = 0.1,
                        sessionLikelihoodLogMean = null,
                        sessionLikelihoodLogVariance = null,
                        sessionLikelihoodProper = true,
                        innovationResidualLog = null,
                        innovationVariance = null,
                        posteriorLogMean = ln(50.0),
                        posteriorLogVariance = 0.1,
                        posteriorMeanIncrementLog = 0.0,
                        transitionDays = 1L,
                        baselineEstablishedBefore = true,
                        baselineEstablishedAfter = true,
                        proxyTransferEligible = false,
                        proxyTransferApplied = false,
                        modelVersion = "B6_TEST",
                        curveVersion = "B6_TEST",
                        rirPolicyVersion = "B6_TEST",
                        evidenceFingerprint = "b6-service-reference-$stableKey",
                        createdAt = 1L
                    )
                }
            )
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            // B8's real authorized fixture intentionally leaves one existing Strength owner
            // available and excludes every other exercise identity. The only material change
            // should therefore be the exact B6 prescription repair for barbell_back_squat.
            val excludedCanonicalKeys = metadata.keys.filter { it != "barbell_back_squat" }.toSet()
            val request = ProgramSkeletonRequest(
                name = "B5 service integration",
                goal = ProgramGoal.BADMINTON_SUPPORT,
                weeklyTrainingDays = 3,
                sessionMinutes = 60,
                availableEquipment = emptySet(),
                excludedExerciseText = "",
                badmintonTransferRatio = 0.5,
                sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO,
                durationWeeks = 2,
                excludedExerciseStableKeys = excludedCanonicalKeys
            )
            val constraints = PersonalizedGenerationConstraints(
                explicitGoal = ProgramGoal.BADMINTON_SUPPORT,
                explicitWeeklyTrainingDays = 3,
                explicitDurationWeeks = 2,
                explicitSessionMinutes = 60
            )
            val preflight = repository.preparePersonalizedProgram(request, constraints, cutoff)
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question ->
                question.id to when (question.id) {
                    QUESTION_STRENGTH_INTENT -> StrengthIntent.STRENGTH_PRIORITY.name
                    QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.ENABLED.name
                    QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                    QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                    else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN"
                    else error("Unexpected personalized question: ${question.id}")
                }
            })
            assertTrue("canonical metadata must be seeded", metadata.isNotEmpty())
            val standalone = repository.generatePreparedPersonalizedProgram(preflight, answers)
            val evaluation = service.generatePreparedStimulusProductionCutoverEvaluation(preflight, answers, metadata)
            val comparison = evaluation.comparison

            assertEquals(
                "control fingerprint must remain unchanged",
                personalizedProgramFingerprint(standalone.request, standalone.items),
                personalizedProgramFingerprint(comparison.control.request, comparison.control.items)
            )
            assertEquals("control goal", preflight.request.goal, comparison.control.request.goal)
            assertEquals("control weekly days", preflight.request.weeklyTrainingDays, comparison.control.request.weeklyTrainingDays)
            assertEquals("control duration", preflight.request.durationWeeks, comparison.control.request.durationWeeks)
            assertNotNull("target plan", comparison.targetPlan)
            assertNotNull("selection plan", comparison.selectionPlan)
            assertNotNull("control audit", comparison.controlAudit)
            assertNotNull("experimental audit", comparison.experimentalAudit)
            assertNotNull("realization plan", comparison.prescriptionRealizationPlan)
            assertNotNull("B7 readiness audit", comparison.experimentalReadinessAudit)
            assertTrue("B7 must be shadow only", comparison.experimentalReadinessAudit?.shadowOnly == true)
            assertFalse("B7 must not grant production authority", comparison.experimentalReadinessAudit?.productionAuthority == true)
            assertEquals("B7 winner must remain null", null, comparison.experimentalReadinessAudit?.winner)
            assertTrue(comparison.prescriptionRealizationPlan?.shadowOnly == true)
            assertFalse(comparison.prescriptionRealizationPlan?.mutationAuthority == true)
            assertTrue(comparison.prescriptionRealizationPlan?.resolutions.orEmpty().all { !it.mutationAuthority })
            val resolved = comparison.prescriptionRealizationPlan?.resolutions.orEmpty().firstOrNull {
                it.status == StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED
            }
            assertNotNull("real service path must resolve one safe B6.1 Strength proposal", resolved)
            val resolution = requireNotNull(resolved)
            val owner = requireNotNull(resolution.owner)
            assertEquals("barbell_back_squat", owner.stableKey)
            assertEquals(
                comparison.control.items.first { it.exerciseStableKey == "barbell_back_squat" }.selectionRole,
                owner.selectionRole
            )
            assertEquals(resolution.currentPrescription?.sets?.size, resolution.proposedPrescription?.sets?.size)
            assertTrue(resolution.proposedPrescription?.sets?.all { it.reps in 1..6 } == true)
            val currentLoads = requireNotNull(resolution.currentPrescription).sets.map { it.weightKg }
            assertTrue(resolution.proposedPrescription?.sets?.all { set -> set.weightKg <= (currentLoads.maxOrNull() ?: 0.0) } == true)
            assertFalse(resolution.mutationAuthority)
            assertTrue("fixture must retain canonical B5 traces", comparison.selectionPlan.traces.isNotEmpty())
            assertTrue(comparison.materializationTraces.isNotEmpty())
            assertTrue(comparison.experimental.items.isNotEmpty())
            assertTrue(comparison.winner == null)
            assertFalse(comparison.selectionPlan.productionSelectionAuthority)
            assertEquals(
                "B8 status=${evaluation.cutoverAuthority.status} reasons=${evaluation.cutoverAuthority.reasonCodes} b7=${evaluation.cutoverAuthority.b7Status}",
                StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                evaluation.cutoverAuthority.status
            )
            assertEquals(StimulusProductionCutoverScope.STRENGTH_V1, evaluation.cutoverAuthority.scope)
            assertFalse(evaluation.cutoverAuthority.routingActive)
            assertFalse(evaluation.cutoverAuthority.productionMutationAuthority)
            assertEquals(evaluation.cutoverAuthority, comparison.productionCutoverAuthority)
            assertEquals(
                "normal repository must continue to return CONTROL",
                personalizedProgramFingerprint(standalone.request, standalone.items),
                personalizedProgramFingerprint(comparison.control.request, comparison.control.items)
            )
            assertFalse(comparison.selectionPlan.prescriptionAuthority)
            assertEquals(
                comparison.control.items.map { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }.toSet(),
                comparison.controlOwnerIdentities
            )
            assertEquals(
                comparison.experimental.items.map { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }.toSet(),
                comparison.experimentalOwnerIdentities
            )
            comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
                .filter { it.stableKey in comparison.removedStableKeys }
                .forEach { attribution ->
                    assertNotNull("removed owner attribution must retain exact role", attribution.selectionRole)
                }
            comparison.materializationTraces.forEach { trace ->
                assertEquals(trace.selectedStableKey != null, trace.selectedAtB5)
                assertTrue(trace.finalWeeklyOccurrences >= 0)
                assertTrue(trace.finalTotalSetUnits >= 0)
            }

            // Reusing the real B6.2 comparison with one CONTROL owner removed proves that B8's
            // narrower first-cutover boundary rejects a valid B7 candidate without routing it.
            val removedOwner = comparison.control.items.firstOrNull()
            if (removedOwner != null) {
                val blocked = comparison.copy(
                    experimental = comparison.experimental.copy(
                        items = comparison.experimental.items.filterNot {
                            it.exerciseStableKey == removedOwner.exerciseStableKey &&
                                it.selectionRole == removedOwner.selectionRole
                        }
                    ),
                    experimentalReadinessAudit = comparison.experimentalReadinessAudit?.copy(
                        status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
                    )
                )
                val blockedDecision = StimulusProductionCutoverAuthorityAuditEngine().audit(blocked)
                assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, blockedDecision.status)
                assertTrue(blockedDecision.reasonCodes.contains("B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"))
            }
        } finally {
            db.close()
        }
    }

    private fun field(target: Any, name: String): Any =
        requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))
}
