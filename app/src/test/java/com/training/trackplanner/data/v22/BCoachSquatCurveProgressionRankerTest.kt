package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachSquatCurveProgressionRankerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val policy = RpeRirPolicy.fromContext(context)
    private val date = LocalDate.of(2026, 11, 12)

    private fun pipeline(
        rpe: Double? = 7.5,
        actualLaterRpe: Double? = 8.0,
        observedLoads: List<Double> = listOf(134.0, 137.0, 140.0),
        actualLoads: List<Double> = listOf(140.0, 140.0, 140.0),
        actualReps: List<Int> = listOf(5, 5, 5)
    ): BCoachSquatShadowPipelineResult.ShadowReport {
        val capacity = StrengthPosteriorModel.distribution(
            StrengthPosteriorModel.initialState(registry, null),
            checkNotNull(registry.target(StrengthPerformanceRegistry.BACK_SQUAT))
        )
        val setRecords = actualLoads.mapIndexed { i, load ->
            BCoachConfirmedSquatSet(i.toLong() + 1, i + 1,
                load, actualReps[i], if (i == 0) rpe else actualLaterRpe)
        }
        val evidence = BCoachSquatEvidence(
            asOf = date,
            posteriorAsOf = date.minusDays(1),
            posteriorFingerprint = "synthetic-records",
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            capacity = capacity,
            curve = curves.resolve("barbell_back_squat"),
            curvePosterior = null,
            latestAnchor = BCoachSquatAnchorSession(
                "observed-not-planned", date.minusDays(1), setRecords
            ),
            recentObservedLoadsKg = observedLoads
        )
        val input = BCoachSquatEvidenceResult.Ready(evidence)
        val result = BCoachSquatShadowPipeline.evaluate(
            input = input, rirPolicy = policy,
            smallestPlateOneSideKg = 1.5,
            targetRirMin = 1.0, targetRirMax = 3.0,
            policies = listOf(BCoachSquatShadowSelector.Weights(
                0.0, 0.0, 0.0, 1.0, 1.0, 1.0
            )),
            omegaBands = listOf(BCoachSquatShadowSelector.OmegaBand.CENTRAL_80)
        )
        return result as BCoachSquatShadowPipelineResult.ShadowReport
    }

    private fun rank(
        input: BCoachSquatShadowPipelineResult.ShadowReport = pipeline(),
        targetSets: Int? = 3
    ) = BCoachSquatCurveProgressionRanker.evaluate(input, 1.0, 3.0, targetSets)

    @Test
    fun `one deterministic rule compares hold load reps and added set together`() {
        val result = rank()
        assertEquals("EXPERIMENTAL_RANKING_ONLY", result.status)
        assertEquals(setOf("HOLD", "LOAD_UP_ONE_STEP", "REPS_UP_ONE", "SETS_UP_ONE"),
            result.ranked.map { it.action }.toSet())
        assertEquals(4, result.ranked.size)
        assertNotNull(result.selectedAction)
        assertTrue(result.ranked.any { it.action == result.selectedAction })
        assertTrue(result.ranked.any { it.eligible && it.action == "LOAD_UP_ONE_STEP" })
        assertTrue(result.ranked.any { it.eligible && it.action == "REPS_UP_ONE" })
        assertFalse(result.ranked.single { it.action == "SETS_UP_ONE" }.eligible)
        assertTrue(result.notes.any { it.contains("SHADOW_ONLY") })
    }

    @Test
    fun `repetition e1rm required at fixed target rir equals anchor curve for same reps-at-limit`() {
        val p = pipeline()
        val result = rank(p)
        val anchor = checkNotNull(p.relative.anchorSet)
        val q = p.input.curve
        val historicalAtRir3 = anchor.loadKg /
            checkNotNull(q.evaluate(anchor.repetitions + 3.0).relativeLoad)
        val neededForReps = result.ranked.single { it.action == "REPS_UP_ONE" }.requiredE1rmKg
        assertEquals(historicalAtRir3, checkNotNull(neededForReps), 1e-8)
        assertTrue(result.historicalE1rmKg!! > 0.0)
        assertTrue(result.ranked.filter { it.eligible }.all {
            it.worstCaseRegret != null && it.worstCaseRegret!! >= 0.0 &&
                it.historicalE1rmLogGap != null
        })
    }

    @Test
    fun `set increase has same first set e1rm but needs an actual dose shortfall`() {
        val withoutNeed = rank(targetSets = 3)
        val withNeed = rank(targetSets = 4)
        val hold = withNeed.ranked.single { it.action == "HOLD" }
        val extra = withNeed.ranked.single { it.action == "SETS_UP_ONE" }
        assertEquals(checkNotNull(hold.requiredE1rmKg),
            checkNotNull(extra.requiredE1rmKg), 1e-9)
        assertFalse(withoutNeed.ranked.single { it.action == "SETS_UP_ONE" }.eligible)
        assertTrue(extra.eligible)
        assertTrue(extra.volumeLoadChange > 0.0)
        assertTrue(withNeed.notes.any { it.contains("structure") })
    }

    @Test
    fun `unknown set target never rewards extra sets`() {
        val x = rank(targetSets = null)
        val row = x.ranked.single { it.action == "SETS_UP_ONE" }
        assertFalse(row.eligible)
        assertEquals("NO_SET_DOSE_SHORTFALL_EVIDENCE", row.reason)
    }

    @Test
    fun `equipment grid mismatch excludes load candidate but retains recorded levels`() {
        val report = rank(pipeline(observedLoads = listOf(137.5, 140.0, 142.5)))
        assertEquals("GRID_CONFLICT_SHADOW_REVIEW", report.status)
        val load = report.ranked.single { it.action == "LOAD_UP_ONE_STEP" }
        assertFalse(load.eligible)
        assertEquals("EQUIPMENT_INCREMENT_REVIEW", load.reason)
        assertTrue(report.ranked.any { it.action == "HOLD" && it.eligible })
    }

    @Test
    fun `hardest confirmed work set RPE is the curve anchor, not the first optimistic set`() {
        val p = pipeline(rpe = 7.5, actualLaterRpe = 8.0)
        assertEquals(8.0, checkNotNull(p.relative.anchorSet).observedRpe!!, 0.0)
        val hold = p.relative.candidates.single {
            it.action == BCoachSquatRelativeShadow.Action.HOLD
        }
        assertEquals(policy.resolve(8.0)!!.expectedRir,
            checkNotNull(hold.expectedRirGivenSupport), 1e-8)
    }

    @Test
    fun `user confirmed 0_75 per side allows 1_5 increment even if old gaps were 2_5`() {
        val p = pipeline(observedLoads=listOf(137.5, 140.0, 142.5))
        val x = BCoachSquatCurveProgressionRanker.evaluate(
            input=p, targetRirMin=1.0, targetRirMax=3.0,
            doseTargetSets=3, userConfirmedEquipmentStep=true
        )
        assertEquals("CONFIRMED_GRID_HISTORICAL_GAPS_DIFFER", x.status)
        val increased=x.ranked.single { it.action=="LOAD_UP_ONE_STEP" }
        assertEquals(143.0, increased.loadKg, 0.0)
        assertTrue(increased.eligible)
        assertTrue(x.notes.any { it.contains("Historical load gaps differ") })
    }

    @Test
    fun `missing RPE does not fabricate reference e1rm or automatic winner`() {
        val result = rank(pipeline(rpe = null, actualLaterRpe = null))
        assertEquals("INSUFFICIENT_CANONICAL_RPE_EVIDENCE", result.status)
        assertNull(result.selectedAction)
        assertNull(result.historicalE1rmKg)
        assertTrue(result.ranked.none { it.eligible })
    }

    @Test
    fun `high RPE followup excludes every progression action without suppressing the hold`() {
        val result = rank(pipeline(rpe = 7.5, actualLaterRpe = 9.5))
        assertEquals("HOLD_ONLY_NO_VERIFIED_RPE_HEADROOM", result.status)
        assertEquals("HOLD", result.selectedAction)
        assertEquals(1, result.ranked.count { it.eligible })
        assertTrue(result.ranked.filter { it.action != "HOLD" }
            .all { it.reason == "NO_CONFIRMED_ALL_SET_RPE_HEADROOM" })
    }

    @Test
    fun `different loads in last session stop unverified advancement`() {
        val result = rank(pipeline(actualLoads = listOf(140.0, 137.0, 140.0)))
        assertEquals("HOLD_ONLY_NO_VERIFIED_RPE_HEADROOM", result.status)
        assertEquals("HOLD", result.selectedAction)
    }

    @Test
    fun `ranking remains reproducible and never edits the observed session`() {
        val p = pipeline()
        val before = checkNotNull(p.input.latestAnchor).confirmedSets.toList()
        val a = rank(p)
        val b = rank(p)
        assertEquals(a, b)
        assertEquals(before, p.input.latestAnchor!!.confirmedSets)
        assertEquals(date.minusDays(1), a.referenceDate)
    }
}
