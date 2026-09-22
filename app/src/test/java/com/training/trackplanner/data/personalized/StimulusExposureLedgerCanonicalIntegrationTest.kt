package com.training.trackplanner.data.personalized

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.RuntimeExerciseMetadata
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusExposureLedgerCanonicalIntegrationTest {
    private val cutoff = LocalDate.of(2026, 9, 20)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CanonicalExerciseMetadataRepository(context)

    @Test
    fun repairedCanonicalSentinelsRemainQueryableWithoutSyntheticFacetMetadata() {
        val exercises = repository.exercises(includeHistory = true).associateBy(Exercise::stableKey)
        val metadata = repository.runtimeMetadataCatalog().all().associateBy(RuntimeExerciseMetadata::stableKey)
        val physicalQualityCatalog = repository.physicalQualityCatalog()
        val movementRelations = repository.movementRelations()
        val coreCatalog = repository.coreCatalog()
        val badmintonCatalog = repository.badmintonObjectiveCatalog()
        val roleCatalog = ExerciseRoleRelationCatalog.of(
            repository.trainingRoleRelations(),
            repository.programSlotCapabilityRelations()
        )
        val sentinelKeys = setOf(
            "ex_e2efd0fe",
            "ex_a091b9fe",
            "ex_ab468462",
            "ex_e159d15a",
            "ex_8824026f",
            "ex_ae9ecdbc"
        )
        assertTrue(sentinelKeys.all(exercises::containsKey))
        assertTrue(sentinelKeys.all(metadata::containsKey))

        val history = sentinelKeys.mapIndexed { index, stableKey ->
            record(index.toLong() + 1, exercises.getValue(stableKey), index.toLong())
        }
        val ledger = StimulusExposureLedgerBuilder().build(
            cutoff = cutoff,
            history = history,
            exercises = exercises,
            metadata = metadata,
            physicalQualityCatalog = physicalQualityCatalog,
            movementRelations = movementRelations,
            coreCatalog = coreCatalog,
            badmintonCatalog = badmintonCatalog,
            exerciseRoleCatalog = roleCatalog
        )

        val bulgarian = ledger.facetProfilesByStableKey.getValue("ex_e2efd0fe")
        assertTrue(bulgarian.physicalQualities.any {
            it.qualityId == TrainableQuality.STRENGTH &&
                it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                it.regionQualifier == PhysicalQualityRegion.LOWER &&
                it.modeQualifier == PhysicalQualityMode.SQUAT
        })
        assertEquals("UNILATERAL", bulgarian.intrinsicLaterality)

        val singleLegPress = ledger.facetProfilesByStableKey.getValue("ex_a091b9fe")
        assertTrue(singleLegPress.physicalQualities.any {
            it.qualityId == TrainableQuality.STRENGTH && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
        })
        assertTrue(singleLegPress.physicalQualities.any {
            it.qualityId == TrainableQuality.HYPERTROPHY && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
        })
        assertTrue(singleLegPress.physicalQualities.all {
            it.regionQualifier == PhysicalQualityRegion.LOWER && it.modeQualifier == PhysicalQualityMode.SQUAT
        })
        assertEquals("UNILATERAL", singleLegPress.intrinsicLaterality)

        val ordinaryLegPress = ledger.facetProfilesByStableKey.getValue("ex_ab468462")
        assertEquals("BILATERAL", ordinaryLegPress.intrinsicLaterality)

        val unilateralLowerStrength = StimulusFacetFilter(
            quality = TrainableQuality.STRENGTH,
            acceptedRegions = setOf(PhysicalQualityRegion.LOWER, PhysicalQualityRegion.UNILATERAL_LOWER),
            laterality = setOf("UNILATERAL")
        )
        val bilateralLowerStrength = unilateralLowerStrength.copy(laterality = setOf("BILATERAL"))
        assertTrue(ledger.query(unilateralLowerStrength).map { it.source.stableKey }.toSet().containsAll(setOf("ex_e2efd0fe", "ex_a091b9fe")))
        assertFalse(ledger.query(unilateralLowerStrength).any { it.source.stableKey == "ex_ab468462" })
        assertFalse(ledger.query(bilateralLowerStrength).any { it.source.stableKey in setOf("ex_e2efd0fe", "ex_a091b9fe") })
        assertTrue(ledger.query(bilateralLowerStrength).any { it.source.stableKey == "ex_ab468462" })

        assertEquals(setOf("ex_e159d15a"), ledger.query(StimulusFacetFilter(movementPatterns = setOf("HORIZONTAL_PULL"))).map { it.source.stableKey }.toSet())
        assertFalse(ledger.query(StimulusFacetFilter(movementPatterns = setOf("VERTICAL_PULL"))).any { it.source.stableKey == "ex_e159d15a" })

        val oneLegCurl = ledger.facetProfilesByStableKey.getValue("ex_8824026f")
        assertTrue(oneLegCurl.physicalQualities.any {
            it.qualityId == TrainableQuality.HYPERTROPHY &&
                it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                it.regionQualifier == PhysicalQualityRegion.HAMSTRING
        })
        assertFalse(ledger.query(StimulusFacetFilter(quality = TrainableQuality.REACTIVE_STRENGTH_SSC)).any { it.source.stableKey == "ex_8824026f" })

        assertEquals(1, ledger.courtObservations.size)
        assertEquals("ex_ae9ecdbc", ledger.courtObservations.single().source.stableKey)
        assertTrue(ledger.matchingCourtObservations().isNotEmpty())
        assertFalse(ledger.query(unilateralLowerStrength).any { it.source.stableKey == "ex_ae9ecdbc" })
    }

    @Test
    fun canonicalLedgerFeedsB1EvidenceAndKeepsGenericCourtSeparate() {
        val exercises = repository.exercises(includeHistory = true).associateBy(Exercise::stableKey)
        val metadata = repository.runtimeMetadataCatalog().all().associateBy(RuntimeExerciseMetadata::stableKey)
        val history = listOf(
            "ex_e2efd0fe", // Bulgarian split squat
            "ex_a091b9fe", // single-leg leg press
            "ex_ab468462", // ordinary leg press
            "ex_e159d15a", // inverted row
            "ex_8824026f", // one-leg leg curl
            "ex_ae9ecdbc"  // generic badminton session
        ).mapIndexed { index, stableKey ->
            val reps = if (stableKey in setOf("ex_a091b9fe", "ex_8824026f")) 8 else 5
            record(index.toLong() + 20, exercises.getValue(stableKey), index.toLong(), reps)
        }
        val ledger = StimulusExposureLedgerBuilder().build(
            cutoff = cutoff,
            history = history,
            exercises = exercises,
            metadata = metadata,
            physicalQualityCatalog = repository.physicalQualityCatalog(),
            movementRelations = repository.movementRelations(),
            coreCatalog = repository.coreCatalog(),
            badmintonCatalog = repository.badmintonObjectiveCatalog(),
            exerciseRoleCatalog = ExerciseRoleRelationCatalog.of(
                repository.trainingRoleRelations(), repository.programSlotCapabilityRelations()
            )
        )
        val snapshot = PlanningHistorySnapshot(
            cutoff = cutoff,
            allConfirmedSets = history.flatMap { item ->
                item.sets.filter { it.confirmed }.map { set ->
                    PlanningSetRecord(
                        date = LocalDate.parse(item.entry.date),
                        stableKey = item.entry.exerciseStableKey,
                        exerciseName = item.entry.exerciseName,
                        category = item.entry.category,
                        setIndex = set.setIndex,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        seconds = set.seconds,
                        rpe = set.rpe ?: item.entry.rpe
                    )
                }
            },
            exercises = exercises,
            metadata = metadata,
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "STRENGTH_GAIN",
            strengthTrainingYears = 1.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(
                strengthIntent = StrengthIntent.STRENGTH_PRIORITY,
                badmintonIntent = BadmintonPlanningIntent.ENABLED,
                freeWeightWillingness = FreeWeightWillingness.WILLING
            ),
            stimulusExposureLedger = ledger
        )
        val evidence = StimulusNeedEvidenceIndexBuilder().build(snapshot)
        assertEquals(3, evidence.qualityEvidence.getValue(TrainableQuality.STRENGTH).current28d.directUnits)
        assertEquals(2, evidence.qualityEvidence.getValue(TrainableQuality.HYPERTROPHY).current28d.directUnits)
        assertTrue(evidence.currentStrengthStableKeys.containsAll(setOf("ex_e2efd0fe", "ex_ab468462", "ex_e159d15a")))
        assertTrue(ledger.facetProfilesByStableKey.getValue("ex_e2efd0fe").badmintonObjectives.any {
            it.objective == BadmintonObjective.DECELERATION &&
                it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE
        })
        assertTrue(evidence.taskEvidence.getValue("DECELERATION").current28d.supportiveUnits > 0)
        assertTrue(evidence.courtContext.current28d.sessions > 0)
        assertTrue(evidence.courtContext.current28d.durationMinutes > 0.0)
        val withoutCourt = StimulusNeedEvidenceIndexBuilder().build(
            snapshot.copy(stimulusExposureLedger = ledger.copy(courtObservations = emptyList()))
        )
        assertEquals(withoutCourt.qualityEvidence, evidence.qualityEvidence)
        assertEquals(withoutCourt.taskEvidence, evidence.taskEvidence)
        assertEquals(0, withoutCourt.courtContext.current28d.sessions)
        assertEquals(0.0, withoutCourt.courtContext.current28d.durationMinutes, 0.0)
    }

    @Test
    fun canonicalLedgerBackedDoseHistoryUsesExtendedCompletedWeekCoverage() {
        val exercises = repository.exercises(includeHistory = true).associateBy(Exercise::stableKey)
        val metadata = repository.runtimeMetadataCatalog().all().associateBy(RuntimeExerciseMetadata::stableKey)
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val history = listOf(55L, 48L, 41L, 34L).mapIndexed { index, offset ->
            record(index.toLong() + 80, exercises.getValue("ex_e2efd0fe"), offset, reps = 5)
        } + record(100, exercises.getValue("ex_ae9ecdbc"), 0)
        val ledger = StimulusExposureLedgerBuilder().build(
            cutoff = cutoff,
            history = history,
            exercises = exercises,
            metadata = metadata,
            physicalQualityCatalog = repository.physicalQualityCatalog(),
            movementRelations = repository.movementRelations(),
            coreCatalog = repository.coreCatalog(),
            badmintonCatalog = repository.badmintonObjectiveCatalog(),
            exerciseRoleCatalog = ExerciseRoleRelationCatalog.of(
                repository.trainingRoleRelations(), repository.programSlotCapabilityRelations()
            ),
            historyStart = horizon.ledgerStart
        )
        val snapshot = PlanningHistorySnapshot(
            cutoff = cutoff,
            allConfirmedSets = history.map { item ->
                val set = item.sets.single()
                PlanningSetRecord(LocalDate.parse(item.entry.date), item.entry.exerciseStableKey, item.entry.exerciseName,
                    item.entry.category, set.setIndex, set.reps, set.weightKg, set.seconds, set.rpe)
            },
            exercises = exercises,
            metadata = metadata,
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "STRENGTH_GAIN",
            strengthTrainingYears = 1.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            stimulusExposureLedger = ledger
        )
        val state = AthletePlanningState(
            ObservedTrainingBehavior.UNKNOWN, StrengthExposure.PRESENT, StrengthIntent.STRENGTH_PRIORITY,
            BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.UNRESOLVED, "MIXED", 56, 3.0, 0.0,
            0.0, 1.0, emptyList(), StrengthProgrammingStyle.UNRESOLVED, PlanningConfidence.LOW, 0,
            "NONE", PlanningConfidence.MODERATE
        )
        val legacy = QualityDoseHistoryAnalyzer().analyze(snapshot, state, repository.physicalQualityCatalog())
        val shadow = LedgerBackedQualityDoseHistoryAnalyzer().analyze(snapshot, state, legacy)
        val strength = shadow.bands.getValue(TrainableQuality.STRENGTH)
        val needProfile = AthleteStimulusNeedEngine().analyze(snapshot, state)
        val strengthNeed = needProfile.qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        val portfolio = StimulusTrainingDecisionPortfolioEngine().build(needProfile, shadow)
        val strengthDecision = portfolio.qualityDecisions.single { it.quality == TrainableQuality.STRENGTH }

        assertEquals(horizon.ledgerStart, ledger.historyStart)
        assertEquals(4, shadow.weeklyEvidence.getValue(TrainableQuality.STRENGTH).count { it.directUnits > 0 })
        assertEquals(4, strength.directExposureWeekCount)
        assertTrue(shadow.reasonCodes.contains("GENERIC_COURT_EXCLUDED_FROM_QUALITY_DOSE"))
        assertEquals(TrainingNeedDecision.DEVELOP, strengthNeed.decision)
        assertTrue(shadow.available)
        assertTrue(strength.hasPersonalDirectBaseline)
        assertEquals(SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, strength.source)
        assertEquals(StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE, strengthDecision.strategy)
        assertTrue(portfolio.shadowOnly)
        assertFalse(portfolio.prescriptionAuthority)
        assertFalse(portfolio.selectionAuthority)
        assertFalse(portfolio.placementAuthority)
    }

    private fun record(id: Long, exercise: Exercise, dayOffset: Long, reps: Int = 8): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = id,
            date = cutoff.minusDays(dayOffset).toString(),
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            rpe = 8.0,
            sessionStableKey = "canonical-sentinel-$id"
        )
        val workoutSet = WorkoutSet(
            id = id * 10,
            entryId = id,
            setIndex = 1,
            reps = reps,
            weightKg = 50.0,
            seconds = if (exercise.stableKey == "ex_ae9ecdbc") 600 else 0,
            confirmed = true,
            rpe = 8.0
        )
        return WorkoutEntryWithSets(entry, listOf(workoutSet))
    }
}
