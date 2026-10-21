package com.training.trackplanner.data.v22

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramWeekPlan
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.round

internal fun restoredBBalancedWeekPlans(metadataJson: String?, durationWeeks: Int): List<ProgramWeekPlan>? {
    val metadata = metadataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    if (metadata.optString("schema") != BBalanced12WeekEngine.SCHEMA || durationWeeks != 12) return null
    val blocks = metadata.optJSONArray("blocks") ?: return null
    val focusByWeek = mutableMapOf<Int, Pair<Int, String>>()
    repeat(blocks.length()) { index ->
        val block = blocks.getJSONObject(index)
        val number = block.getInt("block")
        val focus = block.getString("focus")
        for (week in block.getInt("startWeek")..block.getInt("endWeek")) focusByWeek[week] = number to focus
    }
    if (focusByWeek.keys != (1..12).toSet()) return null
    return (1..12).map { week ->
        val (block, focus) = focusByWeek.getValue(week)
        ProgramWeekPlan(
            weekIndex = week,
            weekType = "B_BALANCED_BLOCK_${block}_$focus",
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false,
            targetRpeMin = 6.0,
            targetRpeMax = 8.5
        )
    }
}

/** Recomputes the persisted coverage/shortfall audit only; user edits are never auto-filled. */
internal fun GeneratedProgramSkeleton.reassessBBalancedAfterEdit(): GeneratedProgramSkeleton {
    val metadata = planningMetadataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return this
    if (metadata.optString("schema") != BBalanced12WeekEngine.SCHEMA) return this
    val contributions = metadata.getJSONObject("contributions")
    val divisors = metadata.getJSONObject("doseDivisors")
    val targets = metadata.getJSONArray("goalTargets")
    val averageDose = BBalancedGoal.entries.associateWith { goal ->
        val total = (1..request.durationWeeks).sumOf { week ->
            items.asSequence().filter { it.weekNumber == week }.sumOf { item ->
                contributions.optJSONObject(item.exerciseStableKey)?.optDouble(goal.name, 0.0)?.times(item.setCount) ?: 0.0
            }
        }
        rounded(total / request.durationWeeks / divisors.optDouble(goal.name, 1.0).coerceAtLeast(1.0))
    }
    val evaluations = JSONArray()
    repeat(targets.length()) { index ->
        val target = targets.getJSONObject(index)
        val goal = BBalancedGoal.valueOf(target.getString("goal"))
        val planned = averageDose.getValue(goal)
        val development = target.optNullableDouble("developmentTarget")
        val maintenance = target.optNullableDouble("maintenanceFloor")
        val developmentShortfall = development?.let { rounded(max(0.0, it - planned)) }
        val maintenanceShortfall = maintenance?.let { rounded(max(0.0, it - planned)) }
        evaluations.put(JSONObject()
            .put("goal", goal.name)
            .put("plannedDose", planned)
            .put("developmentTarget", development ?: JSONObject.NULL)
            .put("maintenanceFloor", maintenance ?: JSONObject.NULL)
            .put("developmentShortfall", developmentShortfall ?: JSONObject.NULL)
            .put("maintenanceShortfall", maintenanceShortfall ?: JSONObject.NULL)
            .put("status", when {
                maintenanceShortfall != null && maintenanceShortfall > 0.0 -> "BELOW_MAINTENANCE_REVIEW"
                developmentShortfall != null && developmentShortfall > 0.0 -> "BELOW_DEVELOPMENT_TARGET"
                development != null -> "TARGET_MET"
                planned > 0.0 -> "DIRECTIONAL_COVERAGE_PRESENT"
                else -> "REVIEW_REQUIRED_NO_NUMERIC_AUTHORITY"
            }))
    }
    metadata.put("userEdited", true)
        .put("postEditEvaluation", evaluations)
        .put("postEditPolicy", "REASSESS_ONLY_NO_AUTOMATIC_COMPENSATION")
    return copy(
        planningMetadataJson = metadata.toString(),
        warnings = (warnings + "B12_POST_EDIT_TARGETS_REEVALUATED_NO_AUTOMATIC_COMPENSATION").distinct()
    )
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

private fun rounded(value: Double): Double = round(value * 1000.0) / 1000.0
