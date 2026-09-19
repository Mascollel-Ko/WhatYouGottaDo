package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Audit provenance only. These labels are not ordinary user-facing copy. */
enum class RegionalEvidenceTier {
    A_RESEARCH_SYNTHESIS,
    B_MECHANISTIC_REVIEW,
    C_COACHING_CONSENSUS,
    D_PRODUCT_HEURISTIC
}

enum class RegionalLimitingFactor {
    NO_CLEAR_LIMITATION,
    EXPOSURE_LIMITED,
    RECOVERY_LIMITED,
    SPECIFICITY_POSSIBLY_LIMITING,
    MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING,
    MULTIFACTORIAL,
    INSUFFICIENT_EVIDENCE
}

enum class RegionalStrengthExposureStatus { ABSENT, LOW, ADEQUATE, UNKNOWN }
enum class RegionalHypertrophySupportStatus { LOW, ADEQUATE, UNKNOWN }
enum class SpecificityContinuity { ADEQUATE, REDUCED, UNKNOWN }

data class RegionalDoseBand(
    val eligibleWeekCount: Int = 0,
    val weeklyUnitsQ25: Double? = null,
    val weeklyUnitsMedian: Double? = null,
    val weeklyUnitsQ75: Double? = null,
    val exposureWeekUnitsQ25: Double? = null,
    val exposureWeekUnitsMedian: Double? = null,
    val exposureWeekUnitsQ75: Double? = null,
    val directExposureWeekCount: Int = 0,
    val directExposureWeekFrequency: Double? = null,
    val current28dUnits: Int = 0,
    val previous28dUnits: Int = 0,
    val current28dSessions: Int = 0,
    val previous28dSessions: Int = 0,
    val confidence: PlanningConfidence = PlanningConfidence.LOW,
    val source: SuccessfulDoseSource = SuccessfulDoseSource.NO_PERSONAL_BASELINE
) {
    val hasPersonalBaseline: Boolean
        get() = source in setOf(SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK) &&
            eligibleWeekCount >= 2 && directExposureWeekCount >= 2
}

data class RegionalRegionEvidence(
    val region: MovementCoverage,
    val strengthDoseBand: RegionalDoseBand,
    val hypertrophyDoseBand: RegionalDoseBand,
    val strengthResponse: TrainingResponseState,
    val validStrengthObservationCount: Int,
    val specificCurrent28dUnits: Int,
    val specificPrevious28dUnits: Int,
    val specificCurrent28dSessions: Int,
    val specificPrevious28dSessions: Int,
    val strengthBaselineEstablished: Boolean,
    val hypertrophyBaselineEstablished: Boolean
)

/** One immutable, pre-aggregated pass over the governed history window. */
data class RegionalEvidenceIndex(
    val regions: Map<MovementCoverage, RegionalRegionEvidence>,
    val indexedRowCount: Int,
    val eligibleWeekCount: Int,
    val activeWeekCount: Int,
    val windowDays: Int = 56,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

data class RegionalBottleneckDiagnosis(
    val region: MovementCoverage,
    val outcomeQuality: TrainableQuality = TrainableQuality.STRENGTH,
    val requirement: NeedRelevance,
    val performanceResponse: TrainingResponseState,
    val validStrengthObservationCount: Int,
    val strengthExposureStatus: RegionalStrengthExposureStatus,
    val strengthDoseBand: RegionalDoseBand,
    val strengthExposureFrequency: Double?,
    val specificityContinuity: SpecificityContinuity,
    val hypertrophySupportStatus: RegionalHypertrophySupportStatus,
    val hypertrophyDoseBand: RegionalDoseBand,
    val hypertrophyExposureFrequency: Double?,
    val recoveryConstraint: Boolean,
    val sportLoadInterference: Boolean,
    val limitingFactors: List<RegionalLimitingFactor>,
    val primaryInterpretation: RegionalLimitingFactor,
    val confidence: PlanningConfidence,
    val reasonCodes: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val evidenceTiers: List<RegionalEvidenceTier> = listOf(RegionalEvidenceTier.D_PRODUCT_HEURISTIC),
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

data class ProgramEmphasisLabel(
    val region: MovementCoverage,
    val quality: TrainableQuality,
    val plannedUnits: Int
)

private val regionalSupportedRegions = listOf(
    MovementCoverage.LOWER_KNEE,
    MovementCoverage.POSTERIOR_CHAIN,
    MovementCoverage.CALVES,
    MovementCoverage.HORIZONTAL_PUSH,
    MovementCoverage.VERTICAL_PUSH,
    MovementCoverage.HORIZONTAL_PULL,
    MovementCoverage.VERTICAL_PULL
)

private data class RegionalWeek(val start: LocalDate, val end: LocalDate, val rows: List<PlanningSetRecord>, val excluded: Boolean)

private class MutableRegionalDose {
    val weeklyUnits = linkedMapOf<LocalDate, Int>()
    var currentUnits = 0
    var previousUnits = 0
    val currentDates = linkedSetOf<LocalDate>()
    val previousDates = linkedSetOf<LocalDate>()

    fun add(row: PlanningSetRecord, weekStart: LocalDate, age: Int) {
        weeklyUnits[weekStart] = weeklyUnits.getOrDefault(weekStart, 0) + 1
        if (age in 0..27) {
            currentUnits++
            currentDates += row.date
        } else if (age in 28..55) {
            previousUnits++
            previousDates += row.date
        }
    }

    fun finish(eligibleWeeks: List<RegionalWeek>, confidence: PlanningConfidence): RegionalDoseBand {
        val weekly = eligibleWeeks.map { weeklyUnits[it.start]?.toDouble() ?: 0.0 }
        val exposed = weekly.filter { it > 0.0 }
        val source = when {
            exposed.isNotEmpty() -> SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS
            weekly.isNotEmpty() -> SuccessfulDoseSource.NO_PERSONAL_BASELINE
            else -> SuccessfulDoseSource.NO_PERSONAL_BASELINE
        }
        return RegionalDoseBand(
            eligibleWeekCount = weekly.size,
            weeklyUnitsQ25 = quantile(weekly, .25),
            weeklyUnitsMedian = quantile(weekly, .50),
            weeklyUnitsQ75 = quantile(weekly, .75),
            exposureWeekUnitsQ25 = quantile(exposed, .25),
            exposureWeekUnitsMedian = quantile(exposed, .50),
            exposureWeekUnitsQ75 = quantile(exposed, .75),
            directExposureWeekCount = exposed.size,
            directExposureWeekFrequency = if (weekly.isEmpty()) null else exposed.size.toDouble() / weekly.size,
            current28dUnits = currentUnits,
            previous28dUnits = previousUnits,
            current28dSessions = currentDates.size,
            previous28dSessions = previousDates.size,
            confidence = confidence,
            source = source
        )
    }
}

/** Builds all supported regional evidence in one history pass. */
class RegionalEvidenceIndexBuilder {
    fun build(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): RegionalEvidenceIndex {
        val completeEnd = completedTrainingWeekEnd(snapshot.cutoff)
        val weekEnds = (7 downTo 0).map { completeEnd.minusDays(it * 7L) }
        val contextByStart = state.trainingStateAssessment?.weeklyContext.orEmpty().associateBy { it.start }
        val firstStart = weekEnds.minOf { it }.minusDays(6)
        val lastEnd = weekEnds.maxOf { it }
        val rowsByWeekStart = snapshot.allConfirmedSets.asSequence()
            .filter { it.date in firstStart..lastEnd }
            .filter { snapshot.activityKind(it.stableKey) in TrainingStatePolicy.controllableDomains }
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
        val weeks = weekEnds.map { end ->
            val start = end.minusDays(6)
            RegionalWeek(start, end, rowsByWeekStart[start].orEmpty(), contextByStart[start]?.excludedFromTolerance == true)
        }
        val eligible = weeks.filter { !it.excluded && it.rows.isNotEmpty() }
        val confidence = when {
            eligible.size >= 6 -> PlanningConfidence.HIGH
            eligible.size >= 3 -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        val strength = regionalSupportedRegions.associateWith { MutableRegionalDose() }.toMutableMap()
        val hypertrophy = regionalSupportedRegions.associateWith { MutableRegionalDose() }.toMutableMap()
        val strengthKeys = regionalSupportedRegions.associateWith { linkedSetOf<String>() }.toMutableMap()
        val specificCurrent = regionalSupportedRegions.associateWith { 0 }.toMutableMap()
        val specificPrevious = regionalSupportedRegions.associateWith { 0 }.toMutableMap()
        val specificCurrentDates = regionalSupportedRegions.associateWith { linkedSetOf<LocalDate>() }.toMutableMap()
        val specificPreviousDates = regionalSupportedRegions.associateWith { linkedSetOf<LocalDate>() }.toMutableMap()
        val relationByKey = catalog.trainingRelations().groupBy(ExercisePhysicalQualityRelation::exerciseStableKey)
        val eligibleByDate = eligible.flatMap { week -> week.rows.map { it to week.start } }.toMap()
        var indexedRows = 0
        snapshot.allConfirmedSets.forEach { row ->
            val age = java.time.temporal.ChronoUnit.DAYS.between(row.date, snapshot.cutoff).toInt()
            if (age !in 0..55) return@forEach
            val weekStart = eligibleByDate[row] ?: return@forEach
            val movement = snapshot.movementCoverage(row.stableKey)
            if (movement !in regionalSupportedRegions) return@forEach
            val relations = relationByKey[row.stableKey].orEmpty()
            if (relations.isEmpty()) return@forEach
            indexedRows++
            val realized = provisionalRealizedStimulusClass(row)
            val doseSeen = linkedSetOf<TrainableQuality>()
            var specificStrengthRelation = false
            relations.forEach { relation ->
                if (relation.relationLevel != StimulusCapabilityLevel.DIRECT_CAPABILITY ||
                    relation.qualityId !in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) ||
                    !regionQualifierMatches(movement, relation.regionQualifier)) return@forEach
                when (relation.qualityId) {
                    TrainableQuality.STRENGTH -> if (realized == RealizedStimulusClass.STRENGTH_LIKE) {
                        if (doseSeen.add(relation.qualityId)) {
                            strength.getValue(movement).add(row, weekStart, age)
                            if (age in 0..27) strengthKeys.getValue(movement).add(row.stableKey)
                        }
                        if (relation.modeQualifier !in setOf(PhysicalQualityMode.GENERAL, PhysicalQualityMode.OTHER)) specificStrengthRelation = true
                    }
                    TrainableQuality.HYPERTROPHY -> if (realized == RealizedStimulusClass.HYPERTROPHY_LIKE) {
                        if (doseSeen.add(relation.qualityId)) hypertrophy.getValue(movement).add(row, weekStart, age)
                    }
                    else -> Unit
                }
            }
            if (specificStrengthRelation && realized == RealizedStimulusClass.STRENGTH_LIKE) {
                if (age in 0..27) { specificCurrent[movement] = specificCurrent.getValue(movement) + 1; specificCurrentDates.getValue(movement) += row.date }
                if (age in 28..55) { specificPrevious[movement] = specificPrevious.getValue(movement) + 1; specificPreviousDates.getValue(movement) += row.date }
            }
        }
        val regions = regionalSupportedRegions.associateWith { region ->
            val strengthBand = strength.getValue(region).finish(eligible, confidence)
            val hypertrophyBand = hypertrophy.getValue(region).finish(eligible, confidence)
            val validSignals = strengthKeys.getValue(region).mapNotNull { key ->
                snapshot.canonicalStrengthSignals[key]?.takeIf { it.observationCount >= 2 && it.posteriorChangePercent?.isFinite() == true }
            }
            val changes = validSignals.mapNotNull { it.posteriorChangePercent }
            val response = when {
                changes.isEmpty() -> TrainingResponseState.INSUFFICIENT_EVIDENCE
                quantile(changes, .50)!! > 2.0 -> TrainingResponseState.POSITIVE_RESPONSE
                quantile(changes, .50)!! < -5.0 -> TrainingResponseState.NEGATIVE_RESPONSE
                else -> TrainingResponseState.STABLE_RESPONSE
            }
            RegionalRegionEvidence(
                region,
                strengthBand,
                hypertrophyBand,
                response,
                validSignals.sumOf { it.observationCount },
                specificCurrent.getValue(region),
                specificPrevious.getValue(region),
                specificCurrentDates.getValue(region).size,
                specificPreviousDates.getValue(region).size,
                strengthBand.hasPersonalBaseline,
                hypertrophyBand.hasPersonalBaseline
            )
        }
        return RegionalEvidenceIndex(regions, indexedRows, eligible.size, weeks.count { it.rows.isNotEmpty() })
    }

    private fun regionQualifierMatches(movement: MovementCoverage, qualifier: PhysicalQualityRegion): Boolean = when (movement) {
        MovementCoverage.LOWER_KNEE -> qualifier in setOf(PhysicalQualityRegion.LOWER, PhysicalQualityRegion.QUADS_GLUTE, PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.POSTERIOR_CHAIN -> qualifier in setOf(PhysicalQualityRegion.POSTERIOR_CHAIN, PhysicalQualityRegion.HAMSTRING, PhysicalQualityRegion.LOWER, PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.CALVES -> qualifier in setOf(PhysicalQualityRegion.ANKLE, PhysicalQualityRegion.LOWER)
        MovementCoverage.HORIZONTAL_PUSH, MovementCoverage.VERTICAL_PUSH -> qualifier in setOf(PhysicalQualityRegion.UPPER_PUSH, PhysicalQualityRegion.CHEST, PhysicalQualityRegion.SHOULDERS, PhysicalQualityRegion.ARMS)
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL -> qualifier in setOf(PhysicalQualityRegion.UPPER_PULL, PhysicalQualityRegion.SHOULDERS, PhysicalQualityRegion.ARMS)
        else -> false
    }
}

class RegionalBottleneckDiagnosisEngine {
    fun analyze(
        index: RegionalEvidenceIndex,
        requirement: NeedRelevance,
        recoveryConstraint: Boolean,
        sportLoadInterference: Boolean
    ): List<RegionalBottleneckDiagnosis> = index.regions.toSortedMap(compareBy { it.ordinal }).values.map { evidence ->
        diagnose(evidence, requirement, recoveryConstraint, sportLoadInterference)
    }

    fun analyze(
        index: RegionalEvidenceIndex,
        requirementByRegion: Map<MovementCoverage, NeedRelevance>,
        recoveryConstraint: Boolean,
        sportLoadInterference: Boolean
    ): List<RegionalBottleneckDiagnosis> = index.regions.toSortedMap(compareBy { it.ordinal }).values.map { evidence ->
        diagnose(evidence, requirementByRegion[evidence.region] ?: NeedRelevance.UNKNOWN, recoveryConstraint, sportLoadInterference)
    }

    private fun diagnose(
        evidence: RegionalRegionEvidence,
        requirement: NeedRelevance,
        recoveryConstraint: Boolean,
        sportLoadInterference: Boolean
    ): RegionalBottleneckDiagnosis {
        val strength = evidence.strengthDoseBand
        val hypo = evidence.hypertrophyDoseBand
        val strengthStatus = strengthStatus(strength)
        val hypoStatus = hypertrophyStatus(hypo)
        val specificity = when {
            evidence.specificPrevious28dUnits <= 0 -> SpecificityContinuity.UNKNOWN
            evidence.specificCurrent28dUnits.toDouble() < evidence.specificPrevious28dUnits * .5 -> SpecificityContinuity.REDUCED
            else -> SpecificityContinuity.ADEQUATE
        }
        val performanceSufficient = evidence.strengthResponse != TrainingResponseState.INSUFFICIENT_EVIDENCE && evidence.validStrengthObservationCount >= 2
        val positive = evidence.strengthResponse == TrainingResponseState.POSITIVE_RESPONSE
        val factors = mutableListOf<RegionalLimitingFactor>()
        if (requirement in setOf(NeedRelevance.MODERATE, NeedRelevance.HIGH) && !positive && strengthStatus == RegionalStrengthExposureStatus.LOW &&
            (evidence.strengthBaselineEstablished || performanceSufficient)) factors += RegionalLimitingFactor.EXPOSURE_LIMITED
        if (recoveryConstraint && !positive && performanceSufficient) factors += RegionalLimitingFactor.RECOVERY_LIMITED
        if (specificity == SpecificityContinuity.REDUCED && strengthStatus == RegionalStrengthExposureStatus.ADEQUATE &&
            evidence.strengthResponse in setOf(TrainingResponseState.STABLE_RESPONSE, TrainingResponseState.NEGATIVE_RESPONSE)) {
            factors += RegionalLimitingFactor.SPECIFICITY_POSSIBLY_LIMITING
        }
        if (!positive && performanceSufficient && !recoveryConstraint && specificity != SpecificityContinuity.REDUCED &&
            strengthStatus == RegionalStrengthExposureStatus.ADEQUATE && hypoStatus == RegionalHypertrophySupportStatus.LOW &&
            evidence.strengthResponse in setOf(TrainingResponseState.STABLE_RESPONSE, TrainingResponseState.NEGATIVE_RESPONSE)) {
            factors += RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING
        }
        val finalFactors = when {
            positive -> listOf(RegionalLimitingFactor.NO_CLEAR_LIMITATION)
            !performanceSufficient && factors.isEmpty() -> listOf(RegionalLimitingFactor.INSUFFICIENT_EVIDENCE)
            factors.isEmpty() -> listOf(RegionalLimitingFactor.NO_CLEAR_LIMITATION)
            else -> factors.distinct()
        }
        val primary = when {
            finalFactors.size > 1 -> RegionalLimitingFactor.MULTIFACTORIAL
            else -> finalFactors.single()
        }
        val confidence = when {
            primary == RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING -> PlanningConfidence.MODERATE
            evidence.validStrengthObservationCount >= 4 && strength.eligibleWeekCount >= 4 -> PlanningConfidence.HIGH
            evidence.validStrengthObservationCount >= 2 || strength.eligibleWeekCount >= 3 -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        val reasons = buildList {
            if (positive) add("LOW_EXPOSURE_BUT_POSITIVE_PERSONAL_RESPONSE")
            if (positive && hypoStatus == RegionalHypertrophySupportStatus.LOW) add("LOW_HYPERTROPHY_SUPPORT_NOT_A_DEFICIT_WHILE_STRENGTH_RESPONSE_IS_POSITIVE")
            if (finalFactors.contains(RegionalLimitingFactor.EXPOSURE_LIMITED)) add("REGIONAL_STRENGTH_EXPOSURE_BELOW_PERSONAL_EVIDENCE")
            if (finalFactors.contains(RegionalLimitingFactor.RECOVERY_LIMITED)) add("RECOVERY_RESTRICTION_TAKES_PRIORITY_OVER_MORPHOLOGICAL_HYPOTHESIS")
            if (finalFactors.contains(RegionalLimitingFactor.SPECIFICITY_POSSIBLY_LIMITING)) add("SPECIFIC_PERFORMANCE_EXPOSURE_DECLINED")
            if (finalFactors.contains(RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING)) add("LOW_HYPERTROPHY_SUPPORT_AFTER_STRONGER_ALTERNATIVES_CHECKED")
            if (!performanceSufficient) add("INSUFFICIENT_CANONICAL_STRENGTH_RESPONSE_EVIDENCE")
        }
        return RegionalBottleneckDiagnosis(
            region = evidence.region,
            requirement = requirement,
            performanceResponse = evidence.strengthResponse,
            validStrengthObservationCount = evidence.validStrengthObservationCount,
            strengthExposureStatus = strengthStatus,
            strengthDoseBand = strength,
            strengthExposureFrequency = strength.directExposureWeekFrequency,
            specificityContinuity = specificity,
            hypertrophySupportStatus = hypoStatus,
            hypertrophyDoseBand = hypo,
            hypertrophyExposureFrequency = hypo.directExposureWeekFrequency,
            recoveryConstraint = recoveryConstraint,
            sportLoadInterference = sportLoadInterference,
            limitingFactors = finalFactors,
            primaryInterpretation = primary,
            confidence = confidence,
            reasonCodes = reasons,
            evidence = listOf(
                "eligibleWeekCount=${strength.eligibleWeekCount}",
                "strengthExposureWeekCount=${strength.directExposureWeekCount}",
                "strengthExposureWeekFrequency=${strength.directExposureWeekFrequency}",
                "validStrengthObservationCount=${evidence.validStrengthObservationCount}",
                "specificCurrent28dUnits=${evidence.specificCurrent28dUnits}",
                "specificPrevious28dUnits=${evidence.specificPrevious28dUnits}"
            ),
            evidenceTiers = listOf(RegionalEvidenceTier.A_RESEARCH_SYNTHESIS, RegionalEvidenceTier.C_COACHING_CONSENSUS, RegionalEvidenceTier.D_PRODUCT_HEURISTIC)
        )
    }

    private fun strengthStatus(band: RegionalDoseBand): RegionalStrengthExposureStatus = when {
        band.eligibleWeekCount == 0 -> RegionalStrengthExposureStatus.UNKNOWN
        band.directExposureWeekCount == 0 || (band.current28dUnits == 0 && band.previous28dUnits == 0) -> RegionalStrengthExposureStatus.ABSENT
        band.previous28dUnits > 0 && band.current28dUnits.toDouble() < band.previous28dUnits * .5 -> RegionalStrengthExposureStatus.LOW
        band.directExposureWeekFrequency != null && band.directExposureWeekFrequency < .25 -> RegionalStrengthExposureStatus.LOW
        else -> RegionalStrengthExposureStatus.ADEQUATE
    }

    private fun hypertrophyStatus(band: RegionalDoseBand): RegionalHypertrophySupportStatus = when {
        band.eligibleWeekCount == 0 -> RegionalHypertrophySupportStatus.UNKNOWN
        band.directExposureWeekCount == 0 -> RegionalHypertrophySupportStatus.LOW
        band.previous28dUnits > 0 && band.current28dUnits.toDouble() < band.previous28dUnits * .5 -> RegionalHypertrophySupportStatus.LOW
        else -> RegionalHypertrophySupportStatus.ADEQUATE
    }
}

/** Uses the final skeleton only; it never feeds selection or prescription back into the planner. */
class ProgramEmphasisProjector {
    fun project(
        skeleton: com.training.trackplanner.data.GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): List<ProgramEmphasisLabel> {
        val totals = linkedMapOf<Pair<MovementCoverage, TrainableQuality>, Int>()
        skeleton.items.forEach { item ->
            val region = snapshot.movementCoverage(item.exerciseStableKey)
            if (region !in regionalSupportedRegions) return@forEach
            val qualities = catalog.relations(item.exerciseStableKey)
                .filter { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
                .map { relation ->
                    when (relation.qualityId) {
                        TrainableQuality.RAPID_FORCE_PRODUCTION,
                        TrainableQuality.REACTIVE_STRENGTH_SSC -> TrainableQuality.POWER
                        else -> relation.qualityId
                    }
                }.distinct()
                .filter { quality ->
                    when (quality) {
                        TrainableQuality.STRENGTH -> item.reps in 1..6
                        TrainableQuality.HYPERTROPHY -> item.reps in 7..15
                        TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION, TrainableQuality.REACTIVE_STRENGTH_SSC -> true
                        else -> false
                    }
                }
            qualities.forEach { quality ->
                totals[region to quality] = totals.getOrDefault(region to quality, 0) + item.setCount
            }
        }
        return totals.entries.asSequence()
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<Pair<MovementCoverage, TrainableQuality>, Int>> { it.value }
                .thenBy { it.key.first.ordinal }.thenBy { it.key.second.ordinal })
            .take(3)
            .map { ProgramEmphasisLabel(it.key.first, it.key.second, it.value) }
            .toList()
    }
}

internal fun RegionalDoseBand.toJson(): JSONObject = JSONObject()
    .put("eligibleWeekCount", eligibleWeekCount)
    .put("weeklyUnitsQ25", weeklyUnitsQ25).put("weeklyUnitsMedian", weeklyUnitsMedian).put("weeklyUnitsQ75", weeklyUnitsQ75)
    .put("exposureWeekUnitsQ25", exposureWeekUnitsQ25).put("exposureWeekUnitsMedian", exposureWeekUnitsMedian).put("exposureWeekUnitsQ75", exposureWeekUnitsQ75)
    .put("directExposureWeekCount", directExposureWeekCount).put("directExposureWeekFrequency", directExposureWeekFrequency)
    .put("current28dUnits", current28dUnits).put("previous28dUnits", previous28dUnits)
    .put("current28dSessions", current28dSessions).put("previous28dSessions", previous28dSessions)
    .put("confidence", confidence.name).put("source", source.name)

internal fun RegionalBottleneckDiagnosis.toJson(): JSONObject = JSONObject()
    .put("region", region.name).put("outcomeQuality", outcomeQuality.name).put("requirement", requirement.name)
    .put("performanceResponse", performanceResponse.name).put("validStrengthObservationCount", validStrengthObservationCount)
    .put("strengthExposureStatus", strengthExposureStatus.name)
    .put("strengthDoseBand", strengthDoseBand.toJson()).put("strengthExposureFrequency", strengthExposureFrequency)
    .put("specificityContinuity", specificityContinuity.name).put("hypertrophySupportStatus", hypertrophySupportStatus.name)
    .put("hypertrophyDoseBand", hypertrophyDoseBand.toJson()).put("hypertrophyExposureFrequency", hypertrophyExposureFrequency)
    .put("recoveryConstraint", recoveryConstraint).put("sportLoadInterference", sportLoadInterference)
    .put("limitingFactors", JSONArray(limitingFactors.map { it.name })).put("primaryInterpretation", primaryInterpretation.name)
    .put("confidence", confidence.name).put("reasonCodes", JSONArray(reasonCodes)).put("evidence", JSONArray(evidence))
    .put("evidenceTiers", JSONArray(evidenceTiers.map { it.name })).put("shadowOnly", shadowOnly).put("prescriptionAuthority", prescriptionAuthority)

internal fun ProgramEmphasisLabel.toJson(): JSONObject = JSONObject()
    .put("region", region.name).put("quality", quality.name).put("plannedUnits", plannedUnits)

private fun quantile(values: List<Double>, q: Double): Double? = values.takeIf { it.isNotEmpty() }?.sorted()?.let { it[((it.size - 1) * q).roundToInt()] }
