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

data class BCoachObjectiveValue(
    val name: String,
    val value: Double?,
    val unit: String,
    val provenance: String,
    val missingReason: String? = null
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
    val inputFingerprint: String,
    val goalWeights: List<BCoachGoalWeight>,
    val baselineObjectives: List<BCoachAxisEvaluation>,
    val candidateObjectives: List<BCoachAxisEvaluation>,
    val changes: List<String>,
    val alternatives: List<BCoachPlanAlternative>,
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
        expectedCandidateFingerprint: String,
        currentSkeleton: GeneratedProgramSkeleton
    ): BCoachProposalAcceptance {
        val candidate = decision.candidateFingerprint
        if (candidate == null || expectedCandidateFingerprint != candidate) {
            return BCoachProposalAcceptance(false, null, "CANDIDATE_FINGERPRINT_MISMATCH")
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
                "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW"
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
        val baselineFingerprint = fingerprint(baseline)
        val candidateFingerprint = fingerprint(candidate)
        val changes = changes(baseline, candidate)
        val changed = materialFingerprint(baseline) != materialFingerprint(candidate)
        val goalWeights = goalWeights(request.bGoalImportance)
        val unknowns = listOf(
            "APPROVED_TASK_NEED_INTERVALS_UNAVAILABLE",
            "APPROVED_SIX_AXIS_RECOVERY_CAPS_AND_DECAY_UNAVAILABLE",
            "APPROVED_CROSS_AXIS_NORMALIZERS_WEIGHTS_AND_EPSILON_UNAVAILABLE",
            "FUTURE_BODYWEIGHT_UNAVAILABLE",
            "MULTI_SET_SUCCESS_PROBABILITY_NOT_MODELED"
        )
        val baselineAxes = evaluateAxes(baseline, baseline, request, history, exercises)
        val candidateAxes = evaluateAxes(candidate, baseline, request, history, exercises)
        val hardConstraintFailures = hardConstraintFailures(candidate, candidateAxes, request)
        val reviewable = changed && hardConstraintFailures.isEmpty()
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
            inputFingerprint = inputFingerprint(request, history),
            goalWeights = goalWeights,
            baselineObjectives = baselineAxes,
            candidateObjectives = candidateAxes,
            changes = changes,
            alternatives = buildList {
                add(alternative(BCoachProductionStatus.HOLD_CALIBRATE, "KEEP_SAFE_BASELINE", baseline, 0))
                if (reviewable) add(alternative(BCoachProductionStatus.USER_REVIEW_PROPOSED,
                    "APPLY_REVIEWED_WHOLE_BLOCK_CANDIDATE", candidate, changes.size))
            },
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
                "recordedHistoryRowsAtCutoff" to history.count { !it.date.isAfter(request.asOf) }.toString()
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

    private fun hardConstraintFailures(
        candidate: GeneratedProgramSkeleton,
        axes: List<BCoachAxisEvaluation>,
        request: V22GenerationRequest
    ): List<String> = buildList {
        val daysOverLimit = objective(axes, BCoachObjectiveAxis.EXECUTION, "daysOverUserLimit") ?: 0.0
        if (daysOverLimit > 0.0) add("SESSION_TIME_LIMIT")
        if (candidate.items.any { it.weekNumber !in 1..request.weeks || it.dayOfWeek !in 1..7 }) {
            add("PROGRAM_BOUNDS")
        }
        if (candidate.items.any { item ->
                item.setCount <= 0 || ProgramSetPrescriptionResolver.resolve(item).size != item.setCount
            }) {
            add("SET_PRESCRIPTION_INTEGRITY")
        }
        if (candidate.items.map { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }.distinct().size !=
            candidate.items.size) {
            add("DUPLICATE_ORDER_SLOT")
        }
        if (candidate.items.map { Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey) }.distinct().size !=
            candidate.items.size) {
            add("DUPLICATE_EXERCISE_SESSION")
        }
        request.fixedWeekdays?.let { fixed ->
            val mismatched = candidate.items.groupBy(ProgramSkeletonItem::weekNumber).values.any { week ->
                week.map(ProgramSkeletonItem::dayOfWeek).toSet() != fixed
            }
            if (mismatched) add("FIXED_WEEKDAYS")
        }
        request.allowedWeekdays?.let { allowed ->
            if (candidate.items.any { it.dayOfWeek !in allowed }) add("ALLOWED_WEEKDAYS")
        }
    }

    private fun goalWeights(importance: BBalancedGoalImportance): List<BCoachGoalWeight> {
        val explicitTotal = BBalancedGoal.entries.sumOf { importance.value(it) ?: 0 }
        return BBalancedGoal.entries.map { goal ->
            val value = importance.value(goal)
            BCoachGoalWeight(
                goal = goal,
                explicitImportance = value,
                normalizedImportance = value?.takeIf { explicitTotal > 0 }
                    ?.let { it.toDouble() / explicitTotal },
                source = if (value == null) "MISSING_NOT_ZERO" else "EXPLICIT_USER_1_TO_5"
            )
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
        val recovery = recoveryValues(plan, history.filter { !it.date.isAfter(request.asOf) }, exercises)
        val unknownExercises = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
            .count { exercises[it] == null || exercises[it]?.metadataConfidence == "UNKNOWN" }
        val unknownEquipment = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
            .count { BBlockEquipmentIncrements.kind(it, exercises[it]) == BEquipmentIncrementKind.UNKNOWN }
        fun missingNeed(name: String) = BCoachObjectiveValue(
            name, null, "TASK_SPECIFIC_EFFECTIVE_DOSE_RATIO", "NO_APPROVED_NEED_POLICY",
            "APPROVED_NEED_INTERVAL_UNAVAILABLE"
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
                value("lastWeekMaximumDayMinutes", lastWeekMax, "minutes",
                    "B_ENGINEERING_TIME_UPPER_ESTIMATE_NOT_MEASURED"),
                value("daysOverUserLimit", if (timeLimit == null) 0.0
                    else dayMinutes.count { it > timeLimit }.toDouble(), "days",
                    if (timeLimit == null) "NO_USER_TIME_LIMIT" else "USER_SESSION_MINUTES")
            )),
            BCoachAxisEvaluation(BCoachObjectiveAxis.PLAN_CHANGE, listOf(
                value("exerciseOccurrencesAdded", addedOccurrences.toDouble(), "occurrences", "PLAN_DIFF"),
                value("exerciseOccurrencesRemoved", removedOccurrences.toDouble(), "occurrences", "PLAN_DIFF"),
                value("absoluteWorkSetChange", kotlin.math.abs(setDelta).toDouble(), "sets", "PLAN_DIFF")
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
                val observed = history.sumOf { row -> exercises[row.stableKey]?.let(selector) ?: 0.0 }
                add(value("${name}PlannedMaximum", daily.maxOrNull() ?: 0.0,
                    "metadata_weighted_sets", "REVIEWED_EXERCISE_METADATA_RAW_NO_CAP"))
                add(value("${name}PlannedMean", daily.averageOrZero(),
                    "metadata_weighted_sets", "REVIEWED_EXERCISE_METADATA_RAW_NO_CAP"))
                add(value("${name}RecentObservedRaw", observed,
                    "metadata_weighted_recorded_sets", "RECORDED_HISTORY_RAW_NO_DECAY"))
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
                old.setPrescriptions != new.setPrescriptions ->
                    "PRESCRIPTION:${slot.first}/${slot.second}/${old.exerciseStableKey}:" +
                        "${old.setCount}->${new.setCount}sets"
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

    internal fun fingerprint(skeleton: GeneratedProgramSkeleton): String = sha256(buildString {
        append(skeleton.suggestedName).append('|').append(skeleton.durationDays).append('|')
        append(skeleton.request.weeklyTrainingDays).append('|').append(skeleton.request.sessionMinutes)
            .append('|').append(skeleton.request.durationWeeks).append('|')
        skeleton.items.sortedWith(compareBy<ProgramSkeletonItem> { it.weekNumber }
            .thenBy { it.dayOfWeek }.thenBy { it.orderIndex }.thenBy { it.exerciseStableKey })
            .forEach { item ->
                append(item.weekNumber).append('/').append(item.dayOfWeek).append('/')
                    .append(item.orderIndex).append('/').append(item.exerciseStableKey).append('/')
                    .append(item.restSeconds).append(':')
                ProgramSetPrescriptionResolver.resolve(item).forEach { set ->
                    append(set.reps).append('@').append(set.weightKg).append('@').append(set.seconds)
                        .append('@').append(set.targetRpeMin).append('@').append(set.loadState.name).append(',')
                }
                append(';')
            }
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
            append(request.asOf).append('|').append(request.days).append('|').append(request.sessionMinutes)
                .append('|').append(request.allowedWeekdays?.sorted()).append('|')
                .append(request.fixedWeekdays?.sorted()).append('|').append(request.bGoalImportance)
                .append('|').append(request.bShadowPlateOneSideKg).append('|')
                .append(request.bShadowDumbbellIncrementKg).append('|')
                .append(request.bShadowMachineIncrementKg).append('|')
            request.blockProgram?.let { program ->
                program.blocks.forEach { block ->
                    append(block.weeks).append('/').append(block.goal).append('/')
                        .append(block.pattern).append('/').append(block.adjustment).append('/')
                        .append(block.amplitude).append('/').append(block.peakTestMode).append('/')
                        .append(block.customPriority).append('/').append(block.relationToPrevious).append(';')
                }
                append(program.recoveryDaysBetween)
            }
            append('|')
            history.asSequence().filter { !it.date.isAfter(request.asOf) }
                .sortedWith(compareBy<V22HistoryRow> { it.date }.thenBy { it.stableKey }.thenBy { it.setIndex })
                .forEach { row ->
                    append(row.date).append('/').append(row.stableKey).append('/').append(row.setIndex)
                        .append('/').append(row.reps).append('/').append(row.weightKg)
                        .append('/').append(row.seconds).append('/').append(row.rpe).append(';')
                }
        })

    internal fun withDecisionMetadata(
        skeleton: GeneratedProgramSkeleton,
        decision: BCoachMultiobjectiveDecision,
        acceptance: String
    ): GeneratedProgramSkeleton {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: "{}") }
            .getOrElse { JSONObject() }
        val decisionJson = JSONObject()
            .put("engineVersion", decision.engineVersion)
            .put("policyVersion", decision.policyVersion)
            .put("sourceOwner", decision.sourceOwner)
            .put("evidenceCutoff", decision.evidenceCutoff)
            .put("status", decision.status.name)
            .put("proposalAcceptance", acceptance)
            .put("baselineFingerprint", decision.baselineFingerprint)
            .put("candidateFingerprint", decision.candidateFingerprint ?: JSONObject.NULL)
            .put("inputFingerprint", decision.inputFingerprint)
            .put("fallbackReason", decision.fallbackReason ?: JSONObject.NULL)
            .put("mostAdverseModeledScenario", decision.mostAdverseModeledScenario)
            .put("unknowns", JSONArray(decision.unknowns))
            .put("changes", JSONArray(decision.changes))
            .put("goalWeights", JSONArray(decision.goalWeights.map { weight ->
                JSONObject().put("goal", weight.goal.name)
                    .put("explicitImportance", weight.explicitImportance ?: JSONObject.NULL)
                    .put("normalizedImportance", weight.normalizedImportance ?: JSONObject.NULL)
                    .put("source", weight.source)
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
        val warning = when (decision.status) {
            BCoachProductionStatus.USER_REVIEW_PROPOSED ->
                "B-Coach 다목적 후보는 승인 전에는 저장 계획에 반영되지 않습니다."
            BCoachProductionStatus.HOLD_CALIBRATE ->
                "B-Coach는 현재 구성을 유지하며 미확정 중량을 사용자 보정으로 남겼습니다."
            else -> null
        }
        return skeleton.copy(
            planningMetadataJson = root.toString(),
            warnings = (skeleton.warnings + listOfNotNull(warning)).distinct()
        )
    }

    private fun axesJson(axes: List<BCoachAxisEvaluation>): JSONArray = JSONArray(axes.map { axis ->
        JSONObject().put("axis", axis.axis.name).put("values", JSONArray(axis.values.map { value ->
            JSONObject().put("name", value.name).put("value", value.value ?: JSONObject.NULL)
                .put("unit", value.unit).put("provenance", value.provenance)
                .put("missingReason", value.missingReason ?: JSONObject.NULL)
        }))
    })

    private fun value(name: String, value: Double, unit: String, provenance: String) =
        BCoachObjectiveValue(name, value, unit, provenance)

    private fun objective(
        axes: List<BCoachAxisEvaluation>,
        axis: BCoachObjectiveAxis,
        name: String
    ): Double? = axes.single { it.axis == axis }.values.singleOrNull { it.name == name }?.value

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
