package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import kotlin.math.max
import kotlin.math.roundToInt

/** Internal/test-only switch. CONTROL is the production default. */
enum class RegionalPlanningAuthorityMode {
    CONTROL,
    EXPERIMENTAL_REGIONAL_TARGETS
}

/** Requirement importance is independent of current exposure representation. */
class RegionalStrengthRequirementResolver {
    fun resolve(
        globalStrengthRelevance: NeedRelevance,
        representations: Iterable<MovementExposureRepresentation>
    ): Map<MovementCoverage, NeedRelevance> {
        if (globalStrengthRelevance == NeedRelevance.UNKNOWN) return emptyMap()
        val result = linkedMapOf<MovementCoverage, NeedRelevance>()
        representations.forEach { representation ->
            val owner = when (representation.movementCoverage) {
                "UPPER_PULL" -> listOf(MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL)
                else -> runCatching { listOf(MovementCoverage.valueOf(representation.movementCoverage)) }.getOrDefault(emptyList())
            }
            owner.forEach { region ->
                val regional = when (representation.basePriority) {
                    RepresentationPriority.HIGH -> globalStrengthRelevance
                    RepresentationPriority.MODERATE -> when (globalStrengthRelevance) {
                        NeedRelevance.HIGH -> NeedRelevance.MODERATE
                        NeedRelevance.MODERATE -> NeedRelevance.MODERATE
                        NeedRelevance.LOW -> NeedRelevance.LOW
                        else -> globalStrengthRelevance
                    }
                }
                result[region] = regional
            }
        }
        return result
    }

    fun resolve(
        globalStrengthRelevance: NeedRelevance,
        representations: Iterable<MovementExposureRepresentation>,
        supportedRegions: Set<MovementCoverage>
    ): Map<MovementCoverage, NeedRelevance> = resolve(globalStrengthRelevance, representations)
        .filterKeys { it in supportedRegions }
}

enum class RegionalTrainingDecision {
    PRESERVE_EFFECTIVE_STRENGTH,
    RESTORE_STRENGTH_EXPOSURE,
    RESTORE_SPECIFIC_STRENGTH_PRACTICE,
    ADD_HYPERTROPHY_SUPPORT,
    HOLD_FOR_RECOVERY,
    HOLD_FOR_SPORT_LOAD,
    NO_AUTHORIZED_CHANGE,
    UNRESOLVED
}

enum class RegionalTargetAction {
    PRESERVE,
    RESTORE,
    ADD_SUPPORT,
    HOLD,
    NONE
}

enum class RegionalNumericAuthority {
    PRE_DECLINE_PERSONAL_PATTERN,
    FULL_WINDOW_PERSONAL_BAND,
    PRIOR_TOLERATED_HYPERTROPHY,
    DIRECTION_ONLY,
    NONE
}

data class RegionalTrainingDecisionResult(
    val region: MovementCoverage,
    val decision: RegionalTrainingDecision,
    val reasonCodes: List<String> = emptyList()
)

class RegionalTrainingDecisionResolver {
    fun resolve(diagnosis: RegionalBottleneckDiagnosis): RegionalTrainingDecisionResult {
        if (diagnosis.performanceResponse == TrainingResponseState.POSITIVE_RESPONSE) {
            return RegionalTrainingDecisionResult(
                diagnosis.region,
                RegionalTrainingDecision.PRESERVE_EFFECTIVE_STRENGTH,
                listOf("POSITIVE_RESPONSE_SUPPRESSES_UNSUPPORTED_INTERVENTION")
            )
        }
        if (RegionalLimitingFactor.RECOVERY_LIMITED in diagnosis.limitingFactors) {
            return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.HOLD_FOR_RECOVERY,
                listOf("SYSTEMIC_OR_REGION_TISSUE_RECOVERY_HOLD"))
        }
        if (RegionalLimitingFactor.SPORT_LOAD_INTERFERENCE in diagnosis.limitingFactors) {
            return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.HOLD_FOR_SPORT_LOAD,
                listOf("COURT_LOAD_COMPETING_LOWER_REGION_EXPLANATION"))
        }
        if (RegionalLimitingFactor.SPECIFICITY_POSSIBLY_LIMITING in diagnosis.limitingFactors) {
            return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.RESTORE_SPECIFIC_STRENGTH_PRACTICE,
                listOf("CANONICAL_STABLE_KEY_PRACTICE_DECLINED"))
        }
        if (RegionalLimitingFactor.EXPOSURE_LIMITED in diagnosis.limitingFactors) {
            return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.RESTORE_STRENGTH_EXPOSURE,
                listOf("REGIONAL_STRENGTH_EXPOSURE_BELOW_PERSONAL_PATTERN"))
        }
        if (RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING in diagnosis.limitingFactors) {
            return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.ADD_HYPERTROPHY_SUPPORT,
                listOf("POSSIBLE_MORPHOLOGICAL_LIMITATION_NEEDS_HYPERTROPHY_SUPPORT"))
        }
        return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.NO_AUTHORIZED_CHANGE,
            listOf("NO_AUTHORIZED_REGIONAL_CHANGE"))
    }
}

data class RegionalStimulusTarget(
    val region: MovementCoverage,
    val quality: TrainableQuality,
    val action: RegionalTargetAction,
    val numericAuthority: RegionalNumericAuthority,
    val weeklyDoseTarget: Double? = null,
    val exposureWeekDoseTarget: Double? = null,
    val exposureFrequencyTarget: Double? = null,
    val priority: NeedRelevance = NeedRelevance.UNKNOWN,
    val reasonCodes: List<String> = emptyList(),
    val specificStableKey: String? = null
)

class RegionalStimulusTargetResolver(
    private val decisions: RegionalTrainingDecisionResolver = RegionalTrainingDecisionResolver()
) {
    fun resolve(diagnosis: RegionalBottleneckDiagnosis): RegionalStimulusTarget {
        val decision = decisions.resolve(diagnosis)
        val strength = diagnosis.strengthDoseBand
        val hypo = diagnosis.hypertrophyDoseBand
        return when (decision.decision) {
            RegionalTrainingDecision.RESTORE_STRENGTH_EXPOSURE -> strengthTarget(diagnosis, RegionalTargetAction.RESTORE)
            RegionalTrainingDecision.RESTORE_SPECIFIC_STRENGTH_PRACTICE -> strengthTarget(diagnosis, RegionalTargetAction.RESTORE,
                diagnosis.specificityEvidence.firstOrNull { it.previous28dUnits > 0 }?.stableKey)
            RegionalTrainingDecision.PRESERVE_EFFECTIVE_STRENGTH -> RegionalStimulusTarget(
                diagnosis.region, TrainableQuality.STRENGTH, RegionalTargetAction.PRESERVE,
                RegionalNumericAuthority.PRE_DECLINE_PERSONAL_PATTERN,
                weeklyDoseTarget = strength.current28dWeeklyUnitsMedian,
                exposureWeekDoseTarget = strength.current28dExposureWeekUnitsMedian,
                exposureFrequencyTarget = strength.directExposureWeekFrequency,
                priority = diagnosis.requirement,
                reasonCodes = decision.reasonCodes
            )
            RegionalTrainingDecision.ADD_HYPERTROPHY_SUPPORT -> {
                val authority = when {
                    hypo.hasPersonalBaseline && hypo.previous28dExposureWeekUnitsMedian != null -> RegionalNumericAuthority.PRIOR_TOLERATED_HYPERTROPHY
                    hypo.hasPersonalBaseline -> RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND
                    else -> RegionalNumericAuthority.DIRECTION_ONLY
                }
                RegionalStimulusTarget(
                    diagnosis.region, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
                    authority,
                    weeklyDoseTarget = if (authority == RegionalNumericAuthority.DIRECTION_ONLY) null else
                        (hypo.previous28dWeeklyUnitsMedian ?: hypo.weeklyUnitsMedian),
                    exposureWeekDoseTarget = if (authority == RegionalNumericAuthority.DIRECTION_ONLY) null else
                        (hypo.previous28dExposureWeekUnitsMedian ?: hypo.exposureWeekUnitsMedian),
                    exposureFrequencyTarget = if (authority == RegionalNumericAuthority.DIRECTION_ONLY) null else hypo.directExposureWeekFrequency,
                    priority = diagnosis.requirement,
                    reasonCodes = decision.reasonCodes + if (authority == RegionalNumericAuthority.DIRECTION_ONLY)
                        listOf("DIRECTION_ONLY_CANNOT_INVENT_NUMERIC_VOLUME") else emptyList()
                )
            }
            RegionalTrainingDecision.HOLD_FOR_RECOVERY, RegionalTrainingDecision.HOLD_FOR_SPORT_LOAD -> RegionalStimulusTarget(
                diagnosis.region, TrainableQuality.STRENGTH, RegionalTargetAction.HOLD,
                RegionalNumericAuthority.NONE, priority = diagnosis.requirement, reasonCodes = decision.reasonCodes
            )
            RegionalTrainingDecision.NO_AUTHORIZED_CHANGE, RegionalTrainingDecision.UNRESOLVED -> RegionalStimulusTarget(
                diagnosis.region, TrainableQuality.STRENGTH, RegionalTargetAction.NONE,
                RegionalNumericAuthority.NONE, priority = diagnosis.requirement, reasonCodes = decision.reasonCodes
            )
        }
    }

    private fun strengthTarget(
        diagnosis: RegionalBottleneckDiagnosis,
        action: RegionalTargetAction,
        specificStableKey: String? = null
    ): RegionalStimulusTarget {
        val band = diagnosis.strengthDoseBand
        val authority = when {
            band.previous28dWeeklyUnitsMedian != null && band.previous28dWeeklyUnitsMedian > 0.0 -> RegionalNumericAuthority.PRE_DECLINE_PERSONAL_PATTERN
            band.hasPersonalBaseline && band.weeklyUnitsMedian != null -> RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND
            else -> RegionalNumericAuthority.DIRECTION_ONLY
        }
        return RegionalStimulusTarget(
            diagnosis.region, TrainableQuality.STRENGTH, action, authority,
            weeklyDoseTarget = if (authority == RegionalNumericAuthority.DIRECTION_ONLY) null else
                (band.previous28dWeeklyUnitsMedian ?: band.weeklyUnitsMedian),
            exposureWeekDoseTarget = if (authority == RegionalNumericAuthority.DIRECTION_ONLY) null else
                (band.previous28dExposureWeekUnitsMedian ?: band.exposureWeekUnitsMedian),
            exposureFrequencyTarget = if (authority == RegionalNumericAuthority.DIRECTION_ONLY) null else
                (band.previous28dExposureWeekCount.toDouble() / 4.0).coerceAtMost(1.0),
            priority = diagnosis.requirement,
            reasonCodes = listOf("PRE_DECLINE_PATTERN_PREFERRED_WHEN_DECLINE_IS_THE_EVIDENCE") +
                if (authority == RegionalNumericAuthority.DIRECTION_ONLY) listOf("DIRECTION_ONLY_CANNOT_INVENT_NUMERIC_VOLUME") else emptyList(),
            specificStableKey = specificStableKey
        )
    }
}

data class RegionalPlannedStimulus(
    val region: MovementCoverage,
    val quality: TrainableQuality,
    val weeklyUnits: Int,
    val exposureWeeks: Int,
    val exposureFrequency: Double
)

/** Credits final set prescriptions, not representative item.reps. */
class RegionalStimulusCreditProjector {
    fun project(
        plan: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): Map<Pair<MovementCoverage, TrainableQuality>, RegionalPlannedStimulus> {
        val rows = linkedMapOf<Pair<MovementCoverage, TrainableQuality>, MutableRegionalPlannedStimulus>()
        val weekCount = plan.weekPlans.size.coerceAtLeast(1)
        plan.items.forEach { item ->
            val region = snapshot.movementCoverage(item.exerciseStableKey)
            if (region == MovementCoverage.OTHER) return@forEach
            item.setPrescriptions.forEach { set ->
                val realized = when {
                    provisionalRealizedStimulusClass(set.reps) == RealizedStimulusClass.STRENGTH_LIKE -> TrainableQuality.STRENGTH
                    provisionalRealizedStimulusClass(set.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE -> TrainableQuality.HYPERTROPHY
                    else -> null
                } ?: return@forEach
                catalog.relations(item.exerciseStableKey).filter {
                    it.qualityId == realized && it.relationLevel.name == "DIRECT_CAPABILITY" &&
                        regionQualifierMatches(region, it.regionQualifier)
                }.forEach {
                    val key = region to realized
                    val value = rows.getOrPut(key) { MutableRegionalPlannedStimulus(region, realized) }
                    value.units++
                    value.weeks += item.weekNumber
                }
            }
        }
        return rows.mapValues { (_, value) ->
            RegionalPlannedStimulus(value.region, value.quality, (value.units.toDouble() / weekCount).roundToInt(), value.weeks.size,
                value.weeks.size.toDouble() / weekCount)
        }
    }

    private class MutableRegionalPlannedStimulus(
        val region: MovementCoverage,
        val quality: TrainableQuality
    ) {
        var units = 0
        val weeks = linkedSetOf<Int>()
    }

    private fun regionQualifierMatches(movement: MovementCoverage, qualifier: com.training.trackplanner.data.PhysicalQualityRegion): Boolean = when (movement) {
        MovementCoverage.LOWER_KNEE -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.QUADS_GLUTE, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.POSTERIOR_CHAIN -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.POSTERIOR_CHAIN, com.training.trackplanner.data.PhysicalQualityRegion.HAMSTRING, com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.CALVES -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.ANKLE, com.training.trackplanner.data.PhysicalQualityRegion.LOWER)
        MovementCoverage.HORIZONTAL_PUSH -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH, com.training.trackplanner.data.PhysicalQualityRegion.CHEST, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.VERTICAL_PUSH -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        else -> false
    }
}

data class RegionalAuthorityTrace(
    val region: MovementCoverage,
    val diagnosis: RegionalBottleneckDiagnosis,
    val requirement: NeedRelevance,
    val performanceResponse: TrainingResponseState,
    val trainingDecision: RegionalTrainingDecision,
    val targetQuality: TrainableQuality,
    val targetAction: RegionalTargetAction,
    val targetWeeklyDose: Double?,
    val targetExposureWeekDose: Double?,
    val targetFrequency: Double?,
    val numericAuthority: RegionalNumericAuthority,
    val existingPlannedCompatibleDose: Int,
    val existingPlannedExposureFrequency: Double,
    val residualDose: Int,
    val candidatePool: List<String> = emptyList(),
    val selectedStableKey: String? = null,
    val selectionReasons: List<String> = emptyList(),
    val prescriptionCompatibility: String = "UNRESOLVED",
    val requestedUnits: Int = 0,
    val authorizedUnits: Int = 0,
    val materializedUnits: Int = 0,
    val shortfall: Int = 0,
    val finalReasonCodes: List<String> = emptyList()
)

/** Candidate selection is typed by MovementCoverage and TrainableQuality. */
class RegionalTargetCandidateSelector {
    data class Selection(
        val target: RegionalStimulusTarget,
        val credit: RegionalPlannedStimulus?,
        val candidates: List<PlannedExercise>,
        val selected: PlannedExercise?,
        val residualUnits: Int,
        val reasons: List<String>
    )

    fun select(
        target: RegionalStimulusTarget,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        existingPlan: GeneratedProgramSkeleton? = null,
        usedStableKeys: Set<String> = emptySet(),
        catalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY
    ): Selection {
        if (target.action !in setOf(RegionalTargetAction.ADD_SUPPORT, RegionalTargetAction.RESTORE) ||
            target.numericAuthority == RegionalNumericAuthority.DIRECTION_ONLY ||
            target.numericAuthority == RegionalNumericAuthority.NONE || target.weeklyDoseTarget == null
        ) return Selection(target, null, emptyList(), null, 0, listOf("NO_NUMERIC_TARGET_OR_NO_ADD_AUTHORITY"))

        val credit = existingPlan?.let {
            RegionalStimulusCreditProjector().project(it, snapshot, catalog)
                .get(target.region to target.quality)
        }
        val alreadyPlanned = credit?.weeklyUnits ?: 0
        val requested = target.weeklyDoseTarget.roundToInt().coerceAtLeast(0)
        val residual = (requested - alreadyPlanned).coerceAtLeast(0)
        if (residual == 0) return Selection(target, credit, emptyList(), null, 0, listOf("EXISTING_PLAN_CREDIT_COVERS_TARGET"))

        val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
        val pool = snapshot.exercises.keys.asSequence()
            .filter { key -> target.specificStableKey == null || key == target.specificStableKey }
            .filter { key -> key !in usedStableKeys }
            .filter { key -> snapshot.movementCoverage(key) == target.region }
            .filter { key -> snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") }
            .filterNot(snapshot::explicitlyRestricted)
            .filter { key -> key !in snapshot.recoverySignals.tissueRestrictedStableKeys }
            .filter { key -> exactCapability(catalog, key, target) }
            .filter { key -> equipmentCompatible(snapshot, key, state) }
            .filter { key -> prescriptionCompatible(snapshot, key, target.quality) }
            .sortedWith(
                compareByDescending<String> { it in historyKeys }
                    .thenByDescending { qualityCompatibleHistory(snapshot, it, target.quality) }
                    .thenByDescending { snapshot.metadata[it]?.sourceConfidenceLevel == "HIGH" }
                    .thenBy { redundancyPenalty(snapshot, it, existingPlan) }
                    .thenBy { it }
            ).toList()
        val chosen = pool.firstOrNull()?.let { key ->
            PlannedExercise(
                stableKey = key,
                role = "REGIONAL_TARGET_${target.region.name}_${target.quality.name}",
                reason = "Experimental target ${target.region.name} × ${target.quality.name}; diagnosis does not choose exercise identity.",
                priority = when (target.priority) { NeedRelevance.HIGH -> 100; NeedRelevance.MODERATE -> 90; NeedRelevance.LOW -> 70; else -> 50 },
                targetSets = residual,
                representedGapCodes = setOf("REGIONAL_TARGET_${target.region.name}_${target.quality.name}"),
                regionalTarget = target
            )
        }
        return Selection(target, credit, pool.map { key ->
            PlannedExercise(key, "REGIONAL_CANDIDATE", "Typed regional candidate", 0, targetSets = residual, material = true, regionalTarget = target)
        }, chosen, residual, listOfNotNull(
            "EXISTING_PLANNED_COMPATIBLE_UNITS=$alreadyPlanned",
            "RESIDUAL_UNITS=$residual",
            chosen?.let { "LEXICOGRAPHIC_WINNER=${it.stableKey}" } ?: "NO_ELIGIBLE_CANDIDATE"
        ))
    }

    private fun exactCapability(catalog: CanonicalExercisePhysicalQualityCatalog, key: String, target: RegionalStimulusTarget): Boolean =
        catalog.relations(key).any { relation ->
            relation.qualityId == target.quality && relation.relationLevel == com.training.trackplanner.data.StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                regionQualifierMatches(target.region, relation.regionQualifier)
        }

    private fun prescriptionCompatible(snapshot: PlanningHistorySnapshot, key: String, quality: TrainableQuality): Boolean {
        val rows = snapshot.allConfirmedSets.filter { it.stableKey == key }
        return when (quality) {
            TrainableQuality.STRENGTH -> rows.any { provisionalRealizedStimulusClass(it) == RealizedStimulusClass.STRENGTH_LIKE && it.weightKg > 0.0 } ||
                snapshot.canonicalStrengthSignals[key]?.observationCount?.let { it >= 2 } == true
            TrainableQuality.HYPERTROPHY -> true
            else -> false
        }
    }

    private fun qualityCompatibleHistory(snapshot: PlanningHistorySnapshot, key: String, quality: TrainableQuality): Boolean =
        snapshot.allConfirmedSets.any { it.stableKey == key && when (quality) {
            TrainableQuality.STRENGTH -> provisionalRealizedStimulusClass(it) == RealizedStimulusClass.STRENGTH_LIKE
            TrainableQuality.HYPERTROPHY -> provisionalRealizedStimulusClass(it) == RealizedStimulusClass.HYPERTROPHY_LIKE
            else -> false
        } }

    private fun equipmentCompatible(snapshot: PlanningHistorySnapshot, key: String, state: AthletePlanningState): Boolean {
        val exercise = snapshot.exercises[key] ?: return false
        return state.freeWeightWillingness != FreeWeightWillingness.AVOID || !snapshot.isFreeWeight(key) ||
            snapshot.allConfirmedSets.any { it.stableKey == key } && exercise.equipment.isNotBlank()
    }

    private fun redundancyPenalty(snapshot: PlanningHistorySnapshot, key: String, existingPlan: GeneratedProgramSkeleton?): Int {
        val group = snapshot.metadata[key]?.redundancyGroup.orEmpty()
        return if (group.isBlank()) 0 else existingPlan?.items.orEmpty().count { it.redundancyGroup == group }
    }

    private fun regionQualifierMatches(movement: MovementCoverage, qualifier: com.training.trackplanner.data.PhysicalQualityRegion): Boolean = when (movement) {
        MovementCoverage.LOWER_KNEE -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.QUADS_GLUTE, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.POSTERIOR_CHAIN -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.POSTERIOR_CHAIN, com.training.trackplanner.data.PhysicalQualityRegion.HAMSTRING, com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.CALVES -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.ANKLE, com.training.trackplanner.data.PhysicalQualityRegion.LOWER)
        MovementCoverage.HORIZONTAL_PUSH, MovementCoverage.VERTICAL_PUSH -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH, com.training.trackplanner.data.PhysicalQualityRegion.CHEST, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        else -> false
    }
}

data class RegionalExperimentalMaterialDemand(
    val demand: MaterialDemand,
    val traces: List<RegionalAuthorityTrace>,
    val counters: RegionalAuthorityCounters
)

class RegionalExperimentalMaterialDemandBuilder(
    private val requirementResolver: RegionalTrainingDecisionResolver = RegionalTrainingDecisionResolver(),
    private val targetResolver: RegionalStimulusTargetResolver = RegionalStimulusTargetResolver(),
    private val selector: RegionalTargetCandidateSelector = RegionalTargetCandidateSelector()
) {
    fun build(
        diagnoses: List<RegionalBottleneckDiagnosis>,
        control: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): RegionalExperimentalMaterialDemand {
        val candidates = mutableListOf<PlannedExercise>()
        val traces = mutableListOf<RegionalAuthorityTrace>()
        var candidateCount = 0
        diagnoses.sortedBy { it.region.ordinal }.forEach { diagnosis ->
            val decision = requirementResolver.resolve(diagnosis)
            val target = targetResolver.resolve(diagnosis)
            val selection = selector.select(target, snapshot, state, control, candidates.map(PlannedExercise::stableKey).toSet(), catalog)
            candidateCount += selection.candidates.size
            selection.selected?.let(candidates::add)
            val credit = selection.credit
            traces += RegionalAuthorityTrace(
                region = diagnosis.region,
                diagnosis = diagnosis,
                requirement = diagnosis.requirement,
                performanceResponse = diagnosis.performanceResponse,
                trainingDecision = decision.decision,
                targetQuality = target.quality,
                targetAction = target.action,
                targetWeeklyDose = target.weeklyDoseTarget,
                targetExposureWeekDose = target.exposureWeekDoseTarget,
                targetFrequency = target.exposureFrequencyTarget,
                numericAuthority = target.numericAuthority,
                existingPlannedCompatibleDose = credit?.weeklyUnits ?: 0,
                existingPlannedExposureFrequency = credit?.exposureFrequency ?: 0.0,
                residualDose = selection.residualUnits,
                candidatePool = selection.candidates.map(PlannedExercise::stableKey),
                selectedStableKey = selection.selected?.stableKey,
                selectionReasons = selection.reasons,
                prescriptionCompatibility = selection.selected?.let { "TARGET_COMPATIBLE_EXISTING_PRESCRIPTION_AUTHORITY" } ?: "NO_SAFE_AUTHORITY",
                requestedUnits = target.weeklyDoseTarget?.roundToInt() ?: 0,
                authorizedUnits = selection.selected?.targetSets ?: 0,
                finalReasonCodes = target.reasonCodes + selection.reasons
            )
        }
        return RegionalExperimentalMaterialDemand(
            demand = MaterialDemand(
                candidates = candidates,
                deferred = traces.filter { it.residualDose > 0 && it.selectedStableKey == null }
                    .associate { "REGIONAL_TARGET_${it.region.name}_${it.targetQuality.name}" to "NO_ELIGIBLE_CANDIDATE_OR_CAPACITY" },
                audit = candidates.associate { it.stableKey to "SELECTED_REGIONAL_TARGET_CANDIDATE" }
            ),
            traces = traces,
            counters = RegionalAuthorityCounters(
                historyRowsIndexed = snapshot.allConfirmedSets.size,
                regionalDiagnosesProduced = diagnoses.size,
                regionalTargetsProduced = traces.size,
                candidatePoolsEvaluated = diagnoses.size,
                candidateCountEvaluated = candidateCount
            )
        )
    }
}

data class RegionalProgramComparison(
    val control: GeneratedProgramSkeleton,
    val experimental: GeneratedProgramSkeleton,
    val traces: List<RegionalAuthorityTrace>,
    val counters: RegionalAuthorityCounters,
    val differences: List<String> = emptyList(),
    val controlTargetComparison: TargetPlanComparison? = control.personalizedDecision?.targetPlanComparison,
    val experimentalTargetComparison: TargetPlanComparison? = experimental.personalizedDecision?.targetPlanComparison
)

/** Compares final skeletons without making the experimental plan persistent. */
class RegionalAuthorityProgramComparison {
    fun compare(
        control: GeneratedProgramSkeleton,
        experimental: GeneratedProgramSkeleton,
        traces: List<RegionalAuthorityTrace>,
        counters: RegionalAuthorityCounters
    ): RegionalProgramComparison {
        val a = control.items.map { listOf(it.weekNumber, it.dayOfWeek, it.exerciseStableKey, it.setPrescriptions.joinToString { set -> "${set.reps}:${set.weightKg}:${set.seconds}" }) }
        val b = experimental.items.map { listOf(it.weekNumber, it.dayOfWeek, it.exerciseStableKey, it.setPrescriptions.joinToString { set -> "${set.reps}:${set.weightKg}:${set.seconds}" }) }
        val differences = buildList {
            if (a != b) add("FINAL_SKELETON_CHANGED")
            traces.filter { it.selectedStableKey != null && it.residualDose > 0 }.forEach { add("${it.region.name}:${it.trainingDecision.name}:${it.selectedStableKey}") }
        }
        return RegionalProgramComparison(control, experimental, traces, counters, differences)
    }
}



/** A compact immutable counter set for audit output, not a benchmarking framework. */
data class RegionalAuthorityCounters(
    val historyRowsIndexed: Int = 0,
    val regionalDiagnosesProduced: Int = 0,
    val regionalTargetsProduced: Int = 0,
    val candidatePoolsEvaluated: Int = 0,
    val candidateCountEvaluated: Int = 0,
    val prescriptionResolutions: Int = 0
)
