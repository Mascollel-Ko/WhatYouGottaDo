package com.training.trackplanner.analysis.tissue

data class TissueAnalysisChildUi(
    val key: String,
    val name: String,
    val info: TissueEducationalInfo,
    val status: TissueCanonicalStatus,
    val recoveryRange: String,
    val contributors: String?,
    val diagnostics: String
)

data class TissueAnalysisJointUi(
    val key: String,
    val name: String,
    val info: TissueEducationalInfo,
    val status: TissueCanonicalStatus,
    val highChildCount: Int,
    val highestChild: String?,
    val contributors: String?,
    val children: List<TissueAnalysisChildUi>
)

data class TissueBaselineProvenanceUi(
    val source: TissueBaselineProvenance
)

data class TissueAnalysisUiState(
    val status: TissueCanonicalStatus,
    val topAreas: String?,
    val joints: List<TissueAnalysisJointUi>,
    val diagnostics: List<String>,
    val provenance: TissueBaselineProvenanceUi
) {
    fun visibleJoints(showAll: Boolean): List<TissueAnalysisJointUi> =
        if (showAll) joints else joints.take(3)

    fun info(stableKey: String): TissueEducationalInfo? =
        joints.asSequence().flatMap { joint ->
            sequenceOf(joint.info) + joint.children.asSequence().map(TissueAnalysisChildUi::info)
        }.firstOrNull { it.stableKey == stableKey }
}

data class TissueSummaryNavigationUi(
    val status: TissueCanonicalStatus?,
    val topAreas: String?
)

object TissueAnalysisUiMapper {
    fun summary(state: TissueCurrentState?): TissueSummaryNavigationUi {
        return TissueSummaryNavigationUi(
            status = state?.ofiSummary?.status,
            topAreas = state?.ofiSummary?.topJointComplexes
                ?.joinToString { it.nameKo }
                ?.takeIf { it.isNotBlank() }
        )
    }

    fun map(state: TissueCurrentState): TissueAnalysisUiState =
        TissueAnalysisUiState(
            status = state.ofiSummary.status,
            topAreas = state.ofiSummary.topJointComplexes.joinToString { it.nameKo }.takeIf { it.isNotBlank() },
            joints = state.jointComplexes.map { joint ->
                TissueAnalysisJointUi(
                    key = joint.jointComplexStableKey,
                    name = joint.nameKo,
                    info = joint.educationalInfo,
                    status = joint.status,
                    highChildCount = joint.highOrVeryHighChildCount,
                    highestChild = joint.highestChild?.loadUnitName?.takeIf { it.isNotBlank() },
                    contributors = joint.contributors.joinToString { it.exerciseName }.takeIf { it.isNotBlank() },
                    children = joint.childStates.map { child ->
                        TissueAnalysisChildUi(
                            key = "${child.key.loadUnitStableKey}|${child.key.loadDimension}",
                            name = if (child.key.loadDimension == "UNOBSERVED" ||
                                child.key.loadDimension == "UNIT_TOTAL"
                            ) {
                                child.loadUnitName
                            } else {
                                "${child.loadUnitName} · ${child.key.loadDimension}"
                            },
                            info = child.educationalInfo,
                            status = child.status,
                            recoveryRange = range(child.rawResidual),
                            contributors = child.contributors.joinToString { it.exerciseName }
                                .takeIf { it.isNotBlank() },
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
            diagnostics = state.diagnostics,
            provenance = TissueBaselineProvenanceUi(state.baselineProvenance)
        )

    private fun range(value: TissueResidualRange): String =
        java.lang.String.format(java.util.Locale.US, "%.2f~%.2f", value.lower, value.upper)
}
