package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AthleteStimulusNeedTest {
    private val cutoff = LocalDate.of(2026, 9, 20)

    @Test
    fun prescriptionCompatibilityAndSessionIdentityAreAudited() {
        val strength = profile("s", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val supportive = profile("support", relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val observations = listOf(
            observation("s", 1, cutoff, "same-day-a", 5),
            observation("s", 2, cutoff, "same-day-b", 5),
            observation("support", 3, cutoff, "support", 5),
            observation("s", 5, cutoff, "incompatible", 12),
            observation("s", 4, cutoff.minusDays(28), "prior", 5)
        )
        val snapshot = snapshot(listOf("s", "support"), observations, mapOf(strength.stableKey to strength, supportive.stableKey to supportive))
        val profile = AthleteStimulusNeedEngine().analyze(snapshot, AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers()))
        val need = profile.qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(2, need.exposure.current28d.directUnits)
        assertEquals(1, need.exposure.current28d.excludedDirectByPrescriptionUnits)
        assertEquals(1, need.exposure.current28d.supportiveUnits)
        assertEquals(2, need.exposure.current28d.directSessions)
        assertEquals(1, need.exposure.current28d.directTrainingDays)
        assertTrue("DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE" in need.exposure.reasonCodes)
    }

    @Test
    fun finalAuditUsesActualSetsAndExposesDistributionDelta() {
        val strength = profile("s", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val snapshot = snapshot(listOf("s"), emptyList(), mapOf(strength.stableKey to strength))
        val beforeRow = item("before", 1, List(6) { ProgramSetPrescription(it + 1, 5, 50.0, 0) })
        val afterRows = listOf(
            item("after-a", 1, List(3) { ProgramSetPrescription(it + 1, 5, 50.0, 0) }),
            item("after-b", 3, List(3) { ProgramSetPrescription(it + 1, 5, 50.0, 0) })
        )
        val request = ProgramSkeletonRequest("audit", ProgramGoal.BODYBUILDING, 3, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1)
        fun plan(rows: List<ProgramSkeletonItem>) = com.training.trackplanner.data.GeneratedProgramSkeleton("audit", 7, request, request.periodizationType, emptyList(), rows)
        val audit = FinalStimulusNeedAudit().audit(plan(afterRows), snapshot, CanonicalExercisePhysicalQualityCatalog.EMPTY, plan(listOf(beforeRow)))
        assertEquals(6, audit.qualityBefore.getValue(TrainableQuality.STRENGTH).directUnits)
        assertEquals(6, audit.qualityAfter.getValue(TrainableQuality.STRENGTH).directUnits)
        assertEquals(1, audit.qualityBefore.getValue(TrainableQuality.STRENGTH).directSessions)
        assertEquals(2, audit.qualityAfter.getValue(TrainableQuality.STRENGTH).directSessions)
        assertEquals(1, audit.qualityDeltas.getValue(TrainableQuality.STRENGTH).directSessionsBefore)
        assertEquals(2, audit.qualityDeltas.getValue(TrainableQuality.STRENGTH).directSessionsAfter)
    }

    private fun snapshot(keys: List<String>, observations: List<StimulusSetObservation>, profiles: Map<String, CanonicalStimulusFacetProfile>): PlanningHistorySnapshot {
        val exercises = keys.associateWith { Exercise(it, it, "STRENGTH", equipment = "BODYWEIGHT") }
        val metadata = exercises.mapValues { (_, exercise) -> RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            activityKind = "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE", programSlot = "MAIN_LOWER_STRENGTH",
            analysisEligibility = com.training.trackplanner.data.MetadataTokenField.parse("STRENGTH_PROGRESS"), progressMetricType = "LOAD_REPS") }
        val sets = if (observations.isEmpty()) listOf(PlanningSetRecord(cutoff, "s", "s", "STRENGTH", 1, 5, 50.0, 0, null))
        else observations.map { PlanningSetRecord(it.source.date, it.source.stableKey, it.source.stableKey, "STRENGTH", it.source.setIndex ?: 1, it.reps, it.weightKg, it.seconds, it.rpe) }
        return PlanningHistorySnapshot(cutoff, sets, exercises, metadata, emptyMap(), "STRENGTH_GAIN", 1.0, 0.0,
            PersonalizedPlanningPreferences(strengthIntent = StrengthIntent.STRENGTH_PRIORITY, badmintonIntent = BadmintonPlanningIntent.DISABLED,
                freeWeightWillingness = FreeWeightWillingness.WILLING), stimulusExposureLedger = StimulusExposureLedger(profiles, observations, emptyList(), cutoff))
    }

    private fun profile(key: String, relation: ExercisePhysicalQualityRelation) = CanonicalStimulusFacetProfile(key, physicalQualities = listOf(relation), integrity = StimulusFacetIntegrity.CONSISTENT)

    private fun relation(id: String, quality: TrainableQuality, level: StimulusCapabilityLevel) = ExercisePhysicalQualityRelation(
        id, id, quality, level, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", "TEST")

    private fun observation(key: String, id: Long, date: LocalDate, session: String, reps: Int) = StimulusSetObservation(
        StimulusSourceRef(id, "backup-$id", id, 1, session, date, key), PlannedActivityKind.RESISTANCE, reps, 50.0, 0, 8.0,
        if (reps in 1..6) RealizedStimulusClass.STRENGTH_LIKE else if (reps in 7..15) RealizedStimulusClass.HYPERTROPHY_LIKE else RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS, key)

    private fun item(id: String, day: Int, sets: List<ProgramSetPrescription>) = ProgramSkeletonItem(
        localId = id, weekNumber = 1, dayOfWeek = day, orderIndex = 1, exerciseStableKey = "s", exerciseName = "s", category = "STRENGTH",
        restSeconds = 60, prescription = "test", setCount = sets.size, reps = sets.first().reps, weightKg = sets.first().weightKg, seconds = 0,
        selectionReason = "test", weightSource = "test", setPrescriptions = sets)
}
