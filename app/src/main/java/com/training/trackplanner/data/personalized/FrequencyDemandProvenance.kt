package com.training.trackplanner.data.personalized

import org.json.JSONArray
import org.json.JSONObject

enum class PlanningFrequencySource { AUTO, EXPLICIT_USER }
data class PlanningFrequencyProvenance(val recommendation: WeeklyFrequencyEvidence, val resolvedUserDays: Int,
    val source: PlanningFrequencySource) {
    val algorithmRecommendedDays: Int get() = recommendation.recommendedDays
    val explicitIncrease: Boolean get() = source == PlanningFrequencySource.EXPLICIT_USER && resolvedUserDays > algorithmRecommendedDays
    fun toJson() = JSONObject().put("algorithmRecommendedDays", algorithmRecommendedDays)
        .put("resolvedUserDays", resolvedUserDays).put("frequencySource", source.name).put("expansionActivated", explicitIncrease)
        .put("recommendation", recommendation.toJson())
}

enum class PlanningFundingSource { BASE, USER_FREQUENCY_EXPANSION }
enum class CandidateRejectionReason { FUNDED, FINITE_CAPACITY, SAFETY_OR_SEMANTIC_REJECTION }

/** Original prescription and owner travel with the candidate, including unfunded portions. */
data class CapacityCandidateTrace(val originalRank: Int, val item: PlannedExercise, val prescription: PlannedPrescription,
    val fundedBaseUnits: Int, val continuity: Boolean, val rejectionReason: CandidateRejectionReason) {
    val requestedUnits: Int get() = prescription.sets.size
    val remainingUnits: Int get() = (requestedUnits - fundedBaseUnits).coerceAtLeast(0)
    val fundingSource: PlanningFundingSource get() = PlanningFundingSource.BASE
    fun toJson() = JSONObject().put("originalRank", originalRank).put("stableKey", item.stableKey)
        .put("requestedUnits", requestedUnits).put("fundedBaseUnits", fundedBaseUnits).put("remainingUnits", remainingUnits)
        .put("priority", item.priority).put("scheduleTier", item.scheduleTier().name).put("rejectionReason", rejectionReason.name)
        .put("ownership", JSONArray(item.representedGapCodes.sorted())).put("representedObjectives", JSONArray(item.representedObjectives.sorted()))
        .put("supportiveObjectives", JSONArray(item.supportiveObjectives.sorted())).put("continuity", continuity)
        .put("style", item.style.name).put("variant", item.styleVariant).put("transition", item.transition?.stableKey)
        .put("prescription", prescription.text).put("prescriptionSource", prescription.weightSource)
        .put("restSeconds", prescription.restSeconds).put("sets", auditSets(prescription.sets)).put("fundingSource", fundingSource.name)
}

data class FrequencyDemandProvenance(val frequency: PlanningFrequencyProvenance, val candidates: List<CapacityCandidateTrace>,
    val baseAuthorized: List<AuthorizedSchedulingDemand>, val computedCapacity: WeeklyCapacityEnvelope,
    val actualMaterializedUnits: Int = 0) {
    val capacityRejected: List<CapacityCandidateTrace> get() = candidates.filter {
        it.rejectionReason == CandidateRejectionReason.FINITE_CAPACITY && it.remainingUnits > 0
    }.sortedBy { it.originalRank }
    fun toJson() = JSONObject().put("frequency", frequency.toJson())
        .put("candidates", JSONArray(candidates.map { it.toJson() }))
        .put("baseAuthorizedUnits", baseAuthorized.sumOf { it.prescription.sets.size })
        .put("computedCapacityUnits", computedCapacity.finalControllableUnits)
        .put("actualMaterializedUnits", actualMaterializedUnits)
        .put("baseAuthorized", AuthorizedSchedulingTrace(baseAuthorized, emptyList()).toJson())
}

internal fun capacityCandidateTrace(snapshot: PlanningHistorySnapshot, state: AthletePlanningState,
    originals: List<Pair<PlannedExercise, Boolean>>, funded: List<PlannedExercise>, prescriptions: PersonalizedPrescriptionPlanner
): List<CapacityCandidateTrace> = originals.mapIndexed { index, (original, continuity) ->
    val originalPrescription = prescriptions.prescribe(snapshot, state.strengthIntent, original, original.style)
    val fundedItem = funded.firstOrNull { it.stableKey == original.stableKey && it.styleVariant == original.styleVariant &&
        it.representedGapCodes == original.representedGapCodes }
    val fundedUnits = fundedItem?.let { prescriptions.prescribe(snapshot, state.strengthIntent, it, it.style).sets.size } ?: 0
    CapacityCandidateTrace(index + 1, original, originalPrescription, fundedUnits, continuity,
        if (fundedUnits < originalPrescription.sets.size) CandidateRejectionReason.FINITE_CAPACITY else CandidateRejectionReason.FUNDED)
}
