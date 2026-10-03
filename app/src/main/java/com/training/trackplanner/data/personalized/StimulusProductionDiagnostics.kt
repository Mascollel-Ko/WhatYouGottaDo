package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality

enum class StimulusProductionScopeResolutionStatus {
    RESOLVED_STRENGTH, RESOLVED_STRENGTH_CALIBRATION, RESOLVED_HYPERTROPHY, RESOLVED_STRENGTH_HYPERTROPHY,
    NO_MATERIAL, MISSING_PROVENANCE, PARTIAL_PROVENANCE, UNKNOWN_TARGET,
    UNSUPPORTED_QUALITY, AMBIGUOUS_MATERIAL_SCOPE
}

data class StimulusProductionScopeResolution(
    val scope: StimulusProductionCutoverScope?,
    val status: StimulusProductionScopeResolutionStatus,
    val materialQualities: Set<TrainableQuality>,
    val materialOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>,
    val reasonCodes: List<String>,
    val unknownTargetIds: Set<String>,
    val unattributedOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>,
    val removedOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>,
    val materialAuthorityIdentities: Set<StimulusPrescriptionAuthorityIdentity>
)

/** Observation only. The nullable scope is supplied by the existing resolver, never inferred here. */
internal fun observeProductionScope(
    comparison: StimulusSelectionProgramComparison,
    scope: StimulusProductionCutoverScope?
): StimulusProductionScopeResolution {
    val owners = buildSet {
        addAll(comparison.addedOwnerIdentities)
        comparison.sharedOwnerIdentities.forEach { owner ->
            if (comparison.control.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole } !=
                comparison.experimental.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }) add(owner)
        }
    }
    val audit = comparison.experimentalReadinessAudit
    val attributions = audit?.changeAttributions.orEmpty().filter {
        it.source in setOf(StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
            StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION) &&
            it.stableKey != null && it.selectionRole != null &&
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in owners
    }
    val attributed = attributions.map { StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole)) }.toSet()
    val targetIds = attributions.flatMap { it.targetIds }.toSortedSet()
    val governed = comparison.targetPlan.qualityTargets.map { "QUALITY:${it.quality.name}" }.toSet() +
        comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }
    val unknown = (targetIds - governed).toSortedSet()
    val qualities = targetIds.mapNotNull { id -> TrainableQuality.entries.firstOrNull { id == "QUALITY:${it.name}" } }.toSet()
    val unsupported = qualities - setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)
    val reasons = sortedSetOf<String>()
    val removed = comparison.removedOwnerIdentities
    val structureChanged = comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule ||
        personalizedProgramFingerprint(comparison.control.request, comparison.control.items) !=
        personalizedProgramFingerprint(comparison.experimental.request, comparison.experimental.items)
    if (owners.isEmpty() && removed.isEmpty() && !structureChanged) reasons += "NO_MATERIAL_CHANGE"
    if (owners.isEmpty() && removed.isEmpty() && structureChanged) reasons += "PROGRAM_STRUCTURE_CHANGE_WITHOUT_MATERIAL_OWNER"
    if (removed.isNotEmpty()) reasons += "REMOVED_OWNER_CHANGE_PRESENT"
    if (owners.isEmpty() && removed.isNotEmpty()) reasons += "REMOVAL_ONLY_SCOPE_UNSUPPORTED"
    if (audit == null) reasons += "MATERIAL_READINESS_AUDIT_MISSING"
    if (owners.isNotEmpty() && attributions.isEmpty()) reasons += "MATERIAL_PROVENANCE_MISSING"
    if (attributed.isNotEmpty() && attributed != owners) reasons += "MATERIAL_PROVENANCE_PARTIAL"
    if (attributions.isNotEmpty() && targetIds.isEmpty()) reasons += "MATERIAL_TARGET_PROVENANCE_MISSING"
    if (attributions.any { it.targetIds.isEmpty() }) reasons += "MATERIAL_ATTRIBUTION_TARGETS_EMPTY"
    if (unknown.isNotEmpty()) reasons += "UNKNOWN_TARGET_ID"
    unsupported.forEach { reasons += "UNSUPPORTED_QUALITY_${it.name}" }
    if (qualities.containsAll(setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)) && unsupported.isNotEmpty()) reasons += "THIRD_QUALITY_PRESENT"
    if (targetIds.any { id -> id in governed && !id.startsWith("QUALITY:") }) reasons += "UNSUPPORTED_TARGET_COMBINATION"
    val status = when {
        scope == StimulusProductionCutoverScope.STRENGTH_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_STRENGTH
        scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_STRENGTH_CALIBRATION
        scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_HYPERTROPHY
        scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_STRENGTH_HYPERTROPHY
        audit == null -> StimulusProductionScopeResolutionStatus.MISSING_PROVENANCE
        owners.isEmpty() && removed.isNotEmpty() -> StimulusProductionScopeResolutionStatus.AMBIGUOUS_MATERIAL_SCOPE
        owners.isEmpty() && structureChanged -> StimulusProductionScopeResolutionStatus.AMBIGUOUS_MATERIAL_SCOPE
        owners.isEmpty() -> StimulusProductionScopeResolutionStatus.NO_MATERIAL
        attributions.isEmpty() || targetIds.isEmpty() -> StimulusProductionScopeResolutionStatus.MISSING_PROVENANCE
        attributed != owners -> StimulusProductionScopeResolutionStatus.PARTIAL_PROVENANCE
        unknown.isNotEmpty() -> StimulusProductionScopeResolutionStatus.UNKNOWN_TARGET
        unsupported.isNotEmpty() -> StimulusProductionScopeResolutionStatus.UNSUPPORTED_QUALITY
        else -> StimulusProductionScopeResolutionStatus.AMBIGUOUS_MATERIAL_SCOPE
    }
    val identities = attributions.flatMap { attribution ->
        attribution.targetIds.mapNotNull { id ->
            TrainableQuality.entries.firstOrNull { id == "QUALITY:${it.name}" }?.let { quality ->
                StimulusPrescriptionAuthorityIdentity(requireNotNull(attribution.stableKey), requireNotNull(attribution.selectionRole), quality)
            }
        }
    }.toSet()
    return StimulusProductionScopeResolution(scope, status, qualities, owners + removed, reasons.toList(), unknown, owners - attributed, removed, identities)
}

enum class StimulusProductionFallbackStage {
    CONTROL_POLICY, UPSTREAM_EVALUATION_FAILURE, SCOPE_RESOLUTION, B6_EXECUTION_AUTHORITY,
    B7_READINESS, B8_CUTOVER_AUTHORITY, B9_ROUTING_CONTRACT, NO_MATERIAL_CHANGE
}

/** Primary stage is exclusive; secondary reasons retain all observations without driving routing. */
internal data class StimulusProductionDiagnostics(
    val scopeResolution: StimulusProductionScopeResolution?,
    val primaryFallbackStage: StimulusProductionFallbackStage?,
    val secondaryReasonCodes: List<String>
) {
    companion object {
        fun observe(
            comparison: StimulusSelectionProgramComparison?,
            route: StimulusProductionRoutingDecision,
            upstreamFailureReason: String? = null
        ): StimulusProductionDiagnostics {
            val resolution = comparison?.let { StimulusProductionMaterialScopeResolver().resolveDetailed(it) }
            val b7 = comparison?.experimentalReadinessAudit
            val b8 = comparison?.productionCutoverAuthority
            // Restrict B6 blockers to actual material owners. Unrelated unavailable quality
            // models must not explain away a different CONTROL decision.
            val failedB6 = comparison?.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { authorization ->
                val owner = authorization.owner
                val quality = authorization.quality
                owner != null && quality != null &&
                    (StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, quality) in resolution?.materialAuthorityIdentities.orEmpty() ||
                        (StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) in resolution?.materialOwnerIdentities.orEmpty() && quality in resolution?.materialQualities.orEmpty())) && (authorization.status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR) ||
                    authorization.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED) &&
                        !(resolution?.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 &&
                            authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                            authorization.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT)
            }
            val failedMaterializations = comparison?.prescriptionMaterializationAudits.orEmpty().filter { materialization ->
                val owner = materialization.owner
                val quality = materialization.quality
                owner != null && quality != null &&
                    (StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, quality) in resolution?.materialAuthorityIdentities.orEmpty() ||
                        (StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) in resolution?.materialOwnerIdentities.orEmpty() && quality in resolution?.materialQualities.orEmpty())) &&
                    (materialization.state != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ||
                        materialization.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED) &&
                    !(resolution?.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 &&
                        materialization.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED &&
                        materialization.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT)
            }
            val primary = when {
                route.selectedSource != StimulusProductionProgramSource.CONTROL -> null
                route.mode == StimulusProductionRoutingMode.CONTROL_ONLY -> StimulusProductionFallbackStage.CONTROL_POLICY
                comparison == null -> StimulusProductionFallbackStage.UPSTREAM_EVALUATION_FAILURE
                b7?.materializationIntegrityPassed == false -> StimulusProductionFallbackStage.B6_EXECUTION_AUTHORITY
                resolution?.status == StimulusProductionScopeResolutionStatus.NO_MATERIAL -> StimulusProductionFallbackStage.NO_MATERIAL_CHANGE
                resolution?.scope == null -> StimulusProductionFallbackStage.SCOPE_RESOLUTION
                failedB6.isNotEmpty() || failedMaterializations.isNotEmpty() -> StimulusProductionFallbackStage.B6_EXECUTION_AUTHORITY
                b7?.status != StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW -> StimulusProductionFallbackStage.B7_READINESS
                b8?.status != StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER -> StimulusProductionFallbackStage.B8_CUTOVER_AUTHORITY
                else -> StimulusProductionFallbackStage.B9_ROUTING_CONTRACT
            }
            return StimulusProductionDiagnostics(resolution, primary, (route.reasonCodes + resolution?.reasonCodes.orEmpty() +
                b7?.reasonCodes.orEmpty() + b8?.reasonCodes.orEmpty() + failedB6.flatMap { it.reasonCodes } +
                failedMaterializations.flatMap { it.reasonCodes } + listOfNotNull(upstreamFailureReason)).distinct().sorted())
        }
    }
}
