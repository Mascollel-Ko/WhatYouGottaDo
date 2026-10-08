package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality

enum class StimulusProductionScopeResolutionStatus {
    RESOLVED_STRENGTH, RESOLVED_STRENGTH_CALIBRATION, RESOLVED_HYPERTROPHY, RESOLVED_STRENGTH_HYPERTROPHY,
    RESOLVED_BADMINTON_TASK,
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
    val materialAuthorityIdentities: Set<StimulusPrescriptionAuthorityIdentity>,
    val materialTaskProtocolIdentities: Set<StimulusTaskProtocolAuthorityIdentity> = emptySet()
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
            StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION,
            StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL) &&
            it.stableKey != null && it.selectionRole != null &&
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in owners
    }
    val attributed = attributions.map { StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole)) }.toSet()
    val targetIds = attributions.flatMap { it.targetIds }.toSortedSet()
    val regionalHypertrophyTargets = comparison.targetPlan.movementTargets.filter { movement ->
        val dose = movement.regionalDoseTargets.firstOrNull {
            it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                it.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                it.numericAuthority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
                    StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY
                ) && it.weeklyTarget != null && it.existingEquivalentExposure?.let { value -> value.isFinite() && value >= 0.0 } == true &&
                (it.authorizedWholeSetUnits ?: 0) > 0 && it.residualEquivalentExposure?.let { value -> value.isFinite() && value > 0.0 } == true &&
                (it.numericAuthority != StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ||
                    it.evidence.any { evidence -> evidence.contains("doseProvenance=${RegionalColdStartDosePolicy.PROVENANCE}") })
        } ?: return@filter false
        val selected = comparison.selectionPlan.selectedCandidates.filter { movement.targetId in it.coveredTargetIds }
        selected.size == 1 && comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { authorization ->
            authorization.targetId == movement.targetId && authorization.quality == TrainableQuality.HYPERTROPHY &&
                authorization.owner?.let { owner -> selected.singleOrNull()?.let { owner.stableKey == it.stableKey && owner.selectionRole == it.selectionRole } } == true &&
                authorization.authorizedPrescription != null && authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                ) && authorization.authorizedPrescription.sets.size == dose.authorizedWholeSetUnits
        }
    }.map { it.targetId }.toSet()
    val existingAuthorizedRegionalTargets = comparison.targetPlan.movementTargets.filter { movement ->
        val dose = movement.regionalDoseTargets.firstOrNull {
            it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                it.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                it.numericAuthority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
                    StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY
                ) && it.weeklyTarget != null && it.authorizedWholeSetUnits == 0 &&
                it.existingEquivalentExposure?.let { value -> value.isFinite() && value + 1e-9 >= it.weeklyTarget } == true &&
                (it.numericAuthority != StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ||
                    it.evidence.any { evidence -> evidence.contains("doseProvenance=${RegionalColdStartDosePolicy.PROVENANCE}") })
        } ?: return@filter false
        val selected = comparison.selectionPlan.selectedCandidates.filter { movement.targetId in it.coveredTargetIds }
        val owner = selected.singleOrNull()?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            ?: return@filter false
        val movementAuthority = comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty()
            .singleOrNull { it.targetId == movement.targetId }
        movementAuthority?.status == StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6 &&
            movementAuthority.existingAuthorityTargetId == "QUALITY:HYPERTROPHY" &&
            comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { authorization ->
                authorization.targetId == "QUALITY:HYPERTROPHY" && authorization.quality == TrainableQuality.HYPERTROPHY &&
                    authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } == owner &&
                    authorization.authorizedPrescription != null && authorization.status in setOf(
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                    )
            }
    }.map { it.targetId }.toSet()
    val governed = comparison.targetPlan.qualityTargets.map { "QUALITY:${it.quality.name}" }.toSet() +
        comparison.targetPlan.taskTargets.map { "TASK:${it.task}" } + regionalHypertrophyTargets + existingAuthorizedRegionalTargets
    val unknown = (targetIds - governed).toSortedSet()
    val qualities = targetIds.mapNotNull { id ->
        TrainableQuality.entries.firstOrNull { id == "QUALITY:${it.name}" }
            ?: TrainableQuality.HYPERTROPHY.takeIf { id in regionalHypertrophyTargets || id in existingAuthorizedRegionalTargets }
    }.toSet()
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
    if (scope != StimulusProductionCutoverScope.BADMINTON_TASK_V1 &&
        targetIds.any { id -> id in governed && !id.startsWith("QUALITY:") &&
            id !in regionalHypertrophyTargets && id !in existingAuthorizedRegionalTargets }) reasons += "UNSUPPORTED_TARGET_COMBINATION"
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
            (TrainableQuality.entries.firstOrNull { id == "QUALITY:${it.name}" }
                ?: TrainableQuality.HYPERTROPHY.takeIf { id in regionalHypertrophyTargets || id in existingAuthorizedRegionalTargets })?.let { quality ->
                StimulusPrescriptionAuthorityIdentity(requireNotNull(attribution.stableKey), requireNotNull(attribution.selectionRole), quality)
            }
        }
    }.toSet()
    val taskIdentities = owners.mapNotNull { owner ->
        val metadata = taskProtocolMetadataForOwner(comparison.experimental, owner)
        val definitions = metadata.map { it.authorization.definition }.distinct()
        val tasks = metadata.map { it.authorization.attributedTasks }.distinct()
        val definition = definitions.singleOrNull()?.takeIf {
            ApprovedBadmintonTaskProtocols.exact(owner.stableKey, owner.selectionRole, it.primaryTask) == it
        } ?: return@mapNotNull null
        val authorizedTasks = tasks.singleOrNull() ?: return@mapNotNull null
        StimulusTaskProtocolAuthorityIdentity(definition.protocolId, owner.stableKey, owner.selectionRole, authorizedTasks)
    }.toSet()
    if (targetIds.any { it.startsWith("TASK:") } && scope == null && targetIds.all { it in governed }) {
        reasons += "MATERIAL_SCOPE_MIXED_OR_UNSUPPORTED"
    }
    val finalStatus = when {
        scope == StimulusProductionCutoverScope.STRENGTH_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_STRENGTH
        scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_STRENGTH_CALIBRATION
        scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_HYPERTROPHY
        scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_STRENGTH_HYPERTROPHY
        scope == StimulusProductionCutoverScope.BADMINTON_TASK_V1 -> StimulusProductionScopeResolutionStatus.RESOLVED_BADMINTON_TASK
        else -> status
    }
    return StimulusProductionScopeResolution(scope, finalStatus, qualities, owners + removed, reasons.toList(), unknown,
        owners - attributed, removed, identities, taskIdentities)
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
