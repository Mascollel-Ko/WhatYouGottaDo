package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TissuePriorProductionParityTest {
    private val catalog by lazy { repository().catalog }
    private val zoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun offlineFixturesUseExactProductionExposureForStrengthBodyweightBadmintonCodAndDuration() {
        val stableKeys = listOf(
            stableKeyForBasis("WEIGHTED_REPETITION"),
            "ex_28902b13",
            "ex_ae9ecdbc",
            "ex_33841b88",
            "ex_a44ae2ca"
        )
        val records = stableKeys.mapIndexed(::record)
        val events = TissueRcvEventLedgerBuilder(catalog, zoneId).build(records).events

        assertFalse(events.isEmpty())
        stableKeys.forEach { stableKey ->
            assertTrue("$stableKey must produce a production exposure event.", events.any {
                it.exerciseStableKey == stableKey
            })
        }
        events.forEach { event ->
            assertEquals(
                tissueRcvInitialExposure(
                    magnitude = event.magnitudeM,
                    normalizedDose = event.normalizedDose,
                    effortValue = requireNotNull(event.selectedEffort.value),
                    resolvedContextModifier = event.contextModifier
                ),
                event.initialExposure,
                1e-12
            )
        }
    }

    @Test
    fun productionRecoveryAuthorityHandlesRepeatedEventsFullRecoveryAndMultipleTimestamps() {
        val base = TissueRcvEventLedgerBuilder(catalog, zoneId)
            .build(listOf(record(0, stableKeyForBasis("WEIGHTED_REPETITION"))))
            .events.first()
        val curves = TissueRecoveryCurveRepository(catalog.curves)
        val calculator = TissueResidualCalculator(curves, zoneId)
        val performedAt = requireNotNull(base.performedTime.earliestEpochMillis)
        val atStart = requireNotNull(calculator.calculate(base, performedAt))
        val afterSixHours = requireNotNull(calculator.calculate(base, performedAt + 6 * HOUR))
        val afterFourteenDays = requireNotNull(calculator.calculate(base, performedAt + 14 * 24 * HOUR))
        val repeated = (0 until 3).map { day ->
            val time = performedAt + day * 24 * HOUR
            base.copy(
                eventId = "${base.eventId}|$day",
                performedTime = TissueEventTimeRange(time, time, TissueTimestampPrecision.EXACT)
            )
        }
        val repeatedAtThird = repeated.sumOf {
            calculator.calculate(it, performedAt + 2 * 24 * HOUR)?.currentResidualRange?.upper ?: 0.0
        }

        assertTrue(atStart.currentResidualRange.upper >= afterSixHours.currentResidualRange.upper)
        assertEquals(0.0, afterFourteenDays.currentResidualRange.upper, 1e-12)
        assertTrue(repeatedAtThird >= requireNotNull(calculator.calculate(repeated.last(), performedAt + 2 * 24 * HOUR))
            .currentResidualRange.upper)
    }

    @Test
    fun productionNormalizationMakesCanonicalBodyweightFitNeutralForConstantProfileWeight() {
        val low = TissueRcvEventLedgerBuilder(catalog, zoneId)
            .build(listOf(record(0, "ex_28902b13", bodyWeightKg = 50.0))).events
        val high = TissueRcvEventLedgerBuilder(catalog, zoneId)
            .build(listOf(record(0, "ex_28902b13", bodyWeightKg = 105.0))).events

        assertEquals(low.map(TissueExposureEvent::eventId), high.map(TissueExposureEvent::eventId))
        assertEquals(low.map(TissueExposureEvent::initialExposure), high.map(TissueExposureEvent::initialExposure))
    }

    private fun stableKeyForBasis(basis: String): String =
        catalog.authorityRows.first { it.doseBasis == basis }.exerciseStableKey

    private fun record(index: Int, stableKey: String, bodyWeightKg: Double = 75.0): TissueWorkoutRecord {
        val exercise = Exercise(
            id = index + 1L,
            name = catalog.exerciseNamesByStableKey.getValue(stableKey),
            category = "OFFLINE_PRIOR_PARITY",
            stableKey = stableKey
        )
        val time = LocalDate.of(2026, 1, 5).atTime(18, 0).atZone(zoneId).toInstant().toEpochMilli()
        return TissueWorkoutRecord(
            entry = WorkoutEntry(
                id = index + 1L,
                date = "2026-01-05",
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category,
                rpe = 7.0,
                performedAt = time
            ),
            sets = listOf(
                WorkoutSet(
                    id = index + 1L,
                    entryId = index + 1L,
                    setIndex = 0,
                    reps = 10,
                    weightKg = 40.0,
                    seconds = 60,
                    confirmed = true,
                    rpe = 7.0
                )
            ),
            exercise = exercise,
            bodyWeightKg = bodyWeightKg
        )
    }

    private fun repository(): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith { name ->
                sequenceOf(
                    File("src/main/assets/metadata/tissue_load_v1/$name"),
                    File("app/src/main/assets/metadata/tissue_load_v1/$name")
                ).first(File::isFile).readText(Charsets.UTF_8)
            }
        )

    private companion object {
        const val HOUR = 3_600_000L
    }
}
