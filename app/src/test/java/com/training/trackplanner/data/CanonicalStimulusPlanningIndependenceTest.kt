package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the existing production corpus inputs; no route or physiological policy is mocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalStimulusPlanningIndependenceTest {
    private fun spec(label: String, h: Boolean = false, mixed: Boolean = false,
        badminton: Boolean = false, history: String = "reviewed") =
        StimulusProductionCoverageAuditTest.CoverageSpec(
            label, if (h) TrainableQuality.HYPERTROPHY else TrainableQuality.STRENGTH,
            if (h) "cable_rear_delt_fly" else "barbell_back_squat",
            if (mixed || badminton) "MIXED" else if (h) "HYPERTROPHY_PHYSIQUE" else "STRENGTH_GAIN",
            if (badminton) ProgramGoal.BADMINTON_SUPPORT else if (mixed) ProgramGoal.FUNCTIONAL_CONDITIONING
                else if (h) ProgramGoal.BODYBUILDING else ProgramGoal.STRENGTH,
            if (mixed || badminton) StrengthIntent.MIXED else if (h) StrengthIntent.HYPERTROPHY_PRIORITY else StrengthIntent.STRENGTH_PRIORITY,
            badminton, history, 3, 60, emptySet(), !mixed && !badminton
        )

    @Test fun strengthParity() = parity(spec("c1_strength"))
    @Test fun hypertrophyParity() = parity(spec("c1_hypertrophy", h = true))
    @Test fun combinedParity() = parity(spec("c1_combined", mixed = true, history = "mixed"))
    @Test fun badmintonPerformanceParity() = parity(spec("c1_badminton", badminton = true))
    @Test fun sparseHistoryParity() = parity(spec("c1_sparse", history = "sparse"))
    @Test fun reviewedEightWeekHistoryParity() = parity(spec("c1_eight_week", history = "mixed"))

    private fun parity(spec: StimulusProductionCoverageAuditTest.CoverageSpec) = runBlocking {
        StimulusProductionCoverageAuditTest().runCase(spec) { service, preflight, answers, metadata ->
            // No program has been constructed by this seam, and no CONTROL argument exists.
            val independent = service.buildCanonicalStimulusPlanningForPrepared(preflight, answers, metadata)
            assertNull(independent.controlProgramAudit)
            assertNull(independent.athleteStimulusNeedProfile.finalAudit)
            assertNull(independent.athleteStimulusNeedProfile.stimulusTargetPlanShadow)
            var computations = 0
            val result = service.generatePreparedProduction(preflight, answers, metadata,
                canonicalPlanningComputation = { snapshot, state, legacyDose ->
                    computations++
                    // Frozen pre-C1 producer chain from 4f670674. Compare complete data classes,
                    // including evidence, reasons, unresolved markers, authority and target ranges.
                    val b1 = AthleteStimulusNeedEngine().analyze(snapshot, state)
                    val b2 = LedgerBackedQualityDoseHistoryAnalyzer().analyze(snapshot, state, legacyDose)
                    val b3 = StimulusTrainingDecisionPortfolioEngine().build(b1, b2)
                    val b4 = StimulusTargetPlanEngine().build(b3, b2)
                    val actual = service.buildCanonicalStimulusPlanningResult(snapshot, state, legacyDose)
                    assertEquals("${spec.label}: B1", b1, actual.athleteStimulusNeedProfile)
                    assertEquals("${spec.label}: B2", b2, actual.qualityDoseHistory)
                    assertEquals("${spec.label}: B3", b3, actual.decisionPortfolio)
                    assertEquals("${spec.label}: B4", b4, actual.targetPlan)
                    assertEquals("standalone and production inputs", independent, actual)
                    actual
                })
            assertEquals(1, computations)
            val comparison = requireNotNull(result.comparison)
            val mirror = requireNotNull(comparison.control.personalizedDecision?.athleteStimulusNeedProfile)
            assertEquals(independent.athleteStimulusNeedProfile, mirror.copy(finalAudit = null,
                qualityDoseHistoryShadow = null, trainingDecisionPortfolioShadow = null, stimulusTargetPlanShadow = null))
            assertEquals(independent.qualityDoseHistory, mirror.qualityDoseHistoryShadow)
            assertEquals(independent.decisionPortfolio, mirror.trainingDecisionPortfolioShadow?.copy(comparison = null))
            assertEquals(independent.targetPlan, mirror.stimulusTargetPlanShadow?.copy(legacyComparison = null, controlProgramAudit = null))
            assertEquals(independent.targetPlan, comparison.targetPlan)
            assertNotNull(mirror.finalAudit)
            assertNotNull(mirror.trainingDecisionPortfolioShadow?.comparison)
            assertNotNull(mirror.stimulusTargetPlanShadow?.legacyComparison)
            assertNotNull(mirror.stimulusTargetPlanShadow?.controlProgramAudit)
            assertBuilds(result, 1, 1)
            result
        }
        Unit
    }

    @Test fun missingPersonalizedDecisionDoesNotAffectB5B6() = hostIndependence("decision")
    @Test fun missingNeedProfileDoesNotAffectB5B6() = hostIndependence("needs")
    @Test fun missingTargetPlanDoesNotAffectB5B6() = hostIndependence("target")
    @Test fun explicitHypertrophyWinsOverStrengthMirror() = hostIndependence("mismatch")

    private fun hostIndependence(remove: String) = runBlocking {
        StimulusProductionCoverageAuditTest().runCase(spec("c1_host_$remove", h = true)) { service, preflight, answers, metadata ->
            var canonical: CanonicalStimulusPlanningResult? = null
            val production = service.generatePreparedProduction(preflight, answers, metadata,
                canonicalPlanningComputation = { snapshot, state, dose ->
                    service.buildCanonicalStimulusPlanningResult(snapshot, state, dose).also { canonical = it }
                })
            val baseline = requireNotNull(production.comparison)
            val explicit = requireNotNull(canonical).copy(controlProgramAudit = baseline.controlAudit)
            val control = baseline.control
            val decision = requireNotNull(control.personalizedDecision)
            val needs = requireNotNull(decision.athleteStimulusNeedProfile)
            val hOnly = explicit.copy(targetPlan = explicit.targetPlan.copy(
                qualityTargets = explicit.targetPlan.qualityTargets.filter { it.quality == TrainableQuality.HYPERTROPHY },
                taskTargets = emptyList()))
            assertEquals(listOf(TrainableQuality.HYPERTROPHY), hOnly.targetPlan.qualityTargets.map { it.quality })
            val altered = control.copy(personalizedDecision = when (remove) {
                "decision" -> null
                "needs" -> decision.copy(athleteStimulusNeedProfile = null)
                "target" -> decision.copy(athleteStimulusNeedProfile = needs.copy(stimulusTargetPlanShadow = null))
                else -> decision.copy(athleteStimulusNeedProfile = needs.copy(stimulusTargetPlanShadow =
                    hOnly.targetPlan.copy(qualityTargets = hOnly.targetPlan.qualityTargets.map { it.copy(quality = TrainableQuality.STRENGTH) })))
            })
            val input = if (remove == "mismatch") hOnly else explicit
            val counts = MutableStimulusProductionBuildCounts()
            val actual = service.generatePreparedStimulusPrescriptionMaterializationComparison(
                preflight, answers, metadata, canonicalPlanning = input, controlOverride = altered, productionBuildCounts = counts)
            assertSame(input.targetPlan, actual.targetPlan)
            assertTrue(actual.selectionPlan.traces.any { it.targetId == "QUALITY:HYPERTROPHY" })
            if (remove == "mismatch") {
                assertEquals(listOf("QUALITY:HYPERTROPHY"), actual.selectionPlan.traces.map { it.targetId })
            }
            assertTrue(requireNotNull(actual.prescriptionAuthorizationPlan).authorizations.any {
                it.quality == TrainableQuality.HYPERTROPHY && it.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
            })
            if (remove != "mismatch") {
                assertEquals(baseline.selectionPlan, actual.selectionPlan)
                assertEquals(baseline.prescriptionAuthorizationPlan, actual.prescriptionAuthorizationPlan)
                assertEquals(personalizedProgramFingerprint(baseline.experimental.request, baseline.experimental.items),
                    personalizedProgramFingerprint(actual.experimental.request, actual.experimental.items))
            }
            assertEquals(0, counts.snapshot().controlBuilds)
            assertEquals(1, counts.snapshot().experimentalBuilds)
            assertEquals(0, counts.snapshot().thirdBuilds)
            production
        }
        Unit
    }

    @Test fun productionComputesCanonicalOnceAndCompletesOnce() = runBlocking {
        StimulusProductionCoverageAuditTest().runCase(spec("c1_once")) { service, preflight, answers, metadata ->
            var computations = 0
            val updates = mutableListOf<Int>()
            val result = service.generatePreparedProduction(preflight, answers, metadata, reporter(updates),
                canonicalPlanningComputation = { snapshot, state, dose ->
                    assertEquals("CONTROL completes its mapped build before canonical evaluation", 45, updates.last())
                    computations++
                    service.buildCanonicalStimulusPlanningResult(snapshot, state, dose)
                })
            assertEquals(1, computations)
            assertBuilds(result, 1, 1)
            assertProgress(updates, completed = true)
            result
        }
        Unit
    }

    @Test fun expectedIndependentPlanningFailureFallsBackAndCompletes() = runBlocking {
        StimulusProductionCoverageAuditTest().runCase(spec("c1_expected_failure")) { service, preflight, answers, metadata ->
            val updates = mutableListOf<Int>()
            var attempts = 0
            val result = service.generatePreparedProduction(preflight, answers, metadata, reporter(updates),
                canonicalPlanningComputation = { _, _, _ ->
                    attempts++
                    throw StimulusCanonicalEvaluationFailure(StimulusCanonicalEvaluationFailureReason.FINAL_CANONICAL_VALIDATION, "C1_TEST_FAILURE")
                })
            assertEquals(1, attempts)
            assertEquals(StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
            assertFalse(result.routeDecision.productionRoutingActive)
            assertNull(result.comparison)
            assertTrue(result.program.items.isNotEmpty())
            assertEquals("B9_EXPECTED_CANONICAL_EVALUATION_FAILURE", result.upstreamFailureReason)
            assertTrue("C1_TEST_FAILURE" in result.diagnostics.secondaryReasonCodes)
            assertEquals(StimulusProductionFallbackStage.UPSTREAM_EVALUATION_FAILURE, result.diagnostics.primaryFallbackStage)
            assertBuilds(result, 1, 0)
            assertProgress(updates, completed = true)
            result
        }
        Unit
    }

    @Test fun independentPlanningCancellationPropagates() = propagates(CancellationException("C1_CANCEL"))
    @Test fun independentPlanningIllegalStatePropagates() = propagates(IllegalStateException("C1_STATE"))
    @Test fun independentPlanningIllegalArgumentPropagates() = propagates(IllegalArgumentException("C1_ARGUMENT"))

    private fun propagates(failure: RuntimeException) = runBlocking {
        val updates = mutableListOf<Int>()
        var caught: Throwable? = null
        try {
            StimulusProductionCoverageAuditTest().runCase(spec("c1_propagate")) { service, preflight, answers, metadata ->
                service.generatePreparedProduction(preflight, answers, metadata, reporter(updates),
                    canonicalPlanningComputation = { _, _, _ -> throw failure })
            }
        } catch (actual: Throwable) { caught = actual }
        assertSame(failure, caught)
        assertProgress(updates, completed = false)
    }

    private fun reporter(updates: MutableList<Int>) = object : PersonalizedPlannerProgressReporter {
        override fun report(stage: PersonalizedPlannerStage) = Unit
        override fun report(update: PersonalizedPlannerProgress) { updates += update.percent }
    }

    private fun assertProgress(updates: List<Int>, completed: Boolean) {
        assertTrue(updates.isNotEmpty())
        assertEquals(updates.sorted(), updates)
        assertEquals(if (completed) 1 else 0, updates.count { it == 100 })
        if (completed) assertEquals(100, updates.last())
    }

    private fun assertBuilds(result: StimulusProductionGenerationResult, control: Int, experimental: Int) {
        assertEquals(control, result.buildCounts.controlBuilds)
        assertEquals(experimental, result.buildCounts.experimentalBuilds)
        assertEquals(control + experimental, result.buildCounts.totalBuildInvocations)
        assertEquals(0, result.buildCounts.thirdBuilds)
    }
}
