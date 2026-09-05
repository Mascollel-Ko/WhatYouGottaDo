package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ExecutionAllocationV012Test {
    private val cutoff = LocalDate.of(2026, 9, 3)
    private fun request(days: Int = 3, minutes: Int = 45) = ProgramSkeletonRequest(
        "execution", ProgramGoal.BODYBUILDING, days, minutes, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 4)

    @Test fun `Python execution golden reaches production finite allocator`() {
        val path = "tools/planner_reference/fixtures/v012_execution_allocation_golden.json"
        val cases = JSONObject(sequenceOf(File(path), File("../$path")).first(File::isFile).readText()).getJSONArray("cases")
        repeat(cases.length()) { index ->
            val case = cases.getJSONObject(index)
            fun ints(name: String): List<Int> = case.getJSONArray(name).let { array -> List(array.length()) { array.getInt(it) } }
            val actual = FiniteExecutionAllocator.allocate(case.getInt("capacity"), case.getInt("continuity"),
                ints("minimums"), case.getDouble("share"), case.getInt("core"), ints("flexible").toSet())
            val expected = case.getJSONObject("expected")
            assertEquals(case.getString("name"), expected.getInt("continuity"), actual.continuity)
            assertEquals(case.getString("name"), expected.getJSONArray("material").let { a -> List(a.length()) { a.getInt(it) } }, actual.material)
            assertEquals(case.getString("name"), expected.getJSONArray("deferred").let { a -> List(a.length()) { a.getInt(it) } }, actual.deferred)
        }
    }

    @Test fun `successful five sets buys high gap from incumbent when capacity binds`() {
        val result = FiniteExecutionAllocator.allocate(5, 5, listOf(2), .3, 1)
        assertEquals(3, result.continuity)
        assertEquals(listOf(2), result.material)
        assertEquals(5, result.continuity + result.material.sum())
    }

    @Test fun `Madcow finite eleven to eight with two domains retains core`() {
        val result = FiniteExecutionAllocator.allocate(8, 11, listOf(2, 2), .48, 2)
        assertEquals(4, result.continuity)
        assertEquals(listOf(2, 2), result.material)
    }

    @Test fun `indivisible multi objective drill is funded once and never tokenized`() {
        val full = FiniteExecutionAllocator.allocate(6, 5, listOf(3), .35, 1, emptySet())
        assertEquals(listOf(3), full.material)
        val constrained = FiniteExecutionAllocator.allocate(3, 5, listOf(3), .35, 1, emptySet())
        assertEquals(listOf(0), constrained.material)
        assertEquals(listOf(0), constrained.deferred)
    }

    @Test fun `explicit two through five days increase useful allocated work when binding`() {
        val source = history()
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val totals = (2..5).map { days ->
            val envelope = ExecutionCapacityPlanner().envelope(source, state, request(days, 30), 60.0, 300, 1.0)
            val result = FiniteExecutionAllocator.allocate(envelope.finalControllableUnits, 200, List(20) { 3 }, .48, 2, emptySet())
            result.continuity + result.material.sum()
        }
        assertTrue(totals.zipWithNext().all { (a,b) -> b > a })
    }

    @Test fun `30 45 60 90 120 minute capacity is monotonic while demand remains`() {
        val source = history()
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val capacities = listOf(30,45,60,90,120).map {
            ExecutionCapacityPlanner().envelope(source, state, request(3,it), 60.0, 1000, 1.0).finalControllableUnits
        }
        assertTrue(capacities.zipWithNext().all { (a,b) -> b > a })
    }

    @Test fun `no extra demand means no extra units`() {
        val source = history()
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        assertEquals(5, ExecutionCapacityPlanner().envelope(source, state, request(5,120),60.0,5,1.0).finalControllableUnits)
    }

    @Test fun `high court context cannot impose daily item limit`() {
        val rows = (1..24).map { i -> timed("item$i", 2, 20, 20) }
        val (days, rejected) = TimedWeeklyPlacementPlanner().distribute(rows, 3, 45)
        assertTrue(rejected.isEmpty())
        assertTrue(days.values.all { it.size == 8 })
        assertTrue(days.values.all { items -> items.sumOf { it.estimatedSeconds } <= 45*60 })
        val source = history()
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        assertEquals(
            ExecutionCapacityPlanner().envelope(source,state,request(5),60.0,100,1.0).finalControllableUnits,
            ExecutionCapacityPlanner().envelope(source,state.copy(genericCourtLoad=300.0),request(5),60.0,100,1.0).finalControllableUnits)
    }

    @Test fun `actual duration governs placement rather than item counts`() {
        val rows = (1..30).map { i -> timed("item$i", 4, 45, 180) }
        val short = TimedWeeklyPlacementPlanner().distribute(rows, 3, 45)
        val long = TimedWeeklyPlacementPlanner().distribute(rows, 3, 90)
        assertTrue(long.first.values.sumOf { it.size } > short.first.values.sumOf { it.size })
        assertTrue(long.first.values.all { day -> day.sumOf { it.estimatedSeconds } <= 90*60 })
    }

    @Test fun `timing without reps or seconds cannot fabricate performance prescription`() {
        val source = performanceSource()
        assertNull(PerformancePrescriptionResolver.resolve(source,"landing"))
    }

    @Test fun `canonical prescription resolves exact shape beyond legacy table`() {
        val source = performanceSource().copy(performancePrescriptions = mapOf("landing" to PerformancePrescriptionAuthority(
            List(3) { ProgramSetPrescription(it+1,5,0.0,0) },60,"quality","CANONICAL_PROGRAM_TEST")))
        val rx = PerformancePrescriptionResolver.prescribe(source, PlannedExercise("landing","GAP","",100,targetSets=3))
        assertEquals(3,rx.sets.size)
        assertTrue(rx.sets.all { it.reps == 5 && it.seconds == 0 && it.weightKg == 0.0 })
        assertEquals(60,rx.restSeconds)
    }

    @Test fun `personal timed execution stays timed and uses hold`() {
        val source = performanceSource().copy(allConfirmedSets=listOf(
            PlanningSetRecord(cutoff,"landing","","",1,0,0.0,18,7.0),
            PlanningSetRecord(cutoff,"landing","","",2,0,0.0,18,7.0)))
        val rx = PerformancePrescriptionResolver.prescribe(source, PlannedExercise("landing","GAP","",100,targetSets=2))
        assertTrue(rx.sets.all { it.seconds == 18 && it.reps == 0 })
        assertEquals("RECENT_PERSONAL_EXECUTION_HOLD", rx.weightSource)
    }

    @Test fun `normal badminton horizon respects four weeks but LIMITED is short bridge`() {
        val source = history()
        val state = AthletePlanningStateBuilder().build(source,PersonalizedPlanningAnswers()).copy(
            primaryAdaptation="BADMINTON_SUPPORT", badmintonIntent=BadmintonPlanningIntent.ENABLED,
            recoverySignals=PlanningRecoverySignals(readinessStatus="NORMAL",tissueStatus="NORMAL"))
        val gaps = listOf(AdaptationGap("BADMINTON_FOUNDATIONAL_ONRAMP","HIGH",""))
        assertTrue(PlanningHorizonPlanner().choose(state,gaps) in 4..6)
        assertEquals(2,PlanningHorizonPlanner().choose(state.copy(recoverySignals=PlanningRecoverySignals(readinessStatus="LIMITED")),gaps))
    }

    @Test fun `unknown saved intentions are asked again and never offered as executable answers`() {
        val source=history().copy(preferences=PersonalizedPlanningPreferences(
            strengthIntent=StrengthIntent.UNRESOLVED,badmintonIntent=BadmintonPlanningIntent.UNRESOLVED,
            freeWeightWillingness=FreeWeightWillingness.UNRESOLVED))
        val state=AthletePlanningStateBuilder().build(source,PersonalizedPlanningAnswers()).copy(
            strengthIntent=StrengthIntent.UNRESOLVED,badmintonIntent=BadmintonPlanningIntent.UNRESOLVED,
            freeWeightWillingness=FreeWeightWillingness.UNRESOLVED)
        val questions=PlanningQuestionPolicy().questions(source,state,PersonalizedPlanningAnswers())
        assertEquals(setOf(QUESTION_STRENGTH_INTENT,QUESTION_BADMINTON_INTENT,QUESTION_FREE_WEIGHT), questions.map{it.id}.toSet())
        assertTrue(questions.all { q -> q.options.none { it.value=="UNRESOLVED" } })
    }

    @Test fun `execution provenance is an additive JSON object with domain counts separate`() {
        val envelope=ExecutionCapacityPlanner().envelope(history(),AthletePlanningStateBuilder().build(history(),PersonalizedPlanningAnswers()),request(),60.0,20,1.0)
        val trace=ExecutionAllocationTrace(envelope,10,8,3,3,listOf("GAP"),listOf("GAP"),emptyMap(),emptyList(),emptyMap(),mapOf("drill" to setOf("GAP")),mapOf("drill" to "REVIEWED"))
        val parsed=JSONObject(trace.toJson().toString())
        assertEquals(3,parsed.getInt("materialGapAllocatedUnits"))
        assertEquals("GAP",parsed.getJSONObject("representedGapCodesByStableKey").getJSONArray("drill").getString(0))
    }

    @Test fun `core preferences are asked proactively even with resolved fresh preferences and history`() {
        val source = history()
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val questions = PlanningQuestionPolicy().questions(source, state, PersonalizedPlanningAnswers())
        assertEquals(3, questions.size)
        val answers = PersonalizedPlanningAnswers(questions.associate { it.id to it.options.first().value })
        assertTrue(PlanningQuestionPolicy().questions(source, state, answers).isEmpty())
        assertEquals(1, PlanningQuestionPolicy().questions(source, state,
            answers.copy(values = answers.values + (QUESTION_STRENGTH_INTENT to "TYPO"))).size)
    }

    @Test fun `canonical supportive stability can execute reviewed assistance without resistance or direct relabeling`() {
        val source = performanceSource()
        val key = "band_pallof_press"
        val candidate = source.exercises.getValue("landing").copy(stableKey = key)
        val snapshot = source.copy(exercises = source.exercises + (key to candidate),
            metadata = source.metadata + (key to source.metadata.getValue("landing").copy(
                programSlot = "CORE_STABILITY", progressMetricType = "QUALITY_BASED",
                analysisEligibility = MetadataTokenField.parse("FATIGUE|BADMINTON_SUPPORTIVE|BALANCE"))),
            badmintonSupportiveObjectives = mapOf(key to setOf("ANTI_ROTATION")))
        assertEquals(PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL, snapshot.activityKind(key))
        assertTrue(snapshot.badmintonDirectObjectives[key].isNullOrEmpty())
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        val gap = AdaptationGap("BADMINTON_UNDERREPRESENTED_ANTI_ROTATION", "MODERATE", "fixture")
        val plan = PersonalizedProgramBuilder().build(snapshot, state, listOf(gap), BlockIntentPlanner().decide(state,listOf(gap)),
            4,request(3,45),PersonalizedPlanningAnswers(),null)
        assertTrue(plan.items.any { it.exerciseStableKey == key && it.setCount >= 2 })
        assertEquals(setOf(gap.code), plan.personalizedDecision!!.planningBudget!!.execution!!.supportiveGapCodesByStableKey[key])
    }

    @Test fun `final materialized plans use additional days and minutes when continuity demand remains`() {
        val base = history()
        val keys = (1..9).map { "anchor_$it" }
        val source = base.copy(
            exercises = keys.associateWith { base.exercises.getValue("anchor").copy(stableKey = it) },
            metadata = keys.associateWith { base.metadata.getValue("anchor") },
            allConfirmedSets = keys.flatMap { key -> base.allConfirmedSets.map { it.copy(stableKey = key) } })
        val prototype = AthletePlanningStateBuilder().build(base, PersonalizedPlanningAnswers())
        val state = prototype.copy(
            anchors = keys.map { prototype.anchors.single().copy(stableKey = it) },
            styleFeaturesByAnchor = keys.associateWith { prototype.styleFeaturesByAnchor.getValue("anchor") })
        fun total(days: Int, minutes: Int): Int {
            val plan = PersonalizedProgramBuilder().build(source,state,emptyList(),BlockIntentPlanner().decide(state,emptyList()),
                4,request(days,minutes),PersonalizedPlanningAnswers(),null)
            assertEquals(days, plan.request.weeklyTrainingDays)
            assertEquals(4, plan.items.map { it.weekNumber }.distinct().size)
            assertTrue(plan.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { rows ->
                rows.sumOf { it.estimatedDurationSeconds } <= minutes * 60 })
            return plan.items.filter { it.weekNumber == 1 }.sumOf { it.setCount }
        }
        val days = (2..5).map { total(it,45) }
        assertTrue(days.toString(), days.zipWithNext().all { (a,b) -> b >= a })
        assertTrue(days.toString(), days.last() > days[1])
        val minutes = listOf(30,45,60,90,120).map { total(3,it) }
        assertTrue(minutes.toString(), minutes.zipWithNext().all { (a,b) -> b >= a })
        assertTrue(minutes.toString(), minutes[3] > minutes[1])
    }

    @Test fun `supportive exercise is materialized but not relabeled as direct exposure`() {
        val original = history()
        val assistance = original.exercises.getValue("anchor").copy(stableKey = "assistance", name = "unrelated label")
        val source = original.copy(exercises = original.exercises + ("assistance" to assistance),
            metadata = original.metadata + ("assistance" to original.metadata.getValue("anchor").copy(movementFamily = "CORE_BRACING")),
            badmintonObjectives = mapOf("assistance" to mapOf("ANTI_ROTATION" to .6)),
            badmintonSupportiveObjectives = mapOf("assistance" to setOf("ANTI_ROTATION")))
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val gaps = listOf(AdaptationGap("BADMINTON_UNDERREPRESENTED_ANTI_ROTATION", "MODERATE", "fixture"))
        val plan = PersonalizedProgramBuilder().build(source, state, gaps, BlockIntentPlanner().decide(state, gaps),
            4, request(3, 45), PersonalizedPlanningAnswers(), null)
        val trace = plan.personalizedDecision!!.planningBudget!!.execution!!
        assertTrue(plan.items.any { it.exerciseStableKey == "assistance" && it.setCount >= 2 })
        assertEquals(setOf(gaps.single().code), trace.supportiveGapCodesByStableKey.getValue("assistance"))
        assertFalse(gaps.single().code in trace.representedMaterialGaps)
        assertEquals("SUPPORTIVE_ONLY_DIRECT_EXPOSURE_NOT_REPLACED", trace.deferredMaterialGaps[gaps.single().code])
        assertEquals(.6, source.badmintonObjectives.getValue("assistance").getValue("ANTI_ROTATION"), 0.0)
    }

    private fun timed(key:String,count:Int,seconds:Int,rest:Int)=TimedPlannedExercise(
        PlannedExercise(key,"GAP","",100,targetSets=count),
        PlannedPrescription("",List(count){ProgramSetPrescription(it+1,0,0.0,seconds)},rest,"TEST"))

    @Test fun `supporting a later objective cannot suppress its feasible direct candidate`() {
        val base=performanceSource()
        val other=base.exercises.getValue("landing").copy(stableKey="direct_rotation")
        val source=base.copy(exercises=base.exercises+(other.stableKey to other),
            metadata=base.metadata+(other.stableKey to base.metadata.getValue("landing").copy(programSlot="PLYOMETRIC_POWER"))+
                ("landing" to base.metadata.getValue("landing").copy(programSlot="PLYOMETRIC_POWER")),
            badmintonDirectObjectives=mapOf("landing" to setOf("FOOTWORK"),other.stableKey to setOf("ROTATIONAL_POWER")),
            badmintonSupportiveObjectives=mapOf("landing" to setOf("ROTATIONAL_POWER")),
            performancePrescriptions=listOf("landing",other.stableKey).associateWith { PerformancePrescriptionAuthority(
                List(2) { ProgramSetPrescription(it+1,5,0.0,0) },60,"quality","CANONICAL_PROGRAM_TEST") })
        val gaps=listOf(AdaptationGap("BADMINTON_UNDERREPRESENTED_FOOTWORK","HIGH",""),
            AdaptationGap("BADMINTON_UNDERREPRESENTED_ROTATIONAL_POWER","HIGH",""))
        val state=AthletePlanningStateBuilder().build(source,PersonalizedPlanningAnswers())
        val demand=MaterialDemandResolver().resolve(source,state,gaps,request())
        assertEquals("audit=${demand.audit}; deferred=${demand.deferred}",setOf("landing",other.stableKey),demand.candidates.map { it.stableKey }.toSet())
    }

    private fun history():PlanningHistorySnapshot {
        val exercise=Exercise("anchor","anchor","",equipment="BARBELL",equipmentTags="BARBELL",
            activityKind="EXERCISE",planningEligibility="PROGRAM_SELECTABLE",defaultRestSeconds=180)
        val rows=(0..7).flatMap { week -> listOf(0,2,4).flatMap { day ->
            (1..20).map { PlanningSetRecord(cutoff.minusDays((week*7+day).toLong()),"anchor","","",it,5,100.0,0,7.0) }
        } }
        val metadata=RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            activityKind="EXERCISE",planningEligibility="PROGRAM_SELECTABLE",programSlot="MAIN_LOWER_STRENGTH",
            progressMetricType="LOAD_REPS",sourceConfidenceLevel="HIGH",
            analysisEligibility=MetadataTokenField.parse("STRENGTH_PROGRESS|HYPERTROPHY_VOLUME"))
        return PlanningHistorySnapshot(cutoff,rows,mapOf("anchor" to exercise),mapOf("anchor" to metadata),emptyMap(),
            "STRENGTH",3.0,1.0,PersonalizedPlanningPreferences(StrengthIntent.STRENGTH_PRIORITY,BadmintonPlanningIntent.ENABLED,FreeWeightWillingness.WILLING),
            recoverySignals=PlanningRecoverySignals(readinessStatus="NORMAL",tissueStatus="NORMAL"))
    }
    private fun performanceSource():PlanningHistorySnapshot {
        val source=history()
        val exercise=Exercise("landing","arbitrary name","",equipment="BODYWEIGHT",activityKind="EXERCISE",planningEligibility="PROGRAM_SELECTABLE",defaultRestSeconds=60)
        return source.copy(exercises=source.exercises+("landing" to exercise),
            metadata=source.metadata+("landing" to RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                activityKind="EXERCISE",planningEligibility="PROGRAM_SELECTABLE",programSlot="PLYOMETRIC_SLOT",
                progressMetricType="REPS",analysisEligibility=MetadataTokenField.parse("BADMINTON_TRANSFER"),
                badmintonTransferLevel="DIRECT")))
    }
}
