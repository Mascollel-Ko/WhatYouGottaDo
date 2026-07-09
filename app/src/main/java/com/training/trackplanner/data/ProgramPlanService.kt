package com.training.trackplanner.data

import androidx.room.withTransaction
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class TodayProgramGateContext(
    val date: String,
    val gate: ProgramFatigueGate,
    val candidatesByExerciseId: Map<Long, ProgramCandidate>
)

internal class ProgramPlanService(
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val runtimeMetadataCatalogResolver: suspend (List<Exercise>) -> RuntimeExerciseMetadataCatalog,
    private val prescriptionNoteFormatter: (String) -> String
) {
    val programs: Flow<List<TrainingProgram>> = programDao.observePrograms()

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        programDao.observeItems(programId)

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
        val program = TrainingProgram(
            id = existingProgramId ?: 0,
            name = skeleton.suggestedName.ifBlank { request.name.ifBlank { "새 프로그램" } },
            durationDays = skeleton.durationDays,
            createdAt = existingProgramId?.let { programDao.findProgram(it)?.createdAt } ?: now,
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
        val programId = if (existingProgramId != null && programDao.findProgram(existingProgramId) != null) {
            programDao.updateProgram(program)
            programDao.deleteProgramItems(existingProgramId)
            existingProgramId
        } else {
            programDao.insertProgram(program)
        }
        programDao.insertProgramItems(
            skeleton.items.map { item -> item.toTrainingProgramItem(programId) }
        )
        programId
    }

    suspend fun deleteProgram(programId: Long) {
        db.withTransaction {
            programDao.deleteProgramItems(programId)
            programDao.deleteProgram(programId)
        }
    }

    suspend fun addExerciseToProgram(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        exerciseId: Long
    ) {
        val exercise = exerciseDao.findById(exerciseId) ?: return
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
                seconds = if (exercise.mode.contains("시간") || exercise.category in timedCategories) 30 else 0,
                trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                dayIntensity = ProgramDayIntensity.MODERATE.name,
                weightSource = "MANUAL_INPUT"
            )
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
        mode: ProgramApplyMode,
        trainingGate: TrainingGateSnapshot? = null
    ) {
        val program = programDao.findProgram(programId) ?: return
        val items = programDao.itemsForProgram(program.id)
        if (items.isEmpty()) return
        val range = program.dateRangeFor(startDate) ?: return
        val fatigueSlotPolicy = FatigueSlotPolicy.DEFAULT
        val todayGateContext = trainingGate?.let { gateSnapshot ->
            val today = SystemAnalysisDateProvider().today().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val exercises = exerciseDao.allExercises()
            val metadataCatalog = runtimeMetadataCatalogResolver(exercises)
            TodayProgramGateContext(
                date = today,
                gate = fatigueSlotPolicy.gate(gateSnapshot),
                candidatesByExerciseId = exercises.associate { exercise ->
                    val metadata = metadataCatalog.resolve(exercise)
                    exercise.id to ProgramCandidate(
                        exercise = exercise,
                        metadata = metadata,
                        canonical = metadata != null,
                        slotCapabilities = SlotCapabilityResolver.DEFAULT.resolve(exercise, metadata)
                    )
                }
            )
        }
        db.withTransaction {
            if (mode == ProgramApplyMode.Overwrite) {
                workoutDao.deletePlannedOnlySetsBetween(range.first, range.second)
                workoutDao.deletePlannedOnlyEntriesBetween(range.first, range.second)
            }

            val now = System.currentTimeMillis()
            items.forEachIndexed { index, item ->
                val itemDate = dateForProgramItem(startDate, item)
                val adjustedItem = fatigueSlotPolicy.adjustItemForResolvedDate(
                    item = item,
                    itemDate = itemDate,
                    todayDate = todayGateContext?.date,
                    candidate = todayGateContext?.candidatesByExerciseId?.get(item.exerciseId),
                    gate = todayGateContext?.gate
                ) ?: return@forEachIndexed
                val entryId = workoutDao.insertEntry(
                    WorkoutEntry(
                        date = itemDate,
                        exerciseId = adjustedItem.exerciseId,
                        exerciseName = adjustedItem.exerciseName,
                        category = adjustedItem.category,
                        restSeconds = adjustedItem.restSeconds,
                        notes = prescriptionNoteFormatter(adjustedItem.prescription),
                        createdAt = now + index,
                        displayOrder = index + 1
                    )
                )
                repeat(adjustedItem.setCount.coerceAtLeast(1)) { setIndex ->
                    workoutDao.insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = setIndex + 1,
                            reps = adjustedItem.reps,
                            weightKg = adjustedItem.weightKg,
                            seconds = adjustedItem.seconds,
                            confirmed = false,
                            manualWeight = adjustedItem.weightKg > 0.0
                        )
                    )
                }
            }
        }
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
        val timedCategories = setOf("유산소운동", "스포츠")
    }
}

internal fun ProgramSkeletonItem.toTrainingProgramItem(programId: Long): TrainingProgramItem =
    TrainingProgramItem(
        programId = programId,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        orderIndex = orderIndex,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        category = category,
        restSeconds = restSeconds,
        prescription = prescription,
        setCount = setCount.coerceAtLeast(1),
        reps = reps,
        weightKg = weightKg,
        seconds = seconds,
        trainingSlot = trainingSlot.ifBlank { ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name },
        dayIntensity = dayIntensity.ifBlank { ProgramDayIntensity.MODERATE.name },
        weightSource = weightSource.ifBlank { "MANUAL_OR_EXISTING" }
    )
