package com.training.trackplanner.data

import org.junit.Assert.*
import org.junit.Test

internal fun manualSessionDraft(weight: Double = 140.0): GeneratedProgramSkeleton {
    val request = ProgramSkeletonRequest("Manual", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO)
    val items = listOf(1, 3, 5).mapIndexed { index, day -> ProgramSkeletonItem("item-$index", 1, day, 1, "squat", "스쿼트", "", 60, "",
        3, 5, weight, 0, "", "MANUAL_INPUT") } + ProgramSkeletonItem("bench", 1, 1, 2, "bench", "벤치프레스", "", 60, "", 3, 5, weight, 0, "", "MANUAL_INPUT")
    return GeneratedProgramSkeleton("Manual", 7, request, ProgramPeriodizationType.AUTO, emptyList(), items).reconcileProgression(setOf("squat", "bench"))
}

internal fun GeneratedProgramSkeleton.sessionKey(item: String): String = items.single { it.localId == item }.progressionBinding!!.sessionKey
internal fun GeneratedProgramSkeleton.session(item: String): ProgramProgressionTrack = progressionSessions.single { it.key == sessionKey(item) }.track
internal fun GeneratedProgramSkeleton.choose(item: String, link: ProgressionLinkMode, target: String? = null,
    role: ProgressionRole = ProgressionRole.MAIN, mode: ProgressionMode = ProgressionMode.CUSTOM, rule: ProgressionRule = ProgressionRule(incrementKg = 2.5)) =
    configureProgressionSession(item, link, target, role, mode, rule)

class ProgramProgressionDraftTest {
    @Test fun appDefaultsStillUseEightForMainAndNineForAssistance() {
        val draft = manualSessionDraft().choose("item-0", ProgressionLinkMode.SEPARATE, mode = ProgressionMode.APP)
            .choose("item-1", ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE, mode = ProgressionMode.APP)
        assertEquals(ProgressionRule(rpeThreshold = 8.0), draft.session("item-0").rule)
        assertEquals(ProgressionRule(rpeThreshold = 9.0), draft.session("item-1").rule)
    }
    @Test fun zeroWeightHasIndependentSessionAndTypedEligibilityNeverUsesNames() {
        val exercise = Exercise("squat", "Timed court clean name", "anything", activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true)
        assertTrue(progressionEligible(exercise, emptySet()))
        assertFalse(progressionEligible(exercise.copy(activityKind = "SPORT_SESSION"), emptySet()))
        for (role in listOf("PLYOMETRIC", "SKILL_DRILL", "CONDITIONING")) assertFalse(progressionEligible(exercise, setOf(role)))
        assertFalse(progressionEligible(exercise.copy(volumeLoadEligible = false), emptySet()))
        val draft = manualSessionDraft(0.0).choose("item-0", ProgressionLinkMode.SEPARATE)
        assertNotNull(draft.items.first().progressionBinding)
        assertEquals(ProgressionBase.REVIEW, draft.session("item-0").basePolicy)
    }
    @Test fun newJoinAndSeparateHaveDurableSessionKeys() {
        var draft = manualSessionDraft().choose("item-0", ProgressionLinkMode.SEPARATE)
        val a = draft.sessionKey("item-0")
        draft = draft.choose("item-2", ProgressionLinkMode.EXISTING, a)
        draft = draft.choose("item-1", ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE)
        assertEquals(a, draft.sessionKey("item-2"))
        assertNotEquals(a, draft.sessionKey("item-1"))
        assertEquals(draft, draft.reconcileProgression(setOf("squat", "bench")))
    }
    @Test fun crossExerciseAssignmentFailsClosed() {
        val draft = manualSessionDraft()
        assertTrue(runCatching { draft.choose("bench", ProgressionLinkMode.EXISTING, draft.sessionKey("item-0")) }.isFailure)
        assertTrue(runCatching { draft.choose("item-0", ProgressionLinkMode.EXISTING, "missing") }.isFailure)
    }
    @Test fun explicitLinkSurvivesRepsWeightAndSetCountDriftWithReview() {
        var draft = manualSessionDraft().choose("item-0", ProgressionLinkMode.SEPARATE)
        val a = draft.sessionKey("item-0")
        draft = draft.choose("item-2", ProgressionLinkMode.EXISTING, a)
        for (change in listOf<(ProgramSkeletonItem) -> ProgramSkeletonItem>({ it.copy(reps = 10) }, { it.copy(weightKg = 90.0) }, { it.copy(setCount = 4) })) {
            val edited = draft.upsertDraftItem(change(draft.items.single { it.localId == "item-2" })).reconcileProgression(setOf("squat", "bench"))
            assertEquals(a, edited.sessionKey("item-2"))
            assertTrue(edited.session("item-2").needsReview)
        }
    }
    @Test fun deletingFirstMemberCannotDestroyOtherMembersSession() {
        var draft = manualSessionDraft().choose("item-0", ProgressionLinkMode.SEPARATE)
        val a = draft.sessionKey("item-0")
        draft = draft.choose("item-2", ProgressionLinkMode.EXISTING, a).deleteDraftItem("item-0").reconcileProgression(setOf("squat", "bench"))
        assertEquals(a, draft.sessionKey("item-2"))
        assertEquals(2.5, draft.session("item-2").rule.incrementKg!!, 0.0)
    }
    @Test fun rolesAndRulesAreSharedWhileSeparateSessionsStayIndependent() {
        var draft = manualSessionDraft().choose("item-0", ProgressionLinkMode.SEPARATE)
        val a = draft.sessionKey("item-0")
        draft = draft.choose("item-2", ProgressionLinkMode.EXISTING, a)
            .choose("item-1", ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE)
            .choose("item-2", ProgressionLinkMode.EXISTING, a, ProgressionRole.MAIN, ProgressionMode.DIRECT)
        assertEquals(draft.session("item-0"), draft.session("item-2"))
        assertEquals(ProgressionMode.DIRECT, draft.session("item-0").mode)
        assertEquals(ProgressionRole.ASSISTANCE, draft.session("item-1").role)
        draft = draft.choose("item-0", ProgressionLinkMode.EXISTING, a, ProgressionRole.AUTO)
        assertEquals(ProgressionRole.AUTO, draft.session("item-2").roleOverride)
    }
    @Test fun userOverrideWinsOverPlannerAndSeparateSignaturesNeverCollapse() {
        var draft = manualSessionDraft()
        draft = draft.copy(items = draft.items.map { it.copy(progressionStyle = "HEAVY_LIGHT_MEDIUM", progressionVariant = "HEAVY", progressionRole = ProgressionRole.MAIN) })
            .reconcileProgression(setOf("squat", "bench"))
        assertTrue(draft.progressionSessions.all { it.source == ProgressionAuthority.PLANNER_EXPLICIT })
        draft = draft.choose("item-2", ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE)
        val key = draft.sessionKey("item-2")
        assertNotEquals(draft.sessionKey("item-0"), key)
        assertEquals(ProgressionAuthority.USER_EXPLICIT, draft.progressionSessions.single { it.key == key }.source)
        assertEquals(ProgressionRole.ASSISTANCE, draft.session("item-2").role)
    }
    @Test fun automaticClassifierAndGeneratedDriftRemainUnchanged() {
        val a = ProgressionTrackInference.signature("squat", List(3) { ProgramSetPrescription(it + 1, 5, 100.0, 0) })
        assertFalse(ProgressionTrackInference.sameTrack(a, a.copy(setCount = 4)))
        assertFalse(ProgressionTrackInference.sameTrack(a, a.copy(baseKg = 90.0)))
        assertTrue(ProgressionTrackInference.sameTrack(a, a.copy(baseKg = 103.0)))
        val generated = a.copy(style = "HLM", variant = "HEAVY")
        assertTrue(ProgressionTrackInference.sameTrack(generated, generated.copy(setCount = 4, baseKg = 90.0)))
    }
}
