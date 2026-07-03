package com.training.trackplanner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmashSpeedServiceTest {
    @Test
    fun addUsesNextAttemptIndexForDate() = runBlocking {
        val dao = FakeSmashSpeedDao()
        val service = SmashSpeedService(dao)
        service.add("2026-06-29", 210.0)
        service.add("2026-06-29", 220.0, note = "fresh")

        val records = dao.forDate("2026-06-29")

        assertEquals(listOf(1, 2), records.map { it.attemptIndex })
        assertEquals(listOf(210.0, 220.0), records.map { it.speedKmh })
        assertEquals("fresh", records.last().note)
    }

    @Test
    fun deleteRemovesRecordById() = runBlocking {
        val dao = FakeSmashSpeedDao()
        val service = SmashSpeedService(dao)
        service.add("2026-06-29", 210.0)
        val recordId = dao.forDate("2026-06-29").single().id

        service.delete(recordId)

        assertTrue(dao.forDate("2026-06-29").isEmpty())
    }

    private class FakeSmashSpeedDao : SmashSpeedDao {
        private val records = mutableListOf<SmashSpeedRecord>()
        private var nextId = 1L

        override fun observeForDate(date: String): Flow<List<SmashSpeedRecord>> =
            flowOf(recordsFor(date))

        override suspend fun forDate(date: String): List<SmashSpeedRecord> =
            recordsFor(date)

        override suspend fun between(startDate: String, endDate: String): List<SmashSpeedRecord> =
            records.filter { it.date in startDate..endDate }

        override suspend fun all(): List<SmashSpeedRecord> =
            records.sortedWith(
                compareBy<SmashSpeedRecord> { it.date }
                    .thenBy { it.attemptIndex ?: it.id.toInt() }
                    .thenBy { it.id }
            )

        override suspend fun upsert(record: SmashSpeedRecord): Long {
            val id = record.id.takeIf { it > 0 } ?: nextId++
            records.removeAll { it.id == id }
            records += record.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Long) {
            records.removeAll { it.id == id }
        }

        private fun recordsFor(date: String): List<SmashSpeedRecord> =
            records
                .filter { it.date == date }
                .sortedWith(compareBy<SmashSpeedRecord> { it.attemptIndex ?: it.id.toInt() }.thenBy { it.id })
    }
}
