package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** The completed ISO-week coverage required by the reviewed quality-dose baseline. */
data class QualityDoseHistoryHorizon(
    val requiredCompletedWeeks: Int,
    val newestCompletedWeekEnd: LocalDate,
    val oldestCompletedWeekStart: LocalDate,
    val ledgerStart: LocalDate
) {
    init { require(requiredCompletedWeeks > 0) { "requiredCompletedWeeks must be positive" } }

    val completedWeekEnds: List<LocalDate>
        get() = (requiredCompletedWeeks - 1 downTo 0).map { newestCompletedWeekEnd.minusDays(it * 7L) }
}

internal fun qualityDoseHistoryHorizon(cutoff: LocalDate, requiredCompletedWeeks: Int = 8): QualityDoseHistoryHorizon {
    require(requiredCompletedWeeks > 0) { "requiredCompletedWeeks must be positive" }
    val newestEnd = completedTrainingWeekEnd(cutoff)
    val oldestEnd = newestEnd.minusDays((requiredCompletedWeeks - 1) * 7L)
    val oldestStart = oldestEnd.minusDays(6)
    return QualityDoseHistoryHorizon(
        requiredCompletedWeeks = requiredCompletedWeeks,
        newestCompletedWeekEnd = newestEnd,
        oldestCompletedWeekStart = oldestStart,
        ledgerStart = minOf(cutoff.minusDays(55), oldestStart)
    )
}

/** One quality's canonical ledger evidence for one completed ISO week. */
data class QualityDoseWeekEvidence(
    val start: LocalDate,
    val end: LocalDate,
    val excludedFromBaseline: Boolean,
    val hasSourceObservations: Boolean,
    val directUnits: Int = 0,
    val supportiveUnits: Int = 0,
    val directSessions: Int = 0,
    val supportiveSessions: Int = 0,
    val directTrainingDays: Int = 0,
    val supportiveTrainingDays: Int = 0,
    val excludedDirectByPrescriptionUnits: Int = 0,
    val excludedSupportiveByPrescriptionUnits: Int = 0,
    val directPrecedenceResolutions: Int = 0
) {
    val eligibleForBaseline: Boolean
        get() = !excludedFromBaseline && hasSourceObservations
}

enum class QualityDoseHistoryComparisonStatus { MATCH, MISMATCH, UNAVAILABLE }

data class QualityDoseHistoryShadowComparison(
    val quality: TrainableQuality,
    val status: QualityDoseHistoryComparisonStatus,
    val reasonCodes: List<String>,
    val legacyBand: SuccessfulDoseBand?,
    val ledgerBand: SuccessfulDoseBand?
)

/**
 * Shadow-only ledger interpretation of the existing quality-dose history. It is intentionally
 * separate from QualityDoseHistory because the latter remains the production planner input in
 * Phase B2.
 */
data class LedgerBackedQualityDoseHistory(
    val bands: Map<TrainableQuality, SuccessfulDoseBand>,
    val weeklyEvidence: Map<TrainableQuality, List<QualityDoseWeekEvidence>>,
    val indexedWeekCount: Int,
    val excludedWeekCount: Int,
    val horizon: QualityDoseHistoryHorizon,
    val comparisons: Map<TrainableQuality, QualityDoseHistoryShadowComparison>,
    val available: Boolean,
    val reasonCodes: List<String>,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

internal fun LedgerBackedQualityDoseHistory.toCompactJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("available", available)
    .put("reasonCodes", JSONArray(reasonCodes))
    .put("horizon", JSONObject()
        .put("requiredCompletedWeeks", horizon.requiredCompletedWeeks)
        .put("newestCompletedWeekEnd", horizon.newestCompletedWeekEnd.toString())
        .put("oldestCompletedWeekStart", horizon.oldestCompletedWeekStart.toString())
        .put("ledgerStart", horizon.ledgerStart.toString()))
    .put("indexedWeekCount", indexedWeekCount)
    .put("excludedWeekCount", excludedWeekCount)
    .put("qualities", JSONArray(TrainableQuality.entries.map { quality ->
        val band = bands[quality]
        val comparison = comparisons[quality]
        JSONObject()
            .put("quality", quality.name)
            .put("baselineSource", band?.source?.name)
            .put("eligibleWeekCount", band?.eligibleWeekCount ?: 0)
            .put("directUnitsMedian", band?.directUnitsMedian)
            .put("directSessionsMedian", band?.directSessionsMedian)
            .put("supportiveUnitsMedian", band?.supportiveUnitsMedian)
            .put("directExposureWeekCount", band?.directExposureWeekCount ?: 0)
            .put("directExposureWeekFrequency", band?.directExposureWeekFrequency)
            .put("comparisonStatus", comparison?.status?.name)
            .put("comparisonReasonCodes", JSONArray(comparison?.reasonCodes.orEmpty()))
            .put("weeklyEvidence", JSONArray(weeklyEvidence[quality].orEmpty().map { week ->
                JSONObject()
                    .put("start", week.start.toString())
                    .put("end", week.end.toString())
                    .put("excludedFromBaseline", week.excludedFromBaseline)
                    .put("hasSourceObservations", week.hasSourceObservations)
                    .put("directUnits", week.directUnits)
                    .put("supportiveUnits", week.supportiveUnits)
                    .put("directSessions", week.directSessions)
                    .put("supportiveSessions", week.supportiveSessions)
                    .put("directTrainingDays", week.directTrainingDays)
                    .put("supportiveTrainingDays", week.supportiveTrainingDays)
                    .put("excludedDirectByPrescriptionUnits", week.excludedDirectByPrescriptionUnits)
                    .put("excludedSupportiveByPrescriptionUnits", week.excludedSupportiveByPrescriptionUnits)
                    .put("directPrecedenceResolutions", week.directPrecedenceResolutions)
            }))
    }))

internal class LedgerBackedQualityDoseHistoryAnalyzer {
    fun analyze(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        legacyHistory: QualityDoseHistory
    ): LedgerBackedQualityDoseHistory {
        val horizon = qualityDoseHistoryHorizon(snapshot.cutoff)
        val ledger = snapshot.stimulusExposureLedger
        if (ledger.cutoff != snapshot.cutoff) {
            val bands = TrainableQuality.entries.associateWith { SuccessfulDoseBand(eligibleWeekCount = 0) }
            val comparisons = TrainableQuality.entries.associateWith { quality ->
                QualityDoseHistoryShadowComparison(
                    quality = quality,
                    status = QualityDoseHistoryComparisonStatus.UNAVAILABLE,
                    reasonCodes = listOf("LEDGER_UNAVAILABLE_OR_CUTOFF_MISMATCH"),
                    legacyBand = legacyHistory.bands[quality],
                    ledgerBand = bands[quality]
                )
            }
            return LedgerBackedQualityDoseHistory(
                bands = bands,
                weeklyEvidence = emptyMap(),
                indexedWeekCount = 0,
                excludedWeekCount = 0,
                horizon = horizon,
                comparisons = comparisons,
                available = false,
                reasonCodes = listOf("LEDGER_UNAVAILABLE_OR_CUTOFF_MISMATCH"),
            )
        }

        val contextByStart = state.trainingStateAssessment?.weeklyContext.orEmpty().associateBy { it.start }
        val weeks = horizon.completedWeekEnds.map { end ->
            val start = end.minusDays(6)
            WeekAccumulator(start, end, contextByStart[start]?.excludedFromTolerance == true)
        }
        val weekByStart = weeks.associateBy { it.start }
        var sourceObservationCount = 0
        ledger.setObservations.forEach { observation ->
            val weekStart = observation.source.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = weekByStart[weekStart] ?: return@forEach
            sourceObservationCount++
            week.sourceObservationCount++
            val profile = ledger.facetProfilesByStableKey[observation.facetProfileKey]
            if (profile == null) return@forEach
            profile.physicalQualities.groupBy(ExercisePhysicalQualityRelation::qualityId).forEach { (quality, relations) ->
                val direct = relations.any { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
                val supportive = !direct && relations.any { it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY }
                if (!direct && !supportive) return@forEach
                val compatible = stimulusPrescriptionCompatible(quality, observation.realizedPrescriptionClass)
                week.add(
                    quality = quality,
                    date = observation.source.date,
                    sessionStableKey = observation.source.sessionStableKey,
                    direct = direct,
                    supportive = supportive,
                    compatible = compatible,
                    precedenceResolved = direct && relations.any { it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY }
                )
            }
        }

        val weekly = TrainableQuality.entries.associateWith { quality -> weeks.map { it.evidence(quality) } }
        val bands = TrainableQuality.entries.associateWith { quality ->
            val all = weekly.getValue(quality).filter(QualityDoseWeekEvidence::eligibleForBaseline)
            val recentStart = snapshot.cutoff.minusDays(27).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val current = all.filter { it.start >= recentStart }
            buildQualityDoseBand(all.toDoseValues(), current.toDoseValues())
        }
        val reasons = linkedSetOf(
            "LEDGER_BACKED_COMPLETED_ISO_WEEKS",
            "B1_WINDOWS_REMAIN_RECENT7_0_TO_6_CURRENT28_0_TO_27_PRIOR28_28_TO_55_CONTEXT56_0_TO_55",
            "DIRECT_PRECEDENCE_OVER_SUPPORTIVE",
            "SESSION_IDENTITY_DATE_AND_SESSION_STABLE_KEY",
            "GENERIC_COURT_EXCLUDED_FROM_QUALITY_DOSE"
        )
        if (horizon.ledgerStart.isBefore(snapshot.cutoff.minusDays(55))) {
            reasons += "LEDGER_HORIZON_EXTENDED_FOR_COMPLETED_ISO_WEEK_BASELINE"
        }
        if (ledger.courtObservations.isNotEmpty()) reasons += "COURT_RETAINED_AS_SEPARATE_CONTEXT_CHANNEL"
        if (sourceObservationCount == 0) reasons += "NO_CANONICAL_SET_OBSERVATIONS_IN_COMPLETED_WEEKS"
        TrainableQuality.entries.filter { it !in PRESCRIPTION_GATED_QUALITIES }.forEach {
            reasons += "${it.name}_CANONICAL_CAPABILITY_PROXY_REALIZED_STIMULUS_AUTHORITY_UNAVAILABLE"
        }
        val comparisons = TrainableQuality.entries.associateWith { quality ->
            compare(quality, legacyHistory.bands[quality], bands.getValue(quality), weekly.getValue(quality), horizon, snapshot.cutoff)
        }
        return LedgerBackedQualityDoseHistory(
            bands = bands,
            weeklyEvidence = weekly,
            indexedWeekCount = weeks.size,
            excludedWeekCount = weeks.count { it.excludedFromBaseline },
            horizon = horizon,
            comparisons = comparisons,
            available = true,
            reasonCodes = reasons.toList()
        )
    }

    private fun compare(
        quality: TrainableQuality,
        legacy: SuccessfulDoseBand?,
        ledger: SuccessfulDoseBand,
        weekly: List<QualityDoseWeekEvidence>,
        horizon: QualityDoseHistoryHorizon,
        cutoff: LocalDate
    ): QualityDoseHistoryShadowComparison {
        val reasons = linkedSetOf<String>()
        if (legacy == ledger) {
            reasons += "LEDGER_AND_LEGACY_BASELINE_VALUES_MATCH"
            if (ledger.source == SuccessfulDoseSource.NO_PERSONAL_BASELINE) reasons += "ZERO_OR_UNAVAILABLE_BASELINE_RETAINED"
            return QualityDoseHistoryShadowComparison(quality, QualityDoseHistoryComparisonStatus.MATCH, reasons.toList(), legacy, ledger)
        }
        reasons += "LEDGER_BASELINE_VALUES_DIFFER_FROM_LEGACY"
        if (legacy == null) reasons += "LEGACY_BAND_UNAVAILABLE"
        if (weekly.any { it.excludedDirectByPrescriptionUnits > 0 || it.excludedSupportiveByPrescriptionUnits > 0 }) {
            reasons += "PRESCRIPTION_INCOMPATIBLE_SOURCE_OBSERVATIONS_EXCLUDED"
        }
        if (weekly.any { it.directPrecedenceResolutions > 0 }) reasons += "DIRECT_PRECEDENCE_REPLACED_OVERLAPPING_SUPPORTIVE_CREDIT"
        if (legacy?.directSessionsMedian != ledger.directSessionsMedian) reasons += "SESSION_IDENTITY_CHANGED_TO_DATE_AND_SESSION_STABLE_KEY"
        if (horizon.ledgerStart.isBefore(cutoff.minusDays(55))) {
            reasons += "COMPLETED_WEEK_HORIZON_EXTENDED"
        }
        if (legacy?.directUnitsMedian == 0.0 && ledger.directUnitsMedian == 0.0) reasons += "ZERO_EXPOSURE_RETAINED_AS_ZERO"
        return QualityDoseHistoryShadowComparison(quality, QualityDoseHistoryComparisonStatus.MISMATCH, reasons.toList(), legacy, ledger)
    }

    private class WeekAccumulator(
        val start: LocalDate,
        val end: LocalDate,
        val excludedFromBaseline: Boolean
    ) {
        private val byQuality = mutableMapOf<TrainableQuality, MutableEvidence>()
        var sourceObservationCount: Int = 0

        fun add(quality: TrainableQuality, date: LocalDate, sessionStableKey: String, direct: Boolean,
            supportive: Boolean, compatible: Boolean, precedenceResolved: Boolean) {
            byQuality.getOrPut(quality) { MutableEvidence() }.add(
                date, sessionStableKey, direct, supportive, compatible, precedenceResolved
            )
        }

        fun evidence(quality: TrainableQuality): QualityDoseWeekEvidence {
            val value = byQuality[quality] ?: MutableEvidence()
            return value.toEvidence(start, end, excludedFromBaseline, sourceObservationCount > 0)
        }
    }

    private class MutableEvidence {
        var directUnits = 0
        var supportiveUnits = 0
        var excludedDirect = 0
        var excludedSupportive = 0
        var precedenceResolved = 0
        val directSessions = linkedSetOf<Pair<LocalDate, String>>()
        val supportiveSessions = linkedSetOf<Pair<LocalDate, String>>()
        val directDays = linkedSetOf<LocalDate>()
        val supportiveDays = linkedSetOf<LocalDate>()

        fun add(date: LocalDate, session: String, direct: Boolean, supportive: Boolean, compatible: Boolean, precedenceResolved: Boolean) {
            if (precedenceResolved) this.precedenceResolved++
            when {
                direct && compatible -> {
                    directUnits++
                    directSessions += date to session
                    directDays += date
                }
                direct -> excludedDirect++
                supportive && compatible -> {
                    supportiveUnits++
                    supportiveSessions += date to session
                    supportiveDays += date
                }
                supportive -> excludedSupportive++
            }
        }

        fun toEvidence(start: LocalDate, end: LocalDate, excluded: Boolean, hasSource: Boolean) = QualityDoseWeekEvidence(
            start = start,
            end = end,
            excludedFromBaseline = excluded,
            hasSourceObservations = hasSource,
            directUnits = directUnits,
            supportiveUnits = supportiveUnits,
            directSessions = directSessions.size,
            supportiveSessions = supportiveSessions.size,
            directTrainingDays = directDays.size,
            supportiveTrainingDays = supportiveDays.size,
            excludedDirectByPrescriptionUnits = excludedDirect,
            excludedSupportiveByPrescriptionUnits = excludedSupportive,
            directPrecedenceResolutions = precedenceResolved
        )
    }
}

private val PRESCRIPTION_GATED_QUALITIES = setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)

private data class QualityDoseValues(
    val directUnits: Double,
    val directSessions: Double,
    val supportiveUnits: Double,
    val supportiveSessions: Double
)

private fun List<QualityDoseWeekEvidence>.toDoseValues() = map {
    QualityDoseValues(it.directUnits.toDouble(), it.directSessions.toDouble(), it.supportiveUnits.toDouble(), it.supportiveSessions.toDouble())
}

/** Same fallback, quantile, confidence and frequency rules as the reviewed legacy analyzer. */
private fun buildQualityDoseBand(normalValues: List<QualityDoseValues>, currentValues: List<QualityDoseValues>): SuccessfulDoseBand {
    val normalExposure = normalValues.filter { it.directUnits > 0.0 }
    val currentExposure = currentValues.filter { it.directUnits > 0.0 }
    val source: SuccessfulDoseSource
    val weeklyValues: List<QualityDoseValues>
    val exposureValues: List<QualityDoseValues>
    when {
        normalExposure.isNotEmpty() -> {
            source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS
            weeklyValues = normalValues
            exposureValues = normalExposure
        }
        currentExposure.isNotEmpty() -> {
            source = SuccessfulDoseSource.CURRENT_28D_FALLBACK
            weeklyValues = currentValues
            exposureValues = currentExposure
        }
        normalValues.any { it.supportiveUnits > 0.0 } -> {
            source = SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK
            weeklyValues = normalValues
            exposureValues = emptyList()
        }
        else -> {
            source = SuccessfulDoseSource.NO_PERSONAL_BASELINE
            weeklyValues = normalValues
            exposureValues = emptyList()
        }
    }
    val confidence = when {
        exposureValues.size >= 4 && consistentDoseValues(exposureValues.map { it.directUnits }) -> PlanningConfidence.HIGH
        exposureValues.size >= 2 -> PlanningConfidence.MODERATE
        else -> PlanningConfidence.LOW
    }
    val weeklyUnitsQ25 = doseQuantile(weeklyValues.map { it.directUnits }, .25)
    val weeklyUnitsMedian = doseQuantile(weeklyValues.map { it.directUnits }, .50)
    val weeklyUnitsQ75 = doseQuantile(weeklyValues.map { it.directUnits }, .75)
    val weeklySessionsQ25 = doseQuantile(weeklyValues.map { it.directSessions }, .25)
    val weeklySessionsMedian = doseQuantile(weeklyValues.map { it.directSessions }, .50)
    val weeklySessionsQ75 = doseQuantile(weeklyValues.map { it.directSessions }, .75)
    val supportiveUnitsQ25 = doseQuantile(weeklyValues.map { it.supportiveUnits }, .25)
    val supportiveUnitsMedian = doseQuantile(weeklyValues.map { it.supportiveUnits }, .50)
    val supportiveUnitsQ75 = doseQuantile(weeklyValues.map { it.supportiveUnits }, .75)
    val supportiveSessionsQ25 = doseQuantile(weeklyValues.map { it.supportiveSessions }, .25)
    val supportiveSessionsMedian = doseQuantile(weeklyValues.map { it.supportiveSessions }, .50)
    val supportiveSessionsQ75 = doseQuantile(weeklyValues.map { it.supportiveSessions }, .75)
    val exposureFrequency = if (weeklyValues.isEmpty()) null else exposureValues.size.toDouble() / weeklyValues.size.toDouble()
    return SuccessfulDoseBand(
        eligibleWeekCount = weeklyValues.size,
        directUnitsQ25 = weeklyUnitsQ25,
        directUnitsMedian = weeklyUnitsMedian,
        directUnitsQ75 = weeklyUnitsQ75,
        directSessionsQ25 = weeklySessionsQ25,
        directSessionsMedian = weeklySessionsMedian,
        directSessionsQ75 = weeklySessionsQ75,
        supportiveUnitsQ25 = supportiveUnitsQ25,
        supportiveUnitsMedian = supportiveUnitsMedian,
        supportiveUnitsQ75 = supportiveUnitsQ75,
        confidence = confidence,
        source = source,
        weeklyDirectUnitsQ25 = weeklyUnitsQ25,
        weeklyDirectUnitsMedian = weeklyUnitsMedian,
        weeklyDirectUnitsQ75 = weeklyUnitsQ75,
        weeklyDirectSessionsQ25 = weeklySessionsQ25,
        weeklyDirectSessionsMedian = weeklySessionsMedian,
        weeklyDirectSessionsQ75 = weeklySessionsQ75,
        exposureWeekDirectUnitsQ25 = doseQuantile(exposureValues.map { it.directUnits }, .25),
        exposureWeekDirectUnitsMedian = doseQuantile(exposureValues.map { it.directUnits }, .50),
        exposureWeekDirectUnitsQ75 = doseQuantile(exposureValues.map { it.directUnits }, .75),
        exposureWeekDirectSessionsQ25 = doseQuantile(exposureValues.map { it.directSessions }, .25),
        exposureWeekDirectSessionsMedian = doseQuantile(exposureValues.map { it.directSessions }, .50),
        exposureWeekDirectSessionsQ75 = doseQuantile(exposureValues.map { it.directSessions }, .75),
        directExposureWeekCount = exposureValues.size,
        directExposureWeekFrequency = exposureFrequency,
        weeklySupportiveUnitsQ25 = supportiveUnitsQ25,
        weeklySupportiveUnitsMedian = supportiveUnitsMedian,
        weeklySupportiveUnitsQ75 = supportiveUnitsQ75,
        weeklySupportiveSessionsQ25 = supportiveSessionsQ25,
        weeklySupportiveSessionsMedian = supportiveSessionsMedian,
        weeklySupportiveSessionsQ75 = supportiveSessionsQ75
    )
}

private fun consistentDoseValues(values: List<Double>): Boolean {
    val positive = values.filter { it > 0.0 }
    if (positive.isEmpty()) return false
    val median = doseQuantile(positive, .50) ?: return false
    return median > 0.0 && (doseQuantile(positive, .75) ?: median) / median <= 2.5
}

private fun doseQuantile(values: List<Double>, q: Double): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    return sorted[((sorted.size - 1) * q).roundToInt()]
}
