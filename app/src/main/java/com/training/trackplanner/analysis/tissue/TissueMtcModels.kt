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
enum class TissueMtcRubricKind { ABSOLUTE_INTERVAL, CONDITION_ANCHOR, ORDERING_RULE, FAMILY_DEFAULT, CONSERVATIVE_FALLBACK }
enum class TissueMtcProvenanceTier {
    DIRECT_INTERNAL_MEASUREMENT, VALIDATED_INTERNAL_MODEL, VALIDATED_PROXY, CLOSE_CONDITION_ESTIMATE,
    CLOSE_VARIANT_ESTIMATE, COMPLEX_LEVEL_ESTIMATE, MOVEMENT_FAMILY_DEFAULT, CONSERVATIVE_FALLBACK
}
enum class TissueMtcInheritanceLevel { EXACT_CONDITION, STABLE_KEY_BASE, CLOSE_VARIANT, FUNCTIONAL_COMPLEX, MOVEMENT_FAMILY, CONSERVATIVE_FALLBACK }

data class TissueMtcAxisScale(
    val axisScaleId: String,
    val targetId: String,
    val axis: TissueMtcAxis,
    val physicalMetricType: String,
    val normalizationBasis: String,
    val measurementFamily: String,
    val comparisonFamily: String,
    val populationScope: String,
    val conditionFamily: String,
    val version: String
)

data class TissueMtcRubric(
    val rubricId: String,
    val axisScaleId: String,
    val rubricKind: TissueMtcRubricKind,
    val score: Double?,
    val lowerBound: Double?,
    val upperBound: Double?,
    val lowerInclusive: Boolean?,
    val upperInclusive: Boolean?,
    val anchorValue: Double?,
    val sourceConditionIds: Set<String>,
    val sourceIds: Set<String>,
    val independentSourceCount: Int,
    val distinctConditionCount: Int,
    val externalValidationSourceIds: Set<String>,
    val boundaryDerivation: String,
    val sensitivityAnalysisStatus: String,
    val researchEligible: Boolean,
    val operationalOnly: Boolean,
    val status: String,
    val version: String
)

data class TissueMtcAxisProvenance(
    val provenanceId: String,
    val axisScaleId: String,
    val score: Double,
    val researchScore: Double?,
    val operationalScore: Double,
    val provenanceTier: TissueMtcProvenanceTier,
    val confidence: String,
    val sourceIds: Set<String>,
    val metricExtractionIds: Set<String>,
    val rubricId: String,
    val fallbackRuleId: String,
    val inheritanceLevel: TissueMtcInheritanceLevel,
    val limitations: String,
    val coefficientSetId: String
)

data class TissueMtcFallbackRule(
    val fallbackRuleId: String,
    val priority: Int,
    val inheritanceLevel: TissueMtcInheritanceLevel,
    val matchRequirement: String,
    val provenanceTier: TissueMtcProvenanceTier,
    val confidence: String,
    val scorePolicy: String,
    val forbiddenTransfers: Set<String>,
    val allocationPolicy: String,
    val coefficientSetId: String,
    val version: String
)

data class TissueMtcCoefficientSet(
    val coefficientSetId: String,
    val semanticVersion: String,
    val status: String,
    val effectiveFrom: String,
    val effectiveTo: String,
    val publishedAt: String,
    val sourceSnapshotHash: String,
    val rubricSnapshotHash: String,
    val fallbackPolicyVersion: String,
    val exerciseCatalogSnapshotHash: String,
    val complexRegistrySnapshotHash: String,
    val axisRegistrySnapshotHash: String,
    val supersedesCoefficientSetId: String,
    val changeReason: String,
    val preparedBy: String,
    val preparedByType: TissueActorType
)

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
