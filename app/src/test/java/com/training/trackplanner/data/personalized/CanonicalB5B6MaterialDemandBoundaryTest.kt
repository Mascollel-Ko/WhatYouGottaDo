package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalB5B6MaterialDemandBoundaryTest {
    private val powerOwner = StimulusPrescriptionOwnerIdentity("ex_314df428", "CANONICAL_STIMULUS_QUALITY_POWER")
    private val taskOwner = StimulusPrescriptionOwnerIdentity("ex_33841b88", "CANONICAL_STIMULUS_TASK_ACCELERATION")
    private val unrelatedLegacyOwner = StimulusPrescriptionOwnerIdentity("legacy_drill", "PERFORMANCE_CONTINUITY")

    private fun item(owner: StimulusPrescriptionOwnerIdentity, material: Boolean = true) = PlannedExercise(
        stableKey = owner.stableKey,
        role = owner.selectionRole,
        reason = "test",
        priority = 100,
        targetSets = 3,
        material = material
    )

    private fun demand(
        vararg items: PlannedExercise,
        origins: List<MaterialDemandCandidateOrigin> = emptyList(),
        alternatives: List<MaterialDemandCandidateAlternative> = emptyList()
    ) = MaterialDemand(
        candidates = items.toList(), deferred = emptyMap(), audit = emptyMap(), candidateOrigins = origins,
        candidateAlternatives = alternatives
    )

    private fun origin(owner: StimulusPrescriptionOwnerIdentity) = MaterialDemandCandidateOrigin(
        owner = owner, gapCodes = setOf("RESISTANCE_FOUNDATIONAL_ONRAMP")
    )

    @Test
    fun materialDemandCandidateWithoutExactB6IsDeferredBeforeExecutableScheduling() {
        val candidate = item(unrelatedLegacyOwner)
        val input = demand(candidate, origins = listOf(origin(unrelatedLegacyOwner)))

        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            input, ExactPrescriptionAuthorizationProvider { _, _ -> null }
        )

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(listOf(origin(unrelatedLegacyOwner)), filtered.candidateOrigins)
        assertEquals(
            MaterialDemandExecutionDeferral.NO_EXACT_EXECUTABLE_PRESCRIPTION_AUTHORITY.reasonCode,
            filtered.deferred["${unrelatedLegacyOwner.stableKey}#${unrelatedLegacyOwner.selectionRole}"]
        )
        assertEquals(filtered.deferred, filtered.audit)
    }

    @Test
    fun missingAuthorityProviderFailsClosedForMaterialDemandCandidate() {
        val candidate = item(unrelatedLegacyOwner)
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(candidate, origins = listOf(origin(unrelatedLegacyOwner))), provider = null
        )

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(listOf(origin(unrelatedLegacyOwner)), filtered.candidateOrigins)
        assertEquals(
            MaterialDemandExecutionDeferral.NO_EXACT_EXECUTABLE_PRESCRIPTION_AUTHORITY.reasonCode,
            filtered.deferred["${unrelatedLegacyOwner.stableKey}#${unrelatedLegacyOwner.selectionRole}"]
        )
    }

    @Test
    fun unauthorizedFirstCandidateReturnsToBoundedSelectorAndUsesExactAuthorizedAlternative() {
        val original = item(unrelatedLegacyOwner)
        val authorizedOwner = StimulusPrescriptionOwnerIdentity("authorized_alternative", "COVERAGE_RESISTANCE_FOUNDATIONAL_ONRAMP")
        val alternative = item(authorizedOwner).copy(targetSets = 0)
        val exactPrescription = PlannedPrescription(
            "existing exact B6", listOf(ProgramSetPrescription(1, 6, 42.5, 0), ProgramSetPrescription(2, 6, 42.5, 0)),
            120, "EXACT_B6_EXISTING_AUTHORITY"
        )
        val provider = object : ExactPrescriptionAuthorizationProvider {
            override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
                exactPrescription.takeIf { StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) == authorizedOwner }
            override val authorizedOwners = mapOf(authorizedOwner to exactPrescription)
            override val b5SelectedQualityOwners = setOf(authorizedOwner)
        }
        val input = demand(
            original,
            origins = listOf(origin(unrelatedLegacyOwner)),
            alternatives = listOf(
                MaterialDemandCandidateAlternative(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), original),
                MaterialDemandCandidateAlternative(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), alternative)
            )
        )

        val resolved = reResolveMaterialDemandCandidatesWithExistingAuthority(input, provider)
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(resolved, provider)

        assertEquals(listOf(alternative.copy(targetSets = 2)), filtered.candidates)
        assertEquals(2, filtered.candidates.single().targetSets)
        assertTrue(filtered.unresolvedGapCodes.isEmpty())
        val trace = requireNotNull(filtered.candidateOrigins.single().authorityResolution)
        assertEquals(ExecutionAuthorityResolutionStatus.READY, trace.status)
        assertEquals(ExecutionAuthorityReturnTarget.MATERIAL_DEMAND_CANDIDATE_SELECTION, trace.returnTarget)
        assertEquals(unrelatedLegacyOwner, trace.originalOwner)
        assertEquals(authorizedOwner, trace.finalOwner)
        assertEquals(listOf(unrelatedLegacyOwner, authorizedOwner), trace.attemptedOwners)
        assertEquals(exactPrescription, provider.resolveOwnerPrescription(filtered.candidates.single()).let {
            (it as ExactOwnerPrescriptionResolution.Authorized).prescription
        })
    }

    @Test
    fun exhaustedCandidatesRemainAsTypedUnresolvedNeedWithoutExecutableMaterial() {
        val first = item(unrelatedLegacyOwner)
        val second = item(StimulusPrescriptionOwnerIdentity("second_unapproved", "COVERAGE_RESISTANCE_FOUNDATIONAL_ONRAMP"))
        val input = demand(
            first,
            origins = listOf(origin(unrelatedLegacyOwner)),
            alternatives = listOf(
                MaterialDemandCandidateAlternative(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), first),
                MaterialDemandCandidateAlternative(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), second)
            )
        )
        val noAuthority = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val resolved = reResolveMaterialDemandCandidatesWithExistingAuthority(input, noAuthority)
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(resolved, noAuthority)
        val trace = requireNotNull(filtered.candidateOrigins.single().authorityResolution)

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), filtered.unresolvedGapCodes)
        assertEquals(ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY, trace.status)
        assertEquals(ExecutionAuthorityResolutionReason.OWNER_CANDIDATES_EXHAUSTED, trace.reason)
        assertEquals(ExecutionAuthorityReturnTarget.MATERIAL_DEMAND_CANDIDATE_SELECTION, trace.returnTarget)
        assertEquals(listOf(unrelatedLegacyOwner, second.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) }), trace.attemptedOwners)
        assertEquals(
            MaterialDemandExecutionDeferral.NO_SUPPORTED_AUTHORIZED_OWNER_AFTER_RESELECTION.reasonCode,
            filtered.deferred["${unrelatedLegacyOwner.stableKey}#${unrelatedLegacyOwner.selectionRole}"]
        )
    }

    @Test
    fun candidateReselectionHasFiniteUniqueAttemptSet() {
        val first = item(unrelatedLegacyOwner)
        val duplicate = MaterialDemandCandidateAlternative(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), first)
        val input = demand(
            first,
            origins = listOf(origin(unrelatedLegacyOwner)),
            alternatives = listOf(duplicate, duplicate, duplicate)
        )
        val noAuthority = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val resolved = reResolveMaterialDemandCandidatesWithExistingAuthority(input, noAuthority)
        val trace = requireNotNull(resolved.candidateOrigins.single().authorityResolution)

        assertEquals(1, trace.attemptedOwners.size)
        assertEquals(ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY, trace.status)
    }

    @Test
    fun typedB6LoadRequirementReturnsToExplicitInputInsteadOfReselectingOrInventingLoad() {
        val candidate = item(unrelatedLegacyOwner)
        val alternativeOwner = StimulusPrescriptionOwnerIdentity("another_owner", "COVERAGE_RESISTANCE_FOUNDATIONAL_ONRAMP")
        val alternative = item(alternativeOwner)
        val recovery = ExecutionAuthorityResolution(
            status = ExecutionAuthorityResolutionStatus.NEEDS_LOAD_INPUT,
            reason = ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
            returnTarget = ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT,
            originalOwner = unrelatedLegacyOwner
        )
        val authorization = StimulusPrescriptionAuthorization(
            targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
            owner = StimulusPrescriptionOwner(unrelatedLegacyOwner.stableKey, unrelatedLegacyOwner.selectionRole),
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = null, plannedCompatibility = null, authorizedPrescription = null,
            status = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            authorityRecovery = recovery
        )
        val provider = StimulusPrescriptionAuthorizationPlan(listOf(authorization)).provider()
        val input = demand(
            candidate,
            origins = listOf(origin(unrelatedLegacyOwner)),
            alternatives = listOf(MaterialDemandCandidateAlternative(
                setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), alternative
            ))
        )

        val resolved = reResolveMaterialDemandCandidatesWithExistingAuthority(input, provider)
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(resolved, provider)
        val trace = requireNotNull(filtered.candidateOrigins.single().authorityResolution)

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), filtered.unresolvedGapCodes)
        assertEquals(ExecutionAuthorityResolutionStatus.NEEDS_LOAD_INPUT, trace.status)
        assertEquals(ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT, trace.returnTarget)
        assertEquals(listOf(unrelatedLegacyOwner), trace.attemptedOwners)
    }

    @Test
    fun nonMaterialCandidateOriginIsAlsoRemovedBeforeOptionalScheduling() {
        val candidate = item(unrelatedLegacyOwner, material = false)
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(candidate, origins = listOf(origin(unrelatedLegacyOwner))),
            ExactPrescriptionAuthorizationProvider { _, _ -> null }
        )

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(listOf(origin(unrelatedLegacyOwner)), filtered.candidateOrigins)
    }

    @Test
    fun exactB6MaterialDemandCandidateRemainsExecutable() {
        val candidate = item(powerOwner)
        val prescription = PlannedPrescription(
            "exact authority", listOf(ProgramSetPrescription(1, 5, 50.0, 0)), 90, "EXACT_B6_TEST"
        )
        val provider = object : ExactPrescriptionAuthorizationProvider {
            override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
                prescription.takeIf { StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) == powerOwner }

            override val authorizedOwners = mapOf(powerOwner to prescription)
            override val b5SelectedQualityOwners = setOf(powerOwner)
        }

        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(candidate, origins = listOf(origin(powerOwner))), provider
        )

        assertEquals(listOf(candidate), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun prescriptionLookupWithoutExactB5SelectionCannotAuthorizeCandidate() {
        val candidate = item(powerOwner)
        val prescription = PlannedPrescription(
            "orphaned B6 row", listOf(ProgramSetPrescription(1, 5, 50.0, 0)), 90, "EXACT_B6_TEST"
        )
        val provider = object : ExactPrescriptionAuthorizationProvider {
            override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
                prescription.takeIf { StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) == powerOwner }

            override val authorizedOwners = mapOf(powerOwner to prescription)
            override val b5SelectedQualityOwners = emptySet<StimulusPrescriptionOwnerIdentity>()
        }

        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(candidate, origins = listOf(origin(powerOwner))), provider
        )

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(setOf("RESISTANCE_FOUNDATIONAL_ONRAMP"), filtered.unresolvedGapCodes)
    }

    @Test
    fun exactApprovedTaskB6CanCrossMaterialDemandBoundary() {
        val definition = ApprovedBadmintonTaskProtocols.definitions.first()
        val owner = StimulusPrescriptionOwnerIdentity(definition.stableKey, definition.selectionRole)
        val grant = TaskProtocolB6Authorization(
            status = TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
            definition = definition,
            materializationActivityKind = PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL,
            attributedTasks = definition.authorizedTasks,
            transferEvidence = definition.authorizedTasks.associateWith {
                com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel.DIRECT
            }
        )
        val candidate = item(owner).copy(taskProtocolAuthorization = grant)

        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(candidate, origins = listOf(origin(owner))),
            ExactPrescriptionAuthorizationProvider { _, _ -> null }
        )

        assertEquals(listOf(candidate), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun taskCandidateRequiresTheExactApprovedProtocolDefinition() {
        val definition = ApprovedBadmintonTaskProtocols.definitions.first()
        val owner = StimulusPrescriptionOwnerIdentity(definition.stableKey, definition.selectionRole)
        val mismatchedDefinition = definition.copy(protocolId = "OTHER_PROTOCOL")
        val grant = TaskProtocolB6Authorization(
            status = TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
            definition = mismatchedDefinition,
            materializationActivityKind = PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL,
            attributedTasks = definition.authorizedTasks,
            transferEvidence = definition.authorizedTasks.associateWith {
                com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel.DIRECT
            }
        )
        val candidate = item(owner).copy(taskProtocolAuthorization = grant)

        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(candidate, origins = listOf(origin(owner))),
            ExactPrescriptionAuthorizationProvider { _, _ -> null }
        )

        assertTrue(filtered.candidates.isEmpty())
    }

    @Test
    fun regionalExactPrescriptionCanAuthorizeButOriginAloneCannot() {
        val owner = unrelatedLegacyOwner
        val candidate = item(owner, material = false)
        val prescription = PlannedPrescription(
            "regional exact", (1..3).map { ProgramSetPrescription(it, 8, 20.0, 0) }, 90, "REGIONAL_EXACT"
        )
        val plan = RegionalExperimentalTargetPlan(
            demand = demand(), targetByStableKey = emptyMap(), ownedKeys = emptySet(),
            authorizedPrescriptionBySelectionRole = mapOf(
                RegionalSelectionIdentity(owner.stableKey, owner.selectionRole) to prescription
            )
        )
        val input = demand(candidate, origins = listOf(origin(owner)))

        val denied = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(input, null)
        val authorized = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(input, null, plan)

        assertTrue(denied.candidates.isEmpty())
        assertEquals(listOf(candidate), authorized.candidates)
    }

    @Test
    fun demandOriginAloneDoesNotAuthorizeAndSameStableKeyWrongRoleDoesNotBorrow() {
        val wrongRole = item(powerOwner.copy(selectionRole = "OTHER_QUALITY_ROLE"))
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            demand(wrongRole, origins = listOf(origin(StimulusPrescriptionOwnerIdentity(
                wrongRole.stableKey, wrongRole.role
            )))),
            ExactPrescriptionAuthorizationProvider { _, _ -> null }
        )

        assertTrue(filtered.candidates.isEmpty())
        assertEquals(1, filtered.candidateOrigins.size)
    }

    @Test
    fun canonicalDemandMergeRetainsCandidateOriginWithoutPromotingItToAuthority() {
        val baseOrigin = origin(unrelatedLegacyOwner)
        val baseCandidate = item(unrelatedLegacyOwner)
        val replacement = item(unrelatedLegacyOwner.copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH"))
        val canonicalOrigin = origin(StimulusPrescriptionOwnerIdentity(replacement.stableKey, replacement.role))

        val merged = mergeCanonicalMaterialDemand(
            demand(baseCandidate, origins = listOf(baseOrigin)),
            demand(replacement, origins = listOf(canonicalOrigin))
        )

        assertEquals(listOf(replacement), merged.candidates)
        assertEquals(setOf(baseOrigin, canonicalOrigin), merged.candidateOrigins.toSet())
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
            merged, ExactPrescriptionAuthorizationProvider { _, _ -> null }
        )
        assertTrue(filtered.candidates.isEmpty())
        assertEquals(setOf(baseOrigin, canonicalOrigin), filtered.candidateOrigins.toSet())
    }

    @Test
    fun b5SelectedMaterialOwnerWithoutB6IsDeferredBeforeAllocation() {
        val owner = item(powerOwner)
        val unrelated = item(unrelatedLegacyOwner)
        val provider = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(owner, unrelated), setOf(powerOwner), provider
        )

        assertEquals(listOf(unrelated), filtered.candidates)
        assertEquals(
            CanonicalB5MaterialDemandDeferral.NO_EXECUTABLE_EXACT_B6_AUTHORITY.reasonCode,
            filtered.deferred["${powerOwner.stableKey}#${powerOwner.selectionRole}"]
        )
        assertEquals(filtered.deferred, filtered.audit)
    }

    @Test
    fun rejectedQualityFamiliesRemainB5IdentitiesAndAreDeferredBeforeFrequencyExpansion() {
        val rejected = listOf(
            "squat" to (TrainableQuality.STRENGTH to "CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE"),
            "bench" to (TrainableQuality.STRENGTH to "B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE"),
            "row" to (TrainableQuality.HYPERTROPHY to "HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE"),
            "press" to (TrainableQuality.HYPERTROPHY to "PLANNED_RESISTANCE_LOAD_UNAVAILABLE")
        ).map { (stableKey, evidence) ->
            val (quality, refusal) = evidence
            val role = "CANONICAL_STIMULUS_QUALITY_${quality.name}"
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:${quality.name}", quality = quality,
                owner = StimulusPrescriptionOwner(stableKey, role),
                source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = null, plannedCompatibility = null, authorizedPrescription = null,
                status = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                reasonCodes = listOf(refusal)
            )
        }
        val plan = StimulusPrescriptionAuthorizationPlan(rejected)
        val selected = rejected.map { authorization ->
            val owner = requireNotNull(authorization.owner)
            StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
        }.toSet()
        assertEquals(selected, plan.provider().b5SelectedQualityOwners)

        val sourceRows = rejected.map { authorization ->
            val owner = requireNotNull(authorization.owner)
            item(StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole))
        }
        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(*sourceRows.toTypedArray()), selected, plan.provider()
        )
        assertTrue(filtered.candidates.isEmpty())
        assertEquals(selected.size, filtered.deferred.size)
        assertEquals(selected.size, filtered.audit.size)
    }

    @Test
    fun exactB6AuthorityRetainsTheB5Owner() {
        val owner = item(powerOwner)
        val prescription = PlannedPrescription(
            "authorized Power fixture",
            listOf(ProgramSetPrescription(1, 5, 0.0, 0)),
            restSeconds = 75,
            weightSource = "AUTHORIZED_TEST"
        )
        val provider = object : ExactPrescriptionAuthorizationProvider {
            override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
                prescription.takeIf { StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) == powerOwner }
                    ?.copy(sets = prescription.sets.take(requestedSets))

            override val authorizedOwners = mapOf(powerOwner to prescription)
        }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(demand(owner), setOf(powerOwner), provider)

        assertEquals(listOf(owner), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun exactMovementB5B6GrantPassesTheSharedMaterialDemandBoundary() {
        val coreOwner = StimulusPrescriptionOwnerIdentity("bird_dog", "CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT")
        val candidate = item(coreOwner).copy(targetSets = 4)
        val prescription = PlannedPrescription(
            "4 direct Core sets × 8 repetitions",
            List(4) { ProgramSetPrescription(it + 1, 8, 0.0, 0) },
            restSeconds = 60,
            weightSource = "USER_APPROVED_PROJECT_POLICY_CORE_REPETITION_ANCHOR_8"
        )
        val plan = StimulusPrescriptionAuthorizationPlan(
            authorizations = emptyList(),
            movementAuthorizations = listOf(StimulusMovementB6Authorization(
                targetId = "MOVEMENT:CORE_DIRECT",
                owner = coreOwner,
                status = StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6,
                reasonCodes = listOf("B6_CONSUMED_EXACT_B4_CORE_RESIDUAL")
            )),
            movementOwnerPrescriptions = mapOf(coreOwner to prescription)
        )
        val provider = plan.provider()
        val input = demand(candidate, origins = listOf(origin(coreOwner)))

        val resolved = reResolveMaterialDemandCandidatesWithExistingAuthority(input, provider)
        val filtered = filterMaterialDemandCandidatesWithoutExactExecutionAuthority(resolved, provider)

        assertEquals(setOf(coreOwner), provider.b5SelectedMovementOwners)
        assertEquals(listOf(candidate), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
        assertEquals(ExecutionAuthorityResolutionStatus.READY, filtered.candidateOrigins.single().authorityResolution?.status)
    }

    @Test
    fun sameStableKeyWithDifferentRoleDoesNotBorrowB6Authority() {
        val wrongRole = item(powerOwner.copy(selectionRole = "OTHER_POWER_ROLE"))
        val provider = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(wrongRole), setOf(powerOwner), provider
        )

        assertEquals(listOf(wrongRole), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun nonMaterialB5OwnerIsNotTreatedAsAnExecutableDemand() {
        val nonMaterial = item(powerOwner, material = false)
        val provider = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(nonMaterial), setOf(powerOwner), provider
        )

        assertEquals(listOf(nonMaterial), filtered.candidates)
        assertFalse(filtered.deferred.containsKey("${powerOwner.stableKey}#${powerOwner.selectionRole}"))
    }

    @Test
    fun selectedTaskOwnerWithoutTaskB6IsDeferredBeforeLegacyFallbackMaterialization() {
        val selectedTask = item(taskOwner)
        val unrelated = item(unrelatedLegacyOwner)

        val filtered = filterCanonicalB5TaskDemandWithoutExecutableB6(
            demand(selectedTask, unrelated), setOf(taskOwner)
        )

        assertEquals(listOf(unrelated), filtered.candidates)
        assertEquals(
            CanonicalB5TaskMaterialDemandDeferral.NO_EXECUTABLE_TASK_B6_AUTHORITY.reasonCode,
            filtered.deferred["${taskOwner.stableKey}#${taskOwner.selectionRole}"]
        )
        assertEquals(filtered.deferred, filtered.audit)
    }

    @Test
    fun taskB5OwnerDoesNotTransferToSameStableKeyWithDifferentRole() {
        val otherRole = item(taskOwner.copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_POWER"))

        val filtered = filterCanonicalB5TaskDemandWithoutExecutableB6(
            demand(otherRole), setOf(taskOwner)
        )

        assertEquals(listOf(otherRole), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun taskB5DeferralLeavesNonMaterialDemandAndUnselectedTaskOwnersAlone() {
        val nonMaterialSelected = item(taskOwner, material = false)
        val unselectedTask = item(StimulusPrescriptionOwnerIdentity("other_drill", "CANONICAL_STIMULUS_TASK_FOOTWORK"))

        val filtered = filterCanonicalB5TaskDemandWithoutExecutableB6(
            demand(nonMaterialSelected, unselectedTask), setOf(taskOwner)
        )

        assertEquals(listOf(nonMaterialSelected, unselectedTask), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }
}
