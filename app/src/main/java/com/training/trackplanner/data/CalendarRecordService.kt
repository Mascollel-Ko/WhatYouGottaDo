package com.training.trackplanner.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.DateTimeException
import java.time.format.DateTimeFormatter

data class PlanPushResult(
    val shiftedEntryCount: Int = 0,
    val shiftedSetCount: Int = 0
) {
    val shifted: Boolean get() = shiftedSetCount > 0
}

internal class CalendarRecordService(
    private val db: TrainingDatabase,
    private val workoutDao: WorkoutDao,
    private val strengthPosteriorCoordinator: StrengthPosteriorUpdateCoordinator? = null,
    private val workoutSourceIdentityProvider: WorkoutSourceIdentityProvider? = null,
    private val beforePlanShiftInsert: suspend (Int) -> Unit = {}
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
        mutateDates(listOf(date)) {
            workoutDao.deleteSetsOnDates(listOf(date))
            workoutDao.deleteEntriesOnDates(listOf(date))
        }
    }

    suspend fun deleteDateRange(
        startDate: String,
        endDate: String,
        includeConfirmed: Boolean
    ) {
        val dates = dateRange(startDate, endDate)
        mutateDates(dates) {
            if (dates.isEmpty()) return@mutateDates
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
        mutateDates(listOf(targetDate)) {
            val sourceEntries = workoutDao.entriesWithSets(sourceDate)
            if (sourceEntries.isEmpty()) return@mutateDates
            if (conflictMode == CalendarConflictMode.Overwrite) {
                workoutDao.deleteSetsOnDates(listOf(targetDate))
                workoutDao.deleteEntriesOnDates(listOf(targetDate))
            }
            copyEntriesToDate(
                sourceEntries = sourceEntries,
                targetDate = targetDate,
                keepConfirmed = keepConfirmed,
                baseCreatedAt = nextCreatedAt(),
                preserveSourceIdentity = false
            )
        }
    }

    suspend fun moveDate(
        sourceDate: String,
        targetDate: String,
        conflictMode: CalendarConflictMode
    ) {
        if (sourceDate == targetDate) return
        mutateDates(listOf(sourceDate, targetDate)) {
            val sourceEntries = workoutDao.entriesWithSets(sourceDate)
            if (sourceEntries.isEmpty()) return@mutateDates
            if (conflictMode == CalendarConflictMode.Overwrite) {
                workoutDao.deleteSetsOnDates(listOf(targetDate))
                workoutDao.deleteEntriesOnDates(listOf(targetDate))
            }
            // A move retains source identity. Remove the source row first so the
            // unique backupSourceId can be reused by its replacement in this transaction.
            workoutDao.deleteSetsOnDates(listOf(sourceDate))
            workoutDao.deleteEntriesOnDates(listOf(sourceDate))
            copyEntriesToDate(
                sourceEntries = sourceEntries,
                targetDate = targetDate,
                keepConfirmed = true,
                baseCreatedAt = nextCreatedAt(),
                preserveSourceIdentity = true
            )
        }
    }

    suspend fun pushFuturePlan(startDate: String, dayCount: Int): PlanPushResult {
        require(dayCount in 1..MAX_PLAN_PUSH_DAYS) { "dayCount must be between 1 and $MAX_PLAN_PUSH_DAYS" }
        val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        try {
            start.plusDays(dayCount.toLong())
        } catch (error: DateTimeException) {
            throw IllegalArgumentException("dayCount overflows LocalDate", error)
        }
        val snapshot = workoutDao.allEntriesWithSets()
            .filter { record -> record.entry.date >= startDate && record.sets.any { !it.confirmed } }
        if (snapshot.isEmpty()) return PlanPushResult()

        val shifted = snapshot.map { record ->
            val targetDate = try {
                LocalDate.parse(record.entry.date, DateTimeFormatter.ISO_LOCAL_DATE)
                    .plusDays(dayCount.toLong())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (error: DateTimeException) {
                throw IllegalArgumentException("dayCount overflows LocalDate", error)
            }
            ShiftedPlan(record, targetDate, record.sets.filterNot(WorkoutSet::confirmed))
        }
        val affectedDates = shifted.flatMap { listOf(it.source.entry.date, it.targetDate) }.distinct()

        return mutateDates(affectedDates) {
            shifted.forEach { item ->
                item.plannedSets.forEach { workoutDao.deleteSet(it) }
                val confirmed = item.source.sets.filter(WorkoutSet::confirmed).sortedBy(WorkoutSet::setIndex)
                if (confirmed.isEmpty()) {
                    workoutDao.deleteEntryById(item.source.entry.id)
                } else {
                    confirmed.forEachIndexed { index, set ->
                        if (set.setIndex != index + 1) workoutDao.updateSetIndex(set.id, index + 1)
                    }
                }
            }

            var createdAt = nextCreatedAt()
            var insertIndex = 0
            shifted.groupBy(ShiftedPlan::targetDate).toSortedMap().forEach { (targetDate, items) ->
                var displayOrder = workoutDao.entriesWithSets(targetDate)
                    .maxOfOrNull { it.entry.displayOrder } ?: 0
                items.sortedWith(compareBy({ it.source.entry.date }, { it.source.entry.createdAt }, { it.source.entry.id }))
                    .forEach { item ->
                        beforePlanShiftInsert(insertIndex++)
                        val sourceHadConfirmed = item.source.sets.any(WorkoutSet::confirmed)
                        val entryId = workoutDao.insertEntry(
                            item.source.entry.copy(
                                id = 0,
                                date = targetDate,
                                createdAt = createdAt++,
                                completedAt = null,
                                firstConfirmedAt = null,
                                performedAt = null,
                                displayOrder = ++displayOrder,
                                backupSourceId = if (sourceHadConfirmed) {
                                    workoutSourceIdentityProvider?.newWorkoutSourceId()
                                } else {
                                    item.source.entry.backupSourceId
                                }
                            )
                        )
                        item.plannedSets.sortedBy(WorkoutSet::setIndex).forEachIndexed { index, set ->
                            workoutDao.insertSet(
                                set.copy(id = 0, entryId = entryId, setIndex = index + 1, confirmed = false)
                            )
                        }
                    }
            }
            PlanPushResult(
                shiftedEntryCount = shifted.size,
                shiftedSetCount = shifted.sumOf { it.plannedSets.size }
            )
        }
    }

    suspend fun copyDateRangeAsPlan(
        sourceStart: String,
        sourceEnd: String,
        targetStart: String,
        conflictMode: CalendarConflictMode,
        keepConfirmed: Boolean = false
    ) {
        val sourceDates = dateRange(sourceStart, sourceEnd)
        val targetStartDate = LocalDate.parse(targetStart, DateTimeFormatter.ISO_LOCAL_DATE)
        val targetDates = sourceDates.mapIndexed { index, _ ->
            targetStartDate.plusDays(index.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        mutateDates(targetDates) {
            val sourceEntriesByDate = sourceDates.map { sourceDate ->
                sourceDate to workoutDao.entriesWithSets(sourceDate)
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
                        baseCreatedAt = createdAt,
                        preserveSourceIdentity = false
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
        baseCreatedAt: Long,
        preserveSourceIdentity: Boolean
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
                    performedAt = null,
                    backupSourceId = if (preserveSourceIdentity) {
                        workoutSourceIdentityProvider?.sourceIdForImport(entryWithSets.entry.backupSourceId)
                            ?: entryWithSets.entry.backupSourceId
                    } else {
                        workoutSourceIdentityProvider?.newWorkoutSourceId()
                    }
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

    private data class ShiftedPlan(
        val source: WorkoutEntryWithSets,
        val targetDate: String,
        val plannedSets: List<WorkoutSet>
    )

    private suspend fun <T> mutateDates(dates: Collection<String>, mutation: suspend () -> T): T =
        strengthPosteriorCoordinator?.mutateDates(dates, mutation = mutation)
            ?: db.withTransaction { mutation() }

    private companion object {
        const val MAX_PLAN_PUSH_DAYS = 36_500
    }
}
