package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.canonicalTargetRpeFingerprint
import java.time.temporal.TemporalAdjusters
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
    USER_APPROVED_PROJECT_POLICY,
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
        if (diagnosis.recoveryConstraint || RegionalLimitingFactor.RECOVERY_LIMITED in diagnosis.limitingFactors) {
            return RegionalTrainingDecisionResult(diagnosis.region, RegionalTrainingDecision.HOLD_FOR_RECOVERY,
                listOf("CURRENT_RECOVERY_CONSTRAINT_INTERVENTION_HOLD"))
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
                    else -> RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY
                }
                val weeklyTarget = when (authority) {
                    RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY -> RegionalColdStartDosePolicy.HYPERTROPHY_EQUIVALENT_SETS_PER_REGION_WEEK
                    else -> hypo.previous28dWeeklyUnitsMedian ?: hypo.weeklyUnitsMedian
                }
                RegionalStimulusTarget(
                    diagnosis.region, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
                    authority,
                    weeklyDoseTarget = weeklyTarget,
                    exposureWeekDoseTarget = if (authority == RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY) null else
                        (hypo.previous28dExposureWeekUnitsMedian ?: hypo.exposureWeekUnitsMedian),
                    exposureFrequencyTarget = if (authority == RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY) null else hypo.directExposureWeekFrequency,
                    priority = diagnosis.requirement,
                    reasonCodes = decision.reasonCodes + if (authority == RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY)
                        listOf("USER_APPROVED_PROJECT_POLICY_HYPERTROPHY_COLD_START_8_EQUIVALENT_SETS_PER_REGION_WEEK") else emptyList()
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
                if (authority == RegionalNumericAuthority.DIRECTION_ONLY) listOf("STRENGTH_COLD_START_REQUIRES_APPROVED_ANCHOR_IDENTITY") else emptyList(),
            specificStableKey = specificStableKey
        )
    }
}

/** User-approved conservative product seeds; these are not universal physiological optima. */
object RegionalColdStartDosePolicy {
    const val HYPERTROPHY_EQUIVALENT_SETS_PER_REGION_WEEK = 8.0
    /** Deterministic cold-start anchor inside the approved practical 8–12 rep band. */
    const val HYPERTROPHY_COLD_START_REPS = 8
    const val HYPERTROPHY_PRACTICAL_REPS_MIN = 8
    const val HYPERTROPHY_PRACTICAL_REPS_MAX = 12
    const val HYPERTROPHY_PERSONAL_REPS_MIN = 7
    const val HYPERTROPHY_PERSONAL_REPS_MAX = 15
    const val HYPERTROPHY_MINIMUM_TARGET_RPE = 7.0
    const val STRENGTH_DIRECT_SETS_PER_ANCHOR_WEEK = 4.0
    const val CORE_DIRECT_SETS_PER_WEEK = 6.0
    const val PROVENANCE = "USER_APPROVED_PROJECT_POLICY"
}

/** B4 attachment builder: semantic movement admission remains separate from regional dose authority. */
class RegionalMovementDoseTargetBuilder {
    fun build(
        movementTargets: List<StimulusMovementTarget>,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        catalog: CanonicalExercisePhysicalQualityCatalog,
        coreCatalog: com.training.trackplanner.analysis.core.CanonicalCoreCatalog
    ): Map<MovementCoverage, List<StimulusMovementDoseTarget>> {
        val regionalIndex = RegionalEvidenceIndexBuilder().build(snapshot, state, catalog)
        return movementTargets.associate { movement ->
            val regionalHypotrophy = if (movement.movementCoverage == MovementCoverage.CORE_DIRECT) null else {
                val hasDirect = catalog.relations(TrainableQuality.HYPERTROPHY).any { relation ->
                    relation.relationLevel == com.training.trackplanner.data.StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                        regionalRegionQualifierMatches(movement.movementCoverage, relation.regionQualifier)
                }
                if (!hasDirect) null else regionalIndex.regions[movement.movementCoverage]?.hypertrophyDoseBand?.let { band ->
                    val personalBaseline = band.hasPersonalBaseline
                    val personalTarget = band.weeklyUnitsMedian
                    val authority = when {
                        personalBaseline && personalTarget != null -> StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
                        personalBaseline -> StimulusTargetNumericAuthority.UNRESOLVED
                        else -> StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY
                    }
                    StimulusMovementDoseTarget(
                        kind = StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
                        numericAuthority = authority,
                        weeklyTarget = when (authority) {
                            StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE -> personalTarget
                            StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ->
                                RegionalColdStartDosePolicy.HYPERTROPHY_EQUIVALENT_SETS_PER_REGION_WEEK
                            else -> null
                        },
                        reasonCodes = listOf(when (authority) {
                            StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE -> "PERSONAL_REGIONAL_HYPERTROPHY_BASELINE"
                            StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY ->
                                "USER_APPROVED_PROJECT_POLICY_HYPERTROPHY_COLD_START_8_EQUIVALENT_SETS_PER_REGION_WEEK"
                            else -> "PERSONAL_DOSE_BASELINE_EXISTS_BUT_NUMERIC_TARGET_UNAVAILABLE"
                        }),
                        evidence = listOf(
                            "movementCoverage=${movement.movementCoverage.name}",
                            "personalBaseline=$personalBaseline",
                            "weeklyEquivalentExposureMedian=$personalTarget",
                            "doseProvenance=${if (authority == StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY) RegionalColdStartDosePolicy.PROVENANCE else authority.name}",
                            "shapePolicy=8-12 cold-start band; 7-15 validated personal reps; RPE>=7; load calibration when unknown"
                        ),
                        shapeAuthority = StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION
                    )
                }
            }
            val core = if (movement.movementCoverage == MovementCoverage.CORE_DIRECT) {
                val completeEnd = completedTrainingWeekEnd(snapshot.cutoff)
                val starts = (7 downTo 0).map { completeEnd.minusDays(it * 7L).minusDays(6) }
                val excluded = state.trainingStateAssessment?.weeklyContext.orEmpty()
                    .filter { it.excludedFromTolerance }.mapTo(hashSetOf()) { it.start }
                val completedTrainingWeeks = snapshot.allConfirmedSets.mapTo(hashSetOf()) {
                    it.date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                }
                val counts = starts.filter { it !in excluded && it in completedTrainingWeeks }
                    .associateWith { 0.0 }.toMutableMap()
                snapshot.allConfirmedSets.forEach { row ->
                    val weekStart = row.date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    if (weekStart !in counts) return@forEach
                    val profile = coreCatalog.resolve(row.stableKey) ?: return@forEach
                    if (profile.coreClass == com.training.trackplanner.analysis.core.CoreClass.DIRECT && profile.directTarget != null) {
                        counts[weekStart] = counts.getValue(weekStart) + 1.0
                    }
                }
                val weekly = counts.values.toList()
                val exposedWeeks = weekly.count { it > 0.0 }
                val personalBaseline = weekly.size >= 2 && exposedWeeks >= 2
                val personalWeeklyMedian = median(weekly).takeIf { personalBaseline }
                StimulusMovementDoseTarget(
                    kind = StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET,
                    numericAuthority = if (personalBaseline) StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
                        else StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                    weeklyTarget = personalWeeklyMedian ?: RegionalColdStartDosePolicy.CORE_DIRECT_SETS_PER_WEEK,
                    reasonCodes = if (personalBaseline) listOf("PERSONAL_CORE_DIRECT_COMPLETED_WEEK_BASELINE",
                        "CORE_PRESCRIPTION_SHAPE_AUTHORITY_UNAVAILABLE") else listOf(
                        "USER_APPROVED_PROJECT_POLICY_CORE_DIRECT_COLD_START_6_SETS_PER_WEEK",
                        "INSUFFICIENT_CORE_HISTORY_FOR_PERSONAL_TOLERANCE_BASELINE",
                        "CORE_PRESCRIPTION_SHAPE_AUTHORITY_UNAVAILABLE"),
                    evidence = listOf("eligibleWeeks=${weekly.size}", "directExposureWeeks=$exposedWeeks",
                        "observedWeeklySetMedian=${personalWeeklyMedian ?: median(weekly)}", "prescriptionShapeAuthority=NONE"),
                    shapeAuthority = StimulusMovementDoseShapeAuthority.NONE
                )
            } else null
            movement.movementCoverage to listOfNotNull(regionalHypotrophy, core)
        }
    }

    private fun median(values: List<Double>): Double? {
        val sorted = values.filter(Double::isFinite).sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}

/** Resolves the movement-attached B4 regional dose against incumbent and already-authorized B5/B6 work. */
class CanonicalRegionalMovementB4ResidualResolver(
    private val projector: RegionalStimulusCreditProjector = RegionalStimulusCreditProjector()
) {
    fun resolve(
        targetPlan: StimulusTargetPlan,
        existingProgram: GeneratedProgramSkeleton?,
        selectionPlan: StimulusCandidateSelectionPlan,
        authorizationPlan: StimulusPrescriptionAuthorizationPlan,
        snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): StimulusTargetPlan {
        val exposure = projector.projectWithAuthorizedQualityMaterial(
            existingProgram, selectionPlan, authorizationPlan, snapshot, catalog,
            targetRegions = targetPlan.movementTargets.mapTo(linkedSetOf()) { it.movementCoverage }
        )
        val movements = targetPlan.movementTargets.map { movement ->
            val doses = movement.regionalDoseTargets.map { dose ->
                if (dose.kind != StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET ||
                    dose.shapeAuthority != StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION ||
                    dose.weeklyTarget == null || dose.numericAuthority in setOf(
                        StimulusTargetNumericAuthority.NONE,
                        StimulusTargetNumericAuthority.DIRECTION_ONLY,
                        StimulusTargetNumericAuthority.UNRESOLVED
                    )) return@map dose
                val existing = exposure[movement.movementCoverage to TrainableQuality.HYPERTROPHY]?.weeklyEquivalentUnits ?: 0.0
                val residual = (dose.weeklyTarget - existing).coerceAtLeast(0.0)
                val wholeSets = kotlin.math.floor(residual + 1e-9).toInt()
                dose.copy(
                    existingEquivalentExposure = existing,
                    residualEquivalentExposure = residual,
                    authorizedWholeSetUnits = wholeSets,
                    residualReasonCodes = when {
                        wholeSets == 0 && residual > 0.0 -> listOf("B4_FRACTIONAL_RESIDUAL_CANNOT_AUTHORIZE_WHOLE_SET")
                        wholeSets == 0 -> listOf("B4_EXISTING_COMPATIBLE_EXPOSURE_COVERS_TARGET")
                        else -> listOf("B4_REGIONAL_RESIDUAL_AUTHORIZED", "B4_RESIDUAL_EQUIVALENT_UNITS=$residual",
                            "B4_AUTHORIZED_WHOLE_SET_UNITS=$wholeSets")
                    }
                )
            }
            movement.copy(regionalDoseTargets = doses)
        }
        return targetPlan.copy(movementTargets = movements)
    }
}

data class RegionalPlannedStimulus(
    val region: MovementCoverage,
    val quality: TrainableQuality,
    val weeklyUnits: Int,
    val exposureWeeks: Int,
    val exposureFrequency: Double,
    val weeklyEquivalentUnits: Double = weeklyUnits.toDouble()
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
                val relationCreditByQuality = linkedMapOf<TrainableQuality, Double>()
                catalog.relations(item.exerciseStableKey).filter { regionQualifierMatches(region, it.regionQualifier) }.forEach { relation ->
                    val credit = when (relation.relationLevel) {
                        com.training.trackplanner.data.StimulusCapabilityLevel.DIRECT_CAPABILITY -> 1.0
                        com.training.trackplanner.data.StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY ->
                            if (relation.qualityId == TrainableQuality.HYPERTROPHY) 0.5 else 0.0
                    }
                    if (credit <= 0.0) return@forEach
                    val compatible = if (relation.qualityId in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)) {
                        prescriptionShapeCompatible(relation.qualityId, item.exerciseStableKey, set.reps)
                    } else true
                    if (!compatible) return@forEach
                    relationCreditByQuality[relation.qualityId] = maxOf(relationCreditByQuality[relation.qualityId] ?: 0.0, credit)
                }
                relationCreditByQuality.forEach { (quality, credit) ->
                    val key = region to quality
                    val value = rows.getOrPut(key) { MutableRegionalPlannedStimulus(region, quality) }
                    value.units += credit
                    value.weeks += item.weekNumber
                }
            }
        }
        return rows.mapValues { (_, value) ->
            val weekly = value.units / weekCount
            RegionalPlannedStimulus(value.region, value.quality, weekly.roundToInt(), value.weeks.size,
                value.weeks.size.toDouble() / weekCount, weekly)
        }
    }

    /**
     * Projects CONTROL-compatible exposure after replacing rows that canonical B5/B6 has
     * already authorized in the ordinary generation. This prevents a movement residual from
     * being added on top of the same newly authorized physical work.
     */
    fun projectWithAuthorizedQualityMaterial(
        existingPlan: GeneratedProgramSkeleton?,
        selectionPlan: StimulusCandidateSelectionPlan,
        authorizationPlan: StimulusPrescriptionAuthorizationPlan,
        snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog,
        targetRegions: Set<MovementCoverage>? = null
    ): Map<Pair<MovementCoverage, TrainableQuality>, RegionalPlannedStimulus> {
        val selectedByOwner = selectionPlan.selectedCandidates.associateBy {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
        val authorized = authorizationPlan.authorizations.filter { authorization ->
            val owner = authorization.owner ?: return@filter false
            authorization.quality == TrainableQuality.HYPERTROPHY && authorization.authorizedPrescription != null &&
                authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                ) && StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) in selectedByOwner &&
                selectedByOwner.getValue(StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole))
                    .coveredTargetIds.contains("QUALITY:HYPERTROPHY")
        }
        val replaced = authorized.mapNotNullTo(linkedSetOf()) { authorization ->
            authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        }
        val retainedPlan = existingPlan?.copy(items = existingPlan.items.filterNot { item ->
            StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) in replaced
        })
        val combined = retainedPlan?.let { project(it, snapshot, catalog) }
            .orEmpty().mapValuesTo(linkedMapOf()) { it.value.weeklyEquivalentUnits }
        val weekCount = existingPlan?.weekPlans?.size?.coerceAtLeast(1) ?: 1
        authorized.forEach { authorization ->
            val owner = requireNotNull(authorization.owner)
            val prescription = requireNotNull(authorization.authorizedPrescription)
            prescription.sets.forEach { set ->
                val regions = targetRegions ?: setOf(snapshot.movementCoverage(owner.stableKey))
                regions.forEach { region ->
                    if (region == MovementCoverage.OTHER) return@forEach
                    val credit = catalog.relations(owner.stableKey)
                        .filter { regionalRegionQualifierMatches(region, it.regionQualifier) }
                        .filter { it.qualityId == TrainableQuality.HYPERTROPHY }
                        .maxOfOrNull { relation ->
                            when (relation.relationLevel) {
                                com.training.trackplanner.data.StimulusCapabilityLevel.DIRECT_CAPABILITY -> 1.0
                                com.training.trackplanner.data.StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY -> 0.5
                            }
                        }
                        ?: 0.0
                    if (credit <= 0.0 || !prescriptionShapeCompatible(TrainableQuality.HYPERTROPHY, owner.stableKey, set.reps)) return@forEach
                    val key = region to TrainableQuality.HYPERTROPHY
                    combined[key] = (combined[key] ?: 0.0) + credit
                }
            }
        }
        return combined.mapValues { (key, units) ->
            RegionalPlannedStimulus(key.first, key.second, units.roundToInt(),
                existingPlan?.weekPlans?.size?.coerceIn(0, weekCount) ?: weekCount,
                1.0, units)
        }
    }

    private class MutableRegionalPlannedStimulus(
        val region: MovementCoverage,
        val quality: TrainableQuality
    ) {
        var units = 0.0
        val weeks = linkedSetOf<Int>()
    }

    private fun regionQualifierMatches(movement: MovementCoverage, qualifier: com.training.trackplanner.data.PhysicalQualityRegion): Boolean = when (movement) {
        MovementCoverage.LOWER_KNEE -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.QUADS_GLUTE, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.POSTERIOR_CHAIN -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.POSTERIOR_CHAIN, com.training.trackplanner.data.PhysicalQualityRegion.HAMSTRING, com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
        MovementCoverage.CALVES -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.ANKLE, com.training.trackplanner.data.PhysicalQualityRegion.LOWER)
        MovementCoverage.HORIZONTAL_PUSH -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH, com.training.trackplanner.data.PhysicalQualityRegion.CHEST, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.VERTICAL_PUSH -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL, MovementCoverage.UPPER_PULL -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.ARMS_BICEPS, MovementCoverage.ARMS_TRICEPS -> qualifier == com.training.trackplanner.data.PhysicalQualityRegion.ARMS
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
    val targetCompatibleMaterializedUnits: Int = 0,
    val shortfall: Int = 0,
    val finalReasonCodes: List<String> = emptyList(),
    val selectedIdentity: RegionalSelectionIdentity? = null,
    val overrunUnits: Int = 0,
    val ordinarySameKeyCompatibleUnits: Int = 0
)

/** Typed identity for an experimental regional owner. Display strings are not authority. */
data class RegionalOwnershipKey(
    val region: MovementCoverage,
    val quality: TrainableQuality
)

data class RegionalSelectionIdentity(
    val stableKey: String,
    val selectionRole: String
)

/** The side table keeps a selected exercise linked to its target without changing PlannedExercise. */
data class RegionalExperimentalTargetPlan(
    val demand: MaterialDemand,
    val targetByStableKey: Map<String, RegionalStimulusTarget>,
    val ownedKeys: Set<RegionalOwnershipKey>,
    val targetBySelectionRole: Map<String, RegionalStimulusTarget> = emptyMap(),
    val authorizedPrescriptionBySelectionRole: Map<RegionalSelectionIdentity, PlannedPrescription> = emptyMap()
) {
    /** Exact composite identity; stableKey alone is not sufficient when one exercise has multiple roles. */
    fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int = item.targetSets): PlannedPrescription? {
        val base = authorizedPrescriptionBySelectionRole[RegionalSelectionIdentity(item.stableKey, item.role)] ?: return null
        require(requestedSets in 0..base.sets.size) { "REGIONAL_AUTHORIZATION_OVERRUN: requested=$requestedSets authorized=${base.sets.size}" }
        return if (requestedSets == base.sets.size) base else base.copy(sets = base.sets.take(requestedSets))
    }
}

/** Adapts the existing regional exact-prescription authority to the generic mechanical seam. */
internal fun RegionalExperimentalTargetPlan.asExactPrescriptionAuthorizationProvider(): ExactPrescriptionAuthorizationProvider {
    val plan = this
    return object : ExactPrescriptionAuthorizationProvider {
        override val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> =
            plan.authorizedPrescriptionBySelectionRole.mapKeys { (owner, _) ->
                StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
            }

        override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
            plan.authorizedPrescriptionFor(item, requestedSets)
    }
}

/** Candidate selection is typed by MovementCoverage and TrainableQuality. */
data class RegionalB4ResidualDoseAuthority(
    val target: RegionalStimulusTarget,
    val existingCredit: RegionalPlannedStimulus?,
    val existingEquivalentUnits: Double,
    val targetEquivalentUnits: Double?,
    val residualEquivalentUnits: Double,
    val authorizedWholeSetUnits: Int,
    val reasonCodes: List<String>
)

/** B4 computes the remaining regional dose before B5 searches for an owner. */
class RegionalB4ResidualDoseAuthorityResolver(
    private val projector: RegionalStimulusCreditProjector = RegionalStimulusCreditProjector()
) {
    fun resolve(
        target: RegionalStimulusTarget,
        snapshot: PlanningHistorySnapshot,
        existingPlan: GeneratedProgramSkeleton?,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): RegionalB4ResidualDoseAuthority {
        val canAdd = target.action in setOf(RegionalTargetAction.ADD_SUPPORT, RegionalTargetAction.RESTORE) &&
            target.numericAuthority !in setOf(RegionalNumericAuthority.NONE, RegionalNumericAuthority.DIRECTION_ONLY) &&
            target.weeklyDoseTarget != null
        val credit = if (canAdd && existingPlan != null) projector.project(existingPlan, snapshot, catalog)
            .get(target.region to target.quality) else null
        val existing = credit?.weeklyEquivalentUnits ?: 0.0
        val numericTarget = target.weeklyDoseTarget?.takeIf { canAdd }
        val residual = ((numericTarget ?: 0.0) - existing).coerceAtLeast(0.0)
        val wholeSets = kotlin.math.floor(residual + 1e-9).toInt()
        val reasons = when {
            !canAdd -> listOf("B4_NO_NUMERIC_RESIDUAL_AUTHORITY")
            wholeSets == 0 && residual > 0.0 -> listOf("B4_FRACTIONAL_RESIDUAL_CANNOT_AUTHORIZE_WHOLE_SET")
            wholeSets == 0 -> listOf("B4_EXISTING_EXPOSURE_COVERS_TARGET")
            else -> listOf("B4_RESIDUAL_DOSE_AUTHORIZED", "B4_EXISTING_EQUIVALENT_UNITS=$existing",
                "B4_TARGET_EQUIVALENT_UNITS=$numericTarget", "B4_RESIDUAL_EQUIVALENT_UNITS=$residual",
                "B4_AUTHORIZED_WHOLE_SET_UNITS=$wholeSets")
        }
        return RegionalB4ResidualDoseAuthority(
            target = target,
            existingCredit = credit,
            existingEquivalentUnits = existing,
            targetEquivalentUnits = numericTarget,
            residualEquivalentUnits = residual,
            authorizedWholeSetUnits = wholeSets,
            reasonCodes = reasons
        )
    }
}

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
        request: ProgramSkeletonRequest,
        existingPlan: GeneratedProgramSkeleton? = null,
        usedStableKeys: Set<String> = emptySet(),
        catalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY
    ): Selection {
        val b4 = RegionalB4ResidualDoseAuthorityResolver().resolve(target, snapshot, existingPlan, catalog)
        return selectB5(b4, snapshot, state, request, existingPlan, usedStableKeys, catalog)
    }

    /** B5 consumes the immutable B4 residual; it cannot recalculate or enlarge the dose. */
    fun selectB5(
        b4DoseAuthority: RegionalB4ResidualDoseAuthority,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest?,
        existingPlan: GeneratedProgramSkeleton? = null,
        usedStableKeys: Set<String> = emptySet(),
        catalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY
    ): Selection = selectInternal(b4DoseAuthority, snapshot, state, request, existingPlan, usedStableKeys, catalog)

    /** Compatibility overload for focused unit tests; production experimental calls pass the real request. */
    fun select(
        target: RegionalStimulusTarget,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        existingPlan: GeneratedProgramSkeleton? = null,
        usedStableKeys: Set<String> = emptySet(),
        catalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY
    ): Selection {
        val b4 = RegionalB4ResidualDoseAuthorityResolver().resolve(target, snapshot, existingPlan, catalog)
        return selectB5(b4, snapshot, state, null, existingPlan, usedStableKeys, catalog)
    }

    private fun selectInternal(
        b4DoseAuthority: RegionalB4ResidualDoseAuthority,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest?,
        existingPlan: GeneratedProgramSkeleton?,
        usedStableKeys: Set<String>,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): Selection {
        val target = b4DoseAuthority.target
        if (target.action !in setOf(RegionalTargetAction.ADD_SUPPORT, RegionalTargetAction.RESTORE) ||
            target.numericAuthority == RegionalNumericAuthority.DIRECTION_ONLY ||
            target.numericAuthority == RegionalNumericAuthority.NONE || target.weeklyDoseTarget == null
        ) return Selection(target, null, emptyList(), null, 0, listOf("NO_NUMERIC_TARGET_OR_NO_ADD_AUTHORITY"))

        val credit = b4DoseAuthority.existingCredit
        val alreadyPlanned = b4DoseAuthority.existingEquivalentUnits
        val residualEquivalent = b4DoseAuthority.residualEquivalentUnits
        val residual = b4DoseAuthority.authorizedWholeSetUnits
        if (residual == 0) return Selection(target, credit, emptyList(), null, 0, listOf(
            if (residualEquivalent > 0.0) "FRACTIONAL_RESIDUAL_CANNOT_AUTHORIZE_A_WHOLE_EXECUTABLE_SET"
            else "EXISTING_PLAN_CREDIT_COVERS_TARGET"
        ))

        val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
        val pool = snapshot.exercises.keys.asSequence()
            .filter { key -> target.specificStableKey == null || key == target.specificStableKey }
            .filter { key -> key !in usedStableKeys }
            .filter { key -> snapshot.movementCoverage(key) == target.region }
            .filter { key -> request?.excludedExerciseStableKeys?.contains(key) != true }
            .filter { key -> snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") }
            .filterNot(snapshot::explicitlyRestricted)
            .filter { key -> key !in snapshot.recoverySignals.tissueRestrictedStableKeys }
            .filter { key -> exactCapability(catalog, key, target) }
            .filter { key -> equipmentCompatible(snapshot, key, state, request) }
            .filter { key -> prescriptionCompatible(snapshot, key, target.quality) }
            .sortedWith(
                compareByDescending<String> { it in historyKeys }
                    .thenByDescending { qualityCompatibleHistory(snapshot, it, target.quality) }
                    .thenByDescending { state.freeWeightWillingness != FreeWeightWillingness.PREFER_FAMILIAR || !snapshot.isFreeWeight(it) }
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
                representedGapCodes = setOf("REGIONAL_TARGET_${target.region.name}_${target.quality.name}")
            )
        }
        return Selection(target, credit, pool.map { key ->
            PlannedExercise(key, "REGIONAL_CANDIDATE", "Typed regional candidate", 0, targetSets = residual, material = true)
        }, chosen, residual, b4DoseAuthority.reasonCodes + listOfNotNull(
            "EXISTING_PLANNED_EQUIVALENT_UNITS=$alreadyPlanned",
            "RESIDUAL_EQUIVALENT_UNITS=$residualEquivalent",
            "AUTHORIZED_WHOLE_SET_UNITS=$residual",
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
            TrainableQuality.STRENGTH -> rows.any { snapshot.historyRealizedKind(it) == RealizedStimulusKind.STRENGTH_LIKE && it.weightKg > 0.0 } ||
                snapshot.canonicalStrengthSignals[key]?.observationCount?.let { it >= 2 } == true
            TrainableQuality.HYPERTROPHY -> true
            else -> false
        }
    }

    private fun qualityCompatibleHistory(snapshot: PlanningHistorySnapshot, key: String, quality: TrainableQuality): Boolean =
        snapshot.allConfirmedSets.any { it.stableKey == key && when (quality) {
            TrainableQuality.STRENGTH -> snapshot.historyRealizedKind(it) == RealizedStimulusKind.STRENGTH_LIKE
            TrainableQuality.HYPERTROPHY -> snapshot.historyRealizedKind(it) == RealizedStimulusKind.HYPERTROPHY_LIKE
            else -> false
        } }

    private fun equipmentCompatible(snapshot: PlanningHistorySnapshot, key: String, state: AthletePlanningState, request: ProgramSkeletonRequest?): Boolean {
        val exercise = snapshot.exercises[key] ?: return false
        val equipment = exercise.equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
        if (request != null && request.availableEquipment.isNotEmpty() && equipment.any { it != "BODYWEIGHT" && it !in request.availableEquipment }) return false
        if (state.freeWeightWillingness in setOf(FreeWeightWillingness.AVOID, FreeWeightWillingness.UNRESOLVED) && snapshot.isFreeWeight(key) &&
            snapshot.allConfirmedSets.none { it.stableKey == key }) return false
        return true
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
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL, MovementCoverage.UPPER_PULL -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
        MovementCoverage.ARMS_BICEPS, MovementCoverage.ARMS_TRICEPS -> qualifier == com.training.trackplanner.data.PhysicalQualityRegion.ARMS
        else -> false
    }
}

data class RegionalExperimentalMaterialDemand(
    val demand: MaterialDemand,
    val traces: List<RegionalAuthorityTrace>,
    val counters: RegionalAuthorityCounters,
    val targetPlan: RegionalExperimentalTargetPlan
)

class RegionalExperimentalMaterialDemandBuilder(
    private val requirementResolver: RegionalTrainingDecisionResolver = RegionalTrainingDecisionResolver(),
    private val targetResolver: RegionalStimulusTargetResolver = RegionalStimulusTargetResolver(),
    private val residualDoseResolver: RegionalB4ResidualDoseAuthorityResolver = RegionalB4ResidualDoseAuthorityResolver(),
    private val selector: RegionalTargetCandidateSelector = RegionalTargetCandidateSelector(),
    private val prescriptionResolver: RegionalTargetPrescriptionResolver = RegionalTargetPrescriptionResolver()
) {
    fun build(
        diagnoses: List<RegionalBottleneckDiagnosis>,
        control: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): RegionalExperimentalMaterialDemand {
        val candidates = mutableListOf<PlannedExercise>()
        val traces = mutableListOf<RegionalAuthorityTrace>()
        val targetByStableKey = linkedMapOf<String, RegionalStimulusTarget>()
        val targetBySelectionRole = linkedMapOf<String, RegionalStimulusTarget>()
        val authorizedPrescriptionBySelectionRole = linkedMapOf<RegionalSelectionIdentity, PlannedPrescription>()
        val ownedKeys = linkedSetOf<RegionalOwnershipKey>()
        var candidateCount = 0
        var prescriptionResolutions = 0
        diagnoses.sortedBy { it.region.ordinal }.forEach { diagnosis ->
            val decision = requirementResolver.resolve(diagnosis)
            val target = targetResolver.resolve(diagnosis)
            if (target.action in setOf(RegionalTargetAction.ADD_SUPPORT, RegionalTargetAction.RESTORE)) {
                ownedKeys += RegionalOwnershipKey(target.region, target.quality)
            }
            val b4Residual = residualDoseResolver.resolve(target, snapshot, control, catalog)
            val selection = selector.selectB5(
                b4Residual, snapshot, state, request, control,
                candidates.map(PlannedExercise::stableKey).toSet(), catalog
            )
            candidateCount += selection.candidates.size
            val resolution = selection.selected?.let {
                prescriptionResolutions++
                prescriptionResolver.resolve(b4Residual, it, snapshot)
            }
            val authorized = resolution?.prescription
            val selected = selection.selected?.takeIf { authorized != null }
            selected?.let {
                candidates += it
                targetByStableKey[it.stableKey] = target
                targetBySelectionRole["${it.stableKey}|${it.role}"] = target
                authorizedPrescriptionBySelectionRole[RegionalSelectionIdentity(it.stableKey, it.role)] = authorized!!
            }
            val credit = selection.credit
            val resolutionReasons = resolution?.reasonCodes.orEmpty()
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
                selectedStableKey = selected?.stableKey,
                selectedIdentity = selected?.let { RegionalSelectionIdentity(it.stableKey, it.role) },
                selectionReasons = selection.reasons + resolutionReasons,
                prescriptionCompatibility = when {
                    selected != null -> "TARGET_COMPATIBLE_PRESCRIPTION_AUTHORITY"
                    resolution != null -> "NO_SAFE_COMPATIBLE_PRESCRIPTION"
                    else -> "NO_SAFE_AUTHORITY"
                },
                requestedUnits = target.weeklyDoseTarget?.roundToInt() ?: 0,
                authorizedUnits = selected?.targetSets ?: 0,
                finalReasonCodes = target.reasonCodes + selection.reasons + resolutionReasons
            )
        }
        val deferred = traces.filter { it.residualDose > 0 && it.selectedStableKey == null }
            .associate { trace ->
                "REGIONAL_TARGET_${trace.region.name}_${trace.targetQuality.name}" to
                    if ("TARGET_PRESENT_BUT_NO_SAFE_COMPATIBLE_PRESCRIPTION" in trace.finalReasonCodes)
                        "TARGET_PRESENT_BUT_NO_SAFE_COMPATIBLE_PRESCRIPTION"
                    else "NO_ELIGIBLE_CANDIDATE_OR_CAPACITY"
            }
        val materialDemand = MaterialDemand(
            candidates = candidates,
            deferred = deferred,
            audit = candidates.associate { it.stableKey to "SELECTED_REGIONAL_TARGET_CANDIDATE" }
        )
        return RegionalExperimentalMaterialDemand(
            demand = materialDemand,
            traces = traces,
            counters = RegionalAuthorityCounters(
                historyRowsIndexed = snapshot.allConfirmedSets.size,
                regionalDiagnosesProduced = diagnoses.size,
                regionalTargetsProduced = traces.size,
                candidatePoolsEvaluated = diagnoses.size,
                candidateCountEvaluated = candidateCount,
                prescriptionResolutions = prescriptionResolutions
            ),
            targetPlan = RegionalExperimentalTargetPlan(
                demand = materialDemand,
                targetByStableKey = targetByStableKey,
                ownedKeys = ownedKeys,
                targetBySelectionRole = targetBySelectionRole,
                authorizedPrescriptionBySelectionRole = authorizedPrescriptionBySelectionRole
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
        val a = control.items.map { listOf(it.weekNumber, it.dayOfWeek, it.exerciseStableKey, it.setPrescriptions.joinToString { set -> "${set.reps}:${set.weightKg}:${set.seconds}:${set.targetRpeMin?.canonicalTargetRpeFingerprint().orEmpty()}" }) }
        val b = experimental.items.map { listOf(it.weekNumber, it.dayOfWeek, it.exerciseStableKey, it.setPrescriptions.joinToString { set -> "${set.reps}:${set.weightKg}:${set.seconds}:${set.targetRpeMin?.canonicalTargetRpeFingerprint().orEmpty()}" }) }
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

/**
 * Experimental target-to-prescription seam. The production planner remains the source of
 * canonical history/load semantics; this wrapper only accepts a prescription when its actual
 * rep shape realizes the requested quality.
 */
class RegionalTargetPrescriptionResolver(
    private val canonical: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner()
) {
    data class Resolution(
        val prescription: PlannedPrescription?,
        val reasonCodes: List<String>,
        val authoritySource: RegionalPrescriptionAuthoritySource = RegionalPrescriptionAuthoritySource.NONE
    )

    fun resolve(
        b4DoseAuthority: RegionalB4ResidualDoseAuthority,
        item: PlannedExercise,
        snapshot: PlanningHistorySnapshot
    ): Resolution {
        val target = b4DoseAuthority.target
        if (target.action !in setOf(RegionalTargetAction.ADD_SUPPORT, RegionalTargetAction.RESTORE) ||
            target.numericAuthority in setOf(RegionalNumericAuthority.NONE, RegionalNumericAuthority.DIRECTION_ONLY) ||
            target.weeklyDoseTarget == null
        ) return Resolution(null, listOf("NO_NUMERIC_TARGET_OR_NO_ADD_AUTHORITY"))

        // B4 owns dose units. B6 consumes the exact authority object rather than inferring a
        // count from an exercise default or the canonical probe prescription.
        val requestedSets = b4DoseAuthority.authorizedWholeSetUnits.takeIf { it > 0 }
            ?: return Resolution(null, listOf("NO_B4_RESIDUAL_SET_AUTHORITY"))
        if (item.targetSets != requestedSets) {
            return Resolution(null, listOf("B5_OWNER_SET_COUNT_DOES_NOT_MATCH_B4_RESIDUAL"))
        }
        val canonicalPrescription = canonical.prescribe(snapshot, snapshot.preferences.strengthIntent ?: StrengthIntent.MIXED, item, item.style)
        return when (target.quality) {
            TrainableQuality.HYPERTROPHY -> resolveHypertrophy(canonicalPrescription, snapshot, item, requestedSets)
            TrainableQuality.STRENGTH -> resolveStrength(canonicalPrescription, snapshot, item, requestedSets)
            else -> Resolution(null, listOf("UNSUPPORTED_TARGET_QUALITY"))
        }
    }

    private fun resolveHypertrophy(
        canonicalPrescription: PlannedPrescription,
        snapshot: PlanningHistorySnapshot,
        item: PlannedExercise,
        requestedSets: Int
    ): Resolution {
        val personalHistoryPrescription = targetCompatiblePersonalHistoryPrescription(
            TrainableQuality.HYPERTROPHY, item, snapshot, requestedSets, canonicalPrescription.restSeconds,
            requireReviewedAuthority = false, weightSourceOverride = "TARGET_COMPATIBLE_PERSONAL_HISTORY"
        )
        if (personalHistoryPrescription != null) {
            return Resolution(
                personalHistoryPrescription,
                listOf("HYPERTROPHY_PERSONAL_HISTORY_7_15"),
                RegionalPrescriptionAuthoritySource.PERSONAL_SUCCESSFUL_HISTORY
            )
        }
        // With no eligible exact-exercise history, canonical planner output is not a load
        // authority for this new owner. B4's residual owns the count; the approved cold-start
        // shape supplies only the deterministic 8-rep anchor and effort floor. The user
        // calibrates load on first performance, even if a generic planner probe has a weight.
        return Resolution(
            PlannedPrescription(
                text = "Regional hypertrophy cold-start; user load calibration required",
                sets = List(requestedSets) { index -> ProgramSetPrescription(
                    index + 1, RegionalColdStartDosePolicy.HYPERTROPHY_COLD_START_REPS, 0.0, 0,
                    targetRpeMin = RegionalColdStartDosePolicy.HYPERTROPHY_MINIMUM_TARGET_RPE,
                    loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                ) },
                restSeconds = canonicalPrescription.restSeconds,
                weightSource = RegionalColdStartDosePolicy.PROVENANCE
            ),
            listOf("USER_APPROVED_COLD_START_HYPERTROPHY_8_REPS_WITHIN_8_12_BAND_LOAD_CALIBRATION_REQUIRED"),
            RegionalPrescriptionAuthoritySource.USER_APPROVED_COLD_START_CALIBRATION
        )
    }

    private fun resolveStrength(
        canonicalPrescription: PlannedPrescription,
        snapshot: PlanningHistorySnapshot,
        item: PlannedExercise,
        requestedSets: Int
    ): Resolution {
        val personalHistoryPrescription = targetCompatiblePersonalHistoryPrescription(
            TrainableQuality.STRENGTH, item, snapshot, requestedSets, canonicalPrescription.restSeconds,
            requireReviewedAuthority = false
        )
        if (personalHistoryPrescription != null) {
            return Resolution(
                personalHistoryPrescription,
                listOf("STRENGTH_PERSONAL_HISTORY_1_6")
            )
        }
        val canonicalStrengthAuthority = snapshot.canonicalStrengthSignals[item.stableKey]?.observationCount?.let { it >= 2 } == true
        if (canonicalStrengthAuthority && canonicalPrescription.sets.isNotEmpty() &&
            CanonicalStrengthExposureCapability.strengthPossible(item.stableKey) &&
            canonicalPrescription.sets.all {
                plannedTargetSetStimulusClass(item.stableKey, it.reps, item.role, TrainableQuality.STRENGTH) ==
                    RealizedStimulusClass.STRENGTH_LIKE
            } &&
            canonicalPrescription.sets.any { it.weightKg > 0.0 }
        ) {
            return Resolution(
                canonicalPrescription.copy(
                    sets = List(requestedSets) { index -> canonicalPrescription.sets[index % canonicalPrescription.sets.size].copy(setIndex = index + 1) },
                    weightSource = "TARGET_COMPATIBLE_CANONICAL_STRENGTH_AUTHORITY"
                ),
                listOf("STRENGTH_CANONICAL_AUTHORITY_1_6")
            )
        }
        return Resolution(null, listOf("TARGET_PRESENT_BUT_NO_SAFE_COMPATIBLE_PRESCRIPTION"))
    }
}

/** B6 consumes the exact B4 regional residual for a canonical B5-selected owner. */
class CanonicalRegionalMovementB6AuthorizationEngine(
    private val prescriptionResolver: RegionalTargetPrescriptionResolver = RegionalTargetPrescriptionResolver()
) {
    fun authorize(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        baseAuthorizationPlan: StimulusPrescriptionAuthorizationPlan,
        snapshot: PlanningHistorySnapshot
    ): StimulusPrescriptionAuthorizationPlan {
        val regionalAuthorizations = targetPlan.movementTargets.mapNotNull { movement ->
            val dose = movement.regionalDoseTargets.firstOrNull {
                it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                    it.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION
            } ?: return@mapNotNull null
            val units = dose.authorizedWholeSetUnits ?: return@mapNotNull null
            if (units <= 0) return@mapNotNull null
            val candidate = selectionPlan.selectedCandidates.firstOrNull { movement.targetId in it.coveredTargetIds }
            val owner = candidate?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            val item = owner?.let { selected -> selectionPlan.materialDemand.candidates.singleOrNull {
                it.stableKey == selected.stableKey && it.role == selected.selectionRole && it.targetSets == units
            } }
            val regionalTarget = RegionalStimulusTarget(
                region = movement.movementCoverage,
                quality = TrainableQuality.HYPERTROPHY,
                action = if (dose.numericAuthority == StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE)
                    RegionalTargetAction.RESTORE else RegionalTargetAction.ADD_SUPPORT,
                numericAuthority = when (dose.numericAuthority) {
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE -> RegionalNumericAuthority.PRIOR_TOLERATED_HYPERTROPHY
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE -> RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND
                    StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY -> RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY
                    else -> RegionalNumericAuthority.NONE
                },
                weeklyDoseTarget = dose.weeklyTarget,
                priority = when (movement.priority) {
                    TargetPriority.PRIMARY -> NeedRelevance.HIGH
                    TargetPriority.SECONDARY -> NeedRelevance.MODERATE
                    TargetPriority.MAINTENANCE -> NeedRelevance.MODERATE
                    TargetPriority.BACKGROUND -> NeedRelevance.LOW
                    TargetPriority.NONE, TargetPriority.UNRESOLVED -> NeedRelevance.UNKNOWN
                },
                reasonCodes = movement.reasonCodes + dose.reasonCodes + dose.residualReasonCodes
            )
            val b4 = RegionalB4ResidualDoseAuthority(
                target = regionalTarget,
                existingCredit = dose.existingEquivalentExposure?.let { existing ->
                    RegionalPlannedStimulus(movement.movementCoverage, TrainableQuality.HYPERTROPHY,
                        existing.roundToInt(), 0, 0.0, existing)
                },
                existingEquivalentUnits = dose.existingEquivalentExposure ?: 0.0,
                targetEquivalentUnits = dose.weeklyTarget,
                residualEquivalentUnits = dose.residualEquivalentExposure ?: 0.0,
                authorizedWholeSetUnits = units,
                reasonCodes = dose.residualReasonCodes
            )
            val resolution = if (owner == null || candidate == null || item == null) {
                RegionalTargetPrescriptionResolver.Resolution(null, listOf("B6_REQUIRES_EXACT_B5_REGIONAL_OWNER"))
            } else prescriptionResolver.resolve(b4, item, snapshot)
            val authorized = resolution.prescription
            val isCalibration = authorized?.sets?.isNotEmpty() == true && authorized.sets.all {
                it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && it.weightKg == 0.0
            }
            val status = when {
                authorized == null -> StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION
                isCalibration -> StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                else -> StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
            }
            StimulusPrescriptionAuthorization(
                targetId = movement.targetId,
                quality = TrainableQuality.HYPERTROPHY,
                owner = owner?.let { StimulusPrescriptionOwner(it.stableKey, it.selectionRole) },
                source = if (owner == null) null else StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = authorized,
                plannedCompatibility = null,
                authorizedPrescription = authorized,
                status = status,
                reasonCodes = resolution.reasonCodes,
                executionAuthority = canonicalExecutionAuthority(TrainableQuality.HYPERTROPHY, authorized),
                authorityRecovery = when {
                    authorized == null -> ExecutionAuthorityResolution(
                        ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                        ExecutionAuthorityResolutionReason.NO_EXECUTABLE_AUTHORIZATION,
                        ExecutionAuthorityReturnTarget.NONE, owner
                    )
                    isCalibration -> ExecutionAuthorityResolution(
                        ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED,
                        ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
                        ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT, owner,
                        owner?.let(::listOf).orEmpty(), owner
                    )
                    else -> ExecutionAuthorityResolution(
                        ExecutionAuthorityResolutionStatus.READY,
                        ExecutionAuthorityResolutionReason.EXACT_AUTHORITY_AVAILABLE,
                        ExecutionAuthorityReturnTarget.NONE, owner, owner?.let(::listOf).orEmpty(), owner
                    )
                }
            )
        }
        val regionalByTarget = regionalAuthorizations.associateBy(StimulusPrescriptionAuthorization::targetId)
        val movementStatuses = baseAuthorizationPlan.movementAuthorizations.map { existing ->
            val regional = regionalByTarget[existing.targetId]
            when {
                regional?.authorizedPrescription != null -> existing.copy(
                    owner = regional.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) },
                    status = StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6,
                    reasonCodes = regional.reasonCodes + "B6_CONSUMED_EXACT_B4_REGIONAL_RESIDUAL"
                )
                else -> existing
            }
        }
        return baseAuthorizationPlan.copy(
            authorizations = baseAuthorizationPlan.authorizations + regionalAuthorizations,
            movementAuthorizations = movementStatuses
        )
    }
}

enum class RegionalPrescriptionAuthoritySource {
    NONE,
    PERSONAL_SUCCESSFUL_HISTORY,
    CANONICAL_HYPERTROPHY_AUTHORITY,
    USER_APPROVED_COLD_START_CALIBRATION,
    CANONICAL_STRENGTH_AUTHORITY
}

/** Removes only legacy demand that would fund an owned region × quality with the same actual set shape. */
object RegionalMaterialDemandOwnershipFilter {
    fun filter(
        base: MaterialDemand,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        ownedKeys: Set<RegionalOwnershipKey>,
        planner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner(),
        catalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY
    ): MaterialDemand {
        if (ownedKeys.isEmpty()) return base
        val anchors = state.anchors.mapTo(mutableSetOf(), UserAnchor::stableKey)
        val kept = base.candidates.filter { candidate ->
            if (candidate.stableKey in anchors) return@filter true
            val prescription = planner.prescribe(snapshot, state.strengthIntent, candidate, candidate.style)
            val actualQualities = prescription.sets.mapNotNull { set ->
                when {
                    plannedTargetSetStimulusClass(candidate.stableKey, set.reps, candidate.role, TrainableQuality.STRENGTH) ==
                        RealizedStimulusClass.STRENGTH_LIKE -> TrainableQuality.STRENGTH
                    plannedTargetSetStimulusClass(candidate.stableKey, set.reps, candidate.role, TrainableQuality.HYPERTROPHY) ==
                        RealizedStimulusClass.HYPERTROPHY_LIKE -> TrainableQuality.HYPERTROPHY
                    else -> null
                }
            }.toSet()
            ownedKeys.none { owned ->
                owned.quality in actualQualities && catalog.relations(candidate.stableKey).any { relation ->
                    relation.qualityId == owned.quality &&
                        relation.relationLevel == com.training.trackplanner.data.StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                        regionalRegionQualifierMatches(owned.region, relation.regionQualifier)
                }
            }
        }
        val removed = base.candidates.map(PlannedExercise::stableKey).toSet() - kept.map(PlannedExercise::stableKey).toSet()
        return base.copy(
            candidates = kept,
            audit = base.audit + removed.associateWith { "SUPPRESSED_BY_TYPED_REGIONAL_OWNERSHIP" }
        )
    }
}

/** Final, post-reflow materialization audit over actual set prescriptions. */
data class FinalRegionalStimulusProjection(
    val requestedUnits: Int,
    val creditedUnits: Int,
    val residualUnits: Int,
    val authorizedUnits: Int,
    val targetCompatibleMaterializedUnits: Int,
    val shortfall: Int,
    val reasonCode: String,
    val overrunUnits: Int = 0,
    val ordinarySameKeyCompatibleUnits: Int = 0
)

/** Canonical parent provenance wins over a row's display role after splitting/reflow. */
internal fun regionalSelectionIdentity(plan: GeneratedProgramSkeleton, row: ProgramSkeletonItem): RegionalSelectionIdentity? {
    val trace = plan.personalizedDecision?.authorizedScheduling
    val origin = trace?.localOrigins?.get(row.localId)
    if (origin != null) {
        val parent = trace.authorized.singleOrNull { it.id == origin.authorizedDemandId } ?: return null
        return RegionalSelectionIdentity(parent.item.stableKey, parent.item.role)
    }
    return RegionalSelectionIdentity(row.exerciseStableKey, row.selectionRole)
}

class FinalRegionalStimulusProjector {
    fun project(
        target: RegionalStimulusTarget,
        finalPlan: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog,
        selectedIdentity: RegionalSelectionIdentity?,
        creditedUnits: Int,
        residualUnits: Int,
        authorizedUnits: Int
    ): FinalRegionalStimulusProjection {
        val sameKeyRows = finalPlan.items
            .filter { it.weekNumber == 1 && it.exerciseStableKey == selectedIdentity?.stableKey }
            .filter { item ->
                snapshot.movementCoverage(item.exerciseStableKey) == target.region &&
                    catalog.relations(item.exerciseStableKey).any { relation ->
                        relation.qualityId == target.quality &&
                            relation.relationLevel == com.training.trackplanner.data.StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                            regionalRegionQualifierMatches(target.region, relation.regionQualifier)
                    }
            }
        fun compatibleUnits(items: List<ProgramSkeletonItem>) = items.sumOf { item -> item.setPrescriptions.count { set ->
                when (target.quality) {
                    TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY ->
                        plannedTargetSetStimulusClass(item.exerciseStableKey, set.reps, item.selectionRole, target.quality) ==
                            if (target.quality == TrainableQuality.STRENGTH) RealizedStimulusClass.STRENGTH_LIKE
                            else RealizedStimulusClass.HYPERTROPHY_LIKE
                    else -> false
                }
            } }
        val compatible = compatibleUnits(sameKeyRows.filter { regionalSelectionIdentity(finalPlan, it) == selectedIdentity })
        val ordinary = compatibleUnits(sameKeyRows.filter { regionalSelectionIdentity(finalPlan, it) != selectedIdentity })
        val overrun = (compatible - authorizedUnits).coerceAtLeast(0)
        val shortfall = (residualUnits - compatible).coerceAtLeast(0)
        val reason = when {
            overrun > 0 -> "REGIONAL_AUTHORIZATION_OVERRUN"
            target.action !in setOf(RegionalTargetAction.ADD_SUPPORT, RegionalTargetAction.RESTORE) || target.weeklyDoseTarget == null -> "NO_NUMERIC_TARGET_OR_NO_ADD_AUTHORITY"
            selectedIdentity != null && compatible >= residualUnits -> "TARGET_FULLY_MATERIALIZED"
            selectedIdentity != null && compatible > 0 -> "TARGET_PARTIALLY_MATERIALIZED"
            selectedIdentity != null -> "TARGET_NOT_MATERIALIZED"
            residualUnits > 0 -> "TARGET_NOT_MATERIALIZED"
            else -> "EXISTING_PLAN_CREDIT_COVERS_TARGET"
        }
        return FinalRegionalStimulusProjection(
            requestedUnits = target.weeklyDoseTarget?.roundToInt() ?: 0,
            creditedUnits = creditedUnits,
            residualUnits = residualUnits,
            authorizedUnits = authorizedUnits,
            targetCompatibleMaterializedUnits = compatible,
            shortfall = shortfall,
            reasonCode = reason,
            overrunUnits = overrun,
            ordinarySameKeyCompatibleUnits = ordinary
        )
    }
}

/** Post-reflow hook is intentionally observation-only; target prescriptions are immutable before feasibility. */
class RegionalTargetAwareFinalizer {
    fun apply(
        plan: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        targetByStableKey: Map<String, RegionalStimulusTarget>,
        targetBySelectionRole: Map<String, RegionalStimulusTarget> = emptyMap()
    ): GeneratedProgramSkeleton {
        return plan
    }
}

internal fun regionalRegionQualifierMatches(
    movement: MovementCoverage,
    qualifier: com.training.trackplanner.data.PhysicalQualityRegion
): Boolean = when (movement) {
    MovementCoverage.LOWER_KNEE -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.QUADS_GLUTE, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
    MovementCoverage.POSTERIOR_CHAIN -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.POSTERIOR_CHAIN, com.training.trackplanner.data.PhysicalQualityRegion.HAMSTRING, com.training.trackplanner.data.PhysicalQualityRegion.LOWER, com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER)
    MovementCoverage.CALVES -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.ANKLE, com.training.trackplanner.data.PhysicalQualityRegion.LOWER)
    MovementCoverage.HORIZONTAL_PUSH, MovementCoverage.VERTICAL_PUSH -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH, com.training.trackplanner.data.PhysicalQualityRegion.CHEST, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
    MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL, MovementCoverage.UPPER_PULL -> qualifier in setOf(com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL, com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS, com.training.trackplanner.data.PhysicalQualityRegion.ARMS)
    MovementCoverage.ARMS_BICEPS, MovementCoverage.ARMS_TRICEPS -> qualifier == com.training.trackplanner.data.PhysicalQualityRegion.ARMS
    else -> false
}
