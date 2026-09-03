package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.data.personalized.AdaptationGapAnalyzer
import com.training.trackplanner.data.personalized.AthletePlanningStateBuilder
import com.training.trackplanner.data.personalized.BadmintonPlanningIntent
import com.training.trackplanner.data.personalized.BlockIntentPlanner
import com.training.trackplanner.data.personalized.FreeWeightWillingness
import com.training.trackplanner.data.personalized.PersonalizedPlanningAnswers
import com.training.trackplanner.data.personalized.PersonalizedPlanningDecision
import com.training.trackplanner.data.personalized.PersonalizedPlanningOutcome
import com.training.trackplanner.data.personalized.PersonalizedPlanningPreferences
import com.training.trackplanner.data.personalized.PersonalizedProgramBuilder
import com.training.trackplanner.data.personalized.PlanningHistorySnapshotBuilder
import com.training.trackplanner.data.personalized.PlanningHorizonPlanner
import com.training.trackplanner.data.personalized.PlanningQuestionPolicy
import com.training.trackplanner.data.personalized.QUESTION_BADMINTON_INTENT
import com.training.trackplanner.data.personalized.QUESTION_FREE_WEIGHT
import com.training.trackplanner.data.personalized.QUESTION_STRENGTH_INTENT
import com.training.trackplanner.data.personalized.StrengthIntent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal class PersonalizedProgramPlanningService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val profileDao: InitialUserProfileDao,
    private val appMetaDao: AppMetaDao,
    private val badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
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
        cutoff: LocalDate = LocalDate.now()
    ): PersonalizedPlanningOutcome {
        val preferences = readPreferences()
        val snapshot = snapshotBuilder.build(cutoff, workoutDao.entriesWithSetsUntil(cutoff.toString()), exerciseDao.allExercises(), metadata, badmintonCatalog, profileDao.profile(), preferences)
        val state = stateBuilder.build(snapshot, answers)
        val questions = questionPolicy.questions(snapshot, state, answers)
        if (questions.isNotEmpty()) return PersonalizedPlanningOutcome.Questions(questions)
        persistAnswers(answers)
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val horizon = horizonPlanner.choose(state, gaps)
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        return PersonalizedPlanningOutcome.Generated(programBuilder.build(snapshot, state, gaps, intent, horizon, request, answers, priorId))
    }

    suspend fun persistDecision(programId: Long, decision: PersonalizedPlanningDecision) {
        val saved = decision.copy(generatedProgramId = programId)
        appMetaDao.upsert(AppMeta("$DECISION_PREFIX${saved.generatedAtEpochMillis}_${saved.decisionId}", saved.toJson()))
        appMetaDao.trimLatest("$DECISION_PREFIX%", 20)
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
        .put("userAnswers", JSONObject(userAnswers)).toString()

    companion object {
        internal const val PREFERENCES_KEY = "personalized_planning_preferences_v1"
        internal const val DECISION_PREFIX = "personalized_planning_decision_v1_"
    }
}
