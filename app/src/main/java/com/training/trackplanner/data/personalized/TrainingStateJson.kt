package com.training.trackplanner.data.personalized

import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

/** Additive decision provenance; old decisions need no migration. No reflective persistence. */
internal fun LongitudinalStrainProfile.toJson(): JSONObject = evidenceObject(
    "ofi7" to ofi7,
    "baselineMedian" to baselineMedian,
    "baselineMad" to baselineMad,
    "robustScale" to robustScale,
    "personalZ" to personalZ,
    "absoluteStrain" to absoluteStrain,
    "relativeStrain" to relativeStrain,
    "persistence" to persistence,
    "ofiTrend" to ofiTrend,
    "maxAxisElevation" to maxAxisElevation,
    "elevatedAxisCount" to elevatedAxisCount,
    "baselineDays" to baselineDays,
    "recentDays" to recentDays,
    "strainScore" to strainScore)

internal fun ExerciseAdaptationEvidence.toJson(): JSONObject = evidenceObject(
    "stableKey" to stableKey,
    "movement" to movement,
    "posteriorChangePercent" to posteriorChangePercent,
    "posteriorResponse" to posteriorResponse,
    "posteriorConfidence" to posteriorConfidence,
    "matchedLoadChange" to matchedLoadChange,
    "matchedRepChange" to matchedRepChange,
    "matchedRpeEfficiency" to matchedRpeEfficiency,
    "rawPerformanceResponse" to rawPerformanceResponse,
    "validRawComparisonCount" to validRawComparisonCount,
    "rawConfidence" to rawConfidence,
    "responseScore" to responseScore,
    "confidence" to confidence)

internal fun MovementAdaptationEvidence.toJson(): JSONObject = evidenceObject(
    "response" to response,
    "confidence" to confidence)

internal fun LongitudinalAdaptationProfile.toJson(): JSONObject = evidenceObject(
    "exercises" to exercises,
    "movements" to movements,
    "globalAdaptationResponse" to globalAdaptationResponse,
    "positiveBreadth" to positiveBreadth,
    "negativeBreadth" to negativeBreadth,
    "rpeDrift" to rpeDrift)

internal fun ToleranceWindow.toJson(): JSONObject = evidenceObject(
    "units" to units,
    "days" to days,
    "density" to density)

internal fun WorkloadToleranceProfile.toJson(): JSONObject = evidenceObject(
    "current" to current,
    "prior" to prior,
    "workloadStability" to workloadStability,
    "dayStability" to dayStability,
    "densityStability" to densityStability,
    "rpeStability" to rpeStability,
    "score" to score)

internal fun WeeklyWorkloadEvidence.toJson(): JSONObject = evidenceObject(
    "start" to start,
    "end" to end,
    "units" to units,
    "minutes" to minutes,
    "days" to days,
    "courtLoad" to courtLoad,
    "medianRpe" to medianRpe,
    "performanceResponse" to performanceResponse,
    "negativeBreadth" to negativeBreadth,
    "rpeDrift" to rpeDrift,
    "context" to context,
    "low" to low,
    "localTypicalUnits" to localTypicalUnits,
    "reasonCodes" to reasonCodes,
    "excludedFromTolerance" to excludedFromTolerance)

internal fun StableWorkloadRun.toJson(): JSONObject = evidenceObject(
    "start" to start,
    "end" to end,
    "durationWeeks" to durationWeeks,
    "units" to units,
    "minutes" to minutes,
    "days" to days,
    "response" to response,
    "rpeDrift" to rpeDrift,
    "weight" to weight)

internal fun SustainableWorkloadEvidence.toJson(): JSONObject = evidenceObject(
    "sustainableWeeklyControllableUnits" to sustainableWeeklyControllableUnits,
    "sustainableWeeklyMinutes" to sustainableWeeklyMinutes,
    "sustainableDaysPerWeek" to sustainableDaysPerWeek,
    "longestStableRunWeeks" to longestStableRunWeeks,
    "successfulStableRunCount" to successfulStableRunCount,
    "observationWeeks" to observationWeeks,
    "confidence" to confidence,
    "runs" to runs,
    "rawWeeklyMean" to rawWeeklyMean,
    "normalWeekMedian" to normalWeekMedian,
    "questionRequired" to questionRequired,
    "robustSchedule" to robustSchedule,
    "reasonCodes" to reasonCodes)

internal fun TrainingStateAssessment.toJson(): JSONObject = evidenceObject(
    "strain" to strain,
    "adaptation" to adaptation,
    "tolerance" to tolerance,
    "weeklyContext" to weeklyContext,
    "sustainable" to sustainable,
    "strainScore" to strainScore,
    "productiveEvidence" to productiveEvidence,
    "maladaptationEvidence" to maladaptationEvidence,
    "positiveBreadth" to positiveBreadth,
    "negativeBreadth" to negativeBreadth,
    "state" to state,
    "confidence" to confidence,
    "globalDoseFactor" to globalDoseFactor,
    "globalHardRestriction" to globalHardRestriction,
    "hardRestrictionCodes" to hardRestrictionCodes,
    "reasonCodes" to reasonCodes)

private fun evidenceObject(vararg fields: Pair<String,Any?>) = JSONObject().apply { fields.forEach { (key,value) -> put(key,evidenceValue(value)) } }
private fun evidenceValue(value: Any?): Any = when(value) {
    null -> JSONObject.NULL
    is Double -> if (value == 0.0) 0.0 else value
    is Enum<*> -> value.name
    is LocalDate -> value.toString()
    is Map<*,*> -> JSONObject().apply { value.forEach { (k,v) -> put(k.toString(),evidenceValue(v)) } }
    is Iterable<*> -> JSONArray(value.map(::evidenceValue))
    is LongitudinalStrainProfile -> value.toJson()
    is ExerciseAdaptationEvidence -> value.toJson()
    is MovementAdaptationEvidence -> value.toJson()
    is LongitudinalAdaptationProfile -> value.toJson()
    is ToleranceWindow -> value.toJson()
    is WorkloadToleranceProfile -> value.toJson()
    is WeeklyWorkloadEvidence -> value.toJson()
    is StableWorkloadRun -> value.toJson()
    is SustainableWorkloadEvidence -> value.toJson()
    is TrainingStateAssessment -> value.toJson()
    else -> value
}
