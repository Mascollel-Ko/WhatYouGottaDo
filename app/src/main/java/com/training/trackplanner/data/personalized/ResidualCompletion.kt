package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONArray
import org.json.JSONObject

enum class PlanningDemandUnit { RESISTANCE_SETS, OBJECTIVE_EXPOSURE, DIRECT_SETS, CONTINUITY_SETS }
data class PlanningResidual(
    val id: String, val unit: PlanningDemandUnit, val priority: Int, val requested: Double,
    val initialCoverage: Double, val coverage: Double, val residual: Double
) {
    val fraction: Double get() = if (requested > 0) residual / requested else 0.0
    fun toJson(): JSONObject = JSONObject().put("id", id).put("unit", unit.name).put("priority", priority)
        .put("requested", requested).put("initialCoverage", initialCoverage).put("coverage", coverage).put("residual", residual)
}
data class ResidualAddition(val atomId: String, val demandId: String, val stableKey: String, val day: Int,
    val setCount: Int, val prescriptionSource: String, val ofi: Int, val residualsAfter: List<PlanningResidual>,
    val residualsBefore: List<PlanningResidual> = emptyList(), val exactExhausted: Boolean = true) {
    fun toJson(): JSONObject = JSONObject().put("atomId", atomId).put("demandId", demandId).put("stableKey", stableKey)
        .put("day", day).put("setCount", setCount).put("prescriptionSource", prescriptionSource).put("ofi", ofi)
        .put("action", "SEMANTIC_ADDITION").put("authorizedParent", JSONObject.NULL).put("exactExhausted", exactExhausted)
        .put("timeGate", "PASS").put("ofiGate", "PASS").put("residualsBefore", JSONArray(residualsBefore.map { it.toJson() }))
        .put("tissueGate", "CANONICAL_CURRENT_RESTRICTIONS_PASS").put("residualsAfter", JSONArray(residualsAfter.map { it.toJson() }))
}
data class ResidualCompletionTrace(val state: String, val initialFingerprint: String,
    val completedFingerprint: String, val projectionDate: String, val referenceUnits: Double = 0.0,
    val referenceSeconds: Double = 0.0, val authorizedUnits: Int = 0, val residuals: List<PlanningResidual> = emptyList(),
    val additions: List<ResidualAddition> = emptyList(), val addedDay: Boolean = false,
    val exactShortfalls: List<ExactPrescriptionShortfall> = emptyList(), val restorations: List<ExactRestorationAction> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put("state", state).put("initialFingerprint", initialFingerprint)
        .put("completedFingerprint", completedFingerprint).put("projectionDate", projectionDate)
        .put("demandBoundary", "AUTHORIZED_POST_CAPACITY_PRE_PLACEMENT_DEMAND")
        .put("referenceUnits", referenceUnits).put("referenceSeconds", referenceSeconds).put("authorizedUnits", authorizedUnits)
        .put("residuals", JSONArray(residuals.map { it.toJson() })).put("additions", JSONArray(additions.map { it.toJson() }))
        .put("addedDay", addedDay)
        .put("exactShortfalls", JSONArray(exactShortfalls.map { it.toJson() }))
        .put("restorations", JSONArray(restorations.map { it.toJson() }))
}

internal data class AuthorizedPrescription(val id: String, val item: PlannedExercise, val prescription: PlannedPrescription, val continuity: Boolean)
internal data class DemandDefinition(val id: String, val unit: PlanningDemandUnit, val priority: Int,
    val objective: String = "", val movements: Set<MovementCoverage> = emptySet(), val key: String = "", val variant: String = "",
    val ownerGapCodes: Set<String> = emptySet()) {
    fun contribution(snapshot: PlanningHistorySnapshot, stableKey: String, styleVariant: String, count: Int): Double = when (unit) {
        PlanningDemandUnit.RESISTANCE_SETS -> if (snapshot.activityKind(stableKey) == PlannedActivityKind.RESISTANCE &&
            snapshot.movementCoverage(stableKey) in movements) count.toDouble() else 0.0
        PlanningDemandUnit.OBJECTIVE_EXPOSURE -> count * (snapshot.badmintonObjectives[stableKey]?.get(objective) ?: 0.0)
        PlanningDemandUnit.DIRECT_SETS -> if (objective in snapshot.badmintonDirectObjectives[stableKey].orEmpty()) count.toDouble() else 0.0
        PlanningDemandUnit.CONTINUITY_SETS -> if (key == stableKey && variant == styleVariant) count.toDouble() else 0.0
    }
}

/** Q is constructed exclusively from the exact prescriptions of funded items, never raw candidates. */
internal class AuthorizedPlanningDemand(private val snapshot: PlanningHistorySnapshot, val authorized: List<AuthorizedPrescription>,
    gaps: List<AdaptationGap>, initial: List<ProgramSkeletonItem>) {
    val definitions: List<DemandDefinition> = buildList {
        authorized.filter { it.continuity }.forEach { row -> add(DemandDefinition("CONTINUITY:${row.item.stableKey}:${row.item.styleVariant}",
            PlanningDemandUnit.CONTINUITY_SETS, row.item.priority, key = row.item.stableKey, variant = row.item.styleVariant)) }
        val codes = authorized.flatMap { it.item.representedGapCodes }.distinct()
        codes.forEach { code ->
            val gap = gaps.firstOrNull { it.code == code } ?: return@forEach
            val priority = when (gap.priority) { "HIGH" -> 100; "MODERATE", "MEDIUM" -> 90; else -> 70 }
            val objective = badmintonObjectiveFromGap(code)
            if (objective.isNotBlank()) {
                add(DemandDefinition("OBJECTIVE:$objective", PlanningDemandUnit.OBJECTIVE_EXPOSURE, priority, objective, ownerGapCodes = setOf(code)))
                if (code == "BADMINTON_DROP_$objective") add(DemandDefinition("DIRECT:$objective", PlanningDemandUnit.DIRECT_SETS, priority, objective, ownerGapCodes = setOf(code)))
            } else if (code == "BADMINTON_FOUNDATIONAL_ONRAMP") {
                authorized.filter { code in it.item.representedGapCodes }.flatMap { it.item.representedObjectives + it.item.supportiveObjectives }
                    .distinct().sorted().forEach { add(DemandDefinition("OBJECTIVE:$it", PlanningDemandUnit.OBJECTIVE_EXPOSURE, priority, it, ownerGapCodes = setOf(code))) }
            } else {
                val mapped = when (code.removePrefix("HYPERTROPHY_REBALANCE_")) {
                    "UPPER_PULL" -> setOf(MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL)
                    else -> MovementCoverage.entries.filter { it.name == code.removePrefix("HYPERTROPHY_REBALANCE_") }.toSet()
                }
                val movements = mapped.ifEmpty { authorized.filter { code in it.item.representedGapCodes }
                    .map { snapshot.movementCoverage(it.item.stableKey) }.toSet() }
                if (code == "RESISTANCE_FOUNDATIONAL_ONRAMP") movements.sortedBy { it.name }.forEach { movement ->
                    add(DemandDefinition("$code:${movement.name}", PlanningDemandUnit.RESISTANCE_SETS, priority, movements = setOf(movement), ownerGapCodes = setOf(code)))
                } else add(DemandDefinition(code, PlanningDemandUnit.RESISTANCE_SETS, priority, movements = movements, ownerGapCodes = setOf(code)))
            }
        }
    }.groupBy { it.id }.values.map { definitions -> definitions.first().copy(
        priority = definitions.maxOf { it.priority }, ownerGapCodes = definitions.flatMap { it.ownerGapCodes }.toSet()) }
    // Q asks why an item was funded. C below retains the canonical semantic contribution rules.
    private val requested = definitions.associate { d -> d.id to authorized.filter { a ->
        if (d.unit == PlanningDemandUnit.CONTINUITY_SETS) a.continuity && a.item.stableKey == d.key && a.item.styleVariant == d.variant
        else a.item.representedGapCodes.any { code -> code in d.ownerGapCodes &&
            (code != "BADMINTON_FOUNDATIONAL_ONRAMP" || d.objective in a.item.representedObjectives + a.item.supportiveObjectives) }
    }.sumOf {
        d.contribution(snapshot, it.item.stableKey, it.item.styleVariant, it.prescription.sets.size) } }
    private val initialCoverage = coverage(initial)
    val authorizedUnits: Int = authorized.sumOf { it.prescription.sets.size }
    fun coverage(rows: List<ProgramSkeletonItem>): Map<String, Double> = definitions.associate { d -> d.id to
        rows.sumOf { d.contribution(snapshot, it.exerciseStableKey, it.progressionVariant, it.setPrescriptions.size) } }
    fun residuals(rows: List<ProgramSkeletonItem>): List<PlanningResidual> {
        val c = coverage(rows)
        return definitions.map { d ->
            val q = requested.getValue(d.id)
            val remaining = (q - c.getValue(d.id)).coerceAtLeast(0.0).let { if (it < PLANNING_EPSILON) 0.0 else it }
            PlanningResidual(d.id, d.unit, d.priority, q, initialCoverage.getValue(d.id), c.getValue(d.id), remaining)
        }
    }
}

internal data class CompletionResult(val skeleton: GeneratedProgramSkeleton, val trace: ResidualCompletionTrace,
    val week: RepresentativeWeek?, val sourceByAtom: Map<String, PlannedExercise>, val demand: AuthorizedPlanningDemand?)

internal class ResidualCompletion(private val prescriptions: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()) {
    fun complete(initial: GeneratedProgramSkeleton, snapshot: PlanningHistorySnapshot, state: AthletePlanningState,
        gaps: List<AdaptationGap>, authorized: List<AuthorizedPrescription>, envelope: WeeklyCapacityEnvelope,
        atoms: Map<String, String>, sources: Map<String, PlannedExercise>, explicitDays: Boolean,
        projection: PlanDayProjection?, origins: Map<String, AuthorizedAtomOrigin>? = null): CompletionResult {
        val fingerprint = personalizedProgramFingerprint(initial.request, initial.items)
        fun unchanged(code: String) = CompletionResult(initial, ResidualCompletionTrace(code, fingerprint, fingerprint,
            snapshot.cutoff.plusDays(1).toString()), null, sources, null)
        val week = RepresentativeWeek.derive(initial, atoms) ?: return unchanged("POST_PROCESS_SKIPPED_NON_ISOMORPHIC_WEEKS")
        if (projection == null) return unchanged("POST_PROCESS_SKIPPED_MISSING_CANONICAL_PROJECTION")
        return try {
            run(initial, snapshot, state, gaps, authorized, envelope, week, sources, explicitDays, projection, origins)
        } catch (failure: Exception) {
            if (failure is java.util.concurrent.CancellationException) throw failure
            unchanged("POST_PROCESS_FAILED_SAFE_INITIAL_SKELETON")
        }
    }

    private fun run(initial: GeneratedProgramSkeleton, snapshot: PlanningHistorySnapshot, state: AthletePlanningState,
        gaps: List<AdaptationGap>, authorized: List<AuthorizedPrescription>, envelope: WeeklyCapacityEnvelope,
        week: RepresentativeWeek, sources: Map<String, PlannedExercise>, explicitDays: Boolean, projection: PlanDayProjection,
        origins: Map<String, AuthorizedAtomOrigin>?): CompletionResult {
        val demand = AuthorizedPlanningDemand(snapshot, authorized, gaps, week.items)
        var rows = week.items
        var schedule = initial.weekDaySchedule
        var days = week.days
        val sourceByAtom = sources.toMutableMap()
        val nonEmpty = days.map { day -> rows.filter { it.dayOfWeek == day } }.filter { it.isNotEmpty() }
        val referenceUnits = if (envelope.historicalSessionObservationCount >= 4) envelope.historicalSessionUnitMedian
            else planningMedian(nonEmpty.map { day -> day.sumOf { it.setPrescriptions.size }.toDouble() })
        val referenceSeconds = minOf(initial.request.sessionMinutes * 60.0,
            if (envelope.historicalSessionObservationCount >= 4) envelope.historicalSessionSecondsMedian
            else planningMedian(nonEmpty.map { it.sumOf(::plannedSeconds).toDouble() }))
        val additions = mutableListOf<ResidualAddition>()
        val exact = origins?.let { ExactAuthorizedRestoration(snapshot, state, initial.request, authorized,
            minOf(demand.authorizedUnits, envelope.finalControllableUnits), projection, demand, week.atomByLocalId, it, rows) }
        if (exact != null) rows = exact.restore(rows, days)
        fun dayRows(day: Int) = rows.filter { it.dayOfWeek == day }
        fun unitFill(day: Int) = if (referenceUnits > 0) dayRows(day).sumOf { it.setPrescriptions.size } / referenceUnits else 1.0
        fun timeFill(day: Int) = if (referenceSeconds > 0) dayRows(day).sumOf(::plannedSeconds) / referenceSeconds else 1.0
        fun sparse(day: Int) = unitFill(day) < .50 && timeFill(day) < .50
        fun rankedResiduals() = demand.residuals(rows).filter { it.residual > PLANNING_EPSILON }
            .sortedWith(compareByDescending<PlanningResidual> { it.priority }.thenByDescending { it.fraction })
        // Existing selector owns ordering/relations; only candidates for a funded residual are considered.
        val alternatives = GapCandidateSelector().select(snapshot, state, gaps,
            state.anchors.mapTo(mutableSetOf(), UserAnchor::stableKey), allAlternatives = true)
        fun candidateItems(residual: PlanningResidual): List<PlannedExercise> {
            val definition = demand.definitions.first { it.id == residual.id }
            // Exact continuity cannot be fragmented by the later flexible semantic prescription loop.
            if (exact != null && definition.unit == PlanningDemandUnit.CONTINUITY_SETS) return emptyList()
            val incumbents = authorized.filter { a -> definition.contribution(snapshot, a.item.stableKey, a.item.styleVariant, 1) > 0 }.map { it.item }
            val pool = if (definition.unit == PlanningDemandUnit.CONTINUITY_SETS) incumbents else
                alternatives.filter { definition.contribution(snapshot, it.stableKey, it.styleVariant, 1) > 0 }
                    .map { alternative -> incumbents.firstOrNull { it.stableKey == alternative.stableKey && it.styleVariant == alternative.styleVariant } ?: alternative } + incumbents
            return pool.distinctBy { it.stableKey to it.styleVariant }.filter { item ->
                val ownsUnrestoredExact = exact?.shortfalls(rows)?.any { it.stableKey == item.stableKey && it.shortfall > 0 } == true
                val equipment = snapshot.exercises[item.stableKey]?.equipment.orEmpty().split('|', ',').map(String::trim).filter(String::isNotBlank)
                !ownsUnrestoredExact && item.stableKey !in initial.request.excludedExerciseStableKeys && !snapshot.explicitlyRestricted(item.stableKey) &&
                    snapshot.metadata[item.stableKey]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") &&
                    postProcessTissueAllowed(snapshot, state, item.stableKey) &&
                    (initial.request.availableEquipment.isEmpty() || equipment.all { it == "BODYWEIGHT" || it in initial.request.availableEquipment })
            }
        }
        fun feasible(residual: PlanningResidual, day: Int): Pair<ProgramSkeletonItem, PlannedExercise>? {
            val definition = demand.definitions.first { it.id == residual.id }
            for (candidate in candidateItems(residual)) {
                if (dayRows(day).any { it.exerciseStableKey == candidate.stableKey }) continue
                var trial = candidate
                // A flexible prescription is re-issued by its existing authority at each smaller count.
                while (trial.targetSets > 0) {
                    val rx = try { prescriptions.prescribe(snapshot, state.strengthIntent, trial, trial.style) }
                        catch (_: IllegalArgumentException) { break }
                    val delta = definition.contribution(snapshot, trial.stableKey, trial.styleVariant, rx.sets.size)
                    val units = rows.sumOf { it.setPrescriptions.size } + rx.sets.size
                    val capacity = minOf(demand.authorizedUnits, envelope.finalControllableUnits)
                    val sameKeyAuthority = authorized.filter { it.item.stableKey == trial.stableKey }
                    val withinExactKeyCeiling = exact == null || sameKeyAuthority.isEmpty() ||
                        rows.filter { it.exerciseStableKey == trial.stableKey }.sumOf { it.setPrescriptions.size } + rx.sets.size <=
                        sameKeyAuthority.sumOf { it.prescription.sets.size }
                    val row = residualItem(snapshot, trial, rx, "addition_${additions.size}", day,
                        (dayRows(day).maxOfOrNull { it.orderIndex } ?: 0) + 1)
                    if (withinExactKeyCeiling && delta > PLANNING_EPSILON && delta <= residual.residual + PLANNING_EPSILON && units <= capacity &&
                        dayRows(day).sumOf(::plannedSeconds) + plannedSeconds(row) <= initial.request.sessionMinutes * 60 &&
                        projection.evaluate(dayRows(day) + row).feasible) return row to trial
                    if (snapshot.activityKind(trial.stableKey) != PlannedActivityKind.RESISTANCE || trial.styleVariant.isNotBlank()) break
                    val minimum = if (trial.transition != null) 1 else 2
                    if (trial.targetSets <= minimum) break
                    trial = trial.copy(targetSets = trial.targetSets - 1)
                }
            }
            return null
        }
        fun accept(residual: PlanningResidual, day: Int, candidate: Pair<ProgramSkeletonItem, PlannedExercise>) {
            val (row, source) = candidate
            check(exact == null || exact.restore(rows, days) == rows) { "Semantic substitution preceded legal exact restoration" }
            val before = demand.residuals(rows)
            rows = rows + row
            sourceByAtom[row.localId] = source
            additions += ResidualAddition(row.localId, residual.id, row.exerciseStableKey, day, row.setCount,
                row.weightSource, projection.evaluate(dayRows(day)).ofi, demand.residuals(rows), before)
        }
        var addedDay = false
        // Existing +1 eligibility: unfixed, below five, funded residual and no hard-feasible existing-day completion.
        val newDayResiduals = rankedResiduals().filter { residual -> days.none { feasible(residual, it) != null } }.map { it.id }.toSet()
        if (exact != null && !explicitDays && initial.request.weeklyTrainingDays < 5 &&
            exact.shortfalls(rows).any { it.shortfall > 0 } && newDayResiduals.isNotEmpty()) {
            val proposed = RecordBasedReviewedPolicy.defaultSchedule(initial.request.durationWeeks, initial.request.weeklyTrainingDays + 1)
            val proposedDays = proposed.getValue(1).sorted()
            if (proposedDays.size == days.size + 1) {
                val remapped = rows.map { it.copy(dayOfWeek = proposedDays[days.indexOf(it.dayOfWeek)]) }
                val restored = exact.restore(remapped, proposedDays, newDayResiduals)
                if (restored != remapped) { rows = restored; days = proposedDays; schedule = proposed; addedDay = true }
            }
        }
        while (true) {
            var accepted = false
            val destinations = days.filter(::sparse).sortedWith(compareBy<Int> { timeFill(it) }.thenBy { unitFill(it) }.thenBy { it })
            for (residual in rankedResiduals()) {
                for (day in destinations) {
                    val candidate = feasible(residual, day) ?: continue
                    accept(residual, day, candidate); accepted = true; break
                }
                if (accepted) break
            }
            if (!accepted) break
        }
        if (!addedDay && !explicitDays && initial.request.weeklyTrainingDays < 5) {
            // Hard-feasibility check deliberately considers ALL existing days, including non-sparse days.
            for (residual in rankedResiduals()) {
                if (days.any { feasible(residual, it) != null }) continue
                val proposed = RecordBasedReviewedPolicy.defaultSchedule(initial.request.durationWeeks, initial.request.weeklyTrainingDays + 1)
                val proposedDays = proposed.getValue(1).sorted()
                // Preserve existing logical slots in order; no invented weekday or per-week optimization.
                if (proposedDays.size != days.size + 1) continue
                val newDay = proposedDays.last()
                val oldRows = rows
                rows = rows.map { it.copy(dayOfWeek = proposedDays[days.indexOf(it.dayOfWeek)]) }
                val restored = exact?.restore(rows, proposedDays, setOf(residual.id))
                if (restored != null && restored != rows) {
                    rows = restored; days = proposedDays; schedule = proposed; addedDay = true; break
                }
                val candidate = feasible(residual, newDay)
                if (candidate == null) { rows = oldRows; continue }
                days = proposedDays; schedule = proposed
                accept(residual, newDay, candidate); addedDay = true
                while (sparse(newDay)) {
                    val next = rankedResiduals().firstNotNullOfOrNull { remaining -> feasible(remaining, newDay)?.let { remaining to it } } ?: break
                    accept(next.first, newDay, next.second)
                }
                break
            }
        }
        val completed = week.mirror(initial, rows, schedule).let { if (addedDay) it.copy(request = it.request.copy(
            weeklyTrainingDays = it.request.weeklyTrainingDays + 1)) else it }
        require(ProgramProjectionValidator().errors(completed, state.genericCourtLoad).isEmpty())
        val trace = ResidualCompletionTrace("POST_GENERATION_RESIDUAL_COMPLETION", personalizedProgramFingerprint(initial.request, initial.items),
            personalizedProgramFingerprint(completed.request, completed.items), snapshot.cutoff.plusDays(1).toString(),
            referenceUnits, referenceSeconds, demand.authorizedUnits, demand.residuals(rows), additions, addedDay,
            exact?.shortfalls(rows).orEmpty(), exact?.actions.orEmpty())
        sourceByAtom.putAll(exact?.sources.orEmpty())
        val newAtoms = rows.filter { it.localId !in week.atomByLocalId }.map { it.localId }
        val completedAtoms = completed.items.associate { row -> row.localId to
            (week.atomByLocalId[row.localId] ?: newAtoms.first { row.localId == "residual_${row.weekNumber}_$it" }) }
        return CompletionResult(completed, trace, RepresentativeWeek.derive(completed, completedAtoms), sourceByAtom, demand)
    }
}

internal fun residualItem(snapshot: PlanningHistorySnapshot, item: PlannedExercise, rx: PlannedPrescription, id: String, day: Int, order: Int): ProgramSkeletonItem {
    val exercise = snapshot.exercises.getValue(item.stableKey)
    val meta = snapshot.metadata.getValue(item.stableKey)
    val scalar = rx.sets.first()
    return ProgramSkeletonItem(localId = id, weekNumber = 1, dayOfWeek = day, orderIndex = order,
        exerciseStableKey = item.stableKey, exerciseName = exercise.name, category = exercise.category,
        restSeconds = rx.restSeconds, prescription = rx.text, setCount = rx.sets.size, reps = scalar.reps,
        weightKg = scalar.weightKg, seconds = scalar.seconds, setPrescriptions = rx.sets,
        estimatedDurationSeconds = TimedPlannedExercise(item, rx).estimatedSeconds,
        progressionStyle = item.style.takeUnless { it in setOf(StrengthProgrammingStyle.NONE, StrengthProgrammingStyle.UNRESOLVED) }?.name.orEmpty(),
        progressionVariant = item.styleVariant, progressionRole = if (item.transition != null && item.styleVariant !in setOf("LIGHT", "VOLUME")) ProgressionRole.MAIN else ProgressionRole.ASSISTANCE,
        progressionAnchorSetIndex = if (item.style in setOf(StrengthProgrammingStyle.TOP_SET_BACKOFF, StrengthProgrammingStyle.TOP_SET_HYPERTROPHY, StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING)) rx.sets.maxByOrNull { it.weightKg }?.setIndex else null,
        selectionReason = item.reason, weightSource = rx.weightSource, trainingSlot = item.role, stableKey = item.stableKey,
        selectionRole = item.role, movementFamily = meta.movementFamily, movementSubtype = meta.movementSubtype,
        metadataProgramSlot = meta.programSlot, redundancyGroup = meta.redundancyGroup, strengthProgressionGroup = meta.strengthProgressionGroup,
        primaryStressProfile = meta.primaryStressProfile, stressMagnitudeHint = meta.stressMagnitudeHint,
        neuromuscularStressLevel = meta.neuromuscularStressLevel, systemicMuscularStressLevel = meta.systemicMuscularStressLevel,
        localMuscularStressLevel = meta.localMuscularStressLevel, jointTendonImpactStressLevel = meta.jointTendonImpactStressLevel,
        movementFocusDemandLevel = meta.movementFocusDemandLevel, recoveryDurationClass = meta.recoveryDurationClass,
        badmintonTransferLevel = meta.badmintonTransferLevel)
}
