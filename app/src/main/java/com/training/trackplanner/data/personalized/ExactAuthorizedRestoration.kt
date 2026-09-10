package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONArray
import org.json.JSONObject

data class ExactPrescriptionShortfall(val authorizedDemandId: String, val stableKey: String,
    val requested: Int, val initialMaterialized: Int, val materialized: Int) {
    val shortfall: Int get() = (requested - materialized).coerceAtLeast(0)
    fun toJson() = JSONObject().put("authorizedDemandId", authorizedDemandId).put("stableKey", stableKey)
        .put("requested", requested).put("initialMaterialized", initialMaterialized).put("materialized", materialized).put("shortfall", shortfall)
}
data class ExactRestorationAction(val authorizedDemandId: String, val before: Int, val after: Int,
    val rows: List<ProgramSkeletonItem>, val residualsBefore: List<PlanningResidual>, val residualsAfter: List<PlanningResidual>,
    val ofiGate: String = "PASS") {
    fun toJson() = JSONObject().put("action", "RESTORE_EXACT").put("authorizedDemandId", authorizedDemandId)
        .put("beforeShortfall", before).put("afterShortfall", after).put("rows", JSONArray(rows.map(::auditPlannedItem)))
        .put("timeGate", "PASS").put("ofiGate", ofiGate).put("tissueGate", "CANONICAL_CURRENT_RESTRICTIONS_PASS")
        .put("residualsBefore", JSONArray(residualsBefore.map { it.toJson() })).put("residualsAfter", JSONArray(residualsAfter.map { it.toJson() }))
}

/** Parent provenance, not key matching, owns exact materialization. No prescription is reissued. */
internal class ExactAuthorizedRestoration(private val snapshot: PlanningHistorySnapshot, private val state: AthletePlanningState,
    private val request: ProgramSkeletonRequest, private val authorized: List<AuthorizedPrescription>,
    private val capacity: Int, private val projection: PlanDayProjection, private val demand: AuthorizedPlanningDemand,
    private val atomByLocalId: Map<String, String>, origins: Map<String, AuthorizedAtomOrigin>, initial: List<ProgramSkeletonItem>,
    private val progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE) {
    val origins = origins.toMutableMap()
    val sources = mutableMapOf<String, PlannedExercise>()
    val actions = mutableListOf<ExactRestorationAction>()
    private fun origin(row: ProgramSkeletonItem) = origins[atomByLocalId[row.localId] ?: row.localId]
    private fun children(rows: List<ProgramSkeletonItem>, id: String) = rows.filter { origin(it)?.authorizedDemandId == id }
    private val initialCounts = authorized.associate { it.id to children(initial, it.id).sumOf { row -> row.setPrescriptions.size } }
    fun shortfalls(rows: List<ProgramSkeletonItem>) = authorized.map { parent -> ExactPrescriptionShortfall(parent.id,
        parent.item.stableKey, parent.prescription.sets.size, initialCounts.getValue(parent.id), children(rows, parent.id).sumOf { it.setPrescriptions.size }) }

    fun restore(input: List<ProgramSkeletonItem>, days: List<Int>, requiredResidualIds: Set<String>? = null): List<ProgramSkeletonItem> {
        var rows = input
        do {
        val unitsBefore = rows.sumOf { it.setPrescriptions.size }
        for (parent in authorized.sortedWith(compareByDescending<AuthorizedPrescription> { it.item.priority }.thenBy { it.id })) {
            val old = children(rows, parent.id)
            val count = old.sumOf { it.setPrescriptions.size }
            if (count >= parent.prescription.sets.size || !allowed(parent.item.stableKey)) continue
            progress.report(PersonalizedPlannerStage.RESIDUAL)
            val scheduling = AuthorizedSchedulingDemand(parent.id, parent.item, parent.prescription, parent.continuity)
            val split = ContinuitySplitPolicy.eligible(snapshot, scheduling) && old.none { it.requiredTemplateAnchor }
            val whole = listOf(AuthorizedTimedAtom(TimedPlannedExercise(parent.item, parent.prescription), AuthorizedAtomOrigin(parent.id)))
            val mandatory = ContinuitySplitPolicy.mandatory(snapshot, scheduling)
            val chunks = if (split) ContinuitySplitPolicy.chunks(scheduling, days.size) { n ->
                // Only the presentation text is obtained from the existing authority; dose stays frozen.
                PersonalizedPrescriptionPlanner().prescribe(snapshot, state.strengthIntent, parent.item.copy(targetSets = n), parent.item.style).text
            } else emptyList()
            // A partially present split keeps its canonical structure. An unsplit reduction first attempts same-day whole restoration.
            val variants = if (mandatory || old.any { origin(it)?.splitChunkIndex != null }) listOf(chunks) else listOf(whole, chunks)
            val others = rows - old.toSet()
            var accepted: List<Pair<ProgramSkeletonItem, AuthorizedAtomOrigin>>? = null
            for (variant in variants.filter { it.isNotEmpty() }) {
                val unmatched = old.toMutableList()
                val existingByChunk = variant.map { chunk ->
                    val match = unmatched.firstOrNull { origin(it)?.splitChunkIndex == chunk.origin.splitChunkIndex && origin(it)?.splitChunkIndex != null }
                        ?: unmatched.firstOrNull { origin(it)?.splitChunkIndex == null && it.setPrescriptions.size == chunk.timed.prescription.sets.size }
                    if (match != null) unmatched.remove(match)
                    match
                }.toMutableList()
                existingByChunk.indices.filter { existingByChunk[it] == null }.forEach { index ->
                    val match = unmatched.firstOrNull { origin(it)?.splitChunkIndex == null }
                    if (match != null) { existingByChunk[index] = match; unmatched.remove(match) }
                }
                fun place(index: Int, placed: List<Pair<ProgramSkeletonItem, AuthorizedAtomOrigin>>): List<Pair<ProgramSkeletonItem, AuthorizedAtomOrigin>>? {
                    if (index == variant.size) return placed.takeIf { result ->
                        result.sumOf { it.first.setPrescriptions.size } > count &&
                            old.all { previous -> result.any { it.first.localId == previous.localId } } &&
                            (!mandatory || splitTissueAllowed(snapshot, others + result.map { it.first }, parent.item.stableKey, 8.5))
                    }
                    val chunk = variant[index]
                    val existing = existingByChunk[index]
                    val destinations = (listOfNotNull(existing?.dayOfWeek) + days.sorted()).distinct()
                    for (day in destinations) {
                        val onDay = (others + placed.map { it.first }).filter { it.dayOfWeek == day }
                        if (onDay.any { it.exerciseStableKey == parent.item.stableKey }) continue
                        if (PrimaryStrengthAnchorSpacingPolicy.protects(snapshot,state,parent.item.stableKey,parent.continuity) &&
                            !PrimaryStrengthAnchorSpacingPolicy.allowed((others + placed.map { it.first })
                                .filter { it.exerciseStableKey==parent.item.stableKey }.map { it.dayOfWeek } + day)) continue
                        val rx = chunk.timed.prescription
                        val base = existing ?: residualItem(snapshot, chunk.timed.item, rx, "exact_${parent.id}_$index", day, 1)
                        val scalar = rx.sets.first()
                        val row = base.copy(dayOfWeek = day, orderIndex = (onDay.maxOfOrNull { it.orderIndex } ?: 0) + 1,
                            setCount = rx.sets.size, setPrescriptions = rx.sets, reps = scalar.reps, weightKg = scalar.weightKg,
                            seconds = scalar.seconds, restSeconds = rx.restSeconds, prescription = rx.text, weightSource = rx.weightSource,
                            estimatedDurationSeconds = chunk.timed.estimatedSeconds,
                            progressionAnchorSetIndex = if (parent.item.style in setOf(StrengthProgrammingStyle.TOP_SET_BACKOFF,
                                StrengthProgrammingStyle.TOP_SET_HYPERTROPHY, StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING))
                                rx.sets.maxByOrNull { it.weightKg }?.setIndex else base.progressionAnchorSetIndex,
                            progressionRole = if (parent.continuity && parent.item.styleVariant !in setOf("LIGHT", "VOLUME")) ProgressionRole.MAIN else base.progressionRole)
                        if (others.sumOf { it.setPrescriptions.size } + placed.sumOf { it.first.setPrescriptions.size } + rx.sets.size > capacity ||
                            onDay.sumOf(::plannedSeconds) + plannedSeconds(row) > request.sessionMinutes * 60 ||
                            (!mandatory && !projection.evaluate(onDay + row).feasible)) continue
                        val result = place(index + 1, placed + (row to chunk.origin))
                        if (result != null) return result
                    }
                    // A missing canonical chunk may remain deferred; never fabricate a smaller chunk.
                    if (variant.size <= 1) return null
                    if (existing == null) return place(index + 1, placed)
                    // Keep a previously reduced chunk if only its sibling can currently be restored.
                    val onDay = (others + placed.map { it.first }).filter { it.dayOfWeek == existing.dayOfWeek }
                    return if (onDay.none { it.exerciseStableKey == existing.exerciseStableKey } &&
                        (!PrimaryStrengthAnchorSpacingPolicy.protects(snapshot,state,parent.item.stableKey,parent.continuity) ||
                            PrimaryStrengthAnchorSpacingPolicy.allowed((others + placed.map { it.first }).filter { it.exerciseStableKey==parent.item.stableKey }
                                .map { it.dayOfWeek } + existing.dayOfWeek)) &&
                        others.sumOf { it.setPrescriptions.size } + placed.sumOf { it.first.setPrescriptions.size } + existing.setPrescriptions.size <= capacity)
                        place(index + 1, placed + (existing to chunk.origin)) else null
                }
                accepted = place(0, emptyList())
                if (accepted != null) break
            }
            val restored = accepted ?: continue
            val before = demand.residuals(rows)
            val candidateRows = others + restored.map { it.first }
            if (requiredResidualIds != null && demand.residuals(candidateRows).none { after -> after.id in requiredResidualIds &&
                after.residual < before.first { it.id == after.id }.residual - PLANNING_EPSILON }) continue
            rows = candidateRows
            restored.forEach { (row, origin) ->
                val atom = atomByLocalId[row.localId] ?: row.localId
                origins[atom] = origin; sources[atom] = parent.item
            }
            actions += ExactRestorationAction(parent.id, parent.prescription.sets.size - count,
                parent.prescription.sets.size - restored.sumOf { it.first.setPrescriptions.size },
                restored.map { it.first }, before, demand.residuals(rows),
                if (mandatory && restored.any { restoredRow -> !projection.evaluate(rows.filter { it.dayOfWeek == restoredRow.first.dayOfWeek }).feasible })
                    "ADVISORY_AUTHORIZED_HIGH_SET_SPLIT" else "PASS")
        }
        } while (rows.sumOf { it.setPrescriptions.size } > unitsBefore)
        return rows
    }

    private fun allowed(key: String): Boolean {
        val equipment = snapshot.exercises[key]?.equipment.orEmpty().split('|', ',').map(String::trim).filter(String::isNotBlank)
        return key !in request.excludedExerciseStableKeys && !snapshot.explicitlyRestricted(key) &&
            snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") && postProcessTissueAllowed(snapshot, state, key) &&
            (request.availableEquipment.isEmpty() || equipment.all { it == "BODYWEIGHT" || it in request.availableEquipment })
    }
}
