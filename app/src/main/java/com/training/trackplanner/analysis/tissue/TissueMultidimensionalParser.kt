package com.training.trackplanner.analysis.tissue

object TissueMultidimensionalParser {
    fun mechanicalLoadModes(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMechanicalLoadModeDefinition(
            mechanicalLoadMode = row.enum("mechanicalLoadMode"),
            tissueClasses = row.enums("tissueClasses"),
            biomechanicalMeaning = row.required("biomechanicalMeaning"),
            clinicalInterpretationBoundary = row.required("clinicalInterpretationBoundary")
        )
    }

    fun temporalMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueTemporalMetricDefinition(
            temporalMetric = row.enum("temporalMetric"),
            metricOrigin = row.enum("metricOrigin"),
            biomechanicalMeaning = row.required("biomechanicalMeaning"),
            aggregationBoundary = row.required("aggregationBoundary")
        )
    }

    fun measurementMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMeasurementMetricDefinition(
            measurementMetric = row.enum("measurementMetric"),
            measurementFamily = row.required("measurementFamily"),
            metricOrigin = row.enum("metricOrigin"),
            compatibleMechanicalLoadModes = row.enums("compatibleMechanicalLoadModes"),
            compatibleTemporalMetrics = row.enums("compatibleTemporalMetrics"),
            requiredModelAssumptions = row.required("requiredModelAssumptions")
        )
    }

    fun normalizations(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueNormalizationDefinition(
            normalizationBasis = row.enum("normalizationBasis"),
            unitFamily = row.required("unitFamily"),
            biomechanicalMeaning = row.required("biomechanicalMeaning"),
            compatibleMeasurementMetrics = row.enums("compatibleMeasurementMetrics")
        )
    }

    fun dimensions(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueLoadDimensionDefinition(
            dimensionId = row.required("dimensionId"),
            tissueClass = row.enum("tissueClass"),
            tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"),
            temporalMetric = row.enum("temporalMetric"),
            allowedMeasurementMetrics = row.enums("allowedMeasurementMetrics"),
            allowedNormalizationBases = row.enums("allowedNormalizationBases"),
            metricOrigin = row.enum("sourceObservedOrDerived"),
            derivedFormulaId = row.value("derivedFormulaId"),
            biomechanicalMeaning = row.required("biomechanicalMeaning"),
            clinicalInterpretationBoundary = row.required("clinicalInterpretationBoundary"),
            minimumEvidenceLevel = row.required("minimumEvidenceLevel"),
            rubricEligible = row.boolean("rubricEligible"),
            profileEligible = row.boolean("profileEligible"),
            deprecatedLegacyDimensions = row.enums("deprecatedLegacyDimensions"),
            migrationNotes = row.required("migrationNotes")
        )
    }

    fun migrations(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueLegacyDimensionMigration(
            migrationId = row.required("migrationId"),
            legacyDimension = row.enum("legacyDimension"),
            targetMechanicalLoadMode = row.optionalEnum<TissueMechanicalLoadMode>("targetMechanicalLoadMode"),
            targetTemporalMetric = row.optionalEnum<TissueTemporalMetric>("targetTemporalMetric"),
            targetMeasurementMetric = row.optionalEnum<TissueMeasurementMetric>("targetMeasurementMetric"),
            migrationDecision = row.enum("migrationDecision"),
            affectedClaimIds = row.tokens("affectedClaimIds"),
            affectedRubricIds = row.tokens("affectedRubricIds"),
            ambiguityReason = row.value("ambiguityReason"),
            requiredManualReview = row.boolean("requiredManualReview"),
            migrationNotes = row.required("migrationNotes")
        )
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) =
        value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.boolean(name: String) = when (value(name).uppercase()) {
        "TRUE", "1", "YES" -> true
        "FALSE", "0", "NO", "" -> false
        else -> error("Invalid boolean in $name: ${value(name)}")
    }
    private fun Map<String, String>.tokens(name: String) =
        value(name).split('|').map(String::trim).filter(String::isNotBlank).toSet()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T =
        enumValueOf(required(name).uppercase())
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        value(name).takeIf(String::isNotBlank)?.uppercase()?.let { enumValueOf<T>(it) }
    private inline fun <reified T : Enum<T>> Map<String, String>.enums(name: String): Set<T> =
        tokens(name).mapTo(linkedSetOf()) { enumValueOf(it.uppercase()) }
}
