package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonRequest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Scheduling currency only: units from different domains are not equivalent stimuli. */
data class WeeklyCapacityEnvelope(
    val resolvedWeeklyDays: Int,
    val requestedSessionMinutes: Int,
    val availableWeeklySeconds: Int,
    val recentControllableTrainingDays: Int,
    val historicalSessionObservationCount: Int,
    val historicalSessionUnitMedian: Double,
    val historicalSessionUnitUpperTypical: Double,
    val historicalSessionSecondsMedian: Double,
    val historicalControllableUnits: Double,
    val recoveryConstraint: Double,
    val courtInterferenceContext: Double,
    val usefulDemandUnits: Int,
    val scheduleFeasibleUnits: Int,
    val finalControllableUnits: Int
)

data class ExecutionAllocationTrace(
    val capacity: WeeklyCapacityEnvelope,
    val continuityRequestedUnits: Int,
    val continuityAllocatedUnits: Int,
    val materialGapRequestedUnits: Int,
    val materialGapAllocatedUnits: Int,
    val selectedMaterialGaps: List<String>,
    val representedMaterialGaps: List<String>,
    val deferredMaterialGaps: Map<String, String>,
    val optionalDevelopmentalItems: List<String>,
    val candidateAudit: Map<String, String>,
    val representedGapCodesByStableKey: Map<String, Set<String>>,
    val prescriptionSources: Map<String, String>,
    val supportiveGapCodesByStableKey: Map<String, Set<String>> = emptyMap(),
    val scheduleTiers: Map<String, ScheduleTier> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("capacity", JSONObject()
            .put("resolvedWeeklyDays", capacity.resolvedWeeklyDays)
            .put("sessionMinutes", capacity.requestedSessionMinutes)
            .put("availableWeeklySeconds", capacity.availableWeeklySeconds)
            .put("recentControllableTrainingDays", capacity.recentControllableTrainingDays)
            .put("historicalSessionObservationCount", capacity.historicalSessionObservationCount)
            .put("historicalSessionUnitMedian", capacity.historicalSessionUnitMedian)
            .put("historicalSessionUnitUpperTypical", capacity.historicalSessionUnitUpperTypical)
            .put("historicalSessionSecondsMedian", capacity.historicalSessionSecondsMedian)
            .put("historicalControllableUnits", capacity.historicalControllableUnits)
            .put("recoveryConstraint", capacity.recoveryConstraint)
            .put("courtInterferenceContext", capacity.courtInterferenceContext)
            .put("usefulDemandUnits", capacity.usefulDemandUnits)
            .put("scheduleFeasibleUnits", capacity.scheduleFeasibleUnits)
            .put("finalControllableUnits", capacity.finalControllableUnits))
        .put("continuityRequestedUnits", continuityRequestedUnits)
        .put("continuityAllocatedUnits", continuityAllocatedUnits)
        .put("materialGapRequestedUnits", materialGapRequestedUnits)
        .put("materialGapAllocatedUnits", materialGapAllocatedUnits)
        .put("selectedMaterialGaps", JSONArray(selectedMaterialGaps))
        .put("representedMaterialGaps", JSONArray(representedMaterialGaps))
        .put("deferredMaterialGaps", JSONObject(deferredMaterialGaps))
        .put("optionalDevelopmentalItems", JSONArray(optionalDevelopmentalItems))
        .put("candidateAudit", JSONObject(candidateAudit))
        .put("representedGapCodesByStableKey", JSONObject().apply {
            representedGapCodesByStableKey.forEach { (key, codes) -> put(key, JSONArray(codes.toList())) }
        })
        .put("prescriptionSources", JSONObject(prescriptionSources))
        .put("scheduleTiers",JSONObject(scheduleTiers.mapValues { it.value.name }))
        .put("supportiveGapCodesByStableKey", JSONObject().apply {
            supportiveGapCodesByStableKey.forEach { (key, codes) -> put(key, JSONArray(codes.toList())) }
        })
}

data class TimedPlannedExercise(val item: PlannedExercise, val prescription: PlannedPrescription) {
    val estimatedSeconds: Int get() = prescription.sets.sumOf { if (it.seconds > 0) it.seconds else 45 } +
        (prescription.sets.size - 1).coerceAtLeast(0) * prescription.restSeconds
}

data class TimedExecutionAllocation(
    val days: Map<Int, List<TimedPlannedExercise>>,
    val deferred: List<TimedPlannedExercise>
)

/** Funds executable material work before discretionary continuity, using exact prescriptions.
 * The placement trial is a feasibility check; repair does not choose the block's priorities.
 */
class TimedExecutionAllocationPlanner(private val prescriptions: PersonalizedPrescriptionPlanner) {
    fun allocate(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, continuity: List<PlannedExercise>,
                 material: List<PlannedExercise>, optional: List<PlannedExercise>, days: Int, minutes: Int): TimedExecutionAllocation {
        val funded = mutableListOf<PlannedExercise>()
        val deferred = mutableListOf<TimedPlannedExercise>()
        fun timed(item: PlannedExercise) = TimedPlannedExercise(item, prescriptions.prescribe(snapshot, state.strengthIntent, item, item.style))
        fun fits(items: List<PlannedExercise>): Boolean =
            TimedWeeklyPlacementPlanner().distribute(items.map(::timed), days, minutes, snapshot,
                state.trainingStateAssessment?.sustainable?.robustSchedule == true).second.isEmpty()
        // One core set per retained resistance anchor; style variants are rebuilt below.
        val cores = continuity.filter { it.transition != null }.groupBy(PlannedExercise::stableKey).values.map { variants ->
            variants.first().copy(targetSets = 1)
        }
        cores.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey }).forEach {
            if (fits(funded + it)) funded += it
        }
        material.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey }).forEach { item ->
            if (fits(funded + item)) funded += item else deferred += timed(item)
        }
        // Restore desired continuity a set at a time so a long item cannot be discarded whole.
        continuity.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey }.thenBy { it.styleVariant }).forEach { desired ->
            var index = funded.indexOfFirst { it.stableKey == desired.stableKey && it.styleVariant == desired.styleVariant && it.representedGapCodes.isEmpty() }
            var count = if (index >= 0) funded[index].targetSets else 0
            while (count < desired.targetSets) {
                val candidate = desired.copy(targetSets = count + 1)
                val trial = funded.toMutableList().apply { if (index >= 0) set(index, candidate) else add(candidate) }
                if (!fits(trial)) break
                if (index >= 0) funded[index] = candidate else { funded += candidate; index = funded.lastIndex }
                count++
            }
            if (count == 0) deferred += timed(desired)
        }
        optional.forEach { if (fits(funded + it)) funded += it else deferred += timed(it) }
        val placement = TimedWeeklyPlacementPlanner().distribute(funded.map(::timed), days, minutes, snapshot,
            state.trainingStateAssessment?.sustainable?.robustSchedule == true)
        check(placement.second.isEmpty())
        return TimedExecutionAllocation(placement.first, deferred)
    }
}

data class MaterialDemand(
    val candidates: List<PlannedExercise>,
    val deferred: Map<String, String>,
    val audit: Map<String, String>
)

class MaterialDemandResolver {
    fun resolve(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, request: ProgramSkeletonRequest): MaterialDemand {
        val anchors = state.anchors.mapTo(mutableSetOf(), UserAnchor::stableKey)
        val alternatives = GapCandidateSelector().select(snapshot, state, gaps, anchors, allAlternatives = true)
        val audit = linkedMapOf<String, String>()
        val feasible = alternatives.filter { item ->
            val exercise = snapshot.exercises.getValue(item.stableKey)
            val equipment = exercise.equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
            val minimumPrescription = PersonalizedPrescriptionPlanner().prescribe(snapshot, state.strengthIntent, item, item.style)
            val minimumSeconds = TimedPlannedExercise(item, minimumPrescription).estimatedSeconds
            val reason = when {
                item.stableKey in request.excludedExerciseStableKeys -> "USER_EXCLUDED"
                item.stableKey in snapshot.recoverySignals.tissueRestrictedStableKeys -> "TISSUE_RESTRICTED"
                request.availableEquipment.isNotEmpty() && equipment.any { it !in request.availableEquipment && it != "BODYWEIGHT" } -> "EQUIPMENT_INCOMPATIBLE"
                minimumSeconds > request.sessionMinutes * 60 -> "MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME"
                else -> null
            }
            audit[item.stableKey] = reason ?: "FEASIBLE_ALTERNATIVE"
            reason == null
        }
        val selected = linkedMapOf<String, PlannedExercise>()
        val represented = mutableSetOf<String>()
        val selectedQualities = mutableSetOf<String>()
        val deferred = linkedMapOf<String, String>()
        fun quality(item: PlannedExercise): String {
            val metadata = snapshot.metadata[item.stableKey]
            return listOf(snapshot.activityKind(item.stableKey), metadata?.redundancyGroup?.takeIf(String::isNotBlank)
                ?: metadata?.movementFamily.orEmpty()).joinToString(":")
        }
        gaps.sortedWith(compareByDescending<AdaptationGap> { when(it.priority) { "HIGH" -> 3; "MEDIUM", "MODERATE" -> 2; else -> 1 } }
            .thenBy { !it.contributesTransitionPressure }.thenBy { it.code }).forEach { gap ->
            if (gap.code in represented && !gap.code.endsWith("FOUNDATIONAL_ONRAMP")) return@forEach
            val pool = feasible.filter { gap.code in it.representedGapCodes }
            // A foundational block can contain distinct canonical objective qualities.
            // Explicit supportive work is eligible; direct candidates lead when feasible.
            val remaining = pool.sortedWith(compareBy<PlannedExercise> {
                objectiveFromGap(gap.code).let { objective -> objective.isNotBlank() && objective !in it.representedObjectives }
            }.thenBy { quality(it) in selectedQualities }
                .thenBy { it.representedObjectives.size }
                .thenByDescending { item -> snapshot.allConfirmedSets.any { it.stableKey == item.stableKey } }
                .thenBy { it.stableKey })
            val choices = if (gap.code == "RESISTANCE_FOUNDATIONAL_ONRAMP") remaining
                else if (gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP") {
                    val objectives = mutableSetOf<String>()
                    remaining.filter { item ->
                        val contributes = item.representedObjectives.any { it !in objectives }
                        if (contributes) objectives += item.representedObjectives
                        contributes
                    }
                } else remaining.take(1)
            if (choices.isEmpty()) deferred[gap.code] = "NO_FEASIBLE_PRESCRIPTION_OR_CANDIDATE"
            choices.forEach { choice ->
                val covered = gaps.filter { other ->
                    other.contributesTransitionPressure == gap.contributesTransitionPressure &&
                        (other.code == gap.code || objectiveFromGap(other.code) in choice.representedObjectives ||
                            objectiveFromGap(other.code) in choice.supportiveObjectives)
                }.mapTo(linkedSetOf(), AdaptationGap::code)
                val existing = selected[choice.stableKey]
                val owner = existing?.takeIf { it.material && !choice.material } ?: choice
                selected[choice.stableKey] = owner.copy(representedGapCodes = covered + existing?.representedGapCodes.orEmpty())
                // Supportive exposure remains useful demand, but cannot close another DIRECT gap.
                represented += covered.filter { it == gap.code || objectiveFromGap(it) in choice.representedObjectives }
                selectedQualities += quality(choice)
                audit[choice.stableKey] = if (objectiveFromGap(gap.code) in choice.supportiveObjectives &&
                    objectiveFromGap(gap.code) !in choice.representedObjectives) "SELECTED_SUPPORTIVE_DEMAND" else "SELECTED_MATERIAL_DEMAND"
            }
        }
        snapshot.exercises.keys.filter { snapshot.activityKind(it) in setOf(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL) ||
            snapshot.badmintonSupportiveObjectives[it].orEmpty().isNotEmpty() }.forEach { key ->
            val objectives = snapshot.badmintonDirectObjectives[key].orEmpty() + snapshot.badmintonSupportiveObjectives[key].orEmpty()
            val matches = gaps.any { objectiveFromGap(it.code) in objectives || it.code == "BADMINTON_FOUNDATIONAL_ONRAMP" }
            audit.putIfAbsent(key, when {
                key in request.excludedExerciseStableKeys -> "USER_EXCLUDED"
                snapshot.explicitlyRestricted(key) -> "EXPLICIT_PROFILE_RESTRICTION"
                key in snapshot.recoverySignals.tissueRestrictedStableKeys -> "TISSUE_RESTRICTED"
                PerformancePrescriptionResolver.resolve(snapshot, key) == null -> "NO_SAFE_PRESCRIPTION_AUTHORITY"
                !matches -> "NO_MATCHING_CURRENT_OBJECTIVE_DEMAND"
                else -> "REDUNDANT_OR_INELIGIBLE_CANDIDATE"
            })
        }
        return MaterialDemand(selected.values.toList(), deferred, audit)
    }

    private fun objectiveFromGap(code: String): String = listOf("BADMINTON_DROP_", "BADMINTON_UNDERREPRESENTED_", "BADMINTON_DEVELOP_")
        .firstOrNull(code::startsWith)?.let(code::removePrefix).orEmpty()
}

/** Pure finite allocation kernel shared by all activity domains; independently golden-tested. */
data class FiniteAllocation(val continuity: Int, val material: List<Int>, val deferred: List<Int>)
object FiniteExecutionAllocator {
    fun allocate(capacity: Int, continuityDemand: Int, minimums: List<Int>, share: Double, coreReserve: Int,
                 flexible: Set<Int> = minimums.indices.toSet()): FiniteAllocation {
        val reserve = minOf(coreReserve, continuityDemand, capacity)
        val selected = mutableListOf<Int>()
        var spent = 0
        minimums.forEachIndexed { index, minimum ->
            if (minimum > 0 && spent + minimum <= capacity - reserve) { selected += index; spent += minimum }
        }
        // Fund meaningful material units first; an indivisible prescription is never rounded down.
        val material = minimums.indices.map { if (it in selected) minimums[it] else 0 }.toMutableList()
        val gapTarget = maxOf(spent, (capacity * share).roundToInt()).coerceAtMost(capacity - reserve)
        val expandable = selected.filter { it in flexible }
        var cursor = 0
        while (material.sum() < gapTarget && expandable.isNotEmpty()) {
            material[expandable[cursor++ % expandable.size]]++
        }
        val continuity = minOf(continuityDemand, (capacity - material.sum()).coerceAtLeast(0))
        return FiniteAllocation(continuity, material, minimums.indices.filter { material[it] == 0 })
    }
}

class ExecutionCapacityPlanner {
    fun envelope(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, request: ProgramSkeletonRequest,
                 baseline: Double, usefulDemand: Int, doseFactor: Double): WeeklyCapacityEnvelope {
        val assessment = state.trainingStateAssessment
        val excludedWeeks = assessment?.weeklyContext.orEmpty().filter { it.excludedFromTolerance }
        val recent = snapshot.allConfirmedSets.filter {
            !it.date.isBefore(snapshot.cutoff.minusDays(55)) && !it.date.isAfter(snapshot.cutoff) &&
                snapshot.activityKind(it.stableKey) in setOf(PlannedActivityKind.RESISTANCE, PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL) &&
                excludedWeeks.none { week -> it.date in week.start..week.end }
        }
        val sessions = recent.groupBy(PlanningSetRecord::date).values
        val counts = sessions.map { it.size.toDouble() }.sorted()
        val seconds = sessions.map { rows -> rows.groupBy(PlanningSetRecord::stableKey).values.sumOf { group ->
            group.sumOf { if (it.seconds > 0) it.seconds else 45 } +
                (group.size - 1).coerceAtLeast(0) * (snapshot.exercises[group.first().stableKey]?.defaultRestSeconds ?: 60)
        }.toDouble() }.sorted()
        fun quantile(values: List<Double>, q: Double): Double = if (values.isEmpty()) 0.0 else values[((values.size - 1) * q).roundToInt()]
        val median = quantile(counts, .5)
        val typical = if (sessions.size >= 4) quantile(counts, .75) else median
        val medianSeconds = quantile(seconds, .5)
        val observedWeeks = recent.map { it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.distinct().size.coerceAtLeast(1)
        val historical = recent.size.toDouble() / observedWeeks
        val historicalDays = sessions.size.toDouble() / observedWeeks
        // Extra availability releases deferred useful demand, never invents new demand.
        val dayScale = if (historicalDays > 0) maxOf(1.0, request.weeklyTrainingDays / historicalDays) else 1.0
        val timeScale = if (medianSeconds > 0) maxOf(1.0, request.sessionMinutes * 60.0 / medianSeconds) else 1.0
        val demonstrated = maxOf(baseline, historical)
        val sustainable = assessment?.sustainable
        val release = assessment?.permitsSustainableRelease == true &&
            request.weeklyTrainingDays >= (sustainable?.sustainableDaysPerWeek ?: Double.MAX_VALUE) &&
            request.weeklyTrainingDays * request.sessionMinutes >= (sustainable?.sustainableWeeklyMinutes ?: Double.MAX_VALUE)
        val densityBound = if (release) requireNotNull(sustainable?.sustainableWeeklyControllableUnits).toInt()
            else if (sessions.isEmpty()) usefulDemand else ceil(demonstrated * dayScale * timeScale).toInt()
        val available = request.weeklyTrainingDays * request.sessionMinutes * 60
        val scheduleUnits = (available / maxOf(45.0, if (median > 0) medianSeconds / median else 135.0)).toInt()
        // Exactly one global soft/hard factor, after availability and useful demand bounds.
        val final = (minOf(usefulDemand, densityBound, scheduleUnits) * doseFactor).roundToInt().coerceAtLeast(0)
        return WeeklyCapacityEnvelope(request.weeklyTrainingDays, request.sessionMinutes, available, sessions.size,
            sessions.size, median, typical, medianSeconds, historical, 1.0 - doseFactor, state.genericCourtLoad,
            usefulDemand, scheduleUnits, final)
    }
}

/** Prescriptions precede placement. No item-count or generic-court-count capacity rule. */
class TimedWeeklyPlacementPlanner {
    fun distribute(items: List<TimedPlannedExercise>, days: Int, sessionMinutes: Int, snapshot: PlanningHistorySnapshot? = null,
                   robustSchedule: Boolean = false): Pair<Map<Int, List<TimedPlannedExercise>>, List<TimedPlannedExercise>> {
        val buckets = (1..days).associateWith { mutableListOf<TimedPlannedExercise>() }
        val deferred = mutableListOf<TimedPlannedExercise>()
        val ordered = items.sortedWith(compareByDescending<TimedPlannedExercise> { it.item.priority }
            .thenByDescending { it.item.representedGapCodes.isNotEmpty() }
            .thenByDescending { it.item.styleVariant.isNotBlank() }.thenByDescending { it.estimatedSeconds }
            .thenBy { it.item.stableKey }.thenBy { it.item.styleVariant })
        ordered.forEach { row ->
            val sameKeyDays = buckets.filterValues { list -> list.any { it.item.stableKey == row.item.stableKey } }.keys
            fun lowerStress(row: TimedPlannedExercise): Boolean = snapshot?.metadata?.get(row.item.stableKey)?.let { metadata ->
                snapshot.movementCoverage(row.item.stableKey) in setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES) ||
                    metadata.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
            } ?: false
            val target = buckets.entries.filter { entry ->
                entry.key !in sameKeyDays && entry.value.sumOf { it.estimatedSeconds } + row.estimatedSeconds <= sessionMinutes * 60
            }.minWithOrNull(compareBy<Map.Entry<Int, MutableList<TimedPlannedExercise>>> {
                if (lowerStress(row)) it.value.filter(::lowerStress).sumOf(TimedPlannedExercise::estimatedSeconds) else 0
            }.thenBy {
                if (!robustSchedule) 0 else when (row.item.scheduleTier()) {
                    ScheduleTier.CORE_MUST_DO -> if (it.key <= (days+1)/2) 0 else 1
                    ScheduleTier.IMPORTANT -> 0
                    ScheduleTier.OPTIONAL_CAPACITY -> days-it.key
                }
            }.thenBy { it.value.sumOf(TimedPlannedExercise::estimatedSeconds) }
                .thenBy { it.key })
            if (target == null) deferred += row else target.value += row
        }
        return buckets to deferred
    }
}

internal fun PlannedExercise.scheduleTier(): ScheduleTier = when {
    transition != null && priority >= 75 || material && priority >= 100 -> ScheduleTier.CORE_MUST_DO
    transition != null || material || role == "PERFORMANCE_CONTINUITY" -> ScheduleTier.IMPORTANT
    else -> ScheduleTier.OPTIONAL_CAPACITY
}
