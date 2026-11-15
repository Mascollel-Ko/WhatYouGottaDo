package com.training.trackplanner.data.v22

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.TrainingRepository
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.skeletonFromProgram
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.io.InputStreamReader

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class V22ProgramIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @After
    fun closeDatabases() = databases.forEach(TrainingDatabase::close)

    @Test
    fun `generated plan saves and applies through existing program session and progression paths`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)
        db.exerciseDao().insertExercise(
            Exercise(
                stableKey = "test_curl",
                name = "테스트 컬",
                category = "근력운동",
                defaultRestSeconds = 75,
                activityKind = "STRENGTH",
                planningEligibility = "ELIGIBLE"
            )
        )
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = "2026-10-10",
                exerciseStableKey = "test_curl",
                exerciseName = "테스트 컬",
                category = "근력운동",
                completedAt = 1L
            )
        )
        repeat(8) { index ->
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = index + 1,
                    reps = 10,
                    weightKg = 20.0,
                    rpe = 8.0,
                    confirmed = true,
                    manualWeight = true
                )
            )
        }
        val repository = TrainingRepository(db, context)
        val generated = repository.generateV22Program(
            V22GenerationRequest(
                name = "V2-2 integration",
                weeks = 2,
                days = 1,
                mode = V22Mode.A,
                asOf = LocalDate.of(2026, 10, 11),
                sessionMinutes = 45
            )
        )

        val programId = repository.saveGeneratedProgram(null, generated.skeleton)
        val program = requireNotNull(db.programDao().findProgram(programId))
        val items = db.programDao().itemsForProgram(programId)
        val storedSets = db.programDao().programItemSetsForProgram(programId)
        assertEquals(14, program.durationDays)
        assertEquals(1, program.weeklyTrainingDays)
        assertEquals(45, program.sessionMinutes)
        assertNull(program.canonicalBuilderProtocolVersion)
        assertNull(program.canonicalPlannerRuntimeVersion)
        assertEquals(2, items.size)
        assertEquals(4, storedSets.size)
        assertTrue(storedSets.all { it.weightKg == 20.0 && it.reps == 10 })

        repository.applyProgramToDates(programId, "2026-11-01", ProgramApplyMode.Append)
        val applied = db.workoutDao().entriesWithSetsBetween("2026-11-01", "2026-11-14")
        assertEquals(2, applied.size)
        assertEquals(4, applied.flatMap { it.sets }.size)
        assertTrue(applied.flatMap { it.sets }.all { !it.confirmed && it.weightKg == 20.0 })
        assertEquals(2, db.programProgressionDao().links().size)
        assertEquals(4, db.programProgressionDao().prescriptions().size)
        assertEquals(8, db.workoutDao().entriesWithSets("2026-10-10").single().sets.size)
        assertFalse(requireNotNull(generated.plan).authority.executionMissCarryover)
        assertTrue(requireNotNull(generated.plan).authority.structuralPlanningOnly)
    }

    @Test
    fun `reviewed B candidate materially persists reopens and applies without private fixtures`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build().also(databases::add)
        val exercise = Exercise(
            stableKey = "barbell_back_squat", name = "Back squat", category = "근력운동",
            equipment = "BARBELL", metadataConfidence = "REVIEWED", systemicLoadWeight = 1.0
        )
        db.exerciseDao().insertExercise(exercise)
        val programRequest = ProgramSkeletonRequest(
            name = "review integration", goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
            weeklyTrainingDays = 1, sessionMinutes = 60, availableEquipment = emptySet(),
            excludedExerciseText = "", badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 2
        )
        fun skeleton(setCount: Int): GeneratedProgramSkeleton {
            val prescriptions = List(setCount) { index -> ProgramSetPrescription(
                setIndex = index + 1, reps = 5, weightKg = 0.0, seconds = 0,
                targetRpeMin = null, loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
            ) }
            return GeneratedProgramSkeleton(
                suggestedName = programRequest.name, durationDays = 14, request = programRequest,
                periodizationType = ProgramPeriodizationType.AUTO, weekPlans = emptyList(),
                items = listOf(ProgramSkeletonItem(
                    localId = "w1-d1-squat", weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
                    exerciseStableKey = exercise.stableKey, exerciseName = exercise.name,
                    category = exercise.category, restSeconds = 120, prescription = "review",
                    setCount = setCount, reps = 5, weightKg = 0.0, seconds = 0,
                    selectionReason = "integration", weightSource = "USER_CALIBRATION_REQUIRED",
                    setPrescriptions = prescriptions
                )), planningMetadataJson = "{}"
            )
        }
        val generationRequest = V22GenerationRequest(
            name = programRequest.name, weeks = 2, days = 1, mode = V22Mode.B,
            asOf = LocalDate.of(2026, 11, 14), sessionMinutes = 60,
            blockProgram = BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 2)))
        )
        val outcome = BCoachMultiobjectiveProduction.prepare(
            generationRequest, emptyList(), mapOf(exercise.stableKey to exercise),
            baseline = skeleton(2), candidate = skeleton(3)
        )
        val jointCandidateId = outcome.decision.alternatives.single { it.label == "JOINT_SEED" }.candidateId
        val accepted = requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton, jointCandidateId
        )
        val acceptedSkeleton = requireNotNull(accepted.skeleton)
        assertEquals(3, acceptedSkeleton.items.single().setCount)
        val calibratedSkeleton = acceptedSkeleton.copy(items = acceptedSkeleton.items.map { item ->
            item.copy(
                weightKg = 100.0,
                weightSource = "B_BLOCK_USER_OVERRIDE",
                setPrescriptions = item.setPrescriptions.map { set ->
                    set.copy(weightKg = 100.0, loadState = ProgramLoadState.EXPLICIT_LOAD)
                }
            )
        })

        val repository = TrainingRepository(db, context)
        val programId = repository.saveGeneratedProgram(null, calibratedSkeleton)
        val reopened = skeletonFromProgram(repository.programEditorSnapshot(programId))
        assertEquals(3, reopened.items.single().setCount)
        assertTrue(reopened.items.single().setPrescriptions.all {
            it.weightKg == 100.0 && it.loadState == ProgramLoadState.EXPLICIT_LOAD
        })
        assertTrue(reopened.planningMetadataJson.orEmpty()
            .contains("ACCEPTED_AND_PERSISTED"))

        val staleEntryId = db.workoutDao().insertEntry(WorkoutEntry(
            date = "2026-11-13", exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name, category = exercise.category, completedAt = 2L
        ))
        db.workoutDao().insertSet(WorkoutSet(
            entryId = staleEntryId, setIndex = 1, reps = 5, weightKg = 100.0,
            rpe = 8.0, confirmed = true, manualWeight = true
        ))
        val countBeforeRejectedSave = db.programDao().countPrograms()
        val staleSave = runCatching { repository.saveGeneratedProgram(null, calibratedSkeleton) }
            .exceptionOrNull()
        assertEquals("B_COACH_ACCEPTANCE_HISTORY_STALE", staleSave?.message)
        assertEquals(countBeforeRejectedSave, db.programDao().countPrograms())
        repository.saveGeneratedProgram(programId, reopened.copy(
            suggestedName = "review integration renamed",
            request = reopened.request.copy(name = "review integration renamed")
        ))
        assertEquals("review integration renamed", db.programDao().findProgram(programId)?.name)

        repository.applyProgramToDates(programId, "2026-11-16", ProgramApplyMode.Append)
        val applied = db.workoutDao().entriesWithSets("2026-11-16").single()
        assertEquals(exercise.stableKey, applied.entry.exerciseStableKey)
        assertEquals(3, applied.sets.size)
        assertTrue(applied.sets.all {
            !it.confirmed && it.weightKg == 100.0 && it.loadState == ProgramLoadState.EXPLICIT_LOAD
        })
        val applicationAudit = JSONObject(requireNotNull(
            db.programDao().findProgram(programId)?.planningMetadataJson
        )).getJSONObject("bCoachMultiobjectiveDecision").getJSONObject("applicationRecoveryAudit")
        assertEquals("2026-11-16", applicationAudit.getString("planningStartDate"))
        assertEquals("RECALCULATED_FOR_APPLICATION_START", applicationAudit.getString("status"))
        assertTrue(applicationAudit.getJSONObject("scoresByScenario").getDouble("BASE") in 0.0..1.0)
    }

    @Test
    fun `real history A B C programs survive preview edit save apply and requery`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)
        val history = realHistoryRows()
        seedHistory(db, history)
        val repository = TrainingRepository(db, context)
        val asOf = LocalDate.of(2026, 10, 11)
        val inferredDays = V22PythonParityEngine.generateBaseline(
            history,
            asOf,
            "whatyougottado_backup_2026-10-12-v22.csv",
            null
        ).trainingDays
        assertEquals(3, inferredDays)
        val historyBefore = db.workoutDao().entriesWithSetsBetween("2026-06-01", "2026-10-11")
            .flatMap { row -> row.sets.map { set -> listOf(row.entry.date, row.entry.exerciseStableKey, set.reps, set.weightKg, set.rpe) } }

        data class IntegrationCase(
            val mode: V22Mode,
            val weeks: Int,
            val days: Int,
            val focus: String?,
            val startDate: LocalDate
        )
        val cases = listOf(
            IntegrationCase(V22Mode.A, 2, 3, null, LocalDate.of(2027, 1, 4)),
            IntegrationCase(V22Mode.B, 8, inferredDays, null, LocalDate.of(2027, 2, 1)),
            IntegrationCase(V22Mode.C, 12, 5, "barbell_back_squat", LocalDate.of(2027, 6, 7))
        )

        cases.forEach { case ->
            val request = V22GenerationRequest(
                name = "real-${case.mode}-${case.weeks}w-${case.days}d",
                weeks = case.weeks,
                days = case.days,
                mode = case.mode,
                asOf = asOf,
                focusStableKey = case.focus,
                sessionMinutes = 60,
                backupName = "whatyougottado_backup_2026-10-12-v22.csv"
            )
            val generated = repository.generateV22Program(request)
            val comparator = requireNotNull(generated.plan)
            val effectiveRequest = if (case.mode == V22Mode.B) request.copy(weeks = 12) else request
            assertEquals(V22PythonParityEngine.build(history, effectiveRequest), comparator)
            val preview = generated.skeleton
            val effectiveWeeks = if (case.mode == V22Mode.B) 12 else case.weeks
            assertEquals(effectiveWeeks * 7, preview.durationDays)
            assertEquals(effectiveWeeks, comparator.weeks.size)
            assertEquals(V22PythonParityEngine.splitBlocks(effectiveWeeks).size, comparator.blocks.size)
            assertTrue(preview.items.isNotEmpty())

            // This mirrors an allowed preview edit without altering the Python-derived prescription.
            val edited = preview.copy(suggestedName = "${preview.suggestedName} · 검증 편집")
            val programId = repository.saveGeneratedProgram(null, edited)
            val storedProgram = requireNotNull(db.programDao().findProgram(programId))
            val storedItems = db.programDao().itemsForProgram(programId)
            val storedSets = db.programDao().programItemSetsForProgram(programId).groupBy { it.programItemId }
            assertEquals(edited.suggestedName, storedProgram.name)
            assertEquals(effectiveWeeks * 7, storedProgram.durationDays)
            assertEquals(case.days, storedProgram.weeklyTrainingDays)
            assertEquals(edited.items.size, storedItems.size)
            if (case.mode == V22Mode.B) {
                assertTrue(storedProgram.planningMetadataJson.orEmpty().contains("B_BALANCED_12_WEEK_V1"))
                assertTrue(edited.weekPlans.none { it.deloadFlag })
                val reopened = skeletonFromProgram(repository.programEditorSnapshot(programId))
                assertEquals(edited.planningMetadataJson, reopened.planningMetadataJson)
                assertEquals(3, reopened.weekPlans.map { it.weekType.substringBeforeLast('_') }.distinct().size)
                assertTrue(reopened.weekPlans.none { it.deloadFlag })
            }
            edited.items.zip(storedItems).forEach { (expected, actual) ->
                assertEquals(expected.weekNumber, actual.weekNumber)
                assertEquals(expected.dayOfWeek, actual.dayOfWeek)
                assertEquals(expected.orderIndex, actual.orderIndex)
                assertEquals(expected.exerciseStableKey, actual.exerciseStableKey)
                assertEquals(expected.setCount, actual.setCount)
                val actualSets = storedSets[actual.id].orEmpty()
                assertEquals(expected.setPrescriptions.size, actualSets.size)
                expected.setPrescriptions.zip(actualSets).forEach { (expectedSet, actualSet) ->
                    assertEquals(expectedSet.setIndex, actualSet.setIndex)
                    assertEquals(expectedSet.reps, actualSet.reps)
                    assertEquals(expectedSet.weightKg, actualSet.weightKg, 0.0)
                    assertEquals(expectedSet.seconds, actualSet.seconds)
                    assertEquals(expectedSet.loadState, actualSet.loadState)
                    assertNull(actualSet.targetRpeMin)
                }
            }
            if (case.mode == V22Mode.B) {
                assertTrue(storedSets.values.flatten().all {
                    it.weightKg == 0.0 && it.loadState == com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED
                })
            }

            repository.applyProgramToDates(programId, case.startDate.toString(), ProgramApplyMode.Append)
            val endDate = case.startDate.plusDays((effectiveWeeks * 7 - 1).toLong())
            val applied = db.workoutDao().entriesWithSetsBetween(case.startDate.toString(), endDate.toString())
            assertEquals(storedItems.size, applied.size)
            assertTrue(applied.flatMap { it.sets }.all { !it.confirmed && it.targetRpeMin == null })
            val expectedSchedule = storedItems.groupingBy { item ->
                val date = case.startDate.plusDays(((item.weekNumber - 1) * 7L) + item.dayOfWeek - 1L)
                "${date}|${item.exerciseStableKey}|${item.setCount}"
            }.eachCount()
            val actualSchedule = applied.groupingBy { row ->
                "${row.entry.date}|${row.entry.exerciseStableKey}|${row.sets.size}"
            }.eachCount()
            assertEquals(expectedSchedule, actualSchedule)
            assertFalse(comparator.authority.executionMissCarryover)
        }

        val historyAfter = db.workoutDao().entriesWithSetsBetween("2026-06-01", "2026-10-11")
            .flatMap { row -> row.sets.map { set -> listOf(row.entry.date, row.entry.exerciseStableKey, set.reps, set.weightKg, set.rpe) } }
        assertEquals(historyBefore, historyAfter)
    }


    @Test
    fun `new block program with partial recovery survives load calibration and save restore`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build().also(databases::add)
        seedHistory(db, realHistoryRows())
        val repository = TrainingRepository(db, context)
        val layout = BBlockProgramDefinition(
            blocks = listOf(
                BBlockDefinition(weeks = 3, goal = BBlockGoal.HYPERTROPHY, pattern = BVolumePattern.WAVE),
                BBlockDefinition(weeks = 3, goal = BBlockGoal.STRENGTH,
                    pattern = BVolumePattern.PROGRESSIVE,
                    relationToPrevious = BBlockRelation.COMPLEMENT_PLANNED_GAPS)
            ),
            recoveryDaysBetween = listOf(3)
        )
        val generated = repository.generateV22Program(newBBalancedGenerationRequest(
            name = "B-block partial recovery",
            days = 3,
            asOf = LocalDate.of(2026, 10, 11),
            sessionMinutes = 60,
            allowFourthDay = false,
            importance = BBalancedGoalImportance(),
            blockProgram = layout
        ))
        val proposal = requireNotNull(generated.bCoachMultiobjectiveProposal) {
            "Expected the joint whole-block candidate to require explicit review"
        }
        val acceptance = proposal.accept(
            requireNotNull(generated.bCoachMultiobjectiveDecision?.proposalFingerprint),
            generated.skeleton
        )
        assertTrue(acceptance.accepted)
        val initial = requireNotNull(acceptance.skeleton)
        assertTrue(initial.planningMetadataJson.orEmpty()
            .contains("ACCEPTED_AFTER_EXPLICIT_USER_REVIEW"))
        assertEquals(BBalanced12WeekEngine.BLOCK_SCHEMA, generated.balanced12WeekPlan?.schema)
        assertEquals(45, initial.durationDays)
        assertEquals(7, initial.request.durationWeeks)
        assertEquals(7, initial.weekPlans.size)
        assertEquals((1..7).toList(), initial.weekPlans.map { it.weekIndex })
        assertTrue(initial.items.all { it.weekNumber in 1..7 && it.dayOfWeek in 1..7 })
        assertTrue(initial.items.any { it.weekNumber == 4 && it.dayOfWeek == 4 })
        assertFalse(initial.items.any { it.weekNumber == 4 && it.dayOfWeek <= 3 })
        assertTrue(initial.items.filter { it.weekNumber<=3 }
            .groupBy { it.dayOfWeek to it.exerciseStableKey }.values.any { rows ->
                rows.map { row -> row.setPrescriptions.map { set -> listOf(set.reps,set.seconds) } }.distinct().size>1
            })
        assertTrue(initial.items.any { it.prescription.contains("시작 중량 입력 후 실제 중량에 반영") })

        val firstSecondBlockSlot = BBlockCalendar(layout).slot(1, 0, 1)
        val anchor = initial.items.first {
            it.weekNumber == firstSecondBlockSlot.week && it.dayOfWeek == firstSecondBlockSlot.day
        }
        val filled = initial.copy(items = initial.items.map { item ->
            if (item.localId != anchor.localId) item else item.copy(
                weightKg = 100.0,
                setPrescriptions = item.setPrescriptions.map { set ->
                    set.copy(weightKg = 100.0,
                        loadState = com.training.trackplanner.data.ProgramLoadState.EXPLICIT_LOAD)
                }
            )
        })
        val derived = filled.propagateBBlockStartLoad(initial, 1.25)
        val nextWeekSlot = BBlockCalendar(layout).slot(1, 1, 1)
        val next = derived.items.first {
            it.weekNumber == nextWeekSlot.week &&
                it.dayOfWeek == nextWeekSlot.day &&
                it.exerciseStableKey == anchor.exerciseStableKey
        }
        val equipmentKind = org.json.JSONObject(initial.planningMetadataJson!!)
            .getJSONObject("equipmentIncrementKinds")
            .getString(anchor.exerciseStableKey)
        val unitKg = BBlockEquipmentIncrements(1.25, 1.0, 2.5)
            .incrementFor(BEquipmentIncrementKind.valueOf(equipmentKind)) ?: 0.0
        val expectedNext = 100.0 + unitKg
        assertEquals(expectedNext, next.weightKg, 0.001)
        assertTrue(next.setPrescriptions.all { it.weightKg == expectedNext })
        val thirdWeekSlot = BBlockCalendar(layout).slot(1, 2, 1)
        val third = derived.items.first {
            it.weekNumber == thirdWeekSlot.week &&
                it.dayOfWeek == thirdWeekSlot.day &&
                it.exerciseStableKey == anchor.exerciseStableKey
        }
        assertEquals(100.0 + unitKg * 2, third.weightKg, 0.001)

        val id = repository.saveGeneratedProgram(null, derived)
        val reopened = skeletonFromProgram(repository.programEditorSnapshot(id))
        assertEquals(45, reopened.durationDays)
        assertEquals(7, reopened.weekPlans.size)
        assertTrue(reopened.planningMetadataJson.orEmpty()
            .contains("ACCEPTED_AND_PERSISTED"))
        assertTrue(reopened.items.any {
            it.weekNumber == next.weekNumber && it.dayOfWeek == next.dayOfWeek &&
                it.exerciseStableKey == next.exerciseStableKey &&
                it.setPrescriptions.all { set -> set.weightKg == expectedNext }
        })
        val startDate=LocalDate.of(2026,11,2)
        repository.applyProgramToDates(id,startDate.toString(),ProgramApplyMode.Append)
        val nextDate=startDate.plusDays((next.weekNumber-1)*7L+next.dayOfWeek-1L).toString()
        val applied=db.workoutDao().entriesWithSets(nextDate)
            .single { it.entry.exerciseStableKey==next.exerciseStableKey }
        assertTrue(applied.sets.all { it.weightKg==expectedNext &&
            it.loadState==com.training.trackplanner.data.ProgramLoadState.EXPLICIT_LOAD })
    }

    private suspend fun seedHistory(db: TrainingDatabase, history: List<V22HistoryRow>) {
        val latest = history.groupBy(V22HistoryRow::stableKey).mapValues { (_, values) -> values.maxBy(V22HistoryRow::date) }
        db.exerciseDao().insertAll(latest.values.map { row ->
            Exercise(
                stableKey = row.stableKey,
                name = row.exerciseName,
                category = row.category,
                activityKind = if (row.category == "근력운동") "STRENGTH" else "FUNCTIONAL",
                planningEligibility = "ELIGIBLE"
            )
        })
        history.groupBy { row -> listOf(row.date.toString(), row.stableKey, row.exerciseName, row.category) }
            .forEach { (identity, values) ->
                val entryId = db.workoutDao().insertEntry(
                    WorkoutEntry(
                        date = identity[0],
                        exerciseStableKey = identity[1],
                        exerciseName = identity[2],
                        category = identity[3],
                        completedAt = 1L
                    )
                )
                values.forEachIndexed { index, row ->
                    db.workoutDao().insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = index + 1,
                            reps = row.reps?.toInt() ?: 0,
                            weightKg = row.weightKg ?: 0.0,
                            seconds = row.seconds?.toInt() ?: 0,
                            confirmed = true,
                            manualWeight = (row.weightKg ?: 0.0) > 0.0,
                            rpe = row.rpe
                        )
                    )
                }
            }
    }

    private fun realHistoryRows(): List<V22HistoryRow> = GZIPInputStream(
        (javaClass.classLoader?.getResourceAsStream(
            "v22-real-history/whatyougottado_backup_2026-10-12-v22.csv.gz"
        ) ?: run {
                assumeTrue("Private historical training fixture removed from public repository", false)
                error("Fixture unavailable")
            })
    ).use { input -> V22HistoryInput.fromBackupCsv(InputStreamReader(input, Charsets.UTF_8)) }
}
