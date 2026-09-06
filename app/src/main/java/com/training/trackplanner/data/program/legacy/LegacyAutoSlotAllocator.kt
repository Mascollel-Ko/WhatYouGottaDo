package com.training.trackplanner.data.program.legacy

// Mechanically isolated from f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9.
// Frozen product rules: do not generalize or route through another planner.
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.ProgramUserNoticeLevel
import com.training.trackplanner.data.ProgramSetPrescription

internal data class LegacyAutoAllocationContext(
    val request: LegacyAutoRequest,
    val exercises: List<Exercise>,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val daySlotIndex: Int,
    val globalDayIndex: Int,
    val dayRule: LegacyAutoDayRule,
    val intensityByArea: Map<LegacyAutoMainArea, LegacyAutoIntensityLabel>,
    val usage: LegacyAutoUsage
)

internal class LegacyAutoSlotAllocator {
    fun allocate(context: LegacyAutoAllocationContext): List<LegacyAutoSkeletonItem> {
        val caps = LegacyAutoRuleTables.slotCaps(context.request.sessionMinutes)
        val items = mutableListOf<LegacyAutoSkeletonItem>()
        val mainLabel = context.dayRule.mainArea?.let { context.intensityByArea[it] }
        val highIntensityMain = mainLabel == LegacyAutoIntensityLabel.HIGH_LOW

        context.dayRule.mainArea?.let { area ->
            if (items.size < caps.totalSlots) {
                items += item(
                    context = context,
                    spec = chooseMain(area, context.usage),
                    orderIndex = items.size + 1,
                    prescription = LegacyAutoIntensityResolver.main(
                        label = context.intensityByArea[area] ?: LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
                        area = area,
                        weekNumber = context.weekNumber
                    ),
                    reason = "Main / ${area.label}",
                    trainingSlot = "MAIN_${area.name}"
                )
            }
        }
        context.dayRule.secondaryMainArea?.let { area ->
            if (
                items.size < caps.totalSlots &&
                items.count { it.selectionRole == LegacyAutoAutoSlotType.MAIN.name } < caps.mainCap
            ) {
                val tableLabel = context.intensityByArea[area] ?: LegacyAutoIntensityLabel.MEDIUM_MEDIUM
                val label = if (tableLabel == LegacyAutoIntensityLabel.DELOAD) {
                    LegacyAutoIntensityLabel.DELOAD
                } else {
                    LegacyAutoIntensityLabel.LOW_HIGH
                }
                items += item(
                    context = context,
                    spec = chooseMain(area, context.usage),
                    orderIndex = items.size + 1,
                    prescription = LegacyAutoIntensityResolver.main(
                        label = label,
                        area = area,
                        weekNumber = context.weekNumber
                    ),
                    reason = "Main / Secondary ${area.label}",
                    trainingSlot = "MAIN_${area.name}"
                )
            }
        }

        val badmintonTarget = LegacyAutoRuleTables.badmintonTargetCount(
            ratio = context.request.badmintonTransferRatio,
            sessionMinutes = context.request.sessionMinutes,
            globalDayIndex = context.globalDayIndex
        )
        repeat(badmintonTarget.coerceAtMost(caps.totalSlots - items.size)) {
            val category = chooseBadmintonCategory(context.usage)
            items += item(
                context = context,
                spec = chooseBadminton(category, context.usage),
                orderIndex = items.size + 1,
                prescription = LegacyAutoIntensityResolver.badminton(category),
                reason = "Badminton / ${category.label}",
                trainingSlot = "BADMINTON_${category.name}"
            )
        }

        for (area in context.dayRule.pairedPriorities) {
            if (items.size >= caps.totalSlots) break
            items += item(
                context = context,
                spec = choosePaired(area, context.usage, context.exercises, items),
                orderIndex = items.size + 1,
                prescription = LegacyAutoIntensityResolver.strengthAccessory(highIntensityMain),
                reason = "Strength accessory / Paired ${area.label}",
                trainingSlot = "PAIRED_${area.name}"
            )
        }

        while (items.size < caps.totalSlots) {
            val part = chooseSmallPart(context.usage)
            items += item(
                context = context,
                spec = chooseSmall(part, context.usage),
                orderIndex = items.size + 1,
                prescription = LegacyAutoIntensityResolver.strengthAccessory(highIntensityMain),
                reason = "Strength accessory / ${part.label}",
                trainingSlot = "SMALL_${part.name}"
            )
        }

        return items
    }

    private fun item(
        context: LegacyAutoAllocationContext,
        spec: LegacyAutoExerciseSpec,
        orderIndex: Int,
        prescription: LegacyAutoPrescriptionGuide,
        reason: String,
        trainingSlot: String
    ): LegacyAutoSkeletonItem {
        val resolved = spec.resolve(context.exercises)
        context.usage.record(spec)
        return LegacyAutoSkeletonItem(
            localId = "w${context.weekNumber}d${context.daySlotIndex}o$orderIndex",
            weekNumber = context.weekNumber,
            dayOfWeek = context.dayOfWeek,
            orderIndex = orderIndex,
            exerciseStableKey = resolved.exerciseStableKey,
            exerciseName = resolved.exerciseName,
            category = resolved.category,
            restSeconds = prescription.restSeconds,
            prescription = prescription.text,
            setCount = prescription.setCount,
            reps = prescription.reps,
            weightKg = 0.0,
            seconds = prescription.seconds,
            selectionReason = reason,
            weightSource = prescription.weightSource,
            trainingSlot = trainingSlot,
            stableKey = resolved.stableKey,
            selectionRole = spec.slotType.name,
            movementFamily = spec.substitutionGroup.orEmpty(),
            requestedTemplateSlot = context.dayRule.label,
            requiredTemplateAnchor = spec.slotType == LegacyAutoAutoSlotType.MAIN
        )
    }

    private fun chooseMain(area: LegacyAutoMainArea, usage: LegacyAutoUsage): LegacyAutoExerciseSpec =
        choose(LegacyAutoRuleTables.mainExercises.getValue(area), usage)

    private fun choosePaired(
        area: LegacyAutoMainArea,
        usage: LegacyAutoUsage,
        exercises: List<Exercise>,
        currentItems: List<LegacyAutoSkeletonItem>
    ): LegacyAutoExerciseSpec {
        val pool = LegacyAutoRuleTables.pairedAccessories.getValue(area)
        val mainSquatStableKey = currentItems
            .firstOrNull { it.trainingSlot == "MAIN_${LegacyAutoMainArea.LOWER_ANTERIOR.name}" }
            ?.stableKey
            ?.takeIf { it.isNotBlank() }
        if (area != LegacyAutoMainArea.LOWER_ANTERIOR || mainSquatStableKey == null) return choose(pool, usage)
        val sorted = pool.sortedWith(programExerciseSpecComparator(pool, usage))
        return sorted
            .firstOrNull {
                val stableKey = it.resolve(exercises).stableKey
                stableKey.isNotBlank() && stableKey != mainSquatStableKey
            }
            ?: sorted.firstOrNull { it.resolve(exercises).stableKey != mainSquatStableKey }
            ?: choose(pool, usage)
    }

    private fun chooseSmall(part: LegacyAutoSmallPart, usage: LegacyAutoUsage): LegacyAutoExerciseSpec =
        choose(LegacyAutoRuleTables.smallPartAccessories.getValue(part), usage)

    private fun chooseBadminton(category: LegacyAutoBadmintonCategory, usage: LegacyAutoUsage): LegacyAutoExerciseSpec =
        choose(LegacyAutoRuleTables.badmintonAccessories.getValue(category), usage)

    private fun choose(pool: List<LegacyAutoExerciseSpec>, usage: LegacyAutoUsage): LegacyAutoExerciseSpec =
        pool.minWith(programExerciseSpecComparator(pool, usage))

    private fun programExerciseSpecComparator(
        pool: List<LegacyAutoExerciseSpec>,
        usage: LegacyAutoUsage
    ): Comparator<LegacyAutoExerciseSpec> =
        compareBy<LegacyAutoExerciseSpec> { usage.exerciseCount(it.stableKey) }
            .thenBy { usage.groupCount(it.substitutionGroup.orEmpty()) }
            .thenBy { pool.indexOf(it) }

    private fun chooseBadmintonCategory(usage: LegacyAutoUsage): LegacyAutoBadmintonCategory {
        val categories = LegacyAutoBadmintonCategory.entries
        return categories
            .filter { it != usage.lastBadmintonCategory || categories.size == 1 }
            .minWith(compareBy<LegacyAutoBadmintonCategory> { usage.badmintonCount(it) }.thenBy { categories.indexOf(it) })
    }

    private fun chooseSmallPart(usage: LegacyAutoUsage): LegacyAutoSmallPart {
        val parts = LegacyAutoSmallPart.entries
        return parts
            .filter { it != usage.lastSmallPart || parts.size == 1 }
            .minWith(compareBy<LegacyAutoSmallPart> { usage.smallPartCount(it) }.thenBy { parts.indexOf(it) })
    }
}

internal class LegacyAutoUsage {
    var lastBadmintonCategory: LegacyAutoBadmintonCategory? = null
        private set
    var lastSmallPart: LegacyAutoSmallPart? = null
        private set

    private val exerciseCounts = mutableMapOf<String, Int>()
    private val groupCounts = mutableMapOf<String, Int>()
    private val badmintonCounts = mutableMapOf<LegacyAutoBadmintonCategory, Int>()
    private val smallPartCounts = mutableMapOf<LegacyAutoSmallPart, Int>()

    fun record(spec: LegacyAutoExerciseSpec) {
        exerciseCounts[spec.stableKey] = exerciseCount(spec.stableKey) + 1
        spec.substitutionGroup?.let { groupCounts[it] = groupCount(it) + 1 }
        spec.badmintonCategory?.let {
            badmintonCounts[it] = badmintonCount(it) + 1
            lastBadmintonCategory = it
        }
        spec.strengthBodyPart?.let {
            smallPartCounts[it] = smallPartCount(it) + 1
            lastSmallPart = it
        }
    }

    fun exerciseCount(name: String): Int = exerciseCounts[name] ?: 0
    fun groupCount(group: String): Int = groupCounts[group] ?: 0
    fun badmintonCount(category: LegacyAutoBadmintonCategory): Int = badmintonCounts[category] ?: 0
    fun smallPartCount(part: LegacyAutoSmallPart): Int = smallPartCounts[part] ?: 0
}
