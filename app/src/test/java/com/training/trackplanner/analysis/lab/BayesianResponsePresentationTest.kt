package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictPosteriorSummary
import com.training.trackplanner.analysis.trends.TrendMetricId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BayesianResponsePresentationTest {
    @Test
    fun `interpretation describes interval direction and largest median horizon`() {
        val response = StrictLabResponse(
            Y,
            "피로 종합",
            listOf(
                point(1, 0.4, 0.1, 0.7),
                point(2, -0.9, -1.2, -0.2),
                point(3, 0.2, -0.1, 0.5)
            )
        )

        val interpretation = presentation(listOf(response)).responseInterpretations.single()

        assertTrue("1주 후에는 증가 방향" in interpretation.summary)
        assertTrue("2주 후에는 감소 방향" in interpretation.summary)
        assertTrue("3주 후에는 80% 구간이 0을 포함해 방향이 불확실" in interpretation.summary)
        assertTrue("중앙값 기준 가장 큰 반응은 2주 후" in interpretation.summary)
        assertEquals(2, interpretation.peakMedianHorizonWeeks)
        assertFalse("통계적으로 유의" in interpretation.summary)
    }

    @Test
    fun `shock description states the production standardized training-row shock without invented units`() {
        val shock = StrictLabShockDefinitionFactory.standardizedTrainingRowShock(X, "배드민턴 훈련 부하")

        assertEquals(1.0, shock.standardizedMagnitude, 0.0)
        assertNull(shock.originalUnitMagnitude)
        assertEquals(
            "앱에서 분석에 맞게 변환한 배드민턴 훈련 부하 값이, 분석에 사용된 공통 주간 기록의 표본표준편차 1개만큼 증가한 경우",
            shock.humanDescription
        )
        assertFalse("약 +" in shock.humanDescription)
    }

    @Test
    fun `lag explanation identifies the highest posterior weight without causal timing language`() {
        val explanation = presentation(lags = mapOf(1 to 0.25, 2 to 0.6, 3 to 0.15)).lagExplanation

        assertTrue("2주 시차가 가장 높은 posterior 비중(60.0%)" in explanation)
        assertTrue("주별 반응 경로를 함께 보세요" in explanation)
        assertFalse("효과가 발생할 확률" in explanation)
    }

    @Test
    fun `presentation is read-only and keeps existing posterior values and lag probabilities`() {
        val responses = listOf(StrictLabResponse(Y, "피로 종합", listOf(point(1, -0.3, -0.7, 0.1))))
        val lags = linkedMapOf(1 to 0.7, 2 to 0.3)
        val responseSnapshot = responses.single().points.single().copy()
        val lagSnapshot = lags.toMap()

        val presentation = presentation(responses, lags, StrictSamplingDiagnosticClassification.LIMITED)

        assertEquals(responseSnapshot, responses.single().points.single())
        assertEquals(lagSnapshot, lags)
        assertTrue("표본추출 진단이 제한적" in presentation.overallSummary)
        assertTrue("인과관계를 확정하지 않습니다" in presentation.overallSummary)
    }

    private fun presentation(
        responses: List<StrictLabResponse> = listOf(StrictLabResponse(Y, "피로 종합", listOf(point(1, 0.1, -0.2, 0.4)))),
        lags: Map<Int, Double> = mapOf(1 to 1.0),
        classification: StrictSamplingDiagnosticClassification = StrictSamplingDiagnosticClassification.STRICT
    ): StrictLabResponsePresentation {
        val shock = StrictLabShockDefinitionFactory.standardizedTrainingRowShock(X, "배드민턴 훈련 부하")
        return BayesianResponsePresentationFactory.create(shock, responses, lags, classification)
    }

    private fun point(horizon: Int, median: Double, low80: Double, high80: Double): StrictLabResponsePoint =
        StrictLabResponsePoint(
            horizon,
            median,
            low80,
            high80,
            StrictPosteriorSummary(median, median, low80, high80, 1.0, 100.0, 100.0, 0.05)
        )

    private companion object {
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
    }
}
