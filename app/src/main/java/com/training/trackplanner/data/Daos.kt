package com.training.trackplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class WorkoutEntryWithSets(
    @Embedded val entry: WorkoutEntry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val sets: List<WorkoutSet>
)

data class AnalysisStats(
    val confirmedSetCount: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val totalSeconds: Int = 0
)

data class DailyRecordSummary(
    val date: String,
    val confirmedSets: Int = 0,
    val plannedSets: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val totalSeconds: Int = 0,
    val entryCount: Int = 0,
    val categorySummary: String = "",
    val bodyPartSummary: String? = null
)

data class CalendarConflictSummary(
    val affectedDateCount: Int = 0,
    val existingDateCount: Int = 0,
    val existingEntryCount: Int = 0,
    val existingSetCount: Int = 0,
    val existingConfirmedSetCount: Int = 0
) {
    val hasExistingEntries: Boolean
        get() = existingEntryCount > 0
}

data class ProgramWithItems(
    val program: TrainingProgram,
    val items: List<TrainingProgramItem>
)

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY category, name")
    fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT * FROM exercises WHERE stableKey = :stableKey LIMIT 1")
    suspend fun findByStableKey(stableKey: String): Exercise?

    @Query("SELECT * FROM exercises")
    suspend fun allExercises(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun countExercises(): Int

    @Query("SELECT * FROM exercises WHERE isCustom = 1 AND trim(stableKey) = ''")
    suspend fun customExercisesWithBlankStableKey(): List<Exercise>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercise(exercise: Exercise): Long

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)
}

@Dao
interface RuntimeExerciseMetadataDao {
    @Query("SELECT * FROM runtime_exercise_metadata WHERE stableKey = :stableKey LIMIT 1")
    suspend fun findByStableKey(stableKey: String): RuntimeExerciseMetadataEntity?

    @Query("SELECT * FROM runtime_exercise_metadata")
    suspend fun all(): List<RuntimeExerciseMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: RuntimeExerciseMetadataEntity)

    @Query("DELETE FROM runtime_exercise_metadata WHERE stableKey = :stableKey")
    suspend fun deleteByStableKey(stableKey: String)
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_entries WHERE date = :date ORDER BY createdAt, id")
    fun observeEntriesWithSets(date: String): Flow<List<WorkoutEntryWithSets>>

    @Transaction
    @Query("SELECT * FROM workout_entries WHERE date = :date ORDER BY createdAt, id")
    suspend fun entriesWithSets(date: String): List<WorkoutEntryWithSets>

    @Transaction
    @Query("SELECT * FROM workout_entries WHERE date <= :date ORDER BY date, createdAt, id")
    suspend fun entriesWithSetsUntil(date: String): List<WorkoutEntryWithSets>

    @Transaction
    @Query("SELECT * FROM workout_entries WHERE date > :date ORDER BY date, createdAt, id")
    suspend fun entriesWithSetsAfter(date: String): List<WorkoutEntryWithSets>

    @Transaction
    @Query("SELECT * FROM workout_entries ORDER BY date, createdAt, id")
    suspend fun allEntriesWithSets(): List<WorkoutEntryWithSets>

    @Query("SELECT COUNT(*) FROM workout_entries WHERE date = :date")
    fun observeEntryCount(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM workout_entries WHERE date IN (:dates)")
    suspend fun countEntriesOnDates(dates: List<String>): Int

    @Query("SELECT COUNT(DISTINCT date) FROM workout_entries WHERE date IN (:dates)")
    suspend fun countDatesWithEntries(dates: List<String>): Int

    @Query("SELECT COUNT(*) FROM workout_entries WHERE date = :date")
    suspend fun countEntriesOnDate(date: String): Int

    @Query("SELECT COUNT(*) FROM workout_entries WHERE exerciseId = :exerciseId")
    suspend fun countEntriesForExercise(exerciseId: Long): Int

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date IN (:dates)
        """
    )
    suspend fun countSetsOnDates(dates: List<String>): Int

    @Insert
    suspend fun insertEntry(entry: WorkoutEntry): Long

    @Query("SELECT * FROM workout_entries WHERE id = :entryId LIMIT 1")
    suspend fun findEntryById(entryId: Long): WorkoutEntry?

    @Update
    suspend fun updateEntry(entry: WorkoutEntry)

    @Insert
    suspend fun insertSet(set: WorkoutSet): Long

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Query("SELECT * FROM workout_sets WHERE id = :setId LIMIT 1")
    suspend fun findSetById(setId: Long): WorkoutSet?

    @Delete
    suspend fun deleteSet(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE entryId = :entryId")
    suspend fun deleteSetsForEntry(entryId: Long)

    @Query("SELECT * FROM workout_sets WHERE entryId = :entryId ORDER BY setIndex")
    suspend fun setsForEntry(entryId: Long): List<WorkoutSet>

    @Query("SELECT COUNT(*) FROM workout_sets WHERE entryId = :entryId")
    suspend fun setCount(entryId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_sets WHERE entryId = :entryId AND confirmed = 1")
    suspend fun confirmedCountForEntry(entryId: Long): Int

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date IN (:dates) AND workout_sets.confirmed = 1
        """
    )
    suspend fun countConfirmedSetsOnDates(dates: List<String>): Int

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date BETWEEN :startDate AND :endDate AND workout_sets.confirmed = 1
        """
    )
    suspend fun countConfirmedSetsBetween(startDate: String, endDate: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM workout_entries
        WHERE date BETWEEN :startDate AND :endDate
        AND NOT EXISTS (
            SELECT 1 FROM workout_sets
            WHERE workout_sets.entryId = workout_entries.id
            AND workout_sets.confirmed = 1
        )
        """
    )
    suspend fun countPlannedOnlyEntriesBetween(startDate: String, endDate: String): Int

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date = :date AND workout_sets.confirmed = 1
        """
    )
    suspend fun countConfirmedSetsOnDate(date: String): Int

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date = :date AND workout_sets.confirmed = 0
        """
    )
    suspend fun countUnconfirmedSetsOnDate(date: String): Int

    @Query("UPDATE workout_entries SET completedAt = :completedAt WHERE id = :entryId")
    suspend fun updateEntryCompletedAt(entryId: Long, completedAt: Long?)

    @Query(
        """
        UPDATE workout_entries
        SET displayOrder = :displayOrder
        WHERE id = :entryId
        """
    )
    suspend fun updateEntryDisplayOrder(entryId: Long, displayOrder: Int)

    @Query(
        """
        UPDATE workout_entries
        SET firstConfirmedAt = COALESCE(firstConfirmedAt, :confirmedAt),
            completedAt = :confirmedAt
        WHERE id = :entryId
        """
    )
    suspend fun markEntryConfirmed(entryId: Long, confirmedAt: Long)

    @Query("UPDATE workout_sets SET setIndex = :setIndex WHERE id = :setId")
    suspend fun updateSetIndex(setId: Long, setIndex: Int)

    @Query(
        """
        DELETE FROM workout_sets
        WHERE entryId IN (
            SELECT id FROM workout_entries WHERE date IN (:dates)
        )
        """
    )
    suspend fun deleteSetsOnDates(dates: List<String>)

    @Query("DELETE FROM workout_entries WHERE date IN (:dates)")
    suspend fun deleteEntriesOnDates(dates: List<String>)

    @Query("DELETE FROM workout_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)

    @Query(
        """
        DELETE FROM workout_sets
        WHERE entryId IN (
            SELECT workout_entries.id
            FROM workout_entries
            WHERE workout_entries.date BETWEEN :startDate AND :endDate
            AND NOT EXISTS (
                SELECT 1 FROM workout_sets AS confirmed_sets
                WHERE confirmed_sets.entryId = workout_entries.id
                AND confirmed_sets.confirmed = 1
            )
        )
        """
    )
    suspend fun deletePlannedOnlySetsBetween(startDate: String, endDate: String)

    @Query(
        """
        DELETE FROM workout_entries
        WHERE date BETWEEN :startDate AND :endDate
        AND NOT EXISTS (
            SELECT 1 FROM workout_sets
            WHERE workout_sets.entryId = workout_entries.id
            AND workout_sets.confirmed = 1
        )
        """
    )
    suspend fun deletePlannedOnlyEntriesBetween(startDate: String, endDate: String)

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date = :date AND workout_sets.confirmed = 0
        """
    )
    fun observePlannedSetCount(date: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(workout_sets.id)
        FROM workout_sets
        INNER JOIN workout_entries ON workout_entries.id = workout_sets.entryId
        WHERE workout_entries.date = :date AND workout_sets.confirmed = 1
        """
    )
    fun observeConfirmedSetCount(date: String): Flow<Int>

    @Query(
        """
        SELECT
            COUNT(workout_sets.id) AS confirmedSetCount,
            COALESCE(SUM(workout_sets.reps * workout_sets.weightKg), 0.0) AS totalVolumeKg,
            COALESCE(SUM(workout_sets.seconds), 0) AS totalSeconds
        FROM workout_sets
        WHERE workout_sets.confirmed = 1
        """
    )
    fun observeAnalysisStats(): Flow<AnalysisStats>

    @Query(
        """
        SELECT
            workout_entries.date AS date,
            COALESCE(SUM(CASE WHEN workout_sets.confirmed = 1 THEN 1 ELSE 0 END), 0) AS confirmedSets,
            COALESCE(SUM(CASE WHEN workout_sets.confirmed = 0 THEN 1 ELSE 0 END), 0) AS plannedSets,
            COALESCE(SUM(CASE WHEN workout_sets.confirmed = 1 THEN workout_sets.reps * workout_sets.weightKg ELSE 0 END), 0.0) AS totalVolumeKg,
            COALESCE(SUM(CASE WHEN workout_sets.confirmed = 1 THEN workout_sets.seconds ELSE 0 END), 0) AS totalSeconds,
            COUNT(DISTINCT workout_entries.id) AS entryCount,
            COALESCE(GROUP_CONCAT(DISTINCT workout_entries.category), '') AS categorySummary,
            GROUP_CONCAT(DISTINCT NULLIF(exercises.bodyRegion, '')) AS bodyPartSummary
        FROM workout_entries
        LEFT JOIN workout_sets ON workout_sets.entryId = workout_entries.id
        LEFT JOIN exercises ON exercises.id = workout_entries.exerciseId
        WHERE workout_entries.date BETWEEN :startDate AND :endDate
        GROUP BY workout_entries.date
        ORDER BY workout_entries.date
        """
    )
    fun observeDailySummariesBetween(
        startDate: String,
        endDate: String
    ): Flow<List<DailyRecordSummary>>
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM training_programs ORDER BY createdAt DESC")
    fun observePrograms(): Flow<List<TrainingProgram>>

    @Query("SELECT COUNT(*) FROM training_programs")
    suspend fun countPrograms(): Int

    @Query("SELECT * FROM training_programs WHERE id = :programId LIMIT 1")
    suspend fun findProgram(programId: Long): TrainingProgram?

    @Query("SELECT * FROM training_programs WHERE name = :name LIMIT 1")
    suspend fun findProgramByName(name: String): TrainingProgram?

    @Query("SELECT COUNT(*) FROM training_program_items")
    suspend fun countProgramItems(): Int

    @Query("SELECT COUNT(*) FROM training_program_items WHERE exerciseId = :exerciseId")
    suspend fun countProgramItemsForExercise(exerciseId: Long): Int

    @Query(
        """
        SELECT * FROM training_program_items
        WHERE programId = :programId
        ORDER BY weekNumber, dayOfWeek, orderIndex, id
        """
    )
    fun observeItems(programId: Long): Flow<List<TrainingProgramItem>>

    @Query(
        """
        SELECT * FROM training_program_items
        WHERE programId = :programId
        ORDER BY weekNumber, dayOfWeek, orderIndex, id
        """
    )
    suspend fun itemsForProgram(programId: Long): List<TrainingProgramItem>

    @Query(
        """
        SELECT * FROM training_program_items
        WHERE programId = :programId AND weekNumber = :weekNumber AND dayOfWeek = :dayOfWeek
        ORDER BY orderIndex, id
        """
    )
    suspend fun itemsForProgramDay(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int
    ): List<TrainingProgramItem>

    @Insert
    suspend fun insertProgram(program: TrainingProgram): Long

    @Update
    suspend fun updateProgram(program: TrainingProgram)

    @Query("DELETE FROM training_program_items WHERE programId = :programId")
    suspend fun deleteProgramItems(programId: Long)

    @Query("DELETE FROM training_programs WHERE id = :programId")
    suspend fun deleteProgram(programId: Long)

    @Insert
    suspend fun insertProgramItem(item: TrainingProgramItem): Long

    @Insert
    suspend fun insertProgramItems(items: List<TrainingProgramItem>)

    @Update
    suspend fun updateProgramItem(item: TrainingProgramItem)

    @Delete
    suspend fun deleteProgramItem(item: TrainingProgramItem)

    @Query("UPDATE training_program_items SET orderIndex = :orderIndex WHERE id = :itemId")
    suspend fun updateProgramItemOrder(itemId: Long, orderIndex: Int)
}

@Dao
interface DailyMetricDao {
    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    fun observeMetric(date: String): Flow<DailyMetric?>

    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    suspend fun metric(date: String): DailyMetric?

    @Query("SELECT * FROM daily_metrics WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun observeBetween(startDate: String, endDate: String): Flow<List<DailyMetric>>

    @Query("SELECT * FROM daily_metrics WHERE date <= :date ORDER BY date")
    suspend fun metricsUntil(date: String): List<DailyMetric>

    @Query("SELECT * FROM daily_metrics ORDER BY date")
    suspend fun allMetrics(): List<DailyMetric>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: DailyMetric)
}

@Dao
interface DailyCheckInDao {
    @Query("SELECT * FROM daily_check_ins WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): DailyCheckIn?

    @Query("SELECT * FROM daily_check_ins WHERE date = :date LIMIT 1")
    fun observeForDate(date: String): Flow<DailyCheckIn?>

    @Query("SELECT * FROM daily_check_ins WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun observeBetween(startDate: String, endDate: String): Flow<List<DailyCheckIn>>

    @Query("SELECT * FROM daily_check_ins WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun between(startDate: String, endDate: String): List<DailyCheckIn>

    @Query("SELECT * FROM daily_check_ins ORDER BY date")
    suspend fun all(): List<DailyCheckIn>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkIn: DailyCheckIn)

    @Query("DELETE FROM daily_check_ins WHERE date = :date")
    suspend fun deleteForDate(date: String)
}

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    suspend fun value(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: AppMeta)
}

@Dao
interface InitialUserProfileDao {
    @Query("SELECT * FROM initial_user_profiles WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<InitialUserProfile?>

    @Query("SELECT * FROM initial_user_profiles WHERE id = 1 LIMIT 1")
    suspend fun profile(): InitialUserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: InitialUserProfile)
}
