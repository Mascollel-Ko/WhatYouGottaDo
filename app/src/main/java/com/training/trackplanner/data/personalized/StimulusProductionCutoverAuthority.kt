package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** The deliberately narrow first production-cutover boundary. */
enum class StimulusProductionCutoverScope {
    STRENGTH_V1,
    STRENGTH_CALIBRATION_V1,
    HYPERTROPHY_V1,
    STRENGTH_HYPERTROPHY_V1,
    BADMINTON_TASK_V1,
    POWER_JUMP_V1
}

enum class StimulusProductionCutoverAuthorityStatus {
    AUTHORIZED_FOR_BOUNDED_CUTOVER,
    CONTROL_REQUIRED,
    INCONCLUSIVE,
    NO_MATERIAL_CHANGE
}

data class StimulusTaskProtocolAuthorityIdentity(
    val protocolId: String,
    val stableKey: String,
    val selectionRole: String,
    val authorizedTasks: Set<CanonicalTaskTarget>
)

/** Exact B4 movement target to B5 owner binding accepted by B8 alongside another bounded scope. */
data class StimulusMovementTargetOwnerIdentity(
    val targetId: String,
    val stableKey: String,
    val selectionRole: String
)

data class StimulusProductionCutoverAuthorityDecision(
    val status: StimulusProductionCutoverAuthorityStatus,
    val scope: StimulusProductionCutoverScope,
    val authorizedOwnerIdentities: List<StimulusPrescriptionOwnerIdentity>,
    val reasonCodes: List<String>,
    val b7Status: StimulusExperimentalReadinessStatus,
    val routingActive: Boolean = false,
    val productionMutationAuthority: Boolean = false,
    val authorizedAuthorityIdentities: List<StimulusPrescriptionAuthorityIdentity> = emptyList(),
    val authorizedTaskProtocolIdentities: List<StimulusTaskProtocolAuthorityIdentity> = emptyList(),
    val authorizedMovementTargetOwnerIdentities: List<StimulusMovementTargetOwnerIdentity> = emptyList()
) {
    // B8 normally emits both flags as false. The B9 selector still validates them so malformed
    // diagnostics fail closed with a typed contract-inconsistency reason instead of throwing.
}

/** Result of the internal B8 evaluation. The experimental object is already built by B6.2. */
data class StimulusProductionCutoverEvaluation(
    val comparison: StimulusSelectionProgramComparison,
    val cutoverAuthority: StimulusProductionCutoverAuthorityDecision
) {
    val decision: StimulusProductionCutoverAuthorityDecision get() = cutoverAuthority
}

/**
 * B8 is a pure, fail-closed consumer of the existing B6.2/B7 comparison. It does not have
 * access to a builder, DAO, snapshot or request, so it cannot rerun any upstream phase.
 */
class StimulusProductionCutoverAuthorityAuditEngine {
    fun audit(comparison: StimulusSelectionProgramComparison): StimulusProductionCutoverAuthorityDecision {
        // Preserve the historical Strength default for every existing scope, while dispatching
        // only an exact, fully attributed B6 Power/Jump material delta to its bounded B8 audit.
        val scope = StimulusProductionMaterialScopeResolver().resolve(comparison)
            ?.takeIf { it == StimulusProductionCutoverScope.POWER_JUMP_V1 }
            ?: StimulusProductionCutoverScope.STRENGTH_V1
        return audit(comparison, scope)
    }

    fun audit(
        comparison: StimulusSelectionProgramComparison,
        scope: StimulusProductionCutoverScope
    ): StimulusProductionCutoverAuthorityDecision {
        if (scope == StimulusProductionCutoverScope.POWER_JUMP_V1) {
            return auditPowerJump(comparison)
        }
        if (scope == StimulusProductionCutoverScope.BADMINTON_TASK_V1) {
            return auditBadmintonTask(comparison)
        }
        if (scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1) {
            return auditCombined(comparison)
        }
        val policy = scopePolicy(scope)
        val b7 = comparison.experimentalReadinessAudit
            ?: return control(comparison, policy, "B8_B7_AUDIT_MISSING")

        when (b7.status) {
            StimulusExperimentalReadinessStatus.NOT_ELIGIBLE ->
                return control(comparison, policy, "B8_B7_NOT_ELIGIBLE")
            StimulusExperimentalReadinessStatus.INCONCLUSIVE ->
                return inconclusive(comparison, policy, "B8_B7_INCONCLUSIVE")
            StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE ->
                return noMaterialChange(policy, b7.status)
            StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW -> Unit
        }

        val reasons = linkedSetOf<String>()
        if (!b7.changeProvenanceClosed) reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        if (!b7.collateralRegressionFree) reasons += "B8_CUTOVER_V1_COLLATERAL_REGRESSION"
        if (b7.changeAttributions.any {
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
                    it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
            }) {
            reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        }
        if (b7.targetOutcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }) {
            reasons += "B8_CUTOVER_V1_COLLATERAL_REGRESSION"
        }
        val materialOwners = materialOwnerIdentities(comparison)
        val roleReplacements = canonicalRoleReplacementControlOwners(comparison, materialOwners) +
            StimulusProductionMovementScopeEvidence.exactUserApprovedReplacementControlOwners(comparison)
        if ((comparison.removedOwnerIdentities - roleReplacements).isNotEmpty()) {
            reasons += "B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"
        }
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) {
            reasons += "B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED"
        }
        reasons += b6IntegrityReasons(comparison, policy, materialOwners)
        if (materialOwners.isEmpty()) {
            reasons += "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"
            reasons += "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        if (materialOwners.isNotEmpty()) {
            materialOwners.forEach { identity ->
                val authorization = exactAuthorization(comparison, identity, policy)
                val targetId = authorization?.targetId
                    ?: if (identity in comparison.addedOwnerIdentities) policy.targetId else null
                targetAuthorityReason(comparison, targetId, policy)?.let(reasons::add)
            }
            val nonTarget = materialOwners.flatMap { identity ->
                materialAttributionsFor(comparison, identity).filter(::isMaterialAttribution).flatMap { attribution ->
                    attribution.targetIds.filterNot { it in allowedMaterialTargetIds(comparison, policy) }
                }
            }.distinct().sorted()
            if (nonTarget.isNotEmpty()) {
                reasons += policy.nonQualityChangeReason
            }
        }

        val authorized = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
        materialOwners.sortedWith(OWNER_ORDER).forEach { identity ->
            when {
                identity in comparison.addedOwnerIdentities -> {
                    val validation = validateAddedOwner(comparison, identity, policy)
                    reasons += validation
                    if (validation.isEmpty()) authorized += identity
                }
                identity in comparison.sharedOwnerIdentities -> {
                    val validation = validateSharedOwner(comparison, identity, policy)
                    reasons += validation
                    if (validation.isEmpty()) authorized += identity
                }
            }
        }

        if (authorized.isEmpty()) {
            reasons += "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"
        }

        val parityFailures = unrelatedControlParityFailures(comparison, authorized)
        if (parityFailures) reasons += "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION"

        // A material owner is authorized only after every independent gate passes. Do not
        // expose a partial list when the decision is CONTROL_REQUIRED or INCONCLUSIVE.
        val normalizedReasons = reasons.toList().sorted()
        return if (normalizedReasons.isEmpty()) {
            StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                scope = policy.scope,
                authorizedOwnerIdentities = authorized.toList().sortedWith(OWNER_ORDER),
                authorizedAuthorityIdentities = authorized.mapNotNull { identity ->
                    exactAuthorization(comparison, identity, policy)?.let {
                        StimulusPrescriptionAuthorityIdentity(identity.stableKey, identity.selectionRole, policy.quality)
                    }
                }.sortedWith(AUTHORITY_ORDER),
                reasonCodes = listOf(policy.authorizedReason),
                b7Status = b7.status
            )
        } else {
            control(comparison, policy, normalizedReasons)
        }
    }

    /** B8 consumes only exact B7-closed Power/Jump material and rechecks its full B3-B6 chain. */
    private fun auditPowerJump(
        comparison: StimulusSelectionProgramComparison
    ): StimulusProductionCutoverAuthorityDecision {
        val scope = StimulusProductionCutoverScope.POWER_JUMP_V1
        fun denied(reason: String): StimulusProductionCutoverAuthorityDecision = StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
            scope = scope,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf(reason),
            b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
        )
        if (StimulusProductionMaterialScopeResolver().resolve(comparison) != scope) {
            return denied("B8_POWER_JUMP_MATERIAL_SCOPE_MISMATCH")
        }
        val b7 = comparison.experimentalReadinessAudit ?: return denied("B8_POWER_JUMP_B7_AUDIT_MISSING")
        when (b7.status) {
            StimulusExperimentalReadinessStatus.NOT_ELIGIBLE -> return denied("B8_POWER_JUMP_B7_NOT_ELIGIBLE")
            StimulusExperimentalReadinessStatus.INCONCLUSIVE -> return inconclusivePowerJump(comparison, "B8_POWER_JUMP_B7_INCONCLUSIVE")
            StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE -> return StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE,
                scope = scope,
                authorizedOwnerIdentities = emptyList(),
                reasonCodes = listOf("B8_NO_MATERIAL_CHANGE"),
                b7Status = b7.status
            )
            StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW -> Unit
        }

        val reasons = linkedSetOf<String>()
        if (!b7.changeProvenanceClosed || b7.changeAttributions.any {
                it.source in setOf(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
                    StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT)
            }) reasons += "B8_POWER_JUMP_PROVENANCE_NOT_CLOSED"
        if (!b7.materializationIntegrityPassed) reasons += "B8_POWER_JUMP_MATERIALIZATION_NOT_INTEGRAL"
        if (!b7.collateralRegressionFree || b7.targetOutcomes.any {
                it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED
            }) reasons += "B8_POWER_JUMP_TARGET_OR_COLLATERAL_REGRESSION"
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) {
            reasons += "B8_POWER_JUMP_WEEKDAY_SCHEDULE_CHANGED"
        }
        if (comparison.control.request != comparison.experimental.request ||
            comparison.control.durationDays != comparison.experimental.durationDays ||
            comparison.control.periodizationType != comparison.experimental.periodizationType) {
            reasons += "B8_POWER_JUMP_PROGRAM_CONTRACT_CHANGED"
        }
        if (ProgramProjectionValidator().errors(comparison.experimental).isNotEmpty() ||
            comparison.experimental.items.any { it.dayOfWeek !in comparison.experimental.weekDaySchedule[it.weekNumber].orEmpty() }) {
            reasons += "B8_POWER_JUMP_HARD_PROJECTION_INVALID"
        }

        val materialOwners = materialOwnerIdentities(comparison)
        val movementTargetOwners = StimulusProductionMovementScopeEvidence.exactMovementTargetOwnerIdentities(
            comparison, materialOwners
        )
        val movementOwners = movementTargetOwners.mapTo(linkedSetOf()) {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
        val exactMovementRoleReplacements = StimulusProductionMovementScopeEvidence.exactSameExerciseRoleReplacements(
            comparison, movementOwners
        ) + StimulusProductionMovementScopeEvidence.exactUserApprovedReplacementControlOwners(comparison)
        if ((comparison.removedOwnerIdentities - exactMovementRoleReplacements).isNotEmpty()) {
            reasons += "B8_POWER_JUMP_CONTROL_OWNER_REMOVAL_NOT_EXACT_MOVEMENT_ROLE_REPLACEMENT"
        }
        if (materialOwners.isEmpty()) reasons += "B8_POWER_JUMP_EMPTY_MATERIAL_AUTHORITY"
        reasons += b6IntegrityReasonsInternal(comparison) { owner, quality ->
            owner != null && owner in materialOwners && quality in setOf(TrainableQuality.POWER, TrainableQuality.REACTIVE_STRENGTH_SSC)
        }
        val attrsByOwner = b7.changeAttributions.filter { attr ->
            attr.stableKey != null && attr.selectionRole != null &&
                StimulusPrescriptionOwnerIdentity(attr.stableKey, attr.selectionRole) in materialOwners
        }.groupBy { StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole)) }
        if (attrsByOwner.keys != materialOwners) reasons += "B8_POWER_JUMP_MATERIAL_PROVENANCE_INCOMPLETE"
        val attributedTargetIds = attrsByOwner.values.flatten().flatMap { it.targetIds }.toSet()
        val governedTaskTargetIds = comparison.targetPlan.taskTargets.mapTo(linkedSetOf()) { "TASK:${it.task}" }
        val exactMovementTargetIds = movementTargetOwners.mapTo(linkedSetOf()) { it.targetId }
        if (attributedTargetIds.isEmpty() || attributedTargetIds.any {
                it !in POWER_JUMP_TARGET_IDS && it !in governedTaskTargetIds && it !in exactMovementTargetIds
            }) {
            reasons += "B8_POWER_JUMP_UNKNOWN_OR_EMPTY_TARGET"
        }
        val taskTargets = comparison.targetPlan.taskTargets.mapNotNull { target ->
            runCatching { CanonicalTaskTarget.valueOf(target.task) }.getOrNull()
        }.toSet()
        if (taskTargets.size != comparison.targetPlan.taskTargets.size ||
            comparison.targetPlan.taskTargets.any { target ->
                target.task == CanonicalTaskTarget.JUMP_LANDING.name &&
                    (target.strategy == StimulusDoseStrategy.UNRESOLVED || "TASK:JUMP_LANDING" in comparison.targetPlan.unresolved)
            }) reasons += "B8_POWER_JUMP_UNRESOLVED_OR_UNKNOWN_TASK_TARGET"

        val authorized = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
        val authorityIdentities = linkedSetOf<StimulusPrescriptionAuthorityIdentity>()
        val taskProtocolIdentities = linkedSetOf<StimulusTaskProtocolAuthorityIdentity>()
        val authorizedMovementTargetIdentities = linkedSetOf<StimulusMovementTargetOwnerIdentity>()
        val powerJumpOwners = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
        materialOwners.sortedWith(OWNER_ORDER).forEach { owner ->
            val ownerAttributions = attrsByOwner[owner].orEmpty()
            val targetId = ownerAttributions.singleOrNull()?.targetIds?.singleOrNull()
            val ownerReasons = when {
                targetId != null && targetId in POWER_JUMP_TARGET_IDS -> validatePowerJumpOwner(comparison, owner, ownerAttributions)
                targetId != null && targetId in governedTaskTargetIds -> if (ownerAttributions.singleOrNull()?.source ==
                    StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL) {
                    validateTaskOwner(comparison, owner, taskTargets)
                } else listOf("B8_POWER_JUMP_TASK_PROTOCOL_AUTHORITY_REQUIRED")
                targetId != null && targetId in exactMovementTargetIds -> validateMovementTargetOwner(
                    comparison, owner, targetId, ownerAttributions
                )
                else -> listOf("B8_POWER_JUMP_FOREIGN_OR_UNAUTHORIZED_MATERIAL")
            }
            reasons += ownerReasons
            if (ownerReasons.isEmpty()) {
                val quality = when (targetId) {
                    "QUALITY:POWER" -> TrainableQuality.POWER
                    "QUALITY:REACTIVE_STRENGTH_SSC" -> TrainableQuality.REACTIVE_STRENGTH_SSC
                    else -> null
                }
                if (quality != null) {
                    authorized += owner
                    powerJumpOwners += owner
                    authorityIdentities += StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, quality)
                } else if (targetId != null && targetId in governedTaskTargetIds) {
                    val metadata = taskProtocolMetadataForOwner(comparison.experimental, owner).firstOrNull()
                    val definition = metadata?.authorization?.definition
                    if (metadata == null || definition == null) {
                        reasons += "B8_POWER_JUMP_TASK_PROTOCOL_IDENTITY_REQUIRED"
                    } else {
                        authorized += owner
                        taskProtocolIdentities += StimulusTaskProtocolAuthorityIdentity(
                            protocolId = definition.protocolId,
                            stableKey = owner.stableKey,
                            selectionRole = owner.selectionRole,
                            authorizedTasks = metadata.authorization.attributedTasks
                        )
                    }
                } else if (targetId != null && targetId in exactMovementTargetIds) {
                    authorized += owner
                    authorizedMovementTargetIdentities += StimulusMovementTargetOwnerIdentity(
                        targetId, owner.stableKey, owner.selectionRole
                    )
                }
            }
        }
        reasons += powerJumpScheduleReasons(comparison, powerJumpOwners)
        if (powerJumpOwners.isEmpty()) reasons += "B8_POWER_JUMP_EMPTY_MATERIAL_AUTHORITY"
        if (unrelatedControlParityFailures(comparison, authorized)) reasons += "B8_POWER_JUMP_UNRELATED_CONTROL_MUTATION"
        val normalized = reasons.toList().distinct().sorted()
        return if (normalized.isEmpty()) StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            scope = scope,
            authorizedOwnerIdentities = authorized.toList().sortedWith(OWNER_ORDER),
            authorizedAuthorityIdentities = authorityIdentities.toList().sortedWith(AUTHORITY_ORDER),
            authorizedTaskProtocolIdentities = taskProtocolIdentities.toList().sortedWith(TASK_PROTOCOL_AUTHORITY_ORDER),
            authorizedMovementTargetOwnerIdentities = authorizedMovementTargetIdentities.toList()
                .sortedWith(compareBy({ it.targetId }, { it.stableKey }, { it.selectionRole })),
            reasonCodes = listOf("B8_POWER_JUMP_V1_AUTHORIZED"),
            b7Status = b7.status
        ) else StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
            scope = scope,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = normalized,
            b7Status = b7.status
        )
    }

    private fun inconclusivePowerJump(
        comparison: StimulusSelectionProgramComparison,
        reason: String
    ) = StimulusProductionCutoverAuthorityDecision(
        status = StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE,
        scope = StimulusProductionCutoverScope.POWER_JUMP_V1,
        authorizedOwnerIdentities = emptyList(),
        reasonCodes = listOf(reason),
        b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.INCONCLUSIVE
    )

    private fun validatePowerJumpOwner(
        comparison: StimulusSelectionProgramComparison,
        owner: StimulusPrescriptionOwnerIdentity,
        attributions: List<StimulusExperimentalChangeAttribution>
    ): List<String> {
        val reasons = linkedSetOf<String>()
        val targetId = attributions.singleOrNull()?.targetIds?.singleOrNull()
        val quality = when (targetId) {
            "QUALITY:POWER" -> TrainableQuality.POWER
            "QUALITY:REACTIVE_STRENGTH_SSC" -> TrainableQuality.REACTIVE_STRENGTH_SSC
            else -> null
        }
        if (quality == null || attributions.size != 1) {
            reasons += "B8_POWER_JUMP_EXACT_OWNER_TARGET_ATTRIBUTION_REQUIRED"
            return reasons.toList()
        }
        val target = comparison.targetPlan.qualityTargets.singleOrNull { it.quality == quality }
        val dose = comparison.targetPlan.powerJumpDoseDecisions.singleOrNull { it.targetId == targetId }
        if (target == null || target.needDecision != TrainingNeedDecision.DEVELOP ||
            target.strategy == StimulusDoseStrategy.UNRESOLVED || target.numericAuthority != StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ||
            targetId in comparison.targetPlan.unresolved || target.requiredPhysicalModes.isNullOrEmpty() ||
            dose?.status != PowerJumpDoseStatus.AUTHORIZED || dose.approvedWeeklySetUnits !in 2..4 ||
            dose.targetRegion == null ||
            target.weeklyDirectUnitsTarget?.preferred != dose.approvedWeeklySetUnits.toDouble() ||
            "B3_DEVELOP_NEED_CONFIRMED" !in dose.reasonCodes ||
            dose.provenance != PowerJumpIntegratedDosePolicy.PROVENANCE) {
            reasons += "B8_POWER_JUMP_EXACT_B3_B4_AUTHORITY_REQUIRED"
        }
        val candidate = comparison.selectionPlan.selectedCandidates.singleOrNull {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        }
        if (candidate == null || candidate.primaryTargetId != targetId || targetId !in candidate.coveredTargetIds ||
            comparison.selectionPlan.traces.none { trace -> trace.targetId == targetId &&
                trace.selectedStableKey == owner.stableKey && trace.selectedSelectionRole == owner.selectionRole } ||
            comparison.materializationTraces.none { trace -> trace.targetId == targetId && trace.selectedAtB5 &&
                trace.directIdentityVerifiedAtSelection == true && trace.presentInFinalExperimentalSkeleton &&
                trace.directIdentityStillValid && trace.selectedStableKey == owner.stableKey && trace.selectionRole == owner.selectionRole }) {
            reasons += "B8_POWER_JUMP_EXACT_B5_OWNER_REQUIRED"
        }
        val authorizations = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { auth ->
            auth.targetId == targetId && auth.quality == quality && auth.owner?.let {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == owner
            } == true
        }
        val auth = authorizations.singleOrNull()
        val prescription = auth?.authorizedPrescription
        val calibration = auth?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        if (auth == null || prescription == null || auth.source != StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE ||
            auth.status !in setOf(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_USER_APPROVED_POWER_JUMP_POLICY,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION) ||
            "B3_NEED_PRESERVED" !in auth.reasonCodes ||
            "B4_EXACT_POWER_JUMP_SET_UNITS=${dose?.approvedWeeklySetUnits}" !in auth.reasonCodes ||
            "B5_EXACT_OWNER=${owner.stableKey}#${owner.selectionRole}" !in auth.reasonCodes ||
            prescription.sets.size != dose?.approvedWeeklySetUnits || prescription.sets.any {
                it.reps !in 3..6 || it.seconds != 0 || it.targetRpeMin != null || when (it.loadState) {
                    com.training.trackplanner.data.ProgramLoadState.NOT_APPLICABLE -> it.weightKg != 0.0
                    com.training.trackplanner.data.ProgramLoadState.EXPLICIT_LOAD -> !it.weightKg.isFinite() || it.weightKg <= 0.0
                    com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED -> it.weightKg != 0.0
                    else -> true
                }
            } || (calibration && (auth.executionAuthority != StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT ||
                prescription.sets.any { it.loadState != com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED } ||
                auth.authorityRecovery?.status != ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED ||
                auth.authorityRecovery.returnTarget != ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT)) ||
            (!calibration && (auth.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED ||
                prescription.sets.any { it.loadState !in setOf(com.training.trackplanner.data.ProgramLoadState.NOT_APPLICABLE,
                    com.training.trackplanner.data.ProgramLoadState.EXPLICIT_LOAD) }))) {
            reasons += "B8_POWER_JUMP_EXACT_B6_AUTHORITY_REQUIRED"
        }
        val b6Attribution = attributions.singleOrNull()
        val expectedSource = if (calibration) StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION
            else StimulusExperimentalChangeAttributionSource.B6_APPROVED_POWER_JUMP_POLICY
        if (b6Attribution == null || b6Attribution.targetIds != listOf(targetId) ||
            b6Attribution.source != expectedSource ||
            "B4_EXACT_POWER_JUMP_SET_UNITS=${dose?.approvedWeeklySetUnits}" !in b6Attribution.reasonCodes ||
            "B5_EXACT_OWNER=${owner.stableKey}#${owner.selectionRole}" !in b6Attribution.reasonCodes) {
            reasons += "B8_POWER_JUMP_B6_PROVENANCE_REQUIRED"
        }
        val rows = comparison.experimental.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
        if (prescription == null || rows.isEmpty() || !validateAuthorizedWeeklySubset(rows, prescription, owner.stableKey, owner.selectionRole).valid) {
            reasons += "B8_POWER_JUMP_MATERIALIZED_ROWS_DO_NOT_MATCH_B6"
        }
        if (rows.groupBy { it.weekNumber to it.dayOfWeek }.values.any { sessionRows ->
                sessionRows.sumOf { it.setCount } !in 2..4
            }) reasons += "B8_POWER_JUMP_SESSION_SET_COUNT_OUTSIDE_2_TO_4"
        val audits = comparison.prescriptionMaterializationAudits.filter { it.targetId == targetId && it.quality == quality &&
            it.owner?.let { row -> StimulusPrescriptionOwnerIdentity(row.stableKey, row.selectionRole) == owner } == true }
        val audit = audits.singleOrNull()
        val weeks = comparison.experimental.request.durationWeeks.coerceAtLeast(1)
        if (audit == null || audit.state != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ||
            audit.executionAuthority != (if (calibration) StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT else StimulusPrescriptionExecutionAuthority.FULLY_ENCODED) ||
            audit.authorizedWeeklySetUnits != dose?.approvedWeeklySetUnits || audit.materializedWeeklySetUnits != dose?.approvedWeeklySetUnits ||
            audit.targetCompatibleMaterializedUnits != dose?.approvedWeeklySetUnits || audit.shortfall != 0 || audit.overrun != 0 ||
            audit.weeklyAudits.size != weeks || audit.weeklyAudits.any { it.authorizedSetUnits != dose?.approvedWeeklySetUnits ||
                it.materializedSetUnits != dose?.approvedWeeklySetUnits || it.targetCompatibleMaterializedUnits != dose?.approvedWeeklySetUnits ||
                it.shortfall != 0 || it.overrun != 0 || !it.prescriptionPreservedOrSubset }) {
            reasons += "B8_POWER_JUMP_FULL_HORIZON_MATERIALIZATION_REQUIRED"
        }
        val outcome = comparison.experimentalReadinessAudit?.targetOutcomes?.singleOrNull { it.targetId == targetId }
        if (outcome == null || !outcome.directlyAffected || outcome.status !in setOf(
                StimulusExperimentalTargetOutcomeStatus.IMPROVED, StimulusExperimentalTargetOutcomeStatus.UNCHANGED)) {
            reasons += "B8_POWER_JUMP_B7_TARGET_OUTCOME_REQUIRED"
        }
        return reasons.toList()
    }

    /** A non-P/J movement row may share this cutover only with its own exact B4/B5/B6 lineage. */
    private fun validateMovementTargetOwner(
        comparison: StimulusSelectionProgramComparison,
        owner: StimulusPrescriptionOwnerIdentity,
        targetId: String,
        attributions: List<StimulusExperimentalChangeAttribution>
    ): List<String> {
        val reasons = linkedSetOf<String>()
        if (attributions.size != 1 || attributions.singleOrNull()?.source !=
            StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY ||
            attributions.singleOrNull()?.targetIds != listOf(targetId) ||
            !StimulusProductionMovementScopeEvidence.hasExactExecutableB6(comparison, targetId, owner)) {
            reasons += "B8_POWER_JUMP_MOVEMENT_TARGET_EXACT_B4_B5_B6_REQUIRED"
        }
        val target = comparison.targetPlan.movementTargets.singleOrNull { it.targetId == targetId }
        val outcome = comparison.experimentalReadinessAudit?.targetOutcomes?.singleOrNull { it.targetId == targetId }
        if (target == null || outcome == null || !outcome.directlyAffected || outcome.status !in setOf(
                StimulusExperimentalTargetOutcomeStatus.IMPROVED, StimulusExperimentalTargetOutcomeStatus.UNCHANGED
            )) reasons += "B8_POWER_JUMP_MOVEMENT_TARGET_OUTCOME_REQUIRED"
        val movementB6 = comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty().singleOrNull {
            it.targetId == targetId && it.owner == owner && it.status in setOf(
                StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6,
                StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6
            )
        }
        val dose = target?.regionalDoseTargets?.singleOrNull { it.kind in setOf(
            StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
            StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET
        ) }
        val units = dose?.authorizedWholeSetUnits
        val quality = when (dose?.kind) {
            StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET -> TrainableQuality.HYPERTROPHY
            StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET -> null
            null -> null
        }
        val authorization = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().singleOrNull { auth ->
            auth.targetId == targetId && auth.quality == quality && auth.owner?.let {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == owner
            } == true
        }
        val prescription = if (quality == null) {
            comparison.prescriptionAuthorizationPlan?.movementOwnerPrescriptions?.get(owner)
        } else authorization?.authorizedPrescription
        val expectedMovementStatus = when (dose?.kind) {
            StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET -> StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6
            StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET -> StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6
            null -> null
        }
        if (dose == null || units == null || units <= 0 || dose.weeklyTarget == null ||
            dose.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY ||
            dose.residualEquivalentExposure?.let { it > 0.0 && it.isFinite() } != true ||
            movementB6?.status != expectedMovementStatus || authorization?.authorizedPrescription != prescription ||
            prescription == null || prescription.sets.size != units ||
            authorization?.status !in setOf(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION)) {
            reasons += "B8_POWER_JUMP_MOVEMENT_TARGET_EXACT_B6_AUTHORITY_REQUIRED"
        }
        val rows = comparison.experimental.items.filter {
            it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        }
        val expectedWeeks = (1..comparison.experimental.request.durationWeeks.coerceAtLeast(1)).toSet()
        val rowsByWeek = rows.groupBy { it.weekNumber }
        if (prescription == null || rowsByWeek.keys != expectedWeeks || expectedWeeks.any { week ->
                val weekRows = rowsByWeek[week].orEmpty()
                weekRows.sumOf { it.setPrescriptions.size } != units ||
                    !validateAuthorizedWeeklySubset(weekRows, prescription, owner.stableKey, owner.selectionRole).valid
            }) reasons += "B8_POWER_JUMP_MOVEMENT_MATERIALIZATION_MISMATCH"
        val audits = comparison.prescriptionMaterializationAudits.filter { audit ->
            audit.targetId == targetId && audit.quality == quality && audit.owner?.let {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == owner
            } == true
        }
        val audit = audits.singleOrNull()
        if (audit == null || audit.state != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ||
            audit.authorizedWeeklySetUnits != units || audit.targetCompatibleMaterializedUnits != units ||
            audit.shortfall != 0 || audit.overrun != 0 || audit.weeklyAudits.size != expectedWeeks.size ||
            audit.weeklyAudits.any { it.authorizedSetUnits != units || it.materializedSetUnits != units ||
                it.targetCompatibleMaterializedUnits != units || it.shortfall != 0 || it.overrun != 0 ||
                !it.prescriptionPreservedOrSubset }) {
            reasons += "B8_POWER_JUMP_MOVEMENT_FULL_HORIZON_MATERIALIZATION_REQUIRED"
        }
        return reasons.toList()
    }

    /** Caps new Power/Jump exposure at two training days for every overlapping B4 region. */
    private fun powerJumpScheduleReasons(
        comparison: StimulusSelectionProgramComparison,
        newOwners: Set<StimulusPrescriptionOwnerIdentity>
    ): Set<String> {
        data class ExposureDay(val region: PowerJumpBodyRegion, val week: Int, val day: Int, val isNew: Boolean)
        val decisions = comparison.targetPlan.powerJumpDoseDecisions.associateBy { it.targetId }
        val exposures = mutableListOf<ExposureDay>()
        val reasons = linkedSetOf<String>()
        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().forEach { authorization ->
            val quality = authorization.quality
            if (quality !in setOf(TrainableQuality.POWER, TrainableQuality.REACTIVE_STRENGTH_SSC)) return@forEach
            val owner = authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                ?: return@forEach
            val dose = decisions[authorization.targetId] ?: return@forEach
            val region = dose.targetRegion ?: return@forEach
            if (authorization.status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_USER_APPROVED_POWER_JUMP_POLICY,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                )) return@forEach
            comparison.experimental.items.filter {
                it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
            }.map { it.weekNumber to it.dayOfWeek }.distinct().forEach { (week, day) ->
                exposures += ExposureDay(region, week, day, owner in newOwners)
            }
        }
        val regions = PowerJumpBodyRegion.entries
        regions.forEach { region ->
            exposures.filter { event ->
                event.region == PowerJumpBodyRegion.WHOLE_BODY || region == PowerJumpBodyRegion.WHOLE_BODY || event.region == region
            }.groupBy(ExposureDay::week).forEach { (_, weekEvents) ->
                val days = weekEvents.mapTo(sortedSetOf()) { it.day }
                if (days.size > 2 && weekEvents.any(ExposureDay::isNew)) {
                    reasons += "B8_POWER_JUMP_MORE_THAN_TWO_EXPOSURE_DAYS_FOR_REGION_${region.name}"
                }
            }
        }
        return reasons
    }

    /** Task-only cutover consumes exact C24 row grants and never routes task work through a quality B6. */
    private fun auditBadmintonTask(
        comparison: StimulusSelectionProgramComparison
    ): StimulusProductionCutoverAuthorityDecision {
        if (StimulusProductionMaterialScopeResolver().resolve(comparison) != StimulusProductionCutoverScope.BADMINTON_TASK_V1) {
            return taskControl(comparison, "B8_BADMINTON_TASK_MATERIAL_SCOPE_MISMATCH")
        }
        val b7 = comparison.experimentalReadinessAudit
            ?: return taskControl(comparison, "B8_BADMINTON_TASK_B7_AUDIT_MISSING")
        when (b7.status) {
            StimulusExperimentalReadinessStatus.NOT_ELIGIBLE ->
                return taskControl(comparison, "B8_BADMINTON_TASK_B7_NOT_ELIGIBLE")
            StimulusExperimentalReadinessStatus.INCONCLUSIVE ->
                return taskInconclusive(comparison, "B8_BADMINTON_TASK_B7_INCONCLUSIVE")
            StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE ->
                return StimulusProductionCutoverAuthorityDecision(
                    status = StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE,
                    scope = StimulusProductionCutoverScope.BADMINTON_TASK_V1,
                    authorizedOwnerIdentities = emptyList(), reasonCodes = listOf("B8_NO_MATERIAL_CHANGE"), b7Status = b7.status
                )
            StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW -> Unit
        }

        val reasons = linkedSetOf<String>()
        if (!b7.changeProvenanceClosed || b7.changeAttributions.any {
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
                    it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
            }) reasons += "B8_BADMINTON_TASK_PROVENANCE_NOT_CLOSED"
        if (!b7.materializationIntegrityPassed) reasons += "B8_BADMINTON_TASK_MATERIALIZATION_NOT_INTEGRAL"
        if (!b7.collateralRegressionFree || b7.targetOutcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }) {
            reasons += "B8_BADMINTON_TASK_COLLATERAL_OR_TARGET_REGRESSION"
        }
        val exactTaskReplacements = StimulusProductionMovementScopeEvidence.exactUserApprovedReplacementControlOwners(comparison)
        if ((comparison.removedOwnerIdentities - exactTaskReplacements).isNotEmpty()) reasons += "B8_BADMINTON_TASK_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) {
            reasons += "B8_BADMINTON_TASK_WEEKDAY_SCHEDULE_CHANGED"
        }
        if (comparison.control.request != comparison.experimental.request ||
            comparison.control.durationDays != comparison.experimental.durationDays ||
            comparison.control.periodizationType != comparison.experimental.periodizationType
        ) reasons += "B8_BADMINTON_TASK_PROGRAM_CONTRACT_CHANGED"
        if (ProgramProjectionValidator().errors(comparison.experimental).isNotEmpty() ||
            comparison.experimental.items.any { item ->
                item.dayOfWeek !in comparison.experimental.weekDaySchedule[item.weekNumber].orEmpty()
            }) reasons += "B8_BADMINTON_TASK_HARD_PROJECTION_INVALID"

        val materialOwners = materialOwnerIdentities(comparison)
        if (materialOwners.isEmpty()) reasons += "B8_BADMINTON_TASK_EMPTY_MATERIAL_SCOPE"
        val attributedMaterial = b7.changeAttributions.filter { attribution ->
            attribution.stableKey != null && attribution.selectionRole != null &&
                StimulusPrescriptionOwnerIdentity(attribution.stableKey, attribution.selectionRole) in materialOwners &&
                attribution.source in TASK_MATERIAL_ATTRIBUTION_SOURCES
        }
        val attributedOwners = attributedMaterial.map {
            StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole))
        }.toSet()
        if (attributedOwners != materialOwners) reasons += "B8_BADMINTON_TASK_MATERIAL_PROVENANCE_INCOMPLETE"
        if (attributedMaterial.any { attribution ->
                attribution.source != StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL ||
                    attribution.targetIds.isEmpty() || attribution.targetIds.any { !it.startsWith("TASK:") }
            }) reasons += "B8_BADMINTON_TASK_FOREIGN_OR_UNAUTHORIZED_MATERIAL"
        if (b7.changeAttributions.any { attribution ->
                attribution.stableKey == null || attribution.selectionRole == null ||
                    (attribution.stableKey to attribution.selectionRole).let { keyRole ->
                        StimulusPrescriptionOwnerIdentity(keyRole.first, keyRole.second) in materialOwners
                    } && attribution.source !in TASK_MATERIAL_ATTRIBUTION_SOURCES
            }) reasons += "B8_BADMINTON_TASK_PROVENANCE_NOT_CLOSED"

        val taskTargets = comparison.targetPlan.taskTargets.mapNotNull { target ->
            runCatching { CanonicalTaskTarget.valueOf(target.task) }.getOrNull()
        }.toSet()
        if (taskTargets.size != comparison.targetPlan.taskTargets.size) reasons += "B8_BADMINTON_TASK_UNKNOWN_TARGET"
        if (comparison.targetPlan.unresolved.any { it == "TASK:JUMP_LANDING" } || comparison.targetPlan.taskTargets.any {
                it.task == CanonicalTaskTarget.JUMP_LANDING.name &&
                    (it.strategy == StimulusDoseStrategy.UNRESOLVED || "TASK:JUMP_LANDING" in comparison.targetPlan.unresolved)
            }) reasons += "B8_BADMINTON_TASK_UNRESOLVED_JUMP_LANDING_PRESENT"
        if (comparison.targetPlan.qualityTargets.isNotEmpty() && materialOwners.any { owner ->
                b7.changeAttributions.any { a -> a.stableKey == owner.stableKey && a.selectionRole == owner.selectionRole &&
                    a.targetIds.any { it.startsWith("QUALITY:") } }
            }) reasons += "B8_BADMINTON_TASK_FOREIGN_QUALITY_MATERIAL"

        val protocols = mutableListOf<StimulusTaskProtocolAuthorityIdentity>()
        materialOwners.sortedWith(OWNER_ORDER).forEach { owner ->
            val ownerReasons = validateTaskOwner(comparison, owner, taskTargets)
            reasons += ownerReasons
            if (ownerReasons.isEmpty()) {
                val metadata = taskProtocolMetadataForOwner(comparison.experimental, owner).first()
                protocols += StimulusTaskProtocolAuthorityIdentity(
                    protocolId = metadata.authorization.definition.protocolId,
                    stableKey = owner.stableKey,
                    selectionRole = owner.selectionRole,
                    authorizedTasks = metadata.authorization.attributedTasks
                )
            }
        }
        val normalized = reasons.toList().distinct().sorted()
        return if (normalized.isEmpty() && protocols.isNotEmpty()) {
            StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                scope = StimulusProductionCutoverScope.BADMINTON_TASK_V1,
                authorizedOwnerIdentities = materialOwners.sortedWith(OWNER_ORDER),
                authorizedTaskProtocolIdentities = protocols.distinct().sortedWith(TASK_PROTOCOL_AUTHORITY_ORDER),
                reasonCodes = listOf("B8_BADMINTON_TASK_V1_AUTHORIZED"),
                b7Status = b7.status
            )
        } else {
            taskControl(comparison, normalized.ifEmpty { listOf("B8_BADMINTON_TASK_NO_EXACT_AUTHORITY") })
        }
    }

    private fun validateTaskOwner(
        comparison: StimulusSelectionProgramComparison,
        owner: StimulusPrescriptionOwnerIdentity,
        currentB4Tasks: Set<CanonicalTaskTarget>
    ): List<String> {
        val reasons = linkedSetOf<String>()
        val rows = comparison.experimental.items.filter {
            it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        }
        val decoded = rows.mapNotNull { row ->
            val metadata = row.taskProtocolSemanticsJson?.let { raw ->
                runCatching { TaskProtocolExposureMetadata.fromJsonString(raw) }.getOrNull()
            }
            metadata?.let { row to it }
        }
        if (rows.isEmpty() || decoded.size != rows.size) {
            reasons += "B8_BADMINTON_TASK_B6_AUTHORITY_MISSING_OR_INVALID"
            return reasons.toList()
        }
        val definitions = decoded.map { it.second.authorization.definition }.distinct()
        if (definitions.size != 1) reasons += "B8_BADMINTON_TASK_PROTOCOL_IDENTITY_CONFLICT"
        val definition = definitions.singleOrNull()
        if (decoded.map { it.second.authorization.attributedTasks }.distinct().size != 1) {
            reasons += "B8_BADMINTON_TASK_TARGET_ATTRIBUTION_CONFLICT"
        }
        if (definition == null || ApprovedBadmintonTaskProtocols.exact(
                owner.stableKey, owner.selectionRole, definition.primaryTask
            ) != definition || definition.provenance != TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY
        ) reasons += "B8_BADMINTON_TASK_EXACT_APPROVED_PROTOCOL_REQUIRED"

        val candidate = comparison.selectionPlan.selectedCandidates.singleOrNull {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        }
        val primaryTargetId = definition?.let { "TASK:${it.primaryTask.name}" }
        if (candidate == null || candidate.primaryTargetId != primaryTargetId || primaryTargetId !in candidate.coveredTargetIds ||
            comparison.selectionPlan.traces.none {
                it.targetId == primaryTargetId && it.selectedStableKey == owner.stableKey &&
                    it.selectedSelectionRole == owner.selectionRole
            }) reasons += "B8_BADMINTON_TASK_EXACT_B5_OWNER_REQUIRED"
        val primaryB4 = definition?.let { protocol -> comparison.targetPlan.taskTargets.singleOrNull { it.task == protocol.primaryTask.name } }
        if (primaryB4 == null || primaryB4.strategy == StimulusDoseStrategy.UNRESOLVED ||
            primaryB4.numericAuthority != StimulusTargetNumericAuthority.DIRECTION_ONLY ||
            primaryTargetId in comparison.targetPlan.unresolved
        ) reasons += "B8_BADMINTON_TASK_EXACT_B4_TARGET_REQUIRED"

        val materialAttributions = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().filter {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole &&
                it.source in TASK_MATERIAL_ATTRIBUTION_SOURCES
        }
        val attributedTargets = materialAttributions.flatMap { it.targetIds }.toSet()
        val metadataTasks = decoded.flatMap { it.second.authorization.attributedTasks }.toSet()
        if (materialAttributions.none { it.source == StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL } ||
            materialAttributions.any { it.source != StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL } ||
            attributedTargets != metadataTasks.map { "TASK:${it.name}" }.toSet()
        ) reasons += "B8_BADMINTON_TASK_EXACT_B6_ATTRIBUTION_REQUIRED"
        if (metadataTasks.isEmpty() || !metadataTasks.all { it in currentB4Tasks } ||
            decoded.any { (row, metadata) ->
                metadata.authorization.attributedTasks.any { task ->
                    metadata.authorization.transferEvidence[task] != com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel.DIRECT
                } || metadata.authorization.status != TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL ||
                    !metadata.matchesMaterializedItem(
                        item = row,
                        actualActivityKind = metadata.authorization.materializationActivityKind,
                        currentB4Tasks = currentB4Tasks,
                        exactDirectTasks = metadata.authorization.attributedTasks,
                        selectedPrimaryTargetId = primaryTargetId
                    )
            }) reasons += "B8_BADMINTON_TASK_DIRECT_RELATION_OR_MATERIALIZATION_INVALID"

        val expectedFrequency = definition?.weeklyExposures
        val outcomes = comparison.experimental.taskProtocolFrequencyOutcomes.filter {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole && it.protocolId == definition?.protocolId
        }
        val weeks = comparison.experimental.request.durationWeeks.coerceAtLeast(1)
        if (expectedFrequency == null || outcomes.size != weeks ||
            outcomes.map { it.week }.toSet() != (1..weeks).toSet() || outcomes.any {
                it.status != TaskProtocolFrequencyStatus.SATISFIED || it.requestedExposures != expectedFrequency ||
                    it.placedExposures != expectedFrequency || it.shortfall != 0 ||
                    it.authority != TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY
            }) reasons += "B8_BADMINTON_TASK_FREQUENCY_OR_PLACEMENT_SHORTFALL"

        val rowsByWeek = decoded.groupBy { it.first.weekNumber }
        if (rowsByWeek.keys != (1..weeks).toSet() || rowsByWeek.any { (_, weekRows) ->
                val entries = weekRows.map { it.first to it.second.exposureIndex }
                entries.size != expectedFrequency || entries.map { it.second }.toSet().size != entries.size ||
                    entries.map { it.first.dayOfWeek }.toSet().size != entries.size ||
                    entries.any { (row, _) -> row.dayOfWeek !in comparison.experimental.weekDaySchedule[row.weekNumber].orEmpty() }
            }) reasons += "B8_BADMINTON_TASK_FREQUENCY_OR_PLACEMENT_SHORTFALL"
        return reasons.toList()
    }

    private fun taskControl(
        comparison: StimulusSelectionProgramComparison,
        vararg reasonCodes: String
    ) = taskControl(comparison, reasonCodes.toList())

    private fun taskControl(
        comparison: StimulusSelectionProgramComparison,
        reasonCodes: List<String>
    ) = StimulusProductionCutoverAuthorityDecision(
        status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
        scope = StimulusProductionCutoverScope.BADMINTON_TASK_V1,
        authorizedOwnerIdentities = emptyList(),
        reasonCodes = reasonCodes.distinct().sorted(),
        b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
    )

    private fun taskInconclusive(
        comparison: StimulusSelectionProgramComparison,
        reason: String
    ) = StimulusProductionCutoverAuthorityDecision(
        status = StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE,
        scope = StimulusProductionCutoverScope.BADMINTON_TASK_V1,
        authorizedOwnerIdentities = emptyList(), reasonCodes = listOf(reason),
        b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.INCONCLUSIVE
    )

    private fun auditCombined(
        comparison: StimulusSelectionProgramComparison
    ): StimulusProductionCutoverAuthorityDecision {
        val policy = scopePolicy(StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1)
        val b7 = comparison.experimentalReadinessAudit
            ?: return control(comparison, policy, "B8_B7_AUDIT_MISSING")
        when (b7.status) {
            StimulusExperimentalReadinessStatus.NOT_ELIGIBLE ->
                return control(comparison, policy, "B8_B7_NOT_ELIGIBLE")
            StimulusExperimentalReadinessStatus.INCONCLUSIVE ->
                return inconclusive(comparison, policy, "B8_B7_INCONCLUSIVE")
            StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE ->
                return noMaterialChange(policy, b7.status)
            StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW -> Unit
        }
        val reasons = linkedSetOf<String>()
        if (!b7.changeProvenanceClosed) reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        if (!b7.collateralRegressionFree) reasons += "B8_CUTOVER_V1_COLLATERAL_REGRESSION"
        if (b7.changeAttributions.any {
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
                    it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
            }) reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        if (b7.targetOutcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }) {
            reasons += "B8_CUTOVER_V1_COLLATERAL_REGRESSION"
        }
        val materialOwners = materialOwnerIdentities(comparison)
        val roleReplacements = canonicalRoleReplacementControlOwners(comparison, materialOwners) +
            StimulusProductionMovementScopeEvidence.exactUserApprovedReplacementControlOwners(comparison)
        if ((comparison.removedOwnerIdentities - roleReplacements).isNotEmpty()) reasons += "B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) reasons += "B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED"

        val allowedTargets = setOf("QUALITY:STRENGTH", "QUALITY:HYPERTROPHY")
        val requiredQualities = linkedMapOf<StimulusPrescriptionOwnerIdentity, Set<TrainableQuality>>()
        materialOwners.forEach { identity ->
            val targetIds = materialAttributionsFor(comparison, identity)
                .filter(::isMaterialAttribution)
                .flatMap { it.targetIds }
                .toSet()
            if (targetIds.any { it !in allowedTargets }) {
                reasons += "B8_STRENGTH_HYPERTROPHY_V1_OTHER_QUALITY_CHANGE_OUT_OF_SCOPE"
            }
            val qualities = targetIds.mapNotNull { targetId ->
                when (targetId) {
                    "QUALITY:STRENGTH" -> TrainableQuality.STRENGTH
                    "QUALITY:HYPERTROPHY" -> TrainableQuality.HYPERTROPHY
                    else -> null
                }
            }.toSet()
            requiredQualities[identity] = qualities
            if (qualities.isEmpty()) reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        }
        val materialQualitySet = requiredQualities.values.flatten().toSet()
        if (TrainableQuality.STRENGTH !in materialQualitySet || TrainableQuality.HYPERTROPHY !in materialQualitySet) {
            reasons += "B8_STRENGTH_HYPERTROPHY_V1_REQUIRES_BOTH_QUALITIES_MATERIAL"
        }
        reasons += b6IntegrityReasons(comparison, requiredQualities)
        if (materialOwners.isEmpty()) {
            reasons += "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"
            reasons += "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }

        val authorized = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
        val authorityIdentities = linkedSetOf<StimulusPrescriptionAuthorityIdentity>()
        materialOwners.sortedWith(OWNER_ORDER).forEach { identity ->
            val qualities = requiredQualities[identity].orEmpty()
            val ownerReasons = linkedSetOf<String>()
            qualities.sortedBy { it.name }.forEach { quality ->
                val qualityPolicy = qualityPolicy(quality)
                val targetId = "QUALITY:${quality.name}"
                targetAuthorityReason(comparison, targetId, qualityPolicy)?.let(ownerReasons::add)
                val validation = when {
                    identity in comparison.addedOwnerIdentities -> validateAddedOwner(comparison, identity, qualityPolicy)
                    identity in comparison.sharedOwnerIdentities -> validateSharedOwner(comparison, identity, qualityPolicy, allowedTargets)
                    else -> listOf("B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY")
                }
                ownerReasons += validation
                if (validation.isEmpty()) {
                    authorityIdentities += StimulusPrescriptionAuthorityIdentity(identity.stableKey, identity.selectionRole, quality)
                }
            }
            reasons += ownerReasons
            if (ownerReasons.isEmpty() && qualities.isNotEmpty()) authorized += identity
        }
        if (authorized.isEmpty()) reasons += "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"
        if (unrelatedControlParityFailures(comparison, authorized)) reasons += "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION"

        val normalizedReasons = reasons.toList().sorted()
        return if (normalizedReasons.isEmpty() && authorized.isNotEmpty()) {
            StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                scope = StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1,
                authorizedOwnerIdentities = authorized.toList().sortedWith(OWNER_ORDER),
                authorizedAuthorityIdentities = authorityIdentities.toList().sortedWith(AUTHORITY_ORDER),
                reasonCodes = listOf("B8_STRENGTH_HYPERTROPHY_V1_AUTHORIZED"),
                b7Status = b7.status
            )
        } else {
            control(comparison, policy, normalizedReasons)
        }
    }

    private fun validateAddedOwner(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        policy: CutoverScopePolicy
    ): List<String> {
        val reasons = linkedSetOf<String>()
        val candidate = comparison.selectionPlan.selectedCandidates.firstOrNull {
            it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }
        if (candidate == null) reasons += policy.addedOwnerB5Reason

        val candidateTargets = candidate?.coveredTargetIds.orEmpty()
        val auth = exactAuthorization(comparison, identity, policy)
        val authorizationTargetId = auth?.targetId
        val selectedTraceTargets = comparison.selectionPlan.traces
            .filter { trace -> trace.selectedStableKey == identity.stableKey && trace.selectedSelectionRole == identity.selectionRole }
            .map { it.targetId }
        val canonicalQualityTarget = authorizationTargetId == policy.targetId && comparison.targetPlan.qualityTargets.any {
            "QUALITY:${it.quality.name}" == policy.targetId && it.quality == policy.quality
        }
        val regionalTarget = authorizationTargetId?.let { isRegionalHypertrophyTarget(comparison, it, policy) } == true
        if ((!canonicalQualityTarget && !regionalTarget) || authorizationTargetId !in candidateTargets ||
            authorizationTargetId !in selectedTraceTargets) {
            reasons += policy.addedOwnerB5Reason
        }
        targetAuthorityReason(comparison, authorizationTargetId, policy)?.let(reasons::add)

        val attribution = materialAttributionsFor(comparison, identity)
            .firstOrNull { it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY }
        if (attribution == null || authorizationTargetId !in attribution.targetIds) {
            reasons += policy.addedOwnerB5Reason
        }

        if (!isExecutableAuthorization(comparison, auth, policy)) {
            reasons += policy.prescriptionAuthorityReason
        }
        executionAuthorityReason(comparison, auth, policy)?.let(reasons::add)
        targetAuthorityReason(comparison, auth?.targetId, policy)?.let(reasons::add)
        if (!fullMaterialization(comparison, identity, policy)) {
            reasons += policy.materializationReason
        }
        return reasons.toList()
    }

    private fun validateSharedOwner(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        policy: CutoverScopePolicy,
        allowedMaterialTargetIds: Set<String> = setOf(policy.targetId)
    ): List<String> {
        val before = ownerRows(comparison.control, identity)
        val after = ownerRows(comparison.experimental, identity)
        if (before == after) return emptyList()
        val reasons = linkedSetOf<String>()
        if (placementSignature(before) != placementSignature(after)) {
            reasons += "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION"
        }
        val auth = exactAuthorization(comparison, identity, policy)
        if (!isExecutableAuthorization(comparison, auth, policy)) {
            reasons += policy.prescriptionAuthorityReason
        }
        executionAuthorityReason(comparison, auth, policy)?.let(reasons::add)
        if (policy.scope == StimulusProductionCutoverScope.STRENGTH_V1 &&
            auth?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE) {
            reasons += "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        val attributions = materialAttributionsFor(comparison, identity)
        if (attributions.none {
                it.source == StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION ||
                    it.source == StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION ||
                    it.source == StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION
            }) {
            reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        }
        val allowedTargets = allowedMaterialTargetIds + regionalAllowedTargetIds(comparison, policy)
        if (attributions.filter(::isMaterialAttribution).any { attribution ->
            attribution.targetIds.any { it !in allowedTargets }
        }) {
            reasons += policy.nonQualityChangeReason
        }
        if (!fullMaterialization(comparison, identity, policy)) {
            reasons += policy.materializationReason
        }
        return reasons.toList()
    }

    private fun exactAuthorization(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        policy: CutoverScopePolicy
    ): StimulusPrescriptionAuthorization? {
        val matches = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter {
            val owner = it.owner ?: return@filter false
            owner.stableKey == identity.stableKey && owner.selectionRole == identity.selectionRole &&
                it.quality == policy.quality
        }
        return matches.singleOrNull()
    }

    private fun isExecutableAuthorization(
        comparison: StimulusSelectionProgramComparison,
        authorization: StimulusPrescriptionAuthorization?,
        policy: CutoverScopePolicy
    ): Boolean = authorization != null && authorization.quality == policy.quality &&
            authorization.authorizedPrescription != null && when {
                policy.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 ->
                    validColdStartAuthorization(comparison, authorization)
                policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
                    isRegionalHypertrophyTarget(comparison, authorization.targetId, policy) -> {
                    val prescription = authorization.authorizedPrescription
                    val calibration = authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                        validRegionalHypertrophyCalibration(comparison, authorization)
                    val materialLoad = authorization.status in setOf(
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                    ) && prescription.sets.all { it.weightKg.isFinite() && it.weightKg > 0.0 }
                    (calibration || materialLoad) &&
                        canonicalExecutionAuthority(policy.quality, prescription) == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
                        prescription.sets.isNotEmpty() && prescription.sets.all { it.reps in 7..15 && it.targetRpeMin?.let { rpe -> rpe >= 7.0 } == true }
                }
                else -> authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                ) && (policy.scope != StimulusProductionCutoverScope.HYPERTROPHY_V1 ||
                    canonicalExecutionAuthority(policy.quality, authorization.authorizedPrescription) == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
                    authorization.authorizedPrescription.sets.isNotEmpty() && authorization.authorizedPrescription.sets.all { set ->
                        set.reps in 7..15 && set.weightKg.isFinite() && set.weightKg > 0.0
                    })
            }

    private fun executionAuthorityReason(
        comparison: StimulusSelectionProgramComparison,
        authorization: StimulusPrescriptionAuthorization?,
        policy: CutoverScopePolicy
    ): String? = if (policy.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 &&
        authorization != null && !validColdStartAuthorization(comparison, authorization)
    ) "B8_STRENGTH_CALIBRATION_V1_AUTHORITY_INVALID" else if (
        policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
        authorization != null &&
        canonicalExecutionAuthority(policy.quality, authorization.authorizedPrescription) != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
    ) policy.effortAuthorityReason else null

    private fun targetAuthorityReason(
        comparison: StimulusSelectionProgramComparison,
        targetId: String?,
        policy: CutoverScopePolicy
    ): String? {
        if (policy.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1) {
            val target = comparison.targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == targetId }
            return if (target == null || target.quality != TrainableQuality.STRENGTH ||
                target.strategy == StimulusDoseStrategy.UNRESOLVED || targetId in comparison.targetPlan.unresolved ||
                (target.numericAuthority !in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
                ) && !(target.numericAuthority == StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY &&
                    "USER_APPROVED_PROJECT_POLICY_STRENGTH_COLD_START_4_DIRECT_SETS_PER_SELECTED_ANCHOR_WEEK" in target.reasonCodes))) {
                "B8_STRENGTH_CALIBRATION_V1_REQUIRES_NUMERIC_DOSE"
            } else null
        }
        if (policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1) {
            if (targetId != null && isRegionalHypertrophyTarget(comparison, targetId, policy)) return null
            val target = comparison.targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == targetId }
            return if (target == null || target.quality != policy.quality ||
                target.strategy == StimulusDoseStrategy.UNRESOLVED || targetId in comparison.targetPlan.unresolved ||
                target.numericAuthority !in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
                )) policy.targetNoNumericReason else null
        }
        if (targetId == null) return "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        val target = comparison.targetPlan.qualityTargets.firstOrNull {
            "QUALITY:${it.quality.name}" == targetId
        }
        if (target == null || target.strategy == StimulusDoseStrategy.UNRESOLVED || targetId in comparison.targetPlan.unresolved) {
            return "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        if (target.quality != TrainableQuality.STRENGTH ||
            target.numericAuthority in setOf(
                StimulusTargetNumericAuthority.NONE,
                StimulusTargetNumericAuthority.UNRESOLVED
            )
        ) {
            return policy.targetNoNumericReason
        }
        return null
    }

    private fun isRegionalHypertrophyTarget(
        comparison: StimulusSelectionProgramComparison,
        targetId: String,
        policy: CutoverScopePolicy
    ): Boolean = policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
        comparison.targetPlan.movementTargets.any { movement ->
            movement.targetId == targetId && movement.regionalDoseTargets.any { dose ->
                dose.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                    dose.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                    dose.numericAuthority in setOf(
                        StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                        StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
                        StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY
                    ) && dose.weeklyTarget != null && (dose.authorizedWholeSetUnits ?: 0) > 0 &&
                    dose.existingEquivalentExposure?.let { it.isFinite() && it >= 0.0 } == true &&
                    dose.residualEquivalentExposure?.let { it.isFinite() && it > 0.0 } == true &&
                    (dose.numericAuthority != StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ||
                        dose.evidence.any { it.contains("doseProvenance=${RegionalColdStartDosePolicy.PROVENANCE}") })
            }
        }

    private fun regionalAllowedTargetIds(
        comparison: StimulusSelectionProgramComparison,
        policy: CutoverScopePolicy
    ): Set<String> = if (policy.scope != StimulusProductionCutoverScope.HYPERTROPHY_V1) emptySet() else
        comparison.targetPlan.movementTargets.mapNotNullTo(linkedSetOf()) { movement ->
            movement.targetId.takeIf {
                isRegionalHypertrophyTarget(comparison, it, policy) ||
                    isCoveredByExistingAuthorizedQualityMaterial(comparison, it, policy)
            }
        }

    private fun allowedMaterialTargetIds(
        comparison: StimulusSelectionProgramComparison,
        policy: CutoverScopePolicy
    ): Set<String> = setOf(policy.targetId) + regionalAllowedTargetIds(comparison, policy)

    private fun isCoveredByExistingAuthorizedQualityMaterial(
        comparison: StimulusSelectionProgramComparison,
        targetId: String,
        policy: CutoverScopePolicy
    ): Boolean = policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
        comparison.targetPlan.movementTargets.any { movement ->
            movement.targetId == targetId && movement.regionalDoseTargets.any { dose ->
                dose.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                    dose.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                    dose.weeklyTarget != null && dose.authorizedWholeSetUnits == 0 &&
                    dose.existingEquivalentExposure?.let { it.isFinite() && it + 1e-9 >= dose.weeklyTarget } == true &&
                    comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty().any {
                        it.targetId == targetId && it.status == StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6
                    }
            }
        }

    private fun validRegionalHypertrophyCalibration(
        comparison: StimulusSelectionProgramComparison,
        authorization: StimulusPrescriptionAuthorization
    ): Boolean {
        val prescription = authorization.authorizedPrescription ?: return false
        val dose = comparison.targetPlan.movementTargets.firstOrNull { it.targetId == authorization.targetId }
            ?.regionalDoseTargets?.firstOrNull { it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET }
            ?: return false
        val units = dose.authorizedWholeSetUnits ?: return false
        return authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
            units == prescription.sets.size && authorization.reasonCodes.isNotEmpty() && prescription.sets.isNotEmpty() && prescription.sets.all { set ->
                set.reps in 7..15 && set.targetRpeMin?.let { it >= 7.0 } == true &&
                    set.weightKg == 0.0 && set.loadState == com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED
            }
    }

    private fun fullMaterialization(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        policy: CutoverScopePolicy
    ): Boolean {
        val audits = comparison.prescriptionMaterializationAudits.filter {
            val owner = it.owner ?: return@filter false
            owner.stableKey == identity.stableKey && owner.selectionRole == identity.selectionRole &&
                it.quality == policy.quality
        }
        if (audits.size != 1) return false
        val audit = audits.single()
        if (policy.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1) {
            if (audit.executionAuthority != StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT) return false
            val authorization = exactAuthorization(comparison, identity, policy) ?: return false
            if (!validColdStartAuthorization(comparison, authorization)) return false
            val authorizedSets = authorization.authorizedPrescription?.sets ?: return false
            val rows = comparison.experimental.items.filter {
                it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
            }
            if (rows.isEmpty() || rows.any { row -> row.setPrescriptions != authorizedSets }) return false
        }
        if (policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
            audit.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED) return false
        if (policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
            isRegionalHypertrophyTarget(comparison, audit.targetId, policy)) {
            val dose = comparison.targetPlan.movementTargets.first { it.targetId == audit.targetId }
                .regionalDoseTargets.first { it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET }
            val residualUnits = dose.authorizedWholeSetUnits ?: return false
            if (audit.authorizedWeeklySetUnits != residualUnits || audit.weeklyAudits.any {
                    it.authorizedSetUnits != residualUnits || it.materializedSetUnits != residualUnits ||
                        it.targetCompatibleMaterializedUnits != residualUnits
                }) return false
        }
        val expectedWeeks = comparison.experimental.request.durationWeeks.coerceAtLeast(1)
        if (audit.state != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ||
            audit.weeklyAudits.size != expectedWeeks ||
            audit.weeklyAudits.map { it.weekNumber }.toSet() != (1..expectedWeeks).toSet()) return false
        return audit.weeklyAudits.all { week ->
            week.shortfall == 0 && week.overrun == 0 &&
                week.prescriptionPreservedOrSubset &&
                week.targetCompatibleMaterializedUnits == week.materializedSetUnits
        } && audit.shortfall == 0 && audit.overrun == 0 && audit.prescriptionPreservedOrSubset
    }

    private fun validColdStartAuthorization(
        comparison: StimulusSelectionProgramComparison,
        authorization: StimulusPrescriptionAuthorization
    ): Boolean {
        val proposal = authorization.coldStartCalibration ?: return false
        val prescription = authorization.authorizedPrescription ?: return false
        val owner = authorization.owner ?: return false
        return authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
            authorization.quality == TrainableQuality.STRENGTH && authorization.targetId == "QUALITY:STRENGTH" &&
            authorization.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT &&
            proposal.owner == StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) &&
            proposal.quality == TrainableQuality.STRENGTH &&
            com.training.trackplanner.data.CanonicalStrengthExposureCapability.movementAnchor(owner.stableKey) != null &&
            proposal.loadState == com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED &&
            proposal.ownerHistoryStatus == ColdStartStrengthOwnerHistoryStatus.EXACT_OWNER_STRENGTH_SIGNAL_MISSING &&
            proposal.setCount > 0 && proposal.repetitions == 6 && proposal.targetRpe == 6.5 &&
            proposal.restSeconds == prescription.restSeconds &&
            comparison.selectionPlan.materialDemand.candidates.any {
                it.stableKey == owner.stableKey && it.role == owner.selectionRole && it.targetSets == proposal.setCount
            } &&
            prescription.sets.size == proposal.setCount && prescription.sets.all {
                it.reps == proposal.repetitions && it.weightKg == 0.0 &&
                    it.loadState == com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED &&
                    it.targetRpeMin == proposal.targetRpe && it.seconds == 0
            }
    }

    private fun b6IntegrityReasons(
        comparison: StimulusSelectionProgramComparison,
        requiredQualities: Map<StimulusPrescriptionOwnerIdentity, Set<TrainableQuality>>
    ): Set<String> = b6IntegrityReasonsInternal(comparison) { ownerIdentity, rowQuality ->
        ownerIdentity != null && rowQuality != null && rowQuality in requiredQualities[ownerIdentity].orEmpty()
    }

    private fun b6IntegrityReasons(
        comparison: StimulusSelectionProgramComparison,
        policy: CutoverScopePolicy,
        materialOwners: Set<StimulusPrescriptionOwnerIdentity>
    ): Set<String> = b6IntegrityReasonsInternal(comparison) { ownerIdentity, rowQuality ->
        includeQualitySpecificDiagnostic(policy, ownerIdentity, rowQuality, materialOwners)
    }

    private fun b6IntegrityReasonsInternal(
        comparison: StimulusSelectionProgramComparison,
        includeQualityDiagnostic: (StimulusPrescriptionOwnerIdentity?, TrainableQuality?) -> Boolean
    ): Set<String> = buildSet {
        val known = setOf(
            "B6_AUTHORIZED_SET_REUSED",
            "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED",
            "B6_UNAUTHORIZED_SET_CONTENT",
            "B6_UNAUTHORIZED_SET_IDENTITY",
            "B6_AUTHORIZATION_OVERRUN",
            "B6_AUTHORIZATION_SHORTFALL",
            "B6_AUTHORIZATION_MISSING_WEEK",
            "B6_PRESCRIPTION_NOT_PRESERVED",
            "B6_PRESCRIPTION_MUTATION",
            "B6_PRESCRIPTION_AUTHORITY_MISMATCH",
            "B6_MATERIALIZATION_INVARIANT_FAILURE",
            "B6_EFFORT_TARGET_NOT_PRESERVED",
            "B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM",
            "B6_EFFORT_TARGET_MISSING"
        )
        val effortIntegrityReasons = setOf(
            "B6_EFFORT_TARGET_NOT_PRESERVED",
            "B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM",
            "B6_EFFORT_TARGET_MISSING"
        )
        comparison.prescriptionMaterializationAudits.forEach { audit ->
            val ownerIdentity = audit.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            val includeEffortDiagnostics = includeQualityDiagnostic(ownerIdentity, audit.quality)
            if (audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE) add("B6_MATERIALIZATION_INVARIANT_FAILURE")
            addAll(audit.reasonCodes.filter { it in known && (includeEffortDiagnostics || it !in effortIntegrityReasons) })
            addAll(audit.weeklyAudits.flatMap { it.reasonCodes }.filter { it in known && (includeEffortDiagnostics || it !in effortIntegrityReasons) })
            if (audit.overrun > 0 || audit.maximumWeeklyOverrun > 0) add("B6_AUTHORIZATION_OVERRUN")
            if (audit.shortfall > 0 || audit.maximumWeeklyShortfall > 0) add("B6_AUTHORIZATION_SHORTFALL")
            if (!audit.prescriptionPreservedOrSubset || audit.weeklyAudits.any { !it.prescriptionPreservedOrSubset }) {
                add("B6_PRESCRIPTION_NOT_PRESERVED")
            }
        }
        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().forEach { authorization ->
            val ownerIdentity = authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            val includeEffortDiagnostics = includeQualityDiagnostic(ownerIdentity, authorization.quality)
            addAll(authorization.reasonCodes.filter { it in known && (includeEffortDiagnostics || it !in effortIntegrityReasons) })
        }
    }

    private fun includeQualitySpecificDiagnostic(
        policy: CutoverScopePolicy,
        ownerIdentity: StimulusPrescriptionOwnerIdentity?,
        rowQuality: TrainableQuality?,
        materialOwners: Set<StimulusPrescriptionOwnerIdentity>
    ): Boolean = ownerIdentity != null && ownerIdentity in materialOwners && rowQuality == policy.quality

    private fun materialOwnerIdentities(comparison: StimulusSelectionProgramComparison): Set<StimulusPrescriptionOwnerIdentity> = buildSet {
        addAll(comparison.addedOwnerIdentities)
        comparison.sharedOwnerIdentities.forEach { identity ->
            if (ownerRows(comparison.control, identity) != ownerRows(comparison.experimental, identity)) add(identity)
        }
    }

    private fun materialAttributionsFor(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<StimulusExperimentalChangeAttribution> = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().filter {
        it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
    }

    private fun isMaterialAttribution(attribution: StimulusExperimentalChangeAttribution): Boolean =
        attribution.source in setOf(
            StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
            StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION,
            StimulusExperimentalChangeAttributionSource.B6_APPROVED_POWER_JUMP_POLICY
        )

    private fun unrelatedControlParityFailures(
        comparison: StimulusSelectionProgramComparison,
        authorized: Set<StimulusPrescriptionOwnerIdentity>
    ): Boolean {
        val allowedDifferences = authorized + canonicalRoleReplacementControlOwners(comparison, authorized) +
            StimulusProductionMovementScopeEvidence.exactSameExerciseRoleReplacements(comparison, authorized) +
            StimulusProductionMovementScopeEvidence.exactUserApprovedReplacementControlOwners(comparison) +
            authorizedDownstreamConstraintControlOwners(comparison, authorized)
        return comparison.controlOwnerIdentities.any { identity ->
            identity !in allowedDifferences && ownerRows(comparison.control, identity) != ownerRows(comparison.experimental, identity)
        }
    }

    /** B5 may replace a legacy CONTROL role for the same exercise only with exact B6 authority. */
    private fun canonicalRoleReplacementControlOwners(
        comparison: StimulusSelectionProgramComparison,
        eligibleCanonicalOwners: Set<StimulusPrescriptionOwnerIdentity>
    ): Set<StimulusPrescriptionOwnerIdentity> {
        val attributions = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().filter {
            it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY &&
                "B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE" in it.reasonCodes
        }
        return attributions.mapNotNullTo(linkedSetOf()) { attribution ->
            val old = StimulusPrescriptionOwnerIdentity(attribution.stableKey ?: return@mapNotNullTo null,
                attribution.selectionRole ?: return@mapNotNullTo null)
            if (old !in comparison.removedOwnerIdentities) return@mapNotNullTo null
            val replacement = eligibleCanonicalOwners.firstOrNull { candidate ->
                candidate.stableKey == old.stableKey && candidate != old && candidate in comparison.addedOwnerIdentities &&
                    comparison.selectionPlan.selectedCandidates.any {
                        it.stableKey == candidate.stableKey && it.selectionRole == candidate.selectionRole &&
                            attribution.targetIds.any { targetId -> targetId in it.coveredTargetIds }
                    } && comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { authorization ->
                        authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == candidate } == true &&
                            authorization.targetId in attribution.targetIds && authorization.authorizedPrescription != null &&
                            authorization.status in setOf(
                                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                            )
                    }
            }
            old.takeIf { replacement != null }
        }
    }

    /** Accept only B7-verified same-slot set-prefix reductions with explicit builder constraint evidence. */
    private fun authorizedDownstreamConstraintControlOwners(
        comparison: StimulusSelectionProgramComparison,
        authorized: Set<StimulusPrescriptionOwnerIdentity>
    ): Set<StimulusPrescriptionOwnerIdentity> {
        val targetIds = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { authorization ->
            val owner = authorization.owner ?: return@filter false
            StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) in authorized &&
                authorization.authorizedPrescription != null && authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                )
        }.mapTo(linkedSetOf(), StimulusPrescriptionAuthorization::targetId)
        if (targetIds.isEmpty()) return emptySet()
        return comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().mapNotNullTo(linkedSetOf()) { attribution ->
            if (attribution.source != StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT ||
                "OWNER_LOCAL_CONSTRAINED_SET_SUBSET" !in attribution.reasonCodes ||
                !attribution.targetIds.all { it in targetIds }
            ) return@mapNotNullTo null
            val identity = StimulusPrescriptionOwnerIdentity(attribution.stableKey ?: return@mapNotNullTo null,
                attribution.selectionRole ?: return@mapNotNullTo null)
            val constrained = comparison.experimental.personalizedDecision?.planningBudget?.execution
                ?.constrainedOwnerStableKeys.orEmpty()
            identity.takeIf {
                it in comparison.sharedOwnerIdentities && it.stableKey in constrained && exactSetPrefixReduction(comparison, it)
            }
        }
    }

    private fun exactSetPrefixReduction(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): Boolean {
        val control = comparison.control.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        val experimental = comparison.experimental.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        if (control.isEmpty() || control.keys != experimental.keys) return false
        var reduced = false
        for ((slot, before) in control) {
            val after = experimental.getValue(slot)
            if (before.setPrescriptions == after.setPrescriptions && before.setCount == after.setCount) continue
            val exactPrefix = after.setPrescriptions.size < before.setPrescriptions.size &&
                before.setPrescriptions.take(after.setPrescriptions.size) == after.setPrescriptions &&
                after.setCount == after.setPrescriptions.size && before.prescription == after.prescription &&
                before.restSeconds == after.restSeconds && before.weightSource == after.weightSource &&
                before.reps == after.reps && before.weightKg == after.weightKg && before.seconds == after.seconds
            if (!exactPrefix) return false
            reduced = true
        }
        return reduced
    }

    private data class OwnerRow(
        val weekNumber: Int,
        val dayOfWeek: Int,
        val orderIndex: Int,
        val stableKey: String,
        val selectionRole: String,
        val setPrescriptions: List<com.training.trackplanner.data.ProgramSetPrescription>,
        val restSeconds: Int,
        val weightSource: String,
        val prescription: String,
        val setCount: Int,
        val reps: Int,
        val weightKg: Double,
        val seconds: Int
    )

    private fun ownerRows(
        program: com.training.trackplanner.data.GeneratedProgramSkeleton,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<OwnerRow> = program.items.filter {
        it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
    }.map(::ownerRow).sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.stableKey }, { it.selectionRole }))

    private fun ownerRow(item: ProgramSkeletonItem) = OwnerRow(
        weekNumber = item.weekNumber,
        dayOfWeek = item.dayOfWeek,
        orderIndex = item.orderIndex,
        stableKey = item.exerciseStableKey,
        selectionRole = item.selectionRole,
        setPrescriptions = item.setPrescriptions,
        restSeconds = item.restSeconds,
        weightSource = item.weightSource,
        prescription = item.prescription,
        setCount = item.setCount,
        reps = item.reps,
        weightKg = item.weightKg,
        seconds = item.seconds
    )

    private fun placementSignature(rows: List<OwnerRow>) = rows.map { listOf(it.weekNumber, it.dayOfWeek, it.orderIndex, it.stableKey, it.selectionRole) }

    private fun control(comparison: StimulusSelectionProgramComparison, policy: CutoverScopePolicy, reason: String) =
        control(comparison, policy, listOf(reason))

    private fun control(comparison: StimulusSelectionProgramComparison, policy: CutoverScopePolicy, reasons: List<String>) =
        StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
            scope = policy.scope,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = reasons.distinct().sorted(),
            b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
        )

    private fun inconclusive(comparison: StimulusSelectionProgramComparison, policy: CutoverScopePolicy, reason: String) =
        StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE,
            scope = policy.scope,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf(reason),
            b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.INCONCLUSIVE
        )

    private fun noMaterialChange(policy: CutoverScopePolicy, b7Status: StimulusExperimentalReadinessStatus) =
        StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE,
            scope = policy.scope,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf("B8_NO_MATERIAL_CHANGE"),
            b7Status = b7Status
        )

    private companion object {
        val OWNER_ORDER = compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole })
        val AUTHORITY_ORDER = compareBy<StimulusPrescriptionAuthorityIdentity>({ it.stableKey }, { it.selectionRole }, { it.quality.name })
        val TASK_PROTOCOL_AUTHORITY_ORDER = compareBy<StimulusTaskProtocolAuthorityIdentity>(
            { it.protocolId }, { it.stableKey }, { it.selectionRole }
        )
        val TASK_MATERIAL_ATTRIBUTION_SOURCES = setOf(
            StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL
        )
        val POWER_JUMP_TARGET_IDS = setOf("QUALITY:POWER", "QUALITY:REACTIVE_STRENGTH_SSC")

        fun qualityPolicy(quality: TrainableQuality): CutoverScopePolicy = when (quality) {
            TrainableQuality.STRENGTH -> scopePolicy(StimulusProductionCutoverScope.STRENGTH_V1)
            TrainableQuality.HYPERTROPHY -> scopePolicy(StimulusProductionCutoverScope.HYPERTROPHY_V1)
            else -> error("B8 combined scope does not authorize $quality")
        }

        fun scopePolicy(scope: StimulusProductionCutoverScope): CutoverScopePolicy = when (scope) {
            StimulusProductionCutoverScope.BADMINTON_TASK_V1 -> error("Task scope does not use quality prescription policy")
            StimulusProductionCutoverScope.POWER_JUMP_V1 -> error("Power/Jump scope has its own exact B8 validator")
            StimulusProductionCutoverScope.STRENGTH_V1 -> CutoverScopePolicy(
                scope = scope,
                quality = TrainableQuality.STRENGTH,
                targetId = "QUALITY:STRENGTH",
                authorizedReason = "B8_STRENGTH_V1_AUTHORIZED",
                targetNoNumericReason = "B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY",
                addedOwnerB5Reason = "B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY",
                prescriptionAuthorityReason = "B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY",
                effortAuthorityReason = "B8_CUTOVER_V1_EFFORT_NOT_FULLY_ENCODED",
                materializationReason = "B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION",
                nonQualityChangeReason = "B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE"
            )
            StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 -> CutoverScopePolicy(
                scope = scope,
                quality = TrainableQuality.STRENGTH,
                targetId = "QUALITY:STRENGTH",
                authorizedReason = "B8_STRENGTH_CALIBRATION_V1_AUTHORIZED",
                targetNoNumericReason = "B8_STRENGTH_CALIBRATION_V1_REQUIRES_NUMERIC_DOSE",
                addedOwnerB5Reason = "B8_STRENGTH_CALIBRATION_V1_EXACT_B5_REQUIRED",
                prescriptionAuthorityReason = "B8_STRENGTH_CALIBRATION_V1_EXACT_B6_REQUIRED",
                effortAuthorityReason = "B8_STRENGTH_CALIBRATION_V1_EFFORT_REQUIRED",
                materializationReason = "B8_STRENGTH_CALIBRATION_V1_SHAPE_MATERIALIZATION_REQUIRED",
                nonQualityChangeReason = "B8_STRENGTH_CALIBRATION_V1_STRENGTH_ONLY"
            )
            StimulusProductionCutoverScope.HYPERTROPHY_V1 -> CutoverScopePolicy(
                scope = scope,
                quality = TrainableQuality.HYPERTROPHY,
                targetId = "QUALITY:HYPERTROPHY",
                authorizedReason = "B8_HYPERTROPHY_V1_AUTHORIZED",
                targetNoNumericReason = "B8_HYPERTROPHY_V1_TARGET_HAS_NO_NUMERIC_AUTHORITY",
                addedOwnerB5Reason = "B8_HYPERTROPHY_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY",
                prescriptionAuthorityReason = "B8_HYPERTROPHY_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY",
                effortAuthorityReason = "B8_HYPERTROPHY_V1_EFFORT_NOT_FULLY_ENCODED",
                materializationReason = "B8_HYPERTROPHY_V1_REQUIRES_FULL_B6_MATERIALIZATION",
                nonQualityChangeReason = "B8_HYPERTROPHY_V1_NON_HYPERTROPHY_CHANGE_OUT_OF_SCOPE"
            )
            StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 -> CutoverScopePolicy(
                scope = scope,
                quality = TrainableQuality.STRENGTH,
                targetId = "QUALITY:STRENGTH",
                authorizedReason = "B8_STRENGTH_HYPERTROPHY_V1_AUTHORIZED",
                targetNoNumericReason = "B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY",
                addedOwnerB5Reason = "B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY",
                prescriptionAuthorityReason = "B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY",
                effortAuthorityReason = "B8_CUTOVER_V1_EFFORT_NOT_FULLY_ENCODED",
                materializationReason = "B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION",
                nonQualityChangeReason = "B8_STRENGTH_HYPERTROPHY_V1_OTHER_QUALITY_CHANGE_OUT_OF_SCOPE"
            )
        }
    }
}

private data class CutoverScopePolicy(
    val scope: StimulusProductionCutoverScope,
    val quality: TrainableQuality,
    val targetId: String,
    val authorizedReason: String,
    val targetNoNumericReason: String,
    val addedOwnerB5Reason: String,
    val prescriptionAuthorityReason: String,
    val effortAuthorityReason: String,
    val materializationReason: String,
    val nonQualityChangeReason: String
)

/** Compatibility spelling for callers that prefer the shorter engine name. */
typealias StimulusProductionCutoverAuthorityEngine = StimulusProductionCutoverAuthorityAuditEngine

internal fun StimulusProductionCutoverAuthorityDecision.toJson(): JSONObject = JSONObject()
    .put("status", status.name)
    .put("scope", scope.name)
    .put("authorizedOwnerIdentities", JSONArray(authorizedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
    }))
    .put("authorizedAuthorityIdentities", JSONArray(authorizedAuthorityIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }, { it.quality.name })).map {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole).put("quality", it.quality.name)
    }))
    .put("authorizedTaskProtocolIdentities", JSONArray(authorizedTaskProtocolIdentities.sortedWith(compareBy({ it.protocolId }, { it.stableKey }, { it.selectionRole })).map {
        JSONObject().put("protocolId", it.protocolId).put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
            .put("authorizedTasks", JSONArray(it.authorizedTasks.map { task -> task.name }.sorted()))
    }))
    .put("authorizedMovementTargetOwnerIdentities", JSONArray(authorizedMovementTargetOwnerIdentities.sortedWith(
        compareBy({ it.targetId }, { it.stableKey }, { it.selectionRole })
    ).map { JSONObject().put("targetId", it.targetId).put("stableKey", it.stableKey).put("selectionRole", it.selectionRole) }))
    .put("reasonCodes", JSONArray(reasonCodes.distinct().sorted()))
    .put("b7Status", b7Status.name)
    .put("routingActive", routingActive)
    .put("productionMutationAuthority", productionMutationAuthority)

internal fun taskProtocolMetadataForOwner(
    program: com.training.trackplanner.data.GeneratedProgramSkeleton,
    owner: StimulusPrescriptionOwnerIdentity
): List<TaskProtocolExposureMetadata> = program.items.asSequence().filter {
    it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
}.mapNotNull { item ->
    item.taskProtocolSemanticsJson?.let { raw -> runCatching { TaskProtocolExposureMetadata.fromJsonString(raw) }.getOrNull() }
}.toList()

internal fun StimulusProductionCutoverEvaluation.toCompactJson(): JSONObject = JSONObject()
    .put("comparison", comparison.toCompactJson())
    .put("cutoverAuthority", cutoverAuthority.toJson())
