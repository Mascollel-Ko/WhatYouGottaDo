package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachCommonStrengthRirCalculatorTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val curve = RepetitionCurveRegistry.fromContext(ctx)
    private val policy = RpeRirPolicy.fromContext(ctx)

    @Test fun easyCompletedDumbbellMainUsesCanonicalCurveAndOneUnitStep() {
        val answer = checkNotNull(BCoachCommonStrengthRirCalculator.choose(
            performedKg = 60.0, performedReps = 6, reportedMaxRpe = 6.5,
            nextReps = 6, incrementKg = 1.0,
            curve = curve.resolve("dumbbell_user_selected_main"), rirPolicy = policy
        ))
        assertEquals("LOAD_UP_ONE_STEP", answer.action)
        assertEquals(61.0, answer.loadKg, 1e-9)
        assertTrue(answer.observedE1rmKg!! > 60.0)
        assertTrue(answer.requiredE1rmKg!! > 60.0)
        assertTrue(answer.lowRirMass!! <= 0.10)
    }

    @Test fun RpeEightDoesNotBlindlyIncrement() {
        val answer = checkNotNull(BCoachCommonStrengthRirCalculator.choose(
            60.0, 6, 8.0, 6, 1.0,
            curve.resolve("dumbbell_user_selected_main"), policy
        ))
        assertEquals("HOLD", answer.action)
        assertEquals(60.0, answer.loadKg, 1e-9)
    }

    @Test fun canonicalBenchAndDeadliftUseAssignedProfilesRatherThanSharedFakeCurve() {
        for(key in listOf("barbell_bench_press", "barbell_deadlift")) {
            val answer = checkNotNull(BCoachCommonStrengthRirCalculator.choose(
                130.0, 6, 6.5, 6, 1.5, curve.resolve(key), policy
            ))
            assertEquals("LOAD_UP_ONE_STEP",answer.action)
            assertEquals(131.5,answer.loadKg,1e-9)
        }
        assertNotEquals(curve.resolve("barbell_bench_press").profile.id,
            curve.resolve("barbell_deadlift").profile.id)
    }

    @Test fun largerStepDoesNotInventSmallerPlates() {
        val answer = checkNotNull(BCoachCommonStrengthRirCalculator.choose(
            30.0, 6, 6.5, 6, 5.0,
            curve.resolve("custom_dumbbell"), policy
        ))
        assertEquals("HOLD",answer.action)
        assertEquals(30.0,answer.loadKg,0.0)
    }

    @Test fun differentRepPurposeRemainsHumanReviewNotFlattened() {
        val answer = checkNotNull(BCoachCommonStrengthRirCalculator.choose(
            60.0, 6, 6.5, 10, 1.0,
            curve.resolve("custom_dumbbell"), policy
        ))
        assertEquals("REP_SCHEME_REVIEW",answer.action)
    }

    @Test fun outsideCurveOrMissingRpeCannotGenerateFakeE1rm() {
        assertNull(BCoachCommonStrengthRirCalculator.choose(
            60.0, 20, 6.5, 20, 1.0, curve.resolve("custom"), policy
        ))
        assertNull(BCoachCommonStrengthRirCalculator.choose(
            60.0, 6, 5.0, 6, 1.0, curve.resolve("custom"), policy
        ))
    }
}
