package com.training.trackplanner.analysis.tissue

import java.time.LocalDate

object TissueWindowedExposureCalculator {
    fun snapshot(exposures: List<RecordTissueExposure>, targetDate: LocalDate): DailyTissueLoadSnapshot {
        val considered = exposures.filter { it.date <= targetDate }
        val eligible = considered.filter { it.adjustedExposure != null }
        val states = eligible.groupBy(RecordTissueExposure::tissueLoadKey).map { (key, rows) ->
            state(key, rows, targetDate)
        }.sortedWith(
            compareBy<TissueResidualState>({ it.tissueLoadKey.tissueClass.name })
                .thenBy { it.tissueLoadKey.tissueId }
                .thenBy { it.tissueLoadKey.loadDimension.name }
        )
        return DailyTissueLoadSnapshot(
            targetDate = targetDate,
            jointLoads = states.filterClass(TissueClass.JOINT),
            tendonLoads = states.filterClass(TissueClass.TENDON),
            ligamentLoads = states.filterClass(TissueClass.LIGAMENT),
            fasciaLoads = states.filterClass(TissueClass.FASCIA),
            incompleteMetadata = considered.filter {
                it.calculationStatus == TissueCalculationStatus.INCOMPLETE_TISSUE_METADATA
            }.map { TissueMetadataGap(it.recordId, it.stableKey, it.diagnostics.joinToString(" ")) },
            missingRecordInputs = considered.filter {
                it.calculationStatus == TissueCalculationStatus.MISSING_RECORD_INPUT
            }.map { TissueInputGap(it.recordId, it.stableKey, it.diagnostics.joinToString(" ")) },
            unsupportedModifierCombinations = considered.filter {
                it.calculationStatus == TissueCalculationStatus.UNSUPPORTED_MODIFIER_COMBINATION
            }.map { TissueModifierGap(it.recordId, it.tissueLoadKey, it.diagnostics.joinToString(" ")) },
            conflictingEvidence = considered.filter {
                it.evidenceStatus == TissueEvidenceStatus.CONFLICTING_EVIDENCE
            }.map { TissueEvidenceConflict(it.recordId, it.tissueLoadKey, it.diagnostics.joinToString(" ")) }
        )
    }

    private fun state(
        key: TissueLoadKey,
        rows: List<RecordTissueExposure>,
        targetDate: LocalDate
    ): TissueResidualState {
        fun sum(days: Long): Double {
            val start = targetDate.minusDays(days - 1)
            return rows.filter { it.date in start..targetDate }.sumOf { requireNotNull(it.adjustedExposure) }
        }
        val contributors = rows.filter { it.date in targetDate.minusDays(6)..targetDate }
            .map { TissueContributionSummary(it.recordId, it.date, it.stableKey, requireNotNull(it.adjustedExposure)) }
            .sortedWith(compareByDescending<TissueContributionSummary>(TissueContributionSummary::exposure)
                .thenByDescending(TissueContributionSummary::date)
                .thenBy(TissueContributionSummary::recordId))
        val statuses = rows.map(RecordTissueExposure::calculationStatus).toSet()
        val coverage = when {
            statuses.all { it == TissueCalculationStatus.CALCULABLE } -> TissueMetadataCoverageStatus.COMPLETE
            TissueCalculationStatus.EVIDENCE_NOT_APPROVED in statuses -> TissueMetadataCoverageStatus.BLOCKED
            else -> TissueMetadataCoverageStatus.PARTIAL
        }
        return TissueResidualState(
            tissueLoadKey = key,
            targetDate = targetDate,
            residualExposure = null,
            rolling24HourExposure = sum(1),
            rolling72HourExposure = sum(3),
            rolling7DayExposure = sum(7),
            calculationMode = TissueRecoveryCalculationMode.WINDOWED_EXPOSURE,
            topContributingRecords = contributors,
            metadataCoverageStatus = coverage,
            confidenceLevel = rows.map(RecordTissueExposure::confidenceLevel).distinct().sorted().joinToString("|"),
            diagnostics = listOf("Calendar windows are exposure summaries, not exact residual fatigue.")
        )
    }

    private fun List<TissueResidualState>.filterClass(tissueClass: TissueClass) =
        filter { it.tissueLoadKey.tissueClass == tissueClass }
}
