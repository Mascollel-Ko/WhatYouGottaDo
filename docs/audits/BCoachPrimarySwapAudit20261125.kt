package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit fixture. Temporarily copy under app/src/test/java to execute with Gradle. */
class BCoachPrimarySwapAudit20261125 {
    private val anchors = setOf("audit-pull-anchor", "audit-core-anchor")
    private val relations = listOf(
        "audit-pull-anchor" to "BACK_LATS", "audit-pull-accessory" to "BACK_LATS",
        "audit-core-anchor" to "ANTERIOR_CORE", "audit-core-accessory" to "ANTERIOR_CORE"
    ).map { (key, muscle) ->
        CanonicalMetadataRelation(
            domain = CanonicalRelationDomain.MUSCLE, relationKey = key,
            exerciseStableKey = key, relationType = "PRIMARY", relationValue = muscle,
            coefficient = 1.0, sourceStableKey = key, status = "REVIEWED", provenance = "AUDIT"
        )
    }

    private fun row(key: String, day: Int, order: Int, count: Int) = ProgramSkeletonItem(
        localId = key, exerciseStableKey = key, exerciseName = key, category = "Strength",
        weekNumber = 1, dayOfWeek = day, orderIndex = order, restSeconds = 60,
        prescription = "audit", setCount = count, reps = 8, weightKg = 0.0, seconds = 0,
        selectionReason = "AUDIT", weightSource = "USER_CALIBRATION_REQUIRED",
        selectionRole = "ACCESSORY",
        movementFamily = key, redundancyGroup = key, primaryStressProfile = "HORIZONTAL_ROW_STRESS",
        setPrescriptions = (1..count).map {
            ProgramSetPrescription(it, 8, 0.0, 0, null, ProgramLoadState.USER_CALIBRATION_REQUIRED)
        }
    )

    private fun plan(pullSets: Int = 2, coreSets: Int = 2): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            name = "Swap audit", goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
            weeklyTrainingDays = 2, sessionMinutes = 60, availableEquipment = emptySet(),
            excludedExerciseText = "", badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 1
        )
        return GeneratedProgramSkeleton(
            suggestedName = request.name, durationDays = 7, request = request,
            periodizationType = ProgramPeriodizationType.AUTO, weekPlans = emptyList(),
            items = listOf(
                row("audit-pull-anchor", 4, 1, 2), row("audit-pull-accessory", 4, 2, pullSets),
                row("audit-core-anchor", 6, 1, 2), row("audit-core-accessory", 6, 2, coreSets)
            ),
            weekDaySchedule = mapOf(1 to setOf(4, 6))
        )
    }

    private fun swap(plan: GeneratedProgramSkeleton) = BCoachSessionComposition
        .neighbors(plan, anchors, emptyMap(), muscleRelations = relations)
        .single { it.first.startsWith("SWAP_PRIMARY") }.second

    @Test fun equalStressSwapCanLoseWithoutPrimaryRecoveryCredit() {
        val baseline = plan()
        val swapped = swap(baseline)
        val exercises = baseline.items.associate { row ->
            val key = row.exerciseStableKey
            key to Exercise(stableKey = key, name = key, category = "Strength", equipment = "DUMBBELL",
                metadataConfidence = "REVIEWED", progressMetricType = "LOAD_REPS",
                localLoadWeight = 0.7, systemicLoadWeight = 0.3)
        }
        val request = V22GenerationRequest(
            name = "Swap audit", weeks = 1, days = 2, mode = V22Mode.B,
            asOf = LocalDate.of(2026, 11, 14), sessionMinutes = 60, fixedWeekdays = setOf(4, 6),
            sourceProgramStableKey = "audit-source-program"
        )
        fun evaluate(plan: GeneratedProgramSkeleton) = BCoachV2DoseEvaluator.evaluate(
            plan = plan, baseline = baseline, request = request, history = emptyList(),
            exercises = exercises, muscleRelations = relations, coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY, mainExerciseKeys = anchors,
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(3, 4, 3, 3), false, emptyMap())
        )
        val before = evaluate(baseline)
        val after = evaluate(swapped)
        assertEquals(6, swapped.items.single { it.exerciseStableKey == "audit-pull-accessory" }.dayOfWeek)
        assertEquals(4, swapped.items.single { it.exerciseStableKey == "audit-core-accessory" }.dayOfWeek)
        assertTrue(before.hardConstraints.all { it.status == BCoachConstraintStatus.PASS })
        assertTrue(after.hardConstraints.all { it.status == BCoachConstraintStatus.PASS })
        for (axis in listOf(BCoachObjectiveAxis.RECOVERY_LOAD, BCoachObjectiveAxis.GOAL_DEFICIT,
            BCoachObjectiveAxis.GOAL_EXCESS)) {
            assertEquals(before.baseAxes.getValue(axis), after.baseAxes.getValue(axis), 1e-10)
        }
        assertEquals(0.0, before.baseAxes.getValue(BCoachObjectiveAxis.PLAN_CHANGE), 1e-10)
        assertEquals(0.5, after.baseAxes.getValue(BCoachObjectiveAxis.PLAN_CHANGE), 1e-10)
        assertEquals(0.02, after.baseLoss - before.baseLoss, 1e-10)
        println("baseline=${before.baseLoss} swapped=${after.baseLoss} recovery=${after.baseAxes[BCoachObjectiveAxis.RECOVERY_LOAD]}")
    }

    @Test fun unequalSetsTravelWithTheExercise() {
        val baseline = plan(pullSets = 5, coreSets = 2)
        val swapped = swap(baseline)
        val prior = baseline.items.associateBy { it.localId }
        assertEquals(prior, swapped.items.associate { row ->
            val old = prior.getValue(row.localId)
            row.localId to row.copy(dayOfWeek = old.dayOfWeek, orderIndex = old.orderIndex)
        })
        fun counts(plan: GeneratedProgramSkeleton) =
            plan.items.groupBy { it.dayOfWeek }.mapValues { (_, rows) -> rows.sumOf { it.setCount } }
        assertEquals(mapOf(4 to 7, 6 to 4), counts(baseline))
        assertEquals(mapOf(4 to 4, 6 to 7), counts(swapped))
    }
}
