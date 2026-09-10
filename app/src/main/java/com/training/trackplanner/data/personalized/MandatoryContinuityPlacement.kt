package com.training.trackplanner.data.personalized

enum class SplitPlacementFailure { SAME_KEY_OR_DISTINCT_DAY, SESSION_TIME_LIMIT, OFI_CONSTRAINT, TISSUE_RECOVERY_CONSTRAINT }
data class SplitOfiWarning(val chunkIndex: Int, val sets: Int, val day: Int, val load: StandaloneDayLoad)
internal data class MandatoryContinuityResult(val days: Map<Int, List<AuthorizedTimedAtom>>, val failures: Set<SplitPlacementFailure>,
    val ofiWarnings: List<SplitOfiWarning>)

/** Searches only the finite canonical partition; never invents a day, set or alternative exercise. */
internal class MandatoryContinuityPlacement(private val snapshot: PlanningHistorySnapshot, private val state: AthletePlanningState,
    private val days: Int, private val minutes: Int) {
    fun place(parent: AuthorizedSchedulingDemand, current: Map<Int, List<AuthorizedTimedAtom>>,
        prescriptions: PersonalizedPrescriptionPlanner): MandatoryContinuityResult {
        val failures = linkedSetOf<SplitPlacementFailure>()
        val ofiWarnings = linkedSetOf<SplitOfiWarning>()
        val others = (1..days).associateWith { day -> current[day].orEmpty().filter { it.origin.authorizedDemandId != parent.id } }
        val chunks = ContinuitySplitPolicy.chunks(parent, days) { count ->
            prescriptions.prescribe(snapshot, state.strengthIntent, parent.item.copy(targetSets = count), parent.item.style).text
        }
        val actualDays = RecordBasedReviewedPolicy.defaultSchedule(1, days).getValue(1).sorted()
        val dayLoads = mutableMapOf<List<com.training.trackplanner.data.ProgramSkeletonItem>, StandaloneDayLoad>()
        val tissueFeasibility = mutableMapOf<List<com.training.trackplanner.data.ProgramSkeletonItem>, Boolean>()
        fun rows(layout: Map<Int, List<AuthorizedTimedAtom>>) = layout.flatMap { (day, atoms) -> atoms.mapIndexed { index, atom ->
            residualItem(snapshot, atom.timed.item, atom.timed.prescription, "partition_${day}_$index", actualDays[day - 1], index + 1)
        } }
        var best = others
        var bestUnits = 0
        var bestMaximum = Int.MAX_VALUE
        fun search(index: Int, layout: Map<Int, List<AuthorizedTimedAtom>>, used: Set<Int>, units: Int) {
            if (bestUnits == parent.prescription.sets.size) return
            if (index == chunks.size) {
                if (units == 0) return
                val projected = rows(layout)
                if (!tissueFeasibility.getOrPut(projected) { splitTissueAllowed(snapshot, projected, parent.item.stableKey, 8.5) }) {
                    failures += SplitPlacementFailure.TISSUE_RECOVERY_CONSTRAINT; return
                }
                val maximum = layout.values.maxOf { atoms -> atoms.sumOf { it.timed.estimatedSeconds } }
                if (units > bestUnits || units == bestUnits && maximum < bestMaximum) {
                    best = layout; bestUnits = units; bestMaximum = maximum
                }
                return
            }
            val chunk = chunks[index]
            for (day in (1..days).sortedWith(compareBy<Int> { layout.getValue(it).sumOf { atom -> atom.timed.estimatedSeconds } }.thenBy { it })) {
                val onDay = layout.getValue(day)
                if (day in used || onDay.any { it.timed.item.stableKey == parent.item.stableKey }) {
                    failures += SplitPlacementFailure.SAME_KEY_OR_DISTINCT_DAY; continue
                }
                if (onDay.sumOf { it.timed.estimatedSeconds } + chunk.timed.estimatedSeconds > minutes * 60) {
                    failures += SplitPlacementFailure.SESSION_TIME_LIMIT; continue
                }
                val next = layout + (day to (onDay + chunk))
                val projectedDay = rows(next).filter { it.dayOfWeek == actualDays[day - 1] }
                val load = snapshot.planDayProjection?.let { projection -> dayLoads.getOrPut(projectedDay) { projection.evaluate(projectedDay) } }
                if (load?.feasible == false) {
                    // Explicit product override: preserve already-authorized high-set volume; OFI is advisory here only.
                    ofiWarnings += SplitOfiWarning(index, chunk.timed.prescription.sets.size, actualDays[day - 1], load)
                }
                search(index + 1, next, used + day, units + chunk.timed.prescription.sets.size)
            }
            // A failed chunk remains exact authorized shortfall; a smaller generic chunk is not fabricated.
            search(index + 1, layout, used, units)
        }
        search(0, others, emptySet(), 0)
        return MandatoryContinuityResult(best, if (bestUnits == parent.prescription.sets.size) emptySet() else failures, ofiWarnings.toList())
    }
}

/** Existing unrelated unresolved identities remain diagnostic; the split identity must resolve everywhere.
 * No chronological blocked tissue is allowed, including downstream days affected by the split. */
internal fun splitTissueAllowed(snapshot: PlanningHistorySnapshot, rows: List<com.training.trackplanner.data.ProgramSkeletonItem>,
    key: String, targetRpeMax: Double): Boolean {
    val projection = snapshot.planWeekTissueProjection ?: return true
    val result = projection.evaluate(rows, targetRpeMax)
    return result.diagnostic == "CANONICAL_RCV_PROJECTION" && result.days.all { it.blockedUnits.isEmpty() && key !in it.unresolvedKeys }
}
