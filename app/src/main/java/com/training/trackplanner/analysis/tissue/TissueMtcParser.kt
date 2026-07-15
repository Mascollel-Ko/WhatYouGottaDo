package com.training.trackplanner.analysis.tissue

object TissueMtcParser {
    fun temporalMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { it.enum<TissueMtcTemporalMetric>("temporalMetric") }
    fun measurementMetrics(csv: String) = TissueMetadataParser.table(csv).rows.map { it.enum<TissueMtcMeasurementMetric>("measurementMetric") }
    fun evidenceRelations(csv: String) = TissueMetadataParser.table(csv).rows.map { it.enum<TissueMtcEvidenceRelation>("evidenceRelation") }

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

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) = value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.tokens(name: String) = value(name).split('|').map(String::trim).filter(String::isNotBlank).toSet()
    private fun Map<String, String>.boolean(name: String) = value(name).equals("true", true)
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T = enumValueOf(required(name).uppercase())
}
