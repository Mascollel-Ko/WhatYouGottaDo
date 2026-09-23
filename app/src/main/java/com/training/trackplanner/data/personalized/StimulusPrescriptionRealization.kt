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
    NO_PRESCRIPTION_CHANGE_AUTHORIZED
}

/** B6 authority is intentionally separate from the B4 evidence flag. */
enum class StimulusPrescriptionResolutionAuthority { SHADOW_ONLY }

data class StimulusPrescriptionOwner(
    val stableKey: String,
    val selectionRole: String,
    val source: String = "B5_SELECTION"
)

data class StimulusEffortTarget(
    val minimumRpe: Double,
    val maximumImpliedRir: Int,
    val source: String = "B6_REVIEWED_EFFORT_GATE"
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
    val currentRealization: List<RealizedStimulusClassification> = emptyList(),
    val proposedPrescription: StimulusTargetCompatiblePrescription? = null,
    val effortTarget: StimulusEffortTarget? = null,
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

/**
 * B6.1 resolver. It consumes a B5-selected identity and reports a typed proposal. It never
 * changes a skeleton, writes persistence, changes set counts, or becomes generation authority.
 */
class StimulusPrescriptionRealizationPlanEngine(
    private val prescriptions: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()
) {
    fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        currentPrescriptions: Map<String, PlannedPrescription> = emptyMap(),
        currentRealizations: Map<String, List<RealizedStimulusClassification>> = emptyMap()
    ): StimulusPrescriptionRealizationPlan {
        val resolutions = targetPlan.qualityTargets.map { target ->
            val targetId = "QUALITY:${target.quality.name}"
            val candidates = selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }
            resolveTarget(targetId, target, candidates, selectionPlan, snapshot, currentPrescriptions, currentRealizations)
        }
        return StimulusPrescriptionRealizationPlan(resolutions)
    }

    private fun resolveTarget(
        targetId: String,
        target: StimulusQualityTarget,
        candidates: List<StimulusSelectedCandidate>,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        currentPrescriptions: Map<String, PlannedPrescription>,
        currentRealizations: Map<String, List<RealizedStimulusClassification>>
    ): StimulusPrescriptionResolution {
        fun base(status: StimulusPrescriptionResolutionStatus, reasons: List<String>, owner: StimulusPrescriptionOwner? = null,
                 current: PlannedPrescription? = null, probe: PlannedPrescription? = current,
                 realizations: List<RealizedStimulusClassification> = emptyList(), proposal: StimulusTargetCompatiblePrescription? = null): StimulusPrescriptionResolution =
            StimulusPrescriptionResolution(
                targetId = targetId, quality = target.quality, evidenceBasis = target.evidenceBasis,
                strategy = target.strategy, numericAuthority = target.numericAuthority, owner = owner,
                currentPrescription = current, probePrescription = probe, currentRealization = realizations,
                proposedPrescription = proposal, effortTarget = proposal?.effortTarget,
                status = status, reasonCodes = reasons, mutationAuthority = false
            )
        if (target.quality !in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) ||
            target.evidenceBasis != StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED
        ) return base(StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE, listOf("REALIZATION_MODEL_UNAVAILABLE_FOR_TARGET"))
        if (candidates.isEmpty()) return base(StimulusPrescriptionResolutionStatus.OWNER_UNRESOLVED, listOf("B5_OWNER_NOT_SELECTED"))
        if (candidates.size != 1) return base(StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER, listOf("MULTIPLE_B5_IDENTITIES_WITHOUT_EXACT_OWNER"))
        val candidate = candidates.single()
        val owner = StimulusPrescriptionOwner(candidate.stableKey, candidate.selectionRole)
        val current = currentPrescriptions[candidate.stableKey] ?: PerformancePrescriptionResolver.resolve(snapshot, candidate.stableKey)?.let {
            PlannedPrescription(it.text, it.sets, it.restSeconds, it.source)
        }
        val probe = current ?: selectionPlan.materialDemand.candidates.firstOrNull { it.stableKey == candidate.stableKey }?.let { item ->
            prescriptions.prescribe(snapshot, StrengthIntent.MIXED, item,
                if (target.quality == TrainableQuality.STRENGTH) StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS
                else StrengthProgrammingStyle.TOP_SET_HYPERTROPHY)
        }
        val realizations = currentRealizations[candidate.stableKey].orEmpty()
        if (current == null && probe == null) return base(
            StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION,
            listOf("NO_CURRENT_OR_REUSABLE_PRESCRIPTION"), owner, null, null, realizations
        )
        val effective = current ?: probe ?: return base(StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION, listOf("NO_SAFE_PRESCRIPTION"), owner)
        if (realizations.isEmpty()) return base(
            StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE,
            listOf("CURRENT_REALIZATION_CLASSIFICATION_REQUIRED"), owner, current, effective
        )
        val compatible = realizations.all { it.isRealized && it.kind == target.quality.toRealizedKind() }
        val effort = target.quality.effortTarget()
        if (compatible) {
            val proposal = StimulusTargetCompatiblePrescription(effective.sets, effort, "EXISTING_TYPED_AUTHORITY", "CURRENT_PRESCRIPTION_ALREADY_COMPATIBLE")
            return base(StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE, listOf("CURRENT_PRESCRIPTION_AND_REALIZATION_COMPATIBLE"), owner, current, effective, realizations, proposal)
        }
        if (target.numericAuthority in setOf(
                StimulusTargetNumericAuthority.NONE,
                StimulusTargetNumericAuthority.DIRECTION_ONLY,
                StimulusTargetNumericAuthority.UNRESOLVED
            )
        ) return base(
            StimulusPrescriptionResolutionStatus.NO_PRESCRIPTION_CHANGE_AUTHORIZED,
            listOf("B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE"), owner, current, effective, realizations
        )
        val proposed = propose(target.quality, effective, snapshot, candidate.stableKey, effort) ?: return base(
            StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION,
            listOf("SAFE_LOAD_OR_EFFORT_AUTHORITY_UNAVAILABLE"), owner, current, effective, realizations
        )
        return base(StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED,
            listOf("SHADOW_PROPOSAL_ONLY", "B5_OWNER_STABLE_KEY_AND_SELECTION_ROLE_PRESERVED"), owner, current, effective, realizations, proposed)
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
                val load = current.sets.map { it.weightKg }.filter { it.isFinite() && it > 0.0 }.minOrNull()
                    ?: return null
                if (snapshot.canonicalStrengthSignals[stableKey]?.observationCount ?: 0 < 1) return null
                StimulusTargetCompatiblePrescription(
                    sets = List(count) { index -> ProgramSetPrescription(index + 1, 5, round(load * 2.0) / 2.0, 0) },
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

    private fun TrainableQuality.toRealizedKind() = when (this) {
        TrainableQuality.STRENGTH -> RealizedStimulusKind.STRENGTH_LIKE
        TrainableQuality.HYPERTROPHY -> RealizedStimulusKind.HYPERTROPHY_LIKE
        else -> RealizedStimulusKind.NONE
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
        .put("status", resolution.status.name)
        .put("reasonCodes", JSONArray(resolution.reasonCodes))
        .put("mutationAuthority", resolution.mutationAuthority)
    }))
