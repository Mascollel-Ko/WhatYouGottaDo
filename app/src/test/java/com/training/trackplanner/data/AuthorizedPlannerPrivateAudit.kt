package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Opt-in, ignored artifacts only. Reflection observes the existing production snapshot authority. */
internal object AuthorizedPlannerPrivateAudit {
    suspend fun snapshot(repository: TrainingRepository, cutoff: LocalDate): PlanningHistorySnapshot {
        fun field(target: Any, name: String) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)!!
        suspend fun call(target: Any, name: String, types: Array<Class<*>>, args: Array<Any>): Any? =
            suspendCoroutineUninterceptedOrReturn { continuation -> target.javaClass.getDeclaredMethod(name, *types, Continuation::class.java)
                .apply { isAccessible = true }.invoke(target, *args, continuation) }
        val service = field(repository, "personalizedProgramPlanningService")
        val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
        val preferences = call(service, "readPreferences", emptyArray(), emptyArray()) as PersonalizedPlanningPreferences
        return call(service, "buildSnapshot", arrayOf(LocalDate::class.java, Map::class.java, PersonalizedPlanningPreferences::class.java),
            arrayOf(cutoff, editor.resolvedRuntimeMetadataByExerciseStableKey(), preferences)) as PlanningHistorySnapshot
    }

    fun write(label: String, plan: GeneratedProgramSkeleton, snapshot: PlanningHistorySnapshot, format: Int) {
        if (plan.personalizedDecision?.frequencyExpansion != null) {
            // Expansion has its own relocation/tissue gate before normal balancing, so the pre-expansion replay is not equivalent.
            writeFrequencyAudit("LIFECYCLE_$label", plan, snapshot)
            return
        }
        val decision = requireNotNull(plan.personalizedDecision)
        val allocation = requireNotNull(decision.authorizedScheduling)
        val residual = requireNotNull(decision.residualCompletion)
        val balance = requireNotNull(decision.dayRebalancing)
        assertEquals("POST_GENERATION_RESIDUAL_COMPLETION", residual.state)
        assertFalse(balance.diagnostic.contains("FAILED"))
        val initialCount = plan.request.weeklyTrainingDays - if (residual.addedDay) 1 else 0
        val schedule = RecordBasedReviewedPolicy.defaultSchedule(plan.request.durationWeeks, initialCount)
        val initialDays = schedule.getValue(1).sorted()
        // Test-only positional identity contract, not semantic key/name inference.
        fun atom(row: ProgramSkeletonItem) = "slot_${initialDays.indexOf(row.dayOfWeek) + 1}_${row.orderIndex - 1}"
        fun local(row: ProgramSkeletonItem, week: Int) = "personalized_${week}_${initialDays.indexOf(row.dayOfWeek) + 1}_${row.orderIndex - 1}_${row.exerciseStableKey}"
        val initialItems = (1..plan.request.durationWeeks).flatMap { week -> allocation.initialWeek.map { row ->
            assertEquals(local(row, 1), row.localId); row.copy(localId = local(row, week), weekNumber = week)
        } }
        val atomMap = initialItems.associate { it.localId to atom(it) }
        val parents = allocation.authorized.associateBy { it.id }
        val sources = allocation.initialWeek.associate { row ->
            val parent = parents.getValue(allocation.origins.getValue(atom(row)).authorizedDemandId)
            assertEquals(parent.item.stableKey, row.exerciseStableKey)
            atom(row) to parent.item.copy(targetSets = row.setCount)
        }
        val authorized = allocation.authorized.map { AuthorizedPrescription(it.id, it.item, it.prescription, it.continuity) }
        val initial = plan.copy(items = initialItems, request = plan.request.copy(weeklyTrainingDays = initialCount), weekDaySchedule = schedule)
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers(decision.userAnswers))
        val replay = ResidualCompletion().complete(initial, snapshot, state, decision.adaptationGaps, authorized,
            requireNotNull(decision.planningBudget?.execution).capacity, atomMap, sources, label != "AUTO", snapshot.planDayProjection, allocation.origins)
        assertEquals("Independent production replay must reproduce CompletedPlan", residual.completedFingerprint, replay.trace.completedFingerprint)
        assertEquals(residual.exactShortfalls, replay.trace.exactShortfalls)
        assertTrue("Never substitute while exact restoration is legally available", replay.trace.additions.all { it.exactExhausted })
        val rebalanced = BoundedDayRebalancer().rebalance(replay, snapshot, state, snapshot.planDayProjection)
        assertEquals(balance.finalFingerprint, rebalanced.trace.finalFingerprint)
        assertEquals(balance.actions, rebalanced.trace.actions)
        val first = plan.items.filter { it.weekNumber == 1 }
        assertTrue(first.sumOf { it.setCount } <= minOf(authorized.sumOf { it.prescription.sets.size }, decision.planningBudget!!.execution!!.capacity.finalControllableUnits))
        val demand = requireNotNull(replay.demand)
        assertEquals(demand.residuals(replay.week!!.items), demand.residuals(first))
        authorized.forEach { parent ->
            val siblings = authorized.filter { it.item.stableKey == parent.item.stableKey }
            val actual = first.filter { it.exerciseStableKey == parent.item.stableKey }
            assertTrue("No authorized key dose inflation", actual.sumOf { it.setCount } <= siblings.sumOf { it.prescription.sets.size })
            if (siblings.size == 1 && residual.exactShortfalls.single { it.authorizedDemandId == parent.id }.shortfall == 0) {
                fun content(sets: List<ProgramSetPrescription>) = sets.map { it.copy(setIndex = 0).toString() }.sorted()
                assertEquals(content(parent.prescription.sets), content(actual.flatMap { it.setPrescriptions }))
                assertTrue(actual.all { it.restSeconds == parent.prescription.restSeconds && it.weightSource == parent.prescription.weightSource })
            }
        }
        residual.exactShortfalls.forEach { exact ->
            assertEquals(allocation.initialWeek.filter { allocation.origins.getValue(atom(it)).authorizedDemandId == exact.authorizedDemandId }.sumOf { it.setCount }, exact.initialMaterialized)
        }
        demand.definitions.forEach { d ->
            val owners = authorized.filter { a -> if (d.unit == PlanningDemandUnit.CONTINUITY_SETS)
                a.continuity && a.item.stableKey == d.key && a.item.styleVariant == d.variant
            else a.item.representedGapCodes.any { code -> code in d.ownerGapCodes &&
                (code != "BADMINTON_FOUNDATIONAL_ONRAMP" || d.objective in a.item.representedObjectives + a.item.supportiveObjectives) } }
            assertEquals(owners.sumOf { d.contribution(snapshot, it.item.stableKey, it.item.styleVariant, it.prescription.sets.size) },
                residual.residuals.single { it.id == d.id }.requested, 1e-9)
        }
        fun item(row: ProgramSkeletonItem) = auditPlannedItem(row).put("role", row.progressionRole.name).put("style", row.progressionStyle)
            .put("variant", row.progressionVariant).put("requiredTemplateAnchor", row.requiredTemplateAnchor)
            .put("coverage", snapshot.movementCoverage(row.exerciseStableKey).name).put("impact", row.jointTendonImpactStressLevel)
        val report = JSONObject().put("label", label).put("backupFormat", format).put("verification", "PASS_REPLAY_EXACT_DOSE_OWNERSHIP_QCR")
            .put("A_input", JSONObject().put("cutoff", decision.historyCutoff).put("weeks", plan.request.durationWeeks)
                .put("days", plan.request.weeklyTrainingDays).put("minutes", plan.request.sessionMinutes).put("strengthIntent", decision.strengthIntent)
                .put("badmintonIntent", decision.badmintonIntent).put("recovery", snapshot.recoverySignals.toString())
                .put("genericCourtLoad", decision.genericCourtLoad).put("constraints", JSONArray(decision.constraints))
                .put("answers", JSONObject(decision.userAnswers)).put("answerAuthority", "TEST_ASSUMPTIONS_NOT_USER_CONFIRMATIONS; historical causes UNKNOWN and frequency UNSURE"))
            .put("B_gaps", JSONArray(decision.adaptationGaps.map { JSONObject().put("code", it.code).put("priority", it.priority)
                .put("reason", it.reason).put("source", it.sourceType).put("representationState", it.representationState?.name) }))
            .put("C_D_authorized_allocation", allocation.toJson().apply {
                getJSONArray("authorized").let { array -> for (i in 0 until array.length()) array.getJSONObject(i).let { row ->
                    row.put("name", snapshot.exercises[row.getString("stableKey")]?.name)
                } }
                put("initialAtoms", JSONArray(allocation.initialWeek.map { row -> item(row).put("atomId", atom(row))
                    .put("authorizedDemandId", allocation.origins.getValue(atom(row)).authorizedDemandId) }))
            })
            .put("E_initial", JSONArray(allocation.initialWeek.map(::item)))
            .put("F_G_H_residual", residual.toJson()).put("I_J_balance", balance.toJson())
            .put("K_final", JSONArray(first.map(::item)))
            .put("L_constraints", JSONObject().put("exact", JSONArray(residual.exactShortfalls.filter { it.shortfall > 0 }.map { it.toJson() }))
                .put("semantic", JSONArray(residual.residuals.filter { it.residual > 0 }.map { it.toJson() }))
                .put("days", JSONArray(balance.finalDays.filter { it.needsBalance || it.standaloneOfi >= 87 }.map { it.toJson() }))
                .put("protected", JSONArray(first.filter { it.progressionRole == ProgressionRole.MAIN || it.requiredTemplateAnchor || it.progressionVariant.isNotBlank() }.map(::item)))
                .put("tissueRestrictedKeys", JSONArray(snapshot.recoverySignals.tissueRestrictedStableKeys.toList())))
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        val directory = File(root, "build/private-audit/authorized-planner").apply { mkdirs() }
        File(directory, "$label.json").writeText(report.toString(2))
        File(directory, "$label.md").writeText(buildString {
            appendLine("# Actual production planner audit: $label")
            appendLine("Private input; fixture answers including UNKNOWN/UNSURE are test assumptions, not user confirmations.")
            appendLine("Production replay, exact-dose, ownership and Q/C/R invariants PASS.")
            listOf("A_input", "B_gaps", "C_D_authorized_allocation", "E_initial", "F_G_H_residual", "I_J_balance", "K_final", "L_constraints").forEach { key ->
                appendLine("\n## $key\n"); appendLine("```json"); appendLine(when (val value = report.get(key)) {
                    is JSONObject -> value.toString(2); is JSONArray -> value.toString(2); else -> value.toString()
                }); appendLine("```")
            }
        })
        println("AUTHORIZED_PRIVATE_AUDIT $label PASS ${File(directory, "$label.json").absolutePath}")
        println(report.toString(2))
    }
}
