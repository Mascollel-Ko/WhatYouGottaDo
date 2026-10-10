package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.ln

/** Unmodified real Room/service results; outcomes are measurements, never success quotas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusProductionCoverageAuditTest {
    internal data class CoverageSpec(
        val label: String, val quality: TrainableQuality, val stableKey: String,
        val profileGoal: String, val goal: ProgramGoal, val intent: StrengthIntent,
        val badminton: Boolean, val history: String, val days: Int, val minutes: Int,
        val equipment: Set<String>, val isolateOwner: Boolean = false,
        val explicitWeeklyDays: Boolean = true, val explicitDuration: Boolean = true
    )

    @Test
    fun hardValidIncumbentPlacementIsStableAcrossAcceptedRegeneration() = runBlocking {
        val spec = CoverageSpec(
            label = "persona0_mixed",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "mixed",
            days = 2,
            minutes = 60,
            equipment = setOf("MACHINE", "CABLE")
        )
        val result = runCase(
            spec,
            seedIncumbentPlacementFixture = true,
            verifyRepeatedAcceptedRegeneration = true
        )
        assertNotNull(result)
    }

    @Test
    fun unavailableStrengthOwnerFinishesWithTypedShortfallAndUserNotice() = runBlocking {
        val spec = CoverageSpec(
            label = "c32_strength_unavailable_equipment",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 3,
            minutes = 60,
            equipment = setOf("MACHINE", "CABLE"),
            isolateOwner = true
        )
        var canonicalPlanning: CanonicalStimulusPlanningResult? = null
        val result = requireNotNull(runCase(spec, observeCanonicalPlanning = { canonicalPlanning = it }))

        assertTrue("B4 must retain the Strength need", canonicalPlanning!!.targetPlan.qualityTargets.any {
            it.quality == TrainableQuality.STRENGTH
        })
        assertTrue(result.strengthShortfalls.any {
            it.reason == StimulusStrengthShortfallReason.NO_ELIGIBLE_STRENGTH_EXERCISE
        })
        assertTrue(result.program.optimizationSummary.notices.any {
            it.code == ProgramUserNoticeCode.STRENGTH_EXPOSURE_SHORTFALL &&
                it.level == ProgramUserNoticeLevel.WARNING
        })
        assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
        assertNotNull("generation returns the intact user program instead of retrying indefinitely", result.program)
        assertEquals(1, result.buildCounts.controlBuilds)
        assertTrue(result.buildCounts.experimentalBuilds <= 1)
        assertEquals(result.buildCounts.controlBuilds + result.buildCounts.experimentalBuilds,
            result.buildCounts.totalBuildInvocations)
        assertEquals(0, result.buildCounts.thirdBuilds)
        assertFalse("an unavailable Strength owner must not leak into an EXP skeleton",
            result.comparison?.experimental?.items.orEmpty().any { item ->
                item.selectionRole.startsWith("CANONICAL_STIMULUS_QUALITY_STRENGTH") &&
                    item.setPrescriptions.any {
                        plannedTargetSetStimulusClass(item.exerciseStableKey, it.reps, item.selectionRole, TrainableQuality.STRENGTH) ==
                            RealizedStimulusClass.STRENGTH_LIKE
                    }
            })
    }

    @Test
    fun measureUnmodifiedServiceCoverage() = runBlocking {
        // Snapshot the small exact-key metadata facts before generating the memory-heavy corpus.
        // The census must not reparse all canonical tissue assets after the production runs.
        val canonicalMetadataRepository = CanonicalExerciseMetadataRepository(ApplicationProvider.getApplicationContext())
        val c18TissueAuthoritySnapshot = C18TissueIncumbentPlacementCensus.captureTissueAuthoritySnapshot(
            canonicalMetadataRepository
        )
        val histories = listOf("none", "sparse", "recent", "reviewed", "mixed")
        val equipment = listOf(setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"), setOf("MACHINE", "CABLE"), emptySet())
        val specs = (0..4).flatMap { persona -> histories.mapIndexed { historyIndex, history ->
            val h = persona == 1
            CoverageSpec(
                label = "persona${persona}_${history}",
                quality = if (h) TrainableQuality.HYPERTROPHY else TrainableQuality.STRENGTH,
                stableKey = if (h) "cable_rear_delt_fly" else "barbell_back_squat",
                profileGoal = when (persona) { 0 -> "STRENGTH_GAIN"; 1 -> "HYPERTROPHY_PHYSIQUE"; else -> "MIXED" },
                goal = when (persona) { 0 -> ProgramGoal.STRENGTH; 1 -> ProgramGoal.BODYBUILDING; 3 -> ProgramGoal.BADMINTON_SUPPORT; else -> ProgramGoal.FUNCTIONAL_CONDITIONING },
                intent = when (persona) { 0 -> StrengthIntent.STRENGTH_PRIORITY; 1 -> StrengthIntent.HYPERTROPHY_PRIORITY; else -> StrengthIntent.MIXED },
                badminton = persona == 3, history = history, days = 2 + (persona + historyIndex) % 4,
                minutes = listOf(30, 60, 90)[(persona + historyIndex) % 3],
                equipment = equipment[(persona * 2 + historyIndex) % 3]
            )
        } } + listOf(
            CoverageSpec("reviewed_strength_isolated", TrainableQuality.STRENGTH, "barbell_back_squat", "STRENGTH_GAIN", ProgramGoal.STRENGTH, StrengthIntent.STRENGTH_PRIORITY, false, "reviewed", 3, 60, emptySet(), true),
            CoverageSpec("reviewed_hypertrophy_isolated", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly", "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY, false, "reviewed", 3, 60, emptySet(), true)
        )
        val canonicalPlanningByCase = linkedMapOf<String, CanonicalStimulusPlanningResult>()
        val productionContextByCase = linkedMapOf<String, PreparedCanonicalGenerationContext>()
        val c29PhaseDurationsByCase = linkedMapOf<String, Map<String, Long>>()
        val c29GenerationDurationsByCase = linkedMapOf<String, Long>()
        val c29PlannerMetricsByCase = linkedMapOf<String, Map<String, Int>>()
        val c30SelectionPlanByCase = linkedMapOf<String, StimulusCandidateSelectionPlan>()
        val c30AuthorizationPlanByCase = linkedMapOf<String, StimulusPrescriptionAuthorizationPlan>()
        val records = specs.map { spec ->
            val result = runCase(spec, seedIncumbentPlacementFixture = spec.label in
                setOf("persona0_mixed", "persona0_reviewed", "persona3_reviewed", "persona4_mixed"), observeCanonicalPlanning = { planning ->
                canonicalPlanningByCase[spec.label] = planning
            }, evaluateWithIncumbent = { service, preflight, answers, metadata, incumbentIndex ->
                withContext(Dispatchers.IO) {
                    val generationStartedAt = System.nanoTime()
                    val previousPhaseAt = linkedMapOf<ProductionGenerationPhase, Long>()
                    val phaseDurations = linkedMapOf<String, Long>()
                    try {
                        service.generatePreparedProduction(
                            preflight = preflight,
                            answers = answers,
                            metadata = metadata,
                            incumbentPlacementIndex = incumbentIndex,
                            productionGenerationObserver = { observation ->
                                val observedAt = System.nanoTime()
                                if (observation.phase == ProductionGenerationPhase.CANONICAL_PREPARED) {
                                    productionContextByCase[spec.label] = observation.context
                                    phaseDurations["generation_start_to_canonical_prepared"] =
                                        (observedAt - generationStartedAt).coerceAtLeast(0L) / 1_000_000L
                                }
                                previousPhaseAt.entries.lastOrNull()?.let { (previous, previousAt) ->
                                    phaseDurations["${previous.name}_to_${observation.phase.name}"] =
                                        (observedAt - previousAt).coerceAtLeast(0L) / 1_000_000L
                                }
                                previousPhaseAt[observation.phase] = observedAt
                                if (observation.phase == ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE) {
                                    val builder = field(service, "programBuilder") as PersonalizedProgramBuilder
                                    c29PlannerMetricsByCase[spec.label] = builder.lastPerformanceMetrics.toMap()
                                }
                                observation.selectionPlan?.let { c30SelectionPlanByCase[spec.label] = it }
                                observation.authorizationPlan?.let { c30AuthorizationPlanByCase[spec.label] = it }
                            }
                        )
                    } finally {
                        c29GenerationDurationsByCase[spec.label] =
                            (System.nanoTime() - generationStartedAt).coerceAtLeast(0L) / 1_000_000L
                        c29PhaseDurationsByCase[spec.label] = phaseDurations.toMap()
                    }
                }
            })
            if (result == null) {
                assertEquals("Only the real no-history precondition may reject this corpus", "none", spec.history)
                return@map spec to null
            }
            requireNotNull(canonicalPlanningByCase[spec.label]) { "${spec.label} missing CONTROL-independent B1-B4 evidence" }
            val comparison = result.comparison
            assertEquals(spec.label, 1, result.buildCounts.controlBuilds)
            assertEquals(spec.label, 0, result.buildCounts.thirdBuilds)
            if (comparison != null) {
                assertEquals(spec.label, 1, result.buildCounts.experimentalBuilds)
                assertEquals(spec.label, 2, result.buildCounts.totalBuildInvocations)
                assertEquals("${spec.label} CONTROL and EXPERIMENTAL must use the same complete request",
                    comparison.control.request, comparison.experimental.request)
                val selected = if (result.routeDecision.productionRoutingActive) comparison.experimental else comparison.control
                assertEquals(selected.copy(
                    incumbentSourceSnapshotToken = result.program.incumbentSourceSnapshotToken,
                    optimizationSummary = result.program.optimizationSummary
                ), result.program.copy(replacementReview = null))
                assertEquals(spec.label, comparison.selectionPlan.strengthShortfalls, result.strengthShortfalls)
                assertEquals(StimulusProductionMaterialScopeResolver().resolve(comparison), result.diagnostics.scopeResolution?.scope)
            }
            if (result.strengthShortfalls.isNotEmpty()) {
                assertTrue("${spec.label} must disclose an unmet Strength target, including safe CONTROL fallback",
                    result.program.optimizationSummary.notices.any {
                        it.code == ProgramUserNoticeCode.STRENGTH_EXPOSURE_SHORTFALL &&
                            it.level == ProgramUserNoticeLevel.WARNING
                    })
            }
            assertEquals(!result.routeDecision.productionRoutingActive, result.diagnostics.primaryFallbackStage != null)
            spec to result
        }
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        val authorizedStrengthStatuses = setOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        )
        generated.forEach { (spec, result) ->
            val comparison = result.comparison ?: return@forEach
            comparison.experimental.items.forEach { item ->
                item.setPrescriptions.filter { set ->
                    plannedTargetSetStimulusClass(item.stableKey, set.reps, item.selectionRole, TrainableQuality.STRENGTH) ==
                        RealizedStimulusClass.STRENGTH_LIKE
                }.forEach { set ->
                    assertTrue(
                        "${spec.label} ${item.stableKey}#${item.selectionRole} ${set.reps} reps must have exact authorized Strength B6",
                        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { authorization ->
                            authorization.quality == TrainableQuality.STRENGTH &&
                                authorization.owner?.stableKey == item.stableKey &&
                                authorization.owner.selectionRole == item.selectionRole &&
                                authorization.status in authorizedStrengthStatuses
                        }
                    )
                }
            }
        }
        java.io.File("build/reports/c28-generation-fallback-diagnostics.json").apply {
            parentFile?.mkdirs()
            writeText(org.json.JSONArray(generated.filter { it.second.comparison == null }.map { (spec, result) ->
                org.json.JSONObject()
                    .put("case", spec.label)
                    .put("upstreamFailureReason", result.upstreamFailureReason)
                    .put("upstreamFailureDetails", org.json.JSONArray(result.upstreamFailureDetails))
                    .put("diagnosticReasons", org.json.JSONArray(result.diagnostics.secondaryReasonCodes))
                    .put("buildCounts", org.json.JSONObject()
                        .put("control", result.buildCounts.controlBuilds)
                        .put("experimental", result.buildCounts.experimentalBuilds)
                        .put("total", result.buildCounts.totalBuildInvocations)
                        .put("third", result.buildCounts.thirdBuilds))
                    .put("programItemCount", result.program.items.size)
            }).toString(2))
        }
        // Keep the old C25 snapshot parse in a separate stack frame. The two full corpus JSON
        // documents are large; retaining both while the downstream audit objects are live made
        // the Hosted runner's single coverage test exceed its memory limit.
        assertC26RejectedQualityRowsAreNotMaterialized(records)
        val report = render(records)
        assertEquals(report, render(records.reversed()))
        assertEquals(renderProvenance(records), renderProvenance(records.reversed()))
        val path = java.io.File("build/reports/stimulus-production-coverage.txt")
        requireNotNull(path.parentFile).mkdirs()
        path.writeText(report)
        java.io.File("build/reports/c9-provenance-census.txt").writeText(renderProvenance(records))
        val c15Census = renderC15ColdStartStrengthCalibrationCensus(
            records, report,
            c14MergeMainHead = "b1c8aa06dcae0c25aa4f38a1d3a92298fcba23ec",
            c15StartHead = "b1c8aa06dcae0c25aa4f38a1d3a92298fcba23ec"
        )
        assertEquals(c15Census, renderC15ColdStartStrengthCalibrationCensus(
            records.reversed(), report,
            c14MergeMainHead = "b1c8aa06dcae0c25aa4f38a1d3a92298fcba23ec",
            c15StartHead = "b1c8aa06dcae0c25aa4f38a1d3a92298fcba23ec"
        ))
        val c15File = java.io.File("build/reports/c15-cold-start-strength-calibration-census.json")
        requireNotNull(c15File.parentFile).mkdirs()
        c15File.writeText(c15Census)
        assertC15CurrentCorpusContracts(c15Census)
        val c16Census = renderC16B8ResidualBlockerCensus(
            records,
            c15MergeMainHead = "caa3e8d07f52462ec009e31aad406fa0c7b18aea",
            c16StartHead = "caa3e8d07f52462ec009e31aad406fa0c7b18aea"
        )
        assertEquals(c16Census, renderC16B8ResidualBlockerCensus(
            records.reversed(),
            c15MergeMainHead = "caa3e8d07f52462ec009e31aad406fa0c7b18aea",
            c16StartHead = "caa3e8d07f52462ec009e31aad406fa0c7b18aea"
        ))
        java.io.File("build/reports/c16-b8-residual-blocker-census.json").writeText(c16Census)
        assertC16CurrentCorpusContracts(c16Census)
        val c17Census = C17PlacementCausalityCensus.render(
            records,
            productionContextByCase,
            c16Census,
            c16MergeSha = "128cdf2c359a5cc82a25984924175897586b7624",
            c17StartSha = "128cdf2c359a5cc82a25984924175897586b7624"
        )
        java.io.File("build/reports/c17-placement-causality-census.json").writeText(c17Census)
        assertEquals(c17Census, C17PlacementCausalityCensus.render(
            records.reversed(),
            productionContextByCase,
            c16Census,
            c16MergeSha = "128cdf2c359a5cc82a25984924175897586b7624",
            c17StartSha = "128cdf2c359a5cc82a25984924175897586b7624"
        ))
        val c17Summary = org.json.JSONObject(c17Census).getJSONObject("summary")
        // Exact Core B6 rows change the initial material population for persona4_mixed. The
        // resulting four placement changes are still counted as drift (not silently approved);
        // the two owner-role pairs each occur in both weeks and no rebalancer claims authority.
        assertEquals(4, c17Summary.getInt("placementDeltas"))
        assertEquals(2, c17Summary.getInt("caseOwnerRolePairs"))
        assertEquals(4, c17Summary.getInt("unnecessaryPlacementDrift"))
        assertEquals(0, c17Summary.getInt("unresolved"))
        assertEquals(0, c17Summary.getInt("necessaryAuthorizedDisplacementCandidates"))
        assertEquals(0, c17Summary.getInt("boundedDayRebalancerAcceptedEvents"))
        assertEquals(0, c17Summary.getInt("orderOnlyRows"))
        val c17PositiveReference = org.json.JSONObject(c17Census).getJSONObject("positiveReference")
        // persona2_reviewed's only canonical Strength history is 8-rep squat work. Under C32 it
        // is not Strength exposure, so this legacy calibration reference must remain absent.
        assertTrue(c17PositiveReference.isNull("calibrationOwner"))
        assertEquals(0, c17PositiveReference.getInt("authorizedCalibrationRows"))
        assertEquals(2, org.json.JSONObject(c17Census).getJSONObject("positiveReference")
            .getJSONObject("sharedOwnerPlacementMetrics").getInt("movedOwnerRows"))
        val c18Census = C18TissueIncumbentPlacementCensus.render(
            c17Census,
            c18TissueAuthoritySnapshot,
            c17MergeSha = "bb8dfca386b8fd296417185a9c105ac2b5e94b0f",
            c18StartSha = "bb8dfca386b8fd296417185a9c105ac2b5e94b0f"
        )
        assertTrue("C18 census retains gate evidence without duplicating full tissue-unit snapshots", c18Census.length < 2_000_000)
        assertEquals(c18Census, C18TissueIncumbentPlacementCensus.render(
            c17Census,
            c18TissueAuthoritySnapshot,
            c17MergeSha = "bb8dfca386b8fd296417185a9c105ac2b5e94b0f",
            c18StartSha = "bb8dfca386b8fd296417185a9c105ac2b5e94b0f"
        ))
        java.io.File("build/reports/c18-tissue-incumbent-placement-census.json").writeText(c18Census)
        val c19Census = C19CanonicalProgramLineageCensus.render(c18Census, records)
        assertEquals(c19Census, C19CanonicalProgramLineageCensus.render(c18Census, records.reversed()))
        val c19Summary = org.json.JSONObject(c19Census).getJSONObject("summary")
        // The current full-production corpus now contributes four exact incumbent rows:
        // two persona4_mixed owner-role pairs repeated across two weeks. They remain valid
        // prior placements and are retained by the shadow evaluator.
        assertEquals(4, c19Summary.getInt("placementRows"))
        assertEquals(4, c19Summary.getInt("PRESERVE_INCUMBENT"))
        assertEquals(0, c19Summary.getInt("INCUMBENT_REJECTED_HARD_CONSTRAINT"))
        assertEquals(0, c19Summary.getInt("NO_DECISION_UNRESOLVED"))
        assertFalse(c19Summary.getBoolean("actualProductionPlacementChanged"))
        val c19Routes = org.json.JSONObject(c19Census).getJSONObject("routeSnapshot")
        java.io.File("build/reports/c19-program-lineage-incumbent-placement-census.json").writeText(c19Census)
        val measuredC19RouteCount = records.mapNotNull { it.second }.size
        assertEquals(measuredC19RouteCount, sequenceOf(
            "CONTROL", "POWER_JUMP", "STRENGTH_V1", "STRENGTH_CALIBRATION_V1", "HYPERTROPHY", "COMBINED"
        ).sumOf(c19Routes::getInt))
        // B7 totals are remeasured by the C26 census below; these embedded C19 metrics
        // describe the current EXP result and are not frozen historical C19 baselines.
        java.io.File("build/reports/c19-program-lineage-incumbent-placement-census.json").writeText(c19Census)
        // C28 legitimately removes the unauthorized rows that generated live placement
        // deltas. Keep the C20 regression corpus tied to the frozen C18 placement fixture;
        // otherwise the test would silently stop evaluating incumbent feasibility.
        val c20IncumbentFixture = repositoryFile("docs/c18-tissue-incumbent-placement-census.json").readText()
        val c20Census = C20LiveIncumbentStabilityCensus.render(
            c18Census = c20IncumbentFixture,
            records = records,
            c19MergeSha = "46a166499dc2136c73d22e66670d6232d59c21b4",
            c20StartSha = "46a166499dc2136c73d22e66670d6232d59c21b4"
        )
        java.io.File("build/reports/c20-live-incumbent-stability-census.json").writeText(c20Census)
        val c37PowerJumpCensus = C37PowerJumpIntegratedAuthorityCensus.render(
            records = records,
            planningByCase = canonicalPlanningByCase,
            contextByCase = productionContextByCase,
            metadataRepository = canonicalMetadataRepository
        )
        assertEquals(c37PowerJumpCensus, C37PowerJumpIntegratedAuthorityCensus.render(
            records.reversed(), canonicalPlanningByCase, productionContextByCase, canonicalMetadataRepository
        ))
        java.io.File("build/reports/c37-power-jump-integrated-authority-census.json").writeText(c37PowerJumpCensus)
        val c37PowerSummary = C37PowerJumpIntegratedAuthorityCensus.renderCompactSummary(c37PowerJumpCensus)
        assertEquals(c37PowerSummary, C37PowerJumpIntegratedAuthorityCensus.renderCompactSummary(c37PowerJumpCensus))
        java.io.File("build/reports/c37-power-jump-integrated-authority-summary.json").writeText(c37PowerSummary)
        val c37Json = org.json.JSONObject(c37PowerJumpCensus)
        val c37Counts = c37Json.getJSONObject("counts")
        assertEquals("c37-power-jump-integrated-authority-census-v1", c37Json.getString("schema"))
        assertEquals(27, c37Counts.getInt("corpusCases"))
        assertEquals(22, c37Counts.getInt("generatedCases"))
        assertEquals(5, c37Counts.getInt("preflightRejected"))
        assertEquals(22, c37Counts.getInt("powerTargets"))
        assertEquals(4, c37Counts.getInt("directionOnlyBeforeC37B4"))
        assertEquals(3, c37Counts.getInt("numericPowerAuthorityAfter"))
        assertEquals(3, c37Counts.getInt("userApprovedPolicyAuthorityCases"))
        assertEquals(0, c37Counts.getInt("personalNumericAuthorityCases"))
        assertEquals(3, c37Counts.getInt("exactB6AuthorizedPowerCases"))
        assertEquals(0, c37Counts.getInt("reviewedStarterAuthority"))
        assertEquals(1, c37Counts.getInt("stillDirectionOnly"))
        assertEquals(3, c37Counts.getInt("fullyMaterializedPower"))
        assertEquals(1, c37Counts.getInt("b7EligiblePowerCases"))
        assertEquals(1, c37Counts.getInt("b8AuthorizedPowerCases"))
        assertEquals(1, c37Counts.getInt("powerRoutedCases"))
        assertEquals(8, c37Counts.getInt("preC21BaselineGeneratedPowerRows"))
        assertEquals(6, c37Counts.getInt("generatedPowerRowsAfterAuthorityFilter"))
        assertEquals(22, c37Counts.getInt("jumpLandingTargets"))
        assertEquals(0, c37Counts.getInt("jumpLandingNeedCases"))
        assertEquals(0, c37Counts.getInt("jumpLandingNumericB4Cases"))
        assertEquals(0, c37Counts.getInt("jumpLandingB5OwnerRows"))
        assertEquals(0, c37Counts.getInt("jumpLandingB6AuthorizedCases"))
        assertEquals(0, c37Counts.getInt("jumpLandingMaterializedCases"))
        assertEquals(0, c37Counts.getInt("jumpLandingMaterializedSetUnits"))
        assertEquals(0, c37Counts.getInt("jumpLandingB7EligibleCases"))
        assertEquals(0, c37Counts.getInt("jumpLandingB8AuthorizedCases"))
        assertEquals(0, c37Counts.getInt("jumpLandingRoutedCases"))
        assertEquals("EXACT_B8_B9_POWER_JUMP_ROUTE_WITH_OTHER_CASES_FAIL_CLOSED", c37Json.getString("policyConclusion"))
        val c37Persona3 = c37Json.getJSONObject("persona3Reviewed")
        assertEquals("USER_APPROVED_PROJECT_POLICY", c37Persona3.getJSONObject("powerTarget").getString("numericAuthority"))
        assertEquals("lateral_bound_continuous", c37Persona3.getJSONObject("selectedOwner").getString("stableKey"))
        assertEquals("CANONICAL_STIMULUS_QUALITY_POWER", c37Persona3.getJSONObject("selectedOwner").getString("selectionRole"))
        assertEquals(0, c37Persona3.getInt("exactOwnerPersonalHistoryCount"))
        assertEquals(2, c37Persona3.getInt("currentPowerRowsAfterAuthorityFilter"))
        assertEquals("AUTHORIZED_USER_APPROVED_POWER_JUMP_POLICY", c37Persona3.getString("currentB6Status"))
        val c37CaseRows = c37Json.getJSONArray("cases")
        fun c37Case(name: String) = (0 until c37CaseRows.length()).map { c37CaseRows.getJSONObject(it) }
            .single { it.getString("case") == name }
        listOf("persona3_mixed", "persona3_reviewed", "persona3_sparse").forEach { name ->
            val case = c37Case(name)
            assertEquals("USER_APPROVED_PROJECT_POLICY", case.getJSONObject("b4PowerTarget").getString("numericAuthority"))
            assertEquals(2, case.getJSONObject("b4PowerTarget").getJSONObject("weeklyDirectUnitsTarget").getInt("preferred"))
            assertEquals("lateral_bound_continuous", case.getJSONArray("b5SelectedPowerOwners").getJSONObject(0).getString("stableKey"))
            assertEquals("AUTHORIZED_USER_APPROVED_POWER_JUMP_POLICY",
                case.getJSONObject("b6PowerAuthorization").getString("status"))
            assertEquals("FULLY_MATERIALIZED", case.getJSONObject("b6PowerMaterialization").getString("state"))
            assertEquals(2, case.getJSONArray("generatedPowerRows").length())
            val weeklyRows = case.getJSONArray("generatedPowerRows")
            for (rowIndex in 0 until weeklyRows.length()) {
                val row = weeklyRows.getJSONObject(rowIndex)
                assertEquals("USER_APPROVED_POWER_JUMP_BODYWEIGHT_POLICY", row.getString("weightSource"))
                val sets = row.getJSONArray("sets")
                assertEquals(2, sets.length())
                for (setIndex in 0 until sets.length()) {
                    val set = sets.getJSONObject(setIndex)
                    assertEquals("NOT_APPLICABLE", set.getString("loadState"))
                    assertEquals(0.0, set.getDouble("weightKg"), 0.0)
                }
            }
            val compact = org.json.JSONObject(C37PowerJumpIntegratedAuthorityCensus.renderCompactSummary(c37PowerJumpCensus))
            val compactRows = compact.getJSONArray("cases").let { rows ->
                (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("case") == name }
            }.getJSONArray("generatedRows")
            assertEquals(2, compactRows.length())
            for (rowIndex in 0 until compactRows.length()) {
                val compactRow = compactRows.getJSONObject(rowIndex)
                assertEquals("NOT_APPLICABLE", compactRow.getJSONArray("loadStates").getString(0))
                assertEquals(0.0, compactRow.getJSONArray("weightKg").getDouble(0), 0.0)
                assertEquals("NOT_APPLICABLE", compactRow.getJSONArray("setPrescriptions").getJSONObject(0).getString("loadState"))
            }
            val jumpLanding = case.getJSONObject("jumpLanding")
            assertEquals("NO_EXTRA_NEED", jumpLanding.getJSONObject("b1Need").getString("decision"))
            assertEquals(0, jumpLanding.getJSONArray("b5Owners").length())
            assertEquals("IMPROVED", case.getJSONObject("powerTargetOutcome").getString("status"))
        }
        assertEquals("CONTROL", c37Case("persona3_reviewed").getString("route"))
        assertEquals("B8_POWER_JUMP_V1", c37Case("persona3_mixed").getString("route"))
        val persona3RecentPower = c37Case("persona3_recent")
        assertEquals("WORKLOAD_UNKNOWN", persona3RecentPower.getJSONObject("b4PowerDoseDecision").getString("status"))
        assertEquals("NO_EXECUTABLE_AUTHORIZATION", persona3RecentPower.getJSONObject("b6PowerAuthorization").getString("status"))
        assertTrue(persona3RecentPower.getJSONObject("b6PowerAuthorization").getJSONArray("reasonCodes").toString()
            .contains("EXACT_PLANNED_STRENGTH_HYPERTROPHY_WORKLOAD_REQUIRED"))
        assertEquals(0, persona3RecentPower.getJSONArray("generatedPowerRows").length())
        // This is the one bounded route: B8 consumes exact Power plus same-exercise C37
        // movement authorities. Reviewed/sparse variants remain fail-closed.
        assertEquals("ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW",
            c37Case("persona3_mixed").getJSONObject("b7").getString("status"))
        assertEquals("AUTHORIZED_FOR_BOUNDED_CUTOVER", c37Case("persona3_mixed").getJSONObject("b8").getString("status"))
        assertEquals("POWER_JUMP_V1", c37Case("persona3_mixed").getJSONObject("b8").getString("scope"))
        assertEquals(2, c37Case("persona3_mixed").getJSONObject("b8")
            .getJSONArray("authorizedMovementTargetOwnerIdentities").length())
        assertEquals("CONTROL", c37Case("persona3_reviewed").getString("route"))
        assertEquals("CONTROL", c37Case("persona3_sparse").getString("route"))
        val mixedResult = requireNotNull(records.single { it.first.label == "persona3_mixed" }.second)
        val mixedComparison = requireNotNull(mixedResult.comparison)
        val mixedB8 = requireNotNull(mixedComparison.productionCutoverAuthority)
        val missingMovementBinding = StimulusProductionRouter().route(
            mixedComparison,
            mixedB8.copy(authorizedMovementTargetOwnerIdentities = mixedB8.authorizedMovementTargetOwnerIdentities.drop(1)),
            StimulusProductionRoutingPolicy.defaultMode
        )
        assertEquals(StimulusProductionProgramSource.CONTROL, missingMovementBinding.decision.selectedSource)
        assertTrue(missingMovementBinding.decision.reasonCodes.contains("B9_B8_POWER_JUMP_OWNER_AUTHORITY_IDENTITY_MISMATCH"))
        val c37Routes = c37Json.getJSONObject("routeSnapshot")
        assertEquals(1, c37Routes.getInt("POWER_JUMP"))
        listOf("CONTROL", "POWER_JUMP", "STRENGTH_V1", "STRENGTH_CALIBRATION_V1", "HYPERTROPHY", "COMBINED")
            .forEach { route -> assertEquals(c19Routes.getInt(route), c37Routes.getInt(route)) }
        val c37B7 = c37Json.getJSONObject("b7ReasonOccurrences")
        assertTrue(c37B7.length() > 0)
        val c37C20 = c37Json.getJSONObject("c20LiveIncumbentFeasibility")
        assertEquals(10, c37C20.getInt("HARD_VALID"))
        assertEquals(0, c37C20.getInt("HARD_INVALID"))
        assertEquals(0, c37C20.getInt("UNRESOLVED"))
        assertEquals(4, c37C20.getInt("preservedHardValidRows"))
        assertEquals(0, c37C20.getInt("hardInvalidOrUnresolvedForcedPreserved"))
        // C24's historical handoff expects the C21-shaped count envelope. Feed it the live
        // C37 counts so downstream task auditing observes current Power material without
        // pretending C24 itself grants any Power authority.
        val c21PowerCensus = org.json.JSONObject().put("counts", org.json.JSONObject()
            .put("numericPowerAuthorityAfter", c37Counts.getInt("numericPowerAuthorityAfter"))
            .put("fullyMaterializedPower", c37Counts.getInt("fullyMaterializedPower")))
            .toString()
        val c22PreC22CMaterializedTaskRows = javaClass.classLoader!!
            .getResourceAsStream("c22-task-materialization-before-authority-filter.json")!!
            .bufferedReader().use { org.json.JSONArray(it.readText()) }
        val c22TaskCensus = C22BadmintonTaskAuthorityCensus.render(
            records = records,
            planningByCase = canonicalPlanningByCase,
            contextByCase = productionContextByCase,
            metadataRepository = canonicalMetadataRepository,
            preC22CMaterializedTaskRows = c22PreC22CMaterializedTaskRows
        )
        assertEquals(c22TaskCensus, C22BadmintonTaskAuthorityCensus.render(
            records.reversed(), canonicalPlanningByCase, productionContextByCase, canonicalMetadataRepository,
            c22PreC22CMaterializedTaskRows
        ))
        java.io.File("build/reports/c22-badminton-task-authority-census.json").writeText(c22TaskCensus)
        val c22 = org.json.JSONObject(c22TaskCensus)
        val c22Counts = c22.getJSONObject("counts")
        assertEquals(22, c22Counts.getInt("generatedCases"))
        assertEquals(132, c22Counts.getInt("taskTargetsTotal"))
        assertEquals(24, c22Counts.getInt("directionOnlyTaskTargets"))
        assertEquals(2, c22Counts.getInt("exactB5TaskOwnerRows"))
        assertEquals(2, c22Counts.getInt("uniqueTaskOwners"))
        assertEquals(5, c22Counts.getInt("directTaskRelations"))
        assertEquals(0, c22Counts.getInt("supportiveTaskRelations"))
        assertEquals(0, c22Counts.getInt("reviewedGuideMatches"))
        assertEquals(5, c22Counts.getInt("reviewedGuideMismatches"))
        assertEquals(0, c22Counts.getInt("personalTaskAuthorities"))
        assertEquals(0, c22Counts.getInt("reviewedStarterAuthorities"))
        assertEquals(0, c22Counts.getInt("fullyEncodedTaskAuthorities"))
        assertEquals(4, c22Counts.getInt("materializedTaskRowsBefore"))
        assertEquals(0, c22Counts.getInt("materializedTaskRowsAfter"))
        assertEquals(1, c22Counts.getInt("blockedPerSide"))
        assertEquals(4, c22Counts.getInt("blockedRange"))
        assertEquals(24, c22Counts.getInt("blockedFrequency"))
        assertEquals(5, c22Counts.getInt("blockedCategoryMismatch"))
        assertEquals(6, c22.getJSONArray("taskMatrix").length())
        assertEquals(4, c22.getJSONArray("materializedTaskRowsBefore").length())
        assertEquals(0, c22.getJSONArray("materializedTaskRowsAfter").length())
        val taskSelectionItems = records.mapNotNull { it.second?.comparison?.experimental }
            .flatMap { program -> program.items.filter { it.selectionRole.startsWith("CANONICAL_STIMULUS_TASK_") } }
        assertTrue(taskSelectionItems.isNotEmpty()) // C24 now materializes exact approved protocols.
        assertTrue(taskSelectionItems.all { it.taskProtocolSemanticsJson != null }) // No legacy fallback rows.
        listOf("CONTROL", "STRENGTH_V1", "STRENGTH_CALIBRATION_V1", "HYPERTROPHY", "COMBINED")
            .forEach { route -> assertEquals(c19Routes.getInt(route), c22.getJSONObject("routeSnapshot").getInt(route)) }
        // B7 is intentionally not frozen to the C22 snapshot: newly authorized C24
        // task evidence may change the canonical target outcome without changing B7 rules.
        val c22BuildProfile = c22.getJSONObject("buildAccounting").getJSONArray("generatedCaseProfiles")
        assertEquals(1, c22BuildProfile.length())
        assertEquals(22, c22BuildProfile.getJSONObject(0).getInt("cases"))
        assertEquals(1, c22BuildProfile.getJSONObject(0).getInt("CONTROL"))
        assertEquals(1, c22BuildProfile.getJSONObject(0).getInt("EXPERIMENTAL"))
        assertEquals(2, c22BuildProfile.getJSONObject(0).getInt("TOTAL"))
        assertEquals(0, c22BuildProfile.getJSONObject(0).getInt("THIRD"))
        val c22Persona3Recent = c22.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map(rows::getJSONObject).single { it.getString("case") == "persona3_recent" }
        }
        assertEquals(2, c22Persona3Recent.getJSONArray("selectedTaskOwners").length())
        assertEquals("CONTROL", c22Persona3Recent.getString("route"))
        // C22's four legacy rows are retained as before-state evidence below. Current
        // target outcomes are measured in the C24 census after exact B6 materialization.
        val c22LegacyRows = c22.getJSONArray("materializedTaskRowsBefore")
        assertTrue((0 until c22LegacyRows.length()).map(c22LegacyRows::getJSONObject)
            .all { it.getString("authority") == "NO_EXACT_TASK_B6; LEGACY_PERFORMANCE_FALLBACK" })
        assertTrue((0 until c22LegacyRows.length()).map(c22LegacyRows::getJSONObject)
            .all { it.getString("legacyResolverSource").startsWith("CANONICAL_PROGRAM_") })
        assertEquals(0, c22.getJSONObject("powerRegression").getInt("numericPowerAuthority"))
        assertEquals(0, c22.getJSONObject("powerRegression").getInt("powerB6Executable"))
        assertEquals(0, c22.getJSONObject("powerRegression").getInt("powerMaterialRows"))
        val c23TaskCensus = C23TaskProtocolFrequencyCensus.render(
            c22Census = c22TaskCensus,
            metadataRepository = canonicalMetadataRepository,
            c20Census = c20Census
        )
        assertEquals(c23TaskCensus, C23TaskProtocolFrequencyCensus.render(
            c22TaskCensus, canonicalMetadataRepository, c20Census
        ))
        java.io.File("build/reports/c23-task-protocol-frequency-representation-census.json").writeText(c23TaskCensus)
        val c23 = org.json.JSONObject(c23TaskCensus)
        val c23Counts = c23.getJSONObject("counts")
        assertEquals(27, c23Counts.getInt("corpusCases"))
        assertEquals(132, c23Counts.getInt("taskTargetsTotal"))
        assertEquals(24, c23Counts.getInt("directionOnlyTargets"))
        assertEquals(2, c23Counts.getInt("exactB5TaskOwnerRows"))
        assertEquals(2, c23Counts.getInt("uniqueTaskOwners"))
        assertEquals(5, c23Counts.getInt("exactB5OwnerTaskPairs"))
        assertEquals(5, c23Counts.getInt("directTaskRelations"))
        assertEquals(0, c23Counts.getInt("supportiveTaskRelations"))
        assertEquals(0, c23Counts.getInt("exactProtocolBindings"))
        assertEquals(132, c23Counts.getInt("missingProtocolBindings"))
        assertEquals(0, c23Counts.getInt("conflictingProtocolBindings"))
        assertEquals(0, c23Counts.getInt("personalFrequencyAuthorities"))
        assertEquals(0, c23Counts.getInt("reviewedFrequencyAuthorities"))
        assertEquals(132, c23Counts.getInt("missingFrequencyAuthorities"))
        assertEquals(0, c23Counts.getInt("existingB4NumericFrequencyTargets"))
        assertEquals(0, c23Counts.getInt("completeTaskAuthorities"))
        assertEquals(132, c23Counts.getInt("incompleteTaskAuthorities"))
        assertEquals(0, c23Counts.getInt("conflictingTaskAuthorities"))
        assertEquals(0, c23Counts.getInt("materializedTaskRows"))
        assertEquals(2, c23Counts.getInt("perSideRepresentable"))
        assertEquals(2, c23Counts.getInt("durationRangeRepresentable"))
        assertEquals(2, c23Counts.getInt("repRangeRepresentable"))
        val c23Mappings = c23.getJSONObject("taskProtocolBindings").getJSONArray("taskOutcomes")
        assertEquals(6, c23Mappings.length())
        assertTrue((0 until c23Mappings.length()).all {
            c23Mappings.getJSONObject(it).getString("reviewedProtocolOutcome") == "NO_APPROVED_PROTOCOL_BINDING"
        })
        assertEquals(0, c23.getJSONArray("taskMaterialRowsAfter").length())
        assertEquals(0, c23.getJSONObject("powerInvariant").getInt("numericAuthority"))
        assertEquals(0, c23.getJSONObject("powerInvariant").getInt("executableB6"))
        assertEquals(0, c23.getJSONObject("powerInvariant").getInt("materialRows"))
        assertEquals(10, c23.getJSONObject("c20IncumbentRegression").getInt("HARD_VALID"))
        assertEquals(0, c23.getJSONObject("c20IncumbentRegression").getInt("HARD_INVALID"))
        assertEquals(0, c23.getJSONObject("c20IncumbentRegression").getInt("UNRESOLVED"))
        assertFalse(c23.getJSONObject("productionChanges").getBoolean("taskB6Added"))
        assertFalse(c23.getJSONObject("productionChanges").getBoolean("badmintonRouteAdded"))
        val c24Census = C24BadmintonTaskB6Census.render(
            records = records,
            c23Census = c23TaskCensus,
            c21PowerCensus = c21PowerCensus,
            c20Census = c20Census,
            standardCoverageSha256 = org.json.JSONObject(c15Census).getString("standardCoverageSha256"),
            metadataRepository = canonicalMetadataRepository
        )
        assertEquals(c24Census, C24BadmintonTaskB6Census.render(
            records.reversed(), c23TaskCensus, c21PowerCensus, c20Census,
            org.json.JSONObject(c15Census).getString("standardCoverageSha256"), canonicalMetadataRepository
        ))
        java.io.File("build/reports/c24-badminton-task-b6-persistence-census.json").writeText(c24Census)
        val c24 = org.json.JSONObject(c24Census)
        val c24Counts = c24.getJSONObject("counts")
        assertEquals(27, c24Counts.getInt("corpusCases"))
        assertEquals(132, c24Counts.getInt("taskTargetsTotal"))
        assertEquals(2, c24Counts.getInt("exactApprovedBindingsUsed"))
        assertEquals(2, c24Counts.getInt("authorizedTaskB6Owners"))
        assertEquals(8, c24Counts.getInt("materializedTaskProtocolRows"))
        assertEquals(0, c24Counts.getInt("untypedTaskMaterialRows"))
        assertEquals(5, c24Counts.getInt("taskTargetsReceivingCredit"))
        assertEquals(0, c24Counts.getInt("weeklyFrequencyShortfalls"))
        assertEquals(0, c24Counts.getInt("placementShortfalls"))
        assertEquals(0, c24Counts.getInt("unapprovedTaskOwnersDeferred"))
        assertEquals(c37Counts.getInt("generatedPowerRowsAfterAuthorityFilter"), c24Counts.getInt("powerMaterialRows"))
        assertEquals(0, c24Counts.getInt("jumpLandingMaterialRows"))
        assertTrue(taskSelectionItems.all { item ->
            runCatching { TaskProtocolExposureMetadata.fromJsonString(checkNotNull(item.taskProtocolSemanticsJson)) }.isSuccess
        })
        assertEquals(10, c24.getJSONObject("c20IncumbentRegression").getInt("HARD_VALID"))
        assertEquals(0, c24.getJSONObject("c20IncumbentRegression").getInt("HARD_INVALID"))
        assertEquals(0, c24.getJSONObject("c20IncumbentRegression").getInt("UNRESOLVED"))
        assertEquals(0, c24.getJSONObject("c20IncumbentRegression").getInt("invalidOrUnresolvedForcedPreserved"))
        val c24Persona3Recent = c24.getJSONObject("persona3Recent")
        val c24RecentRows = c24Persona3Recent.getJSONArray("protocolRows")
        val sixCornerRows = (0 until c24RecentRows.length()).map { c24RecentRows.getJSONObject(it) }
            .filter { it.optString("protocolId") == "BADMINTON_SIX_CORNER_FOOTWORK_V1" }
        val lateralRows = (0 until c24RecentRows.length()).map { c24RecentRows.getJSONObject(it) }
            .filter { it.optString("protocolId") == "BADMINTON_LATERAL_SHUTTLE_LUNGE_V1" }
        assertTrue(sixCornerRows.isNotEmpty())
        assertTrue(sixCornerRows.all { it.getString("prescription") == "3 라운드 × 10–20초 · 휴식 60초" })
        assertTrue(lateralRows.isNotEmpty())
        assertTrue(lateralRows.all { it.getString("prescription") == "3 세트 × 5회/side · 휴식 75초" })
        assertFalse(sixCornerRows.any { it.getString("prescription").contains("4 라운드") || it.getString("prescription").contains("15초") })
        assertFalse(lateralRows.any { it.getString("prescription").contains("18초") })
        listOf(sixCornerRows, lateralRows).forEach { protocolRows ->
            protocolRows.groupBy { it.getInt("week") }.values.forEach { rows ->
                assertEquals(2, rows.map { it.getInt("day") }.distinct().size)
                assertEquals(setOf(1, 2), rows.map { it.getInt("exposureIndex") }.toSet())
            }
        }
        assertEquals(c37Counts.getInt("numericPowerAuthorityAfter"), c24.getJSONObject("powerInvariant").getInt("numericPowerAuthority"))
        assertEquals(c37Counts.getInt("fullyMaterializedPower"), c24.getJSONObject("powerInvariant").getInt("executableB6"))
        val persona3RecentServiceResult = requireNotNull(records.single { it.first.label == "persona3_recent" }.second)
        val persona3RecentComparison = requireNotNull(persona3RecentServiceResult.comparison)
        val taskReplacementExpectations = mapOf(
            "ex_33841b88" to "TASK:ACCELERATION",
            "ex_421ba24b" to "TASK:LUNGE_REACH"
        )
        taskReplacementExpectations.forEach { (stableKey, taskTarget) ->
            val oldRole = persona3RecentComparison.experimentalReadinessAudit!!.changeAttributions.single {
                it.stableKey == stableKey && it.selectionRole == "BADMINTON_OBJECTIVE_"
            }
            assertEquals(StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, oldRole.source)
            assertEquals(listOf(taskTarget), oldRole.targetIds)
            assertTrue("EXACT_TASK_B6_AUTHORIZATION" in oldRole.evidenceSources)
            assertTrue("DIRECT_CANONICAL_TASK_RELATION" in oldRole.evidenceSources)
            assertTrue("USER_APPROVED_PROJECT_POLICY" in oldRole.evidenceSources)
            assertTrue("LOSSLESS_TASK_MATERIALIZATION" in oldRole.evidenceSources)
            assertTrue("TASK_PROTOCOL_FREQUENCY_SATISFIED" in oldRole.evidenceSources)
        }
        fun assertTaskReplacementFailsClosed(
            stableKey: String,
            mutate: (com.training.trackplanner.data.ProgramSkeletonItem) -> com.training.trackplanner.data.ProgramSkeletonItem
        ) {
            val taskRole = when (stableKey) {
                "ex_33841b88" -> "CANONICAL_STIMULUS_TASK_ACCELERATION"
                else -> "CANONICAL_STIMULUS_TASK_LUNGE_REACH"
            }
            val tampered = persona3RecentComparison.copy(experimental = persona3RecentComparison.experimental.copy(
                items = persona3RecentComparison.experimental.items.map { row ->
                    if (row.exerciseStableKey == stableKey && row.selectionRole == taskRole) mutate(row) else row
                }
            ))
            val audit = StimulusExperimentalReadinessAuditEngine().audit(tampered)
            val oldRole = audit.changeAttributions.single {
                it.stableKey == stableKey && it.selectionRole == "BADMINTON_OBJECTIVE_"
            }
            assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, oldRole.source)
            assertEquals(listOf("UNEXPLAINED_REMOVED_IDENTITY"), oldRole.reasonCodes)
        }
        val sixCornerKey = "ex_33841b88"
        val sixCornerRole = "CANONICAL_STIMULUS_TASK_ACCELERATION"
        val sixCornerRow = persona3RecentComparison.experimental.items.first {
            it.exerciseStableKey == sixCornerKey && it.selectionRole == sixCornerRole
        }
        val sixCornerMetadata = requireNotNull(sixCornerRow.taskProtocolSemanticsJson)
        assertTaskReplacementFailsClosed(sixCornerKey) { it.copy(exerciseStableKey = "wrong_stable_key") }
        assertTaskReplacementFailsClosed(sixCornerKey) { it.copy(selectionRole = "CANONICAL_STIMULUS_TASK_REACTION") }
        assertTaskReplacementFailsClosed(sixCornerKey) {
            it.copy(taskProtocolSemanticsJson = sixCornerMetadata.replace(
                "BADMINTON_SIX_CORNER_FOOTWORK_V1", "BADMINTON_LATERAL_SHUTTLE_LUNGE_V1"))
        }
        assertTaskReplacementFailsClosed(sixCornerKey) {
            it.copy(taskProtocolSemanticsJson = sixCornerMetadata.replace(
                "USER_APPROVED_PROJECT_POLICY", "UNAPPROVED_POLICY"))
        }
        assertTaskReplacementFailsClosed(sixCornerKey) {
            val semantics = org.json.JSONObject(sixCornerMetadata)
            semantics.getJSONObject("transferEvidence").put("ACCELERATION", "SUPPORTIVE")
            it.copy(taskProtocolSemanticsJson = semantics.toString())
        }
        assertTaskReplacementFailsClosed(sixCornerKey) {
            val semantics = org.json.JSONObject(sixCornerMetadata)
            semantics.put("authorizedTasks", org.json.JSONArray(listOf("ACCELERATION")))
            it.copy(taskProtocolSemanticsJson = semantics.toString())
        }
        assertTaskReplacementFailsClosed(sixCornerKey) { it.copy(taskProtocolSemanticsJson = null) }
        assertTaskReplacementFailsClosed(sixCornerKey) { it.copy(seconds = it.seconds + 1) }
        val firstExposureDay = sixCornerRow.dayOfWeek
        val otherExposureDay = persona3RecentComparison.experimental.items.first { row ->
            row.exerciseStableKey == sixCornerKey && row.selectionRole == sixCornerRole && row.weekNumber == sixCornerRow.weekNumber &&
                row.dayOfWeek != firstExposureDay
        }.dayOfWeek
        assertTaskReplacementFailsClosed(sixCornerKey) {
            if (it.dayOfWeek == firstExposureDay) it.copy(dayOfWeek = otherExposureDay) else it
        }
        val remainingQualityReplacementRows = records.flatMap { (spec, result) ->
            val comparison = result?.comparison ?: return@flatMap emptyList()
            val canonicalQualityReplacementOwners = comparison.nonSelectionProvenance.asSequence()
                .filter { provenance -> provenance.targetEvidence.any {
                    it.classification == StimulusNonSelectionClassification.CANONICAL_REPLACEMENT &&
                        it.targetId.startsWith("QUALITY:")
                } }
                .mapNotNull { it.omittedControlOwner }
                .toSet()
            val unclosedByOwner = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
                .filter { it.reasonCodes.contains("UNEXPLAINED_REMOVED_IDENTITY") }
                .mapNotNull { attribution ->
                    val stableKey = attribution.stableKey ?: return@mapNotNull null
                    val role = attribution.selectionRole ?: return@mapNotNull null
                    StimulusPrescriptionOwnerIdentity(stableKey, role) to attribution
                }.toMap()
            canonicalQualityReplacementOwners.mapNotNull { owner ->
                unclosedByOwner[owner]?.let { spec.label to it }
            }
        }
        // C33's regional B4/B5/B6 path leaves B7 comparisons available in the same corpus;
        // all seven Quality replacement rows still lack exact executable B6 and stay unclosed.
        assertEquals("All seven Quality replacements without executable B6 remain closed", 7,
            remainingQualityReplacementRows.size)
        val frozenQualityReplacementRows = org.json.JSONObject(
            repositoryFile("docs/c26-b6-exp-b7-consistency-census.json").readText()
        ).getJSONObject("qualityReplacementRowsStillUnclosed")
        assertEquals(7, frozenQualityReplacementRows.getInt("count"))
        val frozenReplacementRows = frozenQualityReplacementRows.getJSONArray("rows")
        assertTrue((0 until frozenReplacementRows.length()).all { index ->
            val row = frozenReplacementRows.getJSONObject(index)
            !row.getBoolean("removalProvenanceClosed") && row.getJSONObject("b6Authorization").optBoolean("authorized").not()
        })
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            remainingQualityReplacementRows.first().second.source)
        val c25RoomBackedFixture = buildC25TaskOnlyComparison(persona3RecentServiceResult)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
            c25RoomBackedFixture.experimentalReadinessAudit?.status)
        assertEquals(StimulusProductionCutoverScope.BADMINTON_TASK_V1,
            StimulusProductionMaterialScopeResolver().resolve(c25RoomBackedFixture))
        val c25RoomBackedB8 = StimulusProductionCutoverAuthorityAuditEngine().audit(
            c25RoomBackedFixture, StimulusProductionCutoverScope.BADMINTON_TASK_V1
        )
        assertEquals("${c25RoomBackedB8.reasonCodes}",
            StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, c25RoomBackedB8.status)
        assertEquals(2, c25RoomBackedB8.authorizedTaskProtocolIdentities.size)
        assertEquals(setOf("BADMINTON_SIX_CORNER_FOOTWORK_V1", "BADMINTON_LATERAL_SHUTTLE_LUNGE_V1"),
            c25RoomBackedB8.authorizedTaskProtocolIdentities.map { it.protocolId }.toSet())
        assertEquals(8, c25RoomBackedFixture.experimental.items.count { !it.taskProtocolSemanticsJson.isNullOrBlank() })
        val c25RoomBackedRoute = StimulusProductionRouter().route(
            c25RoomBackedFixture, c25RoomBackedB8, StimulusProductionRoutingPolicy.defaultMode
        )
        assertTrue(c25RoomBackedRoute.program === c25RoomBackedFixture.experimental)
        assertEquals(StimulusProductionProgramSource.B8_BADMINTON_TASK_V1, c25RoomBackedRoute.decision.selectedSource)
        val c25RoomBackedRollback = StimulusProductionRouter().route(
            c25RoomBackedFixture, c25RoomBackedB8, StimulusProductionRoutingMode.CONTROL_ONLY
        )
        assertTrue(c25RoomBackedRollback.program === c25RoomBackedFixture.control)
        assertEquals(StimulusProductionProgramSource.CONTROL, c25RoomBackedRollback.decision.selectedSource)
        assertEquals(1, persona3RecentServiceResult.buildCounts.controlBuilds)
        assertEquals(1, persona3RecentServiceResult.buildCounts.experimentalBuilds)
        assertEquals(2, persona3RecentServiceResult.buildCounts.totalBuildInvocations)
        assertEquals(0, persona3RecentServiceResult.buildCounts.thirdBuilds)
        assertFalse(persona3RecentServiceResult.routeDecision.productionRoutingActive)

        val c25Census = renderC25TaskCutoverCensus(records, c24Census, c20Census, c25RoomBackedFixture,
            c25RoomBackedB8, c25RoomBackedRoute.decision, c25RoomBackedRollback.decision)
        assertEquals(c25Census, renderC25TaskCutoverCensus(records.reversed(), c24Census, c20Census,
            c25RoomBackedFixture, c25RoomBackedB8, c25RoomBackedRoute.decision, c25RoomBackedRollback.decision))
        java.io.File("build/reports/c25-bounded-badminton-task-b8-census.json").writeText(c25Census)
        val c25Json = org.json.JSONObject(c25Census)
        assertEquals(0, c25Json.getJSONObject("summary").getInt("authorizedTaskCases"))
        assertEquals(8, c25Json.getJSONObject("summary").getInt("persona3RecentTaskRows"))
        assertEquals("CONTROL", c25Json.getJSONObject("persona3Recent").getString("route"))
        assertEquals("AUTHORIZED_FOR_BOUNDED_CUTOVER", c25Json.getJSONObject("roomBackedPositiveFixture")
            .getString("b8Status"))

        val determinismSample = records.filter { it.second?.comparison != null }.take(2)
        val determinismSampleCensus = NextPhaseBottleneckCensus.render(determinismSample)
        assertEquals(determinismSampleCensus, NextPhaseBottleneckCensus.render(determinismSample.reversed()))
        val nextPhaseCensus = NextPhaseBottleneckCensus.render(records)
        java.io.File("build/reports/c26-current-next-phase-corpus.json").apply {
            parentFile?.mkdirs()
            writeText(nextPhaseCensus)
        }
        val nextPhaseJson = org.json.JSONObject(nextPhaseCensus)
        val orderedCaseNames = nextPhaseJson.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).getString("case") }
        }
        assertEquals(orderedCaseNames.sorted(), orderedCaseNames)
        val nextPhaseSummary = nextPhaseJson.getJSONObject("summary")
        // Sparse movement targets now enter the ordinary B4/B5/B6 path; keep corpus
        // accounting and safety contracts current instead of reasserting C28's old output.
        assertEquals(22, nextPhaseSummary.getInt("generatedCases"))
        assertEquals(21, nextPhaseSummary.getInt("controlCases"))
        assertEquals(1, nextPhaseSummary.getInt("powerJumpCases"))
        assertEquals(0, nextPhaseSummary.getInt("casesWithMixedStrengthAndTaskMaterialOnlyBlockers"))
        assertEquals(0, nextPhaseSummary.getJSONObject("unclosedAttributionReasonOccurrences")
            .optInt("UNEXPLAINED_ADDED_IDENTITY", 0))
        // C28-C32 historical census builders consume the live corpus and therefore no
        // longer represent their original phase snapshots after C33 adds authorized
        // residual materialization. Keep their immutable checked-in baselines as references;
        // assert current safety contracts here and emit a fresh C33 before/after census.
        assertEquals(22, nextPhaseSummary.getInt("generatedCases"))
        assertEquals(21, nextPhaseSummary.getInt("controlCases"))
        assertEquals(1, nextPhaseSummary.getInt("powerJumpCases"))
        assertEquals(0, nextPhaseSummary.getInt("qualityAddedOwnerWeeksWithoutAuthorizedB6"))
        assertEquals(0, nextPhaseSummary.getJSONObject("unclosedAttributionReasonOccurrences")
            .optInt("UNEXPLAINED_ADDED_IDENTITY", 0))
        assertTrue(records.filter { it.second?.comparison != null }.all { (_, result) ->
            result!!.buildCounts.controlBuilds == 1 && result.buildCounts.experimentalBuilds == 1 &&
                result.buildCounts.totalBuildInvocations == 2 && result.buildCounts.thirdBuilds == 0
        })
        assertC26RejectedQualityRowsAreNotMaterialized(records)

        val generatedByCase = records.mapNotNull { (spec, result) -> result?.let { spec.label to it } }.toMap()
        val c33RegionalCensus = C33RegionalB4ResidualProductionCensus.render(
            generatedByCase = generatedByCase,
            canonicalPlanningByCase = canonicalPlanningByCase,
            generationMillisByCase = c29GenerationDurationsByCase,
            startSha = "59e80b1a2179dfba37c7aa9cb99f07db6d13dc29"
        )
        assertEquals(c33RegionalCensus, C33RegionalB4ResidualProductionCensus.render(
            generatedByCase = generatedByCase,
            canonicalPlanningByCase = canonicalPlanningByCase,
            generationMillisByCase = c29GenerationDurationsByCase,
            startSha = "59e80b1a2179dfba37c7aa9cb99f07db6d13dc29"
        ))
        val c33RegionalJson = org.json.JSONObject(c33RegionalCensus)
        val c33RegionalSummary = c33RegionalJson.getJSONObject("summary")
        java.io.File("build/reports/c33-regional-b4-residual-production-census.json").apply {
            parentFile?.mkdirs()
            writeText(c33RegionalCensus)
        }
        val c35Census = C35HypertrophyCapacityCensus.render(
            generatedByCase = generatedByCase,
            generationMillisByCase = c29GenerationDurationsByCase,
            startSha = "fe7eed90f5a53fb656406507152c32782aa9e676"
        )
        assertEquals(c35Census, C35HypertrophyCapacityCensus.render(
            generatedByCase = generatedByCase.toSortedMap().toList().reversed().toMap(),
            generationMillisByCase = c29GenerationDurationsByCase,
            startSha = "fe7eed90f5a53fb656406507152c32782aa9e676"
        ))
        java.io.File("build/reports/c35-hypertrophy-capacity-census.json").apply {
            parentFile?.mkdirs()
            writeText(c35Census)
        }
        val c35Summary = org.json.JSONObject(c35Census).getJSONObject("summary")
        assertEquals(0, c35Summary.getInt("priorityInversionsAfter"))
        assertEquals(0, c35Summary.getInt("lowerPriorityFundedWhileHigherPriorityCapacityRejected"))
        assertTrue(c35Summary.getBoolean("equalPriorityOrderMatchesDeterministicCanonicalTieBreak"))
        assertEquals(1, c35Summary.getJSONObject("syntheticPriorityProbe").getInt("beforeInversions"))
        assertEquals(0, c35Summary.getJSONObject("syntheticPriorityProbe").getInt("afterInversions"))
        assertEquals(2, c35Summary.getInt("aggregateOnlyFalseRegressionCandidates"))
        assertEquals(0, c35Summary.getInt("aggregateHypertrophyRegressionCasesAfter"))
        assertEquals(0, c35Summary.getInt("regionalHypertrophyOverrunTargetsAfter"))
        assertEquals(698.0, c35Summary.getDouble("regionalRawResidualWeeklyUnits"), 0.0)
        assertEquals(581, c35Summary.getInt("regionalFundedUnits"))
        assertEquals(533, c35Summary.getInt("regionalMaterializedCompatibleUnits"))
        assertEquals(117, c35Summary.getInt("regionalUnfundedUnits"))
        assertEquals(48, c35Summary.getInt("regionalUnmaterializedShortfallUnits") - c35Summary.getInt("regionalUnfundedUnits"))
        assertEquals(0, c35Summary.getInt("unauthorizedMaterialRows"))
        assertEquals(0, c35Summary.getInt("duplicatePhysicalSetRows"))

        val c36Json = C36ExecutionProvenanceCensus.render(
            records = records,
            contexts = productionContextByCase,
            generationMillisByCase = c29GenerationDurationsByCase,
            c35Summary = c35Summary,
            startSha = "751cb2eff36e4c5969eedd634c0dfecbd13ff70d",
            implementationSha = "0c321c3d415981d9f0c4bc5e761f6ac36e2fe709"
        )
        val c36CaseNames = c36Json.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).getString("case") }
        }
        assertEquals(c36CaseNames.sorted(), c36CaseNames)
        val c36RemovalOrder = c36Json.getJSONArray("removedOwnerWeeks").let { rows ->
            (0 until rows.length()).map { row -> rows.getJSONObject(row).let {
                listOf(it.getString("case"), it.getJSONObject("removedOwner").getString("stableKey"),
                    it.getJSONObject("removedOwner").getString("selectionRole"), it.getInt("week").toString().padStart(2, '0'))
            }.joinToString("|") }
        }
        assertEquals(c36RemovalOrder.sorted(), c36RemovalOrder)
        java.io.File("build/reports/c36-execution-provenance-census.json").apply {
            parentFile?.mkdirs()
            writer(Charsets.UTF_8).use { C36ExecutionProvenanceCensus.writeCompactJson(c36Json, it) }
        }
        val c36Summary = c36Json.getJSONObject("summary")
        val c36AccountingComparison = c36Json.getJSONObject("beforeAfter")
        assertEquals(581, c36AccountingComparison.getJSONObject("c35ReportedAccounting").getInt("finiteFundedPerWeekUnits"))
        assertEquals(117, c36AccountingComparison.getJSONObject("c35ReportedAccounting").getInt("reportedUnfundedPerWeekUnits"))
        assertEquals(533, c36AccountingComparison.getJSONObject("c36StageAlignedAccounting").getInt("finalAuthorizedAndMaterializedPerWeekUnits"))
        assertEquals(165, c36AccountingComparison.getJSONObject("c36StageAlignedAccounting").getInt("b4ToFinalScheduleShortfallPerWeekUnits"))
        assertEquals(47, c36AccountingComparison.getJSONObject("c34ToC36RemovalAccounting").getInt("c34UnexplainedCaseOwnerPairs"))
        assertEquals(22, c36Summary.getInt("generatedCases"))
        assertEquals(0, c36Summary.getInt("regionalOverrunUnits"))
        assertEquals(0, c36Summary.getInt("duplicatePhysicalRows"))
        assertEquals(0, c36Summary.getInt("unauthorizedMaterialUnits"))
        assertEquals(21, c36Summary.getJSONObject("routes").getInt("CONTROL"))
        assertEquals(1, c36Summary.getJSONObject("routes").getInt("B8_POWER_JUMP_V1"))
        val squatDossier = c36Json.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("case") == "persona4_recent" }
        }.getJSONObject("persona4RecentSquatDossier")
        assertEquals(5, squatDossier.getJSONArray("controlRows").getJSONObject(0).getInt("sets"))
        assertEquals(5, squatDossier.getJSONArray("experimentalRows").getJSONObject(0).getInt("sets"))
        assertFalse(squatDossier.getBoolean("exactB5Selected"))
        assertEquals(0, squatDossier.getJSONArray("exactB6Authorities").length())
        assertEquals(28, squatDossier.getInt("frequencyComputedCapacityUnits"))
        assertEquals(1, squatDossier.getJSONArray("frequencyCapacityCandidates").let { rows ->
            (0 until rows.length()).count { rows.getJSONObject(it).optString("selectionRole") == "STYLE_HEAVY_LOWER_KNEE" &&
                rows.getJSONObject(it).optBoolean("continuity") && rows.getJSONObject(it).optInt("requestedUnits") == 5 &&
                rows.getJSONObject(it).optInt("fundedBaseUnits") == 5 && rows.getJSONObject(it).optString("rejectionReason") == "FUNDED" }
        })
        assertTrue("the set prescription is preserved; only the scheduled day changes",
            squatDossier.getJSONArray("experimentalRows").let { rows ->
                (0 until rows.length()).all { rows.getJSONObject(it).getInt("sets") == 5 }
            })
        val changedSquatWeeks = c36Json.getJSONArray("prescriptionChangeOwnerWeeks").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }
                .filter { it.getString("case") == "persona4_recent" &&
                    it.getJSONObject("owner").getString("stableKey") == "barbell_back_squat" &&
                    it.getJSONObject("owner").getString("selectionRole") == "STYLE_HEAVY_LOWER_KNEE" }
        }
        assertEquals(0, changedSquatWeeks.size)
        // The live corpus preserves persona4_recent's five squat sets. Exercise the exact
        // capacity-attribution guard on a real current reduction instead of the stale C36
        // persona4_recent 5→4 row: persona0_reviewed is 6→4 with exact finite-capacity proof.
        val capacityComparison = requireNotNull(records.single { it.first.label == "persona0_reviewed" }.second?.comparison)
        val capacityDecision = requireNotNull(capacityComparison.experimental.personalizedDecision)
        val capacityFrequency = requireNotNull(capacityDecision.frequencyDemand)
        fun capacitySquatAttributionAfter(tampered: StimulusSelectionProgramComparison) =
            StimulusExperimentalReadinessAuditEngine().audit(tampered).changeAttributions.single {
                it.stableKey == "barbell_back_squat" && it.selectionRole == "STYLE_HEAVY_LOWER_KNEE"
            }
        assertEquals(StimulusExperimentalChangeAttributionSource.FINITE_CAPACITY_CONTINUITY_ALLOCATION,
            capacitySquatAttributionAfter(capacityComparison).source)
        val wrongRoleFrequency = capacityFrequency.copy(candidates = capacityFrequency.candidates.map { candidate ->
            if (candidate.item.stableKey == "barbell_back_squat" && candidate.item.role == "STYLE_HEAVY_LOWER_KNEE")
                candidate.copy(item = candidate.item.copy(role = "UNRELATED_ROLE")) else candidate
        })
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            capacitySquatAttributionAfter(capacityComparison.copy(experimental = capacityComparison.experimental.copy(
                personalizedDecision = capacityDecision.copy(frequencyDemand = wrongRoleFrequency)
            ))).source)
        val nonBindingCapacity = capacityFrequency.copy(computedCapacity =
            capacityFrequency.computedCapacity.copy(finalControllableUnits = 37))
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            capacitySquatAttributionAfter(capacityComparison.copy(experimental = capacityComparison.experimental.copy(
                personalizedDecision = capacityDecision.copy(frequencyDemand = nonBindingCapacity)
            ))).source)
        val changedSquatRest = capacityComparison.experimental.items.map { row ->
            if (row.exerciseStableKey == "barbell_back_squat" && row.selectionRole == "STYLE_HEAVY_LOWER_KNEE")
                row.copy(restSeconds = row.restSeconds + 15) else row
        }
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            capacitySquatAttributionAfter(capacityComparison.copy(experimental = capacityComparison.experimental.copy(items = changedSquatRest))).source)
        assertEquals(18, c36Summary.getJSONObject("b7StatusesAndReasons").getInt("CHANGE_PROVENANCE_UNCLOSED"))
        assertEquals(21, c36Summary.getJSONObject("b8Statuses").getInt("CONTROL_REQUIRED"))
        assertEquals(1, c36Summary.getJSONObject("b8Statuses").getInt("AUTHORIZED_FOR_BOUNDED_CUTOVER"))
        assertEquals(21, c36Summary.getJSONObject("routes").getInt("CONTROL"))
        assertEquals(1, c36Summary.getJSONObject("routes").getInt("B8_POWER_JUMP_V1"))
        assertEquals(64, c36Summary.getInt("unexplainedRemovedOwnerWeekOccurrences"))
        assertEquals(4, c36Summary.getInt("prescriptionChangeOwnerWeeks"))
        assertEquals(0, c36Summary.getInt("unexplainedPrescriptionChangeOwnerWeeks"))
        assertEquals(4, c36Summary.getInt("finiteCapacityAttributedPrescriptionChangeOwnerWeeks"))
        assertEquals(32, c36Summary.getInt("uniqueUnexplainedCaseOwnerPairs"))
        assertEquals(32, c36Summary.getInt("coreDirectB7ClosedOwnerWeekOccurrences"))
        assertEquals(16, c36Summary.getInt("coreDirectB7ClosedCaseOwnerPairs"))
        assertEquals(2, c36Summary.getInt("coreDirectStillUnexplainedOwnerWeekOccurrences"))
        assertEquals(23, c36Summary.getInt("uniqueUnexplainedOwnerCasesWithB11CanonicalReplacement"))
        assertEquals(0, c36Summary.getInt("unexplainedOwnerCasesWithExactB4B5B6ScheduleMaterialization"))
        assertEquals(0, c36Summary.getInt("exactDisplacementOwnerWeekOccurrences"))
        assertEquals(533, c36Summary.getInt("regionalFrequencyFundedBaseUnitsPerTargetWeek"))
        assertEquals(533, c36Summary.getInt("regionalFirstWeekMaterializedUnits"))
        assertEquals(1066, c36Summary.getInt("regionalFinalAuthorizedWeeklyUnitsRepeatedAcrossHorizon"))
        assertEquals(1066, c36Summary.getInt("regionalMaterializedUnitsAcrossHorizon"))
        assertEquals(7, c36Summary.getInt("legacyDiagnosticFundedButCapacityRejectedRows"))
        assertEquals(56, c36Summary.getInt("legacyDiagnosticFundedButCapacityRejectedUnits"))
        assertEquals(1, c36Summary.getInt("legacyDiagnosticZeroButActuallyScheduledRows"))
        assertEquals(8, c36Summary.getInt("legacyDiagnosticZeroButActuallyScheduledUnits"))
        assertTrue("C36 requires complete owner/week removal evidence", c36Json
            .getJSONArray("removedOwnerWeeks").length() >= c36Summary.getInt("uniqueUnexplainedCaseOwnerPairs"))
        assertEquals(29, c33RegionalSummary.getInt("movementTargets"))
        assertEquals(58, c33RegionalSummary.getInt("ownerWeekRows"))
        assertEquals(22, c33RegionalSummary.getInt("c31BaselineMovementTargets"))
        assertEquals(44, c33RegionalSummary.getInt("c31BaselineOwnerWeekRows"))
        assertEquals(0, c33RegionalSummary.getJSONArray("c31BaselineContextsAbsentNow").length())
        assertEquals(7, c33RegionalSummary.getJSONArray("additionalCurrentTargetContexts").length())
        assertEquals(4, c33RegionalSummary.getInt("targetsWithoutRegionalDoseAuthority"))
        assertEquals(0, c33RegionalSummary.getInt("unauthorizedMaterialRows"))
        assertEquals(0, c33RegionalSummary.getInt("overfilledTargets"))
        assertEquals(0, c33RegionalSummary.getInt("duplicateCreditTargets"))
        assertEquals(3, c33RegionalSummary.getInt("capacityLimitedTargets"))
        assertEquals(245, c33RegionalSummary.getInt("candidateRows"))
        assertEquals(27, c33RegionalSummary.getInt("selectedCandidates"))
        assertEquals(218, c33RegionalSummary.getInt("legitimatelyRejectedCandidates"))
        assertEquals(20, c33RegionalSummary.getInt("fullySatisfiedTargets"))
        assertEquals(0, c33RegionalSummary.getInt("partiallySatisfiedTargets"))
        assertEquals(9, c33RegionalSummary.getInt("legitimatelyUnsatisfiedTargets"))
        assertEquals(18, c33RegionalSummary.getInt("userCalibrationRequiredTargets"))
        assertEquals(5, c33RegionalSummary.getInt("coreDirectTargets"))
        assertEquals(48, c33RegionalSummary.getInt("coreMaterializedSetRows"))
        assertEquals(348, c33RegionalSummary.getInt("authorizedMaterialUnits"))
        assertEquals(304, c33RegionalSummary.getInt("actualMaterialUnits"))
        val c33Targets = c33RegionalJson.getJSONArray("targets")
        val c33CoreTargets = (0 until c33Targets.length()).map { c33Targets.getJSONObject(it) }
            .filter { it.getString("movementCoverage") == "CORE_DIRECT" }
        assertEquals(5, c33CoreTargets.size)
        assertTrue(c33CoreTargets.all {
            it.getInt("authorizedWholeSetUnits") == 6 && it.getBoolean("authorized") &&
                it.getInt("b6AuthorizationRowCount") == 1 && it.getString("b6Status") in setOf(
                    "AUTHORIZED_EXISTING_COMPATIBLE", "AUTHORIZED_COLD_START_USER_CALIBRATION"
                )
        })
        val fullyMaterializedCore = c33CoreTargets.filter { it.getString("materializationState") == "FULLY_MATERIALIZED" }
        assertEquals(4, fullyMaterializedCore.size)
        assertTrue(fullyMaterializedCore.all { target ->
            val rows = target.getJSONArray("exactOwnerExperimentalRows")
            rows.length() == 2 && (0 until rows.length()).all { index ->
                val row = rows.getJSONObject(index)
                row.getInt("sets") == 6 && row.getInt("reps") == 8 && row.getInt("seconds") == 0 &&
                    row.getString("loadState") == "NOT_APPLICABLE"
            }
        })
        val capacityLimitedCore = c33CoreTargets.single { it.getString("materializationDisposition") == "UNMATERIALIZED_FINITE_CAPACITY_LIMIT" }
        assertTrue(capacityLimitedCore.getJSONArray("finiteCapacityDisposition").length() > 0)
        assertEquals(0, capacityLimitedCore.getInt("materializedTotalUnits"))
        assertEquals(6, capacityLimitedCore.getInt("remainingShortfallPerWeek"))
        (0 until c33Targets.length()).map { c33Targets.getJSONObject(it) }
            .filter { it.getBoolean("authorized") && it.getInt("authorizedWeeklyUnits") > 0 &&
                it.getString("materializationState") != "FULLY_MATERIALIZED" }
            .forEach { target ->
                assertEquals(target.getString("movementTargetId"), "UNMATERIALIZED_FINITE_CAPACITY_LIMIT",
                    target.getString("materializationDisposition"))
                assertTrue(target.getJSONArray("finiteCapacityDisposition").length() > 0)
                assertTrue(target.getJSONArray("materializationReasonCodes").length() > 0)
            }
        assertEquals(0, nextPhaseSummary.getInt("qualityAddedOwnerWeeksWithoutAuthorizedB6"))
        assertEquals(0, nextPhaseSummary.getJSONArray("qualityAddedOwnerWeeksWithoutAuthorizedB6Cases").length())
        assertEquals(23, nextPhaseSummary.getInt("b11CanonicalReplacementButB7UnclosedOwnerRows"))
        assertEquals(16, nextPhaseSummary.getInt("b11CanonicalReplacementButB7UnclosedMovementOwnerRows"))
        assertEquals(7, nextPhaseSummary.getInt("b11CanonicalReplacementButB7UnclosedQualityOwnerRows"))
        assertEquals(0, nextPhaseSummary.getInt("b11CanonicalReplacementButB7UnclosedTaskOwnerRows"))
        assertEquals(0, nextPhaseSummary.getJSONArray("taskRoleReplacementRowsWithExactApprovedTaskB6").length())
        assertEquals(32, nextPhaseSummary.getInt("unexplainedRemovedIdentityAttributions"))
        assertEquals(41, nextPhaseSummary.getInt("exactMovementCanonicalReplacementAttributions"))
        val movementReplacementResult = requireNotNull(records.single { it.first.label == "persona1_mixed" }.second)
        val movementReplacementComparison = requireNotNull(movementReplacementResult.comparison)
        val movementReplacementOldOwner = StimulusPrescriptionOwnerIdentity("barbell_reverse_curl", "COVERAGE_ARMS_BICEPS")
        val movementReplacementNewOwner = StimulusPrescriptionOwnerIdentity(
            "barbell_reverse_curl", "CANONICAL_STIMULUS_MOVEMENT_ARMS_BICEPS"
        )
        val movementReplacement = movementReplacementComparison.experimentalReadinessAudit!!.changeAttributions.single {
            it.stableKey == movementReplacementOldOwner.stableKey &&
                it.selectionRole == movementReplacementOldOwner.selectionRole
        }
        assertEquals(StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, movementReplacement.source)
        assertEquals(listOf("MOVEMENT:ARMS_BICEPS"), movementReplacement.targetIds)
        assertTrue("B6_AUTHORIZED_REGIONAL_MOVEMENT_REPLACEMENT" in movementReplacement.reasonCodes)
        assertTrue("EXACT_B4_MOVEMENT_TARGET" in movementReplacement.evidenceSources)
        assertTrue("EXACT_B5_PRIMARY_MOVEMENT_OWNER" in movementReplacement.evidenceSources)
        assertTrue("EXACT_B6_REGIONAL_HYPERTROPHY_AUTHORITY" in movementReplacement.evidenceSources)
        assertTrue("EXACT_WEEKLY_MATERIALIZATION_MATCH" in movementReplacement.evidenceSources)
        val exactMovementSelection = movementReplacementComparison.selectionPlan.selectedCandidates.single {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == movementReplacementNewOwner
        }
        assertEquals("MOVEMENT:ARMS_BICEPS", exactMovementSelection.primaryTargetId)
        val exactMovementB6 = movementReplacementComparison.prescriptionAuthorizationPlan!!.authorizations.single {
            it.targetId == "MOVEMENT:ARMS_BICEPS" &&
                it.owner?.let { owner -> StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) } == movementReplacementNewOwner
        }
        assertNotNull(exactMovementB6.authorizedPrescription)
        val exactMovementMaterialization = movementReplacementComparison.materializationTraces.single {
            it.targetId == "MOVEMENT:ARMS_BICEPS" && it.selectedStableKey == movementReplacementNewOwner.stableKey &&
                it.selectionRole == movementReplacementNewOwner.selectionRole
        }
        assertTrue(exactMovementMaterialization.directIdentityVerifiedAtSelection == true)
        assertTrue(exactMovementMaterialization.presentInFinalExperimentalSkeleton)
        fun assertMovementReplacementFailsClosed(tampered: StimulusSelectionProgramComparison) {
            val attribution = StimulusExperimentalReadinessAuditEngine().audit(tampered).changeAttributions.single {
                it.stableKey == movementReplacementOldOwner.stableKey &&
                    it.selectionRole == movementReplacementOldOwner.selectionRole
            }
            assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, attribution.source)
            assertEquals(listOf("UNEXPLAINED_REMOVED_IDENTITY"), attribution.reasonCodes)
        }
        val b6WithoutMovementAuthority = movementReplacementComparison.copy(
            prescriptionAuthorizationPlan = movementReplacementComparison.prescriptionAuthorizationPlan!!.copy(
                authorizations = movementReplacementComparison.prescriptionAuthorizationPlan!!.authorizations.filterNot {
                    it.targetId == "MOVEMENT:ARMS_BICEPS" &&
                        it.owner?.let { owner -> StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) } == movementReplacementNewOwner
                }
            )
        )
        assertMovementReplacementFailsClosed(b6WithoutMovementAuthority)
        assertMovementReplacementFailsClosed(movementReplacementComparison.copy(
            materializationTraces = movementReplacementComparison.materializationTraces.filterNot {
                it.targetId == "MOVEMENT:ARMS_BICEPS" && it.selectedStableKey == movementReplacementNewOwner.stableKey &&
                    it.selectionRole == movementReplacementNewOwner.selectionRole
            }
        ))
        val unrelatedRoleOmission = movementReplacementComparison.nonSelectionProvenance.map { omission ->
            if (omission.omittedControlOwner != movementReplacementOldOwner) omission else omission.copy(
                targetEvidence = omission.targetEvidence.map { targetEvidence ->
                    if (targetEvidence.targetId != "MOVEMENT:ARMS_BICEPS") targetEvidence else targetEvidence.copy(
                        disposition = targetEvidence.disposition.copy(
                            canonicalSelectionRole = "CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL"
                        )
                    )
                }
            )
        }
        assertMovementReplacementFailsClosed(movementReplacementComparison.copy(nonSelectionProvenance = unrelatedRoleOmission))
        val persona3RecentCensus = nextPhaseJson.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("case") == "persona3_recent" }
        }
        assertEquals("CONTROL", persona3RecentCensus.getString("route"))
        assertEquals(8, persona3RecentCensus.getJSONArray("taskMaterialRows").length())
        assertEquals(0, persona3RecentCensus.getJSONObject("b7").getJSONArray("affectedUnmetTargets").length())
        // POWER remains a need, but with planned resistance workload unknown B4 correctly
        // withholds its executable numeric target; only the independent C24 task is unmet.
        assertEquals(setOf("TASK:JUMP_LANDING"),
            persona3RecentCensus.getJSONObject("b7").getJSONArray("allUnmetTargetOutcomes")
                .let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() })
        val strengthTargetOutcome = persona3RecentCensus.getJSONObject("b7").getJSONArray("targetOutcomes")
            .let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) }
                .single { it.getString("target") == "QUALITY:STRENGTH" } }
        assertEquals("NOT_APPLICABLE", strengthTargetOutcome.getString("status"))
        assertTrue(strengthTargetOutcome.getJSONArray("reasonCodes").let { reasons ->
            (0 until reasons.length()).any { reasons.getString(it) == "TARGET_NUMERIC_EVIDENCE_UNRESOLVED" }
        })
        assertEquals(listOf("B8_B7_NOT_ELIGIBLE"), persona3RecentCensus.getJSONObject("b8")
            .getJSONArray("reasons").let { rows -> (0 until rows.length()).map { rows.getString(it) } })
        assertFalse(persona3RecentCensus.getJSONArray("materialDeltas").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.any { delta ->
                delta.getString("kind") == "ADDED_OWNER" &&
                    delta.getJSONObject("owner").getString("stableKey") == "barbell_back_squat" &&
                    delta.getJSONObject("owner").getString("selectionRole") == "CANONICAL_STIMULUS_QUALITY_STRENGTH"
            }
        })
        assertTrue(persona3RecentCensus.getJSONArray("b6QualityAuthorities").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.any { authority ->
                authority.getString("target") == "QUALITY:STRENGTH" &&
                    authority.getString("status") == "NO_EXECUTABLE_AUTHORIZATION" &&
                    authority.getJSONArray("reasonCodes").let { reasons ->
                        (0 until reasons.length()).any { reasons.getString(it) == "CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE" }
                    }
            }
        })
        val regressionCase = nextPhaseJson.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("case") == "persona1_mixed" }
        }
        assertEquals(emptyList<String>(), regressionCase.getJSONObject("b7")
            .getJSONArray("regressedTargets").let { rows -> (0 until rows.length()).map { rows.getString(it) } })
        assertTrue(regressionCase.getJSONObject("b7").getBoolean("collateralRegressionFree"))
        // Both historical aggregate-H regressions were caused by comparing regional B4 dose
        // against a whole-quality observed envelope. Regional target outcomes remain separately
        // checked; these cases now stay CONTROL only because their owner-removal provenance is open.
        val reviewedRegressionCase = nextPhaseJson.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("case") == "persona1_reviewed" }
        }
        assertEquals("CONTROL", reviewedRegressionCase.getString("route"))
        assertTrue(reviewedRegressionCase.getJSONObject("b7").getJSONArray("reasons").toString()
            .contains("CHANGE_PROVENANCE_UNCLOSED"))
        assertEquals(emptyList<String>(), reviewedRegressionCase.getJSONObject("b7")
            .getJSONArray("regressedTargets").let { rows -> (0 until rows.length()).map { rows.getString(it) } })
        val bottleneckReport = java.io.File("build/reports/next-phase-bottleneck-census.json")
        requireNotNull(bottleneckReport.parentFile).mkdirs()
        bottleneckReport.writeText(nextPhaseCensus)

        val c24Routes = c24.getJSONObject("routeSnapshot")
        assertEquals(27, c24Routes.keys().asSequence().map { c24Routes.getInt(it) }.sum())
        assertEquals(c19Routes.getInt("CONTROL"), c24Routes.getInt("CONTROL"))
        assertEquals(c19Routes.optInt("STRENGTH_V1", 0), c24Routes.optInt("B8_STRENGTH_V1", 0))
        assertEquals(c19Routes.optInt("STRENGTH_CALIBRATION_V1", 0), c24Routes.optInt("B8_STRENGTH_CALIBRATION_V1", 0))
        assertFalse(c24Routes.has("B8_BADMINTON_TASK_V1"))
        assertTrue(c24.getJSONObject("b7Summary").length() > 0)
        // Historical aggregate-H overage is not a collateral loss of regional target coverage.
        assertEquals(0, c24.getJSONObject("b7Summary").getInt("collateralRegressionCases"))
        val perturbedComparator = org.json.JSONObject(c22TaskCensus)
        val perturbedCases = perturbedComparator.getJSONArray("cases")
        for (index in 0 until perturbedCases.length()) {
            perturbedCases.getJSONObject(index).put("controlTaskRows", org.json.JSONArray(listOf(
                org.json.JSONObject().put("stableKey", "control-only-owner-$index")
                    .put("selectionRole", "CONTROL_ONLY").put("reps", index + 3).put("day", index % 7 + 1)
            )))
        }
        val c23AfterControlPerturbation = org.json.JSONObject(C23TaskProtocolFrequencyCensus.render(
            perturbedComparator.toString(), canonicalMetadataRepository, c20Census
        ))
        assertEquals(c23.getJSONObject("taskProtocolBindings").toString(),
            c23AfterControlPerturbation.getJSONObject("taskProtocolBindings").toString())
        assertEquals(c23.getJSONObject("taskFrequencyAuthorities").toString(),
            c23AfterControlPerturbation.getJSONObject("taskFrequencyAuthorities").toString())
        assertEquals(c23.getJSONObject("taskPrescriptionRepresentations").toString(),
            c23AfterControlPerturbation.getJSONObject("taskPrescriptionRepresentations").toString())
        assertEquals(c23.getJSONArray("taskAuthorityCompleteness").toString(),
            c23AfterControlPerturbation.getJSONArray("taskAuthorityCompleteness").toString())
        val c24AfterControlPerturbation = org.json.JSONObject(C24BadmintonTaskB6Census.render(
            records = records,
            c23Census = c23AfterControlPerturbation.toString(),
            c21PowerCensus = c21PowerCensus,
            c20Census = c20Census,
            standardCoverageSha256 = org.json.JSONObject(c15Census).getString("standardCoverageSha256"),
            metadataRepository = canonicalMetadataRepository
        ))
        assertEquals(c24.getJSONArray("approvedProtocols").toString(),
            c24AfterControlPerturbation.getJSONArray("approvedProtocols").toString())
        assertEquals(c24.getJSONObject("routeSnapshot").toString(),
            c24AfterControlPerturbation.getJSONObject("routeSnapshot").toString())
        val c24ProtocolRows = c24.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.map { it.optJSONArray("protocolRows")?.toString().orEmpty() }
        }
        val perturbedC24ProtocolRows = c24AfterControlPerturbation.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.map { it.optJSONArray("protocolRows")?.toString().orEmpty() }
        }
        assertEquals(c24ProtocolRows, perturbedC24ProtocolRows)
        assertEquals(c20Census, C20LiveIncumbentStabilityCensus.render(
            c18Census = c20IncumbentFixture,
            records = records.reversed(),
            c19MergeSha = "46a166499dc2136c73d22e66670d6232d59c21b4",
            c20StartSha = "46a166499dc2136c73d22e66670d6232d59c21b4"
        ))
        val c20Summary = org.json.JSONObject(c20Census).getJSONObject("summary")
        assertEquals(32, c20Summary.getInt("placementRows"))
        assertEquals(10, c20Summary.getInt("HARD_VALID"))
        assertEquals(0, c20Summary.getInt("HARD_INVALID"))
        assertEquals(0, c20Summary.getInt("UNRESOLVED"))
        assertEquals(22, c20Summary.getInt("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER"))
        assertEquals(8, c20Summary.getInt("preservedHardValidRows"))
        assertEquals(0, c20Summary.getInt("hardInvalidRowsForcedPreserved"))
        assertEquals(0, c20Summary.getInt("unresolvedRowsForcedPreserved"))
        assertEquals(0, c20Summary.getInt("combinedAnchorSetConflicts"))
        val c20PlacementMetrics = org.json.JSONObject(c20Census).getJSONObject("shadowPlacement")
        assertEquals(6, c20PlacementMetrics.getInt("sharedPlacementDeltaBefore"))
        assertEquals(2, c20PlacementMetrics.getInt("sharedPlacementDeltaAfter"))
        assertEquals(12, c20PlacementMetrics.getInt("totalDayDistanceBefore"))
        assertEquals(4, c20PlacementMetrics.getInt("totalDayDistanceAfter"))
        val c20Routes = org.json.JSONObject(c20Census).getJSONObject("routeSnapshot")
        assertEquals(c19Routes.optInt("CONTROL", 0), c20Routes.optInt("CONTROL", 0))
        assertEquals(c19Routes.optInt("STRENGTH_V1", 0), c20Routes.optInt("B8_STRENGTH_V1", 0))
        assertEquals(c19Routes.optInt("STRENGTH_CALIBRATION_V1", 0), c20Routes.optInt("B8_STRENGTH_CALIBRATION_V1", 0))
        assertEquals(c19Routes.optInt("HYPERTROPHY", 0), c20Routes.optInt("B8_HYPERTROPHY_V1", 0))
        assertEquals(c19Routes.optInt("COMBINED", 0), c20Routes.optInt("B8_STRENGTH_HYPERTROPHY_V1", 0))
        // Current B7 totals are frozen once in the C26 census, not in this historical C20 view.
        val c20CaseRows = org.json.JSONObject(c20Census).getJSONArray("caseRows")
        fun c20Case(name: String) = (0 until c20CaseRows.length()).map { c20CaseRows.getJSONObject(it) }
            .single { it.getString("case") == name }
        assertEquals(4, c20Case("persona0_mixed").getJSONObject("liveFeasibility").getInt("HARD_VALID"))
        assertEquals(0, c20Case("persona0_mixed").getJSONObject("liveFeasibility").getInt("HARD_INVALID"))
        assertEquals(0, c20Case("persona0_mixed").getJSONObject("liveFeasibility").getInt("UNRESOLVED"))
        assertEquals(2, c20Case("persona0_mixed").getJSONObject("liveFeasibility").getInt("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER"))
        assertEquals(0, c20Case("persona0_reviewed").getJSONObject("liveFeasibility").getInt("HARD_VALID"))
        assertEquals(0, c20Case("persona0_reviewed").getJSONObject("liveFeasibility").getInt("HARD_INVALID"))
        assertEquals(0, c20Case("persona0_reviewed").getJSONObject("liveFeasibility").getInt("UNRESOLVED"))
        assertEquals(4, c20Case("persona0_reviewed").getJSONObject("liveFeasibility").getInt("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER"))
        assertEquals(2, c20Case("persona3_reviewed").getJSONObject("liveFeasibility").getInt("HARD_VALID"))
        assertEquals(0, c20Case("persona3_reviewed").getJSONObject("liveFeasibility").getInt("HARD_INVALID"))
        assertEquals(0, c20Case("persona3_reviewed").getJSONObject("liveFeasibility").getInt("UNRESOLVED"))
        assertEquals(6, c20Case("persona3_reviewed").getJSONObject("liveFeasibility").getInt("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER"))
        assertEquals(4, c20Case("persona4_mixed").getJSONObject("liveFeasibility").getInt("HARD_VALID"))
        assertEquals(0, c20Case("persona4_mixed").getJSONObject("liveFeasibility").getInt("HARD_INVALID"))
        assertEquals(0, c20Case("persona4_mixed").getJSONObject("liveFeasibility").getInt("UNRESOLVED"))
        assertEquals(10, c20Case("persona4_mixed").getJSONObject("liveFeasibility").getInt("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER"))
        assertEquals("ACTIVATED", c20Case("persona0_mixed").getString("activationStatus"))
        assertEquals(0, c20Case("persona0_mixed").getJSONArray("preservationEvents").length())
        assertFalse(c20Case("persona0_mixed").getBoolean("productionPlacementChanged"))
        assertEquals("NO_ELIGIBLE_HARD_VALID_ANCHORS", c20Case("persona0_reviewed").getString("activationStatus"))
        assertFalse(c20Case("persona0_reviewed").getBoolean("productionPlacementChanged"))
        assertEquals("NON_ANCHOR_PLACEMENT_WOULD_CHANGE", c20Case("persona3_reviewed").getString("activationStatus"))
        assertEquals(0, c20Case("persona3_reviewed").getJSONArray("preservationEvents").length())
        assertFalse(c20Case("persona3_reviewed").getBoolean("productionPlacementChanged"))
        assertEquals("ACTIVATED", c20Case("persona4_mixed").getString("activationStatus"))
        assertEquals(4, c20Case("persona4_mixed").getJSONArray("preservationEvents").length())
        assertTrue(c20Case("persona4_mixed").getBoolean("productionPlacementChanged"))
        val c20PositiveReference = org.json.JSONObject(c20Census).getJSONObject("positiveReference")
        assertEquals("persona2_reviewed", c20PositiveReference.getString("case"))
        // Under C32, the reviewed squat evidence in this case is outside the approved 1..6
        // exposure band, so it no longer creates calibration rows. Its removed owners remain
        // unclosed in B7, so the intact EXP skeleton is correctly not selected.
        assertEquals("CONTROL", c20PositiveReference.getString("route"))
        assertEquals("SOURCE_UNAVAILABLE", c20PositiveReference.getString("activationStatus"))
        assertEquals(2, c20PositiveReference.getInt("sharedOwnerRows"))
        assertEquals(0, c20PositiveReference.getInt("sharedOwnerRowsPlacementUnchanged"))
        assertEquals(0, c20PositiveReference.getInt("preservationEvents"))
        assertEquals(8, c20PositiveReference.getInt("calibrationRows"))
        val c18Json = org.json.JSONObject(c18Census)
        val c18Summary = c18Json.getJSONObject("summary")
        assertEquals(4, c18Summary.getInt("placementDeltas"))
        assertEquals(2, c18Summary.getInt("uniqueCaseOwnerRolePairs"))
        // Current integrated output contributes two owner-role pairs across two weeks.
        // They remain placement-only with no inferred displacement authority or unresolved inputs.
        assertEquals(4, c18Summary.getInt("priorPlacementValid"))
        assertEquals(0, c18Summary.getInt("priorPlacementHardInvalid"))
        assertEquals(0, c18Summary.getInt("stillUnresolved"))
        assertEquals(0, c18Summary.getInt("actualDisplacementAuthorityProven"))
        assertEquals(4, c18Summary.getInt("syntheticIncumbentShadowKeep"))
        assertEquals(0, c18Summary.getInt("syntheticIncumbentShadowMove"))
        assertEquals(0, c18Summary.getInt("syntheticIncumbentShadowNoDecision"))
        assertEquals(0, c18Summary.getInt("currentCorpusInputUnresolvedTissueKeys"))
        assertEquals(5, c18Summary.getInt("resolvedWithRequiredInput"))
        assertEquals(1, c18Summary.getInt("exactBodyweightAdapterRepairs"))
        val c18TissueKeys = c18Json.getJSONArray("tissueKeys")
        assertEquals(5, c18TissueKeys.length())
        (0 until c18TissueKeys.length()).map(c18TissueKeys::getJSONObject).forEach { row ->
            assertTrue(row.getString("stableKey"), row.getBoolean("canonicalMetadataRowExists"))
            assertTrue(row.getString("stableKey"), row.getBoolean("planningMetadataExists"))
            assertTrue(row.getString("stableKey"), row.getBoolean("runtimeJoinExists"))
            assertFalse(row.getString("stableKey"), row.getBoolean("safeToTreatAsZeroLoad"))
            assertTrue(row.getString("stableKey"), row.getString("projectionWithRequiredInput").startsWith("RESOLVED_"))
        }
        val c17Cases = org.json.JSONObject(c17Census).getJSONArray("cases")
        (0 until c17Cases.length()).map(c17Cases::getJSONObject).flatMap { case ->
            val deltas = case.getJSONArray("deltas")
            (0 until deltas.length()).map(deltas::getJSONObject)
        }
            .forEach { delta ->
                assertTrue(delta.getBoolean("materialParity"))
                assertFalse(delta.getBoolean("b5Selected"))
                assertEquals(0, delta.getJSONArray("b5TargetRelations").length())
                assertEquals(0, delta.getJSONArray("b6Authorities").length())
                assertEquals(0, delta.getJSONArray("directCausalDisplacementEdges").length())
            }
        println("C17_PLACEMENT_CENSUS=${c17Summary}")
        val serializationProbe = generated.first { it.first.label == "reviewed_strength_isolated" }.second
            .comparison!!.prescriptionRealizationPlan!!.toCompactJson().toString()
        assertFalse(serializationProbe.contains("c14StrengthTrainingLoadShadow"))
        println(report)
        assertC9CorpusBoundaries(records)
        val control = generated.filter { it.second.routeDecision.selectedSource == StimulusProductionProgramSource.CONTROL }
        val b7ReasonCounts = control.flatMap { it.second.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
            .groupingBy { it }.eachCount()
        assertTrue(b7ReasonCounts.keys.contains("CHANGE_PROVENANCE_UNCLOSED"))
        assertFalse("aggregate regional H is not compared with the whole-quality reference",
            b7ReasonCounts.containsKey("TARGET_REGRESSED"))
        assertFalse("B6-denied Quality additions no longer contribute affected unmet targets",
            b7ReasonCounts.containsKey("AFFECTED_TARGET_REMAINS_UNMET"))
    }

    /** Parse prior-phase reports in short stack frames before downstream corpus audits accumulate. */
    private fun assertC15CurrentCorpusContracts(c15Census: String) {
        val c15 = org.json.JSONObject(c15Census)
        assertEquals(27, c15.getJSONObject("corpus").getInt("totalCases"))
        assertEquals(22, c15.getJSONObject("corpus").getInt("generated"))
        assertEquals(5, c15.getJSONObject("corpus").getInt("preflightRejected"))
        // Only persona3_mixed passes the exact P/J + C37 movement B4/B5/B6 proof; every other
        // corpus case remains on CONTROL unless its own complete authority chain passes.
        assertEquals(21, c15.getJSONObject("corpus").getJSONObject("routes").getInt("CONTROL"))
        assertEquals(1, c15.getJSONObject("corpus").getJSONObject("routes").getInt("B8_POWER_JUMP_V1"))
        assertEquals(0, c15.getJSONObject("corpus").getJSONObject("routes").getInt("B8_STRENGTH_CALIBRATION_V1"))
        assertEquals(0, c15.getJSONObject("corpus").getJSONObject("routes").getInt("B8_STRENGTH_V1"))
        assertEquals(3, c15.getJSONArray("cases").let { rows ->
            (0 until rows.length()).count { rows.getJSONObject(it).optJSONObject("C15_coldStart")?.optString("status") == "AVAILABLE" }
        })
        assertEquals(5, c15.getJSONArray("fiveC13RepRangeCases").length())
        assertEquals(0, c15.getJSONArray("directionOnlyStrengthCases").length())
        assertEquals(8, c15.getJSONArray("cases").let { rows ->
            (0 until rows.length()).count {
                rows.getJSONObject(it).optJSONObject("B4_strengthTarget")
                    ?.optString("numericAuthority") == "USER_APPROVED_PROJECT_POLICY"
            }
        })
        assertTrue(c15.getString("standardCoverageSha256").matches(Regex("[A-F0-9]{64}")))
        val calibrationCase = c15.getJSONArray("cases").let { rows ->
            (0 until rows.length()).map(rows::getJSONObject).single { it.getString("caseId") == "persona0_reviewed" }
        }
        assertEquals("persona0_reviewed", calibrationCase.getString("caseId"))
        assertEquals("PERSONAL_RESTORE_BASELINE", calibrationCase.getJSONObject("B4_strengthTarget").getString("numericAuthority"))
        assertTrue(calibrationCase.getBoolean("exactOwnerSameKeyAndRole"))
        assertEquals("AVAILABLE", calibrationCase.getJSONObject("C15_coldStart").getString("status"))
        assertEquals("USER_CALIBRATION_REQUIRED", calibrationCase.getJSONObject("C15_coldStart").getString("loadState"))
        assertEquals(2, calibrationCase.getJSONObject("C15_coldStart").getInt("sets"))
        assertEquals(6, calibrationCase.getJSONObject("C15_coldStart").getInt("reps"))
        assertEquals(6.5, calibrationCase.getJSONObject("C15_coldStart").getDouble("targetRpe"), 0.0)
        assertEquals("AUTHORIZED_COLD_START_USER_CALIBRATION", calibrationCase.getJSONObject("B6").getString("status"))
        assertEquals("FULLY_MATERIALIZED", calibrationCase.getJSONObject("materialization").getString("state"))
        assertEquals("NOT_ELIGIBLE", calibrationCase.getJSONObject("B7").getString("status"))
        assertTrue(calibrationCase.getJSONObject("B7").getJSONArray("reasons").toString()
            .contains("CHANGE_PROVENANCE_UNCLOSED"))
        assertEquals("CONTROL_REQUIRED", calibrationCase.getJSONObject("B8").getString("status"))
        assertEquals(0, calibrationCase.getJSONObject("buildAccounting").getInt("third"))
        assertFalse(calibrationCase.getJSONObject("C14_shadow").getBoolean("hasCapacityReference"))
        val routeCounts = c15.getJSONObject("corpus").getJSONObject("routes")
        assertEquals(0, routeCounts.getInt("B8_HYPERTROPHY_V1"))
        assertEquals(0, routeCounts.getInt("B8_STRENGTH_HYPERTROPHY_V1"))
    }

    private fun assertC16CurrentCorpusContracts(c16Census: String) {
        val c16Cases = org.json.JSONObject(c16Census).getJSONArray("cases")
        fun c16Case(name: String) = (0 until c16Cases.length()).map { c16Cases.getJSONObject(it) }
            .single { it.getString("case") == name }
        assertEquals(0, c16Case("persona0_mixed").getInt("authorizedCalibrationDeltaCount"))
        assertEquals(2, c16Case("persona0_mixed").getInt("authorizedCoreMaterialDeltaCount"))
        assertEquals(6, c16Case("persona0_mixed").getInt("residualDeltaCount"))
        assertEquals(4, c16Case("persona0_reviewed").getInt("authorizedCalibrationDeltaCount"))
        assertEquals(2, c16Case("persona0_reviewed").getInt("authorizedCoreMaterialDeltaCount"))
        assertEquals(12, c16Case("persona0_reviewed").getInt("residualDeltaCount"))
        assertEquals(4, c16Case("persona3_reviewed").getInt("authorizedCalibrationDeltaCount"))
        assertEquals(2, c16Case("persona3_reviewed").getInt("authorizedCoreMaterialDeltaCount"))
        assertEquals(2, c16Case("persona3_reviewed").getInt("authorizedPowerJumpMaterialDeltaCount"))
        assertEquals(12, c16Case("persona3_reviewed").getInt("residualDeltaCount"))
        assertEquals(4, c16Case("persona4_mixed").getInt("authorizedCalibrationDeltaCount"))
        assertEquals(2, c16Case("persona4_mixed").getInt("authorizedCoreMaterialDeltaCount"))
        assertEquals(24, c16Case("persona4_mixed").getInt("residualDeltaCount"))
        assertEquals(0, c16Case("persona2_reviewed").getInt("authorizedCalibrationDeltaCount"))
        assertEquals(2, c16Case("persona2_reviewed").getInt("authorizedCoreMaterialDeltaCount"))
        assertEquals(22, c16Case("persona2_reviewed").getInt("residualDeltaCount"))
        // C32 excludes the eight-rep reviewed squat history from Strength exposure.
        assertEquals("TRUE_SAFETY_BLOCK", c16Case("persona2_reviewed")
            .getJSONObject("rootClassification").getString("primaryDisposition"))
        val reviewedReplacementRoot = c16Case("persona0_reviewed").getJSONObject("rootClassification")
        assertEquals("AUDIT_OR_PROVENANCE_GAP", reviewedReplacementRoot.getString("primaryDisposition"))
        assertEquals("PROVENANCE_ONLY_GAP", reviewedReplacementRoot.getJSONObject("primaryRootBlocker").getString("type"))
        listOf("persona4_mixed").forEach { name ->
            val root = c16Case(name).getJSONObject("rootClassification")
            assertEquals("$name disposition", "TRUE_SAFETY_BLOCK", root.getString("primaryDisposition"))
            assertFalse("$name is not an exact false negative", root.getBoolean("potentialFalseNegativeEligible"))
        }
        val mixedReplacementRoot = c16Case("persona0_mixed").getJSONObject("rootClassification")
        // Once exact Core B6 replacement provenance is closed, this mixed Strength + movement
        // material remains correctly blocked: the existing B8 scope is not a Core grant.
        assertEquals("TRUE_SAFETY_BLOCK", mixedReplacementRoot.getString("primaryDisposition"))
        assertTrue(mixedReplacementRoot.getJSONArray("B8Reasons").toString()
            .contains("B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE"))
        assertEquals("TRUE_SAFETY_BLOCK", c16Case("persona3_reviewed")
            .getJSONObject("rootClassification").getString("primaryDisposition"))
        assertFalse(c16Case("persona3_reviewed").getJSONObject("rootClassification").getBoolean("potentialFalseNegativeEligible"))
        assertEquals("UNAUTHORIZED_PLACEMENT_CHANGE", c16Case("persona3_reviewed")
            .getJSONObject("rootClassification").getJSONObject("primaryRootBlocker").getString("type"))
    }

    private fun assertC26RejectedQualityRowsAreNotMaterialized(
        records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>
    ) {
        // Parse the prior census only in this helper's frame, before the current full census is
        // rendered and parsed. Keeping both JSONObject trees live at once exceeded Hosted CI's
        // memory budget even though the assertions themselves are small.
        val c25BeforeC26 = org.json.JSONObject(repositoryFile("docs/next-phase-bottleneck-census.json").readText())
        val rejectedRows = c25BeforeC26.getJSONObject("summary")
            .getJSONArray("qualityAddedOwnerWeeksWithoutAuthorizedB6Evidence")
        assertEquals(22, rejectedRows.length())
        var unexplainedAddedIntersection = 0
        val currentByCase = records.associateBy { it.first.label }
        for (index in 0 until rejectedRows.length()) {
            val baselineRow = rejectedRows.getJSONObject(index)
            val caseName = baselineRow.getString("case")
            val ownerJson = baselineRow.getJSONObject("owner")
            val identity = StimulusPrescriptionOwnerIdentity(
                ownerJson.getString("stableKey"), ownerJson.getString("selectionRole")
            )
            val week = baselineRow.getInt("week")
            val beforeCase = c25BeforeC26.getJSONArray("cases").let { rows ->
                (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("case") == caseName }
            }
            val beforeDelta = beforeCase.getJSONArray("materialDeltas").let { rows ->
                (0 until rows.length()).map { rows.getJSONObject(it) }.single { delta ->
                    delta.getString("kind") == "ADDED_OWNER" && delta.getInt("week") == week &&
                        delta.getJSONObject("owner").getString("stableKey") == identity.stableKey &&
                        delta.getJSONObject("owner").getString("selectionRole") == identity.selectionRole
                }
            }
            val beforeExecutableRows = beforeDelta.getJSONArray("after")
            assertEquals("C25 row was an actual scheduled prescription", 1, beforeExecutableRows.length())
            assertTrue(beforeExecutableRows.getJSONObject(0).getInt("sets") > 0)

            val baselineB7Added = beforeCase.getJSONObject("b7").getJSONArray("changeAttributions").let { rows ->
                (0 until rows.length()).map { rows.getJSONObject(it) }.any { attribution ->
                    attribution.optString("stableKey") == identity.stableKey &&
                        attribution.optString("selectionRole") == identity.selectionRole &&
                        attribution.getJSONArray("reasons").let { reasons ->
                            (0 until reasons.length()).any { reasons.getString(it) == "UNEXPLAINED_ADDED_IDENTITY" }
                        }
                }
            }
            if (baselineB7Added) unexplainedAddedIntersection++

            val currentResult = requireNotNull(currentByCase[caseName]?.second) {
                "C28 generated result missing for denied Quality row $caseName/$week/${identity.stableKey}#${identity.selectionRole}"
            }
            val currentComparison = currentResult.comparison
            if (currentComparison == null) {
                // When every experimental demand in a case is rejected, the canonical
                // no-demand failure returns intact CONTROL before any EXP skeleton exists.
                // This is a safe fail-closed outcome, not a missing comparison to paper over.
                assertEquals(StimulusProductionProgramSource.CONTROL, currentResult.routeDecision.selectedSource)
                assertEquals(1, currentResult.buildCounts.controlBuilds)
                assertEquals(1, currentResult.buildCounts.experimentalBuilds)
                assertEquals(2, currentResult.buildCounts.totalBuildInvocations)
                assertEquals(0, currentResult.buildCounts.thirdBuilds)
                assertFalse(currentResult.program.items.any {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole &&
                        it.weekNumber == week
                })
                assertEquals("NO_EXECUTABLE_PLANNING_DEMAND", currentResult.upstreamFailureDetails.firstOrNull())
                assertTrue(currentResult.upstreamFailureDetails.drop(1).all {
                    it == "EXACT_PRESCRIPTION_AUTHORITY_REQUIRED"
                })
                assertTrue("unresolved material need must survive the CONTROL fallback",
                    currentResult.unresolvedMaterialDemandGaps.isNotEmpty())
                assertTrue("owner re-resolution result must survive the CONTROL fallback",
                    currentResult.materialDemandAuthorityResolutions.any {
                        it.status == ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY &&
                            it.returnTarget == ExecutionAuthorityReturnTarget.MATERIAL_DEMAND_CANDIDATE_SELECTION &&
                            it.reason == ExecutionAuthorityResolutionReason.OWNER_CANDIDATES_EXHAUSTED
                    })
                continue
            }
            val currentAuthorizations = currentComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { authorization ->
                authorization.owner?.let { it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole } == true &&
                    authorization.quality != null
            }
            val currentAuthorization = currentAuthorizations.singleOrNull()
            if (currentAuthorization == null) {
                // C32 can reject an off-list owner at B5 before B6 is asked to authorize it.
                // That is a stronger fail-closed result than the C26 B6 refusal and must not be
                // mistaken for a missing authorization record.
                assertTrue("denied owner is not selected by B5: $caseName/$week/$identity",
                    currentComparison.selectionPlan.selectedCandidates.none {
                        it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                    })
                val typedCapabilityRejection = currentComparison.selectionPlan.candidateDispositionIndex.entries.any {
                    it.stableKey == identity.stableKey && it.canonicalSelectionRole == identity.selectionRole &&
                        StimulusCandidateDispositionReason.STRENGTH_CAPABILITY_NOT_APPROVED in it.reasons
                }
                assertTrue("missing B6 record is explained by typed B5 capability rejection: $caseName/$week/$identity",
                    typedCapabilityRejection)
                assertFalse(currentComparison.experimental.items.any {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole && it.weekNumber == week
                })
                assertFalse(currentComparison.addedOwnerIdentities.any { it == identity })
                continue
            }
            assertEquals(baselineRow.getString("b6Status"), currentAuthorization.status.name)
            val historicalReasons = baselineRow.getJSONArray("b6Reasons").let { reasons ->
                (0 until reasons.length()).map { reasons.getString(it) }.toSet()
            }
            if ("HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE" in historicalReasons ||
                "B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE" in historicalReasons) {
                // C33 now supplies a target-scoped regional residual only when B4 admitted
                // the need. Older C26 Quality rows can still have no applicable B4 numeric
                // target; either outcome must remain a typed, fail-closed B6 refusal.
                assertTrue(currentAuthorization.reasonCodes.toSet().any {
                    it in setOf(
                        "SAFE_LOAD_OR_EFFORT_AUTHORITY_UNAVAILABLE",
                        "HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE",
                        "B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE"
                    )
                })
            } else {
                assertEquals(historicalReasons, currentAuthorization.reasonCodes.toSet())
            }
            val executableRows = currentComparison.experimental.items.filter {
                it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole && it.weekNumber == week
            }
            assertTrue("B6-rejected owner-week must not be executable EXP material: $caseName w$week $identity",
                executableRows.isEmpty())
            assertFalse("B6-rejected identity must not be an EXP added owner: $caseName $identity",
                currentComparison.addedOwnerIdentities.any { it == identity })
            assertFalse(currentComparison.experimentalReadinessAudit?.changeAttributions.orEmpty().any { attribution ->
                attribution.stableKey == identity.stableKey && attribution.selectionRole == identity.selectionRole &&
                    "UNEXPLAINED_ADDED_IDENTITY" in attribution.reasonCodes
            })
        }
        assertEquals("Rejected Quality rows and B7 unexplained additions are disjoint", 0, unexplainedAddedIntersection)
    }

    private data class C25TaskOnlyRoomFixture(
        val comparison: StimulusSelectionProgramComparison,
        val b8: StimulusProductionCutoverAuthorityDecision,
        val route: StimulusProductionRoutingDecision,
        val rollback: StimulusProductionRoutingDecision
    )

    /**
     * Starts with the exact EXP skeleton produced through the real Room/service path. The
     * comparator is then a test-only projection of that already-built skeleton with its
     * governed task rows omitted, so the only material delta is the task B6 material itself.
     * No planner/build service is called a third time and no CONTROL data supplies authority.
     */
    private fun buildC25TaskOnlyComparison(
        serviceResult: StimulusProductionGenerationResult
    ): StimulusSelectionProgramComparison {
        val source = requireNotNull(serviceResult.comparison)
        val experimental = source.experimental
        val taskRows = experimental.items.filter { !it.taskProtocolSemanticsJson.isNullOrBlank() }
        require(taskRows.isNotEmpty()) { "Room/service EXP result has no exact C24 task rows" }
        val ownerSet = taskRows.mapTo(linkedSetOf()) {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }
        val decoded = taskRows.map { row ->
            row to TaskProtocolExposureMetadata.fromJsonString(requireNotNull(row.taskProtocolSemanticsJson))
        }
        val tasks = decoded.flatMapTo(linkedSetOf()) { it.second.authorization.attributedTasks }
        require(tasks.isNotEmpty() && CanonicalTaskTarget.JUMP_LANDING !in tasks)
        val taskIds = tasks.mapTo(linkedSetOf()) { "TASK:${it.name}" }
        val targets = source.targetPlan.taskTargets.filter { "TASK:${it.task}" in taskIds }
        require(targets.map { "TASK:${it.task}" }.toSet() == taskIds)
        val selected = source.selectionPlan.selectedCandidates.filter {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in ownerSet
        }
        require(selected.map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }.toSet() == ownerSet)
        val traces = source.selectionPlan.traces.filter { it.targetId in taskIds }
        val taskOnlySelection = source.selectionPlan.copy(selectedCandidates = selected, traces = traces)
        val taskOnlyPlan = source.targetPlan.copy(qualityTargets = emptyList(), taskTargets = targets, unresolved = emptyList())
        val comparator = experimental.copy(
            items = experimental.items.filter { it.taskProtocolSemanticsJson.isNullOrBlank() },
            taskProtocolFrequencyOutcomes = emptyList()
        )
        val raw = StimulusSelectionProgramComparisonEngine().compare(
            control = comparator,
            experimental = experimental,
            targetPlan = taskOnlyPlan,
            selectionPlan = taskOnlySelection,
            controlAudit = source.controlAudit,
            experimentalAudit = source.experimentalAudit
        )
        val b7 = StimulusExperimentalReadinessAuditEngine().audit(raw)
        return raw.copy(experimentalReadinessAudit = b7)
    }

    private fun renderC25TaskCutoverCensus(
        records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>,
        c24Census: String,
        c20Census: String,
        roomBackedFixture: StimulusSelectionProgramComparison,
        roomBackedB8: StimulusProductionCutoverAuthorityDecision,
        roomBackedRoute: StimulusProductionRoutingDecision,
        roomBackedRollback: StimulusProductionRoutingDecision
    ): String {
        val jsonCases = records.mapNotNull { (spec, result) ->
            result?.comparison?.let { comparison ->
                val taskRows = comparison.experimental.items.filter { !it.taskProtocolSemanticsJson.isNullOrBlank() }
                val materialScope = StimulusProductionMaterialScopeResolver().resolve(comparison)
                org.json.JSONObject()
                    .put("case", spec.label)
                    .put("materialScope", materialScope?.name)
                    .put("taskProtocolRows", taskRows.size)
                    .put("scopeResolutionStatus", result.diagnostics.scopeResolution?.status?.name)
                    .put("scopeResolutionReasons", org.json.JSONArray(
                        result.diagnostics.scopeResolution?.reasonCodes.orEmpty().sorted()
                    ))
                    .put("powerMaterialRows", comparison.experimental.items.count {
                        it.selectionRole == "CANONICAL_STIMULUS_QUALITY_POWER"
                    })
                    .put("jumpLandingMaterialRows", taskRows.count { row ->
                        TaskProtocolExposureMetadata.fromJsonString(requireNotNull(row.taskProtocolSemanticsJson))
                            .authorization.attributedTasks.contains(CanonicalTaskTarget.JUMP_LANDING)
                    })
                    .put("b7Status", comparison.experimentalReadinessAudit?.status?.name)
                    .put("b7Reasons", org.json.JSONArray(comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                    .put("b8Scope", comparison.productionCutoverAuthority?.scope?.name)
                    .put("b8Status", comparison.productionCutoverAuthority?.status?.name)
                    .put("b8Reasons", org.json.JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                    .put("route", result.routeDecision.selectedSource.name)
                    .put("routeReasons", org.json.JSONArray(result.routeDecision.reasonCodes.sorted()))
                    .put("builds", org.json.JSONObject()
                        .put("control", result.buildCounts.controlBuilds)
                        .put("experimental", result.buildCounts.experimentalBuilds)
                        .put("total", result.buildCounts.totalBuildInvocations)
                        .put("third", result.buildCounts.thirdBuilds))
            }
        }.sortedBy { it.getString("case") }
        val routeCounts = jsonCases.groupingBy { it.getString("route") }.eachCount().toSortedMap()
        val b8StatusCounts = jsonCases.groupingBy { it.optString("b8Status", "MISSING") }.eachCount().toSortedMap()
        val b8ReasonCounts = jsonCases.flatMap { row ->
            val reasons = row.getJSONArray("b8Reasons")
            (0 until reasons.length()).map(reasons::getString)
        }.groupingBy { it }.eachCount().toSortedMap()
        val scopeStatusCounts = jsonCases.groupingBy { it.optString("scopeResolutionStatus", "MISSING") }.eachCount().toSortedMap()
        val scopeReasonCounts = jsonCases.flatMap { row ->
            val reasons = row.getJSONArray("scopeResolutionReasons")
            (0 until reasons.length()).map(reasons::getString)
        }.groupingBy { it }.eachCount().toSortedMap()
        val c24 = org.json.JSONObject(c24Census)
        val c20 = org.json.JSONObject(c20Census).getJSONObject("summary")
        val persona = jsonCases.single { it.getString("case") == "persona3_recent" }
        val summary = org.json.JSONObject()
            .put("generatedCases", jsonCases.size)
            .put("taskOnlyScopeCandidates", jsonCases.count { it.optString("materialScope") == "BADMINTON_TASK_V1" })
            .put("authorizedTaskCases", jsonCases.count {
                it.optString("b8Scope") == "BADMINTON_TASK_V1" &&
                    it.optString("b8Status") == "AUTHORIZED_FOR_BOUNDED_CUTOVER"
            })
            .put("taskRouteCases", routeCounts[StimulusProductionProgramSource.B8_BADMINTON_TASK_V1.name] ?: 0)
            .put("taskMaterialCases", jsonCases.count { it.getInt("taskProtocolRows") > 0 })
            .put("persona3RecentTaskRows", persona.getInt("taskProtocolRows"))
            .put("powerMaterialRows", jsonCases.sumOf { it.getInt("powerMaterialRows") })
            .put("jumpLandingMaterialRows", jsonCases.sumOf { it.getInt("jumpLandingMaterialRows") })
            .put("routes", org.json.JSONObject().also { obj -> routeCounts.forEach(obj::put) })
            .put("b8StatusCounts", org.json.JSONObject().also { obj -> b8StatusCounts.forEach(obj::put) })
            .put("b8ReasonOccurrences", org.json.JSONObject().also { obj -> b8ReasonCounts.forEach(obj::put) })
            .put("scopeResolutionStatusCounts", org.json.JSONObject().also { obj -> scopeStatusCounts.forEach(obj::put) })
            .put("scopeResolutionReasonOccurrences", org.json.JSONObject().also { obj -> scopeReasonCounts.forEach(obj::put) })
            .put("buildAccounting", org.json.JSONObject().put("control", 1).put("experimental", 1).put("total", 2).put("third", 0))
            .put("b7", c24.getJSONObject("b7Summary"))
            .put("c20", org.json.JSONObject()
                .put("hardValid", c20.getInt("HARD_VALID"))
                .put("hardInvalid", c20.getInt("HARD_INVALID"))
                .put("unresolved", c20.getInt("UNRESOLVED"))
                .put("invalidOrUnresolvedForcedPreserved",
                    c20.getInt("hardInvalidRowsForcedPreserved") + c20.getInt("unresolvedRowsForcedPreserved")))
        val fixture = org.json.JSONObject()
            .put("source", "PERSONA3_RECENT_REAL_ROOM_SERVICE_EXPERIMENTAL")
            .put("syntheticComparator", "SAME_ALREADY_BUILT_EXPERIMENTAL_WITH_GOVERNED_TASK_ROWS_OMITTED")
            .put("b7Status", roomBackedFixture.experimentalReadinessAudit?.status?.name)
            .put("b7Reasons", org.json.JSONArray(roomBackedFixture.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
            .put("b8Status", roomBackedB8.status.name)
            .put("b8Scope", roomBackedB8.scope.name)
            .put("b8Reasons", org.json.JSONArray(roomBackedB8.reasonCodes.sorted()))
            .put("authorizedTaskOwners", roomBackedB8.authorizedTaskProtocolIdentities.size)
            .put("route", roomBackedRoute.selectedSource.name)
            .put("usesExperimentalObject", roomBackedRoute.selectedSource == StimulusProductionProgramSource.B8_BADMINTON_TASK_V1)
            .put("rollback", roomBackedRollback.selectedSource.name)
            .put("usesOriginalControlObject", roomBackedRollback.selectedSource == StimulusProductionProgramSource.CONTROL)
            .put("builds", org.json.JSONObject().put("control", 1).put("experimental", 1).put("total", 2).put("third", 0))
        return org.json.JSONObject()
            .put("phase", "C25_BOUNDED_BADMINTON_TASK_ONLY_B8_CUTOVER")
            .put("cases", org.json.JSONArray(jsonCases))
            .put("summary", summary)
            .put("persona3Recent", persona)
            .put("roomBackedPositiveFixture", fixture)
            .toString(2) + "\n"
    }

    private fun assertC9CorpusBoundaries(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>) {
        assertEquals(27, records.size)
        assertEquals(5, records.count { it.second == null })
        val omissions = records.mapNotNull { (spec, result) -> result?.comparison?.let { spec to it } }
            .flatMap { (spec, comparison) -> comparison.nonSelectionProvenance.map { spec.label to it } }
        assertTrue("C28 preserves typed non-selection evidence", omissions.isNotEmpty())
        assertTrue(omissions.all { (caseId, omission) ->
            val comparison = requireNotNull(records.single { it.first.label == caseId }.second?.comparison)
            comparison.control.items.any {
                it.exerciseStableKey == omission.omittedControlOwner.stableKey &&
                    it.selectionRole == omission.omittedControlOwner.selectionRole
            }
        })
        // Exact B4/B5/B6 regional movement replacement evidence may close some or all
        // owner-removal provenance. Any remaining authority or target blocker still retains CONTROL.
        records.forEach { (spec, result) ->
            if (result != null) {
                val comparison = result.comparison
                if (comparison == null) {
                    assertTrue("Failed generation retains unresolved demand for ${spec.label}",
                        result.unresolvedMaterialDemandGaps.isNotEmpty())
                } else {
                    val audit = requireNotNull(comparison.experimentalReadinessAudit)
                    if (audit.changeProvenanceClosed) {
                        assertTrue("Closed B7 provenance has no unexplained or inconclusive removals for ${spec.label}",
                            audit.changeAttributions.none {
                                it.source in setOf(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
                                    StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT)
                            })
                    } else {
                        assertTrue("Unclosed B7 provenance remains explicit for ${spec.label}",
                            "CHANGE_PROVENANCE_UNCLOSED" in audit.reasonCodes)
                    }
                }
                if (result.routeDecision.selectedSource == StimulusProductionProgramSource.B8_POWER_JUMP_V1) {
                    assertEquals("Only exact Power/Jump B8 can leave CONTROL: ${spec.label}",
                        StimulusProductionCutoverScope.POWER_JUMP_V1,
                        comparison?.productionCutoverAuthority?.scope)
                    assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                        comparison?.productionCutoverAuthority?.status)
                    assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                        comparison?.experimentalReadinessAudit?.status)
                } else {
                    assertEquals("Unresolved authority retains CONTROL: ${spec.label}",
                        StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
                }
            }
        }
        val h = requireNotNull(records.single { it.first.label == "reviewed_hypertrophy_isolated" }.second)
        val c = requireNotNull(h.comparison)
        val audit = requireNotNull(c.experimentalReadinessAudit)
        assertTrue(audit.materializationIntegrityPassed)
        assertTrue(audit.collateralRegressionFree)
        assertFalse(audit.changeProvenanceClosed)
        assertEquals(listOf("CHANGE_PROVENANCE_UNCLOSED"), audit.reasonCodes)
        val unresolved = audit.changeAttributions.single {
            it.stableKey == "ex_284ecca6" && it.selectionRole == "COVERAGE_POSTERIOR_CHAIN" &&
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED
        }
        assertEquals("ex_284ecca6", unresolved.stableKey)
        assertEquals("COVERAGE_POSTERIOR_CHAIN", unresolved.selectionRole)
        assertEquals(listOf("UNEXPLAINED_REMOVED_IDENTITY"), unresolved.reasonCodes)
        assertFalse(c.experimental.items.any {
            it.exerciseStableKey == unresolved.stableKey && it.selectionRole == unresolved.selectionRole
        })
        assertFalse(c.selectionPlan.selectedCandidates.any { it.stableKey == unresolved.stableKey && it.selectionRole == unresolved.selectionRole })
        assertFalse(c.prescriptionAuthorizationPlan!!.authorizations.any { it.owner?.stableKey == unresolved.stableKey })
        assertFalse(unresolved.stableKey in c.experimental.personalizedDecision?.planningBudget?.execution?.constrainedOwnerStableKeys.orEmpty())
        assertTrue(audit.changeAttributions.any { it.stableKey == "cable_rear_delt_fly" &&
            "B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE" in it.reasonCodes && it.targetIds == listOf("QUALITY:HYPERTROPHY") })
        val legacyFly = StimulusPrescriptionOwnerIdentity("cable_rear_delt_fly", "STYLE_MEDIUM_HORIZONTAL_PULL")
        val flyOmission = c.nonSelectionProvenance.single { it.omittedControlOwner == legacyFly }
        assertEquals(StimulusNonSelectionClassification.CANONICAL_REPLACEMENT, flyOmission.classification)
        val exactFlyDisposition = flyOmission.targetEvidence.single { it.targetId == "QUALITY:HYPERTROPHY" }.disposition
        assertEquals(StimulusCandidateDispositionStatus.SELECTED, exactFlyDisposition.status)
        assertEquals(legacyFly.stableKey, exactFlyDisposition.stableKey)
        assertEquals("CANONICAL_STIMULUS_QUALITY_HYPERTROPHY", exactFlyDisposition.canonicalSelectionRole)
        val withoutTypedOmissionEvidence = c.copy(
            selectionPlan = c.selectionPlan.copy(candidateDispositionIndex = StimulusCandidateDispositionIndex(
                c.selectionPlan.candidateDispositionIndex.entries - exactFlyDisposition
            )),
            nonSelectionProvenance = c.nonSelectionProvenance.map { omission ->
                if (omission.omittedControlOwner != legacyFly) omission else omission.copy(
                    targetEvidence = omission.targetEvidence.filterNot { it.disposition == exactFlyDisposition }
                )
            }
        )
        val withoutTypedAudit = StimulusExperimentalReadinessAuditEngine().audit(withoutTypedOmissionEvidence)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            withoutTypedAudit.changeAttributions.single { it.stableKey == legacyFly.stableKey && it.selectionRole == legacyFly.selectionRole }.source)
    }

    @Test
    fun c28UnapprovedCoverageCandidateStaysDiagnosticAndDoesNotMaterialize() = runBlocking {
        val spec = CoverageSpec("c9_allocator_boundary", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly",
            "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY,
            false, "reviewed", 3, 60, emptySet(), true)
        val result = requireNotNull(runCase(spec))
        val owner = StimulusPrescriptionOwnerIdentity("ex_284ecca6", "COVERAGE_POSTERIOR_CHAIN")
        assertFalse(result.comparison?.experimental?.items.orEmpty().any {
            it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        })
        if (result.comparison == null) {
            assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
            assertEquals("NO_EXECUTABLE_PLANNING_DEMAND", result.upstreamFailureReason)
            assertTrue(result.unresolvedMaterialDemandGaps.isNotEmpty())
            assertTrue(result.materialDemandAuthorityResolutions.any {
                it.status == ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY
            })
        } else {
            val execution = requireNotNull(result.comparison?.experimental?.personalizedDecision?.planningBudget?.execution)
            assertTrue(execution.materialDemandCandidateOrigins.any {
                it.owner.stableKey == owner.stableKey && it.owner.selectionRole == owner.selectionRole
            })
        }
    }

    /** C9 diagnostic: actual service objects, before any attribution/golden changes. */
    private fun renderProvenance(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String = buildString {
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        appendLine("generated=${generated.size} rejected=${records.size - generated.size}")
        appendLine("routes=" + generated.groupingBy { it.second.routeDecision.selectedSource }.eachCount().toSortedMap())
        appendLine("B7 counts=" + generated.flatMap { it.second.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }.groupingBy { it }.eachCount().toSortedMap())
        appendLine("B8 counts=" + generated.flatMap { it.second.comparison?.productionCutoverAuthority?.reasonCodes.orEmpty() }.groupingBy { it }.eachCount().toSortedMap())
        appendLine("C10 origin trace coverage (${renderC10TraceCoverage(records)})")
        val omissions = generated.flatMap { (spec, result) -> result.comparison?.nonSelectionProvenance.orEmpty().map { spec.label to it } }
        appendLine("C11 unique omitted CONTROL owner classifications=" + omissions.groupingBy { it.second.classification }.eachCount().toSortedMap())
        val weeklyOmissions = generated.flatMap { (spec, result) ->
            val comparison = result.comparison ?: return@flatMap emptyList()
            comparison.nonSelectionProvenance.flatMap { omission ->
                comparison.control.items.filter {
                    it.exerciseStableKey == omission.omittedControlOwner.stableKey &&
                        it.selectionRole == omission.omittedControlOwner.selectionRole
                }.map { it.weekNumber }.distinct().map { week -> omission.classification }
            }
        }
        appendLine("C11 removed owner-week classifications=" + weeklyOmissions.groupingBy { it }.eachCount().toSortedMap())
        omissions.sortedWith(compareBy({ it.first }, { it.second.omittedControlOwner.stableKey }, { it.second.omittedControlOwner.selectionRole }))
            .forEach { (caseId, omission) ->
                appendLine("C11 omitted case=$caseId owner=${omission.omittedControlOwner} classification=${omission.classification}")
                omission.targetEvidence.forEach { target ->
                    val d = target.disposition
                    appendLine("  target=${target.targetId} classification=${target.classification} direct=${d.directTargetCandidate} selectionRequired=${d.selectionRequired} status=${d.status} reasons=${d.reasons} selectedInstead=${d.selectedInstead} candidateTuple=${d.candidateRanking} selectedTuple=${d.selectedInsteadRanking} firstDifference=${d.firstDifferingField}")
                }
            }
        generated.singleOrNull { it.first.label == "reviewed_hypertrophy_isolated" }?.second?.comparison?.let { h ->
            listOf("ex_284ecca6", "ex_28347c1f").forEach { key ->
                appendLine("C11 H owner diagnostic stableKey=$key disposition=${h.selectionPlan.candidateDispositionIndex.forStableKey(key)}")
            }
        }
        generated.sortedBy { it.first.label }.forEach { (spec, result) ->
            val c = result.comparison
            if (c == null) {
                appendLine("CASE ${spec.label}")
                appendLine("EXPERIMENTAL failed closed before skeleton materialization: ${result.upstreamFailureReason} ${result.upstreamFailureDetails}")
                appendLine("route=${result.routeDecision.selectedSource} builds=${result.buildCounts}")
                return@forEach
            }
            fun identity(row: ProgramSkeletonItem) = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
            val before = c.control.items.groupBy(::identity)
            val after = c.experimental.items.groupBy(::identity)
            fun prescriptions(rows: List<ProgramSkeletonItem>) = rows.map {
                PlannedPrescription(it.prescription, it.setPrescriptions, it.restSeconds, it.weightSource)
            }
            appendLine("CASE ${spec.label}")
            appendLine("B4=${c.targetPlan}")
            appendLine("B5=${c.selectionPlan.selectedCandidates}")
            appendLine("B6=${c.prescriptionAuthorizationPlan}")
            appendLine("CONTROL owners=${before.keys}")
            appendLine("EXPERIMENTAL owners=${after.keys}")
            appendLine("added=${after.keys - before.keys}")
            appendLine("removed=${before.keys - after.keys}")
            appendLine("shared=${before.keys intersect after.keys}")
            appendLine("prescriptionChanged=" + (before.keys intersect after.keys).filter { prescriptions(before.getValue(it)) != prescriptions(after.getValue(it)) })
            appendLine("B7=${c.experimentalReadinessAudit}")
            appendLine("B8=${c.productionCutoverAuthority}")
            appendLine("route=${result.routeDecision.selectedSource} builds=${result.buildCounts}")
            appendLine("materialization=${c.prescriptionMaterializationAudits}")
            appendLine("realization=${c.prescriptionRealizationPlan}")
            appendLine("selectionTraces=${c.selectionPlan.traces}")
            appendLine("materializationTraces=${c.materializationTraces}")
            appendLine("allocator=${c.experimental.personalizedDecision?.planningBudget?.execution}")
            listOf("CONTROL" to c.control, "EXPERIMENTAL" to c.experimental).forEach { (label, program) ->
                appendLine("$label schedule=${program.weekDaySchedule}")
                program.items.forEach { row ->
                    appendLine("$label row owner=${identity(row)} slot=${row.weekNumber}/${row.dayOfWeek}/${row.orderIndex} sets=${row.setPrescriptions} rest=${row.restSeconds} weightSource=${row.weightSource} prescription=${row.prescription} setCount=${row.setCount} reps=${row.reps} weight=${row.weightKg} seconds=${row.seconds}")
                }
            }
        }
    }

    private fun renderC10TraceCoverage(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String {
        data class Delta(val caseId: String, val comparison: StimulusSelectionProgramComparison, val kind: String,
            val owner: StimulusPrescriptionOwnerIdentity, val week: Int,
            val before: List<ProgramSkeletonItem>, val after: List<ProgramSkeletonItem>)
        val totals = sortedMapOf<String, Int>()
        val exact = sortedMapOf<String, Int>()
        val deltas = mutableListOf<Delta>()
        records.sortedBy { it.first.label }.forEach { (spec, result) -> result?.comparison?.let { comparison ->
            fun byOwnerWeek(rows: List<ProgramSkeletonItem>) = rows.groupBy {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
            }
            val before = byOwnerWeek(comparison.control.items)
            val after = byOwnerWeek(comparison.experimental.items)
            (before.keys + after.keys).distinct().sortedWith(compareBy({ it.first.stableKey }, { it.first.selectionRole }, { it.second }))
                .forEach { (owner, week) ->
                    val oldRows = before[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val newRows = after[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val kind = when {
                        oldRows.isEmpty() && newRows.isNotEmpty() -> "added_owner"
                        oldRows.isNotEmpty() && newRows.isEmpty() -> "removed_owner"
                        oldRows.sumOf { it.setCount } != newRows.sumOf { it.setCount } -> "set_change"
                        oldRows.map { it.prescription to it.setPrescriptions } != newRows.map { it.prescription to it.setPrescriptions } -> "prescription_change"
                        oldRows.map { it.dayOfWeek to it.orderIndex } != newRows.map { it.dayOfWeek to it.orderIndex } &&
                            oldRows.map { it.dayOfWeek } != newRows.map { it.dayOfWeek } -> "placement_move"
                        oldRows.map { it.orderIndex } != newRows.map { it.orderIndex } -> "order_change"
                        else -> null
                    } ?: return@forEach
                    val delta = Delta(spec.label, comparison, kind, owner, week, oldRows, newRows)
                    deltas += delta
                    totals[kind] = (totals[kind] ?: 0) + 1
                }
        } }
        fun stateMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity): Boolean =
            state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
                (state.day == null || state.day == row.dayOfWeek) && (state.order == null || state.order == row.orderIndex) &&
                state.setCount == row.setCount && state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun materialMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity): Boolean =
            state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
                state.setCount == row.setCount && state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun placementMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity,
            includeOrder: Boolean): Boolean = state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
            state.day == row.dayOfWeek && (!includeOrder || state.order == row.orderIndex) && state.setCount == row.setCount &&
            state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun trace(program: GeneratedProgramSkeleton) = program.personalizedDecision?.planningBudget?.execution?.ownerAllocationProvenance.orEmpty()
        fun exactOrigin(delta: Delta, comparison: StimulusSelectionProgramComparison): Boolean {
            val beforeTrace = trace(comparison.control)
            val afterTrace = trace(comparison.experimental)
            return when (delta.kind) {
                "added_owner" -> afterTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.ADDED, OwnerAllocationAction.FREQUENCY_REPLICATED,
                        OwnerAllocationAction.PLACEMENT_ASSIGNED) && delta.after.any { row -> materialMatches(event.after, row, delta.owner) } }
                "removed_owner" -> afterTrace.any { event -> event.owner == delta.owner && event.action == OwnerAllocationAction.REMOVED &&
                    event.after == null && delta.before.any { row -> stateMatches(event.before, row, delta.owner) } }
                "set_change" -> {
                    val events = beforeTrace + afterTrace
                    events.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED) &&
                    delta.before.any { old -> stateMatches(event.before, old, delta.owner) } &&
                    delta.after.any { new -> stateMatches(event.after, new, delta.owner) } } ||
                    events.any { reverse -> reverse.owner == delta.owner &&
                        reverse.action in setOf(OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED) &&
                        delta.after.any { new -> stateMatches(reverse.before, new, delta.owner) } &&
                        delta.before.any { old -> stateMatches(reverse.after, old, delta.owner) } }
                }
                "prescription_change" -> (beforeTrace + afterTrace).any { event -> event.owner == delta.owner &&
                    event.action == OwnerAllocationAction.PRESCRIPTION_CHANGED &&
                    delta.before.any { old -> stateMatches(event.before, old, delta.owner) } &&
                    delta.after.any { new -> stateMatches(event.after, new, delta.owner) } }
                "placement_move" -> beforeTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                        OwnerAllocationAction.ORDER_CHANGED) && delta.before.any { row -> placementMatches(event.after, row, delta.owner, false) } } &&
                    afterTrace.any { event -> event.owner == delta.owner &&
                        event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                            OwnerAllocationAction.ORDER_CHANGED) && delta.after.any { row -> placementMatches(event.after, row, delta.owner, false) } }
                "order_change" -> beforeTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                        OwnerAllocationAction.ORDER_CHANGED) && delta.before.any { row -> placementMatches(event.after, row, delta.owner, true) } } &&
                    afterTrace.any { event -> event.owner == delta.owner &&
                        event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                            OwnerAllocationAction.ORDER_CHANGED) && delta.after.any { row -> placementMatches(event.after, row, delta.owner, true) } }
                else -> false
            }
        }
        val unproven = mutableListOf<String>()
        deltas.forEach { delta ->
            if (exactOrigin(delta, delta.comparison)) exact[delta.kind] = (exact[delta.kind] ?: 0) + 1
            else {
                val decision = delta.comparison.experimental.personalizedDecision
                val selectedAtB5 = delta.owner in delta.comparison.selectionPlan.selectedCandidates.map {
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
                }
                val inFiniteDemand = delta.owner in decision?.frequencyDemand?.candidates.orEmpty().map {
                    StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role)
                }
                val authorizedForScheduling = delta.owner in decision?.authorizedScheduling?.authorized.orEmpty().map {
                    StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role)
                }
                val boundary = when {
                    authorizedForScheduling -> "owner_reached_authorized_scheduling"
                    inFiniteDemand -> "owner_reached_finite_demand_not_authorized_for_scheduling"
                    selectedAtB5 -> "b5_selected_before_finite_demand"
                    else -> "no_exact_owner_in_exp_b5_or_finite_demand"
                }
                unproven += "${delta.caseId}:${delta.kind}:${delta.owner.stableKey}#${delta.owner.selectionRole}:w${delta.week}[$boundary]"
            }
        }
        val categories = listOf("added_owner", "removed_owner", "set_change", "prescription_change", "placement_move", "order_change")
        val counts = categories.joinToString("; ") { kind ->
            val count = totals[kind] ?: 0
            "$kind=$count/${exact[kind] ?: 0}/${count - (exact[kind] ?: 0)}"
        }
        return "$counts; unproven=${unproven.joinToString(",")}"
    }

    @Test
    fun resolvedRequestParityCoversExplicitAndInferredInputs() = runBlocking {
        val cases = listOf(
            CoverageSpec("request_strength_explicit", TrainableQuality.STRENGTH, "barbell_back_squat", "STRENGTH_GAIN",
                ProgramGoal.STRENGTH, StrengthIntent.STRENGTH_PRIORITY, false, "reviewed", 2, 30,
                setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"), explicitWeeklyDays = true, explicitDuration = true),
            CoverageSpec("request_hypertrophy_inferred", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly", "HYPERTROPHY_PHYSIQUE",
                ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY, false, "reviewed", 3, 60,
                setOf("MACHINE", "CABLE"), explicitWeeklyDays = false, explicitDuration = false),
            CoverageSpec("request_mixed_inferred_duration", TrainableQuality.STRENGTH, "barbell_back_squat", "MIXED",
                ProgramGoal.FUNCTIONAL_CONDITIONING, StrengthIntent.MIXED, false, "mixed", 4, 90,
                emptySet(), explicitWeeklyDays = true, explicitDuration = false),
            CoverageSpec("request_badminton_inferred_days", TrainableQuality.STRENGTH, "barbell_back_squat", "MIXED",
                ProgramGoal.BADMINTON_SUPPORT, StrengthIntent.MIXED, true, "reviewed", 5, 30,
                emptySet(), explicitWeeklyDays = false, explicitDuration = true)
        )
        cases.forEach { spec ->
            val result = requireNotNull(runCase(spec) { service, preflight, answers, metadata ->
                service.generatePreparedProduction(preflight, answers, metadata)
            })
            val comparison = requireNotNull(result.comparison)
            val resolved = comparison.control.request
            assertEquals("${spec.label} complete request parity", resolved, comparison.experimental.request)
            assertEquals("${spec.label} audit horizon", resolved.durationWeeks,
                requireNotNull(comparison.experimentalAudit).planningHorizonWeeks)
            val frequency = requireNotNull(comparison.experimental.personalizedDecision?.frequencyDemand?.frequency)
            assertEquals("${spec.label} frequency request days", resolved.weeklyTrainingDays, frequency.resolvedUserDays)
            assertEquals("${spec.label} frequency source",
                if (spec.explicitWeeklyDays) PlanningFrequencySource.EXPLICIT_USER else PlanningFrequencySource.AUTO,
                frequency.source)
            assertEquals("${spec.label} session minutes", spec.minutes, resolved.sessionMinutes)
            assertTrue("${spec.label} days stay in the bounded range", resolved.weeklyTrainingDays in 2..5)
            assertTrue("${spec.label} duration stays in the bounded horizon", resolved.durationWeeks in 2..6)
            if (spec.explicitDuration) assertEquals("${spec.label} explicit duration", 2, resolved.durationWeeks)
        }
    }

    @Test
    fun activePowerDraftKeepsReplacementSelectionOnTheFullB5B6B7B8B9Path() = runBlocking {
        val spec = CoverageSpec(
            label = "persona3_mixed",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "MIXED",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            intent = StrengthIntent.MIXED,
            badminton = true,
            history = "mixed",
            days = 5,
            minutes = 60,
            equipment = setOf("MACHINE", "CABLE")
        )
        val result = requireNotNull(runCase(spec) { service, preflight, answers, metadata ->
            val generated = service.generatePreparedProduction(
                preflight = preflight,
                answers = answers,
                metadata = metadata,
                routingMode = StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE
            )
            val keepFingerprint = ProgramReplacementReviewFingerprint.create(generated.program)
            val firstCandidate = generated.program.replacementReview?.options?.firstOrNull {
                it.targetId == "MOVEMENT:HORIZONTAL_PUSH"
            }
            val b8 = requireNotNull(generated.comparison?.productionCutoverAuthority)
            val authorizedTargetIds = buildSet {
                b8.authorizedAuthorityIdentities.forEach { add("QUALITY:${it.quality.name}") }
                b8.authorizedTaskProtocolIdentities.flatMap { it.authorizedTasks }.forEach { add("TASK:${it.name}") }
                b8.authorizedMovementTargetOwnerIdentities.forEach { add(it.targetId) }
            }
            assertTrue(generated.program.replacementReview?.options.orEmpty().all { it.targetId in authorizedTargetIds })
            val selectedCandidate = requireNotNull(firstCandidate) { "Expected a B5 replacement candidate for an authorized Power-route movement target" }
            val alternative = service.validateReplacementSelection(
                generated.program,
                setOf(selectedCandidate.optionId),
                metadata
            )
            val review = requireNotNull(alternative.replacementReview)
            assertEquals(ProgramReplacementValidationStatus.VALIDATED, review.validation?.status)
            assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                review.validation?.b7Status)
            assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                review.validation?.b8Status)
            assertTrue("validation is only a preview; explicit apply is still required",
                review.appliedOptionIds.isEmpty())
            assertNotEquals(keepFingerprint, ProgramReplacementReviewFingerprint.create(alternative))
            val edge = requireNotNull(review.validation?.causalEvidence?.singleOrNull())
            assertEquals(selectedCandidate.targetId, edge.targetId)
            assertEquals(selectedCandidate.sourceStableKey, edge.keepOwner.stableKey)
            assertEquals(selectedCandidate.candidateStableKey, edge.replacementOwner.stableKey)
            assertTrue(edge.controlRows.isNotEmpty() && edge.keepRows.isNotEmpty() && edge.replacementRows.isNotEmpty())
            assertEquals(StimulusProductionProgramSource.B8_POWER_JUMP_V1,
                review.validation?.b9SelectedSource)
            assertEquals("failed validation must leave the keep draft untouched", keepFingerprint,
                ProgramReplacementReviewFingerprint.create(generated.program))
            generated
        })
        assertEquals(StimulusProductionProgramSource.B8_POWER_JUMP_V1, result.routeDecision.selectedSource)
        assertEquals(1, result.buildCounts.controlBuilds)
        assertEquals(1, result.buildCounts.experimentalBuilds)
        assertEquals(0, result.buildCounts.thirdBuilds)
    }

    private fun render(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String = buildString {
        fun names(values: Iterable<Any?>) = values.map { it.toString() }.distinct().sorted().joinToString(",")
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        appendLine("TOTAL CASES: ${records.size}")
        appendLine("PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY: ${records.size - generated.size}")
        appendLine("GENERATED CASES: ${generated.size}")
        StimulusProductionProgramSource.entries.forEach { source ->
            appendLine("SOURCE $source: ${generated.count { it.second.routeDecision.selectedSource == source }}")
        }
        StimulusProductionFallbackStage.entries.forEach { stage ->
            appendLine("PRIMARY $stage: ${generated.count { it.second.diagnostics.primaryFallbackStage == stage }}")
        }
        TrainableQuality.entries.forEach { quality ->
            val targets = generated.count { (_, r) -> r.comparison?.targetPlan?.qualityTargets.orEmpty().any { it.quality == quality } }
            val selected = generated.count { (_, r) -> r.comparison?.selectionPlan?.selectedCandidates.orEmpty().any { "QUALITY:${quality.name}" in it.coveredTargetIds } }
            val material = generated.count { (_, r) -> quality in r.diagnostics.scopeResolution?.materialQualities.orEmpty() }
            // Needs/targets alone are never counted as blockers. Require governed material
            // provenance and a failed resolution explicitly identifying unsupported quality.
            val blockers = generated.count { (_, r) ->
                val scope = r.diagnostics.scopeResolution
                r.routeDecision.selectedSource == StimulusProductionProgramSource.CONTROL &&
                    scope?.status == StimulusProductionScopeResolutionStatus.UNSUPPORTED_QUALITY &&
                    "UNSUPPORTED_QUALITY_${quality.name}" in scope.reasonCodes
            }
            appendLine("QUALITY $quality: targets=$targets selected=$selected material=$material unsupportedBlockers=$blockers")
        }
        records.sortedBy { it.first.label }.forEach { (spec, r) ->
            if (r == null) {
                appendLine("CASE ${spec.label}: history=none intent=${spec.intent} goal=${spec.goal} badminton=${spec.badminton} days=${spec.days} minutes=${spec.minutes} equipment=${names(spec.equipment)}")
                appendLine("FINAL PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0")
                return@forEach
            }
            val c = r.comparison
            val d = r.diagnostics
            val scope = d.scopeResolution
            val auth = c?.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            appendLine("CASE ${spec.label}: intent=${spec.intent} goal=${spec.goal} badminton=${spec.badminton} history=${spec.history} days=${spec.days} minutes=${spec.minutes} equipment=${names(spec.equipment)} isolated=${spec.isolateOwner}")
            appendLine("C7 CONTROL stableKeys=${names(c?.controlStableKeys.orEmpty())}")
            appendLine("C7 B4 targets=${names(c?.targetPlan?.qualityTargets.orEmpty().map { "${it.quality}/${it.strategy}/${it.numericAuthority}" })}")
            appendLine("C7 B5 selectedOwners=${names(c?.selectionPlan?.selectedCandidates.orEmpty().map { "${it.stableKey}#${it.selectionRole}" })}")
            appendLine("C7 EXPERIMENTAL stableKeys=${names(c?.experimentalStableKeys.orEmpty())}")
            appendLine("C7 added=${names(c?.addedStableKeys.orEmpty())} removed=${names(c?.removedStableKeys.orEmpty())}")
            appendLine("C7 B6 authorizedOwners=${names(auth.filter { it.authorizedPrescription != null }.mapNotNull { row -> row.owner?.let { "${row.targetId}:${it.stableKey}#${it.selectionRole}" } })}")
            appendLine("C7 routeAfter=${r.routeDecision.selectedSource} B7=${c?.experimentalReadinessAudit?.status} B8=${c?.productionCutoverAuthority?.status} B7reasons=${c?.experimentalReadinessAudit?.reasonCodes?.sorted()} B8reasons=${c?.productionCutoverAuthority?.reasonCodes?.sorted()}")
            appendLine("C7 safety B7shadowOnly=${c?.experimentalReadinessAudit?.shadowOnly} B7productionAuthority=${c?.experimentalReadinessAudit?.productionAuthority} B7winner=${c?.experimentalReadinessAudit?.winner} B8routingActive=${c?.productionCutoverAuthority?.routingActive} B8mutationAuthority=${c?.productionCutoverAuthority?.productionMutationAuthority} B9source=${r.routeDecision.selectedSource} builds=${r.buildCounts.controlBuilds}/${r.buildCounts.experimentalBuilds}/${r.buildCounts.totalBuildInvocations}/${r.buildCounts.thirdBuilds}")
            appendLine("targets=${names(c?.targetPlan?.qualityTargets.orEmpty().map { "${it.quality}/${it.strategy}/${it.numericAuthority}" })}")
            appendLine("material=${names(scope?.materialQualities.orEmpty())} owners=${names(scope?.materialOwnerIdentities.orEmpty())}")
            appendLine("removedOwners=${names(scope?.removedOwnerIdentities.orEmpty())}")
            appendLine("scope=${scope?.scope} status=${scope?.status} reasons=${scope?.reasonCodes} unknown=${scope?.unknownTargetIds} unattributed=${names(scope?.unattributedOwnerIdentities.orEmpty())}")
            appendLine("B6 executable=${names(auth.filter { it.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED && it.status in setOf(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE, StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR) }.mapNotNull { it.quality })}")
            appendLine("B6 resolutions=${names(c?.prescriptionRealizationPlan?.resolutions.orEmpty().map { "${it.quality}/${it.status}/${it.reasonCodes.sorted()}" })}")
            appendLine("B6 authorities=${names(auth.map { "${it.targetId}/${it.owner}/${it.status}/${it.executionAuthority}/${it.reasonCodes.sorted()}" })} conflicts=${names(c?.prescriptionAuthorizationPlan?.conflictingOwners.orEmpty())}")
            appendLine("B6 materialization=${names(c?.prescriptionMaterializationAudits.orEmpty().map { "${it.quality}/${it.owner}/${it.state}/${it.executionAuthority}/${it.reasonCodes.sorted()}" })}")
            appendLine("B7 ${c?.experimentalReadinessAudit?.status} ${c?.experimentalReadinessAudit?.reasonCodes?.sorted()}")
            appendLine("B8 ${c?.productionCutoverAuthority?.scope} ${c?.productionCutoverAuthority?.status} ${c?.productionCutoverAuthority?.reasonCodes?.sorted()}")
            appendLine("B9 ${r.routeDecision.selectedSource} ${r.routeDecision.reasonCodes}")
            appendLine("FINAL ${if (r.routeDecision.productionRoutingActive) "EXPERIMENTAL" else "CONTROL"} primary=${d.primaryFallbackStage} secondary=${d.secondaryReasonCodes} builds=${r.buildCounts}")
        }
    }

    internal suspend fun runCase(
        spec: CoverageSpec,
        seedIncumbentPlacementFixture: Boolean = false,
        verifyRepeatedAcceptedRegeneration: Boolean = false,
        observeCanonicalPlanning: (suspend (CanonicalStimulusPlanningResult) -> Unit)? = null,
        evaluateWithIncumbent: (suspend (PersonalizedProgramPlanningService, PersonalizedPlanningPreflight,
            PersonalizedPlanningAnswers, Map<String, RuntimeExerciseMetadata>, CanonicalIncumbentPlacementIndex) -> StimulusProductionGenerationResult)? = null,
        evaluate: (suspend (PersonalizedProgramPlanningService, PersonalizedPlanningPreflight, PersonalizedPlanningAnswers,
            Map<String, RuntimeExerciseMetadata>) -> StimulusProductionGenerationResult)? = null
    ): StimulusProductionGenerationResult? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.seedIfNeeded()
            db.workoutDao().allEntries().forEach { db.workoutDao().deleteEntryById(it.id) }
            db.initialUserProfileDao().upsert(InitialUserProfile(
                primaryGoal = spec.profileGoal,
                strengthTrainingYears = 2.0, badmintonTrainingYears = if (spec.badminton) 2.0 else 0.0,
                strengthSessionsPerWeek = spec.days.toDouble(), strengthMinutesPerSession = spec.minutes, habitualTrainingIntensity = "NORMAL"
            ))
            val cutoff = LocalDate.of(2026, 9, 20)
            val exercise = requireNotNull(db.exerciseDao().findByStableKey(spec.stableKey)) { "B14.1 missing seeded exercise ${spec.stableKey}" }
            if (spec.history != "none") {
                val days = if (spec.quality == TrainableQuality.STRENGTH) listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L, 49L, 51L) else listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L)
                days.filter { when (spec.history) { "sparse" -> it == 7L; "recent" -> it <= 21L; else -> true } }.forEachIndexed { index, daysAgo ->
                    val entryId = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(daysAgo).toString(), exerciseStableKey = exercise.stableKey, exerciseName = exercise.name, category = exercise.category, sessionStableKey = "b141-${spec.label}-$index"))
                    (1..3).forEach { setIndex ->
                        val reps = if (spec.quality == TrainableQuality.STRENGTH) if (daysAgo >= 42L) 5 else 8 else if (daysAgo <= 23L) 5 else 10
                        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = setIndex, reps = reps, weightKg = 40.0, confirmed = true, rpe = 8.0))
                    }
                }
                if (spec.quality == TrainableQuality.STRENGTH && spec.history in setOf("reviewed", "mixed")) {
                    val posteriorDao = db.strengthPosteriorDao(); val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    if (posteriorDao.revision(revisionKey) == null) posteriorDao.insertRevisionStrict(StrengthModelRevisionPolicy.current(1L, null).copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE, rebuildCompletedAt = 1L)) else posteriorDao.updateRevisionStatus(revisionKey, StrengthModelRevisionPolicy.STATUS_ACTIVE, 1L, null, null)
                    posteriorDao.insertLocalHistoryStrict(listOf(StrengthExercisePerformanceHistoryEntity(revisionKey = revisionKey, eventUuid = "b141-${spec.label}", sessionKey = "b141-${spec.label}", sessionDate = cutoff.minusDays(55).toString(), exerciseStableKey = spec.stableKey, priorLogMean = ln(50.0), priorLogVariance = 0.1, sessionLikelihoodLogMean = null, sessionLikelihoodLogVariance = null, sessionLikelihoodProper = true, innovationResidualLog = null, innovationVariance = null, posteriorLogMean = ln(50.0), posteriorLogVariance = 0.1, posteriorMeanIncrementLog = 0.0, transitionDays = 1L, baselineEstablishedBefore = true, baselineEstablishedAfter = true, proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "B6_TEST", curveVersion = "B6_TEST", rirPolicyVersion = "B6_TEST", evidenceFingerprint = "b141-${spec.label}", createdAt = 1L)))
                }
            }
            if (spec.history == "mixed") {
                val other = requireNotNull(db.exerciseDao().findByStableKey("cable_rear_delt_fly"))
                (1..8).forEach { week ->
                    val entry = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(week * 7L).toString(), exerciseStableKey = other.stableKey, exerciseName = other.name, category = other.category, sessionStableKey = "mixed-${spec.label}-$week"))
                    (1..3).forEach { index -> db.workoutDao().insertSet(WorkoutSet(entryId = entry, setIndex = index, reps = 10, weightKg = 12.5, confirmed = true, rpe = 8.0)) }
                }
            }
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val catalog = field(service, "physicalQualityCatalog") as CanonicalExercisePhysicalQualityCatalog
            val excluded = if (spec.isolateOwner) metadata.keys.filter { key -> key != spec.stableKey && catalog.relations(key).any { it.qualityId == spec.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } }.toSet() else emptySet()
            val goal = spec.goal
            val request = ProgramSkeletonRequest(
                name = "Coverage ${spec.label}", goal = goal, weeklyTrainingDays = spec.days, sessionMinutes = spec.minutes,
                availableEquipment = spec.equipment, excludedExerciseText = "",
                badmintonTransferRatio = if (spec.badminton) 0.5 else 0.0,
                sportStrengthRatio = "AUTO", periodizationType = ProgramPeriodizationType.AUTO,
                durationWeeks = 2, excludedExerciseStableKeys = excluded
            )
            val constraints = PersonalizedGenerationConstraints(
                explicitGoal = goal,
                explicitWeeklyTrainingDays = spec.days.takeIf { spec.explicitWeeklyDays },
                explicitDurationWeeks = if (spec.explicitDuration) 2 else null,
                explicitSessionMinutes = spec.minutes
            )
            val preflight = try {
                repository.preparePersonalizedProgram(request, constraints, cutoff)
            } catch (failure: IllegalArgumentException) {
                // Existing record-based planner precondition, not a CONTROL routing outcome.
                // Catch only this exact no-history boundary; all other errors must fail the test.
                if (spec.history == "none" && failure.message == "기록 기반 계획에 사용할 완료 세트가 없습니다.") return null
                throw failure
            }
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question -> question.id to when (question.id) {
                QUESTION_STRENGTH_INTENT -> spec.intent.name
                QUESTION_BADMINTON_INTENT -> if (spec.badminton) BadmintonPlanningIntent.ENABLED.name else BadmintonPlanningIntent.DISABLED.name
                QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN" else error("Unexpected personalized question: ${question.id}")
            } })
            val existingProgramId = if (seedIncumbentPlacementFixture) {
                seedIncumbentPlacementProgram(db, spec.label)
            } else null
            val incumbentIndex = if (existingProgramId == null) {
                CanonicalIncumbentPlacementIndex.unavailable(CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM)
            } else {
                val programPlanService = field(repository, "programPlanService") as ProgramPlanService
                programPlanService.canonicalIncumbentPlacementIndex(existingProgramId)
            }
            observeCanonicalPlanning?.invoke(service.buildCanonicalStimulusPlanningForPrepared(preflight, answers, metadata))
            val production = evaluateWithIncumbent?.invoke(service, preflight, answers, metadata, incumbentIndex)
                ?: evaluate?.invoke(service, preflight, answers, metadata)
                ?: repository.generatePreparedPersonalizedProgramEvaluation(preflight, answers, existingProgramId = existingProgramId)
            if (verifyRepeatedAcceptedRegeneration) {
                val programId = requireNotNull(existingProgramId) { "C20 idempotence check requires a persisted incumbent source" }
                fun placementFingerprint(result: StimulusProductionGenerationResult): List<String> =
                    requireNotNull(result.comparison).experimental.items
                        .sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
                            ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey,
                            ProgramSkeletonItem::selectionRole))
                        .map { "${it.weekNumber}:${it.exerciseStableKey}#${it.selectionRole}:${it.dayOfWeek}/${it.orderIndex}" }
                val expectedPlacement = placementFingerprint(production)
                var accepted = production
                repeat(2) {
                    assertEquals(programId, repository.saveGeneratedProgram(programId, accepted.program))
                    val refreshed = repository.generatePreparedPersonalizedProgramEvaluation(
                        preflight, answers, existingProgramId = programId
                    )
                    assertEquals(1, refreshed.buildCounts.controlBuilds)
                    assertEquals(1, refreshed.buildCounts.experimentalBuilds)
                    assertEquals(2, refreshed.buildCounts.totalBuildInvocations)
                    assertEquals(0, refreshed.buildCounts.thirdBuilds)
                    assertEquals(
                        "accepted regeneration changed incumbent placement; route=${refreshed.routeDecision}; " +
                            "activation=${refreshed.incumbentPlacementActivationStatus}; " +
                            "preservations=${refreshed.incumbentPlacementPreservations}; " +
                            "details=${refreshed.incumbentPlacementActivationDetails}; " +
                            "shadow=${refreshed.incumbentPlacementShadow?.let { shadow ->
                                listOf(shadow.indexStatus, shadow.rows, shadow.combinedFeasibility, shadow.combinedAnchorSetConflict)
                            }}",
                        expectedPlacement, placementFingerprint(refreshed)
                    )
                    accepted = refreshed
                }
            }
            return production
        } finally { db.close() }
    }

    private suspend fun seedIncumbentPlacementProgram(db: TrainingDatabase, caseId: String): Long {
        val c18 = org.json.JSONObject(repositoryFile("docs/c18-tissue-incumbent-placement-census.json").readText())
        val rows = c18.getJSONArray("placementRows").let { array ->
            (0 until array.length()).map(array::getJSONObject).filter { it.getString("case") == caseId }
        }
        require(rows.isNotEmpty()) { "C20 exact incumbent fixture missing for $caseId" }
        val programId = db.programDao().insertProgram(TrainingProgram(
            stableKey = "c20_fixture_$caseId",
            name = "C20 incumbent fixture",
            durationDays = 14,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
            canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
        ))
        rows.sortedWith(compareBy({ it.getInt("week") }, { it.getJSONObject("owner").getString("stableKey") },
            { it.getJSONObject("owner").getString("selectionRole") })).forEachIndexed { index, row ->
            val owner = row.getJSONObject("owner")
            val exercise = requireNotNull(db.exerciseDao().findByStableKey(owner.getString("stableKey")))
            val from = row.getJSONObject("from")
            db.programDao().insertProgramItem(TrainingProgramItem(
                programId = programId,
                weekNumber = row.getInt("week"),
                dayOfWeek = from.getInt("day"),
                orderIndex = from.getInt("order"),
                exerciseStableKey = owner.getString("stableKey"),
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = 60,
                prescription = "C20 fixture",
                setCount = 2,
                reps = 8,
                selectionRole = owner.getString("selectionRole")
            ))
        }
        return programId
    }

    private fun field(target: Any, name: String): Any = requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))

    private fun repositoryFile(relativePath: String): java.io.File {
        var directory: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val candidate = directory?.resolve(relativePath)
            if (candidate?.isFile == true) return candidate
            directory = directory?.parentFile
        }
        error("Repository fixture not found from ${System.getProperty("user.dir")}: $relativePath")
    }
}
