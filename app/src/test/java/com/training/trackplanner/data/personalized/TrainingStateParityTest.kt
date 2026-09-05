package com.training.trackplanner.data.personalized

import org.json.JSONArray
import org.json.JSONObject
import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TrainingStateParityTest {
    private fun fixture(name: String): TrainingStateInput {
        val file=listOf(File("../tools/planner_reference/fixtures/v013_training_state_golden.json"),
            File("tools/planner_reference/fixtures/v013_training_state_golden.json")).first { it.isFile }
        val cases=JSONArray(file.readText())
        return trainingInput((0 until cases.length()).map(cases::getJSONObject).first { it.getString("name")==name }.getJSONObject("input"))
    }
    private fun snapshot(input: TrainingStateInput): PlanningHistorySnapshot {
        val slots=mapOf("knee" to "MAIN_LOWER_STRENGTH","hinge" to "MAIN_HINGE_STRENGTH",
            "push" to "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY","pull" to "VERTICAL_PULL_STRENGTH")
        val exercises=input.domains.keys.associateWith { Exercise(it,it,"",equipment="BARBELL",equipmentTags="BARBELL",
            activityKind="EXERCISE",planningEligibility="PROGRAM_SELECTABLE",defaultRestSeconds=60) }
        return PlanningHistorySnapshot(input.cutoff,input.records,exercises,exercises.mapValues { (key,ex) ->
            RuntimeExerciseMetadataDefaults.forExercise(ex).copy(activityKind="EXERCISE",planningEligibility="PROGRAM_SELECTABLE",
                programSlot=slots.getValue(key),progressMetricType="LOAD_REPS",sourceConfidenceLevel="HIGH",
                analysisEligibility=MetadataTokenField.parse("STRENGTH_PROGRESS|HYPERTROPHY_VOLUME")) },emptyMap(),
            "STRENGTH",3.0,1.0,PersonalizedPlanningPreferences(StrengthIntent.MIXED,BadmintonPlanningIntent.ENABLED,FreeWeightWillingness.WILLING),
            canonicalStrengthSignals=input.signals,recoverySignals=input.recovery,dailyStrain=input.daily,weeklyCourtLoad=input.weeklyCourtLoad)
    }

    @Test fun sustainableReleaseIsBoundedByEvidenceAvailabilityAndDemand() {
        val input=fixture("older_successful_run")
        val source=snapshot(input)
        val assessment=TrainingStateAnalyzer().assess(input)
        val state=AthletePlanningStateBuilder().build(source,PersonalizedPlanningAnswers()).copy(trainingStateAssessment=assessment)
        val request=ProgramSkeletonRequest("test",ProgramGoal.BODYBUILDING,4,90,emptySet(),"",.5,"AUTO",ProgramPeriodizationType.AUTO,4)
        val capacity=ExecutionCapacityPlanner().envelope(source,state,request,30.0,100,assessment.globalDoseFactor)
        assertTrue(capacity.finalControllableUnits in 50..54)
        assertTrue(capacity.finalControllableUnits>assessment.sustainable.rawWeeklyMean!!)
        assertTrue(ExecutionCapacityPlanner().envelope(source,state,request,30.0,20,assessment.globalDoseFactor).finalControllableUnits<=20)
        assertFalse(assessment.copy(hardRestrictionCodes=listOf("BLOCKED")).permitsSustainableRelease)
        assertTrue(ExecutionCapacityPlanner().envelope(source,state,request.copy(sessionMinutes=5),30.0,100,assessment.globalDoseFactor).finalControllableUnits<capacity.finalControllableUnits)
    }

    @Test fun frequentInterruptionsProtectEarlierCoreWithoutRemovingUnits() {
        fun item(key: String,priority: Int,material: Boolean)=TimedPlannedExercise(
            PlannedExercise(key,"GAP","",priority,targetSets=2,material=material),
            PlannedPrescription("",List(2) { ProgramSetPrescription(it+1,8,0.0,0) },60,"TEST"))
        val rows=listOf(item("core",100,true),item("important",90,true),item("optional",70,false))
        val regular=TimedWeeklyPlacementPlanner().distribute(rows,4,45)
        val robust=TimedWeeklyPlacementPlanner().distribute(rows,4,45,robustSchedule=true)
        assertEquals(regular.first.values.flatten().sumOf { it.item.targetSets },robust.first.values.flatten().sumOf { it.item.targetSets })
        val coreDay=robust.first.entries.first { it.value.any { r -> r.item.stableKey=="core" } }.key
        val optionalDay=robust.first.entries.first { it.value.any { r -> r.item.stableKey=="optional" } }.key
        assertTrue(coreDay<optionalDay)
        assertTrue(robust.second.isEmpty())
    }

    @Test fun questionsAreConditionalAndUnknownIsAnExplicitValidContextAnswer() {
        val normal=snapshot(fixture("long_successful_run"))
        val normalState=AthletePlanningStateBuilder().build(normal,PersonalizedPlanningAnswers())
        assertEquals(3,PlanningQuestionPolicy().questions(normal,normalState,PersonalizedPlanningAnswers()).size)
        val interrupted=snapshot(fixture("confirmed_external").copy(interruptionCause=InterruptionCause.UNSURE))
        val state=AthletePlanningStateBuilder().build(interrupted,PersonalizedPlanningAnswers())
        val questions=PlanningQuestionPolicy().questions(interrupted,state,PersonalizedPlanningAnswers())
        assertEquals(5,questions.size)
        val answers=PersonalizedPlanningAnswers(questions.associate { it.id to if (it.id.startsWith("INTERRUPTION")) "UNSURE" else it.options.first().value })
        assertTrue(PlanningQuestionPolicy().questions(interrupted,state,answers).isEmpty())
        assertEquals(InterruptionCause.UNSURE,interrupted.trainingStateInput(answers).interruptionCause)
        val explained=interrupted.copy(preferences=interrupted.preferences.copy(interruptionCause=InterruptionCause.FATIGUE))
        assertEquals(3,PlanningQuestionPolicy().questions(explained,
            AthletePlanningStateBuilder().build(explained,PersonalizedPlanningAnswers()),PersonalizedPlanningAnswers()).size)
    }

    @Test fun highFatigueCannotBeAppliedAgainAsAnchorLocalDoseOrShortHorizon() {
        val input=fixture("chronic_high_improving")
        val source=snapshot(input)
        val state=AthletePlanningStateBuilder().build(source,PersonalizedPlanningAnswers()).copy(trainingStateAssessment=TrainingStateAnalyzer().assess(input),genericCourtLoad=0.0)
        val anchor=state.anchors.first()
        val original=AdaptationTransitionPlanner().decide(anchor,state,emptyList())
        val softer=AdaptationTransitionPlanner().decide(anchor,state.copy(recoverySignals=PlanningRecoverySignals(readinessStatus="NORMAL",tissueStatus="NORMAL",overallFatigueIndex=20)),emptyList())
        assertEquals(softer.localDoseFactor,original.localDoseFactor,0.0)
        assertTrue(PlanningHorizonPlanner().choose(state,emptyList()) in 4..6)
    }

    @Test fun blockedTissueCannotAllowPositivePerformanceToAdvancePrescription() {
        val source=snapshot(fixture("blocked_overrides_performance"))
        val prescription=PersonalizedPrescriptionPlanner().prescribe(source,StrengthIntent.MIXED,
            PlannedExercise("knee","CONTINUITY","",100,targetSets=2),StrengthProgrammingStyle.NONE)
        assertTrue(prescription.sets.all { it.weightKg==90.0 })
        val held=PersonalizedPrescriptionPlanner().prescribe(source.copy(recoverySignals=PlanningRecoverySignals(),
            hardRestrictedModes=setOf("UNMAPPED_EXPLICIT_RESTRICTION")),StrengthIntent.MIXED,
            PlannedExercise("knee","CONTINUITY","",100,targetSets=2),StrengthProgrammingStyle.NONE)
        assertTrue(held.sets.all { it.weightKg==100.0 })
    }
    @Test fun realPythonAssessmentMatchesProductionAnalyzerWhenPrivateArtifactProvided() {
        val directory=System.getenv("WGTD_COMPARISON_DIR")?.let(::File)
        org.junit.Assume.assumeTrue(directory?.resolve("v013_python_actual.json")?.isFile==true)
        val source=JSONObject(directory!!.resolve("v013_numerical_inputs.json").readText())
        val expected=JSONObject(directory.resolve("v013_python_actual.json").readText())
        compare(expected,TrainingStateAnalyzer().assess(trainingInput(source)).toJson(),"real")
    }
    @Test fun independentPythonRawInputsMatchEveryAssessmentField() {
        val file=listOf(File("../tools/planner_reference/fixtures/v013_training_state_golden.json"),
            File("tools/planner_reference/fixtures/v013_training_state_golden.json")).first { it.isFile }
        val cases=JSONArray(file.readText())
        assertEquals(16,cases.length())
        for (i in 0 until cases.length()) {
            val case=cases.getJSONObject(i)
            val actual=TrainingStateAnalyzer().assess(trainingInput(case.getJSONObject("input")))
            compare(case.getJSONObject("expected"),assessmentJson(actual),case.getString("name"))
            compare(assessmentJson(actual),actual.toJson(),"persisted.${case.getString("name")}")
        }
    }

    @Test fun cutoffAndPhysiologicalHistoryRemainIndependentOfSchedulingContext() {
        val file=listOf(File("../tools/planner_reference/fixtures/v013_training_state_golden.json"),
            File("tools/planner_reference/fixtures/v013_training_state_golden.json")).first { it.isFile }
        val cases=JSONArray(file.readText())
        val source=(0 until cases.length()).map(cases::getJSONObject).first { it.getString("name")=="confirmed_event" }.getJSONObject("input")
        val input=trainingInput(source)
        val analyzer=TrainingStateAnalyzer()
        val confirmed=analyzer.assess(input)
        val unknown=analyzer.assess(input.copy(interruptionCause=InterruptionCause.UNSURE))
        assertEquals(unknown.strain,confirmed.strain)
        assertEquals(unknown.weeklyContext.map { it.courtLoad },confirmed.weeklyContext.map { it.courtLoad })
        val future=input.records.first().copy(date=input.cutoff.plusDays(1),weightKg=10000.0)
        compare(assessmentJson(confirmed),assessmentJson(analyzer.assess(input.copy(records=input.records+future))),"future")
    }

    private fun compare(expected: Any,actual: Any,path: String) {
        when (expected) {
            is JSONObject -> {
                assertTrue(path,actual is JSONObject); actual as JSONObject
                assertEquals(path,expected.keys().asSequence().toSet(),actual.keys().asSequence().toSet())
                expected.keys().forEach { compare(expected.get(it),actual.get(it),"$path.$it") }
            }
            is JSONArray -> {
                assertTrue(path,actual is JSONArray); actual as JSONArray
                assertEquals(path,expected.length(),actual.length())
                for (i in 0 until expected.length()) compare(expected.get(i),actual.get(i),"$path[$i]")
            }
            is Number -> { assertTrue(path,actual is Number); assertEquals(path,expected.toDouble(),(actual as Number).toDouble(),1e-9) }
            else -> assertEquals(path,expected,actual)
        }
    }
}

internal fun trainingInput(json: JSONObject): TrainingStateInput {
    fun obj(name: String)=json.optJSONObject(name)?:JSONObject()
    fun rows(name: String)=json.optJSONArray(name)?.let { a -> (0 until a.length()).map(a::getJSONObject) }.orEmpty()
    fun JSONObject.number(name: String)=if (has(name) && !isNull(name)) getDouble(name) else null
    fun strings(a: JSONArray?)=a?.let { (0 until it.length()).map(it::getString).toSet() }.orEmpty()
    val recovery=obj("recovery")
    return TrainingStateInput(LocalDate.parse(json.getString("cutoff")),rows("records").map { r ->
        PlanningSetRecord(LocalDate.parse(r.getString("date")),r.getString("stableKey"),r.optString("exerciseName"),r.optString("category"),
            r.getInt("setIndex"),r.getInt("reps"),r.getDouble("weightKg"),r.getInt("seconds"),r.number("rpe")) },
        rows("daily").map { r -> PlanningDailyStrain(LocalDate.parse(r.getString("date")),r.getDouble("overallFatigueIndex"),
            r.getDouble("highForceNeuralScore"),r.getDouble("systemicMuscularScore"),r.getDouble("localMuscularScore"),
            r.getDouble("highSpeedScore"),r.getDouble("reactiveScore"),r.getDouble("recoveryPressureScore"),r.getDouble("confirmedTrainingLoad")) },
        obj("domains").let { o -> o.keys().asSequence().associateWith { PlannedActivityKind.valueOf(o.getString(it)) } },
        obj("coverage").let { o -> o.keys().asSequence().associateWith { MovementCoverage.valueOf(o.getString(it)) } },
        obj("metadata").let { o -> o.keys().asSequence().filter { o.getJSONObject(it).optString("progressMetricType") in setOf("LOAD_REPS","VOLUME_LOAD","ESTIMATED_1RM") }.toSet() },
        obj("signals").let { o -> o.keys().asSequence().associateWith { val s=o.getJSONObject(it); CanonicalStrengthSignal(
            posteriorChangePercent=s.number("posteriorChangePercent"),observationCount=s.optInt("observationCount"),source=s.optString("source")) } },
        PlanningRecoverySignals(readinessStatus=recovery.getString("readinessStatus"),tissueStatus=recovery.getString("tissueStatus"),
            tissueRestrictedStableKeys=strings(recovery.optJSONArray("tissueRestrictedStableKeys"))),
        obj("restSeconds").let { o -> o.keys().asSequence().associateWith(o::getInt) },
        rows("weeklyCourtLoad").associate { LocalDate.parse(it.getString("end")) to it.getDouble("load") },
        strings(json.optJSONArray("hardRestrictedModes")),InterruptionCause.valueOf(json.optString("interruptionCause","UNSURE")),
        InterruptionFrequency.valueOf(json.optString("interruptionFrequency","UNSURE")))
}

/** Test-only structural serializer also checks that no evidence field was silently omitted. */
internal fun assessmentJson(value: Any?): Any = when(value) {
    null -> JSONObject.NULL
    is String,is Boolean,is Number -> value
    is Enum<*> -> value.name
    is LocalDate -> value.toString()
    is Map<*,*> -> JSONObject().apply { value.forEach { (k,v)-> put(k.toString(),assessmentJson(v)) } }
    is Iterable<*> -> JSONArray(value.map(::assessmentJson))
    else -> JSONObject().apply { value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers)||it.isSynthetic }.forEach {
        it.isAccessible=true; put(it.name,assessmentJson(it.get(value))) } }
}
