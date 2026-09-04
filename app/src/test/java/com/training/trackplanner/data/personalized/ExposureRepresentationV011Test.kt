package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseProgramSlotCapabilityRelation
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.ExerciseTrainingRoleRelation
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

class ExposureRepresentationV011Test {
    private val cutoff = LocalDate.of(2026, 2, 28)
    private val fixture by lazy {
        val relative = "tools/planner_reference/fixtures/v011_exposure_representation_golden.json"
        val file = sequenceOf(File(relative), File("../$relative")).first(File::isFile)
        JSONObject(file.readText())
    }

    @Test
    fun `movement analyzer matches all Python v011 golden ratios states confidence and gap priorities`() {
        val cases = fixture.getJSONArray("movementCases")
        repeat(cases.length()) { caseIndex ->
            val case = cases.getJSONObject(caseIndex)
            val snapshot = movementSnapshot(case)
            val actual = MovementExposureRepresentationAnalyzer().analyze(snapshot, case.getJSONObject("priorities").has("CORE_DIRECT"))
                .associateBy(MovementExposureRepresentation::movementCoverage)
            val expectedRoot = case.getJSONObject("expected")
            assertEquals(case.getString("id"), expectedRoot.getBoolean("foundationalOnramp"), actual.values.sumOf { it.currentExposure28d } == 0.0)
            val expected = expectedRoot.getJSONObject("representations")
            expected.keys().forEach { key ->
                val value = actual.getValue(key)
                val target = expected.getJSONObject(key)
                assertEquals(case.getString("id"), target.getString("state"), value.representationState.name)
                assertEquals(case.getString("id"), target.getString("confidence"), value.evidenceConfidence.name)
                assertNullable(case.getString("id"), target, "currentShare", value.currentShare)
                assertNullable(case.getString("id"), target, "priorShare", value.priorShare)
                assertNullable(case.getString("id"), target, "peerReference", value.peerReference)
                assertNullable(case.getString("id"), target, "peerRepresentationRatio", value.peerRepresentationRatio)
                assertNullable(case.getString("id"), target, "personalRetentionRatio", value.personalRetentionRatio)
                val priority = ExposureRepresentationPolicy.movementGapPriority(value.basePriority, value.representationState, value.evidenceConfidence)
                assertJsonNullable(case.getString("id"), target, "gapPriority", priority)
            }
        }
    }

    @Test
    fun `badminton policy matches all Python v011 golden ratios states and priority caps`() {
        val cases = fixture.getJSONArray("badmintonCases")
        repeat(cases.length()) { caseIndex ->
            val case = cases.getJSONObject(caseIndex)
            val current = objectiveMap(case.getJSONObject("currentWeighted"))
            val prior = objectiveMap(case.getJSONObject("priorWeighted"))
            val currentDirect = objectiveMap(case.getJSONObject("currentDirect"))
            val priorDirect = objectiveMap(case.getJSONObject("priorDirect"))
            val currentTotal = current.values.sum()
            val priorTotal = prior.values.sum()
            val confidence = ExposureRepresentationPolicy.confidence(case.getInt("activeBins"))
            val expected = case.getJSONObject("expected").getJSONObject("representations")
            BadmintonObjective.entries.forEach { objective ->
                val key = objective.name
                val currentShare = current.getValue(key).takeIf { currentTotal > 0 }?.div(currentTotal)
                val priorShare = prior.getValue(key).takeIf { priorTotal > 0 }?.div(priorTotal)
                val personal = if (currentShare != null && priorShare != null && priorShare > 0) currentShare / priorShare else null
                val peers = current.filterKeys { it != key }.values.filter { it > 0 }
                val peerMedian = peers.takeIf { it.size >= 3 }?.median()
                val peer = peerMedian?.let { current.getValue(key) / it }
                val state = ExposureRepresentationPolicy.badmintonState(current.getValue(key), confidence, peer, personal)
                val directDrop = priorDirect.getValue(key) > 0 && currentDirect.getValue(key) == 0.0
                val neverDirect = priorDirect.getValue(key) == 0.0 && currentDirect.getValue(key) == 0.0
                val peerOnly = personal == null && peer != null && peer <= ExposureRepresentationPolicy.SEVERE_RATIO
                val target = expected.getJSONObject(key)
                assertEquals(case.getString("id"), target.getString("state"), state.name)
                assertEquals(case.getString("id"), target.getBoolean("directDrop"), directDrop)
                assertEquals(case.getString("id"), target.getBoolean("neverDirectObserved"), neverDirect)
                assertNullable(case.getString("id"), target, "currentShare", currentShare)
                assertNullable(case.getString("id"), target, "priorShare", priorShare)
                assertNullable(case.getString("id"), target, "peerMedianCurrent", peerMedian)
                assertNullable(case.getString("id"), target, "peerRepresentationRatio", peer)
                assertNullable(case.getString("id"), target, "personalRetentionRatio", personal)
                assertJsonNullable(case.getString("id"), target, "gapPriority", ExposureRepresentationPolicy.badmintonGapPriority(state, confidence, peerOnly, directDrop))
            }
        }
    }

    @Test
    fun `activity resolver uses typed authority and excludes plyometric SSC and deceleration from resistance`() {
        val definitions = mapOf(
            "drop" to Triple("PLYOMETRIC_JUMP_VARIANTS", "PLYOMETRIC_POWER", "QUALITY_BASED"),
            "line_hop" to Triple("ANKLE_STIFFNESS_SSC_CONDITIONING", "ANKLE_SSC_CONDITIONING", "TIME_OR_REPS"),
            "lateral_bound" to Triple("LATERAL_BOUND_LANDING_DECELERATION_VARIANTS", "DECELERATION_LANDING", "QUALITY_BASED"),
            "deceleration" to Triple("DECELERATION_LANDING", "DECELERATION_LANDING", "QUALITY_BASED"),
            "squat" to Triple("SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "ESTIMATED_1RM"),
            "deadlift" to Triple("DEADLIFT_HINGE_VARIANTS", "MAIN_HINGE_STRENGTH", "ESTIMATED_1RM"),
            "row" to Triple("ROW_VARIANTS", "HORIZONTAL_PULL_STRENGTH", "VOLUME_LOAD"),
            "curl" to Triple("ELBOW_FLEXION_BICEPS_CURL_VARIANTS", "BICEPS_ACCESSORY", "VOLUME_LOAD"),
            "core" to Triple("ANTI_ROTATION_ANTI_EXTENSION_CORE", "CORE_STABILITY_ACCESSORY", "REPS_OR_TIME")
        )
        val snapshot = snapshotForDefinitions(definitions)
        listOf("drop", "line_hop", "lateral_bound", "deceleration").forEach {
            assertEquals(it, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL, snapshot.activityKind(it))
        }
        listOf("squat", "deadlift", "row", "curl", "core").forEach {
            assertEquals(it, PlannedActivityKind.RESISTANCE, snapshot.activityKind(it))
        }
        assertEquals(PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL, snapshot.copy(
            badmintonDirectObjectives = mapOf("drop" to setOf("JUMP_LANDING"))
        ).activityKind("drop"))
    }

    @Test
    fun `comparison windows include exact 27 28 55 boundaries and exclude day 56`() {
        val base = snapshotForDefinitions(mapOf("LOWER_KNEE" to Triple("SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "LOAD_REPS")))
        val snapshot = base.copy(allConfirmedSets = listOf(
            row(cutoff.minusDays(27), "LOWER_KNEE"),
            row(cutoff.minusDays(28), "LOWER_KNEE"),
            row(cutoff.minusDays(55), "LOWER_KNEE"),
            row(cutoff.minusDays(56), "LOWER_KNEE")
        ))
        val value = MovementExposureRepresentationAnalyzer().analyze(snapshot, false).single { it.movementCoverage == "LOWER_KNEE" }
        assertEquals(1.0, value.currentExposure28d, 0.0)
        assertEquals(2.0, value.priorExposure28d, 0.0)
        assertEquals(1, value.currentActiveBins)
    }

    @Test
    fun `badminton analyzer keeps weighted supportive evidence separate from direct disappearance`() {
        val definitions = mapOf(
            "prior_direct" to Triple("DECELERATION_LANDING", "DECELERATION_LANDING", "QUALITY_BASED"),
            "current_supportive" to Triple("SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "LOAD_REPS"),
            "court" to Triple("OTHER", "OTHER", "SESSION_DURATION")
        )
        val base = snapshotForDefinitions(definitions, activityKinds = mapOf("court" to "SPORT_SESSION"))
        val rows = buildList {
            add(row(cutoff.minusDays(40), "prior_direct"))
            listOf(3L, 10L, 17L, 24L).forEach { day ->
                add(row(cutoff.minusDays(day), "current_supportive"))
                add(row(cutoff.minusDays(day), "court", seconds = 3600))
            }
        }
        val snapshot = base.copy(
            allConfirmedSets = rows,
            badmintonObjectives = mapOf("prior_direct" to mapOf("DECELERATION" to 1.0), "current_supportive" to mapOf("DECELERATION" to .6), "court" to mapOf("DECELERATION" to 1.0)),
            badmintonDirectObjectives = mapOf("prior_direct" to setOf("DECELERATION"), "court" to setOf("DECELERATION"))
        )
        val deceleration = BadmintonObjectiveRepresentationAnalyzer().analyze(snapshot).single { it.objective == "DECELERATION" }
        assertTrue(deceleration.currentWeighted28d > 0.0)
        assertEquals(0.0, deceleration.currentDirect28d, 0.0)
        assertTrue(deceleration.priorDirect28d > 0.0)
        assertTrue(deceleration.directDrop)
        assertTrue("WEIGHTED_WITHOUT_DIRECT" in deceleration.reasonCodes)
        assertEquals(4, deceleration.currentActiveBins)
    }

    @Test
    fun `foundational and developmental evidence do not create multiplied transition pressure`() {
        val absent = listOf(
            MovementExposureRepresentation("LOWER_KNEE", RepresentationPriority.HIGH, 0.0, 0.0, 0, null, null, null, null, null, RepresentationState.ABSENT, PlanningConfidence.LOW, listOf("CURRENT_EXPOSURE_ABSENT")),
            MovementExposureRepresentation("CORE_DIRECT", RepresentationPriority.MODERATE, 0.0, 0.0, 0, null, null, null, null, null, RepresentationState.ABSENT, PlanningConfidence.LOW, listOf("CURRENT_EXPOSURE_ABSENT"))
        )
        val badminton = BadmintonObjective.entries.map {
            BadmintonObjectiveRepresentation(it.name, 1.0, 1.0, 0.0, 0.0, 1.0 / 9, 1.0 / 9, 1.0, 1.0, 1.0, 4, PlanningConfidence.HIGH, false, true, RepresentationState.NO_CLEAR_DEFICIT_SIGNAL, listOf("NEVER_DIRECT_OBSERVED"))
        }
        val state = state().copy(
            badmintonIntent = BadmintonPlanningIntent.ENABLED,
            movementRepresentations = absent,
            resistanceFoundationalOnramp = true,
            badmintonObjectiveRepresentations = badminton
        )
        val gaps = AdaptationGapAnalyzer().analyze(emptySnapshot(), state)
        assertEquals(1, gaps.count { it.code == "RESISTANCE_FOUNDATIONAL_ONRAMP" })
        assertFalse(gaps.any { it.code in setOf("LOWER_KNEE", "CORE_DIRECT") })
        assertEquals(1, gaps.count { it.code.startsWith("BADMINTON_DEVELOP_") })
        assertFalse(gaps.single { it.code.startsWith("BADMINTON_DEVELOP_") }.contributesTransitionPressure)
    }

    @Test
    fun `direct remediation requires exact direct relation and safe prescription authority`() {
        val definitions = mapOf(
            "lateral_bound_continuous" to Triple("LATERAL_BOUND_CONTINUOUS_VARIANTS", "PLYOMETRIC_POWER", "QUALITY_BASED"),
            "supportive_squat" to Triple("SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "LOAD_REPS"),
            "novel_direct" to Triple("DECELERATION_LANDING", "DECELERATION_LANDING", "QUALITY_BASED")
        )
        val base = snapshotForDefinitions(definitions).copy(
            badmintonObjectives = mapOf(
                "lateral_bound_continuous" to mapOf("DECELERATION" to 1.0),
                "supportive_squat" to mapOf("DECELERATION" to .6),
                "novel_direct" to mapOf("DECELERATION" to 1.0)
            ),
            badmintonDirectObjectives = mapOf("lateral_bound_continuous" to setOf("DECELERATION"), "novel_direct" to setOf("DECELERATION"))
        )
        val gap = AdaptationGap("BADMINTON_UNDERREPRESENTED_DECELERATION", "MODERATE", "fixture")
        val selected = GapCandidateSelector().select(base, state(), listOf(gap), emptySet())
        assertEquals(listOf("lateral_bound_continuous"), selected.map(PlannedExercise::stableKey))
        assertEquals(PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL, base.activityKind(selected.single().stableKey))
        val noReviewed = base.copy(exercises = base.exercises - "lateral_bound_continuous")
        assertTrue(GapCandidateSelector().select(noReviewed, state(), listOf(gap), emptySet()).isEmpty())
    }

    @Test
    fun `production planner has no name or stableKey substring semantic classifier and binary gap path is retired`() {
        val relative = "src/main/java/com/training/trackplanner/data/personalized"
        val directory = sequenceOf(File(relative), File("app/$relative")).first(File::isDirectory)
        val source = directory.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("name.contains(\"", "stableKey.contains(\"", "expectedShare", "ExposureSufficiency", "ADEQUATE").forEach { forbidden ->
            assertFalse(forbidden, forbidden in source)
        }
        val gapAnalyzer = File(directory, "PersonalizedDecisionComponents.kt").readText().substringAfter("class AdaptationGapAnalyzer")
        assertFalse("binary count==0 movement gap", "groupingBy" in gapAnalyzer.substringBefore("class PlanningHorizonPlanner"))
    }

    private fun movementSnapshot(case: JSONObject): PlanningHistorySnapshot {
        val priorities = case.getJSONObject("priorities")
        val definitions = priorities.keys().asSequence().associateWith { key ->
            when (key) {
                "LOWER_KNEE" -> Triple("SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "LOAD_REPS")
                "POSTERIOR_CHAIN" -> Triple("DEADLIFT_HINGE_VARIANTS", "MAIN_HINGE_STRENGTH", "LOAD_REPS")
                "HORIZONTAL_PUSH" -> Triple("PRESS_VARIANTS", "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY", "LOAD_REPS")
                "UPPER_PULL" -> Triple("ROW_VARIANTS", "HORIZONTAL_PULL_STRENGTH", "LOAD_REPS")
                else -> Triple("ANTI_ROTATION_ANTI_EXTENSION_CORE", "CORE_STABILITY_ACCESSORY", "REPS_OR_TIME")
            }
        }.toMutableMap()
        if (case.has("excludedAthleticBouts")) definitions["athletic"] = Triple("PLYOMETRIC_JUMP_VARIANTS", "PLYOMETRIC_POWER", "QUALITY_BASED")
        val base = snapshotForDefinitions(definitions)
        val rows = mutableListOf<PlanningSetRecord>()
        appendCountRows(rows, case.getJSONObject("current"), case.getInt("activeBins"), current = true)
        appendCountRows(rows, case.getJSONObject("prior"), 4, current = false)
        if (case.has("excludedAthleticBouts")) repeat(case.getInt("excludedAthleticBouts")) { rows += row(cutoff.minusDays((it % 4) * 7L + 3), "athletic") }
        return base.copy(allConfirmedSets = rows)
    }

    private fun appendCountRows(target: MutableList<PlanningSetRecord>, counts: JSONObject, activeBins: Int, current: Boolean) {
        val expanded = counts.keys().asSequence().flatMap { key -> List(counts.getDouble(key).toInt()) { key }.asSequence() }.toList()
        expanded.forEachIndexed { index, key ->
            val bin = if (activeBins == 0) 0 else index % activeBins
            val day = if (current) bin * 7L + 3 else 31L + bin * 7L
            target += row(cutoff.minusDays(day), key)
        }
    }

    private fun snapshotForDefinitions(
        definitions: Map<String, Triple<String, String, String>>,
        activityKinds: Map<String, String> = emptyMap()
    ): PlanningHistorySnapshot {
        val exercises = definitions.map { (key, _) -> Exercise(stableKey = key, name = key, category = "fixture", activityKind = activityKinds[key] ?: "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE") }
        val byKey = exercises.associateBy(Exercise::stableKey)
        val metadata = definitions.mapValues { (key, definition) ->
            metadata(byKey.getValue(key), definition.first, definition.second, definition.third)
        }
        val athletic = definitions.filterValues { it.second in setOf("PLYOMETRIC_POWER", "ANKLE_SSC_CONDITIONING", "DECELERATION_LANDING") }.keys
        val strength = definitions.keys - athletic
        val roles = athletic.map { ExerciseTrainingRoleRelation(it, "PLYOMETRIC", "TEST", "APPROVED") } +
            strength.map { ExerciseTrainingRoleRelation(it, "STRENGTH", "TEST", "APPROVED") }
        val capabilities = athletic.map { ExerciseProgramSlotCapabilityRelation(it, "PLYOMETRIC_SLOT", "TEST", "APPROVED") }
        return PlanningHistorySnapshot(
            cutoff, emptyList(), byKey, metadata, emptyMap(), "MIXED", 3.0, 0.0, PersonalizedPlanningPreferences(),
            exerciseRoleCatalog = ExerciseRoleRelationCatalog.of(roles, capabilities)
        )
    }

    private fun emptySnapshot() = PlanningHistorySnapshot(cutoff, emptyList(), emptyMap(), emptyMap(), emptyMap(), "MIXED", 0.0, 0.0, PersonalizedPlanningPreferences())

    private fun metadata(exercise: Exercise, family: String, slot: String, metric: String): RuntimeExerciseMetadata =
        RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            activityKind = exercise.activityKind,
            planningEligibility = exercise.planningEligibility,
            movementFamily = family,
            programSlot = slot,
            progressMetricType = metric,
            analysisEligibility = MetadataTokenField.parse(if (slot in setOf("PLYOMETRIC_POWER", "ANKLE_SSC_CONDITIONING", "DECELERATION_LANDING")) "FATIGUE|BADMINTON_TRANSFER" else "FATIGUE|STRENGTH_PROGRESS|HYPERTROPHY_VOLUME"),
            sourceConfidenceLevel = "HIGH",
            finalSourceStatus = "SOURCE_ACCEPTED"
        )

    private fun row(date: LocalDate, key: String, seconds: Int = 0) = PlanningSetRecord(date, key, key, "fixture", 1, 8, 20.0, seconds, null)

    private fun state() = AthletePlanningState(
        ObservedTrainingBehavior.GENERAL_MIXED, StrengthExposure.PRESENT, StrengthIntent.MIXED,
        BadmintonPlanningIntent.ENABLED, FreeWeightWillingness.WILLING, "BADMINTON_SUPPORT", 56,
        3.0, 0.0, 0.0, 1.0, emptyList(), StrengthProgrammingStyle.NONE, PlanningConfidence.HIGH,
        4, "NONE", PlanningConfidence.HIGH
    )

    private fun objectiveMap(source: JSONObject): Map<String, Double> = BadmintonObjective.entries.associate { it.name to source.optDouble(it.name, 0.0) }

    private fun assertNullable(caseId: String, target: JSONObject, key: String, actual: Double?) {
        if (target.isNull(key)) assertNull("$caseId:$key", actual)
        else assertTrue("$caseId:$key expected=${target.getDouble(key)} actual=$actual", actual != null && abs(target.getDouble(key) - actual) <= fixture.getDouble("epsilon"))
    }

    private fun assertJsonNullable(caseId: String, target: JSONObject, key: String, actual: String?) {
        if (target.isNull(key)) assertNull("$caseId:$key", actual) else assertEquals("$caseId:$key", target.getString(key), actual)
    }

    private fun List<Double>.median(): Double = sorted().let { if (size % 2 == 1) it[size / 2] else (it[size / 2 - 1] + it[size / 2]) / 2.0 }
}
