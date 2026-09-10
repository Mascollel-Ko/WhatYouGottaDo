package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Private backup and generated programs remain in ignored build/private-audit only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FrequencyExpansionRealBackupTest {
    @Test fun sameCutoffAndAnswersThreeFourFiveDayProductionAudit() = runBlocking {
        val backup = System.getenv("WGTD_REAL_BACKUP_PATH")?.let(::File)
        assumeTrue("WGTD_REAL_BACKUP_PATH not configured", backup?.isFile == true)
        val context: Context = ApplicationProvider.getApplicationContext()
        var fixedAnswers: PersonalizedPlanningAnswers? = null
        // Each run restores the identical backup. Persisted answers from an earlier run must not reveal different follow-up questions.
        for (days in listOf<Int?>(null, 3, 4, 5)) {
          val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
          try {
            val repository = TrainingRepository(db, context)
            repository.importRecordsBackup(Uri.fromFile(requireNotNull(backup)))
            val cutoff = LocalDate.of(2026, 9, 2)
            val historyBefore = db.workoutDao().allEntriesWithSets()
                val request = ProgramSkeletonRequest("실제 백업 교정 검증", ProgramGoal.BODYBUILDING, days ?: 4, 90, emptySet(), "", .5,
                    "AUTO", ProgramPeriodizationType.AUTO, 5)
                val preflight = repository.preparePersonalizedProgram(request, constraints = PersonalizedGenerationConstraints(
                    explicitGoal = request.goal, explicitDurationWeeks = 5, explicitWeeklyTrainingDays = days, explicitSessionMinutes = 90), cutoff = cutoff)
                val answers = fixedAnswers ?: PersonalizedPlanningAnswers(preflight.questions.associate { question -> question.id to when (question.id) {
                    QUESTION_STRENGTH_INTENT -> StrengthIntent.MIXED.name
                    QUESTION_BADMINTON_INTENT -> BadmintonPlanningIntent.ENABLED.name
                    QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                    QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                    else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN" else error(question.id)
                } })
                fixedAnswers = answers
                val stages = mutableListOf<PersonalizedPlannerStage>()
                var lastPercent = 0
                val plan = repository.generatePreparedPersonalizedProgram(preflight, answers, PersonalizedPlannerProgressReporter {
                    stages += it
                    assertTrue("Stage regressed: $lastPercent to ${it.percent}", it.percent >= lastPercent)
                    lastPercent = it.percent
                })
                assertEquals(PersonalizedPlannerStage.FINAL, stages.last())
                assertEquals(days != null && days > plan.personalizedDecision!!.frequencyDemand!!.frequency.algorithmRecommendedDays,
                    PersonalizedPlannerStage.EXPANSION in stages)
                val snapshot = AuthorizedPlannerPrivateAudit.snapshot(repository, cutoff)
                writeFrequencyAudit(days?.let { "${it}_DAY" } ?: "AUTO", plan, snapshot)
                if (days != null) assertEquals(days, plan.request.weeklyTrainingDays)
                assertEquals(historyBefore, db.workoutDao().allEntriesWithSets())
                val saved = repository.saveGeneratedProgram(null, plan)
                assertEquals(plan.request.weeklyTrainingDays, db.programDao().findProgram(saved)!!.weeklyTrainingDays)
                val json = JSONObject(db.appMetaDao().latestByPrefix("${PersonalizedProgramPlanningService.DECISION_PREFIX}%")!!.value)
                assertFalse(json.getBoolean("userEditedAfterGeneration"))
                if (plan.personalizedDecision!!.frequencyExpansion != null) assertTrue(json.has("frequencyExpansion"))
          } finally { db.close() }
        }
    }
}

internal fun writeFrequencyAudit(label: String, plan: GeneratedProgramSkeleton, snapshot: PlanningHistorySnapshot) {
    val decision = requireNotNull(plan.personalizedDecision)
    val provenance = requireNotNull(decision.frequencyDemand)
    val trace = decision.frequencyExpansion
    val first = plan.items.filter { it.weekNumber == 1 }
    val count = first.sumOf { it.setCount }
    if (trace != null) {
        assertTrue(count >= provenance.baseAuthorized.sumOf { it.prescription.sets.size } || trace.diagnostic.startsWith("BASE_"))
        assertEquals(count, trace.actualFinalMaterializedUnits)
        assertTrue(trace.finalExpandedUnits <= trace.mathematicalExpandedTarget - trace.baseAuthorizedUnits)
        assertTrue(trace.expansionAttempts.filter { it.action == "AUTHORIZED" }.zipWithNext().all { (a, b) -> a.rank < b.rank })
        if (trace.finalExpandedUnits > 0) {
            assertTrue(trace.tissueProjection!!.feasible)
            assertEquals(count, trace.unitOrigins.size)
            assertTrue(trace.dayLoads.all { it.second.feasible })
        }
    }
    val tissue = snapshot.planWeekTissueProjection?.evaluate(first, plan.weekPlans.first().targetRpeMax)
    val scheduling = decision.authorizedScheduling
    val splitAudit = scheduling?.authorized.orEmpty().filter { ContinuitySplitPolicy.mandatory(snapshot, it) }.map { parent ->
        val rows = first.filter { scheduling!!.localOrigins[it.localId]?.authorizedDemandId == parent.id }
        val materialized = rows.sumOf { it.setCount }
        val expected = ContinuitySplitPolicy.template(parent.prescription.sets.size, plan.request.weeklyTrainingDays)
        assertTrue(rows.map { it.dayOfWeek }.distinct().size == rows.size)
        if (materialized == parent.prescription.sets.size) assertEquals(expected.sorted(), rows.map { it.setCount }.sorted())
        JSONObject().put("parentId", parent.id).put("stableKey", parent.item.stableKey).put("eligible", true)
            .put("Q", parent.prescription.sets.size).put("C", materialized).put("R", parent.prescription.sets.size - materialized)
            .put("canonicalPartition", JSONArray(expected)).put("chunks", JSONArray(rows.map { it.setCount }))
            .put("days", JSONArray(rows.map { it.dayOfWeek })).put("fundingSource", parent.fundingSource.name)
    }
    val report = JSONObject().put("cutoff", decision.historyCutoff).put("answers", JSONObject(decision.userAnswers))
        .put("answerAuthority", "TEST_ASSUMPTIONS_NOT_USER_CONFIRMATIONS; same UNKNOWN/UNSURE answers as previous audit")
        .put("frequencyDemand", provenance.toJson()).put("frequencyExpansion", trace?.toJson())
        .put("actualFinalUnits", count).put("tissueProjection", tissue?.toJson())
        .put("splitAudit", JSONArray(splitAudit)).put("authorizedScheduling", scheduling?.toJson())
        .put("residualCompletion", decision.residualCompletion?.toJson())
        .put("postSplitReflow", decision.postSplitReflow?.toJson())
        .put("fixedSplitRelocationReview", reviewFixedSplitRelocations(plan, snapshot))
        .put("program", JSONArray(first.map(::auditPlannedItem)))
    val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
    val directory = File(root, "build/private-audit/frequency-expansion").apply { mkdirs() }
    File(directory, "$label.json").writeText(report.toString(2))
    File(directory, "$label.md").writeText(buildString {
        appendLine("# $label actual program\n")
        appendLine("Cutoff ${decision.historyCutoff}; test assumptions, not newly confirmed user preferences.")
        appendLine("Algorithm=${provenance.frequency.algorithmRecommendedDays}; user=${provenance.frequency.resolvedUserDays}; " +
            "BASE=${provenance.baseAuthorized.sumOf { it.prescription.sets.size }}; target=${trace?.mathematicalExpandedTarget}; " +
            "capacity=${decision.planningBudget?.execution?.capacity?.finalControllableUnits}; queue=${provenance.capacityRejected.sumOf { it.remainingUnits }}; " +
            "expansion=${trace?.finalExpandedUnits ?: 0}; rollback=${trace?.rollbackActions?.sumOf { it.units } ?: 0}; final=$count; ${trace?.diagnostic}")
        appendLine("\n|Day|Exercise / stableKey|Sets/bouts|Prescription|Funding / rank / owner|\n|---|---|---|---|---|")
        first.sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex })).forEach { row ->
            val units = trace?.unitOrigins.orEmpty().filter { it.localId == row.localId }
            val owners = units.mapNotNull { unit -> provenance.capacityRejected.firstOrNull { it.originalRank == unit.originalRank } }
            appendLine("|${row.dayOfWeek}|${row.exerciseName} / ${row.exerciseStableKey}|${row.setCount}|${row.prescription.replace('|', '/')}|" +
                "${units.map { it.fundingSource }.distinct().ifEmpty { listOf(PlanningFundingSource.BASE) }} " +
                "${units.mapNotNull { it.originalRank }.distinct()} ${owners.map { it.item.representedGapCodes }}|")
        }
        appendLine("\n|Day|Minutes|Standalone OFI|Tissue/recovery|\n|---|---|---|---|")
        first.groupBy { it.dayOfWeek }.toSortedMap().forEach { (day, rows) -> appendLine("|$day|${rows.sumOf(::plannedSeconds) / 60.0}|" +
            "${snapshot.planDayProjection?.evaluate(rows)?.ofi}|${tissue?.days?.firstOrNull { it.day == day }?.let { "blocked=${it.blockedUnits}; unresolved=${it.unresolvedKeys}" }}|") }
        appendLine("\n## Full deterministic trace\n\n```json\n${report.toString(2)}\n```")
    })
    println("FREQUENCY_REAL $label algorithm=${provenance.frequency.algorithmRecommendedDays} base=${provenance.baseAuthorized.sumOf { it.prescription.sets.size }} " +
        "target=${trace?.mathematicalExpandedTarget} capacity=${decision.planningBudget?.execution?.capacity?.finalControllableUnits} " +
        "queue=${provenance.capacityRejected.sumOf { it.remainingUnits }} extra=${trace?.finalExpandedUnits ?: 0} " +
        "rollback=${trace?.rollbackActions?.sumOf { it.units } ?: 0} final=$count reason=${trace?.diagnostic}")
}
