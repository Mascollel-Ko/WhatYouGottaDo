package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import kotlin.math.round
import org.json.JSONArray
import org.json.JSONObject

enum class StimulusPrescriptionResolutionStatus {
    ALREADY_TARGET_COMPATIBLE,
    SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED,
    NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION,
    REALIZATION_MODEL_UNAVAILABLE,
    OWNER_UNRESOLVED,
    AMBIGUOUS_OWNER,
    AMBIGUOUS_EXISTING_REALIZATION_OWNER,
    NO_PRESCRIPTION_CHANGE_AUTHORIZED
}

enum class StimulusPrescriptionResolutionAuthority { SHADOW_ONLY }

data class StimulusPrescriptionOwner(
    val stableKey: String,
    val selectionRole: String,
    val source: String = "B5_SELECTION"
)

data class StimulusPrescriptionOwnerIdentity(
    val stableKey: String,
    val selectionRole: String
)

data class StimulusEffortTarget(
    val minimumRpe: Double,
    val maximumImpliedRir: Int,
    val source: String = "B6_REVIEWED_EFFORT_GATE"
)

enum class PlannedStimulusCompatibilityStatus { COMPATIBLE_CONDITIONAL_ON_EFFORT, INCOMPATIBLE, UNRESOLVED }

data class PlannedStimulusCompatibility(
    val quality: TrainableQuality,
    val status: PlannedStimulusCompatibilityStatus,
    val reference1RmKg: Double? = null,
    val relativeIntensity: Double? = null,
    val reasonCodes: List<String> = emptyList()
)

data class StimulusTargetCompatiblePrescription(
    val sets: List<ProgramSetPrescription>,
    val effortTarget: StimulusEffortTarget,
    val numericAuthority: String,
    val source: String,
    val shadowOnly: Boolean = true
)

data class StimulusPrescriptionResolution(
    val targetId: String,
    val quality: TrainableQuality?,
    val evidenceBasis: StimulusEvidenceBasis,
    val strategy: StimulusDoseStrategy?,
    val numericAuthority: StimulusTargetNumericAuthority,
    val owner: StimulusPrescriptionOwner?,
    val currentPrescription: PlannedPrescription?,
    val probePrescription: PlannedPrescription?,
    val plannedCompatibility: PlannedStimulusCompatibility? = null,
    val proposedPrescription: StimulusTargetCompatiblePrescription? = null,
    val effortTarget: StimulusEffortTarget? = proposedPrescription?.effortTarget,
    val status: StimulusPrescriptionResolutionStatus,
    val reasonCodes: List<String> = emptyList(),
    val mutationAuthority: Boolean = false,
    val authority: StimulusPrescriptionResolutionAuthority = StimulusPrescriptionResolutionAuthority.SHADOW_ONLY
)

data class StimulusPrescriptionRealizationPlan(
    val resolutions: List<StimulusPrescriptionResolution>,
    val shadowOnly: Boolean = true,
    val mutationAuthority: Boolean = false,
    val authority: StimulusPrescriptionResolutionAuthority = StimulusPrescriptionResolutionAuthority.SHADOW_ONLY
)

/** B6.1 shadow resolver. Planned compatibility is calculated from materialized prescriptions. */
class StimulusPrescriptionRealizationPlanEngine(
    private val prescriptions: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()
) {
    fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        currentPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = emptyMap()
    ): StimulusPrescriptionRealizationPlan = StimulusPrescriptionRealizationPlan(
        targetPlan.qualityTargets.map { target ->
            val targetId = "QUALITY:${target.quality.name}"
            val candidates = selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }
            resolveTarget(targetId, target, candidates, selectionPlan, snapshot, currentPrescriptions)
        }
    )

    private fun resolveTarget(
        targetId: String,
        target: StimulusQualityTarget,
        candidates: List<StimulusSelectedCandidate>,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        currentPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
    ): StimulusPrescriptionResolution {
        fun base(
            status: StimulusPrescriptionResolutionStatus,
            reasons: List<String>,
            owner: StimulusPrescriptionOwner? = null,
            current: PlannedPrescription? = null,
            probe: PlannedPrescription? = current,
            compatibility: PlannedStimulusCompatibility? = null,
            proposal: StimulusTargetCompatiblePrescription? = null
        ) = StimulusPrescriptionResolution(
            targetId = targetId, quality = target.quality, evidenceBasis = target.evidenceBasis,
            strategy = target.strategy, numericAuthority = target.numericAuthority, owner = owner,
            currentPrescription = current, probePrescription = probe, plannedCompatibility = compatibility,
            proposedPrescription = proposal, status = status, reasonCodes = reasons, mutationAuthority = false
        )

        if (target.quality !in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) ||
            target.evidenceBasis != StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED
        ) return base(StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE,
            listOf("REALIZATION_MODEL_UNAVAILABLE_FOR_TARGET"))

        val trace = selectionPlan.traces.firstOrNull { it.targetId == targetId }
        val ownerOptions = when {
            candidates.isNotEmpty() -> candidates.map { candidate ->
                StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole) to "B5_SELECTION"
            }
            else -> trace?.controlDirectCapabilityIdentities.orEmpty().flatMap { stableKey ->
                currentPrescriptions.keys.filter { it.stableKey == stableKey }.map { it to "CONTROL_EXISTING_DIRECT_IDENTITY" }
            }
        }.distinct()
        if (ownerOptions.isEmpty()) return base(StimulusPrescriptionResolutionStatus.OWNER_UNRESOLVED,
            listOf(if (candidates.isEmpty()) "CONTROL_DIRECT_IDENTITY_OWNER_UNRESOLVED" else "B5_OWNER_NOT_SELECTED"))

        fun probeFor(identity: StimulusPrescriptionOwnerIdentity): PlannedPrescription? {
            val item = selectionPlan.materialDemand.candidates.firstOrNull { it.stableKey == identity.stableKey } ?: return null
            val style = if (target.quality == TrainableQuality.STRENGTH) StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS
            else StrengthProgrammingStyle.TOP_SET_HYPERTROPHY
            return prescriptions.prescribe(snapshot, StrengthIntent.MIXED, item, style)
        }
        val evaluated = ownerOptions.mapNotNull { (identity, source) ->
            val current = currentPrescriptions[identity]
            val effective = current ?: probeFor(identity) ?: return@mapNotNull null
            val compatibility = plannedCompatibility(target.quality, effective, snapshot, identity.stableKey)
            Triple(identity, source, Triple(current, effective, compatibility))
        }
        if (evaluated.isEmpty()) return base(StimulusPrescriptionResolutionStatus.OWNER_UNRESOLVED,
            listOf("MATERIALIZED_OWNER_PRESCRIPTION_UNAVAILABLE"))
        val compatible = evaluated.filter { it.third.third.status == PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT }
        if (compatible.size > 1) return base(StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE,
            listOf("ALREADY_TARGET_COMPATIBLE", "MULTIPLE_COMPATIBLE_EXISTING_IDENTITIES_NO_ARBITRARY_SELECTION"))
        val selected = when {
            compatible.size == 1 -> compatible.single()
            evaluated.size == 1 -> evaluated.single()
            else -> return base(
                if (candidates.isNotEmpty()) StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER
                else StimulusPrescriptionResolutionStatus.AMBIGUOUS_EXISTING_REALIZATION_OWNER,
                listOf(
                    if (candidates.isNotEmpty()) "MULTIPLE_B5_IDENTITIES_WITHOUT_EXACT_OWNER"
                    else "AMBIGUOUS_EXISTING_REALIZATION_OWNER",
                    "NO_COMPATIBLE_EXISTING_IDENTITY"
                )
            )
        }
        val identity = selected.first
        val source = selected.second
        val current = selected.third.first
        val effective = selected.third.second
        val compatibility = selected.third.third
        val owner = StimulusPrescriptionOwner(identity.stableKey, identity.selectionRole, source)
        val effort = target.quality.effortTarget()
        if (compatibility.status == PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT) {
            val proposal = StimulusTargetCompatiblePrescription(effective.sets, effort,
                "EXISTING_TYPED_PLANNED_AUTHORITY", "CURRENT_PLANNED_PRESCRIPTION_ALREADY_COMPATIBLE")
            return base(StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE,
                listOf("CURRENT_PLANNED_PRESCRIPTION_COMPATIBLE_CONDITIONAL_ON_EFFORT"), owner, current, effective, compatibility, proposal)
        }
        if (compatibility.status == PlannedStimulusCompatibilityStatus.UNRESOLVED) return base(
            StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION,
            compatibility.reasonCodes.ifEmpty { listOf("PLANNED_COMPATIBILITY_UNRESOLVED") }, owner, current, effective, compatibility)
        if (target.numericAuthority in setOf(
                StimulusTargetNumericAuthority.NONE,
                StimulusTargetNumericAuthority.DIRECTION_ONLY,
                StimulusTargetNumericAuthority.UNRESOLVED
            )) return base(StimulusPrescriptionResolutionStatus.NO_PRESCRIPTION_CHANGE_AUTHORIZED,
            listOf("B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE"), owner, current, effective, compatibility)
        val proposed = propose(target.quality, effective, snapshot, identity.stableKey, effort)
            ?: return base(StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION,
                listOf("SAFE_LOAD_OR_EFFORT_AUTHORITY_UNAVAILABLE"), owner, current, effective, compatibility)
        return base(StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED,
            listOf("SHADOW_PROPOSAL_ONLY", "B5_OWNER_STABLE_KEY_AND_SELECTION_ROLE_PRESERVED"), owner, current, effective, compatibility, proposed)
    }

    private fun plannedCompatibility(
        quality: TrainableQuality,
        prescription: PlannedPrescription,
        snapshot: PlanningHistorySnapshot,
        stableKey: String
    ): PlannedStimulusCompatibility {
        if (prescription.sets.isEmpty()) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
            reasonCodes = listOf("PLANNED_SET_PRESCRIPTION_EMPTY"))
        val repsCompatible = prescription.sets.all { set -> when (quality) {
            TrainableQuality.STRENGTH -> set.reps in 1..6
            TrainableQuality.HYPERTROPHY -> set.reps in 7..15
            else -> false
        } }
        if (!repsCompatible) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.INCOMPATIBLE,
            reasonCodes = listOf("PLANNED_REPS_OUTSIDE_${quality.name}_MODEL"))
        val reference = snapshot.canonicalStrengthSignals[stableKey]?.posteriorMedianKg
            ?.takeIf { it.isFinite() && it > 0.0 }
        val loads = prescription.sets.map { it.weightKg }
        if (quality == TrainableQuality.STRENGTH) {
            if (reference == null) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
                reasonCodes = listOf("CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE"))
            val relative = loads.filter { it.isFinite() && it > 0.0 }.minOrNull()?.div(reference)
                ?: return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
                    reference1RmKg = reference, reasonCodes = listOf("PLANNED_LOAD_UNAVAILABLE"))
            return PlannedStimulusCompatibility(quality,
                if (relative >= .70) PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT
                else PlannedStimulusCompatibilityStatus.INCOMPATIBLE,
                reference1RmKg = reference, relativeIntensity = relative,
                reasonCodes = if (relative < .70) listOf("PLANNED_LOAD_BELOW_70_PERCENT_REFERENCE_1RM") else emptyList())
        }
        val validLoad = loads.all { it.isFinite() && (it > 0.0 || prescription.weightSource.startsWith("PROVISIONAL")) }
        if (!validLoad) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
            reasonCodes = listOf("PLANNED_RESISTANCE_LOAD_UNAVAILABLE"))
        return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT,
            reference1RmKg = reference)
    }

    private fun propose(
        quality: TrainableQuality,
        current: PlannedPrescription,
        snapshot: PlanningHistorySnapshot,
        stableKey: String,
        effort: StimulusEffortTarget
    ): StimulusTargetCompatiblePrescription? {
        val count = current.sets.size
        if (count == 0) return null
        return when (quality) {
            TrainableQuality.STRENGTH -> {
                val reference = snapshot.canonicalStrengthSignals[stableKey]?.posteriorMedianKg
                    ?.takeIf { it.isFinite() && it > 0.0 } ?: return null
                val load = current.sets.map { it.weightKg }.filter { it.isFinite() && it > 0.0 }.minOrNull() ?: return null
                if (load / reference < .70) return null
                StimulusTargetCompatiblePrescription(
                    sets = List(count) { index -> ProgramSetPrescription(index + 1, 5, round(load * 2) / 2, 0) },
                    effortTarget = effort, numericAuthority = "SAFE_CANONICAL_STRENGTH_LOAD",
                    source = "B6_SHADOW_STRENGTH_RESOLUTION"
                )
            }
            TrainableQuality.HYPERTROPHY -> StimulusTargetCompatiblePrescription(
                sets = List(count) { index -> ProgramSetPrescription(index + 1, 8, 0.0, 0) },
                effortTarget = effort, numericAuthority = "PROVISIONAL_8_REPS_ZERO_LOAD",
                source = "B6_SHADOW_HYPERTROPHY_PROVISIONAL"
            )
            else -> null
        }
    }

    private fun TrainableQuality.effortTarget() = when (this) {
        TrainableQuality.STRENGTH -> StimulusEffortTarget(6.0, 4)
        TrainableQuality.HYPERTROPHY -> StimulusEffortTarget(7.0, 3)
        else -> StimulusEffortTarget(0.0, Int.MAX_VALUE)
    }
}

internal fun StimulusPrescriptionRealizationPlan.toCompactJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("mutationAuthority", mutationAuthority)
    .put("authority", authority.name)
    .put("resolutions", JSONArray(resolutions.map { resolution -> JSONObject()
        .put("targetId", resolution.targetId)
        .put("quality", resolution.quality?.name)
        .put("evidenceBasis", resolution.evidenceBasis.name)
        .put("strategy", resolution.strategy?.name)
        .put("numericAuthority", resolution.numericAuthority.name)
        .put("owner", resolution.owner?.let { owner -> JSONObject()
            .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole).put("source", owner.source)
        })
        .put("plannedCompatibility", resolution.plannedCompatibility?.let { compatibility -> JSONObject()
            .put("status", compatibility.status.name)
            .put("reference1RmKg", compatibility.reference1RmKg)
            .put("relativeIntensity", compatibility.relativeIntensity)
            .put("reasonCodes", JSONArray(compatibility.reasonCodes))
        })
        .put("status", resolution.status.name)
        .put("reasonCodes", JSONArray(resolution.reasonCodes))
        .put("mutationAuthority", resolution.mutationAuthority)
    }))
