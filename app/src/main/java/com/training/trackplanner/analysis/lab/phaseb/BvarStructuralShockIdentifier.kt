package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.StableLinearAlgebra
import com.training.trackplanner.analysis.lab.pipeline.BvarPosteriorSourceIdentity
import com.training.trackplanner.analysis.lab.pipeline.IdentifiedShockPosterior
import com.training.trackplanner.analysis.lab.pipeline.RejectedShockDrawDiagnostic

internal object BvarStructuralShockIdentifier {
    fun identify(
        request: BvarPhaseBRequest,
        sample: BvarCommonSample,
        posteriorMixtureFingerprint: String,
        draws: List<BvarPosteriorDraw>,
        rejectedDrawDiagnostics: List<BvarRejectedDrawDiagnostic>
    ): IdentifiedShockPosterior {
        val sourceIdentity = BvarPosteriorSourceIdentity.createValidated(
            input = request.preparedInput,
            sourceMetric = request.system.shockSourceMetric,
            sourceBvarPosteriorFingerprint = posteriorMixtureFingerprint,
            eligibleSourceWeeks = sample.identity.sourceWeeks
        )
        val shockSeries = draws.associate { draw ->
            draw.drawId to sourceShockSeries(sample, draw, request.system.shockSourceIndex, request.preparedInput, request.system)
        }
        return IdentifiedShockPosterior.createValidated(
            sourceMetric = request.system.shockSourceMetric,
            orderedEndogenousMetrics = request.system.orderedMetrics,
            structuralOrdering = request.system.orderedMetrics.joinToString(" -> ") { it.name },
            normalizationPolicy = PHASE_B_SHOCK_NORMALIZATION,
            posteriorDrawIds = draws.map { it.drawId },
            drawWeights = draws.associate { it.drawId to it.weight },
            shockSeriesByDraw = shockSeries,
            sourceCovarianceDrawFingerprintByDraw = draws.associate { it.drawId to it.covarianceFingerprint },
            sourceIdentity = sourceIdentity,
            rejectedDrawDiagnostics = rejectedDrawDiagnostics.map { RejectedShockDrawDiagnostic(it.drawId, it.reason) }
        )
    }

    fun sourceShockSeries(
        sample: BvarCommonSample,
        draw: BvarPosteriorDraw,
        sourceIndex: Int,
        input: com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput,
        system: BvarEndogenousSystem
    ): List<Double> {
        val residuals = reducedFormResiduals(sample, draw)
        val lower = runCatching { StableLinearAlgebra.strictCholesky(draw.covariance).factor }.getOrElse {
            fail(BvarPhaseBFailureCode.DRAW_COVARIANCE_NOT_SPD, "structural-shock", it.message ?: "draw covariance Cholesky failed", input)
        }
        val structural = StableLinearAlgebra.applyLowerTriangular(lower, transpose(residuals))
        val structuralRows = transpose(structural)
        residuals.indices.forEach { row ->
            val reconstructed = multiply(lower, Array(system.k) { index -> doubleArrayOf(structuralRows[row][index]) })
                .map { it[0] }
                .toDoubleArray()
            val scale = maxOf(1.0, residuals[row].maxOf { kotlin.math.abs(it) })
            val error = residuals[row].indices.maxOf { index -> kotlin.math.abs(residuals[row][index] - reconstructed[index]) } / scale
            if (!error.isFinite() || error > 1e-8) {
                fail(BvarPhaseBFailureCode.STRUCTURAL_SHOCK_RECONSTRUCTION_FAILED, "structural-shock", "draw residual reconstruction failed", input)
            }
        }
        val series = structuralRows.map { it[sourceIndex] }
        if (series.size != sample.identity.sourceWeeks.size || series.any { !it.isFinite() }) {
            fail(BvarPhaseBFailureCode.STRUCTURAL_SHOCK_RECONSTRUCTION_FAILED, "structural-shock", "invalid source shock series", input)
        }
        return series
    }

    fun reducedFormResiduals(sample: BvarCommonSample, draw: BvarPosteriorDraw): Array<DoubleArray> {
        val q = 1 + sample.identity.orderedMetrics.size * draw.lag
        val x = sample.xMax.map { it.copyOfRange(0, q) }.toTypedArray()
        return subtract(sample.y, multiply(x, draw.coefficients))
    }
}
