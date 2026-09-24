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
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusExperimentalReadinessTest {
    @Test
    fun numericDistanceImprovementIsEligibleWithoutWinnerOrProductionAuthority() {
        val comparison = comparison(
            controlUnits = 2.0,
            experimentalUnits = 4.0,
            controlSessions = 1.0,
            experimentalSessions = 2.0,
            controlItems = listOf(item("candidate").copy(dayOfWeek = 2)),
            experimentalItems = listOf(item("candidate").copy(dayOfWeek = 1))
        )
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW, audit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.IMPROVED, audit.targetOutcomes.single().status)
        assertTrue(audit.targetOutcomes.single().controlWeeklyUnitsDistance!! > audit.targetOutcomes.single().experimentalWeeklyUnitsDistance!!)
        assertTrue(audit.shadowOnly)
        assertFalse(audit.productionAuthority)
        assertEquals(null, audit.winner)
    }

    @Test
    fun numericRegressionBlocksReadinessAndCollateralRegressionIsVisible() {
        val comparison = comparison(controlUnits = 4.0, experimentalUnits = 2.0, controlSessions = 2.0, experimentalSessions = 1.0)
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.REGRESSED, audit.targetOutcomes.single().status)
        assertTrue(audit.reasonCodes.contains("TARGET_REGRESSED"))
    }

    @Test
    fun mixedNumericDimensionsUseTheRegressionPrecedenceRule() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlUnits = 2.0, experimentalUnits = 4.0,
            controlSessions = 2.0, experimentalSessions = 1.0
        ))
        assertEquals(StimulusExperimentalTargetOutcomeStatus.REGRESSED, audit.targetOutcomes.single().status)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
    }

    @Test
    fun directionOnlyPresenceUsesDirectPresenceSemantics() {
        val target = qualityTarget(StimulusTargetNumericAuthority.DIRECTION_ONLY, null)
        val comparison = comparison(
            target = target,
            controlUnits = 0.0,
            experimentalUnits = 2.0,
            controlSessions = 0.0,
            experimentalSessions = 1.0,
            controlUnitsStatus = StimulusTargetControlStatus.DIRECT_ABSENT,
            experimentalUnitsStatus = StimulusTargetControlStatus.DIRECT_PRESENT,
            controlSessionsStatus = StimulusTargetControlStatus.DIRECT_ABSENT,
            experimentalSessionsStatus = StimulusTargetControlStatus.DIRECT_PRESENT,
            controlItems = listOf(item("candidate").copy(dayOfWeek = 2)),
            experimentalItems = listOf(item("candidate").copy(dayOfWeek = 1))
        )
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.IMPROVED, audit.targetOutcomes.single().status)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW, audit.status)
    }

    @Test
    fun deferredDirectionEvidenceRemainsInconclusive() {
        val target = qualityTarget(StimulusTargetNumericAuthority.DIRECTION_ONLY, null)
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            target = target, controlUnits = 0.0, experimentalUnits = 2.0,
            controlSessions = 0.0, experimentalSessions = 1.0,
            controlUnitsStatus = StimulusTargetControlStatus.DIRECT_ABSENT,
            experimentalUnitsStatus = StimulusTargetControlStatus.DIRECT_PRESENT,
            controlSessionsStatus = StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED,
            experimentalSessionsStatus = StimulusTargetControlStatus.DIRECT_PRESENT
        ))
        assertEquals(StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE, audit.targetOutcomes.single().status)
        assertEquals(StimulusExperimentalReadinessStatus.INCONCLUSIVE, audit.status)
    }

    @Test
    fun b6InvariantFailureAndUnexplainedIdentityFailClosed() {
        val comparison = comparison(
            controlUnits = 2.0,
            experimentalUnits = 4.0,
            controlSessions = 1.0,
            experimentalSessions = 2.0,
            materialization = listOf(StimulusPrescriptionMaterializationAudit(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = null,
                authorizedWeeklySetUnits = 1, materializedWeeklySetUnits = 1, targetCompatibleMaterializedUnits = 1,
                shortfall = 0, overrun = 1, prescriptionPreservedOrSubset = true,
                state = StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
            )),
            selectedCandidate = null
        )
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
        assertFalse(audit.materializationIntegrityPassed)
        assertTrue(audit.reasonCodes.contains("B6_AUTHORIZATION_OVERRUN"))
        assertFalse(audit.changeProvenanceClosed)
    }

    @Test
    fun noMaterialChangeIsExplicitAndUnresolvedAffectedEvidenceIsInconclusive() {
        val unchanged = comparison(controlUnits = 4.0, experimentalUnits = 4.0, controlSessions = 2.0, experimentalSessions = 2.0, sameIdentity = true)
        val unchangedAudit = StimulusExperimentalReadinessAuditEngine().audit(unchanged)
        assertEquals(StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE, unchangedAudit.status)

        val unresolved = comparison(controlUnits = null, experimentalUnits = null, controlSessions = null, experimentalSessions = null)
        val unresolvedAudit = StimulusExperimentalReadinessAuditEngine().audit(unresolved)
        assertEquals(StimulusExperimentalReadinessStatus.INCONCLUSIVE, unresolvedAudit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE, unresolvedAudit.targetOutcomes.single().status)
    }

    @Test
    fun heterogeneousSplitClosesB7PrescriptionProvenanceButDuplicatedPrefixDoesNot() {
        val authorized = heterogeneousAuthorization()
        val controlRows = listOf(item("candidate"))
        fun splitRows(duplicate: Boolean) = listOf(
            item("candidate").copy(localId = "split-a", dayOfWeek = 1, setCount = 1, reps = 5, weightKg = 80.0,
                restSeconds = 120, setPrescriptions = listOf(authorized.sets[0])),
            item("candidate").copy(localId = "split-b", dayOfWeek = 4, setCount = 2, reps = 5, weightKg = if (duplicate) 80.0 else 75.0,
                restSeconds = 120, setPrescriptions = if (duplicate) listOf(authorized.sets[0], authorized.sets[1]) else authorized.sets.drop(1))
        )
        fun run(duplicate: Boolean): StimulusExperimentalReadinessAudit {
            val comparison = comparison(
                controlUnits = 2.0, experimentalUnits = 4.0,
                controlSessions = 2.0, experimentalSessions = 2.0,
                controlItems = controlRows, experimentalItems = splitRows(duplicate),
                authorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(
                    StimulusPrescriptionAuthorization(
                        targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
                        owner = StimulusPrescriptionOwner("candidate", "STRENGTH", "B5_SELECTION"),
                        source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                        inputPrescription = authorized, plannedCompatibility = null, authorizedPrescription = authorized,
                        status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
                    )
                )),
                materialization = if (duplicate) listOf(StimulusPrescriptionMaterializationAudit(
                    targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
                    owner = StimulusPrescriptionOwner("candidate", "STRENGTH", "B5_SELECTION"),
                    authorizedWeeklySetUnits = 3, materializedWeeklySetUnits = 3, targetCompatibleMaterializedUnits = 3,
                    shortfall = 0, overrun = 0, prescriptionPreservedOrSubset = false,
                    state = StimulusPrescriptionMaterializationState.INVARIANT_FAILURE,
                    reasonCodes = listOf("B6_AUTHORIZED_SET_REUSED", "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED")
                )) else emptyList()
            )
            return StimulusExperimentalReadinessAuditEngine().audit(comparison)
        }
        val splitAudit = run(false)
        assertEquals(StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            splitAudit.changeAttributions.first { it.stableKey == "candidate" && it.source.name.startsWith("B6_") }.source)
        assertTrue(splitAudit.changeProvenanceClosed)
        val duplicateAudit = run(true)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, duplicateAudit.status)
        assertFalse(duplicateAudit.materializationIntegrityPassed)
        assertTrue(duplicateAudit.reasonCodes.contains("B6_AUTHORIZED_SET_REUSED"))
    }

    @Test
    fun removedIdentityRequiresCausalEvidenceBeforeDisplacementAttribution() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlUnits = 2.0, experimentalUnits = 4.0,
            controlSessions = 2.0, experimentalSessions = 2.0,
            selectedCandidate = StimulusSelectedCandidate("candidate", setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH",
                listOf("B5"), "REALIZATION_UNCLASSIFIED", 2, "STRENGTH")
        ))
        val removal = audit.changeAttributions.first { it.stableKey == "control" }
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, removal.source)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
    }

    @Test
    fun unrelatedGlobalCapacityEvidenceCannotProveRemovedOwnerDisplacement() {
        val candidate = selectedCandidate("C", "CANONICAL_STIMULUS_QUALITY_STRENGTH")
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("A").copy(selectionRole = "PRIMARY"), item("B")),
            experimentalItems = listOf(item("B"), item("C").copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH")),
            selectedCandidate = candidate,
            selectionTraces = listOf(selectionTrace("C", "CANONICAL_STIMULUS_QUALITY_STRENGTH", listOf("CAPACITY", "PLACEMENT")))
        ))
        val removal = audit.changeAttributions.first { it.stableKey == "A" }
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, removal.source)
        assertTrue(audit.reasonCodes.contains("CHANGE_PROVENANCE_UNCLOSED"))
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
    }

    @Test
    fun exactOwnerLocalDisplacementEvidenceClosesRemovalAttribution() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("A").copy(selectionRole = "PRIMARY"), item("B")),
            experimentalItems = listOf(item("B"), item("C").copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH")),
            selectedCandidate = selectedCandidate("C", "CANONICAL_STIMULUS_QUALITY_STRENGTH"),
            selectionTraces = listOf(selectionTrace("C", "CANONICAL_STIMULUS_QUALITY_STRENGTH", listOf("B5_SELECTED_IDENTITY"))),
            materializationTraces = listOf(ownerMaterializationTrace(
                stableKey = "A", role = "PRIMARY", reasonCodes = listOf("CAPACITY", "PLACEMENT", "NOT_MATERIALIZED")
            ))
        ))
        val removal = audit.changeAttributions.first { it.stableKey == "A" }
        assertEquals(StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT, removal.source)
        assertEquals("PRIMARY", removal.selectionRole)
        assertEquals(StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW, audit.status)
    }

    @Test
    fun ownerLocalParticipationWithoutRemovalProofIsInconclusive() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("A").copy(selectionRole = "PRIMARY"), item("B")),
            experimentalItems = listOf(item("B"), item("C").copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH")),
            selectedCandidate = selectedCandidate("C", "CANONICAL_STIMULUS_QUALITY_STRENGTH"),
            selectionTraces = listOf(selectionTrace("C", "CANONICAL_STIMULUS_QUALITY_STRENGTH", listOf("B5_SELECTED_IDENTITY"))),
            materializationTraces = listOf(ownerMaterializationTrace(
                stableKey = "A", role = "PRIMARY", reasonCodes = listOf("CAPACITY")
            ))
        ))
        val removal = audit.changeAttributions.first { it.stableKey == "A" }
        assertEquals(StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT, removal.source)
        assertEquals(StimulusExperimentalReadinessStatus.INCONCLUSIVE, audit.status)
        assertTrue(audit.reasonCodes.contains("REMOVAL_CAUSALITY_UNPROVEN"))
    }

    @Test
    fun ownerLocalEvidenceWithoutGovernedChangeIsUnexplained() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("A").copy(selectionRole = "PRIMARY")),
            experimentalItems = listOf(item("B")),
            selectedCandidate = null,
            materializationTraces = listOf(ownerMaterializationTrace(
                stableKey = "A", role = "PRIMARY", reasonCodes = listOf("CAPACITY", "NOT_MATERIALIZED")
            ))
        ))
        val removal = audit.changeAttributions.first { it.stableKey == "A" }
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, removal.source)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
    }

    @Test
    fun sameStableKeyDifferentRoleUsesExactRemovedOwnerIdentity() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("squat").copy(selectionRole = "PRIMARY_STRENGTH")),
            experimentalItems = listOf(item("squat").copy(selectionRole = "SUPPORT")),
            selectedCandidate = selectedCandidate("squat", "SUPPORT")
        ))
        assertTrue(audit.changeAttributions.any {
            it.stableKey == "squat" && it.selectionRole == "PRIMARY_STRENGTH" &&
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED
        })
        assertTrue(audit.changeAttributions.any {
            it.stableKey == "squat" && it.selectionRole == "SUPPORT" &&
                it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY
        })
    }

    @Test
    fun addedStableKeyWithWrongSelectionRoleCannotBorrowB5Authority() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("control")),
            experimentalItems = listOf(item("X").copy(selectionRole = "ROLE_B")),
            selectedCandidate = selectedCandidate("X", "ROLE_A")
        ))
        val addition = audit.changeAttributions.first { it.stableKey == "X" }
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, addition.source)
        assertEquals("ROLE_B", addition.selectionRole)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, audit.status)
    }

    private fun comparison(
        target: StimulusQualityTarget = qualityTarget(StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, StimulusTargetRange(4.0, 5.0, 6.0)),
        controlUnits: Double?,
        experimentalUnits: Double?,
        controlSessions: Double?,
        experimentalSessions: Double?,
        controlUnitsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.BELOW_BAND,
        experimentalUnitsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.WITHIN_BAND,
        controlSessionsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.BELOW_BAND,
        experimentalSessionsStatus: StimulusTargetControlStatus = StimulusTargetControlStatus.WITHIN_BAND,
        materialization: List<StimulusPrescriptionMaterializationAudit> = emptyList(),
        selectedCandidate: StimulusSelectedCandidate? = StimulusSelectedCandidate("candidate", setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", listOf("B5"), "REALIZATION_UNCLASSIFIED", 2, "STRENGTH"),
        sameIdentity: Boolean = false,
        controlItems: List<ProgramSkeletonItem>? = null,
        experimentalItems: List<ProgramSkeletonItem>? = null,
        authorizationPlan: StimulusPrescriptionAuthorizationPlan? = null,
        selectionTraces: List<StimulusCandidateSelectionTrace> = emptyList(),
        materializationTraces: List<StimulusCandidateMaterializationTrace>? = null
    ): StimulusSelectionProgramComparison {
        val request = ProgramSkeletonRequest("readiness", ProgramGoal.STRENGTH, 1, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 2)
        val controlKey = if (sameIdentity) "candidate" else "control"
        val control = skeleton(request, controlItems ?: listOf(item(controlKey)))
        val experimental = skeleton(request, experimentalItems ?: listOf(item("candidate")))
        val targetPlan = StimulusTargetPlan(listOf(target), emptyList(), emptyList())
        val controlAudit = StimulusTargetControlProgramAudit(1, listOf(qualityAudit(target, controlUnits, controlSessions, controlUnitsStatus, controlSessionsStatus)), emptyList())
        val experimentalAudit = StimulusTargetControlProgramAudit(1, listOf(qualityAudit(target, experimentalUnits, experimentalSessions, experimentalUnitsStatus, experimentalSessionsStatus)), emptyList())
        val selection = StimulusCandidateSelectionPlan(
            selectedCandidates = listOfNotNull(selectedCandidate), traces = selectionTraces,
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(control, experimental, targetPlan, selection, controlAudit, experimentalAudit)
        return comparison.copy(
            prescriptionAuthorizationPlan = authorizationPlan,
            prescriptionMaterializationAudits = materialization,
            materializationTraces = materializationTraces ?: comparison.materializationTraces
        )
    }

    private fun selectedCandidate(key: String, role: String) = StimulusSelectedCandidate(
        key, setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", listOf("B5"),
        "REALIZATION_UNCLASSIFIED", 2, role
    )

    private fun selectionTrace(key: String, role: String, reasons: List<String>) = StimulusCandidateSelectionTrace(
        targetId = "QUALITY:STRENGTH", strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, controlDirectCapabilityIdentities = emptyList(), selectionRequired = true,
        candidatePool = listOf(key), selectedStableKey = key, coveredByPreviouslySelectedStableKey = null,
        reasonCodes = reasons, selectedSelectionRole = role
    )

    private fun ownerMaterializationTrace(stableKey: String, role: String, reasonCodes: List<String>) = StimulusCandidateMaterializationTrace(
        targetId = "QUALITY:STRENGTH", selectedStableKey = stableKey, selectedAtB5 = false,
        presentInFinalExperimentalSkeleton = false, finalWeeklyOccurrences = 0, finalTotalSetUnits = 0,
        realizedTargetStatus = null, reasonCodes = reasonCodes, selectionRole = role
    )

    private fun heterogeneousAuthorization() = PlannedPrescription("heterogeneous", listOf(
        ProgramSetPrescription(1, 5, 80.0, 0), ProgramSetPrescription(2, 5, 75.0, 0), ProgramSetPrescription(3, 5, 70.0, 0)
    ), 120, "TEST")

    private fun qualityTarget(authority: StimulusTargetNumericAuthority, range: StimulusTargetRange?) = StimulusQualityTarget(
        quality = TrainableQuality.STRENGTH, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, numericAuthority = authority, baselineSource = null, baselineConfidence = null,
        weeklyDirectUnitsTarget = range, weeklyDirectSessionsTarget = range,
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null, exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun qualityAudit(target: StimulusQualityTarget, units: Double?, sessions: Double?, unitsStatus: StimulusTargetControlStatus, sessionsStatus: StimulusTargetControlStatus) = StimulusQualityControlProgramAudit(
        quality = target.quality, strategy = target.strategy, numericAuthority = target.numericAuthority,
        targetWeeklyDirectUnits = target.weeklyDirectUnitsTarget, plannedWeeklyDirectUnits = units, weeklyDirectUnitsStatus = unitsStatus,
        targetWeeklyDirectSessions = target.weeklyDirectSessionsTarget, plannedWeeklyDirectSessions = sessions, weeklyDirectSessionsStatus = sessionsStatus,
        exposureWeekFrequencyReference = null, plannedExposureWeekFrequency = null, exposureWeekFrequencyDelta = null, reasonCodes = emptyList()
    )

    private fun item(key: String) = ProgramSkeletonItem(
        localId = key, weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key, exerciseName = key,
        category = "STRENGTH", restSeconds = 90, prescription = "8 reps", setCount = 2, reps = 8, weightKg = 0.0,
        seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = "STRENGTH",
        setPrescriptions = List(2) { ProgramSetPrescription(it + 1, 8, 0.0, 0) }
    )

    private fun skeleton(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = request.durationWeeks * 7, request = request,
        periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)), items = items
    )
}
