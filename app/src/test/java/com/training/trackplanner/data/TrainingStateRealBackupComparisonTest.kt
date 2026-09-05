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
        assumeTrue(source?.isFile == true && phase != null && phase in setOf("before", "after"))
        val directory = File(requireNotNull(System.getenv("WGTD_COMPARISON_DIR"))).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.importRecordsBackup(Uri.fromFile(source!!))
            val history = db.workoutDao().allEntriesWithSets()
            val cutoff = history.filter { row -> row.sets.any(WorkoutSet::confirmed) }.maxOf { LocalDate.parse(it.entry.date) }
            val request = ProgramSkeletonRequest("실제 백업 교정 검증", ProgramGoal.BODYBUILDING,4,90,
                emptySet(),"",.5,"AUTO",ProgramPeriodizationType.AUTO,5)
            val constraints = PersonalizedGenerationConstraints(explicitSessionMinutes = 90)
            val originalAnswers = PersonalizedPlanningAnswers(mapOf(QUESTION_STRENGTH_INTENT to "MIXED",
                QUESTION_BADMINTON_INTENT to "ENABLED", QUESTION_FREE_WEIGHT to "WILLING"))
            val answers = if (phase == "after") PersonalizedPlanningAnswers(originalAnswers.values + mapOf(
                QUESTION_INTERRUPTION_CAUSE to (System.getenv("WGTD_INTERRUPTION_CAUSE") ?: "UNSURE"),
                QUESTION_INTERRUPTION_FREQUENCY to (System.getenv("WGTD_INTERRUPTION_FREQUENCY") ?: "UNSURE"))) else originalAnswers
            val service = field(repository,"personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val editor = field(repository,"exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val preferences = invokeSuspending(service,"readPreferences",emptyArray(),emptyArray()) as PersonalizedPlanningPreferences
            val snapshot = invokeSuspending(service,"buildSnapshot",
                arrayOf(LocalDate::class.java,Map::class.java,PersonalizedPlanningPreferences::class.java),
                arrayOf(cutoff,metadata,preferences)) as PlanningHistorySnapshot
            val state = AthletePlanningStateBuilder().build(snapshot,answers)
            val preflight = repository.preparePersonalizedProgram(request,constraints,cutoff)
            val plan = repository.generatePreparedPersonalizedProgram(preflight,answers)
            val decision = requireNotNull(plan.personalizedDecision)
            val budget = requireNotNull(decision.planningBudget)
            val input = JSONObject().put("cutoff",cutoff).put("request",json(request))
                .put("constraints",json(constraints)).put("answers",json(originalAnswers))
                .put("preferences",(json(preferences) as JSONObject).apply { remove("interruptionCause"); remove("interruptionFrequency") })
            val report = JSONObject().put("input",input).put("recovery",json(snapshot.recoverySignals))
                .put("isConstrained",snapshot.recoverySignals.isConstrained)
                .put("systemicRecoveryPressure",AdaptationTransitionPlanner().systemicRecoveryPressure(snapshot.recoverySignals))
                .put("genericCourtLoad",snapshot.genericCourtLoad).put("budget",json(budget))
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
            if (phase == "after") {
                report.put("trainingStateAssessment",decision.trainingStateAssessment?.toJson())
                    .put("additionalContextAnswers",JSONObject(answers.values-originalAnswers.values.keys))
                    .put("additionalContextSource",if (System.getenv("WGTD_INTERRUPTION_CAUSE")==null) "TEST_ASSUMPTION_UNSURE_NOT_USER_CONFIRMED" else "EXPLICIT_TEST_CONTEXT")
                val expected=JSONObject(File(directory,"v013_python_actual.json").readText())
                val actual=decision.trainingStateAssessment!!
                assertEquals(expected.getDouble("globalDoseFactor"),actual.globalDoseFactor,1e-9)
                assertEquals(expected.getString("state"),actual.state.name)
                val programId=repository.saveGeneratedProgram(null,plan)
                val saved=JSONObject(db.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
                assertEquals(actual.toJson().toString(),saved.getJSONObject("trainingStateAssessment").toString())
                val preferencesBefore=db.appMetaDao().value(PersonalizedProgramPlanningService.PREFERENCES_KEY)
                val roundTrip=File(directory,"v013_private_roundtrip.csv")
                repository.exportRecordsBackup(Uri.fromFile(roundTrip))
                val restoredDb=Room.inMemoryDatabaseBuilder(context,TrainingDatabase::class.java).allowMainThreadQueries().build()
                try {
                    TrainingRepository(restoredDb,context).importRecordsBackup(Uri.fromFile(roundTrip))
                    val restored=JSONObject(restoredDb.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
                    assertEquals(saved.getJSONObject("trainingStateAssessment").toString(),restored.getJSONObject("trainingStateAssessment").toString())
                    assertEquals(preferencesBefore,restoredDb.appMetaDao().value(PersonalizedProgramPlanningService.PREFERENCES_KEY))
                    assertNotNull(restoredDb.programDao().findProgramByStableKey(db.programDao().findProgram(programId)!!.stableKey))
                    report.put("backupRestore","PASS_ASSESSMENT_PREFERENCES_PROGRAM")
                } finally { restoredDb.close() }
            }
            val output = File(directory,"v013_$phase.json")
            val rendered = report.toString(2)+"\n"
            if (phase == "before" && output.exists()) assertEquals("BEFORE must stay frozen",output.readText(),rendered)
            else output.writeText(rendered)
            File(directory,"v013_$phase.txt").writeText(buildString {
                appendLine(input.toString(2)); appendLine(report.getJSONObject("recovery").toString(2))
                appendLine("isConstrained=${report.getBoolean("isConstrained")}"); appendLine(json(budget))
                appendLine("weeklyTotals=${json(weeks)}")
                plan.items.forEach { appendLine("W${it.weekNumber}/D${it.dayOfWeek} ${it.exerciseName} [${it.exerciseStableKey}] domain=${snapshot.activityKind(it.exerciseStableKey)} role=${it.trainingSlot} ${it.setPrescriptions} rest=${it.restSeconds} seconds=${it.estimatedDurationSeconds}") }
            })
            if (phase=="before") {
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
                    .put("weeklyCourtLoad",JSONArray((0..11).map { offset ->
                        val end=cutoff.minusDays(offset*7L)
                        JSONObject().put("end",end.toString()).put("load",
                            BadmintonPracticeLoadCalculator(RuntimeExerciseMetadataCatalog.of(metadata.values)).calculateRaw(
                                history.filter { LocalDate.parse(it.entry.date) in end.minusDays(6)..end },snapshot.exercises))
                    }))
                File(directory,"v013_numerical_inputs.json").writeText(numerical.toString(2)+"\n")
            } else assertEquals(JSONObject(File(directory,"v013_before.json").readText()).getJSONObject("input").toString(),input.toString())
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
