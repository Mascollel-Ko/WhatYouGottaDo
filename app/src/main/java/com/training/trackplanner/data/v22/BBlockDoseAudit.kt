package com.training.trackplanner.data.v22

import org.json.JSONArray
import org.json.JSONObject

/**
 * Audits the *realized prescription*, not the illustrative line drawn in the UI.
 * Body mass, RPE, %1RM and uncalibrated kilograms are not converted into a
 * fictitious "volume" scalar just to make a desired curve look correct.
 */
internal object BBlockDoseAudit {
    fun compute(
        weeks: List<V22Week>,
        blocks: List<BBalancedBlock>,
        spec: BBlockProgramDefinition
    ): JSONArray {
        val out = JSONArray()
        blocks.forEachIndexed { index, block ->
            val pattern = BBlockPeriodization.steps(spec.blocks[index])
            val blockWeeks = weeks.filter { it.block == block.block }.sortedBy(V22Week::week)
            require(blockWeeks.size == pattern.size)
            val baseline = blockWeeks.first()
            val baseSets = baseline.weeklyNominalSets
            val baseReps = repetitions(baseline)
            for ((localWeek, week) in blockWeeks.withIndex()) {
                val loadSteps = week.sessions.flatMap(V22Session::items).sumOf { item ->
                    Regex("LOAD_STEPS=(-?[0-9]+)").find(item.plannedProgression.orEmpty())
                        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                }
                val reps = repetitions(week)
                val stage = pattern[localWeek]
                val status = when {
                    spec.blocks[index].goal == BBlockGoal.PEAK_EVALUATION &&
                        localWeek == pattern.lastIndex -> "PEAK_USER_SELECTED_TEST_LOAD"
                    stage == 0 -> "BASELINE_OR_LOW_STEP"
                    stage > 0 && loadSteps > 0 -> "LOAD_CALIBRATION_REQUIRED"
                    stage < 0 && loadSteps < 0 -> "LOAD_CALIBRATION_REQUIRED"
                    stage > 0 && (week.weeklyNominalSets > baseSets || reps > baseReps) -> "VISIBLE_INCREASE"
                    stage < 0 && (week.weeklyNominalSets < baseSets || reps < baseReps) -> "VISIBLE_RECOVERY_REDUCTION"
                    else -> "PATTERN_NOT_REALIZED_USER_REVIEW"
                }
                out.put(JSONObject()
                    .put("block", block.block)
                    .put("week", week.week)
                    .put("expectedStage", stage)
                    .put("workingSets", week.weeklyNominalSets)
                    .put("plannedRepetitions", reps)
                    .put("pendingLoadSteps", loadSteps)
                    .put("status", status)
                    .put("authority", "PRESCRIPTION_AUDIT_NOT_PHYSIOLOGICAL_VOLUME"))
            }
        }
        return out
    }

    private fun repetitions(week: V22Week): Int =
        week.sessions.flatMap(V22Session::items).flatMap(V22ExercisePlan::sets)
            .sumOf { it.reps ?: 0 }
}
