package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

internal data class BayesianAnalysisReportBuildIdentity(
    val versionName: String,
    val versionCode: Int,
    val gitCommitSha: String
)

internal data class BayesianAnalysisReportSection(
    val title: String,
    val lines: List<String>
)

internal data class BayesianAnalysisReport(
    val analysisId: String,
    val generatedAt: Instant,
    val availability: StrictAnalysisAvailability,
    val samplingClassification: StrictSamplingDiagnosticClassification,
    val adjustmentTrace: AnalysisAdjustmentTrace,
    val sections: List<BayesianAnalysisReportSection>
) {
    val exportFileName: String
        get() = "whatyougottado_analysis_${LocalDate.now()}_${analysisId}.txt"
}

internal object BayesianAnalysisReportFactory {
    fun available(
        state: StrictBayesianLabUiState.Available,
        displayNames: Map<AnalysisFeatureKey, String>,
        build: BayesianAnalysisReportBuildIdentity,
        generatedAt: Instant = Instant.now()
    ): BayesianAnalysisReport {
        val result = state.result
        val assessment = requireNotNull(result.samplingAssessment)
        val analysisId = "BA-${result.resultFingerprint.take(12).uppercase()}"
        return BayesianAnalysisReport(
            analysisId = analysisId,
            generatedAt = generatedAt,
            availability = StrictAnalysisAvailability.AVAILABLE,
            samplingClassification = assessment.classification,
            adjustmentTrace = result.adjustmentTrace,
            sections = listOf(
                buildSection(build, analysisId, generatedAt, state.preflight.snapshotFingerprint),
                BayesianAnalysisReportSection(
                    "FINAL STATUS",
                    listOf(
                        "Result availability: AVAILABLE",
                        "MCMC diagnostic classification: ${assessment.classification.name}",
                        "Model adjustments: ${result.adjustmentTrace.modelAdjustmentCount}",
                        "Sampling adjustments: ${result.adjustmentTrace.samplingAdjustmentCount}"
                    )
                ),
                requestSection("ORIGINAL REQUEST", result.request, displayNames),
                BayesianAnalysisReportSection(
                    "EFFECTIVE MODEL",
                    requestLines(result.effectiveRequest, displayNames) + listOf(
                        "Removed controls: ${result.request.controls.filterNot { it in result.effectiveRequest.controls }.namesOrNone()}",
                        "Candidates: ${result.effectiveCandidates.joinToString().ifBlank { "none" }}",
                        "Representations:"
                    ) + result.representationDecisions.map { "  - $it" } +
                        "Effective Pmax: ${result.selectedPmax ?: "unknown"}"
                ),
                BayesianAnalysisReportSection(
                    "DATA PREPARATION",
                    listOf(
                        "CLOSED weeks: ${result.closedWeeks}",
                        "CLOSED-week span: ${result.availableFrom ?: "unknown"} to ${result.availableUntil ?: "unknown"}",
                        "Selected contiguous/common rows: ${result.commonRows}",
                        "Row-plan fingerprint: ${result.rowPlanFingerprint}",
                        "Scaling fingerprint: ${result.scalingFingerprint}",
                        "Prepared-input fingerprint: ${result.preparedInputFingerprint}",
                        "Design fingerprint: ${result.designFingerprint}"
                    )
                ),
                adjustmentSection(result.adjustmentTrace),
                samplingPolicySection(assessment),
                samplingDiagnosticsSection(assessment),
                BayesianAnalysisReportSection(
                    "RESULT SUMMARY",
                    buildList {
                        add(result.summary)
                        add("Official lag probability: ${result.officialLagProbability.toSortedMap().entries.joinToString { "${it.key}w=${value(it.value)}" }}")
                        result.responses.forEach { response ->
                            response.points.forEach { point ->
                                add("${response.displayName} ${point.horizonWeeks}w: median=${value(point.estimate)}, 80%=${value(point.low80)}..${value(point.high80)}")
                            }
                        }
                        result.sourceSummaries.forEach { source ->
                            add(
                                "Source ${source.sourceId}: median=${value(source.contribution.median)}, " +
                                    "80%=${value(source.contribution.lower80)}..${value(source.contribution.upper80)}"
                            )
                        }
                    }
                ),
                BayesianAnalysisReportSection(
                    "PROVENANCE",
                    listOf(
                        "Preparation policy: ${result.preparationPolicyFingerprint}",
                        "Effective plan: ${result.effectivePlanFingerprint}",
                        "Sampling policy: ${result.samplingPolicyFingerprint}",
                        "Sampling identity: ${result.samplingIdentityFingerprint}",
                        "Posterior: ${result.posteriorFingerprint}",
                        "Adjustment trace: ${result.adjustmentTrace.fingerprint}"
                    )
                )
            )
        )
    }

    fun unavailable(
        state: StrictBayesianLabUiState.Unavailable,
        displayNames: Map<AnalysisFeatureKey, String>,
        build: BayesianAnalysisReportBuildIdentity,
        generatedAt: Instant = Instant.now()
    ): BayesianAnalysisReport = unavailable(
        state.request,
        state.failure,
        state.adjustmentTrace,
        displayNames,
        build,
        generatedAt
    )

    fun unavailable(
        request: StrictLabAnalysisRequest,
        failure: StrictFailureDiagnostics,
        adjustmentTrace: AnalysisAdjustmentTrace,
        displayNames: Map<AnalysisFeatureKey, String>,
        build: BayesianAnalysisReportBuildIdentity,
        generatedAt: Instant = Instant.now()
    ): BayesianAnalysisReport {
        val analysisId = failure.diagnosticId
        return BayesianAnalysisReport(
            analysisId = analysisId,
            generatedAt = generatedAt,
            availability = StrictAnalysisAvailability.UNAVAILABLE,
            samplingClassification = StrictSamplingDiagnosticClassification.NOT_APPLICABLE,
            adjustmentTrace = adjustmentTrace,
            sections = listOf(
                buildSection(build, analysisId, generatedAt, failure.snapshotFingerprint),
                BayesianAnalysisReportSection(
                    "FINAL STATUS",
                    listOf(
                        "Result availability: UNAVAILABLE",
                        "MCMC diagnostic classification: NOT_APPLICABLE",
                        "Terminal blocker: ${failure.code.name}",
                        "Reason: ${failure.primaryReason}",
                        "Model adjustments attempted: ${adjustmentTrace.modelAdjustmentCount}",
                        "Sampling adjustments attempted: ${adjustmentTrace.samplingAdjustmentCount}"
                    )
                ),
                requestSection("ORIGINAL REQUEST", request, displayNames),
                BayesianAnalysisReportSection(
                    "EFFECTIVE MODEL",
                    listOf(
                        "X and Y: unchanged",
                        "Controls retained: ${failure.effectiveControls.joinToString().ifBlank { "none" }}",
                        "Controls removed: ${failure.originalControls.filterNot { it in failure.effectiveControls }.joinToString().ifBlank { "none" }}",
                        "Effective Pmax: ${failure.selectedPmax ?: "unavailable"}"
                    )
                ),
                BayesianAnalysisReportSection(
                    "DATA PREPARATION",
                    buildList {
                        add("CLOSED weeks: ${failure.availableClosedWeeks ?: "unknown"}")
                        add("Usable/common rows: ${failure.usableCommonRows ?: "unavailable"}")
                        add("Attempted lags: ${failure.attemptedLags.joinToString().ifBlank { "none" }}")
                        failure.attemptedCommonRowsByPmax.toSortedMap(reverseOrder()).forEach { (pmax, rows) ->
                            add("Common rows at Pmax=$pmax: $rows")
                        }
                    }
                ),
                adjustmentSection(adjustmentTrace),
                BayesianAnalysisReportSection(
                    "SAMPLING POLICY",
                    listOf("Sampling did not produce a valid finite result; policy classification is NOT_APPLICABLE.")
                ),
                BayesianAnalysisReportSection(
                    "SAMPLING DIAGNOSTICS",
                    failure.observations.map(StrictDiagnosticObservation::displayLine).ifEmpty { listOf("not applicable") }
                ),
                BayesianAnalysisReportSection(
                    "RESULT SUMMARY",
                    listOf(
                        "No mathematically and semantically valid finite result remained after approved adjustments.",
                        "Terminal blocker: ${failure.code.name}",
                        "Details: ${failure.technicalDetails.joinToString(" | ").ifBlank { "none" }}"
                    )
                ),
                BayesianAnalysisReportSection(
                    "PROVENANCE",
                    listOf(
                        "Snapshot: ${failure.snapshotFingerprint ?: "unknown"}",
                        "Prepared input: ${failure.preparedInputFingerprint ?: "unavailable"}",
                        "Preparation policy: ${failure.preparationPolicyFingerprint ?: "unavailable"}",
                        "Effective plan: ${failure.effectivePlanFingerprint ?: "unavailable"}",
                        "Sampling policy: ${failure.samplingPolicyFingerprint ?: "not applicable"}",
                        "Sampling identity: ${failure.samplingIdentityFingerprint ?: "not applicable"}",
                        "Adjustment trace: ${adjustmentTrace.fingerprint}"
                    )
                )
            )
        )
    }

    private fun buildSection(
        build: BayesianAnalysisReportBuildIdentity,
        analysisId: String,
        generatedAt: Instant,
        snapshotFingerprint: String?
    ): BayesianAnalysisReportSection = BayesianAnalysisReportSection(
        "BUILD",
        listOf(
            "Generated: $generatedAt",
            "App version: ${build.versionName} (${build.versionCode})",
            "Git/build commit SHA: ${build.gitCommitSha}",
            "Analysis ID: $analysisId",
            "Snapshot fingerprint: ${snapshotFingerprint ?: "unknown"}"
        )
    )

    private fun requestSection(
        title: String,
        request: StrictLabAnalysisRequest,
        displayNames: Map<AnalysisFeatureKey, String>
    ): BayesianAnalysisReportSection = BayesianAnalysisReportSection(title, requestLines(request, displayNames))

    private fun requestLines(
        request: StrictLabAnalysisRequest,
        displayNames: Map<AnalysisFeatureKey, String>
    ): List<String> = listOf(
        "X: ${featureLine(request.xFeature, displayNames)}",
        "Y: ${request.yFeatures.joinToString { featureLine(it, displayNames) }}",
        "Controls: ${request.controls.joinToString { featureLine(it, displayNames) }.ifBlank { "none" }}",
        "Horizon: ${request.requestedHorizon} week(s)"
    )

    private fun adjustmentSection(trace: AnalysisAdjustmentTrace): BayesianAnalysisReportSection =
        BayesianAnalysisReportSection(
            "AUTOMATIC ADJUSTMENT LOG",
            trace.events.flatMap { event ->
                listOf(
                    "${event.sequence}. ${event.type.name}: ${event.action}",
                    "   trigger=${event.triggerCode}; affected=${event.affected ?: "none"}; observed=${event.observedCondition}",
                    "   before=${event.beforeValue ?: "n/a"}; after=${event.afterValue ?: "n/a"}",
                    "   modelChanged=${yesNo(event.modelStructureChanged)}; samplingChanged=${yesNo(event.samplingPolicyChanged)}",
                    "   reason=${event.explanation}; fingerprints=${event.beforeFingerprint ?: "n/a"} -> ${event.afterFingerprint ?: "n/a"}"
                )
            }.ifEmpty { listOf("none") }
        )

    private fun samplingPolicySection(assessment: StrictSamplingAssessment): BayesianAnalysisReportSection =
        BayesianAnalysisReportSection(
            "SAMPLING POLICY",
            listOf(assessment.strictPolicy, assessment.relaxedPolicy).flatMap { policy ->
                listOf(
                    "${policy.identity}: chains=${policy.chains}; R-hat<${policy.maximumRhat}; ESS>=${policy.minimumEss}; MCSE/SD<=${policy.maximumMcseToSd}",
                    "${policy.identity}: stabilizationCap=${policy.stabilizationCap}; passes=${policy.consecutiveStabilizationPasses}; productionMax=${policy.productionMaximum}; precisionMax=${policy.precisionExtensionMaximum}",
                    "${policy.identity}: fingerprint=${policy.fingerprint}"
                )
            }
        )

    private fun samplingDiagnosticsSection(assessment: StrictSamplingAssessment): BayesianAnalysisReportSection =
        BayesianAnalysisReportSection(
            "SAMPLING DIAGNOSTICS",
            buildList {
                add("Classification: ${assessment.classification.name}")
                add("Warmup draws/chain: ${assessment.stabilizationDrawsPerChain}")
                add("Production draws/chain: ${assessment.productionDrawsPerChain}")
                add("STRICT criteria: ${if (assessment.strictCriteriaMet) "PASS" else "MISS"}")
                add("RELAXED criteria: ${if (assessment.relaxedCriteriaMet) "PASS" else "MISS"}")
                add("Lag mixing concern: ${yesNo(assessment.lagMixingConcern)}")
                assessment.recentWindows.forEachIndexed { index, window ->
                    add(
                        "Window ${index + 1}: stage=${window.stage}; draws=${window.drawsPerChain}; " +
                            "worstRhat=${value(window.worstRhat)} (${window.worstRhatFunctional}); " +
                            "minBulkESS=${value(window.minimumBulkEss)}; minTailESS=${value(window.minimumTailEss)}; " +
                            "worstMCSE/SD=${value(window.worstMcseToSd)}; strict=${if (window.strictCriteriaMet) "PASS" else "MISS"}; " +
                            "relaxed=${if (window.relaxedCriteriaMet) "PASS" else "MISS"}"
                    )
                }
            }
        )

    private fun featureLine(
        feature: AnalysisFeatureKey,
        displayNames: Map<AnalysisFeatureKey, String>
    ): String = "${feature.value} (${displayNames[feature] ?: "display name unavailable"})"

    private fun List<AnalysisFeatureKey>.namesOrNone(): String = joinToString { it.value }.ifBlank { "none" }
    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"
    private fun value(value: Double): String = String.format(Locale.US, "%.6f", value)
}

internal object BayesianAnalysisReportFormatter {
    fun format(report: BayesianAnalysisReport): String = buildString {
        appendLine("WHATYOUGOTTADO BAYESIAN ANALYSIS REPORT")
        report.sections.forEach { section ->
            appendLine()
            appendLine(section.title)
            section.lines.forEach { appendLine("- $it") }
        }
    }.trimEnd() + "\n"
}

// Compatibility for callers/tests from the former failure-only report API.
internal typealias StrictFailureReportBuildIdentity = BayesianAnalysisReportBuildIdentity

internal object StrictFailureReportFormatter {
    fun format(
        request: StrictLabAnalysisRequest,
        failure: StrictFailureDiagnostics,
        displayNames: Map<AnalysisFeatureKey, String>,
        build: StrictFailureReportBuildIdentity,
        generatedAt: Instant = Instant.now()
    ): String = BayesianAnalysisReportFormatter.format(
        BayesianAnalysisReportFactory.unavailable(
            request,
            failure,
            AnalysisAdjustmentTrace(),
            displayNames,
            build,
            generatedAt
        )
    )
}
