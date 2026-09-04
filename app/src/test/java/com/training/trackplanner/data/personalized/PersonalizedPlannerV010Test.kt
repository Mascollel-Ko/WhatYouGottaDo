package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StrengthExercisePerformanceHistoryEntity
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.data.resolvePersonalizedRequest
import com.training.trackplanner.data.canonicalStrengthSignalsForWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.ln

class PersonalizedPlannerV010Test {
    private val cutoff = LocalDate.of(2026, 1, 10)

    @Test
    fun `style and features use only the fixed recent 56 day window`() {
        val old = (1..5).map { row(cutoff.minusDays(70), "squat", it, 5, 100.0) }
        val recent = (1..4).flatMap { week ->
            listOf(
                row(cutoff.minusDays((week * 7).toLong()), "squat", 1, 4, 100.0),
                row(cutoff.minusDays((week * 7).toLong()), "squat", 2, 7, 90.0),
                row(cutoff.minusDays((week * 7).toLong()), "squat", 3, 7, 90.0)
            )
        }
        val snapshot = snapshot(old + recent)
        assertEquals(StrengthProgrammingStyle.TOP_SET_BACKOFF, StrengthProgrammingStyleAnalyzer().analyze(snapshot, setOf("squat")).first)
        assertEquals(4, StrengthStyleFeatureAnalyzer().analyze(snapshot, "squat").weeksObserved)
    }

    @Test
    fun `strength response uses recent 56 day posteriors and fewer than two is unknown`() {
        val rows = listOf(
            posterior(cutoff.minusDays(90), 50.0, "old"),
            posterior(cutoff.minusDays(40), 100.0, "first"),
            posterior(cutoff.minusDays(5), 110.0, "last"),
            posterior(cutoff.minusDays(3), 80.0, "single", key = "press")
        )
        val signals = canonicalStrengthSignalsForWindow(rows, cutoff, "test")
        assertEquals(10.0, signals.getValue("squat").posteriorChangePercent!!, .0001)
        assertEquals(2, signals.getValue("squat").observationCount)
        assertEquals(null, signals.getValue("press").posteriorChangePercent)
    }

    @Test
    fun `badminton drop compares current 28 days only with immediately prior 28 days`() {
        val drill = exercise("drill", "BADMINTON_FOOTWORK")
        val monthsOld = snapshot(
            rows = listOf(row(cutoff.minusDays(100), "drill", seconds = 25)),
            exercises = listOf(drill),
            objectives = mapOf("drill" to mapOf("FOOTWORK" to 1.0))
        )
        val oldState = AthletePlanningStateBuilder().build(monthsOld, PersonalizedPlanningAnswers())
        assertFalse("FOOTWORK" in oldState.objectiveDropGaps)
        val prior = monthsOld.copy(allConfirmedSets = listOf(row(cutoff.minusDays(40), "drill", seconds = 25)))
        val priorState = AthletePlanningStateBuilder().build(prior, PersonalizedPlanningAnswers())
        assertTrue("FOOTWORK" in priorState.objectiveDropGaps)
    }

    @Test
    fun `planner boundary normalizes 28 day court load to weekly equivalent`() {
        val sport = Exercise(stableKey = "court", name = "코트", category = "스포츠", activityKind = "SPORT_SESSION", planningEligibility = "NOT_PROGRAM_SELECTABLE")
        val history = (0..3).map { week ->
            val id = week + 1L
            WorkoutEntryWithSets(
                WorkoutEntry(id = id, date = cutoff.minusWeeks(week.toLong()).toString(), exerciseStableKey = "court", exerciseName = "코트", category = "스포츠", rpe = 8.0),
                listOf(WorkoutSet(entryId = id, setIndex = 1, seconds = 3600, rpe = 8.0, confirmed = true))
            )
        }
        val built = PlanningHistorySnapshotBuilder().build(cutoff, history, listOf(sport), mapOf("court" to metadata(sport, "OTHER")), CanonicalBadmintonObjectiveCatalog.EMPTY, null, PersonalizedPlanningPreferences())
        assertEquals(built.genericCourtLoad28d / 4.0, built.genericCourtLoad, .0001)
    }

    @Test
    fun `actual court cost remains when badminton generation intent is disabled and stays lower body local`() {
        val lower = anchor("squat", MovementCoverage.LOWER_KNEE, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM)
        val upper = anchor("press", MovementCoverage.HORIZONTAL_PUSH, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM)
        val features = StyleFeatures(weeklyFrequency = 3.0, frequencyStability = 1.0, loadUndulation = 1.0, hlmOrdering = 1.0, heavyExposure = 1.0, weeksObserved = 8)
        val state = state(
            anchors = listOf(lower, upper),
            badminton = BadmintonPlanningIntent.DISABLED,
            courtLoad = 240.0,
            features = mapOf("squat" to features, "press" to features)
        )
        val planner = AdaptationTransitionPlanner()
        val lowerDecision = planner.decide(lower, state, emptyList())
        val upperDecision = planner.decide(upper, state, emptyList())
        assertTrue(lowerDecision.adaptation.sportInterferencePressure > 0.0)
        assertEquals(0.0, upperDecision.adaptation.sportInterferencePressure, .0001)
        assertTrue(lowerDecision.localDoseFactor < upperDecision.localDoseFactor)
    }

    @Test
    fun `tissue restriction blocks only the exact anchor local dose`() {
        val squat = anchor("squat", MovementCoverage.LOWER_KNEE)
        val press = anchor("press", MovementCoverage.HORIZONTAL_PUSH)
        val features = StyleFeatures(frequencyStability = 1.0, weeksObserved = 8)
        val state = state(
            anchors = listOf(squat, press),
            recovery = PlanningRecoverySignals(readinessStatus = "NORMAL", tissueStatus = "NORMAL", tissueRestrictedStableKeys = setOf("squat")),
            features = mapOf("squat" to features, "press" to features)
        )
        val planner = AdaptationTransitionPlanner()
        assertTrue(planner.decide(squat, state, emptyList()).localDoseFactor < planner.decide(press, state, emptyList()).localDoseFactor)
    }

    @Test
    fun `prefer familiar differs from willing avoid and unresolved for novel free weights`() {
        val incumbent = exercise("incumbent", "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY", equipment = "MACHINE")
        val free = exercise("a_free", "HORIZONTAL_PULL_STRENGTH", equipment = "BARBELL")
        val machine = exercise("z_machine", "HORIZONTAL_PULL_STRENGTH", equipment = "MACHINE")
        val source = snapshot(listOf(row(cutoff.minusDays(7), "incumbent"), row(cutoff.minusDays(14), "incumbent")), listOf(incumbent, free, machine))
        fun selected(willingness: FreeWeightWillingness): String {
            val chosen = GapCandidateSelector().select(source, state(freeWeight = willingness), listOf(AdaptationGap("UPPER_PULL", "HIGH", "fixture")), emptySet())
            return chosen.single().stableKey
        }
        assertEquals("a_free", selected(FreeWeightWillingness.WILLING))
        assertEquals("z_machine", selected(FreeWeightWillingness.PREFER_FAMILIAR))
        assertEquals("z_machine", selected(FreeWeightWillingness.AVOID))
        assertEquals("z_machine", selected(FreeWeightWillingness.UNRESOLVED))
    }

    @Test
    fun `all material questions are returned together and dismissal has no answer`() {
        val source = snapshot(listOf(row(cutoff.minusDays(55), "squat", reps = 10), row(cutoff.minusDays(7), "squat", reps = 10)))
        val ambiguous = state(historyDays = 56, strengthExposure = StrengthExposure.ABSENT, machineRatio = .9, freeRatio = 0.0)
        val questions = PlanningQuestionPolicy().questions(source, ambiguous, PersonalizedPlanningAnswers())
        assertEquals(setOf(QUESTION_STRENGTH_INTENT, QUESTION_BADMINTON_INTENT, QUESTION_FREE_WEIGHT), questions.map { it.id }.toSet())
        assertTrue(PersonalizedPlanningAnswers().values.isEmpty())
    }

    @Test
    fun `auto and explicit personalized constraints remain distinct and legacy ratio is ignored`() {
        val request = request(days = 7, weeks = 8)
        val auto = resolvePersonalizedRequest(request, PersonalizedGenerationConstraints(explicitSessionMinutes = 60), ProgramGoal.STRENGTH, 3, 4)
        val explicit = resolvePersonalizedRequest(request, PersonalizedGenerationConstraints(explicitWeeklyTrainingDays = 5, explicitDurationWeeks = 6, explicitSessionMinutes = 60), ProgramGoal.STRENGTH, 3, 4)
        assertEquals(3, auto.weeklyTrainingDays)
        assertEquals(4, auto.durationWeeks)
        assertEquals(5, explicit.weeklyTrainingDays)
        assertEquals(6, explicit.durationWeeks)
        assertEquals(0.0, explicit.badmintonTransferRatio, .0)
    }

    @Test
    fun `multiple multi day anchors stay inside finite resistance budget and provenance separates observation from treatment`() {
        val exercises = listOf(exercise("squat", "MAIN_LOWER_STRENGTH"), exercise("press", "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY"))
        val rows = buildList {
            repeat(8) { week ->
                val monday = cutoff.minusWeeks((7 - week).toLong()).with(java.time.DayOfWeek.MONDAY)
                exercises.forEach { exercise ->
                    addAll(session(monday, exercise.stableKey, 100.0))
                    addAll(session(monday.plusDays(2), exercise.stableKey, 80.0))
                    addAll(session(monday.plusDays(4), exercise.stableKey, 90.0))
                }
            }
        }
        val source = snapshot(rows, exercises)
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val gaps = emptyList<AdaptationGap>()
        val intent = BlockIntentPlanner().decide(state, gaps)
        val plan = PersonalizedProgramBuilder().build(source, state, gaps, intent, 4, request(days = 4, weeks = 4), PersonalizedPlanningAnswers(), null)
        val weekOne = plan.items.filter { it.weekNumber == 1 }
        assertTrue(exercises.all { exercise -> weekOne.count { it.exerciseStableKey == exercise.stableKey } <= 3 })
        assertTrue(plan.personalizedDecision!!.planningBudget!!.plannedResistanceSets <= plan.personalizedDecision!!.planningBudget!!.targetResistanceSets)
        assertTrue(plan.personalizedDecision!!.anchorTransitions.all { it.observedStyle == StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM })
        assertTrue(plan.personalizedDecision!!.anchorTransitions.all { it.structureTreatment.name.isNotBlank() && it.doseTreatment.name.isNotBlank() })
    }

    @Test
    fun `gap candidate is paid from finite resistance budget before capacity expansion`() {
        val squat = exercise("squat", "MAIN_LOWER_STRENGTH")
        val pull = exercise("machine_pull", "HORIZONTAL_PULL_STRENGTH", equipment = "MACHINE")
        val rows = (1..8).flatMap { week -> session(cutoff.minusWeeks(week.toLong()), "squat", 100.0) }
        val source = snapshot(rows, listOf(squat, pull))
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val gaps = listOf(AdaptationGap("UPPER_PULL", "HIGH", "fixture"))
        val plan = PersonalizedProgramBuilder().build(source, state, gaps, BlockIntentPlanner().decide(state, gaps), 3, request(days = 3, weeks = 3), PersonalizedPlanningAnswers(), null)
        val budget = plan.personalizedDecision!!.planningBudget!!
        assertTrue(plan.items.any { it.exerciseStableKey == "machine_pull" })
        assertTrue(budget.plannedResistanceSets <= budget.targetResistanceSets)
        assertEquals(budget.plannedResistanceSets, plan.items.filter { it.weekNumber == 1 }.sumOf { it.setCount })
    }

    @Test
    fun `prepared generation freezes cutoff constraints and answers and cannot ask again`() {
        val service = source("app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt")
        val body = service.substringAfter("suspend fun generatePrepared(").substringBefore("/** Compatibility wrapper")
        assertTrue(body.contains("preflight.cutoff"))
        assertTrue(body.contains("preflight.constraints"))
        assertTrue(body.contains("answers"))
        assertFalse(body.contains("LocalDate.now"))
        assertFalse(body.contains("PersonalizedPlanningOutcome.Questions"))
        assertFalse(service.contains("questions(snapshot, state, answers).take(1)"))
        val decisions = source("app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedDecisionComponents.kt")
        assertFalse(decisions.contains("selectedStyle = state.observedStrengthStyle"))
    }

    @Test
    fun `latest week strongest exposure is load reference and future weeks do not auto progress`() {
        val squat = exercise("squat", "MAIN_LOWER_STRENGTH")
        val monday = cutoff.with(java.time.DayOfWeek.MONDAY)
        val rows = session(monday, "squat", 100.0) + session(monday.plusDays(2), "squat", 80.0) + session(monday.plusDays(4), "squat", 90.0) +
            session(monday.minusWeeks(1), "squat", 100.0) + session(monday.minusWeeks(1).plusDays(2), "squat", 80.0) + session(monday.minusWeeks(1).plusDays(4), "squat", 90.0)
        val source = snapshot(rows, listOf(squat))
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val plan = PersonalizedProgramBuilder().build(source, state, emptyList(), BlockIntentPlanner().decide(state, emptyList()), 3, request(days = 3, weeks = 3), PersonalizedPlanningAnswers(), null)
        val heavy = plan.items.first { it.weekNumber == 1 && it.selectionRole.contains("HEAVY") }
        assertTrue(heavy.setPrescriptions.any { it.weightKg == 100.0 })
        val signatures = (1..3).map { week -> plan.items.filter { it.weekNumber == week }.map { it.exerciseStableKey to it.setPrescriptions }.toSet() }
        assertEquals(signatures.first(), signatures[1])
        assertEquals(signatures.first(), signatures[2])
    }

    @Test
    fun `resistance sets and timed drills have separate budgets but share session capacity`() {
        val squat = exercise("squat", "MAIN_LOWER_STRENGTH")
        val drill = exercise("drill", "BADMINTON_FOOTWORK", equipment = "BODYWEIGHT")
        val rows = (1..8).flatMap { week -> session(cutoff.minusWeeks(week.toLong()), "squat", 100.0) }
        val source = snapshot(rows, listOf(squat, drill), objectives = mapOf("drill" to mapOf("FOOTWORK" to 1.0)))
        val state = AthletePlanningStateBuilder().build(source.copy(preferences = source.preferences.copy(badmintonIntent = BadmintonPlanningIntent.ENABLED)), PersonalizedPlanningAnswers())
        val gaps = listOf(AdaptationGap("BADMINTON_FOUNDATIONAL_ONRAMP", "HIGH", "fixture"))
        val plan = PersonalizedProgramBuilder().build(source, state, gaps, BlockIntentPlanner().decide(state, gaps), 3, request(days = 2, weeks = 3, minutes = 30), PersonalizedPlanningAnswers(), null)
        val budget = plan.personalizedDecision!!.planningBudget!!
        assertTrue(budget.targetStructuredBadmintonBouts > 0)
        assertTrue(budget.plannedResistanceSets <= budget.targetResistanceSets)
        assertTrue(plan.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { day -> day.sumOf { it.estimatedDurationSeconds } <= 30 * 60 })
    }

    @Test
    fun `canonical metadata not display name controls movement and novel load stays unknown`() {
        val oddlyNamed = exercise("machine_pull", "HORIZONTAL_PULL_STRENGTH", name = "스쿼트 클린 배드민턴")
        val source = snapshot(listOf(row(cutoff.minusDays(7), "squat"), row(cutoff.minusDays(14), "squat")), listOf(exercise("squat", "MAIN_LOWER_STRENGTH"), oddlyNamed))
        assertEquals(MovementCoverage.HORIZONTAL_PULL, source.movementCoverage("machine_pull"))
        val rx = PersonalizedPrescriptionPlanner().prescribe(source, PlannedExercise("machine_pull", "COVERAGE_UPPER_PULL", "fixture", 80, targetSets = 2), StrengthProgrammingStyle.NONE, 1)
        assertTrue(rx.sets.all { it.weightKg == 0.0 })
    }

    private fun session(date: LocalDate, key: String, load: Double): List<PlanningSetRecord> = (1..3).map { row(date, key, it, 5, load) }

    private fun row(date: LocalDate, key: String, index: Int = 1, reps: Int = 8, weight: Double = 50.0, seconds: Int = 0) =
        PlanningSetRecord(date, key, key, "근력운동", index, reps, weight, seconds, 8.0)

    private fun exercise(key: String, slot: String, equipment: String = "BARBELL", name: String = key) = Exercise(
        stableKey = key,
        name = name,
        category = if (slot == "BADMINTON_FOOTWORK") "배드민턴" else "근력운동",
        equipmentTags = equipment,
        activityKind = "EXERCISE",
        planningEligibility = "PROGRAM_SELECTABLE"
    )

    private fun metadata(exercise: Exercise, slot: String): RuntimeExerciseMetadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
        activityKind = exercise.activityKind,
        planningEligibility = exercise.planningEligibility,
        programSlot = slot,
        progressMetricType = "LOAD_REPS",
        analysisEligibility = MetadataTokenField.parse(if (slot == "BADMINTON_FOOTWORK") "FATIGUE|BADMINTON_TRANSFER" else "FATIGUE|STRENGTH_PROGRESS|HYPERTROPHY_VOLUME"),
        badmintonTransferLevel = if (slot == "BADMINTON_FOOTWORK") "DIRECT" else "NONE",
        sourceConfidenceLevel = "HIGH",
        finalSourceStatus = "SOURCE_ACCEPTED"
    )

    private fun snapshot(
        rows: List<PlanningSetRecord>,
        exercises: List<Exercise> = listOf(exercise("squat", "MAIN_LOWER_STRENGTH")),
        objectives: Map<String, Map<String, Double>> = emptyMap(),
        preferences: PersonalizedPlanningPreferences = PersonalizedPlanningPreferences(),
        recovery: PlanningRecoverySignals = PlanningRecoverySignals(readinessStatus = "NORMAL", tissueStatus = "NORMAL")
    ): PlanningHistorySnapshot {
        val byKey = exercises.associateBy(Exercise::stableKey)
        return PlanningHistorySnapshot(
            cutoff = cutoff,
            allConfirmedSets = rows,
            exercises = byKey,
            metadata = byKey.mapValues { (key, exercise) -> metadata(exercise, slotFor(key)) },
            badmintonObjectives = objectives,
            profilePrimaryGoal = "MIXED",
            strengthTrainingYears = 3.0,
            badmintonTrainingYears = 0.0,
            preferences = preferences,
            canonicalStrengthSignals = byKey.mapValues { CanonicalStrengthSignal(100.0, null, 1, "TEST") },
            recoverySignals = recovery
        )
    }

    private fun anchor(key: String, movement: MovementCoverage, style: StrengthProgrammingStyle = StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS) =
        UserAnchor(key, key, 8, 24, movement.name, "LOAD_REPS", "STABLE", 10.0, style, PlanningConfidence.HIGH, "TEST")

    private fun slotFor(key: String): String = when (key) {
        "squat" -> "MAIN_LOWER_STRENGTH"
        "press", "incumbent" -> "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY"
        "drill" -> "BADMINTON_FOOTWORK"
        "a_free", "z_machine", "machine_pull" -> "HORIZONTAL_PULL_STRENGTH"
        else -> "OTHER"
    }

    private fun state(
        anchors: List<UserAnchor> = emptyList(),
        badminton: BadmintonPlanningIntent = BadmintonPlanningIntent.UNRESOLVED,
        freeWeight: FreeWeightWillingness = FreeWeightWillingness.UNRESOLVED,
        courtLoad: Double = 0.0,
        recovery: PlanningRecoverySignals = PlanningRecoverySignals(readinessStatus = "NORMAL", tissueStatus = "NORMAL"),
        features: Map<String, StyleFeatures> = emptyMap(),
        historyDays: Int = 56,
        strengthExposure: StrengthExposure = StrengthExposure.ABSENT,
        machineRatio: Double = 0.0,
        freeRatio: Double = 0.0
    ) = AthletePlanningState(
        observedBehavior = ObservedTrainingBehavior.GENERAL_MIXED,
        strengthExposure = strengthExposure,
        strengthIntent = StrengthIntent.UNRESOLVED,
        badmintonIntent = badminton,
        freeWeightWillingness = freeWeight,
        primaryAdaptation = "GENERAL_FOUNDATION",
        historyDays = historyDays,
        recentTrainingDaysPerWeek = 3.0,
        scheduleVolatility = 0.0,
        machineSetRatio = machineRatio,
        freeWeightSetRatio = freeRatio,
        anchors = anchors,
        observedStrengthStyle = anchors.firstOrNull()?.style ?: StrengthProgrammingStyle.UNRESOLVED,
        observedStyleConfidence = anchors.firstOrNull()?.styleConfidence ?: PlanningConfidence.LOW,
        structuredBadmintonSessions = 0,
        recoveryConstraint = "",
        confidence = PlanningConfidence.MODERATE,
        genericCourtLoad = courtLoad,
        recoverySignals = recovery,
        styleFeaturesByAnchor = features
    )

    private fun request(days: Int, weeks: Int, minutes: Int = 90) = ProgramSkeletonRequest(
        name = "v010",
        goal = ProgramGoal.STRENGTH,
        weeklyTrainingDays = days,
        sessionMinutes = minutes,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = .7,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = weeks
    )

    private fun posterior(date: LocalDate, medianKg: Double, id: String, key: String = "squat") = StrengthExercisePerformanceHistoryEntity(
        revisionKey = "test", eventUuid = id, sessionKey = id, sessionDate = date.toString(), exerciseStableKey = key,
        priorLogMean = ln(medianKg), priorLogVariance = .1, sessionLikelihoodLogMean = ln(medianKg), sessionLikelihoodLogVariance = .1,
        sessionLikelihoodProper = true, innovationResidualLog = 0.0, innovationVariance = .1, posteriorLogMean = ln(medianKg), posteriorLogVariance = .1,
        posteriorMeanIncrementLog = 0.0, transitionDays = 1, baselineEstablishedBefore = true, baselineEstablishedAfter = true,
        proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "test", curveVersion = "test", rirPolicyVersion = "test",
        evidenceFingerprint = id, createdAt = date.toEpochDay()
    )

    private fun source(path: String): String = sequenceOf(File(path), File("../$path"))
        .first(File::isFile)
        .readText(Charsets.UTF_8)
}
