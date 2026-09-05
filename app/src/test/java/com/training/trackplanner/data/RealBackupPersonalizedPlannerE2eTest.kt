package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.BadmintonPlanningIntent
import com.training.trackplanner.data.personalized.FreeWeightWillingness
import com.training.trackplanner.data.personalized.PersonalizedPlanningAnswers
import com.training.trackplanner.data.personalized.PersonalizedGenerationConstraints
import com.training.trackplanner.data.personalized.QUESTION_BADMINTON_INTENT
import com.training.trackplanner.data.personalized.QUESTION_FREE_WEIGHT
import com.training.trackplanner.data.personalized.QUESTION_STRENGTH_INTENT
import com.training.trackplanner.data.personalized.StrengthIntent
import com.training.trackplanner.data.personalized.personalizedProgramFingerprint
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Opt-in production-route verification. The private backup always stays outside the repository.
 * Run with WGTD_REAL_BACKUP_PATH pointing at an explicit format-12 backup file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RealBackupPersonalizedPlannerE2eTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `real backup restores plans saves edits applies restarts and remains cutoff safe`() = runBlocking {
        val backup = System.getenv("WGTD_REAL_BACKUP_PATH")?.let(::File)
        assumeTrue("WGTD_REAL_BACKUP_PATH is not configured", backup?.isFile == true)
        val sourceFile = requireNotNull(backup)
        val parsed = RecordCsvBackupRestore.parse(sourceFile.readText(Charsets.UTF_8)) as RecordCsvImportData.Restore
        assertEquals(12, parsed.manifest?.formatVersion)

        val firstDb = database()
        val firstRepository = TrainingRepository(firstDb, context)
        try {
            val restored = firstRepository.importRecordsBackup(Uri.fromFile(sourceFile))
            assertEquals(parsed.manifest!!.entityCounts.getValue("workout_entry"), restored.entryCount)
            assertEquals(parsed.setRows.size, restored.setCount)
            val confirmedHistory = firstDb.workoutDao().allEntriesWithSets()
                .filter { row -> row.sets.any(WorkoutSet::confirmed) }
            val latest = confirmedHistory.maxOf { row -> LocalDate.parse(row.entry.date) }
            val request = request()

            val threeDay = generate(firstRepository, request.copy(weeklyTrainingDays = 3), latest)
            val fiveDay = generate(firstRepository, request.copy(weeklyTrainingDays = 5), latest)
            val autoPreflight = firstRepository.preparePersonalizedProgram(request, cutoff = latest)
            val autoPlan = firstRepository.generatePreparedPersonalizedProgram(autoPreflight, PersonalizedPlanningAnswers(
                autoPreflight.questions.associate { question -> question.id to when (question.id) {
                    QUESTION_STRENGTH_INTENT -> StrengthIntent.MIXED.name
                    QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.ENABLED.name
                    QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                    else -> error(question.id)
                } }
            ))
            listOf("AUTO" to autoPlan, "3_DAY" to threeDay, "5_DAY" to fiveDay).forEach { (label, plan) -> printAllocation(label, plan) }
            val auditCatalogue = firstDb.exerciseDao().allExercises().associateBy(Exercise::stableKey)
            listOf("ex_314df428", "ex_4255e429", "ex_421ba24b", "ex_bc84eb7f",
                "ex_c5f4c242", "ex_8e69fc74", "ex_33841b88", "vipr_chop").forEach { key ->
                println("CANDIDATE_AUDIT $key|${auditCatalogue[key]?.name}|${threeDay.personalizedDecision!!.planningBudget!!.execution!!.candidateAudit[key]}")
            }
            val threeTrace = requireNotNull(threeDay.personalizedDecision!!.planningBudget!!.execution)
            val fiveTrace = requireNotNull(fiveDay.personalizedDecision!!.planningBudget!!.execution)
            assertTrue(fiveTrace.capacity.finalControllableUnits >= threeTrace.capacity.finalControllableUnits)
            if (threeTrace.deferredMaterialGaps.values.any { it == "FINITE_CAPACITY" }) {
                assertTrue("More availability must fund deferred useful demand", fiveTrace.capacity.finalControllableUnits > threeTrace.capacity.finalControllableUnits)
            }
            listOf(threeDay, fiveDay).forEach { plan ->
                val budget = plan.personalizedDecision!!.planningBudget!!
                val trace = requireNotNull(budget.execution)
                assertTrue(plan.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { day -> day.sumOf { it.estimatedDurationSeconds } <= plan.request.sessionMinutes * 60 })
                val performance = plan.items.filter { it.weekNumber == 1 && trace.prescriptionSources[it.exerciseStableKey]?.let { source ->
                    source.startsWith("RECENT_PERSONAL_EXECUTION") || source.startsWith("CANONICAL_PROGRAM_") || source.startsWith("REVIEWED_BADMINTON")
                } == true }
                assertTrue("Real history requires more than one isolated performance item: ${trace.candidateAudit}", performance.map { it.exerciseStableKey }.distinct().size >= 2)
                assertTrue(performance.all { it.setCount >= 2 })
                assertTrue("Explicit anti-rotation assistance must actually execute",
                    trace.supportiveGapCodesByStableKey.values.any { "BADMINTON_UNDERREPRESENTED_ANTI_ROTATION" in it })
            }
            val latestPlan = generate(firstRepository, request, latest)
            val fourWeeksAgo = generate(firstRepository, request, latest.minusWeeks(4))
            val eightWeeksAgo = generate(firstRepository, request, latest.minusWeeks(8))
            assertTrue(latestPlan.items.isNotEmpty())
            assertTrue(fourWeeksAgo.items.isNotEmpty())
            assertTrue(eightWeeksAgo.items.isNotEmpty())
            assertEquals(5, latestPlan.request.durationWeeks)
            assertEquals(4, latestPlan.request.weeklyTrainingDays)
            assertEquals(ProgramGoal.BODYBUILDING, latestPlan.request.goal)
            assertEquals((1..5).toSet(), latestPlan.items.map { it.weekNumber }.toSet())

            val oldFingerprint = personalizedProgramFingerprint(fourWeeksAgo.request, fourWeeksAgo.items)
            val exercise = firstDb.exerciseDao().allExercises().first { it.activityKind != "SPORT_SESSION" }
            val futureEntryId = firstDb.workoutDao().insertEntry(
                WorkoutEntry(date = latest.plusDays(1).toString(), exerciseStableKey = exercise.stableKey, exerciseName = exercise.name, category = exercise.category)
            )
            firstDb.workoutDao().insertSet(WorkoutSet(entryId = futureEntryId, setIndex = 1, reps = 50, weightKg = 999.0, confirmed = true))
            val repeatedOld = generate(firstRepository, request, latest.minusWeeks(4))
            assertEquals(oldFingerprint, personalizedProgramFingerprint(repeatedOld.request, repeatedOld.items))

            val beforeCourt = latestPlan.personalizedDecision!!
            val badminton = firstDb.exerciseDao().findByStableKey("ex_ae9ecdbc")!!
            val courtEntryId = firstDb.workoutDao().insertEntry(
                WorkoutEntry(date = latest.toString(), exerciseStableKey = badminton.stableKey, exerciseName = badminton.name, category = badminton.category, rpe = 8.0)
            )
            firstDb.workoutDao().insertSet(WorkoutSet(entryId = courtEntryId, setIndex = 1, seconds = 90 * 60, rpe = 8.0, confirmed = true))
            val afterCourtPlan = generate(firstRepository, request, latest)
            val afterCourt = afterCourtPlan.personalizedDecision!!
            assertTrue(afterCourt.genericCourtLoad > beforeCourt.genericCourtLoad)
            assertEquals(beforeCourt.objectiveExposure, afterCourt.objectiveExposure)
            assertNotEquals(beforeCourt.constraints, afterCourt.constraints)

            val programId = firstRepository.saveGeneratedProgram(null, afterCourtPlan)
            val savedProgram = firstDb.programDao().findProgram(programId)!!
            assertEquals(35, savedProgram.durationDays)
            assertEquals(4, savedProgram.weeklyTrainingDays)
            assertEquals(ProgramGoal.BODYBUILDING.name, savedProgram.goal)
            val decisionBeforeEdit = JSONObject(firstDb.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
            assertEquals(savedProgram.stableKey, decisionBeforeEdit.getString("generatedProgramStableKey"))
            assertFalse(decisionBeforeEdit.getBoolean("userEditedAfterGeneration"))

            val firstItem = firstDb.programDao().itemsForProgram(programId).first()
            firstRepository.updateProgramItem(firstItem.copy(prescription = firstItem.prescription + " · 사용자 메모"))
            val decisionAfterEdit = JSONObject(firstDb.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
            assertTrue(decisionAfterEdit.getBoolean("userEditedAfterGeneration"))
            assertNotEquals(decisionBeforeEdit.getString("finalSavedFingerprint"), decisionAfterEdit.getString("finalSavedFingerprint"))

            val applyDate = latest.plusWeeks(2).toString()
            firstRepository.applyProgramToDates(programId, applyDate, ProgramApplyMode.Append)
            val applied = firstDb.workoutDao().entriesWithSetsBetween(applyDate, latest.plusWeeks(7).toString())
            assertTrue(applied.isNotEmpty())
            assertTrue(applied.flatMap(WorkoutEntryWithSets::sets).none(WorkoutSet::confirmed))

            val roundTrip = File.createTempFile("wgtd-planner-roundtrip", ".csv")
            try {
                firstRepository.exportRecordsBackup(Uri.fromFile(roundTrip))
                val secondDb = database()
                try {
                    val secondRepository = TrainingRepository(secondDb, context)
                    secondRepository.importRecordsBackup(Uri.fromFile(roundTrip))
                    assertNotNull(secondDb.programDao().findProgramByStableKey(savedProgram.stableKey))
                    val restoredDecision = JSONObject(secondDb.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
                    assertTrue(restoredDecision.getBoolean("userEditedAfterGeneration"))
                    assertEquals(savedProgram.stableKey, restoredDecision.getString("generatedProgramStableKey"))
                    assertEquals(decisionAfterEdit.getJSONObject("planningBudget").getJSONObject("execution").toString(),
                        restoredDecision.getJSONObject("planningBudget").getJSONObject("execution").toString())
                } finally {
                    secondDb.close()
                }
            } finally {
                roundTrip.delete()
            }
        } finally {
            firstDb.close()
        }
    }

    private suspend fun generate(repository: TrainingRepository, request: ProgramSkeletonRequest, cutoff: LocalDate): GeneratedProgramSkeleton {
        val preflight = repository.preparePersonalizedProgram(
            request = request,
            constraints = PersonalizedGenerationConstraints(
                explicitGoal = request.goal,
                explicitWeeklyTrainingDays = request.weeklyTrainingDays,
                explicitDurationWeeks = request.durationWeeks,
                explicitSessionMinutes = request.sessionMinutes
            ),
            cutoff = cutoff
        )
        val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question ->
            question.id to when (question.id) {
                QUESTION_STRENGTH_INTENT -> StrengthIntent.MIXED.name
                QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.ENABLED.name
                QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                else -> error("Unexpected personalized question: ${question.id}")
            }
        })
        return repository.generatePreparedPersonalizedProgram(preflight, answers)
    }


    private fun printAllocation(label: String, plan: GeneratedProgramSkeleton) {
        val decision = requireNotNull(plan.personalizedDecision)
        val trace = requireNotNull(decision.planningBudget?.execution)
        println("V012_REAL_BACKUP $label intent=${decision.primaryAdaptation} weeks=${plan.request.durationWeeks} days=${plan.request.weeklyTrainingDays}")
        println(trace.toJson().toString(2))
        println("GAPS " + decision.adaptationGaps.joinToString { "${it.code}:${it.priority}:${it.contributesTransitionPressure}" })
        println("DOMAIN_BUDGET " + decision.planningBudget)
        plan.items.groupBy { it.weekNumber to it.dayOfWeek }.toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }).forEach { (day, items) ->
            println("WEEK/DAY $day items=${items.size} minutes=${items.sumOf { it.estimatedDurationSeconds } / 60.0}")
            items.forEach { item ->
                println("${item.exerciseName}|${item.exerciseStableKey}|slot=${item.metadataProgramSlot}|sets/bouts=${item.setCount}|${item.setPrescriptions}|rest=${item.restSeconds}|seconds=${item.estimatedDurationSeconds}|gaps=${trace.representedGapCodesByStableKey[item.exerciseStableKey]}|source=${item.weightSource}")
            }
        }
    }

    private fun request() = ProgramSkeletonRequest(
        name = "실제 백업 교정 검증",
        goal = ProgramGoal.BODYBUILDING,
        weeklyTrainingDays = 4,
        sessionMinutes = 90,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = .5,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = 5
    )

    private fun database(): TrainingDatabase = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
