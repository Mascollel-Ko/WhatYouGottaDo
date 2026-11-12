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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachSquatShadowSelectorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val rirPolicy = RpeRirPolicy.fromContext(context)
    private val date = LocalDate.of(2026, 11, 12)

    private fun gateB(
        load: Double = 140.0,
        reps: Int = 5,
        rpe: Double? = 7.5,
        observed: List<Double> = listOf(137.0, 140.0),
        sets: Int = 3
    ): BCoachSquatRelativeShadow.Result {
        val capacity = StrengthPosteriorModel.distribution(
            StrengthPosteriorModel.initialState(registry, null),
            checkNotNull(registry.target(StrengthPerformanceRegistry.BACK_SQUAT))
        )
        val evidence = BCoachSquatEvidence(
            asOf = date,
            posteriorAsOf = date.minusDays(1),
            posteriorFingerprint = "test-state",
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            capacity = capacity,
            curve = curves.resolve("barbell_back_squat"),
            curvePosterior = null,
            latestAnchor = BCoachSquatAnchorSession(
                sessionStableKey = "session",
                date = date.minusDays(1),
                confirmedSets = (1..sets).map { i ->
                    BCoachConfirmedSquatSet(i.toLong(), i, load, reps, rpe)
                }
            ),
            recentObservedLoadsKg = observed
        )
        return BCoachSquatRelativeShadow.evaluate(
            evidence, rirPolicy,
            smallestPlateOneSideKg = 1.5,
            targetRirMin = 1.0,
            targetRirMax = 3.0
        )
    }

    private val holdBiased = BCoachSquatShadowSelector.Weights(
        rewardPerLoadStep = 0.0,
        rewardPerAddedRep = 0.0,
        rewardPerAddedSet = 0.0,
        effortDeviationPenalty = 10.0,
        rampPenalty = 100.0,
        changePenalty = 100.0
    )
    private val advanceBiased = BCoachSquatShadowSelector.Weights(
        rewardPerLoadStep = 50.0,
        rewardPerAddedRep = 0.0,
        rewardPerAddedSet = 0.0,
        effortDeviationPenalty = 0.01,
        rampPenalty = 0.0,
        changePenalty = 0.0
    )

    private fun compare(
        input: BCoachSquatRelativeShadow.Result = gateB(),
        weights: List<BCoachSquatShadowSelector.Weights> = listOf(holdBiased, advanceBiased),
        bands: List<BCoachSquatShadowSelector.OmegaBand> = listOf(
            BCoachSquatShadowSelector.OmegaBand.CENTRAL_80,
            BCoachSquatShadowSelector.OmegaBand.CENTRAL_90
        )
    ) = BCoachSquatShadowSelector.compare(input, weights, bands, 1.0, 3.0)

    @Test
    fun `real progression reward exposes reversal versus expensive change policies`() {
        val decision = compare()
        assertEquals(BCoachSquatShadowSelector.DecisionState.WEIGHT_OR_OMEGA_SENSITIVE, decision.state)
        assertTrue(decision.sensitivityWinnerCount > 1)
        val hold = decision.results.filter { it.weightsIndex == 0 }.map { it.winner }.distinct()
        val advance = decision.results.filter { it.weightsIndex == 1 }.map { it.winner }.distinct()
        assertEquals(listOf(BCoachSquatRelativeShadow.Action.HOLD), hold)
        assertFalse(advance.contains(BCoachSquatRelativeShadow.Action.HOLD))
        assertNull(decision.experimentalWinner)
        assertTrue(decision.warnings.any { it.contains("WINNER_REVERSAL") })
    }

    @Test
    fun `omega scenario tails are explicitly the discrete anchor RIR quantiles`() {
        val decision = compare(weights = listOf(holdBiased))
        val lower = decision.results.single {
            it.omegaBand == BCoachSquatShadowSelector.OmegaBand.CENTRAL_80
        }.anchorRirScenarios
        val wider = decision.results.single {
            it.omegaBand == BCoachSquatShadowSelector.OmegaBand.CENTRAL_90
        }.anchorRirScenarios
        assertEquals(listOf(2, 3), lower)
        assertEquals(listOf(2, 3, 4), wider)
    }

    @Test
    fun `grid conflict prevents an apparent stable policy from becoming an automatic decision`() {
        val result = compare(
            input = gateB(observed = listOf(137.5, 140.0, 142.5)),
            weights = listOf(holdBiased)
        )
        assertEquals(BCoachSquatShadowSelector.DecisionState.GRID_CONFLICT_REVIEW, result.state)
        assertTrue(result.warnings.any { it.contains("GRID_REVIEW") })
        assertTrue(result.results.isNotEmpty())
    }

    @Test
    fun `only predeclared benchmark actions enter the opportunity regret baseline`() {
        val original = gateB()
        val expanded = original.copy(
            candidates = original.candidates + original.candidates.first().copy(
                action = BCoachSquatRelativeShadow.Action.SETS_UP_ONE,
                totalLoadKg = 900.0, // impossible new search proposal must not move reference best
                nominalVolumeLoadDelta = 100.0
            )
        )
        val a = compare(input = original, weights = listOf(holdBiased))
        val b = compare(input = expanded, weights = listOf(holdBiased))
        assertEquals(a.results.map { it.winner }, b.results.map { it.winner })
        a.results.zip(b.results).forEach { (x, y) ->
            assertEquals(x.benchmarkActions, y.benchmarkActions)
            assertEquals(x.candidates, y.candidates)
        }
        assertTrue(b.excludedActions.contains(BCoachSquatRelativeShadow.Action.SETS_UP_ONE))
    }

    @Test
    fun `hold costs and scenario regrets are separately exposed instead of conflated`() {
        val result = compare(weights = listOf(holdBiased))
        result.results.forEach { run ->
            val hold = run.candidates.single { it.action == BCoachSquatRelativeShadow.Action.HOLD }
            assertEquals(0.0, hold.worstHoldRelativeCost, 1e-9)
            assertEquals(0.0, hold.avgHoldRelativeCost, 1e-9)
            assertTrue(hold.worstOpportunityRegret >= 0.0)
            assertTrue(run.candidates.all { it.worstOpportunityRegret >= 0.0 })
        }
    }

    @Test
    fun `unavailable anchor RPE and incomplete benchmarks cannot silently choose`() {
        val missing = compare(input = gateB(rpe = null))
        assertEquals(BCoachSquatShadowSelector.DecisionState.NO_COMPARABLE_EVIDENCE, missing.state)
        assertTrue(missing.results.isEmpty())
        assertNull(missing.experimentalWinner)
        val incomplete = compare(input = gateB(reps = 1, rpe = 10.0, sets = 1))
        assertEquals(BCoachSquatShadowSelector.DecisionState.INCOMPLETE_BENCHMARK, incomplete.state)
    }

    @Test
    fun `a stable shadow comparison is explicitly not an approved load prescription`() {
        val result = compare(weights = listOf(holdBiased))
        assertEquals(BCoachSquatShadowSelector.DecisionState.STABLE_SHADOW_ONLY, result.state)
        assertEquals(BCoachSquatRelativeShadow.Action.HOLD, result.experimentalWinner)
        assertTrue(result.warnings.any { it.startsWith("SHADOW_ONLY") })
        assertTrue(result.warnings.any { it.contains("Later working-set") })
    }

    @Test
    fun `the full discrete RIR mixture is available without hiding tail states`() {
        val result = compare(
            weights = listOf(holdBiased),
            bands = listOf(BCoachSquatShadowSelector.OmegaBand.FULL_RIR_SUPPORT)
        )
        assertEquals(listOf(2, 3, 4), result.results.single().anchorRirScenarios)
    }

    @Test
    fun `a new set does not earn tested RIR confidence from the first set`() {
        val initial = gateB()
        val addedSet = initial.candidates.single {
            it.action == BCoachSquatRelativeShadow.Action.SETS_UP_ONE
        }
        assertEquals(3, addedSet.followUpSetsNotPredicted)
        assertFalse(BCoachSquatShadowSelector.frozenBenchmark.contains(addedSet.action))
        val result = compare(input = initial, weights = listOf(advanceBiased))
        assertTrue(result.excludedActions.contains(BCoachSquatRelativeShadow.Action.SETS_UP_ONE))
    }
}
