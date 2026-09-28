package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** The deliberately narrow first production-cutover boundary. */
enum class StimulusProductionCutoverScope {
    STRENGTH_V1,
    HYPERTROPHY_V1,
    STRENGTH_HYPERTROPHY_V1
}

enum class StimulusProductionCutoverAuthorityStatus {
    AUTHORIZED_FOR_BOUNDED_CUTOVER,
    CONTROL_REQUIRED,
    INCONCLUSIVE,
    NO_MATERIAL_CHANGE
}

data class StimulusProductionCutoverAuthorityDecision(
    val status: StimulusProductionCutoverAuthorityStatus,
    val scope: StimulusProductionCutoverScope,
    val authorizedOwnerIdentities: List<StimulusPrescriptionOwnerIdentity>,
    val reasonCodes: List<String>,
    val b7Status: StimulusExperimentalReadinessStatus,
    val routingActive: Boolean = false,
    val productionMutationAuthority: Boolean = false,
    val authorizedAuthorityIdentities: List<StimulusPrescriptionAuthorityIdentity> = emptyList()
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
    fun audit(comparison: StimulusSelectionProgramComparison): StimulusProductionCutoverAuthorityDecision =
        audit(comparison, StimulusProductionCutoverScope.STRENGTH_V1)

    fun audit(
        comparison: StimulusSelectionProgramComparison,
        scope: StimulusProductionCutoverScope
    ): StimulusProductionCutoverAuthorityDecision {
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
        if (comparison.removedOwnerIdentities.isNotEmpty()) {
            reasons += "B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"
        }
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) {
            reasons += "B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED"
        }
        val materialOwners = materialOwnerIdentities(comparison)
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
                    attribution.targetIds.filterNot { it == policy.targetId }
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
        if (comparison.removedOwnerIdentities.isNotEmpty()) reasons += "B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) reasons += "B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED"

        val materialOwners = materialOwnerIdentities(comparison)
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

        val target = comparison.targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == policy.targetId }
        val candidateTargets = candidate?.coveredTargetIds.orEmpty()
        val selectedTraceTargets = comparison.selectionPlan.traces
            .filter { trace -> trace.selectedStableKey == identity.stableKey && trace.selectedSelectionRole == identity.selectionRole }
            .map { it.targetId }
        if (target == null || policy.targetId !in candidateTargets || policy.targetId !in selectedTraceTargets) {
            reasons += policy.addedOwnerB5Reason
        }
        targetAuthorityReason(comparison, policy.targetId, policy)?.let(reasons::add)

        val attribution = materialAttributionsFor(comparison, identity)
            .firstOrNull { it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY }
        if (attribution == null || policy.targetId !in attribution.targetIds) {
            reasons += policy.addedOwnerB5Reason
        }

        val auth = exactAuthorization(comparison, identity, policy)
        if (!isExecutableAuthorization(auth, policy)) {
            reasons += policy.prescriptionAuthorityReason
        }
        executionAuthorityReason(auth, policy)?.let(reasons::add)
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
        if (!isExecutableAuthorization(auth, policy)) {
            reasons += policy.prescriptionAuthorityReason
        }
        executionAuthorityReason(auth, policy)?.let(reasons::add)
        if (policy.scope == StimulusProductionCutoverScope.STRENGTH_V1 &&
            auth?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE) {
            reasons += "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        val attributions = materialAttributionsFor(comparison, identity)
        if (attributions.none {
                it.source == StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION ||
                    it.source == StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
            }) {
            reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        }
        if (attributions.filter(::isMaterialAttribution).any { attribution ->
            attribution.targetIds.any { it !in allowedMaterialTargetIds }
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
        authorization: StimulusPrescriptionAuthorization?,
        policy: CutoverScopePolicy
    ): Boolean = authorization != null && authorization.quality == policy.quality &&
            authorization.authorizedPrescription != null && authorization.status in setOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
        ) && (policy.scope != StimulusProductionCutoverScope.HYPERTROPHY_V1 ||
            canonicalExecutionAuthority(policy.quality, authorization.authorizedPrescription) == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
            authorization.authorizedPrescription.sets.isNotEmpty() && authorization.authorizedPrescription.sets.all { set ->
                set.reps in 7..15 && set.weightKg.isFinite() && set.weightKg > 0.0
            })

    private fun executionAuthorityReason(
        authorization: StimulusPrescriptionAuthorization?,
        policy: CutoverScopePolicy
    ): String? = if (
        policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
        authorization != null &&
        canonicalExecutionAuthority(policy.quality, authorization.authorizedPrescription) != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
    ) policy.effortAuthorityReason else null

    private fun targetAuthorityReason(
        comparison: StimulusSelectionProgramComparison,
        targetId: String?,
        policy: CutoverScopePolicy
    ): String? {
        if (policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1) {
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
        if (policy.scope == StimulusProductionCutoverScope.HYPERTROPHY_V1 &&
            audit.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED) return false
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
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
        )

    private fun unrelatedControlParityFailures(
        comparison: StimulusSelectionProgramComparison,
        authorized: Set<StimulusPrescriptionOwnerIdentity>
    ): Boolean = comparison.controlOwnerIdentities.any { identity ->
        identity !in authorized && ownerRows(comparison.control, identity) != ownerRows(comparison.experimental, identity)
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

        fun qualityPolicy(quality: TrainableQuality): CutoverScopePolicy = when (quality) {
            TrainableQuality.STRENGTH -> scopePolicy(StimulusProductionCutoverScope.STRENGTH_V1)
            TrainableQuality.HYPERTROPHY -> scopePolicy(StimulusProductionCutoverScope.HYPERTROPHY_V1)
            else -> error("B8 combined scope does not authorize $quality")
        }

        fun scopePolicy(scope: StimulusProductionCutoverScope): CutoverScopePolicy = when (scope) {
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
    .put("reasonCodes", JSONArray(reasonCodes.distinct().sorted()))
    .put("b7Status", b7Status.name)
    .put("routingActive", routingActive)
    .put("productionMutationAuthority", productionMutationAuthority)

internal fun StimulusProductionCutoverEvaluation.toCompactJson(): JSONObject = JSONObject()
    .put("comparison", comparison.toCompactJson())
    .put("cutoverAuthority", cutoverAuthority.toJson())
