package com.training.trackplanner.analysis.tissue

object TissueMtcParser {
    fun temporalMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { it.enum<TissueMtcTemporalMetric>("temporalMetric") }
    fun measurementMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { it.enum<TissueMtcMeasurementMetric>("measurementMetric") }
    fun evidenceRelations(csv: String) = TissueMetadataParser.table(csv).rows.map { it.enum<TissueMtcEvidenceRelation>("evidenceRelation") }

    fun seedSemanticCorrections(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcSeedSemanticCorrection(
            row.required("correctionId"), row.required("seedId"), row.optional("reportedMetrics"),
            row.optional("exerciseConditions"), row.optionalEnum<TissueMtcEvidenceRelation>("evidenceRelation"),
            row.enum("evidenceRelationReviewStatus"), row.required("publicationIdentityStatus"),
            row.required("publicationIntegrityStatus"), row.required("rationale"), row.required("version")
        )
    }

    fun functionalComplexes(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueFunctionalComplex(
            row.required("complexId"), row.tokens("componentIds"), row.required("outputPolicy"),
            row.tokens("parallelProfileIds"), row.required("researchRationale"), row.required("version"),
            row.required("status")
        )
    }

    fun axisMetricRules(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcAxisMetricRule(
            row.required("axisMetricRuleId"), row.enum("targetType"), row.required("targetId"),
            row.enum("profileKind"), row.enum("axis"), row.tokens("primaryMetricTypes"),
            row.tokens("secondaryMetricTypes"), row.tokens("forbiddenMetricTypes"),
            row.tokens("allowedMeasurementFamilies"), row.tokens("allowedNormalizationBases"),
            row.tokens("requiredContextFields"), row.required("comparisonFamily"),
            row.boolean("rubricEligible"), row.boolean("operationalFallbackEligible"),
            row.required("biomechanicalMeaning"), row.required("limitations"), row.required("version")
        )
    }

    fun dynamicStabilizationProfiles(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueDynamicStabilizationProfile(
            row.required("profileId"), row.required("relatedComplexId"), row.required("magnitudeDemand"),
            row.required("temporalDemand"), row.required("contextDemand"),
            row.boolean("separateFromMechanicalLoad"), row.required("version")
        )
    }

    fun axisScales(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcAxisScale(
            row.required("axisScaleId"), row.required("targetId"), row.enum("axis"),
            row.required("physicalMetricType"), row.required("normalizationBasis"),
            row.required("measurementFamily"), row.required("comparisonFamily"),
            row.required("populationScope"), row.required("conditionFamily"), row.required("version")
        )
    }

    fun rubrics(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcRubric(
            row.required("rubricId"), row.required("axisScaleId"), row.enum("rubricKind"), row.double("score"),
            row.double("lowerBound"), row.double("upperBound"), row.optionalBoolean("lowerInclusive"),
            row.optionalBoolean("upperInclusive"), row.double("anchorValue"), row.tokens("sourceConditionIds"),
            row.tokens("sourceIds"), row.int("independentSourceCount"), row.int("distinctConditionCount"),
            row.tokens("externalValidationSourceIds"), row.value("boundaryDerivation"),
            row.required("sensitivityAnalysisStatus"), row.boolean("researchEligible"), row.boolean("operationalOnly"),
            row.required("status"), row.required("version")
        )
    }

    fun axisProvenance(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcAxisProvenance(
            row.required("provenanceId"), row.required("axisScaleId"), row.required("score").toDouble(),
            row.double("researchScore"), row.required("operationalScore").toDouble(), row.enum("provenanceTier"),
            row.required("confidence"), row.tokens("sourceIds"), row.tokens("metricExtractionIds"),
            row.required("rubricId"), row.required("fallbackRuleId"), row.enum("inheritanceLevel"),
            row.required("limitations"), row.required("coefficientSetId")
        )
    }

    fun fallbackRules(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcFallbackRule(
            row.required("fallbackRuleId"), row.int("priority"), row.enum("inheritanceLevel"),
            row.required("matchRequirement"), row.enum("provenanceTier"), row.required("confidence"),
            row.required("scorePolicy"), row.tokens("forbiddenTransfers"), row.required("allocationPolicy"),
            row.required("coefficientSetId"), row.required("version")
        )
    }

    fun coefficientSets(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueMtcCoefficientSet(
            row.required("coefficientSetId"), row.required("semanticVersion"), row.required("status"),
            row.value("effectiveFrom"), row.value("effectiveTo"), row.value("publishedAt"),
            row.required("sourceSnapshotHash"), row.required("rubricSnapshotHash"),
            row.required("fallbackPolicyVersion"), row.required("exerciseCatalogSnapshotHash"),
            row.required("complexRegistrySnapshotHash"), row.required("axisRegistrySnapshotHash"),
            row.value("supersedesCoefficientSetId"), row.required("changeReason"), row.required("preparedBy"),
            row.enum("preparedByType")
        )
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.optional(name: String) = value(name).takeIf(String::isNotBlank)
    private fun Map<String, String>.required(name: String) = value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.tokens(name: String) = value(name).split('|').map(String::trim).filter(String::isNotBlank).toSet()
    private fun Map<String, String>.boolean(name: String) = value(name).equals("true", true)
    private fun Map<String, String>.optionalBoolean(name: String) = value(name).takeIf(String::isNotBlank)?.equals("true", true)
    private fun Map<String, String>.double(name: String) = value(name).toDoubleOrNull()
    private fun Map<String, String>.int(name: String) = required(name).toInt()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T = enumValueOf(required(name).uppercase())
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        optional(name)?.uppercase()?.let { enumValueOf<T>(it) }
}
