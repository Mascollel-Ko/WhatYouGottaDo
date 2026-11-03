package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise

/**
 * Per-exercise *planning* policy for new B blocks. The observed roster can be reused,
 * but new repetitions and set counts must be prescribed by purpose rather than copied
 * blindly from the most recent workout. No kilogram or RPE is inferred here.
 */
internal object BBlockPurposePrescription {
    fun prepare(
        sessions: List<V22Session>,
        block: BBlockDefinition,
        mainKeys: Set<String>,
        exercises: Map<String, Exercise>,
        autoFocus: BBalancedGoal? = null
    ): List<V22Session> = sessions.map { session ->
        val items = session.items.map { item ->
            val exercise = exercises[item.stableKey]
            val isMain = item.stableKey in mainKeys
            val effective = if (block.goal == BBlockGoal.AUTO) when (autoFocus) {
                BBalancedGoal.STRENGTH -> BBlockGoal.STRENGTH
                BBalancedGoal.HYPERTROPHY -> BBlockGoal.HYPERTROPHY
                BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> BBlockGoal.SPORT_SUPPORT
                null -> BBlockGoal.BALANCED
            } else block.goal
            val role = purpose(effective, isMain, exercise)
            val bounds = repRange(role, isMain)
            val knownReps = item.sets.mapNotNull(V22ObservedSet::reps).filter { it > 0 }
            val fallback = when (role) {
                BBlockGoal.STRENGTH -> if (isMain) 5 else 8
                BBlockGoal.HYPERTROPHY -> if (isMain) 8 else 10
                BBlockGoal.SPORT_SUPPORT -> 6
                BBlockGoal.PEAK_EVALUATION -> if (isMain) 3 else 8
                BBlockGoal.AUTO, BBlockGoal.BALANCED -> if (isMain) 5 else 10
            }
            val reps = (knownReps.sorted().getOrNull(knownReps.size / 2) ?: fallback)
                .coerceIn(bounds.first, bounds.last)
            val timed = item.sets.isNotEmpty() && item.sets.all {
                (it.seconds ?: 0) > 0 && (it.reps ?: 0) <= 0
            }
            val emptyBaseline = item.source.contains("COLD_START") ||
                item.sets.all { (it.reps ?: 0) <= 0 && (it.seconds ?: 0) <= 0 }
            val sets = if (emptyBaseline) {
                when {
                    role == BBlockGoal.PEAK_EVALUATION && isMain -> 2
                    isMain -> 3
                    role == BBlockGoal.SPORT_SUPPORT -> 2
                    else -> 2
                }
            } else item.sets.size.coerceAtLeast(1)
            val prototype = item.sets.firstOrNull()
                ?: V22ObservedSet(null, null, null, null, "", "USER_CALIBRATION_REQUIRED")
            val prescribed = List(sets) { index ->
                val observed = item.sets.getOrNull(index) ?: prototype
                observed.copy(
                    // Retain observed durations for time-based drills; no fabricated repetitions.
                    reps = if (timed) null else reps,
                    // A historical load is never granted prescription authority.
                    weightKg = null,
                    observedRpe = null,
                    loadStatus = "B_BLOCK_USER_START_LOAD_CALIBRATION_REQUIRED"
                )
            }
            item.copy(
                setCount = sets,
                sets = prescribed,
                prescriptionAuthority = "B_BLOCK_GOAL_PRESCRIPTION_USER_REVIEW",
                plannedProgression = "B_BLOCK_BASE_GOAL=" + role.name +
                    ";REP_RANGE=" + bounds.first + ".." + bounds.last +
                    ";NO_AUTOMATIC_LOAD_OR_RPE"
            )
        }
        session.copy(items = items, nominalSets = items.sumOf(V22ExercisePlan::setCount))
    }

    /** Individual exercises in balanced blocks do not all follow one progression lever. */
    fun purpose(
        goal: BBlockGoal,
        main: Boolean,
        exercise: Exercise? = null
    ): BBlockGoal = when (goal) {
        BBlockGoal.AUTO, BBlockGoal.BALANCED -> when {
            main -> BBlockGoal.STRENGTH
            exercise?.activityKind == "FUNCTIONAL" ||
                exercise?.category == "기능성운동" -> BBlockGoal.SPORT_SUPPORT
            else -> BBlockGoal.HYPERTROPHY
        }
        else -> goal
    }

    fun repRange(purpose: BBlockGoal, main: Boolean): IntRange = when (purpose) {
        BBlockGoal.STRENGTH -> if (main) 3..6 else 6..12
        BBlockGoal.HYPERTROPHY -> if (main) 6..12 else 8..15
        BBlockGoal.SPORT_SUPPORT -> 4..10
        BBlockGoal.PEAK_EVALUATION -> if (main) 1..3 else 6..10
        BBlockGoal.AUTO, BBlockGoal.BALANCED -> if (main) 4..8 else 8..12
    }

    /** Peak/evaluation is a distinct last-week prescription, not a stronger normal week. */
    fun finishWeek(
        session: V22Session,
        block: BBlockDefinition,
        finalWeek: Boolean,
        mainKeys: Set<String>
    ): V22Session {
        if (!finalWeek || block.goal != BBlockGoal.PEAK_EVALUATION) return session
        val items = session.items.map { item ->
            val main = item.stableKey in mainKeys
            if (!main) item.copy(
                setCount = item.setCount.coerceAtMost(2),
                sets = item.sets.take(2),
                plannedProgression = item.plannedProgression.orEmpty() +
                    ";PEAK_REDUCED_ACCESSORIES_USER_REVIEW"
            ) else {
                val one = item.sets.firstOrNull()?.copy(reps = 1)
                val sets = listOfNotNull(one)
                item.copy(setCount = sets.size, sets = sets,
                    plannedProgression = item.plannedProgression.orEmpty() +
                        ";PEAK_TEST_WEEK_ONE_REP_LOAD_USER_SELECTED_NOT_AUTOMATIC_MAX")
            }
        }
        return session.copy(items = items, nominalSets = items.sumOf(V22ExercisePlan::setCount))
    }
}
