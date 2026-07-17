package com.training.trackplanner.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class DailyStatusService(
    private val db: TrainingDatabase,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao
) {
    fun observeCheckInForDate(date: String): Flow<DailyCheckIn?> =
        dailyCheckInDao.observeForDate(date)

    fun observeRecentCheckIns(startDate: String, endDate: String): Flow<List<DailyCheckIn>> =
        dailyCheckInDao.observeBetween(startDate, endDate)

    suspend fun checkInForDate(date: String): DailyCheckIn? = withContext(Dispatchers.IO) {
        dailyCheckInDao.getForDate(date)
    }

    suspend fun recentCheckIns(startDate: String, endDate: String): List<DailyCheckIn> =
        withContext(Dispatchers.IO) { dailyCheckInDao.between(startDate, endDate) }

    suspend fun upsertDailyCheckIn(checkIn: DailyCheckIn) = withContext(Dispatchers.IO) {
        db.withTransaction {
            upsertInTransaction(checkIn)
        }
    }

    internal suspend fun upsertInTransaction(
        checkIn: DailyCheckIn,
        preserveUpdatedAt: Boolean = false
    ) {
        val validated = checkIn.validated()
        val existing = dailyCheckInDao.getForDate(checkIn.date)
        val existingMetric = dailyMetricDao.metric(checkIn.date)
        val updatedAt = if (preserveUpdatedAt) validated.updatedAt else System.currentTimeMillis()
        val canonical = validated.copy(
            createdAt = existing?.createdAt ?: validated.createdAt,
            updatedAt = updatedAt
        )
        dailyCheckInDao.upsert(canonical)
        if (canonical.sleepHours != null || canonical.bodyWeightKg != null || existingMetric != null) {
            dailyMetricDao.upsert(
                DailyMetric(
                    date = canonical.date,
                    sleepHours = canonical.sleepHours,
                    bodyWeightKg = canonical.bodyWeightKg,
                    updatedAt = updatedAt
                )
            )
        }
    }

    suspend fun deleteDailyCheckIn(date: String) = withContext(Dispatchers.IO) {
        dailyCheckInDao.deleteForDate(date)
    }

    suspend fun saveDailyMetric(
        date: String,
        sleepHours: Double?,
        bodyWeightKg: Double?
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            saveDailyMetricInTransaction(date, sleepHours, bodyWeightKg)
        }
    }

    internal suspend fun saveDailyMetricInTransaction(
        date: String,
        sleepHours: Double?,
        bodyWeightKg: Double?
    ) {
        val existing = dailyCheckInDao.getForDate(date)
        upsertInTransaction(
            (existing ?: DailyCheckIn(date = date)).copy(
                sleepHours = sleepHours,
                bodyWeightKg = bodyWeightKg
            )
        )
    }
}
