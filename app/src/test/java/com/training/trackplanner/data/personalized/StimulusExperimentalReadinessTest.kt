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
            experimentalSessionsStatus = StimulusTargetControlStatus.DIRECT_PRESENT,
            controlItems = listOf(item("candidate").copy(dayOfWeek = 2)),
            experimentalItems = listOf(item("candidate").copy(dayOfWeek = 1))
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
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, unresolvedAudit.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE, unresolvedAudit.targetOutcomes.single().status)
    }

    @Test
    fun equalProgramsWithTrueB6IntegrityViolationDoNotBypassB7AndBlockB8AndB9() {
        val comparison = comparison(
            controlUnits = 4.0, experimentalUnits = 4.0,
            controlSessions = 2.0, experimentalSessions = 2.0,
            sameIdentity = true,
            materialization = listOf(StimulusPrescriptionMaterializationAudit(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = null,
                authorizedWeeklySetUnits = 2, materializedWeeklySetUnits = 2, targetCompatibleMaterializedUnits = 2,
                shortfall = 0, overrun = 1, prescriptionPreservedOrSubset = true,
                state = StimulusPrescriptionMaterializationState.INVARIANT_FAILURE,
                reasonCodes = listOf("B6_AUTHORIZED_SET_REUSED")
            ))
        )
        val b7 = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, b7.status)
        assertFalse(b7.materializationIntegrityPassed)
        assertTrue(b7.reasonCodes.contains("B6_AUTHORIZED_SET_REUSED"))
        val withB7 = comparison.copy(experimentalReadinessAudit = b7)
        val b8 = StimulusProductionCutoverAuthorityAuditEngine().audit(withB7)
        assertEquals(StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED, b8.status)
        val b9 = StimulusProductionRouter().route(
            withB7, b8, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )
        assertEquals(StimulusProductionProgramSource.CONTROL, b9.decision.selectedSource)
    }

    @Test
    fun conflictOnlyEqualProgramsRemainNoMaterialChangeAndControlRoutes() {
        val owner = StimulusPrescriptionOwner("candidate", "STRENGTH", "B5_SELECTION")
        fun authorization(quality: TrainableQuality, reps: Int) = StimulusPrescriptionAuthorization(
            targetId = "QUALITY:${quality.name}", quality = quality, owner = owner,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = heterogeneousAuthorization(), plannedCompatibility = null,
            authorizedPrescription = heterogeneousAuthorization().copy(sets = heterogeneousAuthorization().sets.map { it.copy(reps = reps) }),
            status = StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY
        )
        val comparison = comparison(
            sameIdentity = true,
            authorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(
                authorization(TrainableQuality.STRENGTH, 5),
                authorization(TrainableQuality.HYPERTROPHY, 8)
            ))
        )
        val b7 = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        assertEquals(StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE, b7.status)
        val withB7 = comparison.copy(experimentalReadinessAudit = b7)
        val b8 = StimulusProductionCutoverAuthorityAuditEngine().audit(withB7)
        assertEquals(StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE, b8.status)
        val b9 = StimulusProductionRouter().route(
            withB7, b8, StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE
        )
        assertEquals(StimulusProductionProgramSource.CONTROL, b9.decision.selectedSource)
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
            experimentalItems = listOf(item("squat").copy(selectionRole = "SUPPORT", dayOfWeek = 2)),
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
    fun canonicalB5RoleReplacementRequiresExactB6Authorization() {
        val newOwner = StimulusPrescriptionOwner("squat", "CANONICAL_STIMULUS_QUALITY_STRENGTH", "CANONICAL_HISTORY_PRESCRIPTION")
        val current = item("squat")
        val authorized = PlannedPrescription(current.prescription, current.setPrescriptions, current.restSeconds, current.weightSource)
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(current.copy(selectionRole = "LEGACY_PRIMARY_STRENGTH")),
            experimentalItems = listOf(current.copy(selectionRole = newOwner.selectionRole)),
            selectedCandidate = selectedCandidate("squat", newOwner.selectionRole),
            authorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(
                StimulusPrescriptionAuthorization(
                    targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = newOwner,
                    source = StimulusPrescriptionAuthorizationSource.CANONICAL_HISTORY_PRESCRIPTION,
                    inputPrescription = authorized, plannedCompatibility = null, authorizedPrescription = authorized,
                    status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
                )
            ))
        ))
        val replacement = audit.changeAttributions.first { it.selectionRole == "LEGACY_PRIMARY_STRENGTH" }
        assertEquals(StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, replacement.source)
        assertEquals(listOf("QUALITY:STRENGTH"), replacement.targetIds)
        assertTrue(audit.changeProvenanceClosed)
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

    @Test
    fun c9ExactReusedOwnerUsesCompatibleOrRepairAuthorityLocally() {
        for ((status, source) in listOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE to StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR to StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
        )) {
            val c = c9Fixture(replacement = false, status = status)
            val audit = StimulusExperimentalReadinessAuditEngine().audit(c)
            assertTrue(audit.toString(), audit.changeProvenanceClosed)
            val change = audit.changeAttributions.single { it.source == source }
            assertEquals("squat", change.stableKey)
            assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", change.selectionRole)
            assertEquals(listOf("QUALITY:STRENGTH"), change.targetIds)
        }
    }

    @Test
    fun c9ReplacementRequiresB6AndValidMaterializedPrescription() {
        val good = c9Fixture()
        assertTrue(StimulusExperimentalReadinessAuditEngine().audit(good).changeProvenanceClosed)
        c9AssertUnclosed(good.copy(prescriptionAuthorizationPlan = null))
        val bad = good.experimental.copy(items = good.experimental.items.map { row ->
            row.copy(setPrescriptions = row.setPrescriptions.map { it.copy(weightKg = 999.0) })
        })
        c9AssertUnclosed(good.copy(experimental = bad))
    }

    @Test
    fun c9UnrelatedRoleCannotBorrowSameKeyReplacementAuthority() {
        c9AssertUnclosed(c9Fixture(role = "UNRELATED_SUPPORT"))
    }

    @Test
    fun c9TargetAndQualityMustAgreeForReplacementAndReusedPrescription() {
        for (replacement in listOf(true, false)) {
            val c = c9Fixture(replacement = replacement)
            val plan = requireNotNull(c.prescriptionAuthorizationPlan)
            c9AssertUnclosed(c.copy(prescriptionAuthorizationPlan = plan.copy(authorizations = plan.authorizations.map {
                it.copy(targetId = "QUALITY:HYPERTROPHY")
            })))
            c9AssertUnclosed(c.copy(selectionPlan = c.selectionPlan.copy(selectedCandidates = c.selectionPlan.selectedCandidates.map {
                it.copy(coveredTargetIds = setOf("QUALITY:HYPERTROPHY"))
            })))
        }
    }

    @Test
    fun c9ConflictingQualityAuthorityCannotCloseAnyOwnerChange() {
        for (replacement in listOf(true, false)) {
            val c = c9Fixture(replacement = replacement)
            val plan = requireNotNull(c.prescriptionAuthorizationPlan)
            val strength = plan.authorizations.single()
            val hypertrophy = strength.copy(targetId = "QUALITY:HYPERTROPHY", quality = TrainableQuality.HYPERTROPHY,
                authorizedPrescription = strength.authorizedPrescription!!.copy(sets = strength.authorizedPrescription.sets.map {
                    it.copy(reps = 12, targetRpeMin = 7.0)
                }))
            c9AssertUnclosed(c.copy(prescriptionAuthorizationPlan = plan.copy(authorizations = listOf(strength, hypertrophy))))
        }
    }

    @Test
    fun c9ReplacementRejectsContradictoryDisappearanceTrace() {
        val c = c9Fixture()
        c9AssertUnclosed(c.copy(materializationTraces = c.materializationTraces + ownerMaterializationTrace(
            "squat", "LEGACY_PRIMARY_STRENGTH", listOf("SELECTION_TARGET_IDENTITY_MATERIALIZED")
        )))
    }

    @Test
    fun c9UnrelatedRemovedOwnerStillNeedsDisappearanceEvidence() {
        val c = c9Fixture()
        val control = c.control.copy(items = c.control.items + item("curl").copy(selectionRole = "ACCESSORY"))
        val recomputed = StimulusSelectionProgramComparisonEngine().compare(control, c.experimental,
            c.targetPlan, c.selectionPlan, c.controlAudit, c.experimentalAudit)
            .copy(prescriptionAuthorizationPlan = c.prescriptionAuthorizationPlan)
        val audit = c9AssertUnclosed(recomputed)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit.changeAttributions.single { it.stableKey == "curl" }.source)
    }

    @Test
    fun c9OwnerAuthorityDoesNotExplainAnotherOwnersPrescription() {
        val c = c9Fixture()
        val control = c.control.copy(items = c.control.items + item("curl"))
        val experimental = c.experimental.copy(items = c.experimental.items + item("curl").copy(restSeconds = 240))
        val recomputed = StimulusSelectionProgramComparisonEngine().compare(control, experimental,
            c.targetPlan, c.selectionPlan, c.controlAudit, c.experimentalAudit)
            .copy(prescriptionAuthorizationPlan = c.prescriptionAuthorizationPlan)
        val audit = c9AssertUnclosed(recomputed)
        assertTrue(audit.changeAttributions.any { it.stableKey == "curl" && "UNEXPLAINED_PRESCRIPTION_CHANGE" in it.reasonCodes })
    }

    private fun c9AssertUnclosed(c: StimulusSelectionProgramComparison): StimulusExperimentalReadinessAudit {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(c)
        assertFalse(audit.toString(), audit.changeProvenanceClosed)
        assertTrue(audit.reasonCodes.contains("CHANGE_PROVENANCE_UNCLOSED"))
        assertTrue(audit.changeAttributions.any { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
            it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT })
        return audit
    }

    private fun c9Fixture(
        replacement: Boolean = true,
        role: String = "CANONICAL_STIMULUS_QUALITY_STRENGTH",
        status: StimulusPrescriptionAuthorizationStatus = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
    ): StimulusSelectionProgramComparison {
        val before = item("squat").copy(selectionRole = if (replacement) "LEGACY_PRIMARY_STRENGTH" else role)
        val after = item("squat").copy(selectionRole = role, restSeconds = 120)
        val authorized = PlannedPrescription(after.prescription, after.setPrescriptions, after.restSeconds, after.weightSource)
        return comparison(controlItems = listOf(before), experimentalItems = listOf(after),
            selectedCandidate = selectedCandidate("squat", role),
            authorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(StimulusPrescriptionAuthorization(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
                owner = StimulusPrescriptionOwner("squat", role),
                source = StimulusPrescriptionAuthorizationSource.CANONICAL_HISTORY_PRESCRIPTION,
                inputPrescription = authorized, plannedCompatibility = null, authorizedPrescription = authorized, status = status
            ))))
    }

    private fun comparison(
        target: StimulusQualityTarget = qualityTarget(StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, StimulusTargetRange(4.0, 5.0, 6.0)),
        controlUnits: Double? = 2.0,
        experimentalUnits: Double? = 4.0,
        controlSessions: Double? = 2.0,
        experimentalSessions: Double? = 2.0,
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
        priority = TargetPriority.PRIMARY, historyDirectCapabilityIdentities = emptyList(), selectionRequired = true,
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
