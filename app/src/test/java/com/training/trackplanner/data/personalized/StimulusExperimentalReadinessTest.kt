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
    fun authorizedRegionalHypertrophyUnitsDoNotRegressTheAggregateQualityReference() {
        val comparison = regionalHypertrophyAggregateFixture(overrun = 0)
        assertEquals(16, comparison.experimental.items.filter { it.exerciseStableKey == "machine_chest_press" }.sumOf { it.setPrescriptions.size })
        assertEquals(0, comparison.control.items.filter { it.exerciseStableKey == "machine_chest_press" }.sumOf { it.setPrescriptions.size })
        assertEquals(8.0, regionalHypertrophyAddedWeeklyUnits(comparison), 0.0)
        val audit = StimulusExperimentalReadinessAuditEngine().audit(comparison)
        val aggregate = audit.targetOutcomes.single { it.targetId == "QUALITY:HYPERTROPHY" }

        assertEquals(StimulusExperimentalTargetOutcomeStatus.UNCHANGED, aggregate.status)
        assertEquals(8.0, aggregate.regionalHypertrophyUnitsExcludedFromAggregateComparison!!, 0.0)
        assertTrue(aggregate.reasonCodes.contains("AUTHORIZED_REGIONAL_HYPERTROPHY_EXCLUDED_FROM_AGGREGATE_QUALITY_COMPARISON"))
        assertFalse(audit.reasonCodes.contains("TARGET_REGRESSED"))
        assertTrue(audit.collateralRegressionFree)
    }

    @Test
    fun aggregateProjectionDoesNotHideARealRegionalOverrun() {
        val audit = StimulusExperimentalReadinessAuditEngine().audit(regionalHypertrophyAggregateFixture(overrun = 1))

        assertEquals(StimulusExperimentalTargetOutcomeStatus.UNCHANGED,
            audit.targetOutcomes.single { it.targetId == "QUALITY:HYPERTROPHY" }.status)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.REGRESSED,
            audit.targetOutcomes.single { it.targetId == "MOVEMENT:HORIZONTAL_PUSH" }.status)
        assertTrue(audit.reasonCodes.contains("TARGET_REGRESSED"))
    }

    @Test
    fun aggregateProjectionExcludesOnlyExactB6AuthorizedRegionalMaterial() {
        val comparison = regionalHypertrophyAggregateFixture(overrun = 0)
        val authorizationPlan = requireNotNull(comparison.prescriptionAuthorizationPlan)
        val withoutExactMovementB6 = comparison.copy(
            prescriptionAuthorizationPlan = authorizationPlan.copy(
                authorizations = authorizationPlan.authorizations.filterNot {
                    it.targetId == "MOVEMENT:HORIZONTAL_PUSH" && it.quality == TrainableQuality.HYPERTROPHY
                }
            )
        )

        assertEquals(8.0, regionalHypertrophyAddedWeeklyUnits(comparison), 0.0)
        assertEquals(0.0, regionalHypertrophyAddedWeeklyUnits(withoutExactMovementB6), 0.0)
    }

    @Test
    fun exactCoreB6MaterializationIsCountedByB7AndRejectedWithoutItsGrant() {
        val target = StimulusMovementTarget(
            movementCoverage = MovementCoverage.CORE_DIRECT,
            priority = TargetPriority.PRIMARY,
            reasonCodes = listOf("EXACT_CORE_NEED"),
            evidence = listOf("CANONICAL_DIRECT_CORE_PROFILE"),
            regionalDoseTargets = listOf(StimulusMovementDoseTarget(
                kind = StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET,
                numericAuthority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                weeklyTarget = 6.0,
                reasonCodes = listOf("USER_APPROVED_CORE_COLD_START"),
                shapeAuthority = StimulusMovementDoseShapeAuthority.CORE_DIRECT_SET_DOSE_EXACT_OWNER_SHAPE_REQUIRED,
                existingEquivalentExposure = 2.0,
                residualEquivalentExposure = 4.0,
                authorizedWholeSetUnits = 4
            ))
        )
        val owner = StimulusPrescriptionOwnerIdentity("bird-dog", "CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT")
        val ownerRef = StimulusPrescriptionOwner(owner.stableKey, owner.selectionRole)
        val coreWeightSource = "USER_APPROVED_PROJECT_POLICY_CORE_REPETITION_ANCHOR_8"
        val rows = (1..2).map { week -> item(owner.stableKey).copy(
            localId = "bird-$week", weekNumber = week, selectionRole = owner.selectionRole, setCount = 4,
            restSeconds = 60, weightSource = coreWeightSource,
            setPrescriptions = List(4) { ProgramSetPrescription(it + 1, 8, 0.0, 0,
                loadState = com.training.trackplanner.data.ProgramLoadState.NOT_APPLICABLE) }
        ) }
        val prescription = PlannedPrescription("4 sets × 8 reps per side", rows.first().setPrescriptions, 60,
            coreWeightSource)
        val authorization = StimulusPrescriptionAuthorization(
            targetId = target.targetId,
            quality = null,
            owner = ownerRef,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = prescription,
            plannedCompatibility = null,
            authorizedPrescription = prescription,
            status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            reasonCodes = listOf("CORE_B6_EXACT_OWNER_SHAPE"),
            executionAuthority = StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
        )
        val movementB6 = StimulusMovementB6Authorization(
            targetId = target.targetId, owner = owner,
            status = StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6,
            reasonCodes = listOf("B6_CONSUMED_EXACT_B4_CORE_RESIDUAL")
        )
        val selection = StimulusSelectedCandidate(
            stableKey = owner.stableKey, coveredTargetIds = setOf(target.targetId),
            primaryTargetId = target.targetId, selectionReasons = listOf("B5_EXACT_CORE_OWNER"),
            currentPrescriptionCompatibility = "EXACT_CORE_SHAPE", targetSetsFromExistingPrescription = 0,
            selectionRole = owner.selectionRole
        )
        val base = comparison(
            controlItems = emptyList(), experimentalItems = rows, selectedCandidate = null
        )
        val materialDemand = MaterialDemand(listOf(PlannedExercise(owner.stableKey, owner.selectionRole,
            "B4 Core residual", 100, targetSets = 4)), emptyMap(), emptyMap())
        val authorizationPlan = StimulusPrescriptionAuthorizationPlan(
            authorizations = listOf(authorization),
            movementAuthorizations = listOf(movementB6),
            movementOwnerPrescriptions = mapOf(owner to prescription)
        )
        val withCore = base.copy(
            targetPlan = StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target)),
            selectionPlan = base.selectionPlan.copy(selectedCandidates = listOf(selection), materialDemand = materialDemand),
            prescriptionAuthorizationPlan = authorizationPlan
        ).let { comparison ->
            comparison.copy(prescriptionMaterializationAudits = StimulusPrescriptionMaterializationAuditEngine()
                .audit(authorizationPlan, comparison.experimental, PlanningHistorySnapshot(
                    cutoff = java.time.LocalDate.of(2026, 9, 19), allConfirmedSets = emptyList(), exercises = emptyMap(),
                    metadata = emptyMap(), badmintonObjectives = emptyMap(), profilePrimaryGoal = "GENERAL_FITNESS",
                    strengthTrainingYears = 0.0, badmintonTrainingYears = 0.0, preferences = PersonalizedPlanningPreferences()
                )))
        }
        val audit = StimulusExperimentalReadinessAuditEngine().audit(withCore)
        assertEquals(StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED,
            withCore.prescriptionMaterializationAudits.single().state)
        assertEquals(StimulusExperimentalTargetOutcomeStatus.IMPROVED, audit.targetOutcomes.single().status)
        assertTrue(audit.materializationIntegrityPassed)

        val withoutGrant = withCore.copy(
            prescriptionAuthorizationPlan = authorizationPlan.copy(movementAuthorizations = emptyList())
        ).let { comparison ->
            comparison.copy(prescriptionMaterializationAudits = StimulusPrescriptionMaterializationAuditEngine()
                .audit(requireNotNull(comparison.prescriptionAuthorizationPlan), comparison.experimental, PlanningHistorySnapshot(
                    cutoff = java.time.LocalDate.of(2026, 9, 19), allConfirmedSets = emptyList(), exercises = emptyMap(),
                    metadata = emptyMap(), badmintonObjectives = emptyMap(), profilePrimaryGoal = "GENERAL_FITNESS",
                    strengthTrainingYears = 0.0, badmintonTrainingYears = 0.0, preferences = PersonalizedPlanningPreferences()
                )))
        }
        val rejected = StimulusExperimentalReadinessAuditEngine().audit(withoutGrant)
        assertFalse(rejected.materializationIntegrityPassed)
        assertTrue(rejected.reasonCodes.contains("B6_REJECTED_CORE_OWNER_WEEK_MATERIALIZED"))
        assertEquals(StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY, rejected.targetOutcomes.single().status)
    }

    @Test
    fun exactCoreB6ClosesSameExerciseRoleReplacementButMissingGrantDoesNot() {
        fun fixture(includeCoreGrant: Boolean, requireUserInput: Boolean = false): StimulusSelectionProgramComparison {
            val key = "ex_28347c1f"
            val oldRole = "COVERAGE_CORE_DIRECT"
            val newRole = "CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT"
            val targetId = "MOVEMENT:CORE_DIRECT"
            val oldOwner = StimulusPrescriptionOwnerIdentity(key, oldRole)
            val owner = StimulusPrescriptionOwnerIdentity(key, newRole)
            val target = StimulusMovementTarget(
                movementCoverage = MovementCoverage.CORE_DIRECT,
                priority = TargetPriority.PRIMARY,
                numericAuthority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                reasonCodes = listOf("EXACT_CORE_NEED"),
                evidence = listOf("CANONICAL_DIRECT_CORE_PROFILE"),
                regionalDoseTargets = listOf(StimulusMovementDoseTarget(
                    kind = StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET,
                    numericAuthority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                    weeklyTarget = 6.0,
                    reasonCodes = listOf("USER_APPROVED_CORE_COLD_START"),
                    shapeAuthority = StimulusMovementDoseShapeAuthority.CORE_DIRECT_SET_DOSE_EXACT_OWNER_SHAPE_REQUIRED,
                    existingEquivalentExposure = 2.0,
                    residualEquivalentExposure = 4.0,
                    authorizedWholeSetUnits = 4
                ))
            )
            val candidate = StimulusSelectedCandidate(
                stableKey = key,
                coveredTargetIds = setOf(targetId),
                primaryTargetId = targetId,
                selectionReasons = listOf("B5_EXACT_CORE_OWNER"),
                currentPrescriptionCompatibility = "EXACT_CORE_SHAPE",
                targetSetsFromExistingPrescription = 0,
                selectionRole = newRole
            )
            val disposition = StimulusCandidateDisposition(
                targetId = targetId,
                stableKey = key,
                canonicalSelectionRole = newRole,
                directTargetCandidate = true,
                selectionRequired = true,
                status = StimulusCandidateDispositionStatus.SELECTED,
                reasons = emptyList()
            )
            val coreWeightSource = "USER_APPROVED_PROJECT_POLICY_CORE_REPETITION_ANCHOR_8"
            val sets = List(4) { ProgramSetPrescription(
                setIndex = it + 1, reps = 8, weightKg = 0.0, seconds = 0,
                loadState = if (requireUserInput) com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED
                    else com.training.trackplanner.data.ProgramLoadState.NOT_APPLICABLE
            ) }
            val prescription = PlannedPrescription("4 sets × 8 reps per side", sets, 60, coreWeightSource)
            val materialized = item(key).copy(
                localId = "core-week-1", weekNumber = 1, selectionRole = newRole,
                setCount = 4, restSeconds = 60, weightSource = coreWeightSource,
                setPrescriptions = sets
            )
            val base = comparison(
                controlItems = listOf(item(key).copy(selectionRole = oldRole)),
                experimentalItems = listOf(materialized),
                selectedCandidate = candidate
            )
            val authorization = StimulusPrescriptionAuthorization(
                targetId = targetId,
                quality = null,
                owner = StimulusPrescriptionOwner(key, newRole),
                source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = prescription,
                plannedCompatibility = null,
                authorizedPrescription = prescription,
                status = if (requireUserInput) StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                    else StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                reasonCodes = listOf("CORE_B6_EXACT_OWNER_SHAPE"),
                executionAuthority = if (requireUserInput) StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT
                    else StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
                authorityRecovery = if (requireUserInput) ExecutionAuthorityResolution(
                    status = ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED,
                    reason = ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
                    returnTarget = ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT,
                    originalOwner = owner,
                    attemptedOwners = listOf(owner),
                    finalOwner = owner
                ) else null
            )
            val plan = StimulusPrescriptionAuthorizationPlan(
                authorizations = listOf(authorization),
                movementAuthorizations = if (includeCoreGrant) listOf(StimulusMovementB6Authorization(
                    targetId = targetId, owner = owner,
                    status = StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6,
                    reasonCodes = listOf("B6_CONSUMED_EXACT_B4_CORE_RESIDUAL")
                )) else emptyList(),
                movementOwnerPrescriptions = mapOf(owner to prescription)
            )
            val exactMaterializationTrace = StimulusCandidateMaterializationTrace(
                targetId = targetId,
                selectedStableKey = key,
                selectedAtB5 = true,
                directIdentityVerifiedAtSelection = true,
                presentInFinalExperimentalSkeleton = true,
                finalWeeklyOccurrences = 1,
                finalTotalSetUnits = 4,
                directIdentityStillValid = true,
                realizedTargetStatus = "DIRECT_PRESENT",
                reasonCodes = listOf("EXACT_CORE_MATERIALIZED"),
                selectionRole = newRole
            )
            return base.copy(
                targetPlan = StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target)),
                selectionPlan = base.selectionPlan.copy(
                    materialDemand = MaterialDemand(
                        listOf(PlannedExercise(key, newRole, "B4 Core residual", 100, targetSets = 4)),
                        emptyMap(), emptyMap()
                    ),
                    candidateDispositionIndex = StimulusCandidateDispositionIndex(listOf(disposition))
                ),
                prescriptionAuthorizationPlan = plan,
                materializationTraces = listOf(exactMaterializationTrace),
                nonSelectionProvenance = listOf(StimulusNonSelectionProvenance(
                    omittedControlOwner = oldOwner,
                    classification = StimulusNonSelectionClassification.CANONICAL_REPLACEMENT,
                    targetEvidence = listOf(StimulusTargetNonSelectionProvenance(
                        targetId = targetId,
                        classification = StimulusNonSelectionClassification.CANONICAL_REPLACEMENT,
                        disposition = disposition
                    ))
                ))
            )
        }

        val withExactGrant = StimulusExperimentalReadinessAuditEngine().audit(fixture(includeCoreGrant = true))
        val coreRemoval = withExactGrant.changeAttributions.single {
            it.stableKey == "ex_28347c1f" && it.selectionRole == "COVERAGE_CORE_DIRECT"
        }
        assertEquals(StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, coreRemoval.source)
        assertTrue(coreRemoval.targetIds.contains("MOVEMENT:CORE_DIRECT"))
        assertTrue(coreRemoval.reasonCodes.contains("B6_AUTHORIZED_CORE_DIRECT_MOVEMENT_REPLACEMENT"))
        assertTrue(coreRemoval.evidenceSources.contains("EXACT_B6_CORE_DIRECT_AUTHORITY"))
        assertFalse(coreRemoval.reasonCodes.contains("UNEXPLAINED_REMOVED_IDENTITY"))

        val awaitingLoadInput = StimulusExperimentalReadinessAuditEngine().audit(
            fixture(includeCoreGrant = true, requireUserInput = true)
        )
        val inputRequiredRemoval = awaitingLoadInput.changeAttributions.single {
            it.stableKey == "ex_28347c1f" && it.selectionRole == "COVERAGE_CORE_DIRECT"
        }
        assertEquals(StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY, inputRequiredRemoval.source)
        assertTrue(inputRequiredRemoval.evidenceSources.contains("EXACT_B6_CORE_DIRECT_AUTHORITY"))

        val withoutExactGrant = StimulusExperimentalReadinessAuditEngine().audit(fixture(includeCoreGrant = false))
        val unexplained = withoutExactGrant.changeAttributions.single {
            it.stableKey == "ex_28347c1f" && it.selectionRole == "COVERAGE_CORE_DIRECT"
        }
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED, unexplained.source)
        assertTrue(unexplained.reasonCodes.contains("UNEXPLAINED_REMOVED_IDENTITY"))
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
    fun rejectedQualityB6CannotAccompanyNewOrChangedExecutableOwnerWeek() {
        val role = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
        val owner = StimulusPrescriptionOwner("candidate", role)
        val rejected = StimulusPrescriptionAuthorization(
            targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = owner,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = heterogeneousAuthorization(), plannedCompatibility = null,
            authorizedPrescription = null,
            status = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            reasonCodes = listOf("B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE")
        )
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(rejected))
        val candidate = selectedCandidate("candidate", role)
        val before = item("candidate").copy(selectionRole = role)
        val after = before.copy(dayOfWeek = 3)
        val added = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(item("control")),
            experimentalItems = listOf(after),
            selectedCandidate = candidate,
            authorizationPlan = plan
        ))
        assertEquals(StimulusExperimentalReadinessStatus.NOT_ELIGIBLE, added.status)
        assertFalse(added.materializationIntegrityPassed)
        assertTrue(added.reasonCodes.contains("B6_REJECTED_QUALITY_OWNER_WEEK_MATERIALIZED"))

        val prescriptionChanged = after.copy(reps = 9, setPrescriptions = after.setPrescriptions.map { it.copy(reps = 9) })
        val changed = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(before),
            experimentalItems = listOf(prescriptionChanged),
            selectedCandidate = candidate,
            authorizationPlan = plan
        ))
        assertTrue(changed.reasonCodes.contains("B6_REJECTED_QUALITY_OWNER_WEEK_MATERIALIZED"))

        val placementOnly = StimulusExperimentalReadinessAuditEngine().audit(comparison(
            controlItems = listOf(before),
            experimentalItems = listOf(after),
            selectedCandidate = candidate,
            sameIdentity = true,
            authorizationPlan = plan
        ))
        assertFalse(placementOnly.reasonCodes.contains("B6_REJECTED_QUALITY_OWNER_WEEK_MATERIALIZED"))
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
            )),
            exactB5Disposition = true
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
            exactB5Disposition = replacement,
            authorizationPlan = StimulusPrescriptionAuthorizationPlan(listOf(StimulusPrescriptionAuthorization(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
                owner = StimulusPrescriptionOwner("squat", role),
                source = StimulusPrescriptionAuthorizationSource.CANONICAL_HISTORY_PRESCRIPTION,
                inputPrescription = authorized, plannedCompatibility = null, authorizedPrescription = authorized, status = status
            ))))
    }

    private fun regionalHypertrophyAggregateFixture(overrun: Int): StimulusSelectionProgramComparison {
        val target = qualityTarget(
            StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
            StimulusTargetRange(3.0, 5.0, 9.0)
        ).copy(quality = TrainableQuality.HYPERTROPHY)
        val dose = StimulusMovementDoseTarget(
            kind = StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
            numericAuthority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
            weeklyTarget = 8.0,
            reasonCodes = listOf("C35_FIXTURE"),
            shapeAuthority = StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION,
            existingEquivalentExposure = 0.0,
            residualEquivalentExposure = 8.0,
            authorizedWholeSetUnits = 8
        )
        val movement = StimulusMovementTarget(MovementCoverage.HORIZONTAL_PUSH, TargetPriority.PRIMARY,
            reasonCodes = listOf("C35_FIXTURE"), evidence = listOf("EXACT_MOVEMENT_NEED"), regionalDoseTargets = listOf(dose))
        val role = "CANONICAL_STIMULUS_MOVEMENT_HORIZONTAL_PUSH"
        val owner = StimulusPrescriptionOwnerIdentity("machine_chest_press", role)
        val ownerRef = StimulusPrescriptionOwner(owner.stableKey, owner.selectionRole)
        val selected = StimulusSelectedCandidate(
            stableKey = owner.stableKey,
            coveredTargetIds = setOf(movement.targetId),
            primaryTargetId = movement.targetId,
            selectionReasons = listOf("EXACT_B4_REGIONAL_RESIDUAL"),
            currentPrescriptionCompatibility = "B5_OWNER_FOR_B4_REGIONAL_RESIDUAL",
            targetSetsFromExistingPrescription = 0,
            selectionRole = role,
            probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZATION_UNCLASSIFIED
        )
        val prescription = PlannedPrescription("8 reps", List(8) {
            ProgramSetPrescription(it + 1, 8, 0.0, 0)
        }, 90, "USER_CALIBRATION_REQUIRED")
        val exactAuth = StimulusPrescriptionAuthorization(
            targetId = movement.targetId,
            quality = TrainableQuality.HYPERTROPHY,
            owner = ownerRef,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = prescription,
            plannedCompatibility = null,
            authorizedPrescription = prescription,
            status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        )
        val materialization = StimulusPrescriptionMaterializationAudit(
            targetId = movement.targetId,
            quality = TrainableQuality.HYPERTROPHY,
            owner = ownerRef,
            authorizedWeeklySetUnits = 8,
            materializedWeeklySetUnits = 8,
            targetCompatibleMaterializedUnits = 8,
            shortfall = 0,
            overrun = overrun,
            prescriptionPreservedOrSubset = true,
            state = StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED,
            executionAuthority = StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT,
            totalAuthorizedUnits = 16,
            totalMaterializedUnits = 16 + overrun * 2,
            totalCompatibleUnits = 16,
            totalShortfallUnits = 0,
            maximumWeeklyOverrun = overrun
        )
        val controlRow = item("control-h").copy(setCount = 12,
            setPrescriptions = List(12) { ProgramSetPrescription(it + 1, 8, 0.0, 0) })
        val controlRows = listOf(controlRow.copy(weekNumber = 1), controlRow.copy(weekNumber = 2))
        val movementRow = item(owner.stableKey).copy(selectionRole = owner.selectionRole, setCount = 8,
            setPrescriptions = prescription.sets)
        val base = comparison(
            target = target,
            controlUnits = 12.0,
            experimentalUnits = 20.0,
            controlSessions = 2.0,
            experimentalSessions = 2.0,
            controlItems = controlRows,
            experimentalItems = controlRows + listOf(movementRow.copy(weekNumber = 1), movementRow.copy(weekNumber = 2)),
            selectedCandidate = selected
        )
        return base.copy(
            targetPlan = StimulusTargetPlan(listOf(target), emptyList(), emptyList(), movementTargets = listOf(movement)),
            selectionPlan = base.selectionPlan.copy(
                selectedCandidates = listOf(selected),
                materialDemand = MaterialDemand(listOf(PlannedExercise(owner.stableKey, owner.selectionRole,
                    "C35 exact regional owner", 100, targetSets = 8)), emptyMap(), emptyMap())
            ),
            prescriptionAuthorizationPlan = StimulusPrescriptionAuthorizationPlan(
                authorizations = listOf(exactAuth),
                movementAuthorizations = listOf(StimulusMovementB6Authorization(
                    targetId = movement.targetId,
                    owner = owner,
                    status = StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6,
                    reasonCodes = listOf("EXACT_B6_FIXTURE")
                ))
            ),
            prescriptionMaterializationAudits = listOf(materialization)
        )
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
        materializationTraces: List<StimulusCandidateMaterializationTrace>? = null,
        exactB5Disposition: Boolean = false
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
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap()),
            candidateDispositionIndex = if (exactB5Disposition && selectedCandidate != null) {
                val disposition = StimulusCandidateDisposition(
                    targetId = "QUALITY:STRENGTH",
                    stableKey = selectedCandidate.stableKey,
                    canonicalSelectionRole = selectedCandidate.selectionRole,
                    directTargetCandidate = true,
                    selectionRequired = true,
                    status = StimulusCandidateDispositionStatus.SELECTED,
                    reasons = emptyList()
                )
                StimulusCandidateDispositionIndex(listOf(disposition))
            } else StimulusCandidateDispositionIndex()
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
