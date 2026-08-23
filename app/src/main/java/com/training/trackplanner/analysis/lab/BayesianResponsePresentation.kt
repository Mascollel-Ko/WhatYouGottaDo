package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import java.util.Locale
import kotlin.math.abs

internal object StrictLabShockDefinitionFactory {
    fun standardizedTrainingRowShock(
        feature: AnalysisFeatureKey,
        displayName: String
    ): StrictLabShockDefinition = StrictLabShockDefinition(
        feature = feature,
        displayName = displayName,
        humanDescription =
            "앱에서 분석에 맞게 변환한 $displayName 값이, 분석에 사용된 공통 주간 기록의 표본표준편차 1개만큼 증가한 경우",
        standardizedMagnitude = 1.0,
        originalUnitMagnitude = null
    )
}

internal object BayesianResponsePresentationFactory {
    fun create(
        shockDefinition: StrictLabShockDefinition,
        responses: List<StrictLabResponse>,
        lagProbabilities: Map<Int, Double>,
        samplingClassification: StrictSamplingDiagnosticClassification
    ): StrictLabResponsePresentation {
        val interpretations = responses.map(::interpret)
        val diagnosticNotice = when (samplingClassification) {
            StrictSamplingDiagnosticClassification.STRICT,
            StrictSamplingDiagnosticClassification.NOT_APPLICABLE -> ""
            StrictSamplingDiagnosticClassification.RELAXED ->
                "이 결과는 완화 진단 기준을 충족했습니다."
            StrictSamplingDiagnosticClassification.LIMITED ->
                "결과는 계산되었지만 표본추출 진단이 제한적이므로 더 주의해서 해석하세요."
        }
        val overall = buildString {
            append(interpretations.joinToString(" ") { it.summary })
            if (interpretations.isNotEmpty()) {
                append(' ')
                append("이 반응은 현재 기록과 모형에 조건부이며 인과관계를 확정하지 않습니다.")
            }
            if (diagnosticNotice.isNotEmpty()) {
                append(' ')
                append(diagnosticNotice)
            }
        }
        return StrictLabResponsePresentation(
            shockDefinition = shockDefinition,
            responseInterpretations = interpretations,
            overallSummary = overall,
            lagExplanation = lagExplanation(lagProbabilities)
        )
    }

    private fun interpret(response: StrictLabResponse): StrictLabResponseInterpretation {
        require(response.points.isNotEmpty())
        val ordered = response.points.sortedBy { it.horizonWeeks }
        val increasing = ordered.filter { it.low80 > 0.0 }.map { it.horizonWeeks }
        val decreasing = ordered.filter { it.high80 < 0.0 }.map { it.horizonWeeks }
        val uncertain = ordered.filter { it.low80 <= 0.0 && it.high80 >= 0.0 }.map { it.horizonWeeks }
        val peak = ordered.maxBy { abs(it.estimate) }.horizonWeeks
        val direction = buildList {
            if (increasing.isNotEmpty()) add("${increasing.weekList()} 후에는 증가 방향이 비교적 일관되게 나타났습니다.")
            if (decreasing.isNotEmpty()) add("${decreasing.weekList()} 후에는 감소 방향이 비교적 일관되게 나타났습니다.")
            if (uncertain.isNotEmpty()) add("${uncertain.weekList()} 후에는 80% 구간이 0을 포함해 방향이 불확실합니다.")
        }.joinToString(" ")
        return StrictLabResponseInterpretation(
            feature = response.feature,
            displayName = response.displayName,
            summary = "${response.displayName}의 반응은 $direction 중앙값 기준 가장 큰 반응은 ${peak}주 후에 나타났습니다.",
            peakMedianHorizonWeeks = peak
        )
    }

    private fun lagExplanation(probabilities: Map<Int, Double>): String {
        if (probabilities.isEmpty()) return "비교할 시차 posterior가 없습니다."
        val (lag, probability) = probabilities.maxWith(compareBy<Map.Entry<Int, Double>> { it.value }.thenByDescending { it.key })
        return "시차 모형 중에서는 ${lag}주 시차가 가장 높은 posterior 비중(${percent(probability)})을 가졌습니다." +
            " " +
            "다른 시차에도 비중이 분포할 수 있으므로 특정 한 주를 효과 발생 시점으로 단정하지 말고 주별 반응 경로를 함께 보세요."
    }

    private fun List<Int>.weekList(): String = joinToString(", ") { "${it}주" }

    private fun percent(value: Double): String = String.format(Locale.US, "%.1f%%", value * 100.0)
}
