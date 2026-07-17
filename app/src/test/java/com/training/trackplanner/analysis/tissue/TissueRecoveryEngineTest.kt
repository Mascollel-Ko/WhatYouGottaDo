package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

class TissueRecoveryEngineTest {
    private val catalog by lazy { repository().catalog }
    private val curves by lazy { TissueRecoveryCurveRepository(catalog.curves) }

    @Test
    fun everyReviewedCurveIsExactAtKnotsAndNeverOvershootsLocalExtrema() {
        catalog.curves.values.forEach { curve ->
            curve.knots.forEach { knot ->
                assertEquals("${curve.id}@${knot.elapsedHours}", knot.value, curves.value(curve.id, knot.elapsedHours), 1e-12)
            }
            curve.knots.zipWithNext().forEach { (left, right) ->
                (0..20).forEach { step ->
                    val x = left.elapsedHours + (right.elapsedHours - left.elapsedHours) * step / 20.0
                    val value = curves.value(curve.id, x)
                    assertTrue(value in minOf(left.value, right.value)..maxOf(left.value, right.value))
                    assertTrue(value in 0.0..1.25)
                }
            }
        }
    }

    @Test
    fun monotoneCurvesRemainMonotoneAndDelayedCurvesPreserveTheirDeclaredPeak() {
        catalog.curves.values.forEach { curve ->
            val sampled = sample(curve)
            if (curve.knots.zipWithNext().all { (a, b) -> a.value >= b.value }) {
                assertTrue(curve.id, sampled.zipWithNext().all { (a, b) -> a + 1e-12 >= b })
            }
            val peak = curve.knots.maxBy(TissueRecoveryKnot::value)
            if (peak != curve.knots.first() && peak != curve.knots.last()) {
                assertEquals(peak.value, curves.value(curve.id, peak.elapsedHours), 1e-12)
                assertTrue(curve.id, sampled.max() <= peak.value + 1e-12)
            }
        }
    }

    @Test
    fun exactAndLegacyDateOnlyTimestampsRemainDistinguishable() {
        val exact = TissuePerformedTimeResolver.resolve(
            WorkoutEntry(
                id = 1,
                date = "2026-07-13",
                exerciseId = 1,
                exerciseName = "Exact",
                category = "Strength",
                performedAt = 1234L
            ),
            ZoneOffset.UTC
        )
        val legacy = TissuePerformedTimeResolver.resolve(
            WorkoutEntry(
                id = 2,
                date = "2026-07-13",
                exerciseId = 1,
                exerciseName = "Legacy",
                category = "Strength"
            ),
            ZoneOffset.UTC
        )

        assertEquals(TissueTimestampPrecision.EXACT, exact.precision)
        assertEquals(1234L, exact.earliestEpochMillis)
        assertEquals(1234L, exact.latestEpochMillis)
        assertEquals(TissueTimestampPrecision.DATE_ONLY_RANGE, legacy.precision)
        assertEquals(86_399_999L, legacy.latestEpochMillis!! - legacy.earliestEpochMillis!!)
    }

    @Test
    fun overlappingEventsKeepIndependentRecoveryClocks() {
        val curve = catalog.curves.values.first { candidate ->
            candidate.knots.zipWithNext().all { (a, b) -> a.value >= b.value } &&
                candidate.knots.first().value > candidate.knots.last().value
        }
        val now = LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val calculator = TissueResidualCalculator(curves, ZoneOffset.UTC)
        val older = event("older", curve.id, now - 24 * 3_600_000L)
        val newer = event("newer", curve.id, now)

        val olderResidual = calculator.calculate(older, now)!!
        val newerResidual = calculator.calculate(newer, now)!!

        assertTrue(newerResidual.currentResidualRange.upper >= olderResidual.currentResidualRange.upper)
        assertEquals(
            curves.value(curve.id, 24.0),
            olderResidual.currentResidualRange.upper,
            1e-9
        )
        assertEquals(curves.value(curve.id, 0.0), newerResidual.currentResidualRange.upper, 1e-9)
    }

    @Test
    fun dateOnlyResidualUsesConservativeRangeWithoutInventingAnObservedTime() {
        val curve = catalog.curves.values.first { candidate ->
            candidate.knots.zipWithNext().all { (a, b) -> a.value >= b.value }
        }
        val eventDate = LocalDate.of(2026, 7, 12)
        val now = LocalDate.of(2026, 7, 13).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val event = event(
            id = "legacy",
            curveId = curve.id,
            time = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        ).copy(
            performedTime = TissueEventTimeRange(
                eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                eventDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1,
                TissueTimestampPrecision.DATE_ONLY_RANGE
            )
        )

        val residual = TissueResidualCalculator(curves, ZoneOffset.UTC).calculate(event, now)!!

        assertTrue(residual.currentResidualRange.lower <= residual.currentResidualRange.upper)
        assertEquals(curves.value(curve.id, 12.0), residual.currentResidualRange.upper, 1e-6)
        assertEquals(curves.value(curve.id, 36.0), residual.currentResidualRange.lower, 1e-6)
    }

    @Test
    fun allReviewedDiProfilesEitherResolveRecordedInputsOrFailClosedForGenericUnresolved() {
        assertEquals(13, catalog.diProfiles.size)
        catalog.diProfiles.values.forEach { profile ->
            val record = recordFor(profile.doseBasis)
            val result = TissueRcvDoseResolver.resolve(record, profile.doseBasis)
            if (profile.doseBasis == "UNRESOLVED") {
                assertNull(result.resolvedDose)
                assertTrue(result.diagnostics.isNotEmpty())
            } else {
                assertNotNull(profile.id, result.resolvedDose)
                assertTrue(profile.id, result.resolvedDose!! > 0.0)
            }
        }
    }

    @Test
    fun ledgerUsesSetRpeBeforeEntryRpeAndPreservesUnsidedUniqueEvents() {
        val stableKey = "ex_201f6426"
        val exercise = Exercise(id = 10, name = "B-스탠스 RDL", category = "근력운동", stableKey = stableKey)
        val record = TissueWorkoutRecord(
            entry = WorkoutEntry(
                id = 42,
                date = "2026-07-13",
                exerciseId = 10,
                exerciseName = exercise.name,
                category = exercise.category,
                rpe = 6.0,
                performedAt = 1000L
            ),
            sets = listOf(
                WorkoutSet(id = 1, entryId = 42, setIndex = 1, reps = 8, weightKg = 40.0, confirmed = true, rpe = 8.0)
            ),
            exercise = exercise
        )

        val result = TissueRcvEventLedgerBuilder(catalog, ZoneOffset.UTC).build(listOf(record))

        assertFalse(result.events.isEmpty())
        assertEquals(result.events.size, result.events.map(TissueExposureEvent::eventId).distinct().size)
        assertTrue(result.events.all { it.selectedEffort.source == TissueEffortSource.SET_RPE })
        assertTrue(result.events.all { TissueEffortSource.ENTRY_RPE in it.selectedEffort.rejectedAvailableSources })
        assertTrue(result.events.all { it.key.loadUnitStableKey.isNotBlank() && it.key.loadDimension.isNotBlank() })
        assertFalse(result.events.any { it.eventId.contains("LEFT") || it.eventId.contains("RIGHT") })
    }

    private fun recordFor(basis: String): TissueWorkoutRecord {
        val stableKey = when (basis) {
            "BODYWEIGHT_REPETITION" -> "push_up"
            "HOLD_TIME" -> "plank"
            else -> "fixture"
        }
        val exercise = Exercise(
            id = 1,
            name = stableKey,
            category = "fixture",
            stableKey = stableKey,
            mode = if (basis == "HOLD_TIME") "시간" else ""
        )
        return TissueWorkoutRecord(
            entry = WorkoutEntry(
                id = 1,
                date = "2026-07-13",
                exerciseId = 1,
                exerciseName = stableKey,
                category = "fixture",
                rpe = 7.0
            ),
            sets = listOf(
                WorkoutSet(
                    id = 1,
                    entryId = 1,
                    setIndex = 1,
                    reps = 10,
                    weightKg = 20.0,
                    seconds = 60,
                    confirmed = true,
                    rpe = 8.0
                )
            ),
            exercise = exercise,
            bodyWeightKg = 80.0
        )
    }

    private fun event(id: String, curveId: String, time: Long) = TissueExposureEvent(
        eventId = id,
        recordId = id.hashCode().toLong(),
        exerciseStableKey = id,
        exerciseName = id,
        key = TissueRcvLoadKey("load", "SMOOTH_CYCLE"),
        jointComplexStableKey = "joint",
        tissueClass = "TENDON",
        initialExposure = 1.0,
        rawDose = 1.0,
        doseReference56d = 1.0,
        normalizedDose = 1.0,
        selectedEffort = TissueEffortSelection(1.0, TissueEffortSource.SET_RPE),
        magnitudeM = 10.0,
        rapidityS = 1.0,
        contextModifier = 1.0,
        mappingRoleWeight = 1.0,
        curveIds = mapOf(TissueRecoveryChannel.FUNCTIONAL_CAPACITY to curveId),
        performedTime = TissueEventTimeRange(time, time, TissueTimestampPrecision.EXACT),
        scoreVersion = "test",
        protocolVersion = "test",
        curveVersion = "test",
        evidenceGrade = "test",
        sourceRefs = emptyList(),
        diagnostics = emptyList()
    )

    private fun sample(curve: TissueRecoveryCurve): List<Double> {
        val start = curve.knots.first().elapsedHours
        val end = curve.knots.last().elapsedHours
        return (0..200).map { step ->
            curves.value(curve.id, start + (end - start) * step / 200.0)
        }
    }

    private fun repository(): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(TissueRcvAssetFiles.required.associateWith(::asset))

    private fun asset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
