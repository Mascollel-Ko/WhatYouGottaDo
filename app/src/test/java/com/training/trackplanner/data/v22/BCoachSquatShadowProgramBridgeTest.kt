package com.training.trackplanner.data.v22

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BCoachSquatShadowProgramBridgeTest {
    private val cutoff = LocalDate.of(2026, 11, 21)
    private fun item(week: Int) = ProgramSkeletonItem(
        localId = "squat-$week", weekNumber = week, dayOfWeek = 1, orderIndex = 1,
        exerciseStableKey = "barbell_back_squat", exerciseName = "백스쿼트",
        category = "근력운동", restSeconds = 150, prescription = "review",
        setCount = 3, reps = 6, weightKg = 0.0, seconds = 0,
        selectionReason = "B12_BLOCK_1_FOCUS_STRENGTH",
        selectionRole = "MAIN_STRENGTH",
        weightSource = "B_COACH_V2_SET_SPECIFIC",
        setPrescriptions = (1..3).map { index ->
            ProgramSetPrescription(
                setIndex = index, reps = 6, weightKg = 0.0, seconds = 0,
                loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                estimatedLoadKg = 135.0 - index,
                loadSource = ProgramLoadSource.CALIBRATION_REQUIRED
            )
        }
    )

    private fun skeleton(): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            "B review", ProgramGoal.FUNCTIONAL_CONDITIONING,
            1, 60, emptySet(), "", 0.0, "AUTO",
            ProgramPeriodizationType.AUTO, 4
        )
        return GeneratedProgramSkeleton(
            suggestedName = "B review", durationDays = 28, request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = (1..4).map {
                ProgramWeekPlan(it, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)
            }, items = (1..4).map(::item),
            weekDaySchedule = (1..4).associateWith { setOf(1) },
            planningMetadataJson = "{}"
        )
    }

    private fun result(
        action: String = "LOAD_UP_ONE_STEP",
        kg: Double = 131.5,
        reps: Int = 6,
        sets: Int = 3,
        status: String = "EXPERIMENTAL_RANKING_ONLY",
        referenceDate: LocalDate = cutoff.minusDays(2)
    ): BCoachSquatShadowProgramComparison {
        fun candidate(name: String, load: Double, count: Int, number: Int) =
            BCoachSquatCurveProgressionReport.Candidate(
                action = name, loadKg = load, reps = count, sets = number,
                requiredE1rmKg = 155.0, historicalE1rmLogGap = 0.05,
                worstCaseRegret = 0.01, weightedCost = 0.1,
                targetRirMixtureMass = 0.8, volumeLoadChange = 0.01,
                eligible = true, reason = "EXPERIMENTAL_ELIGIBLE_NOT_APPROVED"
            )
        val ranked = listOf(candidate("HOLD", 130.0, 6, 3)) +
            (if (action == "HOLD") emptyList() else listOf(candidate(action, kg, reps, sets)))
        return BCoachSquatShadowProgramComparison(
            status = "STABLE_EXPERIMENT_ONLY",
            snapshotDate = cutoff,
            observedReference = "130kg × 6회 / RPE 7",
            plannedSquatRows = emptyList(), experimentCandidates = emptyList(),
            experiments = emptyList(), notices = emptyList(),
            curveRanking = BCoachSquatCurveProgressionReport(
                status = status, asOf = cutoff, referenceDate = referenceDate,
                selectedAction = action, historicalE1rmKg = 150.0,
                targetRir = 2.0, doseTargetSets = 3,
                ranked = ranked, notes = emptyList()
            )
        )
    }

    @Test
    fun firstSetCanBeExplicitlyApprovedWithoutTouchingOtherSetsOrWeeks() {
        val original = skeleton()
        val report = result()
        val choice = requireNotNull(BCoachSquatShadowProgramBridge.inspect(original, report))
        assertEquals(1, choice.week)
        assertEquals(1, choice.day)
        val amended = requireNotNull(BCoachSquatShadowProgramBridge.apply(original, report, choice))
        assertEquals(original.items.drop(1), amended.items.drop(1))
        val before = original.items.first().setPrescriptions
        val after = amended.items.first().setPrescriptions
        assertEquals(before.drop(1), after.drop(1))
        assertEquals(131.5, after.first().weightKg, 1e-9)
        assertEquals(ProgramLoadState.EXPLICIT_LOAD, after.first().loadState)
        assertEquals(ProgramLoadSource.USER_FIXED, after.first().loadSource)
        assertEquals("B_COACH_SQUAT_GATE_F_EXPLICIT_USER_APPROVAL_V1", after.first().evidenceVersion)
        val audit = JSONObject(amended.planningMetadataJson!!).getJSONObject(
            "bCoachSquatShadowApprovedFirstSet")
        assertEquals("LOAD_UP_ONE_STEP", audit.getString("action"))
        assertFalse(audit.getBoolean("laterSetsOrWeeksModified"))
        assertEquals(original.items, skeleton().items)
    }

    @Test
    fun missingOrStaleEvidenceAndIncompatibleCandidatesNeverChangeThePlan() {
        val plan = skeleton()
        assertNull(BCoachSquatShadowProgramBridge.inspect(plan, null))
        assertNull(BCoachSquatShadowProgramBridge.inspect(plan, result(status = "GRID_CONFLICT_SHADOW_REVIEW")))
        assertNull(BCoachSquatShadowProgramBridge.inspect(
            plan, result(referenceDate = cutoff.minusDays(70))
        ))
        assertNull(BCoachSquatShadowProgramBridge.inspect(plan, result(kg = 134.5)))
        assertNull(BCoachSquatShadowProgramBridge.inspect(plan, result(action = "SETS_UP_ONE", sets = 4)))
        assertNull(BCoachSquatShadowProgramBridge.inspect(plan, result(action = "REPS_UP_ONE", reps = 8, kg = 130.0)))
        val manual = plan.copy(items = plan.items.mapIndexed { i, item ->
            if (i != 0) item else item.copy(
                setPrescriptions = item.setPrescriptions.mapIndexed { k, set ->
                    if (k != 0) set else set.copy(
                        weightKg = 140.0, loadState = ProgramLoadState.EXPLICIT_LOAD,
                        loadSource = ProgramLoadSource.USER_FIXED
                    )
                }
            )
        })
        assertNull(BCoachSquatShadowProgramBridge.inspect(manual, result()))
        val easierTarget = plan.copy(items = plan.items.mapIndexed { index, item ->
            if (index != 0) item else item.copy(
                setPrescriptions = item.setPrescriptions.map { it.copy(targetRpeMin = 6.0) }
            )
        })
        assertNull(BCoachSquatShadowProgramBridge.inspect(easierTarget, result()))
        val differentPlannedLoad = plan.copy(items = plan.items.mapIndexed { index, item ->
            if (index != 0) item else item.copy(
                setPrescriptions = item.setPrescriptions.map {
                    it.copy(estimatedLoadKg = 100.0)
                }
            )
        })
        assertNull(BCoachSquatShadowProgramBridge.inspect(differentPlannedLoad, result()))
        val first = requireNotNull(BCoachSquatShadowProgramBridge.inspect(plan, result()))
        assertNull(BCoachSquatShadowProgramBridge.apply(
            plan, result(action = "REPS_UP_ONE", kg = 130.0, reps = 7), first
        ))
    }

    @Test
    fun oneExtraRepCanBeApprovedOnlyWithSameLoadAndSetCount() {
        val plan = skeleton()
        val report = result(action = "REPS_UP_ONE", kg = 130.0, reps = 7)
        val review = requireNotNull(BCoachSquatShadowProgramBridge.inspect(plan, report))
        val amended = requireNotNull(BCoachSquatShadowProgramBridge.apply(plan, report, review))
        assertEquals(7, amended.items.first().setPrescriptions.first().reps)
        assertEquals(6, amended.items.first().setPrescriptions[1].reps)
        assertEquals(3, amended.items.first().setCount)
        assertEquals(0, amended.items.first().reps)
    }
}
