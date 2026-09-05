package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TrainingStateCorrectionTest {
    private val helpers=TrainingStateParityTest()
    private fun input(name: String)=helpers.fixture(name)
    private fun snapshot(name: String)=helpers.snapshot(input(name))
    private fun state(s: PlanningHistorySnapshot)=AthletePlanningStateBuilder().build(s,PersonalizedPlanningAnswers())
    private fun assess(name: String)=TrainingStateAnalyzer().assess(input(name))

    @Test fun highCourtNeverOwnsTheFrequencyCeiling() {
        val s=state(snapshot("long_successful_run"))
        val a=assess("long_successful_run")
        val productive=s.copy(trainingStateAssessment=a.copy(state=TrainingState.PRODUCTIVE_HIGH_LOAD),genericCourtLoad=305.0)
        val days=WeeklyDosePlanner().resolve(productive,8)
        assertTrue(days.recommendedDays>=4)
        assertEquals("HIGH_QUALIFIED_SUSTAINABLE_RUN",days.referenceSource)
        for (court in listOf(20.0,305.0)) {
            val result=WeeklyDosePlanner().resolve(productive.copy(genericCourtLoad=court,
                trainingStateAssessment=a.copy(state=TrainingState.MALADAPTATION_PATTERN)),8)
            assertEquals(3,result.recommendedDays)
            assertEquals("MALADAPTATION_PATTERN",result.ceilingReason)
        }
    }

    @Test fun exactWeekCausesDoNotLeakAndLegacyCauseHasNoAuthority() {
        val mixed=assess("different_causes").weeklyContext.filter { it.low }
        assertEquals(2,mixed.size)
        assertEquals(WeeklyContextCause.EXTERNAL,mixed[0].cause)
        assertTrue(mixed[0].excludedFromTolerance)
        assertEquals(WeeklyContextCause.FATIGUE,mixed[1].cause)
        assertFalse(mixed[1].excludedFromTolerance)
        val legacy=input("legacy_global_external_ignored")
        assertEquals(TrainingStateAnalyzer().assess(legacy.copy(interruptionCause=InterruptionCause.UNSURE)),
            TrainingStateAnalyzer().assess(legacy))
        assertTrue(assess("unknown_answer").weeklyContext.any { it.low && it.context==WeeklyTrainingContext.UNEXPLAINED_LOW_WEEK })
    }

    @Test fun externalAndEventAnswersPreserveMeasuredDeteriorationAndCourtHistory() {
        for (name in listOf("external_with_rpe_decline","confirmed_event")) {
            val i=input(name); val annotated=TrainingStateAnalyzer().assess(i)
            val unknown=TrainingStateAnalyzer().assess(i.copy(weekAnnotations=emptyMap()))
            assertEquals(unknown.adaptation,annotated.adaptation)
            assertEquals(unknown.strain,annotated.strain)
            assertEquals(unknown.weeklyContext.map { it.courtLoad },annotated.weeklyContext.map { it.courtLoad })
            assertTrue(annotated.weeklyContext.any { it.excludedFromTolerance })
            if (name=="external_with_rpe_decline") {
                assertTrue(annotated.adaptation.rpeDrift!!>0)
                assertTrue(annotated.maladaptationEvidence!!>.5)
            }
        }
    }

    @Test fun localRestrictionDoesNotDisableUnrelatedStateCapacityOrProgression() {
        val local=assess("local_press_only")
        assertFalse(local.globalHardRestriction)
        assertNotEquals(TrainingState.HARD_RESTRICTION,local.state)
        assertTrue(local.permitsSustainableRelease)
        assertEquals(setOf("push"),local.localRestrictedStableKeys)
        val s=snapshot("local_press_only")
        val prescription=PersonalizedPrescriptionPlanner()
        val press=prescription.prescribe(s,StrengthIntent.MIXED,PlannedExercise("push","CONTINUITY","",100,targetSets=2),StrengthProgrammingStyle.NONE)
        assertTrue(press.sets.all { it.weightKg==90.0 })
        val lower=prescription.prescribe(s,StrengthIntent.MIXED,PlannedExercise("knee","CONTINUITY","",100,targetSets=2),StrengthProgrammingStyle.NONE)
        assertTrue(lower.sets.all { it.weightKg==102.5 })
        val original=state(s)
        val request=ProgramSkeletonRequest("test",ProgramGoal.BODYBUILDING,4,90,emptySet(),"",.5,"AUTO",ProgramPeriodizationType.AUTO,4)
        val clear=s.copy(recoverySignals=s.recoverySignals.copy(tissueRestrictedStableKeys=emptySet(),tissueStatus="HIGH"))
        assertEquals(ExecutionCapacityPlanner().envelope(clear,state(clear),request,30.0,100,1.0).finalControllableUnits,
            ExecutionCapacityPlanner().envelope(s,original,request,30.0,100,1.0).finalControllableUnits)
    }

    @Test fun overheadRestrictionRemainsTypedAndReadinessLimitedIsGlobal() {
        val source=snapshot("overhead_mode_only")
        val s=source.copy(metadata=source.metadata+("push" to source.metadata.getValue("push").copy(programSlot="OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY")))
        // Use the canonical field already owned by movementCoverage; no exercise-name classification.
        assertFalse(assess("overhead_mode_only").globalHardRestriction)
        assertFalse(s.explicitlyRestricted("knee"))
        assertFalse(s.explicitlyRestricted("hinge"))
        assertTrue(assess("limited_global").globalHardRestriction)
        assertTrue(assess("blocked_overrides_performance").globalHardRestriction)
        assertTrue(s.explicitlyRestricted("push"))
    }

    @Test fun datedQuestionsAreBoundedAndAnswersRemainExactWeekPortableState() {
        val s=snapshot("low_weeks_deterioration")
        val questions=PlanningQuestionPolicy().questions(s,state(s),PersonalizedPlanningAnswers())
        val dated=questions.filter { it.id.startsWith(QUESTION_WEEK_CAUSE_PREFIX) }
        assertEquals(3,dated.size)
        assertEquals(dated.map { it.id }.sortedDescending(),dated.map { it.id })
        assertTrue(dated.all { it.prompt.contains(it.id.removePrefix(QUESTION_WEEK_CAUSE_PREFIX)) })
        val answer=PersonalizedPlanningAnswers(mapOf(dated.first().id to "EXTERNAL"))
        val annotations=answer.weekAnnotations(123L)
        assertEquals(annotations,WeeklyContextAnnotationJson.read(WeeklyContextAnnotationJson.write(annotations)))
        assertEquals(BackupAppMetaAuthority.PORTABLE_USER_STATE,BackupAppMetaPolicy.authority(WeeklyContextAnnotationJson.KEY))
        assertEquals(1,s.trainingStateInput(answer).weekAnnotations.size)
        val answered=s.copy(weekAnnotations=annotations)
        assertFalse(PlanningQuestionPolicy().questions(answered,state(answered),PersonalizedPlanningAnswers()).any { it.id==dated.first().id })
        assertTrue(state(answered).trainingStateAssessment!!.weeklyContext.count { it.source==WeeklyContextSource.USER_CONFIRMED }==1)
    }

    @Test fun oneLowWeekGetsOneQuestionAndUnknownIsNotRepeated() {
        val s=snapshot("unknown_answer").copy(weekAnnotations=emptyMap())
        val policy=PlanningQuestionPolicy()
        val dated=policy.questions(s,state(s),PersonalizedPlanningAnswers()).filter { it.id.startsWith(QUESTION_WEEK_CAUSE_PREFIX) }
        assertEquals(1,dated.size)
        val answer=PersonalizedPlanningAnswers(mapOf(dated.single().id to "UNKNOWN"))
        val saved=s.copy(weekAnnotations=answer.weekAnnotations(123))
        assertFalse(policy.questions(saved,state(saved),PersonalizedPlanningAnswers()).any { it.id.startsWith(QUESTION_WEEK_CAUSE_PREFIX) })
        val normal=snapshot("long_successful_run")
        assertFalse(policy.questions(normal,state(normal),PersonalizedPlanningAnswers()).any { it.id.startsWith(QUESTION_WEEK_CAUSE_PREFIX) })
    }

    @Test fun frequencyFreshnessIsRevisitableAndNeverClassifiesHistoricalWeeks() {
        val s=snapshot("confirmed_external")
        val now=1000L*24*60*60*1000
        fun questions(f: InterruptionFrequency,ageDays: Long): List<PersonalizedPlanningQuestion> {
            val saved=s.copy(preferences=s.preferences.copy(interruptionFrequency=f,
                interruptionFrequencyAnsweredAtEpochMillis=now-ageDays*24*60*60*1000))
            return PlanningQuestionPolicy { now }.questions(saved,state(saved),PersonalizedPlanningAnswers())
        }
        assertFalse(questions(InterruptionFrequency.FREQUENT,1).any { it.id==QUESTION_INTERRUPTION_FREQUENCY })
        assertTrue(questions(InterruptionFrequency.FREQUENT,91).any { it.id==QUESTION_INTERRUPTION_FREQUENCY })
        assertTrue(questions(InterruptionFrequency.RARE,1).any { it.id==QUESTION_INTERRUPTION_FREQUENCY })
        val i=input("confirmed_external")
        val a=TrainingStateAnalyzer().assess(i.copy(interruptionFrequency=InterruptionFrequency.NEVER))
        val b=TrainingStateAnalyzer().assess(i.copy(interruptionFrequency=InterruptionFrequency.VERY_FREQUENT))
        assertEquals(a.weeklyContext,b.weeklyContext)
        assertEquals(a.globalDoseFactor,b.globalDoseFactor,0.0)
        assertEquals(a.sustainable.copy(robustSchedule=true),b.sustainable)
    }

    @Test fun weekIdentityAndCompleteBinsSurviveChangingGenerationDay() {
        val i=input("confirmed_bridge")
        val sunday=TrainingStateAnalyzer().assess(i)
        val wednesday=TrainingStateAnalyzer().assess(i.copy(cutoff=i.cutoff.plusDays(3)))
        assertEquals(sunday.weeklyContext,wednesday.weeklyContext)
        assertTrue(wednesday.weeklyContext.all { it.start.dayOfWeek==java.time.DayOfWeek.MONDAY })
    }

    @Test fun incompleteLeadingWeekDoesNotInflateCompleteWeekTolerance() {
        val original=input("long_successful_run")
        val cutoff=original.cutoff
        val i=original.copy(records=original.records.filter { it.date>=cutoff.minusDays(24) })
        val a=TrainingStateAnalyzer().assess(i)
        val weeks=a.weeklyContext.filter { it.end>=cutoff.minusDays(27) }
        assertEquals(3,weeks.size)
        assertEquals(weeks.sumOf { it.units }.toDouble()/weeks.size,a.tolerance.current.units!!,1e-9)
        assertEquals(weeks.sumOf { it.days }.toDouble()/weeks.size,a.tolerance.current.days!!,1e-9)
    }

    @Test fun runLocalPythonGoldensRejectBorrowedPerformanceAndValidateNeutralBridges() {
        val file=listOf(File("../tools/planner_reference/fixtures/v0131_run_golden.json"),
            File("tools/planner_reference/fixtures/v0131_run_golden.json")).first { it.isFile }
        val cases=JSONArray(file.readText())
        assertEquals(7,cases.length())
        for (i in 0 until cases.length()) {
            val case=cases.getJSONObject(i); val json=case.getJSONObject("input")
            val source=input("long_successful_run").copy(cutoff=LocalDate.parse(json.getString("cutoff")),
                weekAnnotations=WeeklyContextAnnotationJson.read(json.optJSONObject("weekAnnotations")?.toString()))
            val rows=case.getJSONArray("weeks")
            val weeks=(0 until rows.length()).map { j ->
                val w=rows.getJSONObject(j)
                fun number(key: String)=if (w.isNull(key)) null else w.getDouble(key)
                WeeklyWorkloadEvidence(LocalDate.parse(w.getString("start")),LocalDate.parse(w.getString("end")),
                    w.getInt("units"),w.getDouble("minutes"),w.getInt("days"),w.getDouble("courtLoad"),
                    number("medianRpe"),number("performanceResponse"),number("negativeBreadth"),number("rpeDrift"))
            }
            val (actual,capacity)=WeeklyWorkloadContextAnalyzer().evaluate(weeks,source)
            helpers.compare(case.getJSONArray("expectedWeeks"),assessmentJson(actual),case.getString("name")+".weeks")
            helpers.compare(case.getJSONObject("expectedSustainable"),capacity.toJson(),case.getString("name")+".runs")
        }
    }
}
