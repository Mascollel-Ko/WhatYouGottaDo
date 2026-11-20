package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgramLoadState
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
        val recommendedCandidateId: String?,
        val sportShadowReport: BCoachSportShadowReport? = null,
        val shadowCandidatesById: Map<String, GeneratedProgramSkeleton> = emptyMap()
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
        val inputFingerprint = BCoachMultiobjectiveProduction.inputFingerprint(
            request, historyAtCutoff, badmintonAtCutoff
        )
        val referenceCapacityByStableKey = BCoachV2LoadCandidateResolver.referenceCapacityByStableKey(
            request, historyAtCutoff, modelSnapshots, rirPolicy
        )
        val loadEvidenceFingerprint = sha256(modelSnapshots.toSortedMap().entries.joinToString("|") {
            (key, model) -> "$key:${model.sourceVersion}:${model.evidenceIds.sorted()}:${model.stale}"
        } + "|rirPolicy=${rirPolicy?.version ?: "NONE"}|badminton=" +
            BCoachMultiobjectiveProduction.historyFingerprint(emptyList(), badmintonAtCutoff) +
            "|referenceCapacity=" + referenceCapacityByStableKey.toSortedMap())
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
                    badmintonHistory = badmintonAtCutoff,
                    referenceCapacityByStableKey = referenceCapacityByStableKey
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
        // The visible safe fallback and the approval baseline must be the same resolved plan.
        // Otherwise cold-start estimated loads disappear as 0 kg and approval compares against
        // a skeleton the user was never shown.
        val baselineFingerprint = resolvedBaseline.signature
        val rawCandidates = searchCandidates(
            baseline, preserveRestAndOrder(baseline, jointCandidate),
            mainExerciseKeys, request, exercises, badmintonCatalog,
            muscleRelations, coreCatalog, goalWeights, ::evaluatePlan
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
        fun sportCoverageCount(candidate: Candidate): Int =
            candidate.evaluation.sportObjectiveWeeks.groupBy { it.objective }
                .count { (_, rows) -> rows.any { it.dose > 0.0 } }
        fun sportDosePatternCount(candidate: Candidate): Int =
            candidate.evaluation.sportObjectiveWeeks.groupBy { it.week }.values
                .map { rows -> rows.sortedBy { it.objective }
                    .joinToString("|") { "${it.objective}:${it.dose}" } }
                .distinct().size
        val conventional = frontier.sortedWith(
            compareBy<Candidate> { it.robustRegret }
                .thenBy { it.evaluation.baseLoss }
                .thenByDescending { it.anchorsMaintained }
                .thenBy { it.changes.size }
                .thenBy { it.signature }
        ).first()
        // One common multiobjective ranking for every importance vector.
        // Nine-domain exposure and weekly roster variety are diagnostics, not
        // substitute objectives that can override strength, recovery or stability.
        val selected = conventional
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
                ),
                changeSummary = candidate.changes
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
            ) + mapOf(
                "sportObjectivesSelected" to sportCoverageCount(selected).toString(),
                "sportObjectivesMaxFeasible" to valid.maxOf(::sportCoverageCount).toString(),
                "sportDosePatternsSelected" to sportDosePatternCount(selected).toString(),
                "sportDosePatternsMaxFeasible" to valid.maxOf(::sportDosePatternCount).toString()
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
            resolvedBaseline.skeleton, decision, "NOT_ACCEPTED_SAFE_FALLBACK_PERSISTED"
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
        // The shadow reads already-evaluated, feasible candidates. It cannot change
        // the production selected ID, approval fingerprints, fallback or stored plan.
        val sportShadow = runCatching {
            BCoachSportShadowReview.evaluate(
                input = valid.map { candidate -> BCoachSportShadowReview.Input(
                    candidate.id, candidate.skeleton, candidate.evaluation
                ) },
                productionCandidateId = selected.id,
                catalog = badmintonCatalog,
                exercises = exercises
            )
        }.getOrElse { error ->
            BCoachSportShadowReport(
                selected.id, null, emptyList(), emptyList(), valid.size,
                error = error.javaClass.simpleName
            )
        }
        return Result(fallback, decision, candidateMap, candidateLoads, selected.id,
            sportShadow, valid.associate { it.id to it.skeleton })
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
        mainExerciseKeys: Set<String>,
        request: V22GenerationRequest,
        exercises: Map<String, Exercise>,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        goalWeights: List<BCoachGoalWeight>,
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
                searchNeighbors(parent.skeleton, parent.evaluation, mainExerciseKeys,
                    request, exercises, badmintonCatalog, muscleRelations, coreCatalog, goalWeights)
                    .forEach { (action, neighbor) ->
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

    /**
     * Search across the reviewed metadata roster, not merely the six stableKeys
     * already present in a program. New exercise loads are deliberately cleared;
     * the normal resolver must re-estimate them from that exercise's own evidence.
     */
    internal fun rosterNeighbors(
        plan: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        exercises: Map<String, Exercise>,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        mainExerciseKeys: Set<String>,
        sportObjectiveWeeks: List<BCoachSportShadowWeek> = emptyList()
    ): List<Pair<String, GeneratedProgramSkeleton>> {
        if (plan.items.isEmpty()) return emptyList()
        val selected = plan.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey)
        val equipment = request.availableEquipment?.flatMap { value ->
            value.split(Regex("[,|;/+&]")).map(String::trim).filter(String::isNotEmpty)
        }?.map(String::uppercase)?.toSet()
        fun permitted(exercise: Exercise): Boolean {
            val tokens = exercise.equipment.split(Regex("[,|;/+&]"))
                .map(String::trim).filter(String::isNotEmpty).map(String::uppercase)
            if (tokens.isEmpty() || tokens.all { it in setOf("BODYWEIGHT", "맨몸") }) return true
            return equipment != null && (tokens.all { it in equipment } ||
                exercise.equipment.uppercase() in request.availableEquipment.orEmpty().map(String::uppercase))
        }
        fun reviewed(exercise: Exercise): Boolean =
            exercise.isActive && !exercise.needsReview && !exercise.isCustom &&
                exercise.planningEligibility != "HISTORY_ONLY" &&
                exercise.metadataConfidence.uppercase() !in
                    setOf("", "UNKNOWN", "UNREVIEWED", "REVIEW_REQUIRED") &&
                !BCoachV2Policy.isTestOnlyExercise(exercise) &&
                permitted(exercise)
        val eligible = exercises.values.filter(::reviewed).filter {
            it.stableKey !in selected && it.stableKey !in mainExerciseKeys &&
                it.activityKind != "SPORT_SESSION"
        }
        val objectiveCoverage = selected.flatMap { key ->
            badmintonCatalog.relations(key).filter { it.transferLevel.coefficient > 0.0 }
                .map { it.objective.name }
        }.toSet()
        fun skillTags(exercise: Exercise): Set<String> =
            exercise.badmintonSkillTargets.split(Regex("[,|;]"))
                .map(String::trim).filter(String::isNotEmpty).toSet()
        val skillCoverage = selected.flatMap { key ->
            exercises[key]?.let(::skillTags).orEmpty()
        }.toSet()
        // Prioritize still-unserved targets using the evaluator's actual
        // week-by-week deficit, not a binary all-horizon coverage flag.
        val gaps = sportObjectiveWeeks.groupBy { it.objective }.mapValues { (_, weeks) ->
            weeks.map { week ->
                if (week.lower <= 0.0) 0.0
                else ((week.lower - week.dose) / week.lower).coerceIn(0.0, 1.0)
            }.average()
        }
        // Some of the nine reviewed objectives have no DIRECT relation.
        // JUMP_LANDING, for example, has only two SUPPORTIVE exercises in
        // the authoritative catalog. Never discard them from recruitment.
        val availabilityByObjective = exercises.values.asSequence().filter(::reviewed)
            .flatMap { exercise ->
                badmintonCatalog.relations(exercise.stableKey).asSequence()
                    .filter { it.transferLevel.coefficient > 0.0 }
                    .map { it.objective.name }
            }.groupingBy { it }.eachCount()
        fun needGain(exercise: Exercise): Double = badmintonCatalog.relations(exercise.stableKey)
            .groupBy { it.objective.name }
            .values.sumOf { matches ->
                val best = matches.maxOf { it.transferLevel.coefficient }
                val objective = matches.first().objective.name
                val scarcity = 1.0 + 4.0 / (availabilityByObjective[objective] ?: 1).coerceAtLeast(1)
                best * scarcity *
                    (gaps[objective] ?: if (objective !in objectiveCoverage) 1.0 else 0.0)
            }
        val rank = compareByDescending<Exercise> { needGain(it) }
            .thenByDescending { e ->
                badmintonCatalog.relations(e.stableKey).count {
                    it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT &&
                        it.objective.name !in objectiveCoverage
                }
            }.thenByDescending { e -> (skillTags(e) - skillCoverage).size }
            .thenBy(Exercise::stableKey)

        fun eligibleSource(item: ProgramSkeletonItem): Boolean =
            item.exerciseStableKey !in mainExerciseKeys &&
                !item.selectionRole.contains("MAIN", ignoreCase = true) &&
                !BCoachV2DoseEvaluator.isExplicitUserExerciseLock(item.selectionReason) &&
                !item.weightSource.contains("USER_FIXED", ignoreCase = true) &&
                ProgramSetPrescriptionResolver.resolve(item).none {
                    BCoachV2LoadCandidateResolver.isSetUserFixed(it, item.weightSource)
                }

        fun knownSeconds(source: ProgramSkeletonItem): Int =
            ProgramSetPrescriptionResolver.resolve(source)
                .firstOrNull { it.seconds > 0 }?.seconds ?: source.seconds.coerceAtLeast(0)

        fun reselectedItem(
            source: ProgramSkeletonItem, exercise: Exercise,
            count: Int = source.setCount.coerceAtLeast(2)
        ): ProgramSkeletonItem {
            val timed = exercise.progressMetricType in setOf(
                "TIME_OR_DISTANCE", "DISTANCE_OR_TIME_LOAD", "DURATION"
            )
            val reps = if (timed) 0 else source.reps.takeIf { it in 4..10 } ?: 6
            val seconds = if (timed) knownSeconds(source) else 0
            val sets = List(count.coerceIn(2, BCoachV2Policy.MAX_PER_EXERCISE_SETS)) { index ->
                ProgramSetPrescription(
                    setIndex = index + 1, reps = reps, weightKg = 0.0, seconds = seconds,
                    loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                    loadSource = ProgramLoadSource.CALIBRATION_REQUIRED
                )
            }
            return source.copy(
                localId = source.localId + "_B_RESELECT_" + exercise.stableKey,
                exerciseStableKey = exercise.stableKey,
                stableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = exercise.defaultRestSeconds,
                prescription = "B_COACH_RESELECTED_USER_REVIEW",
                setCount = sets.size, reps = reps, weightKg = 0.0, seconds = seconds,
                selectionReason = "B_COACH_GLOBAL_ROSTER_RESELECTED",
                weightSource = "B_COACH_RESELECT_USER_CALIBRATION_REQUIRED",
                trainingSlot = "FULL_BODY_BADMINTON_SUPPORT",
                selectionRole = if (badmintonCatalog.relations(exercise.stableKey).isNotEmpty())
                    "SPORT_SUPPORT_RESELECTED" else "ACCESSORY_RESELECTED",
                movementFamily = exercise.movementPattern,
                movementSubtype = "", metadataProgramSlot = "", redundancyGroup = "",
                strengthProgressionGroup = exercise.strengthProgressionGroup,
                primaryStressProfile = "", stressMagnitudeHint = "",
                neuromuscularStressLevel = "", systemicMuscularStressLevel = "",
                localMuscularStressLevel = "", jointTendonImpactStressLevel = "",
                movementFocusDemandLevel = "", recoveryDurationClass = "",
                badmintonTransferLevel = "", estimatedDurationSeconds = 0,
                directSportSession = false, rehabLikeActivation = false,
                scapularStabilityExposure = false,
                primarySlotCapabilities = emptyList(),
                secondarySlotCapabilities = emptyList(), weakSlotCapabilities = emptyList(),
                slotCapabilitySource = "NONE", slotCapabilityConfidence = "NONE",
                slotCapabilityWarnings = emptyList(), requestedTemplateSlot = "",
                requiredTemplateAnchor = false, setPrescriptions = sets,
                taskProtocolSemanticsJson = null, progressionStyle = "",
                progressionVariant = "", progressionAnchorSetIndex = null,
                progressionRole = ProgressionRole.AUTO, progressionBinding = null
            )
        }

        val changes = mutableListOf<Pair<String, GeneratedProgramSkeleton>>()
        val mutableKeys = selected.filter { key ->
            plan.items.filter { it.exerciseStableKey == key }.all(::eligibleSource)
        }
        for (key in mutableKeys.take(4)) {
            val original = exercises[key] ?: continue
            val isSport = badmintonCatalog.relations(key).isNotEmpty()
            val alternatives = eligible.asSequence().filter { candidate ->
                candidate.category == original.category &&
                    candidate.progressMetricType == original.progressMetricType &&
                    (candidate.progressMetricType !in setOf(
                        "TIME_OR_DISTANCE", "DISTANCE_OR_TIME_LOAD", "DURATION"
                    ) || plan.items.filter { it.exerciseStableKey == key }.all {
                        knownSeconds(it) > 0
                    }) &&
                    (if (isSport) badmintonCatalog.relations(candidate.stableKey).isNotEmpty()
                    else candidate.movementPattern.isNotBlank() &&
                        candidate.movementPattern == original.movementPattern)
            }.sortedWith(rank).take(3).toList()
            for (candidate in alternatives) {
                val revised = plan.copy(items = plan.items.map { item ->
                    if (item.exerciseStableKey == key) reselectedItem(item, candidate) else item
                })
                changes += "REPLACE_${key}_WITH_${candidate.stableKey}" to revised
            }
        }
        // An underfilled day must be able to gain a second accessory rather
        // than only replacing one. Insert consistently into each existing week.
        val firstWeek = plan.items.minOf(ProgramSkeletonItem::weekNumber)
        val day = plan.items.filter { it.weekNumber == firstWeek }
            .groupBy(ProgramSkeletonItem::dayOfWeek)
            .minWithOrNull(compareBy<Map.Entry<Int, List<ProgramSkeletonItem>>> {
                BCoachV2ScheduleLoad.timeUpper(it.value, exercises)
            }.thenBy { it.key })?.key
        if (day != null) {
            val additions = eligible.asSequence()
                .filter { badmintonCatalog.relations(it.stableKey).isNotEmpty() &&
                    it.progressMetricType == "QUALITY_BASED" }
                .sortedWith(rank).take(2).toList()
            for (candidate in additions) {
                val next = plan.items.toMutableList()
                for ((week, items) in plan.items.groupBy(ProgramSkeletonItem::weekNumber)) {
                    val sameDay = items.filter { it.dayOfWeek == day }
                    val source = sameDay.firstOrNull() ?: continue
                    val insertion = reselectedItem(source, candidate, count = 2).copy(
                        localId = "B_RESELECT_W${week}_D${day}_${candidate.stableKey}",
                        orderIndex = (sameDay.maxOfOrNull(ProgramSkeletonItem::orderIndex) ?: 0) + 1
                    )
                    if (BCoachV2ScheduleLoad.timeUpper(sameDay + insertion, exercises) <=
                        (request.sessionMinutes ?: BCoachV2Policy.DEFAULT_SESSION_MINUTES)) {
                        next += insertion
                    }
                }
                if (next.size > plan.items.size) {
                    changes += "INSERT_${candidate.stableKey}_ON_DAY_$day" to
                        plan.copy(items = next)
                }
            }
        }
        return changes.distinctBy { (_, candidate) ->
            BCoachMultiobjectiveProduction.fingerprint(candidate)
        }
    }

    /**
     * Candidate-independent task identities and candidate-specific doses are structured
     * evaluator outputs. Rank mutation opportunities by the deficits and excesses
     * that an exercise is actually capable of addressing. This is search ordering,
     * not a claim that an exercise improves measured sporting performance.
     */
    internal fun mutationPriorities(
        plan: GeneratedProgramSkeleton,
        evaluation: BCoachV2CandidateEvaluation,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        goalWeights: List<BCoachGoalWeight>
    ): Map<String, Pair<Double, Double>> {
        val needs = evaluation.taskWeekDoses.filter(BCoachV2TaskWeekDose::scored)
            .groupBy(BCoachV2TaskWeekDose::taskId)
        val gaps = needs.mapValues { (_, rows) ->
            val deficit = rows.map { row ->
                (row.lower - row.dose).coerceAtLeast(0.0) /
                    row.lower.coerceAtLeast(1.0)
            }.average()
            val excess = rows.map { row ->
                (row.dose - row.upper).coerceAtLeast(0.0) /
                    row.upper.coerceAtLeast(1.0)
            }.average()
            deficit.coerceIn(0.0, 1.0) to excess.coerceIn(0.0, 1.0)
        }
        val weights = goalWeights.associate { it.goal to
            (it.normalizedImportance ?: 0.0).coerceAtLeast(0.0) }
        val musclesByKey = muscleRelations.filter { it.domain == CanonicalRelationDomain.MUSCLE &&
            it.relationType in setOf("PRIMARY", "SECONDARY") }
            .groupBy(CanonicalMetadataRelation::exerciseStableKey)
        return plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
            .associateWith { key ->
                val links = linkedMapOf<String, Pair<BBalancedGoal, Double>>()
                if (key in CanonicalStrengthExposureCapability.approvedStableKeys) {
                    links["STRENGTH:$key"] = BBalancedGoal.STRENGTH to 1.0
                }
                musclesByKey[key].orEmpty().forEach { relation ->
                    val task = "HYPERTROPHY:${relation.relationValue}"
                    val credit = if (relation.relationType == "PRIMARY") 1.0 else 0.5
                    val previous = links[task]?.second ?: 0.0
                    links[task] = BBalancedGoal.HYPERTROPHY to maxOf(previous, credit)
                }
                if (coreCatalog.resolve(key)?.coreClass == CoreClass.DIRECT) {
                    links["CORE:DIRECT"] = BBalancedGoal.CORE to 1.0
                }
                val sport = badmintonCatalog.relations(key)
                    .filter { it.transferLevel.coefficient > 0.0 }
                val general = sport.maxOfOrNull { it.transferLevel.coefficient } ?: 0.0
                if (general > 0.0) links["SPORT:GENERAL"] = BBalancedGoal.SPORT_SUPPORT to general
                sport.forEach { relation ->
                    val task = "SPORT:${relation.objective.name}"
                    val previous = links[task]?.second ?: 0.0
                    links[task] = BBalancedGoal.SPORT_SUPPORT to
                        maxOf(previous, relation.transferLevel.coefficient)
                }
                links.entries.fold(0.0 to 0.0) { scores, (task, value) ->
                    val (deficit, excess) = gaps[task] ?: (0.0 to 0.0)
                    val weight = weights[value.first] ?: 0.0
                    scores.first + deficit * value.second * weight to
                        scores.second + excess * value.second * weight
                }
            }
    }

    private fun searchComparator(): Comparator<SearchState> =
        compareBy<SearchState> { state ->
            state.evaluation.hardConstraints.count { it.status == BCoachConstraintStatus.FAIL }
        }.thenBy { it.evaluation.lossesByScenario.values.maxOrNull() ?: Double.POSITIVE_INFINITY }
            .thenBy { it.evaluation.baseLoss }
            .thenBy { it.signature }

    private fun searchNeighbors(
        plan: GeneratedProgramSkeleton,
        evaluation: BCoachV2CandidateEvaluation,
        mainExerciseKeys: Set<String>,
        request: V22GenerationRequest,
        exercises: Map<String, Exercise>,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        goalWeights: List<BCoachGoalWeight>
    ): List<Pair<String, GeneratedProgramSkeleton>> = buildList {
        // Offer roster mutations first. A bounded on-device search must not
        // consume its entire evaluation budget on one-set/repetition tweaks.
        addAll(rosterNeighbors(plan, request, exercises, badmintonCatalog,
            mainExerciseKeys, evaluation.sportObjectiveWeeks))
        val priorities = mutationPriorities(plan, evaluation, muscleRelations,
            coreCatalog, badmintonCatalog, goalWeights)
        val uniqueKeys = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
        val mains = uniqueKeys.filter { key -> key in mainExerciseKeys ||
            plan.items.any { it.exerciseStableKey == key && it.selectionRole.contains("MAIN", true) } }
        val accessories = uniqueKeys - mains.toSet()
        // Rank by actual observed candidate deficit/excess, never by stableKey order.
        // Keep separate main/accessory lanes so the finite beam explores both.
        val ranked = compareByDescending<String> { priorities[it]?.first?.plus(priorities[it]?.second ?: 0.0) ?: 0.0 }
            .thenBy { it }
        val keys = (mains.sortedWith(ranked).take(3) +
            accessories.sortedWith(ranked).take(5)).distinct()
        keys.forEach { key ->
            add("ADD_ONE_SET_$key" to mutateSets(plan, key, +1, mainExerciseKeys))
            add("REMOVE_ONE_SET_$key" to mutateSets(plan, key, -1, mainExerciseKeys))
            add("REPS_PLUS_ONE_$key" to mutateReps(plan, key, +1))
            add("REPS_MINUS_ONE_$key" to mutateReps(plan, key, -1))
        }
        // A coach may redistribute an existing set instead of increasing total load.
        // The ordinary evaluator still enforces recovery, duration and manual locks.
        val receiver = keys.filter { (priorities[it]?.first ?: 0.0) > 0.0 }
            .maxWithOrNull(compareBy<String> { priorities[it]?.first ?: 0.0 }.thenBy { it })
        val donor = keys.filter { it != receiver && (priorities[it]?.second ?: 0.0) > 0.0 }
            .maxWithOrNull(compareBy<String> { priorities[it]?.second ?: 0.0 }.thenBy { it })
        if (receiver != null && donor != null) {
            val reduced = mutateSets(plan, donor, -1, mainExerciseKeys)
            if (BCoachMultiobjectiveProduction.fingerprint(reduced) !=
                BCoachMultiobjectiveProduction.fingerprint(plan)) {
                val reallocated = mutateSets(reduced, receiver, +1, mainExerciseKeys)
                if (BCoachMultiobjectiveProduction.fingerprint(reallocated) !=
                    BCoachMultiobjectiveProduction.fingerprint(reduced)) {
                    add("REDISTRIBUTE_SET_${donor}_TO_${receiver}" to reallocated)
                }
            }
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
        val normalized = candidate.items.groupBy { it.weekNumber to it.dayOfWeek }
            .toSortedMap(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
            .values.flatMap { sessionItems ->
                sessionItems.map { item ->
                    fixed[Triple(item.weekNumber, item.dayOfWeek, item.exerciseStableKey)]?.let { original ->
                        item.copy(restSeconds = original.restSeconds)
                    } ?: item
                }.sortedWith(compareBy<ProgramSkeletonItem> { item ->
                    fixed[Triple(item.weekNumber, item.dayOfWeek, item.exerciseStableKey)]?.orderIndex
                        ?: item.orderIndex
                }.thenBy(ProgramSkeletonItem::orderIndex)
                    .thenBy(ProgramSkeletonItem::exerciseStableKey))
                    .mapIndexed { index, item -> item.copy(orderIndex = index + 1) }
            }
        return candidate.copy(items = normalized)
    }

    internal fun mutateSets(
        plan: GeneratedProgramSkeleton,
        key: String,
        delta: Int,
        mainExerciseKeys: Set<String> = emptySet()
    ): GeneratedProgramSkeleton = plan.copy(items = plan.items.map { item ->
        if (item.exerciseStableKey != key || "PEAK" in item.selectionRole ||
            BCoachV2DoseEvaluator.isExplicitUserExerciseLock(item.selectionReason)) return@map item
        val old = ProgramSetPrescriptionResolver.resolve(item)
        val exception = item.seconds > 0 || item.directSportSession || item.rehabLikeActivation ||
            listOf("RECOVERY", "MOBILITY", "TEST", "DRILL").any {
                it in (item.category + " " + item.selectionRole).uppercase()
            }
        val minimum = when {
            exception -> 1
            key in mainExerciseKeys || item.selectionRole.contains("MAIN", ignoreCase = true) -> 3
            else -> 2
        }
        val target = (old.size + delta).coerceIn(minimum, BCoachV2Policy.MAX_PER_EXERCISE_SETS)
        if (target < old.size && old.drop(target).any {
                BCoachV2LoadCandidateResolver.isSetUserFixed(it, item.weightSource)
            }) return@map item
        val sets = when {
            target > old.size -> old + List(target - old.size) { index ->
                old.last().copy(
                    setIndex = old.size + index + 1,
                    weightKg = 0.0,
                    loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                    estimatedLoadKg = null,
                    loadSource = ProgramLoadSource.CALIBRATION_REQUIRED,
                    freshRir = null,
                    expectedRir = null,
                    relativeIntensity = null,
                    evidenceVersion = null
                )
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

    internal fun mutateReps(
        plan: GeneratedProgramSkeleton,
        key: String,
        delta: Int
    ): GeneratedProgramSkeleton = plan.copy(items = plan.items.map { item ->
        if (item.exerciseStableKey != key || item.seconds > 0 || "PEAK" in item.selectionRole ||
            BCoachV2DoseEvaluator.isExplicitUserExerciseLock(item.selectionReason)) return@map item
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
