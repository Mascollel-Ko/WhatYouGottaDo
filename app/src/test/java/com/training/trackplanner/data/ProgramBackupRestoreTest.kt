package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgramBackupRestoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @After
    fun closeDatabases() {
        databases.forEach(TrainingDatabase::close)
    }

    @Test
    fun `July 26 legacy structure restores records without touching programs or tombstones`() = runBlocking {
        val db = newDatabase()
        val programId = db.programDao().insertProgram(
            TrainingProgram(stableKey = "user_program_keep", name = "Keep me", durationDays = 14)
        )
        db.programDao().upsertProgramTombstone(
            TrainingProgramTombstone("3", deletedAt = 1234L, seedVersion = 1)
        )
        val csv = legacyCsv(
            mapOf(
                "schema_version" to "6",
                "row_type" to "exercise",
                "exercise_name" to "Legacy lift",
                "category" to "Strength",
                "stable_key" to "legacy_exercise",
                "default_rest_seconds" to "90",
                "is_active" to "1",
                "is_custom" to "1",
                "needs_review" to "0"
            ),
            mapOf(
                "schema_version" to "6",
                "row_type" to "set",
                "date" to "2026-07-26",
                "entry_key" to "legacy-entry",
                "entry_order" to "1",
                "exercise_name" to "Legacy lift",
                "category" to "Strength",
                "confirmed" to "1",
                "rest_seconds" to "90",
                "set_index" to "1",
                "set_confirmed" to "1",
                "reps" to "5",
                "weight_kg" to "80",
                "seconds" to "0",
                "stable_key" to "legacy_exercise"
            )
        )

        val result = repository(db).importRecordsBackup(writeBackup(csv))

        assertEquals(1, result.entryCount)
        assertEquals(1, result.setCount)
        assertEquals(0, result.programCount)
        assertEquals("Keep me", db.programDao().findProgram(programId)?.name)
        assertEquals(1234L, db.programDao().findProgramTombstone("3")?.deletedAt)
        assertEquals(1, db.programDao().countPrograms())
    }

    @Test
    fun `authoritative program snapshot round trips stable identities and is idempotent`() = runBlocking {
        val source = newDatabase()
        val squat = insertExercise(source, 10, "ex_squat_backup", "스쿼트")
        val row = insertExercise(source, 20, "ex_row_backup", "케이블 로우")
        val modifiedBuiltInId = source.programDao().insertProgram(
            TrainingProgram(
                stableKey = "3",
                name = "수정된, \"배드민턴\" 프로그램",
                durationDays = 28,
                createdAt = 100L,
                updatedAt = 200L,
                goal = "BADMINTON_PERFORMANCE",
                weeklyTrainingDays = 4,
                sessionMinutes = 45,
                availableEquipment = "바벨|케이블",
                excludedExerciseText = "통증, 점프",
                badmintonTransferRatio = 0.6,
                sportStrengthRatio = "AUTO",
                periodizationType = "BADMINTON_WAVE"
            )
        )
        val userProgramId = source.programDao().insertProgram(
            TrainingProgram(
                stableKey = "user_program_round_trip",
                name = "사용자 프로그램",
                durationDays = 14,
                createdAt = 300L,
                updatedAt = 400L
            )
        )
        source.programDao().insertProgramItems(
            listOf(
                TrainingProgramItem(
                    programId = modifiedBuiltInId,
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 1,
                    exerciseId = squat.id,
                    exerciseName = squat.name,
                    category = squat.category,
                    restSeconds = 150,
                    prescription = "무겁게, 그러나 \"통제\"\n2주차에는 여유 있게",
                    setCount = 4,
                    reps = 5,
                    weightKg = 100.5,
                    seconds = 0,
                    trainingSlot = "LOWER_STRENGTH",
                    dayIntensity = "HARD",
                    weightSource = "MANUAL_INPUT"
                ),
                TrainingProgramItem(
                    programId = userProgramId,
                    weekNumber = 2,
                    dayOfWeek = 4,
                    orderIndex = 1,
                    exerciseId = row.id,
                    exerciseName = row.name,
                    category = row.category,
                    restSeconds = 75,
                    prescription = "3세트, 10회",
                    setCount = 3,
                    reps = 10,
                    weightKg = 40.0,
                    seconds = 0,
                    trainingSlot = "UPPER_PULL",
                    dayIntensity = "MODERATE",
                    weightSource = "MANUAL_INPUT"
                )
            )
        )
        source.programDao().upsertProgramTombstone(
            TrainingProgramTombstone("4", deletedAt = 500L, seedVersion = 1)
        )
        val backup = exportBackup(repository(source))
        val parsed = RecordCsvBackupRestore.parse(backup) as RecordCsvImportData.Restore
        assertNotNull(parsed.programSnapshot)

        val target = newDatabase()
        insertExercise(target, 101, squat.stableKey, squat.name)
        insertExercise(target, 202, row.stableKey, row.name)
        target.programDao().insertProgram(
            TrainingProgram(stableKey = "user_program_replace_me", name = "Old", durationDays = 7)
        )

        val first = repository(target).importRecordsBackup(writeBackup(backup))
        val firstState = semanticProgramState(target)
        val second = repository(target).importRecordsBackup(writeBackup(backup))
        val secondState = semanticProgramState(target)

        assertEquals(2, first.programCount)
        assertEquals(2, first.programItemCount)
        assertEquals(1, first.programTombstoneCount)
        assertEquals(firstState, secondState)
        assertEquals(2, second.programCount)
        assertEquals(2, target.programDao().countPrograms())
        assertNull(target.programDao().findProgramByStableKey("user_program_replace_me"))
        assertEquals("수정된, \"배드민턴\" 프로그램", target.programDao().findProgramByStableKey("3")?.name)
        val restoredItems = target.programDao().allProgramItems()
        assertEquals(101L, restoredItems.single { it.exerciseName == squat.name }.exerciseId)
        assertTrue(restoredItems.single { it.exerciseName == squat.name }.prescription.contains("\n"))

        repository(target).seedMissingPrograms(listOf(SeedData.programs(context).first()))
        assertEquals("수정된, \"배드민턴\" 프로그램", target.programDao().findProgramByStableKey("3")?.name)
    }

    @Test
    fun `deleting a built in program persists tombstone through restore and later seeding`() = runBlocking {
        val seed = SeedData.programs(context).first()
        val source = newDatabase()
        val sourceRepository = repository(source)
        val programId = source.programDao().insertProgram(
            TrainingProgram(stableKey = seed.key, name = seed.name, durationDays = seed.durationDays)
        )

        sourceRepository.deleteProgram(programId)

        assertNull(source.programDao().findProgramByStableKey(seed.key))
        assertNotNull(source.programDao().findProgramTombstone(seed.key))
        val backup = exportBackup(sourceRepository)

        val target = newDatabase()
        val targetRepository = repository(target)
        targetRepository.seedMissingPrograms(listOf(seed))
        assertNotNull(target.programDao().findProgramByStableKey(seed.key))

        targetRepository.importRecordsBackup(writeBackup(backup))
        val futureSeed = seed.copy(key = "future_seed_key", name = "Future built in")
        targetRepository.seedMissingPrograms(listOf(seed, futureSeed))

        assertNull(target.programDao().findProgramByStableKey(seed.key))
        assertNotNull(target.programDao().findProgramTombstone(seed.key))
        assertNotNull(target.programDao().findProgramByStableKey(futureSeed.key))
    }

    @Test
    fun `empty authoritative snapshot replaces programs but preserves workout history`() = runBlocking {
        val db = newDatabase()
        val exercise = insertExercise(db, 5, "ex_history", "History squat")
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = "2026-07-20",
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category
            )
        )
        db.workoutDao().insertSet(
            WorkoutSet(entryId = entryId, setIndex = 1, reps = 5, weightKg = 80.0, confirmed = true)
        )
        db.programDao().insertProgram(
            TrainingProgram(stableKey = "user_program_old", name = "Old", durationDays = 7)
        )
        db.programDao().upsertProgramTombstone(TrainingProgramTombstone("3"))
        val emptySnapshot = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            includeProgramSnapshot = true
        )

        repository(db).importRecordsBackup(writeBackup(emptySnapshot))

        assertEquals(0, db.programDao().countPrograms())
        assertTrue(db.programDao().allProgramTombstones().isEmpty())
        assertEquals(1, db.workoutDao().entriesWithSets("2026-07-20").size)
    }

    @Test
    fun `unresolved program item rolls back the complete import transaction`() = runBlocking {
        val db = newDatabase()
        db.programDao().insertProgram(
            TrainingProgram(stableKey = "user_program_existing", name = "Existing", durationDays = 7)
        )
        db.programDao().upsertProgramTombstone(TrainingProgramTombstone("3", deletedAt = 99L))
        val backup = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            programs = listOf(
                TrainingProgram(stableKey = "user_program_incoming", name = "Incoming", durationDays = 7)
            ),
            programItems = listOf(
                ProgramBackupItem(
                    programStableKey = "user_program_incoming",
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 1,
                    exerciseStableKey = "missing_exercise",
                    exerciseName = "Missing",
                    category = "Strength",
                    restSeconds = 60,
                    prescription = "",
                    setCount = 1,
                    reps = 5,
                    weightKg = 10.0,
                    seconds = 0,
                    trainingSlot = null,
                    dayIntensity = null,
                    weightSource = null
                )
            ),
            includeProgramSnapshot = true
        )

        val failure = runCatching {
            repository(db).importRecordsBackup(writeBackup(backup))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure?.message.orEmpty().contains("missing_exercise"))
        assertNotNull(db.programDao().findProgramByStableKey("user_program_existing"))
        assertNull(db.programDao().findProgramByStableKey("user_program_incoming"))
        assertEquals(99L, db.programDao().findProgramTombstone("3")?.deletedAt)
    }

    @Test
    fun `legacy key repair maps only an exact seed graph and is stable on second startup`() = runBlocking {
        val db = newDatabase()
        val seed = SeedData.programs(context).first()
        val exactId = db.programDao().insertProgram(
            TrainingProgram(
                stableKey = "${ProgramStableKeyPolicy.LEGACY_PREFIX}1",
                name = seed.name.replace("배드민턴 웨이트 보조 4주", "배드민턴 보조 4주"),
                durationDays = seed.durationDays
            )
        )
        db.programDao().insertProgramItems(
            seed.items.map { item ->
                TrainingProgramItem(
                    programId = exactId,
                    weekNumber = item.weekNumber,
                    dayOfWeek = item.dayOfWeek,
                    orderIndex = item.orderIndex,
                    exerciseId = 0,
                    exerciseName = item.exerciseName,
                    category = item.category,
                    restSeconds = item.restSeconds,
                    prescription = item.prescription,
                    setCount = item.setCount.coerceAtLeast(1),
                    reps = item.reps,
                    weightKg = item.weightKg,
                    seconds = item.seconds
                )
            }
        )
        val modifiedId = db.programDao().insertProgram(
            TrainingProgram(
                stableKey = "${ProgramStableKeyPolicy.LEGACY_PREFIX}2",
                name = "Renamed old program",
                durationDays = seed.durationDays
            )
        )
        val repository = repository(db)

        repository.repairLegacyProgramStableKeys()
        val repaired = db.programDao().findProgram(exactId)?.stableKey
        val modified = db.programDao().findProgram(modifiedId)?.stableKey
        repository.repairLegacyProgramStableKeys()

        assertEquals(seed.key, repaired)
        assertEquals("${ProgramStableKeyPolicy.LEGACY_PREFIX}2", modified)
        assertEquals(repaired, db.programDao().findProgram(exactId)?.stableKey)
        assertEquals(2, db.programDao().countPrograms())
    }

    @Test
    fun `user programs receive UUID keys and generated replacements retain identity`() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val createdId = repository.createProgram()
        val created = checkNotNull(db.programDao().findProgram(createdId))
        assertTrue(created.stableKey.startsWith(ProgramStableKeyPolicy.USER_PREFIX))

        val existingId = db.programDao().insertProgram(
            TrainingProgram(stableKey = "3", name = "Built in modified", durationDays = 28)
        )
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = "Renamed generated program",
            durationDays = 28,
            request = ProgramSkeletonRequest(
                name = "Renamed generated program",
                goal = ProgramGoal.STRENGTH,
                weeklyTrainingDays = 3,
                sessionMinutes = 45,
                availableEquipment = emptySet(),
                excludedExerciseText = "",
                badmintonTransferRatio = 0.4,
                sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO
            ),
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = emptyList(),
            items = emptyList()
        )

        repository.saveGeneratedProgram(existingId, skeleton)
        val newGeneratedId = repository.saveGeneratedProgram(null, skeleton)

        assertEquals("3", db.programDao().findProgram(existingId)?.stableKey)
        assertTrue(
            checkNotNull(db.programDao().findProgram(newGeneratedId))
                .stableKey
                .startsWith(ProgramStableKeyPolicy.USER_PREFIX)
        )
        repository.deleteProgram(createdId)
        assertNull(db.programDao().findProgram(createdId))
        assertNull(db.programDao().findProgramTombstone(created.stableKey))
    }

    @Test
    fun `program snapshot parser rejects ambiguous and unsupported structures`() {
        val program = TrainingProgram(
            stableKey = "user_program_validation",
            name = "Validation",
            durationDays = 7
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            programs = listOf(program),
            includeProgramSnapshot = true
        )
        val marker = csv.lineSequence().first { line -> ",program_snapshot," in ",$line," }
        val programRow = csv.lineSequence().first { line -> ",program," in ",$line," }

        assertTrue(
            runCatching {
                RecordCsvBackupRestore.parse(csv + marker.replace(",1,", ",99,") + "\n")
            }.isFailure
        )
        assertTrue(
            runCatching {
                RecordCsvBackupRestore.parse(csv + programRow + "\n")
            }.isFailure
        )
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    private fun repository(db: TrainingDatabase): TrainingRepository =
        TrainingRepository(db, context)

    private suspend fun insertExercise(
        db: TrainingDatabase,
        id: Long,
        stableKey: String,
        name: String
    ): Exercise {
        val exercise = Exercise(
            id = id,
            stableKey = stableKey,
            name = name,
            category = "Strength",
            isCustom = true
        )
        db.exerciseDao().insertExercise(exercise)
        return exercise
    }

    private suspend fun exportBackup(repository: TrainingRepository): String {
        val file = File.createTempFile("program-export", ".csv")
        repository.exportRecordsBackup(Uri.fromFile(file))
        return file.readText(Charsets.UTF_8)
    }

    private fun writeBackup(csv: String): Uri {
        val file = File.createTempFile("program-restore", ".csv")
        file.writeText(csv, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    private fun legacyCsv(vararg rows: Map<String, String>): String {
        val header = checkNotNull(
            javaClass.classLoader?.getResourceAsStream(
                "legacy/whatyougottatrain_backup_2026-07-26-v6-header.csv"
            )
        ).bufferedReader().use { reader -> reader.readText().trim() }
        val columns = header.split(',')
        return buildString {
            appendLine(header)
            rows.forEach { values ->
                appendLine(columns.joinToString(",") { column -> values[column].orEmpty() })
            }
        }
    }

    private suspend fun semanticProgramState(db: TrainingDatabase): List<String> {
        val programs = db.programDao().allPrograms().associateBy(TrainingProgram::id)
        val exercises = db.exerciseDao().allExercises().associateBy(Exercise::id)
        return buildList {
            programs.values.sortedBy(TrainingProgram::stableKey).forEach { program ->
                add(
                    listOf(
                        "program",
                        program.stableKey,
                        program.name,
                        program.durationDays,
                        program.createdAt,
                        program.updatedAt,
                        program.goal,
                        program.weeklyTrainingDays,
                        program.sessionMinutes,
                        program.availableEquipment,
                        program.excludedExerciseText,
                        program.badmintonTransferRatio,
                        program.sportStrengthRatio,
                        program.periodizationType
                    ).joinToString("|")
                )
            }
            db.programDao().allProgramItems().forEach { item ->
                add(
                    listOf(
                        "item",
                        programs.getValue(item.programId).stableKey,
                        item.weekNumber,
                        item.dayOfWeek,
                        item.orderIndex,
                        exercises.getValue(item.exerciseId).stableKey,
                        item.exerciseName,
                        item.category,
                        item.restSeconds,
                        item.prescription,
                        item.setCount,
                        item.reps,
                        item.weightKg,
                        item.seconds,
                        item.trainingSlot,
                        item.dayIntensity,
                        item.weightSource
                    ).joinToString("|")
                )
            }
            db.programDao().allProgramTombstones().forEach { tombstone ->
                add("tombstone|${tombstone.programStableKey}|${tombstone.deletedAt}|${tombstone.seedVersion}")
            }
        }
    }
}
