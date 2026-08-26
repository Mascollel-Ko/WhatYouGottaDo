package com.training.trackplanner.data

import android.content.Context
import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.tissue.TissueCalibrationAnchorPolicy
import com.training.trackplanner.analysis.tissue.TissueCurrentState
import com.training.trackplanner.analysis.tissue.TissueCurrentStateAggregator
import com.training.trackplanner.analysis.tissue.TissueEffectiveBaseline
import com.training.trackplanner.analysis.tissue.TissueEffectiveBaselinePolicy
import com.training.trackplanner.analysis.tissue.TissueHistoricalResidualSampler
import com.training.trackplanner.analysis.tissue.TissueHabitualTrainingIntensity
import com.training.trackplanner.analysis.tissue.TissuePerUnitWeightPolicy
import com.training.trackplanner.analysis.tissue.TissuePersonalBaselinePolicy
import com.training.trackplanner.analysis.tissue.TissuePriorRegistryLoader
import com.training.trackplanner.analysis.tissue.TissuePriorUserProfileInputs
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import com.training.trackplanner.analysis.tissue.TissueRcvEventLedgerBuilder
import com.training.trackplanner.analysis.tissue.TissueRecoveryCurveRepository
import com.training.trackplanner.analysis.tissue.TissueResidualCalculator
import com.training.trackplanner.analysis.tissue.TissueSymptomOverride
import com.training.trackplanner.analysis.tissue.TissueWorkoutRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal class ConnectiveTissueAnalysisService(
    context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val catalog = TissueRcvAssetRepository.fromAssets(context).catalog
    private val curves = TissueRecoveryCurveRepository(catalog.curves)
    private val priorRegistry = TissuePriorRegistryLoader.fromAssets(context)

    suspend fun build(nowEpochMillis: Long = System.currentTimeMillis()): TissueCurrentState {
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val profile = initialUserProfileDao.profile()
        val dailyMetrics = dailyMetricDao.allMetrics()
        val checkIns = dailyCheckInDao.all()
        val exercisesById = exerciseDao.allExercises().associateBy(Exercise::stableKey)
        val records = workoutDao.allEntriesWithSets().mapNotNull { record ->
            if (record.sets.none(WorkoutSet::confirmed)) return@mapNotNull null
            val exercise = exercisesById[record.entry.exerciseStableKey] ?: return@mapNotNull null
            val bodyWeightKg = BodyweightEffectiveLoadCalculator.bodyWeightFor(
                record.entry.date,
                dailyMetrics,
                profile
            )
            TissueWorkoutRecord.from(record, exercise, bodyWeightKg)
        }
        val ledger = TissueRcvEventLedgerBuilder(catalog, zoneId).build(records)
        val residualCalculator = TissueResidualCalculator(curves, zoneId)
        val currentResiduals = ledger.events.mapNotNull { residualCalculator.calculate(it, nowEpochMillis) }
        val confirmedWorkoutDates = records.map(::recordLocalDate).toSet()
        val checkInDates = checkIns.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
        val anchorDate = TissueCalibrationAnchorPolicy.latestConfirmationDate(confirmedWorkoutDates, checkInDates)
        val exposureDatesByUnit = ledger.events.filter { it.initialExposure > 0.0 }.groupBy {
            it.key.loadUnitStableKey
        }.mapValues { (_, events) -> events.mapNotNull(::eventLocalDate).toSet() }
        val weights = TissuePerUnitWeightPolicy.calculateAll(
            anchorDate = anchorDate,
            confirmedWorkoutDates = confirmedWorkoutDates,
            unitExposureDates = catalog.loadUnits.keys.associateWith { exposureDatesByUnit[it].orEmpty() }
        )
        val sampler = TissueHistoricalResidualSampler(residualCalculator, zoneId)
        val historyByUnit = weights.mapValues { (stableKey, weight) ->
            sampler.sampleUnit(stableKey, ledger.events, weight.history, now.hour)
        }
        val profileInputs = TissuePriorUserProfileInputs(
            bodyWeightKg = BodyweightEffectiveLoadCalculator.bodyWeightFor(today.toString(), dailyMetrics, profile),
            strengthTrainingExperienceYears = profile?.strengthTrainingYears,
            racketSportExperienceYears = profile?.badmintonTrainingYears,
            habitualTrainingIntensity = profile?.habitualTrainingIntensity?.let {
                runCatching { TissueHabitualTrainingIntensity.valueOf(it) }.getOrNull()
            }
        )
        val effectiveBaselines = buildBaselines(
            localHour = now.hour,
            profileInputs = profileInputs,
            weights = weights,
            historyByUnit = historyByUnit
        )
        val todayCheckIn = dailyCheckInDao.getForDate(today.toString())
        val symptomPlan = tissueSymptomOverridePlan(
            discomfort = todayCheckIn?.jointTendonDiscomfort,
            selectedJointComplexKey = todayCheckIn?.jointTendonDiscomfortJointComplexKey,
            loadUnitJointComplexes = catalog.loadUnits.mapValues { it.value.jointComplexStableKey }
        )
        return TissueCurrentStateAggregator(catalog).aggregate(
            residuals = currentResiduals,
            effectiveBaselinesByUnit = effectiveBaselines,
            historyByUnit = historyByUnit.mapValues { (_, values) -> values.toSortedMap().values.toList() },
            symptomOverrides = symptomPlan.overridesByLoadUnit,
            diagnostics = buildList {
                addAll(ledger.diagnostics)
                priorRegistry.exceptionOrNull()?.let {
                    add("TISSUE_PRIOR_REGISTRY_INVALID: ${it.message ?: it::class.java.simpleName}")
                }
            }
        ).copy(hasUnscopedHighJointTendonDiscomfort = symptomPlan.unscopedHighDiscomfort)
    }

    private fun buildBaselines(
        localHour: Int,
        profileInputs: TissuePriorUserProfileInputs,
        weights: Map<String, com.training.trackplanner.analysis.tissue.TissuePerUnitCalibrationWeight>,
        historyByUnit: Map<String, Map<LocalDate, Double>>
    ): Map<String, TissueEffectiveBaseline> {
        val registry = priorRegistry.getOrNull() ?: return emptyMap()
        return weights.mapNotNull { (stableKey, weight) ->
            val adjusted = TissueEffectiveBaselinePolicy.adjustedPrior(
                registry,
                stableKey,
                localHour,
                profileInputs
            ) ?: return@mapNotNull null
            val personal = TissuePersonalBaselinePolicy.derive(
                loadUnitStableKey = stableKey,
                priorLoadUnitStableKey = adjusted.loadUnitStableKey,
                meaningfulFloor = adjusted.boundaries.meaningfulFloor,
                calibrationWeight = weight,
                dailyResidualByDate = historyByUnit[stableKey].orEmpty()
            )
            stableKey to TissueEffectiveBaselinePolicy.mix(adjusted, personal, weight)
        }.toMap()
    }

    private fun recordLocalDate(record: TissueWorkoutRecord): LocalDate =
        record.entry.performedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() } ?: record.date

    private fun eventLocalDate(event: com.training.trackplanner.analysis.tissue.TissueExposureEvent): LocalDate? =
        event.performedTime.earliestEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
}

internal data class TissueSymptomOverridePlan(
    val overridesByLoadUnit: Map<String, TissueSymptomOverride>,
    val unscopedHighDiscomfort: Boolean
)

internal fun tissueSymptomOverridePlan(
    discomfort: Int?,
    selectedJointComplexKey: String?,
    loadUnitJointComplexes: Map<String, String>
): TissueSymptomOverridePlan {
    val override = when (discomfort) {
        5 -> TissueSymptomOverride.BLOCK
        4 -> TissueSymptomOverride.CAUTION
        else -> TissueSymptomOverride.NONE
    }
    if (override == TissueSymptomOverride.NONE) return TissueSymptomOverridePlan(emptyMap(), false)

    val matchingUnits = loadUnitJointComplexes.filterValues { it == selectedJointComplexKey }.keys
    return if (matchingUnits.isEmpty()) {
        TissueSymptomOverridePlan(emptyMap(), true)
    } else {
        TissueSymptomOverridePlan(matchingUnits.associateWith { override }, false)
    }
}
