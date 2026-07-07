package com.training.trackplanner.data

internal data class ProgramAllocationContext(
    val request: ProgramSkeletonRequest,
    val exercises: List<Exercise>,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val daySlotIndex: Int,
    val globalDayIndex: Int,
    val dayRule: ProgramDayRule,
    val intensityByArea: Map<ProgramMainArea, ProgramIntensityLabel>,
    val usage: ProgramAutoUsage
)

internal class ProgramSlotAllocator {
    fun allocate(context: ProgramAllocationContext): List<ProgramSkeletonItem> {
        val caps = ProgramRuleTables.slotCaps(context.request.sessionMinutes)
        val items = mutableListOf<ProgramSkeletonItem>()
        val mainLabel = context.dayRule.mainArea?.let { context.intensityByArea[it] }
        val highIntensityMain = mainLabel == ProgramIntensityLabel.HIGH_LOW

        context.dayRule.mainArea?.let { area ->
            if (items.size < caps.totalSlots) {
                items += item(
                    context = context,
                    spec = chooseMain(area, context.usage),
                    orderIndex = items.size + 1,
                    prescription = ProgramIntensityResolver.main(
                        label = context.intensityByArea[area] ?: ProgramIntensityLabel.MEDIUM_MEDIUM,
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
                items.count { it.selectionRole == ProgramAutoSlotType.MAIN.name } < caps.mainCap
            ) {
                val tableLabel = context.intensityByArea[area] ?: ProgramIntensityLabel.MEDIUM_MEDIUM
                val label = if (tableLabel == ProgramIntensityLabel.DELOAD) {
                    ProgramIntensityLabel.DELOAD
                } else {
                    ProgramIntensityLabel.LOW_HIGH
                }
                items += item(
                    context = context,
                    spec = chooseMain(area, context.usage),
                    orderIndex = items.size + 1,
                    prescription = ProgramIntensityResolver.main(
                        label = label,
                        area = area,
                        weekNumber = context.weekNumber
                    ),
                    reason = "Main / Secondary ${area.label}",
                    trainingSlot = "MAIN_${area.name}"
                )
            }
        }

        val badmintonTarget = ProgramRuleTables.badmintonTargetCount(
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
                prescription = ProgramIntensityResolver.badminton(category),
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
                prescription = ProgramIntensityResolver.strengthAccessory(highIntensityMain),
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
                prescription = ProgramIntensityResolver.strengthAccessory(highIntensityMain),
                reason = "Strength accessory / ${part.label}",
                trainingSlot = "SMALL_${part.name}"
            )
        }

        return items
    }

    private fun item(
        context: ProgramAllocationContext,
        spec: ProgramExerciseSpec,
        orderIndex: Int,
        prescription: ProgramPrescriptionGuide,
        reason: String,
        trainingSlot: String
    ): ProgramSkeletonItem {
        val resolved = spec.resolve(context.exercises)
        context.usage.record(spec)
        return ProgramSkeletonItem(
            localId = "w${context.weekNumber}d${context.daySlotIndex}o$orderIndex",
            weekNumber = context.weekNumber,
            dayOfWeek = context.dayOfWeek,
            orderIndex = orderIndex,
            exerciseId = resolved.exerciseId,
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
            requiredTemplateAnchor = spec.slotType == ProgramAutoSlotType.MAIN
        )
    }

    private fun chooseMain(area: ProgramMainArea, usage: ProgramAutoUsage): ProgramExerciseSpec =
        choose(ProgramRuleTables.mainExercises.getValue(area), usage)

    private fun choosePaired(
        area: ProgramMainArea,
        usage: ProgramAutoUsage,
        exercises: List<Exercise>,
        currentItems: List<ProgramSkeletonItem>
    ): ProgramExerciseSpec {
        val pool = ProgramRuleTables.pairedAccessories.getValue(area)
        val mainSquatStableKey = currentItems
            .firstOrNull { it.trainingSlot == "MAIN_${ProgramMainArea.LOWER_ANTERIOR.name}" }
            ?.stableKey
            ?.takeIf { it.isNotBlank() }
        if (area != ProgramMainArea.LOWER_ANTERIOR || mainSquatStableKey == null) return choose(pool, usage)
        val sorted = pool.sortedWith(programExerciseSpecComparator(pool, usage))
        return sorted
            .firstOrNull {
                val stableKey = it.resolve(exercises).stableKey
                stableKey.isNotBlank() && stableKey != mainSquatStableKey
            }
            ?: sorted.firstOrNull { it.resolve(exercises).stableKey != mainSquatStableKey }
            ?: choose(pool, usage)
    }

    private fun chooseSmall(part: ProgramSmallPart, usage: ProgramAutoUsage): ProgramExerciseSpec =
        choose(ProgramRuleTables.smallPartAccessories.getValue(part), usage)

    private fun chooseBadminton(category: ProgramBadmintonCategory, usage: ProgramAutoUsage): ProgramExerciseSpec =
        choose(ProgramRuleTables.badmintonAccessories.getValue(category), usage)

    private fun choose(pool: List<ProgramExerciseSpec>, usage: ProgramAutoUsage): ProgramExerciseSpec =
        pool.minWith(programExerciseSpecComparator(pool, usage))

    private fun programExerciseSpecComparator(
        pool: List<ProgramExerciseSpec>,
        usage: ProgramAutoUsage
    ): Comparator<ProgramExerciseSpec> =
        compareBy<ProgramExerciseSpec> { usage.exerciseCount(it.displayName) }
            .thenBy { usage.groupCount(it.substitutionGroup.orEmpty()) }
            .thenBy { pool.indexOf(it) }

    private fun chooseBadmintonCategory(usage: ProgramAutoUsage): ProgramBadmintonCategory {
        val categories = ProgramBadmintonCategory.entries
        return categories
            .filter { it != usage.lastBadmintonCategory || categories.size == 1 }
            .minWith(compareBy<ProgramBadmintonCategory> { usage.badmintonCount(it) }.thenBy { categories.indexOf(it) })
    }

    private fun chooseSmallPart(usage: ProgramAutoUsage): ProgramSmallPart {
        val parts = ProgramSmallPart.entries
        return parts
            .filter { it != usage.lastSmallPart || parts.size == 1 }
            .minWith(compareBy<ProgramSmallPart> { usage.smallPartCount(it) }.thenBy { parts.indexOf(it) })
    }
}

internal class ProgramAutoUsage {
    var lastBadmintonCategory: ProgramBadmintonCategory? = null
        private set
    var lastSmallPart: ProgramSmallPart? = null
        private set

    private val exerciseCounts = mutableMapOf<String, Int>()
    private val groupCounts = mutableMapOf<String, Int>()
    private val badmintonCounts = mutableMapOf<ProgramBadmintonCategory, Int>()
    private val smallPartCounts = mutableMapOf<ProgramSmallPart, Int>()

    fun record(spec: ProgramExerciseSpec) {
        exerciseCounts[spec.displayName] = exerciseCount(spec.displayName) + 1
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
    fun badmintonCount(category: ProgramBadmintonCategory): Int = badmintonCounts[category] ?: 0
    fun smallPartCount(part: ProgramSmallPart): Int = smallPartCounts[part] ?: 0
}
