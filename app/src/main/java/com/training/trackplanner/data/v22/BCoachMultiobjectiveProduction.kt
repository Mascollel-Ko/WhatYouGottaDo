package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.max

enum class BCoachProductionStatus {
    BOUNDED_AUTO_APPLIED,
    USER_REVIEW_PROPOSED,
    HOLD_CALIBRATE,
    SHADOW_DIAGNOSTIC_ONLY
}

enum class BCoachObjectiveAxis {
    GOAL_DEFICIT,
    GOAL_EXCESS,
    PROGRESSION_FIT,
    RECOVERY_LOAD,
    RAMP_CHANGE,
    EXECUTION,
    PLAN_CHANGE,
    UNCERTAINTY
}

enum class BCoachValueStatus { READY, PARTIAL, UNKNOWN, NOT_APPLICABLE }

enum class BCoachConstraintStatus { PASS, FAIL, UNKNOWN }

data class BCoachObjectiveValue(
    val name: String,
    val value: Double?,
    val unit: String,
    val provenance: String,
    val missingReason: String? = null,
    val status: BCoachValueStatus = if (value == null) BCoachValueStatus.UNKNOWN
        else BCoachValueStatus.READY,
    val reasonCodes: List<String> = listOfNotNull(missingReason)
)

data class BCoachAxisEvaluation(
    val axis: BCoachObjectiveAxis,
    val values: List<BCoachObjectiveValue>
)

data class BCoachGoalWeight(
    val goal: BBalancedGoal,
    val explicitImportance: Int?,
    val normalizedImportance: Double?,
    val source: String
)

data class BCoachNeedEnvelope(
    val goal: BBalancedGoal,
    val block: Int,
    val week: Int,
    val lower: Double?,
    val upper: Double?,
    val source: String,
    val status: BCoachValueStatus,
    val reasonCodes: List<String>
)

data class BCoachConstraintResult(
    val id: String,
    val status: BCoachConstraintStatus,
    val reasonCodes: List<String> = emptyList()
)

data class BCoachPlanAlternative(
    val status: BCoachProductionStatus,
    val label: String,
    val weeklySetAverage: Double,
    val changeCount: Int
)

data class BCoachMultiobjectiveDecision(
    val engineVersion: String,
    val policyVersion: String,
    val sourceOwner: String,
    val evidenceCutoff: String,
    val status: BCoachProductionStatus,
    val baselineFingerprint: String,
    val candidateFingerprint: String?,
    val proposalFingerprint: String?,
    val inputFingerprint: String,
    val historyFingerprint: String,
    val exerciseFingerprint: String,
    val linkedEvidenceFingerprint: String,
    val goalWeights: List<BCoachGoalWeight>,
    val needEnvelopes: List<BCoachNeedEnvelope>,
    val baselineObjectives: List<BCoachAxisEvaluation>,
    val candidateObjectives: List<BCoachAxisEvaluation>,
    val hardConstraints: List<BCoachConstraintResult>,
    val changes: List<String>,
    val alternatives: List<BCoachPlanAlternative>,
    val rejectedActionReasons: List<String>,
    val inputAudit: Map<String, String>,
    val unknowns: List<String>,
    val mostAdverseModeledScenario: String,
    val fallbackReason: String?
)

data class BCoachProposalAcceptance(
    val accepted: Boolean,
    val skeleton: GeneratedProgramSkeleton?,
    val reason: String
)

class BCoachMultiobjectiveProposal internal constructor(
    val decision: BCoachMultiobjectiveDecision,
    private val proposedSkeleton: GeneratedProgramSkeleton
) {
    /** Revalidates the displayed fallback and the selected candidate before applying it. */
    fun accept(
        expectedProposalFingerprint: String,
        currentSkeleton: GeneratedProgramSkeleton
    ): BCoachProposalAcceptance {
        val candidate = decision.candidateFingerprint
        val proposal = decision.proposalFingerprint
        if (candidate == null || proposal == null || expectedProposalFingerprint != proposal) {
            return BCoachProposalAcceptance(false, null, "PROPOSAL_FINGERPRINT_MISMATCH")
        }
        if (BCoachMultiobjectiveProduction.fingerprint(currentSkeleton) != decision.baselineFingerprint) {
            return BCoachProposalAcceptance(false, null, "STALE_INPUT_REGENERATE_REQUIRED")
        }
        if (BCoachMultiobjectiveProduction.fingerprint(proposedSkeleton) != candidate) {
            return BCoachProposalAcceptance(false, null, "PROPOSAL_CONTENT_MISMATCH")
        }
        return BCoachProposalAcceptance(
            accepted = true,
            skeleton = BCoachMultiobjectiveProduction.withDecisionMetadata(
                proposedSkeleton,
                decision,
                "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW",
                acceptedStructureFingerprint = BCoachMultiobjectiveProduction.persistenceStructureFingerprint(
                    proposedSkeleton
                )
            ),
            reason = "ACCEPTED"
        )
    }
}

internal object BCoachMultiobjectiveProduction {
    const val ENGINE_VERSION = "B_COACH_MULTI_OBJECTIVE_V1"
    const val POLICY_VERSION = "B_COACH_REVIEW_GATE_V1"
    const val SOURCE_OWNER = "V22_B_COACH_MULTI_OBJECTIVE_REVIEW_GATE"

    data class Outcome(
        val skeleton: GeneratedProgramSkeleton,
        val decision: BCoachMultiobjectiveDecision,
        val proposal: BCoachMultiobjectiveProposal?
    )

    fun prepare(
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        baseline: GeneratedProgramSkeleton,
        candidate: GeneratedProgramSkeleton
    ): Outcome {
        val historyAtCutoff = history.filter { !it.date.isAfter(request.asOf) }
        val relevantExerciseKeys = (baseline.items + candidate.items)
            .map(ProgramSkeletonItem::exerciseStableKey).distinct().sorted()
        val baselineFingerprint = fingerprint(baseline)
        val candidateFingerprint = fingerprint(candidate)
        val changes = changes(baseline, candidate)
        val changed = materialFingerprint(baseline) != materialFingerprint(candidate)
        val goalWeights = goalWeights(request.bGoalImportance)
        val needEnvelopes = needEnvelopes(request)
        val historyFingerprint = historyFingerprint(historyAtCutoff)
        val exerciseFingerprint = exerciseFingerprint(relevantExerciseKeys, exercises)
        val linkedEvidenceFingerprint = linkedEvidenceFingerprint(request)
        val inputFingerprint = inputFingerprint(request, historyAtCutoff)
        val unknowns = buildList {
            addAll(listOf(
            "APPROVED_TASK_NEED_INTERVALS_UNAVAILABLE",
            "APPROVED_SIX_AXIS_RECOVERY_CAPS_AND_DECAY_UNAVAILABLE",
            "APPROVED_CROSS_AXIS_NORMALIZERS_WEIGHTS_AND_EPSILON_UNAVAILABLE",
            "FUTURE_BODYWEIGHT_UNAVAILABLE",
            "MULTI_SET_SUCCESS_PROBABILITY_NOT_MODELED"
            ))
            if (goalWeights.any { it.explicitImportance == null }) {
                add("GOAL_IMPORTANCE_INCOMPLETE_NOT_NORMALIZED")
            }
        }
        val baselineAxes = evaluateAxes(baseline, baseline, request, historyAtCutoff, exercises)
        val candidateAxes = evaluateAxes(candidate, baseline, request, historyAtCutoff, exercises)
        val hardConstraints = hardConstraints(candidate, candidateAxes, request, exercises)
        val hardConstraintFailures = hardConstraints
            .filter { it.status == BCoachConstraintStatus.FAIL }.map(BCoachConstraintResult::id)
        val reviewable = changed && hardConstraintFailures.isEmpty()
        val proposalFingerprint = if (reviewable) proposalFingerprint(
            baselineFingerprint = baselineFingerprint,
            candidateFingerprint = candidateFingerprint,
            inputFingerprint = inputFingerprint,
            historyFingerprint = historyFingerprint,
            exerciseFingerprint = exerciseFingerprint,
            linkedEvidenceFingerprint = linkedEvidenceFingerprint
        ) else null
        val status = if (reviewable) BCoachProductionStatus.USER_REVIEW_PROPOSED
            else BCoachProductionStatus.HOLD_CALIBRATE
        val adverse = if (hardConstraintFailures.isNotEmpty())
            hardConstraintFailures.joinToString(prefix = "HARD_CONSTRAINT_FAILED:", separator = ",")
        else "NEED_AND_RECOVERY_LIMITS_UNKNOWN_NO_PHYSIOLOGICAL_WORST_CASE_CLAIM"
        val decision = BCoachMultiobjectiveDecision(
            engineVersion = ENGINE_VERSION,
            policyVersion = POLICY_VERSION,
            sourceOwner = SOURCE_OWNER,
            evidenceCutoff = request.asOf.toString(),
            status = status,
            baselineFingerprint = baselineFingerprint,
            candidateFingerprint = candidateFingerprint.takeIf { reviewable },
            proposalFingerprint = proposalFingerprint,
            inputFingerprint = inputFingerprint,
            historyFingerprint = historyFingerprint,
            exerciseFingerprint = exerciseFingerprint,
            linkedEvidenceFingerprint = linkedEvidenceFingerprint,
            goalWeights = goalWeights,
            needEnvelopes = needEnvelopes,
            baselineObjectives = baselineAxes,
            candidateObjectives = candidateAxes,
            hardConstraints = hardConstraints,
            changes = changes,
            alternatives = buildList {
                add(alternative(BCoachProductionStatus.HOLD_CALIBRATE, "KEEP_SAFE_BASELINE", baseline, 0))
                if (reviewable) add(alternative(BCoachProductionStatus.USER_REVIEW_PROPOSED,
                    "APPLY_REVIEWED_WHOLE_BLOCK_CANDIDATE", candidate, changes.size))
            },
            rejectedActionReasons = listOf(
                "REST_ORDER_SEARCH_UNSUPPORTED",
                "UNIVERSAL_AUTOMATIC_LOAD_SELECTION_UNSUPPORTED",
                "ROBUST_SCALAR_WINNER_UNAVAILABLE_WITHOUT_APPROVED_WEIGHTS",
                "ONLY_ONE_ACTIONABLE_JOINT_CANDIDATE_FROM_EXISTING_BOUNDED_SEARCH"
            ),
            inputAudit = linkedMapOf(
                "goalImportance" to request.bGoalImportance.toString(),
                "blockGoals" to (request.blockProgram?.blocks?.map { it.goal.name }?.toString() ?: "LEGACY_B12"),
                "volumePatterns" to (request.blockProgram?.blocks?.map { it.pattern.name }?.toString()
                    ?: "LEGACY_B12"),
                "fixedWeekdays" to (request.fixedWeekdays?.sorted()?.toString() ?: "UNSET"),
                "allowedWeekdays" to (request.allowedWeekdays?.sorted()?.toString() ?: "ALL"),
                "plateOneSideKg" to (request.bShadowPlateOneSideKg?.toString() ?: "UNSET"),
                "dumbbellDisplayedIncrementKg" to
                    (request.bShadowDumbbellIncrementKg?.toString() ?: "UNSET"),
                "machineDisplayedIncrementKg" to
                    (request.bShadowMachineIncrementKg?.toString() ?: "UNSET"),
                "futureBodyweight" to "UNAVAILABLE_NOT_FABRICATED",
                "strengthModelRevision" to
                    "NOT_USED_NO_V22_PRODUCTION_LOAD_AUTHORITY_FOR_THIS_CANDIDATE",
                "recordedHistoryRowsAtCutoff" to historyAtCutoff.size.toString(),
                "sourceProgramStableKey" to (request.sourceProgramStableKey ?: "UNSET"),
                "linkedEvidenceApplicationId" to (request.linkedEvidenceApplicationId ?: "UNSET"),
                "relevantExerciseKeys" to relevantExerciseKeys.joinToString(","),
                "historyFingerprint" to historyFingerprint,
                "exerciseFingerprint" to exerciseFingerprint,
                "linkedEvidenceFingerprint" to linkedEvidenceFingerprint
            ),
            unknowns = unknowns,
            mostAdverseModeledScenario = adverse,
            fallbackReason = if (hardConstraintFailures.isNotEmpty())
                "CANDIDATE_HARD_CONSTRAINT_FAILED:" + hardConstraintFailures.joinToString(",")
            else if (changed)
                "UNAPPROVED_NEED_RECOVERY_AND_TRADEOFF_PARAMETERS_REQUIRE_USER_REVIEW"
            else "NO_MATERIAL_AUTHORIZED_CANDIDATE_KEEP_AND_CALIBRATE"
        )
        val persistedFallback = withDecisionMetadata(
            baseline,
            decision,
            if (reviewable) "NOT_ACCEPTED_SAFE_FALLBACK_PERSISTED" else "NO_PROPOSAL"
        )
        val proposal = if (reviewable) BCoachMultiobjectiveProposal(
            decision,
            withDecisionMetadata(candidate, decision, "PROPOSED_NOT_ACCEPTED")
        ) else null
        return Outcome(persistedFallback, decision, proposal)
    }

    private fun hardConstraints(
        candidate: GeneratedProgramSkeleton,
        axes: List<BCoachAxisEvaluation>,
        request: V22GenerationRequest,
        exercises: Map<String, Exercise>
    ): List<BCoachConstraintResult> = buildList {
        val daysOverLimit = objective(axes, BCoachObjectiveAxis.EXECUTION, "daysOverUserLimit")
        add(when {
            request.sessionMinutes == null -> constraint("SESSION_TIME_LIMIT", BCoachConstraintStatus.UNKNOWN,
                "USER_SESSION_LIMIT_UNSET")
            daysOverLimit == null -> constraint("SESSION_TIME_LIMIT", BCoachConstraintStatus.UNKNOWN,
                "TIME_UPPER_ESTIMATE_INCOMPLETE")
            daysOverLimit > 0.0 -> constraint("SESSION_TIME_LIMIT", BCoachConstraintStatus.FAIL,
                "SESSION_TIME_LIMIT")
            else -> constraint("SESSION_TIME_LIMIT", BCoachConstraintStatus.PASS)
        })
        add(constraint(
            "PROGRAM_BOUNDS",
            if (candidate.items.any {
                    it.weekNumber !in 1..candidate.request.durationWeeks || it.dayOfWeek !in 1..7
                }) BCoachConstraintStatus.FAIL else BCoachConstraintStatus.PASS
        ))
        add(constraint(
            "SET_PRESCRIPTION_INTEGRITY",
            if (candidate.items.any { item ->
                    item.setCount <= 0 || ProgramSetPrescriptionResolver.resolve(item).size != item.setCount
                }) BCoachConstraintStatus.FAIL else BCoachConstraintStatus.PASS
        ))
        add(constraint(
            "DUPLICATE_ORDER_SLOT",
            if (candidate.items.map { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }.distinct().size !=
                candidate.items.size) BCoachConstraintStatus.FAIL else BCoachConstraintStatus.PASS
        ))
        add(constraint(
            "DUPLICATE_EXERCISE_SESSION",
            if (candidate.items.map { Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey) }.distinct().size !=
                candidate.items.size) BCoachConstraintStatus.FAIL else BCoachConstraintStatus.PASS
        ))
        request.fixedWeekdays?.let { fixed ->
            val mismatched = candidate.items.groupBy(ProgramSkeletonItem::weekNumber).values.any { week ->
                week.map(ProgramSkeletonItem::dayOfWeek).toSet() != fixed
            }
            add(constraint("FIXED_WEEKDAYS",
                if (mismatched) BCoachConstraintStatus.FAIL else BCoachConstraintStatus.PASS))
        } ?: add(constraint("FIXED_WEEKDAYS", BCoachConstraintStatus.PASS, "NO_FIXED_WEEKDAYS"))
        request.allowedWeekdays?.let { allowed ->
            add(constraint("ALLOWED_WEEKDAYS", if (candidate.items.any { it.dayOfWeek !in allowed })
                BCoachConstraintStatus.FAIL else BCoachConstraintStatus.PASS))
        } ?: add(constraint("ALLOWED_WEEKDAYS", BCoachConstraintStatus.PASS, "ALL_WEEKDAYS_ALLOWED"))
        val explicitAutomaticLoads = candidate.items.filter { item ->
            ProgramSetPrescriptionResolver.resolve(item).any { set ->
                set.loadState == ProgramLoadState.EXPLICIT_LOAD && set.weightKg > 0.0
            }
        }
        add(when {
            explicitAutomaticLoads.isEmpty() -> constraint("AUTOMATIC_LOAD_AUTHORITY",
                BCoachConstraintStatus.PASS, "NO_AUTOMATIC_EXPLICIT_LOAD_PROPOSED")
            explicitAutomaticLoads.any {
                BBlockEquipmentIncrements.kind(it.exerciseStableKey, exercises[it.exerciseStableKey]) ==
                    BEquipmentIncrementKind.UNKNOWN
            } -> constraint("AUTOMATIC_LOAD_AUTHORITY", BCoachConstraintStatus.FAIL,
                "EQUIPMENT_GRID_UNKNOWN")
            else -> constraint("AUTOMATIC_LOAD_AUTHORITY", BCoachConstraintStatus.FAIL,
                "V22_UNIVERSAL_LOAD_POLICY_UNAPPROVED")
        })
    }

    private fun goalWeights(importance: BBalancedGoalImportance): List<BCoachGoalWeight> {
        val values = BBalancedGoal.entries.associateWith(importance::value)
        val complete = values.values.all { it != null }
        val explicitTotal = values.values.filterNotNull().sum()
        return BBalancedGoal.entries.map { goal ->
            val value = values.getValue(goal)
            BCoachGoalWeight(
                goal = goal,
                explicitImportance = value,
                normalizedImportance = value?.takeIf { complete && explicitTotal > 0 }
                    ?.let { it.toDouble() / explicitTotal },
                source = when {
                    value == null -> "MISSING_NOT_ZERO"
                    complete -> "EXPLICIT_USER_1_TO_5_NORMALIZED_COMPLETE_VECTOR"
                    else -> "EXPLICIT_USER_PARTIAL_VECTOR_NOT_NORMALIZED"
                }
            )
        }
    }

    private fun needEnvelopes(request: V22GenerationRequest): List<BCoachNeedEnvelope> {
        val blocks = request.blockProgram?.blocks.orEmpty().ifEmpty {
            listOf(BBlockDefinition(weeks = request.weeks))
        }
        return blocks.flatMapIndexed { blockIndex, block ->
            (1..block.weeks).flatMap { week ->
                BBalancedGoal.entries.map { goal ->
                    BCoachNeedEnvelope(
                        goal = goal,
                        block = blockIndex + 1,
                        week = week,
                        lower = null,
                        upper = null,
                        source = when (block.goal) {
                            BBlockGoal.BALANCED -> "LONG_TERM_IMPORTANCE_DIRECTION_ONLY"
                            BBlockGoal.AUTO -> "AUTO_PURPOSE_CANDIDATE_DIRECTION_ONLY"
                            else -> "EXPLICIT_BLOCK_PURPOSE_DIRECTION_ONLY"
                        },
                        status = BCoachValueStatus.UNKNOWN,
                        reasonCodes = listOf("APPROVED_V22_TASK_NEED_INTERVAL_UNAVAILABLE")
                    )
                }
            }
        }
    }

    private fun evaluateAxes(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>
    ): List<BCoachAxisEvaluation> {
        val sets = plan.items.sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
        val calibration = plan.items.sumOf { item ->
            ProgramSetPrescriptionResolver.resolve(item).count {
                it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
            }
        }
        val effort = plan.items.sumOf { item ->
            ProgramSetPrescriptionResolver.resolve(item).count { it.targetRpeMin != null }
        }
        val days = plan.items.groupBy { it.weekNumber to it.dayOfWeek }
        val dayMinutes = days.values.map { estimateMinutes(it, exercises) }
        val weekMinutes = days.entries.groupBy { it.key.first }.values.map { week ->
            week.sumOf { estimateMinutes(it.value, exercises) }
        }
        val lastWeek = plan.items.maxOfOrNull(ProgramSkeletonItem::weekNumber)
        val lastWeekMax = days.filterKeys { it.first == lastWeek }.values
            .maxOfOrNull { estimateMinutes(it, exercises) } ?: 0.0
        val timeLimit = request.sessionMinutes?.toDouble()
        val baselineKeys = baseline.items.map { Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey) }
            .groupingBy { it }.eachCount()
        val planKeys = plan.items.map { Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey) }
            .groupingBy { it }.eachCount()
        val addedOccurrences = planKeys.entries.sumOf { (key, count) ->
            max(0, count - (baselineKeys[key] ?: 0))
        }
        val removedOccurrences = baselineKeys.entries.sumOf { (key, count) ->
            max(0, count - (planKeys[key] ?: 0))
        }
        val setDelta = sets - baseline.items.sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
        val baselineBySlot = baseline.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        val planBySlot = plan.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        val sharedSlots = baselineBySlot.keys.intersect(planBySlot.keys)
        val repetitionChanges = sharedSlots.sumOf { key ->
            val before = ProgramSetPrescriptionResolver.resolve(baselineBySlot.getValue(key))
            val after = ProgramSetPrescriptionResolver.resolve(planBySlot.getValue(key))
            before.zip(after).count { (old, new) -> old.reps != new.reps }
        }
        val loadChanges = sharedSlots.sumOf { key ->
            val before = ProgramSetPrescriptionResolver.resolve(baselineBySlot.getValue(key))
            val after = ProgramSetPrescriptionResolver.resolve(planBySlot.getValue(key))
            before.zip(after).count { (old, new) ->
                old.weightKg != new.weightKg || old.loadState != new.loadState
            }
        }
        val recovery = recoveryValues(plan, history.filter { !it.date.isAfter(request.asOf) }, exercises)
        val unknownExercises = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
            .count { exercises[it] == null || exercises[it]?.metadataConfidence == "UNKNOWN" }
        val unknownEquipment = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
            .count { BBlockEquipmentIncrements.kind(it, exercises[it]) == BEquipmentIncrementKind.UNKNOWN }
        fun missingNeed(name: String) = BCoachObjectiveValue(
            name, null, "TASK_SPECIFIC_EFFECTIVE_DOSE_RATIO", "NO_APPROVED_NEED_POLICY",
            "APPROVED_NEED_INTERVAL_UNAVAILABLE", BCoachValueStatus.UNKNOWN
        )
        return listOf(
            BCoachAxisEvaluation(BCoachObjectiveAxis.GOAL_DEFICIT, listOf(
                missingNeed("maximumShortfall"), missingNeed("priorityWeightedMeanShortfall"),
                missingNeed("undersuppliedTaskShare")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.GOAL_EXCESS, listOf(
                missingNeed("maximumExcess"), missingNeed("priorityWeightedMeanExcess"),
                missingNeed("aboveIntervalTaskShare")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.PROGRESSION_FIT, listOf(
                value("plannedWorkSets", sets.toDouble(), "sets", "SKELETON_SET_PRESCRIPTIONS"),
                value("knownEffortTargetSets", effort.toDouble(), "sets", "EXPLICIT_TARGET_RPE_ONLY"),
                value("calibrationRequiredSets", calibration.toDouble(), "sets", "PROGRAM_LOAD_STATE"),
                BCoachObjectiveValue("supportedRirFit", null, "RIR_DISTANCE",
                    "NO_UNIVERSAL_V22_RIR_AUTHORITY", "SUPPORTED_CURVE_AND_POLICY_NOT_AVAILABLE_FOR_ALL_SETS")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.RECOVERY_LOAD, recovery),
            BCoachAxisEvaluation(BCoachObjectiveAxis.RAMP_CHANGE, listOf(
                value("workSetDeltaFromFallback", setDelta.toDouble(), "sets", "PLAN_DIFF"),
                value("exerciseOccurrencesAdded", addedOccurrences.toDouble(), "occurrences", "PLAN_DIFF"),
                value("exerciseOccurrencesRemoved", removedOccurrences.toDouble(), "occurrences", "PLAN_DIFF"),
                BCoachObjectiveValue("approvedRelativeJumpExcess", null, "ratio",
                    "NO_APPROVED_RAMP_THRESHOLD", "APPROVED_THRESHOLD_UNAVAILABLE")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.EXECUTION, listOf(
                value("maximumDayMinutes", dayMinutes.maxOrNull() ?: 0.0, "minutes",
                    "B_ENGINEERING_TIME_UPPER_ESTIMATE_NOT_MEASURED"),
                value("meanDayMinutes", dayMinutes.averageOrZero(), "minutes",
                    "B_ENGINEERING_TIME_UPPER_ESTIMATE_NOT_MEASURED"),
                value("maximumWeekMinutes", weekMinutes.maxOrNull() ?: 0.0, "minutes_per_week",
                    "SUM_OF_B_ENGINEERING_DAY_UPPER_ESTIMATES_NOT_MEASURED"),
                value("lastWeekMaximumDayMinutes", lastWeekMax, "minutes",
                    "B_ENGINEERING_TIME_UPPER_ESTIMATE_NOT_MEASURED"),
                if (timeLimit == null) BCoachObjectiveValue(
                    "daysOverUserLimit", null, "days", "NO_USER_TIME_LIMIT", null,
                    BCoachValueStatus.NOT_APPLICABLE, listOf("USER_SESSION_LIMIT_UNSET")
                ) else value("daysOverUserLimit", dayMinutes.count { it > timeLimit }.toDouble(),
                    "days", "USER_SESSION_MINUTES")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.PLAN_CHANGE, listOf(
                value("exerciseOccurrencesAdded", addedOccurrences.toDouble(), "occurrences", "PLAN_DIFF"),
                value("exerciseOccurrencesRemoved", removedOccurrences.toDouble(), "occurrences", "PLAN_DIFF"),
                value("absoluteWorkSetChange", kotlin.math.abs(setDelta).toDouble(), "sets", "PLAN_DIFF"),
                value("repetitionChanges", repetitionChanges.toDouble(), "sets", "PLAN_DIFF"),
                value("loadChanges", loadChanges.toDouble(), "sets", "PLAN_DIFF")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.UNCERTAINTY, listOf(
                value("missingApprovedNeedAxes", 2.0, "axes", "AUTHORITY_GATE"),
                value("calibrationRequiredSets", calibration.toDouble(), "sets", "PROGRAM_LOAD_STATE"),
                value("unknownMetadataExercises", unknownExercises.toDouble(), "exercises", "CANONICAL_METADATA"),
                value("unknownEquipmentIncrementExercises", unknownEquipment.toDouble(), "exercises", "EQUIPMENT_GRID")
            ))
        )
    }

    private fun recoveryValues(
        plan: GeneratedProgramSkeleton,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>
    ): List<BCoachObjectiveValue> {
        val channels = linkedMapOf<String, (Exercise) -> Double>(
            "systemic" to Exercise::systemicLoadWeight,
            "neuralHeavy" to Exercise::neuralHeavyWeight,
            "neuralSpeed" to Exercise::neuralSpeedWeight,
            "localMuscular" to Exercise::localLoadWeight,
            "deceleration" to Exercise::decelerationWeight,
            "elasticSsc" to Exercise::elasticSscWeight
        )
        val dayItems = plan.items.groupBy { it.weekNumber to it.dayOfWeek }
        return buildList {
            channels.forEach { (name, selector) ->
                val daily = dayItems.values.map { items ->
                    items.sumOf { item ->
                        val exercise = exercises[item.exerciseStableKey]
                        if (exercise == null) 0.0 else item.setCount * selector(exercise)
                    }
                }
                val missingPlanned = plan.items.count { exercises[it.exerciseStableKey] == null }
                val observed = history.sumOf { row -> exercises[row.stableKey]?.let(selector) ?: 0.0 }
                val missingObserved = history.count { exercises[it.stableKey] == null }
                add(rawLowerBoundValue("${name}PlannedMaximum", daily.maxOrNull() ?: 0.0,
                    "metadata_weighted_sets", "REVIEWED_EXERCISE_METADATA_RAW_NO_CAP",
                    missingPlanned, "MISSING_PLANNED_EXERCISE_METADATA"))
                add(rawLowerBoundValue("${name}PlannedMean", daily.averageOrZero(),
                    "metadata_weighted_sets", "REVIEWED_EXERCISE_METADATA_RAW_NO_CAP",
                    missingPlanned, "MISSING_PLANNED_EXERCISE_METADATA"))
                add(rawLowerBoundValue("${name}RecentObservedRaw", observed,
                    "metadata_weighted_recorded_sets", "RECORDED_HISTORY_RAW_NO_DECAY",
                    missingObserved, "MISSING_HISTORY_EXERCISE_METADATA"))
            }
            add(BCoachObjectiveValue("daysOverApprovedRecoveryCap", null, "days",
                "NO_APPROVED_RECOVERY_CAP", "CAP_AND_DECAY_POLICY_UNAVAILABLE"))
        }
    }

    private fun estimateMinutes(items: List<ProgramSkeletonItem>, exercises: Map<String, Exercise>): Double {
        if (items.isEmpty()) return 0.0
        var total = 8.0
        items.forEach { item ->
            val exercise = exercises[item.exerciseStableKey]
            val sets = ProgramSetPrescriptionResolver.resolve(item)
            val setup = if (exercise?.equipment.orEmpty().isBlank()) 1.5 else 2.3
            val activeSeconds = sets.sumOf { set -> (set.seconds.takeIf { it > 0 } ?: 45).toDouble() }
            total += setup + activeSeconds / 60.0 +
                max(0, sets.size - 1) * item.restSeconds.coerceAtLeast(45) / 60.0 + 0.75
        }
        return total * 1.12 + 2.0
    }

    private fun changes(
        baseline: GeneratedProgramSkeleton,
        candidate: GeneratedProgramSkeleton
    ): List<String> {
        fun slots(plan: GeneratedProgramSkeleton) = plan.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        val before = slots(baseline)
        val after = slots(candidate)
        return (before.keys + after.keys).distinct().sortedWith(
            compareBy<Triple<Int, Int, String>> { it.first }.thenBy { it.second }.thenBy { it.third }
        ).mapNotNull { slot ->
            val old = before[slot]
            val new = after[slot]
            when {
                old == null -> "ADD:${slot.first}/${slot.second}/${new!!.exerciseStableKey}:${new.setCount}sets"
                new == null -> "REMOVE:${slot.first}/${slot.second}/${old.exerciseStableKey}:${old.setCount}sets"
                ProgramSetPrescriptionResolver.resolve(old) != ProgramSetPrescriptionResolver.resolve(new) ->
                    "PRESCRIPTION:${slot.first}/${slot.second}/${old.exerciseStableKey}:" +
                        "${old.setCount}->${new.setCount}sets:" +
                        "${ProgramSetPrescriptionResolver.resolve(old).map { it.reps }}->" +
                        ProgramSetPrescriptionResolver.resolve(new).map { it.reps }
                else -> null
            }
        }
    }

    private fun alternative(
        status: BCoachProductionStatus,
        label: String,
        skeleton: GeneratedProgramSkeleton,
        changes: Int
    ): BCoachPlanAlternative {
        val weeks = skeleton.request.durationWeeks.coerceAtLeast(1)
        val sets = skeleton.items.sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
        return BCoachPlanAlternative(status, label, sets.toDouble() / weeks, changes)
    }

    internal fun fingerprint(skeleton: GeneratedProgramSkeleton): String =
        canonicalSkeletonFingerprint(skeleton, includeLoad = true)

    /** Manual kg calibration is an independent user action after accepting roster/set/rep changes. */
    internal fun persistenceStructureFingerprint(skeleton: GeneratedProgramSkeleton): String =
        canonicalSkeletonFingerprint(skeleton, includeLoad = false)

    private fun canonicalSkeletonFingerprint(
        skeleton: GeneratedProgramSkeleton,
        includeLoad: Boolean
    ): String = sha256(buildString {
        canonicalField(if (includeLoad) skeleton.suggestedName else "USER_PROGRAM_NAME_ALLOWED")
        canonicalField(skeleton.durationDays)
        canonicalField(skeleton.periodizationType.name)
        val request = skeleton.request
        canonicalField(if (includeLoad) request.name else "USER_PROGRAM_NAME_ALLOWED")
        canonicalField(request.goal.name)
        canonicalField(request.weeklyTrainingDays)
        canonicalField(request.sessionMinutes)
        canonicalField(request.availableEquipment.sorted().joinToString(","))
        canonicalField(request.excludedExerciseText)
        canonicalField(request.badmintonTransferRatio)
        canonicalField(request.sportStrengthRatio)
        canonicalField(request.periodizationType.name)
        canonicalField(request.durationWeeks)
        canonicalField(request.badmintonEventProfile.name)
        canonicalField(request.varietyPreference.name)
        canonicalField(request.excludedExerciseStableKeys.sorted().joinToString(","))
        canonicalField(request.preferredExerciseStableKeys.sorted().joinToString(","))
        skeleton.weekPlans.sortedBy { it.weekIndex }.forEach { canonicalField(it) }
        skeleton.weekDaySchedule.toSortedMap().forEach { (week, days) ->
            canonicalField(week)
            canonicalField(days.sorted().joinToString(","))
        }
        skeleton.items.sortedWith(compareBy<ProgramSkeletonItem> { it.weekNumber }
            .thenBy { it.dayOfWeek }.thenBy { it.orderIndex }.thenBy { it.exerciseStableKey }
            .thenBy { it.localId })
            .forEach { item ->
                val normalized = item.copy(
                    weightKg = if (includeLoad) item.weightKg else 0.0,
                    weightSource = if (includeLoad) item.weightSource else "MANUAL_LOAD_ALLOWED",
                    primarySlotCapabilities = item.primarySlotCapabilities.sorted(),
                    secondarySlotCapabilities = item.secondarySlotCapabilities.sorted(),
                    weakSlotCapabilities = item.weakSlotCapabilities.sorted(),
                    slotCapabilityWarnings = item.slotCapabilityWarnings.sorted(),
                    setPrescriptions = emptyList()
                )
                canonicalField(normalized)
                ProgramSetPrescriptionResolver.resolve(item).forEach { set ->
                    canonicalField(if (includeLoad) set else set.copy(
                        weightKg = 0.0,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                    ))
                }
            }
        skeleton.progressionSessions.sortedBy { it.key }.forEach { canonicalField(it) }
    })

    /** REST/ORDER are preserved by the exact acceptance fingerprint but are not active search levers. */
    private fun materialFingerprint(skeleton: GeneratedProgramSkeleton): String = sha256(buildString {
        append(skeleton.durationDays).append('|').append(skeleton.request.weeklyTrainingDays).append('|')
            .append(skeleton.request.durationWeeks).append('|')
        skeleton.items.sortedWith(compareBy<ProgramSkeletonItem> { it.weekNumber }
            .thenBy { it.dayOfWeek }.thenBy { it.exerciseStableKey })
            .forEach { item ->
                append(item.weekNumber).append('/').append(item.dayOfWeek).append('/')
                    .append(item.exerciseStableKey).append(':')
                ProgramSetPrescriptionResolver.resolve(item).forEach { set ->
                    append(set.reps).append('@').append(set.weightKg).append('@').append(set.seconds)
                        .append('@').append(set.targetRpeMin).append('@').append(set.loadState.name).append(',')
                }
                append(';')
            }
    })

    private fun inputFingerprint(request: V22GenerationRequest, history: List<V22HistoryRow>): String =
        sha256(buildString {
            canonicalField(request.name)
            canonicalField(request.weeks)
            canonicalField(request.days)
            canonicalField(request.mode.name)
            canonicalField(request.asOf)
            canonicalField(request.focusStableKey)
            canonicalField(request.sessionMinutes)
            canonicalField(request.backupName)
            canonicalField(request.bGoalImportance)
            canonicalField(request.bMaxTrainingDays)
            canonicalField(request.allowedWeekdays?.sorted()?.joinToString(","))
            canonicalField(request.fixedWeekdays?.sorted()?.joinToString(","))
            canonicalField(request.sourceProgramStableKey)
            canonicalField(request.linkedDuePlannedSets)
            canonicalField(request.linkedConfirmedSets)
            canonicalField(request.linkedEvidenceApplicationId)
            canonicalField(request.bShadowPlateOneSideKg)
            canonicalField(request.bShadowDumbbellIncrementKg)
            canonicalField(request.bShadowMachineIncrementKg)
            request.blockProgram?.let { program ->
                program.blocks.forEach { canonicalField(it) }
                canonicalField(program.recoveryDaysBetween.joinToString(","))
            } ?: canonicalField(null)
            canonicalField(historyFingerprint(history))
        })

    internal fun historyFingerprint(history: List<V22HistoryRow>): String = sha256(buildString {
        history.sortedWith(compareBy<V22HistoryRow> { it.date }.thenBy { it.stableKey }
            .thenBy { it.setIndex }.thenBy { it.exerciseName })
            .forEach { canonicalField(it) }
    })

    internal fun exerciseFingerprint(
        relevantKeys: List<String>,
        exercises: Map<String, Exercise>
    ): String = sha256(buildString {
        relevantKeys.sorted().forEach { key ->
            canonicalField(key)
            canonicalField(exercises[key]?.toString() ?: "MISSING")
        }
    })

    internal fun linkedEvidenceFingerprint(request: V22GenerationRequest): String =
        linkedEvidenceFingerprint(
            request.sourceProgramStableKey,
            request.linkedEvidenceApplicationId,
            request.linkedDuePlannedSets,
            request.linkedConfirmedSets
        )

    internal fun linkedEvidenceFingerprint(
        sourceProgramStableKey: String?,
        applicationId: String?,
        duePlannedSets: Int?,
        confirmedSets: Int?
    ): String = sha256(buildString {
        canonicalField(sourceProgramStableKey)
        canonicalField(applicationId)
        canonicalField(duePlannedSets)
        canonicalField(confirmedSets)
    })

    private fun proposalFingerprint(
        baselineFingerprint: String,
        candidateFingerprint: String,
        inputFingerprint: String,
        historyFingerprint: String,
        exerciseFingerprint: String,
        linkedEvidenceFingerprint: String
    ): String = sha256(buildString {
        listOf(
            ENGINE_VERSION, POLICY_VERSION, SOURCE_OWNER, baselineFingerprint, candidateFingerprint,
            inputFingerprint, historyFingerprint, exerciseFingerprint, linkedEvidenceFingerprint
        ).forEach { canonicalField(it) }
    })

    internal data class AcceptedPersistenceSnapshot(
        val cutoff: java.time.LocalDate,
        val sourceProgramStableKey: String?,
        val relevantExerciseKeys: List<String>,
        val historyFingerprint: String,
        val exerciseFingerprint: String,
        val linkedEvidenceFingerprint: String
    )

    internal fun acceptedPersistenceSnapshot(
        skeleton: GeneratedProgramSkeleton
    ): AcceptedPersistenceSnapshot? {
        val decision = runCatching {
            JSONObject(skeleton.planningMetadataJson ?: return null)
                .getJSONObject("bCoachMultiobjectiveDecision")
        }.getOrNull() ?: return null
        // v1 accepted plans may already be persisted and lack a replayable snapshot.
        // Only v2+ transient acceptances are eligible for the new atomic revalidation.
        if (decision.optInt("acceptanceSchemaVersion", 1) != 2) return null
        if (decision.optString("proposalAcceptance") != "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW") {
            return null
        }
        val audit = decision.optJSONObject("inputAudit") ?: return null
        val source = audit.optString("sourceProgramStableKey")
            .takeUnless { it.isBlank() || it == "UNSET" }
        return AcceptedPersistenceSnapshot(
            cutoff = runCatching { java.time.LocalDate.parse(decision.getString("evidenceCutoff")) }
                .getOrElse { throw IllegalStateException("B_COACH_ACCEPTANCE_INVALID_CUTOFF") },
            sourceProgramStableKey = source,
            relevantExerciseKeys = audit.optString("relevantExerciseKeys").split(',')
                .filter(String::isNotBlank).sorted(),
            historyFingerprint = decision.getString("historyFingerprint"),
            exerciseFingerprint = decision.getString("exerciseFingerprint"),
            linkedEvidenceFingerprint = decision.getString("linkedEvidenceFingerprint")
        )
    }

    internal fun validateAcceptedForPersistence(
        skeleton: GeneratedProgramSkeleton,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        currentLinkedEvidenceFingerprint: String
    ) {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: return) }
            .getOrElse { throw IllegalStateException("B_COACH_ACCEPTANCE_METADATA_INVALID") }
        val decision = root.optJSONObject("bCoachMultiobjectiveDecision") ?: return
        if (decision.optString("proposalAcceptance") != "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW") return
        val baseline = decision.optString("baselineFingerprint")
        val candidate = decision.optString("candidateFingerprint")
        val input = decision.optString("inputFingerprint")
        val originalHistory = decision.optString("historyFingerprint")
        val originalExercise = decision.optString("exerciseFingerprint")
        val originalLinked = decision.optString("linkedEvidenceFingerprint")
        val proposal = decision.optString("proposalFingerprint")
        val structure = decision.optString("acceptedStructureFingerprint")
        val audit = decision.optJSONObject("inputAudit")
            ?: throw IllegalStateException("B_COACH_ACCEPTANCE_INPUT_AUDIT_MISSING")
        val relevantKeys = audit.optString("relevantExerciseKeys").split(',')
            .filter(String::isNotBlank).sorted()
        if (listOf(baseline, candidate, input, originalHistory, originalExercise, originalLinked,
                proposal, structure).any(String::isBlank)) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_FINGERPRINT_MISSING")
        }
        val expectedProposal = proposalFingerprint(
            baseline, candidate, input, originalHistory, originalExercise, originalLinked
        )
        if (proposal != expectedProposal) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_PROPOSAL_TAMPERED")
        }
        if (persistenceStructureFingerprint(skeleton) != structure) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_STRUCTURE_STALE")
        }
        if (historyFingerprint(history) != originalHistory) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_HISTORY_STALE")
        }
        if (exerciseFingerprint(relevantKeys, exercises) != originalExercise) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_EXERCISE_METADATA_STALE")
        }
        if (currentLinkedEvidenceFingerprint != originalLinked) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_LINKED_EVIDENCE_STALE")
        }
        if (skeleton.items.flatMap(ProgramSetPrescriptionResolver::resolve).any { set ->
                !set.weightKg.isFinite() || set.weightKg < 0.0 ||
                    (set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && set.weightKg != 0.0)
            }) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_LOAD_STATE_INVALID")
        }
    }

    internal fun markAcceptedAndPersisted(
        skeleton: GeneratedProgramSkeleton
    ): GeneratedProgramSkeleton {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: return skeleton) }
            .getOrElse { return skeleton }
        val decision = root.optJSONObject("bCoachMultiobjectiveDecision") ?: return skeleton
        if (decision.optString("proposalAcceptance") != "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW") {
            return skeleton
        }
        decision.put("proposalAcceptance", "ACCEPTED_AND_PERSISTED")
        return skeleton.copy(planningMetadataJson = root.toString())
    }

    internal fun withDecisionMetadata(
        skeleton: GeneratedProgramSkeleton,
        decision: BCoachMultiobjectiveDecision,
        acceptance: String,
        acceptedStructureFingerprint: String? = null
    ): GeneratedProgramSkeleton {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: "{}") }
            .getOrElse { JSONObject() }
        val decisionJson = JSONObject()
            .put("engineVersion", decision.engineVersion)
            .put("policyVersion", decision.policyVersion)
            .put("acceptanceSchemaVersion", 2)
            .put("sourceOwner", decision.sourceOwner)
            .put("evidenceCutoff", decision.evidenceCutoff)
            .put("status", decision.status.name)
            .put("proposalAcceptance", acceptance)
            .put("baselineFingerprint", decision.baselineFingerprint)
            .put("candidateFingerprint", decision.candidateFingerprint ?: JSONObject.NULL)
            .put("proposalFingerprint", decision.proposalFingerprint ?: JSONObject.NULL)
            .put("acceptedStructureFingerprint", acceptedStructureFingerprint ?: JSONObject.NULL)
            .put("inputFingerprint", decision.inputFingerprint)
            .put("historyFingerprint", decision.historyFingerprint)
            .put("exerciseFingerprint", decision.exerciseFingerprint)
            .put("linkedEvidenceFingerprint", decision.linkedEvidenceFingerprint)
            .put("fallbackReason", decision.fallbackReason ?: JSONObject.NULL)
            .put("mostAdverseModeledScenario", decision.mostAdverseModeledScenario)
            .put("unknowns", JSONArray(decision.unknowns))
            .put("changes", JSONArray(decision.changes))
            .put("rejectedActionReasons", JSONArray(decision.rejectedActionReasons))
            .put("goalWeights", JSONArray(decision.goalWeights.map { weight ->
                JSONObject().put("goal", weight.goal.name)
                    .put("explicitImportance", weight.explicitImportance ?: JSONObject.NULL)
                    .put("normalizedImportance", weight.normalizedImportance ?: JSONObject.NULL)
                    .put("source", weight.source)
            }))
            .put("needEnvelopes", JSONArray(decision.needEnvelopes.map { need ->
                JSONObject().put("goal", need.goal.name).put("block", need.block)
                    .put("week", need.week).put("lower", need.lower ?: JSONObject.NULL)
                    .put("upper", need.upper ?: JSONObject.NULL).put("source", need.source)
                    .put("status", need.status.name).put("reasonCodes", JSONArray(need.reasonCodes))
            }))
            .put("hardConstraints", JSONArray(decision.hardConstraints.map { constraint ->
                JSONObject().put("id", constraint.id).put("status", constraint.status.name)
                    .put("reasonCodes", JSONArray(constraint.reasonCodes))
            }))
            .put("objectiveVector", axesJson(decision.candidateObjectives))
            .put("baselineObjectiveVector", axesJson(decision.baselineObjectives))
            .put("candidateObjectiveVector", axesJson(decision.candidateObjectives))
            .put("alternatives", JSONArray(decision.alternatives.map { alternative ->
                JSONObject().put("status", alternative.status.name).put("label", alternative.label)
                    .put("weeklySetAverage", alternative.weeklySetAverage)
                    .put("changeCount", alternative.changeCount)
            }))
            .put("inputAudit", JSONObject().also { audit ->
                decision.inputAudit.forEach { (key, value) -> audit.put(key, value) }
            })
        root.put("bCoachMultiobjectiveDecision", decisionJson)
        val warning = when {
            acceptance == "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW" ->
                "사용자가 검토한 B-Coach 후보를 적용했습니다. 미확정 중량은 계속 사용자 보정이 필요합니다."
            decision.status == BCoachProductionStatus.USER_REVIEW_PROPOSED ->
                "B-Coach 다목적 후보는 승인 전에는 저장 계획에 반영되지 않습니다."
            decision.status == BCoachProductionStatus.HOLD_CALIBRATE ->
                "B-Coach는 현재 구성을 유지하며 미확정 중량을 사용자 보정으로 남겼습니다."
            else -> null
        }
        return skeleton.copy(
            planningMetadataJson = root.toString(),
            warnings = (skeleton.warnings.filterNot { it.startsWith("B-Coach 다목적 후보는") ||
                it.startsWith("B-Coach는 현재 구성을 유지하며") ||
                it.startsWith("사용자가 검토한 B-Coach 후보를") } + listOfNotNull(warning)).distinct()
        )
    }

    private fun axesJson(axes: List<BCoachAxisEvaluation>): JSONArray = JSONArray(axes.map { axis ->
        JSONObject().put("axis", axis.axis.name).put("values", JSONArray(axis.values.map { value ->
            JSONObject().put("name", value.name).put("value", value.value ?: JSONObject.NULL)
                .put("unit", value.unit).put("provenance", value.provenance)
                .put("missingReason", value.missingReason ?: JSONObject.NULL)
                .put("status", value.status.name).put("reasonCodes", JSONArray(value.reasonCodes))
        }))
    })

    private fun value(name: String, value: Double, unit: String, provenance: String) =
        BCoachObjectiveValue(name, value, unit, provenance)

    private fun rawLowerBoundValue(
        name: String,
        value: Double,
        unit: String,
        provenance: String,
        missingCount: Int,
        missingReason: String
    ) = if (missingCount == 0) BCoachObjectiveValue(name, value, unit, provenance)
    else BCoachObjectiveValue(
        name = name,
        value = value,
        unit = unit,
        provenance = "$provenance:KNOWN_LOWER_BOUND_ONLY",
        missingReason = missingReason,
        status = BCoachValueStatus.PARTIAL,
        reasonCodes = listOf(missingReason, "MISSING_COUNT=$missingCount")
    )

    private fun constraint(
        id: String,
        status: BCoachConstraintStatus,
        vararg reasonCodes: String
    ) = BCoachConstraintResult(id, status, reasonCodes.toList())

    private fun objective(
        axes: List<BCoachAxisEvaluation>,
        axis: BCoachObjectiveAxis,
        name: String
    ): Double? = axes.single { it.axis == axis }.values.singleOrNull { it.name == name }?.value

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun StringBuilder.canonicalField(value: Any?) {
        val encoded = value?.toString() ?: "<NULL>"
        append(encoded.length).append(':').append(encoded).append('|')
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
