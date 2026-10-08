package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.CanonicalIncumbentPlacementShadow
import com.training.trackplanner.data.CanonicalIncumbentActivationStatus
import com.training.trackplanner.data.CanonicalIncumbentPlacementPreservation

/** Internal production policy; switch this single line to CONTROL_ONLY for emergency rollback. */
object StimulusProductionRoutingPolicy {
    val defaultMode: StimulusProductionRoutingMode = StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
}

enum class StimulusProductionRoutingMode {
    CONTROL_ONLY,
    B8_STRENGTH_V1_ACTIVE,
    B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE,
    B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
}

enum class StimulusProductionProgramSource {
    CONTROL,
    B8_STRENGTH_V1,
    B8_STRENGTH_CALIBRATION_V1,
    B8_HYPERTROPHY_V1,
    B8_STRENGTH_HYPERTROPHY_V1,
    B8_BADMINTON_TASK_V1
}

data class StimulusProductionRoutingDecision(
    val mode: StimulusProductionRoutingMode,
    val selectedSource: StimulusProductionProgramSource,
    val b8Status: StimulusProductionCutoverAuthorityStatus?,
    val b8Scope: StimulusProductionCutoverScope?,
    val reasonCodes: List<String>,
    val productionRoutingActive: Boolean
) {
    init {
        require(reasonCodes == reasonCodes.distinct().sorted())
        require(productionRoutingActive == (selectedSource != StimulusProductionProgramSource.CONTROL &&
            mode.permits(selectedSource)))
    }
}

internal fun StimulusProductionRoutingMode.permits(source: StimulusProductionProgramSource): Boolean = when (this) {
    StimulusProductionRoutingMode.CONTROL_ONLY -> false
    StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE -> source == StimulusProductionProgramSource.B8_STRENGTH_V1
    StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE ->
        source == StimulusProductionProgramSource.B8_STRENGTH_V1 ||
            source == StimulusProductionProgramSource.B8_HYPERTROPHY_V1
    StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE ->
        source == StimulusProductionProgramSource.B8_STRENGTH_V1 ||
            source == StimulusProductionProgramSource.B8_STRENGTH_CALIBRATION_V1 ||
            source == StimulusProductionProgramSource.B8_HYPERTROPHY_V1 ||
            source == StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1 ||
            source == StimulusProductionProgramSource.B8_BADMINTON_TASK_V1
}

data class StimulusProductionRouteResult(
    val program: GeneratedProgramSkeleton,
    val decision: StimulusProductionRoutingDecision
)

/** The production result keeps diagnostics internal while exposing the selected skeleton. */
internal data class StimulusProductionGenerationResult(
    val program: GeneratedProgramSkeleton,
    val routeDecision: StimulusProductionRoutingDecision,
    val comparison: StimulusSelectionProgramComparison?,
    val buildCounts: StimulusProductionBuildCounts,
    val upstreamFailureReason: String? = null,
    val upstreamFailureDetails: List<String> = emptyList(),
    val incumbentPlacementShadow: CanonicalIncumbentPlacementShadow? = null,
    val incumbentPlacementActivationStatus: CanonicalIncumbentActivationStatus? = null,
    val incumbentPlacementPreservations: List<CanonicalIncumbentPlacementPreservation> = emptyList(),
    val incumbentPlacementActivationDetails: List<String> = emptyList(),
    /** Typed unmet material demand survives an experimental fallback to CONTROL. */
    val unresolvedMaterialDemandGaps: Set<String> = emptySet(),
    /** Candidate authority recovery remains diagnostic and does not grant execution authority. */
    val materialDemandAuthorityResolutions: List<ExecutionAuthorityResolution> = emptyList(),
    /** Unmet Strength quality demand remains visible even if B9 safely returns CONTROL. */
    val strengthShortfalls: List<StimulusStrengthShortfall> = emptyList()
) {
    val diagnostics: StimulusProductionDiagnostics
        get() = StimulusProductionDiagnostics.observe(comparison, routeDecision, upstreamFailureReason).let {
            it.copy(secondaryReasonCodes = (it.secondaryReasonCodes + upstreamFailureDetails).distinct().sorted())
        }
}

internal data class StimulusProductionBuildCounts(
    val totalBuildInvocations: Int,
    val controlBuilds: Int,
    val experimentalBuilds: Int,
    val thirdBuilds: Int
) {
    /** Alias used by diagnostics that describe unclassified invocations as "other". */
    val otherBuilds: Int get() = thirdBuilds
}

/** The only program-build invocation kinds recognized by the production boundary. */
internal enum class StimulusProductionBuildKind {
    CONTROL,
    EXPERIMENTAL,
    OTHER
}

internal class MutableStimulusProductionBuildCounts {
    private var totalBuildInvocations: Int = 0
    var controlBuilds: Int = 0
    var experimentalBuilds: Int = 0
    private var otherBuilds: Int = 0

    /** Record at the actual program-builder invocation boundary, before the call is entered. */
    fun recordProgramBuildInvocation(kind: StimulusProductionBuildKind) {
        totalBuildInvocations += 1
        when (kind) {
            StimulusProductionBuildKind.CONTROL -> controlBuilds += 1
            StimulusProductionBuildKind.EXPERIMENTAL -> experimentalBuilds += 1
            StimulusProductionBuildKind.OTHER -> otherBuilds += 1
        }
    }

    /** Compatibility helpers retain the old test seam while using the typed boundary. */
    fun recordControlBuild() {
        recordProgramBuildInvocation(StimulusProductionBuildKind.CONTROL)
    }

    fun recordExperimentalBuild() {
        recordProgramBuildInvocation(StimulusProductionBuildKind.EXPERIMENTAL)
    }

    fun snapshot(): StimulusProductionBuildCounts = StimulusProductionBuildCounts(
        totalBuildInvocations = totalBuildInvocations,
        controlBuilds = controlBuilds,
        experimentalBuilds = experimentalBuilds,
        thirdBuilds = otherBuilds
    )
}

/** Recognized failure boundary for the optional experimental/cutover branch after CONTROL exists. */
internal class StimulusProductionEvaluationFailure(
    val reasonCode: String,
    cause: Throwable? = null
) : RuntimeException(reasonCode, cause)

/**
 * B9 only selects one of the already-built B6.2 programs. It has no builder, persistence,
 * Room, snapshot, or authority-engine access and therefore cannot create a hybrid or third plan.
 */
class StimulusProductionRouter {
    fun route(
        comparison: StimulusSelectionProgramComparison,
        authority: StimulusProductionCutoverAuthorityDecision?,
        mode: StimulusProductionRoutingMode
    ): StimulusProductionRouteResult {
        val fallbackReason = when {
            mode == StimulusProductionRoutingMode.CONTROL_ONLY -> "B9_CONTROL_ONLY_POLICY"
            authority == null -> "B9_B8_AUTHORITY_MISSING"
            authority.status == StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED -> "B9_B8_CONTROL_REQUIRED"
            authority.status == StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE -> "B9_B8_INCONCLUSIVE"
            authority.status == StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE -> "B9_B8_NO_MATERIAL_CHANGE"
            authority.status != StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER ->
                "B9_B8_CONTRACT_INCONSISTENCY"
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 &&
                !mode.permits(StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1) ->
                "B9_B8_COMBINED_SCOPE_NOT_ACTIVE"
            authority.scope == StimulusProductionCutoverScope.BADMINTON_TASK_V1 &&
                !mode.permits(StimulusProductionProgramSource.B8_BADMINTON_TASK_V1) -> "B9_B8_TASK_SCOPE_NOT_ACTIVE"
            authority.scope !in setOf(
                StimulusProductionCutoverScope.STRENGTH_V1,
                StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1,
                StimulusProductionCutoverScope.HYPERTROPHY_V1,
                StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1,
                StimulusProductionCutoverScope.BADMINTON_TASK_V1
            ) -> "B9_B8_SCOPE_MISMATCH"
            !mode.permits(sourceForScope(authority.scope)) -> "B9_B8_SCOPE_MISMATCH"
            authority.authorizedOwnerIdentities.isEmpty() -> "B9_B8_EMPTY_AUTHORIZED_OWNER_SET"
            authority.authorizedOwnerIdentities.size != authority.authorizedOwnerIdentities.distinct().size ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.authorizedAuthorityIdentities.size != authority.authorizedAuthorityIdentities.distinct().size ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.authorizedAuthorityIdentities.toSet() != expectedAuthorityIdentities(comparison, authority) ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.BADMINTON_TASK_V1 &&
                authority.authorizedTaskProtocolIdentities.isEmpty() -> "B9_B8_TASK_AUTHORITY_IDENTITY_MISMATCH"
            authority.authorizedTaskProtocolIdentities.size != authority.authorizedTaskProtocolIdentities.distinct().size ->
                "B9_B8_TASK_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.BADMINTON_TASK_V1 &&
                authority.authorizedTaskProtocolIdentities.toSet() != expectedTaskProtocolIdentities(comparison) ->
                "B9_B8_TASK_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope != StimulusProductionCutoverScope.BADMINTON_TASK_V1 &&
                authority.authorizedTaskProtocolIdentities.isNotEmpty() -> "B9_B8_TASK_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.BADMINTON_TASK_V1 &&
                authority.authorizedOwnerIdentities.toSet() != authority.authorizedTaskProtocolIdentities.map {
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
                }.toSet() -> "B9_B8_TASK_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 &&
                authority.authorizedAuthorityIdentities.any { it.quality !in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) } ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 &&
                authority.authorizedAuthorityIdentities.map { it.quality }.toSet() !=
                    setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 &&
                combinedHasUnsupportedAttributionQuality(comparison) ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 &&
                combinedAuthorityHasFailedUpstreamQuality(comparison, authority) ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 &&
                comparison.prescriptionAuthorizationPlan?.conflictingOwners.orEmpty().any { owner ->
                    owner in authority.authorizedOwnerIdentities
                } ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.b7Status != StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW ->
                "B9_B8_CONTRACT_INCONSISTENCY"
            authority.routingActive || authority.productionMutationAuthority -> "B9_B8_CONTRACT_INCONSISTENCY"
            else -> null
        }
        if (fallbackReason != null) {
            return control(comparison, mode, authority, fallbackReason)
        }
        val source = sourceForScope(requireNotNull(authority).scope)
        val decision = StimulusProductionRoutingDecision(
            mode = mode,
            selectedSource = source,
            b8Status = authority?.status,
            b8Scope = authority?.scope,
            reasonCodes = listOf(
                when (source) {
                    StimulusProductionProgramSource.B8_STRENGTH_V1 -> "B9_B8_STRENGTH_V1_ROUTED"
                    StimulusProductionProgramSource.B8_STRENGTH_CALIBRATION_V1 -> "B9_B8_STRENGTH_CALIBRATION_V1_ROUTED"
                    StimulusProductionProgramSource.B8_HYPERTROPHY_V1 -> "B9_B8_HYPERTROPHY_V1_ROUTED"
                    StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1 ->
                        "B9_B8_STRENGTH_HYPERTROPHY_V1_ROUTED"
                    StimulusProductionProgramSource.B8_BADMINTON_TASK_V1 -> "B9_B8_BADMINTON_TASK_V1_ROUTED"
                    StimulusProductionProgramSource.CONTROL -> "B9_B8_CONTROL_REQUIRED"
                }
            ),
            productionRoutingActive = true
        )
        return StimulusProductionRouteResult(comparison.experimental, decision)
    }

    private fun control(
        comparison: StimulusSelectionProgramComparison,
        mode: StimulusProductionRoutingMode,
        authority: StimulusProductionCutoverAuthorityDecision?,
        reason: String
    ): StimulusProductionRouteResult {
        val decision = StimulusProductionRoutingDecision(
            mode = mode,
            selectedSource = StimulusProductionProgramSource.CONTROL,
            b8Status = authority?.status,
            b8Scope = authority?.scope,
            reasonCodes = listOf(reason).sorted(),
            productionRoutingActive = false
        )
        return StimulusProductionRouteResult(comparison.control, decision)
    }

    private fun sourceForScope(scope: StimulusProductionCutoverScope): StimulusProductionProgramSource = when (scope) {
        StimulusProductionCutoverScope.BADMINTON_TASK_V1 -> StimulusProductionProgramSource.B8_BADMINTON_TASK_V1
        StimulusProductionCutoverScope.STRENGTH_V1 -> StimulusProductionProgramSource.B8_STRENGTH_V1
        StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 -> StimulusProductionProgramSource.B8_STRENGTH_CALIBRATION_V1
        StimulusProductionCutoverScope.HYPERTROPHY_V1 -> StimulusProductionProgramSource.B8_HYPERTROPHY_V1
        StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 -> StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1
    }

    private fun expectedAuthorityIdentities(
        comparison: StimulusSelectionProgramComparison,
        authority: StimulusProductionCutoverAuthorityDecision
    ): Set<StimulusPrescriptionAuthorityIdentity> = when (authority.scope) {
        StimulusProductionCutoverScope.BADMINTON_TASK_V1 -> emptySet()
        StimulusProductionCutoverScope.STRENGTH_V1 -> authority.authorizedOwnerIdentities.map {
            StimulusPrescriptionAuthorityIdentity(it.stableKey, it.selectionRole, TrainableQuality.STRENGTH)
        }.toSet()
        StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 -> authority.authorizedOwnerIdentities.map {
            StimulusPrescriptionAuthorityIdentity(it.stableKey, it.selectionRole, TrainableQuality.STRENGTH)
        }.toSet()
        StimulusProductionCutoverScope.HYPERTROPHY_V1 -> authority.authorizedOwnerIdentities.map {
            StimulusPrescriptionAuthorityIdentity(it.stableKey, it.selectionRole, TrainableQuality.HYPERTROPHY)
        }.toSet()
        StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 -> combinedExpectedAuthorityIdentities(comparison)
    }

    private fun expectedTaskProtocolIdentities(
        comparison: StimulusSelectionProgramComparison
    ): Set<StimulusTaskProtocolAuthorityIdentity> = materialOwnerIdentities(comparison).mapNotNull { owner ->
        val metadata = taskProtocolMetadataForOwner(comparison.experimental, owner)
        val definitions = metadata.map { it.authorization.definition }.distinct()
        val taskSets = metadata.map { it.authorization.attributedTasks }.distinct()
        val definition = definitions.singleOrNull()?.takeIf {
            ApprovedBadmintonTaskProtocols.exact(owner.stableKey, owner.selectionRole, it.primaryTask) == it
        } ?: return@mapNotNull null
        val tasks = taskSets.singleOrNull() ?: return@mapNotNull null
        StimulusTaskProtocolAuthorityIdentity(definition.protocolId, owner.stableKey, owner.selectionRole, tasks)
    }.toSet()

    /**
     * B9 validates the lossless B8 identity set from the upstream material provenance. It does
     * not infer a quality from scope or stableKey: each material attribution contributes its
     * exact QUALITY target to the owner/role identity.
     */
    private fun combinedExpectedAuthorityIdentities(
        comparison: StimulusSelectionProgramComparison
    ): Set<StimulusPrescriptionAuthorityIdentity> {
        val materialOwners = materialOwnerIdentities(comparison)
        return comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
            .asSequence()
            .filter { attribution ->
                attribution.source in MATERIAL_ATTRIBUTION_SOURCES &&
                    attribution.stableKey != null && attribution.selectionRole != null &&
                    StimulusPrescriptionOwnerIdentity(attribution.stableKey, attribution.selectionRole) in materialOwners
            }
            .flatMap { attribution ->
                val owner = StimulusPrescriptionOwnerIdentity(requireNotNull(attribution.stableKey), requireNotNull(attribution.selectionRole))
                attribution.targetIds.asSequence().mapNotNull { targetId ->
                    val quality = when (targetId) {
                        "QUALITY:STRENGTH" -> TrainableQuality.STRENGTH
                        "QUALITY:HYPERTROPHY" -> TrainableQuality.HYPERTROPHY
                        else -> null
                    }
                    quality?.let { StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, it) }
                }
            }
            .toSet()
    }

    private fun combinedAuthorityHasFailedUpstreamQuality(
        comparison: StimulusSelectionProgramComparison,
        authority: StimulusProductionCutoverAuthorityDecision
    ): Boolean {
        val expected = combinedExpectedAuthorityIdentities(comparison)
        if (expected.isEmpty()) return true
        val authorized = authority.authorizedAuthorityIdentities.toSet()
        return expected.any { identity ->
            val owner = StimulusPrescriptionOwnerIdentity(identity.stableKey, identity.selectionRole)
            val authorization = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter {
                val candidateOwner = it.owner ?: return@filter false
                candidateOwner.stableKey == identity.stableKey &&
                    candidateOwner.selectionRole == identity.selectionRole &&
                    it.quality == identity.quality
            }
            val materializations = comparison.prescriptionMaterializationAudits.filter {
                val candidateOwner = it.owner ?: return@filter false
                candidateOwner.stableKey == identity.stableKey &&
                    candidateOwner.selectionRole == identity.selectionRole &&
                    it.quality == identity.quality
            }
            identity !in authorized || authorization.size != 1 ||
            authorization.single().status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                ) || materializations.size != 1 ||
                materializations.single().state != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ||
                (identity.quality == TrainableQuality.HYPERTROPHY &&
                    (authorization.single().executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED ||
                        materializations.single().executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED))
        }
    }

    private fun combinedHasUnsupportedAttributionQuality(
        comparison: StimulusSelectionProgramComparison
    ): Boolean = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
        .filter { attribution ->
            attribution.source in MATERIAL_ATTRIBUTION_SOURCES &&
                attribution.stableKey != null && attribution.selectionRole != null
        }
        .flatMap { it.targetIds }
        .any { targetId -> targetId !in setOf("QUALITY:STRENGTH", "QUALITY:HYPERTROPHY") }

    private fun materialOwnerIdentities(comparison: StimulusSelectionProgramComparison): Set<StimulusPrescriptionOwnerIdentity> = buildSet {
        addAll(comparison.addedOwnerIdentities)
        comparison.sharedOwnerIdentities.forEach { identity ->
            val controlRows = comparison.control.items.filter {
                it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
            }
            val experimentalRows = comparison.experimental.items.filter {
                it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
            }
            if (controlRows != experimentalRows) add(identity)
        }
    }

    private companion object {
        val MATERIAL_ATTRIBUTION_SOURCES = setOf(
            StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
            StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION,
            StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL
        )
    }
}

/**
 * Resolves the B8 audit scope from already-observed material B7 provenance. This resolver is
 * deliberately conservative: it never probes B8 policies and it ignores reused/conflict-only
 * rows that were not materially executed.
 */
class StimulusProductionMaterialScopeResolver {
    /** Diagnostic companion; [resolve] remains the unchanged production scope contract. */
    fun resolveDetailed(comparison: StimulusSelectionProgramComparison): StimulusProductionScopeResolution =
        observeProductionScope(comparison, resolve(comparison))

    fun resolve(comparison: StimulusSelectionProgramComparison): StimulusProductionCutoverScope? {
        val audit = comparison.experimentalReadinessAudit ?: return null
        val materialOwners = buildSet {
            addAll(comparison.addedOwnerIdentities)
            comparison.sharedOwnerIdentities.forEach { identity ->
                val controlRows = comparison.control.items.filter {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }
                val experimentalRows = comparison.experimental.items.filter {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }
                if (controlRows != experimentalRows) add(identity)
            }
        }
        if (materialOwners.isEmpty()) return null

        val materialSources = setOf(
            StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
            StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION,
            StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL
        )
        val attributions = audit.changeAttributions.filter { attribution ->
            attribution.source in materialSources &&
                attribution.stableKey != null && attribution.selectionRole != null &&
                StimulusPrescriptionOwnerIdentity(attribution.stableKey, attribution.selectionRole) in materialOwners
        }
        if (attributions.isEmpty()) return null

        // Every material owner must have governed provenance. Missing/ambiguous provenance is
        // intentionally fail-closed rather than allowing a quality to be inferred from a subset.
        val attributedOwners = attributions.map {
            StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole))
        }.toSet()
        if (attributedOwners != materialOwners) return null

        val targetIds = attributions.flatMap { it.targetIds }
        if (targetIds.isEmpty()) return null
        val governedTargetIds = comparison.targetPlan.qualityTargets.mapTo(linkedSetOf()) { "QUALITY:${it.quality.name}" }
            .apply { addAll(comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }) }
            .apply {
                comparison.targetPlan.movementTargets.filter { movement ->
                    movement.regionalDoseTargets.any { dose ->
                        dose.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                            dose.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                            dose.weeklyTarget != null && (
                                (dose.authorizedWholeSetUnits ?: 0) > 0 ||
                                    (dose.authorizedWholeSetUnits == 0 &&
                                        (dose.existingEquivalentExposure ?: 0.0) + 1e-9 >= dose.weeklyTarget &&
                                        comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty().any {
                                            it.targetId == movement.targetId &&
                                                it.status == StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6
                                        })
                                )
                    }
                }.forEach { add(it.targetId) }
            }
        if (targetIds.any { it !in governedTargetIds }) return null
        val targetQualities = targetIds.mapNotNull { targetId ->
            when (targetId) {
                "QUALITY:STRENGTH" -> com.training.trackplanner.data.TrainableQuality.STRENGTH
                "QUALITY:HYPERTROPHY" -> com.training.trackplanner.data.TrainableQuality.HYPERTROPHY
                else -> targetId.takeIf { regionalHypertrophyTarget(comparison, it) }
                    ?.let { com.training.trackplanner.data.TrainableQuality.HYPERTROPHY }
            }
        }.toSet()
        if (targetQualities.isEmpty() && targetIds.all { it.startsWith("TASK:") }) {
            val parsedTasks = targetIds.mapNotNull { id ->
                runCatching { CanonicalTaskTarget.valueOf(id.removePrefix("TASK:")) }.getOrNull()
            }
            val governedTaskIds = comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }.toSet()
            val perOwner = attributions.groupBy {
                StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole))
            }
            if (comparison.removedOwnerIdentities.isEmpty() &&
                parsedTasks.size == targetIds.size && targetIds.all { it in governedTaskIds } &&
                attributedOwners == materialOwners && materialOwners.all { owner ->
                    perOwner[owner].orEmpty().isNotEmpty() && perOwner.getValue(owner).all {
                        it.source == StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL && it.targetIds.isNotEmpty()
                    }
                }
            ) return StimulusProductionCutoverScope.BADMINTON_TASK_V1
            return null
        }
        // Task/unknown material remains out of quality scope. An exact B4 regional Hypertrophy
        // target is admitted only through its typed movement target and B4 dose lineage.
        if (targetIds.any { !it.startsWith("QUALITY:") && !regionalHypertrophyTarget(comparison, it) }) return null
        if (targetQualities == setOf(com.training.trackplanner.data.TrainableQuality.STRENGTH)) {
            val calibrationOwners = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
                .filter { authorization ->
                    val owner = authorization.owner ?: return@filter false
                    StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) in materialOwners &&
                        authorization.quality == com.training.trackplanner.data.TrainableQuality.STRENGTH &&
                        authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                        authorization.coldStartCalibration?.owner == StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
                }.map { StimulusPrescriptionOwnerIdentity(requireNotNull(it.owner).stableKey, requireNotNull(it.owner).selectionRole) }
                .toSet()
            if (calibrationOwners == materialOwners) return StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1
        }
        return when (targetQualities) {
            setOf(com.training.trackplanner.data.TrainableQuality.STRENGTH) ->
                StimulusProductionCutoverScope.STRENGTH_V1
            setOf(com.training.trackplanner.data.TrainableQuality.HYPERTROPHY) ->
                StimulusProductionCutoverScope.HYPERTROPHY_V1
            setOf(
                com.training.trackplanner.data.TrainableQuality.STRENGTH,
                com.training.trackplanner.data.TrainableQuality.HYPERTROPHY
            ) -> StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1
            else -> null
        }
    }

    private fun regionalHypertrophyTarget(
        comparison: StimulusSelectionProgramComparison,
        targetId: String
    ): Boolean = comparison.targetPlan.movementTargets.any { movement ->
        movement.targetId == targetId && movement.regionalDoseTargets.any { dose ->
            dose.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                dose.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                dose.numericAuthority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
                    StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY
                ) && dose.weeklyTarget != null &&
                dose.existingEquivalentExposure?.let { it.isFinite() && it >= 0.0 } == true &&
                ((dose.authorizedWholeSetUnits ?: 0) > 0 &&
                    dose.residualEquivalentExposure?.let { it.isFinite() && it > 0.0 } == true ||
                    dose.authorizedWholeSetUnits == 0 &&
                        (dose.existingEquivalentExposure ?: 0.0) + 1e-9 >= dose.weeklyTarget &&
                        comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty().any {
                            it.targetId == targetId &&
                                it.status == StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6
                        }) &&
                (dose.numericAuthority != StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ||
                    dose.evidence.any { it.contains("doseProvenance=USER_APPROVED_PROJECT_POLICY") })
        }
    }
}
