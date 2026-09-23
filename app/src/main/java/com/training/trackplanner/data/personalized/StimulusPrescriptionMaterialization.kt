package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality

enum class StimulusPrescriptionAuthorizationSource {
    CONTROL_EXISTING_DIRECT_IDENTITY,
    B5_SELECTION_PROBE
}

enum class StimulusPrescriptionAuthorizationStatus {
    AUTHORIZED_EXISTING_COMPATIBLE,
    AUTHORIZED_SAFE_REPAIR,
    NO_EXECUTABLE_AUTHORIZATION,
    AMBIGUOUS_OWNER,
    MODEL_UNAVAILABLE
}

data class StimulusPrescriptionAuthorization(
    val targetId: String,
    val quality: TrainableQuality?,
    val owner: StimulusPrescriptionOwner?,
    val source: StimulusPrescriptionAuthorizationSource?,
    val inputPrescription: PlannedPrescription?,
    val plannedCompatibility: PlannedStimulusCompatibility?,
    val authorizedPrescription: PlannedPrescription?,
    val status: StimulusPrescriptionAuthorizationStatus,
    val reasonCodes: List<String> = emptyList(),
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false
)

data class StimulusPrescriptionAuthorizationPlan(
    val authorizations: List<StimulusPrescriptionAuthorization>,
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false
) {
    val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = authorizations
        .mapNotNull { authorization ->
            val owner = authorization.owner ?: return@mapNotNull null
            val prescription = authorization.authorizedPrescription ?: return@mapNotNull null
            if (authorization.status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                )) return@mapNotNull null
            StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) to prescription
        }.toMap()

    fun provider(): ExactPrescriptionAuthorizationProvider = object : ExactPrescriptionAuthorizationProvider {
        override val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = this@StimulusPrescriptionAuthorizationPlan.authorizedOwners

        override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
            authorizedOwners[StimulusPrescriptionOwnerIdentity(item.stableKey, item.role)]
    }
}

enum class StimulusPrescriptionMaterializationState {
    FULLY_MATERIALIZED,
    PARTIALLY_MATERIALIZED,
    NOT_MATERIALIZED,
    INVARIANT_FAILURE
}

data class StimulusPrescriptionMaterializationAudit(
    val targetId: String,
    val quality: TrainableQuality?,
    val owner: StimulusPrescriptionOwner?,
    val authorizedWeeklySetUnits: Int,
    val materializedWeeklySetUnits: Int,
    val targetCompatibleMaterializedUnits: Int,
    val shortfall: Int,
    val overrun: Int,
    val prescriptionPreservedOrSubset: Boolean,
    val state: StimulusPrescriptionMaterializationState,
    val reasonCodes: List<String> = emptyList()
)

/** Builds B6.2 authority from the B6.1 oracle without building another program. */
class StimulusPrescriptionAuthorizationEngine(
    private val realizationEngine: StimulusPrescriptionRealizationPlanEngine = StimulusPrescriptionRealizationPlanEngine()
) {
    fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        controlPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
    ): StimulusPrescriptionAuthorizationPlan {
        val realization = realizationEngine.build(targetPlan, selectionPlan, snapshot, controlPrescriptions)
        val authorizations = targetPlan.qualityTargets.map { target ->
            val targetId = "QUALITY:${target.quality.name}"
            val resolution = realization.resolutions.firstOrNull { it.targetId == targetId }
            authorizationFor(target, resolution)
        }
        return StimulusPrescriptionAuthorizationPlan(authorizations)
    }

    private fun authorizationFor(
        target: StimulusQualityTarget,
        resolution: StimulusPrescriptionResolution?
    ): StimulusPrescriptionAuthorization {
        val targetId = "QUALITY:${target.quality.name}"
        if (target.quality != TrainableQuality.STRENGTH) {
            return StimulusPrescriptionAuthorization(targetId, target.quality, resolution?.owner,
                resolution?.owner?.let { sourceFor(it.source) }, resolution?.currentPrescription ?: resolution?.probePrescription,
                resolution?.plannedCompatibility, null,
                if (target.quality == TrainableQuality.HYPERTROPHY) StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION
                else StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
                if (target.quality == TrainableQuality.HYPERTROPHY) listOf("PROPOSAL_NOT_EXECUTABLE_NO_LOAD_AUTHORITY")
                else listOf("CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE"))
        }
        if (resolution == null) return StimulusPrescriptionAuthorization(targetId, target.quality, null, null, null, null, null,
            StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE, listOf("STRENGTH_REALIZATION_RESOLUTION_UNAVAILABLE"))
        val source = resolution.owner?.let { sourceFor(it.source) }
        val input = resolution.currentPrescription ?: resolution.probePrescription
        return when {
            resolution.status == StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE &&
                resolution.owner != null && input != null -> StimulusPrescriptionAuthorization(targetId, target.quality,
                resolution.owner, source, input, resolution.plannedCompatibility, input,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                listOf("EXISTING_COMPATIBLE_PRESCRIPTION"))
            resolution.status == StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED &&
                resolution.owner != null && input != null && resolution.proposedPrescription != null -> {
                val proposal = resolution.proposedPrescription
                val authorized = input.copy(sets = proposal.sets)
                StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                    resolution.plannedCompatibility, authorized,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    listOf("SAFE_REPAIRED_PRESCRIPTION"))
            }
            resolution.status in setOf(
                StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER,
                StimulusPrescriptionResolutionStatus.AMBIGUOUS_EXISTING_REALIZATION_OWNER
            ) -> StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                resolution.plannedCompatibility, null, StimulusPrescriptionAuthorizationStatus.AMBIGUOUS_OWNER,
                resolution.reasonCodes)
            else -> StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                resolution.plannedCompatibility, null, StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                resolution.reasonCodes.ifEmpty { listOf("NO_EXECUTABLE_STRENGTH_AUTHORIZATION") })
        }
    }

    private fun sourceFor(value: String): StimulusPrescriptionAuthorizationSource = when {
        value == "B5_SELECTION" -> StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE
        else -> StimulusPrescriptionAuthorizationSource.CONTROL_EXISTING_DIRECT_IDENTITY
    }
}

class StimulusPrescriptionMaterializationAuditEngine(
    private val plannedResolver: StimulusPlannedPrescriptionResolver = StimulusPlannedPrescriptionResolver()
) {
    fun audit(
        plan: StimulusPrescriptionAuthorizationPlan,
        experimental: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot
    ): List<StimulusPrescriptionMaterializationAudit> = plan.authorizations.map { authorization ->
        val owner = authorization.owner
        val authorized = authorization.authorizedPrescription
        if (owner == null || authorized == null) {
            return@map StimulusPrescriptionMaterializationAudit(
                targetId = authorization.targetId,
                quality = authorization.quality,
                owner = owner,
                authorizedWeeklySetUnits = 0,
                materializedWeeklySetUnits = 0,
                targetCompatibleMaterializedUnits = 0,
                shortfall = 0,
                overrun = 0,
                prescriptionPreservedOrSubset = true,
                state = StimulusPrescriptionMaterializationState.NOT_MATERIALIZED,
                reasonCodes = authorization.reasonCodes + "NO_EXECUTABLE_AUTHORIZATION"
            )
        }
        val rowsByWeek = experimental.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
            .groupBy(ProgramSkeletonItem::weekNumber)
        val weeklyUnits = rowsByWeek.values.map { rows -> rows.sumOf { it.setPrescriptions.size } }
        val materialized = weeklyUnits.maxOrNull() ?: 0
        val overrun = weeklyUnits.maxOfOrNull { (it - authorized.sets.size).coerceAtLeast(0) } ?: 0
        val compatible = authorization.quality?.let { quality ->
            rowsByWeek.values.maxOfOrNull { rows ->
                rows.sumOf { row ->
                    val planned = PlannedPrescription(row.prescription, row.setPrescriptions, row.restSeconds, row.weightSource)
                    if (plannedResolver.compatibility(quality, planned, snapshot, owner.stableKey).status == PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT) {
                        row.setPrescriptions.size
                    } else 0
                }
            } ?: 0
        } ?: 0
        val preserved = rowsByWeek.values.flatten().all { row ->
            val expected = authorized.sets.take(row.setPrescriptions.size).mapIndexed { index, set -> set.copy(setIndex = index + 1) }
            row.setPrescriptions == expected
        }
        val shortfall = (authorized.sets.size - materialized).coerceAtLeast(0)
        val reasons = buildList {
            if (shortfall > 0) add("B6_AUTHORIZATION_SHORTFALL")
            if (overrun > 0) add("B6_AUTHORIZATION_OVERRUN")
            if (!preserved) add("B6_PRESCRIPTION_NOT_PRESERVED")
            if (authorization.source == StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE) add("B5_SELECTION_PROBE_AUTHORIZED")
        }
        val state = when {
            overrun > 0 || !preserved -> StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
            materialized == 0 -> StimulusPrescriptionMaterializationState.NOT_MATERIALIZED
            shortfall > 0 -> StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED
            else -> StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED
        }
        StimulusPrescriptionMaterializationAudit(authorization.targetId, authorization.quality, owner,
            authorized.sets.size, materialized, compatible, shortfall, overrun, preserved, state, reasons)
    }
}
