package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

internal object BCoachV2GlobalSelector {
    data class Result(
        val fallback: GeneratedProgramSkeleton,
        val decision: BCoachMultiobjectiveDecision,
        val candidatesById: Map<String, GeneratedProgramSkeleton>,
        val loadInstructionsByCandidateId: Map<String, Map<String, String>>,
        val recommendedCandidateId: String?
    )

    private data class Candidate(
        val id: String,
        val label: String,
        val skeleton: GeneratedProgramSkeleton,
        val evaluation: BCoachV2CandidateEvaluation,
        val loadData: BCoachV2LoadResolution,
        val signature: String,
        val changes: List<String>,
        val anchorsMaintained: Int,
        var robustRegret: Double = 0.0,
        var pareto: Boolean = false
    )

    private data class SearchState(
        val label: String,
        val skeleton: GeneratedProgramSkeleton,
        val evaluation: BCoachV2CandidateEvaluation,
        val loadData: BCoachV2LoadResolution,
        val signature: String
    )

    private data class ResolvedPlan(
        val skeleton: GeneratedProgramSkeleton,
        val evaluation: BCoachV2CandidateEvaluation,
        val loadData: BCoachV2LoadResolution,
        val signature: String
    )

    fun select(
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList(),
        exercises: Map<String, Exercise>,
        baseline: GeneratedProgramSkeleton,
        jointCandidate: GeneratedProgramSkeleton,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        mainExerciseKeys: Set<String>,
        recommendedImportance: Map<BBalancedGoal, Int> = emptyMap(),
        modelSnapshots: Map<String, BCoachV2LoadModelSnapshot> = emptyMap(),
        rirPolicy: RpeRirPolicy? = null
    ): Result {
        val historyAtCutoff = history.filter { !it.date.isAfter(request.asOf) }
        val badmintonAtCutoff = badmintonHistory.filter { !it.date.isAfter(request.asOf) }
        val goalWeights = effectiveGoalWeights(
            request.bGoalImportance,
            historyAtCutoff.isNotEmpty(),
            recommendedImportance
        )
        val baselineFingerprint = BCoachMultiobjectiveProduction.fingerprint(baseline)
        val inputFingerprint = BCoachMultiobjectiveProduction.inputFingerprint(
            request, historyAtCutoff, badmintonAtCutoff
        )
        val loadEvidenceFingerprint = sha256(modelSnapshots.toSortedMap().entries.joinToString("|") {
            (key, model) -> "$key:${model.sourceVersion}:${model.evidenceIds.sorted()}:${model.stale}"
        } + "|rirPolicy=${rirPolicy?.version ?: "NONE"}|badminton=" +
            BCoachMultiobjectiveProduction.historyFingerprint(emptyList(), badmintonAtCutoff))
        val baselineLoad = BCoachV2LoadCandidateResolver.resolve(
            baseline, request, historyAtCutoff, exercises, modelSnapshots, mainExerciseKeys, rirPolicy
        )
        val evaluationCache = mutableMapOf<String, ResolvedPlan>()
        fun evaluatePlan(rawSkeleton: GeneratedProgramSkeleton): ResolvedPlan {
            val rawSignature = BCoachMultiobjectiveProduction.fingerprint(rawSkeleton)
            val cacheKey = sha256("$rawSignature|$inputFingerprint|$loadEvidenceFingerprint")
            return evaluationCache.getOrPut(cacheKey) {
                val loadData = if (rawSkeleton === baseline) baselineLoad else
                    BCoachV2LoadCandidateResolver.resolve(
                        rawSkeleton, request, historyAtCutoff, exercises, modelSnapshots,
                        mainExerciseKeys, rirPolicy
                    )
                val resolvedSkeleton = loadData.skeleton
                val evaluation = BCoachV2DoseEvaluator.evaluate(
                    plan = resolvedSkeleton,
                    baseline = baselineLoad.skeleton,
                    request = request,
                    history = historyAtCutoff,
                    exercises = exercises,
                    muscleRelations = muscleRelations,
                    coreCatalog = coreCatalog,
                    badmintonCatalog = badmintonCatalog,
                    mainExerciseKeys = mainExerciseKeys,
                    goalWeights = goalWeights,
                    baseRirByLocalId = loadData.baseRirByLocalId,
                    baseIntensityByLocalId = loadData.baseIntensityByLocalId,
                    rirByScenario = loadData.rirByScenario,
                    intensityByScenario = loadData.intensityByScenario,
                    baseRirBySetId = loadData.baseRirBySetId,
                    baseIntensityBySetId = loadData.baseIntensityBySetId,
                    rirByScenarioAndSet = loadData.rirByScenarioAndSet,
                    intensityByScenarioAndSet = loadData.intensityByScenarioAndSet,
                    rirPolicy = rirPolicy,
                    badmintonHistory = badmintonAtCutoff
                )
                ResolvedPlan(
                    skeleton = resolvedSkeleton,
                    evaluation = evaluation,
                    loadData = loadData,
                    signature = BCoachMultiobjectiveProduction.fingerprint(resolvedSkeleton)
                )
            }
        }
        val resolvedBaseline = evaluatePlan(baseline)
        val rawCandidates = searchCandidates(
            baseline, preserveRestAndOrder(baseline, jointCandidate), ::evaluatePlan
        )
        val baselineSuccessfulAnchors = historyAtCutoff.filter { (it.reps ?: 0.0) > 0.0 }
            .map(V22HistoryRow::stableKey).toSet()
        val evaluated = rawCandidates.mapIndexed { index, state ->
            Candidate(
                id = "C${(index + 1).toString().padStart(2, '0')}_${state.signature.take(10)}",
                label = state.label,
                skeleton = state.skeleton,
                evaluation = state.evaluation,
                loadData = state.loadData,
                signature = state.signature,
                changes = BCoachMultiobjectiveProduction.changes(baseline, state.skeleton),
                anchorsMaintained = state.skeleton.items.map(ProgramSkeletonItem::exerciseStableKey)
                    .toSet().intersect(baselineSuccessfulAnchors).size
            )
        }
        val valid = evaluated.filter { candidate ->
            candidate.evaluation.hardConstraints.none { it.status == BCoachConstraintStatus.FAIL }
        }
        val relevantKeys = rawCandidates.flatMap { it.skeleton.items }
            .map(ProgramSkeletonItem::exerciseStableKey).distinct().sorted()
        val historyFingerprint = BCoachMultiobjectiveProduction.historyFingerprint(
            historyAtCutoff, badmintonAtCutoff
        )
        val exerciseFingerprint = BCoachMultiobjectiveProduction.exerciseFingerprint(relevantKeys, exercises)
        val linkedFingerprint = BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request)
        if (valid.isEmpty()) {
            val failed = evaluated.flatMap { it.evaluation.hardConstraints }
                .filter { it.status == BCoachConstraintStatus.FAIL }.map(BCoachConstraintResult::id)
                .distinct().sorted()
            val decision = conflictDecision(
                request, baselineFingerprint, inputFingerprint, historyFingerprint, exerciseFingerprint,
                linkedFingerprint, relevantKeys, goalWeights, failed
            )
            return Result(
                fallback = BCoachMultiobjectiveProduction.withDecisionMetadata(baseline, decision, "INPUT_CONFLICT"),
                decision = decision,
                candidatesById = emptyMap(),
                loadInstructionsByCandidateId = emptyMap(),
                recommendedCandidateId = null
            )
        }
        markPareto(valid)
        val regrets = robustRegrets(valid.associate { it.id to it.evaluation.lossesByScenario })
        valid.forEach { it.robustRegret = regrets.getValue(it.id) }
        val frontier = valid.filter(Candidate::pareto).ifEmpty { listOf(valid.minBy(Candidate::signature)) }
        val selected = frontier.sortedWith(
            compareBy<Candidate> { it.robustRegret }
                .thenBy { it.evaluation.baseLoss }
                .thenByDescending { it.anchorsMaintained }
                .thenBy { it.changes.size }
                .thenBy { it.signature }
        ).first()
        val displayed = (listOf(selected) + frontier.filter { it.id != selected.id }
            .sortedWith(compareBy<Candidate> { it.robustRegret }.thenBy { it.evaluation.baseLoss }
                .thenBy { it.signature }) +
            valid.filter { candidate -> frontier.none { it.id == candidate.id } && candidate.id != selected.id }
                .sortedWith(compareBy<Candidate> { it.robustRegret }.thenBy { it.evaluation.baseLoss }
                    .thenBy { it.signature }))
            .distinctBy(Candidate::id).take(1 + BCoachV2Policy.MAX_SHOWN_ALTERNATIVES)
        val allCandidatesHash = sha256(valid.sortedBy(Candidate::id)
            .joinToString("|") { "${it.id}:${it.signature}" })
        val proposalFingerprint = BCoachMultiobjectiveProduction.proposalFingerprint(
            baselineFingerprint = baselineFingerprint,
            candidateFingerprint = selected.signature,
            inputFingerprint = inputFingerprint,
            historyFingerprint = historyFingerprint,
            exerciseFingerprint = exerciseFingerprint,
            linkedEvidenceFingerprint = linkedFingerprint
        )
        val alternatives = displayed.map { candidate ->
            BCoachPlanAlternative(
                status = BCoachProductionStatus.USER_REVIEW_PROPOSED,
                label = candidate.label,
                weeklySetAverage = candidate.skeleton.items.sumOf {
                    ProgramSetPrescriptionResolver.resolve(it).size
                }.toDouble() / candidate.skeleton.request.durationWeeks.coerceAtLeast(1),
                changeCount = candidate.changes.size,
                candidateId = candidate.id,
                candidateFingerprint = candidate.signature,
                recommended = candidate.id == selected.id,
                axisScores = candidate.evaluation.baseAxes,
                scenarioLosses = candidate.evaluation.lossesByScenario.mapKeys { it.key.name },
                robustRegret = candidate.robustRegret,
                paretoFrontier = candidate.pareto,
                reasonCodes = candidate.evaluation.reasonCodes,
                prescriptionSummary = prescriptionSummary(
                    candidate.skeleton, candidate.loadData.instructionsBySetId
                )
            )
        }
        val baselineEvaluation = evaluated.firstOrNull { it.label == "SAFE_BASELINE" }?.evaluation
            ?: selected.evaluation
        val selectedLoad = selected.loadData
        val decision = BCoachMultiobjectiveDecision(
            engineVersion = BCoachV2Policy.ENGINE_VERSION,
            policyVersion = BCoachV2Policy.POLICY_VERSION,
            sourceOwner = BCoachV2Policy.SOURCE_OWNER,
            evidenceCutoff = request.asOf.toString(),
            status = BCoachProductionStatus.USER_REVIEW_PROPOSED,
            baselineFingerprint = baselineFingerprint,
            candidateFingerprint = selected.signature,
            proposalFingerprint = proposalFingerprint,
            inputFingerprint = inputFingerprint,
            historyFingerprint = historyFingerprint,
            exerciseFingerprint = exerciseFingerprint,
            linkedEvidenceFingerprint = linkedFingerprint,
            goalWeights = goalWeights,
            needEnvelopes = selected.evaluation.needEnvelopes,
            baselineObjectives = axes(baselineEvaluation.baseAxes),
            candidateObjectives = axes(selected.evaluation.baseAxes),
            hardConstraints = selected.evaluation.hardConstraints,
            changes = selected.changes,
            alternatives = alternatives,
            rejectedActionReasons = evaluated.filter { it !in valid }.flatMap { candidate ->
                candidate.evaluation.hardConstraints.filter { it.status == BCoachConstraintStatus.FAIL }
                    .map { "${candidate.label}:${it.id}" }
            }.distinct().sorted(),
            inputAudit = inputAudit(
                request, historyAtCutoff, relevantKeys, historyFingerprint, exerciseFingerprint,
                linkedFingerprint, goalWeights
            ),
            unknowns = selectedLoad.reasonCodes + selected.evaluation.reasonCodes,
            mostAdverseModeledScenario = selected.evaluation.lossesByScenario.maxBy { it.value }.key.name,
            fallbackReason = "EXPLICIT_USER_APPROVAL_REQUIRED",
            coefficientSource = BCoachV2Policy.COEFFICIENT_SOURCE,
            baselineAndCandidatesCanonicalHash = allCandidatesHash,
            effectiveSetBreakdown = selected.evaluation.effectiveSetBreakdown,
            paretoFrontierCandidateIds = frontier.map(Candidate::id).sorted(),
            recommendedCandidateId = selected.id,
            loadInstructions = selectedLoad.instructionsBySetId.mapValues { canonicalLoad(it.value) },
            scenarioFlip = scenarioFlip(valid)
        )
        val fallback = BCoachMultiobjectiveProduction.withDecisionMetadata(
            baseline, decision, "NOT_ACCEPTED_SAFE_FALLBACK_PERSISTED"
        )
        val candidateMap = displayed.associate { candidate ->
            candidate.id to BCoachMultiobjectiveProduction.withDecisionMetadata(
                candidate.skeleton,
                decision,
                "PROPOSED_NOT_ACCEPTED",
                chosenAlternativeId = candidate.id,
                chosenLoadInstructions = candidate.loadData.instructionsBySetId.mapValues {
                    canonicalLoad(it.value)
                }
            )
        }
        val candidateLoads = displayed.associate { candidate ->
            candidate.id to candidate.loadData.instructionsBySetId.mapValues { canonicalLoad(it.value) }
        }
        return Result(fallback, decision, candidateMap, candidateLoads, selected.id)
    }

    internal fun effectiveGoalWeights(
        requested: BBalancedGoalImportance,
        hasConfirmedHistory: Boolean,
        recommended: Map<BBalancedGoal, Int>
    ): List<BCoachGoalWeight> {
        val effective = BBalancedGoal.entries.associateWith { goal ->
            requested.value(goal) ?: if (hasConfirmedHistory) {
                recommended[goal]?.coerceIn(1, 5) ?: BCoachV2Policy.DEFAULT_GOAL_IMPORTANCE
            } else BCoachV2Policy.DEFAULT_GOAL_IMPORTANCE
        }
        val total = effective.values.sum().toDouble()
        return BBalancedGoal.entries.map { goal ->
            val raw = requested.value(goal)
            val value = effective.getValue(goal)
            BCoachGoalWeight(
                goal = goal,
                explicitImportance = value,
                normalizedImportance = value / total,
                source = when {
                    raw != null -> "USER_EXPLICIT_1_TO_5"
                    hasConfirmedHistory && goal in recommended -> "EXISTING_B_RECOMMENDED_CLAMPED_1_TO_5"
                    else -> "POLICY_DEFAULT_3_NO_HISTORY_OR_RECOMMENDATION"
                },
                effectiveImportance = value
            )
        }
    }

    internal fun robustRegrets(
        losses: Map<String, Map<BCoachV2Scenario, Double>>
    ): Map<String, Double> {
        require(losses.isNotEmpty())
        require(losses.values.all { it.keys == BCoachV2Scenario.entries.toSet() })
        val best = BCoachV2Scenario.entries.associateWith { scenario ->
            losses.values.minOf { it.getValue(scenario) }
        }
        return losses.mapValues { (_, scenarioLosses) ->
            BCoachV2Scenario.entries.maxOf { scenario ->
                (scenarioLosses.getValue(scenario) - best.getValue(scenario)).coerceAtLeast(0.0)
            }
        }
    }

    private fun searchCandidates(
        baseline: GeneratedProgramSkeleton,
        joint: GeneratedProgramSkeleton,
        evaluate: (GeneratedProgramSkeleton) -> ResolvedPlan
    ): List<SearchState> {
        val cache = linkedMapOf<String, SearchState>()
        fun register(label: String, skeleton: GeneratedProgramSkeleton): SearchState? {
            if (cache.size >= BCoachV2Policy.MAX_CANDIDATE_EVALUATIONS) return null
            val resolved = evaluate(skeleton)
            cache[resolved.signature]?.let { return null }
            return SearchState(
                label, resolved.skeleton, resolved.evaluation, resolved.loadData, resolved.signature
            ).also { cache[resolved.signature] = it }
        }
        val baselineState = register("SAFE_BASELINE", baseline)!!
        val jointState = register("JOINT_SEED", joint)
        var beam = cache.values.sortedWith(searchComparator()).take(BCoachV2Policy.SEARCH_BEAM_WIDTH)
        repeat(BCoachV2Policy.SEARCH_ROUNDS) {
            val added = mutableListOf<SearchState>()
            beam.forEach { parent ->
                searchNeighbors(parent.skeleton).forEach { (action, neighbor) ->
                    if (cache.size >= BCoachV2Policy.MAX_CANDIDATE_EVALUATIONS) return@forEach
                    register(compoundLabel(parent.label, action), neighbor)?.let(added::add)
                }
            }
            if (added.isEmpty()) return@repeat
            val valid = (beam + added).filter { state ->
                state.evaluation.hardConstraints.none { it.status == BCoachConstraintStatus.FAIL }
            }
            beam = (if (valid.isNotEmpty()) valid else beam + added)
                .distinctBy(SearchState::signature)
                .sortedWith(searchComparator())
                .take(BCoachV2Policy.SEARCH_BEAM_WIDTH)
        }
        val ranked = cache.values.filter { state ->
            state.evaluation.hardConstraints.none { it.status == BCoachConstraintStatus.FAIL }
        }.sortedWith(searchComparator())
        return (listOfNotNull(baselineState, jointState) + ranked)
            .distinctBy(SearchState::signature)
            .take(BCoachV2Policy.MAX_FINALISTS)
    }

    private fun searchComparator(): Comparator<SearchState> =
        compareBy<SearchState> { state ->
            state.evaluation.hardConstraints.count { it.status == BCoachConstraintStatus.FAIL }
        }.thenBy { it.evaluation.baseLoss }
            .thenBy { it.signature }

    private fun searchNeighbors(
        plan: GeneratedProgramSkeleton
    ): List<Pair<String, GeneratedProgramSkeleton>> = buildList {
        val keys = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct().sorted().take(6)
        keys.forEach { key ->
            add("ADD_ONE_SET_$key" to mutateSets(plan, key, +1))
            add("REMOVE_ONE_SET_$key" to mutateSets(plan, key, -1))
            add("REPS_PLUS_ONE_$key" to mutateReps(plan, key, +1))
            add("REPS_MINUS_ONE_$key" to mutateReps(plan, key, -1))
        }
    }.distinctBy { (_, skeleton) -> BCoachMultiobjectiveProduction.fingerprint(skeleton) }

    private fun compoundLabel(parent: String, action: String): String {
        val prefix = if (parent == "SAFE_BASELINE" || parent == "JOINT_SEED") parent else "BEAM"
        return "$prefix>$action"
    }

    private fun preserveRestAndOrder(
        baseline: GeneratedProgramSkeleton,
        candidate: GeneratedProgramSkeleton
    ): GeneratedProgramSkeleton {
        val fixed = baseline.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        return candidate.copy(items = candidate.items.map { item ->
            fixed[Triple(item.weekNumber, item.dayOfWeek, item.exerciseStableKey)]?.let { original ->
                item.copy(restSeconds = original.restSeconds, orderIndex = original.orderIndex)
            } ?: item
        })
    }

    private fun mutateSets(
        plan: GeneratedProgramSkeleton,
        key: String,
        delta: Int
    ): GeneratedProgramSkeleton = plan.copy(items = plan.items.map { item ->
        if (item.exerciseStableKey != key || "PEAK" in item.selectionRole) return@map item
        val old = ProgramSetPrescriptionResolver.resolve(item)
        val target = (old.size + delta).coerceIn(1, BCoachV2Policy.MAX_PER_EXERCISE_SETS)
        val sets = when {
            target > old.size -> old + List(target - old.size) { index ->
                old.last().copy(setIndex = old.size + index + 1)
            }
            else -> old.take(target).mapIndexed { index, set -> set.copy(setIndex = index + 1) }
        }
        item.copy(
            setCount = sets.size,
            reps = sets.map { it.reps }.distinct().singleOrNull() ?: 0,
            weightKg = sets.map { it.weightKg }.distinct().singleOrNull() ?: 0.0,
            seconds = sets.map { it.seconds }.distinct().singleOrNull() ?: 0,
            setPrescriptions = sets
        )
    })

    private fun mutateReps(
        plan: GeneratedProgramSkeleton,
        key: String,
        delta: Int
    ): GeneratedProgramSkeleton = plan.copy(items = plan.items.map { item ->
        if (item.exerciseStableKey != key || item.seconds > 0 || "PEAK" in item.selectionRole) return@map item
        val sets = ProgramSetPrescriptionResolver.resolve(item).map { set ->
            set.copy(reps = (set.reps + delta).coerceAtLeast(1))
        }
        item.copy(reps = sets.map { it.reps }.distinct().singleOrNull() ?: 0, setPrescriptions = sets)
    })

    private fun markPareto(candidates: List<Candidate>) {
        candidates.forEach { candidate ->
            candidate.pareto = candidates.none { other ->
                other.id != candidate.id && dominates(other.evaluation.baseAxes, candidate.evaluation.baseAxes)
            }
        }
    }

    private fun dominates(
        left: Map<BCoachObjectiveAxis, Double>,
        right: Map<BCoachObjectiveAxis, Double>
    ): Boolean = BCoachObjectiveAxis.entries.all {
        left.getValue(it) <= right.getValue(it) + BCoachV2Policy.TIE_TOLERANCE
    } && BCoachObjectiveAxis.entries.any {
        left.getValue(it) < right.getValue(it) - BCoachV2Policy.TIE_TOLERANCE
    }

    private fun axes(scores: Map<BCoachObjectiveAxis, Double>): List<BCoachAxisEvaluation> =
        BCoachObjectiveAxis.entries.map { axis ->
            BCoachAxisEvaluation(axis, listOf(BCoachObjectiveValue(
                name = "normalizedLoss",
                value = scores.getValue(axis),
                unit = "ratio_0_to_1",
                provenance = "${BCoachV2Policy.POLICY_VERSION}:${axis.name}",
                status = BCoachValueStatus.READY
            )))
        }

    private fun inputAudit(
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        relevantKeys: List<String>,
        historyFingerprint: String,
        exerciseFingerprint: String,
        linkedFingerprint: String,
        goalWeights: List<BCoachGoalWeight>
    ): Map<String, String> = linkedMapOf(
        "goalImportanceRaw" to request.bGoalImportance.toString(),
        "goalImportanceEffective" to goalWeights.joinToString(",") {
            "${it.goal.name}=${it.effectiveImportance}"
        },
        "sessionMinutesRaw" to (request.sessionMinutes?.toString() ?: "UNSET"),
        "sessionMinutesEffective" to (request.sessionMinutes ?: BCoachV2Policy.DEFAULT_SESSION_MINUTES).toString(),
        "fixedWeekdays" to (request.fixedWeekdays?.sorted()?.toString() ?: "UNSET"),
        "allowedWeekdays" to (request.allowedWeekdays?.sorted()?.toString() ?: "[1, 2, 3, 4, 5, 6, 7]"),
        "equipmentDefault" to "BODYWEIGHT_ONLY_WHEN_UNSET",
        "futureBodyweight" to "UNAVAILABLE_NOT_FABRICATED",
        "recordedHistoryRowsAtCutoff" to history.size.toString(),
        "sourceProgramStableKey" to (request.sourceProgramStableKey ?: "UNSET"),
        "linkedEvidenceApplicationId" to (request.linkedEvidenceApplicationId ?: "UNSET"),
        "plateOneSideKg" to (request.bShadowPlateOneSideKg?.toString() ?: "UNSET"),
        "dumbbellDisplayedIncrementKg" to (request.bShadowDumbbellIncrementKg?.toString() ?: "UNSET"),
        "machineDisplayedIncrementKg" to (request.bShadowMachineIncrementKg?.toString() ?: "UNSET"),
        "planningStartDate" to (request.planningStartDate?.toString() ?: "NEXT_MONDAY_AFTER_AS_OF"),
        "searchBudget" to "beam=${BCoachV2Policy.SEARCH_BEAM_WIDTH},rounds=${BCoachV2Policy.SEARCH_ROUNDS}," +
            "maxEvaluations=${BCoachV2Policy.MAX_CANDIDATE_EVALUATIONS},finalists=${BCoachV2Policy.MAX_FINALISTS}",
        "relevantExerciseKeys" to relevantKeys.joinToString(","),
        "historyFingerprint" to historyFingerprint,
        "exerciseFingerprint" to exerciseFingerprint,
        "linkedEvidenceFingerprint" to linkedFingerprint
    )

    private fun conflictDecision(
        request: V22GenerationRequest,
        baselineFingerprint: String,
        inputFingerprint: String,
        historyFingerprint: String,
        exerciseFingerprint: String,
        linkedFingerprint: String,
        relevantKeys: List<String>,
        weights: List<BCoachGoalWeight>,
        failures: List<String>
    ) = BCoachMultiobjectiveDecision(
        engineVersion = BCoachV2Policy.ENGINE_VERSION,
        policyVersion = BCoachV2Policy.POLICY_VERSION,
        sourceOwner = BCoachV2Policy.SOURCE_OWNER,
        evidenceCutoff = request.asOf.toString(),
        status = BCoachProductionStatus.INPUT_CONFLICT,
        baselineFingerprint = baselineFingerprint,
        candidateFingerprint = null,
        proposalFingerprint = null,
        inputFingerprint = inputFingerprint,
        historyFingerprint = historyFingerprint,
        exerciseFingerprint = exerciseFingerprint,
        linkedEvidenceFingerprint = linkedFingerprint,
        goalWeights = weights,
        needEnvelopes = emptyList(),
        baselineObjectives = axes(BCoachObjectiveAxis.entries.associateWith { 0.0 }),
        candidateObjectives = axes(BCoachObjectiveAxis.entries.associateWith { 0.0 }),
        hardConstraints = failures.map { BCoachConstraintResult(it, BCoachConstraintStatus.FAIL) },
        changes = emptyList(),
        alternatives = emptyList(),
        rejectedActionReasons = failures,
        inputAudit = inputAudit(
            request, emptyList(), relevantKeys, historyFingerprint, exerciseFingerprint, linkedFingerprint, weights
        ),
        unknowns = emptyList(),
        mostAdverseModeledScenario = "NOT_EVALUATED_INPUT_CONFLICT",
        fallbackReason = "INPUT_CONFLICT:${failures.joinToString(",")}",
        coefficientSource = BCoachV2Policy.COEFFICIENT_SOURCE
    )

    private fun canonicalLoad(instruction: BCoachV2LoadInstruction): String = when (instruction) {
        is BCoachV2LoadInstruction.ExactLoad ->
            "EXACT|kg=${instruction.kg}|source=${instruction.loadSource}|tier=${instruction.tier}|" +
                "targetRir=${instruction.targetRir}|freshRir=${instruction.freshRir}|workingRir=${instruction.expectedRir}|" +
                "capacityKg=${instruction.capacityKg}|conservative=${instruction.conservativeFactor}|" +
                "followUp=${instruction.followUpSetFactor}|${instruction.evidenceStatus}"
        is BCoachV2LoadInstruction.EstimatedLoad ->
            "ESTIMATED|kg=${instruction.estimatedKg}|source=${instruction.loadSource}|tier=${instruction.tier}|" +
                "targetRir=${instruction.targetRir}|freshRir=${instruction.freshRir}|workingRir=${instruction.expectedRir}|" +
                "capacityKg=${instruction.capacityKg}|equipmentGrid=REQUIRED|${instruction.evidenceStatus}"
        is BCoachV2LoadInstruction.HoldLastConfirmed ->
            "HOLD|kg=${instruction.kg}|date=${instruction.sourceDate}|${instruction.evidenceStatus}"
        is BCoachV2LoadInstruction.Calibrate -> "CALIBRATE|reason=${instruction.reason}|${instruction.evidenceStatus}"
        is BCoachV2LoadInstruction.NonLoadMetric ->
            "NON_LOAD|metric=${instruction.metric}|value=${instruction.value}|${instruction.evidenceStatus}"
    }

    private fun prescriptionSummary(
        skeleton: GeneratedProgramSkeleton,
        instructions: Map<String, BCoachV2LoadInstruction>
    ): List<String> = skeleton.items.sortedWith(
        compareBy<ProgramSkeletonItem> { it.weekNumber }
            .thenBy { it.dayOfWeek }
            .thenBy { it.orderIndex }
            .thenBy { it.exerciseStableKey }
    ).map { item ->
        val prescription = ProgramSetPrescriptionResolver.resolve(item).joinToString(" + ") { set ->
            val metric = when {
                set.seconds > 0 -> "${set.seconds}s"
                set.reps > 0 -> "${set.reps}rep"
                else -> "metric"
            }
            val load = when {
                set.weightKg > 0.0 -> "@${set.weightKg}kg"
                set.estimatedLoadKg?.let { it > 0.0 } == true -> "@~${set.estimatedLoadKg}kg(EQUIPMENT_GRID_REQUIRED)"
                else -> "@${set.loadState.name}"
            }
            val evidence = instructions[BCoachV2LoadCandidateResolver.setId(item.localId, set.setIndex)]
                ?.let(::canonicalLoad) ?: "NOT_APPLICABLE"
            "S${set.setIndex}:$metric$load{$evidence}"
        }
        "W${item.weekNumber}/D${item.dayOfWeek} ${item.exerciseStableKey}: $prescription"
    }

    private fun scenarioFlip(candidates: List<Candidate>): Boolean {
        val winners = BCoachV2Scenario.entries.map { scenario ->
            candidates.minWithOrNull(compareBy<Candidate> { it.evaluation.lossesByScenario.getValue(scenario) }
                .thenBy { it.signature })?.id
        }.toSet()
        return winners.size > 1
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
