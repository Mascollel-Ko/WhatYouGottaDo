package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.StrengthModelRevisionPolicy
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachSquatShadowEvidenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val date = LocalDate.of(2026, 11, 12)
    private val revision = StrengthModelRevisionPolicy.current(0L, null)
        .copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE)
    private val validModel = StrengthPosteriorModel.toEntity(
        StrengthPosteriorModel.initialState(registry, null).copyDeep(lastProcessedDate = date.minusDays(1)),
        now = 2L,
        modelInstanceKey = StrengthModelRevisionPolicy.modelInstanceKey(
            StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
        )
    )

    private fun record(
        key: String = "barbell_back_squat",
        day: LocalDate = date.minusDays(1),
        sets: List<WorkoutSet> = listOf(
            WorkoutSet(id = 11L, entryId = 1L, setIndex = 1, weightKg = 140.0, reps = 5,
                rpe = 7.5, confirmed = true),
            WorkoutSet(id = 12L, entryId = 1L, setIndex = 2, weightKg = 140.0, reps = 5,
                rpe = 8.0, confirmed = true),
            WorkoutSet(id = 13L, entryId = 1L, setIndex = 3, weightKg = 140.0, reps = 5,
                rpe = 8.0, confirmed = true)
        ),
        rpe: Double? = null
    ) = WorkoutEntryWithSets(
        WorkoutEntry(
            id = 1L, date = day.toString(), exerciseStableKey = key,
            exerciseName = "스쿼트", category = "근력운동",
            sessionStableKey = "observed-session-1", rpe = rpe
        ),
        sets
    )

    private fun adapt(
        records: List<WorkoutEntryWithSets> = listOf(record()),
        asOf: LocalDate = date,
        model: com.training.trackplanner.data.StrengthPosteriorModelStateEntity? = validModel,
        activeRevision: com.training.trackplanner.data.StrengthModelRevisionEntity? = revision,
        pending: Boolean = false
    ) = BCoachSquatEvidenceAdapter.fromPersisted(
        asOf = asOf, revision = activeRevision, model = model,
        exerciseCurve = null, sharedCurve = null,
        records = records, registry = registry, curves = curves,
        hasUnprocessedEvents = pending
    )

    @Test
    fun `reads canonical posterior and preserves all confirmed sets without rewriting RPE`() {
        val result = adapt() as BCoachSquatEvidenceResult.Ready
        val evidence = result.evidence
        assertEquals(validModel.stateFingerprint, evidence.posteriorFingerprint)
        assertEquals(date.minusDays(1), evidence.posteriorAsOf)
        assertTrue(evidence.capacity.median > 0.0)
        assertEquals("barbell_back_squat", registry.target(StrengthPerformanceRegistry.BACK_SQUAT)!!
            .anchorStableKeys.single())
        assertEquals(3, evidence.latestAnchor!!.confirmedSets.size)
        assertEquals(listOf(7.5, 8.0, 8.0),
            evidence.latestAnchor.confirmedSets.map { it.observedRpe })
        assertEquals(1.0, evidence.curve.evaluate(1.0).relativeLoad!!, 1e-12)
        assertNull(evidence.curvePosterior) // canonical prior, not invented personalization
    }

    @Test
    fun `refuses a posterior that includes observations after the requested asOf date`() {
        val result = adapt(asOf = date.minusDays(2))
        assertEquals(
            BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.FUTURE_POSTERIOR_LEAKAGE
            ), result
        )
    }

    @Test
    fun `refuses inactive revisions, pending events, and broken persisted fingerprints`() {
        assertEquals(
            BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.INACTIVE_OR_INCOMPATIBLE_REVISION
            ),
            adapt(activeRevision = revision.copy(status = StrengthModelRevisionPolicy.STATUS_BUILDING))
        )
        assertEquals(
            BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNPROCESSED_MODEL_EVENT
            ),
            adapt(pending = true)
        )
        assertEquals(
            BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNREADABLE_POSTERIOR
            ),
            adapt(model = validModel.copy(stateFingerprint = "broken"))
        )
    }

    @Test
    fun `never promotes unconfirmed or unresolved planned load into an observed anchor`() {
        val records = listOf(record(sets = listOf(
            WorkoutSet(id = 31L, entryId = 1L, setIndex = 1, weightKg = 145.0, reps = 5,
                confirmed = false, rpe = 8.0),
            WorkoutSet(id = 32L, entryId = 1L, setIndex = 2, weightKg = 145.0, reps = 5,
                confirmed = true, rpe = 8.0, loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED),
            WorkoutSet(id = 33L, entryId = 1L, setIndex = 3, weightKg = 140.0, reps = 5,
                confirmed = true, rpe = 8.0)
        )))
        val result = adapt(records) as BCoachSquatEvidenceResult.Ready
        assertEquals(listOf(33L), result.evidence.latestAnchor!!.confirmedSets.map { it.setId })
    }

    @Test
    fun `missing set RPE stays unknown unless a recorded entry RPE is available`() {
        val noRpe = record(sets = listOf(
            WorkoutSet(id = 41L, entryId = 1L, setIndex = 1, weightKg = 100.0, reps = 6,
                confirmed = true)
        ))
        val result = adapt(listOf(noRpe)) as BCoachSquatEvidenceResult.Ready
        assertNull(result.evidence.latestAnchor!!.confirmedSets.single().observedRpe)
        val fallback = adapt(listOf(noRpe.copy(entry = noRpe.entry.copy(rpe = 7.5))))
            as BCoachSquatEvidenceResult.Ready
        assertEquals(7.5, fallback.evidence.latestAnchor!!.confirmedSets.single().observedRpe!!, 0.0)
    }

    @Test
    fun `a valid posterior without a confirmed squat anchor is not converted to an observed session`() {
        val result = adapt(records = listOf(record(key = "barbell_deadlift")))
            as BCoachSquatEvidenceResult.Ready
        assertNull(result.evidence.latestAnchor)
        assertNotNull(result.evidence.capacity)
    }

    @Test
    fun `an invalid or outdated model cannot supply a shadow posterior`() {
        assertEquals(
            BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.STALE_OR_MISSING_MODEL
            ), adapt(model = null)
        )
        assertEquals(
            BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.STALE_OR_MISSING_MODEL
            ), adapt(model = validModel.copy(modelVersion = "old-model"))
        )
    }
}
