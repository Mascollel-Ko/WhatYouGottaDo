package com.training.trackplanner.data.v22

import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BCoachTrainingLayoutPolicyTest {
    private val policy = BCoachTrainingLayoutPolicy
    private val full = BTrainingLayoutPreference.FULL_BODY_DISTRIBUTED
    private val split = BTrainingLayoutPreference.BODY_PART_FOCUSED
    private val auto = BTrainingLayoutPreference.AUTO_BALANCED
    private val pull = "ex_e41f4c2b"
    private val high = "fixture_high_pullup"
    private val deadBug = "ex_d5bdffe1"
    private val ab = "fixture_ab_slide"
    private val bench = "ex_3a7d3eda"
    private val incline = "ex_a61f1e96"
    private val press = "ex_79f3bdbe"
    private val leg = "fixture_leg_curl"
    private val calf = "fixture_calf_raise"

    private fun relation(key: String, muscle: String, role: String = "PRIMARY") = CanonicalMetadataRelation(
        domain = CanonicalRelationDomain.MUSCLE, relationKey = "$key-$muscle-$role",
        exerciseStableKey = key, relationType = role, relationValue = muscle, coefficient = null,
        sourceStableKey = key, status = "REVIEWED", provenance = "TEST"
    )
    private val relations = listOf(
        relation(pull, "BACK_LATS"), relation(high, "BACK_LATS"),
        relation(deadBug, "ANTERIOR_CORE"), relation(ab, "ANTERIOR_CORE"),
        relation(bench, "CHEST"), relation(incline, "CHEST"), relation(press, "SHOULDERS"),
        relation(bench, "SHOULDERS", "SECONDARY"), relation(incline, "SHOULDERS", "SECONDARY"),
        relation(bench, "TRICEPS", "SECONDARY"), relation(incline, "TRICEPS", "SECONDARY"),
        relation(press, "TRICEPS", "SECONDARY"),
        relation(bench, "ANTERIOR_CORE", "STABILIZER"),
        relation(leg, "HAMSTRINGS"), relation(calf, "CALVES")
    )
    private val muscle = policy.coefficients(relations)
    private fun row(key: String, day: Int, order: Int, sets: Int = 3, week: Int = 1) = ProgramSkeletonItem(
        localId = "w$week-$key", weekNumber = week, dayOfWeek = day, orderIndex = order,
        exerciseStableKey = key, stableKey = key, exerciseName = key, category = "Strength",
        restSeconds = 60, prescription = "fixture", setCount = sets, reps = 5, weightKg = 0.0,
        seconds = 0, selectionReason = "B12_BLOCK_1_FOCUS_BALANCED", weightSource = "USER_CALIBRATION_REQUIRED",
        selectionRole = "ACCESSORY|B12_FOCUS_BALANCED",
        setPrescriptions = List(sets) { index ->
            ProgramSetPrescription(index + 1, 5, 0.0, 0, null, ProgramLoadState.USER_CALIBRATION_REQUIRED)
        }
    )
    private fun plan(vararg rows: ProgramSkeletonItem): GeneratedProgramSkeleton {
        val weeks = rows.maxOf { it.weekNumber }
        val days = rows.groupBy { it.weekNumber }.mapValues { (_, items) -> items.map { it.dayOfWeek }.toSet() }
        val request = ProgramSkeletonRequest(
            name = "Layout test", goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
            weeklyTrainingDays = days.values.maxOf { it.size }, sessionMinutes = 120,
            availableEquipment = emptySet(), excludedExerciseText = "", badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO", periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = weeks
        )
        return GeneratedProgramSkeleton(suggestedName = request.name, durationDays = weeks * 7,
            request = request, periodizationType = ProgramPeriodizationType.AUTO, weekPlans = emptyList(),
            items = rows.toList(), weekDaySchedule = days)
    }
    private fun req(pref: BTrainingLayoutPreference, minutes: Int = 120) = V22GenerationRequest(
        name = "Layout test", weeks = 1, days = 2, mode = V22Mode.B,
        asOf = LocalDate.of(2026, 11, 26), sessionMinutes = minutes, trainingLayoutPreference = pref
    )
    private fun exercises(p: GeneratedProgramSkeleton) = p.items.associate { item ->
        item.exerciseStableKey to Exercise(stableKey = item.exerciseStableKey, name = item.exerciseName,
            category = item.category, equipment = "BODYWEIGHT", metadataConfidence = "REVIEWED",
            progressMetricType = "LOAD_REPS")
    }
    private fun neighbors(p: GeneratedProgramSkeleton, pref: BTrainingLayoutPreference, minutes: Int = 120) =
        policy.neighbors(p, req(pref, minutes), exercises(p), setOf(pull, incline, bench), muscle)
    private fun score(p: GeneratedProgramSkeleton) = policy.score(p, null, muscle).concentration

    @Test fun oneSidedDuplicateCanMoveOrSwapWithCleanDay() {
        val p = plan(row(pull, 1, 1), row(high, 1, 2), row(leg, 4, 1))
        assertEquals(1.0, score(p), 1e-12)
        val layouts = neighbors(p, full)
        assertTrue(layouts.any { it.first.startsWith("LAYOUT_MOVE") })
        assertTrue(layouts.any { it.first.startsWith("LAYOUT_SWAP") })
        assertTrue(layouts.all { policy.samePrescription(p, it.second) && score(it.second) < score(p) })
        assertTrue(layouts.any { (_, c) -> c.items.single { it.exerciseStableKey == pull }.dayOfWeek == 4 })
        assertEquals(0.0, layouts.minOf { score(it.second) }, 1e-12)
        assertTrue(neighbors(p, auto).isEmpty())
        assertTrue(neighbors(p, full, 1).isEmpty())
    }

    @Test fun coreAndPressPairsUseTypedSecondaryButNotStabilizer() {
        assertEquals(0.5, muscle.getValue(bench).getValue("SHOULDERS"), 0.0)
        assertFalse(muscle.getValue(bench).containsKey("ANTERIOR_CORE"))
        val core = plan(row(deadBug, 1, 1), row(ab, 1, 2), row(leg, 4, 1))
        assertEquals(0.0, neighbors(core, full).minOf { score(it.second) }, 1e-12)
        val push = plan(row(bench, 1, 1), row(incline, 1, 2), row(press, 1, 3), row(calf, 4, 1))
        assertEquals(1.0, score(push), 1e-12)
        assertTrue(neighbors(push, full).any { score(it.second) < 0.5 })
        val mixed = plan(row(bench, 1, 1), row(press, 1, 2), row(leg, 4, 1))
        assertEquals(1.0, score(mixed), 1e-12)
        assertTrue(neighbors(mixed, full).isNotEmpty())
    }

    @Test fun focusedPreferenceReversesDirectionWithoutChangingSets() {
        val p = plan(row(pull, 1, 1), row(high, 4, 1), row(leg, 4, 2), row(calf, 1, 2))
        assertEquals(0.0, score(p), 1e-12)
        val candidates = neighbors(p, split)
        assertTrue(candidates.isNotEmpty())
        assertEquals(1.0, candidates.maxOf { score(it.second) }, 1e-12)
        assertTrue(candidates.all { policy.samePrescription(p, it.second) })
        assertTrue(neighbors(p, full).isEmpty())
    }

    @Test fun singleDaySingleExposureAndMissingMetadataAreNotPunished() {
        val one = plan(row(pull, 1, 1), row(high, 1, 2))
        assertEquals(0.0, policy.score(one, null, muscle).loss(full), 0.0)
        assertEquals(0.0, policy.score(one, null, muscle).loss(split), 0.0)
        val unique = plan(row(pull, 1, 1), row(leg, 4, 1))
        assertEquals(0.0, policy.score(unique, null, muscle).comparableVolume, 0.0)
        assertTrue(policy.neighbors(unique, req(split), exercises(unique), emptySet(), emptyMap()).isEmpty())
    }

    @Test fun fixedRowsRemainInPlaceAndRepeatedPatternStaysInBlock() {
        val locked = row(pull, 1, 1).copy(selectionReason = "USER_EXERCISE_LOCK")
        val fixed = row(leg, 4, 1).let { item ->
            item.copy(weightSource = "USER_FIXED", weightKg = 10.0,
                setPrescriptions = item.setPrescriptions.map { it.copy(weightKg = 10.0,
                    loadState = ProgramLoadState.EXPLICIT_LOAD, loadSource = ProgramLoadSource.USER_FIXED) })
        }
        val p = plan(locked, row(high, 1, 2, 5), fixed, row(calf, 4, 2))
        assertTrue(neighbors(p, full).isNotEmpty())
        assertTrue(neighbors(p, full).all { (_, candidate) ->
            policy.samePrescription(p, candidate) &&
                candidate.items.single { it.localId == locked.localId } == locked &&
                candidate.items.single { it.localId == fixed.localId } == fixed
        })
        val repeated = plan(row(pull, 1, 1), row(high, 1, 2), row(leg, 4, 1),
            row(pull, 1, 1, week = 2), row(high, 1, 2, week = 2), row(leg, 4, 1, week = 2))
        val result = neighbors(repeated, full).first().second
        assertTrue(result.items.groupBy { it.exerciseStableKey }.values.all { items ->
            items.map { it.dayOfWeek }.distinct().size == 1
        })
    }

    @Test fun explicitPreferenceIsBoundToInputFingerprint() {
        val request = req(auto)
        val fingerprint = BCoachMultiobjectiveProduction.inputFingerprint(request, emptyList())
        assertNotEquals(fingerprint, BCoachMultiobjectiveProduction.inputFingerprint(req(full), emptyList()))
        assertNotEquals(BCoachMultiobjectiveProduction.inputFingerprint(req(full), emptyList()),
            BCoachMultiobjectiveProduction.inputFingerprint(req(split), emptyList()))
    }

    @Test fun productionRecommendationPreservesAutoRosterAndExplicitApproval() {
        val p = plan(row(pull, 1, 1), row(high, 1, 2), row(leg, 4, 1), row(calf, 4, 2))
        fun generate(pref: BTrainingLayoutPreference) = BCoachMultiobjectiveProduction.prepare(
            request = req(pref), history = emptyList(), exercises = exercises(p),
            baseline = p, candidate = p, muscleRelations = relations,
            mainExerciseKeys = p.items.map { it.exerciseStableKey }.toSet()
        )
        val automatic = generate(auto)
        val distributed = generate(full)
        fun recommendation(out: BCoachMultiobjectiveProduction.Outcome): GeneratedProgramSkeleton =
            requireNotNull(out.proposal?.candidateForReview(requireNotNull(out.decision.recommendedCandidateId)))
        val before = recommendation(automatic)
        val after = recommendation(distributed)
        assertTrue(policy.samePrescription(before, after))
        assertTrue(score(after) <= score(before) + 1e-9)
        assertEquals(full.name, distributed.decision.inputAudit.getValue("trainingLayoutPreference"))
        assertTrue(distributed.decision.alternatives.first().recommended)
        val approved = distributed.proposal!!.accept(requireNotNull(distributed.decision.proposalFingerprint),
            distributed.skeleton, requireNotNull(distributed.decision.recommendedCandidateId))
        assertTrue(approved.accepted)
        assertNotNull(approved.skeleton)
    }
}
