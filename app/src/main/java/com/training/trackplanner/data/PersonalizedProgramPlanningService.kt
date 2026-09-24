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
import com.training.trackplanner.data.personalized.PlanningRecoverySignals
import com.training.trackplanner.data.personalized.PersonalizedProgramBuilder
import com.training.trackplanner.data.personalized.PersonalizedPlannerProgressReporter
import com.training.trackplanner.data.personalized.PersonalizedPlannerStage
import com.training.trackplanner.data.personalized.PlanningHistorySnapshotBuilder
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
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceLoadResolver
import com.training.trackplanner.data.personalized.CanonicalStrengthReferenceIndex

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
        val recommendedDays = frequencyEvidence.recommendedDays
        val recommendedHorizon = horizonPlanner.choose(state, gaps, intent)
        val constraints = preflight.constraints
        val personalizedRequest = resolvePersonalizedRequest(preflight.request, constraints, state.programGoal, recommendedDays, recommendedHorizon)
        val priorId = appMetaDao.latestByPrefix("$DECISION_PREFIX%")?.value?.let(::decisionIdFromJson)
        val generated = programBuilder.build(snapshot, state, gaps, intent, personalizedRequest.durationWeeks, personalizedRequest, answers, priorId,
            explicitWeeklyDays = constraints.explicitWeeklyTrainingDays != null,
            frequency = com.training.trackplanner.data.personalized.PlanningFrequencyProvenance(frequencyEvidence,
                personalizedRequest.weeklyTrainingDays, if (constraints.explicitWeeklyTrainingDays != null)
                    com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER
                else com.training.trackplanner.data.personalized.PlanningFrequencySource.AUTO), progress = progress)
        progress.report(PersonalizedPlannerStage.FINAL)
        val legacyNeeds = athleteNeedsProfileEngine.analyze(snapshot, state, physicalQualityCatalog)
        val doseHistoryAnalyzer = QualityDoseHistoryAnalyzer()
        val doseHistory = doseHistoryAnalyzer.analyze(snapshot, state, physicalQualityCatalog)
        val stimulusNeeds = athleteStimulusNeedEngine.analyze(snapshot, state)
        val finalStimulusAudit = FinalStimulusNeedAudit().audit(generated, snapshot, physicalQualityCatalog)
        val ledgerDoseHistory = LedgerBackedQualityDoseHistoryAnalyzer().analyze(snapshot, state, doseHistory)
        // B3 consumes the already-built B1/B2 summaries. It is attached as a separate
        // observation-only portfolio and never enters the legacy target-plan chain.
        val stimulusPortfolio = StimulusTrainingDecisionPortfolioEngine().build(stimulusNeeds, ledgerDoseHistory)
        val stimulusTargetPlan = StimulusTargetPlanEngine().build(stimulusPortfolio, ledgerDoseHistory)
        val stimulusTargetPlanWithAudit = stimulusTargetPlan.copy(
            controlProgramAudit = StimulusTargetControlProgramAuditEngine().audit(
                stimulusTargetPlan,
                finalStimulusAudit,
                generated.request.durationWeeks
            )
        )
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
            return com.training.trackplanner.data.personalized.bindSplitParentProgression(withTargetComparison.copy(
                personalizedDecision = withTargetComparison.personalizedDecision!!.copy(
                    trainingDecisionPortfolio = portfolio,
                    targetStimulusPlan = targetPlan,
                    targetPlanComparison = comparison,
                    regionalBottleneckDiagnosis = regionalDiagnosis,
                    programEmphasisLabels = programEmphasis
                )
            ))
        }
        return com.training.trackplanner.data.personalized.bindSplitParentProgression(withShadowNeeds)
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
        val constraints = preflight.constraints
        val request = resolvePersonalizedRequest(
            preflight.request, constraints, state.programGoal, frequencyEvidence.recommendedDays,
            horizonPlanner.choose(state, gaps, intent)
        )
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
            explicitWeeklyDays = constraints.explicitWeeklyTrainingDays != null,
            frequency = com.training.trackplanner.data.personalized.PlanningFrequencyProvenance(
                frequencyEvidence, request.weeklyTrainingDays,
                if (constraints.explicitWeeklyTrainingDays != null) com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER
                else com.training.trackplanner.data.personalized.PlanningFrequencySource.AUTO
            ),
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
        val preferences = readPreferences()
        val control = generatePrepared(preflight, answers, metadata, progress)
        val targetPlan = requireNotNull(control.personalizedDecision?.athleteStimulusNeedProfile?.stimulusTargetPlanShadow) {
            "B5_REQUIRES_CANONICAL_B4_TARGET_PLAN"
        }
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED && state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val frequencyEvidence = WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size)
        val request = control.request
        val selectionPlan = StimulusTargetCandidateSelector().build(
            targetPlan = targetPlan,
            control = control,
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
            explicitWeeklyDays = preflight.constraints.explicitWeeklyTrainingDays != null,
            frequency = com.training.trackplanner.data.personalized.PlanningFrequencyProvenance(
                frequencyEvidence,
                request.weeklyTrainingDays,
                if (preflight.constraints.explicitWeeklyTrainingDays != null)
                    com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER
                else com.training.trackplanner.data.personalized.PlanningFrequencySource.AUTO
            ),
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
            controlAudit = targetPlan.controlProgramAudit,
            experimentalAudit = experimentalAudit
        )
        // Build one read-only owner side table from the already materialized A/B programs.
        // B6.1 must inspect this comparison; it must not regenerate a third program.
        val ownerKeys = buildSet {
            selectionPlan.selectedCandidates.forEach { add(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) }
            selectionPlan.traces.flatMap { it.controlDirectCapabilityIdentities }.forEach { stableKey ->
                control.items.filter { it.exerciseStableKey == stableKey }.forEach { item ->
                    add(StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole))
                }
            }
        }
        val materializedPrescriptions = (experimental.items + control.items)
            .asSequence()
            .map { item ->
                StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) to
                    PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)
            }
            .filter { it.first in ownerKeys }
            .toList()
            .associateBy({ it.first }, { it.second })
        val prescriptionPlan = StimulusPrescriptionRealizationPlanEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = snapshot,
            currentPrescriptions = materializedPrescriptions
        )
        val controlWithPlan = control.copy(
            personalizedDecision = control.personalizedDecision?.copy(
                athleteStimulusNeedProfile = control.personalizedDecision?.athleteStimulusNeedProfile?.copy(
                    stimulusPrescriptionRealizationPlanShadow = prescriptionPlan
                )
            )
        )
        val enrichedComparison = comparison.copy(
            control = controlWithPlan,
            prescriptionRealizationPlan = prescriptionPlan
        )
        return enrichedComparison.copy(
            experimentalReadinessAudit = StimulusExperimentalReadinessAuditEngine().audit(enrichedComparison)
        )
    }

    /**
     * Test/dev-only Phase B6.2 path. It performs exactly one unchanged CONTROL build and one
     * EXPERIMENTAL build. Exact Strength prescriptions are authorized from B4/B5 plus the
     * already-built CONTROL owner table before the experimental builder starts.
     */
    internal suspend fun generatePreparedStimulusPrescriptionMaterializationComparison(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): StimulusSelectionProgramComparison {
        val preferences = readPreferences()
        val control = generatePrepared(preflight, answers, metadata, progress)
        val targetPlan = requireNotNull(control.personalizedDecision?.athleteStimulusNeedProfile?.stimulusTargetPlanShadow) {
            "B6.2_REQUIRES_CANONICAL_B4_TARGET_PLAN"
        }
        val snapshot = buildSnapshot(preflight.cutoff, metadata, preferences, includeStimulusExposureLedger = true)
        val state = stateBuilder.build(snapshot, answers)
        require(state.strengthIntent != StrengthIntent.UNRESOLVED && state.badmintonIntent != BadmintonPlanningIntent.UNRESOLVED &&
            state.freeWeightWillingness != FreeWeightWillingness.UNRESOLVED) { "UNRESOLVED_PLANNING_INTENT_REQUIRES_PREFLIGHT" }
        val gaps = gapAnalyzer.analyze(snapshot, state)
        val intent = blockPlanner.decide(state, gaps)
        val frequencyEvidence = WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size)
        val request = control.request
        val selectionPlan = StimulusTargetCandidateSelector().build(
            targetPlan = targetPlan,
            control = control,
            snapshot = snapshot,
            state = state,
            request = request,
            physicalQualityCatalog = physicalQualityCatalog
        )
        val controlPrescriptions = control.items.asSequence()
            .map { item ->
                StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) to
                    PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)
            }.distinct().toList().toMap()
        val authorizationPlan = StimulusPrescriptionAuthorizationEngine().build(
            targetPlan = targetPlan,
            selectionPlan = selectionPlan,
            snapshot = snapshot,
            controlPrescriptions = controlPrescriptions
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
            explicitWeeklyDays = preflight.constraints.explicitWeeklyTrainingDays != null,
            frequency = com.training.trackplanner.data.personalized.PlanningFrequencyProvenance(
                frequencyEvidence,
                request.weeklyTrainingDays,
                if (preflight.constraints.explicitWeeklyTrainingDays != null)
                    com.training.trackplanner.data.personalized.PlanningFrequencySource.EXPLICIT_USER
                else com.training.trackplanner.data.personalized.PlanningFrequencySource.AUTO
            ),
            progress = progress,
            materialDemandOverride = selectionPlan.materialDemand,
            exactPrescriptionAuthorizationProvider = authorizationPlan.provider()
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
            controlAudit = targetPlan.controlProgramAudit,
            experimentalAudit = experimentalAudit
        )
        val materializationAudits = StimulusPrescriptionMaterializationAuditEngine().audit(
            authorizationPlan, experimental, snapshot
        )
        val enrichedComparison = comparison.copy(
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

    /**
     * Test/dev-only B8.0 evaluation. B6.2 builds CONTROL and EXPERIMENTAL exactly once; B8
     * consumes that existing comparison and only returns a future cutover authority decision.
     * The normal repository path remains CONTROL and no experimental object is persisted or
     * routed from this entry point.
     */
    internal suspend fun generatePreparedStimulusProductionCutoverEvaluation(
        preflight: PersonalizedPlanningPreflight,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): com.training.trackplanner.data.personalized.StimulusProductionCutoverEvaluation {
        val comparison = generatePreparedStimulusPrescriptionMaterializationComparison(
            preflight = preflight,
            answers = answers,
            metadata = metadata,
            progress = progress
        )
        val authority = com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityAuditEngine()
            .audit(comparison)
        return com.training.trackplanner.data.personalized.StimulusProductionCutoverEvaluation(
            comparison = comparison.copy(productionCutoverAuthority = authority),
            cutoverAuthority = authority
        )
    }

    /** Pure B8 seam for tests and diagnostics that already hold the B6/B7 comparison. */
    internal fun evaluateStimulusProductionCutover(
        comparison: com.training.trackplanner.data.personalized.StimulusSelectionProgramComparison
    ): com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityDecision =
        com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityAuditEngine().audit(comparison)

    /** Compatibility wrapper for callers that have not yet adopted the two-phase API. */
    suspend fun generate(
        request: ProgramSkeletonRequest,
        answers: PersonalizedPlanningAnswers,
        metadata: Map<String, RuntimeExerciseMetadata>,
        cutoff: LocalDate = LocalDate.now(),
        constraints: PersonalizedGenerationConstraints = PersonalizedGenerationConstraints(explicitSessionMinutes = request.sessionMinutes),
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE
    ): PersonalizedPlanningOutcome {
        val preflight = prepare(request, metadata, cutoff, constraints, progress)
        val unanswered = preflight.questions.filter { question ->
            question.options.none { it.value == answers.values[question.id] && it.value != "UNRESOLVED" }
        }
        return if (unanswered.isNotEmpty()) PersonalizedPlanningOutcome.Questions(unanswered)
        else PersonalizedPlanningOutcome.Generated(generatePrepared(preflight, answers, metadata, progress))
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
        .put("finalSavedFingerprint", finalSavedFingerprint).put("recoverySignalCodes", JSONArray(recoverySignalCodes))
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
    (if (decision.postSplitReflow != null) decision.postSplitReflow.finalFingerprint
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
            source = "CANONICAL_EXERCISE_LOCAL_POSTERIOR:$revisionKey"
        )
    }
