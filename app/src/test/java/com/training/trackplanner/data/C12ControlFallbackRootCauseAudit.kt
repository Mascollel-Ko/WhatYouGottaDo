package com.training.trackplanner.data

/** C12-only diagnostic types. These values never participate in production routing. */
internal enum class C12FirstFailingGate {
    B4, B5, B6, MATERIALIZATION, B7, B8, SCOPE, B9, NONE
}

internal enum class C12TargetOutcome {
    PASS, UNMET, REGRESSED, COLLATERAL_REGRESSION, INCONCLUSIVE, NO_AUTHORITY
}

internal enum class C12PrimaryClassification {
    TARGET_NOT_SATISFIED,
    B5_IDENTITY_AUTHORITY_INCOMPLETE,
    B6_PRESCRIPTION_AUTHORITY_INCOMPLETE,
    CHANGE_PROVENANCE_INCOMPLETE,
    MATERIALIZATION_INCOMPLETE,
    PLACEMENT_OR_SCHEDULE_POLICY,
    SCOPE_UNSUPPORTED,
    UPSTREAM_INCONSISTENCY,
    CONTROL_COMPARISON_ARTIFACT,
    POTENTIAL_FALSE_NEGATIVE_GATE
}

internal enum class C12FallbackDisposition {
    TRUE_CONTROL_FALLBACK,
    POTENTIAL_FALSE_NEGATIVE_GATE
}

/** Exact adapter for B8's current reason-code contract; unknown codes stay explicit and fail closed. */
internal data class C12B8Blocker(
    val reasonCode: String,
    val classification: C12PrimaryClassification?,
    val relatedGate: C12FirstFailingGate?
)

internal fun c12B8Blockers(reasonCodes: Iterable<String>): List<C12B8Blocker> = reasonCodes.distinct().sorted().map { code ->
    val known = when (code) {
        "B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY",
        "B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED" ->
        C12PrimaryClassification.B5_IDENTITY_AUTHORITY_INCOMPLETE to C12FirstFailingGate.B5

        "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY",
        "B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY",
        "B8_CUTOVER_V1_EFFORT_NOT_FULLY_ENCODED" ->
        C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE to C12FirstFailingGate.B6

        "B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION" ->
        C12PrimaryClassification.MATERIALIZATION_INCOMPLETE to C12FirstFailingGate.MATERIALIZATION

        "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED" ->
        C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE to C12FirstFailingGate.B7

        "B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE" ->
        C12PrimaryClassification.SCOPE_UNSUPPORTED to C12FirstFailingGate.SCOPE

        "B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED" ->
        C12PrimaryClassification.PLACEMENT_OR_SCHEDULE_POLICY to C12FirstFailingGate.B8

        "B8_CUTOVER_V1_COLLATERAL_REGRESSION" ->
        C12PrimaryClassification.TARGET_NOT_SATISFIED to C12FirstFailingGate.B7

        "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY",
        "B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY" ->
        C12PrimaryClassification.UPSTREAM_INCONSISTENCY to C12FirstFailingGate.B8

        "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION" -> null to C12FirstFailingGate.B8

        "B8_B7_NOT_ELIGIBLE" -> null to C12FirstFailingGate.B7

        else -> C12PrimaryClassification.UPSTREAM_INCONSISTENCY to C12FirstFailingGate.B8
    }
    C12B8Blocker(code, known?.first, known?.second)
}

/** Typed facts are derived from the existing B1-B9 result objects and exact row comparisons. */
internal data class C12GateEvidence(
    val b4Consistent: Boolean = true,
    val upstreamConsistent: Boolean = true,
    val b5Exact: Boolean = true,
    val b6Exact: Boolean = true,
    val materializationComplete: Boolean = true,
    val targetOutcome: C12TargetOutcome = C12TargetOutcome.PASS,
    val collateralSafe: Boolean = true,
    val provenanceClosed: Boolean = true,
    val b7Eligible: Boolean = true,
    val scopeSupported: Boolean = true,
    val placementScheduleAllowed: Boolean = true,
    val b8Authorized: Boolean = true,
    val b9RoutedCanonical: Boolean = true,
    val b8Blockers: List<C12B8Blocker> = emptyList(),
    /** Set only from a typed comparator result after every upstream condition has passed. */
    val controlComparisonOnlyBlocker: Boolean = false
)

internal data class C12RootCauseClassification(
    val firstFailingGate: C12FirstFailingGate,
    val primaryClassification: C12PrimaryClassification,
    val secondaryClassifications: Set<C12PrimaryClassification>,
    val disposition: C12FallbackDisposition
)

internal object C12ControlFallbackClassifier {
    fun classify(evidence: C12GateEvidence): C12RootCauseClassification {
        val failures = linkedSetOf<C12PrimaryClassification>()
        if (evidence.targetOutcome != C12TargetOutcome.PASS || !evidence.collateralSafe) {
            failures += C12PrimaryClassification.TARGET_NOT_SATISFIED
        }
        if (!evidence.b4Consistent || !evidence.upstreamConsistent) {
            failures += C12PrimaryClassification.UPSTREAM_INCONSISTENCY
        }
        if (!evidence.b5Exact) failures += C12PrimaryClassification.B5_IDENTITY_AUTHORITY_INCOMPLETE
        if (!evidence.b6Exact) failures += C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE
        if (!evidence.materializationComplete) failures += C12PrimaryClassification.MATERIALIZATION_INCOMPLETE
        if (!evidence.provenanceClosed) failures += C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE
        if (!evidence.scopeSupported) failures += C12PrimaryClassification.SCOPE_UNSUPPORTED
        if (!evidence.placementScheduleAllowed) failures += C12PrimaryClassification.PLACEMENT_OR_SCHEDULE_POLICY
        failures += evidence.b8Blockers.mapNotNull { it.classification }

        val allPreCutoverEvidencePass = evidence.b4Consistent && evidence.upstreamConsistent && evidence.b5Exact &&
            evidence.b6Exact && evidence.materializationComplete && evidence.targetOutcome == C12TargetOutcome.PASS &&
            evidence.collateralSafe && evidence.provenanceClosed && evidence.scopeSupported &&
            evidence.placementScheduleAllowed && evidence.b8Blockers.all { it.reasonCode == "B8_B7_NOT_ELIGIBLE" }
        val falseNegative = allPreCutoverEvidencePass && (!evidence.b7Eligible || !evidence.b8Authorized)
        if (falseNegative) failures += C12PrimaryClassification.POTENTIAL_FALSE_NEGATIVE_GATE
        if (allPreCutoverEvidencePass && evidence.controlComparisonOnlyBlocker && !falseNegative) {
            failures += C12PrimaryClassification.CONTROL_COMPARISON_ARTIFACT
        }
        if (allPreCutoverEvidencePass && evidence.b7Eligible && evidence.b8Authorized &&
            !evidence.b9RoutedCanonical && !falseNegative
        ) {
            failures += C12PrimaryClassification.CONTROL_COMPARISON_ARTIFACT
        }

        val firstGate = when {
            !evidence.b4Consistent -> C12FirstFailingGate.B4
            !evidence.b5Exact -> C12FirstFailingGate.B5
            !evidence.b6Exact -> C12FirstFailingGate.B6
            !evidence.materializationComplete -> C12FirstFailingGate.MATERIALIZATION
            evidence.targetOutcome != C12TargetOutcome.PASS || !evidence.collateralSafe ||
                !evidence.provenanceClosed || !evidence.b7Eligible -> C12FirstFailingGate.B7
            !evidence.upstreamConsistent -> C12FirstFailingGate.B8
            !evidence.scopeSupported -> C12FirstFailingGate.SCOPE
            !evidence.placementScheduleAllowed || !evidence.b8Authorized -> C12FirstFailingGate.B8
            evidence.b8Blockers.any { it.relatedGate == C12FirstFailingGate.B8 } -> C12FirstFailingGate.B8
            !evidence.b9RoutedCanonical -> C12FirstFailingGate.B9
            else -> C12FirstFailingGate.NONE
        }

        // A failed target outcome is the primary safety blocker even when provenance is also open.
        val primary = when {
            C12PrimaryClassification.TARGET_NOT_SATISFIED in failures -> C12PrimaryClassification.TARGET_NOT_SATISFIED
            !evidence.b4Consistent || !evidence.upstreamConsistent -> C12PrimaryClassification.UPSTREAM_INCONSISTENCY
            C12PrimaryClassification.B5_IDENTITY_AUTHORITY_INCOMPLETE in failures -> C12PrimaryClassification.B5_IDENTITY_AUTHORITY_INCOMPLETE
            C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE in failures -> C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE
            C12PrimaryClassification.MATERIALIZATION_INCOMPLETE in failures -> C12PrimaryClassification.MATERIALIZATION_INCOMPLETE
            C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE in failures -> C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE
            C12PrimaryClassification.SCOPE_UNSUPPORTED in failures -> C12PrimaryClassification.SCOPE_UNSUPPORTED
            C12PrimaryClassification.PLACEMENT_OR_SCHEDULE_POLICY in failures -> C12PrimaryClassification.PLACEMENT_OR_SCHEDULE_POLICY
            C12PrimaryClassification.UPSTREAM_INCONSISTENCY in failures -> C12PrimaryClassification.UPSTREAM_INCONSISTENCY
            C12PrimaryClassification.POTENTIAL_FALSE_NEGATIVE_GATE in failures -> C12PrimaryClassification.POTENTIAL_FALSE_NEGATIVE_GATE
            else -> C12PrimaryClassification.CONTROL_COMPARISON_ARTIFACT
        }
        val disposition = if (primary == C12PrimaryClassification.POTENTIAL_FALSE_NEGATIVE_GATE) {
            C12FallbackDisposition.POTENTIAL_FALSE_NEGATIVE_GATE
        } else {
            C12FallbackDisposition.TRUE_CONTROL_FALLBACK
        }
        return C12RootCauseClassification(firstGate, primary, failures - primary, disposition)
    }
}
