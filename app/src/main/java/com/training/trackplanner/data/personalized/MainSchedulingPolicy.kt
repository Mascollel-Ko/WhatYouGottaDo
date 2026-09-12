package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*

/** Existing materialization semantics, shared with pre-materialization scheduling. */
internal object MainSchedulingPolicy {
    fun role(item: PlannedExercise, continuity: Boolean): ProgressionRole =
        if (continuity && item.styleVariant !in setOf("LIGHT", "VOLUME")) ProgressionRole.MAIN else ProgressionRole.ASSISTANCE

    private val ordinaryStyles = setOf(StrengthProgrammingStyle.NONE, StrengthProgrammingStyle.STRAIGHT_5X5,
        StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS)
    fun ordinary(item: PlannedExercise, prescription: PlannedPrescription) = item.style in ordinaryStyles &&
        item.styleVariant.isBlank() && prescription.sets.isNotEmpty() && prescription.sets.map { it.copy(setIndex=0) }.distinct().size == 1

    fun ordinaryMain(row: ProgramSkeletonItem, source: PlannedExercise, plan: GeneratedProgramSkeleton): Boolean {
        if (!isMain(row,plan) || row.requiredTemplateAnchor || row.progressionVariant.isNotBlank() ||
            source.style !in ordinaryStyles || source.styleVariant.isNotBlank() ||
            row.progressionStyle !in ordinaryStyles.map { it.name } + "" ||
            row.setPrescriptions.isEmpty() || row.setPrescriptions.map { it.copy(setIndex=0) }.distinct().size != 1) return false
        val binding = row.progressionBinding ?: return true // Initial generated rows are bound after planning.
        return plan.progressionSessions.any { it.key == binding.sessionKey } && binding.signature.variant.isBlank() &&
            binding.signature.style in ordinaryStyles.map { it.name } + ""
    }
    fun isMain(row: ProgramSkeletonItem, plan: GeneratedProgramSkeleton) = row.progressionRole == ProgressionRole.MAIN ||
        row.progressionRole == ProgressionRole.AUTO && plan.progressionSessions.any { it.key == row.progressionBinding?.sessionKey && it.track.role == ProgressionRole.MAIN }
}

internal data class MainLayoutObjective(val sameDayMainExcess: Int, val maxMainCountPerDay: Int, val threeDayStreaks: Int): Comparable<MainLayoutObjective> {
    override fun compareTo(other: MainLayoutObjective) = compareValuesBy(this,other,
        MainLayoutObjective::sameDayMainExcess,MainLayoutObjective::maxMainCountPerDay,MainLayoutObjective::threeDayStreaks)
    companion object {
        fun of(counts: Map<Int,Int>): MainLayoutObjective = MainLayoutObjective(counts.values.sumOf { (it-1).coerceAtLeast(0) },
            counts.values.maxOrNull() ?: 0, (1..7).count { start -> (0..2).all { offset -> counts.getOrDefault((start-1+offset)%7+1,0)>0 } })
    }
}

/** Existing branch-and-bound over ordinary MAIN/primary rows; never changes funded content or active days. */
internal object InitialMainPlacement {
    fun review(baseline: Map<Int,List<TimedPlannedExercise>>, minutes: Int, snapshot: PlanningHistorySnapshot?, robust: Boolean,
        state: AthletePlanningState? = null,
        isMain: (PlannedExercise)->Boolean): Map<Int,List<TimedPlannedExercise>> {
        // Funding/conditional-split trials do not opt into this post-authorization review.
        if (state == null && baseline.values.flatten().none { isMain(it.item) }) return baseline
        if (baseline.values.flatten().none { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) }) return baseline
        val days = baseline.keys.sorted()
        val actual = RecordBasedReviewedPolicy.defaultSchedule(1,days.size).getValue(1).sorted()
        val moving = baseline.values.flatten().filter { (isMain(it.item) || StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey)) && MainSchedulingPolicy.ordinary(it.item,it.prescription) }
            .sortedByDescending { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) }
        if (moving.isEmpty()) return baseline
        val fixed = baseline.mapValues { (_,rows) -> rows.filterNot { candidate -> moving.any { it === candidate } }.toMutableList() }
        fun objective(layout: Map<Int,List<TimedPlannedExercise>>) = InitialStrengthLayout(
            StrengthPrimaryMainPolicy.counts(layout.values.map { rows -> rows.count { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) } }),
            MainLayoutObjective.of(layout.mapKeys { actual[days.indexOf(it.key)] }.mapValues { (_,rows) -> rows.count { isMain(it.item) } }))
        var best = baseline
        var bestObjective = objective(best)
        val totalMain = baseline.values.flatten().count { isMain(it.item) }
        val occupied = minOf(totalMain, days.size)
        val minimumStreak = (0 until (1 shl days.size)).filter { Integer.bitCount(it) == occupied }.minOf { mask ->
            MainLayoutObjective.of(actual.mapIndexed { index,day -> day to if(mask and (1 shl index)!=0) 1 else 0 }.toMap()).threeDayStreaks
        }
        val primaryCount = baseline.values.flatten().count { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) }
        val lowerBound = InitialStrengthLayout(StrengthPrimaryObjective((primaryCount-days.size).coerceAtLeast(0),(primaryCount+days.size-1)/days.size),
            MainLayoutObjective((totalMain-days.size).coerceAtLeast(0),(totalMain+days.size-1)/days.size,minimumStreak))
        fun legal(layout: Map<Int,List<TimedPlannedExercise>>): Boolean {
            if (primaryCount == 0 || snapshot == null) return true
            fun projected(source: Map<Int,List<TimedPlannedExercise>>) = source.entries.flatMap { (day,rows) -> rows.mapIndexed { index,row ->
                residualItem(snapshot,row.item,row.prescription,"primary_${day}_$index",actual[days.indexOf(day)],index+1)
            } }
            val rows = projected(layout)
            val prior = projected(baseline)
            val moved = layout.values.flatten().filter { row -> layout.entries.first { row in it.value }.key != baseline.entries.first { row in it.value }.key }
                .map { it.item.stableKey }.toSet()
            if (moved.any { snapshot.explicitlyRestricted(it) || state != null && !postProcessTissueAllowed(snapshot,state,it) }) return false
            if (state != null) {
                val protected = PrimaryStrengthAnchorSpacingPolicy.keys(snapshot,state,baseline.values.flatten().filter { isMain(it.item) }.mapTo(mutableSetOf()) { it.item.stableKey })
                if (!PrimaryStrengthAnchorSpacingPolicy.allowedRows(rows,protected)) return false
            }
            if (actual.any { day -> rows.filter { it.dayOfWeek==day } != prior.filter { it.dayOfWeek==day } &&
                    snapshot.planDayProjection?.evaluate(rows.filter { it.dayOfWeek==day })?.feasible == false }) return false
            val tissue = snapshot.planWeekTissueProjection ?: return true
            val before = tissue.evaluate(prior,8.5)
            val after = tissue.evaluate(rows,8.5)
            return after.diagnostic=="CANONICAL_RCV_PROJECTION" && after.days.all { day -> day.blockedUnits.isEmpty() &&
                day.unresolvedKeys.none { it in moved } && day.unresolvedKeys.all { it in before.days.firstOrNull { old -> old.day==day.day }?.unresolvedKeys.orEmpty() } }
        }
        var nodes = 0
        fun lower(row: TimedPlannedExercise) = snapshot?.let { it.movementCoverage(row.item.stableKey) in
            setOf(MovementCoverage.LOWER_KNEE,MovementCoverage.POSTERIOR_CHAIN,MovementCoverage.CALVES) ||
            it.metadata[row.item.stableKey]?.jointTendonImpactStressLevel in setOf("HIGH","VERY_HIGH") } == true
        fun search(index: Int) {
            if (bestObjective == lowerBound) return
            if (++nodes > 200_000) return // Best known feasible baseline is always retained.
            if (objective(fixed) > bestObjective) return
            if (index == moving.size) {
                val candidate = objective(fixed)
                if (candidate < bestObjective && legal(fixed)) { bestObjective=candidate; best=fixed.mapValues { it.value.toList() } }
                return
            }
            val row = moving[index]
            val targets = days.filter { day -> fixed.getValue(day).none { it.item.stableKey == row.item.stableKey } &&
                fixed.getValue(day).sumOf { it.estimatedSeconds } + row.estimatedSeconds <= minutes*60 }
                .sortedWith(compareBy<Int> { day ->
                    fixed.getValue(day).add(row); val value=objective(fixed); fixed.getValue(day).removeAt(fixed.getValue(day).lastIndex); value
                }.thenBy { day -> if(lower(row)) fixed.getValue(day).filter(::lower).sumOf { it.estimatedSeconds } else 0 }
                    .thenBy { day -> if(robust && row.item.scheduleTier()==ScheduleTier.CORE_MUST_DO && day>(days.size+1)/2) 1 else 0 }
                    .thenBy { day -> fixed.getValue(day).sumOf { it.estimatedSeconds } }.thenBy { it })
            for(day in targets) {
                fixed.getValue(day).add(row); search(index+1); fixed.getValue(day).removeAt(fixed.getValue(day).lastIndex)
            }
        }
        search(0)
        return best
    }
}

private data class InitialStrengthLayout(val primary: StrengthPrimaryObjective, val broadMain: MainLayoutObjective): Comparable<InitialStrengthLayout> {
    override fun compareTo(other: InitialStrengthLayout) = compareValuesBy(this,other,InitialStrengthLayout::primary,InitialStrengthLayout::broadMain)
}
