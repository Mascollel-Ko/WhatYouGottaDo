package com.training.trackplanner.data

import org.junit.Assert.*
import org.junit.Test

class ProgramProgressionEngineTest {
    private fun signature(kg: Double, reps: Int = 5, key: String = "exact", rm: Double? = 100.0, style: String = "", variant: String = "") =
        ProgressionTrackInference.signature(key, (1..3).map { ProgramSetPrescription(it, reps, kg, 0) }, rm, style, variant)
    private fun link(sequence: Int, track: String = "track", mode: ProgressionMode = ProgressionMode.APP, rule: ProgressionRule = ProgressionRule(incrementKg = 2.5)) =
        ProgramWorkoutLink(sequence.toLong(), "application", "program", "item-$sequence", "Program", sequence, 1, track, "Track", sequence,
            ProgressionRole.MAIN, mode, ProgressionBase.UNIFORM, null, false, rule)
    private fun session(sequence: Int, actual: Double = 140.0, planned: Double = 140.0, reps: Int = 5, rpe: Double? = 7.0, track: String = "track") =
        ProgressionSession(link(sequence, track), (1..3).map { WorkoutSet(entryId = sequence.toLong(), setIndex = it, reps = reps, weightKg = actual, confirmed = true, rpe = rpe) },
            (1..3).map { ProgramPrescriptionSet(sequence.toLong(), it, 5, planned, 0) })

    @Test fun nearbyRelativeIntensityMatches() { assertTrue(ProgressionTrackInference.sameTrack(signature(80.0), signature(82.0))) }
    @Test fun differentRepIntentSeparates() { assertFalse(ProgressionTrackInference.sameTrack(signature(80.0), signature(65.0, 10))) }
    @Test fun sameRepsDifferentIntensitySeparates() { assertFalse(ProgressionTrackInference.sameTrack(signature(80.0), signature(65.0))) }
    @Test fun exactIdentityOnly() { assertFalse(ProgressionTrackInference.sameTrack(signature(80.0), signature(80.0, key = "another"))) }
    @Test fun missingOneRmNotInvented() { assertNull(signature(80.0, rm = null).relativeIntensity); assertNull(signature(80.0, rm = null).oneRmSnapshotKg) }
    @Test fun authorSignatureHasNoRpeField() { assertFalse(ProgressionSignature::class.java.declaredFields.any { it.name.contains("rpe", true) }) }
    @Test fun explicitHlmHeavyChainsOnlyHeavy() {
        assertTrue(ProgressionTrackInference.sameTrack(signature(80.0, style = "HEAVY_LIGHT_MEDIUM", variant = "HEAVY"), signature(90.0, style = "HEAVY_LIGHT_MEDIUM", variant = "HEAVY")))
        assertFalse(ProgressionTrackInference.sameTrack(signature(80.0, style = "HEAVY_LIGHT_MEDIUM", variant = "HEAVY"), signature(80.0, style = "HEAVY_LIGHT_MEDIUM", variant = "LIGHT")))
    }
    @Test fun explicitDupSeparatesVolumeAndStrength() {
        assertFalse(ProgressionTrackInference.sameTrack(signature(80.0, style = "DUP_LIKE_UNDULATING", variant = "STRENGTH"), signature(80.0, style = "DUP_LIKE_UNDULATING", variant = "VOLUME")))
    }
    @Test fun directionIsPreviousActualNotCurrent145Plan() {
        val result = ProgressionEngine.evaluate(link(2), listOf(session(1)), false)
        assertEquals(ProgressionDirection.INCREASE, result.direction); assertEquals(142.5, result.kg!!, 0.0)
    }
    @Test fun actual137Point5IsBaselineNotPlanned140() {
        val result = ProgressionEngine.evaluate(link(2), listOf(session(1, actual = 137.5)), false)
        assertEquals(137.5, result.kg!!, 0.0); assertEquals(ProgressionDirection.HOLD, result.direction)
    }
    @Test fun anotherTrackAndApplicationNeverPredecessor() {
        assertEquals(1, ProgressionEngine.predecessors(link(3), listOf(session(1), session(2, track = "other"), session(2).let { it.copy(link = it.link.copy(applicationId = "other")) })).size)
    }
    @Test fun firstMissHolds() { assertEquals(ProgressionDirection.HOLD, ProgressionEngine.evaluate(link(2), listOf(session(1, reps = 4)), false).direction) }
    @Test fun repeatedMissDecreases() { assertEquals(ProgressionDirection.DECREASE, ProgressionEngine.evaluate(link(3), listOf(session(1, reps = 4), session(2, reps = 4)), false).direction) }
    @Test fun onlyRelatedLocalRestrictionBlocks() {
        assertEquals(ProgressionDirection.HOLD, ProgressionEngine.evaluate(link(2), listOf(session(1)), true).direction)
        assertEquals(ProgressionDirection.INCREASE, ProgressionEngine.evaluate(link(2), listOf(session(1)), false).direction)
    }
    @Test fun highOfiNotDirectionInput() { assertFalse(ProgressionEngine::class.java.declaredFields.any { it.name.contains("ofi", true) }) }
    @Test fun missingRpeDefaultHoldCustomCompletionOnlyIncreases() {
        assertEquals(ProgressionDirection.HOLD, ProgressionEngine.evaluate(link(2), listOf(session(1, rpe = null)), false).direction)
        assertEquals(ProgressionDirection.INCREASE, ProgressionEngine.evaluate(link(2, rule = ProgressionRule(incrementKg = 2.5, missingRpe = MissingProgressionRpe.COMPLETION_ONLY)), listOf(session(1, rpe = null)), false).direction)
    }
    @Test fun customThresholdAndSuccessCountRespected() {
        assertEquals(ProgressionDirection.HOLD, ProgressionEngine.evaluate(link(2, rule = ProgressionRule(rpeThreshold = 6.0)), listOf(session(1)), false).direction)
        assertEquals(ProgressionDirection.HOLD, ProgressionEngine.evaluate(link(2, rule = ProgressionRule(successesRequired = 2)), listOf(session(1)), false).direction)
        assertEquals(ProgressionDirection.INCREASE, ProgressionEngine.evaluate(link(3, rule = ProgressionRule(successesRequired = 2)), listOf(session(1), session(2)), false).direction)
    }
    @Test fun directJudgmentDoesNotProposeAlgorithmLoad() { assertNull(ProgressionEngine.evaluate(link(2, mode = ProgressionMode.DIRECT), listOf(session(1)), false).kg) }
    @Test fun mixedManualLoadDoesNotUseLastSet() {
        val sets = session(1).actual.mapIndexed { index, set -> set.copy(weightKg = 100.0 + index) }
        assertNull(ProgressionEngine.base(sets, ProgressionBase.UNIFORM, null))
        assertEquals(101.0, ProgressionEngine.base(sets, ProgressionBase.ANCHOR, 2)!!, 0.0)
    }
    @Test fun stepPrecedenceAndConservativeFallback() {
        assertEquals(1.0, ProgressionEngine.loadStep(1.0, listOf(100.0, 102.5, 105.0), 105.0)!!, 0.0)
        assertEquals(2.5, ProgressionEngine.loadStep(null, listOf(100.0, 102.5, 105.0), 105.0)!!, 0.0)
        assertNull(ProgressionEngine.loadStep(null, emptyList(), 2.0))
    }
    @Test fun ballisticAndSportExcludedRegardlessOfWeightedName() {
        val exercise = Exercise("exact", "Heavy squat", "", activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true)
        assertTrue(progressionEligible(exercise, setOf("STRENGTH")))
        assertFalse(progressionEligible(exercise, setOf("PLYOMETRIC")))
        assertFalse(progressionEligible(exercise.copy(activityKind = "SPORT_SESSION"), setOf("STRENGTH")))
    }
    @Test fun absentTypedAnchorCannotBeBypassedByCompletionOnlyPolicy() {
        val rule = ProgressionRule(rpePolicy = ProgressionRpePolicy.ANCHOR, missingRpe = MissingProgressionRpe.COMPLETION_ONLY)
        assertEquals(ProgressionDirection.REVIEW, ProgressionEngine.evaluate(link(2, rule = rule), listOf(session(1)), false).direction)
    }
    @Test fun partialSessionNotPredecessor() {
        val incomplete = session(1).let { it.copy(actual = it.actual.mapIndexed { i, s -> s.copy(confirmed = i != 2) }) }
        assertTrue(ProgressionEngine.predecessors(link(2), listOf(incomplete)).isEmpty())
    }
}
