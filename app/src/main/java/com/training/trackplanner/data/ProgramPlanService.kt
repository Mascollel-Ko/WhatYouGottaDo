package com.training.trackplanner.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RecordRangeProgramSummary(
    val startDate: String,
    val endDate: String,
    val durationDays: Int,
    val entryCount: Int,
    val setCount: Int,
    val confirmedSetCount: Int,
    val unconfirmedSetCount: Int,
    val defaultName: String
)

internal class ProgramPlanService(
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val prescriptionNoteFormatter: (String) -> String,
    private val builtInProgramKeys: () -> Set<String>,
    private val workoutSourceIdentityProvider: WorkoutSourceIdentityProvider? = null
) {
    val programs: Flow<List<TrainingProgram>> = programDao.observePrograms()

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        programDao.observeItems(programId)

    fun programItemSets(programId: Long): Flow<List<TrainingProgramItemSet>> =
        programDao.observeProgramItemSets(programId)

    suspend fun createProgram(): Long =
        programDao.insertProgram(
            TrainingProgram(
                name = "새 프로그램",
                durationDays = 28
            )
        )

    suspend fun saveGeneratedProgram(
        existingProgramId: Long?,
        skeleton: GeneratedProgramSkeleton
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val request = skeleton.request
        val existing = existingProgramId?.let { programDao.findProgram(it) }
        val program = TrainingProgram(
            id = existing?.id ?: 0,
            stableKey = existing?.stableKey ?: ProgramStableKeyPolicy.newUserKey(),
            name = skeleton.suggestedName.ifBlank { request.name.ifBlank { "새 프로그램" } },
            durationDays = skeleton.durationDays,
            createdAt = existing?.createdAt ?: now,
            goal = request.goal.name,
            weeklyTrainingDays = request.weeklyTrainingDays,
            sessionMinutes = request.sessionMinutes,
            availableEquipment = request.availableEquipment.joinToString("|"),
            excludedExerciseText = request.excludedExerciseText,
            badmintonTransferRatio = request.badmintonTransferRatio,
            sportStrengthRatio = request.sportStrengthRatio,
            periodizationType = skeleton.periodizationType.name,
            updatedAt = now
        )
        val programId = if (existing != null) {
            programDao.updateProgram(program)
            programDao.deleteProgramItems(existing.id)
            existing.id
        } else {
            programDao.insertProgram(program)
        }
        programDao.deleteProgramTombstone(program.stableKey)
        skeleton.items.forEach { item ->
            val itemId = programDao.insertProgramItem(item.toTrainingProgramItem(programId))
            programDao.insertProgramItemSets(
                ProgramSetPrescriptionResolver.resolve(item).map { set -> set.toEntity(itemId) }
            )
        }
        programId
    }

    suspend fun deleteProgram(programId: Long) {
        db.withTransaction {
            val program = programDao.findProgram(programId) ?: return@withTransaction
            programDao.deleteProgramItems(programId)
            programDao.deleteProgram(programId)
            if (program.stableKey in builtInProgramKeys()) {
                programDao.upsertProgramTombstone(
                    TrainingProgramTombstone(program.stableKey)
                )
            }
        }
    }

    suspend fun addExerciseToProgram(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        exerciseStableKey: String
    ) {
        val exercise = exerciseDao.findByStableKey(exerciseStableKey) ?: return
        val nextOrder = (programDao.itemsForProgramDay(programId, weekNumber, dayOfWeek)
            .maxOfOrNull { it.orderIndex } ?: 0) + 1
        val seconds = if (exercise.mode.contains("시간") || exercise.category in timedCategories) 30 else 0
        val itemId = programDao.insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = weekNumber,
                dayOfWeek = dayOfWeek,
                orderIndex = nextOrder,
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = exercise.defaultRestSeconds,
                prescription = "",
                setCount = 1,
                reps = 0,
                weightKg = 0.0,
                seconds = seconds,
                trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                dayIntensity = ProgramDayIntensity.MODERATE.name,
                weightSource = "MANUAL_INPUT"
            )
        )
        programDao.insertProgramItemSets(
            listOf(ProgramSetPrescription(1, 0, 0.0, seconds).toEntity(itemId))
        )
    }

    suspend fun updateProgramItem(item: TrainingProgramItem) {
        programDao.updateProgramItem(item)
    }

    suspend fun deleteProgramItem(item: TrainingProgramItem) {
        programDao.deleteProgramItem(item)
        reindexProgramDay(item.programId, item.weekNumber, item.dayOfWeek)
    }

    suspend fun programHasDateConflicts(programId: Long, startDate: String): Boolean =
        programApplyConflictSummary(programId, startDate).hasExistingEntries

    suspend fun programApplyConflictSummary(
        programId: Long,
        startDate: String
    ): ProgramApplyConflictSummary {
        val program = programDao.findProgram(programId)
        val programItems = programDao.itemsForProgram(programId)
        val range = program?.dateRangeFor(startDate)
        return if (program == null || programItems.isEmpty() || range == null) {
            ProgramApplyConflictSummary()
        } else {
            ProgramApplyConflictSummary(
                affectedDateCount = program.durationDays,
                existingEntryCount = workoutDao.countPlannedOnlyEntriesBetween(range.first, range.second),
                existingConfirmedSetCount = workoutDao.countConfirmedSetsBetween(range.first, range.second),
                startDate = range.first,
                endDate = range.second,
                newPlannedEntryCount = programItems.size
            )
        }
    }

    suspend fun applyProgramToDates(
        programId: Long,
        startDate: String,
        mode: ProgramApplyMode
    ) {
        val program = programDao.findProgram(programId) ?: return
        val items = programDao.itemsForProgram(program.id)
        if (items.isEmpty()) return
        val storedSetsByItemId = programDao.programItemSetsForProgram(program.id)
            .groupBy(TrainingProgramItemSet::programItemId)
        val range = program.dateRangeFor(startDate) ?: return
        db.withTransaction {
            if (mode == ProgramApplyMode.Overwrite) {
                workoutDao.deletePlannedOnlySetsBetween(range.first, range.second)
                workoutDao.deletePlannedOnlyEntriesBetween(range.first, range.second)
            }

            val now = System.currentTimeMillis()
            items.forEachIndexed { index, item ->
                val itemDate = dateForProgramItem(startDate, item)
                val storedSets = storedSetsByItemId[item.id].orEmpty()
                val entryId = workoutDao.insertEntry(
                    WorkoutEntry(
                        date = itemDate,
                        exerciseStableKey = item.exerciseStableKey,
                        exerciseName = item.exerciseName,
                        category = item.category,
                        restSeconds = item.restSeconds,
                        notes = prescriptionNoteFormatter(item.prescription),
                        createdAt = now + index,
                        displayOrder = index + 1,
                        backupSourceId = workoutSourceIdentityProvider?.newWorkoutSourceId()
                    )
                )
                ProgramSetPrescriptionResolver.resolve(item, storedSets).forEach { set ->
                    workoutDao.insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = set.setIndex,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            seconds = set.seconds,
                            confirmed = false,
                            manualWeight = set.weightKg > 0.0
                        )
                    )
                }
            }
        }
    }

    suspend fun recordRangeProgramSummary(
        firstDate: String,
        secondDate: String
    ): RecordRangeProgramSummary {
        val range = normalizedDateRange(firstDate, secondDate)
        val entries = workoutDao.entriesWithSetsBetween(range.first.toString(), range.second.toString())
        val sets = entries.flatMap(WorkoutEntryWithSets::sets)
        return RecordRangeProgramSummary(
            startDate = range.first.toString(),
            endDate = range.second.toString(),
            durationDays = ChronoUnit.DAYS.between(range.first, range.second).toInt() + 1,
            entryCount = entries.size,
            setCount = sets.size,
            confirmedSetCount = sets.count(WorkoutSet::confirmed),
            unconfirmedSetCount = sets.count { !it.confirmed },
            defaultName = "${range.first.format(PROGRAM_NAME_DATE)}~" +
                "${range.second.format(PROGRAM_NAME_DATE)} 기록 프로그램"
        )
    }

    suspend fun createProgramFromRecordRange(
        firstDate: String,
        secondDate: String,
        name: String
    ): Long = db.withTransaction {
        val range = normalizedDateRange(firstDate, secondDate)
        val entries = workoutDao.entriesWithSetsBetween(range.first.toString(), range.second.toString())
        require(entries.isNotEmpty()) { "선택한 기간에 저장할 운동 기록이 없습니다." }
        val durationDays = ChronoUnit.DAYS.between(range.first, range.second).toInt() + 1
        val weeklyTrainingDays = entries
            .groupBy { row ->
                ChronoUnit.DAYS.between(range.first, LocalDate.parse(row.entry.date)).toInt() / 7
            }
            .maxOfOrNull { (_, rows) -> rows.map { it.entry.date }.distinct().size }
            ?: 0
        val now = System.currentTimeMillis()
        val defaultName = "${range.first.format(PROGRAM_NAME_DATE)}~" +
            "${range.second.format(PROGRAM_NAME_DATE)} 기록 프로그램"
        val programId = programDao.insertProgram(
            TrainingProgram(
                stableKey = ProgramStableKeyPolicy.newUserKey(),
                name = name.trim().ifBlank { defaultName },
                durationDays = durationDays,
                createdAt = now,
                weeklyTrainingDays = weeklyTrainingDays,
                updatedAt = now
            )
        )
        entries
            .groupBy { it.entry.date }
            .toSortedMap()
            .forEach { (date, dayEntries) ->
                val offset = ChronoUnit.DAYS.between(range.first, LocalDate.parse(date)).toInt()
                dayEntries
                    .sortedWith(
                        compareBy<WorkoutEntryWithSets> { it.entry.displayOrder }
                            .thenBy { it.entry.createdAt }
                            .thenBy { it.entry.id }
                    )
                    .forEachIndexed { order, row ->
                        val prescriptions = row.sets
                            .sortedWith(compareBy<WorkoutSet> { it.setIndex }.thenBy { it.id })
                            .mapIndexed { index, set ->
                                ProgramSetPrescription(index + 1, set.reps, set.weightKg, set.seconds)
                            }
                        val summary = ProgramSetPrescriptionResolver.summarize(prescriptions)
                        val itemId = programDao.insertProgramItem(
                            TrainingProgramItem(
                                programId = programId,
                                weekNumber = offset / 7 + 1,
                                dayOfWeek = offset % 7 + 1,
                                orderIndex = order + 1,
                                exerciseStableKey = row.entry.exerciseStableKey,
                                exerciseName = row.entry.exerciseName,
                                category = row.entry.category,
                                restSeconds = row.entry.restSeconds,
                                prescription = row.entry.notes,
                                setCount = summary.setCount,
                                reps = summary.reps,
                                weightKg = summary.weightKg,
                                seconds = summary.seconds,
                                weightSource = "RECORDED"
                            )
                        )
                        if (prescriptions.isNotEmpty()) {
                            programDao.insertProgramItemSets(
                                prescriptions.map { set -> set.toEntity(itemId) }
                            )
                        }
                    }
            }
        programId
    }

    private suspend fun reindexProgramDay(programId: Long, weekNumber: Int, dayOfWeek: Int) {
        programDao.itemsForProgramDay(programId, weekNumber, dayOfWeek)
            .forEachIndexed { index, remaining ->
                programDao.updateProgramItemOrder(remaining.id, index + 1)
            }
    }

    private fun TrainingProgram.dateRangeFor(startDate: String): Pair<String, String>? =
        runCatching {
            val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val end = start.plusDays(durationDays.coerceAtLeast(1).toLong() - 1L)
            start.format(DateTimeFormatter.ISO_LOCAL_DATE) to end.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()

    private fun dateForProgramItem(startDate: String, item: TrainingProgramItem): String {
        val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val daysFromStart = ((item.weekNumber - 1) * 7L) + (item.dayOfWeek - 1L)
        return start.plusDays(daysFromStart).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private companion object {
        val PROGRAM_NAME_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        val timedCategories = setOf("유산소운동", "스포츠")
    }
}

private fun normalizedDateRange(firstDate: String, secondDate: String): Pair<LocalDate, LocalDate> {
    val first = LocalDate.parse(firstDate)
    val second = LocalDate.parse(secondDate)
    return if (first <= second) first to second else second to first
}

private fun ProgramSetPrescription.toEntity(programItemId: Long): TrainingProgramItemSet =
    TrainingProgramItemSet(
        programItemId = programItemId,
        setIndex = setIndex,
        reps = reps,
        weightKg = weightKg,
        seconds = seconds
    )

internal fun ProgramSkeletonItem.toTrainingProgramItem(programId: Long): TrainingProgramItem {
    val summary = ProgramSetPrescriptionResolver.summarize(
        ProgramSetPrescriptionResolver.resolve(this)
    )
    return TrainingProgramItem(
        programId = programId,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        orderIndex = orderIndex,
        exerciseStableKey = exerciseStableKey,
        exerciseName = exerciseName,
        category = category,
        restSeconds = restSeconds,
        prescription = prescription,
        setCount = summary.setCount,
        reps = summary.reps,
        weightKg = summary.weightKg,
        seconds = summary.seconds,
        trainingSlot = trainingSlot.ifBlank { ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name },
        dayIntensity = dayIntensity.ifBlank { ProgramDayIntensity.MODERATE.name },
        weightSource = weightSource.ifBlank { "MANUAL_OR_EXISTING" }
    )
}
