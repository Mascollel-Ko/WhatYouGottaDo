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
                                // Keep the latest prescription outside the reviewed Strength
                                // band while older weeks establish a classified direct baseline.
                            reps = if (daysAgo % 4L == 3L) 8 else 5,
                                weightKg = 40.0 + exerciseIndex,
                                confirmed = true,
                                rpe = 8.0
                            )
                        )
                    }
                }
            }
            posteriorDao.insertLocalHistoryStrict(
                listOf("barbell_back_squat").flatMap { stableKey ->
                    // The earlier reviewed reference classifies the historical 40 kg sets;
                    // the newer posterior is the official service comparison denominator.
                    listOf(-55L to 50.0, -1L to 60.0).mapIndexed { index, (daysAgo, mean) ->
                        StrengthExercisePerformanceHistoryEntity(
                            revisionKey = revisionKey,
                            eventUuid = "b6-service-reference-$index",
                            sessionKey = "b6-service-reference-session-$index",
                            sessionDate = cutoff.minusDays(daysAgo).toString(),
                            exerciseStableKey = stableKey,
                            priorLogMean = ln(mean),
                            priorLogVariance = 0.1,
                            sessionLikelihoodLogMean = null,
                            sessionLikelihoodLogVariance = null,
                            sessionLikelihoodProper = true,
                            innovationResidualLog = null,
                            innovationVariance = null,
                            posteriorLogMean = ln(mean),
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
                            evidenceFingerprint = "b6-service-reference-$stableKey-$index",
                            createdAt = index.toLong() + 1L
                        )
                    }
                }
            )
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val physicalQualityCatalog = field(service, "physicalQualityCatalog") as CanonicalExercisePhysicalQualityCatalog
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val excludedStrengthKeys = metadata.keys.filter { stableKey ->
                stableKey != "barbell_back_squat" && physicalQualityCatalog.relations(stableKey).any {
                    it.qualityId == TrainableQuality.STRENGTH && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
                }
            }.toSet()
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
                excludedExerciseStableKeys = excludedStrengthKeys
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
            val comparison = service.generatePreparedStimulusSelectionComparison(preflight, answers, metadata)

            assertEquals(
                personalizedProgramFingerprint(standalone.request, standalone.items),
                personalizedProgramFingerprint(comparison.control.request, comparison.control.items)
            )
            assertEquals(preflight.request.goal, comparison.control.request.goal)
            assertEquals(preflight.request.weeklyTrainingDays, comparison.control.request.weeklyTrainingDays)
            assertEquals(preflight.request.durationWeeks, comparison.control.request.durationWeeks)
            assertNotNull(comparison.targetPlan)
            assertNotNull(comparison.selectionPlan)
            assertNotNull(comparison.controlAudit)
            assertNotNull(comparison.experimentalAudit)
            assertNotNull(comparison.prescriptionRealizationPlan)
            assertTrue(comparison.prescriptionRealizationPlan?.shadowOnly == true)
            assertFalse(comparison.prescriptionRealizationPlan?.mutationAuthority == true)
            assertTrue(comparison.prescriptionRealizationPlan?.resolutions.orEmpty().all { !it.mutationAuthority })
            val resolved = comparison.prescriptionRealizationPlan?.resolutions.orEmpty().firstOrNull {
                it.status == StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED
            }
            if (resolved == null) error(
                "B6 service diagnostics: targets=${comparison.targetPlan?.qualityTargets?.map { it.quality to (it.strategy to (it.evidenceBasis to it.numericAuthority)) }} " +
                    "bands=${comparison.targetPlan?.qualityTargets?.map { it.quality to (it.baselineSource to (it.baselineAvailable to it.hasPersonalDirectBaseline)) }} " +
                    "control=${comparison.control.items.map { it.exerciseStableKey to (it.selectionRole to it.setPrescriptions.map { set -> set.reps to set.weightKg }) }} " +
                    "selected=${comparison.selectionPlan.selectedCandidates.map { it.stableKey to it.selectionRole }} " +
                    "traces=${comparison.selectionPlan.traces.map { it.targetId to (it.controlDirectCapabilityIdentities to it.selectedStableKey) }} " +
                    "resolutions=${comparison.prescriptionRealizationPlan?.resolutions?.map { it.targetId to (it.status to it.reasonCodes) }}"
            )
            val resolution = requireNotNull(resolved)
            val owner = requireNotNull(resolution.owner)
            assertEquals("barbell_back_squat", owner.stableKey)
            assertEquals(
                comparison.control.items.first { it.exerciseStableKey == "barbell_back_squat" }.selectionRole,
                owner.selectionRole
            )
            assertEquals(resolution.currentPrescription?.sets?.size, resolution.proposedPrescription?.sets?.size)
            assertTrue(resolution.proposedPrescription?.sets?.all { it.reps in 1..6 } == true)
            val reference = requireNotNull(resolution.plannedCompatibility?.reference1RmKg)
            assertTrue(resolution.proposedPrescription?.sets?.all { it.weightKg >= reference * .70 } == true)
            assertFalse(resolution.mutationAuthority)
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
        } finally {
            db.close()
        }
    }

    private fun field(target: Any, name: String): Any =
        requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))
}
