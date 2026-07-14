package com.training.trackplanner.analysis.tissue

object TissueC3ResearchValidator {
    fun packageData(
        extractions: List<TissueSourceMetricExtraction>,
        dispositions: List<TissueC3CandidateDispositionRow>,
        candidates: List<TissueMultidimensionalClaimCandidate>,
        rubrics: List<TissueMultidimensionalRubric>,
        decisions: List<TissueC3ResearchDecisionRow>,
        correspondences: List<TissueExerciseVariantCorrespondenceRow>,
        dimensions: List<TissueLoadDimensionDefinition>,
        catalog: List<TissueCatalogEntry>,
        sourceIds: Set<String>,
        expectedOldCandidateIds: Set<String>,
        requiredResearchTargetIds: Set<String>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val dimensionKeys = dimensions.map { Triple(it.tissueId, it.mechanicalLoadMode, it.temporalMetric) }.toSet()
        val catalogIds = catalog.map { it.tissueId }.toSet()
        unique("metric extraction", extractions.map { it.metricExtractionId }, errors)
        unique("candidate disposition", dispositions.map { it.candidateId }, errors)
        unique("claim candidate", candidates.map { it.claimCandidateId }, errors)
        unique("rubric", rubrics.map { it.rubricId }, errors)
        unique("research decision", decisions.map { it.researchDecisionId }, errors)
        unique("correspondence", correspondences.map { it.stableKey }, errors)

        extractions.forEach { row ->
            if (row.sourceId !in sourceIds) errors += "${row.metricExtractionId}: unknown source."
            if (row.tissueId !in catalogIds) errors += "${row.metricExtractionId}: unknown tissue."
            if (row.measurementMetric != TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX &&
                Triple(row.tissueId, row.mechanicalLoadMode, row.temporalMetric) !in dimensionKeys
            ) {
                errors += "${row.metricExtractionId}: unregistered tissue/load-mode/temporal combination."
            }
            validateUnit(row.measurementMetric, row.normalizationBasis, row.reportedUnit, row.metricExtractionId, errors)
        }
        if (dispositions.map { it.candidateId }.toSet() != expectedOldCandidateIds) {
            errors += "Every revised C2A-R1 candidate must have exactly one C3 disposition."
        }
        val extractionById = extractions.associateBy { it.metricExtractionId }
        candidates.forEach { candidate ->
            val extraction = extractionById[candidate.metricExtractionId]
            if (extraction == null) errors += "${candidate.claimCandidateId}: missing metric extraction."
            else if (candidate.sourceId != extraction.sourceId || candidate.tissueId != extraction.tissueId ||
                candidate.mechanicalLoadMode != extraction.mechanicalLoadMode || candidate.temporalMetric != extraction.temporalMetric ||
                candidate.measurementMetric != extraction.measurementMetric || candidate.normalizationBasis != extraction.normalizationBasis
            ) errors += "${candidate.claimCandidateId}: claim does not preserve its extraction identity."
            if (candidate.measurementMetric == TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX &&
                candidate.maximumDefensibleBand != null
            ) errors += "${candidate.claimCandidateId}: source composite cannot carry a generic band."
        }
        rubrics.forEach { rubric ->
            val anchors = rubric.anchorClaimCandidateIds.mapNotNull { id -> candidates.find { it.claimCandidateId == id } }
            if (anchors.size != rubric.anchorClaimCandidateIds.size || anchors.isEmpty()) {
                errors += "${rubric.rubricId}: every rubric anchor must resolve."
            }
            anchors.forEach { anchor ->
                if (anchor.tissueId != rubric.tissueId || anchor.mechanicalLoadMode != rubric.mechanicalLoadMode ||
                    anchor.temporalMetric != rubric.temporalMetric || anchor.measurementMetric != rubric.measurementMetric ||
                    anchor.normalizationBasis != rubric.normalizationBasis || anchor.claimUnit != rubric.metricUnit
                ) errors += "${rubric.rubricId}: incompatible anchor ${anchor.claimCandidateId}."
            }
            if (rubric.measurementMetric == TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX) {
                errors += "${rubric.rubricId}: source composite cannot define a rubric."
            }
        }
        if (!requiredResearchTargetIds.all(decisions.map { it.targetId }.toSet()::contains)) {
            errors += "Required lower-limb research targets are missing decisions."
        }
        decisions.forEach { decision ->
            if ((decision.includedSourceIds + decision.excludedSourceIds).any { it !in sourceIds }) {
                errors += "${decision.researchDecisionId}: unknown source reference."
            }
        }
        correspondences.forEach { row ->
            if (row.transferVariables.isEmpty()) errors += "${row.stableKey}: transfer variables are required."
        }
        return TissueValidationReport(errors)
    }

    fun cumulativeSessionImpulse(perEventImpulse: Double?, eventCount: Int?): Double? {
        if (perEventImpulse == null || eventCount == null) return null
        require(eventCount >= 0) { "Event count cannot be negative." }
        return perEventImpulse * eventCount
    }

    fun anomalies(candidates: List<TissueMultidimensionalClaimCandidate>): List<TissueBiomechanicalAnomaly> {
        val anomalies = mutableListOf<TissueBiomechanicalAnomaly>()
        val numeric = candidates.filter { it.claimValue != null }
        numeric.groupBy { listOf(it.sourceId, it.tissueId, it.measurementMetric.name) }.values.forEach { rows ->
            val impactRate = rows.filter { it.temporalMetric == TissueTemporalMetric.LOADING_RATE &&
                listOf("jump", "hop", "landing").any(it.testedExercise.lowercase()::contains) }
            val squatRate = rows.filter { it.temporalMetric == TissueTemporalMetric.LOADING_RATE &&
                "squat" in it.testedExercise.lowercase() }
            if (impactRate.isNotEmpty() && squatRate.isNotEmpty() &&
                impactRate.maxOf { it.claimValue!! } < squatRate.minOf { it.claimValue!! }
            ) anomalies += anomaly(TissueBiomechanicalAnomalyType.HIGH_IMPACT_LOADING_RATE_INVERSION, impactRate + squatRate)
        }
        numeric.groupBy { listOf(it.stableKey, it.tissueId, it.temporalMetric.name, it.measurementMetric.name) }.values.forEach { rows ->
            val equalAcrossLoads = rows.groupBy { it.claimValue }.values.firstOrNull { equalRows ->
                equalRows.map { it.externalLoadCondition }.distinct().size > 1
            }
            if (equalAcrossLoads != null) {
                anomalies += anomaly(TissueBiomechanicalAnomalyType.LOADED_UNLOADED_EQUALITY, equalAcrossLoads)
            }
        }
        numeric.filter { it.tissueId == "KNEE_PATELLOFEMORAL" && it.temporalMetric == TissueTemporalMetric.PEAK }
            .groupBy { it.sourceId to it.stableKey }.values.forEach { rows ->
                val deep = rows.find { "full" in it.romCondition.lowercase() || "deep" in it.romCondition.lowercase() }
                val shallow = rows.find { "60" in it.romCondition }
                if (deep != null && shallow != null && deep.claimValue!! < shallow.claimValue!!) {
                    anomalies += anomaly(TissueBiomechanicalAnomalyType.DEEP_SHALLOW_SQUAT_INVERSION, listOf(deep, shallow))
                }
            }
        candidates.groupBy { it.sourceId to it.stableKey }.values.forEach { rows ->
            val peakBands = rows.filter { it.temporalMetric == TissueTemporalMetric.PEAK }.mapNotNull { it.maximumDefensibleBand }.toSet()
            val impulseBands = rows.filter { it.temporalMetric == TissueTemporalMetric.IMPULSE_PER_EVENT }.mapNotNull { it.maximumDefensibleBand }.toSet()
            if (peakBands.isNotEmpty() && peakBands == impulseBands) {
                anomalies += anomaly(TissueBiomechanicalAnomalyType.COPIED_PEAK_IMPULSE_BAND, rows)
            }
        }
        return anomalies.distinctBy { it.anomalyType to it.affectedIds }
    }

    private fun anomaly(type: TissueBiomechanicalAnomalyType, rows: List<TissueMultidimensionalClaimCandidate>) =
        TissueBiomechanicalAnomaly(type, rows.map { it.claimCandidateId }.toSet(), "Review required; data are not rewritten automatically.")

    private fun validateUnit(
        metric: TissueMeasurementMetric,
        normalization: TissueNormalizationBasis,
        unit: String,
        id: String,
        errors: MutableList<String>
    ) {
        val allowed = when (normalization) {
            TissueNormalizationBasis.BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE -> setOf("BW")
            TissueNormalizationBasis.BODY_WEIGHT_TIME_NORMALIZED_INTERNAL_IMPULSE -> setOf("BW*s")
            TissueNormalizationBasis.BODY_WEIGHT_NORMALIZED_LOADING_RATE -> setOf("BW/s")
            TissueNormalizationBasis.MEASURED_TENDON_STRAIN_PERCENT -> setOf("percent")
            TissueNormalizationBasis.MEASURED_LIGAMENT_STRAIN_PERCENT -> setOf("percent")
            TissueNormalizationBasis.ABSOLUTE_FORCE_NEWTON -> setOf("N")
            TissueNormalizationBasis.RELATIVE_LOAD_PERCENT_1RM -> setOf("BW", "N")
            TissueNormalizationBasis.SOURCE_DEFINED_NORMALIZED_INDEX -> setOf("index")
            else -> setOf(unit)
        }
        if (unit !in allowed) errors += "$id: unit $unit is incompatible with $normalization."
        if (metric == TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX &&
            normalization != TissueNormalizationBasis.SOURCE_DEFINED_NORMALIZED_INDEX
        ) errors += "$id: source composite requires source-defined normalization."
    }

    private fun unique(label: String, ids: List<String>, errors: MutableList<String>) {
        if (ids.distinct().size != ids.size) errors += "Duplicate $label id."
    }
}
