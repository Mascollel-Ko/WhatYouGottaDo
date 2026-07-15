package com.training.trackplanner.analysis.tissue

enum class TissueMtcSeedVerificationStatus {
    UNVERIFIED_SEED, BIBLIOGRAPHICALLY_VERIFIED, ABSTRACT_REVIEWED, FULL_TEXT_REVIEWED, METRIC_EXTRACTED, EXCLUDED
}

object TissueMtcResearchCatalogValidator {
    private val evidenceRelations = TissueMtcEvidenceRelation.entries.map { it.name }.toSet()
    private val parityFields = listOf(
        "sourceId" to "sourceId",
        "sourceConditionId" to "sourceConditionId",
        "tissueId" to "tissueId",
        "mechanicalLoadMode" to "mechanicalLoadMode",
        "temporalMetric" to "temporalMetric",
        "measurementMetric" to "measurementMetric",
        "normalizationBasis" to "normalizationBasis",
        "claimValue" to "reportedValue",
        "claimLowerBound" to "reportedLowerBound",
        "claimUpperBound" to "reportedUpperBound",
        "claimDispersionType" to "reportedDispersionType",
        "claimDispersionValue" to "reportedDispersionValue",
        "claimUnit" to "reportedUnit",
        "eventContext" to "eventContext",
        "movementPhase" to "movementPhase",
        "additionalExternalLoadKg" to "additionalExternalLoadKg",
        "additionalExternalLoadFractionBw" to "additionalExternalLoadFractionBw",
        "totalSystemMassKg" to "totalSystemMassKg",
        "totalSystemMassFractionBw" to "totalSystemMassFractionBw",
        "relativeLoadPercent1Rm" to "relativeLoadPercent1Rm",
        "externalLoadPlacement" to "externalLoadPlacement",
        "romCondition" to "romCondition",
        "velocityCondition" to "velocityCondition",
        "lateralityCondition" to "lateralityCondition",
        "surfaceCondition" to "surfaceCondition",
        "landingCondition" to "landingCondition"
    )

    fun validate(
        seeds: List<Map<String, String>>,
        sourceConditions: List<Map<String, String>>,
        extractions: List<Map<String, String>>,
        candidates: List<Map<String, String>>,
        gapRows: List<Map<String, String>>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (seeds.size != 130 || seeds.map { it.value("seedId") }.distinct().size != 130) errors += "Research catalog must contain 130 unique seeds."
        seeds.forEach { seed ->
            val id = seed.value("seedId")
            val status = runCatching { enumValueOf<TissueMtcSeedVerificationStatus>(seed.value("verificationStatus")) }.getOrNull()
            if (status == null) errors += "$id: invalid verification status."
            if (seed.value("evidenceRelationCandidate") !in evidenceRelations) errors += "$id: invalid evidence-relation candidate."
            if (status != null && status != TissueMtcSeedVerificationStatus.UNVERIFIED_SEED && seed.value("publicationIntegrityStatus") == "NOT_CHECKED") {
                errors += "$id: verified bibliography lacks publication-integrity triage."
            }
        }

        if (sourceConditions.size != 49 || sourceConditions.map { it.value("sourceConditionId") }.distinct().size != sourceConditions.size) {
            errors += "Source-condition registry must contain 49 unique protocol conditions."
        }
        val conditionIds = sourceConditions.map { it.value("sourceConditionId") }.toSet()
        extractions.forEach { if (it.value("sourceConditionId") !in conditionIds) errors += "${it.value("metricExtractionId")}: missing source condition." }

        val extractionsById = extractions.associateBy { it.value("metricExtractionId") }
        candidates.forEach { candidate ->
            val id = candidate.value("claimCandidateId")
            val extraction = extractionsById[candidate.value("metricExtractionId")]
            if (extraction == null) {
                errors += "$id: missing metric extraction."
            } else {
                parityFields.forEach { (candidateField, extractionField) ->
                    if (candidate.value(candidateField) != extraction.value(extractionField)) errors += "$id: $candidateField differs from extraction."
                }
                if (candidate.value("stableKey") !in extraction.value("appStableKeys").split('|')) errors += "$id: stableKey is outside the exact extraction correspondence."
            }
        }

        val hop = extractions.singleOrNull { it.value("metricExtractionId") == "C3METRIC_35142563_ACH_HOPLAND" }
        if (hop == null || hop.value("temporalMetric") != "EVENT_AVERAGE" || hop.value("externalLoadRepresentation") != "BODYWEIGHT_TASK" ||
            hop.value("additionalExternalLoadFractionBw").isNotBlank() || hop.value("totalSystemMassFractionBw").isNotBlank() || hop.value("externalLoadPlacement").isNotBlank()) {
            errors += "Hop landing is not an event-average bodyweight condition."
        }
        val pcl = extractions.singleOrNull { it.value("metricExtractionId") == "C3METRIC_10656979_PCL_FORCE" }
        if (pcl == null || pcl.value("mechanicalLoadMode") != "TENSION" || pcl.value("measurementMetric") != "MODELED_LIGAMENT_FORCE") {
            errors += "PCL modeled force semantics are invalid."
        }
        val tfj = extractions.singleOrNull { it.value("metricExtractionId") == "C3METRIC_8947402_TFJ_SHEAR" }
        if (tfj == null || tfj.value("measurementMetric") != "INTERSEGMENTAL_JOINT_FORCE_RESULTANT") errors += "TFJ resultant semantics are invalid."
        if (gapRows.size != 27 || gapRows.map { it.value("complexId") to it.value("axis") }.distinct().size != 27) errors += "Research-gap matrix must cover nine complexes by three axes."
        return TissueValidationReport(errors)
    }

    fun formatLoad(value: String?, unit: String, notApplicable: Boolean = false): String = when {
        notApplicable -> "not applicable"
        value.isNullOrBlank() -> "not reported"
        unit.isBlank() -> value.trim()
        else -> "${value.trim()} ${unit.trim()}"
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
}
