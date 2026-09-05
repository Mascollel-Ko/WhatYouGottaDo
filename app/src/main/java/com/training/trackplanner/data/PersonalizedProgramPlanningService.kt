package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.readiness.TodayReadinessEngine
import com.training.trackplanner.analysis.readiness.TodayReadinessEngineInput
import com.training.trackplanner.analysis.tissue.TissueCanonicalStatus
import com.training.trackplanner.data.personalized.AdaptationGapAnalyzer
import com.training.trackplanner.data.personalized.AthletePlanningStateBuilder
import com.training.trackplanner.data.personalized.BadmintonPlanningIntent
import com.training.trackplanner.data.personalized.BlockIntentPlanner
import com.training.trackplanner.data.personalized.FreeWeightWillingness
import com.training.trackplanner.data.personalized.PersonalizedPlanningAnswers
import com.training.trackplanner.data.personalized.PersonalizedGenerationConstraints
import com.training.trackplanner.data.personalized.PersonalizedPlanningDecision
import com.training.trackplanner.data.personalized.PersonalizedPlanningOutcome
import com.training.trackplanner.data.personalized.PersonalizedPlanningPreflight
import com.training.trackplanner.data.personalized.PersonalizedPlanningQuestion
import com.training.trackplanner.data.personalized.PersonalizedPlanningPreferences
import com.training.trackplanner.data.personalized.CanonicalStrengthSignal
import com.training.trackplanner.data.personalized.PlanningRecoverySignals
import com.training.trackplanner.data.personalized.PersonalizedProgramBuilder
import com.training.trackplanner.data.personalized.PlanningHistorySnapshotBuilder
import com.training.trackplanner.data.personalized.PlanningHorizonPlanner
import com.training.trackplanner.data.personalized.WeeklyDosePlanner
import com.training.trackplanner.data.personalized.PlanningQuestionPolicy
import com.training.trackplanner.data.personalized.QUESTION_BADMINTON_INTENT
import com.training.trackplanner.data.personalized.QUESTION_FREE_WEIGHT
import com.training.trackplanner.data.personalized.QUESTION_STRENGTH_INTENT
import com.training.trackplanner.data.personalized.StrengthIntent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.exp

internal class PersonalizedProgramPlanningService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val profileDao: InitialUserProfileDao,
    private val appMetaDao: AppMetaDao,
    private val badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val strengthPerformanceRegistry: com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry,
    private val canonicalOfiAxisProfiles: Map<String, CanonicalOfiAxisProfile>,
    private val exerciseRoleRelationDao: ExerciseRoleRelationDao? = null,
    private val tissueStateProvider: suspend (LocalDate) -> com.training.trackplanner.analysis.tissue.TissueCurrentState? = { null },
    private val performancePrescriptions: Map<String, com.training.trackplanner.data.personalized.PerformancePrescriptionAuthority> = emptyMap(),
    private val snapshotBuilder: PlanningHistorySnapshotBuilder = PlanningHistorySnapshotBuilder(),
    private val stateBuilder: AthletePlanningStateBuilder = AthletePlanningStateBuilder(),
    private val questionPolicy: PlanningQuestionPolicy = PlanningQuestionPolicy(),
    private val gapAnalyzer: AdaptationGapAnalyzer = AdaptationGapAnalyzer(),
    private val blockPlanner: BlockIntentPlanner = BlockIntentPlanner(),
    private val horizonPlanner: PlanningHorizonPlanner = PlanningHorizonPlanner(),
    private val programBuilder: PersonalizedProgramBuilder = PersonalizedProgramBuilder()
) {
    suspend fun prepare(
        request: ProgramSkeletonRequest,
        metadata: Map<String, RuntimeExerciseMetadata>,
        cutoff: LocalDate = LocalDate.now(),
        constraints: PersonalizedGenerationConstraints = PersonalizedGenerationConstraints(explicitSessionMinutes = request.sessionMinutes)
    ): PersonalizedPlanningPreflight {
        val preferences = readPreferences()
        val snapshot = buildSnapshot(cutoff, metadata, preferences)
        val state = stateBuilder.build(snapshot, PersonalizedPlanningAnswers())
        return PersonalizedPlanningPreflight(
            preparationId = UUID.randomUUID().toString(),
            cutoff = cutoff,
            request = request,
            constraints = constraints,
            questions = questionPolicy.questions(snapshot, state, PersonalizedPlanningAnswers()),
            preparedAtEpochMillis = System.currentTimeMillis()
        )
    }

    suspend fun generatePrepared(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>
    ): GeneratedProgramSkeleton {
        val missingAnswers = preflight.questions.filter { question ->
            question.options.none { it.value == answers.values[question.id] && it.value != "UNRESOLVED" }
        }.map(PersonalizedPlanningQuestion::id)
        require(missingAnswers.isEmpty()) { "사전 확인 답변이 누락됐습니다: ${missingAnswers.joinToString()}" }
        val preferences = readPreferences()
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED && state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        persistAnswers(answers, snapshot.profilePrimaryGoal)
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val recommendedDays = WeeklyDosePlanner().chooseDays(state, state.anchors.size + gaps.size)
        val recommendedHorizon = horizonPlanner.choose(state, gaps, intent)
        val constraints = preflight.constraints
        val personalizedRequest = resolvePersonalizedRequest(preflight.request, constraints, state.programGoal, recommendedDays, recommendedHorizon)
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        return programBuilder.build(snapshot, state, gaps, intent, personalizedRequest.durationWeeks, personalizedRequest, answers, priorId)
    }

    /** Compatibility wrapper for callers that have not yet adopted the two-phase API. */
    suspend fun generate(
        request: ProgramSkeletonRequest,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        cutoff: LocalDate = LocalDate.now(),
        constraints: PersonalizedGenerationConstraints = PersonalizedGenerationConstraints(explicitSessionMinutes = request.sessionMinutes)
    ): PersonalizedPlanningOutcome {
        val preflight = prepare(request, metadata, cutoff, constraints)
        val unanswered = preflight.questions.filter { question ->
            question.options.none { it.value == answers.values[question.id] && it.value != "UNRESOLVED" }
        }
        return if (unanswered.isNotEmpty()) PersonalizedPlanningOutcome.Questions(unanswered)
        else PersonalizedPlanningOutcome.Generated(generatePrepared(preflight, answers, metadata))
    }

    private suspend fun buildSnapshot(
        cutoff: LocalDate,
        metadata: Map<String, RuntimeExerciseMetadata>,
        preferences: PersonalizedPlanningPreferences
    ): com.training.trackplanner.data.personalized.PlanningHistorySnapshot {
        val history = workoutDao.entriesWithSetsUntil(cutoff.toString())
        val exercises = exerciseDao.allExercises()
        val profile = profileDao.profile()
        val dailyMetrics = dailyMetricDao.metricsUntil(cutoff.toString())
        val checkIns = dailyCheckInDao.between(cutoff.minusDays(13).toString(), cutoff.toString())
        val canonicalStrength = canonicalStrengthSignals(cutoff)
        val runtimeCatalog = RuntimeExerciseMetadataCatalog.of(metadata.values)
        val readiness = TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(cutoff, exercises, history, dailyMetrics, checkIns, profile, runtimeCatalog)
        )
        val revision = strengthPosteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
            ?.takeIf { it.status == StrengthModelRevisionPolicy.STATUS_ACTIVE && StrengthModelRevisionPolicy.isCompatible(it) }
        val posteriorHistory = revision?.let { strengthPosteriorDao.historyForRevision(it.revisionKey) }.orEmpty()
        val ofi = DailyFatigueCalculator(
            runtimeCatalog,
            canonicalOfiAxisProfiles,
            dailyCanonicalStrengthPosterior(posteriorHistory, strengthPerformanceRegistry)
        ).calculate(cutoff, exercises, history, profile, dailyMetrics).state.overallFatigueIndex
        val tissue = tissueStateProvider(cutoff)
        val restrictedStableKeys = tissue?.loadUnits.orEmpty()
            .filter { it.status in setOf(TissueCanonicalStatus.HIGH, TissueCanonicalStatus.VERY_HIGH) }
            .flatMap { it.contributors.map { contributor -> contributor.exerciseStableKey } }
            .toSet()
        val recovery = PlanningRecoverySignals(
            readinessStatus = readiness.status.name,
            readinessConfidence = readiness.confidence.name,
            overallFatigueIndex = ofi,
            restrictedModes = readiness.restrictedModes.toSet(),
            tissueStatus = tissue?.ofiSummary?.status?.name ?: "UNKNOWN",
            tissueRestrictedStableKeys = restrictedStableKeys,
            sourceCodes = buildSet {
                add("CANONICAL_OFI")
                add("TODAY_READINESS")
                if (tissue != null) add("TISSUE_RCV")
                if (canonicalStrength.isNotEmpty()) add("STRENGTH_POSTERIOR")
            }
        )
        val roleCatalog = exerciseRoleRelationDao?.let { dao ->
            ExerciseRoleRelationCatalog.of(dao.allTrainingRoles(), dao.allProgramSlotCapabilities())
        } ?: ExerciseRoleRelationCatalog.EMPTY
        val snapshot = snapshotBuilder.build(cutoff, history, exercises, metadata, badmintonCatalog, profile, preferences, canonicalStrength, recovery, roleCatalog)
        return snapshot.copy(performancePrescriptions = performancePrescriptions)
    }

    private suspend fun canonicalStrengthSignals(cutoff: LocalDate): Map<String, CanonicalStrengthSignal> {
        val revision = strengthPosteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
            ?.takeIf { it.status == StrengthModelRevisionPolicy.STATUS_ACTIVE && StrengthModelRevisionPolicy.isCompatible(it) }
            ?: return emptyMap()
        return canonicalStrengthSignalsForWindow(strengthPosteriorDao.localHistory(revision.revisionKey), cutoff, revision.revisionKey)
    }

    suspend fun persistDecision(programId: Long, programStableKey: String, decision: PersonalizedPlanningDecision, finalFingerprint: String) {
        val saved = decision.copy(
            generatedProgramId = programId,
            generatedProgramStableKey = programStableKey,
            finalSavedFingerprint = finalFingerprint,
            userEditedAfterGeneration = isPersonalizedProgramEdited(decision, finalFingerprint)
        )
        appMetaDao.upsert(AppMeta("$DECISION_PREFIX${saved.generatedAtEpochMillis}_${saved.decisionId}", saved.toJson()))
        appMetaDao.trimLatest("$DECISION_PREFIX%", 20)
    }

    suspend fun markProgramEdited(programId: Long, finalFingerprint: String) {
        val row = appMetaDao.latestByPrefix("$DECISION_PREFIX%", 20).firstOrNull { candidate ->
            runCatching { JSONObject(candidate.value).optLong("generatedProgramId", Long.MIN_VALUE) == programId }.getOrDefault(false)
        } ?: return
        val json = runCatching { JSONObject(row.value) }.getOrNull() ?: return
        json.put("userEditedAfterGeneration", true).put("finalSavedFingerprint", finalFingerprint)
        appMetaDao.upsert(row.copy(value = json.toString()))
    }

    private suspend fun readPreferences(): PersonalizedPlanningPreferences {
        val json = appMetaDao.value(PREFERENCES_KEY)?.let(::JSONObject) ?: return PersonalizedPlanningPreferences()
        return PersonalizedPlanningPreferences(
            strengthIntent = json.optString("strengthIntent").takeIf(String::isNotBlank)?.let { runCatching { StrengthIntent.valueOf(it) }.getOrNull() },
            badmintonIntent = json.optString("badmintonIntent").takeIf(String::isNotBlank)?.let { runCatching { BadmintonPlanningIntent.valueOf(it) }.getOrNull() },
            freeWeightWillingness = json.optString("freeWeightWillingness").takeIf(String::isNotBlank)?.let { runCatching { FreeWeightWillingness.valueOf(it) }.getOrNull() },
            strengthIntentAnsweredAtEpochMillis = json.optLong("strengthIntentAnsweredAtEpochMillis", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
            strengthIntentProfileGoal = json.optString("strengthIntentProfileGoal").takeIf(String::isNotBlank)
        )
    }

    private suspend fun persistAnswers(answers: PersonalizedPlanningAnswers, profileGoal: String) {
        if (answers.values.isEmpty()) return
        val old = readPreferences()
        val next = old.copy(
            strengthIntent = answers.values[QUESTION_STRENGTH_INTENT]?.let { StrengthIntent.valueOf(it) } ?: old.strengthIntent,
            badmintonIntent = answers.values[QUESTION_BADMINTON_INTENT]?.let { BadmintonPlanningIntent.valueOf(it) } ?: old.badmintonIntent,
            freeWeightWillingness = answers.values[QUESTION_FREE_WEIGHT]?.let { FreeWeightWillingness.valueOf(it) } ?: old.freeWeightWillingness,
            strengthIntentAnsweredAtEpochMillis = if (QUESTION_STRENGTH_INTENT in answers.values) System.currentTimeMillis() else old.strengthIntentAnsweredAtEpochMillis,
            strengthIntentProfileGoal = if (QUESTION_STRENGTH_INTENT in answers.values) profileGoal else old.strengthIntentProfileGoal
        )
        appMetaDao.upsert(AppMeta(PREFERENCES_KEY, JSONObject()
            .put("strengthIntent", next.strengthIntent?.name.orEmpty())
            .put("strengthIntentAnsweredAtEpochMillis", next.strengthIntentAnsweredAtEpochMillis)
            .put("strengthIntentProfileGoal", next.strengthIntentProfileGoal.orEmpty())
            .put("badmintonIntent", next.badmintonIntent?.name.orEmpty())
            .put("freeWeightWillingness", next.freeWeightWillingness?.name.orEmpty()).toString()))
    }

    private fun decisionIdFromJson(value: String): String? = runCatching { JSONObject(value).optString("decisionId").takeIf(String::isNotBlank) }.getOrNull()

    private fun PersonalizedPlanningDecision.toJson(): String = JSONObject()
        .put("decisionId", decisionId).put("protocolVersion", protocolVersion).put("generatedProgramId", generatedProgramId)
        .put("generatedAtEpochMillis", generatedAtEpochMillis).put("historyCutoff", historyCutoff).put("historyWindowDays", historyWindowDays)
        .put("planningHorizonWeeks", planningHorizonWeeks).put("adaptationIntentMinWeeks", adaptationIntentMinWeeks).put("adaptationIntentMaxWeeks", adaptationIntentMaxWeeks)
        .put("observedTrainingBehavior", observedTrainingBehavior).put("strengthIntent", strengthIntent).put("strengthIntentProvenance", strengthIntentProvenance)
        .put("badmintonIntent", badmintonIntent).put("badmintonIntentProvenance", badmintonIntentProvenance).put("primaryAdaptation", primaryAdaptation)
        .put("secondaryTargets", JSONArray(secondaryTargets)).put("strengthStyle", strengthStyle).put("strengthStyleProvenance", strengthStyleProvenance)
        .put("weeklyFrequency", weeklyFrequency).put("confidence", confidence).put("reasonCodes", JSONArray(reasonCodes)).put("reasons", JSONArray(reasons))
        .put("constraints", JSONArray(constraints)).put("metadataAuthorityVersion", metadataAuthorityVersion).put("priorDecisionId", priorDecisionId)
        .put("userAnswers", JSONObject(userAnswers)).put("generatedProgramStableKey", generatedProgramStableKey)
        .put("originalGenerationFingerprint", originalGenerationFingerprint).put("userEditedAfterGeneration", userEditedAfterGeneration)
        .put("finalSavedFingerprint", finalSavedFingerprint).put("recoverySignalCodes", JSONArray(recoverySignalCodes))
        .put("genericCourtLoad", genericCourtLoad).put("objectiveExposure", JSONObject(objectiveExposure))
        .put("anchorTransitions", JSONArray(anchorTransitions.map { transition -> JSONObject()
            .put("stableKey", transition.stableKey)
            .put("observedStyle", transition.observedStyle.name)
            .put("structureTreatment", transition.structureTreatment.name)
            .put("doseTreatment", transition.doseTreatment.name)
            .put("continuityScore", transition.continuityScore)
            .put("localDoseFactor", transition.localDoseFactor)
            .put("preservedFeatures", JSONArray(transition.preservedFeatures))
            .put("moderatedFeatures", JSONArray(transition.moderatedFeatures))
        }))
        .put("planningBudget", planningBudget?.let { budget -> JSONObject()
            .put("baselineResistanceSets", budget.baselineResistanceSets)
            .put("targetResistanceSets", budget.targetResistanceSets)
            .put("plannedResistanceSets", budget.plannedResistanceSets)
            .put("targetStructuredBadmintonBouts", budget.targetStructuredBadmintonBouts)
            .put("plannedStructuredBadmintonBouts", budget.plannedStructuredBadmintonBouts)
            .put("targetAthleticPerformanceBouts", budget.targetAthleticPerformanceBouts)
            .put("plannedAthleticPerformanceBouts", budget.plannedAthleticPerformanceBouts)
            .put("systemicDoseFactor", budget.systemicDoseFactor)
            .put("execution", budget.execution?.toJson())
        })
        .put("movementRepresentations", JSONArray(movementRepresentations.map { value -> JSONObject()
            .put("movementCoverage", value.movementCoverage)
            .put("basePriority", value.basePriority.name)
            .put("currentExposure28d", value.currentExposure28d)
            .put("priorExposure28d", value.priorExposure28d)
            .put("currentActiveBins", value.currentActiveBins)
            .put("currentShare", value.currentShare)
            .put("priorShare", value.priorShare)
            .put("peerReference", value.peerReference)
            .put("peerRepresentationRatio", value.peerRepresentationRatio)
            .put("personalRetentionRatio", value.personalRetentionRatio)
            .put("representationState", value.representationState.name)
            .put("evidenceConfidence", value.evidenceConfidence.name)
            .put("reasonCodes", JSONArray(value.reasonCodes))
        }))
        .put("badmintonObjectiveRepresentations", JSONArray(badmintonObjectiveRepresentations.map { value -> JSONObject()
            .put("objective", value.objective)
            .put("currentWeighted28d", value.currentWeighted28d)
            .put("priorWeighted28d", value.priorWeighted28d)
            .put("currentDirect28d", value.currentDirect28d)
            .put("priorDirect28d", value.priorDirect28d)
            .put("currentShare", value.currentShare)
            .put("priorShare", value.priorShare)
            .put("personalRetentionRatio", value.personalRetentionRatio)
            .put("peerMedianCurrent", value.peerMedianCurrent)
            .put("peerRepresentationRatio", value.peerRepresentationRatio)
            .put("currentActiveBins", value.currentActiveBins)
            .put("evidenceConfidence", value.evidenceConfidence.name)
            .put("directDrop", value.directDrop)
            .put("neverDirectObserved", value.neverDirectObserved)
            .put("representationState", value.representationState.name)
            .put("reasonCodes", JSONArray(value.reasonCodes))
        }))
        .put("adaptationGaps", JSONArray(adaptationGaps.map { gap -> JSONObject()
            .put("code", gap.code)
            .put("priority", gap.priority)
            .put("sourceType", gap.sourceType)
            .put("representationState", gap.representationState?.name)
            .put("evidenceConfidence", gap.evidenceConfidence?.name)
            .put("currentExposure", gap.currentExposure)
            .put("priorExposure", gap.priorExposure)
            .put("currentShare", gap.currentShare)
            .put("priorShare", gap.priorShare)
            .put("peerRatio", gap.peerRatio)
            .put("personalRetentionRatio", gap.personalRetentionRatio)
            .put("reasonCodes", JSONArray(gap.reasonCodes))
            .put("contributesTransitionPressure", gap.contributesTransitionPressure)
        })).toString()

    companion object {
        internal const val PREFERENCES_KEY = "personalized_planning_preferences_v1"
        internal const val DECISION_PREFIX = "personalized_planning_decision_v1_"
    }
}

internal fun isPersonalizedProgramEdited(decision: PersonalizedPlanningDecision, finalFingerprint: String): Boolean =
    decision.originalGenerationFingerprint.isNotBlank() && decision.originalGenerationFingerprint != finalFingerprint

internal fun resolvePersonalizedRequest(
    request: ProgramSkeletonRequest,
    constraints: PersonalizedGenerationConstraints,
    inferredGoal: ProgramGoal,
    recommendedDays: Int,
    recommendedHorizon: Int
): ProgramSkeletonRequest = request.copy(
    goal = constraints.explicitGoal ?: inferredGoal,
    weeklyTrainingDays = (constraints.explicitWeeklyTrainingDays ?: recommendedDays).coerceIn(2, 5),
    durationWeeks = (constraints.explicitDurationWeeks ?: recommendedHorizon).coerceIn(2, 6),
    sessionMinutes = constraints.explicitSessionMinutes ?: request.sessionMinutes,
    badmintonTransferRatio = 0.0,
    sportStrengthRatio = "AUTO"
)

internal fun canonicalStrengthSignalsForWindow(
    rows: List<StrengthExercisePerformanceHistoryEntity>,
    cutoff: LocalDate,
    revisionKey: String
): Map<String, CanonicalStrengthSignal> = rows
    .filter { runCatching { LocalDate.parse(it.sessionDate) }.getOrNull()?.let { date -> !date.isAfter(cutoff) && !date.isBefore(cutoff.minusDays(55)) } == true }
    .groupBy(StrengthExercisePerformanceHistoryEntity::exerciseStableKey)
    .mapValues { (_, exerciseRows) ->
        val ordered = exerciseRows.sortedWith(compareBy(StrengthExercisePerformanceHistoryEntity::sessionDate, StrengthExercisePerformanceHistoryEntity::createdAt))
        val last = exp(ordered.last().posteriorLogMean)
        val first = ordered.takeIf { it.size >= 2 }?.first()?.let { exp(it.posteriorLogMean) }
        CanonicalStrengthSignal(
            posteriorMedianKg = last,
            posteriorChangePercent = first?.takeIf { it > 0.0 }?.let { (last / it - 1.0) * 100.0 },
            observationCount = ordered.size,
            source = "CANONICAL_EXERCISE_LOCAL_POSTERIOR:$revisionKey"
        )
    }
