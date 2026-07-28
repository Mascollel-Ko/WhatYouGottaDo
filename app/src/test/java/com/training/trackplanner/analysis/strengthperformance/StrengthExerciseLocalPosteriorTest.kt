package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
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
class StrengthExerciseLocalPosteriorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val rirPolicy = RpeRirPolicy.fromContext(context)
    private val exercise = Exercise(
        name = "Incline dumbbell press",
        category = "Strength",
        stableKey = "ex_a61f1e96",
        movementPattern = "HORIZONTAL_PUSH",
        estimated1RmEligible = true
    )

    @Test
    fun `first proper local session establishes a baseline without proxy transfer`() {
        val result = StrengthExercisePosteriorEngine.update(
            eventUuid = "event-1",
            observation = observation("2026-06-01", 52.0, 8, 10.0),
            currentState = null
        )

        assertNotNull(result.state)
        assertFalse(checkNotNull(result.history).baselineEstablishedBefore)
        assertTrue(result.history.baselineEstablishedAfter)
        assertFalse(result.history.proxyTransferEligible)
        assertNull(result.history.innovationResidualLog)
        assertEquals(0.0, result.history.posteriorMeanIncrementLog, 0.0)
    }

    @Test
    fun `second proper session separates innovation from posterior increment`() {
        val first = checkNotNull(
            StrengthExercisePosteriorEngine.update(
                "event-1",
                observation("2026-06-01", 52.0, 8, 10.0),
                null
            ).state
        )
        val second = StrengthExercisePosteriorEngine.update(
            "event-2",
            observation("2026-06-15", 52.0, 8, 7.0),
            first
        )
        val history = checkNotNull(second.history)

        assertTrue(history.proxyTransferEligible)
        assertNotNull(history.innovationResidualLog)
        assertNotNull(history.innovationVariance)
        assertTrue(checkNotNull(history.innovationResidualLog) > 0.0)
        assertTrue(history.posteriorMeanIncrementLog > 0.0)
        assertTrue(history.posteriorMeanIncrementLog < checkNotNull(history.innovationResidualLog))
        assertEquals(14L, history.transitionDays)
    }

    @Test
    fun `same innovation with different prior variance produces different posterior increments`() {
        val date = LocalDate.parse("2026-06-15")
        val mean = kotlin.math.ln(60.0)
        fun state(variance: Double) = StrengthExerciseLocalState(
            exerciseStableKey = exercise.stableKey,
            logMean = mean,
            logVariance = variance,
            lastProcessedEventUuid = "prior",
            lastProcessedSessionKey = "date:2026-06-01",
            lastProcessedDate = LocalDate.parse("2026-06-01"),
            baselineEstablished = true,
            observationCount = 1,
            twoSidedObservationCount = 1
        )
        val source = observation(date.toString(), 52.0, 8, 7.0)
        val narrow = checkNotNull(StrengthExercisePosteriorEngine.update("narrow", source, state(0.01)).history)
        val wide = checkNotNull(StrengthExercisePosteriorEngine.update("wide", source, state(0.20)).history)

        assertEquals(narrow.innovationResidualLog!!, wide.innovationResidualLog!!, 1e-12)
        assertTrue(wide.posteriorMeanIncrementLog > narrow.posteriorMeanIncrementLog)
    }

    @Test
    fun `censored-only local session updates an established state but creates no proxy signal`() {
        val first = checkNotNull(
            StrengthExercisePosteriorEngine.update(
                "event-1",
                observation("2026-06-01", 52.0, 8, 10.0),
                null
            ).state
        )
        val censored = StrengthExercisePosteriorEngine.update(
            "event-2",
            observation("2026-06-15", 52.0, 8, null),
            first
        )
        val history = checkNotNull(censored.history)

        assertFalse(history.sessionLikelihoodProper)
        assertFalse(history.proxyTransferEligible)
        assertNull(history.innovationResidualLog)
        assertNotNull(censored.state)
    }

    private fun observation(
        date: String,
        weight: Double,
        reps: Int,
        rpe: Double?
    ): StrengthExerciseSessionObservation = checkNotNull(
        StrengthSessionObservationBuilder.build(
            record = WorkoutEntryWithSets(
                entry = WorkoutEntry(
                    id = date.hashCode().toLong(),
                    date = date,
                    exerciseStableKey = exercise.stableKey,
                    exerciseName = exercise.name,
                    category = exercise.category
                ),
                sets = listOf(
                    WorkoutSet(
                        id = date.hashCode().toLong(),
                        entryId = date.hashCode().toLong(),
                        setIndex = 0,
                        reps = reps,
                        weightKg = weight,
                        confirmed = true,
                        rpe = rpe
                    )
                )
            ),
            exercise = exercise,
            registry = registry,
            curveRegistry = curves,
            loadResolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null),
            rirPolicy = rirPolicy
        )
    )
}
