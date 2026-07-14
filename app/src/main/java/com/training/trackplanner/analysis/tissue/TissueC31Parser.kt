package com.training.trackplanner.analysis.tissue

object TissueC31Parser {
    fun mechanicalLoadModes(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueC31MechanicalLoadModeDefinition(
            mechanicalLoadMode = row.enum("mechanicalLoadMode"), tissueClasses = row.enums("tissueClasses"),
            biomechanicalMeaning = row.required("biomechanicalMeaning"),
            clinicalInterpretationBoundary = row.required("clinicalInterpretationBoundary")
        )
    }

    fun registry(csv: String) = TissueMetadataParser.table(csv).rows.map {
        TissueC31RegistryEntry(it.required("id"), it.required("definition"), it.required("scientificBoundary"))
    }

    fun measurementMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueC31MeasurementMetricDefinition(
            measurementMetric = row.enum("measurementMetric"),
            compatibleMechanicalLoadModes = row.enums("compatibleMechanicalLoadModes"),
            compatibleTemporalMetrics = row.enums("compatibleTemporalMetrics"),
            allowedEvidenceRelations = row.enums("allowedEvidenceRelations")
        )
    }

    fun dimensions(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueC31LoadDimensionDefinition(
            dimensionId = row.required("dimensionId"), c3DimensionId = row.required("c3DimensionId"),
            tissueClass = row.enum("tissueClass"), tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"), temporalMetric = row.enum("temporalMetric"),
            allowedMeasurementMetrics = row.enums("allowedMeasurementMetrics"),
            allowedNormalizationBases = row.enums("allowedNormalizationBases"),
            allowedEvidenceRelations = row.enums("allowedEvidenceRelations"),
            allowedTissueResponseMetrics = row.enums("allowedTissueResponseMetrics"),
            rubricEligible = row.boolean("rubricEligible"), profileEligible = row.boolean("profileEligible"),
            correctionStatus = row.required("correctionStatus")
        )
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) = value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.boolean(name: String) = value(name).equals("true", ignoreCase = true)
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T = enumValueOf(required(name).uppercase())
    private inline fun <reified T : Enum<T>> Map<String, String>.enums(name: String): Set<T> =
        value(name).split('|').filter(String::isNotBlank).mapTo(linkedSetOf()) { enumValueOf(it.uppercase()) }
}
