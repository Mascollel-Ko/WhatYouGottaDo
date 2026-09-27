package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusHypertrophyCutoverAuthorityTest {
    @Test
    fun validSharedOwnerRpeOnlyEncodingIsAuthorized() {
        val comparison = sharedHypertrophyComparison(
            control = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = null),
            experimental = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 7.0),
            authorization = authorization(
                "lateral_raise", "PRIMARY", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                inputReps = 10, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0
            ),
            attributionSource = StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION
        )
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, decision.status)
        assertEquals(StimulusProductionCutoverScope.HYPERTROPHY_V1, decision.scope)
        assertEquals(listOf(StimulusPrescriptionOwnerIdentity("lateral_raise", "PRIMARY")), decision.authorizedOwnerIdentities)
        assertEquals(listOf("B8_HYPERTROPHY_V1_AUTHORIZED"), decision.reasonCodes)
    }

    @Test
    fun validSafeRepairIsAuthorized() {
        val decision = audit(sharedHypertrophyComparison(
            control = item("lateral_raise", "PRIMARY", reps = 5),
            experimental = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 7.0),
            authorization = authorization(
                "lateral_raise", "PRIMARY", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                inputReps = 5, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0
            ),
            attributionSource = StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, decision.status)
    }

    @Test
    fun validAddedHypertrophyOwnerIsAuthorizedWithExactB5Identity() {
        val comparison = addedHypertrophyComparison()
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, decision.status)
        assertEquals(listOf(StimulusPrescriptionOwnerIdentity("lateral_raise", "HYPERTROPHY")), decision.authorizedOwnerIdentities)
    }

    @Test
    fun missingAddedHypertrophyCandidateUsesHypertrophySpecificB5Reason() {
        val decision = audit(addedHypertrophyComparison(includeExactB5 = false))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY"))
        assertFalse(decision.reasonCodes.contains("B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY"))
    }

    @Test
    fun directionOnlyNoneAndUnresolvedNumericAuthorityAreRejected() {
        listOf(
            StimulusTargetNumericAuthority.NONE,
            StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusTargetNumericAuthority.UNRESOLVED
        ).forEach { numericAuthority ->
            val decision = audit(sharedHypertrophyComparison(targetAuthority = numericAuthority))
            assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
            assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_TARGET_HAS_NO_NUMERIC_AUTHORITY"))
            assertTrue(decision.authorizedOwnerIdentities.isEmpty())
        }
    }

    @Test
    fun effortBelowCanonicalMinimumIsRejected() {
        val decision = audit(sharedHypertrophyComparison(
            authorizedTarget = 6.9,
            materializationReasonCodes = listOf("B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY"))
        assertTrue(decision.reasonCodes.contains("B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM"))
    }

    @Test
    fun missingEffortTargetIsRejected() {
        val decision = audit(sharedHypertrophyComparison(
            authorizedTarget = null,
            materializationReasonCodes = listOf("B6_EFFORT_TARGET_MISSING")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY"))
        assertTrue(decision.reasonCodes.contains("B6_EFFORT_TARGET_MISSING"))
    }

    @Test
    fun sufficientRpeMutationStillFailsExactPreservation() {
        val comparison = sharedHypertrophyComparison(
            control = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 7.0),
            experimental = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 8.0),
            materializationReasonCodes = listOf("B6_EFFORT_TARGET_NOT_PRESERVED"),
            materializationState = StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
        )
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B6_EFFORT_TARGET_NOT_PRESERVED"))
    }

    @Test
    fun partialMaterializationIsRejected() {
        val decision = audit(sharedHypertrophyComparison(
            materializationFull = false,
            materializationState = StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_REQUIRES_FULL_B6_MATERIALIZATION"))
    }

    @Test
    fun provisionalZeroLoadDoesNotReachBoundedCutover() {
        val comparison = sharedHypertrophyComparison(
            experimental = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 7.0, weightKg = 0.0),
            authorizedWeightKg = 0.0
        )
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
    }

    @Test
    fun materialStrengthChangeBlocksHypertrophyScope() {
        val h = item("lateral_raise", "H", reps = 10, targetRpeMin = 7.0)
        val strengthBefore = item("squat", "S", reps = 8)
        val strengthAfter = item("squat", "S", reps = 5)
        val comparison = comparison(
            controlItems = listOf(h.copy(setPrescriptions = sets(10, null)), strengthBefore),
            experimentalItems = listOf(h, strengthAfter),
            authorizations = listOf(
                authorization("lateral_raise", "H", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, inputReps = 10, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0),
                authorization("squat", "S", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, quality = TrainableQuality.STRENGTH)
            ),
            materializations = listOf(
                materialization("lateral_raise", "H", quality = TrainableQuality.HYPERTROPHY, authorized = planned(10, 7.0)),
                materialization("squat", "S", quality = TrainableQuality.STRENGTH, authorized = planned(5, null))
            ),
            targets = listOf(qualityTarget(TrainableQuality.HYPERTROPHY), qualityTarget(TrainableQuality.STRENGTH)),
            attributions = listOf(
                StimulusExperimentalChangeAttribution("lateral_raise", "H", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:HYPERTROPHY")),
                StimulusExperimentalChangeAttribution("squat", "S", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:STRENGTH"))
            )
        )
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_NON_HYPERTROPHY_CHANGE_OUT_OF_SCOPE"))
    }

    @Test
    fun materialNonHypertrophyQualityChangeBlocksHypertrophyScope() {
        val h = item("lateral_raise", "H", reps = 10, targetRpeMin = 7.0)
        val powerBefore = item("jump", "P", reps = 8)
        val powerAfter = item("jump", "P", reps = 10)
        val comparison = comparison(
            controlItems = listOf(h.copy(setPrescriptions = sets(10, null)), powerBefore),
            experimentalItems = listOf(h, powerAfter),
            authorizations = listOf(authorization("lateral_raise", "H", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, inputReps = 10, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0)),
            materializations = listOf(materialization("lateral_raise", "H", quality = TrainableQuality.HYPERTROPHY, authorized = planned(10, 7.0))),
            targets = listOf(qualityTarget(TrainableQuality.HYPERTROPHY)),
            attributions = listOf(
                StimulusExperimentalChangeAttribution("lateral_raise", "H", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:HYPERTROPHY", "QUALITY:POWER"))
            )
        )
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_HYPERTROPHY_V1_NON_HYPERTROPHY_CHANGE_OUT_OF_SCOPE"))
    }

    @Test
    fun nonMaterialStrengthAuthorityDoesNotPoisonHypertrophyReview() {
        val comparison = sharedHypertrophyComparison(
            control = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = null),
            experimental = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 7.0),
            additionalControlItems = listOf(item("squat", "S")),
            additionalExperimentalItems = listOf(item("squat", "S")),
            additionalAuthorizations = listOf(
                authorization("squat", "S", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, quality = TrainableQuality.STRENGTH)
                    .copy(reasonCodes = listOf("B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM"))
            ),
            additionalMaterializations = listOf(
                materialization("squat", "S", quality = TrainableQuality.STRENGTH, authorized = planned(8, null),
                    reasonCodes = listOf("B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM"))
            ),
            attributionSource = StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION
        )
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, audit(comparison).status)
    }

    @Test
    fun localizedUnrelatedConflictDoesNotPoisonValidHypertrophyOwner() {
        val h = item("lateral_raise", "H", reps = 10, targetRpeMin = 7.0)
        val conflict = item("squat", "CONFLICT", reps = 8)
        val comparison = comparison(
            controlItems = listOf(h.copy(setPrescriptions = sets(10, null)), conflict),
            experimentalItems = listOf(h, conflict),
            authorizations = listOf(
                authorization("lateral_raise", "H", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, inputReps = 10, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0, quality = TrainableQuality.HYPERTROPHY),
                authorization("squat", "CONFLICT", StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY, quality = TrainableQuality.STRENGTH),
                authorization("squat", "CONFLICT", StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY, quality = TrainableQuality.HYPERTROPHY)
            ),
            materializations = listOf(
                materialization("lateral_raise", "H", quality = TrainableQuality.HYPERTROPHY, authorized = planned(10, 7.0)),
                materialization("squat", "CONFLICT", quality = TrainableQuality.HYPERTROPHY, authorized = planned(8, 7.0), nonMaterialized = true, reasonCodes = listOf("B6_MULTI_QUALITY_OWNER_PRESCRIPTION_CONFLICT")),
                materialization("squat", "CONFLICT", quality = TrainableQuality.STRENGTH, authorized = planned(8, null), nonMaterialized = true, reasonCodes = listOf("B6_MULTI_QUALITY_OWNER_PRESCRIPTION_CONFLICT"))
            ),
            attributions = listOf(StimulusExperimentalChangeAttribution("lateral_raise", "H", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:HYPERTROPHY")))
        )
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, audit(comparison).status)
    }

    @Test
    fun materialConflictOwnerIsRejected() {
        val control = item("squat", "CONFLICT", reps = 10, targetRpeMin = null)
        val experimental = item("squat", "CONFLICT", reps = 5, targetRpeMin = 7.0)
        val comparison = comparison(
            controlItems = listOf(control), experimentalItems = listOf(experimental),
            authorizations = listOf(
                authorization("squat", "CONFLICT", StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY, quality = TrainableQuality.HYPERTROPHY),
                authorization("squat", "CONFLICT", StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY, quality = TrainableQuality.STRENGTH)
            ),
            materializations = listOf(materialization("squat", "CONFLICT", quality = TrainableQuality.HYPERTROPHY, authorized = planned(5, 7.0), nonMaterialized = true, reasonCodes = listOf("B6_MULTI_QUALITY_OWNER_PRESCRIPTION_CONFLICT"))),
            targets = listOf(qualityTarget(TrainableQuality.HYPERTROPHY)),
            attributions = listOf(StimulusExperimentalChangeAttribution("squat", "CONFLICT", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:HYPERTROPHY")))
        )
        val decision = audit(comparison)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
    }

    @Test
    fun validHypertrophyAuthorityStillRoutesControlThroughStrengthOnlyB9() {
        val comparison = sharedHypertrophyComparison()
        val authority = audit(comparison)
        val routed = StimulusProductionRouter().route(comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE)
        assertEquals(StimulusProductionProgramSource.CONTROL, routed.decision.selectedSource)
        assertSame(comparison.control, routed.program)
        assertEquals(listOf("B9_B8_SCOPE_MISMATCH"), routed.decision.reasonCodes)
        assertFalse(routed.decision.productionRoutingActive)
    }

    private fun audit(comparison: StimulusSelectionProgramComparison) =
        StimulusProductionCutoverAuthorityAuditEngine().audit(comparison, StimulusProductionCutoverScope.HYPERTROPHY_V1)

    private fun sharedHypertrophyComparison(
        control: ProgramSkeletonItem = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = null),
        experimental: ProgramSkeletonItem = item("lateral_raise", "PRIMARY", reps = 10, targetRpeMin = 7.0),
        authorization: StimulusPrescriptionAuthorization = authorization(
            "lateral_raise", "PRIMARY", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            inputReps = 10, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0
        ),
        attributionSource: StimulusExperimentalChangeAttributionSource = StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
        authorizedTarget: Double? = 7.0,
        authorizedWeightKg: Double = 60.0,
        materializationFull: Boolean = true,
        materializationState: StimulusPrescriptionMaterializationState = if (materializationFull) StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED else StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED,
        materializationReasonCodes: List<String> = emptyList(),
        targetAuthority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        additionalControlItems: List<ProgramSkeletonItem> = emptyList(),
        additionalExperimentalItems: List<ProgramSkeletonItem> = emptyList(),
        additionalAuthorizations: List<StimulusPrescriptionAuthorization> = emptyList(),
        additionalMaterializations: List<StimulusPrescriptionMaterializationAudit> = emptyList()
    ): StimulusSelectionProgramComparison = comparison(
        controlItems = listOf(control) + additionalControlItems,
        experimentalItems = listOf(experimental) + additionalExperimentalItems,
        authorizations = listOf(authorization.copy(
            authorizedPrescription = planned(experimental.reps, authorizedTarget, authorizedWeightKg),
            executionAuthority = canonicalExecutionAuthority(TrainableQuality.HYPERTROPHY, planned(experimental.reps, authorizedTarget, authorizedWeightKg))
        )) + additionalAuthorizations,
        materializations = listOf(materialization(
            "lateral_raise", "PRIMARY", quality = TrainableQuality.HYPERTROPHY,
            authorized = planned(experimental.reps, authorizedTarget, authorizedWeightKg),
            full = materializationFull, state = materializationState, reasonCodes = materializationReasonCodes,
            executionAuthority = canonicalExecutionAuthority(TrainableQuality.HYPERTROPHY, planned(experimental.reps, authorizedTarget, authorizedWeightKg))
        )) + additionalMaterializations,
        targets = listOf(qualityTarget(TrainableQuality.HYPERTROPHY, targetAuthority)),
        attributions = listOf(StimulusExperimentalChangeAttribution("lateral_raise", "PRIMARY", attributionSource, listOf("QUALITY:HYPERTROPHY")))
    )

    private fun addedHypertrophyComparison(includeExactB5: Boolean = true) = comparison(
        controlItems = listOf(item("base", "BASE")),
        experimentalItems = listOf(item("base", "BASE"), item("lateral_raise", "HYPERTROPHY", reps = 10, targetRpeMin = 7.0)),
        selected = selected("lateral_raise", "HYPERTROPHY", "QUALITY:HYPERTROPHY").takeIf { includeExactB5 },
        traces = if (includeExactB5) listOf(trace("lateral_raise", "HYPERTROPHY", "QUALITY:HYPERTROPHY")) else emptyList(),
        attributions = listOf(StimulusExperimentalChangeAttribution("lateral_raise", "HYPERTROPHY", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, listOf("QUALITY:HYPERTROPHY"))),
        authorizations = listOf(authorization("lateral_raise", "HYPERTROPHY", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, inputReps = 10, inputTarget = null, authorizedReps = 10, authorizedTarget = 7.0)),
        materializations = listOf(materialization("lateral_raise", "HYPERTROPHY", quality = TrainableQuality.HYPERTROPHY, authorized = planned(10, 7.0)))
    )

    private fun comparison(
        controlItems: List<ProgramSkeletonItem>,
        experimentalItems: List<ProgramSkeletonItem>,
        selected: StimulusSelectedCandidate? = null,
        traces: List<StimulusCandidateSelectionTrace> = emptyList(),
        attributions: List<StimulusExperimentalChangeAttribution> = emptyList(),
        authorizations: List<StimulusPrescriptionAuthorization> = emptyList(),
        materializations: List<StimulusPrescriptionMaterializationAudit> = emptyList(),
        targets: List<StimulusQualityTarget> = listOf(qualityTarget(TrainableQuality.HYPERTROPHY))
    ): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("B12", ProgramGoal.BODYBUILDING, 1, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1)
        val control = skeleton(request, controlItems)
        val experimental = skeleton(request, experimentalItems)
        val selection = StimulusCandidateSelectionPlan(listOfNotNull(selected), traces, MaterialDemand(emptyList(), emptyMap(), emptyMap()))
        val compared = StimulusSelectionProgramComparisonEngine().compare(
            control, experimental, StimulusTargetPlan(targets, emptyList(), emptyList()), selection, null, null
        )
        return compared.copy(
            experimentalReadinessAudit = StimulusExperimentalReadinessAudit(
                status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                changeAttributions = attributions,
                materializationIntegrityPassed = true,
                changeProvenanceClosed = true,
                collateralRegressionFree = true
            ),
            prescriptionAuthorizationPlan = StimulusPrescriptionAuthorizationPlan(authorizations).takeIf { it.authorizations.isNotEmpty() },
            prescriptionMaterializationAudits = materializations
        )
    }

    private fun selected(key: String, role: String, targetId: String) = StimulusSelectedCandidate(
        stableKey = key, coveredTargetIds = setOf(targetId), primaryTargetId = targetId,
        selectionReasons = listOf("B5"), currentPrescriptionCompatibility = "REALIZATION_UNCLASSIFIED",
        targetSetsFromExistingPrescription = 2, selectionRole = role
    )

    private fun trace(key: String, role: String, targetId: String) = StimulusCandidateSelectionTrace(
        targetId = targetId, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, priority = TargetPriority.PRIMARY,
        controlDirectCapabilityIdentities = emptyList(), selectionRequired = true, candidatePool = listOf(key), selectedStableKey = key,
        coveredByPreviouslySelectedStableKey = null, reasonCodes = listOf("SELECTION_IDENTITY_PRESENT"), selectedSelectionRole = role
    )

    private fun authorization(
        key: String,
        role: String,
        status: StimulusPrescriptionAuthorizationStatus,
        quality: TrainableQuality = TrainableQuality.HYPERTROPHY,
        inputReps: Int = 10,
        inputTarget: Double? = null,
        authorizedReps: Int = 10,
        authorizedTarget: Double? = 7.0,
        authorizedWeightKg: Double = 60.0
    ) = StimulusPrescriptionAuthorization(
        targetId = "QUALITY:${quality.name}", quality = quality, owner = StimulusPrescriptionOwner(key, role),
        source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
        inputPrescription = planned(inputReps, inputTarget), plannedCompatibility = null,
        authorizedPrescription = planned(authorizedReps, authorizedTarget, authorizedWeightKg), status = status
    )

    private fun materialization(
        key: String,
        role: String,
        quality: TrainableQuality,
        authorized: PlannedPrescription?,
        full: Boolean = true,
        state: StimulusPrescriptionMaterializationState = if (full) StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED else StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED,
        reasonCodes: List<String> = emptyList(),
        executionAuthority: StimulusPrescriptionExecutionAuthority = canonicalExecutionAuthority(quality, authorized),
        nonMaterialized: Boolean = false
    ) = StimulusPrescriptionMaterializationAudit(
        targetId = "QUALITY:${quality.name}", quality = quality, owner = StimulusPrescriptionOwner(key, role),
        authorizedWeeklySetUnits = if (nonMaterialized) 0 else 2,
        materializedWeeklySetUnits = if (nonMaterialized) 0 else if (full) 2 else 1,
        targetCompatibleMaterializedUnits = if (nonMaterialized) 0 else if (full) 2 else 1,
        shortfall = if (nonMaterialized || full) 0 else 1, overrun = 0, prescriptionPreservedOrSubset = true,
        state = if (nonMaterialized) StimulusPrescriptionMaterializationState.NOT_MATERIALIZED else state,
        reasonCodes = reasonCodes, executionAuthority = executionAuthority,
        weeklyAudits = listOf(StimulusPrescriptionWeekMaterializationAudit(1, if (nonMaterialized) 0 else 2, if (nonMaterialized) 0 else if (full) 2 else 1, if (nonMaterialized) 0 else if (full) 2 else 1, if (nonMaterialized || full) 0 else 1, 0, true, reasonCodes))
    )

    private fun qualityTarget(quality: TrainableQuality, numericAuthority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE) = StimulusQualityTarget(
        quality = quality, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, priority = TargetPriority.PRIMARY,
        numericAuthority = numericAuthority, baselineSource = null, baselineConfidence = null,
        weeklyDirectUnitsTarget = StimulusTargetRange(1.0, 2.0, 4.0), weeklyDirectSessionsTarget = StimulusTargetRange(1.0, 1.0, 2.0),
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null, exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun item(key: String, role: String, reps: Int = 8, targetRpeMin: Double? = null, weightKg: Double = 60.0) = ProgramSkeletonItem(
        localId = "$key-$role", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key, exerciseName = key,
        category = "STRENGTH", restSeconds = 90, prescription = "$reps reps", setCount = 2, reps = reps, weightKg = weightKg,
        seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = role, setPrescriptions = sets(reps, targetRpeMin, weightKg)
    )

    private fun sets(reps: Int, targetRpeMin: Double?, weightKg: Double = 60.0) = List(2) {
        ProgramSetPrescription(it + 1, reps, weightKg, 0).copy(targetRpeMin = targetRpeMin)
    }

    private fun planned(reps: Int, targetRpeMin: Double?, weightKg: Double = 60.0) =
        PlannedPrescription("$reps reps", sets(reps, targetRpeMin, weightKg), 90, "TEST")

    private fun skeleton(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = 7, request = request, periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)), items = items,
        weekDaySchedule = mapOf(1 to setOf(1))
    )
}
