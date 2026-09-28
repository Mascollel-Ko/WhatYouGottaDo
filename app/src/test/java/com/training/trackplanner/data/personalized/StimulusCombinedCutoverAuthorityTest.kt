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

class StimulusCombinedCutoverAuthorityTest {
    @Test
    fun validStrengthAndHypertrophyOwnersPreserveLosslessAuthority() {
        val decision = audit(mixedComparison())
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, decision.status)
        assertEquals(StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1, decision.scope)
        assertEquals(
            listOf(StimulusPrescriptionOwnerIdentity("lateral_raise", "H"), StimulusPrescriptionOwnerIdentity("squat", "S")),
            decision.authorizedOwnerIdentities
        )
        assertEquals(
            listOf(
                StimulusPrescriptionAuthorityIdentity("lateral_raise", "H", TrainableQuality.HYPERTROPHY),
                StimulusPrescriptionAuthorityIdentity("squat", "S", TrainableQuality.STRENGTH)
            ), decision.authorizedAuthorityIdentities
        )
    }

    @Test
    fun onlyOneMaterialQualityRequiresBothAndSingleScopesRemainAvailable() {
        val strength = strengthOnly()
        val h = hypertrophyOnly()
        val combinedStrength = audit(strength)
        val combinedH = audit(h)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, combinedStrength.status)
        assertTrue(combinedStrength.reasonCodes.contains("B8_STRENGTH_HYPERTROPHY_V1_REQUIRES_BOTH_QUALITIES_MATERIAL"))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, combinedH.status)
        assertTrue(combinedH.reasonCodes.contains("B8_STRENGTH_HYPERTROPHY_V1_REQUIRES_BOTH_QUALITIES_MATERIAL"))
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            engine.audit(strength, StimulusProductionCutoverScope.STRENGTH_V1).status)
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            engine.audit(h, StimulusProductionCutoverScope.HYPERTROPHY_V1).status)
    }

    @Test
    fun failingOneQualityNeverExposesPartialAuthority() {
        val failingH = mixedComparison(hTargetRpe = null)
        val decision = audit(failingH)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
        assertTrue(decision.authorizedAuthorityIdentities.isEmpty())
        assertTrue(decision.reasonCodes.any { it.contains("HYPERTROPHY") || it.contains("EFFORT") })
    }

    @Test
    fun thirdMaterialQualityIsOutOfScopeButNonMaterialDiagnosticIsIsolated() {
        val third = mixedComparison(extraAttribution = StimulusExperimentalChangeAttribution(
            "power", "P", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
            listOf("QUALITY:POWER")
        ), extraItems = true)
        val blocked = audit(third)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, blocked.status)
        assertTrue(blocked.reasonCodes.contains("B8_STRENGTH_HYPERTROPHY_V1_OTHER_QUALITY_CHANGE_OUT_OF_SCOPE"))

        val nonMaterial = mixedComparison(nonMaterialHDiagnostic = true)
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, audit(nonMaterial).status)
    }

    @Test
    fun currentStrengthRouterRejectsCombinedScope() {
        val comparison = mixedComparison()
        val authority = audit(comparison)
        val routed = StimulusProductionRouter().route(comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE)
        assertEquals(StimulusProductionProgramSource.CONTROL, routed.decision.selectedSource)
        assertEquals(listOf("B9_B8_COMBINED_SCOPE_NOT_ACTIVE"), routed.decision.reasonCodes)
        assertFalse(routed.decision.productionRoutingActive)
    }

    @Test
    fun combinedActiveRouterSelectsTheExistingExperimentalSkeletonByExactAuthorityIdentity() {
        val comparison = mixedComparison()
        val authority = audit(comparison)
        val routed = StimulusProductionRouter().route(
            comparison,
            authority,
            StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertEquals(StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1, routed.decision.selectedSource)
        assertSame(comparison.experimental, routed.program)
        assertEquals(listOf("B9_B8_STRENGTH_HYPERTROPHY_V1_ROUTED"), routed.decision.reasonCodes)
        assertTrue(routed.decision.productionRoutingActive)
    }

    @Test
    fun combinedActiveRouterRejectsPartialAuthorityAndThirdQuality() {
        val comparison = mixedComparison()
        val authority = audit(comparison)
        val partial = authority.copy(
            authorizedAuthorityIdentities = authority.authorizedAuthorityIdentities.filter { it.quality == TrainableQuality.STRENGTH }
        )
        val partialRoute = StimulusProductionRouter().route(
            comparison, partial, StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(comparison.control, partialRoute.program)
        assertEquals(listOf("B9_B8_AUTHORITY_IDENTITY_MISMATCH"), partialRoute.decision.reasonCodes)

        val third = authority.copy(
            authorizedAuthorityIdentities = authority.authorizedAuthorityIdentities +
                StimulusPrescriptionAuthorityIdentity("power", "P", TrainableQuality.POWER)
        )
        val thirdRoute = StimulusProductionRouter().route(
            comparison, third, StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
        )
        assertSame(comparison.control, thirdRoute.program)
        assertEquals(listOf("B9_B8_AUTHORITY_IDENTITY_MISMATCH"), thirdRoute.decision.reasonCodes)
    }

    @Test
    fun materialScopeResolverUsesOnlyGovernedMaterialQualityAttribution() {
        val resolver = StimulusProductionMaterialScopeResolver()
        assertEquals(StimulusProductionCutoverScope.STRENGTH_V1, resolver.resolve(strengthOnly()))
        assertEquals(StimulusProductionCutoverScope.HYPERTROPHY_V1, resolver.resolve(hypertrophyOnly()))
        assertEquals(StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1, resolver.resolve(mixedComparison()))
        val unsupported = strengthOnly().copy(
            experimentalReadinessAudit = strengthOnly().experimentalReadinessAudit!!.copy(
                changeAttributions = listOf(
                    StimulusExperimentalChangeAttribution(
                        "squat", "S", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
                        listOf("QUALITY:POWER")
                    )
                )
            )
        )
        assertEquals(null, resolver.resolve(unsupported))
    }

    private val engine = StimulusProductionCutoverAuthorityAuditEngine()
    private fun audit(comparison: StimulusSelectionProgramComparison) =
        engine.audit(comparison, StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1)

    private fun mixedComparison(
        hTargetRpe: Double? = 7.0,
        extraAttribution: StimulusExperimentalChangeAttribution? = null,
        extraItems: Boolean = false,
        nonMaterialHDiagnostic: Boolean = false
    ): StimulusSelectionProgramComparison {
        val squatBefore = item("squat", "S", 8)
        val squatAfter = item("squat", "S", 5)
        val hBefore = item("lateral_raise", "H", 10, null)
        val hAfter = item("lateral_raise", "H", 10, hTargetRpe)
        val controls = listOf(squatBefore, hBefore) + if (extraItems) listOf(item("power", "P", 8)) else emptyList()
        val experiment = listOf(squatAfter, hAfter) + if (extraItems) listOf(item("power", "P", 10)) else emptyList()
        val attributions = mutableListOf(
            StimulusExperimentalChangeAttribution("squat", "S", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:STRENGTH")),
            StimulusExperimentalChangeAttribution("lateral_raise", "H", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:HYPERTROPHY"))
        )
        if (extraAttribution != null) attributions += extraAttribution
        val auths = mutableListOf(
            authorization("squat", "S", TrainableQuality.STRENGTH, 5, null),
            authorization("lateral_raise", "H", TrainableQuality.HYPERTROPHY, 10, hTargetRpe)
        )
        val mats = mutableListOf(
            materialization("squat", "S", TrainableQuality.STRENGTH, planned(5, null)),
            materialization("lateral_raise", "H", TrainableQuality.HYPERTROPHY, planned(10, hTargetRpe))
        )
        if (nonMaterialHDiagnostic) {
            auths += authorization("shadow", "H", TrainableQuality.HYPERTROPHY, 10, null).copy(reasonCodes = listOf("B6_EFFORT_TARGET_MISSING"))
            mats += materialization("shadow", "H", TrainableQuality.HYPERTROPHY, planned(10, null), reasonCodes = listOf("B6_EFFORT_TARGET_MISSING"))
        }
        return comparison(controls, experiment, attributions, auths, mats)
    }

    private fun strengthOnly() = mixedComparison().let { it.copy(
        control = it.control.copy(items = it.control.items.filter { row -> row.exerciseStableKey == "squat" }),
        experimental = it.experimental.copy(items = it.experimental.items.filter { row -> row.exerciseStableKey == "squat" }),
        experimentalReadinessAudit = it.experimentalReadinessAudit!!.copy(changeAttributions = listOf(
            StimulusExperimentalChangeAttribution("squat", "S", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:STRENGTH"))
        )),
        prescriptionAuthorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(authorization("squat", "S", TrainableQuality.STRENGTH, 5, null))),
        prescriptionMaterializationAudits = listOf(materialization("squat", "S", TrainableQuality.STRENGTH, planned(5, null)))
    ) }

    private fun hypertrophyOnly() = mixedComparison().let { it.copy(
        control = it.control.copy(items = it.control.items.filter { row -> row.exerciseStableKey == "lateral_raise" }),
        experimental = it.experimental.copy(items = it.experimental.items.filter { row -> row.exerciseStableKey == "lateral_raise" }),
        experimentalReadinessAudit = it.experimentalReadinessAudit!!.copy(changeAttributions = listOf(
            StimulusExperimentalChangeAttribution("lateral_raise", "H", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION, listOf("QUALITY:HYPERTROPHY"))
        )),
        prescriptionAuthorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(authorization("lateral_raise", "H", TrainableQuality.HYPERTROPHY, 10, 7.0))),
        prescriptionMaterializationAudits = listOf(materialization("lateral_raise", "H", TrainableQuality.HYPERTROPHY, planned(10, 7.0)))
    ) }

    private fun comparison(
        controlItems: List<ProgramSkeletonItem>,
        experimentalItems: List<ProgramSkeletonItem>,
        attributions: List<StimulusExperimentalChangeAttribution>,
        authorizations: List<StimulusPrescriptionAuthorization>,
        materializations: List<StimulusPrescriptionMaterializationAudit>
    ): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("B13", ProgramGoal.STRENGTH, 1, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1)
        val control = skeleton(request, controlItems)
        val experimental = skeleton(request, experimentalItems)
        val selected = listOf(
            StimulusSelectedCandidate("squat", setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", listOf("B5"), "REALIZATION_UNCLASSIFIED", 2, "S"),
            StimulusSelectedCandidate("lateral_raise", setOf("QUALITY:HYPERTROPHY"), "QUALITY:HYPERTROPHY", listOf("B5"), "REALIZATION_UNCLASSIFIED", 2, "H")
        )
        val traces = listOf(
            trace("squat", "S", "QUALITY:STRENGTH"), trace("lateral_raise", "H", "QUALITY:HYPERTROPHY")
        )
        val compared = StimulusSelectionProgramComparisonEngine().compare(
            control, experimental,
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH), target(TrainableQuality.HYPERTROPHY)), emptyList(), emptyList()),
            StimulusCandidateSelectionPlan(selected, traces, MaterialDemand(emptyList(), emptyMap(), emptyMap())), null, null
        )
        return compared.copy(
            experimentalReadinessAudit = StimulusExperimentalReadinessAudit(
                status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                changeAttributions = attributions,
                materializationIntegrityPassed = true,
                changeProvenanceClosed = true,
                collateralRegressionFree = true
            ),
            prescriptionAuthorizationPlan = StimulusPrescriptionAuthorizationPlan(authorizations),
            prescriptionMaterializationAudits = materializations
        )
    }

    private fun authorization(key: String, role: String, quality: TrainableQuality, reps: Int, rpe: Double?) = StimulusPrescriptionAuthorization(
        "QUALITY:${quality.name}", quality, StimulusPrescriptionOwner(key, role), StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
        planned(reps, null), null, planned(reps, rpe), StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
    )

    private fun materialization(key: String, role: String, quality: TrainableQuality, authorized: PlannedPrescription, reasonCodes: List<String> = emptyList()) = StimulusPrescriptionMaterializationAudit(
        "QUALITY:${quality.name}", quality, StimulusPrescriptionOwner(key, role), 2, 2, 2, 0, 0, true,
        StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED, reasonCodes,
        canonicalExecutionAuthority(quality, authorized), listOf(StimulusPrescriptionWeekMaterializationAudit(1, 2, 2, 2, 0, 0, true, reasonCodes))
    )

    private fun target(quality: TrainableQuality) = StimulusQualityTarget(
        quality, StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, TargetPriority.PRIMARY, StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        null, null, StimulusTargetRange(1.0, 2.0, 4.0), StimulusTargetRange(1.0, 1.0, 2.0), null, null, null, emptyList(), emptyList()
    )

    private fun trace(key: String, role: String, targetId: String) = StimulusCandidateSelectionTrace(
        targetId, StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, TargetPriority.PRIMARY, emptyList(), true, listOf(key), key, null,
        reasonCodes = listOf("SELECTION_IDENTITY_PRESENT"), selectedSelectionRole = role
    )

    private fun item(key: String, role: String, reps: Int, rpe: Double? = null) = ProgramSkeletonItem(
        localId = "$key-$role", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key, exerciseName = key,
        category = "TEST", restSeconds = 90, prescription = "$reps reps", setCount = 2, reps = reps, weightKg = 60.0,
        seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = role,
        setPrescriptions = List(2) { ProgramSetPrescription(it + 1, reps, 60.0, 0).copy(targetRpeMin = rpe) }
    )

    private fun planned(reps: Int, rpe: Double?) = PlannedPrescription("$reps reps", List(2) { ProgramSetPrescription(it + 1, reps, 60.0, 0).copy(targetRpeMin = rpe) }, 90, "TEST")

    private fun skeleton(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>) = GeneratedProgramSkeleton(
        request.name, 7, request, request.periodizationType, listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)), items, mapOf(1 to setOf(1))
    )
}
