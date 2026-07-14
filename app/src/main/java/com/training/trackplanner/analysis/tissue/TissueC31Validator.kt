package com.training.trackplanner.analysis.tissue

object TissueC31Validator {
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
