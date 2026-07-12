package com.training.trackplanner.analysis.lab.pipeline

internal class FutureBvarInput private constructor(
    val view: BvarPreparedView,
    val rowPlan: PreparedRowPlan,
    val scalingPlan: PreparedScalingPlan,
    val priorFingerprint: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            view: BvarPreparedView,
            rowPlan: PreparedRowPlan,
            scalingPlan: PreparedScalingPlan,
            priorFingerprint: String
        ): FutureBvarInput {
            require(priorFingerprint.isNotBlank())
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(scalingPlan.sourceViewFingerprint == view.fingerprint)
            require(scalingPlan.sourceRowPlanFingerprint == rowPlan.fingerprint)
            return FutureBvarInput(
                view,
                rowPlan,
                scalingPlan,
                priorFingerprint,
                strictFingerprint(listOf(view.fingerprint, rowPlan.fingerprint, scalingPlan.fingerprint, priorFingerprint))
            )
        }
    }
}

internal enum class PosteriorPropagationPolicy {
    DRAW_BY_DRAW_WITHOUT_MEAN_SHOCK_COLLAPSE
}

internal class FutureBlpInput private constructor(
    val view: BlpPreparedView,
    val rowPlan: PreparedRowPlan,
    val identifiedShockPosterior: IdentifiedShockPosterior,
    val horizonPolicy: HorizonPolicy,
    val posteriorPropagationPolicy: PosteriorPropagationPolicy,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            view: BlpPreparedView,
            rowPlan: PreparedRowPlan,
            identifiedShockPosterior: IdentifiedShockPosterior,
            horizonPolicy: HorizonPolicy,
            posteriorPropagationPolicy: PosteriorPropagationPolicy = PosteriorPropagationPolicy.DRAW_BY_DRAW_WITHOUT_MEAN_SHOCK_COLLAPSE
        ): FutureBlpInput {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(identifiedShockPosterior.sourceContextFingerprint == view.rootContextFingerprint)
            require(view.responseScalePlansByMetric.isNotEmpty())
            return FutureBlpInput(
                view,
                rowPlan,
                identifiedShockPosterior,
                horizonPolicy,
                posteriorPropagationPolicy,
                strictFingerprint(
                    listOf(
                        view.fingerprint,
                        rowPlan.fingerprint,
                        identifiedShockPosterior.fingerprint,
                        horizonPolicy.name,
                        posteriorPropagationPolicy.name
                    )
                )
            )
        }
    }
}

internal class FutureJohansenInput private constructor(
    val view: JohansenPreparedView,
    val rowPlan: PreparedRowPlan,
    val fingerprint: String
) {
    companion object {
        fun createValidated(view: JohansenPreparedView, rowPlan: PreparedRowPlan): FutureJohansenInput {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            return FutureJohansenInput(view, rowPlan, strictFingerprint(listOf(view.fingerprint, rowPlan.fingerprint)))
        }
    }
}

internal class FutureVecmInput private constructor(
    val view: VecmPreparedView,
    val rowPlan: PreparedRowPlan,
    val rankConfigurationFingerprint: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            view: VecmPreparedView,
            rowPlan: PreparedRowPlan,
            rankConfigurationFingerprint: String
        ): FutureVecmInput {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(rankConfigurationFingerprint.isNotBlank())
            return FutureVecmInput(
                view,
                rowPlan,
                rankConfigurationFingerprint,
                strictFingerprint(listOf(view.fingerprint, rowPlan.fingerprint, rankConfigurationFingerprint))
            )
        }
    }
}
