package com.training.trackplanner.data.v22

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import org.json.JSONObject

/**
 * First-week load is explicitly user-calibrated. Only a changed, uniform positive
 * first-week load authorizes deterministic future-week propagation in the same block.
 * Existing user-entered future loads are never silently overwritten.
 */
internal fun GeneratedProgramSkeleton.propagateBBlockStartLoad(
    before: GeneratedProgramSkeleton,
    smallestPlateKg: Double
): GeneratedProgramSkeleton {
    if (!smallestPlateKg.isFinite() || smallestPlateKg <= 0.0) return this
    val metadata = planningMetadataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return this
    if (metadata.optString("schema") != BBalanced12WeekEngine.BLOCK_SCHEMA) return this
    val blocks = metadata.optJSONArray("blocks") ?: return this
    val specifications = metadata.optJSONArray("blockSpecs") ?: return this
    if (blocks.length() != specifications.length()) return this
    val priorItems = before.items.associateBy { it.localId }
    val derived = mutableMapOf<String, Double>()
    for (index in 0 until blocks.length()) {
        val block = blocks.getJSONObject(index)
        val spec = specifications.getJSONObject(index)
        val firstWeek = block.getInt("startWeek")
        val lastWeek = block.getInt("endWeek")
        val pattern = runCatching { BVolumePattern.valueOf(spec.getString("pattern")) }.getOrNull() ?: continue
        val stages = BBlockPeriodization.steps(BBlockDefinition(lastWeek - firstWeek + 1, pattern = pattern))
        val adjustment = spec.optString("adjustment", BVolumeAdjustment.AUTO.name)
        val loadFirst = adjustment == BVolumeAdjustment.LOAD_FIRST.name ||
            (adjustment == BVolumeAdjustment.AUTO.name && block.optString("focus") == BBalancedGoal.STRENGTH.name)
        val updatedAnchors = items.filter { it.weekNumber == firstWeek }
        for (anchor in updatedAnchors) {
            val old = priorItems[anchor.localId] ?: continue
            val weight = ProgramSetPrescriptionResolver.resolve(anchor).map { it.weightKg }
                .distinct().singleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val previousWeight = ProgramSetPrescriptionResolver.resolve(old).map { it.weightKg }
                .distinct().singleOrNull()
            if (previousWeight == weight) continue
            items.filter { target ->
                target.weekNumber in firstWeek..lastWeek &&
                    target.dayOfWeek == anchor.dayOfWeek &&
                    target.exerciseStableKey == anchor.exerciseStableKey &&
                    target.weekNumber != firstWeek
            }.forEach { target ->
                val existing = ProgramSetPrescriptionResolver.resolve(target)
                // Explicit previously calibrated values belong to the user.
                if (existing.any { it.weightKg > 0.0 }) return@forEach
                val stage = stages[target.weekNumber - firstWeek].coerceAtLeast(0)
                derived[target.localId] = if (loadFirst) weight + stage * BBlockPeriodization.barbellIncrement(smallestPlateKg)
                    else weight
            }
        }
    }
    if (derived.isEmpty()) return this
    return copy(
        items = items.map { item ->
            val kg = derived[item.localId] ?: return@map item
            item.copy(
                weightKg = kg,
                weightSource = "B_BLOCK_USER_START_LOAD_DERIVED",
                setPrescriptions = ProgramSetPrescriptionResolver.resolve(item).map { set ->
                    set.copy(weightKg = kg, loadState = ProgramLoadState.EXPLICIT_LOAD)
                }
            )
        },
        warnings = (warnings + "B_BLOCK_FUTURE_KG_DERIVED_FROM_USER_START_LOAD_REVIEW_REQUIRED").distinct()
    )
}
