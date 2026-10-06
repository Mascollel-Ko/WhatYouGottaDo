package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusBadmintonTaskCutoverTest {
    @Test
    fun exactTaskOnlyMaterialResolvesAuthorizesAndRoutesTheExistingSkeleton() {
        val comparison = positiveComparison()
        val resolver = StimulusProductionMaterialScopeResolver()
        assertEquals(StimulusProductionCutoverScope.BADMINTON_TASK_V1, resolver.resolve(comparison))
        val authority = StimulusProductionCutoverAuthorityAuditEngine().audit(
            comparison, StimulusProductionCutoverScope.BADMINTON_TASK_V1
        )
        assertEquals("${authority.reasonCodes}", StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, authority.status)
        assertEquals(listOf("B8_BADMINTON_TASK_V1_AUTHORIZED"), authority.reasonCodes)
        assertEquals(1, authority.authorizedTaskProtocolIdentities.size)
        assertEquals("BADMINTON_SIX_CORNER_FOOTWORK_V1", authority.authorizedTaskProtocolIdentities.single().protocolId)

        val router = StimulusProductionRouter()
        val production = router.route(comparison, authority, StimulusProductionRoutingPolicy.defaultMode)
        assertSame(comparison.experimental, production.program)
        assertEquals(StimulusProductionProgramSource.B8_BADMINTON_TASK_V1, production.decision.selectedSource)
        assertEquals(listOf("B9_B8_BADMINTON_TASK_V1_ROUTED"), production.decision.reasonCodes)
        val rollback = router.route(comparison, authority, StimulusProductionRoutingMode.CONTROL_ONLY)
        assertSame(comparison.control, rollback.program)
        assertEquals(StimulusProductionProgramSource.CONTROL, rollback.decision.selectedSource)
        assertEquals(listOf("B9_CONTROL_ONLY_POLICY"), rollback.decision.reasonCodes)
        assertEquals(1, comparison.control.items.size)
        assertEquals(3, comparison.experimental.items.size)

        val tamperedAuthority = authority.copy(authorizedTaskProtocolIdentities = authority.authorizedTaskProtocolIdentities.map {
            it.copy(selectionRole = "WRONG_ROLE")
        })
        val tamperedRoute = router.route(comparison, tamperedAuthority, StimulusProductionRoutingPolicy.defaultMode)
        assertSame(comparison.control, tamperedRoute.program)
        assertEquals(listOf("B9_B8_TASK_AUTHORITY_IDENTITY_MISMATCH"), tamperedRoute.decision.reasonCodes)
    }

    @Test
    fun taskOnlyScopeRejectsMixedForeignAndUnclosedMaterial() {
        val base = positiveComparison()
        val engine = StimulusProductionCutoverAuthorityAuditEngine()
        fun assertControl(comparison: StimulusSelectionProgramComparison, expectedReason: String? = null) {
            val scope = StimulusProductionMaterialScopeResolver().resolve(comparison)
            val evaluatedScope = scope ?: StimulusProductionCutoverScope.BADMINTON_TASK_V1
            val authority = engine.audit(comparison, evaluatedScope)
            assertFalse("$scope ${authority.reasonCodes}", authority.status == StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER)
            val route = StimulusProductionRouter().route(comparison, authority, StimulusProductionRoutingPolicy.defaultMode)
            assertSame(comparison.control, route.program)
            if (expectedReason != null) assertTrue("${authority.reasonCodes}", expectedReason in authority.reasonCodes)
        }

        val unsupportedStrength = base.copy(
            targetPlan = base.targetPlan.copy(qualityTargets = listOf(qualityTarget(TrainableQuality.STRENGTH))),
            experimentalReadinessAudit = requireNotNull(base.experimentalReadinessAudit).copy(
                changeAttributions = requireNotNull(base.experimentalReadinessAudit).changeAttributions +
                    StimulusExperimentalChangeAttribution(
                        "foreign_strength", "CANONICAL_STIMULUS_QUALITY_STRENGTH",
                        StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
                        listOf("QUALITY:STRENGTH")
                    )
            ),
            experimental = base.experimental.copy(items = base.experimental.items + item("foreign_strength", "CANONICAL_STIMULUS_QUALITY_STRENGTH"))
        )
        assertControl(unsupportedStrength)

        val power = unsupportedStrength.copy(
            targetPlan = unsupportedStrength.targetPlan.copy(qualityTargets = listOf(qualityTarget(TrainableQuality.POWER))),
            experimentalReadinessAudit = requireNotNull(unsupportedStrength.experimentalReadinessAudit).copy(
                changeAttributions = requireNotNull(unsupportedStrength.experimentalReadinessAudit).changeAttributions.dropLast(1) +
                    StimulusExperimentalChangeAttribution(
                        "foreign_strength", "CANONICAL_STIMULUS_QUALITY_POWER",
                        StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
                        listOf("QUALITY:POWER")
                    )
            ),
            experimental = unsupportedStrength.experimental.copy(items = unsupportedStrength.experimental.items.map {
                if (it.exerciseStableKey == "foreign_strength") it.copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_POWER") else it
            })
        )
        assertControl(power)

        val jumpLanding = base.copy(
            targetPlan = base.targetPlan.copy(
                taskTargets = base.targetPlan.taskTargets + taskTarget(CanonicalTaskTarget.JUMP_LANDING),
                unresolved = listOf("TASK:JUMP_LANDING")
            )
        )
        assertControl(jumpLanding, "B8_BADMINTON_TASK_UNRESOLVED_JUMP_LANDING_PRESENT")

        val changedProgramContract = base.copy(experimental = base.experimental.copy(
            request = base.experimental.request.copy(sessionMinutes = base.experimental.request.sessionMinutes - 10)
        ))
        assertControl(changedProgramContract, "B8_BADMINTON_TASK_PROGRAM_CONTRACT_CHANGED")

        val unknown = base.copy(
            experimental = base.experimental.copy(items = base.experimental.items + item("unknown", "UNKNOWN_ROLE")),
            experimentalReadinessAudit = requireNotNull(base.experimentalReadinessAudit).copy(
                changeAttributions = requireNotNull(base.experimentalReadinessAudit).changeAttributions +
                    StimulusExperimentalChangeAttribution("unknown", "UNKNOWN_ROLE", StimulusExperimentalChangeAttributionSource.UNEXPLAINED)
            )
        )
        assertControl(unknown)

        val unexplainedRemoval = base.copy(control = base.control.copy(items = base.control.items + item("removed", "CONTROL_ONLY")))
        assertControl(unexplainedRemoval)
        assertEquals(null, StimulusProductionMaterialScopeResolver().resolve(unexplainedRemoval))
    }

    @Test
    fun exactTaskProtocolFailsClosedWhenB6IdentitySemanticsOrFrequencyAreInvalid() {
        val base = positiveComparison()
        val owner = StimulusPrescriptionOwnerIdentity("ex_33841b88", "CANONICAL_STIMULUS_TASK_ACCELERATION")
        val engine = StimulusProductionCutoverAuthorityAuditEngine()

        fun assertRejected(comparison: StimulusSelectionProgramComparison, reason: String) {
            val resolvedScope = StimulusProductionMaterialScopeResolver().resolve(comparison)
            val evaluatedScope = resolvedScope ?: StimulusProductionCutoverScope.BADMINTON_TASK_V1
            val authority = engine.audit(comparison, evaluatedScope)
            assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, authority.status)
            assertTrue("${authority.reasonCodes}", reason in authority.reasonCodes)
            assertSame(comparison.control, StimulusProductionRouter().route(
                comparison, authority, StimulusProductionRoutingPolicy.defaultMode
            ).program)
        }

        assertRejected(base.copy(experimental = base.experimental.copy(items = base.experimental.items.map {
            if (it.exerciseStableKey == owner.stableKey) it.copy(taskProtocolSemanticsJson = null) else it
        })), "B8_BADMINTON_TASK_B6_AUTHORITY_MISSING_OR_INVALID")

        assertRejected(base.copy(experimental = base.experimental.copy(items = base.experimental.items.map {
            if (it.exerciseStableKey == owner.stableKey) it.copy(selectionRole = "WRONG_ROLE") else it
        })), "B8_BADMINTON_TASK_MATERIAL_SCOPE_MISMATCH")

        assertRejected(base.copy(experimental = base.experimental.copy(items = base.experimental.items.map {
            if (it.exerciseStableKey == owner.stableKey) it.copy(exerciseStableKey = "wrong_stable_key", stableKey = "wrong_stable_key") else it
        })), "B8_BADMINTON_TASK_MATERIAL_SCOPE_MISMATCH")

        assertRejected(base.copy(experimental = base.experimental.copy(items = base.experimental.items.map {
            if (it.exerciseStableKey == owner.stableKey) it.copy(taskProtocolSemanticsJson =
                requireNotNull(it.taskProtocolSemanticsJson).replace("USER_APPROVED_PROJECT_POLICY", "UNVERIFIED_POLICY")) else it
        })), "B8_BADMINTON_TASK_B6_AUTHORITY_MISSING_OR_INVALID")

        assertRejected(base.copy(experimental = base.experimental.copy(taskProtocolFrequencyOutcomes =
            base.experimental.taskProtocolFrequencyOutcomes.map { it.copy(placedExposures = 1, shortfall = 1, status = TaskProtocolFrequencyStatus.SHORTFALL) }
        )), "B8_BADMINTON_TASK_FREQUENCY_OR_PLACEMENT_SHORTFALL")

        assertRejected(base.copy(experimentalReadinessAudit = requireNotNull(base.experimentalReadinessAudit).copy(
            changeProvenanceClosed = false
        )), "B8_BADMINTON_TASK_PROVENANCE_NOT_CLOSED")

        assertRejected(base.copy(experimental = base.experimental.copy(items = base.experimental.items.map {
            if (it.exerciseStableKey == owner.stableKey) it.copy(estimatedDurationSeconds = 3_601) else it
        })), "B8_BADMINTON_TASK_HARD_PROJECTION_INVALID")

        val fewerRows = base.experimental.copy(items = base.experimental.items.dropLast(1))
        assertRejected(base.copy(experimental = fewerRows), "B8_BADMINTON_TASK_FREQUENCY_OR_PLACEMENT_SHORTFALL")
    }

    private fun positiveComparison(): StimulusSelectionProgramComparison {
        val definition = ApprovedBadmintonTaskProtocols.definitions.single { it.primaryTask == CanonicalTaskTarget.ACCELERATION }
        val owner = StimulusPrescriptionOwnerIdentity(definition.stableKey, definition.selectionRole)
        val tasks = definition.authorizedTasks
        val authorization = TaskProtocolB6Authorization(
            status = TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
            definition = definition,
            materializationActivityKind = PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL,
            attributedTasks = tasks,
            transferEvidence = tasks.associateWith { com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel.DIRECT }
        )
        val rows = (1..2).map { exposure ->
            val item = item(owner.stableKey, owner.selectionRole, day = if (exposure == 1) 1 else 3).copy(
                prescription = definition.shape.format(),
                restSeconds = definition.shape.restSeconds,
                setCount = definition.shape.setCount,
                reps = 0,
                seconds = requireNotNull(definition.shape.maxSeconds),
                weightKg = 0.0,
                setPrescriptions = List(definition.shape.setCount) { index -> ProgramSetPrescription(
                    setIndex = index + 1, reps = 0, weightKg = 0.0,
                    seconds = requireNotNull(definition.shape.maxSeconds),
                    loadState = ProgramLoadState.NOT_APPLICABLE
                ) },
                taskProtocolSemanticsJson = TaskProtocolExposureMetadata(authorization, exposure).toJsonString()
            )
            item
        }
        val request = ProgramSkeletonRequest(
            name = "Task-only service contract", goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 2, sessionMinutes = 60, availableEquipment = emptySet(),
            excludedExerciseText = "", badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 1
        )
        val schedule = mapOf(1 to setOf(1, 3))
        val control = skeleton(request, listOf(item("unchanged", "SHARED")), schedule)
        val experimental = skeleton(request, listOf(item("unchanged", "SHARED")) + rows, schedule)
            .copy(taskProtocolFrequencyOutcomes = listOf(TaskProtocolWeeklyFrequencyOutcome(
                protocolId = definition.protocolId, stableKey = owner.stableKey, selectionRole = owner.selectionRole,
                week = 1, requestedExposures = 2, placedExposures = 2, shortfall = 0,
                status = TaskProtocolFrequencyStatus.SATISFIED, reasonCode = "APPROVED_PROTOCOL_FREQUENCY_PLACED"
            )))
        val targets = tasks.map(::taskTarget)
        val candidate = StimulusSelectedCandidate(
            stableKey = owner.stableKey, coveredTargetIds = setOf("TASK:${definition.primaryTask.name}"),
            primaryTargetId = "TASK:${definition.primaryTask.name}", selectionReasons = listOf("EXACT_B5"),
            currentPrescriptionCompatibility = "REALIZATION_UNCLASSIFIED", targetSetsFromExistingPrescription = 0,
            selectionRole = owner.selectionRole
        )
        val trace = StimulusCandidateSelectionTrace(
            targetId = "TASK:${definition.primaryTask.name}", strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            priority = TargetPriority.PRIMARY, historyDirectCapabilityIdentities = emptyList(), selectionRequired = true,
            candidatePool = listOf(owner.stableKey), selectedStableKey = owner.stableKey,
            coveredByPreviouslySelectedStableKey = null, reasonCodes = listOf("EXACT_B5"), selectedSelectionRole = owner.selectionRole
        )
        val selection = StimulusCandidateSelectionPlan(listOf(candidate), listOf(trace), MaterialDemand(emptyList(), emptyMap(), emptyMap()))
        val compared = StimulusSelectionProgramComparisonEngine().compare(
            control, experimental,
            StimulusTargetPlan(emptyList(), targets, emptyList()), selection, null, null
        )
        val b7 = StimulusExperimentalReadinessAudit(
            status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
            changeAttributions = listOf(StimulusExperimentalChangeAttribution(
                stableKey = owner.stableKey, selectionRole = owner.selectionRole,
                source = StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL,
                targetIds = tasks.map { "TASK:${it.name}" }.sorted(),
                reasonCodes = listOf("B6_USER_APPROVED_EXACT_TASK_PROTOCOL"),
                evidenceSources = listOf("EXACT_B4_TASK_TARGET", "EXACT_B5_OWNER_ROLE", "USER_APPROVED_PROJECT_POLICY")
            )),
            materializationIntegrityPassed = true, changeProvenanceClosed = true, collateralRegressionFree = true
        )
        return compared.copy(experimentalReadinessAudit = b7)
    }

    private fun taskTarget(task: CanonicalTaskTarget) = StimulusTaskTarget(
        task = task.name, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
        reasonCodes = listOf("DIRECTION_ONLY"), evidence = listOf("CANONICAL_DIRECT_TASK_RELATION")
    )

    private fun qualityTarget(quality: TrainableQuality) = StimulusQualityTarget(
        quality = quality, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
        baselineSource = null, baselineConfidence = null, weeklyDirectUnitsTarget = null,
        weeklyDirectSessionsTarget = null, exposureWeekDirectUnitsReference = null,
        exposureWeekDirectSessionsReference = null, exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun item(key: String, role: String, day: Int = 1) = ProgramSkeletonItem(
        localId = "$key-$role-$day", weekNumber = 1, dayOfWeek = day, orderIndex = 1,
        exerciseStableKey = key, exerciseName = key, category = "TASK", restSeconds = 60,
        prescription = "fixture", setCount = 1, reps = 1, weightKg = 0.0, seconds = 0,
        selectionReason = "fixture", weightSource = "NOT_APPLICABLE", selectionRole = role,
        stableKey = key,
        setPrescriptions = listOf(ProgramSetPrescription(1, 1, 0.0, 0, loadState = ProgramLoadState.NOT_APPLICABLE))
    )

    private fun skeleton(
        request: ProgramSkeletonRequest,
        items: List<ProgramSkeletonItem>,
        schedule: Map<Int, Set<Int>>
    ) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = 7, request = request,
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = listOf(ProgramWeekPlan(1, "fixture", 1.0, 1.0, 1, 1.0, 1, 0, false)),
        items = items, weekDaySchedule = schedule
    )
}
