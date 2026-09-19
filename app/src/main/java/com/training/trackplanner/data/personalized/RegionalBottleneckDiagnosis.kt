package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramSetPrescription
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
    SPORT_LOAD_INTERFERENCE,
    SPECIFICITY_POSSIBLY_LIMITING,
    MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING,
    MULTIFACTORIAL,
    INSUFFICIENT_EVIDENCE
}

enum class RegionalStrengthExposureStatus { ABSENT, LOW, SUFFICIENT, UNKNOWN }
enum class RegionalHypertrophySupportStatus { LOW, SUFFICIENT, UNKNOWN }
enum class SpecificityContinuity { CONTINUOUS, REDUCED, UNKNOWN }

/** A dimension is reported separately; these values are not a weighted workload score. */
enum class RegionalDoseDimensionStatus { BELOW_PERSONAL_PATTERN, WITHIN_PERSONAL_PATTERN, UNKNOWN }

data class RegionalSpecificityEvidence(
    val stableKey: String,
    val current28dUnits: Int = 0,
    val previous28dUnits: Int = 0,
    val currentSessions: Int = 0,
    val previousSessions: Int = 0,
    val canonicalStrengthSignal: Boolean = false
)

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
    val current28dExposureWeekCount: Int = 0,
    val previous28dExposureWeekCount: Int = 0,
    val current28dWeeklyUnitsMedian: Double? = null,
    val previous28dWeeklyUnitsMedian: Double? = null,
    val current28dExposureWeekUnitsMedian: Double? = null,
    val previous28dExposureWeekUnitsMedian: Double? = null,
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
    val hypertrophyBaselineEstablished: Boolean,
    val specificityByStableKey: Map<String, RegionalSpecificityEvidence> = emptyMap()
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
    val strengthWeeklyDoseStatus: RegionalDoseDimensionStatus = RegionalDoseDimensionStatus.UNKNOWN,
    val strengthExposureWeekDoseStatus: RegionalDoseDimensionStatus = RegionalDoseDimensionStatus.UNKNOWN,
    val strengthFrequencyStatus: RegionalDoseDimensionStatus = RegionalDoseDimensionStatus.UNKNOWN,
    val specificityContinuity: SpecificityContinuity,
    val hypertrophySupportStatus: RegionalHypertrophySupportStatus,
    val hypertrophyDoseBand: RegionalDoseBand,
    val hypertrophyExposureFrequency: Double?,
    val hypertrophyWeeklyDoseStatus: RegionalDoseDimensionStatus = RegionalDoseDimensionStatus.UNKNOWN,
    val hypertrophyExposureWeekDoseStatus: RegionalDoseDimensionStatus = RegionalDoseDimensionStatus.UNKNOWN,
    val hypertrophyFrequencyStatus: RegionalDoseDimensionStatus = RegionalDoseDimensionStatus.UNKNOWN,
    val specificityEvidence: List<RegionalSpecificityEvidence> = emptyList(),
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
    val currentWeeklyUnits = linkedMapOf<LocalDate, Int>()
    val previousWeeklyUnits = linkedMapOf<LocalDate, Int>()
    var currentUnits = 0
    var previousUnits = 0
    val currentDates = linkedSetOf<LocalDate>()
    val previousDates = linkedSetOf<LocalDate>()

    fun add(row: PlanningSetRecord, weekStart: LocalDate, age: Int) {
        weeklyUnits[weekStart] = weeklyUnits.getOrDefault(weekStart, 0) + 1
        if (age in 0..27) {
            currentUnits++
            currentDates += row.date
            currentWeeklyUnits[weekStart] = currentWeeklyUnits.getOrDefault(weekStart, 0) + 1
        } else if (age in 28..55) {
            previousUnits++
            previousDates += row.date
            previousWeeklyUnits[weekStart] = previousWeeklyUnits.getOrDefault(weekStart, 0) + 1
        }
    }

    fun finish(eligibleWeeks: List<RegionalWeek>, confidence: PlanningConfidence): RegionalDoseBand {
        val weekly = eligibleWeeks.map { weeklyUnits[it.start]?.toDouble() ?: 0.0 }
        // Keep the two half-windows as independent dimensions.  Calendar weeks
        // with no regional exposure remain zeroes in these lists.
        val currentHalf = currentWeeklyUnits.values.map(Int::toDouble)
        val previousHalf = previousWeeklyUnits.values.map(Int::toDouble)
        val exposed = weekly.filter { it > 0.0 }
        val currentExposed = currentHalf.filter { it > 0.0 }
        val previousExposed = previousHalf.filter { it > 0.0 }
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
            current28dExposureWeekCount = currentExposed.size,
            previous28dExposureWeekCount = previousExposed.size,
            current28dWeeklyUnitsMedian = quantile(currentHalf, .50),
            previous28dWeeklyUnitsMedian = quantile(previousHalf, .50),
            current28dExposureWeekUnitsMedian = quantile(currentExposed, .50),
            previous28dExposureWeekUnitsMedian = quantile(previousExposed, .50),
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
        val specificByRegion = regionalSupportedRegions.associateWith { linkedMapOf<String, MutableRegionalSpecificity>() }.toMutableMap()
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
                        if (relation.modeQualifier !in setOf(PhysicalQualityMode.GENERAL, PhysicalQualityMode.OTHER)) {
                            specificStrengthRelation = true
                            val signal = snapshot.canonicalStrengthSignals[row.stableKey]
                            // Stable-key specificity is authoritative only for a
                            // canonical performance key with the existing minimum
                            // observation count.  The aggregate counters below are
                            // retained for legacy shadow payload compatibility.
                            if ((signal?.observationCount ?: 0) >= 2) {
                                val current = specificByRegion.getValue(movement).getOrPut(row.stableKey) { MutableRegionalSpecificity(row.stableKey) }
                                if (age in 0..27) current.currentUnits++ else if (age in 28..55) current.previousUnits++
                                if (age in 0..27) current.currentDates += row.date else if (age in 28..55) current.previousDates += row.date
                            }
                        }
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
                hypertrophyBand.hasPersonalBaseline,
                specificByRegion.getValue(region).mapValues { (_, value) ->
                    RegionalSpecificityEvidence(
                        stableKey = value.stableKey,
                        current28dUnits = value.currentUnits,
                        previous28dUnits = value.previousUnits,
                        currentSessions = value.currentDates.size,
                        previousSessions = value.previousDates.size,
                        canonicalStrengthSignal = true
                    )
                }
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

private class MutableRegionalSpecificity(val stableKey: String) {
    var currentUnits: Int = 0
    var previousUnits: Int = 0
    val currentDates = linkedSetOf<LocalDate>()
    val previousDates = linkedSetOf<LocalDate>()
}

class RegionalBottleneckDiagnosisEngine {
    fun analyze(
        index: RegionalEvidenceIndex,
        requirement: NeedRelevance,
        recoveryConstraint: Boolean,
        sportLoadInterference: Boolean
    ): List<RegionalBottleneckDiagnosis> = analyze(
        index,
        index.regions.keys.associateWith { requirement },
        recoveryConstraint,
        sportLoadInterference,
        emptyMap(),
        if (sportLoadInterference) setOf(
            MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES
        ) else emptySet()
    )

    fun analyze(
        index: RegionalEvidenceIndex,
        requirementByRegion: Map<MovementCoverage, NeedRelevance>,
        recoveryConstraint: Boolean,
        sportLoadInterference: Boolean,
        regionalRecoveryConstraints: Map<MovementCoverage, Boolean> = emptyMap(),
        sportLoadRegions: Set<MovementCoverage> = if (sportLoadInterference) setOf(
            MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES
        ) else emptySet()
    ): List<RegionalBottleneckDiagnosis> = index.regions.toSortedMap(compareBy { it.ordinal }).values.map { evidence ->
        diagnose(
            evidence,
            requirementByRegion[evidence.region] ?: NeedRelevance.UNKNOWN,
            regionalRecoveryConstraints[evidence.region] ?: recoveryConstraint,
            sportLoadInterference && evidence.region in sportLoadRegions
        )
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
        val specificityEvidence = evidence.specificityByStableKey.values.toList()
        val reducedSpecific = specificityEvidence.filter { it.previous28dUnits > 0 &&
            it.current28dUnits.toDouble() < it.previous28dUnits * .5 }
        val specificity = when {
            reducedSpecific.isNotEmpty() -> SpecificityContinuity.REDUCED
            specificityEvidence.any { it.previous28dUnits > 0 } -> SpecificityContinuity.CONTINUOUS
            // Backward-compatible aggregate shadow data is only used when a
            // canonical performance relation supplied it; new callers should
            // prefer specificityByStableKey.
            evidence.specificPrevious28dUnits > 0 && specificityEvidence.isEmpty() &&
                evidence.strengthBaselineEstablished -> when {
                    evidence.specificCurrent28dUnits.toDouble() < evidence.specificPrevious28dUnits * .5 -> SpecificityContinuity.REDUCED
                    else -> SpecificityContinuity.CONTINUOUS
                }
            else -> SpecificityContinuity.UNKNOWN
        }
        val performanceSufficient = evidence.strengthResponse != TrainingResponseState.INSUFFICIENT_EVIDENCE && evidence.validStrengthObservationCount >= 2
        val positive = evidence.strengthResponse == TrainingResponseState.POSITIVE_RESPONSE
        val factors = mutableListOf<RegionalLimitingFactor>()
        if (requirement in setOf(NeedRelevance.MODERATE, NeedRelevance.HIGH) && !positive && strengthStatus == RegionalStrengthExposureStatus.LOW &&
            (evidence.strengthBaselineEstablished || performanceSufficient)) factors += RegionalLimitingFactor.EXPOSURE_LIMITED
        if (recoveryConstraint && !positive && performanceSufficient) factors += RegionalLimitingFactor.RECOVERY_LIMITED
        if (sportLoadInterference && !positive && performanceSufficient) factors += RegionalLimitingFactor.SPORT_LOAD_INTERFERENCE
        if (specificity == SpecificityContinuity.REDUCED && strengthStatus == RegionalStrengthExposureStatus.SUFFICIENT &&
            evidence.strengthResponse in setOf(TrainingResponseState.STABLE_RESPONSE, TrainingResponseState.NEGATIVE_RESPONSE)) {
            factors += RegionalLimitingFactor.SPECIFICITY_POSSIBLY_LIMITING
        }
        if (!positive && performanceSufficient && !recoveryConstraint && !sportLoadInterference && specificity != SpecificityContinuity.REDUCED &&
            strengthStatus == RegionalStrengthExposureStatus.SUFFICIENT && hypoStatus == RegionalHypertrophySupportStatus.LOW &&
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
            if (finalFactors.contains(RegionalLimitingFactor.SPORT_LOAD_INTERFERENCE)) add("LOWER_REGION_COURT_LOAD_IS_A_COMPETING_EXPLANATION")
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
            strengthWeeklyDoseStatus = strengthDimensionStatus(strength, exposureWeek = false),
            strengthExposureWeekDoseStatus = strengthDimensionStatus(strength, exposureWeek = true),
            strengthFrequencyStatus = frequencyStatus(strength),
            specificityContinuity = specificity,
            hypertrophySupportStatus = hypoStatus,
            hypertrophyDoseBand = hypo,
            hypertrophyExposureFrequency = hypo.directExposureWeekFrequency,
            hypertrophyWeeklyDoseStatus = strengthDimensionStatus(hypo, exposureWeek = false),
            hypertrophyExposureWeekDoseStatus = strengthDimensionStatus(hypo, exposureWeek = true),
            hypertrophyFrequencyStatus = frequencyStatus(hypo),
            specificityEvidence = specificityEvidence,
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
                "strengthWeeklyDoseStatus=${strengthDimensionStatus(strength, false)}",
                "strengthExposureWeekDoseStatus=${strengthDimensionStatus(strength, true)}",
                "strengthFrequencyStatus=${frequencyStatus(strength)}",
                "hypertrophyWeeklyDoseStatus=${strengthDimensionStatus(hypo, false)}",
                "hypertrophyExposureWeekDoseStatus=${strengthDimensionStatus(hypo, true)}",
                "hypertrophyFrequencyStatus=${frequencyStatus(hypo)}",
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
        strengthDimensionStatus(band, false) == RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN ||
            strengthDimensionStatus(band, true) == RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN ||
            frequencyStatus(band) == RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN -> RegionalStrengthExposureStatus.LOW
        else -> RegionalStrengthExposureStatus.SUFFICIENT
    }

    private fun hypertrophyStatus(band: RegionalDoseBand): RegionalHypertrophySupportStatus = when {
        band.eligibleWeekCount == 0 -> RegionalHypertrophySupportStatus.UNKNOWN
        band.directExposureWeekCount == 0 -> RegionalHypertrophySupportStatus.LOW
        !band.hasPersonalBaseline -> RegionalHypertrophySupportStatus.LOW
        strengthDimensionStatus(band, false) == RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN ||
            strengthDimensionStatus(band, true) == RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN ||
            frequencyStatus(band) == RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN -> RegionalHypertrophySupportStatus.LOW
        else -> RegionalHypertrophySupportStatus.SUFFICIENT
    }

    private fun strengthDimensionStatus(band: RegionalDoseBand, exposureWeek: Boolean): RegionalDoseDimensionStatus {
        val current = if (exposureWeek) band.current28dExposureWeekUnitsMedian else band.current28dWeeklyUnitsMedian
        val previous = if (exposureWeek) band.previous28dExposureWeekUnitsMedian else band.previous28dWeeklyUnitsMedian
        return when {
            current == null || previous == null || previous <= 0.0 -> RegionalDoseDimensionStatus.UNKNOWN
            current < previous * .5 -> RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN
            else -> RegionalDoseDimensionStatus.WITHIN_PERSONAL_PATTERN
        }
    }

    private fun frequencyStatus(band: RegionalDoseBand): RegionalDoseDimensionStatus = when {
        band.previous28dExposureWeekCount <= 0 -> RegionalDoseDimensionStatus.UNKNOWN
        // One week over a four-week half is the transparent tolerance.  A
        // second missing exposure is meaningful decline evidence.
        band.previous28dExposureWeekCount - band.current28dExposureWeekCount > 1 -> RegionalDoseDimensionStatus.BELOW_PERSONAL_PATTERN
        else -> RegionalDoseDimensionStatus.WITHIN_PERSONAL_PATTERN
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
            val prescriptions = item.setPrescriptions.ifEmpty {
                // Legacy skeletons without set rows retain the canonical scalar fallback.
                List(item.setCount.coerceAtLeast(0)) { ProgramSetPrescription(it + 1, item.reps, item.weightKg, item.seconds) }
            }
            prescriptions.forEach { set ->
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
                            TrainableQuality.STRENGTH -> provisionalRealizedStimulusClass(set.reps) == RealizedStimulusClass.STRENGTH_LIKE
                            TrainableQuality.HYPERTROPHY -> provisionalRealizedStimulusClass(set.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE
                            TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION, TrainableQuality.REACTIVE_STRENGTH_SSC -> true
                            else -> false
                        }
                    }
                qualities.forEach { quality ->
                    totals[region to quality] = totals.getOrDefault(region to quality, 0) + 1
                }
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
    .put("current28dExposureWeekCount", current28dExposureWeekCount)
    .put("previous28dExposureWeekCount", previous28dExposureWeekCount)
    .put("current28dWeeklyUnitsMedian", current28dWeeklyUnitsMedian)
    .put("previous28dWeeklyUnitsMedian", previous28dWeeklyUnitsMedian)
    .put("current28dExposureWeekUnitsMedian", current28dExposureWeekUnitsMedian)
    .put("previous28dExposureWeekUnitsMedian", previous28dExposureWeekUnitsMedian)
    .put("confidence", confidence.name).put("source", source.name)

internal fun RegionalBottleneckDiagnosis.toJson(): JSONObject = JSONObject()
    .put("region", region.name).put("outcomeQuality", outcomeQuality.name).put("requirement", requirement.name)
    .put("performanceResponse", performanceResponse.name).put("validStrengthObservationCount", validStrengthObservationCount)
    .put("strengthExposureStatus", strengthExposureStatus.name)
    .put("strengthDoseBand", strengthDoseBand.toJson()).put("strengthExposureFrequency", strengthExposureFrequency)
    .put("strengthWeeklyDoseStatus", strengthWeeklyDoseStatus.name)
    .put("strengthExposureWeekDoseStatus", strengthExposureWeekDoseStatus.name)
    .put("strengthFrequencyStatus", strengthFrequencyStatus.name)
    .put("specificityContinuity", specificityContinuity.name).put("hypertrophySupportStatus", hypertrophySupportStatus.name)
    .put("hypertrophyDoseBand", hypertrophyDoseBand.toJson()).put("hypertrophyExposureFrequency", hypertrophyExposureFrequency)
    .put("hypertrophyWeeklyDoseStatus", hypertrophyWeeklyDoseStatus.name)
    .put("hypertrophyExposureWeekDoseStatus", hypertrophyExposureWeekDoseStatus.name)
    .put("hypertrophyFrequencyStatus", hypertrophyFrequencyStatus.name)
    .put("specificityEvidence", JSONArray(specificityEvidence.map { evidence -> JSONObject()
        .put("stableKey", evidence.stableKey)
        .put("current28dUnits", evidence.current28dUnits)
        .put("previous28dUnits", evidence.previous28dUnits)
        .put("currentSessions", evidence.currentSessions)
        .put("previousSessions", evidence.previousSessions)
        .put("canonicalStrengthSignal", evidence.canonicalStrengthSignal)
    } ))
    .put("recoveryConstraint", recoveryConstraint).put("sportLoadInterference", sportLoadInterference)
    .put("limitingFactors", JSONArray(limitingFactors.map { it.name })).put("primaryInterpretation", primaryInterpretation.name)
    .put("confidence", confidence.name).put("reasonCodes", JSONArray(reasonCodes)).put("evidence", JSONArray(evidence))
    .put("evidenceTiers", JSONArray(evidenceTiers.map { it.name })).put("shadowOnly", shadowOnly).put("prescriptionAuthority", prescriptionAuthority)

internal fun ProgramEmphasisLabel.toJson(): JSONObject = JSONObject()
    .put("region", region.name).put("quality", quality.name).put("plannedUnits", plannedUnits)

private fun quantile(values: List<Double>, q: Double): Double? = values.takeIf { it.isNotEmpty() }?.sorted()?.let { it[((it.size - 1) * q).roundToInt()] }
