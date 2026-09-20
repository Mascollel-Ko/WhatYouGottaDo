package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Private artifacts only. BEFORE runs without modifying production source; reflective access
 * observes the existing snapshot authority rather than introducing a diagnostic production API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrainingStateRealBackupComparisonTest {
    @Test fun captureExactRealUserGeneration() = runBlocking {
        val source = System.getenv("WGTD_REAL_BACKUP_PATH")?.let(::File)
        val phase = System.getenv("WGTD_COMPARISON_PHASE")
        assumeTrue(source?.isFile == true && phase != null && phase in setOf("before", "after", "audit"))
        val directory = File(requireNotNull(System.getenv("WGTD_COMPARISON_DIR"))).apply { mkdirs() }
        require(phase in setOf("after", "audit")) { "Historical BEFORE stays frozen; use audit for current standalone verification" }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.importRecordsBackup(Uri.fromFile(source!!))
            val importedAnnotationKeys = WeeklyContextAnnotationJson.read(
                db.appMetaDao().value(WeeklyContextAnnotationJson.KEY)).keys
            val history = db.workoutDao().allEntriesWithSets()
            val cutoff = history.filter { row -> row.sets.any(WorkoutSet::confirmed) }.maxOf { LocalDate.parse(it.entry.date) }
            val request = ProgramSkeletonRequest("실제 백업 교정 검증", ProgramGoal.BODYBUILDING,4,90,
                emptySet(),"",.5,"AUTO",ProgramPeriodizationType.AUTO,5)
            val constraints = PersonalizedGenerationConstraints(explicitSessionMinutes = 90)
            val originalAnswers = PersonalizedPlanningAnswers(mapOf(QUESTION_STRENGTH_INTENT to "MIXED",
                QUESTION_BADMINTON_INTENT to "ENABLED", QUESTION_FREE_WEIGHT to "WILLING"))
            val service = field(repository,"personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val editor = field(repository,"exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val preferences = invokeSuspending(service,"readPreferences",emptyArray(),emptyArray()) as PersonalizedPlanningPreferences
            val snapshot = invokeSuspending(service,"buildSnapshot",
                arrayOf(LocalDate::class.java,Map::class.java,PersonalizedPlanningPreferences::class.java),
                arrayOf(cutoff,metadata,preferences)) as PlanningHistorySnapshot
            val preflight = repository.preparePersonalizedProgram(request,constraints,cutoff)
            // Identical core request; unknown dated causes are a test assumption, not a user confirmation.
            val answers=PersonalizedPlanningAnswers(originalAnswers.values+preflight.questions.filter {
                it.id.startsWith(QUESTION_WEEK_CAUSE_PREFIX) }.associate { it.id to "UNKNOWN" }+
                mapOf(QUESTION_INTERRUPTION_FREQUENCY to "UNSURE"))
            val state = AthletePlanningStateBuilder().build(snapshot,answers)
            val plan = repository.generatePreparedPersonalizedProgram(preflight,answers)
            val ab = service.generatePreparedComparison(preflight, answers, metadata)
            val controlRows = plan.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.exerciseStableKey }))
            val comparisonControlRows = ab.control.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.exerciseStableKey }))
            assertEquals("CONTROL exercise/set/reps/load/placement parity", controlRows, comparisonControlRows)
            assertEquals("CONTROL fingerprint parity", plan.personalizedDecision?.originalGenerationFingerprint,
                ab.control.personalizedDecision?.originalGenerationFingerprint)
            val decision = requireNotNull(plan.personalizedDecision)
            val budget = requireNotNull(decision.planningBudget)
            val needs = requireNotNull(decision.athleteNeedsProfile)
            val input = JSONObject().put("cutoff",cutoff).put("request",json(request))
                .put("constraints",json(constraints)).put("answers",json(originalAnswers))
                .put("preferences",(json(preferences) as JSONObject).apply { remove("interruptionCause"); remove("interruptionFrequency"); remove("interruptionFrequencyAnsweredAtEpochMillis") })
            val report = JSONObject().put("input",input).put("recovery",json(snapshot.recoverySignals))
                .put("comparisonAuthority", if (phase == "audit") "CURRENT_AUDIT_ONLY_NO_HISTORICAL_BEFORE_COMPARISON" else "FROZEN_BEFORE_COMPARISON")
                .put("isConstrained",snapshot.recoverySignals.isConstrained)
                .put("systemicRecoveryPressure",AdaptationTransitionPlanner().systemicRecoveryPressure(snapshot.recoverySignals))
                .put("genericCourtLoad",snapshot.genericCourtLoad).put("budget",json(budget))
                .put("regionalAuthorityModeB", ab.experimental.personalizedDecision?.regionalPlanningAuthorityMode?.name)
                .put("programAWeek1", JSONArray(ab.control.items.filter { it.weekNumber == 1 }.map { item ->
                    JSONObject().put("day", item.dayOfWeek).put("exerciseStableKey", item.exerciseStableKey)
                        .put("exerciseName", item.exerciseName).put("sets", item.setPrescriptions.size)
                        .put("setPrescriptions", json(item.setPrescriptions)).put("restSeconds", item.restSeconds)
                }))
                .put("programBWeek1", JSONArray(ab.experimental.items.filter { it.weekNumber == 1 }.map { item ->
                    JSONObject().put("day", item.dayOfWeek).put("exerciseStableKey", item.exerciseStableKey)
                        .put("exerciseName", item.exerciseName).put("sets", item.setPrescriptions.size)
                        .put("setPrescriptions", json(item.setPrescriptions)).put("restSeconds", item.restSeconds)
                }))
                .put("regionalAuthorityTraces", json(ab.traces))
                .put("regionalAuthorityCounters", json(ab.counters))
                .put("regionalDifferences", JSONArray(ab.differences))
                .put("globalTargetComparisonA", json(ab.controlTargetComparison))
                .put("globalTargetComparisonB", json(ab.experimentalTargetComparison))
                .put("weeklyFrequencyEvidence",decision.weeklyFrequencyEvidence?.toJson())
                .put("athleteNeedsProfile",json(needs))
                .put("resolvedRequest",json(plan.request)).put("anchors",json(state.anchors))
                .put("transitions",json(decision.anchorTransitions)).put("gaps",json(decision.adaptationGaps))
                .put("items",JSONArray(plan.items.map { item ->
                    val transition = decision.anchorTransitions.firstOrNull { it.stableKey == item.exerciseStableKey }
                    JSONObject().put("item",json(item)).put("domain",snapshot.activityKind(item.exerciseStableKey))
                        .put("coverage",snapshot.movementCoverage(item.exerciseStableKey))
                        .put("transition",json(transition))
                        .put("directGaps",json(budget.execution?.representedGapCodesByStableKey?.get(item.exerciseStableKey).orEmpty()))
                        .put("supportiveGaps",json(budget.execution?.supportiveGapCodesByStableKey?.get(item.exerciseStableKey).orEmpty()))
                        .put("priorityAuthority",if (transition != null) transition.structureTreatment.name else item.trainingSlot)
                }))
            // Private audit only: inspect both true final programs with identical projection inputs.
            fun finalAudit(program: GeneratedProgramSkeleton): JSONObject {
                val rows = program.items.filter { it.weekNumber == 1 }
                val d = requireNotNull(program.personalizedDecision)
                return JSONObject().put("items", json(rows)).put("request", json(program.request))
                    .put("fingerprint", d.originalGenerationFingerprint)
                    .put("regionalDiagnosis", json(d.regionalBottleneckDiagnosis))
                    .put("regionalDecisions", json(d.regionalTrainingDecisions))
                    .put("regionalTargets", json(d.regionalStimulusTargets))
                    .put("emphasis", json(d.programEmphasisLabels))
                    .put("regionalOwnedSets", rows.filter { regionalSelectionIdentity(program, it) in ab.traces.mapNotNull { trace -> trace.selectedIdentity } }.sumOf { it.setPrescriptions.size })
                    .put("ownerAudit", JSONArray(listOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN).map { region ->
                        val regionRows = rows.filter { snapshot.movementCoverage(it.exerciseStableKey) == region }
                        val identities = ab.traces.filter { it.region == region }.mapNotNull { it.selectedIdentity }.toSet()
                        fun compatible(items: List<ProgramSkeletonItem>) = items.sumOf { row -> row.setPrescriptions.count {
                            provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.STRENGTH_LIKE
                        } }
                        JSONObject().put("region", region.name).put("quality", "STRENGTH")
                            .put("ordinaryCompatible", compatible(regionRows.filter { regionalSelectionIdentity(program, it) !in identities }))
                            .put("regionalCompatible", compatible(regionRows.filter { regionalSelectionIdentity(program, it) in identities }))
                            .put("authorized", if (program === ab.experimental) ab.traces.filter { it.region == region }.sumOf { it.authorizedUnits } else 0)
                            .put("overrun", if (program === ab.experimental) ab.traces.filter { it.region == region }.sumOf { it.overrunUnits } else 0)
                    }))
                    .put("frequencyDemand", d.frequencyDemand?.toJson())
                    .put("frequencyExpansion", d.frequencyExpansion?.toJson())
                    .put("resistanceSets", rows.filter { snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.RESISTANCE }.sumOf { it.setCount })
                    .put("structuredBadmintonBouts", rows.filter { snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf { it.setCount })
                    .put("athleticBouts", rows.filter { snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf { it.setCount })
                    .put("days", JSONArray(rows.groupBy { it.dayOfWeek }.toSortedMap().map { (day, items) ->
                        JSONObject().put("day", day).put("minutes", items.sumOf { it.estimatedDurationSeconds } / 60.0)
                            .put("ofi", json(snapshot.planDayProjection?.evaluate(items)))
                            .put("ofiFeasible", snapshot.planDayProjection?.evaluate(items)?.feasible)
                    }))
                    .put("tissue", snapshot.planWeekTissueProjection?.evaluate(rows,
                        program.weekPlans.firstOrNull { it.weekIndex == 1 }?.targetRpeMax ?: Double.NaN)?.toJson())
            }
            report.put("finalAuditA", finalAudit(ab.control)).put("finalAuditB", finalAudit(ab.experimental))
            val weeks = plan.items.groupBy { it.weekNumber }.toSortedMap().mapValues { (_,items) ->
                mapOf("resistanceSets" to items.filter { snapshot.activityKind(it.exerciseStableKey)==PlannedActivityKind.RESISTANCE }.sumOf { it.setCount },
                    "structuredBouts" to items.filter { snapshot.activityKind(it.exerciseStableKey)==PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf { it.setCount },
                    "athleticBouts" to items.filter { snapshot.activityKind(it.exerciseStableKey)==PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf { it.setCount },
                    "totalUnits" to items.sumOf { it.setCount }, "minutes" to items.sumOf { it.estimatedDurationSeconds }/60.0,
                    "distinctExercises" to items.map { it.exerciseStableKey }.distinct().size,
                    "highForceItems" to items.count { it.neuromuscularStressLevel in setOf("HIGH","VERY_HIGH") },
                    "dailyMinutes" to items.groupBy { it.dayOfWeek }.toSortedMap().mapValues { (_,rows)-> rows.sumOf { it.estimatedDurationSeconds }/60.0 })
            }
            report.put("weeklyTotals",json(weeks))
            if (phase in setOf("after", "audit")) {
                report.put("trainingStateAssessment",decision.trainingStateAssessment?.toJson())
                    .put("additionalContextAnswers",JSONObject(answers.values-originalAnswers.values.keys))
                    .put("additionalContextSource",if (System.getenv("WGTD_INTERRUPTION_CAUSE")==null) "TEST_ASSUMPTION_UNSURE_NOT_USER_CONFIRMED" else "EXPLICIT_TEST_CONTEXT")
                val actual=decision.trainingStateAssessment!!
                val programId=repository.saveGeneratedProgram(null,plan)
                val saved=JSONObject(db.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
                assertEquals(actual.toJson().toString(),saved.getJSONObject("trainingStateAssessment").toString())
                val preferencesBefore=db.appMetaDao().value(PersonalizedProgramPlanningService.PREFERENCES_KEY)
                val annotationsBefore=db.appMetaDao().value(WeeklyContextAnnotationJson.KEY)
                assertEquals(importedAnnotationKeys + answers.weekAnnotations().keys,WeeklyContextAnnotationJson.read(annotationsBefore).keys)
                val roundTrip=File(directory,"v0131_private_roundtrip.csv")
                repository.exportRecordsBackup(Uri.fromFile(roundTrip))
                val restoredDb=Room.inMemoryDatabaseBuilder(context,TrainingDatabase::class.java).allowMainThreadQueries().build()
                try {
                    TrainingRepository(restoredDb,context).importRecordsBackup(Uri.fromFile(roundTrip))
                    val restored=JSONObject(restoredDb.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
                    assertEquals(saved.getJSONObject("trainingStateAssessment").toString(),restored.getJSONObject("trainingStateAssessment").toString())
                    assertEquals(preferencesBefore,restoredDb.appMetaDao().value(PersonalizedProgramPlanningService.PREFERENCES_KEY))
                    assertEquals(annotationsBefore,restoredDb.appMetaDao().value(WeeklyContextAnnotationJson.KEY))
                    report.put("persistedWeekAnnotations",JSONObject(requireNotNull(annotationsBefore)))
                    assertNotNull(restoredDb.programDao().findProgramByStableKey(db.programDao().findProgram(programId)!!.stableKey))
                    report.put("backupRestore","PASS_ASSESSMENT_PREFERENCES_WEEK_ANNOTATIONS_PROGRAM")
                } finally { restoredDb.close() }
            }
            val output = File(directory,"v0131_$phase.json")
            val rendered = report.toString(2)+"\n"
            if (phase == "before" && output.exists()) assertEquals("BEFORE must stay frozen",output.readText(),rendered)
            else output.writeText(rendered)
            File(directory,"v0131_$phase.txt").writeText(buildString {
                appendLine(input.toString(2)); appendLine(report.getJSONObject("recovery").toString(2))
                appendLine("isConstrained=${report.getBoolean("isConstrained")}"); appendLine(json(budget))
                appendLine("weeklyTotals=${json(weeks)}")
                appendLine("ATHLETE_NEEDS_QUALITY quality|relevance|exposure|response|decision|confidence|directUnits|supportiveUnits")
                needs.qualityNeeds.forEach { need -> appendLine(listOf(need.quality, need.relevance, need.currentExposure,
                    need.response, need.decision, need.confidence, need.exposure.directUnits, need.exposure.supportiveUnits).joinToString("|")) }
                appendLine("ATHLETE_NEEDS_TASK task|relevance|directUnits|supportiveUnits|contextLoad|decision")
                needs.sportTaskNeeds.forEach { need -> appendLine(listOf(need.task, need.relevance, need.structuredDirectUnits,
                    need.structuredSupportiveUnits, need.sportContextLoad, need.decision).joinToString("|")) }
                decision.trainingDecisionPortfolio?.let { portfolio ->
                    appendLine("TRAINING_DECISION quality|need|action|priority|confidence")
                    portfolio.qualityDecisions.forEach { row -> appendLine(listOf(row.quality, row.needDecision, row.action, row.priority, row.confidence).joinToString("|")) }
                    appendLine("TRAINING_DECISION_TASK task|need|action|priority|confidence|explicitUserTaskPriority")
                    portfolio.taskDecisions.forEach { row -> appendLine(listOf(row.task, row.needDecision, row.action, row.priority, row.confidence, row.explicitUserTaskPriority).joinToString("|")) }
                }
                decision.targetStimulusPlan?.let { targetPlan ->
                    appendLine("TARGET_STIMULUS quality|action|priority|eligibleWeekCount|weeklyDirectQ25|weeklyDirectMedian|weeklyDirectQ75|exposureWeekDirectQ25|exposureWeekDirectMedian|exposureWeekDirectQ75|directExposureWeekCount|directExposureWeekFrequency|targetWeeklyMin|targetWeeklyPreferred|targetWeeklyMax|targetExposureWeekPreferred|targetExposureWeekFrequency|numericAuthority|plannedCapabilityUnits|comparisonStatus")
                    targetPlan.qualityTargets.forEach { row ->
                        val comparison = decision.targetPlanComparison?.qualityComparisons?.firstOrNull { it.quality == row.quality }
                        appendLine(listOf(row.quality, row.action, row.priority, row.baseline?.eligibleWeekCount,
                            row.baseline?.weeklyDirectUnitsQ25, row.baseline?.weeklyDirectUnitsMedian, row.baseline?.weeklyDirectUnitsQ75,
                            row.baseline?.exposureWeekDirectUnitsQ25, row.baseline?.exposureWeekDirectUnitsMedian, row.baseline?.exposureWeekDirectUnitsQ75,
                            row.baseline?.directExposureWeekCount, row.baseline?.directExposureWeekFrequency,
                            row.targetWeeklyDirectUnitsMin, row.targetWeeklyDirectUnitsPreferred, row.targetWeeklyDirectUnitsMax,
                            row.targetExposureWeekDirectUnitsPreferred, row.targetExposureWeekFrequency, row.numericAuthority,
                            comparison?.plannedCapabilityUnits, comparison?.status).joinToString("|"))
                    }
                    appendLine("TARGET_STIMULUS_TASK task|action|priority|eligibleWeekCount|weeklyDirectQ25|weeklyDirectMedian|weeklyDirectQ75|exposureWeekDirectQ25|exposureWeekDirectMedian|exposureWeekDirectQ75|directExposureWeekCount|directExposureWeekFrequency|targetWeeklyMin|targetWeeklyPreferred|targetWeeklyMax|targetExposureWeekPreferred|targetExposureWeekFrequency|numericAuthority|plannedCapabilityUnits|comparisonStatus")
                    targetPlan.taskTargets.forEach { row ->
                        val comparison = decision.targetPlanComparison?.taskComparisons?.firstOrNull { it.task == row.task }
                        appendLine(listOf(row.task, row.action, row.priority, row.baseline?.eligibleWeekCount,
                            row.baseline?.weeklyDirectUnitsQ25, row.baseline?.weeklyDirectUnitsMedian, row.baseline?.weeklyDirectUnitsQ75,
                            row.baseline?.exposureWeekDirectUnitsQ25, row.baseline?.exposureWeekDirectUnitsMedian, row.baseline?.exposureWeekDirectUnitsQ75,
                            row.baseline?.directExposureWeekCount, row.baseline?.directExposureWeekFrequency,
                            row.targetWeeklyDirectUnitsMin, row.targetWeeklyDirectUnitsPreferred, row.targetWeeklyDirectUnitsMax,
                            row.targetExposureWeekDirectUnitsPreferred, row.targetExposureWeekFrequency, row.numericAuthority,
                            comparison?.plannedCapabilityUnits, comparison?.status).joinToString("|"))
                    }
                }
                decision.targetPlanComparison?.let { comparison ->
                    appendLine("TARGET_COMPARISON quality|historicalExposureWeekFrequency|historicalExposureWeekDosePreferred|plannedCapabilityUnits|plannedCapabilityUnitsPerWeek|plannedExposureWeekCount|plannedExposureWeekFrequency|plannedExposureWeekDose|weeklyDoseStatus|frequencyStatus|exposureWeekDoseStatus|overallStatus")
                    comparison.qualityComparisons.forEach { row -> appendLine(listOf(row.quality,
                        row.historicalTargetExposureFrequency, row.historicalTargetExposureWeekUnitsPreferred,
                        row.plannedCapabilityUnits, row.plannedCapabilityUnitsPerWeek, row.plannedExposureWeekCount,
                        row.plannedExposureWeekFrequency, row.plannedExposureWeekUnitsMedian,
                        row.weeklyDoseStatus, row.frequencyStatus, row.exposureWeekDoseStatus, row.status).joinToString("|")) }
                    appendLine("TARGET_COMPARISON_TASK task|historicalExposureWeekFrequency|historicalExposureWeekDosePreferred|plannedCapabilityUnits|plannedCapabilityUnitsPerWeek|plannedExposureWeekCount|plannedExposureWeekFrequency|plannedExposureWeekDose|weeklyDoseStatus|frequencyStatus|exposureWeekDoseStatus|overallStatus")
                    comparison.taskComparisons.forEach { row -> appendLine(listOf(row.task,
                        row.historicalTargetExposureFrequency, row.historicalTargetExposureWeekUnitsPreferred,
                        row.plannedCapabilityUnits, row.plannedCapabilityUnitsPerWeek, row.plannedExposureWeekCount,
                        row.plannedExposureWeekFrequency, row.plannedExposureWeekUnitsMedian,
                        row.weeklyDoseStatus, row.frequencyStatus, row.exposureWeekDoseStatus, row.status).joinToString("|")) }
                }
                if (decision.regionalBottleneckDiagnosis.isNotEmpty()) {
                    appendLine("REGIONAL_DIAGNOSIS region|strengthRequirement|eligibleWeekCount|strengthWeeklyQ25|strengthWeeklyMedian|strengthWeeklyQ75|strengthExposureWeekQ25|strengthExposureWeekMedian|strengthExposureWeekQ75|strengthExposureWeekCount|strengthExposureFrequency|regionalStrengthResponse|validStrengthObservationCount|hypertrophyWeeklyQ25|hypertrophyWeeklyMedian|hypertrophyWeeklyQ75|hypertrophyExposureWeekMedian|hypertrophyExposureFrequency|recoveryConstraint|sportLoadInterference|specificityContinuity|limitingFactors|primaryInterpretation|confidence|reasonCodes")
                    decision.regionalBottleneckDiagnosis.forEach { row ->
                        val strength = row.strengthDoseBand
                        val hypertrophy = row.hypertrophyDoseBand
                        appendLine(listOf(row.region, row.requirement, strength.eligibleWeekCount,
                            strength.weeklyUnitsQ25, strength.weeklyUnitsMedian, strength.weeklyUnitsQ75,
                            strength.exposureWeekUnitsQ25, strength.exposureWeekUnitsMedian, strength.exposureWeekUnitsQ75,
                            strength.directExposureWeekCount, strength.directExposureWeekFrequency,
                            row.performanceResponse, row.validStrengthObservationCount,
                            hypertrophy.weeklyUnitsQ25, hypertrophy.weeklyUnitsMedian, hypertrophy.weeklyUnitsQ75,
                            hypertrophy.exposureWeekUnitsMedian, hypertrophy.directExposureWeekFrequency,
                            row.recoveryConstraint, row.sportLoadInterference, row.specificityContinuity,
                            row.limitingFactors, row.primaryInterpretation, row.confidence, row.reasonCodes).joinToString("|"))
                    }
                }
                appendLine("PROGRAM_EMPHASIS label|region|quality|plannedUnits")
                decision.programEmphasisLabels.forEach { row -> appendLine(listOf("${row.region.name}_${row.quality.name}", row.region, row.quality, row.plannedUnits).joinToString("|")) }
                appendLine("PROGRAM_A_WEEK_1")
                ab.control.items.filter { it.weekNumber == 1 }.sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex })).forEach { item ->
                    appendLine("D${item.dayOfWeek} ${item.exerciseName} [${item.exerciseStableKey}] ${item.setPrescriptions} rest=${item.restSeconds}")
                }
                appendLine("PROGRAM_B_WEEK_1")
                ab.experimental.items.filter { it.weekNumber == 1 }.sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex })).forEach { item ->
                    appendLine("D${item.dayOfWeek} ${item.exerciseName} [${item.exerciseStableKey}] ${item.setPrescriptions} rest=${item.restSeconds}")
                }
                appendLine("REGIONAL_AUTHORITY_TRACE")
                ab.traces.forEach { trace -> appendLine(listOf(trace.region, trace.trainingDecision, trace.targetQuality,
                    trace.targetAction, trace.targetWeeklyDose, trace.existingPlannedCompatibleDose,
                    trace.residualDose, trace.selectedStableKey, trace.materializedUnits, trace.shortfall,
                    trace.finalReasonCodes).joinToString("|")) }
                plan.items.forEach { appendLine("W${it.weekNumber}/D${it.dayOfWeek} ${it.exerciseName} [${it.exerciseStableKey}] domain=${snapshot.activityKind(it.exerciseStableKey)} role=${it.trainingSlot} ${it.setPrescriptions} rest=${it.restSeconds} seconds=${it.estimatedDurationSeconds}") }
            })
            run {
                val revision=db.strengthPosteriorDao().revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
                    ?.takeIf { it.status==StrengthModelRevisionPolicy.STATUS_ACTIVE && StrengthModelRevisionPolicy.isCompatible(it) }
                val posterior=revision?.let { db.strengthPosteriorDao().historyForRevision(it.revisionKey) }.orEmpty()
                @Suppress("UNCHECKED_CAST")
                val profiles=field(service,"canonicalOfiAxisProfiles") as Map<String,CanonicalOfiAxisProfile>
                val series=DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(metadata.values),profiles,
                    dailyCanonicalStrengthPosterior(posterior,field(service,"strengthPerformanceRegistry") as StrengthPerformanceRegistry))
                    .calculateSeries(cutoff,56,db.exerciseDao().allExercises(),history.filter { LocalDate.parse(it.entry.date)<=cutoff },
                        db.initialUserProfileDao().profile(),db.dailyMetricDao().metricsUntil(cutoff.toString())).map { it.state }
                val numerical=JSONObject().put("cutoff",cutoff).put("daily",json(series))
                    .put("records",json(snapshot.allConfirmedSets)).put("signals",json(snapshot.canonicalStrengthSignals))
                    .put("recovery",json(snapshot.recoverySignals)).put("anchors",json(state.anchors))
                    .put("metadata",json(snapshot.metadata)).put("domains",json(snapshot.exercises.keys.associateWith(snapshot::activityKind)))
                    .put("coverage",json(snapshot.exercises.keys.associateWith(snapshot::movementCoverage)))
                    .put("restSeconds",json(snapshot.exercises.mapValues { it.value.defaultRestSeconds }))
                    .put("weeklyCourtLoad",JSONArray(snapshot.weeklyCourtLoad.toSortedMap().map { (end,load) ->
                        JSONObject().put("end",end.toString()).put("load",load)
                    })).put("hardRestrictedModes",json(snapshot.hardRestrictedModes))
                    .put("weekAnnotations",JSONObject(answers.weekAnnotations().mapKeys { it.key.toString() }.mapValues { (_,a) ->
                        JSONObject().put("cause",a.cause.name).put("source",a.source.name)
                    })).put("interruptionFrequency","UNSURE")
                File(directory,"v0131_numerical_inputs.json").writeText(numerical.toString(2)+"\n")
            }
            if (phase == "after") assertEquals(JSONObject(File(directory,"v0131_before.json").readText()).getJSONObject("input").toString(),input.toString())
            // All regional traces, including nonnumeric HOLD/PRESERVE, must retain their own authority.
            ab.traces.forEach { trace ->
                assertEquals("${trace.region}: explicit overrun", 0, trace.overrunUnits)
                assertTrue("${trace.region}: materialized exceeds authorization", trace.targetCompatibleMaterializedUnits <= trace.authorizedUnits)
                assertTrue("${trace.region}: authorization exceeds residual", trace.authorizedUnits <= trace.residualDose)
                if (trace.targetAction in setOf(RegionalTargetAction.HOLD, RegionalTargetAction.PRESERVE)) {
                    assertEquals(0, trace.residualDose)
                    assertEquals(0, trace.authorizedUnits)
                    assertEquals(0, trace.materializedUnits)
                }
                if (trace.diagnosis.recoveryConstraint && trace.performanceResponse != TrainingResponseState.POSITIVE_RESPONSE) {
                    assertEquals(RegionalTrainingDecision.HOLD_FOR_RECOVERY, trace.trainingDecision)
                }
            }
            val bounded = requireNotNull(ab.experimental.personalizedDecision?.frequencyDemand?.boundedMaterialAllocation)
            assertEquals("total unfunded legitimate demand", (bounded.totalLegitimateDemand - bounded.fundedDemand).coerceAtLeast(0),
                bounded.toJson().getInt("deferredLegitimateDemandRemaining"))
            bounded.owners.forEach { owner ->
                assertTrue("${owner.owner}: funded exceeds legitimate maximum", owner.finalFundedUnits <= owner.maximumUnits)
                assertTrue("${owner.owner}: materialized exceeds funded", owner.materializedUnits <= owner.finalFundedUnits)
            }
            assertEquals("bounded audit physical count", ab.experimental.items.filter { it.weekNumber == 1 }.sumOf { it.setPrescriptions.size },
                bounded.materializedDemand)
            assertTrue("experimental completion must not invent semantic owners", ab.experimental.personalizedDecision?.residualCompletion?.additions.orEmpty().isEmpty())
            System.getenv("WGTD_CONTROL_BASELINE_PATH")?.let { baselinePath ->
                val before = JSONObject(File(baselinePath).readText()).getJSONObject("finalAuditA")
                val after = report.getJSONObject("finalAuditA")
                assertEquals("CONTROL fingerprint", before.getString("fingerprint"), after.getString("fingerprint"))
                assertEquals("CONTROL resolved request", before.getJSONObject("request").toString(), after.getJSONObject("request").toString())
                assertEquals("CONTROL exact final rows", before.getJSONArray("items").toString(), after.getJSONArray("items").toString())
            }
            println("COMPARISON_$phase ${output.absolutePath} weeks=${plan.request.durationWeeks} days=${plan.request.weeklyTrainingDays} totals=${json(weeks)}")
        } finally { db.close() }
    }

    private fun field(target:Any,name:String):Any = requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(target))
    private suspend fun invokeSuspending(target:Any,name:String,types:Array<Class<*>>,args:Array<Any>):Any? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            target.javaClass.getDeclaredMethod(name,*types,Continuation::class.java).apply { isAccessible=true }
                .invoke(target,*args,continuation)
        }
    private fun json(value:Any?):Any = when(value) {
        null -> JSONObject.NULL
        is String, is Boolean, is Number -> value
        is Enum<*> -> value.name
        is LocalDate -> value.toString()
        is Map<*,*> -> JSONObject().apply { value.entries.sortedBy { it.key.toString() }.forEach { put(it.key.toString(),json(it.value)) } }
        is Iterable<*> -> JSONArray(value.map(::json))
        else -> JSONObject().apply {
            value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers)||it.isSynthetic }.sortedBy { it.name }.forEach {
                it.isAccessible=true; put(it.name,json(it.get(value)))
            }
        }
    }
}
