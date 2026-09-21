package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerBackedQualityDoseHistoryTest {
    @Test
    fun horizonUsesOldestCompletedIsoWeekForMondayMidweekAndSundayCutoffs() {
        val monday = qualityDoseHistoryHorizon(LocalDate.of(2026, 9, 21))
        val midweek = qualityDoseHistoryHorizon(LocalDate.of(2026, 9, 23))
        val sunday = qualityDoseHistoryHorizon(LocalDate.of(2026, 9, 27))

        assertEquals(LocalDate.of(2026, 7, 27), monday.oldestCompletedWeekStart)
        assertEquals(LocalDate.of(2026, 7, 27), midweek.oldestCompletedWeekStart)
        assertEquals(LocalDate.of(2026, 8, 3), sunday.oldestCompletedWeekStart)
        assertEquals(LocalDate.of(2026, 7, 27), monday.ledgerStart)
        assertEquals(LocalDate.of(2026, 7, 27), midweek.ledgerStart)
        assertEquals(LocalDate.of(2026, 8, 3), sunday.ledgerStart)
        assertEquals(8, monday.completedWeekEnds.size)
    }

    @Test
    fun oneExtendedLedgerServesB1ContextAndCompletedWeekBaseline() {
        val cutoff = LocalDate.of(2026, 9, 23)
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val old = observation("strength", 1, horizon.oldestCompletedWeekStart, "old", 5)
        val recent = observation("strength", 2, cutoff.minusDays(2), "recent", 5)
        val ledger = ledger(cutoff, listOf(old, recent), profile("strength", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)), horizon.ledgerStart)
        val snapshot = snapshot(cutoff, ledger)
        val shadow = LedgerBackedQualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), CanonicalExercisePhysicalQualityCatalog.EMPTY))

        assertEquals(2, ledger.setObservations.size)
        assertEquals(1, ledger.query(window = StimulusExposureWindow.CONTEXT_56D).count { it.source.stableKey == "strength" })
        assertEquals(1, shadow.weeklyEvidence.getValue(TrainableQuality.STRENGTH).count { it.directUnits > 0 })
        assertTrue(shadow.reasonCodes.contains("LEDGER_HORIZON_EXTENDED_FOR_COMPLETED_ISO_WEEK_BASELINE"))
    }

    @Test
    fun directWinsSupportiveAndSessionsUseDateAndStableSessionKey() {
        val cutoff = LocalDate.of(2026, 9, 23)
        val both = profile("both", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY), relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val supportive = profile("supportive", relation("supportive", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val day = cutoff.minusDays(2)
        val observations = listOf(
            observation("both", 1, day, "session-a", 5),
            observation("supportive", 2, day, "session-b", 5)
        )
        val ledger = StimulusExposureLedger(
            mapOf("both" to both, "supportive" to supportive), observations, emptyList(), cutoff, cutoff.minusDays(55)
        )
        val snapshot = snapshot(cutoff, ledger)
        val shadow = analyze(snapshot)
        val week = shadow.weeklyEvidence.getValue(TrainableQuality.STRENGTH).first { it.directUnits + it.supportiveUnits > 0 }

        assertEquals(1, week.directUnits)
        assertEquals(1, week.supportiveUnits)
        assertEquals(1, week.directSessions)
        assertEquals(1, week.supportiveSessions)
        assertEquals(1, week.directTrainingDays)
        assertEquals(1, week.supportiveTrainingDays)
        assertTrue(week.directPrecedenceResolutions > 0)
    }

    @Test
    fun incompatiblePrescriptionIsExcludedAndDoesNotFallThroughToSupportive() {
        val cutoff = LocalDate.of(2026, 9, 23)
        val profile = profile("strength", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY), relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val ledger = ledger(cutoff, listOf(observation("strength", 1, cutoff.minusDays(2), "session", 12)), profile, cutoff.minusDays(55))
        val shadow = analyze(snapshot(cutoff, ledger))
        val week = shadow.weeklyEvidence.getValue(TrainableQuality.STRENGTH).first { it.hasSourceObservations }

        assertEquals(0, week.directUnits)
        assertEquals(0, week.supportiveUnits)
        assertEquals(1, week.excludedDirectByPrescriptionUnits)
        assertEquals(0, week.excludedSupportiveByPrescriptionUnits)
        assertTrue(shadow.comparisons.getValue(TrainableQuality.STRENGTH).reasonCodes.contains("PRESCRIPTION_INCOMPATIBLE_SOURCE_OBSERVATIONS_EXCLUDED"))
    }

    @Test
    fun baselineMedianAndFrequencyKeepLegacyQuantileAndFallbackRules() {
        val cutoff = LocalDate.of(2026, 9, 23)
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val counts = listOf(4, 6, 8, 10)
        val observations = counts.flatMapIndexed { index, count ->
            val date = horizon.newestCompletedWeekEnd.minusDays(index * 7L)
            (1..count).map { set -> observation("strength", index * 100L + set, date, "s-$index", 5) }
        }
        val profile = profile("strength", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val shadow = analyze(snapshot(cutoff, ledger(cutoff, observations, profile, horizon.ledgerStart)))
        val band = shadow.bands.getValue(TrainableQuality.STRENGTH)

        assertEquals(4, band.directExposureWeekCount)
        assertEquals(8.0, band.directUnitsMedian!!, 0.0)
        assertEquals(0.5, band.directExposureWeekFrequency!!, 0.0)
        assertEquals(SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, band.source)
    }

    @Test
    fun unavailableLedgerIsDistinctFromAvailableZeroBaseline() {
        val cutoff = LocalDate.of(2026, 9, 23)
        val unavailableSnapshot = snapshot(cutoff, StimulusExposureLedger.EMPTY)
        val unavailable = analyze(unavailableSnapshot)
        assertFalse(unavailable.available)
        assertEquals(SuccessfulDoseSource.NO_PERSONAL_BASELINE, unavailable.bands.getValue(TrainableQuality.STRENGTH).source)
        assertTrue(unavailable.reasonCodes.contains("LEDGER_UNAVAILABLE_OR_CUTOFF_MISMATCH"))

        val empty = analyze(snapshot(cutoff, ledger(cutoff, emptyList(), profile("strength", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)), cutoff.minusDays(55))))
        assertTrue(empty.available)
        assertEquals(SuccessfulDoseSource.NO_PERSONAL_BASELINE, empty.bands.getValue(TrainableQuality.STRENGTH).source)
        assertTrue(empty.comparisons.getValue(TrainableQuality.STRENGTH).reasonCodes.contains("ZERO_OR_UNAVAILABLE_BASELINE_RETAINED"))
    }

    private fun analyze(snapshot: PlanningHistorySnapshot): LedgerBackedQualityDoseHistory {
        val legacy = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), CanonicalExercisePhysicalQualityCatalog.EMPTY)
        return LedgerBackedQualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), legacy)
    }

    private fun snapshot(cutoff: LocalDate, ledger: StimulusExposureLedger) = PlanningHistorySnapshot(
        cutoff = cutoff,
        allConfirmedSets = emptyList(),
        exercises = emptyMap(),
        metadata = emptyMap(),
        badmintonObjectives = emptyMap(),
        profilePrimaryGoal = "MIXED",
        strengthTrainingYears = 0.0,
        badmintonTrainingYears = 0.0,
        preferences = PersonalizedPlanningPreferences(),
        stimulusExposureLedger = ledger
    )

    private fun ledger(cutoff: LocalDate, observations: List<StimulusSetObservation>, profile: CanonicalStimulusFacetProfile, historyStart: LocalDate) =
        StimulusExposureLedger(mapOf(profile.stableKey to profile), observations, emptyList(), cutoff, historyStart)

    private fun profile(key: String, vararg relations: ExercisePhysicalQualityRelation) =
        CanonicalStimulusFacetProfile(key, physicalQualities = relations.toList(), integrity = StimulusFacetIntegrity.CONSISTENT)

    private fun relation(id: String, quality: TrainableQuality, level: StimulusCapabilityLevel) = ExercisePhysicalQualityRelation(
        relationId = id,
        exerciseStableKey = id.substringBefore('-').let { if (it == "direct" || it == "support") "both" else it },
        qualityId = quality,
        relationLevel = level,
        regionQualifier = PhysicalQualityRegion.LOWER,
        modeQualifier = PhysicalQualityMode.GENERAL,
        prescriptionDependent = true,
        provenance = "TEST",
        evidenceRelationKeys = setOf("TEST"),
        reviewStatus = "PASS",
        notes = "TEST"
    )

    private fun observation(key: String, id: Long, date: LocalDate, session: String, reps: Int) = StimulusSetObservation(
        source = StimulusSourceRef(id, "backup-$id", id, id.toInt(), session, date, key),
        activityKind = PlannedActivityKind.RESISTANCE,
        reps = reps,
        weightKg = 50.0,
        seconds = 0,
        rpe = 8.0,
        realizedPrescriptionClass = when {
            reps in 1..6 -> RealizedStimulusClass.STRENGTH_LIKE
            reps in 7..15 -> RealizedStimulusClass.HYPERTROPHY_LIKE
            else -> RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
        },
        facetProfileKey = key
    )

    private fun emptyState() = AthletePlanningState(
        ObservedTrainingBehavior.UNKNOWN, StrengthExposure.PRESENT, StrengthIntent.STRENGTH_PRIORITY,
        BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.UNRESOLVED, "MIXED", 56, 3.0, 0.0,
        0.0, 1.0, emptyList(), StrengthProgrammingStyle.UNRESOLVED, PlanningConfidence.LOW, 0,
        "NONE", PlanningConfidence.MODERATE
    )
}
