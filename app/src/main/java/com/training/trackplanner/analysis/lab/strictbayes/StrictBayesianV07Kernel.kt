package com.training.trackplanner.analysis.lab.strictbayes

import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.PreparedBvarComparisonDesign
import com.training.trackplanner.analysis.lab.pipeline.PreparedBvarLagDesign
import com.training.trackplanner.analysis.lab.pipeline.InverseTransformationRule
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarDesignRole
import com.training.trackplanner.analysis.lab.pipeline.StrictSeriesKey
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal data class StrictBayesianV07State(
    val lag: Int,
    val globalCandidateScaleSquared: Double,
    val globalCandidateAuxiliary: Double,
    val dynamicScaleSquared: Double,
    val dynamicAuxiliary: Double,
    val localScaleSquaredBySource: Map<AnalysisSourceKey, Double>,
    val localAuxiliaryBySource: Map<AnalysisSourceKey, Double>
)

internal data class StrictBayesianV07Draw(
    val lag: Int,
    val omegaByLag: Map<Int, Double>,
    val globalCandidateScale: Double,
    val dynamicScale: Double,
    val sigmaDiagonal: DoubleArray,
    val opennessBySource: Map<AnalysisSourceKey, Double>,
    val contributionBySource: Map<AnalysisSourceKey, Double>,
    val coefficientRmsBySource: Map<AnalysisSourceKey, Double>,
    val responseByFeature: Map<StrictSeriesKey, DoubleArray>
)

internal data class StrictBayesianV07Step(
    val state: StrictBayesianV07State,
    val draw: StrictBayesianV07Draw
)

internal class StrictBayesianV07Kernel(
    private val design: PreparedBvarComparisonDesign
) {
    private val sources = design.input.sourceGrouping.featuresBySource.keys.sorted()
    private val responseCount = design.responseFeatures.size
    private val rowCount = design.comparisonRowCount
    private val nu0 = responseCount + 2

    fun initialState(initialLag: Int = design.designsByLag.keys.first()): StrictBayesianV07State =
        StrictBayesianV07State(
            lag = initialLag,
            globalCandidateScaleSquared = 1.0,
            globalCandidateAuxiliary = 1.0,
            dynamicScaleSquared = 1.0,
            dynamicAuxiliary = 1.0,
            localScaleSquaredBySource = sources.associateWith { 1.0 },
            localAuxiliaryBySource = sources.associateWith { 1.0 }
        )

    fun step(state: StrictBayesianV07State, random: StrictRandom): StrictBayesianV07Step {
        validateState(state)
        val collapsed = design.designsByLag.toSortedMap().mapValues { (lag, lagDesign) ->
            collapsedPosterior(lag, lagDesign, state)
        }
        val lagKeys = collapsed.keys.toList()
        val omega = StrictBayesianMatrix.softmax(DoubleArray(lagKeys.size) { collapsed.getValue(lagKeys[it]).logWeight })
        val selectedLag = lagKeys[random.categorical(omega)]
        val selected = collapsed.getValue(selectedLag)
        val sigma = StrictBayesianMatrix.drawInverseWishart(selected.sn, nu0 + rowCount, random)
        val coefficients = drawCoefficients(selected, sigma, random)

        val sourceQuadratics = sources.associateWith { source ->
            groupedQuadratic(selected.design, coefficients, sigma, source)
        }
        val tauZeroSquared = design.input.tauZeroByLag.getValue(selectedLag).pow(2)
        val candidateRows = selected.design.columns.count { it.role == StrictBvarDesignRole.CANDIDATE_SOURCE }
        val nextLocalScale = sources.associateWith { source ->
            val sourceRows = selected.design.columns.count { it.source == source }
            random.inverseGamma(
                shape = (1.0 + responseCount * sourceRows) / 2.0,
                scale = 1.0 / state.localAuxiliaryBySource.getValue(source) +
                    sourceQuadratics.getValue(source) /
                    (2.0 * tauZeroSquared * state.globalCandidateScaleSquared)
            )
        }
        val nextLocalAuxiliary = sources.associateWith { source ->
            random.inverseGamma(1.0, 1.0 + 1.0 / nextLocalScale.getValue(source))
        }
        val nextGlobalCandidate = random.inverseGamma(
            shape = (1.0 + responseCount * candidateRows) / 2.0,
            scale = 1.0 / state.globalCandidateAuxiliary + 0.5 * sources.sumOf { source ->
                sourceQuadratics.getValue(source) / (tauZeroSquared * nextLocalScale.getValue(source))
            }
        )
        val nextGlobalCandidateAuxiliary = random.inverseGamma(1.0, 1.0 + 1.0 / nextGlobalCandidate)

        val coreRows = selected.design.columns.filter { it.role == StrictBvarDesignRole.CORE_DYNAMIC }
        val coreQuadratic = groupedQuadratic(selected.design, coefficients, sigma, source = null)
        val nextDynamic = random.inverseGamma(
            shape = (1.0 + responseCount * coreRows.size) / 2.0,
            scale = 1.0 / state.dynamicAuxiliary + coreQuadratic / 2.0
        )
        val nextDynamicAuxiliary = random.inverseGamma(1.0, 1.0 + 1.0 / nextDynamic)
        val nextState = StrictBayesianV07State(
            selectedLag,
            nextGlobalCandidate,
            nextGlobalCandidateAuxiliary,
            nextDynamic,
            nextDynamicAuxiliary,
            nextLocalScale,
            nextLocalAuxiliary
        )
        validateState(nextState)
        val actualTau = design.input.tauZeroByLag.getValue(selectedLag) * sqrt(nextGlobalCandidate)
        val openness = sources.associateWith { source ->
            sourceOpenness(selected.design, source, actualTau, sqrt(nextLocalScale.getValue(source)))
        }
        val contribution = sources.associateWith { source ->
            sourceContribution(selected.design, coefficients, source)
        }
        val coefficientRms = sources.associateWith { source ->
            coefficientRms(selected.design, coefficients, source)
        }
        val response = focalResponse(selected.design, coefficients)
        return StrictBayesianV07Step(
            nextState,
            StrictBayesianV07Draw(
                selectedLag,
                lagKeys.indices.associate { lagKeys[it] to omega[it] },
                sqrt(nextGlobalCandidate),
                sqrt(nextDynamic),
                DoubleArray(responseCount) { sigma[it][it] },
                openness,
                contribution,
                coefficientRms,
                response
            )
        )
    }

    internal fun collapsedPosterior(
        lag: Int,
        lagDesign: PreparedBvarLagDesign,
        state: StrictBayesianV07State
    ): CollapsedPosterior {
        val diagonalPrior = priorDiagonal(lag, lagDesign, state)
        val a = StrictBayesianMatrix.observationCovariance(lagDesign.x, diagonalPrior)
        val solved = StrictBayesianMatrix.solveSpdStrict(a, lagDesign.y)
        val sn = StrictBayesianMatrix.covarianceScalePosterior(lagDesign.y, solved)
        val logWeight = -ln(design.designsByLag.size.toDouble()) -
            0.5 * responseCount * StrictBayesianMatrix.logDetSpdStrict(a) -
            0.5 * (nu0 + rowCount) * StrictBayesianMatrix.logDetSpdStrict(sn)
        if (!logWeight.isFinite()) throw StrictBayesianNumericalException("NONFINITE_STATE: collapsed lag weight")
        return CollapsedPosterior(lagDesign, diagonalPrior, a, sn, logWeight)
    }

    private fun drawCoefficients(
        posterior: CollapsedPosterior,
        sigma: Array<DoubleArray>,
        random: StrictRandom
    ): Array<DoubleArray> {
        val u = StrictBayesianMatrix.drawMatrixNormalDiagonalRows(posterior.diagonalPrior, sigma, random)
        val delta = StrictBayesianMatrix.drawMatrixNormalIdentityRows(rowCount, sigma, random)
        val v = StrictBayesianMatrix.add(StrictBayesianMatrix.multiply(posterior.design.x, u), delta)
        val residual = StrictBayesianMatrix.subtract(posterior.design.y, v)
        val w = StrictBayesianMatrix.solveSpdStrict(posterior.a, residual)
        val correction = StrictBayesianMatrix.diagonalLeftMultiply(
            posterior.diagonalPrior,
            StrictBayesianMatrix.multiply(StrictBayesianMatrix.transpose(posterior.design.x), w)
        )
        return StrictBayesianMatrix.add(u, correction)
    }

    private fun priorDiagonal(
        lag: Int,
        lagDesign: PreparedBvarLagDesign,
        state: StrictBayesianV07State
    ): DoubleArray {
        val tauZeroSquared = design.input.tauZeroByLag.getValue(lag).pow(2)
        return DoubleArray(lagDesign.columns.size) { index ->
            val column = lagDesign.columns[index]
            val lagDecay = column.lag.toDouble().pow(-4.0)
            when (column.role) {
                StrictBvarDesignRole.CORE_DYNAMIC -> state.dynamicScaleSquared * lagDecay
                StrictBvarDesignRole.CANDIDATE_SOURCE -> {
                    val source = requireNotNull(column.source)
                    tauZeroSquared * state.globalCandidateScaleSquared *
                        state.localScaleSquaredBySource.getValue(source) * lagDecay
                }
            }.also { require(it.isFinite() && it > 0.0) }
        }
    }

    private fun groupedQuadratic(
        lagDesign: PreparedBvarLagDesign,
        coefficients: Array<DoubleArray>,
        sigma: Array<DoubleArray>,
        source: AnalysisSourceKey?
    ): Double {
        val selectedColumns = lagDesign.columns.filter { column ->
            if (source == null) column.role == StrictBvarDesignRole.CORE_DYNAMIC else column.source == source
        }
        if (selectedColumns.isEmpty()) return 0.0
        val coefficientRows = selectedColumns.map { coefficients[it.index] }.toTypedArray()
        val solved = StrictBayesianMatrix.solveSpdStrict(sigma, StrictBayesianMatrix.transpose(coefficientRows))
        return selectedColumns.indices.sumOf { row ->
            val lagDecay = selectedColumns[row].lag.toDouble().pow(-4.0)
            coefficientRows[row].indices.sumOf { response -> coefficientRows[row][response] * solved[response][row] } / lagDecay
        }.also { require(it.isFinite() && it >= 0.0) }
    }

    private fun sourceOpenness(
        lagDesign: PreparedBvarLagDesign,
        source: AnalysisSourceKey,
        tau: Double,
        lambda: Double
    ): Double {
        val columns = lagDesign.columns.filter { it.source == source }
        return columns.map { column ->
            val normSquared = lagDesign.x.sumOf { row -> row[column.index] * row[column.index] }
            val aSquared = normSquared * tau * tau * lambda * lambda * column.lag.toDouble().pow(-4.0)
            aSquared / (1.0 + aSquared)
        }.average()
    }

    private fun sourceContribution(
        lagDesign: PreparedBvarLagDesign,
        coefficients: Array<DoubleArray>,
        source: AnalysisSourceKey
    ): Double {
        val columns = lagDesign.columns.filter { it.source == source }
        var sumSquares = 0.0
        lagDesign.x.indices.forEach { row ->
            for (response in 0 until responseCount) {
                val fitted = columns.sumOf { column -> lagDesign.x[row][column.index] * coefficients[column.index][response] }
                sumSquares += fitted * fitted
            }
        }
        return sqrt(sumSquares / (rowCount * responseCount))
    }

    private fun coefficientRms(
        lagDesign: PreparedBvarLagDesign,
        coefficients: Array<DoubleArray>,
        source: AnalysisSourceKey
    ): Double {
        val values = lagDesign.columns.filter { it.source == source }
            .flatMap { column -> coefficients[column.index].asIterable() }
        return sqrt(values.sumOf { it * it } / values.size)
    }

    private fun focalResponse(
        lagDesign: PreparedBvarLagDesign,
        coefficients: Array<DoubleArray>
    ): Map<StrictSeriesKey, DoubleArray> {
        val responseIndex = design.responseFeatures.withIndex().associate { it.value to it.index }
        val dynamicByLag = (1..lagDesign.lag).associateWith { lag ->
            Array(responseCount) { DoubleArray(responseCount) }.also { matrix ->
                lagDesign.columns
                    .filter { it.role == StrictBvarDesignRole.CORE_DYNAMIC && it.lag == lag }
                    .forEach { column ->
                        val sourceResponse = responseIndex.getValue(column.feature)
                        for (targetResponse in 0 until responseCount) {
                            matrix[targetResponse][sourceResponse] = coefficients[column.index][targetResponse]
                        }
                    }
            }
        }
        val focalByLag = (1..lagDesign.lag).associateWith { lag ->
            DoubleArray(responseCount).also { direct ->
                lagDesign.columns
                    .filter {
                        it.role == StrictBvarDesignRole.CANDIDATE_SOURCE &&
                            it.feature == design.focalFeature && it.lag == lag
                    }
                    .forEach { column ->
                        for (targetResponse in 0 until responseCount) {
                            direct[targetResponse] += coefficients[column.index][targetResponse]
                        }
                    }
            }
        }
        val standardized = Array(design.maximumResponseHorizon + 1) { DoubleArray(responseCount) }
        for (horizon in 1..design.maximumResponseHorizon) {
            if (horizon <= lagDesign.lag) {
                focalByLag.getValue(horizon).copyInto(standardized[horizon])
            }
            for (lag in 1..minOf(lagDesign.lag, horizon - 1)) {
                val transition = dynamicByLag.getValue(lag)
                val previous = standardized[horizon - lag]
                for (target in 0 until responseCount) {
                    standardized[horizon][target] += (0 until responseCount).sumOf { source ->
                        transition[target][source] * previous[source]
                    }
                }
            }
        }
        return design.responseFeatures.associateWith { feature ->
            val target = responseIndex.getValue(feature)
            val scale = design.responseScalingStatistics.getValue(feature).scale
            val raw = DoubleArray(design.maximumResponseHorizon) { horizon ->
                standardized[horizon + 1][target] * scale
            }
            StrictResponseTransformation.apply(raw, design.responseScalePlans.getValue(feature).inverseTransformationRule)
        }.also { response ->
            if (response.values.any { values -> values.any { !it.isFinite() } }) {
                throw StrictBayesianNumericalException("NONFINITE_STATE: user-visible response")
            }
        }
    }

    private fun validateState(state: StrictBayesianV07State) {
        require(state.lag in design.designsByLag)
        require(state.localScaleSquaredBySource.keys == sources.toSet())
        require(state.localAuxiliaryBySource.keys == sources.toSet())
        val values = listOf(
            state.globalCandidateScaleSquared,
            state.globalCandidateAuxiliary,
            state.dynamicScaleSquared,
            state.dynamicAuxiliary
        ) + state.localScaleSquaredBySource.values + state.localAuxiliaryBySource.values
        if (values.any { !it.isFinite() || it <= 0.0 }) {
            throw StrictBayesianNumericalException("NONFINITE_STATE: horseshoe scale")
        }
    }

    internal data class CollapsedPosterior(
        val design: PreparedBvarLagDesign,
        val diagonalPrior: DoubleArray,
        val a: Array<DoubleArray>,
        val sn: Array<DoubleArray>,
        val logWeight: Double
    )
}

internal object StrictResponseTransformation {
    fun apply(raw: DoubleArray, rule: InverseTransformationRule): DoubleArray = when (rule) {
        InverseTransformationRule.IDENTITY -> raw.clone()
        InverseTransformationRule.CUMULATIVE_SUM -> raw.runningSum()
        InverseTransformationRule.EXPONENTIAL -> DoubleArray(raw.size) { index -> exp(raw[index]) - 1.0 }
        InverseTransformationRule.CUMULATIVE_EXPONENTIAL -> {
            val cumulative = raw.runningSum()
            DoubleArray(raw.size) { index -> (exp(cumulative[index]) - 1.0) * 100.0 }
        }
    }

    private fun DoubleArray.runningSum(): DoubleArray {
        var total = 0.0
        return DoubleArray(size) { index ->
            total += this[index]
            total
        }
    }
}
