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
): GeneratedProgramSkeleton =
    propagateBBlockStartLoad(before, BBlockEquipmentIncrements(smallestPlateKg, 1.0, 2.5))

internal fun GeneratedProgramSkeleton.propagateBBlockStartLoad(
    before: GeneratedProgramSkeleton,
    units: BBlockEquipmentIncrements
): GeneratedProgramSkeleton {
    val metadata = planningMetadataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return this
    if (metadata.optString("schema") != BBalanced12WeekEngine.BLOCK_SCHEMA) return this
    val blocks = metadata.optJSONArray("blocks") ?: return this
    val specifications = metadata.optJSONArray("blockSpecs") ?: return this
    val between = metadata.optJSONArray("recoveryDaysBetween") ?: return this
    if (blocks.length() != specifications.length() || between.length() != blocks.length() - 1) return this
    val layout = runCatching {
        BBlockProgramDefinition(
            blocks = (0 until specifications.length()).map { index ->
                val spec = specifications.getJSONObject(index)
                BBlockDefinition(
                    weeks = spec.getInt("weeks"),
                    pattern = BVolumePattern.valueOf(spec.getString("pattern"))
                )
            },
            recoveryDaysBetween = (0 until between.length()).map(between::getInt)
        )
    }.getOrNull() ?: return this
    val calendar = BBlockCalendar(layout)
    // The actual per-exercise lever may differ from the block default when
    // repetitions reach their limit or equipment cannot use paired plates.
    val progressionSteps = metadata.optJSONArray("prescriptionSteps")
    val equipmentKinds = metadata.optJSONObject("equipmentIncrementKinds")
    fun loadSteps(block: Int, week: Int, session: Int, key: String): Int? {
        if (progressionSteps == null) return null
        for (i in 0 until progressionSteps.length()) {
            val entry = progressionSteps.getJSONObject(i)
            if (entry.getInt("block") == block &&
                entry.getInt("localWeek") == week &&
                entry.getInt("session") == session &&
                entry.getString("stableKey") == key) return entry.getInt("loadSteps")
        }
        return null
    }
    val priorItems = before.items.associateBy { it.localId }
    val derived = mutableMapOf<String, Double>()

    for (blockIndex in 0 until blocks.length()) {
        val block = blocks.getJSONObject(blockIndex)
        val spec = specifications.getJSONObject(blockIndex)
        val weeks = layout.blocks[blockIndex].weeks
        val stages = BBlockPeriodization.steps(layout.blocks[blockIndex])
        val adjustment = spec.optString("adjustment", BVolumeAdjustment.AUTO.name)
        val loadFirst = adjustment == BVolumeAdjustment.LOAD_FIRST.name ||
            (adjustment == BVolumeAdjustment.AUTO.name &&
                block.optString("focus") == BBalancedGoal.STRENGTH.name)

        // A break of 1..6 days moves the next block's "first week" across
        // calendar-week boundaries. Anchor by actual scheduled day, never week index.
        for (sessionDay in 1..7) {
            val firstSlot = calendar.slot(blockIndex, 0, sessionDay)
            val anchors = items.filter { item ->
                item.weekNumber == firstSlot.week && item.dayOfWeek == firstSlot.day
            }
            for (anchor in anchors) {
                val previous = priorItems[anchor.localId] ?: continue
                val weight = ProgramSetPrescriptionResolver.resolve(anchor).map { it.weightKg }
                    .distinct().singleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: continue
                val previousWeight = ProgramSetPrescriptionResolver.resolve(previous).map { it.weightKg }
                    .distinct().singleOrNull()
                if (previousWeight == weight) continue
                for (laterWeek in 1 until weeks) {
                    val laterSlot = calendar.slot(blockIndex, laterWeek, sessionDay)
                    val target = items.firstOrNull { it.weekNumber == laterSlot.week &&
                        it.dayOfWeek == laterSlot.day && it.exerciseStableKey == anchor.exerciseStableKey
                    } ?: continue
                    // Previously derived loads follow the edited anchor. A manual
                    // future-week override is never silently overwritten.
                    if (target.weightSource != "B_BLOCK_USER_START_LOAD_DERIVED" &&
                        ProgramSetPrescriptionResolver.resolve(target).any { it.weightKg > 0.0 }) continue
                    val recordedSteps = loadSteps(blockIndex + 1, laterWeek, sessionDay, anchor.exerciseStableKey)
                    val steps = recordedSteps ?: (if (loadFirst) stages[laterWeek] else 0)
                    val kind = equipmentKinds?.optString(anchor.exerciseStableKey)
                        ?.let { runCatching { BEquipmentIncrementKind.valueOf(it) }.getOrNull() }
                        ?: BBlockEquipmentIncrements.kind(anchor.exerciseStableKey, null)
                    val increment = units.incrementFor(kind)
                    val newKg = if (increment == null) weight else weight + steps * increment
                    if (newKg > 0.0) derived[target.localId] = newKg
                }
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
