package com.training.trackplanner.data

import android.content.Context
import com.training.trackplanner.analysis.tissue.TissueCurrentState
import com.training.trackplanner.analysis.tissue.TissueCurrentStateAggregator
import com.training.trackplanner.analysis.tissue.TissueExposureEvent
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import com.training.trackplanner.analysis.tissue.TissueRcvEventLedgerBuilder
import com.training.trackplanner.analysis.tissue.TissueRcvLoadKey
import com.training.trackplanner.analysis.tissue.TissueRecoveryCurveRepository
import com.training.trackplanner.analysis.tissue.TissueResidualCalculator
import com.training.trackplanner.analysis.tissue.TissueSymptomOverride
import com.training.trackplanner.analysis.tissue.TissueWorkoutRecord
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal class ConnectiveTissueAnalysisService(
    context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val catalog = TissueRcvAssetRepository.fromAssets(context).catalog
    private val curves = TissueRecoveryCurveRepository(catalog.curves)

    suspend fun build(nowEpochMillis: Long = System.currentTimeMillis()): TissueCurrentState {
        val today = java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val exercisesById = exerciseDao.allExercises().associateBy(Exercise::id)
        val bodyWeightKg = initialUserProfileDao.profile()?.bodyWeightKg
        val records = workoutDao.allEntriesWithSets().mapNotNull { record ->
            if (record.sets.none(WorkoutSet::confirmed)) return@mapNotNull null
            val exercise = exercisesById[record.entry.exerciseId] ?: return@mapNotNull null
            TissueWorkoutRecord.from(record, exercise, bodyWeightKg)
        }
        val ledger = TissueRcvEventLedgerBuilder(catalog, zoneId).build(records)
        val residualCalculator = TissueResidualCalculator(curves, zoneId)
        val currentResiduals = ledger.events.mapNotNull { residualCalculator.calculate(it, nowEpochMillis) }
        val firstDate = records.minOfOrNull(TissueWorkoutRecord::date)
        val observationDays = firstDate?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 0L
        val history = if (observationDays >= 56) {
            historyByKey(ledger.events, residualCalculator, today)
        } else {
            emptyMap()
        }
        val override = when (dailyCheckInDao.getForDate(today.toString())?.jointTendonDiscomfort) {
            5 -> TissueSymptomOverride.BLOCK
            4 -> TissueSymptomOverride.CAUTION
            else -> TissueSymptomOverride.NONE
        }
        val keys = currentResiduals.mapTo(linkedSetOf()) { it.event.key }.apply {
            catalog.loadUnits.values.filter { unit -> none { it.loadUnitStableKey == unit.stableKey } }
                .mapTo(this) { TissueRcvLoadKey(it.stableKey, "UNOBSERVED") }
        }
        val state = TissueCurrentStateAggregator(catalog).aggregate(
            residuals = currentResiduals,
            observationDays = observationDays,
            historyByKey = history,
            symptomOverrides = keys.associateWith { override }
        )
        return state.copy(diagnostics = (ledger.diagnostics + state.diagnostics).distinct())
    }

    private fun historyByKey(
        events: List<TissueExposureEvent>,
        residualCalculator: TissueResidualCalculator,
        today: LocalDate
    ): Map<TissueRcvLoadKey, List<Double>> {
        val samples = mutableMapOf<TissueRcvLoadKey, MutableList<Double>>()
        (1L..56L).forEach { daysAgo ->
            val asOf = today.minusDays(daysAgo).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
            events.mapNotNull { residualCalculator.calculate(it, asOf) }
                .groupBy { it.event.key }
                .forEach { (key, rows) ->
                    samples.getOrPut(key, ::mutableListOf).add(rows.sumOf { it.currentResidualRange.upper })
                }
        }
        return samples
    }
}
