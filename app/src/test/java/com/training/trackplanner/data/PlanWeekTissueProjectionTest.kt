package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import com.training.trackplanner.analysis.tissue.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlanWeekTissueProjectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val cutoff = LocalDate.of(2026, 9, 3)
    private suspend fun withService(block: suspend (ConnectiveTissueAnalysisService, TrainingDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            listOf("barbell_back_squat", "barbell_bench_press", "romanian_deadlift", "ex_eb636bac").forEach {
                db.exerciseDao().insertExercise(Exercise(stableKey = it, name = it, category = "근력운동")) }
            block(ConnectiveTissueAnalysisService(context, db.exerciseDao(), db.workoutDao(), db.dailyMetricDao(),
                db.initialUserProfileDao(), db.dailyCheckInDao(), zone), db)
        } finally { db.close() }
    }
    private fun row(key: String, day: Int, sets: Int) = PostGenerationFixture.row("press", day, sets, id = "$key-$day").copy(
        exerciseStableKey = key, exerciseName = key, setPrescriptions = List(sets) { ProgramSetPrescription(it + 1, 8, 60.0, 0) })

    @Test fun projectionUsesCanonicalStateAndNeverWritesPlannedRecords() = runBlocking {
        withService { service, db ->
            val before = db.workoutDao().entriesWithSetsUntil("2099-01-01")
            val projection = service.planProjection(cutoff)
            val result = projection.evaluate(listOf(row("barbell_back_squat", 1, 3), row("barbell_bench_press", 4, 3)), 8.0)
            assertEquals(listOf("2026-09-07", "2026-09-10"), result.days.map { it.date })
            assertTrue(result.toJson().toString(), result.days.all { it.unresolvedKeys.isEmpty() })
            val direct = service.build(LocalDate.parse(result.days.first().date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
            assertEquals(direct, result.days.first().before)
            assertTrue(result.days.first().after!!.loadUnits.any { it.rawResidual.upper > 0 })
            assertEquals(before, db.workoutDao().entriesWithSetsUntil("2099-01-01"))
            assertFalse(result.toJson().toString().contains("injuryProbability"))
            assertEquals("MISSING_TYPED_PLANNED_RPE", projection.evaluate(emptyList(), Double.NaN).diagnostic)
        }
    }

    @Test fun sameCanonicalDoseCanFailWithCloseSpacingAndPassWithRecovery() = runBlocking {
        withService { service, _ ->
            val projection = service.planProjection(cutoff)
            val comparisons = listOf(3, 6, 12, 24, 48, 96).map { sets ->
                val first = row("barbell_back_squat", 1, sets)
                val second = row("barbell_back_squat", 2, 3)
                projection.evaluate(listOf(first, second), 8.5) to projection.evaluate(listOf(first, second.copy(dayOfWeek = 7)), 8.5)
            }
            assertTrue(comparisons.joinToString { (close, spaced) -> "${close.days.last().blockedUnits} / ${spaced.days.last().blockedUnits}" },
                comparisons.any { (close, spaced) -> !close.feasible && spaced.feasible })
            comparisons.forEach { (close, spaced) ->
                assertEquals(close.days.first().after, spaced.days.first().after)
                assertEquals(close.days.last().after!!.loadUnits.map { it.key }.toSet(), spaced.days.last().after!!.loadUnits.map { it.key }.toSet())
            }
        }
    }

    @Test fun restrictionsFollowExactExposedUnitsAndTissueClassesNotGenericLowerBody() = runBlocking {
        withService { service, _ ->
            val result = service.planProjection(cutoff).evaluate(listOf(row("barbell_back_squat", 1, 24), row("barbell_bench_press", 2, 3)), 8.5)
            val second = result.days.last()
            val actualExposed = TissueRcvAssetRepository.fromAssets(context).catalog.authorityRows.filter {
                it.exerciseStableKey == "barbell_bench_press" }.map { it.loadUnitStableKey }.toSet()
            assertTrue(second.blockedUnits.all { it in actualExposed })
            assertTrue(second.after!!.loadUnits.map { it.tissueClass }.distinct().size >= 3)
            val firstKeys = result.days.first().after!!.loadUnits.filter { it.rawResidual.upper > 0 }.map { it.key.loadUnitStableKey }.toSet()
            assertTrue(firstKeys.any { it !in actualExposed })
        }
    }
    @Test fun fullExposureAuthorityCannotBeReplacedByTopContributorDisplaySummaries() = runBlocking {
        withService { service, _ ->
            val projection = service.planProjection(cutoff)
            val result = projection.evaluate(listOf(row("barbell_bench_press", 1, 24), row("ex_eb636bac", 2, 2)), 8.5)
            val second = result.days.last()
            val exposed = TissueRcvAssetRepository.fromAssets(context).catalog.authorityRows.filter {
                it.exerciseStableKey == "ex_eb636bac" }.map { it.loadUnitStableKey }.toSet()
            val expectedBlocked = second.before!!.loadUnits.filter { it.key.loadUnitStableKey in exposed &&
                it.status in setOf(TissueCanonicalStatus.HIGH, TissueCanonicalStatus.VERY_HIGH) }.map { it.key.loadUnitStableKey }.toSet()
            assertEquals(expectedBlocked, second.blockedUnits)
            assertTrue(second.unresolvedKeys.isEmpty())
            val mixed = projection.evaluate(listOf(row("barbell_bench_press", 1, 24), row("ex_eb636bac", 1, 2)), 8.5).days.single()
            assertTrue(mixed.unresolvedKeys.isEmpty())
            val summaryOmissions = mixed.after!!.loadUnits.filter { it.key.loadUnitStableKey in exposed }.any { unit ->
                unit.contributors.none { it.exerciseStableKey == "ex_eb636bac" } }
            assertTrue("Fixture must exercise a real top-contributor omission", summaryOmissions)
        }
    }
}
