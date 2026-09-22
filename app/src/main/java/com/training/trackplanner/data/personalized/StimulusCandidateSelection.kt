package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** A typed B5 intent. It carries B4 authority without recomputing any B1-B4 decision. */
sealed interface StimulusSelectionTarget {
    val targetId: String
    val strategy: StimulusDoseStrategy
    val priority: TargetPriority

    data class Quality(val target: StimulusQualityTarget) : StimulusSelectionTarget {
        override val targetId: String = "QUALITY:${target.quality.name}"
        override val strategy: StimulusDoseStrategy = target.strategy
        override val priority: TargetPriority = target.priority
    }

    data class Task(val target: StimulusTaskTarget) : StimulusSelectionTarget {
        override val targetId: String = "TASK:${target.task}"
        override val strategy: StimulusDoseStrategy = target.strategy
        override val priority: TargetPriority = target.priority
    }
}

data class StimulusSelectedCandidate(
    val stableKey: String,
    val coveredTargetIds: Set<String>,
    val primaryTargetId: String,
    val selectionReasons: List<String>,
    val currentPrescriptionCompatibility: String,
    val targetSetsFromExistingPrescription: Int,
    val selectionRole: String
)

data class StimulusCandidateSelectionTrace(
    val targetId: String,
    val strategy: StimulusDoseStrategy,
    val priority: TargetPriority,
    val controlDirectCapabilityIdentities: List<String>,
    val selectionRequired: Boolean,
    val candidatePool: List<String>,
    val selectedStableKey: String?,
    val coveredByPreviouslySelectedStableKey: String?,
    val candidateRejectionReasons: Map<String, String> = emptyMap(),
    val reasonCodes: List<String> = emptyList()
)

/**
 * B5 proposal only. Its MaterialDemand is consumed exclusively by the explicit comparison
 * entry point; normal production generation never reads this object.
 */
data class StimulusCandidateSelectionPlan(
    val selectedCandidates: List<StimulusSelectedCandidate>,
    val traces: List<StimulusCandidateSelectionTrace>,
    val materialDemand: MaterialDemand,
    val shadowOnly: Boolean = true,
    val productionSelectionAuthority: Boolean = false,
    val prescriptionAuthority: Boolean = false,
    val placementAuthority: Boolean = false,
    val schedulingAuthority: Boolean = false
)

data class StimulusSelectionProgramDifference(
    val week: Int,
    val day: Int,
    val order: Int,
    val controlStableKey: String?,
    val experimentalStableKey: String?,
    val controlExerciseName: String?,
    val experimentalExerciseName: String?,
    val prescriptionShapeChanged: Boolean
)

data class StimulusSelectionProgramComparison(
    val control: GeneratedProgramSkeleton,
    val experimental: GeneratedProgramSkeleton,
    val targetPlan: StimulusTargetPlan,
    val selectionPlan: StimulusCandidateSelectionPlan,
    val controlAudit: StimulusTargetControlProgramAudit?,
    val experimentalAudit: StimulusTargetControlProgramAudit?,
    val differences: List<StimulusSelectionProgramDifference>,
    val controlStableKeys: Set<String>,
    val experimentalStableKeys: Set<String>,
    val addedStableKeys: Set<String>,
    val removedStableKeys: Set<String>,
    val sharedStableKeys: Set<String>,
    val winner: String? = null
) {
    init {
        require(winner == null) { "B5 comparison must not select an overall winner" }
    }
}

private data class MaterializedCandidate(
    val item: PlannedExercise,
    val prescription: PlannedPrescription,
    val compatibility: String,
    val rejectionReasons: Map<String, String>
)

/**
 * Deterministic B5 identity selector. It reads B4 targets and canonical relations, but never
 * recalculates Need, baseline, strategy, dose, or a target-compatible prescription.
 */
class StimulusTargetCandidateSelector(
    private val prescriptionPlanner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()
) {
    fun build(
        targetPlan: StimulusTargetPlan,
        control: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog
    ): StimulusCandidateSelectionPlan {
        val controlKeys = control.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey)
        val selected = linkedMapOf<String, StimulusSelectedCandidate>()
        val candidateItems = linkedMapOf<String, PlannedExercise>()
        val deferred = linkedMapOf<String, String>()
        val audit = linkedMapOf<String, String>()
        val traces = mutableListOf<StimulusCandidateSelectionTrace>()
        val targets = (targetPlan.qualityTargets.map(StimulusSelectionTarget::Quality) +
            targetPlan.taskTargets.map(StimulusSelectionTarget::Task)).sortedWith(
            compareBy<StimulusSelectionTarget> { priorityRank(it.priority) }.thenBy { it.targetId }
        )

        targets.forEach { intent ->
            val controlIdentities = controlIdentities(intent, controlKeys, snapshot, physicalQualityCatalog)
            if (controlIdentities.isNotEmpty()) {
                traces += trace(
                    intent, controlIdentities, false, emptyList(), null, null,
                    emptyMap(), listOf("DIRECT_CAPABILITY_IDENTITY_ALREADY_PRESENT", realizedGapCode(intent))
                )
                return@forEach
            }

            val reusable = selected.values.firstOrNull { selectedCandidate ->
                directlyCovers(intent, selectedCandidate.stableKey, snapshot, physicalQualityCatalog)
            }
            if (reusable != null) {
                val merged = reusable.copy(coveredTargetIds = reusable.coveredTargetIds + intent.targetId)
                selected[reusable.stableKey] = merged
                traces += trace(
                    intent, emptyList(), false, emptyList(), null, reusable.stableKey,
                    emptyMap(), listOf("TARGET_COVERED_BY_ALREADY_SELECTED_IDENTITY", realizedGapCode(intent))
                )
                return@forEach
            }

            if (!selectionAllowed(intent)) {
                val reason = noSelectionReason(intent)
                deferred[intent.targetId] = reason
                traces += trace(intent, emptyList(), false, emptyList(), null, null, emptyMap(), listOf(reason))
                return@forEach
            }

            val ranked = eligibleCandidates(intent, controlKeys, selected.keys, snapshot, state, request, physicalQualityCatalog)
            val pool = ranked.map { it.key }
            val rejections = linkedMapOf<String, String>()
            var materialized: MaterializedCandidate? = null
            for (candidate in ranked) {
                val result = materialize(intent, candidate.key, snapshot, state, request)
                if (result is MaterializedCandidateResult.Success) {
                    materialized = result.value
                    break
                }
                rejections[candidate.key] = (result as MaterializedCandidateResult.Failure).reason
            }
            val chosen = materialized
            if (chosen == null) {
                val reason = if (pool.isEmpty()) "TARGET_REQUIRES_SELECTION_BUT_NO_MATERIALIZABLE_CANDIDATE"
                else "TARGET_REQUIRES_SELECTION_BUT_NO_MATERIALIZABLE_CANDIDATE"
                deferred[intent.targetId] = reason
                traces += trace(intent, emptyList(), true, pool, null, null, rejections, listOf(reason))
                return@forEach
            }
            val item = chosen.item
            candidateItems[item.stableKey] = candidateItems[item.stableKey]?.let { old ->
                old.copy(targetSets = maxOf(old.targetSets, item.targetSets), representedObjectives = old.representedObjectives + item.representedObjectives)
            } ?: item
            val selectedCandidate = StimulusSelectedCandidate(
                stableKey = item.stableKey,
                coveredTargetIds = setOf(intent.targetId),
                primaryTargetId = intent.targetId,
                selectionReasons = listOf("B4_TARGET_REQUESTED_IDENTITY", "B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY"),
                currentPrescriptionCompatibility = chosen.compatibility,
                targetSetsFromExistingPrescription = chosen.prescription.sets.size,
                selectionRole = item.role
            )
            selected[item.stableKey] = selectedCandidate
            audit[item.stableKey] = "B5_SELECTED_CANONICAL_IDENTITY"
            traces += trace(
                intent, emptyList(), true, pool, item.stableKey, null, rejections,
                listOf("SELECTION_IDENTITY_PRESENT", chosen.compatibility, "B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY")
            )
        }

        val unresolvedDemand = deferred.mapValues { it.value }
        val materialDemand = MaterialDemand(candidateItems.values.toList(), unresolvedDemand, audit)
        return StimulusCandidateSelectionPlan(selected.values.toList(), traces, materialDemand)
    }

    private fun trace(
        intent: StimulusSelectionTarget,
        controlIdentities: List<String>,
        required: Boolean,
        pool: List<String>,
        selected: String?,
        reused: String?,
        rejections: Map<String, String>,
        reasons: List<String>
    ) = StimulusCandidateSelectionTrace(
        targetId = intent.targetId,
        strategy = intent.strategy,
        priority = intent.priority,
        controlDirectCapabilityIdentities = controlIdentities,
        selectionRequired = required,
        candidatePool = pool,
        selectedStableKey = selected,
        coveredByPreviouslySelectedStableKey = reused,
        candidateRejectionReasons = rejections,
        reasonCodes = reasons.distinct()
    )

    private data class CandidateKey(val key: String, val targetCompatibleHistory: Boolean, val history: Boolean, val freeWeightCompatible: Boolean,
        val highConfidence: Boolean, val redundant: Boolean)

    private fun eligibleCandidates(
        intent: StimulusSelectionTarget,
        controlKeys: Set<String>,
        selectedKeys: Set<String>,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog
    ): List<CandidateKey> {
        val keys = snapshot.exercises.keys.asSequence().filter { key ->
            when (intent) {
                is StimulusSelectionTarget.Quality -> !physicalQualityCatalog.isAssessmentOnly(key) && physicalQualityCatalog.relations(key).any {
                    it.qualityId == intent.target.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
                }
                is StimulusSelectionTarget.Task -> snapshot.badmintonDirectObjectives[key].orEmpty().contains(intent.target.task) &&
                    snapshot.activityKind(key) in TASK_ACTIVITY_KINDS
            }
        }.filter { key -> snapshot.metadata[key]?.planningEligibility in SELECTABLE_ELIGIBILITY }
            .filterNot(snapshot::explicitlyRestricted)
            .filter { key -> key !in request.excludedExerciseStableKeys }
            .filter { key -> key !in snapshot.recoverySignals.tissueRestrictedStableKeys }
            .filter { key -> equipmentCompatible(snapshot, key, request) }
            .filter { key -> freeWeightAllowed(snapshot, state, key) }
            .filterNot { snapshot.activityKind(it) == PlannedActivityKind.GENERIC_COURT_SESSION }
            .map { key ->
                val historyRows = snapshot.allConfirmedSets.filter { it.stableKey == key }
                val targetCompatible = when (intent) {
                    is StimulusSelectionTarget.Quality -> intent.target.quality in TARGET_CLASSIFIED_QUALITIES && historyRows.any {
                        compatibleHistory(intent.target.quality, provisionalRealizedStimulusClass(it))
                    }
                    is StimulusSelectionTarget.Task -> false
                }
                CandidateKey(
                    key = key,
                    targetCompatibleHistory = targetCompatible,
                    history = historyRows.isNotEmpty(),
                    freeWeightCompatible = state.freeWeightWillingness != FreeWeightWillingness.PREFER_FAMILIAR || !snapshot.isFreeWeight(key),
                    highConfidence = snapshot.metadata[key]?.sourceConfidenceLevel == "HIGH",
                    redundant = redundancyGroup(snapshot, key).isNotBlank() &&
                        (controlKeys + selectedKeys).any { existingKey -> redundancyGroup(snapshot, existingKey) == redundancyGroup(snapshot, key) }
                )
            }.toList()
        return keys.sortedWith(
            compareByDescending<CandidateKey> { it.targetCompatibleHistory }
                .thenByDescending { it.history }
                .thenByDescending { it.freeWeightCompatible }
                .thenByDescending { it.highConfidence }
                .thenBy { it.redundant }
                .thenBy { it.key }
        )
    }

    private fun materialize(
        intent: StimulusSelectionTarget,
        key: String,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest
    ): MaterializedCandidateResult {
        val role = "CANONICAL_STIMULUS_${intent.targetId.replace(':', '_')}"
        val probe = PlannedExercise(key, role, "B5 canonical identity probe", priorityBridge(intent.priority), style = StrengthProgrammingStyle.NONE)
        val prescription = runCatching {
            prescriptionPlanner.prescribe(snapshot, state.strengthIntent, probe, StrengthProgrammingStyle.NONE)
        }.getOrElse { return MaterializedCandidateResult.Failure("NO_SAFE_PRESCRIPTION_AUTHORITY") }
        if (prescription.sets.isEmpty()) return MaterializedCandidateResult.Failure("NO_SAFE_PRESCRIPTION_AUTHORITY")
        val seconds = TimedPlannedExercise(probe, prescription).estimatedSeconds
        if (seconds > request.sessionMinutes * 60) return MaterializedCandidateResult.Failure("MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME")
        val item = probe.copy(
            targetSets = prescription.sets.size,
            reason = "B5 selected a canonical direct identity; prescription compatibility is deferred to B6.",
            representedObjectives = if (intent is StimulusSelectionTarget.Task) setOf(intent.target.task) else emptySet()
        )
        val compatibility = when (intent) {
            is StimulusSelectionTarget.Quality -> when (intent.target.quality) {
                TrainableQuality.STRENGTH -> if (prescription.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.STRENGTH_LIKE })
                    "SELECTION_TARGET_IDENTITY_MATERIALIZED" else "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6"
                TrainableQuality.HYPERTROPHY -> if (prescription.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE })
                    "SELECTION_TARGET_IDENTITY_MATERIALIZED" else "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6"
                else -> "SELECTION_TARGET_IDENTITY_MATERIALIZED"
            }
            is StimulusSelectionTarget.Task -> "SELECTION_TARGET_IDENTITY_MATERIALIZED"
        }
        return MaterializedCandidateResult.Success(MaterializedCandidate(item, prescription, compatibility, emptyMap()))
    }

    private sealed interface MaterializedCandidateResult {
        data class Success(val value: MaterializedCandidate) : MaterializedCandidateResult
        data class Failure(val reason: String) : MaterializedCandidateResult
    }

    private fun controlIdentities(intent: StimulusSelectionTarget, keys: Set<String>, snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog): List<String> = keys.filter { directlyCovers(intent, it, snapshot, catalog) }.sorted()

    private fun directlyCovers(intent: StimulusSelectionTarget, key: String, snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog): Boolean = when (intent) {
        is StimulusSelectionTarget.Quality -> catalog.relations(key).any {
            it.qualityId == intent.target.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
        }
        is StimulusSelectionTarget.Task -> snapshot.activityKind(key) in TASK_ACTIVITY_KINDS &&
            intent.target.task in snapshot.badmintonDirectObjectives[key].orEmpty()
    }

    private fun selectionAllowed(intent: StimulusSelectionTarget): Boolean = when (intent) {
        is StimulusSelectionTarget.Quality -> intent.strategy in QUALITY_SELECTION_STRATEGIES
        is StimulusSelectionTarget.Task -> intent.strategy in TASK_SELECTION_STRATEGIES
    }

    private fun noSelectionReason(intent: StimulusSelectionTarget): String = when (intent.strategy) {
        StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY -> "DISTRIBUTION_AUTHORITY_DEFERRED"
        StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE -> "REDUCTION_DOES_NOT_AUTHORIZE_NEW_EXERCISE"
        StimulusDoseStrategy.NO_MINIMUM_TARGET -> "NO_MINIMUM_TARGET"
        StimulusDoseStrategy.UNRESOLVED -> "TARGET_UNRESOLVED"
        else -> "TARGET_SELECTION_NOT_AUTHORIZED_BY_B5_STRATEGY"
    }

    private fun realizedGapCode(intent: StimulusSelectionTarget): String = when (intent) {
        is StimulusSelectionTarget.Quality -> "REALIZED_STIMULUS_GAP_DEFERRED_TO_B6"
        is StimulusSelectionTarget.Task -> "TASK_REALIZATION_GAP_DEFERRED_TO_B6"
    }

    private fun priorityRank(priority: TargetPriority): Int = when (priority) {
        TargetPriority.PRIMARY -> 0
        TargetPriority.SECONDARY -> 1
        TargetPriority.MAINTENANCE -> 2
        TargetPriority.BACKGROUND -> 3
        TargetPriority.NONE -> 4
        TargetPriority.UNRESOLVED -> 5
    }

    private fun priorityBridge(priority: TargetPriority): Int = when (priority) {
        TargetPriority.PRIMARY -> 100
        TargetPriority.SECONDARY -> 90
        TargetPriority.MAINTENANCE -> 85
        TargetPriority.BACKGROUND -> 70
        TargetPriority.NONE, TargetPriority.UNRESOLVED -> 0
    }

    private fun compatibleHistory(quality: TrainableQuality, realized: RealizedStimulusClass): Boolean = when (quality) {
        TrainableQuality.STRENGTH -> realized == RealizedStimulusClass.STRENGTH_LIKE
        TrainableQuality.HYPERTROPHY -> realized == RealizedStimulusClass.HYPERTROPHY_LIKE
        else -> false
    }

    private fun equipmentCompatible(snapshot: PlanningHistorySnapshot, key: String, request: ProgramSkeletonRequest): Boolean {
        if (request.availableEquipment.isEmpty()) return true
        val equipment = snapshot.exercises.getValue(key).equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
        return equipment.all { it == "BODYWEIGHT" || it in request.availableEquipment }
    }

    private fun freeWeightAllowed(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, key: String): Boolean {
        if (state.freeWeightWillingness !in setOf(FreeWeightWillingness.AVOID, FreeWeightWillingness.UNRESOLVED)) return true
        return !snapshot.isFreeWeight(key) || snapshot.allConfirmedSets.any { it.stableKey == key }
    }

    private fun redundancyGroup(snapshot: PlanningHistorySnapshot, key: String): String = snapshot.metadata[key]?.redundancyGroup.orEmpty()

    private companion object {
        val SELECTABLE_ELIGIBILITY = setOf("PROGRAM_SELECTABLE", "SELECTABLE")
        val TASK_ACTIVITY_KINDS = setOf(
            PlannedActivityKind.RESISTANCE,
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
            PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        )
        val TARGET_CLASSIFIED_QUALITIES = setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)
        val QUALITY_SELECTION_STRATEGIES = setOf(
            StimulusDoseStrategy.HOLD_PERSONAL_BASELINE,
            StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION,
            StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE,
            StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY,
            StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE
        )
        val TASK_SELECTION_STRATEGIES = setOf(
            StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY
        )
    }
}

class StimulusSelectionProgramComparisonEngine {
    fun compare(
        control: GeneratedProgramSkeleton,
        experimental: GeneratedProgramSkeleton,
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        controlAudit: StimulusTargetControlProgramAudit?,
        experimentalAudit: StimulusTargetControlProgramAudit?
    ): StimulusSelectionProgramComparison {
        val controlKeys = control.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey)
        val experimentalKeys = experimental.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey)
        val controlRows = control.items.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        val experimentalRows = experimental.items.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        val differences = (controlRows.keys + experimentalRows.keys).sortedWith(compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third }).mapNotNull { key ->
            val old = controlRows[key]
            val next = experimentalRows[key]
            if (old?.exerciseStableKey == next?.exerciseStableKey && old?.prescription == next?.prescription && old?.setPrescriptions == next?.setPrescriptions) null
            else StimulusSelectionProgramDifference(key.first, key.second, key.third, old?.exerciseStableKey, next?.exerciseStableKey,
                old?.exerciseName, next?.exerciseName, old?.prescription != next?.prescription || old?.setPrescriptions != next?.setPrescriptions)
        }
        return StimulusSelectionProgramComparison(
            control = control,
            experimental = experimental,
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            controlAudit = controlAudit,
            experimentalAudit = experimentalAudit,
            differences = differences,
            controlStableKeys = controlKeys,
            experimentalStableKeys = experimentalKeys,
            addedStableKeys = experimentalKeys - controlKeys,
            removedStableKeys = controlKeys - experimentalKeys,
            sharedStableKeys = controlKeys intersect experimentalKeys
        )
    }
}

internal fun StimulusCandidateSelectionPlan.toCompactJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("productionSelectionAuthority", productionSelectionAuthority)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("placementAuthority", placementAuthority)
    .put("schedulingAuthority", schedulingAuthority)
    .put("selectedCandidates", JSONArray(selectedCandidates.map { candidate -> JSONObject()
        .put("stableKey", candidate.stableKey)
        .put("coveredTargetIds", JSONArray(candidate.coveredTargetIds.sorted()))
        .put("primaryTargetId", candidate.primaryTargetId)
        .put("selectionReasons", JSONArray(candidate.selectionReasons))
        .put("currentPrescriptionCompatibility", candidate.currentPrescriptionCompatibility)
        .put("targetSetsFromExistingPrescription", candidate.targetSetsFromExistingPrescription)
        .put("selectionRole", candidate.selectionRole)
    }))
    .put("traces", JSONArray(traces.map { trace -> JSONObject()
        .put("targetId", trace.targetId).put("strategy", trace.strategy.name).put("priority", trace.priority.name)
        .put("controlDirectCapabilityIdentities", JSONArray(trace.controlDirectCapabilityIdentities))
        .put("selectionRequired", trace.selectionRequired).put("candidatePool", JSONArray(trace.candidatePool))
        .put("selectedStableKey", trace.selectedStableKey).put("coveredByPreviouslySelectedStableKey", trace.coveredByPreviouslySelectedStableKey)
        .put("candidateRejectionReasons", JSONObject(trace.candidateRejectionReasons))
        .put("reasonCodes", JSONArray(trace.reasonCodes))
    }))
    .put("materialDemand", JSONObject()
        .put("candidateStableKeys", JSONArray(materialDemand.candidates.map(PlannedExercise::stableKey)))
        .put("deferred", JSONObject(materialDemand.deferred))
        .put("audit", JSONObject(materialDemand.audit)))

internal fun StimulusSelectionProgramComparison.toCompactJson(): JSONObject = JSONObject()
    .put("controlStableKeys", JSONArray(controlStableKeys.sorted()))
    .put("experimentalStableKeys", JSONArray(experimentalStableKeys.sorted()))
    .put("addedStableKeys", JSONArray(addedStableKeys.sorted()))
    .put("removedStableKeys", JSONArray(removedStableKeys.sorted()))
    .put("sharedStableKeys", JSONArray(sharedStableKeys.sorted()))
    .put("winner", winner)
    .put("selectionPlan", selectionPlan.toCompactJson())
    .put("differences", JSONArray(differences.map { difference -> JSONObject()
        .put("week", difference.week)
        .put("day", difference.day)
        .put("order", difference.order)
        .put("controlStableKey", difference.controlStableKey)
        .put("experimentalStableKey", difference.experimentalStableKey)
        .put("controlExerciseName", difference.controlExerciseName)
        .put("experimentalExerciseName", difference.experimentalExerciseName)
        .put("prescriptionShapeChanged", difference.prescriptionShapeChanged)
    }))
