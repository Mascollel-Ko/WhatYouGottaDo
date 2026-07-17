package com.training.trackplanner.analysis.tissue

data class TissuePerUnitCalibrationWeight(
    val loadUnitStableKey: String,
    val weightedValidObservationDays: Double,
    val weightedDistinctExposureDays: Double,
    val spanWeight: Double,
    val exposureWeight: Double,
    val value: Double,
    val history: TissueCalibrationHistory
)

object TissuePerUnitWeightPolicy {
    const val FULL_SPAN_DAYS = 56.0
    const val EXPOSURE_GRACE_DAYS = 3.0
    const val EXPOSURE_RAMP_DAYS = 9.0

    fun calculate(
        loadUnitStableKey: String,
        history: TissueCalibrationHistory
    ): TissuePerUnitCalibrationWeight {
        require(loadUnitStableKey.isNotBlank())
        val observationDays = history.weightedValidObservationDays
        val exposureDays = history.weightedDistinctExposureDays
        require(observationDays.isFinite() && observationDays >= 0.0)
        require(exposureDays.isFinite() && exposureDays >= 0.0)
        val spanWeight = (observationDays / FULL_SPAN_DAYS).coerceIn(0.0, 1.0)
        val exposureWeight = ((exposureDays - EXPOSURE_GRACE_DAYS) / EXPOSURE_RAMP_DAYS)
            .coerceIn(0.0, 1.0)
        return TissuePerUnitCalibrationWeight(
            loadUnitStableKey = loadUnitStableKey,
            weightedValidObservationDays = observationDays,
            weightedDistinctExposureDays = exposureDays,
            spanWeight = spanWeight,
            exposureWeight = exposureWeight,
            value = minOf(spanWeight, exposureWeight),
            history = history
        )
    }

    fun calculateAll(
        anchorDate: java.time.LocalDate?,
        confirmedWorkoutDates: Collection<java.time.LocalDate>,
        unitExposureDates: Map<String, Collection<java.time.LocalDate>>
    ): Map<String, TissuePerUnitCalibrationWeight> = unitExposureDates.mapValues { (stableKey, exposureDates) ->
        calculate(
            stableKey,
            TissueCalibrationHistoryPolicy.build(anchorDate, confirmedWorkoutDates, exposureDates)
        )
    }
}
