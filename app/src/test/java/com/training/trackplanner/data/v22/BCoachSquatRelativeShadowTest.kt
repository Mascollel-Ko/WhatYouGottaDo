package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.StrengthTargetDistribution
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
class BCoachSquatRelativeShadowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curveRegistry = RepetitionCurveRegistry.fromContext(context)
    private val policy = RpeRirPolicy.fromContext(context)
    private val asOf = LocalDate.of(2026, 11, 12)

    private fun evidence(
        load: Double = 140.0,
        reps: Int = 5,
        rpe: Double? = 7.5,
        sets: Int = 3,
        historyLoads: List<Double> = listOf(137.5, 140.0, 142.5, 145.0),
        high80Override: Double? = null
    ): BCoachSquatEvidence {
        val capacity = StrengthPosteriorModel.distribution(
            StrengthPosteriorModel.initialState(registry, null),
            checkNotNull(registry.target(StrengthPerformanceRegistry.BACK_SQUAT))
        )
        return BCoachSquatEvidence(
            asOf = asOf,
            posteriorAsOf = asOf.minusDays(1),
            posteriorFingerprint = "synthetic-state",
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            capacity = if (high80Override == null) capacity else capacity.copy(high80 = high80Override),
            curve = curveRegistry.resolve("barbell_back_squat"),
            curvePosterior = null,
            latestAnchor = BCoachSquatAnchorSession(
                sessionStableKey = "real-observation",
                date = asOf.minusDays(1),
                confirmedSets = (1..sets).map { index ->
                    BCoachConfirmedSquatSet(
                        setId = index.toLong(),
                        setIndex = index,
                        loadKg = load,
                        repetitions = reps,
                        observedRpe = rpe
                    )
                }
            ),
            recentObservedLoadsKg = historyLoads
        )
    }

    private fun evaluate(
        data: BCoachSquatEvidence = evidence(),
        plateOneSide: Double = 1.5
    ) = BCoachSquatRelativeShadow.evaluate(
        evidence = data,
        rirPolicy = policy,
        smallestPlateOneSideKg = plateOneSide,
        targetRirMin = 1.0,
        targetRirMax = 3.0
    )

    private fun candidate(
        result: BCoachSquatRelativeShadow.Result,
        action: BCoachSquatRelativeShadow.Action
    ) = result.candidates.single { it.action == action }

    @Test
    fun `hold preserves the actual anchor RIR mixture without absolute e1RM inversion`() {
        val result = evaluate()
        val hold = candidate(result, BCoachSquatRelativeShadow.Action.HOLD)
        assertEquals(BCoachSquatRelativeShadow.ResultStatus.READY, result.status)
        assertEquals(140.0, hold.totalLoadKg, 0.0)
        assertEquals(5, hold.repetitions)
        assertEquals(3, hold.plannedSetCount)
        val distribution = checkNotNull(policy.resolve(7.5))
        assertEquals(distribution.expectedRir, checkNotNull(hold.expectedRirGivenSupport), 1e-7)
        hold.scenarios.forEach { scenario ->
            assertEquals(scenario.anchorRir.toDouble(), checkNotNull(scenario.projectedRir), 1e-7)
        }
        assertEquals(1.0, hold.supportedMixtureMass, 1e-9)
        assertEquals(0, hold.changedLeverCount)
    }

    @Test
    fun `one plate each side makes three kg increments but observed two point five remains a conflict`() {
        val result = evaluate()
        assertEquals(3.0, result.grid.configuredTotalStepKg, 1e-9)
        assertEquals(BCoachSquatRelativeShadow.GridStatus.CONFLICT_REVIEW_REQUIRED, result.grid.status)
        assertTrue(result.grid.userConfirmationRequired)
        assertEquals(2.5, result.grid.smallestObservedGapKg!!, 1e-9)
        assertEquals(143.0, candidate(result, BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP).totalLoadKg, 0.0)
        assertEquals(146.0, candidate(result, BCoachSquatRelativeShadow.Action.LOAD_UP_TWO_STEPS).totalLoadKg, 0.0)
        assertFalse(result.candidates.any { it.totalLoadKg == 142.5 })
        // Changing user's equipment settings requires explicit action, not a silent fallback.
    }

    @Test
    fun `consistent recorded barbell lattice is not flagged as mismatch`() {
        val result = evaluate(evidence(historyLoads = listOf(134.0, 137.0, 140.0, 143.0)))
        assertEquals(BCoachSquatRelativeShadow.GridStatus.CONSISTENT, result.grid.status)
        assertFalse(result.grid.userConfirmationRequired)
    }

    @Test
    fun `load increase lowers projected RIR in every supported last RIR scenario`() {
        val result = evaluate(evidence(historyLoads = listOf(137.0, 140.0)))
        val hold = candidate(result, BCoachSquatRelativeShadow.Action.HOLD)
        val upOne = candidate(result, BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP)
        val upTwo = candidate(result, BCoachSquatRelativeShadow.Action.LOAD_UP_TWO_STEPS)
        val holdByRir = hold.scenarios.associateBy { it.anchorRir }
        upOne.scenarios.forEach { scenario ->
            if (scenario.projectedRir != null) {
                assertTrue(scenario.projectedRir < checkNotNull(holdByRir[scenario.anchorRir]?.projectedRir))
            }
        }
        assertTrue(upOne.relativeLoadIncrease > 0.0)
        assertTrue(upTwo.relativeLoadIncrease > upOne.relativeLoadIncrease)
    }

    @Test
    fun `one extra repetition reduces projected RIR by exactly one`() {
        val result = evaluate()
        val hold = candidate(result, BCoachSquatRelativeShadow.Action.HOLD)
        val up = candidate(result, BCoachSquatRelativeShadow.Action.REPS_UP_ONE)
        hold.scenarios.zip(up.scenarios).forEach { (a, b) ->
            assertEquals(checkNotNull(a.projectedRir) - 1.0, checkNotNull(b.projectedRir), 1e-7)
        }
        assertEquals(1, up.changedLeverCount)
    }

    @Test
    fun `relative predictions remain invariant when the broad absolute posterior changes`() {
        val first = evaluate(evidence(high80Override = 200.0))
        val second = evaluate(evidence(high80Override = 300.0))
        first.candidates.zip(second.candidates).forEach { (a, b) ->
            assertEquals(a.action, b.action)
            assertEquals(a.targetRirMixtureMass, b.targetRirMixtureMass, 1e-9)
            assertEquals(a.supportedMixtureMass, b.supportedMixtureMass, 1e-9)
        }
    }

    @Test
    fun `multiset scenarios do not invent predictive performance for later sets`() {
        val result = evaluate()
        val hold = candidate(result, BCoachSquatRelativeShadow.Action.HOLD)
        val extra = candidate(result, BCoachSquatRelativeShadow.Action.SETS_UP_ONE)
        assertEquals(2, hold.followUpSetsNotPredicted)
        assertEquals(3, extra.followUpSetsNotPredicted)
        assertEquals(hold.targetRirMixtureMass, extra.targetRirMixtureMass, 1e-9)
        assertEquals(0.0, hold.nominalVolumeLoadDelta, 1e-9)
        assertEquals(1.0 / 3.0, extra.nominalVolumeLoadDelta, 1e-9)
        assertEquals(1, extra.changedLeverCount)
        assertTrue(extra.relativeSetIncrease > 0.0)
        assertTrue(result.notes.any { it.startsWith("FOLLOW_UP_SETS_TARGET_ONLY") })
    }

    @Test
    fun `missing RPE is not converted to zero RIR or a made up certainty`() {
        val result = evaluate(evidence(rpe = null))
        assertEquals(BCoachSquatRelativeShadow.ResultStatus.NO_VALID_ANCHOR_RPE, result.status)
        result.candidates.forEach { item ->
            assertEquals(BCoachSquatRelativeShadow.EvaluationStatus.NO_VALID_ANCHOR_RPE, item.evaluationStatus)
            assertEquals(0.0, item.supportedMixtureMass, 0.0)
            assertNull(item.expectedRirGivenSupport)
            assertTrue(item.scenarios.isEmpty())
        }
    }

    @Test
    fun `load above capacity for an observed one repetition at RPE ten stays outside curve`() {
        val result = evaluate(evidence(load = 190.0, reps = 1, rpe = 10.0, sets = 1))
        val up = candidate(result, BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP)
        assertEquals(BCoachSquatRelativeShadow.EvaluationStatus.OUTSIDE_CURVE_DOMAIN, up.evaluationStatus)
        assertEquals(0.0, up.supportedMixtureMass, 0.0)
        assertNull(up.expectedRirGivenSupport)
        assertEquals(1, up.scenarios.size)
        assertNull(up.scenarios.single().projectedRir)
    }

    @Test
    fun `completed sets are an observed constraint not a guarantee of next day success`() {
        val result = evaluate(evidence(high80Override = 100.0))
        assertTrue(result.observedLowerBoundExceedsPosteriorHigh80)
        assertNotNull(result.lowerBoundCapacityFromAnchorKg)
        assertEquals(BCoachSquatRelativeShadow.ResultStatus.READY, result.status)
        assertTrue(result.notes.any { it.startsWith("OBSERVED_CAPACITY_LOWER_BOUND_EXCEEDS") })
    }

    @Test
    fun `without a confirmed anchor no relative prediction or fabricated candidate is produced`() {
        val data = evidence().copy(latestAnchor = null)
        val result = evaluate(data)
        assertEquals(BCoachSquatRelativeShadow.ResultStatus.NO_CONFIRMED_ANCHOR, result.status)
        assertTrue(result.candidates.isEmpty())
        assertNull(result.anchorSet)
    }

    @Test
    fun `RPE ten hold at one repetition is supported without Epley offset`() {
        val result = evaluate(evidence(reps = 1, load = 140.0, rpe = 10.0, sets = 1))
        val hold = candidate(result, BCoachSquatRelativeShadow.Action.HOLD)
        assertEquals(0.0, checkNotNull(hold.expectedRirGivenSupport), 1e-7)
        assertEquals(1.0, hold.supportedMixtureMass, 1e-9)
    }
}
