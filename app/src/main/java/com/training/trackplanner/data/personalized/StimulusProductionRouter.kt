package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton

/** Internal production policy; switch this single line to CONTROL_ONLY for emergency rollback. */
object StimulusProductionRoutingPolicy {
    val defaultMode: StimulusProductionRoutingMode = StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
}

enum class StimulusProductionRoutingMode {
    CONTROL_ONLY,
    B8_STRENGTH_V1_ACTIVE,
    B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE
}

enum class StimulusProductionProgramSource {
    CONTROL,
    B8_STRENGTH_V1,
    B8_HYPERTROPHY_V1
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
    val buildCounts: StimulusProductionBuildCounts
)

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
            authority.scope == StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 ->
                "B9_B8_COMBINED_SCOPE_NOT_ACTIVE"
            authority.scope !in setOf(
                StimulusProductionCutoverScope.STRENGTH_V1,
                StimulusProductionCutoverScope.HYPERTROPHY_V1
            ) -> "B9_B8_SCOPE_MISMATCH"
            !mode.permits(sourceForScope(authority.scope)) -> "B9_B8_SCOPE_MISMATCH"
            authority.authorizedOwnerIdentities.isEmpty() -> "B9_B8_EMPTY_AUTHORIZED_OWNER_SET"
            authority.authorizedOwnerIdentities.size != authority.authorizedOwnerIdentities.distinct().size ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.authorizedAuthorityIdentities.size != authority.authorizedAuthorityIdentities.distinct().size ->
                "B9_B8_AUTHORITY_IDENTITY_MISMATCH"
            authority.authorizedAuthorityIdentities.toSet() != expectedAuthorityIdentities(authority) ->
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
                    StimulusProductionProgramSource.B8_HYPERTROPHY_V1 -> "B9_B8_HYPERTROPHY_V1_ROUTED"
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
        StimulusProductionCutoverScope.STRENGTH_V1 -> StimulusProductionProgramSource.B8_STRENGTH_V1
        StimulusProductionCutoverScope.HYPERTROPHY_V1 -> StimulusProductionProgramSource.B8_HYPERTROPHY_V1
        StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 -> StimulusProductionProgramSource.CONTROL
    }

    private fun expectedAuthorityIdentities(
        authority: StimulusProductionCutoverAuthorityDecision
    ): Set<StimulusPrescriptionAuthorityIdentity> = authority.authorizedOwnerIdentities.map {
        StimulusPrescriptionAuthorityIdentity(it.stableKey, it.selectionRole, when (authority.scope) {
            StimulusProductionCutoverScope.STRENGTH_V1 -> com.training.trackplanner.data.TrainableQuality.STRENGTH
            StimulusProductionCutoverScope.HYPERTROPHY_V1 -> com.training.trackplanner.data.TrainableQuality.HYPERTROPHY
            StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1 ->
                error("combined scope cannot be routed as a single-quality source")
        })
    }.toSet()
}

/**
 * Resolves the B8 audit scope from already-observed material B7 provenance. This resolver is
 * deliberately conservative: it never probes B8 policies and it ignores reused/conflict-only
 * rows that were not materially executed.
 */
class StimulusProductionMaterialScopeResolver {
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
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
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
        if (targetIds.any { it !in governedTargetIds }) return null
        val targetQualities = targetIds.map { targetId ->
            when (targetId) {
                "QUALITY:STRENGTH" -> com.training.trackplanner.data.TrainableQuality.STRENGTH
                "QUALITY:HYPERTROPHY" -> com.training.trackplanner.data.TrainableQuality.HYPERTROPHY
                else -> return null
            }
        }.toSet()
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
}
