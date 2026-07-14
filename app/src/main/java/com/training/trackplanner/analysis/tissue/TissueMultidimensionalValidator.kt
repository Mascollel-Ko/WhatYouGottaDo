package com.training.trackplanner.analysis.tissue

object TissueMultidimensionalValidator {
    fun ontology(
        modes: List<TissueMechanicalLoadModeDefinition>,
        temporalMetrics: List<TissueTemporalMetricDefinition>,
        measurementMetrics: List<TissueMeasurementMetricDefinition>,
        normalizations: List<TissueNormalizationDefinition>,
        dimensions: List<TissueLoadDimensionDefinition>,
        migrations: List<TissueLegacyDimensionMigration>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        uniqueAndComplete("mechanical load mode", modes.map { it.mechanicalLoadMode }, TissueMechanicalLoadMode.entries, errors)
        uniqueAndComplete("temporal metric", temporalMetrics.map { it.temporalMetric }, TissueTemporalMetric.entries, errors)
        uniqueAndComplete("measurement metric", measurementMetrics.map { it.measurementMetric }, TissueMeasurementMetric.entries, errors)
        uniqueAndComplete("normalization", normalizations.map { it.normalizationBasis }, TissueNormalizationBasis.entries, errors)
        uniqueAndComplete("legacy migration", migrations.map { it.legacyDimension }, TissueLoadDimension.entries, errors)

        val modeById = modes.associateBy { it.mechanicalLoadMode }
        val temporalById = temporalMetrics.associateBy { it.temporalMetric }
        val measurementById = measurementMetrics.associateBy { it.measurementMetric }
        val normalizationById = normalizations.associateBy { it.normalizationBasis }
        val catalogById = catalog.associateBy { it.tissueId }
        if (dimensions.map { it.dimensionId }.distinct().size != dimensions.size) errors += "Duplicate dimensionId."
        if (dimensions.map { Triple(it.tissueId, it.mechanicalLoadMode, it.temporalMetric) }.distinct().size != dimensions.size) {
            errors += "Duplicate tissue/load-mode/temporal-metric dimension."
        }
        dimensions.forEach { dimension ->
            val tissue = catalogById[dimension.tissueId]
            if (tissue == null || tissue.tissueClass != dimension.tissueClass) {
                errors += "${dimension.dimensionId}: unknown tissue or tissue-class mismatch."
            }
            if (dimension.mechanicalLoadMode !in modeById || dimension.tissueClass !in modeById[dimension.mechanicalLoadMode]!!.tissueClasses) {
                errors += "${dimension.dimensionId}: invalid mechanical load mode for tissue class."
            }
            if (dimension.temporalMetric !in temporalById) errors += "${dimension.dimensionId}: unknown temporal metric."
            if (dimension.allowedMeasurementMetrics.isEmpty() || dimension.allowedNormalizationBases.isEmpty()) {
                errors += "${dimension.dimensionId}: measurement and normalization compatibility must be explicit."
            }
            dimension.allowedMeasurementMetrics.forEach { metric ->
                val definition = measurementById[metric]
                if (definition == null || dimension.mechanicalLoadMode !in definition.compatibleMechanicalLoadModes ||
                    dimension.temporalMetric !in definition.compatibleTemporalMetrics
                ) errors += "${dimension.dimensionId}: incompatible measurement metric $metric."
            }
            dimension.allowedNormalizationBases.forEach { normalization ->
                val definition = normalizationById[normalization]
                if (definition == null || dimension.allowedMeasurementMetrics.none(definition.compatibleMeasurementMetrics::contains)) {
                    errors += "${dimension.dimensionId}: incompatible normalization $normalization."
                }
            }
            if (dimension.metricOrigin == TissueMetricOrigin.APPLICATION_DERIVED && dimension.derivedFormulaId.isBlank()) {
                errors += "${dimension.dimensionId}: derived dimension requires a formula."
            }
            if (TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX in dimension.allowedMeasurementMetrics &&
                (dimension.rubricEligible || dimension.profileEligible)
            ) errors += "${dimension.dimensionId}: source-specific composite cannot be rubric or profile eligible."
        }
        migrations.forEach { migration ->
            val exact = migration.migrationDecision == TissueLegacyDimensionMigrationDecision.EXACT_MIGRATION
            if (exact && (migration.targetMechanicalLoadMode == null || migration.targetTemporalMetric == null)) {
                errors += "${migration.migrationId}: exact migration requires load mode and temporal metric."
            }
            if (!exact && !migration.requiredManualReview) {
                errors += "${migration.migrationId}: non-exact migration requires manual review."
            }
            if (migration.migrationDecision == TissueLegacyDimensionMigrationDecision.SOURCE_SPECIFIC_ONLY &&
                migration.targetMeasurementMetric != TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX
            ) errors += "${migration.migrationId}: source-specific migration requires the composite metric."
        }
        return TissueValidationReport(errors)
    }

    private fun <T> uniqueAndComplete(name: String, actual: List<T>, expected: List<T>, errors: MutableList<String>) {
        if (actual.distinct().size != actual.size) errors += "Duplicate $name."
        if (actual.toSet() != expected.toSet()) errors += "$name registry is incomplete."
    }
}
