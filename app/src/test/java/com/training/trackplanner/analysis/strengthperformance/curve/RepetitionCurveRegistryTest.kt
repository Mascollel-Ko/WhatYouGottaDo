package com.training.trackplanner.analysis.strengthperformance.curve

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepetitionCurveRegistryTest {
    private val registry by lazy {
        RepetitionCurveRegistry.fromContext(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `canonical assets are finite monotone and invert deterministically`() {
        registry.profiles().forEach { profile ->
            val values = (1..20).map { reps -> checkNotNull(profile.evaluate(reps.toDouble()).relativeLoad) }
            assertEquals(1.0, values.first(), 0.0)
            assertTrue(values.all { value -> value.isFinite() && value in 0.0..1.0 })
            assertTrue(values.zipWithNext().all { (left, right) -> right <= left })
            for (reps in 1..20) {
                val load = checkNotNull(profile.evaluate(reps.toDouble()).relativeLoad)
                val inverted = checkNotNull(profile.invert(load).repetitions)
                assertEquals(reps.toDouble(), inverted, 1e-8)
            }
            assertEquals(
                RepetitionCurveEvaluationStatus.UNSUPPORTED_REPETITIONS,
                profile.evaluate(21.0).status
            )
        }
    }

    @Test
    fun `asset checksums are stable across Windows line endings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun bytes(name: String): ByteArray = context.assets
            .open("strength_performance/$name")
            .use { input -> input.readBytes() }
        fun windows(bytes: ByteArray): ByteArray = bytes.decodeToString()
            .replace("\r\n", "\n")
            .replace("\n", "\r\n")
            .encodeToByteArray()

        val windowsRegistry = RepetitionCurveRegistry.fromAssets(
            profileBytes = windows(bytes("repetition_curve_profiles_v1.csv")),
            manifestBytes = windows(bytes("repetition_curve_manifest_v1.csv")),
            sourceBytes = windows(bytes("repetition_curve_source_v1.csv")),
            assignmentBytes = windows(bytes("repetition_curve_assignments_v1.csv"))
        )

        assertEquals(registry.profiles(), windowsRegistry.profiles())
    }

    @Test
    fun `bench and leg press profiles remain distinct`() {
        val bench = checkNotNull(registry.profile(RepetitionCurveProfileId("reps_curve.bench_press.v1")))
        val general = checkNotNull(registry.profile(RepetitionCurveRegistry.GENERAL_PROFILE_ID))
        val legPress = checkNotNull(registry.profile(RepetitionCurveProfileId("reps_curve.leg_press.v1")))
        assertNotEquals(bench.evaluate(8.0).relativeLoad, legPress.evaluate(8.0).relativeLoad)
        assertEquals(0.682637196729, bench.evaluate(15.0).relativeLoad!!, 1e-12)
        assertEquals(0.696186722743, general.evaluate(15.0).relativeLoad!!, 1e-12)
        assertEquals(0.764581003307, legPress.evaluate(15.0).relativeLoad!!, 1e-12)
        assertTrue(registry.profiles().all { it.provenance.supportedRepRange == 1..20 })
    }

    @Test
    fun `reviewed curve assignments do not infer from display names`() {
        assertEquals(CurveMatchLevel.EXACT_EXERCISE, registry.resolve("barbell_bench_press").matchLevel)
        assertEquals(
            CurveMatchLevel.BORROWED_WITH_UNCERTAINTY,
            registry.resolve("ex_27b3deb5").matchLevel
        )
        assertTrue(registry.resolve("ex_27b3deb5").varianceMultiplier > 1.0)
        assertEquals(
            RepetitionCurveRegistry.GENERAL_PROFILE_ID,
            registry.resolve("ex_32219f7a").profile.id
        )
        assertEquals(
            RepetitionCurveProfileId("reps_curve.leg_press.v1"),
            registry.resolve("ex_ab468462").profile.id
        )
        assertEquals(
            RepetitionCurveRegistry.GENERAL_PROFILE_ID,
            registry.resolve("barbell_back_squat").profile.id
        )
        val unknown = registry.resolve("custom_named_bench_press", isCustom = true)
        assertEquals(CurveMatchLevel.GENERAL_FALLBACK, unknown.matchLevel)
        assertTrue(unknown.varianceMultiplier >= 1.8)
    }

    @Test
    fun `shape preserving interpolation does not overshoot neighboring knots`() {
        registry.profiles().forEach { profile ->
            for (reps in 1 until 20) {
                val left = checkNotNull(profile.evaluate(reps.toDouble()).relativeLoad)
                val right = checkNotNull(profile.evaluate(reps + 1.0).relativeLoad)
                for (step in 1..9) {
                    val interpolated = checkNotNull(profile.evaluate(reps + step / 10.0).relativeLoad)
                    assertTrue(interpolated <= left + 1e-12)
                    assertTrue(interpolated >= right - 1e-12)
                }
            }
        }
    }
}
