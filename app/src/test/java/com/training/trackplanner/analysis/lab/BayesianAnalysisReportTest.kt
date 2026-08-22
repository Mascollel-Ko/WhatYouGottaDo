package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictPosteriorSummary
import com.training.trackplanner.analysis.lab.strictbayes.StrictSamplingPolicy
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BayesianAnalysisReportTest {
    @Test
    fun `available detail and TXT use the same canonical bounded report`() {
        val report = BayesianAnalysisReportFactory.available(
            StrictBayesianLabUiState.Available(REQUEST, availableResult(), PREFLIGHT),
            NAMES,
            BUILD,
            GENERATED
        )
        val text = BayesianAnalysisReportFormatter.format(report)

        assertSame(report.sections, report.sections)
        listOf(
            "Result availability: AVAILABLE",
            "MCMC diagnostic classification: LIMITED",
            "R-hat<1.01",
            "ESS>=100.0",
            "MCSE/SD<=0.1",
            "worstRhat=1.080000",
            "minBulkESS=42.000000",
            "REMOVE_CONTROL",
            "modelChanged=YES",
            "samplingChanged=NO",
            "App version: 0.5.0.38 (500038)",
            "Git/build commit SHA: abc123",
            "prepared-fp",
            "row-fp",
            "scaling-fp"
        ).forEach { expected -> assertTrue("missing $expected", expected in text) }
        assertFalse("raw set history must not be exported", "workoutSets" in text)
        assertFalse("raw workout history must not be exported", "raw workout history" in text)
    }

    @Test
    fun `unavailable report carries terminal blocker and attempted adjustments`() {
        val trace = adjustmentTrace()
        val report = BayesianAnalysisReportFactory.unavailable(
            REQUEST,
            StrictFailureDiagnostics(
                code = StrictLabFailureCode.NO_TARGET_VARIATION,
                stage = StrictFailureStage.PHASE_A,
                primaryReason = "required Y has no identifiable variation",
                originalControls = listOf(CONTROL.value),
                effectiveControls = emptyList(),
                snapshotFingerprint = "snapshot-fp",
                attemptedCommonRowsByPmax = mapOf(2 to 2, 1 to 3),
                diagnosticId = "SB-REPORT"
            ),
            trace,
            NAMES,
            BUILD,
            GENERATED
        )
        val text = BayesianAnalysisReportFormatter.format(report)

        listOf(
            "Result availability: UNAVAILABLE",
            "MCMC diagnostic classification: NOT_APPLICABLE",
            "Terminal blocker: NO_TARGET_VARIATION",
            "Controls removed: ${CONTROL.value}",
            "Common rows at Pmax=1: 3",
            "No mathematically and semantically valid finite result remained"
        ).forEach { expected -> assertTrue("missing $expected", expected in text) }
    }

    private fun availableResult(): StrictBayesianLabResult {
        val posterior = StrictPosteriorSummary(0.1, 0.1, -0.2, 0.4, 1.08, 42.0, 39.0, 0.24)
        val assessment = StrictSamplingAssessment(
            StrictSamplingDiagnosticClassification.LIMITED,
            StrictSamplingPolicy.appRuntime().snapshot("STRICT"),
            StrictSamplingPolicy.relaxedAppRuntime().snapshot("RELAXED"),
            listOf(
                StrictSamplingDiagnosticWindow(
                    StrictFailureStage.PRODUCTION,
                    10_000,
                    1.08,
                    "response[fatigue,1]",
                    42.0,
                    39.0,
                    0.24,
                    false,
                    false
                )
            ),
            4_000,
            10_000,
            false,
            false,
            true
        )
        return StrictBayesianLabResult(
            request = REQUEST,
            responses = listOf(StrictLabResponse(Y, "피로", listOf(StrictLabResponsePoint(1, 0.1, -0.2, 0.4, posterior)))),
            officialLagProbability = mapOf(1 to 0.7, 2 to 0.3),
            simplificationDiagnostics = emptyList(),
            summary = "제한적 결과",
            preparedInputFingerprint = "prepared-fp",
            posteriorFingerprint = "posterior-fp",
            effectiveRequest = REQUEST.copy(controls = emptyList()),
            samplingAssessment = assessment,
            adjustmentTrace = adjustmentTrace(),
            effectiveCandidates = listOf(X.value),
            representationDecisions = listOf("${X.value}: LEVEL"),
            closedWeeks = 12,
            commonRows = 7,
            selectedPmax = 1,
            rowPlanFingerprint = "row-fp",
            scalingFingerprint = "scaling-fp",
            designFingerprint = "design-fp"
        )
    }

    private fun adjustmentTrace() = AnalysisAdjustmentTrace(
        listOf(
            AnalysisAdjustmentEvent(
                1,
                AnalysisAdjustmentType.REMOVE_CONTROL,
                "CONTROL_PREFIT_UNAVAILABLE",
                CONTROL.value,
                "control has insufficient usable rows",
                "control removed and Phase A rebuilt",
                "included",
                "removed",
                "deterministic prefit adjustment",
                modelStructureChanged = true,
                samplingPolicyChanged = false,
                beforeFingerprint = "before-fp",
                afterFingerprint = "after-fp"
            )
        )
    )

    private companion object {
        val X = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
        val CONTROL = AnalysisFeatureKey.metric(TrendMetricId.SLEEP_HOURS)
        val REQUEST = StrictLabAnalysisRequest(X, listOf(Y), listOf(CONTROL), 2)
        val PREFLIGHT = StrictLabPreflight("snapshot-fp", null, null, 12, emptyList(), emptyList())
        val NAMES = mapOf(X to "훈련 부하", Y to "피로", CONTROL to "수면")
        val BUILD = BayesianAnalysisReportBuildIdentity("0.5.0.38", 500038, "abc123")
        val GENERATED: Instant = Instant.parse("2026-08-22T01:02:03Z")
    }
}
