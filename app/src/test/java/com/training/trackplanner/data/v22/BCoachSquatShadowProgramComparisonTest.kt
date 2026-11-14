package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachSquatShadowProgramComparisonTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val rpeRir = RpeRirPolicy.fromContext(context)

    private fun item(week: Int, key: String = "barbell_back_squat"): ProgramSkeletonItem {
        val prescriptions = List(3) { i ->
            ProgramSetPrescription(
                setIndex = i + 1,
                reps = 5,
                weightKg = 0.0,
                seconds = 0,
                targetRpeMin = null,
                loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
            )
        }
        return ProgramSkeletonItem(
            localId = "w${week}-${key}",
            weekNumber = week,
            dayOfWeek = 2,
            orderIndex = 1,
            exerciseStableKey = key,
            exerciseName = "스쿼트",
            category = "근력운동",
            restSeconds = 180,
            prescription = "원본 유지",
            setCount = 3,
            reps = 5,
            weightKg = 0.0,
            seconds = 0,
            selectionReason = "GENERATED",
            weightSource = "B12_HISTORY_REFERENCE_NOT_APPROVED_LOAD",
            setPrescriptions = prescriptions
        )
    }

    private fun unavailable() = BCoachSquatShadowPipelineResult.ModelUnavailable(
        BCoachSquatEvidenceResult.Reason.STALE_OR_MISSING_MODEL
    )

    private fun pipeline(): BCoachSquatShadowPipelineResult.ShadowReport {
        val capacity = StrengthPosteriorModel.distribution(
            StrengthPosteriorModel.initialState(registry, null),
            checkNotNull(registry.target(StrengthPerformanceRegistry.BACK_SQUAT))
        )
        val snapshot = BCoachSquatEvidence(
            asOf = day,
            posteriorAsOf = day.minusDays(1),
            posteriorFingerprint = "synthetic",
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            capacity = capacity,
            curve = curves.resolve("barbell_back_squat"),
            curvePosterior = null,
            latestAnchor = BCoachSquatAnchorSession(
                sessionStableKey = "set-session",
                date = day.minusDays(1),
                confirmedSets = (1..3).map { i ->
                    BCoachConfirmedSquatSet(i.toLong(), i, 140.0, 5, if (i == 1) 7.5 else 8.0)
                }
            ),
            recentObservedLoadsKg = listOf(137.0, 140.0)
        )
        return BCoachSquatShadowPipeline.evaluate(
            input = BCoachSquatEvidenceResult.Ready(snapshot),
            rirPolicy = rpeRir,
            smallestPlateOneSideKg = 1.5,
            targetRirMin = 1.0,
            targetRirMax = 3.0,
            policies = listOf(
                BCoachSquatShadowSelector.Weights(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                BCoachSquatShadowSelector.Weights(1.0, 0.5, 0.25, 1.0, 0.5, 0.1)
            ),
            omegaBands = listOf(
                BCoachSquatShadowSelector.OmegaBand.CENTRAL_80,
                BCoachSquatShadowSelector.OmegaBand.CENTRAL_90
            )
        ) as BCoachSquatShadowPipelineResult.ShadowReport
    }

    @Test
    fun `every generated squat week remains visible and user-calibration loads remain unresolved`() {
        val original = (1..4).map(::item)
        val before = original.map { it.setPrescriptions.map(ProgramSetPrescription::weightKg) }
        val result = BCoachSquatShadowProgramComparator.compare(day, original, pipeline())
        assertEquals(4, result.plannedSquatRows.size)
        assertEquals(listOf(1, 2, 3, 4), result.plannedSquatRows.map { it.week })
        assertTrue(result.plannedSquatRows.all { it.loadKg == null })
        assertEquals(before, original.map { it.setPrescriptions.map(ProgramSetPrescription::weightKg) })
        assertTrue(result.notices.any { it.contains("저장하지") })
        assertTrue(result.experimentCandidates.isNotEmpty())
        assertEquals(4, result.experimentCandidates.size)
    }

    @Test
    fun `unavailable posterior still preserves baseline and does not invent weight`() {
        val result = BCoachSquatShadowProgramComparator.compare(day, listOf(item(1)), unavailable())
        assertEquals("MODEL_UNAVAILABLE", result.status)
        assertEquals(1, result.plannedSquatRows.size)
        assertNull(result.plannedSquatRows.single().loadKg)
        assertTrue(result.experimentCandidates.isEmpty())
        assertTrue(result.experiments.isEmpty())
    }

    @Test
    fun `no squat in generated plan is not automatically inserted by the shadow engine`() {
        val base = listOf(item(1, "barbell_deadlift"))
        val result = BCoachSquatShadowProgramComparator.compare(day, base, pipeline())
        assertEquals("NO_SQUAT_IN_GENERATED_PROGRAM", result.status)
        assertTrue(result.plannedSquatRows.isEmpty())
        assertTrue(result.experimentCandidates.isEmpty())
        assertEquals("barbell_deadlift", base.single().exerciseStableKey)
    }

    @Test
    fun `shadow selection reports all tested coefficient policies without creating weekly prescription`() {
        val result = BCoachSquatShadowProgramComparator.compare(day, (1..4).map(::item), pipeline())
        assertEquals(4, result.experiments.size)
        assertTrue(result.experiments.all { it.coefficientPolicy.startsWith("EXPERIMENT_") })
        assertTrue(result.experimentCandidates.any { it.action == "HOLD" && it.loadKg == 140.0 })
        assertTrue(result.experimentCandidates.any { it.action == "LOAD_UP_ONE_STEP" && it.loadKg == 143.0 })
        assertEquals(4, result.plannedSquatRows.size)
        assertTrue(result.notices.any { it.contains("각 주차의 중량 처방") })
    }

    @Test
    fun `ephemeral shadow plate input does not silently change the generator request contract`() {
        val defaultRequest = newBBalancedGenerationRequest(
            name = "shadow test", days = 4, asOf = day, sessionMinutes = 60,
            allowFourthDay = false, importance = BBalancedGoalImportance(),
            blockProgram = BBlockProgramDefinition()
        )
        assertNull(defaultRequest.bShadowPlateOneSideKg)
        val shadowRequest = newBBalancedGenerationRequest(
            name = "shadow test", days = 4, asOf = day, sessionMinutes = 60,
            allowFourthDay = false, importance = BBalancedGoalImportance(),
            blockProgram = BBlockProgramDefinition(), shadowPlateOneSideKg = 1.5
        )
        assertEquals(1.5, shadowRequest.bShadowPlateOneSideKg!!, 0.0)
        assertEquals(defaultRequest.days, shadowRequest.days)
        assertEquals(defaultRequest.blockProgram, shadowRequest.blockProgram)
    }
}
