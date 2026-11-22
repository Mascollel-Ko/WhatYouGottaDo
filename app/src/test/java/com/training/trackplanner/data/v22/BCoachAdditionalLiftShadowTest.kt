package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BCoachAdditionalLiftShadowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val policy = RpeRirPolicy.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val asOf = LocalDate.of(2026, 11, 21)
    private val keys = listOf(
        BCoachAdditionalLiftShadow.DEADLIFT,
        BCoachAdditionalLiftShadow.BENCH,
        BCoachAdditionalLiftShadow.DUMBBELL_BENCH
    )

    private fun entry(key: String, id: Long, kg: Double, reps: Int = 6,
                      rpe: Double? = 7.0, confirmed: Boolean = true): WorkoutEntryWithSets {
        val record = WorkoutEntry(id = id, date = "2026-11-20",
            exerciseStableKey = key, exerciseName = key, category = "근력운동",
            sessionStableKey = "session-$id", createdAt = id)
        return WorkoutEntryWithSets(record, (1..3).map { index ->
            WorkoutSet(id = id * 10 + index, entryId = id, setIndex = index,
                weightKg = kg, reps = reps, rpe = rpe, confirmed = confirmed,
                loadState = ProgramLoadState.EXPLICIT_LOAD)
        })
    }

    @Test fun confirmedCurvesPrescribeEveryWeekWithoutAnOptIn() {
        val records = listOf(entry(keys[0], 1, 180.0), entry(keys[1], 2, 70.0),
            entry(keys[2], 3, 48.0), entry("barbell_back_squat", 4, 130.0))
        val snapshots = BCoachConfirmedLiftModel.snapshots(asOf, records, curves, policy)
        assertEquals(BCoachConfirmedLiftModel.keys, snapshots.keys)
        assertTrue(snapshots.getValue(keys[1]).capacityBase > snapshots.getValue(keys[2]).capacityBase)
        val request = newBBalancedGenerationRequest("four weeks", 3, asOf, 60, false,
            BBalancedGoalImportance(3,3,3,3), shadowPlateOneSideKg = 1.25, shadowDumbbellIncrementKg = 2.0)
        val exercises = records.associate { val key = it.entry.exerciseStableKey
            key to com.training.trackplanner.data.Exercise(stableKey = key, name = key, category = "Strength",
                equipment = if (key == keys[2]) "DUMBBELL" else "BARBELL")
        }
        val history = V22HistoryInput.fromWorkoutHistory(records)
        val resolved = BCoachV2LoadCandidateResolver.resolve(plan(), request, history, exercises, snapshots, rirPolicy = policy)
        val legacy = BCoachV2LoadCandidateResolver.resolve(plan(), request, history, exercises, rirPolicy = policy)
        val comparison = java.io.File("build/reports/b-coach/confirmed-lift-comparison.txt")
        comparison.parentFile.mkdirs()
        comparison.writeText(resolved.skeleton.items.joinToString("\n") { item ->
            val old = legacy.skeleton.items.single { it.localId == item.localId }
            "W${item.weekNumber} ${item.exerciseStableKey} legacy=${old.setPrescriptions.map { it.weightKg }} " +
                "curve=${item.setPrescriptions.map { it.weightKg }} reps=${item.setPrescriptions.map { it.reps }}"
        })
        for (item in resolved.skeleton.items) {
            assertEquals(3, item.setPrescriptions.size)
            for (set in item.setPrescriptions) {
                assertTrue(set.weightKg > 0.0)
                assertEquals(ProgramLoadState.EXPLICIT_LOAD, set.loadState)
                assertTrue(set.expectedRir?.isFinite() == true)
                val step = if (item.exerciseStableKey == keys[2]) 2.0 else 2.5
                assertEquals(0.0, set.weightKg % step, 1e-6)
            }
        }
        assertFalse(keys[0] in BCoachConfirmedLiftModel.snapshots(asOf,
            records + entry(keys[0], 5, 180.0, confirmed = false), curves, policy))
    }

    private fun plan(): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            "B", ProgramGoal.FUNCTIONAL_CONDITIONING, 3, 60, emptySet(),
            "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 4
        )
        val loads = mapOf(
            BCoachAdditionalLiftShadow.DEADLIFT to 180.0,
            BCoachAdditionalLiftShadow.BENCH to 70.0,
            BCoachAdditionalLiftShadow.DUMBBELL_BENCH to 48.0
        )
        val items = (1..4).flatMap { week ->
            keys.mapIndexed { index, key ->
                val kg = loads.getValue(key)
                ProgramSkeletonItem(
                    localId = "w$week-$key", weekNumber = week, dayOfWeek = index + 1,
                    orderIndex = 1, exerciseStableKey = key,
                    exerciseName = key, category = "근력운동", restSeconds = 120,
                    prescription = "review", setCount = 3, reps = 6,
                    weightKg = 0.0, seconds = 0,
                    selectionReason = "B12_BLOCK_FOCUS_STRENGTH",
                    selectionRole = "MAIN_STRENGTH",
                    weightSource = "B_COACH_V2_SET_SPECIFIC",
                    setPrescriptions = (1..3).map { set ->
                        ProgramSetPrescription(
                            setIndex = set, reps = 6, weightKg = 0.0, seconds = 0,
                            targetRpeMin = 8.0,
                            loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                            estimatedLoadKg = kg,
                            loadSource = ProgramLoadSource.CALIBRATION_REQUIRED
                        )
                    }
                )
            }
        }
        return GeneratedProgramSkeleton(
            suggestedName = "B", durationDays = 28, request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = (1..4).map {
                ProgramWeekPlan(it, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)
            },
            items = items, weekDaySchedule = (1..4).associateWith { setOf(1, 2, 3) },
            planningMetadataJson = "{}"
        )
    }

    private fun reports(
        records: List<WorkoutEntryWithSets> = listOf(
            entry(keys[0], 1L, 180.0),
            entry(keys[1], 2L, 70.0),
            entry(keys[2], 3L, 48.0)
        ),
        plate: Double? = 1.25,
        dumbbell: Double? = 2.0
    ) = BCoachAdditionalLiftShadow.examine(
        asOf, records, policy, curves, plate, dumbbell
    )

    @Test
    fun eachExerciseUsesItsOwnStableKeyHistoryAndDisplayedIncrement() {
        val candidates = reports()
        assertEquals(keys.toSet(), candidates.keys)
        keys.forEach { key ->
            val row = candidates.getValue(key)
            assertEquals("READY_FOR_EXPLICIT_REVIEW", row.status)
            assertEquals(key, row.stableKey)
            val proposed = requireNotNull(row.proposedKg)
            val anchor = requireNotNull(row.anchorKg)
            val step = requireNotNull(row.stepKg)
            assertTrue(proposed >= anchor)
            assertTrue(proposed <= anchor + step + 1e-9)
            assertTrue(requireNotNull(row.proposedReps) in 6..7)
            assertNotNull(BCoachAdditionalLiftShadow.inspect(plan(), row))
        }
        assertEquals(2.5, candidates.getValue(keys[0]).stepKg!!, 0.0)
        assertEquals(2.5, candidates.getValue(keys[1]).stepKg!!, 0.0)
        assertEquals(2.0, candidates.getValue(keys[2]).stepKg!!, 0.0)
        assertEquals(48.0, candidates.getValue(keys[2]).anchorKg!!, 0.0)
        assertTrue(candidates.getValue(keys[2]).curveMatchLevel!!.contains("BORROWED"))
    }

    @Test
    fun selectedFirstSetsOnlyArePatchedWithExactExerciseProvenance() {
        val baseline = plan()
        var updated = baseline
        for (key in keys) {
            val evidence = reports().getValue(key)
            val review = requireNotNull(BCoachAdditionalLiftShadow.inspect(updated, evidence))
            updated = requireNotNull(BCoachAdditionalLiftShadow.apply(updated, evidence, review))
        }
        keys.forEach { key ->
            val old = baseline.items.first { it.exerciseStableKey == key }
            val now = updated.items.first { it.localId == old.localId }
            assertEquals(old.setPrescriptions.drop(1), now.setPrescriptions.drop(1))
            assertEquals(ProgramLoadState.EXPLICIT_LOAD, now.setPrescriptions.first().loadState)
            assertEquals(ProgramLoadSource.USER_FIXED, now.setPrescriptions.first().loadSource)
            assertEquals(BCoachAdditionalLiftShadow.VERSION,
                now.setPrescriptions.first().evidenceVersion)
            assertEquals(baseline.items.filter { it.exerciseStableKey == key }.drop(1),
                updated.items.filter { it.exerciseStableKey == key }.drop(1))
        }
        val audits = JSONObject(updated.planningMetadataJson!!)
            .getJSONArray("bCoachOtherLiftShadowApprovedFirstSets")
        assertEquals(3, audits.length())
        assertEquals("TWO_DUMBBELLS_TOTAL_DISPLAYED_KG", audits.getJSONObject(2)
            .getString("unit"))
        assertEquals(baseline.items, plan().items)
    }

    @Test
    fun noHistoryMissingRpeOrUnconfirmedSetsFailClosed() {
        assertTrue(reports(emptyList()).values.all { it.status != "READY_FOR_EXPLICIT_REVIEW" })
        val missingRpe = reports(listOf(entry(keys[0], 1L, 180.0, rpe = null)))
        assertNull(BCoachAdditionalLiftShadow.inspect(plan(), missingRpe.getValue(keys[0])))
        val unconfirmed = reports(listOf(entry(keys[1], 2L, 70.0, confirmed = false)))
        assertNull(BCoachAdditionalLiftShadow.inspect(plan(), unconfirmed.getValue(keys[1])))
        assertNull(BCoachAdditionalLiftShadow.inspect(plan(),
            reports(dumbbell = null).getValue(keys[2])))
    }

    @Test
    fun newerUnfinishedSessionDoesNotAllowEarlierCompletedAnchorToReplaceIt() {
        val finished = entry(keys[0], 1L, 180.0)
        val unfinished = entry(keys[0], 2L, 180.0, confirmed = false)
        val evidence = reports(listOf(finished, unfinished)).getValue(keys[0])
        assertEquals("REVIEW_ONLY", evidence.status)
        assertNull(BCoachAdditionalLiftShadow.inspect(plan(), evidence))
    }

    @Test
    fun manualFirstSetAndMismatchedRepOrLoadNeverGetOverwritten() {
        val evidence = reports().getValue(keys[2])
        val baseline = plan()
        val reviewed = requireNotNull(BCoachAdditionalLiftShadow.inspect(baseline, evidence))
        val wrongReps = baseline.copy(items = baseline.items.map { row ->
            if (row.localId != reviewed.localId) row
            else row.copy(setPrescriptions = row.setPrescriptions.mapIndexed { index, set ->
                if (index == 0) set.copy(reps = 8) else set
            })
        })
        assertNull(BCoachAdditionalLiftShadow.inspect(wrongReps, evidence))
        val manual = baseline.copy(items = baseline.items.map { row ->
            if (row.localId != reviewed.localId) row
            else row.copy(setPrescriptions = row.setPrescriptions.mapIndexed { index, set ->
                if (index == 0) set.copy(
                    weightKg = 40.0, loadState = ProgramLoadState.EXPLICIT_LOAD,
                    loadSource = ProgramLoadSource.USER_FIXED
                ) else set
            })
        })
        assertNull(BCoachAdditionalLiftShadow.inspect(manual, evidence))
        assertNull(BCoachAdditionalLiftShadow.apply(wrongReps, evidence, reviewed))
    }
}
