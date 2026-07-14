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

    fun scientificRows(csv: String, idColumn: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueC31ScientificEvidenceRow(
            id = row.required(idColumn), sourceId = row.required("sourceId"), tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"), temporalMetric = row.enum("temporalMetric"),
            measurementMetric = row.enum("measurementMetric"), normalizationBasis = row.enum("normalizationBasis"),
            eventContext = row.optionalEnum<TissueEventContext>("eventContext"),
            movementPhase = row.optionalEnum<TissueMovementPhase>("movementPhase"),
            positionContext = row.optionalEnum<TissuePositionContext>("positionContext"),
            functionalDemand = row.optionalEnum<TissueFunctionalDemand>("functionalDemand"),
            tissueResponseMetric = row.optionalEnum<TissueResponseMetric>("tissueResponseMetric"), evidenceRelation = row.enum("evidenceRelation"),
            proxyMappingId = row.value("proxyMappingId"), additionalExternalLoadFractionBw = row.double("additionalExternalLoadFractionBw"),
            totalSystemMassFractionBw = row.double("totalSystemMassFractionBw"),
            externalLoadDescription = row.required("externalLoadDescription"),
            peakTimingRelativeToContactMs = row.double("peakTimingRelativeToContactMs")
        )
    }

    fun researchDecisions(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueC31ResearchDecisionRow(
            researchDecisionId = row.required("researchDecisionId"),
            mechanicalLoadMode = row.optionalEnum<TissueC31MechanicalLoadMode>("mechanicalLoadMode"),
            eventContext = row.optionalEnum<TissueEventContext>("eventContext"),
            movementPhase = row.optionalEnum<TissueMovementPhase>("movementPhase"),
            functionalDemand = row.optionalEnum<TissueFunctionalDemand>("functionalDemand"),
            tissueResponseMetric = row.optionalEnum<TissueResponseMetric>("tissueResponseMetric"),
            evidenceRelation = row.enum("evidenceRelation"), decision = row.enum("decision")
        )
    }

    fun correctionDispositions(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueC31CorrectionDisposition(
            row.required("correctionDispositionId"), row.required("affectedArtifactType"), row.required("affectedArtifactId"),
            row.value("replacementArtifactId"), row.enum("correctionDecision")
        )
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) = value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.boolean(name: String) = value(name).equals("true", ignoreCase = true)
    private fun Map<String, String>.double(name: String) = value(name).takeIf(String::isNotBlank)?.toDouble()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T = enumValueOf(required(name).uppercase())
    private inline fun <reified T : Enum<T>> Map<String, String>.enums(name: String): Set<T> =
        value(name).split('|').filter(String::isNotBlank).mapTo(linkedSetOf()) { enumValueOf(it.uppercase()) }
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        value(name).takeIf(String::isNotBlank)?.let { enumValueOf<T>(it.uppercase()) }
}
