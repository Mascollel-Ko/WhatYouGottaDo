package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataTransferReportStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun persistsDiagnosticsAndRetainsOnlyTheLatestTwentyReports() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val store = DataTransferReportStore(db.appMetaDao())

        repeat(21) { index ->
            store.save(
                DataTransferReport(
                    operationId = "operation-$index",
                    operation = DataTransferOperation.RESTORE,
                    status = DataTransferStatus.FAILURE,
                    startedAt = index.toLong(),
                    completedAt = index.toLong(),
                    currentStage = DataTransferStages.PLANNING,
                    errors = listOf(
                        DataTransferDiagnostic(
                            code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                            messageKo = "정본 키를 찾을 수 없습니다.",
                            stage = DataTransferStages.PLANNING,
                            sourceExerciseStableKey = "legacy-$index",
                            sourceExerciseName = "Legacy $index"
                        )
                    )
                )
            )
        }

        val recent = DataTransferReportStore(db.appMetaDao()).recent()
        val latest = DataTransferReportStore(db.appMetaDao()).latest()

        assertEquals(DataTransferReportStore.RETENTION_COUNT, recent.size)
        assertEquals("operation-20", latest?.operationId)
        assertTrue(latest?.detailText().orEmpty().contains("legacy-20"))
        assertTrue(recent.none { it.operationId == "operation-0" })
    }

    @Test
    fun blockingPreflightLeavesTheDestinationUntouched() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO exercise_identity_migration_issues (
                issueCode, sourceExerciseId, sourceStableKey, canonicalStableKey,
                entityType, entityRowId, message, createdAt
            ) VALUES ('DANGLING_EXERCISE_ID', 99, '', '', 'WorkoutEntry', 7, '검토 필요', 1)
            """.trimIndent()
        )
        val destination = File.createTempFile("blocked-backup", ".csv")
            .apply { writeText("sentinel", Charsets.UTF_8) }

        val failure = runCatching {
            TrainingRepository(db, context).exportRecordsBackup(Uri.fromFile(destination))
        }.exceptionOrNull() as? DataTransferFailure

        assertNotNull(failure)
        assertEquals("sentinel", destination.readText(Charsets.UTF_8))
        assertTrue(
            failure?.report?.errors.orEmpty().any {
                it.resolutionMethod == "ROOM_MIGRATION_REVIEW_REQUIRED"
            }
        )
    }
}
