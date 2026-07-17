package com.training.trackplanner.analysis.tissue

import java.time.LocalDate
import java.time.ZoneId

data class TissueWeightedSample(
    val value: Double,
    val weight: Double
)

data class TissueBaselineDiagnostic(
    val code: String,
    val message: String
)

data class TissuePersonalBaseline(
    val loadUnitStableKey: String,
    val boundaries: TissuePriorBoundaries?,
    val activeSamples: List<TissueWeightedSample>,
    val diagnostics: List<TissueBaselineDiagnostic>
) {
    val isValid: Boolean get() = boundaries != null
}

object TissueWeightedQuantilePolicy {
    fun quantile(samples: List<TissueWeightedSample>, probability: Double): Double {
        require(probability in 0.0..1.0)
        require(samples.isNotEmpty())
        require(samples.all { it.value.isFinite() && it.weight.isFinite() && it.weight > 0.0 })
        val ordered = samples.withIndex().sortedWith(
            compareBy<IndexedValue<TissueWeightedSample>> { it.value.value }.thenBy(IndexedValue<TissueWeightedSample>::index)
        )
        val target = ordered.sumOf { it.value.weight } * probability
        var cumulative = 0.0
        return ordered.firstOrNull { indexed ->
            cumulative += indexed.value.weight
            cumulative >= target
        }?.value?.value ?: ordered.last().value.value
    }
}

object TissuePersonalBaselinePolicy {
    fun derive(
        loadUnitStableKey: String,
        priorLoadUnitStableKey: String,
        meaningfulFloor: Double,
        calibrationWeight: TissuePerUnitCalibrationWeight,
        dailyResidualByDate: Map<LocalDate, Double>
    ): TissuePersonalBaseline {
        val diagnostics = mutableListOf<TissueBaselineDiagnostic>()
        if (loadUnitStableKey != priorLoadUnitStableKey) {
            diagnostics += diagnostic("PERSONAL_BASELINE_IDENTITY_MISMATCH", "Personal and prior load-unit identities differ.")
            return invalid(loadUnitStableKey, diagnostics)
        }
        if (!meaningfulFloor.isFinite() || meaningfulFloor < 0.0) {
            diagnostics += diagnostic("PERSONAL_BASELINE_INVALID_FLOOR", "The meaningful floor is invalid.")
            return invalid(loadUnitStableKey, diagnostics)
        }
        if (calibrationWeight.exposureWeight <= 0.0) {
            diagnostics += diagnostic("PERSONAL_BASELINE_INSUFFICIENT_EXPOSURE", "Personal exposure weight is zero.")
            return invalid(loadUnitStableKey, diagnostics)
        }
        val retained = calibrationWeight.history.dates.map { retainedDate ->
            val residual = dailyResidualByDate[retainedDate.date] ?: 0.0
            residual to retainedDate.weight
        }
        if (retained.any { (value, weight) -> !value.isFinite() || value < 0.0 || !weight.isFinite() || weight <= 0.0 }) {
            diagnostics += diagnostic("PERSONAL_BASELINE_NON_FINITE_SAMPLE", "Personal residual history contains an invalid sample.")
            return invalid(loadUnitStableKey, diagnostics)
        }
        val active = retained.mapNotNull { (value, weight) ->
            if (value > meaningfulFloor) TissueWeightedSample(value, weight) else null
        }
        if (active.isEmpty()) {
            diagnostics += diagnostic("PERSONAL_BASELINE_EMPTY_ACTIVE_DISTRIBUTION", "No retained residual is above the meaningful floor.")
            return invalid(loadUnitStableKey, diagnostics)
        }
        val q30 = TissueWeightedQuantilePolicy.quantile(active, 0.30)
        val q80 = TissueWeightedQuantilePolicy.quantile(active, 0.80)
        val q95 = TissueWeightedQuantilePolicy.quantile(active, 0.95)
        val boundaries = runCatching { TissuePriorBoundaries(meaningfulFloor, q30, q80, q95) }.getOrNull()
        if (boundaries == null) {
            diagnostics += diagnostic("PERSONAL_BASELINE_DEGENERATE_QUANTILES", "Personal weighted quantiles are not strictly ordered.")
            return invalid(loadUnitStableKey, diagnostics, active)
        }
        return TissuePersonalBaseline(loadUnitStableKey, boundaries, active, diagnostics)
    }

    private fun invalid(
        stableKey: String,
        diagnostics: List<TissueBaselineDiagnostic>,
        samples: List<TissueWeightedSample> = emptyList()
    ) = TissuePersonalBaseline(stableKey, null, samples, diagnostics)

    private fun diagnostic(code: String, message: String) = TissueBaselineDiagnostic(code, message)
}

class TissueHistoricalResidualSampler(
    private val residualCalculator: TissueResidualCalculator,
    private val zoneId: ZoneId
) {
    fun sampleUnit(
        loadUnitStableKey: String,
        events: List<TissueExposureEvent>,
        history: TissueCalibrationHistory,
        evaluationLocalHour: Int
    ): Map<LocalDate, Double> {
        require(evaluationLocalHour in 0..23)
        val unitEvents = events.filter { it.key.loadUnitStableKey == loadUnitStableKey }
        return history.dates.associate { retainedDate ->
            val asOf = evaluationEpochMillis(retainedDate.date, evaluationLocalHour, zoneId)
            retainedDate.date to unitEvents.asSequence()
                .mapNotNull { residualCalculator.calculate(it, asOf) }
                .sumOf { it.currentResidualRange.upper }
        }
    }

    companion object {
        fun evaluationEpochMillis(date: LocalDate, localHour: Int, zoneId: ZoneId): Long {
            require(localHour in 0..23)
            return date.atTime(localHour, 0).atZone(zoneId).toInstant().toEpochMilli()
        }
    }
}
