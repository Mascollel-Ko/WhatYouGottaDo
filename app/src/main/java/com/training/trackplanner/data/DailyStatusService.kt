package com.training.trackplanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

internal class DailyStatusService(
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao
) {
    fun observeCheckInForDate(date: String): Flow<DailyCheckIn?> =
        combine(
            dailyCheckInDao.observeForDate(date),
            dailyMetricDao.observeMetric(date)
        ) { checkIn, metric ->
            checkIn.withCanonicalSleep(date, metric)
        }

    fun observeRecentCheckIns(startDate: String, endDate: String): Flow<List<DailyCheckIn>> =
        combine(
            dailyCheckInDao.observeBetween(startDate, endDate),
            dailyMetricDao.observeBetween(startDate, endDate)
        ) { checkIns, metrics ->
            checkIns.withCanonicalSleep(metrics)
        }

    fun metricForDate(date: String): Flow<DailyMetric?> =
        dailyMetricDao.observeMetric(date)

    suspend fun checkInForDate(date: String): DailyCheckIn? = withContext(Dispatchers.IO) {
        dailyCheckInDao.getForDate(date).withCanonicalSleep(date, dailyMetricDao.metric(date))
    }

    suspend fun recentCheckIns(startDate: String, endDate: String): List<DailyCheckIn> =
        withContext(Dispatchers.IO) {
            dailyCheckInDao.between(startDate, endDate).withCanonicalSleep(
                dailyMetricDao.metricsUntil(endDate).filter { metric -> metric.date >= startDate }
            )
        }

    suspend fun upsertDailyCheckIn(checkIn: DailyCheckIn) = withContext(Dispatchers.IO) {
        val existing = dailyCheckInDao.getForDate(checkIn.date)
        val existingMetric = dailyMetricDao.metric(checkIn.date)
        if (checkIn.sleepHours != null || existingMetric != null) {
            dailyMetricDao.upsert(
                DailyMetric(
                    date = checkIn.date,
                    sleepHours = checkIn.sleepHours ?: existingMetric?.sleepHours,
                    bodyWeightKg = existingMetric?.bodyWeightKg,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        val canonicalSleep = dailyMetricDao.metric(checkIn.date)?.sleepHours ?: checkIn.sleepHours
        dailyCheckInDao.upsert(
            checkIn.copy(sleepHours = canonicalSleep).validated().copy(
                createdAt = existing?.createdAt ?: checkIn.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDailyCheckIn(date: String) = withContext(Dispatchers.IO) {
        dailyCheckInDao.deleteForDate(date)
    }

    suspend fun saveDailyMetric(
        date: String,
        sleepHours: Double?,
        bodyWeightKg: Double?
    ) = withContext(Dispatchers.IO) {
        dailyMetricDao.upsert(
            DailyMetric(
                date = date,
                sleepHours = sleepHours,
                bodyWeightKg = bodyWeightKg,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    internal fun canonicalizeCheckIns(checkIns: List<DailyCheckIn>, metrics: List<DailyMetric>): List<DailyCheckIn> =
        checkIns.withCanonicalSleep(metrics)

    private fun DailyCheckIn?.withCanonicalSleep(date: String, metric: DailyMetric?): DailyCheckIn? {
        val canonicalSleep = metric?.sleepHours ?: this?.sleepHours
        return when {
            this != null -> copy(sleepHours = canonicalSleep)
            canonicalSleep != null -> DailyCheckIn(date = date, sleepHours = canonicalSleep)
            else -> null
        }
    }

    private fun List<DailyCheckIn>.withCanonicalSleep(metrics: List<DailyMetric>): List<DailyCheckIn> {
        val metricsByDate = metrics.associateBy { metric -> metric.date }
        val checkInsByDate = associateBy { checkIn -> checkIn.date }
        return (checkInsByDate.keys + metricsByDate.filterValues { metric -> metric.sleepHours != null }.keys)
            .sorted()
            .mapNotNull { date ->
                checkInsByDate[date].withCanonicalSleep(date, metricsByDate[date])
            }
    }
}

