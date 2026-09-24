package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
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

class StimulusProductionCutoverAuthorityTest {
    @Test
    fun eligibleSameOwnerStrengthRepairIsAuthorized() {
        val control = item("squat", "PRIMARY", reps = 8)
        val experimental = control.copy(reps = 5, prescription = "5 reps", setPrescriptions = sets(5))
        val evaluation = engine().audit(comparison(
            controlItems = listOf(control), experimentalItems = listOf(experimental),
            attribution = attribution("squat", "PRIMARY", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION),
            authorization = authorization("squat", "PRIMARY", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "PRIMARY")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, evaluation.status)
        assertEquals(listOf(StimulusPrescriptionOwnerIdentity("squat", "PRIMARY")), evaluation.authorizedOwnerIdentities)
        assertFalse(evaluation.routingActive)
        assertFalse(evaluation.productionMutationAuthority)
    }

    @Test
    fun eligibleAdditiveStrengthOwnerIsAuthorizedWithExactIdentity() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")),
            experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
            selected = selected("squat", "ROLE_A"),
            traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, decision.status)
        assertEquals(listOf(StimulusPrescriptionOwnerIdentity("squat", "ROLE_A")), decision.authorizedOwnerIdentities)
    }

    @Test
    fun provenB7RemovalIsStillBlockedByStrengthV1() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("removed", "PRIMARY")), experimentalItems = emptyList(),
            b7 = eligibleAudit(attribution("removed", "PRIMARY", StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT))
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"))
    }

    @Test
    fun partialMaterializationIsRejectedEvenWhenB7IsEligible() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")), experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
            selected = selected("squat", "ROLE_A"), traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A", full = false)
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION"))
    }

    @Test
    fun nonStrengthMaterialChangeIsOutOfScopeButIncidentalTransferIsAllowed() {
        val outOfScope = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")), experimentalItems = listOf(item("base", "BASE"), item("jump", "ROLE_A")),
            selected = selected("jump", "ROLE_A", "QUALITY:POWER"), traces = listOf(trace("jump", "ROLE_A", "QUALITY:POWER")),
            target = qualityTarget(TrainableQuality.POWER),
            attribution = attribution("jump", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, "QUALITY:POWER"),
            authorization = authorization("jump", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("jump", "ROLE_A")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, outOfScope.status)
        assertTrue(outOfScope.reasonCodes.contains("B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE"))

        val incidental = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")), experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
            selected = selected("squat", "ROLE_A"), traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, incidental.status)
    }

    @Test
    fun unrelatedOwnerMovementAndScheduleChangeAreRejected() {
        val moved = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE", day = 1)), experimentalItems = listOf(item("base", "BASE", day = 2)),
            target = qualityTarget(TrainableQuality.STRENGTH), b7 = eligibleAudit(emptyList())
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, moved.status)
        assertTrue(moved.reasonCodes.contains("B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION"))

        val schedule = engine().audit(comparison(
            controlItems = listOf(item("squat", "PRIMARY")), experimentalItems = listOf(item("squat", "PRIMARY")),
            scheduleChanged = true, b7 = eligibleAudit(emptyList())
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, schedule.status)
        assertTrue(schedule.reasonCodes.contains("B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED"))
    }

    @Test
    fun upstreamB7MappingAndB6IntegrityFailClosed() {
        val inconclusive = engine().audit(comparison(b7 = eligibleAudit(emptyList()).copy(status = StimulusExperimentalReadinessStatus.INCONCLUSIVE)))
        assertEquals(StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE, inconclusive.status)
        val notEligible = engine().audit(comparison(b7 = eligibleAudit(emptyList()).copy(status = StimulusExperimentalReadinessStatus.NOT_ELIGIBLE)))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, notEligible.status)
        val noChange = engine().audit(comparison(b7 = eligibleAudit(emptyList()).copy(status = StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE)))
        assertEquals(StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE, noChange.status)
        val integrity = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")), experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
            selected = selected("squat", "ROLE_A"), traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A", reasonCodes = listOf("B6_AUTHORIZED_SET_REUSED"))
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, integrity.status)
        assertTrue(integrity.reasonCodes.contains("B6_AUTHORIZED_SET_REUSED"))
    }

    @Test
    fun exactRoleCannotBorrowB5OrB6AuthorityAndDecisionIsDeterministic() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")), experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_B")),
            selected = selected("squat", "ROLE_A"), traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_B", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A")
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY"))
        assertEquals(decision.reasonCodes.sorted(), decision.reasonCodes)
    }

    @Test
    fun eligibleWithoutMaterialOwnerFailsClosedAsUpstreamInconsistency() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("squat", "PRIMARY")),
            experimentalItems = listOf(item("squat", "PRIMARY")),
            b7 = eligibleAudit(emptyList())
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"))
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"))
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
        assertFalse(decision.routingActive)
        assertFalse(decision.productionMutationAuthority)
    }

    @Test
    fun b4NoneNumericAuthorityFailsClosedAtB8Boundary() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")),
            experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
            selected = selected("squat", "ROLE_A"),
            traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A"),
            target = qualityTarget(TrainableQuality.STRENGTH, StimulusTargetNumericAuthority.NONE)
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY"))
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
    }

    @Test
    fun b4UnresolvedNumericAuthorityFailsClosedAtB8Boundary() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("base", "BASE")),
            experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
            selected = selected("squat", "ROLE_A"),
            traces = listOf(trace("squat", "ROLE_A")),
            attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
            authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "ROLE_A"),
            target = qualityTarget(TrainableQuality.STRENGTH, StimulusTargetNumericAuthority.UNRESOLVED)
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY"))
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
    }

    @Test
    fun sharedStrengthRepairAlsoRejectsMissingNumericAuthority() {
        val decision = engine().audit(comparison(
            controlItems = listOf(item("squat", "PRIMARY", reps = 8)),
            experimentalItems = listOf(item("squat", "PRIMARY", reps = 5)),
            attribution = attribution("squat", "PRIMARY", StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION),
            authorization = authorization("squat", "PRIMARY", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
            materialization = materialization("squat", "PRIMARY"),
            target = qualityTarget(TrainableQuality.STRENGTH, StimulusTargetNumericAuthority.NONE)
        ))
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, decision.status)
        assertTrue(decision.reasonCodes.contains("B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY"))
        assertTrue(decision.authorizedOwnerIdentities.isEmpty())
    }

    @Test
    fun b9AuthorizedActiveRoutesTheExistingExperimentalSkeleton() {
        val comparison = authorizedComparison()
        val authority = engine().audit(comparison)
        val result = StimulusProductionRouter().route(
            comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )
        assertSame(comparison.experimental, result.program)
        assertEquals(StimulusProductionProgramSource.B8_STRENGTH_V1, result.decision.selectedSource)
        assertEquals(listOf("B9_B8_STRENGTH_V1_ROUTED"), result.decision.reasonCodes)
        assertTrue(result.decision.productionRoutingActive)
    }

    @Test
    fun b9ControlOnlyIsAnExplicitKillSwitchEvenWhenB8IsAuthorized() {
        val comparison = authorizedComparison()
        val authority = engine().audit(comparison)
        val result = StimulusProductionRouter().route(
            comparison, authority, StimulusProductionRoutingMode.CONTROL_ONLY
        )
        assertSame(comparison.control, result.program)
        assertEquals(StimulusProductionProgramSource.CONTROL, result.decision.selectedSource)
        assertEquals(listOf("B9_CONTROL_ONLY_POLICY"), result.decision.reasonCodes)
        assertFalse(result.decision.productionRoutingActive)
        assertEquals(StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER, result.decision.b8Status)
    }

    @Test
    fun b9StatusMatrixAlwaysFallsBackToTheExistingControlSkeleton() {
        val comparison = authorizedComparison()
        listOf(
            StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED to "B9_B8_CONTROL_REQUIRED",
            StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE to "B9_B8_INCONCLUSIVE",
            StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE to "B9_B8_NO_MATERIAL_CHANGE"
        ).forEach { (status, reason) ->
            val authority = StimulusProductionCutoverAuthorityDecision(
                status = status,
                scope = StimulusProductionCutoverScope.STRENGTH_V1,
                authorizedOwnerIdentities = emptyList(),
                reasonCodes = emptyList(),
                b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
            )
            val result = StimulusProductionRouter().route(
                comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
            )
            assertSame(comparison.control, result.program)
            assertEquals(listOf(reason), result.decision.reasonCodes)
            assertFalse(result.decision.productionRoutingActive)
        }
    }

    @Test
    fun b9MalformedAuthorizedAuthorityWithEmptyOwnersFailsClosed() {
        val comparison = authorizedComparison()
        val authority = StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            scope = StimulusProductionCutoverScope.STRENGTH_V1,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf("B8_STRENGTH_V1_AUTHORIZED"),
            b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
        )
        val result = StimulusProductionRouter().route(
            comparison, authority, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_B8_EMPTY_AUTHORIZED_OWNER_SET"), result.decision.reasonCodes)
        assertFalse(result.decision.productionRoutingActive)
    }

    @Test
    fun b9MissingAuthorityFallsBackToControl() {
        val comparison = authorizedComparison()
        val result = StimulusProductionRouter().route(
            comparison, null, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )
        assertSame(comparison.control, result.program)
        assertEquals(listOf("B9_B8_AUTHORITY_MISSING"), result.decision.reasonCodes)
    }

    private fun authorizedComparison() = comparison(
        controlItems = listOf(item("base", "BASE")),
        experimentalItems = listOf(item("base", "BASE"), item("squat", "ROLE_A")),
        selected = selected("squat", "ROLE_A"),
        traces = listOf(trace("squat", "ROLE_A")),
        attribution = attribution("squat", "ROLE_A", StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY),
        authorization = authorization("squat", "ROLE_A", StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR),
        materialization = materialization("squat", "ROLE_A")
    )

    private fun engine() = StimulusProductionCutoverAuthorityAuditEngine()

    private fun comparison(
        controlItems: List<ProgramSkeletonItem> = listOf(item("squat", "PRIMARY")),
        experimentalItems: List<ProgramSkeletonItem> = listOf(item("squat", "PRIMARY")),
        selected: StimulusSelectedCandidate? = null,
        traces: List<StimulusCandidateSelectionTrace> = emptyList(),
        attribution: List<StimulusExperimentalChangeAttribution> = emptyList(),
        authorization: StimulusPrescriptionAuthorization? = null,
        materialization: StimulusPrescriptionMaterializationAudit? = null,
        target: StimulusQualityTarget = qualityTarget(TrainableQuality.STRENGTH),
        b7: StimulusExperimentalReadinessAudit = eligibleAudit(attribution),
        scheduleChanged: Boolean = false
    ): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("B8", ProgramGoal.STRENGTH, 1, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1)
        val control = skeleton(request, controlItems, scheduleChanged = false)
        val experimental = skeleton(request, experimentalItems, scheduleChanged = scheduleChanged)
        val selection = StimulusCandidateSelectionPlan(listOfNotNull(selected), traces, MaterialDemand(emptyList(), emptyMap(), emptyMap()))
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            control, experimental, StimulusTargetPlan(listOf(target), emptyList(), emptyList()), selection, null, null
        )
        return comparison.copy(
            experimentalReadinessAudit = b7,
            prescriptionAuthorizationPlan = authorization?.let { StimulusPrescriptionAuthorizationPlan(listOf(it)) },
            prescriptionMaterializationAudits = materialization?.let { listOf(it) }.orEmpty()
        )
    }

    private fun eligibleAudit(attribution: List<StimulusExperimentalChangeAttribution>) = StimulusExperimentalReadinessAudit(
        status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
        changeAttributions = attribution,
        materializationIntegrityPassed = true, changeProvenanceClosed = true, collateralRegressionFree = true
    )

    private fun selected(key: String, role: String, vararg targets: String) = StimulusSelectedCandidate(
        stableKey = key, coveredTargetIds = targets.toSet().ifEmpty { setOf("QUALITY:STRENGTH") }, primaryTargetId = targets.firstOrNull() ?: "QUALITY:STRENGTH",
        selectionReasons = listOf("B5"), currentPrescriptionCompatibility = "REALIZATION_UNCLASSIFIED", targetSetsFromExistingPrescription = 2, selectionRole = role
    )

    private fun trace(key: String, role: String, targetId: String = "QUALITY:STRENGTH") = StimulusCandidateSelectionTrace(
        targetId = targetId, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, priority = TargetPriority.PRIMARY,
        controlDirectCapabilityIdentities = emptyList(), selectionRequired = true, candidatePool = listOf(key), selectedStableKey = key,
        coveredByPreviouslySelectedStableKey = null, reasonCodes = listOf("SELECTION_IDENTITY_PRESENT"), selectedSelectionRole = role
    )

    private fun attribution(key: String, role: String, source: StimulusExperimentalChangeAttributionSource, vararg targetIds: String) = listOf(
        StimulusExperimentalChangeAttribution(key, role, source, targetIds.toList().ifEmpty { listOf("QUALITY:STRENGTH") })
    )

    private fun authorization(key: String, role: String, status: StimulusPrescriptionAuthorizationStatus) = StimulusPrescriptionAuthorization(
        targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
        owner = StimulusPrescriptionOwner(key, role), source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
        inputPrescription = planned(8), plannedCompatibility = null, authorizedPrescription = planned(5), status = status
    )

    private fun materialization(key: String, role: String, full: Boolean = true, reasonCodes: List<String> = emptyList()) = StimulusPrescriptionMaterializationAudit(
        targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = StimulusPrescriptionOwner(key, role),
        authorizedWeeklySetUnits = 2, materializedWeeklySetUnits = if (full) 2 else 1, targetCompatibleMaterializedUnits = if (full) 2 else 1,
        shortfall = if (full) 0 else 1, overrun = 0, prescriptionPreservedOrSubset = true,
        state = if (full) StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED else StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED,
        reasonCodes = reasonCodes, weeklyAudits = listOf(StimulusPrescriptionWeekMaterializationAudit(
            weekNumber = 1, authorizedSetUnits = 2, materializedSetUnits = if (full) 2 else 1,
            targetCompatibleMaterializedUnits = if (full) 2 else 1, shortfall = if (full) 0 else 1, overrun = 0,
            prescriptionPreservedOrSubset = true
        ))
    )

    private fun qualityTarget(
        quality: TrainableQuality,
        numericAuthority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
    ) = StimulusQualityTarget(
        quality = quality, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, priority = TargetPriority.PRIMARY,
        numericAuthority = numericAuthority, baselineSource = null, baselineConfidence = null,
        weeklyDirectUnitsTarget = StimulusTargetRange(1.0, 2.0, 4.0), weeklyDirectSessionsTarget = StimulusTargetRange(1.0, 1.0, 2.0),
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null, exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun item(key: String, role: String, day: Int = 1, reps: Int = 8) = ProgramSkeletonItem(
        localId = "$key-$role-$day", weekNumber = 1, dayOfWeek = day, orderIndex = 1, exerciseStableKey = key, exerciseName = key,
        category = "STRENGTH", restSeconds = 90, prescription = "$reps reps", setCount = 2, reps = reps, weightKg = 80.0,
        seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = role, setPrescriptions = sets(reps)
    )

    private fun sets(reps: Int) = List(2) { ProgramSetPrescription(it + 1, reps, 80.0, 0) }
    private fun planned(reps: Int) = PlannedPrescription("$reps reps", sets(reps), 90, "TEST")

    private fun skeleton(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>, scheduleChanged: Boolean) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = 7, request = request, periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)), items = items,
        weekDaySchedule = if (scheduleChanged) mapOf(1 to setOf(2)) else mapOf(1 to setOf(1))
    )
}
