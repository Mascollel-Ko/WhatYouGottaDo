package com.training.trackplanner.data

import kotlinx.coroutines.flow.Flow

internal class SmashSpeedService(
    private val smashSpeedDao: SmashSpeedDao
) {
    fun observeForDate(date: String): Flow<List<SmashSpeedRecord>> =
        smashSpeedDao.observeForDate(date)

    suspend fun add(date: String, speedKmh: Double, note: String? = null) {
        val attemptIndex = smashSpeedDao.forDate(date).size + 1
        smashSpeedDao.upsert(
            SmashSpeedRecord(
                date = date,
                speedKmh = speedKmh,
                attemptIndex = attemptIndex,
                note = note
            ).validated()
        )
    }

    suspend fun delete(recordId: Long) {
        smashSpeedDao.deleteById(recordId)
    }
}
