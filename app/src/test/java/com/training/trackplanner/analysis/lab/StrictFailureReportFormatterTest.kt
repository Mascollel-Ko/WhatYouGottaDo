package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictFailureReportFormatterTest {
    @Test
    fun `report contains bounded structured diagnostics without raw workout history`() {
        val request = StrictLabAnalysisRequest(X, listOf(Y), listOf(CONTROL), 2)
        val failure = StrictFailureDiagnostics(
            code = StrictLabFailureCode.MCMC_CONVERGENCE_FAILED,
            stage = StrictFailureStage.PRODUCTION,
            primaryReason = "posterior reliability failed",
            affectedFeatureOrSource = "beta:fatigue",
            availableClosedWeeks = 12,
            usableCommonRows = 7,
            attemptedLags = listOf(1, 2),
            selectedPmax = 2,
            attemptedSimplifications = listOf("Pmax=2 has 2 common rows"),
            observations = listOf(
                StrictDiagnosticObservation("beta:fatigue", "Rhat=1.07", "Rhat<1.05", false),
                StrictDiagnosticObservation("beta:fatigue.ESS", "bulk=42 tail=39", "ESS>=50", false),
                StrictDiagnosticObservation("beta:fatigue.MCSE", "MCSE/SD=0.24", "MCSE/SD<=0.20", false)
            ),
            chainsAttempted = 4,
            warmupDrawsPerChain = 750,
            productionDrawsPerChain = 1500,
            preparedInputFingerprint = "prepared-fp",
            samplingPolicyFingerprint = "sampling-policy-fp",
            analysisMode = StrictLabAnalysisMode.RELAXED,
            samplingReliabilityMode = StrictSamplingReliabilityMode.RELAXED,
            retryAttempt = 1,
            availableRelaxationRoutes = emptySet(),
            attemptedRelaxationRoutes = setOf(StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS),
            appliedRelaxationRoutes = setOf(StrictRelaxationRoute.REDUCE_CONTROLS_FOR_COMMON_ROWS),
            originalControls = listOf(CONTROL.value),
            effectiveControls = emptyList(),
            representationOverrides = listOf("fatigue: INCONCLUSIVE -> semantic LEVEL"),
            attemptedCommonRowsByPmax = mapOf(2 to 2, 1 to 7),
            snapshotFingerprint = "snapshot-fp",
            preparationPolicyFingerprint = "preparation-policy-fp",
            effectivePlanFingerprint = "effective-plan-fp",
            samplingIdentityFingerprint = "sampling-identity-fp",
            technicalDetails = listOf("bounded diagnostic detail"),
            diagnosticId = "SB-REPORT"
        )

        val report = StrictFailureReportFormatter.format(
            request,
            failure,
            mapOf(X to "훈련 부하", Y to "피로", CONTROL to "수면"),
            StrictFailureReportBuildIdentity("0.5.0.38", 500038, "abc123"),
            Instant.parse("2026-08-22T01:02:03Z")
        )

        listOf(
            "App version name: 0.5.0.38",
            "App version code: 500038",
            "Git/build commit SHA: abc123",
            "ID: SB-REPORT",
            "Failure code: MCMC_CONVERGENCE_FAILED",
            "Failure stage: PRODUCTION",
            "Analysis mode: RELAXED",
            X.value,
            Y.value,
            "Controls removed: ${CONTROL.value}",
            "Common rows at Pmax=1: 7",
            "Planning attempts:",
            "Pmax=2 has 2 common rows",
            "Rhat=1.07",
            "ESS>=50",
            "MCSE/SD=0.24",
            "REDUCE_CONTROLS_FOR_COMMON_ROWS",
            "snapshot-fp",
            "effective-plan-fp",
            "sampling-identity-fp"
        ).forEach { expected -> assertTrue("missing $expected", expected in report) }
        assertFalse("raw set history must not be exported", "workoutSets" in report)
        assertFalse("raw set history must not be exported", "raw workout history" in report)
    }

    private companion object {
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
        val CONTROL: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.SLEEP_HOURS)
    }
}
