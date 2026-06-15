package com.training.trackplanner.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.room.withTransaction
import com.training.trackplanner.analysis.badminton.BadmintonTransferAnalysisEngine
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.core.AnalysisInputCollector
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.engine.AnalysisEngineV3
import com.training.trackplanner.analysis.readiness.TodayReadinessEngine
import com.training.trackplanner.analysis.readiness.TodayReadinessEngineInput
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.trends.PerformanceTrendEngine
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ProgramApplyMode {
    Append,
    Overwrite
}

enum class CalendarConflictMode {
    Append,
    Overwrite
}

data class ProgramApplyConflictSummary(
    val affectedDateCount: Int = 0,
    val existingEntryCount: Int = 0,
    val existingConfirmedSetCount: Int = 0
) {
    val hasExistingEntries: Boolean
        get() = existingEntryCount > 0
}

class TrainingRepository(
    private val db: TrainingDatabase,
    private val context: Context
) {
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val programDao = db.programDao()
    private val dailyMetricDao = db.dailyMetricDao()
    private val appMetaDao = db.appMetaDao()

    val exercises: Flow<List<Exercise>> = exerciseDao.observeExercises()
    val programs: Flow<List<TrainingProgram>> = programDao.observePrograms()
    val analysisStats: Flow<AnalysisStats> = workoutDao.observeAnalysisStats()

    suspend fun todayReadinessSummary(): TodayReadinessSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = exerciseDao.allExercises(),
                entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
                dailyMetrics = dailyMetricDao.metricsUntil(todayString)
            )
        )
    }

    suspend fun performanceTrendSummary(): PerformanceTrendSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        PerformanceTrendEngine().analyze(
            today = today,
            exercises = exerciseDao.allExercises(),
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        )
    }

    suspend fun badmintonTransferSummary(
        readinessSummary: TodayReadinessSummary? = null
    ): BadmintonTransferSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = exerciseDao.allExercises(),
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            readinessSummary = readinessSummary
        )
    }

    fun entriesForDate(date: String): Flow<List<WorkoutEntryWithSets>> =
        workoutDao.observeEntriesWithSets(date)

    fun entryCount(date: String): Flow<Int> =
        workoutDao.observeEntryCount(date)

    fun plannedSetCount(date: String): Flow<Int> =
        workoutDao.observePlannedSetCount(date)

    fun confirmedSetCount(date: String): Flow<Int> =
        workoutDao.observeConfirmedSetCount(date)

    fun dailySummaries(startDate: String, endDate: String): Flow<List<DailyRecordSummary>> =
        workoutDao.observeDailySummariesBetween(startDate, endDate)

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        programDao.observeItems(programId)

    fun metricForDate(date: String): Flow<DailyMetric?> =
        dailyMetricDao.observeMetric(date)

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val exerciseSeedVersion = appMetaDao.intValue(META_EXERCISE_SEED_VERSION)
        val programSeedVersion = appMetaDao.intValue(META_PROGRAM_SEED_VERSION)

        if (exerciseSeedVersion < EXERCISE_SEED_VERSION || exerciseDao.countExercises() == 0) {
            exerciseDao.insertAll(SeedData.exercises(context))
            appMetaDao.upsert(
                AppMeta(
                    key = META_EXERCISE_SEED_VERSION,
                    value = EXERCISE_SEED_VERSION.toString()
                )
            )
        }
        refreshExerciseAnalysisMetadata()

        if (programSeedVersion < PROGRAM_SEED_VERSION) {
            seedMissingPrograms()
            appMetaDao.upsert(
                AppMeta(
                    key = META_PROGRAM_SEED_VERSION,
                    value = PROGRAM_SEED_VERSION.toString()
                )
            )
        }

        logDebugSummary()
    }

    suspend fun addWorkoutEntry(date: String, exerciseId: Long) = withContext(Dispatchers.IO) {
        val exercise = exerciseDao.findById(exerciseId) ?: return@withContext
        val entryId = workoutDao.insertEntry(
            WorkoutEntry(
                date = date,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = exercise.defaultRestSeconds
            )
        )
        workoutDao.insertSet(defaultSet(entryId, 1, exercise))
    }

    suspend fun updateWorkoutEntry(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        workoutDao.updateEntry(entry)
        refreshEntryCompletion(entry.id)
    }

    suspend fun addSet(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        val currentSets = workoutDao.setsForEntry(entry.id)
        val nextIndex = currentSets.size + 1
        val nextSet = currentSets.lastOrNull()
            ?.copy(
                id = 0,
                setIndex = nextIndex,
                confirmed = false,
                rpe = null,
                restSecondsOverride = null
            )
            ?: defaultSet(entry.id, nextIndex, exerciseDao.findById(entry.exerciseId))
        workoutDao.insertSet(nextSet)
        refreshEntryCompletion(entry.id)
    }

    suspend fun updateSet(set: WorkoutSet) = withContext(Dispatchers.IO) {
        workoutDao.updateSet(set)
        refreshEntryCompletion(set.entryId)
    }

    suspend fun deleteSet(set: WorkoutSet): Boolean = withContext(Dispatchers.IO) {
        if (workoutDao.setCount(set.entryId) <= 1) return@withContext false
        workoutDao.deleteSet(set)
        workoutDao.setsForEntry(set.entryId).forEachIndexed { index, remainingSet ->
            workoutDao.updateSetIndex(remainingSet.id, index + 1)
        }
        refreshEntryCompletion(set.entryId)
        true
    }

    suspend fun createProgram() = withContext(Dispatchers.IO) {
        programDao.insertProgram(
            TrainingProgram(
                name = "새 프로그램",
                durationDays = 28
            )
        )
    }

    suspend fun addExerciseToProgram(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        exerciseId: Long
    ) = withContext(Dispatchers.IO) {
        val exercise = exerciseDao.findById(exerciseId) ?: return@withContext
        val nextOrder = (programDao.itemsForProgramDay(programId, weekNumber, dayOfWeek)
            .maxOfOrNull { it.orderIndex } ?: 0) + 1
        programDao.insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = weekNumber,
                dayOfWeek = dayOfWeek,
                orderIndex = nextOrder,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = exercise.defaultRestSeconds,
                prescription = "",
                setCount = 1,
                reps = 0,
                weightKg = 0.0,
                seconds = if (exercise.mode.contains("시간") || exercise.category in timedCategories) 30 else 0
            )
        )
    }

    suspend fun updateProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programDao.updateProgramItem(item)
    }

    suspend fun deleteProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programDao.deleteProgramItem(item)
        reindexProgramDay(item.programId, item.weekNumber, item.dayOfWeek)
    }

    suspend fun programHasDateConflicts(programId: Long, startDate: String): Boolean =
        withContext(Dispatchers.IO) {
            programApplyConflictSummary(programId, startDate).hasExistingEntries
        }

    suspend fun programApplyConflictSummary(
        programId: Long,
        startDate: String
    ): ProgramApplyConflictSummary = withContext(Dispatchers.IO) {
        val programItems = programDao.itemsForProgram(programId)
        val dates = targetDates(startDate, programItems)
        if (dates.isEmpty()) {
            ProgramApplyConflictSummary()
        } else {
            ProgramApplyConflictSummary(
                affectedDateCount = dates.size,
                existingEntryCount = workoutDao.countEntriesOnDates(dates),
                existingConfirmedSetCount = workoutDao.countConfirmedSetsOnDates(dates)
            )
        }
    }

    suspend fun applyProgramToDates(
        programId: Long,
        startDate: String,
        mode: ProgramApplyMode
    ) = withContext(Dispatchers.IO) {
        val program = programDao.findProgram(programId) ?: return@withContext
        val items = programDao.itemsForProgram(program.id)
        if (items.isEmpty()) return@withContext
        val dates = targetDates(startDate, items)
        if (mode == ProgramApplyMode.Overwrite && dates.isNotEmpty()) {
            workoutDao.deleteSetsOnDates(dates)
            workoutDao.deleteEntriesOnDates(dates)
        }

        val now = System.currentTimeMillis()
        items.forEachIndexed { index, item ->
            val entryId = workoutDao.insertEntry(
                WorkoutEntry(
                    date = dateForProgramItem(startDate, item),
                    exerciseId = item.exerciseId,
                    exerciseName = item.exerciseName,
                    category = item.category,
                    restSeconds = item.restSeconds,
                    notes = noteFromPrescription(item.prescription),
                    createdAt = now + index
                )
            )
            repeat(item.setCount.coerceAtLeast(1)) { setIndex ->
                workoutDao.insertSet(
                    WorkoutSet(
                        entryId = entryId,
                        setIndex = setIndex + 1,
                        reps = item.reps,
                        weightKg = item.weightKg,
                        seconds = item.seconds,
                        confirmed = false,
                        manualWeight = item.weightKg > 0.0
                    )
                )
            }
        }
    }

    suspend fun calendarConflictSummary(dates: List<String>): CalendarConflictSummary =
        withContext(Dispatchers.IO) {
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
        }

    suspend fun deleteDate(date: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            workoutDao.deleteSetsOnDates(listOf(date))
            workoutDao.deleteEntriesOnDates(listOf(date))
        }
    }

    suspend fun copyDate(
        sourceDate: String,
        targetDate: String,
        keepConfirmed: Boolean,
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
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
    ) = withContext(Dispatchers.IO) {
        if (sourceDate == targetDate) return@withContext
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
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
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
                        keepConfirmed = false,
                        baseCreatedAt = createdAt
                    )
                    createdAt += entries.size
                }
            }
        }
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

    private suspend fun seedMissingPrograms() {
        SeedData.programs(context).forEach { seed ->
            val programName = seed.displayName()
            if (programDao.findProgramByName(programName) != null) return@forEach

            val programId = programDao.insertProgram(
                TrainingProgram(
                    name = programName,
                    durationDays = seed.durationDays
                )
            )
            val items = seed.items.map { itemSeed ->
                val exercise = exerciseDao.findByName(itemSeed.exerciseName)
                TrainingProgramItem(
                    programId = programId,
                    weekNumber = itemSeed.weekNumber,
                    dayOfWeek = itemSeed.dayOfWeek,
                    orderIndex = itemSeed.orderIndex,
                    exerciseId = exercise?.id ?: 0,
                    exerciseName = exercise?.name ?: itemSeed.exerciseName,
                    category = exercise?.category ?: itemSeed.category,
                    restSeconds = itemSeed.restSeconds,
                    prescription = itemSeed.prescription,
                    setCount = itemSeed.setCount.coerceAtLeast(1),
                    reps = itemSeed.reps,
                    weightKg = itemSeed.weightKg,
                    seconds = itemSeed.seconds
                )
            }
            programDao.insertProgramItems(items)
        }
    }

    private fun defaultSet(entryId: Long, setIndex: Int, exercise: Exercise?): WorkoutSet {
        val seconds = when (exercise?.category) {
            "유산소운동" -> 20 * 60
            "스포츠" -> 60 * 60
            else -> if (exercise?.mode?.contains("시간") == true) 30 else 0
        }

        return WorkoutSet(
            entryId = entryId,
            setIndex = setIndex,
            reps = 0,
            weightKg = 0.0,
            seconds = seconds,
            confirmed = false,
            manualWeight = false
        )
    }

    private suspend fun refreshEntryCompletion(entryId: Long) {
        val completedAt = if (workoutDao.confirmedCountForEntry(entryId) > 0) {
            System.currentTimeMillis()
        } else {
            null
        }
        workoutDao.updateEntryCompletedAt(entryId, completedAt)
    }

    private suspend fun reindexProgramDay(programId: Long, weekNumber: Int, dayOfWeek: Int) {
        programDao.itemsForProgramDay(programId, weekNumber, dayOfWeek)
            .forEachIndexed { index, remaining ->
                programDao.updateProgramItemOrder(remaining.id, index + 1)
            }
    }

    private fun targetDates(startDate: String, items: List<TrainingProgramItem>): List<String> =
        items.map { dateForProgramItem(startDate, it) }.distinct()

    private fun dateForProgramItem(startDate: String, item: TrainingProgramItem): String {
        val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val daysFromStart = ((item.weekNumber - 1) * 7L) + (item.dayOfWeek - 1L)
        return start.plusDays(daysFromStart).format(DateTimeFormatter.ISO_LOCAL_DATE)
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
                    completedAt = if (keepConfirmed && confirmedCount > 0) System.currentTimeMillis() else null
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

    private fun noteFromPrescription(prescription: String): String {
        val trimmed = prescription.trim()
        if (trimmed.isEmpty()) return ""
        val structureOnly = listOf(
            Regex("""^\d+\s*(세트|sets?|set)?\s*[xX×]\s*\d+\s*(회|reps?)?(\s*@\s*\d+(\.\d+)?\s*kg)?$"""),
            Regex("""^\d+\s*(초|분)\s*[xX×]\s*\d+\s*(세트|sets?)$"""),
            Regex("""^\d+\s*(세트|sets?)\s*[xX×]\s*\d+\s*(초|분)$"""),
            Regex("""^\d+\s*(회|reps?)\s*[xX×]\s*\d+\s*(세트|sets?)$""")
        ).any { it.matches(trimmed) }
        return if (structureOnly) "" else trimmed
    }

    private suspend fun logDebugSummary() {
        val isDebuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return

        val analysisDateProvider = SystemAnalysisDateProvider()
        val today = analysisDateProvider.today().format(DateTimeFormatter.ISO_LOCAL_DATE)
        Log.d(
            "TrainingDbSummary",
            listOf(
                "exerciseCount=${exerciseDao.countExercises()}",
                "trainingProgramCount=${programDao.countPrograms()}",
                "trainingProgramItemCount=${programDao.countProgramItems()}",
                "todayWorkoutEntryCount=${workoutDao.countEntriesOnDate(today)}",
                "todayConfirmedSetCount=${workoutDao.countConfirmedSetsOnDate(today)}",
                "todayUnconfirmedSetCount=${workoutDao.countUnconfirmedSetsOnDate(today)}"
            ).joinToString(", ")
        )

        val metadataReport = MetadataSanityChecker.checkAll(exerciseDao.allExercises())
        Log.d(
            "ExerciseMetadataSummary",
            listOf(
                "total=${metadataReport.totalExerciseCount}",
                "confidence=${metadataReport.confidenceCounts}",
                "issueCount=${metadataReport.issueCount}",
                "errorCount=${metadataReport.errorCount}",
                "needsReview=${metadataReport.needsReviewExerciseNames.take(20)}"
            ).joinToString(", ")
        )

        runCatching {
            AnalysisEngineV3(
                inputCollector = AnalysisInputCollector(db),
                dateProvider = analysisDateProvider
            ).analyze()
        }.onSuccess { result ->
            Log.d(
                "AnalysisEngineV3",
                listOf(
                    "today=${result.today}",
                    "weeklyLoad7=${result.commonLoadMetrics.weeklyLoad7}",
                    "chronicLoad28=${result.commonLoadMetrics.chronicLoad28}",
                    "plannedSessionsNext7=${result.commonPlanProjectionMetrics.plannedSessionsNext7}",
                    "enabledMethodResults=${result.methodResults.size}",
                    "warnings=${result.debugWarnings.joinToString("|")}"
                ).joinToString(", ")
            )
        }.onFailure { error ->
            Log.w("AnalysisEngineV3", "V3 debug summary failed.", error)
        }
    }

    private suspend fun AppMetaDao.intValue(key: String): Int =
        value(key)?.toIntOrNull() ?: 0

    private suspend fun refreshExerciseAnalysisMetadata() {
        exerciseDao.allExercises().forEach { exercise ->
            if (!exercise.needsAnalysisMetadataRefresh()) return@forEach
            val mapped = ExerciseMetadataMapper.applyLegacyMetadata(exercise)
            if (mapped != exercise) {
                exerciseDao.updateExercise(mapped)
            }
        }
    }

    private fun Exercise.needsAnalysisMetadataRefresh(): Boolean =
        compoundType.isBlank() ||
            plane.isBlank() ||
            axialLoadLevel.isBlank() ||
            fatigueCategories.isBlank() ||
            adaptiveBaselineGroups.isBlank() ||
            recoveryDecayProfile.isBlank() ||
            progressMetricType.isBlank() ||
            strengthProgressionGroup.isBlank() ||
            hypertrophyVolumeGroup.isBlank() ||
            mainLiftGroup.isBlank() ||
            accessoryContributionGroup.isBlank() ||
            badmintonTransferStrength.isBlank() ||
            courtMovementTypes.isBlank() ||
            badmintonSkillTargets.isBlank() ||
            stabilityDemandLevel.isBlank() ||
            mobilityDemandLevel.isBlank() ||
            analysisEligibility.isBlank() ||
            metadataConfidence !in MetadataConfidence.entries.map { confidence -> confidence.name }

    private fun ProgramSeed.displayName(): String =
        when (name) {
            "배드민턴 웨이트 보조 4주" -> "배드민턴 보조 4주"
            else -> name
        }

    private companion object {
        const val EXERCISE_SEED_VERSION = 3
        const val PROGRAM_SEED_VERSION = 1
        const val META_EXERCISE_SEED_VERSION = "exercise_seed_version"
        const val META_PROGRAM_SEED_VERSION = "program_seed_version"
        val timedCategories = setOf("유산소운동", "스포츠")
    }
}
