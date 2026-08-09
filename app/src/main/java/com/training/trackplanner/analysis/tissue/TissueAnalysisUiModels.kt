package com.training.trackplanner.analysis.tissue

data class TissueAnalysisChildUi(
    val key: String,
    val displayCode: String,
    val name: String,
    val info: TissueEducationalInfo,
    val status: TissueCanonicalStatus,
    val recoveryRange: String,
    val contributors: List<TissueExerciseContribution>
)

data class TissueAnalysisJointUi(
    val key: String,
    val displayCode: String,
    val name: String,
    val info: TissueEducationalInfo,
    val status: TissueCanonicalStatus,
    val highChildCount: Int,
    val highestChild: TissueEducationalInfo?,
    val contributors: List<TissueExerciseContribution>,
    val children: List<TissueAnalysisChildUi>
)

data class TissueBaselineProvenanceUi(
    val source: TissueBaselineProvenance
)

data class TissueAnalysisUiState(
    val status: TissueCanonicalStatus,
    val topAreas: List<TissueEducationalInfo>,
    val joints: List<TissueAnalysisJointUi>,
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
    val topAreas: List<TissueEducationalInfo>
)

object TissueAnalysisUiMapper {
    fun summary(state: TissueCurrentState?): TissueSummaryNavigationUi {
        return TissueSummaryNavigationUi(
            status = state?.ofiSummary?.status,
            topAreas = state?.ofiSummary?.topJointComplexes
                ?.map(TissueJointComplexSummary::educationalInfo)
                .orEmpty()
        )
    }

    fun map(state: TissueCurrentState): TissueAnalysisUiState =
        TissueAnalysisUiState(
            status = state.ofiSummary.status,
            topAreas = state.ofiSummary.topJointComplexes.map(TissueJointComplexSummary::educationalInfo),
            joints = state.jointComplexes.map { joint ->
                TissueAnalysisJointUi(
                    key = joint.jointComplexStableKey,
                    displayCode = joint.jointComplexCode,
                    name = joint.nameKo,
                    info = joint.educationalInfo,
                    status = joint.status,
                    highChildCount = joint.highOrVeryHighChildCount,
                    highestChild = joint.highestChild?.educationalInfo,
                    contributors = joint.contributors,
                    children = joint.childStates.map { child ->
                        TissueAnalysisChildUi(
                            key = "${child.key.loadUnitStableKey}|${child.key.loadDimension}",
                            displayCode = child.loadUnitCode,
                            name = child.loadUnitName,
                            info = child.educationalInfo,
                            status = child.status,
                            recoveryRange = range(child.rawResidual),
                            contributors = child.contributors
                        )
                    }
                )
            },
            provenance = TissueBaselineProvenanceUi(state.baselineProvenance)
        )

    private fun range(value: TissueResidualRange): String =
        java.lang.String.format(java.util.Locale.US, "%.2f~%.2f", value.lower, value.upper)
}
