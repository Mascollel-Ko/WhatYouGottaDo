package com.training.trackplanner.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class CalendarRecordService(
    private val db: TrainingDatabase,
    private val workoutDao: WorkoutDao
) {
    suspend fun calendarConflictSummary(dates: List<String>): CalendarConflictSummary =
        if (dates.isEmpty()) {
            CalendarConflictSummary()
        } else {
            CalendarConflictSummary(
                affectedDateCount = dates.size,
                existingDateCount = workoutDao.countDatesWithEntries(dates),
                existingEntryCount = workoutDao.countEntriesOnDates(dates),
                existingSetCount = workoutDao.countSetsOnDates(dates),
                existingConfirmedSetCount = workoutDao.countConfirmedSetsOnDates(dates)
            )
        }

    suspend fun deleteDate(date: String) {
        db.withTransaction {
            workoutDao.deleteSetsOnDates(listOf(date))
            workoutDao.deleteEntriesOnDates(listOf(date))
        }
    }

    suspend fun deleteDateRange(
        startDate: String,
        endDate: String,
        includeConfirmed: Boolean
    ) {
        db.withTransaction {
            val dates = dateRange(startDate, endDate)
            if (dates.isEmpty()) return@withTransaction
            if (includeConfirmed) {
                workoutDao.deleteSetsOnDates(dates)
                workoutDao.deleteEntriesOnDates(dates)
            } else {
                val entries = dates.flatMap { date -> workoutDao.entriesWithSets(date) }
                entries.forEach { entryWithSets ->
                    entryWithSets.sets
                        .filter { set -> !set.confirmed }
                        .forEach { set -> workoutDao.deleteSet(set) }

                    val remainingSets = workoutDao.setsForEntry(entryWithSets.entry.id)
                        .sortedBy { set -> set.setIndex }
                    if (remainingSets.isEmpty()) {
                        workoutDao.deleteEntryById(entryWithSets.entry.id)
                    } else {
                        remainingSets.forEachIndexed { index, set ->
                            val nextIndex = index + 1
                            if (set.setIndex != nextIndex) {
                                workoutDao.updateSetIndex(set.id, nextIndex)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun copyDate(
        sourceDate: String,
        targetDate: String,
        keepConfirmed: Boolean,
        conflictMode: CalendarConflictMode
    ) {
        db.withTransaction {
            val sourceEntries = workoutDao.entriesWithSets(sourceDate)
            if (sourceEntries.isEmpty()) return@withTransaction
            if (conflictMode == CalendarConflictMode.Overwrite) {
                workoutDao.deleteSetsOnDates(listOf(targetDate))
                workoutDao.deleteEntriesOnDates(listOf(targetDate))
            }
            copyEntriesToDate(
                sourceEntries = sourceEntries,
                targetDate = targetDate,
                keepConfirmed = keepConfirmed,
                baseCreatedAt = nextCreatedAt()
            )
        }
    }

    suspend fun moveDate(
        sourceDate: String,
        targetDate: String,
        conflictMode: CalendarConflictMode
    ) {
        if (sourceDate == targetDate) return
        db.withTransaction {
            val sourceEntries = workoutDao.entriesWithSets(sourceDate)
            if (sourceEntries.isEmpty()) return@withTransaction
            if (conflictMode == CalendarConflictMode.Overwrite) {
                workoutDao.deleteSetsOnDates(listOf(targetDate))
                workoutDao.deleteEntriesOnDates(listOf(targetDate))
            }
            copyEntriesToDate(
                sourceEntries = sourceEntries,
                targetDate = targetDate,
                keepConfirmed = true,
                baseCreatedAt = nextCreatedAt()
            )
            workoutDao.deleteSetsOnDates(listOf(sourceDate))
            workoutDao.deleteEntriesOnDates(listOf(sourceDate))
        }
    }

    suspend fun copyDateRangeAsPlan(
        sourceStart: String,
        sourceEnd: String,
        targetStart: String,
        conflictMode: CalendarConflictMode,
        keepConfirmed: Boolean = false
    ) {
        db.withTransaction {
            val sourceDates = dateRange(sourceStart, sourceEnd)
            val sourceEntriesByDate = sourceDates.map { sourceDate ->
                sourceDate to workoutDao.entriesWithSets(sourceDate)
            }
            val targetStartDate = LocalDate.parse(targetStart, DateTimeFormatter.ISO_LOCAL_DATE)
            val targetDates = sourceDates.mapIndexed { index, _ ->
                targetStartDate.plusDays(index.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            if (conflictMode == CalendarConflictMode.Overwrite && targetDates.isNotEmpty()) {
                workoutDao.deleteSetsOnDates(targetDates)
                workoutDao.deleteEntriesOnDates(targetDates)
            }
            var createdAt = nextCreatedAt()
            sourceEntriesByDate.forEachIndexed { index, (_, entries) ->
                if (entries.isNotEmpty()) {
                    copyEntriesToDate(
                        sourceEntries = entries,
                        targetDate = targetDates[index],
                        keepConfirmed = keepConfirmed,
                        baseCreatedAt = createdAt
                    )
                    createdAt += entries.size
                }
            }
        }
    }

    private suspend fun copyEntriesToDate(
        sourceEntries: List<WorkoutEntryWithSets>,
        targetDate: String,
        keepConfirmed: Boolean,
        baseCreatedAt: Long
    ) {
        sourceEntries.forEachIndexed { entryIndex, entryWithSets ->
            val confirmedCount = entryWithSets.sets.count { it.confirmed }
            val copiedEntryId = workoutDao.insertEntry(
                entryWithSets.entry.copy(
                    id = 0,
                    date = targetDate,
                    createdAt = baseCreatedAt + entryIndex,
                    completedAt = if (keepConfirmed && confirmedCount > 0) System.currentTimeMillis() else null,
                    displayOrder = entryIndex + 1,
                    firstConfirmedAt = if (keepConfirmed && confirmedCount > 0) {
                        System.currentTimeMillis()
                    } else {
                        null
                    },
                    performedAt = null
                )
            )
            entryWithSets.sets.sortedBy { it.setIndex }.forEach { sourceSet ->
                workoutDao.insertSet(
                    sourceSet.copy(
                        id = 0,
                        entryId = copiedEntryId,
                        confirmed = keepConfirmed && sourceSet.confirmed
                    )
                )
            }
        }
    }

    private fun dateRange(startDate: String, endDate: String): List<String> {
        val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val end = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val first = minOf(start, end)
        val last = maxOf(start, end)
        val days = last.toEpochDay() - first.toEpochDay()
        return (0L..days).map { offset ->
            first.plusDays(offset).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    private fun nextCreatedAt(): Long = System.currentTimeMillis()
}
