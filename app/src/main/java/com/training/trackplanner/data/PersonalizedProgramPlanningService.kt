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
    private val tissueStateProvider: suspend (LocalDate) -> com.training.trackplanner.analysis.tissue.TissueCurrentState? = { null },
    private val snapshotBuilder: PlanningHistorySnapshotBuilder = PlanningHistorySnapshotBuilder(),
    private val stateBuilder: AthletePlanningStateBuilder = AthletePlanningStateBuilder(),
    private val questionPolicy: PlanningQuestionPolicy = PlanningQuestionPolicy(),
    private val gapAnalyzer: AdaptationGapAnalyzer = AdaptationGapAnalyzer(),
    private val blockPlanner: BlockIntentPlanner = BlockIntentPlanner(),
    private val horizonPlanner: PlanningHorizonPlanner = PlanningHorizonPlanner(),
    private val programBuilder: PersonalizedProgramBuilder = PersonalizedProgramBuilder()
) {
    suspend fun generate(
        request: ProgramSkeletonRequest,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        cutoff: LocalDate = LocalDate.now(),
        constraints: PersonalizedGenerationConstraints = PersonalizedGenerationConstraints(
            explicitGoal = request.goal,
            explicitWeeklyTrainingDays = request.weeklyTrainingDays,
            explicitDurationWeeks = request.durationWeeks,
            explicitSessionMinutes = request.sessionMinutes
        )
    ): PersonalizedPlanningOutcome {
        val preferences = readPreferences()
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
        val snapshot = snapshotBuilder.build(cutoff, history, exercises, metadata, badmintonCatalog, profile, preferences, canonicalStrength, recovery)
        val state = stateBuilder.build(snapshot, answers)
        val questions = questionPolicy.questions(snapshot, state, answers).take(1)
        if (questions.isNotEmpty()) return PersonalizedPlanningOutcome.Questions(questions)
        persistAnswers(answers)
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val recommendedDays = WeeklyDosePlanner().chooseDays(state, state.anchors.size + gaps.size)
        val recommendedHorizon = horizonPlanner.choose(state, gaps)
        val personalizedRequest = request.copy(
            goal = constraints.explicitGoal ?: state.programGoal,
            weeklyTrainingDays = (constraints.explicitWeeklyTrainingDays ?: recommendedDays).coerceIn(2, 5),
            durationWeeks = (constraints.explicitDurationWeeks ?: recommendedHorizon).coerceIn(2, 6),
            sessionMinutes = constraints.explicitSessionMinutes ?: request.sessionMinutes
        )
        val horizon = personalizedRequest.durationWeeks
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        return PersonalizedPlanningOutcome.Generated(programBuilder.build(snapshot, state, gaps, intent, horizon, personalizedRequest, answers, priorId))
    }

    private suspend fun canonicalStrengthSignals(cutoff: LocalDate): Map<String, CanonicalStrengthSignal> {
        val revision = strengthPosteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
            ?.takeIf { it.status == StrengthModelRevisionPolicy.STATUS_ACTIVE && StrengthModelRevisionPolicy.isCompatible(it) }
            ?: return emptyMap()
        return strengthPosteriorDao.localHistory(revision.revisionKey)
            .filter { runCatching { LocalDate.parse(it.sessionDate) }.getOrNull()?.let { date -> !date.isAfter(cutoff) } == true }
            .groupBy(StrengthExercisePerformanceHistoryEntity::exerciseStableKey)
            .mapValues { (_, rows) ->
                val ordered = rows.sortedWith(compareBy(StrengthExercisePerformanceHistoryEntity::sessionDate, StrengthExercisePerformanceHistoryEntity::createdAt))
                val first = exp(ordered.first().posteriorLogMean)
                val last = exp(ordered.last().posteriorLogMean)
                CanonicalStrengthSignal(
                    posteriorMedianKg = last,
                    posteriorChangePercent = if (first > 0.0) (last / first - 1.0) * 100.0 else null,
                    observationCount = ordered.size,
                    source = "CANONICAL_EXERCISE_LOCAL_POSTERIOR:${revision.revisionKey}"
                )
            }
    }

    suspend fun persistDecision(programId: Long, programStableKey: String, decision: PersonalizedPlanningDecision, finalFingerprint: String) {
        val saved = decision.copy(
            generatedProgramId = programId,
            generatedProgramStableKey = programStableKey,
            finalSavedFingerprint = finalFingerprint,
            userEditedAfterGeneration = decision.originalGenerationFingerprint.isNotBlank() && decision.originalGenerationFingerprint != finalFingerprint
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
            freeWeightWillingness = json.optString("freeWeightWillingness").takeIf(String::isNotBlank)?.let { runCatching { FreeWeightWillingness.valueOf(it) }.getOrNull() }
        )
    }

    private suspend fun persistAnswers(answers: PersonalizedPlanningAnswers) {
        if (answers.values.isEmpty()) return
        val old = readPreferences()
        val next = old.copy(
            strengthIntent = answers.values[QUESTION_STRENGTH_INTENT]?.let { StrengthIntent.valueOf(it) } ?: old.strengthIntent,
            badmintonIntent = answers.values[QUESTION_BADMINTON_INTENT]?.let { BadmintonPlanningIntent.valueOf(it) } ?: old.badmintonIntent,
            freeWeightWillingness = answers.values[QUESTION_FREE_WEIGHT]?.let { FreeWeightWillingness.valueOf(it) } ?: old.freeWeightWillingness
        )
        appMetaDao.upsert(AppMeta(PREFERENCES_KEY, JSONObject().put("strengthIntent", next.strengthIntent?.name.orEmpty()).put("badmintonIntent", next.badmintonIntent?.name.orEmpty()).put("freeWeightWillingness", next.freeWeightWillingness?.name.orEmpty()).toString()))
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
        .put("genericCourtLoad", genericCourtLoad).put("objectiveExposure", JSONObject(objectiveExposure)).toString()

    companion object {
        internal const val PREFERENCES_KEY = "personalized_planning_preferences_v1"
        internal const val DECISION_PREFIX = "personalized_planning_decision_v1_"
    }
}
