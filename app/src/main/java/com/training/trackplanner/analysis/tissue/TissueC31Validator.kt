package com.training.trackplanner.analysis.tissue

object TissueC31Validator {
    fun correctedResearch(
        extractions: List<TissueC31ScientificEvidenceRow>, candidates: List<TissueC31ScientificEvidenceRow>,
        decisions: List<TissueC31ResearchDecisionRow>, corrections: List<TissueC31CorrectionDisposition>,
        dimensions: List<TissueC31LoadDimensionDefinition>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (extractions.size != 49 || candidates.size != 30 || decisions.size != 48 || corrections.size != 188) errors += "C3.1 accounting mismatch."
        if (extractions.map { it.id }.distinct().size != extractions.size || candidates.map { it.id }.distinct().size != candidates.size) errors += "Duplicate corrected evidence ID."
        val dimensionKeys = dimensions.map { Triple(it.tissueId, it.mechanicalLoadMode, it.temporalMetric) }.toSet()
        (extractions + candidates).forEach { row ->
            if (row.measurementMetric != TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX && Triple(row.tissueId, row.mechanicalLoadMode, row.temporalMetric) !in dimensionKeys) errors += "${row.id}: unregistered corrected dimension."
            if (row.measurementMetric == TissueMeasurementMetric.MEASURED_LIGAMENT_STRAIN && row.mechanicalLoadMode != TissueC31MechanicalLoadMode.TENSION) errors += "${row.id}: ligament strain must use TENSION."
            if (row.evidenceRelation == TissueEvidenceRelation.VALIDATED_PROXY && row.proxyMappingId.isBlank()) errors += "${row.id}: validated proxy requires a mapping."
            if (row.additionalExternalLoadFractionBw != null && row.totalSystemMassFractionBw != null && kotlin.math.abs(row.totalSystemMassFractionBw - (1.0 + row.additionalExternalLoadFractionBw)) > 1e-9) errors += "${row.id}: added load and total mass conflict."
            if ("1.2 bodyweight external-load" in row.externalLoadDescription.lowercase()) errors += "${row.id}: total system mass is mislabeled as added load."
        }
        if (candidates.any { it.evidenceRelation in setOf(TissueEvidenceRelation.UNVALIDATED_PROXY, TissueEvidenceRelation.CONTEXT_ONLY) }) errors += "Unvalidated/context evidence cannot become a claim candidate."
        val acl = extractions.filter { it.tissueId == "KNEE_ACL" && it.measurementMetric == TissueMeasurementMetric.MEASURED_LIGAMENT_STRAIN }
        if (acl.size != 2 || acl.any { it.eventContext != TissueEventContext.JUMP_LANDING || it.movementPhase != TissueMovementPhase.PRE_CONTACT || it.peakTimingRelativeToContactMs != -55.0 }) errors += "ACL landing strain context or timing is incorrect."
        val weighted = extractions.singleOrNull { it.id == "C31METRIC_35142563_ACH_HEEL_ADDED20BW" }
        if (weighted?.additionalExternalLoadFractionBw != 0.20 || weighted.totalSystemMassFractionBw != 1.20) errors += "Weighted heel-rise load semantics are incorrect."
        if (corrections.groupingBy { it.affectedArtifactType }.eachCount() != mapOf("MECHANICAL_MODE" to 17, "VALID_DIMENSION" to 42, "METRIC_EXTRACTION" to 49, "CLAIM_CANDIDATE" to 30, "RUBRIC_OR_ANCHOR" to 2, "RESEARCH_DECISION" to 48)) errors += "Full impact disposition is incomplete."
        return TissueValidationReport(errors)
    }

    fun ontology(
        modeRows: List<TissueC31MechanicalLoadModeDefinition>, eventRows: List<TissueC31RegistryEntry>,
        phaseRows: List<TissueC31RegistryEntry>, positionRows: List<TissueC31RegistryEntry>,
        demandRows: List<TissueC31RegistryEntry>, responseRows: List<TissueC31RegistryEntry>,
        relationRows: List<TissueC31RegistryEntry>, rubricRows: List<TissueC31RegistryEntry>,
        loadRows: List<TissueC31RegistryEntry>, measurements: List<TissueC31MeasurementMetricDefinition>,
        dimensions: List<TissueC31LoadDimensionDefinition>, catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (modeRows.map { it.mechanicalLoadMode }.toSet() != TissueC31MechanicalLoadMode.entries.toSet()) errors += "Mechanical mode registry is incomplete."
        closed("event context", eventRows, TissueEventContext.entries, errors)
        closed("movement phase", phaseRows, TissueMovementPhase.entries, errors)
        closed("position context", positionRows, TissuePositionContext.entries, errors)
        closed("functional demand", demandRows, TissueFunctionalDemand.entries, errors)
        closed("tissue response", responseRows, TissueResponseMetric.entries, errors)
        closed("evidence relation", relationRows, TissueEvidenceRelation.entries, errors)
        closed("rubric kind", rubricRows, TissueRubricKind.entries, errors)
        closed("external-load representation", loadRows, TissueExternalLoadRepresentation.entries, errors)

        val measurementById = measurements.associateBy { it.measurementMetric }
        val catalogById = catalog.associateBy { it.tissueId }
        if (measurements.map { it.measurementMetric }.toSet() != TissueMeasurementMetric.entries.toSet()) errors += "Measurement registry is incomplete."
        if (dimensions.map { it.dimensionId }.distinct().size != dimensions.size) errors += "Duplicate C3.1 dimension ID."
        dimensions.forEach { dimension ->
            if (catalogById[dimension.tissueId]?.tissueClass != dimension.tissueClass) errors += "${dimension.dimensionId}: tissue mismatch."
            if (dimension.allowedMeasurementMetrics.isEmpty() || dimension.allowedNormalizationBases.isEmpty() || dimension.allowedEvidenceRelations.isEmpty()) {
                errors += "${dimension.dimensionId}: compatibility must be explicit."
            }
            dimension.allowedMeasurementMetrics.forEach { metric ->
                val definition = measurementById[metric]
                if (definition == null || dimension.mechanicalLoadMode !in definition.compatibleMechanicalLoadModes || dimension.temporalMetric !in definition.compatibleTemporalMetrics) {
                    errors += "${dimension.dimensionId}: incompatible measurement $metric."
                }
            }
            if ((TissueEvidenceRelation.UNVALIDATED_PROXY in dimension.allowedEvidenceRelations || TissueEvidenceRelation.CONTEXT_ONLY in dimension.allowedEvidenceRelations) &&
                (dimension.rubricEligible || dimension.profileEligible)) errors += "${dimension.dimensionId}: unvalidated/context evidence cannot be eligible."
        }
        val tension = modeRows.singleOrNull { it.mechanicalLoadMode == TissueC31MechanicalLoadMode.TENSION }
        if (TissueClass.LIGAMENT !in (tension?.tissueClasses ?: emptySet())) errors += "TENSION must support ligament."
        val acl = dimensions.singleOrNull { it.dimensionId == "ACL_TENSION_PEAK" }
        if (acl?.mechanicalLoadMode != TissueC31MechanicalLoadMode.TENSION || TissueMeasurementMetric.MEASURED_LIGAMENT_STRAIN !in (acl?.allowedMeasurementMetrics ?: emptySet())) {
            errors += "ACL ligament tension/strain dimension is missing."
        }
        return TissueValidationReport(errors)
    }

    private fun <T : Enum<T>> closed(name: String, rows: List<TissueC31RegistryEntry>, values: List<T>, errors: MutableList<String>) {
        if (rows.map { it.id }.toSet() != values.map { it.name }.toSet()) errors += "$name registry is incomplete."
    }
}
