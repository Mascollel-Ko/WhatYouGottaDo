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
internal class InitialMainPlacementMetrics {
    var searchNodes: Int = 0
    var searchLeaves: Int = 0
    var objectiveEvaluations: Int = 0
    var legalChecks: Int = 0
    var baselineProjectionEvaluations: Int = 0
    var dayProjectionEvaluations: Int = 0
    var weekTissueEvaluations: Int = 0
    var bestUpdates: Int = 0
    var lowerBoundPrunes: Int = 0
}

internal object InitialMainPlacement {
    fun review(baseline: Map<Int,List<TimedPlannedExercise>>, minutes: Int, snapshot: PlanningHistorySnapshot?, robust: Boolean,
        state: AthletePlanningState? = null,
        metrics: InitialMainPlacementMetrics? = null,
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
        val dayIndex = days.withIndex().associate { it.value to it.index }
        val primaryByIdentity = java.util.IdentityHashMap<TimedPlannedExercise, Boolean>()
        val mainByIdentity = java.util.IdentityHashMap<TimedPlannedExercise, Boolean>()
        val lowerByIdentity = java.util.IdentityHashMap<TimedPlannedExercise, Boolean>()
        val baselineDayByIdentity = java.util.IdentityHashMap<TimedPlannedExercise, Int>()
        val currentDayByIdentity = java.util.IdentityHashMap<TimedPlannedExercise, Int>()
        baseline.values.flatten().forEach { row ->
            primaryByIdentity[row] = StrengthPrimaryMainPolicy.isPrimary(row.item.stableKey)
            mainByIdentity[row] = isMain(row.item)
        }
        fun isPrimary(row: TimedPlannedExercise) = primaryByIdentity[row] ?: StrengthPrimaryMainPolicy.isPrimary(row.item.stableKey)
        fun isMainRow(row: TimedPlannedExercise) = mainByIdentity[row] ?: isMain(row.item)
        val primaryCounts = IntArray(days.size)
        val mainCounts = IntArray(days.size)
        val seconds = IntArray(days.size)
        val lowerSeconds = IntArray(days.size)
        val stableKeyCounts = days.associateWith { mutableMapOf<String, Int>() }
        fun lowerValue(row: TimedPlannedExercise): Boolean = snapshot?.let { it.movementCoverage(row.item.stableKey) in
            setOf(MovementCoverage.LOWER_KNEE,MovementCoverage.POSTERIOR_CHAIN,MovementCoverage.CALVES) ||
            it.metadata[row.item.stableKey]?.jointTendonImpactStressLevel in setOf("HIGH","VERY_HIGH") } == true
        fun addCounters(day: Int, row: TimedPlannedExercise) {
            val index = dayIndex.getValue(day)
            if (isPrimary(row)) primaryCounts[index]++
            if (isMainRow(row)) mainCounts[index]++
            seconds[index] += row.estimatedSeconds
            if ((lowerByIdentity[row] ?: lowerValue(row)).also { lowerByIdentity[row] = it }) lowerSeconds[index] += row.estimatedSeconds
            val keys = stableKeyCounts.getValue(day)
            keys[row.item.stableKey] = (keys[row.item.stableKey] ?: 0) + 1
        }
        fun removeCounters(day: Int, row: TimedPlannedExercise) {
            val index = dayIndex.getValue(day)
            if (isPrimary(row)) primaryCounts[index]--
            if (isMainRow(row)) mainCounts[index]--
            seconds[index] -= row.estimatedSeconds
            if (lowerByIdentity[row] == true) lowerSeconds[index] -= row.estimatedSeconds
            val keys = stableKeyCounts.getValue(day)
            val count = keys.getValue(row.item.stableKey)
            if (count == 1) keys.remove(row.item.stableKey) else keys[row.item.stableKey] = count - 1
        }
        // Start from the complete baseline so the retained incumbent objective is exact, then
        // remove the movable rows to represent the mutable search layout.
        baseline.forEach { (day, rows) -> rows.forEach {
            baselineDayByIdentity[it] = day
            currentDayByIdentity[it] = day
            addCounters(day, it)
        } }
        fun objective(layout: Map<Int,List<TimedPlannedExercise>>): InitialStrengthLayout {
            metrics?.let { it.objectiveEvaluations++ }
            return InitialStrengthLayout(
                StrengthPrimaryMainPolicy.counts(primaryCounts.asList()),
                MainLayoutObjective.of(days.withIndex().associate { (index, _) -> actual[index] to mainCounts[index] }))
        }
        var best = baseline
        var bestObjective = objective(best)
        baseline.forEach { (day, rows) -> rows.forEach { row -> if (moving.any { it === row }) {
            removeCounters(day, row)
            currentDayByIdentity.remove(row)
        } } }
        val totalMain = baseline.values.flatten().count(::isMainRow)
        val occupied = minOf(totalMain, days.size)
        val minimumStreak = (0 until (1 shl days.size)).filter { Integer.bitCount(it) == occupied }.minOf { mask ->
            MainLayoutObjective.of(actual.mapIndexed { index,day -> day to if(mask and (1 shl index)!=0) 1 else 0 }.toMap()).threeDayStreaks
        }
        val primaryCount = baseline.values.flatten().count(::isPrimary)
        val lowerBound = InitialStrengthLayout(StrengthPrimaryObjective((primaryCount-days.size).coerceAtLeast(0),(primaryCount+days.size-1)/days.size),
            MainLayoutObjective((totalMain-days.size).coerceAtLeast(0),(totalMain+days.size-1)/days.size,minimumStreak))
        // Build the expensive baseline projection only if a legal candidate reaches the final
        // tissue gate. Day-level OFI uses the same per-day representation without materializing a
        // full-week projection for every search node.
        fun projectedDay(source: Map<Int,List<TimedPlannedExercise>>, day: Int): List<ProgramSkeletonItem> =
            source.getValue(day).mapIndexed { index, row ->
                residualItem(snapshot!!, row.item, row.prescription, "primary_${day}_$index", actual[days.indexOf(day)], index + 1)
            }
        fun projected(source: Map<Int,List<TimedPlannedExercise>>) = days.flatMap { day -> projectedDay(source, day) }
        val priorDayCache = mutableMapOf<Int, List<ProgramSkeletonItem>>()
        fun priorDay(day: Int): List<ProgramSkeletonItem> = priorDayCache.getOrPut(day) {
            metrics?.let { it.baselineProjectionEvaluations++ }
            projectedDay(baseline, day)
        }
        val tissue = snapshot?.planWeekTissueProjection
        val before by lazy {
            if (tissue != null && snapshot != null && primaryCount > 0) {
                metrics?.let { it.baselineProjectionEvaluations++ }
                metrics?.let { it.weekTissueEvaluations++ }
                tissue.evaluate(projected(baseline), 8.5)
            } else null
        }
        val protected = if (state != null && snapshot != null) {
            PrimaryStrengthAnchorSpacingPolicy.keys(snapshot,state,baseline.values.flatten().filter { isMain(it.item) }.mapTo(mutableSetOf()) { it.item.stableKey })
        } else emptySet()
        fun lower(row: TimedPlannedExercise): Boolean = lowerByIdentity.getOrPut(row) { lowerValue(row) }
        fun changedDays(): Set<Int> = moving.flatMap { row ->
            val oldDay = baselineDayByIdentity[row]
            val newDay = currentDayByIdentity[row]
            if (oldDay != null && newDay != null && oldDay != newDay) listOf(oldDay, newDay) else emptyList()
        }.toSet()
        fun protectedSpacingAllowed(): Boolean {
            if (protected.isEmpty()) return true
            return protected.all { key ->
                val assigned = baseline.values.flatten().filter { it.item.stableKey == key }
                    .mapNotNull { currentDayByIdentity[it] }
                PrimaryStrengthAnchorSpacingPolicy.allowed(assigned.map { actual[days.indexOf(it)] })
            }
        }
        fun legal(layout: Map<Int,List<TimedPlannedExercise>>): Boolean {
            metrics?.let { it.legalChecks++ }
            if (primaryCount == 0 || snapshot == null) return true
            val changed = changedDays()
            val moved = moving.filter { baselineDayByIdentity[it] != currentDayByIdentity[it] }
                .map { it.item.stableKey }.toSet()
            if (moved.any { snapshot.explicitlyRestricted(it) || state != null && !postProcessTissueAllowed(snapshot,state,it) }) return false
            if (state != null) {
                if (!protectedSpacingAllowed()) return false
            }
            if (changed.any { day ->
                    val candidateDay = projectedDay(layout, day)
                    candidateDay != priorDay(day) &&
                    snapshot.planDayProjection?.also { metrics?.let { it.dayProjectionEvaluations++ } }
                        ?.evaluate(candidateDay)?.feasible == false
                }) return false
            val tissue = snapshot.planWeekTissueProjection ?: return true
            val rows = projected(layout)
            val after = tissue.also { metrics?.let { it.weekTissueEvaluations++ } }.evaluate(rows,8.5)
            return after.diagnostic=="CANONICAL_RCV_PROJECTION" && after.days.all { day -> day.blockedUnits.isEmpty() &&
                day.unresolvedKeys.none { it in moved } && day.unresolvedKeys.all { it in before?.days?.firstOrNull { old -> old.day==day.day }?.unresolvedKeys.orEmpty() } }
        }
        var nodes = 0
        fun search(index: Int) {
            if (bestObjective == lowerBound) { metrics?.let { it.lowerBoundPrunes++ }; return }
            if (++nodes > 200_000) return // Best known feasible baseline is always retained.
            metrics?.searchNodes = nodes
            if (objective(fixed) > bestObjective) return
            if (index == moving.size) {
                metrics?.let { it.searchLeaves++ }
                val candidate = objective(fixed)
                if (candidate < bestObjective && legal(fixed)) { bestObjective=candidate; best=fixed.mapValues { it.value.toList() }; metrics?.let { it.bestUpdates++ } }
                return
            }
            val row = moving[index]
            val targets = days.filter { day -> fixed.getValue(day).none { it.item.stableKey == row.item.stableKey } &&
                stableKeyCounts.getValue(day).getOrDefault(row.item.stableKey, 0) == 0 &&
                seconds[dayIndex.getValue(day)] + row.estimatedSeconds <= minutes*60 }
                .sortedWith(compareBy<Int> { day ->
                    fixed.getValue(day).add(row); addCounters(day, row); val value=objective(fixed); removeCounters(day, row); fixed.getValue(day).removeAt(fixed.getValue(day).lastIndex); value
                }.thenBy { day -> if(lower(row)) lowerSeconds[dayIndex.getValue(day)] else 0 }
                    .thenBy { day -> if(robust && row.item.scheduleTier()==ScheduleTier.CORE_MUST_DO && day>(days.size+1)/2) 1 else 0 }
                    .thenBy { day -> seconds[dayIndex.getValue(day)] }.thenBy { it })
            for(day in targets) {
                fixed.getValue(day).add(row)
                currentDayByIdentity[row] = day
                addCounters(day, row)
                search(index+1)
                removeCounters(day, row)
                currentDayByIdentity.remove(row)
                fixed.getValue(day).removeAt(fixed.getValue(day).lastIndex)
            }
        }
        search(0)
        return best
    }
}

private data class InitialStrengthLayout(val primary: StrengthPrimaryObjective, val broadMain: MainLayoutObjective): Comparable<InitialStrengthLayout> {
    override fun compareTo(other: InitialStrengthLayout) = compareValuesBy(this,other,InitialStrengthLayout::primary,InitialStrengthLayout::broadMain)
}
