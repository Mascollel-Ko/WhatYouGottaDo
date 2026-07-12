package com.training.trackplanner.analysis.lab.phaseb

import com.training.trackplanner.analysis.lab.pipeline.BvarPreparedView
import com.training.trackplanner.analysis.lab.pipeline.FutureBvarInput
import com.training.trackplanner.analysis.lab.pipeline.PreparedRowPlan
import com.training.trackplanner.analysis.lab.pipeline.PreparedScalingPlan
import com.training.trackplanner.analysis.lab.pipeline.strictFingerprint
import com.training.trackplanner.analysis.trends.TrendMetricId

internal class BvarPhaseBEstimator {
    fun estimatePosteriorCore(
        view: BvarPreparedView,
        rowPlan: PreparedRowPlan,
        scalingPlan: PreparedScalingPlan,
        shockSourceMetric: TrendMetricId,
        orderedEndogenousMetrics: List<TrendMetricId>,
        pMax: Int,
        lambdaGrid: List<Double> = DEFAULT_LAMBDA_GRID
    ): BvarPhaseBEstimationResult = runCatching {
        val system = BvarEndogenousSystem.createValidated(shockSourceMetric, orderedEndogenousMetrics)
        val grid = BvarModelGridSpec.createValidated(pMax, lambdaGrid)
        val prior = BvarPriorSpec.defaultFor(grid)
        val input = FutureBvarInput.createValidated(
            view = view,
            rowPlan = rowPlan,
            scalingPlan = scalingPlan,
            priorFingerprint = prior.fingerprint,
            orderedEndogenousMetrics = system.orderedMetrics
        )
        val request = BvarPhaseBRequest(input, system, grid, prior, BvarDrawSpec.testOnly(2))
        estimatePosteriorCore(request)
    }.fold(
        onSuccess = { BvarPhaseBEstimationResult.Success(it) },
        onFailure = { error ->
            BvarPhaseBEstimationResult.Failure((error as? BvarPhaseBException)?.failure ?: BvarPhaseBFailure(
                code = BvarPhaseBFailureCode.INTERNAL_NUMERICAL_FAILURE,
                stage = "posterior-core",
                diagnostic = error.message ?: "unexpected PHASE B core failure"
            ))
        }
    )

    fun estimatePosteriorCore(request: BvarPhaseBRequest): BvarPhaseBResult {
        val sample = BvarPhaseBDesignMaterializer.materialize(
            input = request.preparedInput,
            system = request.system,
            grid = request.grid,
            prior = request.prior
        )
        val (parameters, modelPosterior) = BvarPosteriorAlgebra.fitAll(
            input = request.preparedInput,
            sample = sample,
            system = request.system,
            grid = request.grid,
            prior = request.prior
        )
        val fingerprint = strictFingerprint(
            listOf(
                request.preparedInput.fingerprint,
                request.system.fingerprint,
                sample.identity.fingerprint,
                request.prior.fingerprint,
                parameters.joinToString(",") { it.fingerprint },
                modelPosterior.fingerprint,
                "phase-b-core-result-v1"
            )
        )
        return BvarPhaseBResult(
            inputFingerprint = request.preparedInput.fingerprint,
            system = request.system,
            designIdentity = sample.identity,
            prior = request.prior,
            posteriorParameters = parameters,
            modelPosterior = modelPosterior,
            posteriorDraws = emptyList(),
            rejectedDrawDiagnostics = emptyList(),
            identifiedShockPosterior = null,
            numericalDiagnostics = listOf("conjugate posterior core calculated on one common standardized sample"),
            warnings = emptyList(),
            fingerprint = fingerprint
        )
    }
}
