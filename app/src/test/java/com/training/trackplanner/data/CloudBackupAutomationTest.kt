package com.training.trackplanner.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudBackupAutomationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @Before
    fun initWorkManager() {
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).build()
            )
        }
        WorkManager.getInstance(context).cancelUniqueWork(CloudBackupScheduler.UNIQUE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(CloudBackupScheduler.PERIODIC_WORK_NAME)
    }

    @After
    fun close() {
        WorkManager.getInstance(context).cancelUniqueWork(CloudBackupScheduler.UNIQUE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(CloudBackupScheduler.PERIODIC_WORK_NAME)
        databases.forEach(TrainingDatabase::close)
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }

    private fun automaticWork(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(CloudBackupScheduler.UNIQUE_WORK_NAME).get()
        .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    @Test
    fun meaningfulMutationSchedulesOneConnectedTenMinuteWorkItem() = runBlocking {
        val db = database()
        val exercise = Exercise("auto-cloud-test", "Automatic cloud test", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        db.cloudBackupStateDao().getOrCreate()
        db.cloudBackupStateDao().bindAccount("user-1")

        TrainingRepository(db, context).addWorkoutEntry("2026-09-17", exercise.stableKey)

        val work = automaticWork()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
        assertTrue(db.cloudBackupStateDao().get()!!.cloudBackupPending)
    }

    @Test
    fun rapidMutationsCoalesceToOneUniqueWorkItem() = runBlocking {
        val db = database()
        val exercise = Exercise("auto-cloud-test-2", "Automatic cloud test", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        db.cloudBackupStateDao().getOrCreate()
        db.cloudBackupStateDao().bindAccount("user-1")
        val repository = TrainingRepository(db, context)

        repository.addWorkoutEntry("2026-09-17", exercise.stableKey)
        repository.addWorkoutEntry("2026-09-17", exercise.stableKey)

        assertEquals(1, automaticWork().size)
    }

    @Test
    fun pendingFalseAndCloudOffDoNotSchedule() = runBlocking {
        val db = database()
        db.cloudBackupStateDao().getOrCreate()
        assertFalse(CloudBackupScheduler.scheduleIfPending(context, db, now = 0L))
        assertTrue(automaticWork().isEmpty())

        db.openHelper.writableDatabase.execSQL(
            "UPDATE cloud_backup_state SET cloudBackupPending = 1 WHERE id = 1"
        )
        assertFalse(CloudBackupScheduler.scheduleIfPending(context, db, now = 0L))
        assertTrue(automaticWork().isEmpty())
    }

    @Test
    fun retryPolicyUsesFifteenMinuteExponentialDelays() {
        assertEquals(15 * 60 * 1000L, CloudBackupRetryPolicy.delayMillis(1))
        assertEquals(30 * 60 * 1000L, CloudBackupRetryPolicy.delayMillis(2))
        assertEquals(60 * 60 * 1000L, CloudBackupRetryPolicy.delayMillis(3))
    }

    @Test
    fun accountClassifierBlocksAmbiguousAutomaticEntry() {
        val action = CloudAccountEntryClassifier.classify(
            CloudAccountEntrySnapshot(
                hasMeaningfulLocalData = true,
                boundUserId = null,
                cloudCurrentExists = false,
                cloudCurrentKnown = false
            ),
            "user-1"
        )
        assertEquals(CloudAccountEntryAction.REQUIRE_GUEST_CLOUD_COMPARISON, action)
    }

    @Test
    fun periodicSafetyCheckIsUniqueAndPendingOnly() = runBlocking {
        CloudBackupScheduler.ensurePeriodic(context)
        CloudBackupScheduler.ensurePeriodic(context)
        val periodic = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CloudBackupScheduler.PERIODIC_WORK_NAME).get()
        assertEquals(1, periodic.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })
        assertNotNull(periodic.last().constraints)
        assertEquals(NetworkType.CONNECTED, periodic.last().constraints.requiredNetworkType)
    }
}
