package com.training.trackplanner.data.v22

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.TrainingRepository
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.reconcileProgression
import com.training.trackplanner.data.withResolvedWeekDaySchedule
import com.training.trackplanner.skeletonFromProgram
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BCoachProductionQualityHarnessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After fun close() {
        database?.close()
    }

    private data class Fixture(
        val id: String,
        val weeks: Int,
        val days: Int,
        val minutes: Int,
        val sportImportance: Int,
        val fixedDays: Set<Int>? = null,
        val fullEquipment: Boolean = true
    )

    @Test
    fun `six deterministic production fixtures expose full candidates and persist end to end`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build().also { database = it }
        val exercises = SeedData.exercises(context)
        db.exerciseDao().insertAll(exercises)
        db.initialUserProfileDao().upsert(InitialUserProfile(squatKg = 170.0, deadliftKg = 210.0))
        val repository = TrainingRepository(db, context)
        val allEquipment = exercises.map { it.equipment }
            .filter(String::isNotBlank).toSet() + setOf("BODYWEIGHT", "맨몸", "셔틀콕", "코트", "콘", "마커")
        val fixtures = listOf(
            Fixture("F1_4W_4D_60M_SPORT1", 4, 4, 60, 1),
            Fixture("F2_4W_4D_60M_SPORT5", 4, 4, 60, 5),
            Fixture("F3_12W_3D_60M", 12, 3, 60, 5),
            Fixture("F4_4W_FIXED_60M", 4, 3, 60, 3, setOf(1, 3, 5)),
            Fixture("F5_4W_3D_30M_BODYWEIGHT", 4, 3, 30, 3, fullEquipment = false),
            Fixture("F6_4W_3D_90M", 4, 3, 90, 3)
        )
        val report = buildString {
            appendLine("# B-Coach deterministic production fixtures")
            appendLine()
            appendLine("Cutoff: 2026-11-15 | Profile PR: squat 170kg, deadlift 210kg")
            appendLine()
        }
        var mutableReport = report
        var persisted = false
        val selectedSportKeys = linkedMapOf<String, Set<String>>()
        val supportRosterVariety = linkedMapOf<String, Int>()
        val taskCoverage = linkedMapOf<String, Int>()
        val sportDiagnosticsByFixture = linkedMapOf<String, List<String>>()
        fixtures.forEach { fixture ->
            val blocks = if (fixture.weeks == 12) {
                BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 6), BBlockDefinition(weeks = 6)))
            } else BBlockProgramDefinition(listOf(BBlockDefinition(weeks = fixture.weeks)))
            val request = newBBalancedGenerationRequest(
                name = fixture.id, days = fixture.days, asOf = LocalDate.of(2026, 11, 15),
                sessionMinutes = fixture.minutes, allowFourthDay = false,
                importance = BBalancedGoalImportance(3, 3, 3, fixture.sportImportance),
                blockProgram = blocks, fixedWeekdays = fixture.fixedDays,
                availableEquipment = if (fixture.fullEquipment) allEquipment else setOf("BODYWEIGHT", "맨몸")
            )
            val generated = repository.generateV22Program(request)
            val rawCandidate = generated.balanced12WeekPlan?.toSkeleton(
                request, exercises.associateBy { it.stableKey }
            )
            val proposal = generated.bCoachMultiobjectiveProposal ?: error(
                "${fixture.id}: ${generated.bCoachMultiobjectiveDecision?.status} " +
                    generated.bCoachMultiobjectiveDecision?.hardConstraints.orEmpty() + " items=" +
                    generated.skeleton.items.joinToString { item ->
                        "${item.weekNumber}/${item.dayOfWeek}/${item.orderIndex}/${item.exerciseStableKey}/" +
                            "${item.setCount}:${item.setPrescriptions.size}/${item.selectionReason}"
                    } + " candidate=" + rawCandidate?.items?.joinToString { item ->
                        "${item.weekNumber}/${item.dayOfWeek}/${item.orderIndex}/${item.exerciseStableKey}/" +
                            "${item.setCount}:${item.setPrescriptions.size}"
                    }
            )
            val decision = proposal.decision
            assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, decision.status)
            assertTrue(decision.alternatives.isNotEmpty())
            decision.alternatives.forEach { alternative ->
                val plan = requireNotNull(proposal.candidateForReview(alternative.candidateId))
                assertEquals(fixture.weeks, plan.request.durationWeeks)
                assertEquals((1..fixture.weeks).toSet(), plan.items.map { it.weekNumber }.toSet())
                assertTrue("${fixture.id}/${alternative.candidateId}: main sets", plan.items
                    .filter { it.selectionRole.contains("MAIN", ignoreCase = true) &&
                        listOf("RECOVERY", "PEAK", "TEST", "DRILL").none { marker ->
                            marker in (it.category + " " + it.selectionRole).uppercase()
                        } }
                    .all { it.setCount >= 3 })
                if (fixture.days >= 3 && fixture.fixedDays == null) {
                    assertTrue("${fixture.id}/${alternative.candidateId}: clustered weekdays", plan.items
                        .groupBy { it.weekNumber }.values.all { weekItems ->
                            BCoachV2DoseEvaluator.longestConsecutiveRun(
                                weekItems.mapTo(linkedSetOf()) { it.dayOfWeek }
                            ) < 3
                        })
                }
                val acceptance = proposal.accept(
                    requireNotNull(decision.proposalFingerprint), generated.skeleton.withResolvedWeekDaySchedule(),
                    alternative.candidateId
                )
                assertTrue("${fixture.id}/${alternative.candidateId}: ${acceptance.reason}", acceptance.accepted)
            }
            val selectedId = requireNotNull(decision.recommendedCandidateId)
            val current = generated.skeleton.withResolvedWeekDaySchedule().reconcileProgression(
                generated.skeleton.items.map { it.exerciseStableKey }.toSet()
            )
            val accepted = requireNotNull(proposal.accept(
                requireNotNull(decision.proposalFingerprint), current, selectedId
            ).skeleton)
            val selected = requireNotNull(proposal.candidateForReview(selectedId))
            val selectedAlternative = decision.alternatives.single { it.candidateId == selectedId }
            val auditRoot = JSONObject(generated.skeleton.planningMetadataJson ?: "{}")
            val sportDiagnostics = buildList {
                val audits = auditRoot.optJSONArray("coachWholeBlockAudit")
                for (index in 0 until (audits?.length() ?: 0)) {
                    val rows = audits!!.getJSONObject(index).optJSONArray("candidateDiagnostics")
                    for (row in 0 until (rows?.length() ?: 0)) add(rows!!.getString(row))
                }
            }.distinct()
            sportDiagnosticsByFixture[fixture.id] = sportDiagnostics
            selectedSportKeys[fixture.id] = sportDiagnostics.filter { ":FINAL=SELECTED" in it }
                .mapTo(linkedSetOf()) { it.removePrefix("SPORT_CANDIDATE:").substringBefore(':') }
            val weeklyDays = selected.items.groupBy { it.weekNumber }.toSortedMap().mapValues { (_, items) ->
                items.mapTo(sortedSetOf()) { it.dayOfWeek }
            }
            val oneSetCount = selected.items.count { ProgramSetPrescriptionResolver.resolve(it).size == 1 }
            val exerciseByKey = exercises.associateBy { it.stableKey }
            assertTrue("${fixture.id}: test-only exercise automatically prescribed", selected.items
                .filter { item ->
                    selected.weekPlans.none { week ->
                        week.weekIndex == item.weekNumber && "PEAK" in week.weekType
                    }
                }.none { item ->
                    exerciseByKey[item.exerciseStableKey]?.let(BCoachV2Policy::isTestOnlyExercise) == true
                })
            // Evaluate the actual approved candidate by week. A high SPORT
            // priority must not just copy the same complete plan each week.
            val sportObjectiveRows = decision.effectiveSetBreakdown
                .filter { it.startsWith("SPORT:") }
            val coveredObjectives = sportObjectiveRows.mapNotNull { row ->
                val value = Regex("dose=([^|]+)").find(row)?.groupValues?.get(1)?.toDoubleOrNull()
                if (value != null && value > 0.0) row.substringBefore("|w=") else null
            }.toSet()
            taskCoverage[fixture.id] = coveredObjectives.size
            val supportByWeek = selected.items.groupBy { it.weekNumber }.toSortedMap()
                .mapValues { (_, items) ->
                    items.filter { item ->
                        item.category == "기능성운동" ||
                            item.selectionRole.contains("SPORT", ignoreCase = true)
                    }.map { it.exerciseStableKey }.toSortedSet()
                }
            supportRosterVariety[fixture.id] = supportByWeek.values.toSet().size
            val totalItems = selected.items.size.coerceAtLeast(1)
            assertEquals("${fixture.id}: unexpected one-set exercises", 0, oneSetCount)
            assertTrue("${fixture.id}: one-set hard constraint", decision.hardConstraints.any {
                it.id == "NORMAL_TRAINING_MIN_TWO_SETS" &&
                    it.status == BCoachConstraintStatus.PASS
            })
            mutableReport += buildString {
                appendLine("## ${fixture.id}")
                appendLine()
                appendLine("| weeks | days | minutes | sport | alternatives | selected |")
                appendLine("|---:|---:|---:|---:|---:|---|")
                appendLine("| ${fixture.weeks} | ${fixture.days} | ${fixture.minutes} | ${fixture.sportImportance} | ${decision.alternatives.size} | $selectedId |")
                appendLine()
                appendLine("Weekdays: ${weeklyDays.entries.joinToString("; ") { "W${it.key}=${it.value}" }}  ")
                appendLine("Longest cyclic run: ${weeklyDays.values.maxOfOrNull(BCoachV2DoseEvaluator::longestConsecutiveRun) ?: 0}; " +
                    "one-set items: $oneSetCount/${selected.items.size} (${"%.1f".format(100.0 * oneSetCount / totalItems)}%).  ")
                appendLine("Selected losses: goal deficit ${"%.4f".format(selectedAlternative.axisScores[BCoachObjectiveAxis.GOAL_DEFICIT])}, " +
                    "recovery ${"%.4f".format(selectedAlternative.axisScores[BCoachObjectiveAxis.RECOVERY_LOAD])}, " +
                    "robust regret ${"%.4f".format(selectedAlternative.robustRegret)}.")
                appendLine()
                appendLine("| candidate | recommended | weekly sets | Pareto | regret | changes / reasons |")
                appendLine("|---|---|---:|---|---:|---|")
                decision.alternatives.forEach { alternative ->
                    appendLine("| ${alternative.candidateId} | ${alternative.recommended} | ${"%.1f".format(alternative.weeklySetAverage)} | " +
                        "${alternative.paretoFrontier} | ${"%.4f".format(alternative.robustRegret)} | " +
                        "${(alternative.changeSummary + alternative.reasonCodes).joinToString("; ").ifBlank { "none" }} |")
                }
                appendLine()
                appendLine("Sport recruitment: ${sportDiagnostics.joinToString("; ").ifBlank { "none recorded" }}")
                appendLine()
                appendLine("SPORT goal coverage: ${coveredObjectives.size} distinct objectives with nonzero planned dose.")
                appendLine("SPORT/support rosters by week: ${supportByWeek.entries.joinToString("; ") {
                    "W${it.key}=${it.value}"
                }}")
                appendLine("SPORT objective doses:")
                sportObjectiveRows.forEach { appendLine("  ${it}") }
                appendLine()
                appendLine("| week/day | exercise | stableKey | prescription | per-set load | source | target / expected RIR | rest |")
                appendLine("|---|---|---|---|---|---|---|---:|")
                selected.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
                    .forEach { item ->
                        val sets = ProgramSetPrescriptionResolver.resolve(item)
                        val loads = sets.joinToString("; ") { set -> when {
                            set.loadState == ProgramLoadState.NOT_APPLICABLE ->
                                "S${set.setIndex} non-load"
                            set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && set.estimatedLoadKg != null ->
                                "S${set.setIndex} ~${"%.1f".format(set.estimatedLoadKg)}kg"
                            set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED ->
                                "S${set.setIndex} calibration"
                            else -> "S${set.setIndex} ${"%.1f".format(set.weightKg)}kg"
                        } }
                        val prescription = sets.joinToString("; ") { set ->
                            "S${set.setIndex} " + if (set.seconds > 0) "${set.seconds}s" else "${set.reps} reps"
                        }
                        val sources = sets.map { it.loadSource.name }.distinct().joinToString(" → ")
                        val effort = sets.joinToString("; ") { set ->
                            val target = set.targetRpeMin?.let { "%.1f".format(10.0 - it) } ?: "?"
                            val expected = set.expectedRir?.let { "%.1f".format(it) } ?: "?"
                            "S${set.setIndex} $target/$expected"
                        }
                        appendLine("| W${item.weekNumber}/D${item.dayOfWeek} | ${item.exerciseName} | ${item.exerciseStableKey} | " +
                            "$prescription | $loads | $sources | $effort | ${item.restSeconds}s |")
                    }
                appendLine()
            }
            if (!persisted) {
                val programId = repository.saveGeneratedProgram(null, accepted)
                val reopened = skeletonFromProgram(repository.programEditorSnapshot(programId))
                assertEquals(accepted.items.size, reopened.items.size)
                repository.applyProgramToDates(programId, "2026-11-16", ProgramApplyMode.Append)
                assertTrue(db.workoutDao().entriesWithSetsBetween("2026-11-16", "2027-02-28").isNotEmpty())
                assertNotNull(db.programDao().findProgram(programId))
                persisted = true
            }
        }
        assertTrue(persisted)
        // A non-identical weekly support roster is a product-quality contract,
        // not a hard constraint on fixed main resistance exercises.
        val highSportFixtures = listOf("F2_4W_4D_60M_SPORT5", "F3_12W_3D_60M")
        highSportFixtures.forEach { id ->
            assertTrue("$id: must contain multiple weekly support rosters",
                (supportRosterVariety[id] ?: 0) >= 2)
            assertTrue("$id: must address more than one badminton objective",
                (taskCoverage[id] ?: 0) >= 2)
        }
        val lowSport = selectedSportKeys.getValue("F1_4W_4D_60M_SPORT1")
        val highSport = selectedSportKeys.getValue("F2_4W_4D_60M_SPORT5")
        assertTrue("sport importance must change selected sport support", highSport != lowSport)
        val trackedSportKeys = setOf(
            "lateral_bound_continuous", "ex_34e7d21", "ex_4255e429", "ex_421ba24b", "ex_bc84eb7f"
        )
        assertTrue(sportDiagnosticsByFixture.getValue("F2_4W_4D_60M_SPORT5").let { diagnostics ->
            trackedSportKeys.all { key -> diagnostics.any { it.startsWith("SPORT_CANDIDATE:$key:") } }
        })
        assertTrue(sportDiagnosticsByFixture.getValue("F5_4W_3D_30M_BODYWEIGHT").any {
            trackedSportKeys.any { key -> it.startsWith("SPORT_CANDIDATE:$key:") } &&
                "EQUIPMENT=EQUIPMENT_UNAVAILABLE" in it
        })
        val output = File("build/reports/b-coach/b_coach_generation_fixtures.md")
        output.parentFile?.mkdirs()
        output.writeText(mutableReport)
        assertTrue(output.isFile && output.readText().contains("F6_4W_3D_90M"))
    }
}
