package com.training.trackplanner.analysis.tissue

enum class TissueClass {
    JOINT,
    TENDON,
    LIGAMENT,
    FASCIA
}

enum class TissueLoadDimension {
    COMPRESSION,
    ANTERIOR_POSTERIOR_SHEAR,
    ROTATIONAL_SHEAR,
    IMPACT_IMPULSE,
    STABILITY_DEMAND,
    END_RANGE_STRESS,
    CYCLIC_MECHANICAL_EXPOSURE,
    PEAK_TENSILE_LOAD,
    CYCLIC_TENSILE_LOAD,
    ECCENTRIC_LOAD,
    ENERGY_STORAGE_RELEASE,
    COMPRESSIVE_TENDON_LOAD,
    LOADING_RATE,
    ISOMETRIC_DURATION,
    STRETCH_UNDER_LOAD,
    ANTERIOR_TRANSLATION,
    POSTERIOR_TRANSLATION,
    VALGUS,
    VARUS,
    INTERNAL_ROTATION,
    EXTERNAL_ROTATION,
    INVERSION,
    EVERSION,
    END_RANGE_RESTRAINT,
    DECELERATION_STABILIZATION,
    IMPACT_STABILIZATION,
    TENSILE_LOAD,
    CYCLIC_LOAD,
    COMPRESSIVE_LOAD;

    companion object {
        val byClass: Map<TissueClass, Set<TissueLoadDimension>> = mapOf(
            TissueClass.JOINT to setOf(
                COMPRESSION,
                ANTERIOR_POSTERIOR_SHEAR,
                ROTATIONAL_SHEAR,
                IMPACT_IMPULSE,
                STABILITY_DEMAND,
                END_RANGE_STRESS,
                CYCLIC_MECHANICAL_EXPOSURE
            ),
            TissueClass.TENDON to setOf(
                PEAK_TENSILE_LOAD,
                CYCLIC_TENSILE_LOAD,
                ECCENTRIC_LOAD,
                ENERGY_STORAGE_RELEASE,
                COMPRESSIVE_TENDON_LOAD,
                LOADING_RATE,
                ISOMETRIC_DURATION,
                STRETCH_UNDER_LOAD
            ),
            TissueClass.LIGAMENT to setOf(
                ANTERIOR_TRANSLATION,
                POSTERIOR_TRANSLATION,
                VALGUS,
                VARUS,
                INTERNAL_ROTATION,
                EXTERNAL_ROTATION,
                INVERSION,
                EVERSION,
                END_RANGE_RESTRAINT,
                DECELERATION_STABILIZATION,
                IMPACT_STABILIZATION
            ),
            TissueClass.FASCIA to setOf(
                TENSILE_LOAD,
                CYCLIC_LOAD,
                ENERGY_STORAGE_RELEASE,
                COMPRESSIVE_LOAD,
                LOADING_RATE,
                STRETCH_UNDER_LOAD
            )
        )
    }
}

enum class TissueLoadBand {
    NONE,
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH
}

enum class TissueEvidenceStatus {
    NOT_YET_EVALUATED,
    EVALUATED_ABSENT,
    STUDY_BACKED,
    MODEL_BACKED,
    ESTIMATED_TRANSFER,
    ANATOMICAL_INFERENCE,
    CONFLICTING_EVIDENCE,
    BLOCKED_INSUFFICIENT_EVIDENCE
}

enum class TissueScopeStatus {
    NOT_YET_EVALUATED,
    EVALUATED_IRRELEVANT,
    EVALUATED_RELEVANT,
    CONFLICTING,
    BLOCKED
}

enum class TissueActorType {
    AI_AGENT,
    HUMAN,
    AUTOMATED_VALIDATOR
}

enum class TissueValidationStatus {
    NOT_RUN,
    PASS,
    PASS_WITH_WARNINGS,
    FAIL,
    NOT_APPLICABLE
}

enum class TissueAuditDecision {
    FOUNDATION_COMPLETE_CANDIDATE,
    FOUNDATION_PARTIAL,
    PRODUCTION_REVIEW_REQUIRED,
    BLOCKED
}

enum class TissueRubricStatus {
    DRAFT_RESEARCHED_PENDING_BLIND_REVIEW,
    BLOCKED_INSUFFICIENT_EVIDENCE,
    CONFLICTING_EVIDENCE,
    BLIND_REVIEWED_PENDING_HUMAN_APPROVAL,
    APPROVED
}

enum class TissueRubricAssignmentMethod {
    DIRECT_IN_VIVO_MEASUREMENT,
    VALIDATED_EXACT_MODEL,
    EXACT_KINETIC_PROXY,
    WITHIN_STUDY_RELATIVE_ORDER,
    INTERPOLATED_BETWEEN_ANCHORS,
    CLOSE_VARIANT_TRANSFER,
    MOVEMENT_FAMILY_TRANSFER,
    ANATOMICAL_INFERENCE
}

enum class TissueEvidenceConfidenceLevel {
    HIGH,
    MODERATE,
    LOW,
    VERY_LOW
}

data class TissueCatalogEntry(
    val tissueClass: TissueClass,
    val tissueId: String,
    val displayGroup: String,
    val anatomicalRegion: String,
    val anatomicalName: String,
    val functionalDescription: String,
    val isTrueJoint: Boolean,
    val supportsLaterality: Boolean,
    val supportedLoadDimensions: Set<TissueLoadDimension>,
    val defaultScopePolicy: String,
    val catalogVersion: String,
    val catalogEvidenceType: String,
    val catalogVerificationStatus: String,
    val sourceRefs: List<String>,
    val catalogNotes: String
)

data class TissueLoadProfile(
    val profileRowId: String,
    val stableKey: String,
    val tissueClass: TissueClass,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val loadBand: TissueLoadBand?,
    val evidenceStatus: TissueEvidenceStatus,
    val evidenceLevel: String,
    val confidenceLevel: String,
    val reviewStatus: String,
    val productionEligibility: Boolean,
    val doseBasis: String,
    val referenceConditionId: String,
    val modifierSetId: String,
    val recoveryProfileId: String,
    val sideAllocationPolicy: String,
    val rubricId: String,
    val evidenceSetId: String,
    val evidenceClaimIds: List<String>,
    val sourceRefs: List<String>,
    val reviewBatchId: String,
    val preparedBy: String,
    val preparedByType: TissueActorType?,
    val preparedAt: String,
    val blindReviewedBy: String,
    val blindReviewedByType: TissueActorType?,
    val blindReviewedAt: String,
    val humanApprovedBy: String,
    val humanApprovedAt: String,
    val reviewNotes: String
) {
    val identity: String
        get() = listOf(stableKey, tissueId, loadDimension.name, referenceConditionId).joinToString("|")
}

data class TissueScopeEntry(
    val stableKey: String,
    val tissueClass: TissueClass,
    val tissueId: String,
    val scopeStatus: TissueScopeStatus,
    val legacySeedTags: List<String>,
    val reviewPriority: String,
    val reviewBatchId: String,
    val productionEligibility: Boolean,
    val reviewedCatalogVersion: String,
    val preparedBy: String,
    val preparedByType: TissueActorType?,
    val preparedAt: String,
    val blindReviewedBy: String,
    val blindReviewedByType: TissueActorType?,
    val blindReviewedAt: String,
    val humanApprovedBy: String,
    val humanApprovedAt: String,
    val reviewNotes: String
)

data class TissueLoadRubric(
    val rubricId: String,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val loadBand: TissueLoadBand,
    val metricType: String,
    val metricLowerBound: Double?,
    val metricUpperBound: Double?,
    val metricUnit: String,
    val anchorStableKeys: List<String>,
    val anchorConditions: String,
    val anchorClaimIds: List<String>,
    val researchDecisionId: String,
    val draftClaimIds: List<String>,
    val assignmentMethod: TissueRubricAssignmentMethod,
    val evidenceSetId: String,
    val evidenceClaimIds: List<String>,
    val sourceRefs: List<String>,
    val confidenceLevel: TissueEvidenceConfidenceLevel,
    val rubricStatus: TissueRubricStatus,
    val preparedBy: String,
    val preparedByType: TissueActorType,
    val preparedAt: String,
    val blindReviewedBy: String,
    val blindReviewedByType: TissueActorType?,
    val blindReviewedAt: String,
    val humanApprovedBy: String,
    val humanApprovedAt: String,
    val rubricNotes: String
)

data class TissueMetadataAuditManifest(
    val values: Map<String, String>
) {
    val auditManifestId: String get() = values.getValue("auditManifestId")
    val inputSnapshotHash: String get() = values.getValue("inputSnapshotHash")
    val auditDecision: TissueAuditDecision get() = enumValueOf(values.getValue("auditDecision"))
    val canonicalExerciseCount: Int get() = values.getValue("canonicalExerciseCount").toInt()
    val scopeManifestRowCount: Int get() = values.getValue("scopeManifestRowCount").toInt()
}

data class TissueValidationReport(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty()

    operator fun plus(other: TissueValidationReport): TissueValidationReport =
        TissueValidationReport(errors + other.errors, warnings + other.warnings)
}
