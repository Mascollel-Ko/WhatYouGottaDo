package com.training.trackplanner.analysis.tissue

data class TissueAnalysisChildUi(
    val key: String,
    val name: String,
    val info: TissueEducationalInfo,
    val infoContentDescription: String,
    val status: String,
    val recoveryRange: String,
    val contributors: String,
    val diagnostics: String
)

data class TissueAnalysisJointUi(
    val key: String,
    val name: String,
    val info: TissueEducationalInfo,
    val infoContentDescription: String,
    val status: String,
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
) {
    fun visibleJoints(showAll: Boolean): List<TissueAnalysisJointUi> =
        if (showAll) joints else joints.take(3)

    fun info(stableKey: String): TissueEducationalInfo? =
        joints.asSequence().flatMap { joint ->
            sequenceOf(joint.info) + joint.children.asSequence().map(TissueAnalysisChildUi::info)
        }.firstOrNull { it.stableKey == stableKey }
}

data class TissueSummaryNavigationUi(
    val title: String,
    val supportingText: String,
    val status: String?,
    val topAreas: String?,
    val actionLabel: String
)

object TissueAnalysisUiMapper {
    fun summary(state: TissueCurrentState?): TissueSummaryNavigationUi {
        return TissueSummaryNavigationUi(
            title = "연결조직 분석",
            supportingText = "관절·건·인대 등 연결조직에 남아 있을 상대적인 운동 부하를 확인합니다.",
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
                    info = joint.educationalInfo,
                    infoContentDescription = "${joint.nameKo} 정보 보기",
                    status = statusLabel(joint.status),
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
                            info = child.educationalInfo,
                            infoContentDescription = "${child.loadUnitName} 정보 보기",
                            status = statusLabel(child.status),
                            recoveryRange = range(child.rawResidual),
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
        TissueCanonicalStatus.VERY_HIGH -> "매우 높은 편"
        TissueCanonicalStatus.HIGH -> "높은 편"
        TissueCanonicalStatus.MODERATE -> "평소 범위"
        TissueCanonicalStatus.LOW -> "낮은 편"
        TissueCanonicalStatus.CALIBRATING -> "판단 불가"
        TissueCanonicalStatus.UNAVAILABLE -> "판단 불가"
    }

    private fun range(value: TissueResidualRange): String =
        java.lang.String.format(java.util.Locale.US, "%.2f~%.2f", value.lower, value.upper)
}
