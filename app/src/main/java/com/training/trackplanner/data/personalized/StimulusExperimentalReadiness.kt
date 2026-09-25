package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** B7 is an observation-only cutover gate; it never grants production authority. */
enum class StimulusExperimentalReadinessStatus {
    ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
    NOT_ELIGIBLE,
    INCONCLUSIVE,
    NO_MATERIAL_CHANGE
}

enum class StimulusExperimentalTargetOutcomeStatus {
    IMPROVED,
    UNCHANGED,
    REGRESSED,
    INCONCLUSIVE,
    NOT_APPLICABLE,
    NO_AUTHORITY
}

enum class StimulusExperimentalChangeAttributionSource {
    B5_SELECTED_IDENTITY,
    B5_REUSED_IDENTITY,
    B6_EXISTING_OWNER_PRESCRIPTION,
    B6_SAFE_REPAIRED_PRESCRIPTION,
    DOWNSTREAM_CONSTRAINT_DISPLACEMENT,
    INCONCLUSIVE_DISPLACEMENT,
    UNEXPLAINED
}

data class StimulusExperimentalTargetOutcome(
    val targetId: String,
    val status: StimulusExperimentalTargetOutcomeStatus,
    val directlyAffected: Boolean,
    val controlWeeklyUnitsDistance: Double? = null,
    val experimentalWeeklyUnitsDistance: Double? = null,
    val controlWeeklySessionsDistance: Double? = null,
    val experimentalWeeklySessionsDistance: Double? = null,
    val reasonCodes: List<String> = emptyList()
)

data class StimulusExperimentalChangeAttribution(
    val stableKey: String?,
    val selectionRole: String?,
    val source: StimulusExperimentalChangeAttributionSource,
    val targetIds: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
    val evidenceSources: List<String> = emptyList()
)

/**
 * Owner-local causal evidence for one removed CONTROL identity. Global B5/B6 change evidence
 * is intentionally separate from the capacity, placement and disappearance fields below.
 */
data class StimulusRemovalCausalEvidence(
    val stableKey: String,
    val selectionRole: String?,
    val governedExperimentalChangeExists: Boolean,
    val removedOwnerHasCapacityOrPlacementEvidence: Boolean,
    val removedOwnerHasDisappearanceEvidence: Boolean,
    val removedOwnerWasDirectB5Action: Boolean,
    val contradictoryProvenance: Boolean,
    val evidenceSources: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList()
)

data class StimulusExperimentalReadinessAudit(
    val status: StimulusExperimentalReadinessStatus,
    val targetOutcomes: List<StimulusExperimentalTargetOutcome> = emptyList(),
    val changeAttributions: List<StimulusExperimentalChangeAttribution> = emptyList(),
    val materializationIntegrityPassed: Boolean = true,
    val changeProvenanceClosed: Boolean = true,
    val collateralRegressionFree: Boolean = true,
    val reasonCodes: List<String> = emptyList(),
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false
) {
    val integrityPassed: Boolean get() = materializationIntegrityPassed
    val winner: String? get() = null
}

private val B6_INTEGRITY_REASON_CODES = setOf(
    "B6_AUTHORIZED_SET_REUSED",
    "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED",
    "B6_UNAUTHORIZED_SET_CONTENT",
    "B6_UNAUTHORIZED_SET_IDENTITY",
    "B6_PRESCRIPTION_AUTHORITY_MISMATCH",
    "B6_PRESCRIPTION_NOT_PRESERVED",
    "B6_PRESCRIPTION_MUTATION"
)

/**
 * Adjudicates the already-built B6.2 comparison. This class deliberately accepts no builder,
 * DAO, snapshot or request and therefore cannot trigger a third build or rerun an earlier phase.
 */
class StimulusExperimentalReadinessAuditEngine {
    fun audit(comparison: StimulusSelectionProgramComparison): StimulusExperimentalReadinessAudit {
        if (comparison.winner != null) {
            return failed(comparison, "B5_WINNER_MUST_REMAIN_NULL")
        }
        if (noMaterialChange(comparison)) {
            return StimulusExperimentalReadinessAudit(
                status = StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE,
                materializationIntegrityPassed = true,
                changeProvenanceClosed = true,
                collateralRegressionFree = true,
                reasonCodes = listOf("NO_MATERIAL_CHANGE"),
                shadowOnly = true,
                productionAuthority = false
            )
        }

        val integrityReasons = linkedSetOf<String>()
        comparison.prescriptionMaterializationAudits.forEach { audit ->
            if (audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE) {
                integrityReasons += "B6_MATERIALIZATION_INVARIANT_FAILURE"
            }
            integrityReasons += audit.reasonCodes.filter { it in B6_INTEGRITY_REASON_CODES }
            integrityReasons += audit.weeklyAudits.flatMap { it.reasonCodes }.filter { it in B6_INTEGRITY_REASON_CODES }
            if (audit.overrun > 0 || audit.maximumWeeklyOverrun > 0) integrityReasons += "B6_AUTHORIZATION_OVERRUN"
            if (!audit.prescriptionPreservedOrSubset) integrityReasons += "B6_PRESCRIPTION_MUTATION"
            if (audit.weeklyAudits.any { !it.prescriptionPreservedOrSubset }) integrityReasons += "B6_PRESCRIPTION_MUTATION"
        }
        val experimentalRowsByIdentity = comparison.experimental.items.groupBy {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }
        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().forEach { authorization ->
            val owner = authorization.owner ?: return@forEach
            val authorized = authorization.authorizedPrescription ?: return@forEach
            val validation = validateAuthorizedWeeklySubset(
                experimentalRowsByIdentity[StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)].orEmpty(),
                authorized, owner.stableKey, owner.selectionRole
            )
            if (!validation.valid) integrityReasons += validation.reasonCodes
        }
        val attributions = attributeChanges(comparison)
        val hasUnexplainedProvenance = attributions.any { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED }
        val hasInconclusiveProvenance = attributions.any { it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT }
        val provenanceClosed = !hasUnexplainedProvenance && !hasInconclusiveProvenance
        val affectedTargets = affectedTargetIds(comparison, attributions)
        val outcomes = targetOutcomes(comparison, affectedTargets)
        val collateralRegressionFree = outcomes.none { !it.directlyAffected && it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }
        val reasons = linkedSetOf<String>().apply {
            addAll(integrityReasons)
            if (!provenanceClosed) add("CHANGE_PROVENANCE_UNCLOSED")
            if (hasInconclusiveProvenance) add("REMOVAL_CAUSALITY_UNPROVEN")
            if (!collateralRegressionFree) add("COLLATERAL_TARGET_REGRESSION")
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY && it.directlyAffected }) {
                add("AFFECTED_TARGET_HAS_NO_AUTHORITY")
            }
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE && it.directlyAffected }) {
                add("AFFECTED_TARGET_EVIDENCE_INCONCLUSIVE")
            }
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }) {
                add("TARGET_REGRESSED")
            }
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && it.directlyAffected && it.reasonCodes.contains("TARGET_UNMET") }) {
                add("AFFECTED_TARGET_REMAINS_UNMET")
            }
        }
        val status = when {
            integrityReasons.isNotEmpty() -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            hasUnexplainedProvenance -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            !collateralRegressionFree -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY && it.directlyAffected } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && it.directlyAffected && it.reasonCodes.contains("TARGET_UNMET") } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            hasInconclusiveProvenance || outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE && it.directlyAffected } -> StimulusExperimentalReadinessStatus.INCONCLUSIVE
            else -> StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
        }
        return StimulusExperimentalReadinessAudit(
            status = status,
            targetOutcomes = outcomes,
            changeAttributions = attributions,
            materializationIntegrityPassed = integrityReasons.isEmpty(),
            changeProvenanceClosed = provenanceClosed,
            collateralRegressionFree = collateralRegressionFree,
            reasonCodes = reasons.toList(),
            shadowOnly = true,
            productionAuthority = false
        )
    }

    private fun noMaterialChange(comparison: StimulusSelectionProgramComparison): Boolean =
        personalizedProgramFingerprint(comparison.control.request, comparison.control.items) ==
            personalizedProgramFingerprint(comparison.experimental.request, comparison.experimental.items) &&
            comparison.control.weekDaySchedule == comparison.experimental.weekDaySchedule

    private fun failed(comparison: StimulusSelectionProgramComparison, reason: String) =
        StimulusExperimentalReadinessAudit(
            status = StimulusExperimentalReadinessStatus.NOT_ELIGIBLE,
            targetOutcomes = targetOutcomes(comparison, emptySet()),
            changeAttributions = listOf(StimulusExperimentalChangeAttribution(null, null, StimulusExperimentalChangeAttributionSource.UNEXPLAINED, reasonCodes = listOf(reason))),
            materializationIntegrityPassed = false,
            changeProvenanceClosed = false,
            collateralRegressionFree = false,
            reasonCodes = listOf(reason),
            shadowOnly = true,
            productionAuthority = false
        )

    private fun affectedTargetIds(
        comparison: StimulusSelectionProgramComparison,
        attributions: List<StimulusExperimentalChangeAttribution>
    ): Set<String> {
        val selectedTargets = comparison.selectionPlan.selectedCandidates
            .filter {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in comparison.addedOwnerIdentities ||
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in comparison.sharedOwnerIdentities
            }
            .flatMap { it.coveredTargetIds }
        val attributed = attributions.flatMap { it.targetIds }
        val fromTraces = comparison.materializationTraces
            .filter {
                val key = it.selectedStableKey ?: return@filter false
                val identity = it.selectionRole?.let { role -> StimulusPrescriptionOwnerIdentity(key, role) }
                identity != null && (identity in comparison.addedOwnerIdentities || identity in comparison.sharedOwnerIdentities)
            }
            .map { it.targetId }
        val result = (selectedTargets + attributed + fromTraces).toSet()
        if (result.isNotEmpty()) return result
        val all = comparison.targetPlan.qualityTargets.map { "QUALITY:${it.quality.name}" } +
            comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }
        return if (all.size == 1) all.toSet() else emptySet()
    }

    private fun attributeChanges(comparison: StimulusSelectionProgramComparison): List<StimulusExperimentalChangeAttribution> {
        val result = mutableListOf<StimulusExperimentalChangeAttribution>()
        val selected = comparison.selectionPlan.selectedCandidates.associateBy {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
        comparison.addedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val candidate = selected[identity]
            result += if (candidate != null) {
                StimulusExperimentalChangeAttribution(
                    stableKey = identity.stableKey,
                    selectionRole = identity.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
                    targetIds = candidate.coveredTargetIds.toList().sorted(),
                    reasonCodes = listOf("B5_SELECTED_IDENTITY_ADDED")
                )
            } else {
                StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole, StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
                    reasonCodes = listOf("UNEXPLAINED_ADDED_IDENTITY"))
            }
        }
        comparison.selectionPlan.selectedCandidates
            .filter {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in comparison.sharedOwnerIdentities
            }
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            .forEach { candidate ->
                result += StimulusExperimentalChangeAttribution(
                    stableKey = candidate.stableKey,
                    selectionRole = candidate.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B5_REUSED_IDENTITY,
                    targetIds = candidate.coveredTargetIds.toList().sorted(),
                    reasonCodes = listOf("B5_REUSED_IDENTITY")
                )
            }
        comparison.removedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val evidence = removalCausalEvidence(comparison, identity, selected)
            val targetIds = evidenceOwnerTargetIds(comparison, identity)
            result += when {
                evidence.governedExperimentalChangeExists &&
                    evidence.removedOwnerHasCapacityOrPlacementEvidence &&
                    evidence.removedOwnerHasDisappearanceEvidence &&
                    !evidence.removedOwnerWasDirectB5Action &&
                    !evidence.contradictoryProvenance ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT,
                        targetIds, listOf("REMOVED_IDENTITY_ATTRIBUTED_TO_DOWNSTREAM_CONSTRAINT"), evidence.evidenceSources)
                evidence.governedExperimentalChangeExists &&
                    evidence.removedOwnerHasCapacityOrPlacementEvidence &&
                    !evidence.removedOwnerWasDirectB5Action &&
                    !evidence.contradictoryProvenance ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT,
                        targetIds, listOf("REMOVAL_CAUSALITY_UNPROVEN"), evidence.evidenceSources)
                else ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
                        targetIds, listOf("UNEXPLAINED_REMOVED_IDENTITY"), evidence.evidenceSources)
            }
        }
        val authByOwner = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .filter { it.owner != null }
            .groupBy { StimulusPrescriptionOwnerIdentity(it.owner!!.stableKey, it.owner!!.selectionRole) }
        val controlByIdentity = comparison.control.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
        val experimentalByIdentity = comparison.experimental.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
        (controlByIdentity.keys intersect experimentalByIdentity.keys).sortedWith(compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val before = controlByIdentity.getValue(identity).map(::prescription)
            val after = experimentalByIdentity.getValue(identity).map(::prescription)
            if (before == after) return@forEach
            val executableAuthorizations = authByOwner[identity].orEmpty().mapNotNull { authorization ->
                val authorized = authorization.authorizedPrescription ?: return@mapNotNull null
                if (authorization.status !in setOf(
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
                    )) return@mapNotNull null
                val subsetValidation = validateAuthorizedWeeklySubset(
                    experimentalByIdentity.getValue(identity), authorized, identity.stableKey, identity.selectionRole
                )
                if (!subsetValidation.valid) return@mapNotNull null
                authorization to authorized
            }
            val source = when {
                executableAuthorizations.any { it.first.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR } -> StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
                executableAuthorizations.any { it.first.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE } -> StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION
                else -> StimulusExperimentalChangeAttributionSource.UNEXPLAINED
            }
            val reasonCodes = when {
                source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ->
                    listOf("UNEXPLAINED_PRESCRIPTION_CHANGE")
                else -> listOf("B6_AUTHORIZED_PRESCRIPTION_CHANGE")
            }
            result += StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole, source,
                executableAuthorizations.map { it.first.targetId }.distinct().sorted(), reasonCodes)
        }
        return result
    }

    private fun removalCausalEvidence(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        selected: Map<StimulusPrescriptionOwnerIdentity, StimulusSelectedCandidate>
    ): StimulusRemovalCausalEvidence {
        val selectionTraces = comparison.selectionPlan.traces.filter { trace ->
            traceOwnerMatches(trace, identity, comparison)
        }
        val materializationTraces = comparison.materializationTraces.filter { trace ->
            traceOwnerMatches(trace, identity, comparison)
        }
        val localReasonCodes = buildList {
            selectionTraces.forEach { trace ->
                val selectedOrReused =
                    (trace.selectedStableKey == identity.stableKey &&
                        roleMatches(trace.selectedSelectionRole, identity.selectionRole, comparison, identity.stableKey)) ||
                        (trace.coveredByPreviouslySelectedStableKey == identity.stableKey &&
                            roleMatches(trace.coveredByPreviouslySelectedSelectionRole, identity.selectionRole, comparison, identity.stableKey))
                if (selectedOrReused) addAll(trace.reasonCodes)
                trace.candidateRejectionReasons[identity.stableKey]?.let { reason ->
                    if (roleMatches(trace.candidateSelectionRoles[identity.stableKey], identity.selectionRole, comparison, identity.stableKey)) add(reason)
                }
            }
            materializationTraces.forEach { addAll(it.reasonCodes) }
        }.distinct()
        val governedByB5 = comparison.addedOwnerIdentities.any { selected[it] != null }
        val governedByB6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { authorization ->
            authorization.owner != null && authorization.authorizedPrescription != null &&
                authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                )
        }
        val capacityOrPlacement = localReasonCodes.any(::isCapacityOrPlacementEvidence)
        val disappearance = localReasonCodes.any(::isDisappearanceEvidence)
        val directB5Action = selected.containsKey(identity) || selectionTraces.any {
            (it.selectedStableKey == identity.stableKey && roleMatches(it.selectedSelectionRole, identity.selectionRole, comparison, identity.stableKey))
        }
        val contradictory = localReasonCodes.any(::isContradictoryProvenance)
        val sources = buildList {
            if (selectionTraces.isNotEmpty()) add("B5_SELECTION_TRACE_OWNER_LOCAL")
            if (materializationTraces.isNotEmpty()) add("FINAL_MATERIALIZATION_TRACE_OWNER_LOCAL")
            if (capacityOrPlacement) add("OWNER_LOCAL_CAPACITY_OR_PLACEMENT")
            if (disappearance) add("OWNER_LOCAL_DISAPPEARANCE")
        }.distinct()
        return StimulusRemovalCausalEvidence(
            stableKey = identity.stableKey,
            selectionRole = identity.selectionRole,
            governedExperimentalChangeExists = governedByB5 || governedByB6,
            removedOwnerHasCapacityOrPlacementEvidence = capacityOrPlacement,
            removedOwnerHasDisappearanceEvidence = disappearance,
            removedOwnerWasDirectB5Action = directB5Action,
            contradictoryProvenance = contradictory,
            evidenceSources = sources,
            reasonCodes = localReasonCodes
        )
    }

    private fun evidenceOwnerTargetIds(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<String> = (comparison.selectionPlan.traces.filter { traceOwnerMatches(it, identity, comparison) }.map { it.targetId } +
        comparison.materializationTraces.filter { traceOwnerMatches(it, identity, comparison) }.map { it.targetId }).distinct().sorted()

    private fun traceOwnerMatches(
        trace: StimulusCandidateSelectionTrace,
        identity: StimulusPrescriptionOwnerIdentity,
        comparison: StimulusSelectionProgramComparison
    ): Boolean {
        val selectedMatch = trace.selectedStableKey == identity.stableKey &&
            roleMatches(trace.selectedSelectionRole, identity.selectionRole, comparison, identity.stableKey)
        val reusedMatch = trace.coveredByPreviouslySelectedStableKey == identity.stableKey &&
            roleMatches(trace.coveredByPreviouslySelectedSelectionRole, identity.selectionRole, comparison, identity.stableKey)
        val rejectedMatch = trace.candidateRejectionReasons.containsKey(identity.stableKey) &&
            roleMatches(trace.candidateSelectionRoles[identity.stableKey], identity.selectionRole, comparison, identity.stableKey)
        return selectedMatch || reusedMatch || rejectedMatch
    }

    private fun traceOwnerMatches(
        trace: StimulusCandidateMaterializationTrace,
        identity: StimulusPrescriptionOwnerIdentity,
        comparison: StimulusSelectionProgramComparison
    ): Boolean = trace.selectedStableKey == identity.stableKey &&
        roleMatches(trace.selectionRole, identity.selectionRole, comparison, identity.stableKey)

    private fun roleMatches(
        traceRole: String?,
        expectedRole: String,
        comparison: StimulusSelectionProgramComparison,
        stableKey: String
    ): Boolean = traceRole == expectedRole ||
        (traceRole == null &&
            comparison.controlOwnerIdentities.count { it.stableKey == stableKey } <= 1 &&
            comparison.experimentalOwnerIdentities.count { it.stableKey == stableKey } <= 1)

    private fun isCapacityOrPlacementEvidence(code: String): Boolean =
        code.contains("CAPACITY", ignoreCase = true) || code.contains("PLACEMENT", ignoreCase = true) ||
            code.contains("REFLOW", ignoreCase = true) || code.contains("DISPLAC", ignoreCase = true) ||
            code.contains("CONSTRAINED_MACHINERY", ignoreCase = true)

    private fun isDisappearanceEvidence(code: String): Boolean =
        code.contains("NOT_MATERIALIZED", ignoreCase = true) || code.contains("REMOVED", ignoreCase = true) ||
            code.contains("DISPLAC", ignoreCase = true) || code.contains("UNFUNDED", ignoreCase = true) ||
            code.contains("DEFER", ignoreCase = true) || code.contains("DISAPPEAR", ignoreCase = true)

    private fun isContradictoryProvenance(code: String): Boolean =
        code.contains("DIRECT_B5_ACTION", ignoreCase = true) || code == "SELECTION_TARGET_IDENTITY_MATERIALIZED" ||
            code == "CONTROL_DIRECT_IDENTITY_ALREADY_PRESENT"

    private fun targetOutcomes(comparison: StimulusSelectionProgramComparison, affected: Set<String>): List<StimulusExperimentalTargetOutcome> =
        comparison.targetPlan.qualityTargets.map { target ->
            val id = "QUALITY:${target.quality.name}"
            val control = comparison.controlAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
            val experimental = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
            numericOutcome(id, affected.contains(id), target.numericAuthority, target.weeklyDirectUnitsTarget, target.weeklyDirectSessionsTarget,
                control?.plannedWeeklyDirectUnits, experimental?.plannedWeeklyDirectUnits, control?.plannedWeeklyDirectSessions, experimental?.plannedWeeklyDirectSessions,
                control?.weeklyDirectUnitsStatus, experimental?.weeklyDirectUnitsStatus, control?.weeklyDirectSessionsStatus, experimental?.weeklyDirectSessionsStatus)
        } + comparison.targetPlan.taskTargets.map { target ->
            val id = "TASK:${target.task}"
            val control = comparison.controlAudit?.taskAudits?.firstOrNull { it.task == target.task }
            val experimental = comparison.experimentalAudit?.taskAudits?.firstOrNull { it.task == target.task }
            taskOutcome(id, affected.contains(id), target.numericAuthority, target.weeklyDirectUnitsTarget, target.weeklyDirectSessionsTarget,
                control, experimental)
        }

    private fun numericOutcome(
        id: String,
        affected: Boolean,
        authority: StimulusTargetNumericAuthority,
        unitsRange: StimulusTargetRange?,
        sessionsRange: StimulusTargetRange?,
        controlUnits: Double?,
        experimentalUnits: Double?,
        controlSessions: Double?,
        experimentalSessions: Double?,
        controlUnitsStatus: StimulusTargetControlStatus?,
        experimentalUnitsStatus: StimulusTargetControlStatus?,
        controlSessionsStatus: StimulusTargetControlStatus?,
        experimentalSessionsStatus: StimulusTargetControlStatus?
    ): StimulusExperimentalTargetOutcome {
        if (authority == StimulusTargetNumericAuthority.NONE) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = if (affected) listOf("NO_AUTHORITY_CHANGE") else emptyList())
        if (authority == StimulusTargetNumericAuthority.DIRECTION_ONLY) {
            return directionOutcome(id, affected, controlUnitsStatus, experimentalUnitsStatus, controlSessionsStatus, experimentalSessionsStatus)
        }
        if (authority == StimulusTargetNumericAuthority.UNRESOLVED || unitsRange == null || sessionsRange == null || controlUnits == null || experimentalUnits == null || controlSessions == null || experimentalSessions == null) {
            return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = listOf("TARGET_NUMERIC_EVIDENCE_UNRESOLVED"))
        }
        val cu = distance(unitsRange, controlUnits)
        val eu = distance(unitsRange, experimentalUnits)
        val cs = distance(sessionsRange, controlSessions)
        val es = distance(sessionsRange, experimentalSessions)
        val relation = compareDistances(cu, eu, cs, es)
        val reasons = mutableListOf<String>()
        if (relation == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && (eu > 0.0 || es > 0.0)) reasons += "TARGET_UNMET"
        return StimulusExperimentalTargetOutcome(id, relation, affected, cu, eu, cs, es, reasons)
    }

    private fun taskOutcome(
        id: String,
        affected: Boolean,
        authority: StimulusTargetNumericAuthority,
        unitsRange: StimulusTargetRange?,
        sessionsRange: StimulusTargetRange?,
        control: StimulusTaskControlProgramAudit?,
        experimental: StimulusTaskControlProgramAudit?
    ): StimulusExperimentalTargetOutcome {
        if (authority == StimulusTargetNumericAuthority.NONE) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = if (affected) listOf("NO_AUTHORITY_CHANGE") else emptyList())
        if (authority == StimulusTargetNumericAuthority.UNRESOLVED || control == null || experimental == null) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = listOf("TARGET_EVIDENCE_UNRESOLVED"))
        if (authority == StimulusTargetNumericAuthority.DIRECTION_ONLY || unitsRange == null) {
            val status = directionStatus(control.status, experimental.status)
            return StimulusExperimentalTargetOutcome(id, status, affected, reasonCodes = if (status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && experimental.status != StimulusTargetControlStatus.DIRECT_PRESENT) listOf("TARGET_UNMET") else emptyList())
        }
        val cu = control.plannedDirectUnits?.let { distance(unitsRange, it) }
        val eu = experimental.plannedDirectUnits?.let { distance(unitsRange, it) }
        if (cu == null || eu == null) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = listOf("TARGET_NUMERIC_EVIDENCE_UNRESOLVED"))
        val status = compareDistances(cu, eu, null, null)
        return StimulusExperimentalTargetOutcome(id, status, affected, cu, eu, reasonCodes = if (status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && eu > 0.0) listOf("TARGET_UNMET") else emptyList())
    }

    private fun directionOutcome(id: String, affected: Boolean, cu: StimulusTargetControlStatus?, eu: StimulusTargetControlStatus?, cs: StimulusTargetControlStatus?, es: StimulusTargetControlStatus?): StimulusExperimentalTargetOutcome {
        if (listOf(cu, eu, cs, es).any { it == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED }) {
            return StimulusExperimentalTargetOutcome(id, StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE, affected,
                reasonCodes = listOf("DISTRIBUTION_COMPARISON_DEFERRED"))
        }
        val control = if (cu == StimulusTargetControlStatus.DIRECT_PRESENT || cs == StimulusTargetControlStatus.DIRECT_PRESENT) {
            StimulusTargetControlStatus.DIRECT_PRESENT
        } else cu ?: cs
        val experimental = if (eu == StimulusTargetControlStatus.DIRECT_PRESENT || es == StimulusTargetControlStatus.DIRECT_PRESENT) {
            StimulusTargetControlStatus.DIRECT_PRESENT
        } else eu ?: es
        val status = directionStatus(control, experimental)
        return StimulusExperimentalTargetOutcome(id, status, affected,
            reasonCodes = if (status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && experimental != StimulusTargetControlStatus.DIRECT_PRESENT) listOf("TARGET_UNMET") else emptyList())
    }

    private fun directionStatus(control: StimulusTargetControlStatus?, experimental: StimulusTargetControlStatus?): StimulusExperimentalTargetOutcomeStatus = when {
        control == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED || experimental == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED -> StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE
        control == StimulusTargetControlStatus.DIRECT_ABSENT && experimental == StimulusTargetControlStatus.DIRECT_PRESENT -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
        control == StimulusTargetControlStatus.DIRECT_PRESENT && experimental == StimulusTargetControlStatus.DIRECT_ABSENT -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
        else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
    }

    private fun compareDistances(cu: Double?, eu: Double?, cs: Double?, es: Double?): StimulusExperimentalTargetOutcomeStatus {
        if (cu == null || eu == null || ((cs == null) != (es == null))) return StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE
        val dimensions = buildList {
            add(compareDistance(cu, eu))
            if (cs != null && es != null) add(compareDistance(cs, es))
        }
        val improved = dimensions.count { it == StimulusExperimentalTargetOutcomeStatus.IMPROVED }
        val regressed = dimensions.count { it == StimulusExperimentalTargetOutcomeStatus.REGRESSED }
        return when {
            regressed > 0 -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
            improved > 0 -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
            else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
        }
    }

    private fun compareDistance(control: Double, experimental: Double): StimulusExperimentalTargetOutcomeStatus = when {
        experimental < control -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
        experimental > control -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
        else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
    }

    private fun distance(range: StimulusTargetRange, value: Double): Double = when {
        value < range.min -> range.min - value
        value > range.max -> value - range.max
        else -> 0.0
    }

    private fun prescription(item: ProgramSkeletonItem) = PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)

}

internal fun StimulusExperimentalReadinessAudit.toJson(): JSONObject = JSONObject()
    .put("status", status.name)
    .put("materializationIntegrityPassed", materializationIntegrityPassed)
    .put("changeProvenanceClosed", changeProvenanceClosed)
    .put("collateralRegressionFree", collateralRegressionFree)
    .put("shadowOnly", shadowOnly)
    .put("productionAuthority", productionAuthority)
    .put("reasonCodes", JSONArray(reasonCodes))
    .put("targetOutcomes", JSONArray(targetOutcomes.map { outcome -> JSONObject()
        .put("targetId", outcome.targetId).put("status", outcome.status.name).put("directlyAffected", outcome.directlyAffected)
        .put("controlWeeklyUnitsDistance", outcome.controlWeeklyUnitsDistance).put("experimentalWeeklyUnitsDistance", outcome.experimentalWeeklyUnitsDistance)
        .put("controlWeeklySessionsDistance", outcome.controlWeeklySessionsDistance).put("experimentalWeeklySessionsDistance", outcome.experimentalWeeklySessionsDistance)
        .put("reasonCodes", JSONArray(outcome.reasonCodes))
    }))
    .put("changeAttributions", JSONArray(changeAttributions.map { attribution -> JSONObject()
        .put("stableKey", attribution.stableKey).put("selectionRole", attribution.selectionRole).put("source", attribution.source.name)
        .put("targetIds", JSONArray(attribution.targetIds)).put("reasonCodes", JSONArray(attribution.reasonCodes))
        .put("evidenceSources", JSONArray(attribution.evidenceSources))
    }))
