package com.training.trackplanner.analysis.tissue

import java.util.Locale

data class TissueAnalysisChildUi(
    val key: String,
    val name: String,
    val status: String,
    val score: String,
    val recoveryRange: String,
    val recoveryTrend: String,
    val contributors: String,
    val diagnostics: String
)

data class TissueAnalysisJointUi(
    val key: String,
    val name: String,
    val status: String,
    val score: String,
    val highChildCount: Int,
    val highestChild: String,
    val contributors: String,
    val children: List<TissueAnalysisChildUi>
)

data class TissueAnalysisUiState(
    val status: String,
    val topAreas: String,
    val joints: List<TissueAnalysisJointUi>,
    val diagnostics: List<String>
)

data class TissueSummaryNavigationUi(
    val title: String,
    val supportingText: String,
    val status: String?,
    val topAreas: String?,
    val actionLabel: String
)

object TissueAnalysisUiMapper {
    fun summary(state: TissueCurrentState?): TissueSummaryNavigationUi {
        val calibrating = state == null || state.ofiSummary.status == TissueCanonicalStatus.CALIBRATING
        return TissueSummaryNavigationUi(
            title = "연결조직 분석",
            supportingText = if (calibrating) {
                "개인 기록을 바탕으로 연결조직 기준을 보정하고 있습니다."
            } else {
                "관절·건·인대 등 연결조직의 상태와 주요 기여 운동을 별도 분석에서 확인합니다."
            },
            status = state?.let { statusLabel(it.ofiSummary.status) },
            topAreas = state?.ofiSummary?.topJointComplexes
                ?.joinToString { it.nameKo }
                ?.ifBlank { null },
            actionLabel = "연결조직 분석 보기"
        )
    }

    fun map(state: TissueCurrentState): TissueAnalysisUiState =
        TissueAnalysisUiState(
            status = statusLabel(state.ofiSummary.status),
            topAreas = state.ofiSummary.topJointComplexes.joinToString { it.nameKo }.ifBlank { "없음" },
            joints = state.jointComplexes.map { joint ->
                TissueAnalysisJointUi(
                    key = joint.jointComplexStableKey,
                    name = joint.nameKo,
                    status = statusLabel(joint.status),
                    score = score(joint.displayScore),
                    highChildCount = joint.highOrVeryHighChildCount,
                    highestChild = joint.highestChild?.loadUnitName.orEmpty().ifBlank { "관찰 중" },
                    contributors = joint.contributors.joinToString { it.exerciseName }.ifBlank { "기여 기록 없음" },
                    children = joint.childStates.map { child ->
                        TissueAnalysisChildUi(
                            key = "${child.key.loadUnitStableKey}|${child.key.loadDimension}",
                            name = if (child.key.loadDimension == "UNOBSERVED") {
                                child.loadUnitName
                            } else {
                                "${child.loadUnitName} · ${child.key.loadDimension}"
                            },
                            status = statusLabel(child.status),
                            score = score(child.normalizedScore),
                            recoveryRange = range(child.rawResidual),
                            recoveryTrend = trend(child.recentResidualHistory),
                            contributors = child.contributors.joinToString { it.exerciseName }
                                .ifBlank { "기여 기록 없음" },
                            diagnostics = buildList {
                                if (child.timestampPrecisions.isNotEmpty()) {
                                    add("시간: ${child.timestampPrecisions.joinToString()}")
                                }
                                if (child.evidenceGrades.isNotEmpty()) {
                                    add("근거: ${child.evidenceGrades.joinToString()}")
                                }
                                if (child.symptomOverride != TissueSymptomOverride.NONE) {
                                    add("증상 override: ${child.symptomOverride}")
                                }
                                addAll(child.diagnostics)
                            }.distinct().joinToString(" · ")
                        )
                    }
                )
            },
            diagnostics = state.diagnostics
        )

    fun statusLabel(status: TissueCanonicalStatus): String = when (status) {
        TissueCanonicalStatus.VERY_HIGH -> "매우 높음"
        TissueCanonicalStatus.HIGH -> "높음"
        TissueCanonicalStatus.MODERATE -> "보통"
        TissueCanonicalStatus.LOW -> "낮음"
        TissueCanonicalStatus.CALIBRATING -> "보정 중"
        TissueCanonicalStatus.UNAVAILABLE -> "계산 불가"
    }

    private fun score(value: Double?): String =
        value?.let { String.format(Locale.US, "%.0f", it) } ?: "보정 중"

    private fun range(value: TissueResidualRange): String =
        String.format(Locale.US, "%.2f~%.2f", value.lower, value.upper)

    private fun trend(values: List<Double>): String =
        if (values.isEmpty()) {
            "56일 보정 후 표시"
        } else {
            String.format(Locale.US, "최근 56일 %.2f~%.2f", values.min(), values.max())
        }
}
