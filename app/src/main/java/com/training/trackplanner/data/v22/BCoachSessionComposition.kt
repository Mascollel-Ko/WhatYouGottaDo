package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.personalized.StrengthPrimaryMainPolicy

internal object BCoachSessionComposition {
    const val MAX_VARIANTS = 2
    private val missing = setOf("", "NONE", "UNKNOWN", "NOT_APPLICABLE", "UNCLASSIFIED")

    private fun family(item: ProgramSkeletonItem): Pair<String, String>? =
        if (item.redundancyGroup in missing || item.movementFamily in missing) null
        else item.redundancyGroup to item.movementFamily

    fun families(day: List<ProgramSkeletonItem>) = day.groupBy(::family).filterKeys { it != null }.values

    fun isFixedInSession(item: ProgramSkeletonItem) =
        item.requiredTemplateAnchor || item.progressionBinding != null || item.progressionAnchorSetIndex != null ||
            item.directSportSession || item.rehabLikeActivation || item.selectionRole.contains("PEAK", true) ||
            BCoachV2DoseEvaluator.isExplicitUserExerciseLock(item.selectionReason) ||
            ProgramSetPrescriptionResolver.resolve(item).any {
                BCoachV2LoadCandidateResolver.isSetUserFixed(it, item.weightSource)
            }

    private fun priority(item: ProgramSkeletonItem, mains: Set<String>) = when {
        StrengthPrimaryMainPolicy.isPrimary(item.exerciseStableKey) -> 0
        item.exerciseStableKey in mains || item.selectionRole.contains("MAIN", true) -> 1
        else -> 2
    }

    // Preparatory work and user-controlled positions retain their original slots.
    fun mainFirst(day: List<ProgramSkeletonItem>, mains: Set<String>): List<ProgramSkeletonItem> {
        val ordered = day.sortedBy { it.orderIndex }
        if (ordered.none { priority(it, mains) < 2 }) return ordered
        val mainFamilies = ordered.filter { priority(it, mains) < 2 }.mapNotNull(::family).toSet()
        val movable = ordered.filter { !isFixedInSession(it) && (
            priority(it, mains) < 2 || family(it) in mainFamilies || it.selectionRole.contains("ACCESSORY", true) ||
                it.selectionRole.contains("ASSIST", true) || it.selectionRole.contains("HYPERTROPHY", true)
            ) }
        val slots = movable.map { it.localId }.toSet()
        val sorted = movable.sortedBy { priority(it, mains) }.iterator()
        return ordered.map { if (it.localId in slots) sorted.next().copy(orderIndex = it.orderIndex) else it }
    }

    private fun region(item: ProgramSkeletonItem): String? = when (item.primaryStressProfile) {
        "HORIZONTAL_PUSH_STRESS", "OVERHEAD_PUSH_STRESS", "BODYWEIGHT_PUSH_STRESS", "DIP_PUSH_STRESS" -> "PUSH"
        "HORIZONTAL_ROW_STRESS", "VERTICAL_PULL_STRESS" -> "PULL"
        "HEAVY_AXIAL_LOWER_STRESS", "MACHINE_LOWER_BODY_STRESS", "HINGE_POSTERIOR_CHAIN_STRESS",
        "UNILATERAL_LOWER_STABILITY_STRESS", "LUNGE_DECELERATION_STRESS" -> "LOWER"
        "ISOMETRIC_CORE_BRACING_STRESS", "ROTATIONAL_CORE_STABILITY_STRESS", "DYNAMIC_CORE_FLEXION_STRESS",
        "ANTI_ROTATION_CORE_STABILITY_STRESS", "LOADED_CARRY_BRACING_STRESS" -> "TRUNK"
        else -> null
    }

    // Compare each region with its own weekly work. No target push/pull or upper/lower ratio.
    fun concentration(week: List<ProgramSkeletonItem>): Double {
        if (week.distinctBy { it.dayOfWeek }.size < 2) return 0.0
        val excess = week.filter { region(it) != null }.groupBy(::region).values.sumOf { items ->
            val total = items.sumOf { it.setCount }
            val peak = items.groupBy { it.dayOfWeek }.values.maxOf { day -> day.sumOf { it.setCount } }
            (peak - maxOf(4, (total + 1) / 2)).coerceAtLeast(0)
        }
        return excess.toDouble() / week.sumOf { it.setCount }.coerceAtLeast(1)
    }

    /** Repair all occurrences in a block before beam pruning. Compare destination and prescription choices. */
    fun neighbors(plan: GeneratedProgramSkeleton, mains: Set<String>, exercises: Map<String, Exercise>,
        calendar: BBlockCalendar? = null):
        List<Pair<String, GeneratedProgramSkeleton>> = buildList {
        val sessions = plan.items.groupBy { it.weekNumber to it.dayOfWeek }
        val groups = sessions.values.flatMap(::families)
        val limit = if (groups.any { it.size > MAX_VARIANTS }) MAX_VARIANTS else 1
        val crowded = groups.filter { it.size > limit }
        if (crowded.isEmpty()) return@buildList
        val movableKeys = crowded.flatten().filterNot(::isFixedInSession).distinctBy { it.exerciseStableKey }
            .sortedByDescending { priority(it, mains) }.map { it.exerciseStableKey }
        val layouts = mutableSetOf<List<Pair<String, Int>>>()
        for (preferred in movableKeys.take(4)) {
            for (firstDay in plan.items.map { it.dayOfWeek }.distinct().sorted()) {
                val days = sessions.mapValues { it.value.toMutableList() }
                val moved = mutableSetOf<String>()
                var changed = false
                for ((slot, rows) in days) {
                    for (group in families(rows)) {
                        val choices = group.filterNot(::isFixedInSession).sortedWith(
                            compareByDescending<ProgramSkeletonItem> { it.exerciseStableKey == preferred }
                                .thenByDescending { priority(it, mains) }.thenByDescending { it.orderIndex })
                        for (item in choices.take((group.size - limit).coerceAtLeast(0))) {
                            val places = days.filter { (other, dest) ->
                                other.first == slot.first && other != slot &&
                                    calendar?.blockIndex(other.first, other.second) == calendar?.blockIndex(slot.first, slot.second) &&
                                    dest.none { it.exerciseStableKey == item.exerciseStableKey } &&
                                    dest.count { family(it) == family(item) } < limit
                            }
                            val dest = places.entries.minWithOrNull(compareBy(
                                { it.value.count { row -> family(row) == family(item) } },
                                { if (it.key.second == firstDay) 0 else 1 },
                                { BCoachV2ScheduleLoad.timeUpper(it.value, exercises) }, { it.key.second }))
                            if (dest != null) {
                                rows.remove(item)
                                dest.value += item.copy(dayOfWeek = dest.key.second,
                                    orderIndex = dest.value.maxOf { it.orderIndex } + 1)
                                moved += item.localId
                                changed = true
                            } else if (limit == MAX_VARIANTS &&
                                priority(item, mains) == choices.maxOf { priority(it, mains) }) {
                                rows.remove(item)
                                changed = true
                            }
                        }
                    }
                }
                if (!changed) continue
                val layout = plan.copy(items = days.values.flatMap { mainFirst(it, mains) })
                if (!layouts.add(layout.items.map { it.localId to it.dayOfWeek })) continue
                add("REBALANCE_SESSION_${preferred}_D$firstDay" to layout)
                if (moved.isEmpty()) continue
                for (delta in listOf(-1, 1)) {
                    add("REBALANCE_SETS_${delta}_D$firstDay" to layout.copy(items = layout.items.map {
                        if (it.localId in moved) BCoachV2GlobalSelector.adjustSets(it, delta, mains) else it
                    }))
                    add("REBALANCE_REPS_${delta}_D$firstDay" to layout.copy(items = layout.items.map {
                        if (it.localId in moved) BCoachV2GlobalSelector.adjustReps(it, delta) else it
                    }))
                }
                if (layouts.size >= 8) return@buildList
            }
        }
    }
}
