package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachGenericMainProgressionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val policy = RpeRirPolicy.fromContext(context)
    private val now = LocalDate.of(2026, 11, 14)

    private fun link(id: Long, role: ProgressionRole = ProgressionRole.MAIN) =
        ProgramWorkoutLink(entryId = id, applicationId = "app", sourceProgramStableKey = "b",
            sourceItemId = "item-$id", programName = "B",
            weekNumber = id.toInt(), dayOfWeek = 1,
            trackId = "track", trackLabel = "Main", sequence = id.toInt(),
            role = role, mode = ProgressionMode.APP,
            basePolicy = ProgressionBase.REVIEW, anchorSetIndex = null,
            needsReview = true, rule = ProgressionRule())
    private fun row(id: Long, key: String, done: Boolean, kg: Double,
                    rpe: Double? = if (done) 6.5 else null) =
        WorkoutEntryWithSets(
            WorkoutEntry(id = id, date = if (done) "2026-11-14" else "2026-11-21",
                exerciseStableKey = key, exerciseName = key,
                category = "STRENGTH", sessionStableKey = "s-$id"),
            (1..4).map { i ->
                WorkoutSet(id = id * 10 + i, entryId = id, setIndex = i,
                    reps = 6, weightKg = kg, rpe = rpe, confirmed = done,
                    loadState = if (done) ProgramLoadState.EXPLICIT_LOAD
                        else ProgramLoadState.USER_CALIBRATION_REQUIRED)
            }
        )
    private fun targets(row: WorkoutEntryWithSets) = row.sets.map { set ->
        ProgramPrescriptionSet(entryId = row.entry.id, setIndex = set.setIndex,
            originalReps = set.reps, originalKg = 0.0, originalSeconds = 0,
            originalLoadState = ProgramLoadState.USER_CALIBRATION_REQUIRED)
    }
    private fun evaluate(key: String, kg: Double, step: Double,
                         bwFrom: Double? = null, bwTo: Double? = null): BCoachObservedSquatProgression.ProposalEvidence? {
        val source = row(1, key, true, kg)
        val target = row(2, key, false, 0.0)
        return BCoachObservedSquatProgression.evaluate(
            targetEntryId = 2, asOf = now,
            links = listOf(link(1), link(2)), records = listOf(source, target),
            prescriptions = targets(source) + targets(target),
            plateOneSideKg = 0.75, curve = curves.resolve(key),
            rirPolicy = policy, exerciseStableKey = key, loadStepKg = step,
            sourceBodyWeightKg = bwFrom, targetBodyWeightKg = bwTo,
            maxSingleStepFraction = 0.15
        )
    }

    @Test fun canonicalDeadliftKeepsItsOwnCurveAndCandidateGrid() {
        val result = checkNotNull(evaluate("barbell_deadlift", 130.0, 1.5))
        assertTrue(result.ranking.historicalE1rmKg!! > 130.0)
        assertTrue(result.ranking.ranked.single().loadKg in 130.0..131.5)
    }

    @Test fun genericMainUsesOneDisplayedDumbbellStepNotBilateralBarbellArithmetic() {
        val result = checkNotNull(evaluate("ex_a61f1e96", 60.0, 2.0))
        assertTrue(result.ranking.ranked.single().loadKg in 60.0..62.0)
        assertTrue(result.ranking.ranked.single().requiredE1rmKg!! > 60.0)
    }

    @Test fun weightedPullupUsesTotalLoadAndNeedsBothBodyweights() {
        val key = BCoachMainPolicy.WEIGHTED_PULL_UP
        assertNull(evaluate(key, 0.0, 0.75))
        val result = checkNotNull(evaluate(key, 0.0, 0.75, 75.0, 77.0))
        assertTrue(result.ranking.historicalE1rmKg!! > 75.0)
        assertTrue(result.ranking.ranked.single().loadKg in 0.0..0.75)
    }

    @Test fun cannotSubstituteAnotherMainExerciseInOneTrack() {
        val source = row(1, "barbell_deadlift", true, 130.0)
        val target = row(2, "barbell_back_squat", false, 0.0)
        val result = BCoachObservedSquatProgression.evaluate(
            targetEntryId = 2, asOf = now,
            links = listOf(link(1), link(2)), records = listOf(source, target),
            prescriptions = targets(source) + targets(target), plateOneSideKg = 0.75,
            curve = curves.resolve("barbell_deadlift"), rirPolicy = policy,
            exerciseStableKey = "barbell_deadlift", loadStepKg = 1.5
        )
        assertNull(result)
    }
}
