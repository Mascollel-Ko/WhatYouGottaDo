package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
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
import com.training.trackplanner.data.personalized.CanonicalStimulusPlanningComputation
import com.training.trackplanner.data.personalized.PlanningRecoverySignals
import com.training.trackplanner.data.personalized.PersonalizedProgramBuilder
import com.training.trackplanner.data.personalized.PersonalizedPlannerProgressReporter
import com.training.trackplanner.data.personalized.PersonalizedPlannerStage
import com.training.trackplanner.data.personalized.PlanningHistorySnapshotBuilder
import com.training.trackplanner.data.personalized.PlanningHistorySnapshot
import com.training.trackplanner.data.personalized.AthleteNeedsProfileEngine
import com.training.trackplanner.data.personalized.AthleteStimulusNeedEngine
import com.training.trackplanner.data.personalized.FinalStimulusNeedAudit
import com.training.trackplanner.data.personalized.StimulusExperimentalReadinessAuditEngine
import com.training.trackplanner.data.personalized.toCompactJson
import com.training.trackplanner.data.personalized.QualityDoseHistoryAnalyzer
import com.training.trackplanner.data.personalized.LedgerBackedQualityDoseHistoryAnalyzer
import com.training.trackplanner.data.personalized.qualityDoseHistoryHorizon
import com.training.trackplanner.data.personalized.TargetPlanComparisonEngine
import com.training.trackplanner.data.personalized.TargetStimulusPlanEngine
import com.training.trackplanner.data.personalized.TrainingDecisionPortfolioEngine
import com.training.trackplanner.data.personalized.StimulusTrainingDecisionPortfolioEngine
import com.training.trackplanner.data.personalized.StimulusTrainingDecisionPortfolioComparisonEngine
import com.training.trackplanner.data.personalized.StimulusTargetPlanEngine
import com.training.trackplanner.data.personalized.StimulusTargetPlanComparisonEngine
import com.training.trackplanner.data.personalized.StimulusTargetControlProgramAuditEngine
import com.training.trackplanner.data.personalized.StimulusTargetCandidateSelector
import com.training.trackplanner.data.personalized.StimulusSelectionProgramComparison
import com.training.trackplanner.data.personalized.StimulusSelectionProgramComparisonEngine
import com.training.trackplanner.data.personalized.StimulusPrescriptionRealizationPlanEngine
import com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity
import com.training.trackplanner.data.personalized.PlannedPrescription
import com.training.trackplanner.data.personalized.StimulusPrescriptionAuthorizationEngine
import com.training.trackplanner.data.personalized.StimulusPrescriptionMaterializationAuditEngine
import com.training.trackplanner.data.personalized.CanonicalStimulusPlanningResult
import com.training.trackplanner.data.personalized.CanonicalPreparedProgram
import com.training.trackplanner.data.personalized.CanonicalPlanningOutcome
import com.training.trackplanner.data.personalized.NeedRelevance
import com.training.trackplanner.data.personalized.RegionalBottleneckDiagnosisEngine
import com.training.trackplanner.data.personalized.RegionalEvidenceIndexBuilder
import com.training.trackplanner.data.personalized.ProgramEmphasisProjector
import com.training.trackplanner.data.personalized.RegionalStrengthRequirementResolver
import com.training.trackplanner.data.personalized.RegionalPlanningAuthorityMode
import com.training.trackplanner.data.personalized.MovementCoverage
import com.training.trackplanner.data.personalized.RegionalTrainingDecisionResolver
import com.training.trackplanner.data.personalized.RegionalStimulusTargetResolver
import com.training.trackplanner.data.personalized.RegionalExperimentalMaterialDemandBuilder
import com.training.trackplanner.data.personalized.RegionalAuthorityProgramComparison
import com.training.trackplanner.data.personalized.movementCoverage
import com.training.trackplanner.data.personalized.toJson
import com.training.trackplanner.data.personalized.PlanningHorizonPlanner
import com.training.trackplanner.data.personalized.WeeklyDosePlanner
import com.training.trackplanner.data.personalized.PlanningQuestionPolicy
import com.training.trackplanner.data.personalized.QUESTION_BADMINTON_INTENT
import com.training.trackplanner.data.personalized.QUESTION_FREE_WEIGHT
import com.training.trackplanner.data.personalized.QUESTION_STRENGTH_INTENT
import com.training.trackplanner.data.personalized.StrengthIntent
import com.training.trackplanner.data.personalized.PlanningDailyStrain
import com.training.trackplanner.data.personalized.InterruptionCause
import com.training.trackplanner.data.personalized.InterruptionFrequency
import com.training.trackplanner.data.personalized.QUESTION_INTERRUPTION_CAUSE
import com.training.trackplanner.data.personalized.QUESTION_INTERRUPTION_FREQUENCY
import com.training.trackplanner.data.personalized.WeeklyContextAnnotationJson
import com.training.trackplanner.data.personalized.weekAnnotations
import com.training.trackplanner.data.personalized.completedTrainingWeekEnd
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.exp
import kotlinx.coroutines.CancellationException
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceLoadResolver
import com.training.trackplanner.analysis.strengthperformance.toPosterior
import com.training.trackplanner.data.personalized.CanonicalStrengthReferenceIndex
import com.training.trackplanner.data.personalized.StimulusCanonicalEvaluationFailure
import com.training.trackplanner.data.personalized.StimulusCanonicalEvaluationFailureReason
import com.training.trackplanner.data.personalized.PreparedCanonicalGenerationContext
import com.training.trackplanner.data.personalized.CanonicalExperimentalGeneration
import com.training.trackplanner.data.personalized.ProductionGenerationPhase
import com.training.trackplanner.data.personalized.ProductionGenerationObservation

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
    private val repetitionCurveRegistry: com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry,
    private val rpeRirPolicy: com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy,
    private val canonicalOfiAxisProfiles: Map<String, CanonicalOfiAxisProfile>,
    private val exerciseRoleRelationDao: ExerciseRoleRelationDao? = null,
    private val tissueStateProvider: suspend (LocalDate) -> com.training.trackplanner.analysis.tissue.TissueCurrentState? = { null },
    private val tissueProjectionProvider: suspend (LocalDate) -> com.training.trackplanner.data.personalized.PlanWeekTissueProjection? = { null },
    private val performancePrescriptions: Map<String, com.training.trackplanner.data.personalized.PerformancePrescriptionAuthority> = emptyMap(),
    private val physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.EMPTY,
    private val canonicalMovementRelations: List<CanonicalMetadataRelation> = emptyList(),
    private val canonicalCoreCatalog: CanonicalCoreCatalog = CanonicalCoreCatalog.EMPTY,
    private val reviewedCanonicalStableKeys: Set<String> = emptySet(),
    private val athleteNeedsProfileEngine: AthleteNeedsProfileEngine = AthleteNeedsProfileEngine(),
    private val athleteStimulusNeedEngine: AthleteStimulusNeedEngine = AthleteStimulusNeedEngine(),
    private val snapshotBuilder: PlanningHistorySnapshotBuilder = PlanningHistorySnapshotBuilder(),
    private val stateBuilder: AthletePlanningStateBuilder = AthletePlanningStateBuilder(),
    private val questionPolicy: PlanningQuestionPolicy = PlanningQuestionPolicy(),
    private val gapAnalyzer: AdaptationGapAnalyzer = AdaptationGapAnalyzer(),
    private val blockPlanner: BlockIntentPlanner = BlockIntentPlanner(),
    private val horizonPlanner: PlanningHorizonPlanner = PlanningHorizonPlanner(),
    private val programBuilder: PersonalizedProgramBuilder = PersonalizedProgramBuilder(),
    private val persistUserState: suspend (suspend () -> Unit) -> Unit = { it() }
) {
    suspend fun prepare(
        request: ProgramSkeletonRequest,
        metadata: Map<String, RuntimeExerciseMetadata>,
        cutoff: LocalDate = LocalDate.now(),
        constraints: PersonalizedGenerationConstraints = PersonalizedGenerationConstraints(explicitSessionMinutes = request.sessionMinutes),
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): PersonalizedPlanningPreflight {
        progress.report(PersonalizedPlannerStage.HISTORY)
        val preferences = readPreferences()
        val snapshot = buildSnapshot(cutoff, metadata, preferences, includeStimulusExposureLedger = false)
        progress.report(PersonalizedPlannerStage.PATTERNS)
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
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): GeneratedProgramSkeleton {
        // This compatibility wrapper preserves the frozen preflight.cutoff and
        // preflight.constraints/answers contract for existing callers.  Canonical
        // planning is calculated once by the typed result below.
        val prepared = generatePreparedWithCanonicalPlanning(preflight, answers, metadata, progress)
        prepared.canonicalPlanning // Preserve propagation for callers outside production routing.
        return prepared.program
    }

    /** Compatibility API returning a paired CONTROL/B1-B4 result; production uses the C8 prepared context. */
    internal suspend fun generatePreparedWithCanonicalPlanning(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        canonicalPlanningComputation: CanonicalStimulusPlanningComputation = ::buildCanonicalStimulusPlanningResult
    ): CanonicalPreparedProgram {
        progress.report(PersonalizedPlannerStage.INPUT)
        val missingAnswers = preflight.questions.filter { question ->
            question.options.none { it.value == answers.values[question.id] && it.value != "UNRESOLVED" }
        }.map(PersonalizedPlanningQuestion::id)
        require(missingAnswers.isEmpty()) { "사전 확인 답변이 누락됐습니다: ${missingAnswers.joinToString()}" }
        progress.report(PersonalizedPlannerStage.HISTORY)
        val preferences = readPreferences()
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        progress.report(PersonalizedPlannerStage.PATTERNS)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED && state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        persistAnswers(answers, snapshot.profilePrimaryGoal)
        progress.report(PersonalizedPlannerStage.ADAPTATION)
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val frequencyEvidence = WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size)
        val resolvedProgramRequest = resolvePreparedProgramRequest(preflight, state, gaps, intent, frequencyEvidence)
        val constraints = preflight.constraints
        val personalizedRequest = resolvedProgramRequest.request
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        val generated = programBuilder.build(snapshot, state, gaps, intent, personalizedRequest.durationWeeks, personalizedRequest, answers, priorId,
            explicitWeeklyDays = constraints.explicitWeeklyTrainingDays != null,
            frequency = resolvedProgramRequest.frequencyProvenance, progress = progress)
        progress.report(PersonalizedPlannerStage.FINAL)
        val legacyNeeds = athleteNeedsProfileEngine.analyze(snapshot, state, physicalQualityCatalog)
        val doseHistoryAnalyzer = QualityDoseHistoryAnalyzer()
        val doseHistory = doseHistoryAnalyzer.analyze(snapshot, state, physicalQualityCatalog)
        // This compatibility API still returns a CONTROL skeleton alongside B1-B4. Normal
        // production uses prepareCanonicalGenerationContext and completes B1-B6 before CONTROL.
        val canonicalPlanning = try {
            canonicalPlanningComputation(snapshot, state, doseHistory)
        } catch (failure: StimulusCanonicalEvaluationFailure) {
            return CanonicalPreparedProgram(
                com.training.trackplanner.data.personalized.bindSplitParentProgression(generated),
                com.training.trackplanner.data.personalized.CanonicalPlanningOutcome.ExpectedFailure(failure),
                resolvedProgramRequest
            )
        }
        if (generated.request != personalizedRequest) {
            val failure = StimulusCanonicalEvaluationFailure(
                StimulusCanonicalEvaluationFailureReason.RESOLVED_REQUEST_PARITY,
                detailCode = "CONTROL_REQUEST_DIFFERS_FROM_RESOLVED_REQUEST"
            )
            return CanonicalPreparedProgram(
                com.training.trackplanner.data.personalized.bindSplitParentProgression(generated),
                com.training.trackplanner.data.personalized.CanonicalPlanningOutcome.ExpectedFailure(failure),
                resolvedProgramRequest
            )
        }
        val finalStimulusAudit = FinalStimulusNeedAudit().audit(generated, snapshot, physicalQualityCatalog)
        val stimulusNeeds = canonicalPlanning.athleteStimulusNeedProfile
        val ledgerDoseHistory = canonicalPlanning.qualityDoseHistory
        val stimulusPortfolio = canonicalPlanning.decisionPortfolio
        val stimulusTargetPlan = canonicalPlanning.targetPlan
        val canonicalWithControlAudit = canonicalPlanning.withControlProgramAudit(
            StimulusTargetControlProgramAuditEngine().audit(
                stimulusTargetPlan,
                finalStimulusAudit,
                personalizedRequest.durationWeeks
            )
        )
        val stimulusTargetPlanWithAudit = canonicalWithControlAudit.targetPlanCompatibilityMirror()
        val legacyPortfolio = TrainingDecisionPortfolioEngine().build(legacyNeeds, doseHistory)
        val stimulusPortfolioComparison = StimulusTrainingDecisionPortfolioComparisonEngine()
            .compare(legacyPortfolio, stimulusPortfolio, ledgerDoseHistory, doseHistory)
        val stimulusPortfolioWithComparison = stimulusPortfolio.copy(comparison = stimulusPortfolioComparison)
        val withShadowNeeds = generated.copy(
            personalizedDecision = generated.personalizedDecision?.copy(
                athleteNeedsProfile = legacyNeeds,
                athleteStimulusNeedProfile = stimulusNeeds.copy(
                    finalAudit = finalStimulusAudit,
                    qualityDoseHistoryShadow = ledgerDoseHistory,
                    trainingDecisionPortfolioShadow = stimulusPortfolioWithComparison,
                    stimulusTargetPlanShadow = stimulusTargetPlanWithAudit
                )
            )
        )
        val decision = withShadowNeeds.personalizedDecision
        val needs = decision?.athleteNeedsProfile
        if (decision != null && needs != null) {
            val portfolio = legacyPortfolio
            val targetPlan = TargetStimulusPlanEngine().build(portfolio, doseHistory)
            val stimulusTargetComparison = StimulusTargetPlanComparisonEngine()
                .compare(targetPlan, stimulusTargetPlanWithAudit)
            val withTargetComparison = withShadowNeeds.copy(
                personalizedDecision = withShadowNeeds.personalizedDecision?.copy(
                    athleteStimulusNeedProfile = withShadowNeeds.personalizedDecision?.athleteStimulusNeedProfile?.copy(
                        stimulusTargetPlanShadow = stimulusTargetPlanWithAudit.copy(
                            legacyComparison = stimulusTargetComparison
                        )
                    )
                )
            )
            val comparison = TargetPlanComparisonEngine().compare(targetPlan, withShadowNeeds, snapshot, physicalQualityCatalog)
            val regionalIndex = RegionalEvidenceIndexBuilder().build(snapshot, state, physicalQualityCatalog)
            val strengthRequirement = needs.qualityNeeds.firstOrNull { it.quality == com.training.trackplanner.data.TrainableQuality.STRENGTH }?.relevance
                ?: NeedRelevance.UNKNOWN
            val regionalRequirements = RegionalStrengthRequirementResolver().resolve(
                strengthRequirement,
                state.movementRepresentations
            )
            val localizedTissue = snapshot.recoverySignals.tissueRestrictedStableKeys
                .map(snapshot::movementCoverage)
                .filter { it != com.training.trackplanner.data.personalized.MovementCoverage.OTHER }
                .toSet()
            val systemicRecovery = (
                snapshot.recoverySignals.readinessStatus in setOf("CAUTION", "FATIGUED", "LIMITED") ||
                    (snapshot.recoverySignals.overallFatigueIndex ?: 0) >= 70 ||
                    state.trainingStateAssessment?.globalHardRestriction == true ||
                    (snapshot.recoverySignals.tissueStatus in setOf("VERY_HIGH", "BLOCKED") && localizedTissue.isEmpty())
                )
            val lowerSportRegions = setOf(
                com.training.trackplanner.data.personalized.MovementCoverage.LOWER_KNEE,
                com.training.trackplanner.data.personalized.MovementCoverage.POSTERIOR_CHAIN,
                com.training.trackplanner.data.personalized.MovementCoverage.CALVES
            )
            val sportInterference = state.courtDeviation > 0.0 && state.lowerNegativeEvidence > 0.0 && state.courtInterference > 0.0
            val regionalDiagnosis = RegionalBottleneckDiagnosisEngine().analyze(
                regionalIndex,
                regionalRequirements,
                systemicRecovery,
                sportInterference,
                localizedTissue.associateWith { true },
                lowerSportRegions
            )
            val programEmphasis = ProgramEmphasisProjector().project(withShadowNeeds, snapshot, physicalQualityCatalog)
            return CanonicalPreparedProgram(com.training.trackplanner.data.personalized.bindSplitParentProgression(withTargetComparison.copy(
                personalizedDecision = withTargetComparison.personalizedDecision!!.copy(
                    trainingDecisionPortfolio = portfolio,
                    targetStimulusPlan = targetPlan,
                    targetPlanComparison = comparison,
                    regionalBottleneckDiagnosis = regionalDiagnosis,
                    programEmphasisLabels = programEmphasis
                )
            )), canonicalWithControlAudit, resolvedProgramRequest)
        }
        return CanonicalPreparedProgram(com.training.trackplanner.data.personalized.bindSplitParentProgression(withShadowNeeds), canonicalWithControlAudit,
            resolvedProgramRequest)
    }

    private fun resolvePreparedProgramRequest(
        preflight: PersonalizedPlanningPreflight,
        state: com.training.trackplanner.data.personalized.AthletePlanningState,
        gaps: List<com.training.trackplanner.data.personalized.AdaptationGap>,
        intent: com.training.trackplanner.data.personalized.BlockIntent,
        frequencyEvidence: com.training.trackplanner.data.personalized.WeeklyFrequencyEvidence
    ): com.training.trackplanner.data.personalized.ResolvedPreparedProgramRequest {
        val request = resolvePersonalizedRequest(
            preflight.request,
            preflight.constraints,
            state.programGoal,
            frequencyEvidence.recommendedDays,
            horizonPlanner.choose(state, gaps, intent)
        )
        return com.training.trackplanner.data.personalized.ResolvedPreparedProgramRequest(
            request = request,
            frequencyProvenance = com.training.trackplanner.data.personalized.PlanningFrequencyProvenance(
                recommendation = frequencyEvidence,
                resolvedUserDays = request.weeklyTrainingDays,
                source = if (preflight.constraints.explicitWeeklyTrainingDays != null)
                    com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER
                else com.training.trackplanner.data.personalized.PlanningFrequencySource.AUTO
            )
        )
    }

    /** Computes B1/B2/B3/B4 from canonical inputs without creating a program skeleton. */
    internal fun buildCanonicalStimulusPlanningResult(
        snapshot: PlanningHistorySnapshot,
        state: com.training.trackplanner.data.personalized.AthletePlanningState,
        legacyDoseHistory: com.training.trackplanner.data.personalized.QualityDoseHistory =
            QualityDoseHistoryAnalyzer().analyze(snapshot, state, physicalQualityCatalog)
    ): CanonicalStimulusPlanningResult {
        val stimulusNeeds = athleteStimulusNeedEngine.analyze(snapshot, state)
        val ledgerDoseHistory = LedgerBackedQualityDoseHistoryAnalyzer().analyze(snapshot, state, legacyDoseHistory)
        val decisionPortfolio = StimulusTrainingDecisionPortfolioEngine().build(stimulusNeeds, ledgerDoseHistory)
        val targetPlan = StimulusTargetPlanEngine().build(decisionPortfolio, ledgerDoseHistory)
        return CanonicalStimulusPlanningResult(
            athleteStimulusNeedProfile = stimulusNeeds,
            qualityDoseHistory = ledgerDoseHistory,
            decisionPortfolio = decisionPortfolio,
            targetPlan = targetPlan
        )
    }

    /** Prepares every input shared by canonical EXPERIMENTAL and late CONTROL exactly once. */
    private suspend fun prepareCanonicalGenerationContext(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter,
        canonicalPlanningComputation: CanonicalStimulusPlanningComputation
    ): PreparedCanonicalGenerationContext {
        progress.report(PersonalizedPlannerStage.INPUT)
        val missingAnswers = preflight.questions.filter { question ->
            question.options.none { it.value == answers.values[question.id] && it.value != "UNRESOLVED" }
        }.map(PersonalizedPlanningQuestion::id)
        require(missingAnswers.isEmpty()) { "사전 확인 답변이 누락됐습니다: ${missingAnswers.joinToString()}" }

        progress.report(PersonalizedPlannerStage.HISTORY)
        val preferences = readPreferences()
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        progress.report(PersonalizedPlannerStage.PATTERNS)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED &&
            state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED
        ) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        persistAnswers(answers, snapshot.profilePrimaryGoal)

        progress.report(PersonalizedPlannerStage.ADAPTATION)
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val frequencyEvidence = WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size)
        val resolvedRequest = resolvePreparedProgramRequest(preflight, state, gaps, intent, frequencyEvidence)
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)

        // Keep the pre-C8 compatibility diagnostics independent of any generated skeleton.
        val legacyNeeds = athleteNeedsProfileEngine.analyze(snapshot, state, physicalQualityCatalog)
        val legacyDoseHistory = QualityDoseHistoryAnalyzer().analyze(snapshot, state, physicalQualityCatalog)
        val canonicalOutcome = try {
            CanonicalPlanningOutcome.Success(canonicalPlanningComputation(snapshot, state, legacyDoseHistory))
        } catch (failure: StimulusCanonicalEvaluationFailure) {
            CanonicalPlanningOutcome.ExpectedFailure(failure)
        }
        return PreparedCanonicalGenerationContext(
            snapshot = snapshot,
            state = state,
            gaps = gaps,
            intent = intent,
            resolvedRequest = resolvedRequest,
            priorDecisionId = priorId,
            legacyNeeds = legacyNeeds,
            legacyDoseHistory = legacyDoseHistory,
            canonicalPlanningOutcome = canonicalOutcome
        )
    }

    /** Runs B5 and B6, including EXPERIMENTAL post-materialization audits, without CONTROL. */
    private suspend fun buildCanonicalExperimentalGeneration(
        context: PreparedCanonicalGenerationContext,
        answers: PersonalizedPlanningAnswers,
        progress: PersonalizedPlannerProgressReporter,
        productionBuildCounts: com.training.trackplanner.data.personalized.MutableStimulusProductionBuildCounts,
        observe: (ProductionGenerationPhase) -> Unit,
        experimentalProgramBuildOverride: (suspend () -> GeneratedProgramSkeleton)?
    ): CanonicalExperimentalGeneration {
        val canonicalPlanning = (context.canonicalPlanningOutcome as? CanonicalPlanningOutcome.Success)?.result
            ?: throw requireNotNull((context.canonicalPlanningOutcome as? CanonicalPlanningOutcome.ExpectedFailure)?.failure)
        val targetPlan = canonicalPlanning.targetPlan
        val resolved = context.resolvedRequest
        val selectionPlan = StimulusTargetCandidateSelector().build(
            targetPlan = targetPlan,
            snapshot = context.snapshot,
            state = context.state,
            request = resolved.request,
            physicalQualityCatalog = physicalQualityCatalog
        )
        observe(ProductionGenerationPhase.B5_COMPLETE)
        val prescriptionContext = com.training.trackplanner.data.personalized.buildCanonicalPrescriptionContext(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = context.snapshot,
            strengthIntent = context.state.strengthIntent
        )
        val authorizationPlan = StimulusPrescriptionAuthorizationEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = context.snapshot,
            canonicalPrescriptionContext = prescriptionContext
        )
        observe(ProductionGenerationPhase.B6_PRE_AUTHORITY_COMPLETE)

        observe(ProductionGenerationPhase.EXPERIMENTAL_BUILD)
        val experimental = try {
            productionBuildCounts.recordProgramBuildInvocation(
                com.training.trackplanner.data.personalized.StimulusProductionBuildKind.EXPERIMENTAL
            )
            experimentalProgramBuildOverride?.invoke() ?: programBuilder.build(
                snapshot = context.snapshot,
                state = context.state,
                gaps = context.gaps,
                intent = context.intent,
                horizon = resolved.request.durationWeeks,
                request = resolved.request,
                answers = answers,
                priorDecisionId = context.priorDecisionId,
                explicitWeeklyDays = resolved.frequencyProvenance.source ==
                    com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER,
                frequency = resolved.frequencyProvenance,
                progress = progress,
                materialDemandOverride = selectionPlan.materialDemand,
                exactPrescriptionAuthorizationProvider = authorizationPlan.provider(),
                canonicalFailureEmitter = { reason, detailCode ->
                    throw StimulusCanonicalEvaluationFailure(reason, detailCode)
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StimulusCanonicalEvaluationFailure) {
            throw canonicalEvaluationFailure(error)
        }

        val finalAudit = FinalStimulusNeedAudit().audit(experimental, context.snapshot, physicalQualityCatalog)
        val experimentalAudit = StimulusTargetControlProgramAuditEngine().audit(
            targetPlan,
            finalAudit,
            resolved.request.durationWeeks
        )
        val materializationAudits = StimulusPrescriptionMaterializationAuditEngine().audit(
            authorizationPlan,
            experimental,
            context.snapshot
        )
        val realizationInputs = com.training.trackplanner.data.personalized.buildStimulusRealizationPrescriptionInputs(
            selectionPlan = selectionPlan,
            canonicalPrescriptionContext = prescriptionContext,
            experimentalItems = experimental.items
        )
        val realizationPlan = StimulusPrescriptionRealizationPlanEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = context.snapshot,
            currentPrescriptions = realizationInputs.currentPrescriptions,
            historyBackedOwners = prescriptionContext.historyBackedOwners,
            currentPrescriptionsByQuality = realizationInputs.currentPrescriptionsByQuality,
            historyBackedAuthorities = prescriptionContext.historyBackedAuthorities
        )
        observe(ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE)
        return CanonicalExperimentalGeneration(
            program = experimental,
            selectionPlan = selectionPlan,
            prescriptionContext = prescriptionContext,
            authorizationPlan = authorizationPlan,
            experimentalAudit = experimentalAudit,
            prescriptionRealizationPlan = realizationPlan,
            materializationAudits = materializationAudits
        )
    }

    /** Builds CONTROL only after the canonical artifact is complete. */
    private suspend fun materializeLateControl(
        context: PreparedCanonicalGenerationContext,
        answers: PersonalizedPlanningAnswers,
        progress: PersonalizedPlannerProgressReporter,
        productionBuildCounts: com.training.trackplanner.data.personalized.MutableStimulusProductionBuildCounts,
        controlGenerationOverride: (suspend () -> GeneratedProgramSkeleton)?,
        observe: (ProductionGenerationPhase) -> Unit
    ): GeneratedProgramSkeleton {
        observe(ProductionGenerationPhase.CONTROL_BUILD)
        if (controlGenerationOverride != null) return controlGenerationOverride()
        productionBuildCounts.recordProgramBuildInvocation(
            com.training.trackplanner.data.personalized.StimulusProductionBuildKind.CONTROL
        )
        val resolved = context.resolvedRequest
        return programBuilder.build(
            snapshot = context.snapshot,
            state = context.state,
            gaps = context.gaps,
            intent = context.intent,
            horizon = resolved.request.durationWeeks,
            request = resolved.request,
            answers = answers,
            priorDecisionId = context.priorDecisionId,
            explicitWeeklyDays = resolved.frequencyProvenance.source ==
                com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER,
            frequency = resolved.frequencyProvenance,
            progress = progress
        )
    }

    /** CONTROL parity and the historical compatibility mirrors are late diagnostics only. */
    private fun attachLateControlCompatibilityMirrors(
        control: GeneratedProgramSkeleton,
        context: PreparedCanonicalGenerationContext,
        canonicalPlanning: CanonicalStimulusPlanningResult
    ): CanonicalPreparedProgram {
        val request = context.resolvedRequest.request
        if (control.request != request) {
            val failure = StimulusCanonicalEvaluationFailure(
                StimulusCanonicalEvaluationFailureReason.RESOLVED_REQUEST_PARITY,
                detailCode = "CONTROL_REQUEST_DIFFERS_FROM_RESOLVED_REQUEST"
            )
            return CanonicalPreparedProgram(
                com.training.trackplanner.data.personalized.bindSplitParentProgression(control),
                CanonicalPlanningOutcome.ExpectedFailure(failure),
                context.resolvedRequest
            )
        }

        val finalAudit = FinalStimulusNeedAudit().audit(control, context.snapshot, physicalQualityCatalog)
        val canonicalWithControlAudit = canonicalPlanning.withControlProgramAudit(
            StimulusTargetControlProgramAuditEngine().audit(
                canonicalPlanning.targetPlan,
                finalAudit,
                request.durationWeeks
            )
        )
        val targetPlanWithAudit = canonicalWithControlAudit.targetPlanCompatibilityMirror()
        val legacyPortfolio = TrainingDecisionPortfolioEngine().build(context.legacyNeeds, context.legacyDoseHistory)
        val stimulusPortfolioComparison = StimulusTrainingDecisionPortfolioComparisonEngine().compare(
            legacyPortfolio,
            canonicalPlanning.decisionPortfolio,
            canonicalPlanning.qualityDoseHistory,
            context.legacyDoseHistory
        )
        val stimulusPortfolioWithComparison = canonicalPlanning.decisionPortfolio.copy(comparison = stimulusPortfolioComparison)
        val withShadowNeeds = control.copy(
            personalizedDecision = control.personalizedDecision?.copy(
                athleteNeedsProfile = context.legacyNeeds,
                athleteStimulusNeedProfile = canonicalPlanning.athleteStimulusNeedProfile.copy(
                    finalAudit = finalAudit,
                    qualityDoseHistoryShadow = canonicalPlanning.qualityDoseHistory,
                    trainingDecisionPortfolioShadow = stimulusPortfolioWithComparison,
                    stimulusTargetPlanShadow = targetPlanWithAudit
                )
            )
        )
        val decision = withShadowNeeds.personalizedDecision
        val needs = decision?.athleteNeedsProfile
        if (decision != null && needs != null) {
            val targetPlan = TargetStimulusPlanEngine().build(legacyPortfolio, context.legacyDoseHistory)
            val stimulusTargetComparison = StimulusTargetPlanComparisonEngine().compare(targetPlan, targetPlanWithAudit)
            val withTargetComparison = withShadowNeeds.copy(
                personalizedDecision = withShadowNeeds.personalizedDecision?.copy(
                    athleteStimulusNeedProfile = withShadowNeeds.personalizedDecision?.athleteStimulusNeedProfile?.copy(
                        stimulusTargetPlanShadow = targetPlanWithAudit.copy(legacyComparison = stimulusTargetComparison)
                    )
                )
            )
            val comparison = TargetPlanComparisonEngine().compare(
                targetPlan, withShadowNeeds, context.snapshot, physicalQualityCatalog
            )
            val regionalIndex = RegionalEvidenceIndexBuilder().build(context.snapshot, context.state, physicalQualityCatalog)
            val strengthRequirement = needs.qualityNeeds.firstOrNull {
                it.quality == com.training.trackplanner.data.TrainableQuality.STRENGTH
            }?.relevance ?: NeedRelevance.UNKNOWN
            val regionalRequirements = RegionalStrengthRequirementResolver().resolve(
                strengthRequirement, context.state.movementRepresentations
            )
            val localizedTissue = context.snapshot.recoverySignals.tissueRestrictedStableKeys
                .map(context.snapshot::movementCoverage)
                .filter { it != MovementCoverage.OTHER }
                .toSet()
            val systemicRecovery = (
                context.snapshot.recoverySignals.readinessStatus in setOf("CAUTION", "FATIGUED", "LIMITED") ||
                    (context.snapshot.recoverySignals.overallFatigueIndex ?: 0) >= 70 ||
                    context.state.trainingStateAssessment?.globalHardRestriction == true ||
                    (context.snapshot.recoverySignals.tissueStatus in setOf("VERY_HIGH", "BLOCKED") && localizedTissue.isEmpty())
                )
            val lowerSportRegions = setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES)
            val sportInterference = context.state.courtDeviation > 0.0 &&
                context.state.lowerNegativeEvidence > 0.0 && context.state.courtInterference > 0.0
            val regionalDiagnosis = RegionalBottleneckDiagnosisEngine().analyze(
                regionalIndex, regionalRequirements, systemicRecovery, sportInterference,
                localizedTissue.associateWith { true }, lowerSportRegions
            )
            val programEmphasis = ProgramEmphasisProjector().project(withShadowNeeds, context.snapshot, physicalQualityCatalog)
            val mirrored = withTargetComparison.copy(
                personalizedDecision = withTargetComparison.personalizedDecision!!.copy(
                    trainingDecisionPortfolio = legacyPortfolio,
                    targetStimulusPlan = targetPlan,
                    targetPlanComparison = comparison,
                    regionalBottleneckDiagnosis = regionalDiagnosis,
                    programEmphasisLabels = programEmphasis
                )
            )
            return CanonicalPreparedProgram(
                com.training.trackplanner.data.personalized.bindSplitParentProgression(mirrored),
                canonicalWithControlAudit,
                context.resolvedRequest
            )
        }
        return CanonicalPreparedProgram(
            com.training.trackplanner.data.personalized.bindSplitParentProgression(withShadowNeeds),
            canonicalWithControlAudit,
            context.resolvedRequest
        )
    }

    /** First point at which a completed CONTROL and completed canonical artifact meet. */
    private fun compareCanonicalExperimentalWithControl(
        control: GeneratedProgramSkeleton,
        canonicalPlanning: CanonicalStimulusPlanningResult,
        experimental: CanonicalExperimentalGeneration
    ): StimulusSelectionProgramComparison {
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            control = control,
            experimental = experimental.program,
            targetPlan = canonicalPlanning.targetPlan,
            selectionPlan = experimental.selectionPlan,
            controlAudit = canonicalPlanning.controlProgramAudit,
            experimentalAudit = experimental.experimentalAudit
        ).copy(
            prescriptionRealizationPlan = experimental.prescriptionRealizationPlan,
            prescriptionAuthorizationPlan = experimental.authorizationPlan,
            prescriptionMaterializationAudits = experimental.materializationAudits
        )
        return comparison.copy(experimentalReadinessAudit = StimulusExperimentalReadinessAuditEngine().audit(comparison))
    }

    /** Test/audit seam proving B1-B4 can be calculated without constructing CONTROL. */
    internal suspend fun buildCanonicalStimulusPlanningForPrepared(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>
    ): CanonicalStimulusPlanningResult {
        val preferences = readPreferences()
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED && state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        return buildCanonicalStimulusPlanningResult(snapshot, state)
    }

    /**
     * Test/dev-only A/B entry point.  The returned experimental skeleton is
     * never persisted or routed to the normal app preview.  CONTROL is built
     * first with the unchanged builder arguments, then the regional demand
     * seam is injected into a second pass through the same downstream
     * prescription, capacity, placement, OFI, tissue and time machinery.
     */
    internal suspend fun generatePreparedComparison(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): com.training.trackplanner.data.personalized.RegionalProgramComparison {
        val control = generatePrepared(preflight, answers, metadata, progress)
        val preferences = readPreferences()
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences)
        val state = stateBuilder.build(snapshot, answers)
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val frequencyEvidence = WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size)
        val resolvedRequest = resolvePreparedProgramRequest(preflight, state, gaps, intent, frequencyEvidence)
        val request = resolvedRequest.request
        val regionalIndex = RegionalEvidenceIndexBuilder().build(snapshot, state, physicalQualityCatalog)
        val needs = control.personalizedDecision?.athleteNeedsProfile
        val strengthRequirement = needs?.qualityNeeds
            ?.firstOrNull { it.quality == com.training.trackplanner.data.TrainableQuality.STRENGTH }?.relevance ?: NeedRelevance.UNKNOWN
        val representations = state.movementRepresentations.ifEmpty {
            com.training.trackplanner.data.personalized.MovementExposureRepresentationAnalyzer()
                .analyze(snapshot, state.profileGoal == "HYPERTROPHY")
        }
        val requirements = RegionalStrengthRequirementResolver().resolve(strengthRequirement, representations)
        val localizedTissue = snapshot.recoverySignals.tissueRestrictedStableKeys
            .map(snapshot::movementCoverage).filter { it != MovementCoverage.OTHER }.toSet()
        val systemic = snapshot.recoverySignals.readinessStatus in setOf("CAUTION", "FATIGUED", "LIMITED") ||
            (snapshot.recoverySignals.overallFatigueIndex ?: 0) >= 70 ||
            state.trainingStateAssessment?.globalHardRestriction == true ||
            (snapshot.recoverySignals.tissueStatus in setOf("VERY_HIGH", "BLOCKED") && localizedTissue.isEmpty())
        val sport = state.courtDeviation > 0.0 && state.lowerNegativeEvidence > 0.0 && state.courtInterference > 0.0
        val diagnoses = RegionalBottleneckDiagnosisEngine().analyze(
            regionalIndex, requirements, systemic, sport, localizedTissue.associateWith { true },
            setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.CALVES)
        )
        val regionalDemand = RegionalExperimentalMaterialDemandBuilder().build(
            diagnoses, control, snapshot, state, request, physicalQualityCatalog
        )
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        val experimental = programBuilder.build(
            snapshot, state, gaps, intent, request.durationWeeks, request, answers, priorId,
            explicitWeeklyDays = resolvedRequest.frequencyProvenance.source ==
                com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER,
            frequency = resolvedRequest.frequencyProvenance,
            progress = progress,
            materialDemandOverride = regionalDemand.demand,
            regionalTargetPlan = regionalDemand.targetPlan
        )
        val decisions = diagnoses.map { RegionalTrainingDecisionResolver().resolve(it) }
        val targets = diagnoses.map { RegionalStimulusTargetResolver().resolve(it) }
        val traces = regionalDemand.traces.map { trace ->
            val target = targets.firstOrNull { it.region == trace.region && it.quality == trace.targetQuality && it.action == trace.targetAction }
            val projection = target?.let {
                com.training.trackplanner.data.personalized.FinalRegionalStimulusProjector().project(
                    target = it,
                    finalPlan = experimental,
                    snapshot = snapshot,
                    catalog = physicalQualityCatalog,
                    selectedIdentity = trace.selectedIdentity,
                    creditedUnits = trace.existingPlannedCompatibleDose,
                    residualUnits = trace.residualDose,
                    authorizedUnits = trace.authorizedUnits
                )
            }
            trace.copy(
                materializedUnits = projection?.targetCompatibleMaterializedUnits ?: 0,
                targetCompatibleMaterializedUnits = projection?.targetCompatibleMaterializedUnits ?: 0,
                shortfall = projection?.shortfall ?: trace.residualDose,
                overrunUnits = projection?.overrunUnits ?: 0,
                ordinarySameKeyCompatibleUnits = projection?.ordinarySameKeyCompatibleUnits ?: 0,
                finalReasonCodes = trace.finalReasonCodes + listOfNotNull(projection?.reasonCode)
            )
        }
        val experimentalPortfolio = needs?.let {
            val doseHistory = QualityDoseHistoryAnalyzer().analyze(snapshot, state, physicalQualityCatalog)
            TrainingDecisionPortfolioEngine().build(it, doseHistory) to doseHistory
        }
        val experimentalTargetPlan = experimentalPortfolio?.let { (portfolio, doseHistory) ->
            TargetStimulusPlanEngine().build(portfolio, doseHistory)
        }
        val experimentalComparison = if (experimentalTargetPlan != null)
            TargetPlanComparisonEngine().compare(experimentalTargetPlan, experimental, snapshot, physicalQualityCatalog)
        else null
        val decision = experimental.personalizedDecision
        val experimentalWithTrace = experimental.copy(
            personalizedDecision = decision?.copy(
                regionalPlanningAuthorityMode = RegionalPlanningAuthorityMode.EXPERIMENTAL_REGIONAL_TARGETS,
                athleteNeedsProfile = needs,
                trainingDecisionPortfolio = experimentalPortfolio?.first,
                targetStimulusPlan = experimentalTargetPlan,
                targetPlanComparison = experimentalComparison,
                regionalBottleneckDiagnosis = diagnoses,
                regionalTrainingDecisions = decisions,
                regionalStimulusTargets = targets,
                regionalAuthorityTraces = traces,
                programEmphasisLabels = ProgramEmphasisProjector().project(experimental, snapshot, physicalQualityCatalog)
            )
        )
        return RegionalAuthorityProgramComparison().compare(
            control, experimentalWithTrace, traces,
            regionalDemand.counters.copy(prescriptionResolutions = regionalDemand.counters.candidateCountEvaluated)
        )
    }

    /**
     * Test/dev-only Phase B5 A/B entry point. CONTROL is generated by the unchanged normal
     * path. CANONICAL_SELECTION is a second pass through the same builder and all existing
     * prescription, capacity, placement, OFI, tissue, completion and reflow machinery, with
     * only the B5 identity proposal supplied through materialDemandOverride. The experimental
     * skeleton is returned in memory and is never persisted or routed to normal preview.
     */
    internal suspend fun generatePreparedStimulusSelectionComparison(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): StimulusSelectionProgramComparison {
        val prepared = generatePreparedWithCanonicalPlanning(preflight, answers, metadata, progress)
        val control = prepared.program
        val canonicalPlanning = prepared.canonicalPlanning
        val targetPlan = canonicalPlanning.targetPlan
        val preferences = readPreferences()
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED && state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val request = prepared.resolvedRequest.request
        val selectionPlan = StimulusTargetCandidateSelector().build(
            targetPlan = targetPlan,
            snapshot = snapshot,
            state = state,
            request = request,
            physicalQualityCatalog = physicalQualityCatalog
        )
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        val experimental = programBuilder.build(
            snapshot = snapshot,
            state = state,
            gaps = gaps,
            intent = intent,
            horizon = request.durationWeeks,
            request = request,
            answers = answers,
            priorDecisionId = priorId,
            explicitWeeklyDays = prepared.resolvedRequest.frequencyProvenance.source ==
                com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER,
            frequency = prepared.resolvedRequest.frequencyProvenance,
            progress = progress,
            materialDemandOverride = selectionPlan.materialDemand
        )
        val experimentalFinalAudit = FinalStimulusNeedAudit().audit(experimental, snapshot, physicalQualityCatalog)
        val experimentalAudit = StimulusTargetControlProgramAuditEngine().audit(
            targetPlan,
            experimentalFinalAudit,
            request.durationWeeks
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            control = control,
            experimental = experimental,
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            controlAudit = canonicalPlanning.controlProgramAudit,
            experimentalAudit = experimentalAudit
        )
        // B6 derives current prescriptions from B5 owners and actual snapshot history. Final
        // EXPERIMENTAL rows remain the materialization result; no third program is generated.
        val canonicalPrescriptionContext = com.training.trackplanner.data.personalized.buildCanonicalPrescriptionContext(
            targetPlan, selectionPlan, snapshot, state.strengthIntent
        )
        val realizationInputs = com.training.trackplanner.data.personalized.buildStimulusRealizationPrescriptionInputs(
            selectionPlan = selectionPlan,
            canonicalPrescriptionContext = canonicalPrescriptionContext,
            experimentalItems = experimental.items
        )
        val prescriptionPlan = StimulusPrescriptionRealizationPlanEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = snapshot,
            currentPrescriptions = realizationInputs.currentPrescriptions,
            historyBackedOwners = canonicalPrescriptionContext.historyBackedOwners,
            currentPrescriptionsByQuality = realizationInputs.currentPrescriptionsByQuality,
            historyBackedAuthorities = canonicalPrescriptionContext.historyBackedAuthorities
        )
        val enrichedComparison = comparison.copy(
            prescriptionRealizationPlan = prescriptionPlan
        )
        return enrichedComparison.copy(
            experimentalReadinessAudit = StimulusExperimentalReadinessAuditEngine().audit(enrichedComparison)
        )
    }

    /**
     * Compatibility evaluation seam for callers that already supply CONTROL. Normal production
     * uses buildCanonicalExperimentalGeneration and does not call this combined helper.
     */
    internal suspend fun generatePreparedStimulusPrescriptionMaterializationComparison(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        canonicalPlanning: CanonicalStimulusPlanningResult,
        resolvedRequest: ProgramSkeletonRequest,
        frequencyProvenance: com.training.trackplanner.data.personalized.PlanningFrequencyProvenance,
        controlOverride: GeneratedProgramSkeleton,
        productionBuildCounts: com.training.trackplanner.data.personalized.MutableStimulusProductionBuildCounts? = null
    ): StimulusSelectionProgramComparison {
        val preferences = readPreferences()
        val control = controlOverride
        val targetPlan = canonicalPlanning.targetPlan
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        val state = stateBuilder.build(snapshot, answers)
        if (state.strengthIntent == StrengthIntent.UNRESOLVED || state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED ||
            state.freeWeightWillingness == FreeWeightWillingness.UNRESOLVED
        ) {
            if (productionBuildCounts != null) {
                throw com.training.trackplanner.data.personalized.StimulusProductionEvaluationFailure(
                    "B9_B5_INPUT_UNRESOLVED"
                )
            }
            error("UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT")
        }
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val selectionPlan = StimulusTargetCandidateSelector().build(
            targetPlan = targetPlan,
            snapshot = snapshot,
            state = state,
            request = resolvedRequest,
            physicalQualityCatalog = physicalQualityCatalog
        )
        val canonicalPrescriptionContext = com.training.trackplanner.data.personalized.buildCanonicalPrescriptionContext(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = snapshot,
            strengthIntent = state.strengthIntent
        )
        val authorizationPlan = StimulusPrescriptionAuthorizationEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = snapshot,
            canonicalPrescriptionContext = canonicalPrescriptionContext
        )
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        val experimental = try {
            productionBuildCounts?.recordProgramBuildInvocation(
                com.training.trackplanner.data.personalized.StimulusProductionBuildKind.EXPERIMENTAL
            )
            programBuilder.build(
                snapshot = snapshot,
                state = state,
                gaps = gaps,
                intent = intent,
                horizon = resolvedRequest.durationWeeks,
                request = resolvedRequest,
                answers = answers,
                priorDecisionId = priorId,
                explicitWeeklyDays = frequencyProvenance.source ==
                    com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER,
                frequency = frequencyProvenance,
                progress = progress,
                materialDemandOverride = selectionPlan.materialDemand,
                exactPrescriptionAuthorizationProvider = authorizationPlan.provider(),
                canonicalFailureEmitter = { reason, detailCode ->
                    throw StimulusCanonicalEvaluationFailure(reason, detailCode)
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StimulusCanonicalEvaluationFailure) {
            throw canonicalEvaluationFailure(error)
        }
        val experimentalFinalAudit = FinalStimulusNeedAudit().audit(experimental, snapshot, physicalQualityCatalog)
        val experimentalAudit = StimulusTargetControlProgramAuditEngine().audit(
            targetPlan,
            experimentalFinalAudit,
            resolvedRequest.durationWeeks
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            control = control,
            experimental = experimental,
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            controlAudit = canonicalPlanning.controlProgramAudit,
            experimentalAudit = experimentalAudit
        )
        val materializationAudits = StimulusPrescriptionMaterializationAuditEngine().audit(
            authorizationPlan, experimental, snapshot
        )
        val realizationInputs = com.training.trackplanner.data.personalized.buildStimulusRealizationPrescriptionInputs(
            selectionPlan = selectionPlan,
            canonicalPrescriptionContext = canonicalPrescriptionContext,
            experimentalItems = experimental.items
        )
        val prescriptionPlan = StimulusPrescriptionRealizationPlanEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = snapshot,
            currentPrescriptions = realizationInputs.currentPrescriptions,
            historyBackedOwners = canonicalPrescriptionContext.historyBackedOwners,
            currentPrescriptionsByQuality = realizationInputs.currentPrescriptionsByQuality,
            historyBackedAuthorities = canonicalPrescriptionContext.historyBackedAuthorities
        )
        val enrichedComparison = comparison.copy(
            prescriptionRealizationPlan = prescriptionPlan,
            prescriptionAuthorizationPlan = authorizationPlan,
            prescriptionMaterializationAudits = materializationAudits
        )
        val readinessAudit = StimulusExperimentalReadinessAuditEngine().audit(enrichedComparison)
        return StimulusSelectionProgramComparison(
            control = enrichedComparison.control,
            experimental = enrichedComparison.experimental,
            targetPlan = enrichedComparison.targetPlan,
            selectionPlan = enrichedComparison.selectionPlan,
            controlAudit = enrichedComparison.controlAudit,
            experimentalAudit = enrichedComparison.experimentalAudit,
            differences = enrichedComparison.differences,
            controlStableKeys = enrichedComparison.controlStableKeys,
            experimentalStableKeys = enrichedComparison.experimentalStableKeys,
            addedStableKeys = enrichedComparison.addedStableKeys,
            removedStableKeys = enrichedComparison.removedStableKeys,
            sharedStableKeys = enrichedComparison.sharedStableKeys,
            materializationTraces = enrichedComparison.materializationTraces,
            prescriptionRealizationPlan = enrichedComparison.prescriptionRealizationPlan,
            prescriptionAuthorizationPlan = enrichedComparison.prescriptionAuthorizationPlan,
            prescriptionMaterializationAudits = enrichedComparison.prescriptionMaterializationAudits,
            experimentalReadinessAudit = readinessAudit,
            winner = null
        )
    }

    /** Compatibility wrapper around the combined-control comparison seam for audits/tests. */
    internal suspend fun generatePreparedStimulusProductionCutoverEvaluation(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        canonicalPlanning: CanonicalStimulusPlanningResult,
        resolvedRequest: ProgramSkeletonRequest,
        frequencyProvenance: com.training.trackplanner.data.personalized.PlanningFrequencyProvenance,
        controlOverride: GeneratedProgramSkeleton,
        productionBuildCounts: com.training.trackplanner.data.personalized.MutableStimulusProductionBuildCounts? = null
    ): com.training.trackplanner.data.personalized.StimulusProductionCutoverEvaluation {
        val comparison = generatePreparedStimulusPrescriptionMaterializationComparison(
            preflight = preflight,
            answers = answers,
            metadata = metadata,
            progress = progress,
            canonicalPlanning = canonicalPlanning,
            resolvedRequest = resolvedRequest,
            frequencyProvenance = frequencyProvenance,
            controlOverride = controlOverride,
            productionBuildCounts = productionBuildCounts
        )
        val resolvedScope = com.training.trackplanner.data.personalized.StimulusProductionMaterialScopeResolver()
            .resolve(comparison)
        val authority = com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityAuditEngine()
            .audit(comparison, resolvedScope ?: com.training.trackplanner.data.personalized.StimulusProductionCutoverScope.STRENGTH_V1)
        return com.training.trackplanner.data.personalized.StimulusProductionCutoverEvaluation(
            comparison = comparison.copy(productionCutoverAuthority = authority),
            cutoverAuthority = authority
        )
    }

    /**
     * Production order is canonical preparation and the complete EXPERIMENTAL artifact first,
     * followed by one late CONTROL materialization, comparison, B7/B8 and intact-object B9.
     */
    internal suspend fun generatePreparedProduction(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        routingMode: com.training.trackplanner.data.personalized.StimulusProductionRoutingMode =
            com.training.trackplanner.data.personalized.StimulusProductionRoutingPolicy.defaultMode,
        controlGenerationOverride: (suspend () -> GeneratedProgramSkeleton)? = null,
        canonicalPlanningComputation: CanonicalStimulusPlanningComputation = ::buildCanonicalStimulusPlanningResult,
        productionGenerationObserver: ((ProductionGenerationObservation) -> Unit)? = null,
        experimentalProgramBuildOverride: (suspend () -> GeneratedProgramSkeleton)? = null,
        incumbentPlacementIndex: CanonicalIncumbentPlacementIndex = CanonicalIncumbentPlacementIndex.unavailable(
            CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM
        )
    ): com.training.trackplanner.data.personalized.StimulusProductionGenerationResult {
        val buildCounts = com.training.trackplanner.data.personalized.MutableStimulusProductionBuildCounts()
        var incumbentPlacementShadow: CanonicalIncumbentPlacementShadow? = null
        var incumbentPlacementActivationStatus: CanonicalIncumbentActivationStatus? = null
        var incumbentPlacementPreservations: List<CanonicalIncumbentPlacementPreservation> = emptyList()
        var incumbentPlacementActivationDetails: List<String> = emptyList()
        val productionProgress = com.training.trackplanner.data.personalized.ProductionGenerationProgressMapper(progress)
        val context = prepareCanonicalGenerationContext(
            preflight = preflight,
            answers = answers,
            metadata = metadata,
            progress = productionProgress.canonicalReporter(),
            canonicalPlanningComputation = canonicalPlanningComputation
        )
        fun observe(phase: ProductionGenerationPhase) {
            productionGenerationObserver?.invoke(ProductionGenerationObservation(phase, context))
        }
        observe(ProductionGenerationPhase.CANONICAL_PREPARED)

        fun fallback(
            control: GeneratedProgramSkeleton,
            failure: com.training.trackplanner.data.personalized.StimulusProductionEvaluationFailure
        ) = com.training.trackplanner.data.personalized.StimulusProductionGenerationResult(
            program = control.copy(incumbentSourceSnapshotToken = incumbentPlacementIndex.sourceSnapshotToken),
            routeDecision = com.training.trackplanner.data.personalized.StimulusProductionRoutingDecision(
                mode = routingMode,
                selectedSource = com.training.trackplanner.data.personalized.StimulusProductionProgramSource.CONTROL,
                b8Status = null,
                b8Scope = null,
                reasonCodes = listOf("B9_UPSTREAM_EVALUATION_FAILED_CONTROL_FALLBACK"),
                productionRoutingActive = false
            ),
            comparison = null,
            buildCounts = buildCounts.snapshot(),
            upstreamFailureReason = failure.reasonCode,
            upstreamFailureDetails = (failure.cause as? StimulusCanonicalEvaluationFailure)?.let {
                listOfNotNull(it.reason.name, it.detailCode)
            }.orEmpty(),
            incumbentPlacementShadow = incumbentPlacementShadow
        ).also {
            productionProgress.reportSelection()
            productionProgress.reportValidationComplete()
            productionProgress.reportComplete()
        }

        val canonicalPlanning = when (val outcome = context.canonicalPlanningOutcome) {
            is CanonicalPlanningOutcome.Success -> outcome.result
            is CanonicalPlanningOutcome.ExpectedFailure -> {
                val control = materializeLateControl(
                    context, answers, productionProgress.controlReporter(), buildCounts, controlGenerationOverride, ::observe
                )
                val failure = canonicalEvaluationFailure(outcome.failure)
                return fallback(com.training.trackplanner.data.personalized.bindSplitParentProgression(control), failure)
            }
        }

        var experimental = try {
            buildCanonicalExperimentalGeneration(
                context = context,
                answers = answers,
                progress = productionProgress.experimentalReporter(),
                productionBuildCounts = buildCounts,
                observe = ::observe,
                experimentalProgramBuildOverride = experimentalProgramBuildOverride
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: StimulusCanonicalEvaluationFailure) {
            val control = materializeLateControl(
                context, answers, productionProgress.controlReporter(), buildCounts, controlGenerationOverride, ::observe
            )
            val late = attachLateControlCompatibilityMirrors(control, context, canonicalPlanning)
            val controlFailure = late.planningOutcome as? CanonicalPlanningOutcome.ExpectedFailure
            if (controlFailure != null) return fallback(late.program, canonicalEvaluationFailure(controlFailure.failure))
            return fallback(late.program, canonicalEvaluationFailure(failure))
        } catch (failure: com.training.trackplanner.data.personalized.StimulusProductionEvaluationFailure) {
            val control = materializeLateControl(
                context, answers, productionProgress.controlReporter(), buildCounts, controlGenerationOverride, ::observe
            )
            val late = attachLateControlCompatibilityMirrors(control, context, canonicalPlanning)
            val controlFailure = late.planningOutcome as? CanonicalPlanningOutcome.ExpectedFailure
            if (controlFailure != null) return fallback(late.program, canonicalEvaluationFailure(controlFailure.failure))
            return fallback(late.program, failure)
        }

        // C20 evaluates exact incumbent positions against current EXP rows and the same
        // generation-scoped OFI/tissue projections used by placement review. Only the fully
        // resolved, combined HARD_VALID exact anchors are applied. Invalid and unresolved rows
        // retain the canonical planner's current placement.
        val canonicalRowsBeforeIncumbentActivation = experimental.program.items
        val liveIncumbentFeasibility = CanonicalIncumbentPlacementFeasibilityEvaluator.evaluate(
            index = incumbentPlacementIndex,
            program = experimental.program,
            snapshot = context.snapshot,
            state = context.state
        )
        val activation = CanonicalIncumbentPlacementActivator.activate(
            index = incumbentPlacementIndex,
            program = experimental.program,
            feasibility = liveIncumbentFeasibility
        )
        incumbentPlacementActivationStatus = activation.status
        incumbentPlacementPreservations = activation.preservations
        incumbentPlacementActivationDetails = activation.rejectionDetails
        if (activation.program !== experimental.program) {
            val stabilizedProgram = activation.program
            val finalAudit = FinalStimulusNeedAudit().audit(stabilizedProgram, context.snapshot, physicalQualityCatalog)
            val experimentalAudit = StimulusTargetControlProgramAuditEngine().audit(
                canonicalPlanning.targetPlan,
                finalAudit,
                context.resolvedRequest.request.durationWeeks
            )
            val materializationAudits = StimulusPrescriptionMaterializationAuditEngine().audit(
                experimental.authorizationPlan,
                stabilizedProgram,
                context.snapshot
            )
            val realizationInputs = com.training.trackplanner.data.personalized.buildStimulusRealizationPrescriptionInputs(
                selectionPlan = experimental.selectionPlan,
                canonicalPrescriptionContext = experimental.prescriptionContext,
                experimentalItems = stabilizedProgram.items
            )
            val realizationPlan = StimulusPrescriptionRealizationPlanEngine().build(
                targetPlan = canonicalPlanning.targetPlan,
                selectionPlan = experimental.selectionPlan,
                snapshot = context.snapshot,
                currentPrescriptions = realizationInputs.currentPrescriptions,
                historyBackedOwners = experimental.prescriptionContext.historyBackedOwners,
                currentPrescriptionsByQuality = realizationInputs.currentPrescriptionsByQuality,
                historyBackedAuthorities = experimental.prescriptionContext.historyBackedAuthorities
            )
            experimental = experimental.copy(
                program = stabilizedProgram,
                experimentalAudit = experimentalAudit,
                prescriptionRealizationPlan = realizationPlan,
                materializationAudits = materializationAudits
            )
        }
        incumbentPlacementShadow = CanonicalIncumbentPlacementShadowEvaluator.evaluate(
            index = incumbentPlacementIndex,
            currentRows = canonicalRowsBeforeIncumbentActivation,
            feasibilityByOwnerWeek = liveIncumbentFeasibility.byOwnerWeek.mapValues { it.value.status },
            evidenceByOwnerWeek = liveIncumbentFeasibility.byOwnerWeek,
            combinedFeasibility = liveIncumbentFeasibility.combinedHardValidAnchors,
            combinedAnchorSetConflict = liveIncumbentFeasibility.combinedAnchorSetConflict,
            shadowRows = liveIncumbentFeasibility.shadowRows,
            projectionCallCount = liveIncumbentFeasibility.projectionCallCount,
            dayOfiProjectionCallCount = liveIncumbentFeasibility.dayOfiProjectionCallCount,
            tissueProjectionCallCount = liveIncumbentFeasibility.tissueProjectionCallCount
        )

        val control = materializeLateControl(
            context = context,
            answers = answers,
            progress = productionProgress.controlReporter(),
            productionBuildCounts = buildCounts,
            controlGenerationOverride = controlGenerationOverride,
            observe = ::observe
        )
        val preparedControl = attachLateControlCompatibilityMirrors(control, context, canonicalPlanning)
        val parityFailure = (preparedControl.planningOutcome as? CanonicalPlanningOutcome.ExpectedFailure)?.failure
        if (parityFailure != null) return fallback(preparedControl.program, canonicalEvaluationFailure(parityFailure))
        observe(ProductionGenerationPhase.CONTROL_AUDIT)

        val evaluation = try {
            val comparison = compareCanonicalExperimentalWithControl(
                preparedControl.program,
                preparedControl.canonicalPlanning,
                experimental
            )
            observe(ProductionGenerationPhase.COMPARISON)
            observe(ProductionGenerationPhase.B7)
            val scope = com.training.trackplanner.data.personalized.StimulusProductionMaterialScopeResolver().resolve(comparison)
            val authority = com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityAuditEngine()
                .audit(comparison, scope ?: com.training.trackplanner.data.personalized.StimulusProductionCutoverScope.STRENGTH_V1)
            observe(ProductionGenerationPhase.B8)
            com.training.trackplanner.data.personalized.StimulusProductionCutoverEvaluation(
                comparison = comparison.copy(productionCutoverAuthority = authority),
                cutoverAuthority = authority
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: StimulusCanonicalEvaluationFailure) {
            return fallback(preparedControl.program, canonicalEvaluationFailure(failure))
        } catch (failure: com.training.trackplanner.data.personalized.StimulusProductionEvaluationFailure) {
            return fallback(preparedControl.program, failure)
        }
        val routed = com.training.trackplanner.data.personalized.StimulusProductionRouter().route(
            comparison = evaluation.comparison,
            authority = evaluation.cutoverAuthority,
            mode = routingMode
        )
        observe(ProductionGenerationPhase.B9)
        productionProgress.reportSelection()
        productionProgress.reportValidationComplete()
        productionProgress.reportComplete()
        val routedProgram = if (routed.program.incumbentSourceSnapshotToken == incumbentPlacementIndex.sourceSnapshotToken) {
            routed.program
        } else {
            routed.program.copy(incumbentSourceSnapshotToken = incumbentPlacementIndex.sourceSnapshotToken)
        }
        return com.training.trackplanner.data.personalized.StimulusProductionGenerationResult(
            program = routedProgram,
            routeDecision = routed.decision,
            comparison = evaluation.comparison,
            buildCounts = buildCounts.snapshot(),
            incumbentPlacementShadow = incumbentPlacementShadow,
            incumbentPlacementActivationStatus = incumbentPlacementActivationStatus,
            incumbentPlacementPreservations = incumbentPlacementPreservations,
            incumbentPlacementActivationDetails = incumbentPlacementActivationDetails
        )
    }

    /** Pure B8 seam for tests and diagnostics that already hold the B6/B7 comparison. */
    internal fun evaluateStimulusProductionCutover(
        comparison: com.training.trackplanner.data.personalized.StimulusSelectionProgramComparison
    ): com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityDecision =
        com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityAuditEngine().audit(
            comparison,
            com.training.trackplanner.data.personalized.StimulusProductionMaterialScopeResolver().resolve(comparison)
                ?: com.training.trackplanner.data.personalized.StimulusProductionCutoverScope.STRENGTH_V1
        )

    private fun canonicalEvaluationFailure(
        failure: StimulusCanonicalEvaluationFailure
    ): com.training.trackplanner.data.personalized.StimulusProductionEvaluationFailure =
        com.training.trackplanner.data.personalized.StimulusProductionEvaluationFailure(
            reasonCode = "B9_EXPECTED_CANONICAL_EVALUATION_FAILURE",
            cause = failure
        )

    /** Compatibility wrapper for callers that have not yet adopted the two-phase API. */
    suspend fun generate(
        request: ProgramSkeletonRequest,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        cutoff: LocalDate = LocalDate.now(),
        constraints: PersonalizedGenerationConstraints = PersonalizedGenerationConstraints(explicitSessionMinutes = request.sessionMinutes),
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        incumbentPlacementIndex: CanonicalIncumbentPlacementIndex = CanonicalIncumbentPlacementIndex.unavailable(
            CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM
        )
    ): PersonalizedPlanningOutcome {
        val preflight = prepare(request, metadata, cutoff, constraints, progress)
        val unanswered = preflight.questions.filter { question ->
            question.options.none { it.value == answers.values[question.id] && it.value != "UNRESOLVED" }
        }
        return if (unanswered.isNotEmpty()) PersonalizedPlanningOutcome.Questions(unanswered)
        else PersonalizedPlanningOutcome.Generated(generatePreparedProduction(
            preflight, answers, metadata, progress, incumbentPlacementIndex = incumbentPlacementIndex
        ).program)
    }

    private suspend fun buildSnapshot(
        cutoff: LocalDate,
        metadata: Map<String, RuntimeExerciseMetadata>,
        preferences: PersonalizedPlanningPreferences,
        includeStimulusExposureLedger: Boolean = false
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
        val strengthPerformanceHistory = revision?.let { strengthPosteriorDao.localHistory(it.revisionKey) }.orEmpty()
        val strengthPersonalCurveTheta = revision?.let { activeRevision ->
            strengthPersonalCurveThetaAsOf(
                revisionKey = activeRevision.revisionKey,
                cutoff = cutoff,
                records = strengthPosteriorDao.allCurvePosteriors().mapNotNull { entity ->
                    runCatching {
                        StrengthPersonalCurveThetaRecord(
                            subjectKey = entity.curveSubjectKey,
                            updatedAtMillis = entity.updatedAt,
                            meanTheta = entity.toPosterior().meanTheta
                        )
                    }.getOrNull()
                }
            )
        }.orEmpty()
        val ofiSeries = DailyFatigueCalculator(
            runtimeCatalog,
            canonicalOfiAxisProfiles,
            dailyCanonicalStrengthPosterior(posteriorHistory, strengthPerformanceRegistry)
        ).calculateSeries(cutoff, 56, exercises, history, profile, dailyMetrics).map { it.state }
        val ofi = ofiSeries.last().overallFatigueIndex
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
        val baseSnapshot = snapshotBuilder.build(cutoff, history, exercises, metadata, badmintonCatalog, profile, preferences, canonicalStrength, recovery, roleCatalog)
        val snapshot = if (includeStimulusExposureLedger) {
            val loadResolver = StrengthPerformanceLoadResolver(dailyMetrics, checkIns, profile)
            baseSnapshot.copy(
                stimulusExposureLedger = com.training.trackplanner.data.personalized.StimulusExposureLedgerBuilder(
                    strengthLoadResolver = loadResolver,
                    strengthPerformanceRegistry = strengthPerformanceRegistry
                ).build(
                    cutoff = cutoff,
                    history = history,
                    exercises = baseSnapshot.exercises,
                    metadata = metadata,
                    physicalQualityCatalog = physicalQualityCatalog,
                    movementRelations = canonicalMovementRelations,
                    coreCatalog = canonicalCoreCatalog,
                    badmintonCatalog = badmintonCatalog,
                    exerciseRoleCatalog = roleCatalog,
                    historyStart = qualityDoseHistoryHorizon(cutoff).ledgerStart,
                    reviewedCanonicalStableKeys = reviewedCanonicalStableKeys,
                    strengthPerformanceHistory = strengthPerformanceHistory
                )
            )
        } else baseSnapshot
        return snapshot.copy(performancePrescriptions = performancePrescriptions,
            strengthPerformanceRegistry = strengthPerformanceRegistry,
            repetitionCurveRegistry = repetitionCurveRegistry,
            rpeRirPolicy = rpeRirPolicy,
            strengthPersonalCurveTheta = strengthPersonalCurveTheta,
            strengthPerformanceHistory = strengthPerformanceHistory,
            planWeekTissueProjection = tissueProjectionProvider(cutoff),
            planDayProjection = com.training.trackplanner.data.personalized.PlanDayOfiProjection(cutoff,
                DailyFatigueCalculator(runtimeCatalog, canonicalOfiAxisProfiles,
                    dailyCanonicalStrengthPosterior(posteriorHistory, strengthPerformanceRegistry)),
                exercises, history, profile, dailyMetrics),
            dailyStrain = ofiSeries.filter { it.date >= snapshot.historyStart }.map { PlanningDailyStrain(it.date,it.overallFatigueIndex.toDouble(),
                it.highForceNeuralScore.toDouble(),it.systemicMuscularScore.toDouble(),it.localMuscularScore.toDouble(),
                it.highSpeedScore.toDouble(),it.reactiveScore.toDouble(),it.recoveryPressureScore.toDouble(),it.confirmedTrainingLoad) },
            weeklyCourtLoad = (0..11).associate { offset ->
                val end=completedTrainingWeekEnd(cutoff).minusDays(offset*7L)
                end to com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator(runtimeCatalog).calculateRaw(
                    history.filter { LocalDate.parse(it.entry.date) in end.minusDays(6)..end },snapshot.exercises)
            },
            // Typed user declarations only. Soft readiness advice is not an independent hard gate.
            hardRestrictedModes = listOfNotNull(profile?.painAreaTags,profile?.avoidMovementTags)
                .flatMap { it.split('|',',') }.map(String::trim).filter { it.isNotBlank() && it!="NONE" }.toSet(),
            weekAnnotations = WeeklyContextAnnotationJson.read(appMetaDao.value(WeeklyContextAnnotationJson.KEY)))
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
            strengthIntentProfileGoal = json.optString("strengthIntentProfileGoal").takeIf(String::isNotBlank),
            interruptionCause = runCatching { InterruptionCause.valueOf(json.optString("interruptionCause")) }.getOrNull(),
            interruptionFrequency = runCatching { InterruptionFrequency.valueOf(json.optString("interruptionFrequency")) }.getOrNull(),
            interruptionFrequencyAnsweredAtEpochMillis = json.optLong("interruptionFrequencyAnsweredAtEpochMillis",Long.MIN_VALUE).takeUnless { it==Long.MIN_VALUE }
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
            strengthIntentProfileGoal = if (QUESTION_STRENGTH_INTENT in answers.values) profileGoal else old.strengthIntentProfileGoal,
            interruptionFrequency = answers.values[QUESTION_INTERRUPTION_FREQUENCY]?.let(InterruptionFrequency::valueOf) ?: old.interruptionFrequency,
            interruptionFrequencyAnsweredAtEpochMillis = if (QUESTION_INTERRUPTION_FREQUENCY in answers.values) System.currentTimeMillis() else old.interruptionFrequencyAnsweredAtEpochMillis
        )
        persistUserState {
            appMetaDao.upsert(AppMeta(PREFERENCES_KEY, JSONObject()
                .put("strengthIntent", next.strengthIntent?.name.orEmpty())
                .put("strengthIntentAnsweredAtEpochMillis", next.strengthIntentAnsweredAtEpochMillis)
                .put("strengthIntentProfileGoal", next.strengthIntentProfileGoal.orEmpty())
                .put("badmintonIntent", next.badmintonIntent?.name.orEmpty())
                .put("interruptionCause",next.interruptionCause?.name.orEmpty())
                .put("interruptionFrequency",next.interruptionFrequency?.name.orEmpty())
                .put("interruptionFrequencyAnsweredAtEpochMillis",next.interruptionFrequencyAnsweredAtEpochMillis)
                .put("freeWeightWillingness", next.freeWeightWillingness?.name.orEmpty()).toString()))
            val annotations=answers.weekAnnotations(System.currentTimeMillis())
            if (annotations.isNotEmpty()) appMetaDao.upsert(AppMeta(WeeklyContextAnnotationJson.KEY,
                WeeklyContextAnnotationJson.write(WeeklyContextAnnotationJson.read(appMetaDao.value(WeeklyContextAnnotationJson.KEY))+annotations)))
        }
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
        .put("finalSavedFingerprint", finalSavedFingerprint)
        .put("canonicalPlacementFinalFingerprint", canonicalPlacementFinalFingerprint)
        .put("recoverySignalCodes", JSONArray(recoverySignalCodes))
        .put("genericCourtLoad", genericCourtLoad)
        .put("courtBaselineLoad", courtBaselineLoad)
        .put("recentCourtLoad", recentCourtLoad)
        .put("courtDeviation", courtDeviation)
        .put("lowerNegativeEvidence", lowerNegativeEvidence)
        .put("courtInterference", courtInterference)
        .put("trainingDecisionPortfolio", trainingDecisionPortfolio?.toJson())
        .put("targetStimulusPlan", targetStimulusPlan?.toJson())
        .put("targetPlanComparison", targetPlanComparison?.toJson())
        .put("regionalBottleneckDiagnosis", JSONArray(regionalBottleneckDiagnosis.map { it.toJson() }))
        .put("programEmphasisLabels", JSONArray(programEmphasisLabels.map { it.toJson() }))
        .put("regionalPlanningAuthorityMode", regionalPlanningAuthorityMode.name)
        .put("regionalTrainingDecisions", JSONArray(regionalTrainingDecisions.map { JSONObject()
            .put("region", it.region.name).put("decision", it.decision.name).put("reasonCodes", JSONArray(it.reasonCodes))
        }))
        .put("regionalStimulusTargets", JSONArray(regionalStimulusTargets.map { JSONObject()
            .put("region", it.region.name).put("quality", it.quality.name).put("action", it.action.name)
            .put("numericAuthority", it.numericAuthority.name).put("weeklyDoseTarget", it.weeklyDoseTarget)
            .put("exposureWeekDoseTarget", it.exposureWeekDoseTarget).put("exposureFrequencyTarget", it.exposureFrequencyTarget)
            .put("priority", it.priority.name).put("reasonCodes", JSONArray(it.reasonCodes)).put("specificStableKey", it.specificStableKey)
        }))
        .put("regionalAuthorityTraces", JSONArray(regionalAuthorityTraces.map { trace -> JSONObject()
            .put("region", trace.region.name).put("requirement", trace.requirement.name)
            .put("performanceResponse", trace.performanceResponse.name).put("trainingDecision", trace.trainingDecision.name)
            .put("targetQuality", trace.targetQuality.name).put("targetAction", trace.targetAction.name)
            .put("targetWeeklyDose", trace.targetWeeklyDose).put("targetExposureWeekDose", trace.targetExposureWeekDose)
            .put("targetFrequency", trace.targetFrequency).put("numericAuthority", trace.numericAuthority.name)
            .put("existingPlannedCompatibleDose", trace.existingPlannedCompatibleDose)
            .put("existingPlannedExposureFrequency", trace.existingPlannedExposureFrequency)
            .put("residualDose", trace.residualDose).put("candidatePool", JSONArray(trace.candidatePool))
            .put("selectedStableKey", trace.selectedStableKey).put("selectionReasons", JSONArray(trace.selectionReasons))
            .put("prescriptionCompatibility", trace.prescriptionCompatibility).put("requestedUnits", trace.requestedUnits)
            .put("authorizedUnits", trace.authorizedUnits).put("materializedUnits", trace.materializedUnits)
            .put("targetCompatibleMaterializedUnits", trace.targetCompatibleMaterializedUnits)
            .put("selectedIdentity", trace.selectedIdentity?.let { JSONObject()
                .put("stableKey", it.stableKey).put("selectionRole", it.selectionRole) })
            .put("overrunUnits", trace.overrunUnits)
            .put("ordinarySameKeyCompatibleUnits", trace.ordinarySameKeyCompatibleUnits)
            .put("shortfall", trace.shortfall).put("finalReasonCodes", JSONArray(trace.finalReasonCodes))
        }))
        .put("athleteNeedsProfile", athleteNeedsProfile?.let { profile -> JSONObject()
            .put("generatedAtCutoff", profile.generatedAtCutoff.toString())
            .put("shadowOnly", profile.shadowOnly)
            .put("prescriptionAuthority", profile.prescriptionAuthority)
            .put("maintenanceDomains", JSONArray(profile.maintenanceDomains))
            .put("unresolved", JSONArray(profile.unresolved))
            .put("evidenceSummary", JSONObject()
                .put("recentWindowDays", profile.evidenceSummary.recentWindowDays)
                .put("currentWindowDays", profile.evidenceSummary.currentWindowDays)
                .put("previousWindowDays", profile.evidenceSummary.previousWindowDays)
                .put("contextWindowDays", profile.evidenceSummary.contextWindowDays)
                .put("historyDays", profile.evidenceSummary.historyDays)
                .put("source", profile.evidenceSummary.source)
                .put("notes", JSONArray(profile.evidenceSummary.notes)))
            .put("qualityNeeds", JSONArray(profile.qualityNeeds.map { need -> JSONObject()
                .put("quality", need.quality.name)
                .put("relevance", need.relevance.name)
                .put("currentExposure", need.currentExposure.name)
                .put("response", need.response.name)
                .put("decision", need.decision.name)
                .put("confidence", need.confidence.name)
                .put("reasonCodes", JSONArray(need.reasonCodes))
                .put("evidence", JSONArray(need.evidence))
                .put("exposure", JSONObject()
                    .put("recent7dUnits", need.exposure.recent7dUnits)
                    .put("current28dUnits", need.exposure.current28dUnits)
                    .put("previous28dUnits", need.exposure.previous28dUnits)
                    .put("context56dUnits", need.exposure.context56dUnits)
                    .put("recent7dBouts", need.exposure.recent7dUnits)
                    .put("current28dBouts", need.exposure.current28dUnits)
                    .put("previous28dBouts", need.exposure.previous28dUnits)
                    .put("context56dBouts", need.exposure.context56dUnits)
                    .put("directSessions", need.exposure.directSessions)
                    .put("directUnits", need.exposure.directUnits)
                    .put("directBouts", need.exposure.directUnits)
                    .put("supportiveSessions", need.exposure.supportiveSessions)
                    .put("supportiveUnits", need.exposure.supportiveUnits)
                    .put("supportiveBouts", need.exposure.supportiveUnits)
                    .put("provisionalStrengthLikeUnits", need.exposure.provisionalStrengthLikeUnits)
                    .put("provisionalHypertrophyLikeUnits", need.exposure.provisionalHypertrophyLikeUnits)
                    .put("ambiguousRealizedStimulusUnits", need.exposure.ambiguousRealizedStimulusUnits)
                    .put("strengthLikeBouts", need.exposure.provisionalStrengthLikeUnits)
                    .put("hypertrophyLikeBouts", need.exposure.provisionalHypertrophyLikeUnits)
                    .put("ambiguousBouts", need.exposure.ambiguousRealizedStimulusUnits))
            }))
            .put("sportTaskNeeds", JSONArray(profile.sportTaskNeeds.map { need -> JSONObject()
                .put("task", need.task)
                .put("relevance", need.relevance.name)
                .put("currentExposure", need.currentExposure.name)
                .put("response", need.response.name)
                .put("decision", need.decision.name)
                .put("confidence", need.confidence.name)
                .put("structuredDirectUnits", need.structuredDirectUnits)
                .put("structuredSupportiveUnits", need.structuredSupportiveUnits)
                .put("structuredDirectSessions", need.structuredDirectSessions)
                .put("structuredSupportiveSessions", need.structuredSupportiveSessions)
                .put("structuredDirectBouts", need.structuredDirectUnits)
                .put("structuredSupportiveBouts", need.structuredSupportiveUnits)
                .put("sportContextLoad", need.sportContextLoad)
                .put("reasonCodes", JSONArray(need.reasonCodes))
                .put("evidence", JSONArray(need.evidence))
            }))
            .put("executionModifiers", JSONArray(profile.executionModifiers.map { modifier -> JSONObject()
                .put("domain", modifier.domain)
                .put("stableKeys", JSONArray(modifier.stableKeys))
                .put("modifier", modifier.modifier.name)
                .put("reasonCodes", JSONArray(modifier.reasonCodes))
            }))
        })
        .put("athleteStimulusNeedProfile", athleteStimulusNeedProfile?.toCompactJson())
        .put("objectiveExposure", JSONObject(objectiveExposure))
        .put("trainingStateAssessment", trainingStateAssessment?.toJson())
        .put("weeklyFrequencyEvidence", weeklyFrequencyEvidence?.toJson())
        .put("residualCompletion", residualCompletion?.toJson())
        .put("dayRebalancing", dayRebalancing?.toJson())
        .put("postSplitReflow", postSplitReflow?.toJson())
        .put("authorizedScheduling", authorizedScheduling?.toJson())
        .put("frequencyDemand", frequencyDemand?.toJson())
        .put("frequencyExpansion", frequencyExpansion?.toJson())
        .put("anchorTransitions", JSONArray(anchorTransitions.map { transition -> JSONObject()
            .put("stableKey", transition.stableKey)
            .put("observedStyle", transition.observedStyle.name)
            .put("structureTreatment", transition.structureTreatment.name)
            .put("doseTreatment", transition.doseTreatment.name)
            .put("continuityScore", transition.continuityScore)
            .put("localDoseFactor", transition.localDoseFactor)
            .put("courtInterference", transition.adaptation.courtInterference)
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
            .put("resistance", budget.resistance?.let { resistance -> JSONObject()
                .put("resistanceNormalWeekCount", resistance.resistanceNormalWeekCount)
                .put("resistanceActiveWeekCount", resistance.resistanceActiveWeekCount)
                .put("resistanceWeeklyQ25", resistance.resistanceWeeklyQ25)
                .put("resistanceWeeklyMedian", resistance.resistanceWeeklyMedian)
                .put("resistanceWeeklyQ75", resistance.resistanceWeeklyQ75)
                .put("resistanceBaselineSource", resistance.resistanceBaselineSource)
                .put("resistanceBaselineSets", resistance.resistanceBaselineSets)
                .put("resistanceCoreTarget", resistance.resistanceCoreTarget)
                .put("resistanceDayRelease", resistance.resistanceDayRelease)
                .put("resistanceTimeCeiling", resistance.resistanceTimeCeiling)
                .put("resistanceUsefulDemand", resistance.resistanceUsefulDemand)
                .put("resistanceTargetSets", resistance.resistanceTargetSets)
                .put("resistanceAuthorizedBeforeCompletion", resistance.resistanceAuthorizedBeforeCompletion)
                .put("resistanceCompletionAddedSets", resistance.resistanceCompletionAddedSets)
                .put("resistanceFinalSets", resistance.resistanceFinalSets)
                .put("secondsPerResistanceSet", resistance.secondsPerResistanceSet) })
            .put("domainTargets", budget.domains?.let { domains -> JSONObject()
                .put("resistanceTargetSets", domains.resistance.resistanceTargetSets)
                .put("structuredBadmintonTargetBouts", domains.structuredBadminton.targetBouts)
                .put("athleticPerformanceTargetBouts", domains.athleticPerformance.targetBouts)
                .put("structuredBadmintonFinalBouts", domains.structuredBadminton.finalBouts)
                .put("athleticPerformanceFinalBouts", domains.athleticPerformance.finalBouts) })
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
    (decision.canonicalPlacementFinalFingerprint
        ?: if (decision.postSplitReflow != null) decision.postSplitReflow.finalFingerprint
    else if (decision.frequencyExpansion != null) decision.originalGenerationFingerprint
    else decision.dayRebalancing?.finalFingerprint ?: decision.residualCompletion?.completedFingerprint ?: decision.originalGenerationFingerprint).let { generated ->
        generated.isNotBlank() && generated != finalFingerprint
    }

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
            source = "CANONICAL_EXERCISE_LOCAL_POSTERIOR:$revisionKey",
            posteriorLogVariance = ordered.last().posteriorLogVariance,
            referenceDate = LocalDate.parse(ordered.last().sessionDate),
            twoSidedObservationCount = ordered.count { it.sessionLikelihoodProper },
            baselineEstablished = ordered.last().baselineEstablishedAfter
        )
    }

internal data class StrengthPersonalCurveThetaRecord(
    val subjectKey: String,
    val updatedAtMillis: Long,
    val meanTheta: Double
)

internal fun strengthPersonalCurveThetaAsOf(
    revisionKey: String,
    cutoff: LocalDate,
    records: List<StrengthPersonalCurveThetaRecord>,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
): Map<String, Double> {
    val cutoffExclusive = cutoff.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val prefix = "$revisionKey|"
    return records.asSequence()
        .filter { it.subjectKey.startsWith(prefix) }
        .filter { it.updatedAtMillis < cutoffExclusive }
        .mapNotNull { record ->
            val stableSubject = record.subjectKey.removePrefix(prefix)
            record.meanTheta.takeIf(Double::isFinite)?.let { stableSubject to it }
        }
        .sortedBy { it.first }
        .toMap()
}
