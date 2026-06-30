package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCsvBackupRestoreTest {
    @Test
    fun dailyTimeseriesCsvParsesSleepHoursSafely() {
        val csv = """
            date,sleep_hours,body_weight_kg,total_entries,confirmed_entries,planned_entries,total_sets,total_reps,total_tonnage_kg,total_seconds,strength_entries,functional_entries,cardio_entries,sports_entries,max_6corner_per_min,max_smash_per_min,exercises_summary
            2026-06-15,7.5,72.3,2,1,1,3,30,1200,600,1,0,0,0,,,"스쿼트"
            2026-06-16,bad,,1,1,0,1,0,0,1800,0,0,1,0,,,"러닝"
        """.trimIndent()

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.DailyTimeseries

        assertEquals(2, parsed.rows.size)
        assertEquals(7.5, parsed.rows.first().sleepHours ?: 0.0, 0.001)
        assertEquals(null, parsed.rows[1].sleepHours)
        assertEquals(0, parsed.warningCount)
    }

    @Test
    fun restoreCsvKeepsSetConfirmedSeparateFromPlannedSets() {
        val csv = """
            schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,rpe,max_reps,notes,set_index,set_confirmed,reps,weight_kg,seconds,sleep_hours,body_weight_kg
            1,daily,2026-06-15,,,,,,,,,,,,,,,,8,71
            1,set,2026-06-15,e1,1,스쿼트,근력운동,1,120,8,,note,1,1,5,100,0,,
            1,set,2026-06-15,e1,1,스쿼트,근력운동,1,120,8,,note,2,0,5,100,0,,
        """.trimIndent()

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore

        assertEquals(1, parsed.dailyRows.size)
        assertEquals(2, parsed.setRows.size)
        assertTrue(parsed.setRows.first().setConfirmed)
        assertEquals(false, parsed.setRows[1].setConfirmed)
    }

    @Test
    fun restoreExporterWritesDailyAndSetRows() {
        val entry = WorkoutEntry(
            id = 10,
            date = "2026-06-15",
            exerciseId = 1,
            exerciseName = "스쿼트",
            category = "근력운동",
            restSeconds = 120,
            notes = "memo"
        )
        val set = WorkoutSet(
            id = 1,
            entryId = 10,
            setIndex = 1,
            reps = 5,
            weightKg = 100.0,
            confirmed = true,
            rpe = 8.0
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = listOf(WorkoutEntryWithSets(entry, listOf(set))),
            metrics = listOf(DailyMetric(date = "2026-06-15", sleepHours = 7.0, bodyWeightKg = 72.0)),
            exercises = listOf(
                Exercise(
                    id = 1,
                    name = "스쿼트",
                    category = "근력운동",
                    stableKey = "barbell_squat"
                )
            )
        )
        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore

        assertTrue(csv.contains("row_type"))
        assertTrue(csv.contains(",daily,"))
        assertTrue(csv.contains(",set,"))
        assertTrue(csv.contains("memo"))
        assertEquals("barbell_squat", parsed.setRows.single().stableKey)
    }

    @Test
    fun restoreCsvExportsAndParsesExerciseMasterRows() {
        val exercise = Exercise(
            id = 1,
            name = "Test Squat",
            category = "근력운동",
            stableKey = "test_squat",
            description = "basic test exercise",
            imageAssetName = "exercise_images/local_downloads/test_squat.png",
            primaryMuscles = "QUADRICEPS,GLUTE",
            equipment = "BARBELL",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            metadataConfidence = "HIGH"
        )

        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(exercise)
        )
        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore

        assertTrue(csv.contains(",exercise,"))
        assertEquals(1, parsed.exerciseRows.size)
        assertEquals("test_squat", parsed.exerciseRows.first().stableKey)
        assertEquals("exercise_images/local_downloads/test_squat.png", parsed.exerciseRows.first().imageAssetName)
    }

    @Test
    fun restoreCsvRoundTripsCustomExerciseRawAndRuntimeMetadata() {
        val exercise = Exercise(
            id = 1,
            name = "내 커스텀 로테이션",
            category = "근력",
            stableKey = "user_ex_custom_rotation",
            primaryMuscles = "OBLIQUE",
            secondaryMuscles = "SHOULDER",
            movementPattern = "ROTATION",
            forceType = "ROTATE",
            bodyRegion = "TRUNK",
            isCustom = true
        )
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            movementFamily = "ROTATIONAL_KINETIC_CHAIN",
            badmintonTransferLevel = "SUPPORTIVE",
            badmintonTransferType = MetadataTokenField.parse("ROTATION_POWER"),
            localMuscularStressLevel = "HIGH",
            safeForSeedMutation = false
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(exercise),
            runtimeMetadata = listOf(metadata)
        )

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        val exerciseRow = parsed.exerciseRows.single()
        val runtimeRow = parsed.runtimeMetadataRows.single()

        assertEquals("OBLIQUE", exerciseRow.primaryMuscles)
        assertEquals("SHOULDER", exerciseRow.secondaryMuscles)
        assertEquals("ROTATION", exerciseRow.movementPattern)
        assertTrue(exerciseRow.isCustom)
        assertEquals("user_ex_custom_rotation", runtimeRow.stableKey)
        assertEquals("ROTATIONAL_KINETIC_CHAIN", runtimeRow.movementFamily)
        assertEquals("SUPPORTIVE", runtimeRow.badmintonTransferLevel)
        assertEquals(listOf("ROTATION_POWER"), runtimeRow.badmintonTransferType.values)
        assertFalse(runtimeRow.safeForSeedMutation)
    }

    @Test
    fun restoreCsvExportsAndParsesInitialProfileRows() {
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            initialProfile = InitialUserProfile(
                bodyWeightKg = 72.5,
                birthYear = 1990,
                sex = "MALE",
                strengthSessionsPerWeek = 3.0,
                strengthTrainingYears = 6.0,
                typicalSleepHours = 7.0,
                usualSleepHours = 7.0,
                painAreaTags = "SHOULDER,LOW_BACK",
                avoidMovementTags = "HEAVY_DEADLIFT",
                primaryGoal = "BADMINTON_PERFORMANCE"
            )
        )

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore

        assertTrue(csv.contains(",profile,"))
        val bodyWeightRow = parsed.profileRows.first { it.key == "bodyWeightKg" }
        assertEquals("72.5", bodyWeightRow.value)
        assertTrue(parsed.profileRows.any { it.key == "sex" && it.value == "MALE" })
        assertTrue(parsed.profileRows.any { it.key == "profileRecoveryScaleDirection" && it.value == "HIGH_IS_GOOD" })
        assertTrue(parsed.profileRows.any { it.key == "birthYear" && it.value == "1990" })
        assertTrue(parsed.profileRows.any { it.key == "painAreaTags" && it.value == "SHOULDER,LOW_BACK" })
        assertTrue(parsed.profileRows.any { it.key == "primaryGoal" && it.value == "BADMINTON_PERFORMANCE" })
    }

    @Test
    fun restoreCsvRoundTripsDailyCheckInAndKeepsNulls() {
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            checkIns = listOf(
                DailyCheckIn(
                    date = "2026-06-23",
                    sleepHours = 6.5,
                    overallFatigue = 4,
                    lowerBodyFatigue = null,
                    jointTendonDiscomfort = 2,
                    focusMotivation = 5,
                    note = "light day"
                )
            )
        )

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        assertEquals(6.5, parsed.dailyRows.single().sleepHours ?: 0.0, 0.001)
        val checkIn = parsed.checkInRows.single()
        assertEquals(null, checkIn.sleepHours)
        assertEquals(4, checkIn.overallFatigue)
        assertEquals(null, checkIn.lowerBodyFatigue)
        assertEquals("light day", checkIn.note)
    }

    @Test
    fun restoreCsvExportsCanonicalSleepOnlyOnceWhenMetricAndCheckInDiffer() {
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = listOf(DailyMetric(date = "2026-06-23", sleepHours = 7.25)),
            checkIns = listOf(
                DailyCheckIn(
                    date = "2026-06-23",
                    sleepHours = 5.0,
                    overallFatigue = 3
                )
            )
        )

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        assertEquals(7.25, parsed.dailyRows.single().sleepHours ?: 0.0, 0.001)
        assertEquals(null, parsed.checkInRows.single().sleepHours)
    }

    @Test
    fun restoreCsvRoundTripsSmashSpeedRows() {
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            smashSpeeds = listOf(
                SmashSpeedRecord(
                    id = 7,
                    date = "2026-06-29",
                    speedKmh = 231.5,
                    attemptIndex = 2,
                    note = "external app"
                )
            )
        )

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        val row = parsed.smashSpeedRows.single()

        assertTrue(csv.contains(",smash_speed,"))
        assertEquals(7L, row.smashSpeedId)
        assertEquals("2026-06-29", row.date)
        assertEquals(231.5, row.speedKmh, 0.001)
        assertEquals(2, row.attemptIndex)
        assertEquals("external app", row.note)
    }

    @Test
    fun invalidSmashSpeedRowsAreSkippedWithWarning() {
        val csv = """
            schema_version,row_type,date,speed_kmh,attempt_index
            3,smash_speed,2026-06-29,0,1
            3,smash_speed,2026-06-29,230,2
        """.trimIndent()

        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore

        assertEquals(1, parsed.smashSpeedRows.size)
        assertEquals(1, parsed.warningCount)
    }
}
