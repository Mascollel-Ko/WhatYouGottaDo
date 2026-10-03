package com.training.trackplanner.data

/** Test-only classification; it is intentionally disconnected from production B8 and routing. */
internal enum class C16PrimaryDisposition {
    EXPECTED_CALIBRATION_CUTOVER,
    TRUE_PROGRAM_DEFECT,
    TRUE_SAFETY_BLOCK,
    AUDIT_OR_PROVENANCE_GAP,
    CURRENT_SCOPE_LIMITATION,
    COMPARISON_ARTIFACT,
    POTENTIAL_B8_FALSE_NEGATIVE
}

internal data class C16BlockerEvidence(
    val b8Rejected: Boolean,
    val residualMaterialDeltaCount: Int,
    val numericB4Authority: Boolean = true,
    val exactB5Owner: Boolean = true,
    val exactColdStartB6Authority: Boolean = true,
    val fullMaterialization: Boolean = true,
    val unauthorizedOwnerDelta: Boolean = false,
    val validNonStrengthCanonicalDelta: Boolean = false,
    val provenanceClosed: Boolean = true,
    val targetAccepted: Boolean = true,
    val collateralRegressionFree: Boolean = true,
    val scopeSupported: Boolean = true,
    val scheduleAllowed: Boolean = true,
    val upstreamIdentitiesConsistent: Boolean = true,
    val onlyComparisonArtifactRemains: Boolean = false,
    val canonicalProgramDefect: Boolean = false
)

internal fun classifyC16Primary(evidence: C16BlockerEvidence): C16PrimaryDisposition = when {
    evidence.canonicalProgramDefect || !evidence.targetAccepted -> C16PrimaryDisposition.TRUE_PROGRAM_DEFECT
    !evidence.collateralRegressionFree || evidence.unauthorizedOwnerDelta || !evidence.upstreamIdentitiesConsistent ||
        !evidence.numericB4Authority || !evidence.exactB5Owner || !evidence.exactColdStartB6Authority ||
        !evidence.fullMaterialization || !evidence.scheduleAllowed ->
        C16PrimaryDisposition.TRUE_SAFETY_BLOCK
    evidence.validNonStrengthCanonicalDelta || !evidence.scopeSupported -> C16PrimaryDisposition.CURRENT_SCOPE_LIMITATION
    !evidence.provenanceClosed -> C16PrimaryDisposition.AUDIT_OR_PROVENANCE_GAP
    evidence.onlyComparisonArtifactRemains -> C16PrimaryDisposition.COMPARISON_ARTIFACT
    evidence.b8Rejected && evidence.residualMaterialDeltaCount == 0 && evidence.numericB4Authority && evidence.exactB5Owner &&
        evidence.exactColdStartB6Authority && evidence.fullMaterialization && evidence.targetAccepted &&
        evidence.collateralRegressionFree && evidence.provenanceClosed && evidence.scopeSupported && evidence.scheduleAllowed &&
        evidence.upstreamIdentitiesConsistent -> C16PrimaryDisposition.POTENTIAL_B8_FALSE_NEGATIVE
    !evidence.b8Rejected && evidence.residualMaterialDeltaCount == 0 -> C16PrimaryDisposition.EXPECTED_CALIBRATION_CUTOVER
    else -> C16PrimaryDisposition.TRUE_SAFETY_BLOCK
}
