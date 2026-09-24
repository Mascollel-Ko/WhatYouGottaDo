package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton

/** Internal production policy; switch this single line to CONTROL_ONLY for emergency rollback. */
object StimulusProductionRoutingPolicy {
    val defaultMode: StimulusProductionRoutingMode = StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
}

enum class StimulusProductionRoutingMode {
    CONTROL_ONLY,
    B8_STRENGTH_V1_ACTIVE
}

enum class StimulusProductionProgramSource {
    CONTROL,
    B8_STRENGTH_V1
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
        require(productionRoutingActive == (mode == StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE &&
            selectedSource == StimulusProductionProgramSource.B8_STRENGTH_V1))
    }
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
    val controlBuilds: Int,
    val experimentalBuilds: Int,
    val thirdBuilds: Int
)

internal class MutableStimulusProductionBuildCounts {
    var controlBuilds: Int = 0
    var experimentalBuilds: Int = 0
    private var programBuildInvocations: Int = 0

    fun recordControlBuild() {
        controlBuilds += 1
        programBuildInvocations += 1
    }

    fun recordExperimentalBuild() {
        experimentalBuilds += 1
        programBuildInvocations += 1
    }

    fun snapshot(): StimulusProductionBuildCounts = StimulusProductionBuildCounts(
        controlBuilds = controlBuilds,
        experimentalBuilds = experimentalBuilds,
        thirdBuilds = (programBuildInvocations - controlBuilds - experimentalBuilds).coerceAtLeast(0)
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
            authority.scope != StimulusProductionCutoverScope.STRENGTH_V1 -> "B9_B8_SCOPE_MISMATCH"
            authority.authorizedOwnerIdentities.isEmpty() -> "B9_B8_EMPTY_AUTHORIZED_OWNER_SET"
            authority.b7Status != StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW ->
                "B9_B8_CONTRACT_INCONSISTENCY"
            authority.routingActive || authority.productionMutationAuthority -> "B9_B8_CONTRACT_INCONSISTENCY"
            else -> null
        }
        if (fallbackReason != null) {
            return control(comparison, mode, authority, fallbackReason)
        }
        val decision = StimulusProductionRoutingDecision(
            mode = mode,
            selectedSource = StimulusProductionProgramSource.B8_STRENGTH_V1,
            b8Status = authority?.status,
            b8Scope = authority?.scope,
            reasonCodes = listOf("B9_B8_STRENGTH_V1_ROUTED"),
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
}
