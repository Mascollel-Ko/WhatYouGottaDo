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

    fun estimatePosteriorMixture(
        view: BvarPreparedView,
        rowPlan: PreparedRowPlan,
        scalingPlan: PreparedScalingPlan,
        shockSourceMetric: TrendMetricId,
        orderedEndogenousMetrics: List<TrendMetricId>,
        pMax: Int,
        lambdaGrid: List<Double> = DEFAULT_LAMBDA_GRID,
        drawSpec: BvarDrawSpec = BvarDrawSpec.production()
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
        val request = BvarPhaseBRequest(input, system, grid, prior, drawSpec)
        estimatePosteriorMixture(request)
    }.fold(
        onSuccess = { BvarPhaseBEstimationResult.Success(it) },
        onFailure = { error ->
            BvarPhaseBEstimationResult.Failure((error as? BvarPhaseBException)?.failure ?: BvarPhaseBFailure(
                code = BvarPhaseBFailureCode.INTERNAL_NUMERICAL_FAILURE,
                stage = "posterior-mixture",
                diagnostic = error.message ?: "unexpected PHASE B mixture failure"
            ))
        }
    )

    fun estimate(
        view: BvarPreparedView,
        rowPlan: PreparedRowPlan,
        scalingPlan: PreparedScalingPlan,
        shockSourceMetric: TrendMetricId,
        orderedEndogenousMetrics: List<TrendMetricId>,
        pMax: Int,
        lambdaGrid: List<Double> = DEFAULT_LAMBDA_GRID,
        drawSpec: BvarDrawSpec = BvarDrawSpec.production()
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
        val request = BvarPhaseBRequest(input, system, grid, prior, drawSpec)
        estimate(request)
    }.fold(
        onSuccess = { BvarPhaseBEstimationResult.Success(it) },
        onFailure = { error ->
            BvarPhaseBEstimationResult.Failure((error as? BvarPhaseBException)?.failure ?: BvarPhaseBFailure(
                code = BvarPhaseBFailureCode.INTERNAL_NUMERICAL_FAILURE,
                stage = "phase-b-estimate",
                diagnostic = error.message ?: "unexpected PHASE B failure"
            ))
        }
    )

    fun estimatePosteriorCore(request: BvarPhaseBRequest): BvarPhaseBResult {
        val (sample, parameters, modelPosterior) = posteriorCoreSections(request)
        return coreResult(request, sample, parameters, modelPosterior)
    }

    fun estimatePosteriorMixture(request: BvarPhaseBRequest): BvarPhaseBResult {
        val (sample, parameters, modelPosterior) = posteriorCoreSections(request)
        val (draws, rejected) = BvarPosteriorSampler.generateDraws(
            input = request.preparedInput,
            sample = sample,
            modelPosterior = modelPosterior,
            parameters = parameters,
            drawSpec = request.drawSpec
        )
        val posteriorMixtureFingerprint = posteriorMixtureFingerprint(request, sample, parameters, modelPosterior, draws, rejected)
        val fingerprint = strictFingerprint(
            listOf(
                posteriorMixtureFingerprint,
                "phase-b-mixture-result-v1"
            )
        )
        return BvarPhaseBResult(
            inputFingerprint = request.preparedInput.fingerprint,
            system = request.system,
            designIdentity = sample.identity,
            prior = request.prior,
            posteriorParameters = parameters,
            modelPosterior = modelPosterior,
            posteriorDraws = draws,
            rejectedDrawDiagnostics = rejected,
            identifiedShockPosterior = null,
            posteriorMixtureFingerprint = posteriorMixtureFingerprint,
            numericalDiagnostics = listOf("direct conjugate posterior draws allocated by joint lag/lambda posterior weights"),
            warnings = emptyList(),
            fingerprint = fingerprint
        )
    }

    fun estimate(request: BvarPhaseBRequest): BvarPhaseBResult {
        val (sample, parameters, modelPosterior) = posteriorCoreSections(request)
        val (draws, rejected) = BvarPosteriorSampler.generateDraws(
            input = request.preparedInput,
            sample = sample,
            modelPosterior = modelPosterior,
            parameters = parameters,
            drawSpec = request.drawSpec
        )
        val posteriorMixtureFingerprint = posteriorMixtureFingerprint(request, sample, parameters, modelPosterior, draws, rejected)
        val shockPosterior = BvarStructuralShockIdentifier.identify(
            request = request,
            sample = sample,
            posteriorMixtureFingerprint = posteriorMixtureFingerprint,
            draws = draws,
            rejectedDrawDiagnostics = rejected
        )
        val fingerprint = strictFingerprint(
            listOf(
                posteriorMixtureFingerprint,
                shockPosterior.fingerprint,
                "phase-b-bvar-posterior-and-structural-shocks-v1"
            )
        )
        return BvarPhaseBResult(
            inputFingerprint = request.preparedInput.fingerprint,
            system = request.system,
            designIdentity = sample.identity,
            prior = request.prior,
            posteriorParameters = parameters,
            modelPosterior = modelPosterior,
            posteriorDraws = draws,
            rejectedDrawDiagnostics = rejected,
            identifiedShockPosterior = shockPosterior,
            posteriorMixtureFingerprint = posteriorMixtureFingerprint,
            numericalDiagnostics = listOf(
                "direct conjugate posterior draws allocated by joint lag/lambda posterior weights",
                "draw-specific reduced-form residuals and Cholesky structural shocks identified"
            ),
            warnings = listOf("Cholesky identification depends on the declared variable ordering and is not order-invariant causal identification."),
            fingerprint = fingerprint
        )
    }

    private fun posteriorCoreSections(
        request: BvarPhaseBRequest
    ): Triple<BvarCommonSample, List<BvarPosteriorParameters>, BvarModelPosterior> {
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
        return Triple(sample, parameters, modelPosterior)
    }

    private fun coreResult(
        request: BvarPhaseBRequest,
        sample: BvarCommonSample,
        parameters: List<BvarPosteriorParameters>,
        modelPosterior: BvarModelPosterior
    ): BvarPhaseBResult {
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
            posteriorMixtureFingerprint = fingerprint,
            numericalDiagnostics = listOf("conjugate posterior core calculated on one common standardized sample"),
            warnings = emptyList(),
            fingerprint = fingerprint
        )
    }

    private fun posteriorMixtureFingerprint(
        request: BvarPhaseBRequest,
        sample: BvarCommonSample,
        parameters: List<BvarPosteriorParameters>,
        modelPosterior: BvarModelPosterior,
        draws: List<BvarPosteriorDraw>,
        rejected: List<BvarRejectedDrawDiagnostic>
    ): String = strictFingerprint(
        listOf(
            request.preparedInput.fingerprint,
            request.system.fingerprint,
            sample.identity.fingerprint,
            request.prior.fingerprint,
            parameters.joinToString(",") { it.fingerprint },
            modelPosterior.fingerprint,
            draws.joinToString(",") { "${it.drawId}:${it.weight}:${it.covarianceFingerprint}:${it.coefficientFingerprint}" },
            rejected.joinToString(",") { "${it.drawId}:${it.reason}" },
            "phase-b-posterior-mixture-v1"
        )
    )
}
