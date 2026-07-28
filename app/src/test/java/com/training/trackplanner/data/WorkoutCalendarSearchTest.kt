package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkoutCalendarSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun partialKoreanAndAsciiCaseInsensitiveNamesMatchAllDates() = runBlocking {
        val db = newDatabase()
        insertExercise(db, "calendar.squat", "프론트 스쿼트")
        insertExercise(db, "calendar.bench", "BENCH Press")
        insertEntry(db, "2026-01-03", "calendar.squat", "프론트 스쿼트", confirmedSets = 1)
        insertEntry(db, "2026-01-04", "calendar.bench", "BENCH Press", confirmedSets = 1)

        assertEquals(
            listOf("2026-01-03"),
            query(db, "2026-01-01", "2026-01-31", "스쿼").first()
        )
        assertEquals(
            listOf("2026-01-04"),
            query(db, "2026-01-01", "2026-01-31", "bench").first()
        )
    }

    @Test
    fun multipleExercisesReturnDistinctConfirmedDates() = runBlocking {
        val db = newDatabase()
        insertExercise(db, "calendar.press.one", "덤벨 프레스")
        insertExercise(db, "calendar.press.two", "케이블 프레스")
        insertEntry(db, "2026-02-02", "calendar.press.one", "덤벨 프레스", confirmedSets = 3)
        insertEntry(db, "2026-02-05", "calendar.press.two", "케이블 프레스", confirmedSets = 1)

        assertEquals(
            listOf("2026-02-02", "2026-02-05"),
            query(db, "2026-02-01", "2026-02-28", "프레스").first()
        )
    }

    @Test
    fun planOnlyAndUnconfirmedEntriesAreExcludedButHistoricalNameMatches() = runBlocking {
        val db = newDatabase()
        insertExercise(db, "calendar.renamed", "현재 운동명")
        insertEntry(db, "2026-03-02", "calendar.renamed", "예전 로우 이름", confirmedSets = 1)
        insertEntry(db, "2026-03-03", "calendar.renamed", "예전 로우 이름", confirmedSets = 0)
        insertEntry(db, "2026-03-04", "calendar.renamed", "예전 로우 이름", confirmedSets = 0)

        assertEquals(
            listOf("2026-03-02"),
            query(db, "2026-03-01", "2026-03-31", "예전 로우").first()
        )
    }

    @Test
    fun percentAndUnderscoreAreLiteralCharacters() = runBlocking {
        val db = newDatabase()
        insertExercise(db, "calendar.literal", "Press_100%")
        insertExercise(db, "calendar.other", "PressX100A")
        insertEntry(db, "2026-04-02", "calendar.literal", "Press_100%", confirmedSets = 1)
        insertEntry(db, "2026-04-03", "calendar.other", "PressX100A", confirmedSets = 1)

        assertEquals(
            listOf("2026-04-02"),
            query(db, "2026-04-01", "2026-04-30", "_100%").first()
        )
    }

    @Test
    fun blankQueryReturnsNoDatesWithoutCallingMatchEverything() = runBlocking {
        val db = newDatabase()
        insertExercise(db, "calendar.blank", "스쿼트")
        insertEntry(db, "2026-05-02", "calendar.blank", "스쿼트", confirmedSets = 1)

        assertTrue(query(db, "2026-05-01", "2026-05-31", "   ").first().isEmpty())
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun query(
        db: TrainingDatabase,
        startDate: String,
        endDate: String,
        query: String
    ) = RepositoryReadQueryService(
        exerciseDao = db.exerciseDao(),
        workoutDao = db.workoutDao(),
        initialUserProfileDao = db.initialUserProfileDao()
    ).confirmedExerciseDates(startDate, endDate, query)

    private suspend fun insertExercise(db: TrainingDatabase, stableKey: String, name: String) {
        db.exerciseDao().insertExercise(
            Exercise(
                stableKey = stableKey,
                name = name,
                category = "Test"
            )
        )
    }

    private suspend fun insertEntry(
        db: TrainingDatabase,
        date: String,
        stableKey: String,
        snapshotName: String,
        confirmedSets: Int
    ) {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseStableKey = stableKey,
                exerciseName = snapshotName,
                category = "Test"
            )
        )
        repeat(maxOf(1, confirmedSets)) { index ->
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = index + 1,
                    reps = 8,
                    weightKg = 20.0,
                    confirmed = index < confirmedSets
                )
            )
        }
    }
}
