package com.training.trackplanner.analysis.tissue

enum class TissueMtcAxis { M, T, C }
enum class TissueMtcTargetType { TISSUE, FUNCTIONAL_COMPLEX, DYNAMIC_STABILIZATION }
enum class TissueMtcProfileKind { TISSUE_MECHANICAL_LOAD, DYNAMIC_STABILIZATION }
enum class TissueMtcTemporalMetric { PEAK, EVENT_AVERAGE, PHASE_AVERAGE }
enum class TissueMtcMeasurementMetric { MODELED_JOINT_CONTACT_FORCE, INTERSEGMENTAL_JOINT_FORCE_RESULTANT }
enum class TissueMtcEvidenceRelation {
    DIRECT_INTERNAL_MEASUREMENT, VALIDATED_INTERNAL_MODEL, VALIDATED_PROXY, UNVALIDATED_PROXY, CONTEXT_ONLY,
    CADAVERIC_MECHANISM, FINITE_ELEMENT_MECHANISM
}

data class TissueFunctionalComplex(
    val complexId: String,
    val componentIds: Set<String>,
    val outputPolicy: String,
    val parallelProfileIds: Set<String>,
    val researchRationale: String,
    val version: String,
    val status: String
)

data class TissueMtcAxisMetricRule(
    val axisMetricRuleId: String,
    val targetType: TissueMtcTargetType,
    val targetId: String,
    val profileKind: TissueMtcProfileKind,
    val axis: TissueMtcAxis,
    val primaryMetricTypes: Set<String>,
    val secondaryMetricTypes: Set<String>,
    val forbiddenMetricTypes: Set<String>,
    val allowedMeasurementFamilies: Set<String>,
    val allowedNormalizationBases: Set<String>,
    val requiredContextFields: Set<String>,
    val comparisonFamily: String,
    val rubricEligible: Boolean,
    val operationalFallbackEligible: Boolean,
    val biomechanicalMeaning: String,
    val limitations: String,
    val version: String
)

data class TissueDynamicStabilizationProfile(
    val profileId: String,
    val relatedComplexId: String,
    val magnitudeDemand: String,
    val temporalDemand: String,
    val contextDemand: String,
    val separateFromMechanicalLoad: Boolean,
    val version: String
)
