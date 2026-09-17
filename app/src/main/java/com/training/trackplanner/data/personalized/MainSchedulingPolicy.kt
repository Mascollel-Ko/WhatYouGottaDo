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
        context: PlacementContext? = null,
        performanceMetrics: PlannerPerformanceMetrics? = null,
        isMain: (PlannedExercise)->Boolean): Map<Int,List<TimedPlannedExercise>> {
        if (snapshot != null && state != null) return orderedReview(baseline, minutes, snapshot, robust, state, metrics, context, performanceMetrics, isMain)
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

    /**
     * Production placement path: construct a plausible layout once, then use a
     * small local repair only when canonical OFI/tissue validation rejects it.
     * The old branch-and-bound remains the snapshot-null reference path below.
     */
    private fun orderedReview(
        baseline: Map<Int, List<TimedPlannedExercise>>,
        minutes: Int,
        snapshot: PlanningHistorySnapshot,
        robust: Boolean,
        state: AthletePlanningState,
        metrics: InitialMainPlacementMetrics?,
        context: PlacementContext?,
        performanceMetrics: PlannerPerformanceMetrics?,
        isMain: (PlannedExercise) -> Boolean,
    ): Map<Int, List<TimedPlannedExercise>> {
        val days = baseline.keys.sorted()
        val placement = context ?: PlacementContext(snapshot, state, days.size, minutes)
        val actual = placement.actualDays
        val allRows = baseline.values.flatten()
        if (allRows.none { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) }) return baseline
        val atomContexts = java.util.IdentityHashMap<TimedPlannedExercise, PlacementAtomContext>()
        fun atom(row: TimedPlannedExercise): PlacementAtomContext = atomContexts[row] ?: placement.atom(row, isMain(row.item)).also {
            atomContexts[row] = it
        }
        val moving = allRows.filter { row ->
            val a = atom(row)
            (a.main || a.primary) && MainSchedulingPolicy.ordinary(row.item, row.prescription)
        }
        if (moving.isEmpty()) return baseline
        val movingIdentities = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<TimedPlannedExercise, Boolean>())
        movingIdentities.addAll(moving)
        val fixed = baseline.mapValues { (_, rows) -> rows.filterNot(movingIdentities::contains).toMutableList() }
        val dayIndex = days.withIndex().associate { it.value to it.index }
        val primaryCounts = IntArray(days.size)
        val mainCounts = IntArray(days.size)
        val protectedCounts = IntArray(days.size)
        val seconds = IntArray(days.size)
        val lowerSeconds = IntArray(days.size)
        val stableKeys = days.associateWith { mutableSetOf<String>() }
        val currentDayByIdentity = java.util.IdentityHashMap<TimedPlannedExercise, Int>()
        fun add(day: Int, row: TimedPlannedExercise) {
            val i = dayIndex.getValue(day); val a = atom(row)
            if (a.primary) primaryCounts[i]++
            if (a.main) mainCounts[i]++
            if (a.protectedPrimary) protectedCounts[i]++
            seconds[i] += a.estimatedSeconds
            if (a.lowerStress) lowerSeconds[i] += a.estimatedSeconds
            stableKeys.getValue(day) += a.stableKey
        }
        fun remove(day: Int, row: TimedPlannedExercise) {
            val i = dayIndex.getValue(day); val a = atom(row)
            if (a.primary) primaryCounts[i]--
            if (a.main) mainCounts[i]--
            if (a.protectedPrimary) protectedCounts[i]--
            seconds[i] -= a.estimatedSeconds
            if (a.lowerStress) lowerSeconds[i] -= a.estimatedSeconds
            stableKeys.getValue(day).remove(a.stableKey)
        }
        fixed.forEach { (day, rows) -> rows.forEach { add(day, it); currentDayByIdentity[it] = day } }
        val primaryKeys = PrimaryStrengthAnchorSpacingPolicy.keys(snapshot, state, moving.filter { atom(it).main }.mapTo(mutableSetOf()) { it.item.stableKey })
        val maxPrimary = ((allRows.count { atom(it).primary } + days.size - 1) / days.size).coerceAtLeast(1)
        fun spacingAllowed(row: TimedPlannedExercise, day: Int): Boolean {
            val a = atom(row)
            if (!a.protectedPrimary || a.stableKey !in primaryKeys) return true
            val assigned = baseline.values.flatten().filter { it.item.stableKey == a.stableKey && it !== row }
                .mapNotNull { currentDayByIdentity[it] }
                .map { actual[days.indexOf(it)] } + actual[dayIndex.getValue(day)]
            return PrimaryStrengthAnchorSpacingPolicy.allowed(assigned)
        }
        data class Candidate(val day: Int, val classOrder: Int, val primaryCount: Int, val protectedCount: Int,
            val mainCount: Int, val lowerSeconds: Int, val totalSeconds: Int, val scheduleRank: Int)
        fun candidate(row: TimedPlannedExercise, day: Int, observe: Boolean = true): Candidate? {
            if (observe) performanceMetrics?.let { it.candidateDayChecks++ }
            val a = atom(row); val i = dayIndex.getValue(day)
            if (a.stableKey in stableKeys.getValue(day) || seconds[i] + a.estimatedSeconds > placement.sessionSeconds || !spacingAllowed(row, day)) return null
            val primary = primaryCounts[i] + if (a.primary) 1 else 0
            val protected = protectedCounts[i] + if (a.protectedPrimary) 1 else 0
            val main = mainCounts[i] + if (a.main) 1 else 0
            val classOrder = when {
                primary == 0 && protected == 0 -> 0 // A: no primary/protected co-location
                primary == 0 -> 1 // B: no primary overlap
                primary <= maxPrimary -> 2 // C: does not raise the primary ceiling
                else -> 3 // D: hard-feasible fallback
            }
            val scheduleRank = if (robust && a.scheduleTier == ScheduleTier.CORE_MUST_DO && day > (days.size + 1) / 2) 1 else 0
            return Candidate(day, classOrder, primary, protected, main, lowerSeconds[i] + if (a.lowerStress) a.estimatedSeconds else 0,
                seconds[i] + a.estimatedSeconds, scheduleRank)
        }
        fun candidateComparator() = compareBy<Candidate> { it.classOrder }.thenBy { it.primaryCount }
            .thenBy { it.protectedCount }.thenBy { it.mainCount }.thenBy { it.lowerSeconds }
            .thenBy { it.totalSeconds }.thenBy { it.scheduleRank }.thenBy { it.day }
        fun legalDays(row: TimedPlannedExercise, observe: Boolean = true): List<Candidate> = days.mapNotNull { candidate(row, it, observe) }
            .sortedWith(candidateComparator())
        val orderedRows = moving.sortedWith(compareBy<TimedPlannedExercise> { legalDays(it, observe = false).size }
            .thenBy { if (atom(it).protectedPrimary) 0 else if (atom(it).primary || atom(it).main) 1 else 2 }
            .thenByDescending { atom(it).estimatedSeconds }
            .thenBy { if (atom(it).lowerStress) 0 else 1 }
            .thenByDescending { atom(it).priority }
            .thenBy { atom(it).stableKey }
            .thenBy { it.item.styleVariant })
        fun visit() {
            metrics?.let { it.searchNodes++ }
            performanceMetrics?.let { it.initialPlacementSearchNodes++ }
        }
        for (row in orderedRows) {
            val chosen = legalDays(row).firstOrNull() ?: return baseline
            fixed.getValue(chosen.day).add(row); add(chosen.day, row); currentDayByIdentity[row] = chosen.day; visit()
        }
        fun layout(): Map<Int, List<TimedPlannedExercise>> = fixed.mapValues { it.value.toList() }
        fun objective(rows: Map<Int, List<TimedPlannedExercise>>): InitialStrengthLayout = InitialStrengthLayout(
            StrengthPrimaryMainPolicy.counts(rows.values.map { day -> day.count { atom(it).primary } }),
            MainLayoutObjective.of(rows.mapKeys { actual[days.indexOf(it.key)] }.mapValues { (_, day) -> day.count { atom(it).main } })
        )
        fun projectedDay(rows: Map<Int, List<TimedPlannedExercise>>, day: Int): List<ProgramSkeletonItem> =
            rows.getValue(day).mapIndexed { index, row -> residualItem(snapshot, row.item, row.prescription,
                "primary_${day}_$index", actual[days.indexOf(day)], index + 1) }
        fun projected(rows: Map<Int, List<TimedPlannedExercise>>) = days.flatMap { projectedDay(rows, it) }
        var baselineTissue: PlannedTissueWeek? = null
        fun canonicalValid(rows: Map<Int, List<TimedPlannedExercise>>): Boolean {
            metrics?.let { it.legalChecks++ }
            performanceMetrics?.let { it.initialPlacementLegalChecks++ }
            val moved = orderedRows.filter { row ->
                val prior = baseline.entries.firstOrNull { (_, values) -> values.any { it === row } }?.key
                val now = rows.entries.firstOrNull { (_, values) -> values.any { it === row } }?.key
                prior != null && now != null && prior != now
            }.mapTo(mutableSetOf()) { atom(it).stableKey }
            if (moved.any { snapshot.explicitlyRestricted(it) || !postProcessTissueAllowed(snapshot, state, it) }) return false
            if (!PrimaryStrengthAnchorSpacingPolicy.allowedRows(projected(rows), primaryKeys)) return false
            val dayProjection = snapshot.planDayProjection
            if (dayProjection != null) {
                val changedDays = days.filter { day -> rows.getValue(day).map { it.item.stableKey to it.estimatedSeconds } !=
                    baseline.getValue(day).map { it.item.stableKey to it.estimatedSeconds } }
                for (day in changedDays) {
                    metrics?.let { it.dayProjectionEvaluations++ }
                    performanceMetrics?.let { it.dayProjectionCalls++ }
                    if (!dayProjection.evaluate(projectedDay(rows, day)).feasible) return false
                }
            }
            val tissue = snapshot.planWeekTissueProjection ?: return true
            if (baselineTissue == null) {
                metrics?.let { it.baselineProjectionEvaluations++ }
                metrics?.let { it.weekTissueEvaluations++ }
                performanceMetrics?.let { it.weekTissueProjectionCalls++ }
                baselineTissue = tissue.evaluate(projected(baseline), 8.5)
            }
            metrics?.let { it.weekTissueEvaluations++ }
            performanceMetrics?.let { it.weekTissueProjectionCalls++ }
            val after = tissue.evaluate(projected(rows), 8.5)
            return after.diagnostic == "CANONICAL_RCV_PROJECTION" && after.days.all { day ->
                day.blockedUnits.isEmpty() && day.unresolvedKeys.none { it in moved } &&
                    day.unresolvedKeys.all { it in baselineTissue?.days?.firstOrNull { old -> old.day == day.day }?.unresolvedKeys.orEmpty() }
            }
        }
        val plausible = layout()
        metrics?.let { it.searchLeaves++ }
        val baselineObjective = objective(baseline)
        val plausibleObjective = objective(plausible)
        if (canonicalValid(plausible)) return plausible
        if (canonicalValid(baseline) && plausibleObjective >= baselineObjective) return baseline
        var current = plausible
        var currentObjective = plausibleObjective
        repeat(16) {
            val candidates = orderedRows.flatMap { row ->
                val from = current.entries.firstOrNull { (_, values) -> values.any { it === row } }?.key ?: return@flatMap emptyList()
                val a = atom(row)
                days.mapNotNull { target ->
                    if (target == from || current.getValue(target).any { it.item.stableKey == a.stableKey } ||
                        current.getValue(target).sumOf { it.estimatedSeconds } + a.estimatedSeconds > placement.sessionSeconds) return@mapNotNull null
                    performanceMetrics?.let { it.candidateDayChecks++ }
                    val trial = current.mapValues { (_, values) -> values.toMutableList() }
                    trial.getValue(from).removeIf { it === row }
                    trial.getValue(target).add(row)
                    val result = trial.mapValues { it.value.toList() }
                    if (!PrimaryStrengthAnchorSpacingPolicy.allowedRows(projected(result), primaryKeys)) return@mapNotNull null
                    val currentRows = current.getValue(target)
                    val currentPrimary = currentRows.count { atom(it).primary }
                    val currentProtected = currentRows.count { atom(it).protectedPrimary }
                    val currentMain = currentRows.count { atom(it).main }
                    val currentLower = currentRows.filter { atom(it).lowerStress }.sumOf { it.estimatedSeconds }
                    val currentSeconds = currentRows.sumOf { it.estimatedSeconds }
                    val candidate = Candidate(target,
                        when {
                            (currentPrimary + if (a.primary) 1 else 0) == 0 && (currentProtected + if (a.protectedPrimary) 1 else 0) == 0 -> 0
                            (currentPrimary + if (a.primary) 1 else 0) == 0 -> 1
                            (currentPrimary + if (a.primary) 1 else 0) <= maxPrimary -> 2
                            else -> 3
                        },
                        currentPrimary + if (a.primary) 1 else 0,
                        currentProtected + if (a.protectedPrimary) 1 else 0,
                        currentMain + if (a.main) 1 else 0,
                        currentLower + if (a.lowerStress) a.estimatedSeconds else 0,
                        currentSeconds + a.estimatedSeconds,
                        if (robust && a.scheduleTier == ScheduleTier.CORE_MUST_DO && target > (days.size + 1) / 2) 1 else 0)
                    val nextObjective = objective(result)
                    if (nextObjective < currentObjective && canonicalValid(result)) candidate to (result to nextObjective) else null
                }
            }.sortedWith(Comparator { left, right -> candidateComparator().compare(left.first, right.first) })
            val selected = candidates.firstOrNull()?.second ?: return@repeat
            current = selected.first
            currentObjective = selected.second
            visit()
            if (canonicalValid(current)) return current
        }
        return if (canonicalValid(baseline)) baseline else baseline
    }
}

private data class InitialStrengthLayout(val primary: StrengthPrimaryObjective, val broadMain: MainLayoutObjective): Comparable<InitialStrengthLayout> {
    override fun compareTo(other: InitialStrengthLayout) = compareValuesBy(this,other,InitialStrengthLayout::primary,InitialStrengthLayout::broadMain)
}
