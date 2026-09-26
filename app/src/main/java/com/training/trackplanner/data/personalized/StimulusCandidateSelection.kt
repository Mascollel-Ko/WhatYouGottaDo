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
    val selectionRole: String,
    val probePrescriptionCompatibility: SelectionProbePrescriptionCompatibility = when (currentPrescriptionCompatibility) {
        "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6" -> SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
        else -> runCatching { SelectionProbePrescriptionCompatibility.valueOf(currentPrescriptionCompatibility) }
            .getOrDefault(SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED)
    }
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
    val reasonCodes: List<String> = emptyList(),
    /** Exact B5 owner identity when the selected/reused trace exposes it. */
    val selectedSelectionRole: String? = null,
    val coveredByPreviouslySelectedSelectionRole: String? = null,
    /** Role for each rejected candidate when the producer knows the exact probe role. */
    val candidateSelectionRoles: Map<String, String> = emptyMap()
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

/** Observation of what happened to a B5 identity after the existing builder finished. */
data class StimulusCandidateMaterializationTrace(
    val targetId: String,
    val selectedStableKey: String?,
    val selectedAtB5: Boolean,
    val directIdentityVerifiedAtSelection: Boolean? = null,
    val presentInFinalExperimentalSkeleton: Boolean,
    val finalWeeklyOccurrences: Int,
    val finalTotalSetUnits: Int,
    val directIdentityStillValid: Boolean = presentInFinalExperimentalSkeleton && directIdentityVerifiedAtSelection == true,
    val realizedTargetStatus: String?,
    val reasonCodes: List<String>,
    val evidenceBasis: StimulusEvidenceBasis = StimulusEvidenceBasis.UNCLASSIFIED,
    /** Exact B5 owner role when the producer has one. */
    val selectionRole: String? = null
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
    val materializationTraces: List<StimulusCandidateMaterializationTrace> = emptyList(),
    val prescriptionRealizationPlan: StimulusPrescriptionRealizationPlan? = null,
    val prescriptionAuthorizationPlan: StimulusPrescriptionAuthorizationPlan? = null,
    val prescriptionMaterializationAudits: List<StimulusPrescriptionMaterializationAudit> = emptyList(),
    val experimentalReadinessAudit: StimulusExperimentalReadinessAudit? = null,
    val winner: String? = null,
    /** B8 is attached only by the explicit internal evaluation entry point. */
    val productionCutoverAuthority: StimulusProductionCutoverAuthorityDecision? = null
) {
    init {
        require(winner == null) { "B5 comparison must not select an overall winner" }
    }

    /** B7 vocabulary alias for downstream shadow consumers. */
    val experimentalCutoverReadinessAudit: StimulusExperimentalReadinessAudit?
        get() = experimentalReadinessAudit

    /** Exact owner identities retained alongside the stableKey summaries for B7 provenance. */
    val controlOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>
        get() = control.items.mapTo(linkedSetOf()) { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
    val experimentalOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>
        get() = experimental.items.mapTo(linkedSetOf()) { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
    val addedOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>
        get() = experimentalOwnerIdentities - controlOwnerIdentities
    val removedOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>
        get() = controlOwnerIdentities - experimentalOwnerIdentities
    val sharedOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>
        get() = controlOwnerIdentities intersect experimentalOwnerIdentities
}

private data class MaterializedCandidate(
    val item: PlannedExercise,
    val prescription: PlannedPrescription,
    val compatibility: SelectionProbePrescriptionCompatibility,
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
        val historyByStableKey = snapshot.allConfirmedSets.groupBy(PlanningSetRecord::stableKey)
        val historyIndex = HistoryIndex(
            historyByStableKey = historyByStableKey,
            historyStableKeys = historyByStableKey.keys,
            strengthCompatibleHistoryKeys = historyByStableKey.filterValues { rows -> rows.any { historyCompatible(snapshot, TrainableQuality.STRENGTH, it) } }.keys,
            hypertrophyCompatibleHistoryKeys = historyByStableKey.filterValues { rows -> rows.any { historyCompatible(snapshot, TrainableQuality.HYPERTROPHY, it) } }.keys
        )
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
                    emptyMap(), listOf("TARGET_COVERED_BY_ALREADY_SELECTED_IDENTITY", realizedGapCode(intent)),
                    reusedRole = reusable.selectionRole
                )
                return@forEach
            }

            if (!selectionAllowed(intent)) {
                val reason = noSelectionReason(intent)
                deferred[intent.targetId] = reason
                traces += trace(intent, emptyList(), false, emptyList(), null, null, emptyMap(), listOf(reason))
                return@forEach
            }

            val ranked = eligibleCandidates(intent, controlKeys, selected.keys, snapshot, state, request, physicalQualityCatalog, historyIndex)
            val pool = ranked.map { it.key }
            val rejections = linkedMapOf<String, String>()
            val rejectionRoles = linkedMapOf<String, String>()
            var materialized: MaterializedCandidate? = null
            for (candidate in ranked) {
                val result = materialize(intent, candidate.key, snapshot, state, request)
                if (result is MaterializedCandidateResult.Success) {
                    materialized = result.value
                    break
                }
                rejections[candidate.key] = (result as MaterializedCandidateResult.Failure).reason
                rejectionRoles[candidate.key] = roleFor(intent)
            }
            val chosen = materialized
            if (chosen == null) {
                val reason = if (pool.isEmpty()) "TARGET_REQUIRES_SELECTION_BUT_NO_MATERIALIZABLE_CANDIDATE"
                else "TARGET_REQUIRES_SELECTION_BUT_NO_MATERIALIZABLE_CANDIDATE"
                deferred[intent.targetId] = reason
                traces += trace(intent, emptyList(), true, pool, null, null, rejections, listOf(reason), candidateRoles = rejectionRoles)
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
                currentPrescriptionCompatibility = when (chosen.compatibility) {
                    SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE -> "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6"
                    else -> chosen.compatibility.name
                },
                targetSetsFromExistingPrescription = chosen.prescription.sets.size,
                selectionRole = item.role,
                probePrescriptionCompatibility = chosen.compatibility
            )
            selected[item.stableKey] = selectedCandidate
            audit[item.stableKey] = "B5_SELECTED_CANONICAL_IDENTITY"
            traces += trace(
                intent, emptyList(), true, pool, item.stableKey, null, rejections,
                listOf("SELECTION_IDENTITY_PRESENT", chosen.compatibility.name, "B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY"),
                selectedRole = item.role,
                candidateRoles = rejections.keys.associateWith { roleFor(intent) }
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
        reasons: List<String>,
        selectedRole: String? = null,
        reusedRole: String? = null,
        candidateRoles: Map<String, String> = emptyMap()
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
        reasonCodes = reasons.distinct(),
        selectedSelectionRole = selectedRole,
        coveredByPreviouslySelectedSelectionRole = reusedRole,
        candidateSelectionRoles = candidateRoles
    )

    private fun roleFor(intent: StimulusSelectionTarget): String =
        "CANONICAL_STIMULUS_${intent.targetId.replace(':', '_')}"

    private data class CandidateKey(val key: String, val targetCompatibleHistory: Boolean, val history: Boolean, val freeWeightCompatible: Boolean,
        val highConfidence: Boolean, val redundant: Boolean)

    private data class HistoryIndex(
        val historyByStableKey: Map<String, List<PlanningSetRecord>>,
        val historyStableKeys: Set<String>,
        val strengthCompatibleHistoryKeys: Set<String>,
        val hypertrophyCompatibleHistoryKeys: Set<String>
    )

    private fun eligibleCandidates(
        intent: StimulusSelectionTarget,
        controlKeys: Set<String>,
        selectedKeys: Set<String>,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog,
        historyIndex: HistoryIndex
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
            .filter { key -> freeWeightAllowed(snapshot, state, key, historyIndex.historyStableKeys) }
            .filterNot { snapshot.activityKind(it) == PlannedActivityKind.GENERIC_COURT_SESSION }
            .map { key ->
                val targetCompatible = when (intent) {
                    is StimulusSelectionTarget.Quality -> when (intent.target.quality) {
                        TrainableQuality.STRENGTH -> key in historyIndex.strengthCompatibleHistoryKeys
                        TrainableQuality.HYPERTROPHY -> key in historyIndex.hypertrophyCompatibleHistoryKeys
                        else -> false
                    }
                    is StimulusSelectionTarget.Task -> false
                }
                CandidateKey(
                    key = key,
                    targetCompatibleHistory = targetCompatible,
                    history = key in historyIndex.historyStableKeys,
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
        val role = roleFor(intent)
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
            is StimulusSelectionTarget.Quality -> when (intent.target.evidenceBasis) {
                StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED -> if (
                    (intent.target.quality == TrainableQuality.STRENGTH &&
                        prescription.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.STRENGTH_LIKE }) ||
                    (intent.target.quality == TrainableQuality.HYPERTROPHY &&
                        prescription.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE })
                )
                    SelectionProbePrescriptionCompatibility.REALIZED_COMPATIBLE else SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
                StimulusEvidenceBasis.CANONICAL_CAPABILITY_PROXY,
                StimulusEvidenceBasis.UNCLASSIFIED -> SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
                StimulusEvidenceBasis.CANONICAL_TASK_RELATION -> SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
            }
            is StimulusSelectionTarget.Task -> SelectionProbePrescriptionCompatibility.DIRECTIONAL_TASK_IDENTITY_ONLY
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
        is StimulusSelectionTarget.Quality -> intent.strategy in QUALITY_SELECTION_STRATEGIES ||
            (intent.strategy == StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY &&
                intent.target.evidenceBasis == StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED)
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

    private fun historyCompatible(snapshot: PlanningHistorySnapshot, quality: TrainableQuality, row: PlanningSetRecord): Boolean =
        if (snapshot.stimulusExposureLedger.setObservations.isEmpty()) compatibleHistory(quality, provisionalRealizedStimulusClass(row))
        else if (quality in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)) {
            realizedPrescriptionCompatible(quality, snapshot.reviewedRealization(row))
        } else capabilityProxyCompatible(snapshot.reviewedSourceAuthority(row))

    private fun equipmentCompatible(snapshot: PlanningHistorySnapshot, key: String, request: ProgramSkeletonRequest): Boolean {
        if (request.availableEquipment.isEmpty()) return true
        val equipment = snapshot.exercises.getValue(key).equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
        return equipment.all { it == "BODYWEIGHT" || it in request.availableEquipment }
    }

    private fun freeWeightAllowed(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, key: String, historyStableKeys: Set<String>): Boolean {
        if (state.freeWeightWillingness !in setOf(FreeWeightWillingness.AVOID, FreeWeightWillingness.UNRESOLVED)) return true
        return !snapshot.isFreeWeight(key) || key in historyStableKeys
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
        val materializationTraces = selectionPlan.traces.map { trace ->
            val effectiveIdentity = trace.selectedStableKey ?: trace.coveredByPreviouslySelectedStableKey
            val effectiveRole = trace.selectedSelectionRole ?: trace.coveredByPreviouslySelectedSelectionRole
            val selectedAtB5 = effectiveIdentity != null
            val finalRows = effectiveIdentity?.let { key ->
                experimental.items.filter { item ->
                    item.exerciseStableKey == key && (effectiveRole == null || item.selectionRole == effectiveRole)
                }
            }.orEmpty()
            val present = finalRows.isNotEmpty()
            val candidate = effectiveIdentity?.let { key -> selectionPlan.selectedCandidates.firstOrNull {
                it.stableKey == key && (effectiveRole == null || it.selectionRole == effectiveRole)
            } }
            val directVerified = effectiveIdentity?.let { candidate?.coveredTargetIds?.contains(trace.targetId) == true }
            val realizedStatus = realizedTargetStatus(targetPlan, experimentalAudit, trace.targetId)
            val reasons = linkedSetOf<String>()
            if (!selectedAtB5) {
                if (trace.reasonCodes.contains("DIRECT_CAPABILITY_IDENTITY_ALREADY_PRESENT")) {
                    reasons += "CONTROL_DIRECT_IDENTITY_ALREADY_PRESENT"
                } else if (!trace.selectionRequired || trace.reasonCodes.any { it in NO_SELECTION_REASON_CODES }) {
                    reasons += "SELECTION_NOT_REQUESTED"
                }
            } else if (present) {
                reasons += "SELECTION_TARGET_IDENTITY_MATERIALIZED"
                if (candidate?.probePrescriptionCompatibility == SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE &&
                    realizedStatus in UNMET_REALIZATION_STATUSES
                ) {
                    reasons += "TARGET_REALIZATION_STILL_UNMET"
                    reasons += "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6"
                }
            } else {
                reasons += "CANDIDATE_SELECTED_BUT_NOT_MATERIALIZED"
            }
            reasons += trace.reasonCodes.filter { it !in setOf("SELECTION_IDENTITY_PRESENT") }
            StimulusCandidateMaterializationTrace(
                targetId = trace.targetId,
                selectedStableKey = effectiveIdentity,
                selectedAtB5 = selectedAtB5,
                directIdentityVerifiedAtSelection = directVerified,
                presentInFinalExperimentalSkeleton = present,
                finalWeeklyOccurrences = finalRows.map { it.weekNumber to it.dayOfWeek }.distinct().size,
                finalTotalSetUnits = finalRows.sumOf { it.setPrescriptions.size },
                directIdentityStillValid = present && directVerified == true,
                realizedTargetStatus = realizedStatus,
                reasonCodes = reasons.toList(),
                evidenceBasis = when {
                    trace.targetId.startsWith("QUALITY:") -> targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == trace.targetId }?.evidenceBasis
                        ?: StimulusEvidenceBasis.UNCLASSIFIED
                    trace.targetId.startsWith("TASK:") -> StimulusEvidenceBasis.CANONICAL_TASK_RELATION
                    else -> StimulusEvidenceBasis.UNCLASSIFIED
                },
                selectionRole = effectiveRole
            )
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
            sharedStableKeys = controlKeys intersect experimentalKeys,
            materializationTraces = materializationTraces
        )
    }

    private fun realizedTargetStatus(
        targetPlan: StimulusTargetPlan,
        audit: StimulusTargetControlProgramAudit?,
        targetId: String
    ): String? = when {
        targetId.startsWith("QUALITY:") -> targetPlan.qualityTargets
            .firstOrNull { "QUALITY:${it.quality.name}" == targetId }
            ?.let { target -> audit?.qualityAudits?.firstOrNull { it.quality == target.quality }?.weeklyDirectUnitsStatus?.name }
        targetId.startsWith("TASK:") -> targetPlan.taskTargets
            .firstOrNull { "TASK:${it.task}" == targetId }
            ?.let { target -> audit?.taskAudits?.firstOrNull { it.task == target.task }?.status?.name }
        else -> null
    }

    private companion object {
        val UNMET_REALIZATION_STATUSES = setOf(
            StimulusTargetControlStatus.DIRECT_ABSENT.name,
            StimulusTargetControlStatus.BELOW_BAND.name,
            StimulusTargetControlStatus.UNRESOLVED.name
        )
        val NO_SELECTION_REASON_CODES = setOf(
            "NO_MINIMUM_TARGET",
            "REDUCTION_DOES_NOT_AUTHORIZE_NEW_EXERCISE",
            "DISTRIBUTION_AUTHORITY_DEFERRED",
            "TARGET_UNRESOLVED",
            "TARGET_SELECTION_NOT_AUTHORIZED_BY_B5_STRATEGY"
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
        .put("probePrescriptionCompatibility", candidate.probePrescriptionCompatibility.name)
        .put("targetSetsFromExistingPrescription", candidate.targetSetsFromExistingPrescription)
        .put("selectionRole", candidate.selectionRole)
    }))
    .put("traces", JSONArray(traces.map { trace -> JSONObject()
        .put("targetId", trace.targetId).put("strategy", trace.strategy.name).put("priority", trace.priority.name)
        .put("controlDirectCapabilityIdentities", JSONArray(trace.controlDirectCapabilityIdentities))
        .put("selectionRequired", trace.selectionRequired).put("candidatePool", JSONArray(trace.candidatePool))
        .put("selectedStableKey", trace.selectedStableKey).put("selectedSelectionRole", trace.selectedSelectionRole)
        .put("coveredByPreviouslySelectedStableKey", trace.coveredByPreviouslySelectedStableKey)
        .put("coveredByPreviouslySelectedSelectionRole", trace.coveredByPreviouslySelectedSelectionRole)
        .put("candidateRejectionReasons", JSONObject(trace.candidateRejectionReasons))
        .put("candidateSelectionRoles", JSONObject(trace.candidateSelectionRoles))
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
    .put("addedOwnerIdentities", JSONArray(addedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
    }))
    .put("removedOwnerIdentities", JSONArray(removedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
    }))
    .put("sharedOwnerIdentities", JSONArray(sharedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
    }))
    .put("materializationTraces", JSONArray(materializationTraces.map { trace -> JSONObject()
        .put("targetId", trace.targetId)
        .put("effectiveSelectedStableKey", trace.selectedStableKey)
        .put("selectionRole", trace.selectionRole)
        .put("selectedAtB5", trace.selectedAtB5)
        .put("directIdentityVerifiedAtSelection", trace.directIdentityVerifiedAtSelection)
        .put("presentInFinalExperimentalSkeleton", trace.presentInFinalExperimentalSkeleton)
        .put("finalWeeklyOccurrences", trace.finalWeeklyOccurrences)
        .put("finalTotalSetUnits", trace.finalTotalSetUnits)
        .put("directIdentityStillValid", trace.directIdentityStillValid)
        .put("evidenceBasis", trace.evidenceBasis.name)
        .put("realizedTargetStatus", trace.realizedTargetStatus)
        .put("reasonCodes", JSONArray(trace.reasonCodes))
    }))
    .put("winner", winner)
    .put("experimentalReadinessAudit", experimentalReadinessAudit?.toJson())
    .put("productionCutoverAuthority", productionCutoverAuthority?.toJson())
    .put("prescriptionAuthorizationPlan", prescriptionAuthorizationPlan?.let { plan ->
        JSONObject().put("shadowOnly", plan.shadowOnly).put("productionAuthority", plan.productionAuthority)
            .put("multiQualityResolutions", JSONArray(plan.multiQualityResolutions.values.sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole })).map { resolution -> JSONObject()
                .put("stableKey", resolution.owner.stableKey).put("selectionRole", resolution.owner.selectionRole)
                .put("status", resolution.status.name)
                .put("authorityIdentities", JSONArray(resolution.authorityIdentities.map { identity -> JSONObject()
                    .put("stableKey", identity.stableKey).put("selectionRole", identity.selectionRole).put("quality", identity.quality.name)
                }))
                .put("reasonCodes", JSONArray(resolution.reasonCodes))
            }))
            .put("authorizations", JSONArray(plan.authorizations.map { authorization -> JSONObject()
                .put("targetId", authorization.targetId).put("quality", authorization.quality?.name)
                .put("ownerStableKey", authorization.owner?.stableKey).put("ownerSelectionRole", authorization.owner?.selectionRole)
                .put("source", authorization.source?.name).put("status", authorization.status.name)
                .put("executionAuthority", authorization.executionAuthority.name)
                .put("reasonCodes", JSONArray(authorization.reasonCodes))
            }))
    })
    .put("prescriptionMaterializationAudits", JSONArray(prescriptionMaterializationAudits.map { audit -> JSONObject()
        .put("targetId", audit.targetId).put("quality", audit.quality?.name)
        .put("ownerStableKey", audit.owner?.stableKey).put("ownerSelectionRole", audit.owner?.selectionRole)
        .put("authorizedWeeklySetUnits", audit.authorizedWeeklySetUnits)
        .put("materializedWeeklySetUnits", audit.materializedWeeklySetUnits)
        .put("targetCompatibleMaterializedUnits", audit.targetCompatibleMaterializedUnits)
        .put("shortfall", audit.shortfall).put("overrun", audit.overrun)
        .put("prescriptionPreservedOrSubset", audit.prescriptionPreservedOrSubset)
        .put("minimumWeeklyMaterializedUnits", audit.minimumWeeklyMaterializedUnits)
        .put("minimumWeeklyCompatibleUnits", audit.minimumWeeklyCompatibleUnits)
        .put("maximumWeeklyShortfall", audit.maximumWeeklyShortfall)
        .put("maximumWeeklyOverrun", audit.maximumWeeklyOverrun)
        .put("fullyMaterializedWeekCount", audit.fullyMaterializedWeekCount)
        .put("partiallyMaterializedWeekCount", audit.partiallyMaterializedWeekCount)
        .put("missingWeekCount", audit.missingWeekCount)
        .put("totalAuthorizedUnits", audit.totalAuthorizedUnits)
        .put("totalMaterializedUnits", audit.totalMaterializedUnits)
         .put("totalCompatibleUnits", audit.totalCompatibleUnits)
         .put("totalShortfallUnits", audit.totalShortfallUnits)
         .put("executionAuthority", audit.executionAuthority.name)
        .put("weeklyAudits", JSONArray(audit.weeklyAudits.map { week -> JSONObject()
            .put("weekNumber", week.weekNumber)
            .put("authorizedSetUnits", week.authorizedSetUnits)
            .put("materializedSetUnits", week.materializedSetUnits)
            .put("targetCompatibleMaterializedUnits", week.targetCompatibleMaterializedUnits)
            .put("shortfall", week.shortfall)
            .put("overrun", week.overrun)
            .put("prescriptionPreservedOrSubset", week.prescriptionPreservedOrSubset)
            .put("reasonCodes", JSONArray(week.reasonCodes))
        }))
        .put("state", audit.state.name).put("reasonCodes", JSONArray(audit.reasonCodes))
    }))
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
