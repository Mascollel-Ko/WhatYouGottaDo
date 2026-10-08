package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
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

    data class Movement(val target: StimulusMovementTarget) : StimulusSelectionTarget {
        override val targetId: String = target.targetId
        override val strategy: StimulusDoseStrategy = StimulusDoseStrategy.ADDRESS_MOVEMENT_COVERAGE_DIRECTION_ONLY
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
    val historyDirectCapabilityIdentities: List<String>,
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

enum class StimulusCandidateDispositionStatus {
    SELECTED,
    REUSED_FOR_TARGET,
    INELIGIBLE,
    ELIGIBLE_NOT_SELECTED,
    MATERIALIZATION_FAILED,
    SELECTION_NOT_REQUIRED,
    TARGET_ALREADY_COVERED,
    NOT_RELEVANT_TO_TARGET,
    UNPROVEN
}

enum class StimulusCandidateDispositionReason {
    NO_DIRECT_CAPABILITY,
    ASSESSMENT_ONLY,
    TASK_ACTIVITY_NOT_SELECTABLE,
    PLANNING_NOT_SELECTABLE,
    EXPLICIT_PROFILE_RESTRICTION,
    USER_EXCLUDED,
    TISSUE_RESTRICTED,
    EQUIPMENT_UNAVAILABLE,
    FREE_WEIGHT_POLICY,
    GENERIC_COURT_ACTIVITY,
    NO_MINIMUM_TARGET,
    REDUCTION_DOES_NOT_AUTHORIZE_SELECTION,
    DISTRIBUTION_ONLY,
    TARGET_UNRESOLVED,
    STRATEGY_DOES_NOT_AUTHORIZE_SELECTION,
    LOWER_RANK_THAN_SELECTED_CANDIDATE,
    REDUNDANT_WITH_SELECTED_OWNER,
    DETERMINISTIC_STABLE_KEY_TIE_BREAK,
    TARGET_ALREADY_COVERED_BY_SELECTED_OWNER,
    NO_SAFE_PRESCRIPTION_AUTHORITY,
    STRENGTH_CAPABILITY_NOT_APPROVED,
    MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME
}

enum class StimulusCandidateRankingField {
    TARGET_COMPATIBLE_HISTORY,
    RECENT_HISTORY,
    CONTEXT_HISTORY,
    ANCHOR_CONTINUITY,
    REPEATED_RECENT_SESSIONS,
    FREE_WEIGHT_COMPATIBLE,
    HIGH_CONFIDENCE,
    REDUNDANT,
    STABLE_KEY
}

/** The exact lexicographic B5 tuple; values are observations, not a new score. */
data class StimulusCandidateRankingTuple(
    val targetCompatibleHistory: Boolean,
    val recentHistory: Boolean,
    val contextHistory: Boolean,
    val anchorContinuity: Boolean,
    val repeatedRecentSessions: Int,
    val freeWeightCompatible: Boolean,
    val highConfidence: Boolean,
    val redundant: Boolean,
    val stableKey: String
)

/** Transient B5-only disposition for one canonical target and catalog identity. */
data class StimulusCandidateDisposition(
    val targetId: String,
    val stableKey: String,
    val canonicalSelectionRole: String,
    val directTargetCandidate: Boolean,
    val selectionRequired: Boolean,
    val status: StimulusCandidateDispositionStatus,
    val reasons: List<StimulusCandidateDispositionReason>,
    val candidateRanking: StimulusCandidateRankingTuple? = null,
    val selectedInstead: StimulusPrescriptionOwnerIdentity? = null,
    val selectedInsteadRanking: StimulusCandidateRankingTuple? = null,
    val firstDifferingField: StimulusCandidateRankingField? = null,
    val targetCoveredBySelectedOwner: Boolean = false
)

data class StimulusCandidateDispositionIndex(val entries: List<StimulusCandidateDisposition> = emptyList()) {
    fun forStableKey(stableKey: String): List<StimulusCandidateDisposition> =
        entries.filter { it.stableKey == stableKey }
}

enum class StimulusNonSelectionClassification {
    CANONICAL_REPLACEMENT,
    OUTRANKED_FOR_RELEVANT_TARGET,
    TARGET_ALREADY_COVERED,
    INELIGIBLE_FOR_CURRENT_TARGET,
    NO_CURRENT_B4_SELECTION_DEMAND,
    MATERIALIZATION_FAILED,
    UNPROVEN
}

data class StimulusTargetNonSelectionProvenance(
    val targetId: String,
    val classification: StimulusNonSelectionClassification,
    val disposition: StimulusCandidateDisposition
)

/** CONTROL role is attached only after B5 completes, at the late comparison boundary. */
data class StimulusNonSelectionProvenance(
    val omittedControlOwner: StimulusPrescriptionOwnerIdentity,
    val classification: StimulusNonSelectionClassification,
    val targetEvidence: List<StimulusTargetNonSelectionProvenance>
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
    val schedulingAuthority: Boolean = false,
    /** In-memory diagnostic only; excluded from compact persistence/backup serialization. */
    val candidateDispositionIndex: StimulusCandidateDispositionIndex = StimulusCandidateDispositionIndex(),
    /** A typed B5 shortfall; it records unmet Strength demand without granting dose authority. */
    val strengthShortfalls: List<StimulusStrengthShortfall> = emptyList()
)

enum class StimulusStrengthShortfallReason {
    NO_ELIGIBLE_STRENGTH_EXERCISE,
    NO_EXECUTABLE_STRENGTH_PRESCRIPTION
}

data class StimulusStrengthShortfall(
    val targetId: String,
    val reason: StimulusStrengthShortfallReason
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
    val productionCutoverAuthority: StimulusProductionCutoverAuthorityDecision? = null,
    /** Late comparison diagnostics; CONTROL never enters the B5 disposition index. */
    val nonSelectionProvenance: List<StimulusNonSelectionProvenance> = emptyList()
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
 * Deterministic B5 identity selector. It reads B4 targets, canonical relations and actual-history
 * continuity, but never recalculates Need, baseline, strategy, dose, or a target-compatible
 * prescription. CONTROL identities are outside this selector's input boundary.
 */
class StimulusTargetCandidateSelector(
    private val prescriptionPlanner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()
) {
    fun build(
        targetPlan: StimulusTargetPlan,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog
    ): StimulusCandidateSelectionPlan {
        val contextStart = snapshot.cutoff.minusDays(55)
        val currentStart = snapshot.cutoff.minusDays(27)
        val contextHistory = snapshot.allConfirmedSets.filter { !it.date.isBefore(contextStart) }
        val historyByStableKey = contextHistory.groupBy(PlanningSetRecord::stableKey)
        val historyIndex = HistoryIndex(
            historyByStableKey = historyByStableKey,
            recentHistoryStableKeys = historyByStableKey.filterValues { rows -> rows.any { !it.date.isBefore(currentStart) } }.keys,
            contextHistoryStableKeys = historyByStableKey.keys,
            strengthCompatibleHistoryKeys = historyByStableKey.filterValues { rows -> rows.any { historyCompatible(snapshot, TrainableQuality.STRENGTH, it) } }.keys,
            hypertrophyCompatibleHistoryKeys = historyByStableKey.filterValues { rows -> rows.any { historyCompatible(snapshot, TrainableQuality.HYPERTROPHY, it) } }.keys,
            anchorStableKeys = state.anchors.mapTo(linkedSetOf(), UserAnchor::stableKey),
            recentSessionCountByStableKey = historyByStableKey.mapValues { (_, rows) ->
                rows.filter { !it.date.isBefore(currentStart) }.map(PlanningSetRecord::date).distinct().size
            }
        )
        val selected = linkedMapOf<String, StimulusSelectedCandidate>()
        // A movement-only B5 observation is not an executable selected owner and must not
        // suppress later Quality/Task candidate selection during this same pass.
        val movementSelected = linkedMapOf<String, StimulusSelectedCandidate>()
        val candidateItems = linkedMapOf<String, PlannedExercise>()
        val deferred = linkedMapOf<String, String>()
        val audit = linkedMapOf<String, String>()
        val traces = mutableListOf<StimulusCandidateSelectionTrace>()
        val dispositionContexts = mutableListOf<TargetDispositionContext>()
        val targets = (targetPlan.qualityTargets.map(StimulusSelectionTarget::Quality) +
            targetPlan.taskTargets.map(StimulusSelectionTarget::Task) +
            targetPlan.movementTargets.map(StimulusSelectionTarget::Movement)).sortedWith(
            compareBy<StimulusSelectionTarget> { priorityRank(it.priority) }.thenBy { it.targetId }
        )

        targets.forEach { intent ->
            val historyIdentities = historyDirectCapabilityIdentities(intent, historyIndex.contextHistoryStableKeys, snapshot, physicalQualityCatalog)

            if (!selectionAllowed(intent)) {
                val reason = noSelectionReason(intent)
                deferred[intent.targetId] = reason
                traces += trace(intent, historyIdentities, false, emptyList(), null, null, emptyMap(), listOf(reason))
                dispositionContexts += TargetDispositionContext(intent, selectionRequired = false)
                return@forEach
            }

            val reusable = selected.values.firstOrNull { selectedCandidate ->
                directlyCovers(intent, selectedCandidate.stableKey, snapshot, physicalQualityCatalog)
            }
            if (reusable != null) {
                val merged = reusable.copy(coveredTargetIds = reusable.coveredTargetIds + intent.targetId)
                selected[reusable.stableKey] = merged
                traces += trace(
                    intent, historyIdentities, false, emptyList(), null, reusable.stableKey,
                    emptyMap(), listOf("TARGET_COVERED_BY_ALREADY_SELECTED_IDENTITY", realizedGapCode(intent)),
                    reusedRole = reusable.selectionRole
                )
                dispositionContexts += TargetDispositionContext(
                    intent = intent,
                    selectionRequired = false,
                    selectedInstead = StimulusPrescriptionOwnerIdentity(reusable.stableKey, reusable.selectionRole),
                    targetCoveredBySelectedOwner = true
                )
                return@forEach
            }

            val ranked = eligibleCandidates(intent, selected.keys, snapshot, state, request, physicalQualityCatalog, historyIndex)
            val pool = ranked.map { it.key }
            if (intent is StimulusSelectionTarget.Movement) {
                val chosen = ranked.firstOrNull()
                if (chosen == null) {
                    val reason = "MOVEMENT_TARGET_HAS_NO_ELIGIBLE_B5_OWNER"
                    deferred[intent.targetId] = reason
                    traces += trace(intent, historyIdentities, true, pool, null, null, emptyMap(), listOf(reason))
                    dispositionContexts += TargetDispositionContext(
                        intent = intent,
                        selectionRequired = true,
                        rankedCandidates = ranked
                    )
                    return@forEach
                }
                val role = roleFor(intent)
                movementSelected[intent.targetId] = StimulusSelectedCandidate(
                    stableKey = chosen.key,
                    coveredTargetIds = setOf(intent.targetId),
                    primaryTargetId = intent.targetId,
                    selectionReasons = listOf("B4_MOVEMENT_TARGET_REQUESTED_IDENTITY", "B5_DOES_NOT_GRANT_DOSE_AUTHORITY"),
                    currentPrescriptionCompatibility = "MOVEMENT_OWNER_REQUIRES_EXISTING_B6_AUTHORITY",
                    targetSetsFromExistingPrescription = 0,
                    selectionRole = role,
                    probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
                )
                audit[chosen.key] = "B5_SELECTED_MOVEMENT_IDENTITY_NO_DOSE_AUTHORITY"
                traces += trace(
                    intent, historyIdentities, true, pool, chosen.key, null, emptyMap(),
                    listOf("MOVEMENT_TARGET_OWNER_SELECTED", "B5_SELECTION_IS_NOT_PRESCRIPTION_AUTHORITY"),
                    selectedRole = role
                )
                dispositionContexts += TargetDispositionContext(
                    intent = intent,
                    selectionRequired = true,
                    rankedCandidates = ranked,
                    selectedInstead = StimulusPrescriptionOwnerIdentity(chosen.key, role)
                )
                return@forEach
            }
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
                traces += trace(intent, historyIdentities, true, pool, null, null, rejections, listOf(reason), candidateRoles = rejectionRoles)
                dispositionContexts += TargetDispositionContext(
                    intent = intent,
                    selectionRequired = true,
                    rankedCandidates = ranked,
                    materializationFailures = rejections
                )
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
                intent, historyIdentities, true, pool, item.stableKey, null, rejections,
                listOf("SELECTION_IDENTITY_PRESENT", chosen.compatibility.name, "B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY"),
                selectedRole = item.role,
                candidateRoles = rejections.keys.associateWith { roleFor(intent) }
            )
            dispositionContexts += TargetDispositionContext(
                intent = intent,
                selectionRequired = true,
                rankedCandidates = ranked,
                selectedInstead = StimulusPrescriptionOwnerIdentity(item.stableKey, item.role),
                materializationFailures = rejections
            )
        }

        val unresolvedDemand = deferred.mapValues { it.value }
        val materialDemand = MaterialDemand(candidateItems.values.toList(), unresolvedDemand, audit)
        val movementCandidatesByStableKey = movementSelected.values.groupBy(StimulusSelectedCandidate::stableKey)
        val executableSelected = selected.values.map { selectedCandidate ->
            val movementTargetIds = movementCandidatesByStableKey[selectedCandidate.stableKey].orEmpty()
                .flatMapTo(linkedSetOf(), StimulusSelectedCandidate::coveredTargetIds)
            selectedCandidate.copy(coveredTargetIds = selectedCandidate.coveredTargetIds + movementTargetIds)
        }
        val standaloneMovementCandidates = movementSelected.values.filterNot { movementCandidate ->
            movementCandidate.stableKey in selected
        }
        val allSelected = (executableSelected + standaloneMovementCandidates)
            .sortedWith(compareBy({ it.primaryTargetId }, { it.stableKey }, { it.selectionRole }))
        val finalMovementTraces = traces.map { trace ->
            if (!trace.targetId.startsWith("MOVEMENT:") || trace.selectedStableKey == null) return@map trace
            val movementOwner = movementSelected[trace.targetId]
            if (movementOwner?.stableKey != trace.selectedStableKey) return@map trace
            val finalOwner = selected[trace.selectedStableKey]
            if (finalOwner == null) return@map trace
            trace.copy(
                selectedSelectionRole = finalOwner.selectionRole,
                reasonCodes = (trace.reasonCodes + "MOVEMENT_COVERED_BY_CANONICAL_B5_OWNER").distinct()
            )
        }
        val finalDispositionContexts = dispositionContexts.map { context ->
            if (context.intent !is StimulusSelectionTarget.Movement) return@map context
            val movementOwner = movementSelected[context.intent.targetId] ?: return@map context
            val finalOwner = selected[movementOwner.stableKey] ?: return@map context
            if (finalOwner.primaryTargetId == context.intent.targetId) return@map context
            context.copy(
                selectionRequired = false,
                selectedInstead = StimulusPrescriptionOwnerIdentity(finalOwner.stableKey, finalOwner.selectionRole),
                targetCoveredBySelectedOwner = true
            )
        }
        val dispositionIndex = buildDispositionIndex(
            finalDispositionContexts, snapshot, state, request, physicalQualityCatalog, historyIndex
        )
        val strengthShortfalls = finalDispositionContexts.mapNotNull { context ->
            val quality = (context.intent as? StimulusSelectionTarget.Quality)?.target ?: return@mapNotNull null
            if (quality.quality != TrainableQuality.STRENGTH || !quality.requiresActiveStrengthExposure() ||
                !context.selectionRequired || context.selectedInstead != null) {
                return@mapNotNull null
            }
            StimulusStrengthShortfall(
                targetId = context.intent.targetId,
                reason = if (context.rankedCandidates.isEmpty()) {
                    StimulusStrengthShortfallReason.NO_ELIGIBLE_STRENGTH_EXERCISE
                } else {
                    StimulusStrengthShortfallReason.NO_EXECUTABLE_STRENGTH_PRESCRIPTION
                }
            )
        }
        return StimulusCandidateSelectionPlan(
            allSelected, finalMovementTraces, materialDemand,
            candidateDispositionIndex = dispositionIndex,
            strengthShortfalls = strengthShortfalls
        )
    }

    private fun StimulusQualityTarget.requiresActiveStrengthExposure(): Boolean = strategy !in setOf(
        StimulusDoseStrategy.NO_MINIMUM_TARGET,
        StimulusDoseStrategy.UNRESOLVED,
        StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE
    )

    private fun trace(
        intent: StimulusSelectionTarget,
        historyIdentities: List<String>,
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
        historyDirectCapabilityIdentities = historyIdentities,
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

    /** UPPER_PULL is the existing canonical aggregate over horizontal and vertical pull. */
    private fun MovementCoverage.directlyRepresents(target: MovementCoverage): Boolean = when (target) {
        MovementCoverage.UPPER_PULL -> this in setOf(
            MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL, MovementCoverage.UPPER_PULL
        )
        else -> this == target
    }

    private data class CandidateKey(
        val key: String,
        val targetCompatibleHistory: Boolean,
        val recentHistory: Boolean,
        val contextHistory: Boolean,
        val anchorContinuity: Boolean,
        val repeatedRecentSessions: Int,
        val freeWeightCompatible: Boolean,
        val highConfidence: Boolean,
        val redundant: Boolean
    )

    private data class HistoryIndex(
        val historyByStableKey: Map<String, List<PlanningSetRecord>>,
        val recentHistoryStableKeys: Set<String>,
        val contextHistoryStableKeys: Set<String>,
        val strengthCompatibleHistoryKeys: Set<String>,
        val hypertrophyCompatibleHistoryKeys: Set<String>,
        val anchorStableKeys: Set<String>,
        val recentSessionCountByStableKey: Map<String, Int>
    )

    private data class TargetDispositionContext(
        val intent: StimulusSelectionTarget,
        val selectionRequired: Boolean,
        val rankedCandidates: List<CandidateKey> = emptyList(),
        val selectedInstead: StimulusPrescriptionOwnerIdentity? = null,
        val materializationFailures: Map<String, String> = emptyMap(),
        val targetCoveredBySelectedOwner: Boolean = false
    )

    private fun eligibleCandidates(
        intent: StimulusSelectionTarget,
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
                } && (intent.target.quality != TrainableQuality.STRENGTH ||
                    (snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE &&
                        CanonicalStrengthExposureCapability.strengthPossible(key)))
                is StimulusSelectionTarget.Task -> snapshot.badmintonDirectObjectives[key].orEmpty().contains(intent.target.task) &&
                    snapshot.activityKind(key) in TASK_ACTIVITY_KINDS
                is StimulusSelectionTarget.Movement -> snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE &&
                    snapshot.movementCoverage(key).directlyRepresents(intent.target.movementCoverage)
            }
        }.filter { key -> snapshot.metadata[key]?.planningEligibility in SELECTABLE_ELIGIBILITY }
            .filterNot(snapshot::explicitlyRestricted)
            .filter { key -> key !in request.excludedExerciseStableKeys }
            .filter { key -> key !in snapshot.recoverySignals.tissueRestrictedStableKeys }
            .filter { key -> equipmentCompatible(snapshot, key, request) }
            .filter { key -> freeWeightAllowed(snapshot, state, key, historyIndex.contextHistoryStableKeys) }
            .filterNot { snapshot.activityKind(it) == PlannedActivityKind.GENERIC_COURT_SESSION }
            .map { key ->
                val targetCompatible = when (intent) {
                    is StimulusSelectionTarget.Quality -> when (intent.target.quality) {
                        TrainableQuality.STRENGTH -> key in historyIndex.strengthCompatibleHistoryKeys
                        TrainableQuality.HYPERTROPHY -> key in historyIndex.hypertrophyCompatibleHistoryKeys
                        else -> false
                    }
                    is StimulusSelectionTarget.Task -> false
                    is StimulusSelectionTarget.Movement -> false
                }
                CandidateKey(
                    key = key,
                    targetCompatibleHistory = targetCompatible,
                    recentHistory = key in historyIndex.recentHistoryStableKeys,
                    contextHistory = key in historyIndex.contextHistoryStableKeys,
                    anchorContinuity = key in historyIndex.anchorStableKeys && key in historyIndex.contextHistoryStableKeys,
                    repeatedRecentSessions = historyIndex.recentSessionCountByStableKey[key] ?: 0,
                    freeWeightCompatible = state.freeWeightWillingness != FreeWeightWillingness.PREFER_FAMILIAR || !snapshot.isFreeWeight(key),
                    highConfidence = snapshot.metadata[key]?.sourceConfidenceLevel == "HIGH",
                    redundant = redundancyGroup(snapshot, key).isNotBlank() &&
                        selectedKeys.any { existingKey -> redundancyGroup(snapshot, existingKey) == redundancyGroup(snapshot, key) }
                )
            }.toList()
        return keys.sortedWith(
            compareByDescending<CandidateKey> { it.targetCompatibleHistory }
                .thenByDescending { it.recentHistory }
                .thenByDescending { it.contextHistory }
                .thenByDescending { it.anchorContinuity }
                .thenByDescending { it.repeatedRecentSessions }
                .thenByDescending { it.freeWeightCompatible }
                .thenByDescending { it.highConfidence }
                .thenBy { it.redundant }
                .thenBy { it.key }
        )
    }

    private fun buildDispositionIndex(
        contexts: List<TargetDispositionContext>,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog,
        historyIndex: HistoryIndex
    ): StimulusCandidateDispositionIndex {
        val entries = contexts.flatMap { context ->
            val intent = context.intent
            val ranked = context.rankedCandidates.associateBy(CandidateKey::key)
            val selectedRanking = context.selectedInstead?.stableKey?.let(ranked::get)?.toRankingTuple()
            snapshot.exercises.keys.sorted().map { key ->
                val rawDirectTarget = when (intent) {
                    is StimulusSelectionTarget.Quality -> physicalQualityCatalog.relations(key).any {
                        it.qualityId == intent.target.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
                    }
                    is StimulusSelectionTarget.Task -> intent.target.task in snapshot.badmintonDirectObjectives[key].orEmpty()
                    is StimulusSelectionTarget.Movement -> snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE &&
                        snapshot.movementCoverage(key).directlyRepresents(intent.target.movementCoverage)
                }
                if (!rawDirectTarget) {
                    return@map StimulusCandidateDisposition(
                        targetId = intent.targetId,
                        stableKey = key,
                        canonicalSelectionRole = roleFor(intent),
                        directTargetCandidate = false,
                        selectionRequired = context.selectionRequired,
                        status = StimulusCandidateDispositionStatus.NOT_RELEVANT_TO_TARGET,
                        reasons = listOf(StimulusCandidateDispositionReason.NO_DIRECT_CAPABILITY),
                        targetCoveredBySelectedOwner = context.targetCoveredBySelectedOwner
                    )
                }
                if (!context.selectionRequired && context.targetCoveredBySelectedOwner) {
                    val reused = context.selectedInstead
                    return@map StimulusCandidateDisposition(
                        targetId = intent.targetId,
                        stableKey = key,
                        canonicalSelectionRole = roleFor(intent),
                        directTargetCandidate = true,
                        selectionRequired = false,
                        status = if (reused?.stableKey == key) StimulusCandidateDispositionStatus.REUSED_FOR_TARGET
                            else StimulusCandidateDispositionStatus.TARGET_ALREADY_COVERED,
                        reasons = listOf(StimulusCandidateDispositionReason.TARGET_ALREADY_COVERED_BY_SELECTED_OWNER),
                        selectedInstead = reused,
                        targetCoveredBySelectedOwner = true
                    )
                }
                if (!context.selectionRequired) {
                    return@map StimulusCandidateDisposition(
                        targetId = intent.targetId,
                        stableKey = key,
                        canonicalSelectionRole = roleFor(intent),
                        directTargetCandidate = true,
                        selectionRequired = false,
                        status = StimulusCandidateDispositionStatus.SELECTION_NOT_REQUIRED,
                        reasons = listOf(noSelectionReason(intent).toDispositionReason()),
                        targetCoveredBySelectedOwner = false
                    )
                }

                val gateReasons = eligibilityReasons(intent, key, snapshot, state, request, physicalQualityCatalog,
                    historyIndex.contextHistoryStableKeys)
                if (gateReasons.isNotEmpty()) {
                    return@map StimulusCandidateDisposition(
                        targetId = intent.targetId,
                        stableKey = key,
                        canonicalSelectionRole = roleFor(intent),
                        directTargetCandidate = true,
                        selectionRequired = true,
                        status = StimulusCandidateDispositionStatus.INELIGIBLE,
                        reasons = gateReasons
                    )
                }

                val candidate = ranked[key]
                val selectedInstead = context.selectedInstead
                when {
                    selectedInstead?.stableKey == key && selectedInstead.selectionRole == roleFor(intent) ->
                        StimulusCandidateDisposition(
                            targetId = intent.targetId,
                            stableKey = key,
                            canonicalSelectionRole = roleFor(intent),
                            directTargetCandidate = true,
                            selectionRequired = true,
                            status = StimulusCandidateDispositionStatus.SELECTED,
                            reasons = emptyList(),
                            candidateRanking = candidate?.toRankingTuple()
                        )
                    key in context.materializationFailures -> {
                        val failure = context.materializationFailures.getValue(key).toDispositionReason()
                        StimulusCandidateDisposition(
                            targetId = intent.targetId,
                            stableKey = key,
                            canonicalSelectionRole = roleFor(intent),
                            directTargetCandidate = true,
                            selectionRequired = true,
                            status = StimulusCandidateDispositionStatus.MATERIALIZATION_FAILED,
                            reasons = listOf(failure),
                            candidateRanking = candidate?.toRankingTuple(),
                            selectedInstead = selectedInstead,
                            selectedInsteadRanking = selectedRanking,
                            firstDifferingField = candidate?.let { selectedRanking?.let { winner -> firstDifferingField(it.toRankingTuple(), winner) } }
                        )
                    }
                    else -> {
                        val candidateTuple = candidate?.toRankingTuple()
                        val field = candidateTuple?.let { tuple -> selectedRanking?.let { winner -> firstDifferingField(tuple, winner) } }
                        val explanation = when (field) {
                            StimulusCandidateRankingField.STABLE_KEY -> StimulusCandidateDispositionReason.DETERMINISTIC_STABLE_KEY_TIE_BREAK
                            StimulusCandidateRankingField.REDUNDANT -> StimulusCandidateDispositionReason.REDUNDANT_WITH_SELECTED_OWNER
                            else -> StimulusCandidateDispositionReason.LOWER_RANK_THAN_SELECTED_CANDIDATE
                        }
                        StimulusCandidateDisposition(
                            targetId = intent.targetId,
                            stableKey = key,
                            canonicalSelectionRole = roleFor(intent),
                            directTargetCandidate = true,
                            selectionRequired = true,
                            status = if (candidate == null) StimulusCandidateDispositionStatus.UNPROVEN
                                else StimulusCandidateDispositionStatus.ELIGIBLE_NOT_SELECTED,
                            reasons = listOf(explanation),
                            candidateRanking = candidateTuple,
                            selectedInstead = selectedInstead,
                            selectedInsteadRanking = selectedRanking,
                            firstDifferingField = field
                        )
                    }
                }
            }
        }.sortedWith(compareBy<StimulusCandidateDisposition> { it.targetId }.thenBy { it.stableKey })
        return StimulusCandidateDispositionIndex(entries)
    }

    /** Mirrors the selector's existing gate sequence and names only gates it actually evaluates. */
    private fun eligibilityReasons(
        intent: StimulusSelectionTarget,
        key: String,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog,
        contextHistoryStableKeys: Set<String>
    ): List<StimulusCandidateDispositionReason> = buildList {
        when (intent) {
            is StimulusSelectionTarget.Quality -> if (physicalQualityCatalog.isAssessmentOnly(key)) {
                add(StimulusCandidateDispositionReason.ASSESSMENT_ONLY)
            } else if (intent.target.quality == TrainableQuality.STRENGTH &&
                !CanonicalStrengthExposureCapability.strengthPossible(key)) {
                add(StimulusCandidateDispositionReason.STRENGTH_CAPABILITY_NOT_APPROVED)
            }
            is StimulusSelectionTarget.Task -> {
                val activity = snapshot.activityKind(key)
                if (activity !in TASK_ACTIVITY_KINDS) {
                    add(StimulusCandidateDispositionReason.TASK_ACTIVITY_NOT_SELECTABLE)
                }
            }
            is StimulusSelectionTarget.Movement -> Unit
        }
        if (snapshot.metadata[key]?.planningEligibility !in SELECTABLE_ELIGIBILITY) {
            add(StimulusCandidateDispositionReason.PLANNING_NOT_SELECTABLE)
        }
        if (snapshot.explicitlyRestricted(key)) add(StimulusCandidateDispositionReason.EXPLICIT_PROFILE_RESTRICTION)
        if (key in request.excludedExerciseStableKeys) add(StimulusCandidateDispositionReason.USER_EXCLUDED)
        if (key in snapshot.recoverySignals.tissueRestrictedStableKeys) add(StimulusCandidateDispositionReason.TISSUE_RESTRICTED)
        if (!equipmentCompatible(snapshot, key, request)) add(StimulusCandidateDispositionReason.EQUIPMENT_UNAVAILABLE)
        if (!freeWeightAllowed(snapshot, state, key, contextHistoryStableKeys)) add(StimulusCandidateDispositionReason.FREE_WEIGHT_POLICY)
        if (intent is StimulusSelectionTarget.Quality && snapshot.activityKind(key) == PlannedActivityKind.GENERIC_COURT_SESSION) {
            add(StimulusCandidateDispositionReason.GENERIC_COURT_ACTIVITY)
        }
    }.distinct()

    private fun CandidateKey.toRankingTuple() = StimulusCandidateRankingTuple(
        targetCompatibleHistory, recentHistory, contextHistory, anchorContinuity,
        repeatedRecentSessions, freeWeightCompatible, highConfidence, redundant, key
    )

    private fun firstDifferingField(
        candidate: StimulusCandidateRankingTuple,
        selected: StimulusCandidateRankingTuple
    ): StimulusCandidateRankingField? = when {
        candidate.targetCompatibleHistory != selected.targetCompatibleHistory -> StimulusCandidateRankingField.TARGET_COMPATIBLE_HISTORY
        candidate.recentHistory != selected.recentHistory -> StimulusCandidateRankingField.RECENT_HISTORY
        candidate.contextHistory != selected.contextHistory -> StimulusCandidateRankingField.CONTEXT_HISTORY
        candidate.anchorContinuity != selected.anchorContinuity -> StimulusCandidateRankingField.ANCHOR_CONTINUITY
        candidate.repeatedRecentSessions != selected.repeatedRecentSessions -> StimulusCandidateRankingField.REPEATED_RECENT_SESSIONS
        candidate.freeWeightCompatible != selected.freeWeightCompatible -> StimulusCandidateRankingField.FREE_WEIGHT_COMPATIBLE
        candidate.highConfidence != selected.highConfidence -> StimulusCandidateRankingField.HIGH_CONFIDENCE
        candidate.redundant != selected.redundant -> StimulusCandidateRankingField.REDUNDANT
        candidate.stableKey != selected.stableKey -> StimulusCandidateRankingField.STABLE_KEY
        else -> null
    }

    private fun String.toDispositionReason(): StimulusCandidateDispositionReason = when (this) {
        "NO_SAFE_PRESCRIPTION_AUTHORITY" -> StimulusCandidateDispositionReason.NO_SAFE_PRESCRIPTION_AUTHORITY
        "MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME" -> StimulusCandidateDispositionReason.MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME
        "NO_MINIMUM_TARGET" -> StimulusCandidateDispositionReason.NO_MINIMUM_TARGET
        "REDUCTION_DOES_NOT_AUTHORIZE_NEW_EXERCISE" -> StimulusCandidateDispositionReason.REDUCTION_DOES_NOT_AUTHORIZE_SELECTION
        "DISTRIBUTION_AUTHORITY_DEFERRED" -> StimulusCandidateDispositionReason.DISTRIBUTION_ONLY
        "TARGET_UNRESOLVED" -> StimulusCandidateDispositionReason.TARGET_UNRESOLVED
        "TARGET_SELECTION_NOT_AUTHORIZED_BY_B5_STRATEGY" -> StimulusCandidateDispositionReason.STRATEGY_DOES_NOT_AUTHORIZE_SELECTION
        else -> StimulusCandidateDispositionReason.STRATEGY_DOES_NOT_AUTHORIZE_SELECTION
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
                        prescription.sets.all { provisionalRealizedStimulusClass(key, it.reps) == RealizedStimulusClass.STRENGTH_LIKE }) ||
                    (intent.target.quality == TrainableQuality.HYPERTROPHY &&
                        prescription.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE })
                )
                    SelectionProbePrescriptionCompatibility.REALIZED_COMPATIBLE else SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
                StimulusEvidenceBasis.CANONICAL_CAPABILITY_PROXY,
                StimulusEvidenceBasis.UNCLASSIFIED -> SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
                StimulusEvidenceBasis.CANONICAL_TASK_RELATION -> SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
            }
            is StimulusSelectionTarget.Task -> SelectionProbePrescriptionCompatibility.DIRECTIONAL_TASK_IDENTITY_ONLY
            is StimulusSelectionTarget.Movement -> SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
        }
        return MaterializedCandidateResult.Success(MaterializedCandidate(item, prescription, compatibility, emptyMap()))
    }

    private sealed interface MaterializedCandidateResult {
        data class Success(val value: MaterializedCandidate) : MaterializedCandidateResult
        data class Failure(val reason: String) : MaterializedCandidateResult
    }

    private fun historyDirectCapabilityIdentities(intent: StimulusSelectionTarget, keys: Set<String>, snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog): List<String> = keys.filter { directlyCovers(intent, it, snapshot, catalog) }.sorted()

    private fun directlyCovers(intent: StimulusSelectionTarget, key: String, snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog): Boolean = when (intent) {
        is StimulusSelectionTarget.Quality -> (intent.target.quality != TrainableQuality.STRENGTH ||
            (snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE &&
                CanonicalStrengthExposureCapability.strengthPossible(key))) && catalog.relations(key).any {
            it.qualityId == intent.target.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
        }
        is StimulusSelectionTarget.Task -> snapshot.activityKind(key) in TASK_ACTIVITY_KINDS &&
            intent.target.task in snapshot.badmintonDirectObjectives[key].orEmpty()
        is StimulusSelectionTarget.Movement -> snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE &&
            snapshot.movementCoverage(key).directlyRepresents(intent.target.movementCoverage)
    }

    private fun selectionAllowed(intent: StimulusSelectionTarget): Boolean = when (intent) {
        is StimulusSelectionTarget.Quality -> intent.strategy in QUALITY_SELECTION_STRATEGIES ||
            (intent.strategy == StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY &&
                intent.target.evidenceBasis == StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED)
        is StimulusSelectionTarget.Task -> intent.strategy in TASK_SELECTION_STRATEGIES
        is StimulusSelectionTarget.Movement -> intent.strategy == StimulusDoseStrategy.ADDRESS_MOVEMENT_COVERAGE_DIRECTION_ONLY
    }

    private fun noSelectionReason(intent: StimulusSelectionTarget): String = when (intent.strategy) {
        StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY -> "DISTRIBUTION_AUTHORITY_DEFERRED"
        StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE -> "REDUCTION_DOES_NOT_AUTHORIZE_NEW_EXERCISE"
        StimulusDoseStrategy.NO_MINIMUM_TARGET -> "NO_MINIMUM_TARGET"
        StimulusDoseStrategy.UNRESOLVED -> "TARGET_UNRESOLVED"
        StimulusDoseStrategy.ADDRESS_MOVEMENT_COVERAGE_DIRECTION_ONLY -> "MOVEMENT_TARGET_NOT_ADMITTED"
        else -> "TARGET_SELECTION_NOT_AUTHORIZED_BY_B5_STRATEGY"
    }

    private fun realizedGapCode(intent: StimulusSelectionTarget): String = when (intent) {
        is StimulusSelectionTarget.Quality -> "REALIZED_STIMULUS_GAP_DEFERRED_TO_B6"
        is StimulusSelectionTarget.Task -> "TASK_REALIZATION_GAP_DEFERRED_TO_B6"
        is StimulusSelectionTarget.Movement -> "MOVEMENT_COVERAGE_DIRECTLY_REPRESENTED"
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
                if (!trace.selectionRequired || trace.reasonCodes.any { it in NO_SELECTION_REASON_CODES }) {
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
        val nonSelectionProvenance = (control.items.map {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }.toSet() - experimental.items.map {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }.toSet()).sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map { omittedOwner ->
            val targetEvidence = selectionPlan.candidateDispositionIndex.forStableKey(omittedOwner.stableKey)
                .map { disposition ->
                    StimulusTargetNonSelectionProvenance(
                        targetId = disposition.targetId,
                        classification = disposition.toNonSelectionClassification(),
                        disposition = disposition
                    )
                }
            val ownerClassification = when {
                targetEvidence.any { it.classification == StimulusNonSelectionClassification.CANONICAL_REPLACEMENT } ->
                    StimulusNonSelectionClassification.CANONICAL_REPLACEMENT
                targetEvidence.any { it.classification == StimulusNonSelectionClassification.OUTRANKED_FOR_RELEVANT_TARGET } ->
                    StimulusNonSelectionClassification.OUTRANKED_FOR_RELEVANT_TARGET
                targetEvidence.any { it.classification == StimulusNonSelectionClassification.TARGET_ALREADY_COVERED } ->
                    StimulusNonSelectionClassification.TARGET_ALREADY_COVERED
                targetEvidence.any { it.classification == StimulusNonSelectionClassification.MATERIALIZATION_FAILED } ->
                    StimulusNonSelectionClassification.MATERIALIZATION_FAILED
                targetEvidence.any { it.classification == StimulusNonSelectionClassification.INELIGIBLE_FOR_CURRENT_TARGET } ->
                    StimulusNonSelectionClassification.INELIGIBLE_FOR_CURRENT_TARGET
                targetEvidence.isEmpty() && selectionPlan.traces.isEmpty() ||
                    targetEvidence.isNotEmpty() && targetEvidence.all { it.classification == StimulusNonSelectionClassification.NO_CURRENT_B4_SELECTION_DEMAND } ->
                    StimulusNonSelectionClassification.NO_CURRENT_B4_SELECTION_DEMAND
                else -> StimulusNonSelectionClassification.UNPROVEN
            }
            StimulusNonSelectionProvenance(omittedOwner, ownerClassification, targetEvidence)
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
            materializationTraces = materializationTraces,
            nonSelectionProvenance = nonSelectionProvenance
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

private fun StimulusCandidateDisposition.toNonSelectionClassification(): StimulusNonSelectionClassification = when {
    targetCoveredBySelectedOwner -> StimulusNonSelectionClassification.TARGET_ALREADY_COVERED
    else -> when (status) {
        StimulusCandidateDispositionStatus.SELECTED,
        StimulusCandidateDispositionStatus.REUSED_FOR_TARGET -> StimulusNonSelectionClassification.CANONICAL_REPLACEMENT
        StimulusCandidateDispositionStatus.INELIGIBLE -> StimulusNonSelectionClassification.INELIGIBLE_FOR_CURRENT_TARGET
        StimulusCandidateDispositionStatus.ELIGIBLE_NOT_SELECTED -> if (selectedInstead != null && firstDifferingField != null)
            StimulusNonSelectionClassification.OUTRANKED_FOR_RELEVANT_TARGET
        else StimulusNonSelectionClassification.UNPROVEN
        StimulusCandidateDispositionStatus.MATERIALIZATION_FAILED -> StimulusNonSelectionClassification.MATERIALIZATION_FAILED
        StimulusCandidateDispositionStatus.SELECTION_NOT_REQUIRED -> StimulusNonSelectionClassification.NO_CURRENT_B4_SELECTION_DEMAND
        StimulusCandidateDispositionStatus.TARGET_ALREADY_COVERED -> StimulusNonSelectionClassification.TARGET_ALREADY_COVERED
        StimulusCandidateDispositionStatus.NOT_RELEVANT_TO_TARGET -> if (selectionRequired)
            StimulusNonSelectionClassification.INELIGIBLE_FOR_CURRENT_TARGET
        else StimulusNonSelectionClassification.NO_CURRENT_B4_SELECTION_DEMAND
        StimulusCandidateDispositionStatus.UNPROVEN -> StimulusNonSelectionClassification.UNPROVEN
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
        .put("historyDirectCapabilityIdentities", JSONArray(trace.historyDirectCapabilityIdentities))
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
            .put("ownerExecutionDispositions", JSONArray(plan.ownerExecutionDispositions.entries.sortedWith(compareBy({ it.key.stableKey }, { it.key.selectionRole })).map { (owner, disposition) -> JSONObject()
                .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole).put("disposition", disposition.name)
            }))
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
            .put("movementAuthorizations", JSONArray(plan.movementAuthorizations.map { authorization -> JSONObject()
                .put("targetId", authorization.targetId)
                .put("ownerStableKey", authorization.owner?.stableKey)
                .put("ownerSelectionRole", authorization.owner?.selectionRole)
                .put("status", authorization.status.name)
                .put("existingAuthorityTargetId", authorization.existingAuthorityTargetId)
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
