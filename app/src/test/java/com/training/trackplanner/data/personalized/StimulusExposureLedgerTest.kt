package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusExposureLedgerTest {
    private val cutoff = LocalDate.of(2026, 9, 20)

    @Test
    fun oneObservationCanSatisfyPowerAndRfdWithoutFacetDuplication() {
        val profile = profile(
            "medicine_ball_throw",
            qualities = listOf(
                pq("power", TrainableQuality.POWER, PhysicalQualityRegion.ROTATIONAL, PhysicalQualityMode.BALLISTIC),
                pq("rfd", TrainableQuality.RAPID_FORCE_PRODUCTION, PhysicalQualityRegion.ROTATIONAL, PhysicalQualityMode.BALLISTIC)
            )
        )
        val source = source("medicine_ball_throw", 11, 2)
        val observation = setObservation(source, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL)
        val ledger = StimulusExposureLedger(mapOf(profile.stableKey to profile), listOf(observation), emptyList(), cutoff)

        val power = ledger.query(StimulusFacetFilter(quality = TrainableQuality.POWER))
        val rfd = ledger.query(StimulusFacetFilter(quality = TrainableQuality.RAPID_FORCE_PRODUCTION))
        assertEquals(1, power.size)
        assertEquals(1, rfd.size)
        assertEquals(power.single().source.portableObservationId, rfd.single().source.portableObservationId)
        assertEquals(1, ledger.summary(StimulusFacetFilter(quality = TrainableQuality.POWER)).confirmedSets)
        assertEquals(1, ledger.setObservations.size)
    }

    @Test
    fun canonicalSentinelsUseIndependentFacets() {
        val profiles = listOf(
            profile("weighted_pull_up", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.UPPER_PULL, PhysicalQualityMode.PULL)), patterns = setOf("VERTICAL_PULL")),
            profile("chest_supported_row", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.UPPER_PULL, PhysicalQualityMode.PULL)), patterns = setOf("HORIZONTAL_PULL")),
            profile("back_squat", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, relationId = "back-squat")), laterality = "BILATERAL", patterns = setOf("SQUAT")),
            profile("bulgarian_split_squat", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, relationId = "bulgarian")), laterality = "UNILATERAL", patterns = setOf("LUNGE")),
            profile("deadlift", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.POSTERIOR_CHAIN, PhysicalQualityMode.HINGE, relationId = "deadlift")), patterns = setOf("HINGE")),
            profile("rdl", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.POSTERIOR_CHAIN, PhysicalQualityMode.HINGE, relationId = "rdl")), patterns = setOf("HINGE")),
            profile("single_leg_press", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, relationId = "single-leg-press")), laterality = "UNILATERAL", patterns = setOf("SQUAT")),
            profile("dead_bug", patterns = setOf("ANTI_EXTENSION", "DYNAMIC_TRUNK_STABILIZATION")),
            profile("pallof", patterns = setOf("ANTI_ROTATION")),
            profile("calf_raise", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.ANKLE, PhysicalQualityMode.GENERAL, relationId = "calf"))),
            profile("pogo", qualities = listOf(pq("ssc", TrainableQuality.REACTIVE_STRENGTH_SSC, PhysicalQualityRegion.ANKLE, PhysicalQualityMode.PLYOMETRIC, relationId = "pogo"))),
            profile("inverted_row", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.UPPER_PULL, PhysicalQualityMode.PULL, relationId = "row")), patterns = setOf("HORIZONTAL_PULL")),
            profile("one_leg_curl", qualities = listOf(pq("hamstring", TrainableQuality.HYPERTROPHY, PhysicalQualityRegion.HAMSTRING, PhysicalQualityMode.GENERAL, relationId = "curl")), laterality = "UNILATERAL")
        )
        val observations = profiles.mapIndexed { index, item -> setObservation(source(item.stableKey, index.toLong() + 1, 1), PlannedActivityKind.RESISTANCE) }
        val ledger = StimulusExposureLedger(profiles.associateBy { it.stableKey }, observations, emptyList(), cutoff)

        assertTrue(ledger.query(StimulusFacetFilter(movementPatterns = setOf("VERTICAL_PULL"))).single().source.stableKey == "weighted_pull_up")
        assertFalse(ledger.query(StimulusFacetFilter(movementPatterns = setOf("HORIZONTAL_PULL"))).any { it.source.stableKey == "weighted_pull_up" })
        assertTrue(ledger.query(StimulusFacetFilter(movementPatterns = setOf("HORIZONTAL_PULL"))).map { it.source.stableKey }.containsAll(listOf("chest_supported_row", "inverted_row")))
        assertFalse(ledger.query(StimulusFacetFilter(movementPatterns = setOf("VERTICAL_PULL"))).any { it.source.stableKey == "chest_supported_row" })
        assertEquals("back_squat", ledger.query(StimulusFacetFilter(quality = TrainableQuality.STRENGTH, acceptedRegions = setOf(PhysicalQualityRegion.LOWER), laterality = setOf("BILATERAL"))).single().source.stableKey)
        assertFalse(ledger.query(StimulusFacetFilter(quality = TrainableQuality.STRENGTH, laterality = setOf("UNILATERAL"))).any { it.source.stableKey == "back_squat" })
        val unilateralLower = ledger.query(StimulusFacetFilter(quality = TrainableQuality.STRENGTH, acceptedRegions = setOf(PhysicalQualityRegion.LOWER, PhysicalQualityRegion.UNILATERAL_LOWER), laterality = setOf("UNILATERAL")))
        assertEquals(setOf("bulgarian_split_squat", "single_leg_press"), unilateralLower.map { it.source.stableKey }.toSet())
        assertEquals(setOf("deadlift", "rdl"), ledger.query(StimulusFacetFilter(acceptedRegions = setOf(PhysicalQualityRegion.POSTERIOR_CHAIN), acceptedModes = setOf(PhysicalQualityMode.HINGE))).map { it.source.stableKey }.toSet())
        val deadBug = ledger.query(StimulusFacetFilter(movementPatterns = setOf("ANTI_EXTENSION", "DYNAMIC_TRUNK_STABILIZATION")))
        assertEquals(1, deadBug.size)
        assertEquals("dead_bug", deadBug.single().source.stableKey)
        assertFalse(ledger.query(StimulusFacetFilter(movementPatterns = setOf("ANTI_ROTATION"))).any { it.source.stableKey == "dead_bug" })
        assertEquals("pallof", ledger.query(StimulusFacetFilter(movementPatterns = setOf("ANTI_ROTATION"))).single().source.stableKey)
        assertFalse(ledger.query(StimulusFacetFilter(movementPatterns = setOf("ANTI_EXTENSION"))).any { it.source.stableKey == "pallof" })
        assertTrue(ledger.query(StimulusFacetFilter(quality = TrainableQuality.REACTIVE_STRENGTH_SSC)).single().source.stableKey == "pogo")
        assertFalse(ledger.query(StimulusFacetFilter(quality = TrainableQuality.REACTIVE_STRENGTH_SSC)).any { it.source.stableKey == "calf_raise" })
        assertFalse(ledger.query(StimulusFacetFilter(quality = TrainableQuality.REACTIVE_STRENGTH_SSC)).any { it.source.stableKey == "one_leg_curl" })
        assertFalse(ledger.query(StimulusFacetFilter(movementPatterns = setOf("VERTICAL_PULL"))).any { it.source.stableKey == "inverted_row" })
        assertEquals("one_leg_curl", ledger.query(StimulusFacetFilter(quality = TrainableQuality.HYPERTROPHY)).single { it.source.stableKey == "one_leg_curl" }.source.stableKey)
    }

    @Test
    fun boundedWindowAndSessionOccurrenceRemainLossless() {
        val profile = profile("squat", qualities = listOf(pq("strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT)))
        val inWindowA = setObservation(source("squat", 1, 1, cutoff, "session-a"), PlannedActivityKind.RESISTANCE)
        val inWindowB = setObservation(source("squat", 2, 1, cutoff, "session-b"), PlannedActivityKind.RESISTANCE)
        val shifted = setObservation(source("squat", 3, 1, cutoff.minusDays(1), "session-a"), PlannedActivityKind.RESISTANCE)
        val old = setObservation(source("squat", 4, 1, cutoff.minusDays(56), "old"), PlannedActivityKind.RESISTANCE)
        val ledger = StimulusExposureLedger(mapOf(profile.stableKey to profile), listOf(inWindowA, inWindowB, shifted, old), emptyList(), cutoff)
        val summary = ledger.summary()
        assertEquals(3, summary.confirmedSets)
        assertEquals(3, summary.sessions)
        assertEquals(2, summary.trainingDays)
        assertEquals(setOf(0), summary.activeCutoffRelativeBins)
    }

    @Test
    fun courtObservationDoesNotMatchResistanceFacet() {
        val profile = profile("court", patterns = setOf("COURT_SESSION"))
        val source = source("court", 10, null)
        val court = CourtExposureObservation(source, 30.0, 8.0, 30.0, profile.stableKey)
        val ledger = StimulusExposureLedger(mapOf(profile.stableKey to profile), emptyList(), listOf(court), cutoff)
        assertEquals(0, ledger.query(StimulusFacetFilter(quality = TrainableQuality.STRENGTH)).size)
        assertEquals(1, ledger.matchingCourtObservations().size)
        assertEquals(1, ledger.summary().sessions)
    }

    @Test
    fun builderKeepsOneProfilePerStableKeyAndDropsOlderHistory() {
        val exercise = Exercise(stableKey = "squat", name = "Squat", category = "STRENGTH", laterality = "BILATERAL")
        val relation = pq("squat-strength", TrainableQuality.STRENGTH, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT)
            .copy(exerciseStableKey = "squat")
        val history = listOf(
            record(1, cutoff, "squat", listOf(set(10, 1), set(11, 2))),
            record(2, cutoff.minusDays(55), "squat", listOf(set(12, 1))),
            record(3, cutoff.minusDays(56), "old", listOf(set(13, 1)))
        )
        val ledger = StimulusExposureLedgerBuilder().build(
            cutoff = cutoff,
            history = history,
            exercises = mapOf("squat" to exercise),
            metadata = emptyMap(),
            physicalQualityCatalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)),
            movementRelations = emptyList(),
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY
        )
        assertEquals(setOf("squat"), ledger.facetProfilesByStableKey.keys)
        assertEquals(3, ledger.setObservations.size)
        assertTrue(ledger.setObservations.none { it.source.stableKey == "old" })
        assertEquals(3, ledger.summary(StimulusFacetFilter(quality = TrainableQuality.STRENGTH)).confirmedSets)
    }

    private fun profile(
        key: String,
        qualities: List<ExercisePhysicalQualityRelation> = emptyList(),
        laterality: String? = null,
        patterns: Set<String> = emptySet()
    ): CanonicalStimulusFacetProfile = CanonicalStimulusFacetProfile(
        stableKey = key,
        physicalQualities = qualities,
        movementPatterns = patterns.mapIndexed { index, value ->
            com.training.trackplanner.data.CanonicalMetadataRelation(
                domain = com.training.trackplanner.data.CanonicalRelationDomain.MOVEMENT,
                relationKey = "pattern-$key-$index",
                exerciseStableKey = key,
                relationType = "MOVEMENT_PATTERN",
                relationValue = value,
                coefficient = null,
                sourceStableKey = key,
                status = "PASS",
                provenance = "TEST"
            )
        },
        intrinsicLaterality = laterality,
        integrity = StimulusFacetIntegrity.CONSISTENT
    )

    private fun pq(id: String, quality: TrainableQuality, region: PhysicalQualityRegion, mode: PhysicalQualityMode, relationId: String = id) =
        ExercisePhysicalQualityRelation(relationId, "test", quality, StimulusCapabilityLevel.DIRECT_CAPABILITY, region, mode, true, "TEST", setOf("test"), "PASS", "test")

    private fun source(key: String, entryId: Long, setId: Long?, date: LocalDate = cutoff, session: String = "session") =
        StimulusSourceRef(entryId, "backup-$entryId", setId, setId?.toInt(), session, date, key)

    private fun setObservation(source: StimulusSourceRef, activity: PlannedActivityKind) =
        StimulusSetObservation(source, activity, 5, 50.0, 0, 8.0, RealizedStimulusClass.STRENGTH_LIKE, source.stableKey,
            StimulusClassificationAuthority.REVIEWED_CANONICAL)

    private fun record(id: Long, date: LocalDate, key: String, sets: List<WorkoutSet>) =
        WorkoutEntryWithSets(
            WorkoutEntry(id = id, date = date.toString(), exerciseStableKey = key, exerciseName = key, category = "STRENGTH", sessionStableKey = "session-$id"),
            sets.map { it.copy(entryId = id) }
        )

    private fun set(id: Long, index: Int) = WorkoutSet(id = id, entryId = 0, setIndex = index, reps = 5, weightKg = 80.0, confirmed = true)
}
