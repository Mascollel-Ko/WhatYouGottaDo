package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictPosteriorSummary
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureDescriptor
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate

internal data class StrictLabAnalysisRequest(
    val xFeature: AnalysisFeatureKey,
    val yFeatures: List<AnalysisFeatureKey>,
    val controls: List<AnalysisFeatureKey>,
    val requestedHorizon: Int = 2
) {
    fun normalized(): StrictLabAnalysisRequest = copy(
        yFeatures = yFeatures.distinct().filterNot { it == xFeature },
        controls = controls.distinct().filterNot { it == xFeature || it in yFeatures }
    )
}

internal data class StrictLabFeatureOption(
    val key: AnalysisFeatureKey,
    val displayName: String,
    val family: AnalysisFeatureFamily,
    val availableWeeks: Int,
    val firstAvailableWeek: LocalDate?,
    val lastAvailableWeek: LocalDate?,
    val enabled: Boolean,
    val disabledReason: String?
)

internal data class StrictLabFeatureCatalog(
    val xFeatures: List<StrictLabFeatureOption>,
    val responseFeatures: List<StrictLabFeatureOption>,
    val controlFeatures: List<StrictLabFeatureOption>,
    val snapshotFingerprint: String?
) {
    fun option(key: AnalysisFeatureKey): StrictLabFeatureOption? =
        (xFeatures + responseFeatures + controlFeatures).firstOrNull { it.key == key }

    companion object {
        val EMPTY = StrictLabFeatureCatalog(emptyList(), emptyList(), emptyList(), null)

        fun from(snapshot: WeeklyAnalysisFeatureSnapshot): StrictLabFeatureCatalog {
            val options = snapshot.descriptors.values.map { descriptor ->
                val availability = snapshot.featureAvailabilityIndex.getValue(descriptor.featureKey)
                val enabled = availability.hasData && availability.hasVariation
                StrictLabFeatureOption(
                    key = descriptor.featureKey,
                    displayName = displayName(descriptor),
                    family = descriptor.family,
                    availableWeeks = availability.activeWeeks,
                    firstAvailableWeek = availability.firstAvailableWeek,
                    lastAvailableWeek = availability.lastAvailableWeek,
                    enabled = enabled,
                    disabledReason = when {
                        !availability.hasData -> "기록된 주간 값이 없습니다."
                        !availability.hasVariation -> "주간 값의 변화가 아직 없습니다."
                        else -> null
                    }
                )
            }
            val byName = compareBy<StrictLabFeatureOption> { it.displayName }.thenBy { it.key.value }
            val responses = options.filter { option ->
                option.family in setOf(
                    AnalysisFeatureFamily.RECOVERY_CHECK_IN,
                    AnalysisFeatureFamily.PERFORMANCE,
                    AnalysisFeatureFamily.PERSISTENT_PERFORMANCE
                )
            }.sortedWith(byName)
            val x = options.filterNot { option ->
                option.family in setOf(
                    AnalysisFeatureFamily.EXPOSURE_INDICATOR,
                    AnalysisFeatureFamily.CUMULATIVE_OR_UNKNOWN
                )
            }.sortedWith(byName)
            val controls = options.filterNot { it.family == AnalysisFeatureFamily.CUMULATIVE_OR_UNKNOWN }
                .sortedWith(byName)
            return StrictLabFeatureCatalog(x, responses, controls, snapshot.fingerprint)
        }

        private fun displayName(descriptor: AnalysisFeatureDescriptor): String {
            val metric = TrendMetricId.entries.firstOrNull { it.stableId == descriptor.featureKey.value }
            return metric?.let { AnalysisMetricRegistry.descriptor(it)?.displayName }
                ?: descriptor.displayName
        }
    }
}

internal enum class StrictLabExecutionStage {
    PREPARING_STRICT_INPUT,
    STABILIZING_CHAINS,
    SAMPLING_POSTERIOR,
    CHECKING_PRECISION,
    EXTENDING_SAMPLING,
    SUMMARIZING_POSTERIOR
}

internal enum class StrictLabBlockerCode {
    INVALID_HORIZON,
    RESPONSE_REQUIRED,
    FEATURE_UNAVAILABLE,
    INSUFFICIENT_VARIATION,
    SNAPSHOT_NOT_READY,
    PHASE_A_INELIGIBLE
}

internal data class StrictLabBlocker(
    val code: StrictLabBlockerCode,
    val feature: AnalysisFeatureKey? = null,
    val detail: String? = null
)

internal data class StrictLabPreflight(
    val snapshotFingerprint: String,
    val availableFrom: LocalDate?,
    val availableUntil: LocalDate?,
    val closedWeeks: Int,
    val blockers: List<StrictLabBlocker>,
    val warnings: List<String>
) {
    val canAnalyze: Boolean
        get() = blockers.isEmpty()
}

internal data class StrictLabResponsePoint(
    val horizonWeeks: Int,
    val estimate: Double,
    val low80: Double,
    val high80: Double,
    val diagnostics: StrictPosteriorSummary
)

internal data class StrictLabResponse(
    val feature: AnalysisFeatureKey,
    val displayName: String,
    val points: List<StrictLabResponsePoint>
)

internal data class StrictBayesianLabResult(
    val request: StrictLabAnalysisRequest,
    val responses: List<StrictLabResponse>,
    val officialLagProbability: Map<Int, Double>,
    val simplificationDiagnostics: List<String>,
    val summary: String,
    val preparedInputFingerprint: String,
    val posteriorFingerprint: String
)

internal enum class StrictLabFailureCode {
    DATA_NOT_READY,
    PREFLIGHT_INELIGIBLE,
    FOCAL_FEATURE_UNAVAILABLE,
    NO_TARGET_VARIATION,
    NO_FOCAL_VARIATION,
    NO_FEASIBLE_COMMON_LAG_PLAN,
    METADATA_INCOMPLETE,
    REPRESENTATION_POLICY_UNAVAILABLE,
    REPRESENTATION_DIAGNOSTIC_CONFLICT,
    MCMC_CONVERGENCE_FAILED,
    LAG_POSTERIOR_MIXING_FAILED,
    MONTE_CARLO_PRECISION_NOT_REACHED,
    NUMERICAL_SPD_FAILURE,
    NONFINITE_STATE,
    CANCELLED,
    STALE_RESULT_REJECTED,
    INTERNAL_ERROR
}

internal sealed interface StrictLabExecutionOutcome {
    data class Success(val result: StrictBayesianLabResult) : StrictLabExecutionOutcome
    data class Failure(
        val code: StrictLabFailureCode,
        val message: String,
        val diagnostics: List<String>,
        val diagnosticId: String? = null
    ) : StrictLabExecutionOutcome
}

internal sealed interface StrictBayesianLabUiState {
    data object Idle : StrictBayesianLabUiState
    data class DataPreparing(val request: StrictLabAnalysisRequest) : StrictBayesianLabUiState
    data class PreflightReady(
        val request: StrictLabAnalysisRequest,
        val preflight: StrictLabPreflight
    ) : StrictBayesianLabUiState
    data class Running(
        val requestToken: Long,
        val request: StrictLabAnalysisRequest,
        val preflight: StrictLabPreflight,
        val stage: StrictLabExecutionStage
    ) : StrictBayesianLabUiState
    data class Success(
        val request: StrictLabAnalysisRequest,
        val result: StrictBayesianLabResult,
        val preflight: StrictLabPreflight
    ) : StrictBayesianLabUiState
    data class Failed(
        val request: StrictLabAnalysisRequest,
        val preflight: StrictLabPreflight?,
        val code: StrictLabFailureCode,
        val message: String,
        val diagnostics: List<String>,
        val diagnosticId: String?
    ) : StrictBayesianLabUiState
}
