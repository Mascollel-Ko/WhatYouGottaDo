package com.training.trackplanner.analysis.tissue

enum class TissueMigrationSpecificity {
    SPECIFIC,
    GROUPED,
    BROAD_AMBIGUOUS
}

enum class TissueMigrationStatus {
    LEGACY_SEEDED_NOT_EVALUATED,
    NO_TISSUE_CANDIDATE
}

data class LegacyTissueTagMigration(
    val legacyField: String,
    val legacyToken: String,
    val candidateTissueIds: List<String>,
    val candidateDimensions: List<TissueLoadDimension>,
    val mappingSpecificity: TissueMigrationSpecificity,
    val reviewPriority: String,
    val automaticBandAllowed: Boolean,
    val migrationStatus: TissueMigrationStatus,
    val migrationNotes: String
) {
    val identity: String get() = "$legacyField|$legacyToken"
}

enum class TissueDoseBasis {
    EXTERNAL_LOAD_REPETITIONS,
    EFFECTIVE_BODYWEIGHT_REPETITIONS,
    DURATION_HOLD,
    LOCOMOTION_DURATION,
    DISTANCE,
    LANDING_CONTACT_COUNT,
    DIRECTION_CHANGE_COUNT,
    JUMP_COUNT,
    THROW_COUNT,
    STROKE_COUNT,
    SESSION_DURATION_RPE,
    MIXED_EVENT_DURATION
}

enum class TissueInputAvailabilityStatus {
    DIRECTLY_RECORDED,
    DERIVABLE_FROM_CURRENT_RECORD,
    ESTIMATABLE_WITH_APPROVED_MODEL,
    NOT_CURRENTLY_AVAILABLE,
    NOT_APPLICABLE
}

enum class TissueDoseResolutionStatus {
    DIRECTLY_RECORDED,
    DERIVED_FROM_CURRENT_RECORD,
    ESTIMATED_WITH_APPROVED_MODEL,
    MISSING_RECORD_INPUT,
    UNSUPPORTED_FALLBACK
}

data class TissueDoseCapability(
    val doseBasis: TissueDoseBasis,
    val recordSource: String,
    val availabilityStatus: TissueInputAvailabilityStatus,
    val derivationMethod: String,
    val requiredFields: List<String>,
    val fallbackDoseBasis: TissueDoseBasis?,
    val fallbackAllowed: Boolean,
    val fallbackConfidence: String,
    val fallbackEvidenceClaimIds: List<String>,
    val sourceRefs: List<String>,
    val requiresSchemaChange: Boolean,
    val requiresUiChange: Boolean,
    val implementationNotes: String
)

enum class TissueCalculationStatus {
    INCOMPLETE_TISSUE_METADATA,
    MISSING_RECORD_INPUT,
    SIDE_UNRESOLVED,
    UNSUPPORTED_MODIFIER_COMBINATION,
    EVIDENCE_NOT_APPROVED,
    CALCULABLE
}

enum class TissueSideAllocationPolicy {
    NOT_LATERALIZED,
    BILATERAL_SHARED,
    BILATERAL_INDEPENDENT,
    UNILATERAL_SIDE_REQUIRED,
    ALTERNATING_SYMMETRIC_ASSUMPTION_ALLOWED,
    ALTERNATING_SIDE_COUNT_REQUIRED,
    LEAD_TRAIL_CONTEXT_REQUIRED,
    DOMINANT_NONDOMINANT_CONTEXT_REQUIRED
}

enum class TissueSide {
    LEFT,
    RIGHT,
    BILATERAL,
    UNSIDED,
    LEAD,
    TRAIL,
    DOMINANT,
    NONDOMINANT
}

enum class TissueSideResolutionStatus {
    NOT_REQUIRED,
    DIRECTLY_RECORDED,
    DERIVED_FROM_PROTOCOL,
    SYMMETRIC_ASSUMPTION,
    UNRESOLVED,
    CONFLICTING
}

data class TissueSideResolution(
    val side: TissueSide,
    val status: TissueSideResolutionStatus,
    val calculationStatus: TissueCalculationStatus,
    val diagnostics: List<String> = emptyList()
)

enum class TissueModifierFamily {
    DEPTH,
    ROM,
    GRIP_WIDTH,
    STANCE_WIDTH,
    FOOT_POSITION,
    HEEL_ELEVATION,
    TRUNK_INCLINATION,
    INCLINE_ANGLE,
    DECLINE_ANGLE,
    UNILATERALITY,
    EXTERNAL_LOAD_LEVEL,
    MOVEMENT_VELOCITY,
    ECCENTRIC_TEMPO,
    ISOMETRIC_DURATION,
    SURFACE,
    FOOTWEAR,
    ANTICIPATED_CONDITION,
    FATIGUED_TECHNIQUE,
    LANDING_TECHNIQUE
}

enum class TissueModifierOperation {
    REPLACE_WEIGHT,
    MULTIPLY_WEIGHT,
    SHIFT_BAND,
    NO_CHANGE,
    BLOCK_IF_PRESENT
}

enum class TissueModifierSpecificity {
    GLOBAL_TISSUE_RULE,
    MOVEMENT_FAMILY,
    EXACT_VARIANT,
    EXACT_STABLE_KEY
}

enum class TissueMissingInputBehavior {
    USE_REFERENCE_CONDITION,
    MARK_MISSING_RECORD_INPUT,
    BLOCK_CALCULATION
}

data class TissueModifierRule(
    val modifierRuleId: String,
    val stableKey: String,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val modifierFamily: TissueModifierFamily,
    val inputCondition: String,
    val referenceCondition: String,
    val operation: TissueModifierOperation,
    val factor: Double?,
    val bandShift: Int?,
    val specificityLevel: TissueModifierSpecificity,
    val exclusiveGroup: String,
    val interactionGroup: String,
    val precedence: Int,
    val minimumCombinedFactor: Double?,
    val maximumCombinedFactor: Double?,
    val requiredRecordInputs: List<String>,
    val missingInputBehavior: TissueMissingInputBehavior,
    val evidenceStatus: TissueEvidenceStatus,
    val evidenceClaimIds: List<String>,
    val sourceRefs: List<String>,
    val confidenceLevel: String,
    val reviewStatus: String,
    val humanApprovedBy: String,
    val reviewNotes: String
)

object TissueSideResolver {
    fun resolve(
        policy: TissueSideAllocationPolicy,
        performedSide: TissueSide? = null,
        balancedAlternatingProtocol: Boolean = false
    ): TissueSideResolution = when (policy) {
        TissueSideAllocationPolicy.NOT_LATERALIZED ->
            resolved(TissueSide.UNSIDED, TissueSideResolutionStatus.NOT_REQUIRED)
        TissueSideAllocationPolicy.BILATERAL_SHARED ->
            resolved(TissueSide.BILATERAL, TissueSideResolutionStatus.DERIVED_FROM_PROTOCOL)
        TissueSideAllocationPolicy.ALTERNATING_SYMMETRIC_ASSUMPTION_ALLOWED ->
            if (performedSide != null) resolved(performedSide, TissueSideResolutionStatus.DIRECTLY_RECORDED)
            else if (balancedAlternatingProtocol) {
                resolved(TissueSide.BILATERAL, TissueSideResolutionStatus.SYMMETRIC_ASSUMPTION)
            } else unresolved("Balanced alternating execution is not guaranteed by the record.")
        TissueSideAllocationPolicy.BILATERAL_INDEPENDENT,
        TissueSideAllocationPolicy.UNILATERAL_SIDE_REQUIRED,
        TissueSideAllocationPolicy.ALTERNATING_SIDE_COUNT_REQUIRED,
        TissueSideAllocationPolicy.LEAD_TRAIL_CONTEXT_REQUIRED,
        TissueSideAllocationPolicy.DOMINANT_NONDOMINANT_CONTEXT_REQUIRED ->
            performedSide?.let { resolved(it, TissueSideResolutionStatus.DIRECTLY_RECORDED) }
                ?: unresolved("Performed side/context is not recorded; no 50:50 split was applied.")
    }

    private fun resolved(side: TissueSide, status: TissueSideResolutionStatus) =
        TissueSideResolution(side, status, TissueCalculationStatus.CALCULABLE)

    private fun unresolved(message: String) = TissueSideResolution(
        side = TissueSide.UNSIDED,
        status = TissueSideResolutionStatus.UNRESOLVED,
        calculationStatus = TissueCalculationStatus.SIDE_UNRESOLVED,
        diagnostics = listOf(message)
    )
}
