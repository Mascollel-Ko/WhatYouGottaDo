package com.training.trackplanner.data

import androidx.room.withTransaction

internal class RecordMutationService(
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao
) {
    suspend fun addWorkoutEntry(date: String, exerciseId: Long): Long {
        val exercise = exerciseDao.findById(exerciseId) ?: return 0L
        return db.withTransaction {
            val beforeInsert = normalizeDisplayOrder(date)
            val latestConfirmedEntryId = beforeInsert
                .filter { record -> record.sets.any(WorkoutSet::confirmed) }
                .maxByOrNull { record ->
                    record.entry.completedAt ?: record.entry.firstConfirmedAt ?: record.entry.createdAt
                }
                ?.entry
                ?.id
            val entryId = workoutDao.insertEntry(
                WorkoutEntry(
                    date = date,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    category = exercise.category,
                    restSeconds = exercise.defaultRestSeconds,
                    displayOrder = beforeInsert.size + 1
                )
            )
            workoutDao.insertSet(defaultSet(entryId, 1, exercise))
            if (latestConfirmedEntryId != null) {
                moveEntryAfter(date, entryId, latestConfirmedEntryId)
            }
            entryId
        }
    }

    suspend fun updateWorkoutEntry(entry: WorkoutEntry) {
        workoutDao.updateEntry(entry)
        refreshEntryCompletion(entry.id)
    }

    suspend fun deleteWorkoutEntry(entry: WorkoutEntry) {
        db.withTransaction {
            workoutDao.deleteSetsForEntry(entry.id)
            workoutDao.deleteEntryById(entry.id)
        }
    }

    suspend fun addSet(entry: WorkoutEntry) {
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

    suspend fun updateSet(set: WorkoutSet) {
        db.withTransaction {
            val existing = workoutDao.findSetById(set.id)
            val newlyConfirmed = set.confirmed && existing?.confirmed != true
            val firstConfirmationForEntry = newlyConfirmed && workoutDao.confirmedCountForEntry(set.entryId) == 0
            if (firstConfirmationForEntry) {
                val entry = workoutDao.findEntryById(set.entryId)
                if (entry != null) {
                    val records = normalizeDisplayOrder(entry.date)
                    val previousPerformedEntryId = records
                        .asSequence()
                        .filter { record -> record.entry.id != entry.id }
                        .filter { record -> record.entry.firstConfirmedAt != null }
                        .maxByOrNull { record -> record.entry.firstConfirmedAt ?: Long.MIN_VALUE }
                        ?.entry
                        ?.id
                    moveEntryAfter(entry.date, entry.id, previousPerformedEntryId)
                }
            }
            workoutDao.updateSet(set)
            if (newlyConfirmed) {
                workoutDao.markEntryConfirmed(set.entryId, System.currentTimeMillis())
            } else {
                refreshEntryCompletion(set.entryId)
            }
        }
    }

    suspend fun deleteSet(set: WorkoutSet): Boolean {
        if (workoutDao.setCount(set.entryId) <= 1) return false
        workoutDao.deleteSet(set)
        workoutDao.setsForEntry(set.entryId).forEachIndexed { index, remainingSet ->
            workoutDao.updateSetIndex(remainingSet.id, index + 1)
        }
        refreshEntryCompletion(set.entryId)
        return true
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
        val confirmedCount = workoutDao.confirmedCountForEntry(entryId)
        val entry = workoutDao.findEntryById(entryId) ?: return
        when {
            confirmedCount == 0 -> workoutDao.updateEntryCompletedAt(entryId, null)
            entry.completedAt == null -> workoutDao.updateEntryCompletedAt(entryId, System.currentTimeMillis())
        }
    }

    private suspend fun normalizeDisplayOrder(date: String): List<WorkoutEntryWithSets> {
        val ordered = RecordEntryOrdering.ordered(workoutDao.entriesWithSets(date))
        ordered.forEachIndexed { index, record ->
            val order = index + 1
            if (record.entry.displayOrder != order) {
                workoutDao.updateEntryDisplayOrder(record.entry.id, order)
            }
        }
        return ordered.mapIndexed { index, record ->
            record.copy(entry = record.entry.copy(displayOrder = index + 1))
        }
    }

    private suspend fun moveEntryAfter(date: String, movingEntryId: Long, anchorEntryId: Long?) {
        val orderedIds = normalizeDisplayOrder(date).map { it.entry.id }
        RecordEntryOrdering.moveAfter(orderedIds, movingEntryId, anchorEntryId)
            .forEachIndexed { index, entryId ->
                workoutDao.updateEntryDisplayOrder(entryId, index + 1)
            }
    }
}
