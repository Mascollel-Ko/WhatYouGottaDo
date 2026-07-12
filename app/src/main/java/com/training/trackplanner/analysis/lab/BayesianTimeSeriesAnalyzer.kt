package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

internal class LegacyTimeSeriesAnalyzer(
    private val alignmentService: TimeSeriesAlignmentService = TimeSeriesAlignmentService(),
    private val localProjectionEstimator: BayesianLocalProjectionEstimator = BayesianLocalProjectionEstimator(),
    private val cointegrationAnalyzer: CointegrationAnalyzer = CointegrationAnalyzer(),
    private val endogenousVariableSelector: EndogenousVariableSelector = EndogenousVariableSelector(),
    private val choleskyShockIdentifier: CholeskyShockIdentifier = CholeskyShockIdentifier()
) {
    fun analyze(
        request: TimeSeriesAnalysisRequest,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): BayesianTimeSeriesResult {
        val warnings = mutableListOf("Legacy compatibility analysis; exploratory only and not a completed strict Bayesian result.")
        if (request.requestedHorizon < MIN_HORIZON || request.requestedHorizon > MAX_HORIZON) {
            return unavailable(request, warnings + "Requested horizon must be between $MIN_HORIZON and $MAX_HORIZON.")
        }
        val requestedHorizon = request.requestedHorizon
        val yMetrics = request.yMetrics.distinct().filter { it != request.xMetric }
        if (yMetrics.isEmpty()) return unavailable(request, warnings + "Select at least one response Y.")
        val controls = request.controls.distinct().filter { it != request.xMetric && it !in yMetrics }
        val required = listOf(request.xMetric) + yMetrics + controls
        val rawBaseAlignment = alignmentService.align(required, metricSeries)
            ?: return unavailable(request, warnings + "Selected series have no aligned weekly observations.")
        val levelCatalogAlignment = alignmentService.align(AnalysisMetricRegistry.descriptors.map { it.id }, metricSeries)
            ?.let { alignmentService.restrictToWeeks(it, rawBaseAlignment.weeks) }
            ?: return unavailable(request, warnings + "Selected series cannot be prepared on one canonical weekly calendar.", rawBaseAlignment)
        val transformationPlan = alignmentService.transformationPlan(levelCatalogAlignment, required.toSet())
        val requiredDiagnostics = required.mapNotNull { transformationPlan.diagnostics[it] }
        if (requiredDiagnostics.any { it.levelOrder == IntegrationOrder.I2_OR_HIGHER }) {
            return unavailable(
                request,
                warnings + "A required X/Y/Z series is I(2) or higher and cannot enter the Bayesian IRF system.",
                rawBaseAlignment,
                requiredDiagnostics
            )
        }
        val preparedCatalog = alignmentService.preparedCandidateCatalog(levelCatalogAlignment, transformationPlan)?.preparedSeriesByMetric
            ?: return unavailable(request, warnings + "TRANSFORMATION_PLAN_UNAVAILABLE: no transformed prepared catalog could be created.", rawBaseAlignment, requiredDiagnostics)
        val baseAlignment = alignmentService.alignmentFromPrepared(required, preparedCatalog)
            ?: return unavailable(request, warnings + "Selected transformed prepared series cannot be aligned on one weekly calendar.", rawBaseAlignment, requiredDiagnostics)
        if (baseAlignment.weeks.size < MIN_OBSERVATIONS) {
            return unavailable(request, warnings + "At least 24 transformed aligned weekly observations are required.", baseAlignment, requiredDiagnostics)
        }

        val automaticSelection = endogenousVariableSelector.select(
            xMetric = request.xMetric,
            yMetrics = yMetrics,
            controls = controls,
            requestedHorizon = requestedHorizon,
            baseAlignment = baseAlignment,
            preparedSeries = preparedCatalog
        )
        val system = choleskyShockIdentifier.canonicalOrder(listOf(request.xMetric) + yMetrics + automaticSelection.metrics)
        val levelAlignment = alignmentService.alignmentFromPrepared(system + controls, levelCatalogAlignment.preparedSeries)
            ?: return unavailable(request, warnings + "The selected system cannot be aligned without filling missing values.", baseAlignment)
        val stationaryAlignment = alignmentService.alignmentFromPrepared(system + controls, preparedCatalog)
            ?: return unavailable(request, warnings + "TRANSFORMATION_MISMATCH: selected system is missing transformed prepared series.", baseAlignment, requiredDiagnostics)
        if (stationaryAlignment.weeks.size < MIN_OBSERVATIONS) {
            return unavailable(request, warnings + "Aligned transformed data are insufficient after automatic-variable screening.", stationaryAlignment, requiredDiagnostics)
        }
        val diagnostics = system.mapNotNull { metric -> transformationPlan.diagnostics[metric] }
        val mandatoryDiagnostics = diagnostics.filter { it.metric == request.xMetric || it.metric in yMetrics }
        if (mandatoryDiagnostics.any { it.levelOrder == IntegrationOrder.I2_OR_HIGHER }) {
            return unavailable(
                request,
                warnings + "A required X/Y series is I(2) or higher and cannot enter the Bayesian IRF system.",
                levelAlignment,
                diagnostics,
                automaticSelection = automaticSelection
            )
        }

        val cointegration = cointegrationAnalyzer.analyze(system, levelAlignment, diagnostics)
        val transformations = (system + controls).distinct().associateWith { metric ->
            transformationPlan.plansByMetric[metric]?.transformation?.id.orEmpty()
        }
        val lagAndHorizon = selectLagAndHorizon(requestedHorizon, request.xMetric, yMetrics, system, controls, stationaryAlignment)
            ?: return unavailable(
                request,
                warnings + "No horizon from h=1 satisfies the Bayesian posterior sample-size conditions.",
                stationaryAlignment,
                diagnostics,
                cointegration = cointegration,
                automaticSelection = automaticSelection,
                transformations = transformations
            )
        val (usedHorizon, lagPosterior) = lagAndHorizon
        if (usedHorizon < requestedHorizon) {
            warnings += "Requested horizon $requestedHorizon was reduced to $usedHorizon because the longer horizon fails the sample-size condition."
        }

        if (cointegration.supportedForModelRouting && cointegration.cointegrationVector != null) {
            val vecm = BayesianVecmEstimator().estimate(
                levelAlignment,
                system,
                controls,
                lagPosterior.selectedLag ?: lagPosterior.probabilities.maxBy { it.value }.key,
                usedHorizon,
                request.xMetric,
                cointegration.cointegrationVector
            )
            if (vecm != null) {
                return success(
                    request = request,
                    model = BayesianTimeSeriesModel.BAYESIAN_VECM,
                    responses = selectedResponses(yMetrics, vecm),
                    usedHorizon = usedHorizon,
                    alignment = levelAlignment,
                    diagnostics = diagnostics,
                    cointegration = cointegration,
                    lagPosterior = lagPosterior,
                    automaticSelection = automaticSelection,
                    order = system,
                    sensitivity = dynamicSensitivity(
                        levelAlignment,
                        system,
                        controls,
                        usedHorizon,
                        request.xMetric,
                        yMetrics,
                        lagPosterior,
                        useVecm = true,
                        cointegrationVector = cointegration.cointegrationVector
                    ),
                    transformations = system.associateWith { "level (VECM long-run system)" },
                    warnings = warnings,
                    summary = "Bayesian VECM was selected because the I(1) system has supported cointegration."
                )
            }
            warnings += "Cointegration was supported, but the Bayesian VECM numerical diagnostic failed; differenced Bayesian local projection is shown instead."
        } else if (cointegration.legacyRankOneStatistic != null) {
            warnings += cointegration.message
        }

        val referenceLag = lagPosterior.selectedLag ?: lagPosterior.probabilities.maxBy { it.value }.key
        val systemFit = BayesianVarEstimator().fitSystem(stationaryAlignment, system, controls, referenceLag, includeErrorCorrection = false)
            ?: return unavailable(
                request,
                warnings + "The reduced-form Bayesian system was not stable enough to identify a structural shock.",
                stationaryAlignment,
                diagnostics,
                lagPosterior,
                cointegration,
                automaticSelection,
                transformations
            )
        val structuralShock = choleskyShockIdentifier.structuralShockSeries(systemFit, system, request.xMetric)
            ?: return unavailable(
                request,
                warnings + "Cholesky shock identification failed; no IRF was generated.",
                stationaryAlignment,
                diagnostics,
                lagPosterior,
                cointegration,
                automaticSelection,
                transformations
            )
        if (!choleskyShockIdentifier.posteriorPredictivePass(systemFit)) {
            warnings += "The Bayesian reduced-form posterior predictive coverage is weak; interpret the response cautiously."
        }
        val localResponses = localProjectionResponses(
            request.xMetric,
            yMetrics,
            system,
            controls,
            stationaryAlignment,
            usedHorizon,
            lagPosterior,
            structuralShock
        )
        if (localResponses.isNotEmpty()) {
            return success(
                request = request,
                model = BayesianTimeSeriesModel.BAYESIAN_LOCAL_PROJECTION,
                responses = localResponses,
                usedHorizon = usedHorizon,
                alignment = stationaryAlignment,
                diagnostics = diagnostics,
                cointegration = cointegration,
                lagPosterior = lagPosterior,
                automaticSelection = automaticSelection,
                order = system,
                sensitivity = dynamicSensitivity(stationaryAlignment, system, controls, usedHorizon, request.xMetric, yMetrics, lagPosterior, useVecm = false),
                transformations = transformations,
                warnings = warnings,
                summary = "Bayesian local projections estimate each selected response at h=0 through h=$usedHorizon using a Cholesky-identified one-standard-deviation shock."
            )
        }
        val bvar = BayesianVarEstimator().estimate(stationaryAlignment, system, controls, referenceLag, usedHorizon, request.xMetric)
        if (bvar != null && choleskyShockIdentifier.posteriorPredictivePass(systemFit)) {
            warnings += "Bayesian local projection was not estimable for every horizon; Bayesian VAR fallback is shown."
            return success(
                request = request,
                model = BayesianTimeSeriesModel.BAYESIAN_VAR,
                responses = selectedResponses(yMetrics, bvar),
                usedHorizon = usedHorizon,
                alignment = stationaryAlignment,
                diagnostics = diagnostics,
                cointegration = cointegration,
                lagPosterior = lagPosterior,
                automaticSelection = automaticSelection,
                order = system,
                sensitivity = dynamicSensitivity(stationaryAlignment, system, controls, usedHorizon, request.xMetric, yMetrics, lagPosterior, useVecm = false),
                transformations = transformations,
                warnings = warnings,
                summary = "Bayesian VAR fallback is shown because local projection diagnostics were not sufficient."
            )
        }
        return unavailable(
            request,
            warnings + "Bayesian local projection and BVAR diagnostics both failed.",
            stationaryAlignment,
            diagnostics,
            lagPosterior,
            cointegration,
            automaticSelection,
            transformations
        )
    }

    private fun selectLagAndHorizon(
        requestedHorizon: Int,
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        alignment: TimeSeriesAlignment
    ): Pair<Int, BayesianLagPosterior>? = (requestedHorizon downTo MIN_HORIZON).firstNotNullOfOrNull { horizon ->
        selectLagPosterior(xMetric, yMetrics, system, controls, alignment, horizon)?.let { posterior -> horizon to posterior }
    }

    private fun selectLagPosterior(
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        alignment: TimeSeriesAlignment,
        horizon: Int
    ): BayesianLagPosterior? {
        val maxLag = minOf(MAX_LAG, (alignment.weeks.size - MIN_OBSERVATIONS - horizon) / maxOf(1, system.size))
        val evidence = (1..maxLag).mapNotNull { lag ->
            val estimates = yMetrics.mapNotNull { yMetric ->
                localProjectionEstimator.estimate(alignment, xMetric, yMetric, system, controls, lag, horizon)
            }
            if (estimates.size == yMetrics.size) lag to (estimates.sumOf { it.logEvidence } - LAG_PRIOR_DECAY * (lag - 1)) else null
        }.toMap()
        if (evidence.isEmpty()) return null
        val probabilities = normalizeLogWeights(evidence)
        val best = probabilities.maxByOrNull { it.value } ?: return null
        return BayesianLagPosterior(probabilities, best.key.takeIf { best.value >= HARD_SELECTION_THRESHOLD }, best.value < HARD_SELECTION_THRESHOLD)
    }

    private fun localProjectionResponses(
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        alignment: TimeSeriesAlignment,
        horizon: Int,
        lagPosterior: BayesianLagPosterior,
        structuralShock: Map<Int, Double>
    ): List<BayesianResponseIrf> = yMetrics.mapNotNull { yMetric ->
        val points = (0..horizon).mapNotNull { step ->
            averagedProjection(alignment, xMetric, yMetric, system, controls, step, lagPosterior, structuralShock)
        }
        if (points.size == horizon + 1) BayesianResponseIrf(yMetric, points) else null
    }

    private fun averagedProjection(
        alignment: TimeSeriesAlignment,
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        horizon: Int,
        posterior: BayesianLagPosterior,
        structuralShock: Map<Int, Double>
    ): BayesianIrfPoint? {
        val estimates = posterior.probabilities.map { (lag, weight) ->
            localProjectionEstimator.estimate(alignment, xMetric, yMetric, system, controls, lag, horizon, structuralShock)?.let { weight to it.point }
        }
        if (estimates.any { it == null }) return null
        val resolved = estimates.filterNotNull()
        fun weighted(selector: (BayesianIrfPoint) -> Double): Double = resolved.sumOf { (weight, point) -> weight * selector(point) }
        return BayesianIrfPoint(
            horizon,
            weighted(BayesianIrfPoint::estimate),
            weighted(BayesianIrfPoint::low80),
            weighted(BayesianIrfPoint::high80),
            resolved.minOf { it.second.observations }
        )
    }

    private fun dynamicSensitivity(
        alignment: TimeSeriesAlignment,
        system: List<TrendMetricId>,
        controls: List<TrendMetricId>,
        horizon: Int,
        xMetric: TrendMetricId,
        yMetrics: List<TrendMetricId>,
        lagPosterior: BayesianLagPosterior,
        useVecm: Boolean,
        cointegrationVector: List<Double>? = null
    ): CholeskySensitivityDiagnostic? {
        val alternativeOrder = choleskyShockIdentifier.alternativeOrder(system) ?: return null
        val lag = lagPosterior.selectedLag ?: lagPosterior.probabilities.maxBy { it.value }.key
        val baseline = if (useVecm) {
            val vector = cointegrationVector ?: return null
            BayesianVecmEstimator().estimate(alignment, system, controls, lag, horizon, xMetric, vector)
        } else {
            BayesianVarEstimator().estimate(alignment, system, controls, lag, horizon, xMetric)
        } ?: return null
        val alternative = if (useVecm) {
            val vector = cointegrationVector ?: return null
            val reorderedVector = alternativeOrder.map { metric -> vector[system.indexOf(metric)] }
            BayesianVecmEstimator().estimate(alignment, alternativeOrder, controls, lag, horizon, xMetric, reorderedVector)
        } else {
            BayesianVarEstimator().estimate(alignment, alternativeOrder, controls, lag, horizon, xMetric)
        } ?: return null
        return choleskyShockIdentifier.sensitivity(baseline, alternative, yMetrics)
    }

    private fun selectedResponses(
        yMetrics: List<TrendMetricId>,
        responses: Map<TrendMetricId, List<BayesianIrfPoint>>
    ): List<BayesianResponseIrf> = yMetrics.mapNotNull { metric -> responses[metric]?.let { points -> BayesianResponseIrf(metric, points) } }

    private fun success(
        request: TimeSeriesAnalysisRequest,
        model: BayesianTimeSeriesModel,
        responses: List<BayesianResponseIrf>,
        usedHorizon: Int,
        alignment: TimeSeriesAlignment,
        diagnostics: List<IntegrationDiagnostic>,
        cointegration: CointegrationDiagnostic?,
        lagPosterior: BayesianLagPosterior,
        automaticSelection: AutomaticEndogenousSelection,
        order: List<TrendMetricId>,
        sensitivity: CholeskySensitivityDiagnostic?,
        transformations: Map<TrendMetricId, String>,
        warnings: List<String>,
        summary: String
    ): BayesianTimeSeriesResult = BayesianTimeSeriesResult(
        request = request,
        model = model,
        responses = responses,
        usedHorizon = usedHorizon,
        alignment = alignment,
        integrationDiagnostics = diagnostics,
        cointegration = cointegration,
        lagPosterior = lagPosterior,
        automaticEndogenous = automaticSelection.metrics,
        automaticSelectionDiagnostics = automaticSelection.diagnostics,
        choleskyOrder = order,
        choleskySensitivity = sensitivity,
        transformations = transformations,
        confidence = confidence(responses),
        warnings = warnings.distinct(),
        summary = summary
    )

    private fun unavailable(
        request: TimeSeriesAnalysisRequest,
        warnings: List<String>,
        alignment: TimeSeriesAlignment? = null,
        diagnostics: List<IntegrationDiagnostic> = emptyList(),
        lagPosterior: BayesianLagPosterior? = null,
        cointegration: CointegrationDiagnostic? = null,
        automaticSelection: AutomaticEndogenousSelection = AutomaticEndogenousSelection(emptyList(), emptyList()),
        transformations: Map<TrendMetricId, String> = emptyMap()
    ) = BayesianTimeSeriesResult(
        request = request,
        model = BayesianTimeSeriesModel.UNAVAILABLE,
        responses = emptyList(),
        usedHorizon = 0,
        alignment = alignment,
        integrationDiagnostics = diagnostics,
        cointegration = cointegration,
        lagPosterior = lagPosterior,
        automaticEndogenous = automaticSelection.metrics,
        automaticSelectionDiagnostics = automaticSelection.diagnostics,
        choleskyOrder = emptyList(),
        choleskySensitivity = null,
        transformations = transformations,
        confidence = AnalysisConfidence.LOW,
        warnings = warnings.distinct(),
        summary = warnings.last()
    )

    private fun confidence(responses: List<BayesianResponseIrf>): AnalysisConfidence {
        val observations = responses.flatMap(BayesianResponseIrf::points).minOfOrNull(BayesianIrfPoint::observations) ?: 0
        return when {
            observations >= 40 -> AnalysisConfidence.HIGH
            observations >= 24 -> AnalysisConfidence.MEDIUM
            else -> AnalysisConfidence.LOW
        }
    }

    private companion object {
        const val MIN_HORIZON = 1
        const val MAX_HORIZON = 8
        const val MIN_OBSERVATIONS = 24
        const val MAX_LAG = 4
        const val HARD_SELECTION_THRESHOLD = 0.70
        const val LAG_PRIOR_DECAY = 0.5
    }
}
