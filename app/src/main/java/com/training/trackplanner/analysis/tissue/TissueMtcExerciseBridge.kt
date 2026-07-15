package com.training.trackplanner.analysis.tissue

data class TissueMtcExerciseMovementMapping(
    val stableKey: String,
    val canonicalMovementFamily: String,
    val biomechanicalMovementFamily: String,
    val mappingStatus: String
)

data class TissueMtcExerciseComplexApplicability(
    val relationshipId: String,
    val stableKey: String,
    val complexId: String,
    val applicabilityStatus: String,
    val mappingBasis: String,
    val allocationPolicy: String
)

data class TissueMtcFallbackResolutionTrace(
    val traceId: String,
    val relationshipId: String,
    val stableKey: String,
    val complexId: String,
    val axis: TissueMtcAxis,
    val axisScaleId: String,
    val researchScore: Double?,
    val operationalScore: Double,
    val provenanceTier: TissueMtcProvenanceTier,
    val confidence: String,
    val fallbackRuleId: String,
    val inheritanceLevel: TissueMtcInheritanceLevel,
    val resolutionPath: String,
    val coefficientSetId: String,
    val resolutionStatus: String
)

data class TissueMtcResolutionRequest(
    val stableKey: String,
    val sourceConditionId: String,
    val approvedVariantStableKeys: Set<String>,
    val movementFamily: String,
    val complexId: String,
    val axis: TissueMtcAxis
)

data class TissueMtcResolutionCandidate(
    val candidateId: String,
    val stableKey: String,
    val sourceConditionId: String,
    val movementFamily: String,
    val complexId: String,
    val axis: TissueMtcAxis,
    val operationalScore: Double,
    val inheritanceLevel: TissueMtcInheritanceLevel
)

object TissueMtcExerciseBridgeParser {
    fun movementMappings(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcExerciseMovementMapping(
            row.required("stableKey"), row.required("canonicalMovementFamily"),
            row.required("biomechanicalMovementFamily"), row.required("mappingStatus")
        )
    }

    fun applicability(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcExerciseComplexApplicability(
            row.required("relationshipId"), row.required("stableKey"), row.required("complexId"),
            row.required("applicabilityStatus"), row.required("mappingBasis"), row.required("allocationPolicy")
        )
    }

    fun traces(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcFallbackResolutionTrace(
            row.required("traceId"), row.required("relationshipId"), row.required("stableKey"),
            row.required("complexId"), row.enum("axis"), row.required("axisScaleId"),
            row.double("researchScore"), row.required("operationalScore").toDouble(), row.enum("provenanceTier"),
            row.required("confidence"), row.required("fallbackRuleId"), row.enum("inheritanceLevel"),
            row.required("resolutionPath"), row.required("coefficientSetId"), row.required("resolutionStatus")
        )
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) = value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.double(name: String) = value(name).toDoubleOrNull()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T = enumValueOf(required(name).uppercase())
}

object TissueMtcExerciseBridgeResolver {
    fun resolve(request: TissueMtcResolutionRequest, candidates: List<TissueMtcResolutionCandidate>): TissueMtcResolutionCandidate? =
        candidates.asSequence().filter { it.complexId == request.complexId && it.axis == request.axis }
            .filter { candidate ->
                when (candidate.inheritanceLevel) {
                    TissueMtcInheritanceLevel.EXACT_CONDITION -> candidate.stableKey == request.stableKey && candidate.sourceConditionId == request.sourceConditionId
                    TissueMtcInheritanceLevel.STABLE_KEY_BASE -> candidate.stableKey == request.stableKey
                    TissueMtcInheritanceLevel.CLOSE_VARIANT -> candidate.stableKey in request.approvedVariantStableKeys
                    TissueMtcInheritanceLevel.MOVEMENT_FAMILY -> candidate.movementFamily == request.movementFamily
                    TissueMtcInheritanceLevel.FUNCTIONAL_COMPLEX, TissueMtcInheritanceLevel.CONSERVATIVE_FALLBACK -> true
                }
            }.minByOrNull { bridgePriority.getValue(it.inheritanceLevel) }

    private val bridgePriority = mapOf(
        TissueMtcInheritanceLevel.EXACT_CONDITION to 1,
        TissueMtcInheritanceLevel.STABLE_KEY_BASE to 2,
        TissueMtcInheritanceLevel.CLOSE_VARIANT to 3,
        TissueMtcInheritanceLevel.MOVEMENT_FAMILY to 4,
        TissueMtcInheritanceLevel.FUNCTIONAL_COMPLEX to 5,
        TissueMtcInheritanceLevel.CONSERVATIVE_FALLBACK to 6
    )
}

object TissueMtcExerciseBridgeValidator {
    fun validate(
        canonicalStableKeys: Set<String>,
        mappings: List<TissueMtcExerciseMovementMapping>,
        applicability: List<TissueMtcExerciseComplexApplicability>,
        traces: List<TissueMtcFallbackResolutionTrace>,
        axisScaleIds: Set<String>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (mappings.map { it.stableKey }.toSet() != canonicalStableKeys || mappings.size != canonicalStableKeys.size) errors += "Canonical stableKey mapping is incomplete or duplicated."
        val mappedKeys = mappings.map { it.stableKey }.toSet()
        if (applicability.any { it.stableKey !in mappedKeys || it.applicabilityStatus != "APPLICABLE" }) errors += "Invalid exercise-complex applicability."
        if (applicability.any { it.allocationPolicy != "PARENT_ONLY_WHEN_COMPLEX_FALLBACK" }) errors += "Parent-child double-counting boundary is missing."
        val expectedTraceKeys = applicability.flatMap { row -> TissueMtcAxis.entries.map { row.relationshipId to it } }.toSet()
        val actualTraceKeys = traces.map { it.relationshipId to it.axis }.toSet()
        if (traces.size != expectedTraceKeys.size || actualTraceKeys != expectedTraceKeys) errors += "M/T/C fallback traces are incomplete or duplicated."
        traces.forEach { trace ->
            if (trace.axisScaleId !in axisScaleIds || trace.operationalScore <= 0.0 || trace.coefficientSetId.isBlank() || trace.confidence.isBlank()) errors += "${trace.traceId}: unresolved operational axis."
            if (trace.researchScore == null && trace.operationalScore == 0.0) errors += "${trace.traceId}: UNKNOWN became zero."
        }
        return TissueValidationReport(errors)
    }
}
