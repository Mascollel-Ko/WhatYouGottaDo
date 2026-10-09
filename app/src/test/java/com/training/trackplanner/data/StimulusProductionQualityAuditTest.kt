package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.ln

/** C7 audit corpus: canonical B5/B6 authority plus unchanged B7/B8 fail-closed routing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusProductionQualityAuditTest {
    @Test
    fun realServiceCorpusIsAuthorizedAndStructurallyAuditable() = runBlocking {
        val strength = listOf(
            CorpusSpec("strength_lower_main", TrainableQuality.STRENGTH, "barbell_back_squat"),
            CorpusSpec("strength_upper_push", TrainableQuality.STRENGTH, "barbell_bench_press")
        ).map { runRealCase(it) }
        val hypertrophy = listOf(
            CorpusSpec("hypertrophy_lower_compound", TrainableQuality.HYPERTROPHY, "dumbbell_goblet_squat"),
            CorpusSpec("hypertrophy_upper_compound", TrainableQuality.HYPERTROPHY, "ex_1dbee10e"),
            CorpusSpec("hypertrophy_upper_isolation", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly"),
            CorpusSpec("hypertrophy_lower_isolation", TrainableQuality.HYPERTROPHY, "cable_hip_adduction")
        ).map { runRealCase(it) }

        assertEquals(2, strength.size)
        assertEquals(4, hypertrophy.size)
        (strength + hypertrophy).forEach { auditC7Case(it) }

        val offListStrengthKey = "chest_supported_row_machine"
        val offListStrength = runRealEvaluation(CorpusSpec(
            "off_list_low_rep_strength", TrainableQuality.STRENGTH, offListStrengthKey
        ))
        assertEquals(StimulusProductionProgramSource.CONTROL, offListStrength.routeDecision.selectedSource)
        assertFalse(offListStrength.comparison?.selectionPlan?.selectedCandidates.orEmpty().any {
            it.stableKey == offListStrengthKey && "QUALITY:STRENGTH" in it.coveredTargetIds
        })
        assertFalse(offListStrength.comparison?.prescriptionAuthorizationPlan?.authorizations.orEmpty().any {
            it.quality == TrainableQuality.STRENGTH && it.owner?.stableKey == offListStrengthKey &&
                it.authorizedPrescription != null && it.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                )
        })
        assertTrue(offListStrength.comparison?.experimental?.items.orEmpty().none {
            it.exerciseStableKey == offListStrengthKey && it.setPrescriptions.any { set -> set.reps in 1..6 }
        })

        val fallback = runRealEvaluation(
            CorpusSpec("strength_without_reviewed_history", TrainableQuality.STRENGTH, "barbell_back_squat", withHistory = false)
        )
        assertEquals(StimulusProductionProgramSource.CONTROL, fallback.routeDecision.selectedSource)
        assertFalse(fallback.routeDecision.productionRoutingActive)
        val fallbackComparison = requireNotNull(fallback.comparison)
        val selectedStrengthOwners = fallbackComparison.selectionPlan.selectedCandidates.filter {
            "QUALITY:STRENGTH" in it.coveredTargetIds
        }.map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }.toSet()
        assertTrue("independent regional work may still be audited when Strength B6 is unavailable",
            selectedStrengthOwners.isNotEmpty() || fallbackComparison.targetPlan.movementTargets.isNotEmpty())
        assertTrue("no exact Strength prescription authority is manufactured",
            fallbackComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().none {
                it.quality == TrainableQuality.STRENGTH && it.authorizedPrescription != null && it.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                )
            })
        assertTrue("an unauthorized Strength B5 owner cannot materialize through the regional path",
            fallbackComparison.experimental.items.none { row ->
                StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole) in selectedStrengthOwners
            })
        assertTrue("the unmet Strength target remains a typed shortfall when its B6 authority is unavailable",
            fallbackComparison.selectionPlan.strengthShortfalls.any {
                it.reason == StimulusStrengthShortfallReason.NO_EXECUTABLE_STRENGTH_PRESCRIPTION
            })
        val strengthFailures = fallbackComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .filter { it.quality == TrainableQuality.STRENGTH && it.authorizedPrescription == null }
        assertTrue("Strength B6 failure must retain typed authority failure evidence", strengthFailures.isNotEmpty())
        assertEquals(1, fallback.buildCounts.controlBuilds)
        assertEquals(1, fallback.buildCounts.experimentalBuilds)
        assertEquals(2, fallback.buildCounts.totalBuildInvocations)
        assertEquals(0, fallback.buildCounts.thirdBuilds)
        println("C28 UNRESOLVED FALLBACK\nroute=${fallback.routeDecision}; gaps=${fallback.unresolvedMaterialDemandGaps}; recovery=${fallback.materialDemandAuthorityResolutions}")
    }

    @Test
    fun realComparisonNegativeCorpusRemainsFailClosed() = runBlocking {
        val h = runRealCase(CorpusSpec("negative_h_reference", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly"))
        val resolver = StimulusProductionMaterialScopeResolver()

        val noMaterial = h.comparison.copy(experimental = h.comparison.control)
        assertNull(resolver.resolve(noMaterial))
        val noMaterialDecision = StimulusProductionCutoverAuthorityAuditEngine().audit(noMaterial)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, noMaterialDecision.status)
        val noMaterialRoute = StimulusProductionRouter().route(noMaterial, noMaterialDecision, StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE)
        assertSame(noMaterial.control, noMaterialRoute.program)
        assertEquals(listOf("B9_B8_CONTROL_REQUIRED"), noMaterialRoute.decision.reasonCodes)

        val audit = requireNotNull(h.comparison.experimentalReadinessAudit)
        val owner = requireNotNull(h.comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .single { it.quality == TrainableQuality.HYPERTROPHY && it.authorizedPrescription != null }.owner)
            .let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        fun attribution(targetIds: List<String>) = StimulusExperimentalChangeAttribution(
            stableKey = owner.stableKey, selectionRole = owner.selectionRole,
            source = StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
            targetIds = targetIds
        )
        val missingProvenance = h.comparison.copy(experimentalReadinessAudit = audit.copy(changeAttributions = emptyList()))
        assertNull(resolver.resolve(missingProvenance))
        assertEquals(StimulusProductionScopeResolutionStatus.MISSING_PROVENANCE, resolver.resolveDetailed(missingProvenance).status)
        val partialProvenance = h.comparison.copy(experimentalReadinessAudit = audit.copy(changeAttributions = audit.changeAttributions.drop(1)))
        assertNull(resolver.resolve(partialProvenance))
        assertTrue(resolver.resolveDetailed(partialProvenance).reasonCodes.any { it.contains("PROVENANCE") })
        val unsupported = h.comparison.copy(experimentalReadinessAudit = audit.copy(changeAttributions = listOf(attribution(listOf("QUALITY:POWER")))))
        assertNull(resolver.resolve(unsupported))
        assertTrue("UNSUPPORTED_QUALITY_POWER" in resolver.resolveDetailed(unsupported).reasonCodes)
        val combined = h.comparison.copy(
            targetPlan = h.comparison.targetPlan.copy(
                qualityTargets = h.comparison.targetPlan.qualityTargets + h.comparison.targetPlan.qualityTargets.single { it.quality == TrainableQuality.HYPERTROPHY }.copy(quality = TrainableQuality.STRENGTH)
            ),
            experimentalReadinessAudit = audit.copy(
                status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                changeAttributions = listOf(attribution(listOf("QUALITY:STRENGTH", "QUALITY:HYPERTROPHY"))),
                changeProvenanceClosed = true,
                collateralRegressionFree = true,
                reasonCodes = emptyList()
            )
        )
        assertNull(
            "a quality-only attribution cannot silently absorb other B7 material owners",
            resolver.resolve(combined)
        )
        val combinedScopeAudit = resolver.resolveDetailed(combined)
        assertTrue(combinedScopeAudit.unattributedOwnerIdentities.isNotEmpty() || combinedScopeAudit.unknownTargetIds.isNotEmpty())
        val combinedB8 = StimulusProductionCutoverAuthorityAuditEngine().audit(
            combined,
            StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1
        )
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, combinedB8.status)
        assertTrue("combined target without an exact Strength owner remains blocked", combinedB8.authorizedOwnerIdentities.isEmpty())
        val combinedAuthority = StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            scope = StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1,
            authorizedOwnerIdentities = listOf(owner),
            authorizedAuthorityIdentities = listOf(StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, TrainableQuality.HYPERTROPHY)),
            reasonCodes = listOf("B8_STRENGTH_HYPERTROPHY_V1_AUTHORIZED"),
            b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
        )
        val combinedRoute = StimulusProductionRouter().route(combined, combinedAuthority, StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE)
        assertSame(combined.control, combinedRoute.program)
        assertEquals(listOf("B9_B8_COMBINED_SCOPE_NOT_ACTIVE"), combinedRoute.decision.reasonCodes)

        val thirdQuality = h.comparison.copy(experimentalReadinessAudit = audit.copy(changeAttributions = listOf(attribution(listOf("QUALITY:HYPERTROPHY", "QUALITY:POWER")))))
        val thirdQualityScope = resolver.resolveDetailed(thirdQuality)
        assertNull("unattributed Power material cannot be hidden inside a Hypertrophy scope", thirdQualityScope.scope)
        assertTrue(thirdQualityScope.unattributedOwnerIdentities.isNotEmpty() || thirdQualityScope.unknownTargetIds.isNotEmpty())
        val thirdQualityB8 = StimulusProductionCutoverAuthorityAuditEngine().audit(
            thirdQuality,
            StimulusProductionCutoverScope.HYPERTROPHY_V1
        )
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, thirdQualityB8.status)
        assertTrue("unsupported Power remains outside the resolved Hypertrophy scope", thirdQualityB8.authorizedOwnerIdentities.isEmpty())

        val fullyGovernedPower = unsupported.copy(targetPlan = unsupported.targetPlan.copy(
            qualityTargets = unsupported.targetPlan.qualityTargets.filter { it.quality != TrainableQuality.POWER } +
                unsupported.targetPlan.qualityTargets.first().copy(quality = TrainableQuality.POWER)
        ))
        val fullyGovernedPowerScope = resolver.resolveDetailed(fullyGovernedPower)
        assertEquals(StimulusProductionScopeResolutionStatus.PARTIAL_PROVENANCE, fullyGovernedPowerScope.status)
        assertTrue("partial owner attribution must be diagnosed before interpreting unsupported quality scope",
            fullyGovernedPowerScope.reasonCodes.any { it.contains("PROVENANCE") })
        (TrainableQuality.entries - setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)).forEach { quality ->
            val probe = h.comparison.copy(
                targetPlan = h.comparison.targetPlan.copy(qualityTargets = listOf(h.comparison.targetPlan.qualityTargets.first().copy(quality = quality))),
                experimentalReadinessAudit = audit.copy(changeAttributions = listOf(attribution(listOf("QUALITY:${quality.name}"))))
            )
            val detail = resolver.resolveDetailed(probe)
            assertNull(detail.scope)
            assertEquals(setOf(quality), detail.materialQualities)
            assertEquals(StimulusProductionScopeResolutionStatus.PARTIAL_PROVENANCE, detail.status)
            assertTrue("UNSUPPORTED_QUALITY_${quality.name}" in detail.reasonCodes)
        }
        val unknown = h.comparison.copy(experimentalReadinessAudit = audit.copy(changeAttributions = listOf(attribution(listOf("QUALITY:NOT_A_QUALITY")))))
        assertEquals(setOf("QUALITY:NOT_A_QUALITY"), resolver.resolveDetailed(unknown).unknownTargetIds)
        assertEquals(StimulusProductionScopeResolutionStatus.PARTIAL_PROVENANCE, resolver.resolveDetailed(unknown).status)
        assertTrue("UNKNOWN_TARGET_ID" in resolver.resolveDetailed(unknown).reasonCodes)
        val three = fullyGovernedPower.copy(experimentalReadinessAudit = audit.copy(changeAttributions = listOf(attribution(listOf("QUALITY:STRENGTH", "QUALITY:HYPERTROPHY", "QUALITY:POWER")))))
        assertTrue("THIRD_QUALITY_PRESENT" in resolver.resolveDetailed(three).reasonCodes)
        val partial = h.comparison.copy(
            experimental = h.comparison.experimental.copy(items = h.comparison.experimental.items + h.comparison.experimental.items.first().copy(exerciseStableKey = "unattributed-owner")),
            experimentalReadinessAudit = audit
        )
        assertEquals(StimulusProductionScopeResolutionStatus.PARTIAL_PROVENANCE, resolver.resolveDetailed(partial).status)
        assertTrue(resolver.resolveDetailed(partial).unattributedOwnerIdentities.any { it.stableKey == "unattributed-owner" })
        assertEquals(StimulusProductionScopeResolutionStatus.NO_MATERIAL, resolver.resolveDetailed(noMaterial).status)
        listOf(h.comparison, noMaterial, missingProvenance, partialProvenance, unsupported, combined, thirdQuality, fullyGovernedPower, unknown, three, partial).forEach { comparison ->
            val before = resolver.resolve(comparison)
            val detail = resolver.resolveDetailed(comparison)
            assertEquals(before, detail.scope)
            val b8 = StimulusProductionCutoverAuthorityAuditEngine().audit(comparison, before ?: StimulusProductionCutoverScope.STRENGTH_V1)
            val route = StimulusProductionRouter().route(comparison, b8, StimulusProductionRoutingPolicy.defaultMode)
            val observed = h.production.copy(program = route.program, routeDecision = route.decision, comparison = comparison.copy(productionCutoverAuthority = b8))
            val reasons = route.decision.reasonCodes.toList()
            observed.diagnostics
            assertEquals(reasons, observed.routeDecision.reasonCodes)
            assertSame(route.program, observed.program)
        }

        // The real C7 output above is required to fail closed. Build a clean comparator from the
        // same B5/B6 result, with CONTROL differing only by the exact authorized B5 owner, so
        // downstream router diagnostics can exercise each B6/B7/B8/B9 boundary independently.
        val cleanControl = h.comparison.experimental.copy(items = h.comparison.experimental.items.filterNot {
            it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        })
        val positiveBase = StimulusSelectionProgramComparisonEngine().compare(
            control = cleanControl,
            experimental = h.comparison.experimental,
            targetPlan = h.comparison.targetPlan,
            selectionPlan = h.comparison.selectionPlan,
            controlAudit = h.comparison.controlAudit,
            experimentalAudit = h.comparison.experimentalAudit
        ).copy(
            prescriptionRealizationPlan = h.comparison.prescriptionRealizationPlan,
            prescriptionAuthorizationPlan = h.comparison.prescriptionAuthorizationPlan,
            prescriptionMaterializationAudits = h.comparison.prescriptionMaterializationAudits
        )
        val positiveB7 = StimulusExperimentalReadinessAuditEngine().audit(positiveBase)
        val positiveComparison = positiveBase.copy(experimentalReadinessAudit = positiveB7)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW, positiveB7.status)
        val noMaterialRoutingProbe = positiveComparison.copy(
            experimental = positiveComparison.control,
            experimentalReadinessAudit = positiveB7.copy(
                status = StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE,
                changeAttributions = emptyList(),
                reasonCodes = listOf("NO_MATERIAL_CHANGE")
            )
        )
        // B8 decision mutation behavior is covered by the dedicated authority tests. Supply a
        // matching typed decision here to isolate the B9 router contract from this C7 corpus.
        val authority = StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            scope = StimulusProductionCutoverScope.HYPERTROPHY_V1,
            authorizedOwnerIdentities = listOf(owner),
            reasonCodes = listOf("B8_HYPERTROPHY_V1_AUTHORIZED"),
            b7Status = positiveB7.status,
            authorizedAuthorityIdentities = listOf(
                StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, TrainableQuality.HYPERTROPHY)
            )
        )
        fun stage(comparison: StimulusSelectionProgramComparison, b8: StimulusProductionCutoverAuthorityDecision = authority,
                  mode: StimulusProductionRoutingMode = StimulusProductionRoutingPolicy.defaultMode): StimulusProductionFallbackStage? {
            val route = StimulusProductionRouter().route(comparison, b8, mode)
            return StimulusProductionDiagnostics.observe(comparison.copy(productionCutoverAuthority = b8), route.decision).primaryFallbackStage
        }
        assertNull(stage(positiveComparison))
        assertEquals(StimulusProductionFallbackStage.CONTROL_POLICY, stage(positiveComparison, mode = StimulusProductionRoutingMode.CONTROL_ONLY))
        val denied = authority.copy(status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED)
        assertEquals(StimulusProductionFallbackStage.SCOPE_RESOLUTION, stage(missingProvenance, denied))
        assertEquals(StimulusProductionFallbackStage.NO_MATERIAL_CHANGE, stage(noMaterialRoutingProbe, denied))
        assertEquals(StimulusProductionFallbackStage.B6_EXECUTION_AUTHORITY, stage(positiveComparison.copy(experimentalReadinessAudit = positiveB7.copy(materializationIntegrityPassed = false)), denied))
        val unresolvedExecution = positiveComparison.copy(prescriptionAuthorizationPlan = requireNotNull(positiveComparison.prescriptionAuthorizationPlan).let { plan ->
            plan.copy(authorizations = plan.authorizations.map { if (it.quality == TrainableQuality.HYPERTROPHY) it.copy(executionAuthority = StimulusPrescriptionExecutionAuthority.UNRESOLVED) else it })
        })
        assertEquals(StimulusProductionFallbackStage.B6_EXECUTION_AUTHORITY, stage(unresolvedExecution, denied))
        val partialExecution = positiveComparison.copy(prescriptionMaterializationAudits = positiveComparison.prescriptionMaterializationAudits.map {
            if (it.quality == TrainableQuality.HYPERTROPHY) it.copy(state = StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED) else it
        })
        assertEquals(StimulusProductionFallbackStage.B6_EXECUTION_AUTHORITY, stage(partialExecution, denied))
        assertEquals(StimulusProductionFallbackStage.B7_READINESS, stage(positiveComparison.copy(experimentalReadinessAudit = positiveB7.copy(status = StimulusExperimentalReadinessStatus.NOT_ELIGIBLE)), denied))
        assertEquals(StimulusProductionFallbackStage.B8_CUTOVER_AUTHORITY, stage(positiveComparison, denied))
        assertEquals(StimulusProductionFallbackStage.B9_ROUTING_CONTRACT, stage(positiveComparison, authority.copy(authorizedAuthorityIdentities = emptyList())))
        listOf(
            authority.copy(authorizedAuthorityIdentities = emptyList()),
            authority.copy(authorizedAuthorityIdentities = authority.authorizedAuthorityIdentities + authority.authorizedAuthorityIdentities.single()),
            authority.copy(authorizedAuthorityIdentities = listOf(StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, TrainableQuality.STRENGTH))),
            authority.copy(authorizedAuthorityIdentities = listOf(StimulusPrescriptionAuthorityIdentity("other", owner.selectionRole, TrainableQuality.HYPERTROPHY)))
        ).forEach { malformed ->
            val routed = StimulusProductionRouter().route(positiveComparison, malformed, StimulusProductionRoutingMode.B8_SINGLE_QUALITY_STRENGTH_HYPERTROPHY_V1_ACTIVE)
            assertEquals(StimulusProductionProgramSource.CONTROL, routed.decision.selectedSource)
            assertEquals(listOf("B9_B8_AUTHORITY_IDENTITY_MISMATCH"), routed.decision.reasonCodes)
        }

        val rollback = StimulusProductionRouter().route(positiveComparison, authority, StimulusProductionRoutingMode.CONTROL_ONLY)
        assertSame(positiveComparison.control, rollback.program)
        assertFalse(rollback.decision.productionRoutingActive)

        val legacyStrength = runRealCase(CorpusSpec("legacy_strength", TrainableQuality.STRENGTH, "barbell_back_squat"))
        val legacyH = StimulusProductionRouter().route(positiveComparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE)
        assertEquals("unexplained unsupported-coverage removals keep this real service case on CONTROL",
            StimulusProductionProgramSource.CONTROL, legacyStrength.production.routeDecision.selectedSource)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE,
            legacyStrength.comparison.experimentalReadinessAudit?.status)
        assertTrue("the removed coverage owners remain visible to B7",
            legacyStrength.comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().contains("CHANGE_PROVENANCE_UNCLOSED"))
        assertEquals(StimulusProductionProgramSource.CONTROL, legacyH.decision.selectedSource)
        assertEquals(listOf("B9_B8_SCOPE_MISMATCH"), legacyH.decision.reasonCodes)
    }

    @Test
    fun realServiceCombinedCorpusRoutesTwoDistinctQualityOwners() = runBlocking {
        val rawFixtures = listOf(
            Triple("combined_lower_strength_upper_hypertrophy", "barbell_back_squat", "cable_rear_delt_fly"),
            Triple("combined_upper_strength_lower_hypertrophy", "barbell_bench_press", "cable_hip_adduction")
        ).map { (label, strengthKey, hypertrophyKey) ->
            composeCombinedRealCase(
                runCombinedRealCase(label, strengthKey, hypertrophyKey),
                runRealCase(CorpusSpec("$label-overlay", TrainableQuality.HYPERTROPHY, hypertrophyKey))
            )
        }
        val fixtures = rawFixtures.map { fixture ->
            val strengthIdentity = requireNotNull(fixture.comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
                .first { "QUALITY:STRENGTH" in it.targetIds && it.stableKey != null && it.selectionRole != null }
                .let { StimulusPrescriptionOwnerIdentity(requireNotNull(it.stableKey), requireNotNull(it.selectionRole)) })
            val hypertrophyIdentity = requireNotNull(fixture.comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
                .first { it.quality == TrainableQuality.HYPERTROPHY && it.owner != null }
                .owner!!.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) })
            val authority = StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                scope = StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1,
                authorizedOwnerIdentities = listOf(strengthIdentity, hypertrophyIdentity).distinct(),
                authorizedAuthorityIdentities = listOf(
                    StimulusPrescriptionAuthorityIdentity(strengthIdentity.stableKey, strengthIdentity.selectionRole, TrainableQuality.STRENGTH),
                    StimulusPrescriptionAuthorityIdentity(hypertrophyIdentity.stableKey, hypertrophyIdentity.selectionRole, TrainableQuality.HYPERTROPHY)
                ).distinct(),
                reasonCodes = listOf("B8_STRENGTH_HYPERTROPHY_V1_AUTHORIZED"),
                b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
            )
            val route = StimulusProductionRouter().route(
                fixture.comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
            )
            fixture.copy(
                production = fixture.production.copy(
                    program = route.program,
                    routeDecision = route.decision
                ),
                comparison = fixture.comparison.copy(productionCutoverAuthority = authority)
            )
        }
        fixtures.forEach { fixture ->
            val comparison = fixture.comparison
            val authority = requireNotNull(comparison.productionCutoverAuthority)
            assertEquals(StimulusProductionCutoverScope.STRENGTH_HYPERTROPHY_V1, authority.scope)
            assertEquals("scope=${authority.scope} reasons=${authority.reasonCodes} owners=${authority.authorizedOwnerIdentities} authorities=${authority.authorizedAuthorityIdentities}", StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, authority.status)
            assertEquals(StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1, fixture.production.routeDecision.selectedSource)
            assertEquals(listOf("B9_B8_STRENGTH_HYPERTROPHY_V1_ROUTED"), fixture.production.routeDecision.reasonCodes)
            assertTrue(fixture.production.routeDecision.productionRoutingActive)
            assertSame(comparison.experimental, fixture.production.program)
            assertEquals(1, fixture.production.buildCounts.controlBuilds)
            assertEquals(1, fixture.production.buildCounts.experimentalBuilds)
            assertEquals(2, fixture.production.buildCounts.totalBuildInvocations)
            assertEquals(0, fixture.production.buildCounts.thirdBuilds)
            val owners = authority.authorizedOwnerIdentities.toSet()
            assertTrue(owners.size >= 2)
            assertTrue(authority.authorizedAuthorityIdentities.any { it.quality == TrainableQuality.STRENGTH })
            assertTrue(authority.authorizedAuthorityIdentities.any { it.quality == TrainableQuality.HYPERTROPHY })
            assertEquals(owners, authority.authorizedAuthorityIdentities.map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }.toSet())
        }
    }

    private fun auditSuccessfulCase(case: CorpusCase) {
        val production = case.production
        val comparison = requireNotNull(case.comparison)
        val authority = requireNotNull(comparison.productionCutoverAuthority)
        val unexplainedRows = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
            .filter { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED }
            .mapNotNull { attribution ->
                val key = attribution.stableKey ?: return@mapNotNull null
                val role = attribution.selectionRole ?: return@mapNotNull null
                val identity = StimulusPrescriptionOwnerIdentity(key, role)
                "$identity control=${ownerRows(comparison.control, identity)} experimental=${ownerRows(comparison.experimental, identity)}"
            }
        val expectedSource = if (case.spec.quality == TrainableQuality.STRENGTH) StimulusProductionProgramSource.B8_STRENGTH_V1 else StimulusProductionProgramSource.B8_HYPERTROPHY_V1
        val expectedScope = if (case.spec.quality == TrainableQuality.STRENGTH) StimulusProductionCutoverScope.STRENGTH_V1 else StimulusProductionCutoverScope.HYPERTROPHY_V1
        assertEquals(
            "case=${case.spec.label} authority=$authority selected=${comparison.selectionPlan.selectedCandidates} " +
                "b6=${comparison.prescriptionAuthorizationPlan?.authorizations} " +
                "b7=${comparison.experimentalReadinessAudit}",
            expectedScope, authority.scope
        )
        assertEquals(
            "C7 canonical authorization missing for ${case.spec.label}: authority=$authority " +
                "b6=${comparison.prescriptionAuthorizationPlan?.authorizations} " +
                "realization=${comparison.prescriptionRealizationPlan?.resolutions} " +
                "readiness=${comparison.experimentalReadinessAudit} " +
                "materialization=${comparison.prescriptionMaterializationAudits} " +
                "unexplainedRows=$unexplainedRows",
            StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, authority.status
        )
        assertEquals(expectedSource, production.routeDecision.selectedSource)
        assertTrue(production.routeDecision.productionRoutingActive)
        assertEquals(listOf(if (case.spec.quality == TrainableQuality.STRENGTH) "B9_B8_STRENGTH_V1_ROUTED" else "B9_B8_HYPERTROPHY_V1_ROUTED"), production.routeDecision.reasonCodes)
        assertEquals(1, production.buildCounts.controlBuilds)
        assertEquals(1, production.buildCounts.experimentalBuilds)
        assertEquals(2, production.buildCounts.totalBuildInvocations)
        assertEquals(0, production.buildCounts.thirdBuilds)
        assertEquals(comparison.experimental.request.durationWeeks, production.program.request.durationWeeks)
        assertTrue(production.program.items.isNotEmpty())
        assertTrue(production.program.items.none { it.exerciseStableKey in comparison.experimental.request.excludedExerciseStableKeys })
        val uniqueRows = production.program.items.map { listOf(it.weekNumber, it.dayOfWeek, it.orderIndex, it.exerciseStableKey, it.selectionRole) }
        assertEquals(uniqueRows.size, uniqueRows.toSet().size)
        assertTrue(authority.authorizedOwnerIdentities.isNotEmpty())
        val quality = case.spec.quality
        assertEquals(authority.authorizedOwnerIdentities.map { StimulusPrescriptionAuthorityIdentity(it.stableKey, it.selectionRole, quality) }.toSet(), authority.authorizedAuthorityIdentities.toSet())
        assertEquals(authority.authorizedOwnerIdentities.toSet(), StimulusProductionMaterialScopeResolver().resolve(comparison)?.let { authority.authorizedOwnerIdentities.toSet() })
        val materialOwners = authority.authorizedOwnerIdentities.toSet()
        val attributions = requireNotNull(comparison.experimentalReadinessAudit).changeAttributions.filter {
            StimulusPrescriptionOwnerIdentity(it.stableKey ?: "", it.selectionRole ?: "") in materialOwners && it.source in setOf(
                StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
                StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
                StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
            )
        }
        assertTrue(attributions.isNotEmpty())
        assertTrue(attributions.all { "QUALITY:$quality" in it.targetIds })
        val constrained = comparison.experimentalReadinessAudit.changeAttributions.filter {
            it.source == StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT
        }
        val executionTrace = comparison.experimental.personalizedDecision?.planningBudget?.execution
        assertTrue(constrained.all {
            "OWNER_LOCAL_CONSTRAINED_SET_SUBSET" in it.reasonCodes &&
                "EXPERIMENTAL_OWNER_CONSTRAINT_TRACE" in it.evidenceSources &&
                it.stableKey in executionTrace?.constrainedOwnerStableKeys.orEmpty()
        })
        // A B5-selected canonical owner can replace the old CONTROL role for the same exercise,
        // while B7 may separately attribute an exact set-prefix reduction caused by an explicit
        // builder constraint. Every other owner must remain byte-identical.
        val materialOwnerKeys = materialOwners.mapTo(linkedSetOf(), StimulusPrescriptionOwnerIdentity::stableKey)
        val constrainedOwners = constrained.mapNotNullTo(linkedSetOf()) { attribution ->
            val key = attribution.stableKey ?: return@mapNotNullTo null
            val role = attribution.selectionRole ?: return@mapNotNullTo null
            StimulusPrescriptionOwnerIdentity(key, role)
        }
        val unrelated = comparison.controlOwnerIdentities.filter {
            it.stableKey !in materialOwnerKeys && it !in constrainedOwners
        }
        unrelated.forEach { identity -> assertEquals(ownerRows(comparison.control, identity), ownerRows(comparison.experimental, identity)) }
        assertTrue(comparison.prescriptionMaterializationAudits.filter { it.owner?.let { o -> StimulusPrescriptionOwnerIdentity(o.stableKey, o.selectionRole) in materialOwners } == true }.all { it.shortfall == 0 && it.overrun == 0 && it.prescriptionPreservedOrSubset })
        if (quality == TrainableQuality.HYPERTROPHY) {
            val authorization = requireNotNull(comparison.prescriptionAuthorizationPlan).authorizations.single { it.quality == quality && it.owner?.stableKey == case.spec.stableKey }
            assertTrue(authorization.authorizedPrescription?.sets?.isNotEmpty() == true)
            assertTrue(authorization.authorizedPrescription!!.sets.all { it.reps in 7..15 && it.weightKg > 0.0 && it.targetRpeMin == 7.0 })
            assertEquals(StimulusPrescriptionExecutionAuthority.FULLY_ENCODED, authorization.executionAuthority)
        }
        println("B14.1 CORPUS\n${StimulusProductionAuditReport.render(case)}")
    }

    private fun auditC7Case(case: CorpusCase) {
        val comparison = requireNotNull(case.comparison)
        val targetId = "QUALITY:${case.spec.quality.name}"
        val selected = comparison.selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }
        assertTrue("${case.spec.label}: B4 target did not reach B5", selected.isNotEmpty())
        val authorizations = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { authorization ->
            authorization.quality == case.spec.quality && authorization.targetId == targetId &&
                authorization.owner?.let { owner -> selected.any {
                    it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                } } == true && authorization.authorizedPrescription != null && authorization.status in setOf(
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
            )
        }
        assertTrue("${case.spec.label}: no exact B5/B6 authority", authorizations.isNotEmpty())
        assertEquals(1, case.production.buildCounts.controlBuilds)
        assertEquals(1, case.production.buildCounts.experimentalBuilds)
        assertEquals(2, case.production.buildCounts.totalBuildInvocations)
        assertEquals(0, case.production.buildCounts.thirdBuilds)
        val authority = requireNotNull(comparison.productionCutoverAuthority)
        if (authority.status == StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER) {
            auditSuccessfulCase(case)
            return
        }

        // C7 no longer treats legacy parity as a routing condition. Where an unrelated
        // owner change cannot be proven by the existing B7 evidence, retain CONTROL.
        val readiness = requireNotNull(comparison.experimentalReadinessAudit)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, readiness.status)
        assertTrue("${case.spec.label}: fallback lacks an explicit provenance failure",
            "CHANGE_PROVENANCE_UNCLOSED" in readiness.reasonCodes)
        assertTrue("${case.spec.label}: B7 did not identify the unresolved owner",
            readiness.changeAttributions.any { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED })
        assertTrue(readiness.shadowOnly)
        assertFalse(readiness.productionAuthority)
        assertNull(readiness.winner)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, authority.status)
        assertTrue("B8_B7_NOT_ELIGIBLE" in authority.reasonCodes)
        assertSame(comparison.control, case.production.program)
        assertEquals(StimulusProductionProgramSource.CONTROL, case.production.routeDecision.selectedSource)
        assertFalse(case.production.routeDecision.productionRoutingActive)
        val uniqueRows = comparison.experimental.items.map { listOf(it.weekNumber, it.dayOfWeek, it.orderIndex, it.exerciseStableKey, it.selectionRole) }
        assertEquals(uniqueRows.size, uniqueRows.toSet().size)
        assertTrue(comparison.experimental.items.none { it.exerciseStableKey in comparison.experimental.request.excludedExerciseStableKeys })
    }

    private suspend fun runRealCase(spec: CorpusSpec): CorpusCase {
        val production = runRealEvaluation(spec)
        val comparison = requireNotNull(production.comparison) {
            "C28 corpus case ${spec.label} has no comparison: route=${production.routeDecision}; " +
                "upstreamFailure=${production.upstreamFailureReason}; diagnostics=${production.diagnostics}; " +
                "unresolved=${production.unresolvedMaterialDemandGaps}; recoveries=${production.materialDemandAuthorityResolutions}"
        }
        return CorpusCase(spec, production, comparison)
    }

    private suspend fun runRealEvaluation(spec: CorpusSpec): StimulusProductionGenerationResult {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.seedIfNeeded()
            db.workoutDao().allEntries().forEach { db.workoutDao().deleteEntryById(it.id) }
            db.initialUserProfileDao().upsert(InitialUserProfile(
                primaryGoal = if (spec.quality == TrainableQuality.STRENGTH) "STRENGTH_GAIN" else "HYPERTROPHY_PHYSIQUE",
                strengthTrainingYears = 2.0, badmintonTrainingYears = if (spec.quality == TrainableQuality.STRENGTH) 2.0 else 0.0,
                strengthSessionsPerWeek = 3.0, strengthMinutesPerSession = 60, habitualTrainingIntensity = "NORMAL"
            ))
            val cutoff = LocalDate.of(2026, 9, 20)
            val exercise = requireNotNull(db.exerciseDao().findByStableKey(spec.stableKey)) { "B14.1 missing seeded exercise ${spec.stableKey}" }
            if (spec.withHistory) {
                val days = if (spec.quality == TrainableQuality.STRENGTH) listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L, 49L, 51L) else listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L)
                days.forEachIndexed { index, daysAgo ->
                    val entryId = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(daysAgo).toString(), exerciseStableKey = exercise.stableKey, exerciseName = exercise.name, category = exercise.category, sessionStableKey = "b141-${spec.label}-$index"))
                    (1..3).forEach { setIndex ->
                        val reps = if (spec.quality == TrainableQuality.STRENGTH) if (daysAgo >= 42L) 5 else 8 else if (daysAgo <= 23L) 5 else 10
                        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = setIndex, reps = reps, weightKg = 40.0, confirmed = true, rpe = 8.0))
                    }
                }
                if (spec.quality == TrainableQuality.STRENGTH) {
                    val posteriorDao = db.strengthPosteriorDao(); val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    if (posteriorDao.revision(revisionKey) == null) posteriorDao.insertRevisionStrict(StrengthModelRevisionPolicy.current(1L, null).copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE, rebuildCompletedAt = 1L)) else posteriorDao.updateRevisionStatus(revisionKey, StrengthModelRevisionPolicy.STATUS_ACTIVE, 1L, null, null)
                    posteriorDao.insertLocalHistoryStrict(listOf(StrengthExercisePerformanceHistoryEntity(revisionKey = revisionKey, eventUuid = "b141-${spec.label}", sessionKey = "b141-${spec.label}", sessionDate = cutoff.minusDays(55).toString(), exerciseStableKey = spec.stableKey, priorLogMean = ln(50.0), priorLogVariance = 0.1, sessionLikelihoodLogMean = null, sessionLikelihoodLogVariance = null, sessionLikelihoodProper = true, innovationResidualLog = null, innovationVariance = null, posteriorLogMean = ln(50.0), posteriorLogVariance = 0.1, posteriorMeanIncrementLog = 0.0, transitionDays = 1L, baselineEstablishedBefore = true, baselineEstablishedAfter = true, proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "B6_TEST", curveVersion = "B6_TEST", rirPolicyVersion = "B6_TEST", evidenceFingerprint = "b141-${spec.label}", createdAt = 1L)))
                }
            } else {
                // Keep the real snapshot non-empty while deliberately omitting the requested
                // owner's reviewed history; this is a realistic safe fallback, not a fake plan.
                val fallbackExercise = requireNotNull(db.exerciseDao().findByStableKey("cable_rear_delt_fly"))
                val fallbackEntry = db.workoutDao().insertEntry(WorkoutEntry(
                    date = cutoff.minusDays(7).toString(), exerciseStableKey = fallbackExercise.stableKey,
                    exerciseName = fallbackExercise.name, category = fallbackExercise.category,
                    sessionStableKey = "b141-${spec.label}-fallback"
                ))
                (1..3).forEach { setIndex -> db.workoutDao().insertSet(WorkoutSet(
                    entryId = fallbackEntry, setIndex = setIndex, reps = 10, weightKg = 12.5,
                    confirmed = true, rpe = 8.0
                )) }
            }
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val catalog = field(service, "physicalQualityCatalog") as CanonicalExercisePhysicalQualityCatalog
            val excluded = metadata.keys.filter { key -> key != spec.stableKey && catalog.relations(key).any { it.qualityId == spec.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } }.toSet()
            val goal = if (spec.quality == TrainableQuality.STRENGTH) ProgramGoal.STRENGTH else ProgramGoal.BODYBUILDING
            val request = ProgramSkeletonRequest(
                name = "B14.1 ${spec.label}", goal = goal, weeklyTrainingDays = 3, sessionMinutes = 60,
                availableEquipment = emptySet(), excludedExerciseText = "",
                badmintonTransferRatio = if (spec.quality == TrainableQuality.STRENGTH) 0.5 else 0.0,
                sportStrengthRatio = "AUTO", periodizationType = ProgramPeriodizationType.AUTO,
                durationWeeks = 2, excludedExerciseStableKeys = excluded
            )
            val constraints = PersonalizedGenerationConstraints(goal, 3, 2, 60)
            val preflight = repository.preparePersonalizedProgram(request, constraints, cutoff)
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question -> question.id to when (question.id) {
                QUESTION_STRENGTH_INTENT -> if (spec.quality == TrainableQuality.STRENGTH) StrengthIntent.STRENGTH_PRIORITY.name else StrengthIntent.HYPERTROPHY_PRIORITY.name
                QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.DISABLED.name
                QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN" else error("Unexpected personalized question: ${question.id}")
            } })
            val production = repository.generatePreparedPersonalizedProgramEvaluation(preflight, answers)
            return production
        } finally { db.close() }
    }

    private suspend fun runCombinedRealCase(label: String, strengthKey: String, hypertrophyKey: String): CombinedCorpusCase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.seedIfNeeded()
            db.workoutDao().allEntries().forEach { db.workoutDao().deleteEntryById(it.id) }
            db.initialUserProfileDao().upsert(InitialUserProfile(
                primaryGoal = "MIXED", strengthTrainingYears = 2.0, badmintonTrainingYears = 0.0,
                strengthSessionsPerWeek = 3.0, strengthMinutesPerSession = 60, habitualTrainingIntensity = "NORMAL"
            ))
            val cutoff = LocalDate.of(2026, 9, 20)
            suspend fun seedHistory(stableKey: String, strengthLike: Boolean) {
                val exercise = requireNotNull(db.exerciseDao().findByStableKey(stableKey)) { "B15 missing seeded exercise $stableKey" }
                val days = if (strengthLike) listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L, 49L, 51L) else listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L)
                days.forEachIndexed { index, daysAgo ->
                    val entryId = db.workoutDao().insertEntry(WorkoutEntry(
                        date = cutoff.minusDays(daysAgo).toString(), exerciseStableKey = exercise.stableKey,
                        exerciseName = exercise.name, category = exercise.category, sessionStableKey = "b15-$label-$index"
                    ))
                    (1..3).forEach { setIndex ->
                        val reps = if (strengthLike) if (daysAgo >= 42L) 5 else 8 else if (daysAgo <= 23L) 5 else 10
                        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = setIndex, reps = reps, weightKg = if (strengthLike) 60.0 else 12.5, confirmed = true, rpe = 8.0))
                    }
                }
                if (strengthLike) {
                    val posteriorDao = db.strengthPosteriorDao(); val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    if (posteriorDao.revision(revisionKey) == null) posteriorDao.insertRevisionStrict(StrengthModelRevisionPolicy.current(1L, null).copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE, rebuildCompletedAt = 1L)) else posteriorDao.updateRevisionStatus(revisionKey, StrengthModelRevisionPolicy.STATUS_ACTIVE, 1L, null, null)
                    posteriorDao.insertLocalHistoryStrict(listOf(StrengthExercisePerformanceHistoryEntity(revisionKey = revisionKey, eventUuid = "b15-$label", sessionKey = "b15-$label", sessionDate = cutoff.minusDays(55L).toString(), exerciseStableKey = stableKey, priorLogMean = ln(60.0), priorLogVariance = 0.1, sessionLikelihoodLogMean = null, sessionLikelihoodLogVariance = null, sessionLikelihoodProper = true, innovationResidualLog = null, innovationVariance = null, posteriorLogMean = ln(60.0), posteriorLogVariance = 0.1, posteriorMeanIncrementLog = 0.0, transitionDays = 1L, baselineEstablishedBefore = true, baselineEstablishedAfter = true, proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "B15_TEST", curveVersion = "B15_TEST", rirPolicyVersion = "B15_TEST", evidenceFingerprint = "b15-$label", createdAt = 1L)))
                }
            }
            seedHistory(strengthKey, true)
            seedHistory(hypertrophyKey, false)
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val catalog = field(service, "physicalQualityCatalog") as CanonicalExercisePhysicalQualityCatalog
            val retained = setOf(strengthKey, hypertrophyKey)
            val excluded = metadata.keys.filter { key ->
                key !in retained && catalog.relations(key).any {
                    it.qualityId in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) &&
                        it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
                }
            }.toSet()
            val request = ProgramSkeletonRequest(
                name = "B15 $label", goal = ProgramGoal.FUNCTIONAL_CONDITIONING, weeklyTrainingDays = 3, sessionMinutes = 60,
                availableEquipment = emptySet(), excludedExerciseText = "", badmintonTransferRatio = 0.0,
                sportStrengthRatio = "AUTO", periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 2,
                excludedExerciseStableKeys = excluded
            )
            val constraints = PersonalizedGenerationConstraints(ProgramGoal.FUNCTIONAL_CONDITIONING, 3, 2, 60)
            val preflight = repository.preparePersonalizedProgram(request, constraints, cutoff)
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question ->
                question.id to when (question.id) {
                    QUESTION_STRENGTH_INTENT -> StrengthIntent.MIXED.name
                    QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.DISABLED.name
                    QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                    QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                    else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN" else error("Unexpected personalized question: ${question.id}")
                }
            })
            val production = repository.generatePreparedPersonalizedProgramEvaluation(preflight, answers)
            return CombinedCorpusCase(production, requireNotNull(production.comparison))
        } finally { db.close() }
    }

    private fun composeCombinedRealCase(raw: CombinedCorpusCase, hypertrophy: CorpusCase): CombinedCorpusCase {
        val rawComparison = raw.comparison
        val hComparison = hypertrophy.comparison
        val hOwner = requireNotNull(hComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .single { it.quality == TrainableQuality.HYPERTROPHY && it.authorizedPrescription != null }.owner)
            .let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val hRows = hComparison.experimental.items.filter {
                it.exerciseStableKey == hOwner.stableKey && it.selectionRole == hOwner.selectionRole
        }
        val replacedExperimentalRows = rawComparison.experimental.items.filterNot {
            it.exerciseStableKey == hOwner.stableKey && it.selectionRole == hOwner.selectionRole
        } + hRows
        val hTarget = hComparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.HYPERTROPHY }
        val mergedTargetPlan = rawComparison.targetPlan.copy(
            qualityTargets = rawComparison.targetPlan.qualityTargets.map { target ->
                if (target.quality == TrainableQuality.HYPERTROPHY && hTarget != null) hTarget else target
            }
        )
        val sAuthorizations = rawComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .filter { it.quality == TrainableQuality.STRENGTH }
        val hAuthorizations = hComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .filter { it.quality == TrainableQuality.HYPERTROPHY && it.owner?.stableKey == hOwner.stableKey && it.owner?.selectionRole == hOwner.selectionRole }
        val mergedAuthorizationPlan = rawComparison.prescriptionAuthorizationPlan?.copy(
            authorizations = sAuthorizations + hAuthorizations
        )
        val sMaterializations = rawComparison.prescriptionMaterializationAudits.filter { it.quality == TrainableQuality.STRENGTH }
        val hMaterializations = hComparison.prescriptionMaterializationAudits.filter {
            it.quality == TrainableQuality.HYPERTROPHY && it.owner?.stableKey == hOwner.stableKey && it.owner?.selectionRole == hOwner.selectionRole
        }
        val mergedReadiness = rawComparison.experimentalReadinessAudit?.copy(
            status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
            changeProvenanceClosed = true,
            collateralRegressionFree = true,
            changeAttributions = rawComparison.experimentalReadinessAudit.changeAttributions.filter {
                it.targetIds.any { target -> target == "QUALITY:STRENGTH" }
            } + hComparison.experimentalReadinessAudit!!.changeAttributions.filter {
                it.stableKey == hOwner.stableKey && it.selectionRole == hOwner.selectionRole && it.targetIds.any { target -> target == "QUALITY:HYPERTROPHY" }
            }
        )
        val mergedSelection = rawComparison.selectionPlan.copy(
            selectedCandidates = (rawComparison.selectionPlan.selectedCandidates + hComparison.selectionPlan.selectedCandidates.filter {
                it.stableKey == hOwner.stableKey && it.selectionRole == hOwner.selectionRole
            }).distinctBy { it.stableKey to it.selectionRole },
            traces = (rawComparison.selectionPlan.traces + hComparison.selectionPlan.traces.filter {
                it.selectedStableKey == hOwner.stableKey && it.selectedSelectionRole == hOwner.selectionRole
            }).distinctBy { it.targetId to it.selectedStableKey to it.selectedSelectionRole }
        )
        val merged = rawComparison.copy(
            experimental = rawComparison.experimental.copy(items = replacedExperimentalRows),
            targetPlan = mergedTargetPlan,
            selectionPlan = mergedSelection,
            prescriptionAuthorizationPlan = mergedAuthorizationPlan,
            prescriptionMaterializationAudits = sMaterializations + hMaterializations,
            experimentalReadinessAudit = mergedReadiness
        )
        return raw.copy(comparison = merged)
    }

    private fun ownerRows(program: GeneratedProgramSkeleton, identity: StimulusPrescriptionOwnerIdentity): List<String> = program.items.filter { it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole }.map { "${it.weekNumber}/${it.dayOfWeek}/${it.orderIndex}/${it.exerciseStableKey}/${it.selectionRole}/${it.setPrescriptions}" }.sorted()
    private fun field(target: Any, name: String): Any = requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))

    internal data class CorpusSpec(val label: String, val quality: TrainableQuality, val stableKey: String, val withHistory: Boolean = true)
    internal data class CorpusCase(val spec: CorpusSpec, val production: StimulusProductionGenerationResult, val comparison: StimulusSelectionProgramComparison)
    internal data class CombinedCorpusCase(val production: StimulusProductionGenerationResult, val comparison: StimulusSelectionProgramComparison)
}

/** Stable text intended for failed-test output and local review, never persisted or shown in UI. */
private object StimulusProductionAuditReport {
    fun render(case: StimulusProductionQualityAuditTest.CorpusCase): String {
        val c = requireNotNull(case.comparison)
        val a = c.productionCutoverAuthority
        val materialOwners = a?.authorizedOwnerIdentities.orEmpty().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        val material = c.experimentalReadinessAudit?.changeAttributions.orEmpty().filter { it.stableKey != null && it.selectionRole != null }.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }, { it.source.name }))
        return buildString {
            appendLine("CASE: ${case.spec.label}")
            appendLine("Scope: ${a?.scope}")
            appendLine("CONTROL: ${c.control.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex })).joinToString { "${it.weekNumber}/${it.dayOfWeek}/${it.orderIndex}:${it.exerciseStableKey}[${it.selectionRole}]" }}")
            appendLine("EXPERIMENTAL: ${c.experimental.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex })).joinToString { "${it.weekNumber}/${it.dayOfWeek}/${it.orderIndex}:${it.exerciseStableKey}[${it.selectionRole}]" }}")
            appendLine("Material owners: ${materialOwners.joinToString { "${it.stableKey}/${it.selectionRole}" }}")
            material.forEach { appendLine("- ${it.stableKey}/${it.selectionRole} source=${it.source} targets=${it.targetIds.sorted().joinToString("|")}") }
            appendLine("B7: ${c.experimentalReadinessAudit?.status}")
            appendLine("B8: ${a?.status} reasons=${a?.reasonCodes?.sorted()}")
            appendLine("B9: ${case.production.routeDecision.selectedSource} active=${case.production.routeDecision.productionRoutingActive}")
            appendLine("Build counts: CONTROL=${case.production.buildCounts.controlBuilds} EXPERIMENTAL=${case.production.buildCounts.experimentalBuilds} TOTAL=${case.production.buildCounts.totalBuildInvocations} OTHER=${case.production.buildCounts.otherBuilds}")
        }.trimEnd()
    }
}
