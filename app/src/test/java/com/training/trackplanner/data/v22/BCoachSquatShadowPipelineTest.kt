package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.StrengthModelRevisionPolicy
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachSquatShadowPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val policy = RpeRirPolicy.fromContext(context)
    private val asOf = LocalDate.of(2026, 11, 12)
    private val weights = listOf(
        BCoachSquatShadowSelector.Weights(0.0, 0.0, 0.0, 10.0, 100.0, 100.0),
        BCoachSquatShadowSelector.Weights(50.0, 0.0, 0.0, 0.01, 0.0, 0.0)
    )

    @Test
    fun `confirmed workout plus persisted canonical model reaches Gate C without producing a program`() {
        val revision = StrengthModelRevisionPolicy.current(0L, null)
            .copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE)
        val model = StrengthPosteriorModel.toEntity(
            StrengthPosteriorModel.initialState(registry, null)
                .copyDeep(lastProcessedDate = asOf.minusDays(1)),
            now = 1L,
            modelInstanceKey = StrengthModelRevisionPolicy.modelInstanceKey(
                StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
            )
        )
        val record = WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = 1, date = asOf.minusDays(1).toString(),
                exerciseStableKey = "barbell_back_squat",
                exerciseName = "스쿼트", category = "근력운동",
                sessionStableKey = "test-session"
            ),
            sets = (1..3).map { i ->
                WorkoutSet(id = i.toLong(), entryId = 1, setIndex = i,
                    reps = 5, weightKg = 140.0,
                    rpe = if (i == 1) 7.5 else 8.0, confirmed = true)
            }
        )
        val persisted = BCoachSquatEvidenceAdapter.fromPersisted(
            asOf = asOf, revision = revision, model = model,
            exerciseCurve = null, sharedCurve = null,
            records = listOf(record), registry = registry, curves = curves
        )
        val report = BCoachSquatShadowPipeline.evaluate(
            input = persisted, rirPolicy = policy,
            smallestPlateOneSideKg = 1.5,
            targetRirMin = 1.0, targetRirMax = 3.0,
            policies = weights,
            omegaBands = listOf(
                BCoachSquatShadowSelector.OmegaBand.CENTRAL_80,
                BCoachSquatShadowSelector.OmegaBand.CENTRAL_90
            )
        ) as BCoachSquatShadowPipelineResult.ShadowReport
        assertEquals(3, report.input.latestAnchor!!.confirmedSets.size)
        assertEquals(140.0, report.relative.anchorSet!!.loadKg, 0.0)
        assertTrue(report.relative.candidates.isNotEmpty())
        assertTrue(report.selection.results.isNotEmpty())
        assertTrue(report.selection.warnings.any { it.startsWith("SHADOW_ONLY") })
        assertNotNull(report.input.posteriorFingerprint)
    }

    @Test
    fun `unavailable model fails closed through the entire pipeline`() {
        val output = BCoachSquatShadowPipeline.evaluate(
            input = BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.FUTURE_POSTERIOR_LEAKAGE
            ),
            rirPolicy = policy,
            smallestPlateOneSideKg = 1.5,
            targetRirMin = 1.0, targetRirMax = 3.0,
            policies = weights,
            omegaBands = listOf(BCoachSquatShadowSelector.OmegaBand.CENTRAL_80)
        )
        assertEquals(
            BCoachSquatShadowPipelineResult.ModelUnavailable(
                BCoachSquatEvidenceResult.Reason.FUTURE_POSTERIOR_LEAKAGE
            ), output
        )
    }
}
