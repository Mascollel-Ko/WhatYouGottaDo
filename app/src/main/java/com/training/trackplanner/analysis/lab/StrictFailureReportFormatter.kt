package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import java.time.Instant

internal data class StrictFailureReportBuildIdentity(
    val versionName: String,
    val versionCode: Int,
    val gitCommitSha: String
)

internal object StrictFailureReportFormatter {
    fun format(
        request: StrictLabAnalysisRequest,
        failure: StrictFailureDiagnostics,
        displayNames: Map<AnalysisFeatureKey, String>,
        build: StrictFailureReportBuildIdentity,
        generatedAt: Instant = Instant.now()
    ): String = buildString {
        appendLine("WhatYouGottaDo Bayesian Failure Report")
        appendLine()
        appendLine("App version name: ${build.versionName}")
        appendLine("App version code: ${build.versionCode}")
        appendLine("Git/build commit SHA: ${build.gitCommitSha}")
        appendLine("Generated timestamp: $generatedAt")
        appendLine()
        appendLine("Diagnostic")
        appendLine("- ID: ${failure.diagnosticId}")
        appendLine("- Failure code: ${failure.code.name}")
        appendLine("- Failure stage: ${failure.stage.name}")
        appendLine("- Analysis mode: ${failure.analysisMode.name}")
        appendLine("- Retry attempt: ${failure.retryAttempt}")
        appendLine("- Reason: ${failure.primaryReason}")
        failure.affectedFeatureOrSource?.let { appendLine("- Affected quantity: $it") }
        appendLine()
        appendLine("Original request")
        appendLine("- X: ${featureLine(request.xFeature, displayNames)}")
        appendLine("- Y: ${request.yFeatures.joinToString { featureLine(it, displayNames) }}")
        appendLine("- Controls: ${request.controls.joinToString { featureLine(it, displayNames) }.ifBlank { "none" }}")
        appendLine("- Horizon: ${request.requestedHorizon} week(s)")
        appendLine()
        appendLine("Effective request")
        appendLine("- Controls retained: ${failure.effectiveControls.joinToString().ifBlank { "none" }}")
        appendLine("- Controls removed: ${failure.originalControls.filterNot { it in failure.effectiveControls }.joinToString().ifBlank { "none" }}")
        appendLine("- X, Y, horizon: unchanged")
        appendLine()
        appendLine("Data")
        appendLine("- CLOSED weeks: ${failure.availableClosedWeeks ?: "unknown"}")
        appendLine("- Usable/common rows: ${failure.usableCommonRows ?: "unknown"}")
        appendLine("- Attempted lags: ${failure.attemptedLags.joinToString().ifBlank { "none" }}")
        appendLine("- Selected/effective Pmax: ${failure.selectedPmax ?: "unknown"}")
        failure.attemptedCommonRowsByPmax.toSortedMap(reverseOrder()).forEach { (pmax, rows) ->
            appendLine("- Common rows at Pmax=$pmax: $rows")
        }
        if (failure.attemptedSimplifications.isNotEmpty()) {
            appendLine("- Planning attempts:")
            failure.attemptedSimplifications.distinct().forEach { appendLine("  - $it") }
        }
        appendLine()
        appendLine("Representation")
        if (failure.representationOverrides.isEmpty()) {
            appendLine("- Relaxed semantic overrides: none")
        } else {
            failure.representationOverrides.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("Sampling")
        appendLine("- Mode: ${failure.samplingReliabilityMode.name}")
        appendLine("- Policy fingerprint: ${failure.samplingPolicyFingerprint ?: "unknown"}")
        appendLine("- Chains: ${failure.chainsAttempted ?: "unknown"}")
        appendLine("- Warmup draws per chain: ${failure.warmupDrawsPerChain ?: "unknown"}")
        appendLine("- Production draws per chain: ${failure.productionDrawsPerChain ?: "unknown"}")
        failure.observations.forEach { appendLine("- ${it.displayLine()}") }
        appendLine()
        appendLine("Relaxation")
        appendLine("- Available routes: ${failure.availableRelaxationRoutes.namesOrNone()}")
        appendLine("- Attempted routes: ${failure.attemptedRelaxationRoutes.namesOrNone()}")
        appendLine("- Applied routes: ${failure.appliedRelaxationRoutes.namesOrNone()}")
        appendLine()
        appendLine("Fingerprints")
        appendLine("- Snapshot: ${failure.snapshotFingerprint ?: "unknown"}")
        appendLine("- Prepared input: ${failure.preparedInputFingerprint ?: "unknown"}")
        appendLine("- Preparation policy: ${failure.preparationPolicyFingerprint ?: "unknown"}")
        appendLine("- Effective plan/design: ${failure.effectivePlanFingerprint ?: "unknown"}")
        appendLine("- Sampling policy: ${failure.samplingPolicyFingerprint ?: "unknown"}")
        appendLine("- Sampling identity: ${failure.samplingIdentityFingerprint ?: "unknown"}")
        appendLine()
        appendLine("Technical details")
        if (failure.technicalDetails.isEmpty()) {
            appendLine("- none")
        } else {
            failure.technicalDetails.distinct().forEach { appendLine("- $it") }
        }
    }.trimEnd() + "\n"

    private fun featureLine(
        feature: AnalysisFeatureKey,
        displayNames: Map<AnalysisFeatureKey, String>
    ): String = "${feature.value} (${displayNames[feature] ?: "display name unavailable"})"

    private fun Set<StrictRelaxationRoute>.namesOrNone(): String =
        map { it.name }.sorted().joinToString().ifBlank { "none" }
}
