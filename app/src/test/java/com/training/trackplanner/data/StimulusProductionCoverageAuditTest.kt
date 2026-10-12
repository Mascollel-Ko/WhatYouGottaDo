package com.training.trackplanner.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import kotlin.math.ln

/** Unmodified real Room/service results; outcomes are measurements, never success quotas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusProductionCoverageAuditTest {
    internal data class CoverageSpec(
        val label: String, val quality: TrainableQuality, val stableKey: String,
        val profileGoal: String, val goal: ProgramGoal, val intent: StrengthIntent,
        val badminton: Boolean, val history: String, val days: Int, val minutes: Int,
        val equipment: Set<String>, val isolateOwner: Boolean = false,
        val explicitWeeklyDays: Boolean = true, val explicitDuration: Boolean = true
    )

    @Test
    fun rearDeltFlyCannotSatisfyOrOwnUpperPullInProductionGeneration() = runBlocking {
        var planning: CanonicalStimulusPlanningResult? = null
        val result = requireNotNull(runCase(
            CoverageSpec(
                label = "persona0_sparse",
                quality = TrainableQuality.STRENGTH,
                stableKey = "barbell_back_squat",
                profileGoal = "STRENGTH_GAIN",
                goal = ProgramGoal.STRENGTH,
                intent = StrengthIntent.STRENGTH_PRIORITY,
                badminton = false,
                history = "sparse",
                days = 3,
                minutes = 60,
                equipment = setOf("MACHINE", "CABLE")
            ),
            observeCanonicalPlanning = { planning = it }
        ))
        val comparison = requireNotNull(result.comparison)
        val target = requireNotNull(planning).targetPlan.movementTargets.singleOrNull {
            it.movementCoverage == MovementCoverage.UPPER_PULL
        }
        assertNotNull("B4 keeps the existing need decision; the correction changes eligibility, not need", target)

        val illegalFlySelection = comparison.selectionPlan.selectedCandidates.filter { candidate ->
            candidate.stableKey in setOf("cable_rear_delt_fly", "dumbbell_rear_delt_fly", "ex_7deabeba", "ex_a60496be") &&
                "MOVEMENT:UPPER_PULL" in candidate.coveredTargetIds
        }
        assertTrue("a rear-delt isolation cannot be a direct pull owner", illegalFlySelection.isEmpty())
        assertFalse(comparison.prescriptionAuthorizationPlan!!.authorizations.any { authorization ->
            authorization.targetId == "MOVEMENT:UPPER_PULL" && (authorization.owner?.stableKey ?: "") in
                setOf("cable_rear_delt_fly", "dumbbell_rear_delt_fly", "ex_7deabeba", "ex_a60496be")
        })
        assertFalse(comparison.experimental.items.any { item ->
            item.exerciseStableKey in setOf("cable_rear_delt_fly", "dumbbell_rear_delt_fly", "ex_7deabeba", "ex_a60496be") &&
                item.selectionRole == "CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL"
        })
        assertNotNull(comparison.experimentalReadinessAudit)
        assertNotNull(comparison.productionCutoverAuthority)
        val upperPullOwners = comparison.selectionPlan.selectedCandidates.filter {
            "MOVEMENT:UPPER_PULL" in it.coveredTargetIds
        }
        val upperPullAuthorizations = comparison.prescriptionAuthorizationPlan!!.authorizations.filter {
            it.targetId == "MOVEMENT:UPPER_PULL"
        }
        val upperPullMaterializations = comparison.materializationTraces.filter {
            it.targetId == "MOVEMENT:UPPER_PULL"
        }
        val movementDose = target?.regionalDoseTargets.orEmpty()
        val rearDeltCensus = org.json.JSONObject()
            .put("case", "persona0_sparse")
            .put("b4", org.json.JSONObject()
                .put("movementNeed", target?.reasonCodes?.joinToString(",") ?: "NO_TARGET")
                .put("doseTargets", org.json.JSONArray(movementDose.map { dose ->
                    org.json.JSONObject().put("kind", dose.kind.name).put("authority", dose.numericAuthority.name)
                        .put("weeklyTarget", dose.weeklyTarget).put("reasons", org.json.JSONArray(dose.reasonCodes))
                })))
            .put("b5UpperPullOwners", org.json.JSONArray(upperPullOwners.map {
                org.json.JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                    .put("primaryTarget", it.primaryTargetId).put("targets", org.json.JSONArray(it.coveredTargetIds))
            }))
            .put("b6UpperPull", org.json.JSONArray(upperPullAuthorizations.map {
                org.json.JSONObject().put("owner", it.owner?.let { owner -> "${owner.stableKey}#${owner.selectionRole}" })
                    .put("status", it.status.name).put("reasonCodes", org.json.JSONArray(it.reasonCodes))
                    .put("prescription", it.authorizedPrescription?.let { prescription -> prescription.toString() })
            }))
            .put("fundedUpperPull", org.json.JSONArray(comparison.experimental.personalizedDecision?.authorizedScheduling?.authorized.orEmpty()
                .filter { demand -> upperPullOwners.any { it.stableKey == demand.item.stableKey && it.selectionRole == demand.item.role } }
                .map { demand -> org.json.JSONObject().put("demandId", demand.id)
                    .put("stableKey", demand.item.stableKey).put("selectionRole", demand.item.role)
                    .put("sets", demand.prescription.sets.size).put("prescription", demand.prescription.text) }))
            .put("materializedUpperPull", org.json.JSONArray(upperPullMaterializations.map {
                org.json.JSONObject().put("stableKey", it.selectedStableKey).put("selectionRole", it.selectionRole)
                    .put("selectedAtB5", it.selectedAtB5).put("finalWeeklyOccurrences", it.finalWeeklyOccurrences)
                    .put("finalTotalSetUnits", it.finalTotalSetUnits).put("directIdentityStillValid", it.directIdentityStillValid)
                    .put("reasonCodes", org.json.JSONArray(it.reasonCodes))
            }))
            .put("finalUpperPullRows", org.json.JSONArray(comparison.experimental.items.filter {
                it.selectionRole == "CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL"
            }.map { item -> org.json.JSONObject().put("stableKey", item.exerciseStableKey)
                .put("week", item.weekNumber).put("day", item.dayOfWeek).put("sets", item.setCount)
                .put("prescriptions", org.json.JSONArray(item.setPrescriptions.map { it.toString() })) }))
            .put("b7", org.json.JSONObject().put("status", comparison.experimentalReadinessAudit!!.status.name)
                .put("reasons", org.json.JSONArray(comparison.experimentalReadinessAudit!!.reasonCodes)))
            .put("b8", org.json.JSONObject().put("status", comparison.productionCutoverAuthority!!.status.name)
                .put("scope", comparison.productionCutoverAuthority!!.scope.name)
                .put("reasons", org.json.JSONArray(comparison.productionCutoverAuthority!!.reasonCodes)))
            .put("b9", org.json.JSONObject().put("source", result.routeDecision.selectedSource.name)
                .put("reasons", org.json.JSONArray(result.routeDecision.reasonCodes)))
            .put("buildAccounting", org.json.JSONObject().put("control", result.buildCounts.controlBuilds)
                .put("experimental", result.buildCounts.experimentalBuilds).put("third", result.buildCounts.thirdBuilds))
        File("build/reports/c38-2-rear-delt-production-case.json").apply {
            parentFile?.mkdirs()
            writeText(rearDeltCensus.toString(2))
        }
        assertEquals(1, result.buildCounts.controlBuilds)
        assertTrue(result.buildCounts.experimentalBuilds <= 1)
        assertEquals(0, result.buildCounts.thirdBuilds)
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertTrue("B8 is diagnostic; the independently validated new generator remains active",
            result.routeDecision.productionRoutingActive)
    }

    @Test
    fun c20SummaryPlacementRowWithoutExactPrescriptionCannotOverwriteCanonicalProgram() = runBlocking {
        val spec = CoverageSpec(
            label = "persona0_mixed",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "mixed",
            days = 2,
            minutes = 60,
            equipment = setOf("MACHINE", "CABLE"),
            explicitWeeklyDays = false
        )
        val result = requireNotNull(runCase(
            spec,
            seedIncumbentPlacementFixture = true,
            afterProduction = { repository, db, generated ->
                assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, generated.routeDecision.selectedSource)
                val programId = requireNotNull(db.programDao().findProgramByStableKey("c20_fixture_${spec.label}")).id
                val beforeRows = db.programDao().itemsForProgram(programId)
                val beforeSets = db.programDao().programItemSetsForProgram(programId)
                val savedItemIds = beforeRows.mapTo(linkedSetOf(), TrainingProgramItem::id)
                val beforeProgression = db.programProgressionDao().items().filter { it.programItemId in savedItemIds }
                val replacementFailure = runCatching {
                    repository.saveGeneratedProgram(programId, generated.program)
                }.exceptionOrNull()
                if (replacementFailure != null) assertTrue(
                    "unapproved replacement is rejected by the persisted-source guard: $replacementFailure",
                    replacementFailure is UnexplainedCanonicalIncumbentMutationException
                )
                assertEquals("saved user rows remain unchanged after preview save", beforeRows,
                    db.programDao().itemsForProgram(programId))
                assertEquals("saved prescriptions remain unchanged after preview save", beforeSets,
                    db.programDao().programItemSetsForProgram(programId))
                assertEquals("saved progression links remain unchanged after preview save", beforeProgression,
                    db.programProgressionDao().items().filter { it.programItemId in savedItemIds })
            }
        ))
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertEquals(1, result.buildCounts.controlBuilds) // Explicit comparison diagnostics only.
    }

    @Test
    fun unavailableStrengthOwnerFinishesWithTypedShortfallAndUserNotice() = runBlocking {
        val spec = CoverageSpec(
            label = "c32_strength_unavailable_equipment",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 3,
            minutes = 60,
            equipment = setOf("MACHINE", "CABLE"),
            isolateOwner = true,
            explicitWeeklyDays = false
        )
        var canonicalPlanning: CanonicalStimulusPlanningResult? = null
        val result = requireNotNull(runCase(spec, observeCanonicalPlanning = { canonicalPlanning = it }))

        assertTrue("B4 must retain the Strength need", canonicalPlanning!!.targetPlan.qualityTargets.any {
            it.quality == TrainableQuality.STRENGTH
        })
        assertTrue(result.strengthShortfalls.any {
            it.reason == StimulusStrengthShortfallReason.NO_ELIGIBLE_STRENGTH_EXERCISE
        })
        assertTrue(result.program.optimizationSummary.notices.any {
            it.code == ProgramUserNoticeCode.STRENGTH_EXPOSURE_SHORTFALL &&
                it.level == ProgramUserNoticeLevel.WARNING
        })
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertNotNull("generation returns the independently built new-generator draft", result.program)
        assertEquals(1, result.buildCounts.controlBuilds)
        assertTrue(result.buildCounts.experimentalBuilds <= 1)
        assertEquals(result.buildCounts.controlBuilds + result.buildCounts.experimentalBuilds,
            result.buildCounts.totalBuildInvocations)
        assertEquals(0, result.buildCounts.thirdBuilds)
        assertFalse("an unavailable Strength owner must not leak into an EXP skeleton",
            result.comparison?.experimental?.items.orEmpty().any { item ->
                item.selectionRole.startsWith("CANONICAL_STIMULUS_QUALITY_STRENGTH") &&
                    item.setPrescriptions.any {
                        plannedTargetSetStimulusClass(item.exerciseStableKey, it.reps, item.selectionRole, TrainableQuality.STRENGTH) ==
                            RealizedStimulusClass.STRENGTH_LIKE
                    }
            })
    }

    @Test
    fun measureUnmodifiedServiceCoverage() = runBlocking {
        // Snapshot the small exact-key metadata facts before generating the memory-heavy corpus.
        // The census must not reparse all canonical tissue assets after the production runs.
        val canonicalMetadataRepository = CanonicalExerciseMetadataRepository(ApplicationProvider.getApplicationContext())
        val c18TissueAuthoritySnapshot = C18TissueIncumbentPlacementCensus.captureTissueAuthoritySnapshot(
            canonicalMetadataRepository
        )
        val histories = listOf("none", "sparse", "recent", "reviewed", "mixed")
        val equipment = listOf(setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"), setOf("MACHINE", "CABLE"), emptySet())
        val specs = (0..4).flatMap { persona -> histories.mapIndexed { historyIndex, history ->
            val h = persona == 1
            CoverageSpec(
                label = "persona${persona}_${history}",
                quality = if (h) TrainableQuality.HYPERTROPHY else TrainableQuality.STRENGTH,
                stableKey = if (h) "cable_rear_delt_fly" else "barbell_back_squat",
                profileGoal = when (persona) { 0 -> "STRENGTH_GAIN"; 1 -> "HYPERTROPHY_PHYSIQUE"; else -> "MIXED" },
                goal = when (persona) { 0 -> ProgramGoal.STRENGTH; 1 -> ProgramGoal.BODYBUILDING; 3 -> ProgramGoal.BADMINTON_SUPPORT; else -> ProgramGoal.FUNCTIONAL_CONDITIONING },
                intent = when (persona) { 0 -> StrengthIntent.STRENGTH_PRIORITY; 1 -> StrengthIntent.HYPERTROPHY_PRIORITY; else -> StrengthIntent.MIXED },
                badminton = persona == 3, history = history, days = 2 + (persona + historyIndex) % 4,
                minutes = listOf(30, 60, 90)[(persona + historyIndex) % 3],
                equipment = equipment[(persona * 2 + historyIndex) % 3]
            )
        } } + listOf(
            CoverageSpec("reviewed_strength_isolated", TrainableQuality.STRENGTH, "barbell_back_squat", "STRENGTH_GAIN", ProgramGoal.STRENGTH, StrengthIntent.STRENGTH_PRIORITY, false, "reviewed", 3, 60, emptySet(), true),
            CoverageSpec("reviewed_hypertrophy_isolated", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly", "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY, false, "reviewed", 3, 60, emptySet(), true)
        )
        val canonicalPlanningByCase = linkedMapOf<String, CanonicalStimulusPlanningResult>()
        val productionContextByCase = linkedMapOf<String, PreparedCanonicalGenerationContext>()
        val c29PhaseDurationsByCase = linkedMapOf<String, Map<String, Long>>()
        val c29GenerationDurationsByCase = linkedMapOf<String, Long>()
        val c29PlannerMetricsByCase = linkedMapOf<String, Map<String, Int>>()
        val c30SelectionPlanByCase = linkedMapOf<String, StimulusCandidateSelectionPlan>()
        val c30AuthorizationPlanByCase = linkedMapOf<String, StimulusPrescriptionAuthorizationPlan>()
        val executionFailuresByCase = linkedMapOf<String, StimulusProductionExecutionRejectedException>()
        val executionInputNeedsByCase = linkedMapOf<String, List<String>>()
        val executionBlockersByCase = linkedMapOf<String, String>()
        val executionB6AuthorizationByCase = linkedMapOf<String, Boolean>()
        val executionB4DemandByCase = linkedMapOf<String, Boolean>()
        val records = specs.map { spec ->
            val result = try { runCase(spec, seedIncumbentPlacementFixture = spec.label in
                setOf("persona0_mixed", "persona0_reviewed", "persona3_reviewed", "persona4_mixed"), observeCanonicalPlanning = { planning ->
                canonicalPlanningByCase[spec.label] = planning
            }, evaluateWithIncumbent = { service, preflight, answers, metadata, incumbentIndex ->
                withContext(Dispatchers.IO) {
                    val generationStartedAt = System.nanoTime()
                    val previousPhaseAt = linkedMapOf<ProductionGenerationPhase, Long>()
                    val phaseDurations = linkedMapOf<String, Long>()
                    try {
                        try {
                            service.generatePreparedProduction(
                                preflight = preflight,
                                answers = answers,
                                metadata = metadata,
                                includeComparisonDiagnostics = true,
                                incumbentPlacementIndex = incumbentIndex,
                                productionGenerationObserver = { observation ->
                                val observedAt = System.nanoTime()
                                if (observation.phase == ProductionGenerationPhase.CANONICAL_PREPARED) {
                                    productionContextByCase[spec.label] = observation.context
                                    phaseDurations["generation_start_to_canonical_prepared"] =
                                        (observedAt - generationStartedAt).coerceAtLeast(0L) / 1_000_000L
                                }
                                previousPhaseAt.entries.lastOrNull()?.let { (previous, previousAt) ->
                                    phaseDurations["${previous.name}_to_${observation.phase.name}"] =
                                        (observedAt - previousAt).coerceAtLeast(0L) / 1_000_000L
                                }
                                previousPhaseAt[observation.phase] = observedAt
                                if (observation.phase == ProductionGenerationPhase.B6_POST_MATERIALIZATION_COMPLETE) {
                                    val builder = field(service, "programBuilder") as PersonalizedProgramBuilder
                                    c29PlannerMetricsByCase[spec.label] = builder.lastPerformanceMetrics.toMap()
                                }
                                observation.selectionPlan?.let { c30SelectionPlanByCase[spec.label] = it }
                                observation.authorizationPlan?.let { c30AuthorizationPlanByCase[spec.label] = it }
                                }
                            )
                        } catch (failure: StimulusProductionExecutionRejectedException) {
                            val b4 = canonicalPlanningByCase[spec.label]?.targetPlan?.let { plan ->
                                (plan.qualityTargets.map { "QUALITY:${it.quality}:${it.weeklyDirectUnitsTarget}" } +
                                    plan.movementTargets.map { target ->
                                        "MOVEMENT:${target.movementCoverage}:${target.regionalDoseTargets.map { it.kind to it.weeklyTarget }}"
                                    } + plan.taskTargets.map { "TASK:${it.task}:${it.weeklyDirectUnitsTarget}" }).joinToString("+")
                            }
                            val b5 = c30SelectionPlanByCase[spec.label]?.selectedCandidates.orEmpty()
                                .joinToString("+") { "${it.stableKey}#${it.selectionRole}:${it.coveredTargetIds.sorted()}" }
                            val b6 = c30AuthorizationPlanByCase[spec.label]?.authorizations.orEmpty()
                                .joinToString("+") { authorization ->
                                    val sets = authorization.authorizedPrescription?.sets ?: authorization.inputPrescription?.sets.orEmpty()
                                    "${authorization.targetId}#${authorization.owner?.stableKey}:${authorization.status}:" +
                                        "${authorization.executionAuthority}:sets=${sets.joinToString("/") { set ->
                                            "${set.reps}r:${set.weightKg}kg:${set.loadState}:${set.targetRpeMin}RPE"
                                        }}:reasons=${authorization.reasonCodes.joinToString("/")}"
                                }
                            throw AssertionError("${spec.label} rejected; B4=$b4; B5=$b5; B6=$b6", failure)
                        }
                    } finally {
                        c29GenerationDurationsByCase[spec.label] =
                            (System.nanoTime() - generationStartedAt).coerceAtLeast(0L) / 1_000_000L
                        c29PhaseDurationsByCase[spec.label] = phaseDurations.toMap()
                    }
                }
            }) } catch (failure: AssertionError) {
                var cause: Throwable? = failure
                while (cause != null && cause !is StimulusProductionExecutionRejectedException) cause = cause.cause
                val productionFailure = cause as? StimulusProductionExecutionRejectedException
                val planning = canonicalPlanningByCase[spec.label]
                val hasNumericB4Demand = planning?.targetPlan?.let { plan ->
                    plan.qualityTargets.any { it.weeklyDirectUnitsTarget != null } ||
                        plan.movementTargets.any { movement -> movement.regionalDoseTargets.any { it.weeklyTarget != null } }
                } == true
                val hasExecutableB6 = c30AuthorizationPlanByCase[spec.label]?.authorizations.orEmpty().any {
                    it.authorizedPrescription != null && it.status in setOf(
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                    )
                }
                val conditionalB6Owners = c30AuthorizationPlanByCase[spec.label]?.authorizations.orEmpty()
                    .filter { it.authorizedPrescription == null && it.executionAuthority.name == "CONDITIONAL_ON_UNPERSISTED_EFFORT" }
                    .mapNotNull { it.owner?.let { owner -> "${owner.stableKey}#${owner.selectionRole}" } }
                    .distinct().sorted()
                val b5CandidatesDeniedByB6 = c30SelectionPlanByCase[spec.label]?.traces.orEmpty()
                    .flatMap { trace -> trace.candidateRejectionReasons.map { (stableKey, reason) -> "$stableKey:$reason" } }
                    .filter { it.contains(":B6_REJECTED:") }
                    .distinct().sorted()
                if (
                    productionFailure?.reasonCodes?.contains("NO_EXECUTABLE_PLANNING_DEMAND") == true &&
                    !hasNumericB4Demand && c30SelectionPlanByCase[spec.label]?.selectedCandidates.isNullOrEmpty()
                ) {
                    executionFailuresByCase[spec.label] = requireNotNull(productionFailure)
                    executionB4DemandByCase[spec.label] = false
                    executionB6AuthorizationByCase[spec.label] = hasExecutableB6
                    executionBlockersByCase[spec.label] = "NO_NUMERIC_B4_AUTHORITY"
                    executionInputNeedsByCase[spec.label] = listOf("B4_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE")
                    null
                } else if (
                    productionFailure?.reasonCodes?.contains("NO_EXECUTABLE_PLANNING_DEMAND") == true &&
                    !hasExecutableB6 && b5CandidatesDeniedByB6.isNotEmpty()
                ) {
                    executionFailuresByCase[spec.label] = requireNotNull(productionFailure)
                    executionB4DemandByCase[spec.label] = hasNumericB4Demand
                    executionB6AuthorizationByCase[spec.label] = false
                    executionBlockersByCase[spec.label] = "B6_INPUT_OR_AUTHORITY_REQUIRED"
                    executionInputNeedsByCase[spec.label] = listOf(
                        "B6_USER_EFFORT_OR_LOAD_CALIBRATION_REQUIRED:${b5CandidatesDeniedByB6.joinToString(",")}"
                    )
                    null
                } else if (
                    productionFailure?.reasonCodes?.contains("NO_EXECUTABLE_PLANNING_DEMAND") == true &&
                    !hasExecutableB6 && conditionalB6Owners.isNotEmpty()
                ) {
                    executionFailuresByCase[spec.label] = requireNotNull(productionFailure)
                    executionB4DemandByCase[spec.label] = hasNumericB4Demand
                    executionB6AuthorizationByCase[spec.label] = hasExecutableB6
                    executionBlockersByCase[spec.label] = "B6_INPUT_OR_AUTHORITY_REQUIRED"
                    executionInputNeedsByCase[spec.label] = listOf(
                        "B6_USER_EFFORT_OR_LOAD_CALIBRATION_REQUIRED:${conditionalB6Owners.joinToString(",")}"
                    )
                    null
                } else if (
                    productionFailure != null && hasNumericB4Demand &&
                    "DAY_OFI_OR_RECOVERY_LIMIT_EXCEEDED" in productionFailure.reasonCodes &&
                    productionFailure.detailCodes.any { it.startsWith("DAY_OFI_WEEK_") }
                ) {
                    executionFailuresByCase[spec.label] = productionFailure
                    executionB4DemandByCase[spec.label] = true
                    executionB6AuthorizationByCase[spec.label] = hasExecutableB6
                    executionBlockersByCase[spec.label] = "DAY_OFI_RECOVERY_CAPACITY_UNSATISFIED"
                    executionInputNeedsByCase[spec.label] = emptyList()
                    null
                } else if (
                    productionFailure != null && hasNumericB4Demand &&
                    "ACTUAL_TRAINING_DAYS_MISMATCH" in productionFailure.reasonCodes &&
                    productionFailure.detailCodes.any { it.startsWith("ACTUAL_TRAINING_DAYS_WEEK_") }
                ) {
                    executionFailuresByCase[spec.label] = productionFailure
                    executionB4DemandByCase[spec.label] = true
                    executionB6AuthorizationByCase[spec.label] = hasExecutableB6
                    executionBlockersByCase[spec.label] = "REQUESTED_TRAINING_DAYS_UNFILLED"
                    executionInputNeedsByCase[spec.label] = emptyList()
                    null
                } else throw failure
            }
            if (result == null) {
                if (spec.history == "none") {
                    assertFalse(spec.label in executionFailuresByCase)
                } else {
                    val failure = requireNotNull(executionFailuresByCase[spec.label])
                    when (executionBlockersByCase[spec.label]) {
                        "B6_INPUT_OR_AUTHORITY_REQUIRED" -> {
                            assertTrue(failure.reasonCodes.contains("NO_EXECUTABLE_PLANNING_DEMAND"))
                            assertTrue(executionInputNeedsByCase.getValue(spec.label).single()
                                .startsWith("B6_USER_EFFORT_OR_LOAD_CALIBRATION_REQUIRED:"))
                        }
                        "NO_NUMERIC_B4_AUTHORITY" -> {
                            assertTrue(failure.reasonCodes.contains("NO_EXECUTABLE_PLANNING_DEMAND"))
                            assertTrue(executionInputNeedsByCase.getValue(spec.label).contains("B4_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE"))
                            assertFalse("no numeric B4 authority means no physical set can be fabricated",
                                executionB4DemandByCase.getValue(spec.label))
                        }
                        "DAY_OFI_RECOVERY_CAPACITY_UNSATISFIED" -> {
                            assertTrue(failure.reasonCodes.contains("DAY_OFI_OR_RECOVERY_LIMIT_EXCEEDED"))
                            assertTrue(failure.detailCodes.any { it.startsWith("DAY_OFI_WEEK_") })
                            assertFalse("execution rejection happens before comparison CONTROL is built",
                                c29PhaseDurationsByCase[spec.label].orEmpty().keys.any { it.endsWith("_to_CONTROL_BUILD") })
                            if (spec.label == "persona2_recent") {
                                val benchRejectionRecordedByB5 = c30SelectionPlanByCase[spec.label]?.traces.orEmpty()
                                    .any { trace -> trace.candidateRejectionReasons.any { (stableKey, reason) ->
                                        stableKey == "barbell_bench_press" && reason.startsWith("B6_REJECTED:")
                                    } }
                                val benchNeedsUserLoadCalibration = c30AuthorizationPlanByCase[spec.label]?.authorizations.orEmpty()
                                    .any { authorization -> authorization.owner?.stableKey == "barbell_bench_press" &&
                                        authorization.executionAuthority.name == "REQUIRES_USER_LOAD_INPUT" }
                                assertTrue("the exact bench B6 load limitation is preserved either in its B5 rejection or B6 owner authority",
                                    benchRejectionRecordedByB5 || benchNeedsUserLoadCalibration || failure.detailCodes.any {
                                        it.startsWith("B6_STRENGTH_LOAD_AUTHORITY:QUALITY:STRENGTH#barbell_bench_press#")
                                    })
                                assertFalse("the unprescribable B5 Strength owner cannot re-enter with a generic row",
                                    failure.detailCodes.any {
                                        it.startsWith("ROW_AUDIT_barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH")
                                    })
                            }
                        }
                        "REQUESTED_TRAINING_DAYS_UNFILLED" -> {
                            assertTrue(failure.reasonCodes.contains("ACTUAL_TRAINING_DAYS_MISMATCH"))
                            val actualDayDetail = failure.detailCodes.first { it.startsWith("ACTUAL_TRAINING_DAYS_WEEK_") }
                            assertTrue("empty requested days are explicit", "empty=" in actualDayDetail)
                            assertFalse("rejection happens before optional comparison CONTROL is built",
                                c29PhaseDurationsByCase[spec.label].orEmpty().keys.any { it.endsWith("_to_CONTROL_BUILD") })
                        }
                        else -> throw AssertionError("${spec.label} has an unclassified new-generator failure")
                    }
                }
                return@map spec to null
            }
            requireNotNull(canonicalPlanningByCase[spec.label]) { "${spec.label} missing CONTROL-independent B1-B4 evidence" }
            val comparison = result.comparison
            assertEquals(spec.label, 1, result.buildCounts.controlBuilds)
            assertEquals(spec.label, 0, result.buildCounts.thirdBuilds)
            if (comparison != null) {
                assertEquals(spec.label, 1, result.buildCounts.experimentalBuilds)
                assertEquals(spec.label, 2, result.buildCounts.totalBuildInvocations)
                assertEquals("${spec.label} CONTROL and EXPERIMENTAL must use the same complete request",
                    comparison.control.request, comparison.experimental.request)
                val selected = if (result.routeDecision.productionRoutingActive) comparison.experimental else comparison.control
                assertEquals(selected.copy(
                    incumbentSourceSnapshotToken = result.program.incumbentSourceSnapshotToken,
                    incumbentSaveMutationEvidence = result.program.incumbentSaveMutationEvidence,
                    optimizationSummary = result.program.optimizationSummary
                ), result.program.copy(replacementReview = null))
                assertEquals(spec.label, comparison.selectionPlan.strengthShortfalls, result.strengthShortfalls)
                assertEquals(StimulusProductionMaterialScopeResolver().resolve(comparison), result.diagnostics.scopeResolution?.scope)
            }
            if (result.strengthShortfalls.isNotEmpty()) {
                assertTrue("${spec.label} must disclose an unmet Strength target without changing the selected source",
                    result.program.optimizationSummary.notices.any {
                        it.code == ProgramUserNoticeCode.STRENGTH_EXPOSURE_SHORTFALL &&
                            it.level == ProgramUserNoticeLevel.WARNING
                    })
            }
            assertEquals(!result.routeDecision.productionRoutingActive, result.diagnostics.primaryFallbackStage != null)
            spec to result
        }
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        assertEquals("all 22 production-eligible cases return a new draft or a typed execution/input rejection", 22,
            generated.size + executionFailuresByCase.size)
        assertEquals(executionFailuresByCase.keys, executionInputNeedsByCase.keys)
        assertEquals(executionFailuresByCase.keys, executionBlockersByCase.keys)
        assertEquals(executionFailuresByCase.keys, executionB6AuthorizationByCase.keys)
        assertTrue(generated.all { (_, result) ->
            result.executionValidation?.executable == true &&
                result.routeDecision.selectedSource == StimulusProductionProgramSource.NEW_GENERATOR
        })
        assertTrue("B7 provenance denial remains diagnostic while new output is returned", generated.any { (_, result) ->
            result.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty().contains("CHANGE_PROVENANCE_UNCLOSED") &&
                result.routeDecision.selectedSource == StimulusProductionProgramSource.NEW_GENERATOR
        })
        assertTrue("B8 CONTROL_REQUIRED remains diagnostic while new output is returned", generated.any { (_, result) ->
            result.comparison?.productionCutoverAuthority?.status == StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED &&
                result.routeDecision.selectedSource == StimulusProductionProgramSource.NEW_GENERATOR
        })
        java.io.File("build/reports/c38-3-new-generator-independent-execution-census.json").apply {
            parentFile?.mkdirs()
            writeText(renderNewGeneratorExecutionCensus(records, executionFailuresByCase,
                executionInputNeedsByCase, executionB4DemandByCase, executionBlockersByCase,
                executionB6AuthorizationByCase))
        }
        // Historical CONTROL-versus-EXP removal censuses are no longer execution gates.
        // The C38.3 census above records actual new-generator programs and typed failures;
        // dedicated tests below cover movement semantics, safety, routing, and persisted data.
        Unit
    }

    @Test
    fun c28ControlOnlyPosteriorAccessoryCannotAcquireDirectCoverageOrMaterialize() = runBlocking {
        val spec = CoverageSpec("c9_allocator_boundary", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly",
            "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY,
            false, "reviewed", 3, 60, emptySet(), true, explicitWeeklyDays = false)
        val result = requireNotNull(runCase(spec))
        val owner = StimulusPrescriptionOwnerIdentity("ex_284ecca6", "COVERAGE_POSTERIOR_CHAIN")
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertFalse("a control-only accessory is not materialized in the independently returned program",
            result.program.items.any { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole })
        assertFalse(result.comparison?.experimental?.items.orEmpty().any {
            it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        })
        if (result.comparison == null) {
            assertEquals(0, result.buildCounts.controlBuilds)
        } else {
            val execution = requireNotNull(result.comparison?.experimental?.personalizedDecision?.planningBudget?.execution)
            assertFalse("a control-only accessory is not a direct posterior-chain execution origin",
                execution.materialDemandCandidateOrigins.any {
                    it.owner.stableKey == owner.stableKey && it.owner.selectionRole == owner.selectionRole
                })
            assertFalse("the accessory cannot be selected as a direct posterior-chain owner",
                result.comparison?.selectionPlan?.selectedCandidates.orEmpty().any {
                    it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole &&
                        it.coveredTargetIds.any { target -> target.startsWith("MOVEMENT:") }
                })
            assertFalse("no exact B6 authority or physical row may be manufactured",
                result.comparison?.experimental?.items.orEmpty().any {
                    it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                })
        }
    }

    /** C9 diagnostic: actual service objects, before any attribution/golden changes. */
    private fun renderProvenance(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String = buildString {
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        appendLine("generated=${generated.size} rejected=${records.size - generated.size}")
        appendLine("routes=" + generated.groupingBy { it.second.routeDecision.selectedSource }.eachCount().toSortedMap())
        appendLine("B7 counts=" + generated.flatMap { it.second.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }.groupingBy { it }.eachCount().toSortedMap())
        appendLine("B8 counts=" + generated.flatMap { it.second.comparison?.productionCutoverAuthority?.reasonCodes.orEmpty() }.groupingBy { it }.eachCount().toSortedMap())
        appendLine("C10 origin trace coverage (${renderC10TraceCoverage(records)})")
        val omissions = generated.flatMap { (spec, result) -> result.comparison?.nonSelectionProvenance.orEmpty().map { spec.label to it } }
        appendLine("C11 unique omitted CONTROL owner classifications=" + omissions.groupingBy { it.second.classification }.eachCount().toSortedMap())
        val weeklyOmissions = generated.flatMap { (spec, result) ->
            val comparison = result.comparison ?: return@flatMap emptyList()
            comparison.nonSelectionProvenance.flatMap { omission ->
                comparison.control.items.filter {
                    it.exerciseStableKey == omission.omittedControlOwner.stableKey &&
                        it.selectionRole == omission.omittedControlOwner.selectionRole
                }.map { it.weekNumber }.distinct().map { week -> omission.classification }
            }
        }
        appendLine("C11 removed owner-week classifications=" + weeklyOmissions.groupingBy { it }.eachCount().toSortedMap())
        omissions.sortedWith(compareBy({ it.first }, { it.second.omittedControlOwner.stableKey }, { it.second.omittedControlOwner.selectionRole }))
            .forEach { (caseId, omission) ->
                appendLine("C11 omitted case=$caseId owner=${omission.omittedControlOwner} classification=${omission.classification}")
                omission.targetEvidence.forEach { target ->
                    val d = target.disposition
                    appendLine("  target=${target.targetId} classification=${target.classification} direct=${d.directTargetCandidate} selectionRequired=${d.selectionRequired} status=${d.status} reasons=${d.reasons} selectedInstead=${d.selectedInstead} candidateTuple=${d.candidateRanking} selectedTuple=${d.selectedInsteadRanking} firstDifference=${d.firstDifferingField}")
                }
            }
        generated.singleOrNull { it.first.label == "reviewed_hypertrophy_isolated" }?.second?.comparison?.let { h ->
            listOf("ex_284ecca6", "ex_28347c1f").forEach { key ->
                appendLine("C11 H owner diagnostic stableKey=$key disposition=${h.selectionPlan.candidateDispositionIndex.forStableKey(key)}")
            }
        }
        generated.sortedBy { it.first.label }.forEach { (spec, result) ->
            val c = result.comparison
            if (c == null) {
                appendLine("CASE ${spec.label}")
                appendLine("EXPERIMENTAL failed closed before skeleton materialization: ${result.upstreamFailureReason} ${result.upstreamFailureDetails}")
                appendLine("route=${result.routeDecision.selectedSource} builds=${result.buildCounts}")
                return@forEach
            }
            fun identity(row: ProgramSkeletonItem) = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
            val before = c.control.items.groupBy(::identity)
            val after = c.experimental.items.groupBy(::identity)
            fun prescriptions(rows: List<ProgramSkeletonItem>) = rows.map {
                PlannedPrescription(it.prescription, it.setPrescriptions, it.restSeconds, it.weightSource)
            }
            appendLine("CASE ${spec.label}")
            appendLine("B4=${c.targetPlan}")
            appendLine("B5=${c.selectionPlan.selectedCandidates}")
            appendLine("B6=${c.prescriptionAuthorizationPlan}")
            appendLine("CONTROL owners=${before.keys}")
            appendLine("EXPERIMENTAL owners=${after.keys}")
            appendLine("added=${after.keys - before.keys}")
            appendLine("removed=${before.keys - after.keys}")
            appendLine("shared=${before.keys intersect after.keys}")
            appendLine("prescriptionChanged=" + (before.keys intersect after.keys).filter { prescriptions(before.getValue(it)) != prescriptions(after.getValue(it)) })
            appendLine("B7=${c.experimentalReadinessAudit}")
            appendLine("B8=${c.productionCutoverAuthority}")
            appendLine("route=${result.routeDecision.selectedSource} builds=${result.buildCounts}")
            appendLine("materialization=${c.prescriptionMaterializationAudits}")
            appendLine("realization=${c.prescriptionRealizationPlan}")
            appendLine("selectionTraces=${c.selectionPlan.traces}")
            appendLine("materializationTraces=${c.materializationTraces}")
            appendLine("allocator=${c.experimental.personalizedDecision?.planningBudget?.execution}")
            listOf("CONTROL" to c.control, "EXPERIMENTAL" to c.experimental).forEach { (label, program) ->
                appendLine("$label schedule=${program.weekDaySchedule}")
                program.items.forEach { row ->
                    appendLine("$label row owner=${identity(row)} slot=${row.weekNumber}/${row.dayOfWeek}/${row.orderIndex} sets=${row.setPrescriptions} rest=${row.restSeconds} weightSource=${row.weightSource} prescription=${row.prescription} setCount=${row.setCount} reps=${row.reps} weight=${row.weightKg} seconds=${row.seconds}")
                }
            }
        }
    }

    private fun renderC10TraceCoverage(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String {
        data class Delta(val caseId: String, val comparison: StimulusSelectionProgramComparison, val kind: String,
            val owner: StimulusPrescriptionOwnerIdentity, val week: Int,
            val before: List<ProgramSkeletonItem>, val after: List<ProgramSkeletonItem>)
        val totals = sortedMapOf<String, Int>()
        val exact = sortedMapOf<String, Int>()
        val deltas = mutableListOf<Delta>()
        records.sortedBy { it.first.label }.forEach { (spec, result) -> result?.comparison?.let { comparison ->
            fun byOwnerWeek(rows: List<ProgramSkeletonItem>) = rows.groupBy {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
            }
            val before = byOwnerWeek(comparison.control.items)
            val after = byOwnerWeek(comparison.experimental.items)
            (before.keys + after.keys).distinct().sortedWith(compareBy({ it.first.stableKey }, { it.first.selectionRole }, { it.second }))
                .forEach { (owner, week) ->
                    val oldRows = before[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val newRows = after[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val kind = when {
                        oldRows.isEmpty() && newRows.isNotEmpty() -> "added_owner"
                        oldRows.isNotEmpty() && newRows.isEmpty() -> "removed_owner"
                        oldRows.sumOf { it.setCount } != newRows.sumOf { it.setCount } -> "set_change"
                        oldRows.map { it.prescription to it.setPrescriptions } != newRows.map { it.prescription to it.setPrescriptions } -> "prescription_change"
                        oldRows.map { it.dayOfWeek to it.orderIndex } != newRows.map { it.dayOfWeek to it.orderIndex } &&
                            oldRows.map { it.dayOfWeek } != newRows.map { it.dayOfWeek } -> "placement_move"
                        oldRows.map { it.orderIndex } != newRows.map { it.orderIndex } -> "order_change"
                        else -> null
                    } ?: return@forEach
                    val delta = Delta(spec.label, comparison, kind, owner, week, oldRows, newRows)
                    deltas += delta
                    totals[kind] = (totals[kind] ?: 0) + 1
                }
        } }
        fun stateMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity): Boolean =
            state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
                (state.day == null || state.day == row.dayOfWeek) && (state.order == null || state.order == row.orderIndex) &&
                state.setCount == row.setCount && state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun materialMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity): Boolean =
            state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
                state.setCount == row.setCount && state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun placementMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity,
            includeOrder: Boolean): Boolean = state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
            state.day == row.dayOfWeek && (!includeOrder || state.order == row.orderIndex) && state.setCount == row.setCount &&
            state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun trace(program: GeneratedProgramSkeleton) = program.personalizedDecision?.planningBudget?.execution?.ownerAllocationProvenance.orEmpty()
        fun exactOrigin(delta: Delta, comparison: StimulusSelectionProgramComparison): Boolean {
            val beforeTrace = trace(comparison.control)
            val afterTrace = trace(comparison.experimental)
            return when (delta.kind) {
                "added_owner" -> afterTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.ADDED, OwnerAllocationAction.FREQUENCY_REPLICATED,
                        OwnerAllocationAction.PLACEMENT_ASSIGNED) && delta.after.any { row -> materialMatches(event.after, row, delta.owner) } }
                "removed_owner" -> afterTrace.any { event -> event.owner == delta.owner && event.action == OwnerAllocationAction.REMOVED &&
                    event.after == null && delta.before.any { row -> stateMatches(event.before, row, delta.owner) } }
                "set_change" -> {
                    val events = beforeTrace + afterTrace
                    events.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED) &&
                    delta.before.any { old -> stateMatches(event.before, old, delta.owner) } &&
                    delta.after.any { new -> stateMatches(event.after, new, delta.owner) } } ||
                    events.any { reverse -> reverse.owner == delta.owner &&
                        reverse.action in setOf(OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED) &&
                        delta.after.any { new -> stateMatches(reverse.before, new, delta.owner) } &&
                        delta.before.any { old -> stateMatches(reverse.after, old, delta.owner) } }
                }
                "prescription_change" -> (beforeTrace + afterTrace).any { event -> event.owner == delta.owner &&
                    event.action == OwnerAllocationAction.PRESCRIPTION_CHANGED &&
                    delta.before.any { old -> stateMatches(event.before, old, delta.owner) } &&
                    delta.after.any { new -> stateMatches(event.after, new, delta.owner) } }
                "placement_move" -> beforeTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                        OwnerAllocationAction.ORDER_CHANGED) && delta.before.any { row -> placementMatches(event.after, row, delta.owner, false) } } &&
                    afterTrace.any { event -> event.owner == delta.owner &&
                        event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                            OwnerAllocationAction.ORDER_CHANGED) && delta.after.any { row -> placementMatches(event.after, row, delta.owner, false) } }
                "order_change" -> beforeTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                        OwnerAllocationAction.ORDER_CHANGED) && delta.before.any { row -> placementMatches(event.after, row, delta.owner, true) } } &&
                    afterTrace.any { event -> event.owner == delta.owner &&
                        event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                            OwnerAllocationAction.ORDER_CHANGED) && delta.after.any { row -> placementMatches(event.after, row, delta.owner, true) } }
                else -> false
            }
        }
        val unproven = mutableListOf<String>()
        deltas.forEach { delta ->
            if (exactOrigin(delta, delta.comparison)) exact[delta.kind] = (exact[delta.kind] ?: 0) + 1
            else {
                val decision = delta.comparison.experimental.personalizedDecision
                val selectedAtB5 = delta.owner in delta.comparison.selectionPlan.selectedCandidates.map {
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
                }
                val inFiniteDemand = delta.owner in decision?.frequencyDemand?.candidates.orEmpty().map {
                    StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role)
                }
                val authorizedForScheduling = delta.owner in decision?.authorizedScheduling?.authorized.orEmpty().map {
                    StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role)
                }
                val boundary = when {
                    authorizedForScheduling -> "owner_reached_authorized_scheduling"
                    inFiniteDemand -> "owner_reached_finite_demand_not_authorized_for_scheduling"
                    selectedAtB5 -> "b5_selected_before_finite_demand"
                    else -> "no_exact_owner_in_exp_b5_or_finite_demand"
                }
                unproven += "${delta.caseId}:${delta.kind}:${delta.owner.stableKey}#${delta.owner.selectionRole}:w${delta.week}[$boundary]"
            }
        }
        val categories = listOf("added_owner", "removed_owner", "set_change", "prescription_change", "placement_move", "order_change")
        val counts = categories.joinToString("; ") { kind ->
            val count = totals[kind] ?: 0
            "$kind=$count/${exact[kind] ?: 0}/${count - (exact[kind] ?: 0)}"
        }
        return "$counts; unproven=${unproven.joinToString(",")}"
    }

    @Test
    fun resolvedRequestParityCoversExplicitAndInferredInputs() = runBlocking {
        val cases = listOf(
            CoverageSpec("request_strength_explicit", TrainableQuality.STRENGTH, "barbell_back_squat", "STRENGTH_GAIN",
                ProgramGoal.STRENGTH, StrengthIntent.STRENGTH_PRIORITY, false, "reviewed", 2, 30,
                setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"), explicitWeeklyDays = true, explicitDuration = true),
            CoverageSpec("request_hypertrophy_inferred", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly", "HYPERTROPHY_PHYSIQUE",
                ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY, false, "reviewed", 3, 60,
                setOf("MACHINE", "CABLE"), explicitWeeklyDays = false, explicitDuration = false),
            CoverageSpec("request_mixed_inferred_duration", TrainableQuality.STRENGTH, "barbell_back_squat", "MIXED",
                ProgramGoal.FUNCTIONAL_CONDITIONING, StrengthIntent.MIXED, false, "mixed", 4, 90,
                emptySet(), explicitWeeklyDays = true, explicitDuration = false),
            CoverageSpec("request_badminton_inferred_days", TrainableQuality.STRENGTH, "barbell_back_squat", "MIXED",
                ProgramGoal.BADMINTON_SUPPORT, StrengthIntent.MIXED, true, "reviewed", 5, 30,
                emptySet(), explicitWeeklyDays = false, explicitDuration = true)
        )
        cases.forEach { spec ->
            var preparedContext: PreparedCanonicalGenerationContext? = null
            val result = try {
                runCase(spec) { service, preflight, answers, metadata ->
                    service.generatePreparedProduction(
                        preflight, answers, metadata, includeComparisonDiagnostics = true,
                        productionGenerationObserver = { observation ->
                            if (observation.phase == ProductionGenerationPhase.CANONICAL_PREPARED) {
                                preparedContext = observation.context
                            }
                        }
                    )
                }
            } catch (failure: StimulusProductionExecutionRejectedException) {
                val prepared = requireNotNull(preparedContext) { "${spec.label} did not retain its canonical request" }
                val resolved = prepared.resolvedRequest
                assertEquals("${spec.label} failure keeps the resolved goal", spec.goal, resolved.request.goal)
                assertEquals("${spec.label} failure keeps the resolved session duration", spec.minutes, resolved.request.sessionMinutes)
                assertEquals("${spec.label} failure keeps the equipment constraint", spec.equipment, resolved.request.availableEquipment)
                assertEquals("${spec.label} failure frequency source",
                    if (spec.explicitWeeklyDays) PlanningFrequencySource.EXPLICIT_USER else PlanningFrequencySource.AUTO,
                    resolved.frequencyProvenance.source)
                assertEquals("${spec.label} failure frequency value", resolved.request.weeklyTrainingDays,
                    resolved.frequencyProvenance.resolvedUserDays)
                assertTrue("${spec.label} resolved days stay bounded", resolved.request.weeklyTrainingDays in 2..5)
                assertTrue("${spec.label} resolved duration stays bounded", resolved.request.durationWeeks in 2..6)
                if (spec.explicitWeeklyDays) assertEquals(spec.days, resolved.request.weeklyTrainingDays)
                if (spec.explicitDuration) assertEquals(2, resolved.request.durationWeeks)
                if ("ACTUAL_TRAINING_DAYS_MISMATCH" in failure.reasonCodes) {
                    assertTrue("only a user-selected frequency can require every requested day", spec.explicitWeeklyDays)
                    assertTrue(failure.reasonCodes.contains("SCHEDULED_DAYS_EMPTY"))
                    assertTrue(failure.detailCodes.any { it.startsWith("ACTUAL_TRAINING_DAYS_WEEK_") && "empty=" in it })
                    assertEquals("request_strength_explicit", spec.label)
                    return@forEach
                }
                assertTrue("${spec.label} failure is an explicit hard day-feasibility rejection",
                    "DAY_OFI_OR_RECOVERY_LIMIT_EXCEEDED" in failure.reasonCodes &&
                        failure.detailCodes.any { it.startsWith("DAY_OFI_WEEK_") })
                assertEquals("request_mixed_inferred_duration", spec.label)
                return@forEach
            }
            requireNotNull(result) { "${spec.label} unexpectedly returned no production result" }
            val comparison = requireNotNull(result.comparison)
            val resolved = comparison.control.request
            assertEquals("${spec.label} complete request parity", resolved, comparison.experimental.request)
            assertEquals("${spec.label} audit horizon", resolved.durationWeeks,
                requireNotNull(comparison.experimentalAudit).planningHorizonWeeks)
            val frequency = requireNotNull(comparison.experimental.personalizedDecision?.frequencyDemand?.frequency)
            assertEquals("${spec.label} frequency request days", resolved.weeklyTrainingDays, frequency.resolvedUserDays)
            assertEquals("${spec.label} frequency source",
                if (spec.explicitWeeklyDays) PlanningFrequencySource.EXPLICIT_USER else PlanningFrequencySource.AUTO,
                frequency.source)
            assertEquals("${spec.label} session minutes", spec.minutes, resolved.sessionMinutes)
            assertTrue("${spec.label} days stay in the bounded range", resolved.weeklyTrainingDays in 2..5)
            assertTrue("${spec.label} duration stays in the bounded horizon", resolved.durationWeeks in 2..6)
            if (spec.explicitDuration) assertEquals("${spec.label} explicit duration", 2, resolved.durationWeeks)
        }
    }

    @Test
    fun normalNewGeneratorPreviewSaveRoomReloadAndDateApplyKeepsExactProgram() = runBlocking {
        val spec = CoverageSpec(
            label = "c38_3_default_execution_e2e",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 3,
            minutes = 60,
            equipment = emptySet(),
            isolateOwner = true
        )
        val databaseName = "c38-3-new-generator-e2e-${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = requireNotNull(runCase(
            spec = spec,
            databaseName = databaseName,
            evaluate = { service, preflight, answers, metadata ->
                service.generatePreparedProduction(
                    preflight, answers, metadata, includeComparisonDiagnostics = false
                )
            },
            afterProduction = { repository, db, generated ->
                assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, generated.routeDecision.selectedSource)
                assertTrue(requireNotNull(generated.executionValidation).executable)
                assertEquals(0, generated.buildCounts.controlBuilds)
                assertEquals(1, generated.buildCounts.experimentalBuilds)
                assertEquals(1, generated.buildCounts.totalBuildInvocations)
                assertEquals(null, generated.comparison)
                val expectedRows = generated.program.items.map { row ->
                    listOf(row.weekNumber, row.dayOfWeek, row.orderIndex, row.exerciseStableKey, row.selectionRole,
                        ProgramSetPrescriptionResolver.resolve(row))
                }.sortedBy { it.joinToString("|") }

                val programId = repository.saveGeneratedProgram(existingProgramId = null, skeleton = generated.program)
                val databaseDirectory = File(db.openHelper.writableDatabase.path).parentFile
                val reopenedContext = object : ContextWrapper(context) {
                    override fun getDatabasePath(name: String): File = File(requireNotNull(databaseDirectory), name)
                }
                db.close()
                val reopenedDb = Room.databaseBuilder(reopenedContext, TrainingDatabase::class.java, databaseName)
                    .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
                    .allowMainThreadQueries().build()
                try {
                    val reopenedRepository = TrainingRepository(reopenedDb, context)
                    val reloaded = com.training.trackplanner.skeletonFromProgram(
                        reopenedRepository.programEditorSnapshot(programId)
                    )
                    val actualRows = reloaded.items.map { row ->
                        listOf(row.weekNumber, row.dayOfWeek, row.orderIndex, row.exerciseStableKey, row.selectionRole,
                            ProgramSetPrescriptionResolver.resolve(row))
                    }.sortedBy { it.joinToString("|") }
                    assertEquals("Room reload preserves each generated exercise and exact prescription", expectedRows, actualRows)

                    reopenedRepository.applyProgramToDates(programId, "2026-10-12", ProgramApplyMode.Append)
                    val applied = reopenedDb.workoutDao().allEntriesWithSets().filter {
                        it.entry.date >= "2026-10-12" && it.entry.date < "2026-10-26"
                    }
                    assertEquals("date application creates every saved plan row", expectedRows.size, applied.size)
                    assertEquals(expectedRows.map { it[3].toString() }.sorted(), applied.map { it.entry.exerciseStableKey }.sorted())
                    assertTrue(applied.all { it.sets.isNotEmpty() && it.entry.sessionStableKey.isNotBlank() })
                } finally {
                    reopenedDb.close()
                }
            }
        ))
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertEquals(0, result.buildCounts.controlBuilds)
    }

    @Test
    fun explicitlyRequestedFiveDaysCannotReturnOnlyTwoActiveDays() = runBlocking {
        val spec = CoverageSpec(
            label = "requested_five_actual_days_gate",
            quality = TrainableQuality.HYPERTROPHY,
            stableKey = "cable_rear_delt_fly",
            profileGoal = "HYPERTROPHY_PHYSIQUE",
            goal = ProgramGoal.BODYBUILDING,
            intent = StrengthIntent.HYPERTROPHY_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 5,
            minutes = 60,
            equipment = emptySet(),
            isolateOwner = true
        )
        val observedPhases = mutableListOf<ProductionGenerationPhase>()
        val failure = runCatching {
            runCase(spec) { service, preflight, answers, metadata ->
                service.generatePreparedProduction(
                    preflight = preflight,
                    answers = answers,
                    metadata = metadata,
                    includeComparisonDiagnostics = false,
                    productionGenerationObserver = { observedPhases += it.phase }
                )
            }
        }.exceptionOrNull() as? StimulusProductionExecutionRejectedException
        assertNotNull("an incomplete explicit schedule must be a typed generation failure", failure)
        assertTrue(requireNotNull(failure).reasonCodes.contains("ACTUAL_TRAINING_DAYS_MISMATCH"))
        assertTrue(requireNotNull(failure).reasonCodes.contains("SCHEDULED_DAYS_EMPTY"))
        val detail = requireNotNull(failure).detailCodes.first { it.startsWith("ACTUAL_TRAINING_DAYS_WEEK_1:") }
        assertTrue(detail.contains("requested=5"))
        assertTrue(detail.contains("actual="))
        assertTrue(detail.contains("empty="))
        assertFalse("rejection occurs before diagnostic CONTROL generation",
            ProductionGenerationPhase.CONTROL_BUILD in observedPhases)
    }

    @Test
    fun canonicalB5B6AllocationDoesNotReserveDiscardedContinuityUnits() = runBlocking {
        val spec = CoverageSpec(
            label = "canonical_material_demand_not_displaced_by_discarded_continuity",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "STRENGTH_GAIN",
            goal = ProgramGoal.STRENGTH,
            intent = StrengthIntent.STRENGTH_PRIORITY,
            badminton = false,
            history = "reviewed",
            days = 5,
            minutes = 60,
            equipment = setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"),
            explicitWeeklyDays = true
        )
        var runId: String? = null
        val outcome = runCatching { runCase(spec) { service, preflight, answers, metadata ->
            runId = preflight.preparationId
            service.generatePreparedProduction(
                preflight = preflight,
                answers = answers,
                metadata = metadata,
                includeComparisonDiagnostics = false
            )
        } }
        val trace = org.json.JSONObject(requireNotNull(GenerationDecisionTraceStore.json(requireNotNull(runId))))
        val finite = (0 until trace.getJSONArray("events").length())
            .map { trace.getJSONArray("events").getJSONObject(it) }
            .last { it.getString("stageId") == "FINITE_EXECUTION_ALLOCATOR" }
        val comparisons = finite.getJSONObject("comparisons")
        assertTrue("the fixture has a positive incumbent demand that the canonical path suppresses",
            comparisons.getInt("continuityRequested") > 0)
        assertEquals(0, comparisons.getInt("continuityFunded"))
        assertTrue("approved B5/B6 demand receives capacity instead of stopping at the old 9-unit remainder",
            comparisons.getInt("materialAfter") > 9)
        assertTrue("finite allocation cannot increase the B5 requested sets",
            comparisons.getInt("materialAfter") <= comparisons.getInt("materialBefore"))
        val rejected = outcome.exceptionOrNull() as? StimulusProductionExecutionRejectedException
        if (rejected != null) {
            assertTrue("an explicit 5-day request must fail with a typed execution/input reason rather than return a partial draft",
                rejected.reasonCodes.any { it in setOf("ACTUAL_TRAINING_DAYS_MISMATCH", "DAY_OFI_OR_RECOVERY_LIMIT_EXCEEDED", "NO_EXECUTABLE_PLANNING_DEMAND") })
            assertTrue("requested-day shortfall is identified in the failure trace",
                rejected.reasonCodes.contains("ACTUAL_TRAINING_DAYS_MISMATCH") ||
                    rejected.detailCodes.any { it.startsWith("ACTUAL_TRAINING_DAYS_WEEK_") })
            val report = org.json.JSONObject().put("case", spec.label).put("status", "TYPED_REJECTION")
                .put("reasonCodes", org.json.JSONArray(rejected.reasonCodes)).put("detailCodes", org.json.JSONArray(rejected.detailCodes))
                .put("finiteAllocation", comparisons).put("decisionTrace", trace)
            java.io.File("build/reports/c38-5-authorized-weekly-allocation-regression.json").apply {
                parentFile?.mkdirs()
                writeText(report.toString(2))
            }
            return@runBlocking
        }
        val generated = requireNotNull(outcome.getOrNull())
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, generated.routeDecision.selectedSource)
        assertEquals("the normal canonical execution does not build a diagnostic CONTROL", 0, generated.buildCounts.controlBuilds)
        assertTrue(requireNotNull(generated.executionValidation).executable)
        assertEquals("the explicitly requested weekly frequency is honored in the returned request", 5,
            generated.program.request.weeklyTrainingDays)
        val physicalRows = generated.program.items.filter { it.weekNumber == 1 }
        assertTrue("the final physical rows reflect the expanded exact material allocation",
            physicalRows.sumOf(ProgramSkeletonItem::setCount) > 9)
        assertEquals("an explicit five-day request may not return fewer active days", 5,
            physicalRows.map(ProgramSkeletonItem::dayOfWeek).distinct().size)
        val report = org.json.JSONObject()
            .put("case", spec.label)
            .put("source", generated.routeDecision.selectedSource.name)
            .put("requestedTrainingDays", generated.program.request.weeklyTrainingDays)
            .put("actualWeekOneTrainingDays", org.json.JSONArray(physicalRows.map(ProgramSkeletonItem::dayOfWeek).distinct().sorted()))
            .put("weekOnePhysicalSets", physicalRows.sumOf(ProgramSkeletonItem::setCount))
            .put("weekOneRows", org.json.JSONArray(physicalRows.map { row -> org.json.JSONObject()
                .put("day", row.dayOfWeek).put("name", row.exerciseName).put("stableKey", row.exerciseStableKey)
                .put("selectionRole", row.selectionRole).put("sets", row.setCount)
                .put("prescriptions", org.json.JSONArray(row.setPrescriptions.map { it.toString() }))
            }))
            .put("finiteAllocation", comparisons)
            .put("decisionTrace", trace)
        java.io.File("build/reports/c38-5-authorized-weekly-allocation-regression.json").apply {
            parentFile?.mkdirs()
            writeText(report.toString(2))
        }
        Unit
    }

    @Test
    fun b8DiagnosticModeStillReturnsTheValidatedNewGeneratorThroughRoomAndDateApplication() = runBlocking {
        val spec = CoverageSpec(
            label = "persona3_mixed",
            quality = TrainableQuality.STRENGTH,
            stableKey = "barbell_back_squat",
            profileGoal = "MIXED",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            intent = StrengthIntent.MIXED,
            badminton = true,
            history = "mixed",
            days = 5,
            minutes = 60,
            equipment = setOf("MACHINE", "CABLE"),
            explicitWeeklyDays = false
        )
        val databaseName = "c37-replacement-e2e-${System.nanoTime()}"
        val result = requireNotNull(runCase(
            spec = spec,
            databaseName = databaseName,
            afterProduction = { repository, db, generated ->
                assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, generated.routeDecision.selectedSource)
                assertEquals("explicit comparison diagnostics may build CONTROL without granting it route authority",
                    1, generated.buildCounts.controlBuilds)
                assertEquals(1, generated.buildCounts.experimentalBuilds)
                assertTrue(requireNotNull(generated.executionValidation).executable)
                assertNotNull(generated.comparison?.productionCutoverAuthority)
                assertEquals(generated.program.items, generated.comparison?.experimental?.items)
                val expectedRows = generated.program.items.map { row ->
                    listOf(row.weekNumber, row.dayOfWeek, row.orderIndex, row.exerciseStableKey, row.selectionRole,
                        ProgramSetPrescriptionResolver.resolve(row))
                }.sortedBy { it.joinToString("|") }
                val programId = repository.saveGeneratedProgram(existingProgramId = null, skeleton = generated.program)

                val context = ApplicationProvider.getApplicationContext<Context>()
                val databaseDirectory = File(db.openHelper.writableDatabase.path).parentFile
                val reopenedContext = object : ContextWrapper(context) {
                    override fun getDatabasePath(name: String): File = File(requireNotNull(databaseDirectory), name)
                }
                db.close()
                val reopenedDb = Room.databaseBuilder(reopenedContext, TrainingDatabase::class.java, databaseName)
                    .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
                    .allowMainThreadQueries().build()
                try {
                    val reopenedRepository = TrainingRepository(reopenedDb, context)
                    val reloaded = com.training.trackplanner.skeletonFromProgram(reopenedRepository.programEditorSnapshot(programId))
                    val reloadedRows = reloaded.items.map { row ->
                        listOf(row.weekNumber, row.dayOfWeek, row.orderIndex, row.exerciseStableKey, row.selectionRole,
                            ProgramSetPrescriptionResolver.resolve(row))
                    }.sortedBy { it.joinToString("|") }
                    assertEquals("Room reload preserves the exact B5/B6 program", expectedRows, reloadedRows)
                    reopenedRepository.applyProgramToDates(programId, "2026-10-12", ProgramApplyMode.Append)
                    val appliedEntries = reopenedDb.workoutDao().allEntriesWithSets().filter {
                        it.entry.date >= "2026-10-12" && it.entry.date < "2026-10-26"
                    }
                    assertEquals("all generated rows should be applied to dated sessions", expectedRows.size, appliedEntries.size)
                    assertTrue(appliedEntries.all { it.sets.isNotEmpty() && it.entry.sessionStableKey.isNotBlank() })
                    assertEquals(expectedRows.map { it[3].toString() }.sorted(), appliedEntries.map { it.entry.exerciseStableKey }.sorted())
                } finally {
                    reopenedDb.close()
                }
            }
        ) { service, preflight, answers, metadata ->
            val generated = service.generatePreparedProduction(
                preflight = preflight,
                answers = answers,
                metadata = metadata,
                routingMode = StimulusProductionRoutingMode.B8_STRENGTH_HYPERTROPHY_V1_ACTIVE,
                includeComparisonDiagnostics = true
            )
            assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, generated.routeDecision.selectedSource)
            assertEquals("CONTROL is present only because this test explicitly requested comparison diagnostics",
                1, generated.buildCounts.controlBuilds)
            assertEquals(1, generated.buildCounts.experimentalBuilds)
            generated
        })
        assertEquals(StimulusProductionProgramSource.NEW_GENERATOR, result.routeDecision.selectedSource)
        assertEquals(1, result.buildCounts.controlBuilds)
        assertEquals(1, result.buildCounts.experimentalBuilds)
        assertEquals(0, result.buildCounts.thirdBuilds)
        assertNotNull(result.comparison?.productionCutoverAuthority)
    }

    private fun renderNewGeneratorExecutionCensus(
        records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>,
        executionFailures: Map<String, StimulusProductionExecutionRejectedException> = emptyMap(),
        executionInputNeeds: Map<String, List<String>> = emptyMap(),
        executionB4Demand: Map<String, Boolean> = emptyMap(),
        executionBlockers: Map<String, String> = emptyMap(),
        executionB6Authorization: Map<String, Boolean> = emptyMap()
    ): String {
        fun setJson(set: ProgramSetPrescription) = org.json.JSONObject()
            .put("setIndex", set.setIndex)
            .put("reps", set.reps)
            .put("seconds", set.seconds)
            .put("loadKg", set.weightKg)
            .put("loadState", set.loadState.name)
            .put("targetRpeMin", set.targetRpeMin ?: org.json.JSONObject.NULL)

        fun targetJson(target: StimulusQualityTarget) = org.json.JSONObject()
            .put("quality", target.quality.name)
            .put("strategy", target.strategy.name)
            .put("priority", target.priority.name)
            .put("numericAuthority", target.numericAuthority.name)
            .put("weeklyDirectUnitsTarget", target.weeklyDirectUnitsTarget?.let {
                org.json.JSONObject().put("min", it.min).put("preferred", it.preferred).put("max", it.max)
            } ?: org.json.JSONObject.NULL)
            .put("reasonCodes", org.json.JSONArray(target.reasonCodes.sorted()))
            .put("evidence", org.json.JSONArray(target.evidence.sorted()))

        val cases = org.json.JSONArray(records.sortedBy { it.first.label }.map { (spec, result) ->
            if (result == null) {
                val executionFailure = executionFailures[spec.label]
                return@map org.json.JSONObject()
                    .put("case", spec.label)
                    .put("outcome", when {
                        executionFailure == null -> "PREFLIGHT_INPUT_REQUIRED"
                        executionBlockers[spec.label] == "DAY_OFI_RECOVERY_CAPACITY_UNSATISFIED" -> "EXECUTION_CONSTRAINT_UNSATISFIED"
                        else -> "EXECUTION_INPUT_OR_AUTHORITY_REQUIRED"
                    })
                    .put("executionBlocker", executionBlockers[spec.label] ?: org.json.JSONObject.NULL)
                    .put("reasonCodes", org.json.JSONArray(executionFailure?.reasonCodes.orEmpty()))
                    .put("detailCodes", org.json.JSONArray(executionFailure?.detailCodes.orEmpty()))
                    .put("unresolvedMaterialDemandGaps", org.json.JSONArray(executionFailure?.unresolvedMaterialDemandGaps.orEmpty().sorted()))
                    .put("b4NumericDemandPresent", executionB4Demand[spec.label] == true)
                    .put("b6ExecutableAuthorizationPresent", executionB6Authorization[spec.label] == true)
                    .put("inputNeeds", org.json.JSONArray(executionInputNeeds[spec.label].orEmpty()))
                    .put("reason", if (executionFailure == null) "NO_CONFIRMED_HISTORY" else executionBlockers[spec.label])
            }

            val program = result.program
            val comparison = result.comparison
            val auth = comparison?.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            val execution = result.executionValidation
            org.json.JSONObject()
                .put("case", spec.label)
                .put("outcome", "GENERATED")
                .put("source", result.routeDecision.selectedSource.name)
                .put("b7Status", comparison?.experimentalReadinessAudit?.status?.name ?: org.json.JSONObject.NULL)
                .put("b7Reasons", org.json.JSONArray(comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                .put("b8Status", comparison?.productionCutoverAuthority?.status?.name ?: org.json.JSONObject.NULL)
                .put("b8Reasons", org.json.JSONArray(comparison?.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                .put("diagnosticFailure", result.diagnosticFailureReason ?: org.json.JSONObject.NULL)
                .put("execution", org.json.JSONObject()
                    .put("executable", execution?.executable == true)
                    .put("failureReasons", org.json.JSONArray(execution?.reasonCodes.orEmpty()))
                    .put("sessionTime", org.json.JSONArray(execution?.sessionTimeChecks.orEmpty().map { check ->
                        org.json.JSONObject().put("week", check.week).put("day", check.day)
                            .put("estimatedSeconds", check.estimatedSeconds)
                            .put("availableSeconds", check.availableSeconds).put("withinLimit", check.withinLimit)
                    }))
                    .put("dayOfiRecovery", org.json.JSONArray(execution?.dayOfiChecks.orEmpty().map { check ->
                        org.json.JSONObject().put("week", check.week).put("day", check.day)
                            .put("feasible", check.feasible).put("ofi", check.ofi ?: org.json.JSONObject.NULL)
                            .put("axisScores", org.json.JSONArray(check.axisScores))
                            .put("cautionReasons", org.json.JSONArray(check.cautionReasons))
                    }))
                    .put("connectiveTissueRecovery", org.json.JSONArray(execution?.tissueRecoveryChecks.orEmpty().map { check ->
                        org.json.JSONObject().put("week", check.week).put("feasible", check.feasible)
                            .put("conditionalOnTypedB6Execution", check.conditionalOnTypedB6Execution)
                            .put("diagnostic", check.diagnostic ?: org.json.JSONObject.NULL)
                            .put("blockedUnits", org.json.JSONArray(check.blockedUnits.sorted()))
                            .put("unresolvedExerciseKeys", org.json.JSONArray(check.unresolvedExerciseKeys.sorted()))
                    }))
                    .put("authorizedDoseShortfallUnits", execution?.unmetAuthorizedSetUnits ?: 0)
                    .put("unresolvedDemandGaps", org.json.JSONArray(execution?.unresolvedMaterialDemandGaps.orEmpty().sorted()))
                    .put("deferredDemandGaps", org.json.JSONArray(execution?.deferredMaterialDemandGaps.orEmpty().sorted()))
                    .put("loadCalibrationOwners", org.json.JSONArray(execution?.userLoadCalibrationOwners.orEmpty().map {
                        "${it.stableKey}#${it.selectionRole}"
                    })))
                .put("request", org.json.JSONObject()
                    .put("goal", program.request.goal.name)
                    .put("weeklyTrainingDaysRequested", program.request.weeklyTrainingDays)
                    .put("sessionMinutes", program.request.sessionMinutes)
                    .put("durationWeeks", program.request.durationWeeks)
                    .put("equipment", org.json.JSONArray(program.request.availableEquipment.sorted()))
                    .put("excluded", org.json.JSONArray(program.request.excludedExerciseStableKeys.sorted())))
                .put("schedule", org.json.JSONArray(program.weekDaySchedule.toSortedMap().map { (week, days) ->
                    org.json.JSONObject().put("week", week).put("days", org.json.JSONArray(days.sorted()))
                }))
                .put("b4QualityTargets", org.json.JSONArray(comparison?.targetPlan?.qualityTargets.orEmpty().map(::targetJson)))
                .put("b5SelectedExercises", org.json.JSONArray(comparison?.selectionPlan?.selectedCandidates.orEmpty().map { candidate ->
                    org.json.JSONObject().put("stableKey", candidate.stableKey)
                        .put("selectionRole", candidate.selectionRole)
                        .put("coveredTargets", org.json.JSONArray(candidate.coveredTargetIds.sorted()))
                        .put("primaryTarget", candidate.primaryTargetId)
                        .put("selectionReasons", org.json.JSONArray(candidate.selectionReasons.sorted()))
                        .put("prescriptionCompatibility", candidate.currentPrescriptionCompatibility)
                }))
                .put("b6Authorizations", org.json.JSONArray(auth.map { authorization ->
                    org.json.JSONObject().put("targetId", authorization.targetId)
                        .put("owner", authorization.owner?.let { "${it.stableKey}#${it.selectionRole}" } ?: org.json.JSONObject.NULL)
                        .put("status", authorization.status.name)
                        .put("executionAuthority", authorization.executionAuthority.name)
                        .put("reasonCodes", org.json.JSONArray(authorization.reasonCodes.sorted()))
                }))
                .put("materialization", org.json.JSONArray(comparison?.prescriptionMaterializationAudits.orEmpty().map { audit ->
                    org.json.JSONObject().put("targetId", audit.targetId)
                        .put("owner", audit.owner?.let { "${it.stableKey}#${it.selectionRole}" } ?: org.json.JSONObject.NULL)
                        .put("authorizedWeeklyUnits", audit.authorizedWeeklySetUnits)
                        .put("materializedWeeklyUnits", audit.materializedWeeklySetUnits)
                        .put("targetCompatibleUnits", audit.targetCompatibleMaterializedUnits)
                        .put("shortfallUnits", audit.totalShortfallUnits)
                        .put("overrunUnits", audit.overrun)
                        .put("executionAuthority", audit.executionAuthority.name)
                        .put("state", audit.state.name)
                        .put("reasonCodes", org.json.JSONArray(audit.reasonCodes.sorted()))
                }))
                .put("strengthShortfalls", org.json.JSONArray(result.strengthShortfalls.map {
                    org.json.JSONObject().put("targetId", it.targetId).put("reason", it.reason.name)
                }))
                .put("programItems", org.json.JSONArray(program.items.sortedWith(compareBy(
                    ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
                    ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey
                )).map { row ->
                    org.json.JSONObject().put("week", row.weekNumber).put("day", row.dayOfWeek)
                        .put("order", row.orderIndex).put("stableKey", row.exerciseStableKey)
                        .put("name", row.exerciseName).put("selectionRole", row.selectionRole)
                        .put("category", row.category).put("selectionReason", row.selectionReason)
                        .put("sets", org.json.JSONArray(ProgramSetPrescriptionResolver.resolve(row).map(::setJson)))
                        .put("restSeconds", row.restSeconds).put("estimatedDurationSeconds", row.estimatedDurationSeconds)
                }))
                .put("buildCounts", org.json.JSONObject().put("control", result.buildCounts.controlBuilds)
                    .put("newGenerator", result.buildCounts.experimentalBuilds)
                    .put("total", result.buildCounts.totalBuildInvocations)
                    .put("other", result.buildCounts.thirdBuilds))
        })
        return org.json.JSONObject()
            .put("schema", "c38-3-new-generator-independent-execution-census-v3")
            .put("totalCases", records.size)
            .put("generatedCases", records.count { it.second != null })
            .put("preflightInputRequiredCases", records.count { it.second == null && it.first.label !in executionFailures })
            .put("executionRejectedCases", executionFailures.size)
            .put("cases", cases)
            .toString(2)
    }

    private fun render(
        records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>,
        executionFailures: Map<String, StimulusProductionExecutionRejectedException> = emptyMap()
    ): String = buildString {
        fun names(values: Iterable<Any?>) = values.map { it.toString() }.distinct().sorted().joinToString(",")
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        appendLine("TOTAL CASES: ${records.size}")
        appendLine("PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY: ${records.count { it.second == null && it.first.label !in executionFailures }}")
        appendLine("NEW_GENERATOR_EXECUTION_REJECTIONS: ${executionFailures.size}")
        appendLine("GENERATED CASES: ${generated.size}")
        StimulusProductionProgramSource.entries.forEach { source ->
            appendLine("SOURCE $source: ${generated.count { it.second.routeDecision.selectedSource == source }}")
        }
        StimulusProductionFallbackStage.entries.forEach { stage ->
            appendLine("PRIMARY $stage: ${generated.count { it.second.diagnostics.primaryFallbackStage == stage }}")
        }
        TrainableQuality.entries.forEach { quality ->
            val targets = generated.count { (_, r) -> r.comparison?.targetPlan?.qualityTargets.orEmpty().any { it.quality == quality } }
            val selected = generated.count { (_, r) -> r.comparison?.selectionPlan?.selectedCandidates.orEmpty().any { "QUALITY:${quality.name}" in it.coveredTargetIds } }
            val material = generated.count { (_, r) -> quality in r.diagnostics.scopeResolution?.materialQualities.orEmpty() }
            // Needs/targets alone are never counted as blockers. Require governed material
            // provenance and a failed resolution explicitly identifying unsupported quality.
            val blockers = generated.count { (_, r) ->
                val scope = r.diagnostics.scopeResolution
                r.routeDecision.selectedSource == StimulusProductionProgramSource.CONTROL &&
                    scope?.status == StimulusProductionScopeResolutionStatus.UNSUPPORTED_QUALITY &&
                    "UNSUPPORTED_QUALITY_${quality.name}" in scope.reasonCodes
            }
            appendLine("QUALITY $quality: targets=$targets selected=$selected material=$material unsupportedBlockers=$blockers")
        }
        records.sortedBy { it.first.label }.forEach { (spec, r) ->
            if (r == null) {
                val failure = executionFailures[spec.label]
                appendLine("CASE ${spec.label}: history=${spec.history} intent=${spec.intent} goal=${spec.goal} badminton=${spec.badminton} days=${spec.days} minutes=${spec.minutes} equipment=${names(spec.equipment)}")
                if (failure == null) {
                    appendLine("FINAL PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0")
                } else {
                    appendLine("FINAL NEW_GENERATOR_EXECUTION_REJECTED; reasons=${failure.reasonCodes}; unmet=${failure.unresolvedMaterialDemandGaps}; B4NumericDemand=false; B6ExecutableAuthority=false; CONTROL=NOT_BUILT")
                }
                return@forEach
            }
            val c = r.comparison
            val d = r.diagnostics
            val scope = d.scopeResolution
            val auth = c?.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            appendLine("CASE ${spec.label}: intent=${spec.intent} goal=${spec.goal} badminton=${spec.badminton} history=${spec.history} days=${spec.days} minutes=${spec.minutes} equipment=${names(spec.equipment)} isolated=${spec.isolateOwner}")
            appendLine("C7 CONTROL stableKeys=${names(c?.controlStableKeys.orEmpty())}")
            appendLine("C7 B4 targets=${names(c?.targetPlan?.qualityTargets.orEmpty().map { "${it.quality}/${it.strategy}/${it.numericAuthority}" })}")
            appendLine("C7 B5 selectedOwners=${names(c?.selectionPlan?.selectedCandidates.orEmpty().map { "${it.stableKey}#${it.selectionRole}" })}")
            appendLine("C7 EXPERIMENTAL stableKeys=${names(c?.experimentalStableKeys.orEmpty())}")
            appendLine("C7 added=${names(c?.addedStableKeys.orEmpty())} removed=${names(c?.removedStableKeys.orEmpty())}")
            appendLine("C7 B6 authorizedOwners=${names(auth.filter { it.authorizedPrescription != null }.mapNotNull { row -> row.owner?.let { "${row.targetId}:${it.stableKey}#${it.selectionRole}" } })}")
            appendLine("C7 routeAfter=${r.routeDecision.selectedSource} B7=${c?.experimentalReadinessAudit?.status} B8=${c?.productionCutoverAuthority?.status} B7reasons=${c?.experimentalReadinessAudit?.reasonCodes?.sorted()} B8reasons=${c?.productionCutoverAuthority?.reasonCodes?.sorted()}")
            appendLine("C7 safety B7shadowOnly=${c?.experimentalReadinessAudit?.shadowOnly} B7productionAuthority=${c?.experimentalReadinessAudit?.productionAuthority} B7winner=${c?.experimentalReadinessAudit?.winner} B8routingActive=${c?.productionCutoverAuthority?.routingActive} B8mutationAuthority=${c?.productionCutoverAuthority?.productionMutationAuthority} B9source=${r.routeDecision.selectedSource} builds=${r.buildCounts.controlBuilds}/${r.buildCounts.experimentalBuilds}/${r.buildCounts.totalBuildInvocations}/${r.buildCounts.thirdBuilds}")
            appendLine("targets=${names(c?.targetPlan?.qualityTargets.orEmpty().map { "${it.quality}/${it.strategy}/${it.numericAuthority}" })}")
            appendLine("material=${names(scope?.materialQualities.orEmpty())} owners=${names(scope?.materialOwnerIdentities.orEmpty())}")
            appendLine("removedOwners=${names(scope?.removedOwnerIdentities.orEmpty())}")
            appendLine("scope=${scope?.scope} status=${scope?.status} reasons=${scope?.reasonCodes} unknown=${scope?.unknownTargetIds} unattributed=${names(scope?.unattributedOwnerIdentities.orEmpty())}")
            appendLine("B6 executable=${names(auth.filter { it.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED && it.status in setOf(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE, StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR) }.mapNotNull { it.quality })}")
            appendLine("B6 resolutions=${names(c?.prescriptionRealizationPlan?.resolutions.orEmpty().map { "${it.quality}/${it.status}/${it.reasonCodes.sorted()}" })}")
            appendLine("B6 authorities=${names(auth.map { "${it.targetId}/${it.owner}/${it.status}/${it.executionAuthority}/${it.reasonCodes.sorted()}" })} conflicts=${names(c?.prescriptionAuthorizationPlan?.conflictingOwners.orEmpty())}")
            appendLine("B6 materialization=${names(c?.prescriptionMaterializationAudits.orEmpty().map { "${it.quality}/${it.owner}/${it.state}/${it.executionAuthority}/${it.reasonCodes.sorted()}" })}")
            appendLine("B7 ${c?.experimentalReadinessAudit?.status} ${c?.experimentalReadinessAudit?.reasonCodes?.sorted()}")
            appendLine("B8 ${c?.productionCutoverAuthority?.scope} ${c?.productionCutoverAuthority?.status} ${c?.productionCutoverAuthority?.reasonCodes?.sorted()}")
            appendLine("B9 ${r.routeDecision.selectedSource} ${r.routeDecision.reasonCodes}")
            appendLine("FINAL ${r.routeDecision.selectedSource} independentValidation=${r.executionValidation?.executable} failures=${r.executionValidation?.reasonCodes} primary=${d.primaryFallbackStage} secondary=${d.secondaryReasonCodes} builds=${r.buildCounts}")
            appendLine("FINAL SCHEDULE requestedDays=${r.program.request.weeklyTrainingDays} actualByWeek=${r.program.weekDaySchedule.toSortedMap().mapValues { it.value.sorted() }} sessionMinutes=${r.program.request.sessionMinutes}")
            r.program.items.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
                ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey)).forEach { row ->
                appendLine("FINAL ITEM w${row.weekNumber}/d${row.dayOfWeek}/#${row.orderIndex} ${row.exerciseName}(${row.exerciseStableKey})#${row.selectionRole} category=${row.category} reason=${row.selectionReason} rest=${row.restSeconds}s estimated=${row.estimatedDurationSeconds}s sets=${ProgramSetPrescriptionResolver.resolve(row)}")
            }
            appendLine("FINAL SHORTFALL authorizedSetUnits=${r.executionValidation?.unmetAuthorizedSetUnits} unresolved=${r.executionValidation?.unresolvedMaterialDemandGaps} deferred=${r.executionValidation?.deferredMaterialDemandGaps} strength=${r.strengthShortfalls}")
        }
    }

    internal suspend fun runCase(
        spec: CoverageSpec,
        databaseName: String? = null,
        seedIncumbentPlacementFixture: Boolean = false,
        verifyRepeatedAcceptedRegeneration: Boolean = false,
        observeCanonicalPlanning: (suspend (CanonicalStimulusPlanningResult) -> Unit)? = null,
        evaluateWithIncumbent: (suspend (PersonalizedProgramPlanningService, PersonalizedPlanningPreflight,
            PersonalizedPlanningAnswers, Map<String, RuntimeExerciseMetadata>, CanonicalIncumbentPlacementIndex) -> StimulusProductionGenerationResult)? = null,
        afterProduction: (suspend (TrainingRepository, TrainingDatabase, StimulusProductionGenerationResult) -> Unit)? = null,
        evaluate: (suspend (PersonalizedProgramPlanningService, PersonalizedPlanningPreflight, PersonalizedPlanningAnswers,
            Map<String, RuntimeExerciseMetadata>) -> StimulusProductionGenerationResult?)? = null
    ): StimulusProductionGenerationResult? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseRoot = databaseName?.let { java.nio.file.Files.createTempDirectory("wgtd-c38-3-room-").toFile() }
        val databaseContext = databaseRoot?.let { root -> object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = File(root, name).apply { parentFile?.mkdirs() }
        } } ?: context
        val db = (databaseName?.let { Room.databaseBuilder(databaseContext, TrainingDatabase::class.java, it)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE) }
            ?: Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.seedIfNeeded()
            db.workoutDao().allEntries().forEach { db.workoutDao().deleteEntryById(it.id) }
            db.initialUserProfileDao().upsert(InitialUserProfile(
                primaryGoal = spec.profileGoal,
                strengthTrainingYears = 2.0, badmintonTrainingYears = if (spec.badminton) 2.0 else 0.0,
                strengthSessionsPerWeek = spec.days.toDouble(), strengthMinutesPerSession = spec.minutes, habitualTrainingIntensity = "NORMAL"
            ))
            val cutoff = LocalDate.of(2026, 9, 20)
            val exercise = requireNotNull(db.exerciseDao().findByStableKey(spec.stableKey)) { "B14.1 missing seeded exercise ${spec.stableKey}" }
            if (spec.history != "none") {
                val days = if (spec.quality == TrainableQuality.STRENGTH) listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L, 49L, 51L) else listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L)
                days.filter { when (spec.history) { "sparse" -> it == 7L; "recent" -> it <= 21L; else -> true } }.forEachIndexed { index, daysAgo ->
                    val entryId = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(daysAgo).toString(), exerciseStableKey = exercise.stableKey, exerciseName = exercise.name, category = exercise.category, sessionStableKey = "b141-${spec.label}-$index"))
                    (1..3).forEach { setIndex ->
                        val reps = if (spec.quality == TrainableQuality.STRENGTH) if (daysAgo >= 42L) 5 else 8 else if (daysAgo <= 23L) 5 else 10
                        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = setIndex, reps = reps, weightKg = 40.0, confirmed = true, rpe = 8.0))
                    }
                }
                if (spec.quality == TrainableQuality.STRENGTH && spec.history in setOf("reviewed", "mixed")) {
                    val posteriorDao = db.strengthPosteriorDao(); val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    if (posteriorDao.revision(revisionKey) == null) posteriorDao.insertRevisionStrict(StrengthModelRevisionPolicy.current(1L, null).copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE, rebuildCompletedAt = 1L)) else posteriorDao.updateRevisionStatus(revisionKey, StrengthModelRevisionPolicy.STATUS_ACTIVE, 1L, null, null)
                    posteriorDao.insertLocalHistoryStrict(listOf(StrengthExercisePerformanceHistoryEntity(revisionKey = revisionKey, eventUuid = "b141-${spec.label}", sessionKey = "b141-${spec.label}", sessionDate = cutoff.minusDays(55).toString(), exerciseStableKey = spec.stableKey, priorLogMean = ln(50.0), priorLogVariance = 0.1, sessionLikelihoodLogMean = null, sessionLikelihoodLogVariance = null, sessionLikelihoodProper = true, innovationResidualLog = null, innovationVariance = null, posteriorLogMean = ln(50.0), posteriorLogVariance = 0.1, posteriorMeanIncrementLog = 0.0, transitionDays = 1L, baselineEstablishedBefore = true, baselineEstablishedAfter = true, proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "B6_TEST", curveVersion = "B6_TEST", rirPolicyVersion = "B6_TEST", evidenceFingerprint = "b141-${spec.label}", createdAt = 1L)))
                }
            }
            if (spec.history == "mixed") {
                val other = requireNotNull(db.exerciseDao().findByStableKey("cable_rear_delt_fly"))
                (1..8).forEach { week ->
                    val entry = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(week * 7L).toString(), exerciseStableKey = other.stableKey, exerciseName = other.name, category = other.category, sessionStableKey = "mixed-${spec.label}-$week"))
                    (1..3).forEach { index -> db.workoutDao().insertSet(WorkoutSet(entryId = entry, setIndex = index, reps = 10, weightKg = 12.5, confirmed = true, rpe = 8.0)) }
                }
            }
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val catalog = field(service, "physicalQualityCatalog") as CanonicalExercisePhysicalQualityCatalog
            val excluded = if (spec.isolateOwner) metadata.keys.filter { key -> key != spec.stableKey && catalog.relations(key).any { it.qualityId == spec.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } }.toSet() else emptySet()
            val goal = spec.goal
            val request = ProgramSkeletonRequest(
                name = "Coverage ${spec.label}", goal = goal, weeklyTrainingDays = spec.days, sessionMinutes = spec.minutes,
                availableEquipment = spec.equipment, excludedExerciseText = "",
                badmintonTransferRatio = if (spec.badminton) 0.5 else 0.0,
                sportStrengthRatio = "AUTO", periodizationType = ProgramPeriodizationType.AUTO,
                durationWeeks = 2, excludedExerciseStableKeys = excluded
            )
            val constraints = PersonalizedGenerationConstraints(
                explicitGoal = goal,
                explicitWeeklyTrainingDays = spec.days.takeIf { spec.explicitWeeklyDays },
                explicitDurationWeeks = if (spec.explicitDuration) 2 else null,
                explicitSessionMinutes = spec.minutes
            )
            val preflight = try {
                repository.preparePersonalizedProgram(request, constraints, cutoff)
            } catch (failure: IllegalArgumentException) {
                // Existing record-based planner precondition, not a CONTROL routing outcome.
                // Catch only this exact no-history boundary; all other errors must fail the test.
                if (spec.history == "none" && failure.message == "기록 기반 계획에 사용할 완료 세트가 없습니다.") return null
                throw failure
            }
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question -> question.id to when (question.id) {
                QUESTION_STRENGTH_INTENT -> spec.intent.name
                QUESTION_BADMINTON_INTENT -> if (spec.badminton) BadmintonPlanningIntent.ENABLED.name else BadmintonPlanningIntent.DISABLED.name
                QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN" else error("Unexpected personalized question: ${question.id}")
            } })
            val existingProgramId = if (seedIncumbentPlacementFixture) {
                seedIncumbentPlacementProgram(db, spec.label)
            } else null
            val incumbentIndex = if (existingProgramId == null) {
                CanonicalIncumbentPlacementIndex.unavailable(CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM)
            } else {
                val programPlanService = field(repository, "programPlanService") as ProgramPlanService
                programPlanService.canonicalIncumbentPlacementIndex(existingProgramId)
            }
            observeCanonicalPlanning?.invoke(service.buildCanonicalStimulusPlanningForPrepared(preflight, answers, metadata))
            val production = when {
                evaluateWithIncumbent != null -> evaluateWithIncumbent.invoke(service, preflight, answers, metadata, incumbentIndex)
                evaluate != null -> evaluate.invoke(service, preflight, answers, metadata) ?: return null
                else -> repository.generatePreparedPersonalizedProgramEvaluation(
                    preflight,
                    answers,
                    existingProgramId = existingProgramId,
                    includeComparisonDiagnostics = true
                )
            }
            afterProduction?.invoke(repository, db, production)
            if (verifyRepeatedAcceptedRegeneration) {
                val programId = requireNotNull(existingProgramId) { "C20 idempotence check requires a persisted incumbent source" }
                fun placementFingerprint(result: StimulusProductionGenerationResult): List<String> =
                    result.program.items
                        .sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
                            ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey,
                            ProgramSkeletonItem::selectionRole))
                        .map { "${it.weekNumber}:${it.exerciseStableKey}#${it.selectionRole}:${it.dayOfWeek}/${it.orderIndex}" }
                val expectedPlacement = placementFingerprint(production)
                var accepted = production
                repeat(2) {
                    assertEquals(programId, repository.saveGeneratedProgram(programId, accepted.program))
                    val refreshed = repository.generatePreparedPersonalizedProgramEvaluation(
                        preflight, answers, existingProgramId = programId
                    )
                    assertEquals(0, refreshed.buildCounts.controlBuilds)
                    assertEquals(1, refreshed.buildCounts.experimentalBuilds)
                    assertEquals(1, refreshed.buildCounts.totalBuildInvocations)
                    assertEquals(0, refreshed.buildCounts.thirdBuilds)
                    assertEquals(
                        "accepted regeneration changed incumbent placement; route=${refreshed.routeDecision}; " +
                            "activation=${refreshed.incumbentPlacementActivationStatus}; " +
                            "preservations=${refreshed.incumbentPlacementPreservations}; " +
                            "details=${refreshed.incumbentPlacementActivationDetails}; " +
                            "shadow=${refreshed.incumbentPlacementShadow?.let { shadow ->
                                listOf(shadow.indexStatus, shadow.rows, shadow.combinedFeasibility, shadow.combinedAnchorSetConflict)
                            }}",
                        expectedPlacement, placementFingerprint(refreshed)
                    )
                    accepted = refreshed
                }
            }
            return production
        } finally {
            db.close()
            databaseRoot?.listFiles().orEmpty().forEach(File::delete)
            databaseRoot?.delete()
        }
    }

    private suspend fun seedIncumbentPlacementProgram(db: TrainingDatabase, caseId: String): Long {
        val c18 = org.json.JSONObject(repositoryFile("docs/c18-tissue-incumbent-placement-census.json").readText())
        val rows = c18.getJSONArray("placementRows").let { array ->
            (0 until array.length()).map(array::getJSONObject).filter { it.getString("case") == caseId }
        }
        require(rows.isNotEmpty()) { "C20 exact incumbent fixture missing for $caseId" }
        val programId = db.programDao().insertProgram(TrainingProgram(
            stableKey = "c20_fixture_$caseId",
            name = "C20 incumbent fixture",
            durationDays = 14,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
            canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
        ))
        rows.sortedWith(compareBy({ it.getInt("week") }, { it.getJSONObject("owner").getString("stableKey") },
            { it.getJSONObject("owner").getString("selectionRole") })).forEachIndexed { index, row ->
            val owner = row.getJSONObject("owner")
            val exercise = requireNotNull(db.exerciseDao().findByStableKey(owner.getString("stableKey")))
            val from = row.getJSONObject("from")
            db.programDao().insertProgramItem(TrainingProgramItem(
                programId = programId,
                weekNumber = row.getInt("week"),
                dayOfWeek = from.getInt("day"),
                orderIndex = from.getInt("order"),
                exerciseStableKey = owner.getString("stableKey"),
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = 60,
                prescription = "C20 fixture",
                setCount = 2,
                reps = 8,
                selectionRole = owner.getString("selectionRole")
            ))
        }
        return programId
    }

    private fun field(target: Any, name: String): Any = requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))

    private fun repositoryFile(relativePath: String): java.io.File {
        var directory: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val candidate = directory?.resolve(relativePath)
            if (candidate?.isFile == true) return candidate
            directory = directory?.parentFile
        }
        error("Repository fixture not found from ${System.getProperty("user.dir")}: $relativePath")
    }
}
