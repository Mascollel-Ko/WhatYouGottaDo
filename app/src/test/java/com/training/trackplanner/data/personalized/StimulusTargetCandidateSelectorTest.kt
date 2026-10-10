package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreProfile
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.analysis.core.CoreDirectTarget
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class StimulusTargetCandidateSelectorTest {
    @Test
    fun regionalResidualSkipsTopRankedCandidateWithoutExactB6AndUsesNextEligibleOwner() {
        val first = exercise("a_candidate")
        val second = exercise("b_candidate")
        val base = fixture(
            listOf(first, second),
            listOf(relation("a_candidate", quality = TrainableQuality.HYPERTROPHY).copy(regionQualifier = PhysicalQualityRegion.ARMS),
                relation("b_candidate", quality = TrainableQuality.HYPERTROPHY).copy(regionQualifier = PhysicalQualityRegion.ARMS))
        )
        val snapshot = base.snapshot.copy(metadata = base.snapshot.metadata.mapValues { (_, value) ->
            value.copy(programSlot = "BICEPS_ACCESSORY")
        })
        val target = StimulusMovementTarget(
            movementCoverage = MovementCoverage.ARMS_BICEPS,
            priority = TargetPriority.PRIMARY,
            reasonCodes = listOf("B3_ADDRESS"),
            evidence = listOf("direct exposure gap"),
            regionalDoseTargets = listOf(StimulusMovementDoseTarget(
                kind = StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
                numericAuthority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                weeklyTarget = 8.0,
                reasonCodes = listOf("COLD_START"),
                shapeAuthority = StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION,
                existingEquivalentExposure = 4.0,
                residualEquivalentExposure = 4.0,
                authorizedWholeSetUnits = 4,
                residualReasonCodes = listOf("B4_RESIDUAL_DOSE_AUTHORIZED")
            ))
        )
        val plan = StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target))
        val selection = StimulusTargetCandidateSelector().build(
            targetPlan = plan,
            snapshot = snapshot,
            state = base.state,
            request = base.request,
            physicalQualityCatalog = base.catalog,
            movementCandidateRejectionReason = { _, key, _, _ ->
                if (key == "a_candidate") "NO_EXECUTABLE_B6" else null
            }
        )

        assertEquals(listOf("a_candidate", "b_candidate"), selection.traces.single().candidatePool)
        assertEquals("b_candidate", selection.selectedCandidates.single().stableKey)
        assertEquals(4, selection.materialDemand.candidates.single().targetSets)
        assertEquals("NO_EXECUTABLE_B6", selection.traces.single().candidateRejectionReasons["a_candidate"])
        assertTrue(selection.traces.single().reasonCodes.contains("B5_SKIPPED_HIGHER_RANKED_CANDIDATE_WITHOUT_EXACT_B6"))
        val rejected = selection.candidateDispositionIndex.entries.single { it.stableKey == "a_candidate" }
        assertEquals(StimulusCandidateDispositionStatus.MATERIALIZATION_FAILED, rejected.status)
    }

    @Test
    fun regionalResidualDoesNotMaterializeWhenEveryRankedOwnerLacksExactB6() {
        val exercise = exercise("a_candidate")
        val base = fixture(listOf(exercise), listOf(
            relation("a_candidate", quality = TrainableQuality.HYPERTROPHY).copy(regionQualifier = PhysicalQualityRegion.ARMS)
        ))
        val snapshot = base.snapshot.copy(metadata = base.snapshot.metadata.mapValues { (_, value) ->
            value.copy(programSlot = "BICEPS_ACCESSORY")
        })
        val target = StimulusMovementTarget(
            MovementCoverage.ARMS_BICEPS, TargetPriority.PRIMARY,
            reasonCodes = listOf("B3_ADDRESS"), evidence = listOf("direct exposure gap"),
            regionalDoseTargets = listOf(StimulusMovementDoseTarget(
                StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
                StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                8.0, listOf("COLD_START"),
                shapeAuthority = StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION,
                existingEquivalentExposure = 0.0, residualEquivalentExposure = 4.0,
                authorizedWholeSetUnits = 4, residualReasonCodes = listOf("B4_RESIDUAL_DOSE_AUTHORIZED")
            ))
        )
        val selection = StimulusTargetCandidateSelector().build(
            StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target)),
            snapshot, base.state, base.request, base.catalog,
            movementCandidateRejectionReason = { _, _, _, _ -> "NO_EXECUTABLE_B6" }
        )

        assertTrue(selection.materialDemand.candidates.isEmpty())
        assertTrue(selection.selectedCandidates.isEmpty())
        assertEquals("MOVEMENT_TARGET_HAS_NO_B5_OWNER_WITH_EXACT_B6_AUTHORITY", selection.traces.single().reasonCodes.first())
        assertEquals("NO_EXECUTABLE_B6", selection.traces.single().candidateRejectionReasons["a_candidate"])
    }

    @Test
    fun forcedPreviewCandidateStillMustPassCanonicalB5AndExactB6Checks() {
        val first = exercise("a_candidate")
        val second = exercise("b_candidate")
        val base = fixture(
            listOf(first, second),
            listOf(relation("a_candidate", quality = TrainableQuality.HYPERTROPHY).copy(regionQualifier = PhysicalQualityRegion.ARMS),
                relation("b_candidate", quality = TrainableQuality.HYPERTROPHY).copy(regionQualifier = PhysicalQualityRegion.ARMS))
        )
        val snapshot = base.snapshot.copy(metadata = base.snapshot.metadata.mapValues { (_, value) ->
            value.copy(programSlot = "BICEPS_ACCESSORY")
        })
        val target = StimulusMovementTarget(
            MovementCoverage.ARMS_BICEPS, TargetPriority.PRIMARY,
            reasonCodes = listOf("B3_ADDRESS"), evidence = listOf("direct exposure gap"),
            regionalDoseTargets = listOf(StimulusMovementDoseTarget(
                StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
                StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                8.0, listOf("COLD_START"),
                shapeAuthority = StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION,
                existingEquivalentExposure = 4.0, residualEquivalentExposure = 4.0,
                authorizedWholeSetUnits = 4, residualReasonCodes = listOf("B4_RESIDUAL_DOSE_AUTHORIZED")
            ))
        )
        val selection = StimulusTargetCandidateSelector().build(
            StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target)),
            snapshot, base.state, base.request, base.catalog,
            movementCandidateRejectionReason = { _, key, _, _ ->
                if (key == "b_candidate") "NO_EXECUTABLE_B6" else null
            },
            forcedCandidateByTarget = mapOf(target.targetId to "b_candidate")
        )

        assertTrue("a user-requested preview candidate still fails closed when B6 rejects it",
            selection.selectedCandidates.isEmpty())
        assertTrue(selection.materialDemand.candidates.isEmpty())
        assertEquals("NO_EXECUTABLE_B6", selection.traces.single().candidateRejectionReasons["b_candidate"])
    }

    @Test
    fun strengthTargetWithoutAnApprovedCandidateRemainsATypedShortfall() {
        val offListExercise = exercise("ex_8e4bf08e")
        val fixture = fixture(listOf(offListExercise), listOf(relation("ex_8e4bf08e")))

        val result = select(qualityPlan(), fixture, emptyList())

        assertTrue(result.selectedCandidates.isEmpty())
        assertTrue(result.materialDemand.candidates.isEmpty())
        assertEquals(
            listOf(StimulusStrengthShortfall("QUALITY:STRENGTH", StimulusStrengthShortfallReason.NO_ELIGIBLE_STRENGTH_EXERCISE)),
            result.strengthShortfalls
        )
        val disposition = result.candidateDispositionIndex.entries.single()
        assertEquals(StimulusCandidateDispositionStatus.NOT_RELEVANT_TO_TARGET, disposition.status)
        assertTrue(StimulusCandidateDispositionReason.STRENGTH_CAPABILITY_NOT_APPROVED in disposition.reasons)
    }

    @Test
    fun allTenApprovedStrengthIdentitiesRemainCanonicalB5CandidatesEvenWithSupportiveLegacyRelations() {
        val approved = listOf(
            "barbell_back_squat", "ex_c5043892", "barbell_deadlift", "ex_e41f4c2b", "ex_e41e8dcf",
            "barbell_bench_press", "ex_3a7d3eda", "ex_32219f7a", "ex_79f3bdbe", "ex_bb4b4276"
        )
        val fixture = fixture(approved.map(::exercise), approved.map {
            relation(it, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY)
        })

        val result = select(qualityPlan(), fixture, emptyList())

        assertEquals(approved.toSet(), result.traces.single().candidatePool.toSet())
        approved.forEach { stableKey ->
            val disposition = result.candidateDispositionIndex.entries.single { it.stableKey == stableKey }
            assertTrue("$stableKey must be B5 eligible by the exact approved capability", disposition.directTargetCandidate)
            assertFalse("$stableKey must not be rejected as lacking direct capability",
                StimulusCandidateDispositionReason.NO_DIRECT_CAPABILITY in disposition.reasons)
        }
    }

    @Test
    fun strengthColdStartB4BudgetIsPassedToB5AsOneSelectedAnchorDemand() {
        val key = "barbell_bench_press"
        val fixture = fixture(listOf(exercise(key)), listOf(relation(key)))
        val coldStart = target(
            quality = TrainableQuality.STRENGTH,
            priority = TargetPriority.PRIMARY,
            strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            authority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
            weekly = StimulusTargetRange(4.0, 4.0, 4.0)
        ).copy(reasonCodes = listOf(
            "USER_APPROVED_PROJECT_POLICY_STRENGTH_COLD_START_4_DIRECT_SETS_PER_SELECTED_ANCHOR_WEEK",
            "STRENGTH_ANCHOR_VARIANTS_SHARE_ONE_WEEKLY_BUDGET"
        ))

        val selection = select(qualityPlan().copy(qualityTargets = listOf(coldStart)), fixture, emptyList())

        assertEquals(key, selection.selectedCandidates.single().stableKey)
        assertEquals(4, selection.materialDemand.candidates.single().targetSets)
        assertTrue(selection.materialDemand.candidates.single().role.contains("QUALITY_STRENGTH"))
    }

    @Test
    fun directCandidatesBeatSupportiveCandidatesByEligibility() {
        val supportive = exercise("supportive")
        val direct = exercise("barbell_back_squat")
        val fixture = fixture(
            exercises = listOf(supportive, direct),
            relations = listOf(
                relation("supportive", StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY),
                relation("barbell_back_squat", StimulusCapabilityLevel.DIRECT_CAPABILITY)
            )
        )
        val plan = qualityPlan()
        val result = select(plan, fixture, emptyList())
        assertEquals(listOf("barbell_back_squat"), result.materialDemand.candidates.map { it.stableKey })
        assertFalse(result.traces.single().candidatePool.contains("supportive"))
        val supportiveDisposition = result.candidateDispositionIndex.entries.single { it.stableKey == "supportive" }
        assertEquals(StimulusCandidateDispositionStatus.NOT_RELEVANT_TO_TARGET, supportiveDisposition.status)
        assertEquals(listOf(StimulusCandidateDispositionReason.STRENGTH_CAPABILITY_NOT_APPROVED), supportiveDisposition.reasons)
    }

    @Test
    fun targetCompatibleHistoryRanksBeforeGenericHistoryWithoutWeightedScore() {
        val generic = exercise("barbell_back_squat")
        val familiar = exercise("barbell_deadlift")
        val fixture = fixture(
            exercises = listOf(generic, familiar),
            relations = listOf(relation("barbell_back_squat"), relation("barbell_deadlift")),
            history = listOf(
                PlanningSetRecord(LocalDate.of(2026, 9, 1), "barbell_back_squat", "generic", "STRENGTH", 1, 10, 0.0, 0, 8.0),
                PlanningSetRecord(LocalDate.of(2026, 9, 2), "barbell_deadlift", "familiar", "STRENGTH", 1, 5, 0.0, 0, 8.0)
            )
        )
        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("barbell_deadlift", result.selectedCandidates.single().stableKey)
        assertTrue(result.traces.single().reasonCodes.contains("SELECTION_IDENTITY_PRESENT"))
        val omitted = result.candidateDispositionIndex.entries.single { it.targetId == "QUALITY:STRENGTH" && it.stableKey == "barbell_back_squat" }
        assertEquals(StimulusCandidateDispositionStatus.ELIGIBLE_NOT_SELECTED, omitted.status)
        assertEquals("barbell_deadlift", omitted.selectedInstead?.stableKey)
        assertEquals(StimulusCandidateRankingField.TARGET_COMPATIBLE_HISTORY, omitted.firstDifferingField)
        assertEquals(StimulusCandidateDispositionReason.LOWER_RANK_THAN_SELECTED_CANDIDATE, omitted.reasons.single())
    }

    @Test
    fun stableKeyTieBreakIsExplicitAndDispositionOrderIsDeterministic() {
        val fixture = fixture(
            exercises = listOf(exercise("barbell_deadlift"), exercise("barbell_back_squat")),
            relations = listOf(relation("barbell_deadlift"), relation("barbell_back_squat"))
        )
        val first = select(qualityPlan(), fixture, emptyList())
        val second = select(qualityPlan(), fixture, emptyList())
        assertEquals(first.candidateDispositionIndex, second.candidateDispositionIndex)
        assertEquals("barbell_back_squat", first.selectedCandidates.single().stableKey)
        val omitted = first.candidateDispositionIndex.entries.single { it.stableKey == "barbell_deadlift" }
        assertEquals(StimulusCandidateDispositionStatus.ELIGIBLE_NOT_SELECTED, omitted.status)
        assertEquals(StimulusCandidateRankingField.STABLE_KEY, omitted.firstDifferingField)
        assertEquals(StimulusCandidateDispositionReason.DETERMINISTIC_STABLE_KEY_TIE_BREAK, omitted.reasons.single())
        assertEquals(omitted.candidateRanking?.copy(stableKey = "barbell_back_squat"), omitted.selectedInsteadRanking)
    }

    @Test
    fun noMinimumAndExcludedOwnersReceiveTypedNonSelectionReasons() {
        val candidate = exercise("barbell_back_squat")
        val fixture = fixture(listOf(candidate), listOf(relation("barbell_back_squat")))
        val noDemandPlan = qualityPlan().copy(qualityTargets = listOf(target(
            TrainableQuality.STRENGTH, TargetPriority.PRIMARY, StimulusDoseStrategy.NO_MINIMUM_TARGET,
            StimulusTargetNumericAuthority.NONE
        )))
        val noDemand = select(noDemandPlan, fixture, emptyList()).candidateDispositionIndex.entries.single()
        assertEquals(StimulusCandidateDispositionStatus.SELECTION_NOT_REQUIRED, noDemand.status)
        assertEquals(StimulusCandidateDispositionReason.NO_MINIMUM_TARGET, noDemand.reasons.single())

        val excluded = select(qualityPlan(), fixture.copy(request = fixture.request.copy(
            excludedExerciseStableKeys = setOf("barbell_back_squat")
        )), emptyList()).candidateDispositionIndex.entries.single()
        assertEquals(StimulusCandidateDispositionStatus.INELIGIBLE, excluded.status)
        assertTrue(StimulusCandidateDispositionReason.USER_EXCLUDED in excluded.reasons)
    }

    @Test
    fun admittedMovementTargetSelectsCanonicalB5OwnerButDoesNotInventDoseOrB6() {
        val candidate = exercise("core_candidate", mode = "repetitions")
        val base = fixture(listOf(candidate), emptyList())
        val snapshot = base.snapshot.copy(
            canonicalStrengthSignals = mapOf("dual_authorized_candidate" to CanonicalStrengthSignal(100.0, observationCount = 2)),
            metadata = base.snapshot.metadata.mapValues { (_, metadata) ->
            metadata.copy(activityKind = "EXERCISE", programSlot = "CORE_STABILITY_ACCESSORY", progressMetricType = "LOAD_REPS",
                analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"))
        })
        val target = StimulusMovementTarget(
            movementCoverage = MovementCoverage.CORE_DIRECT,
            priority = TargetPriority.SECONDARY,
            reasonCodes = listOf("B4_MOVEMENT_TARGET_ADMITTED"),
            evidence = listOf("numericDoseAuthority=false")
        )
        val targetPlan = StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target))
        assertEquals(PlannedActivityKind.RESISTANCE, snapshot.activityKind("core_candidate"))
        assertEquals(MovementCoverage.CORE_DIRECT, snapshot.movementCoverage("core_candidate"))
        assertEquals("PROGRAM_SELECTABLE", snapshot.metadata.getValue("core_candidate").planningEligibility)

        val selection = StimulusTargetCandidateSelector(coreCatalog = directCoreCatalog("core_candidate")).build(
            targetPlan, snapshot, base.state, base.request, CanonicalExercisePhysicalQualityCatalog.EMPTY
        )
        val selected = selection.selectedCandidates.single()
        assertEquals("core_candidate", selected.stableKey)
        assertEquals("CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT", selected.selectionRole)
        assertEquals(0, selected.targetSetsFromExistingPrescription)
        assertTrue(selection.materialDemand.candidates.isEmpty())

        val authorization = StimulusPrescriptionAuthorizationEngine().build(
            targetPlan, selection, snapshot, emptyMap()
        ).movementAuthorizations.single()
        assertEquals(StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY, authorization.status)
        assertTrue(authorization.reasonCodes.contains("NO_EXACT_MOVEMENT_PRESCRIPTION_AUTHORITY"))
        assertTrue(authorization.reasonCodes.contains("NO_APPROVED_MOVEMENT_DOSE_POLICY"))
    }

    @Test
    fun canonicalUpperPullTargetAcceptsTheExistingVerticalPullAggregateRelation() {
        val candidate = exercise("vertical_pull_owner")
        val base = fixture(listOf(candidate), emptyList())
        val snapshot = base.snapshot.copy(metadata = base.snapshot.metadata.mapValues { (_, metadata) ->
            metadata.copy(activityKind = "EXERCISE", programSlot = "VERTICAL_PULL_STRENGTH", progressMetricType = "LOAD_REPS",
                analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"))
        })
        assertEquals(MovementCoverage.VERTICAL_PULL, snapshot.movementCoverage(candidate.stableKey))
        val target = StimulusMovementTarget(
            MovementCoverage.UPPER_PULL, TargetPriority.SECONDARY,
            reasonCodes = listOf("B4_MOVEMENT_TARGET_ADMITTED"), evidence = emptyList()
        )
        val selection = StimulusTargetCandidateSelector().build(
            StimulusTargetPlan(emptyList(), emptyList(), emptyList(), movementTargets = listOf(target)),
            snapshot, base.state, base.request, base.catalog
        )

        assertEquals(candidate.stableKey, selection.traces.single().selectedStableKey)
        assertEquals("CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL", selection.selectedCandidates.single().selectionRole)
        assertTrue(selection.materialDemand.candidates.isEmpty())
    }

    @Test
    fun movementCandidateDoesNotSuppressLaterCanonicalQualitySelection() {
        val key = "barbell_back_squat"
        val candidate = exercise(key, mode = "repetitions")
        val base = fixture(listOf(candidate), listOf(relation(key)))
        val snapshot = base.snapshot.copy(metadata = base.snapshot.metadata.mapValues { (_, metadata) ->
            metadata.copy(activityKind = "EXERCISE", programSlot = "CORE_STABILITY_ACCESSORY", progressMetricType = "LOAD_REPS",
                analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"))
        })
        val movement = StimulusMovementTarget(
            MovementCoverage.CORE_DIRECT, TargetPriority.PRIMARY,
            reasonCodes = listOf("B4_MOVEMENT_TARGET_ADMITTED"), evidence = emptyList()
        )
        val strength = target(TrainableQuality.STRENGTH, TargetPriority.SECONDARY)
        val targetPlan = StimulusTargetPlan(listOf(strength), emptyList(), emptyList(), movementTargets = listOf(movement))

        val result = StimulusTargetCandidateSelector(coreCatalog = directCoreCatalog(key)).build(
            targetPlan, snapshot, base.state, base.request, base.catalog
        )

        val strengthTrace = result.traces.single { it.targetId == "QUALITY:STRENGTH" }
        assertTrue(strengthTrace.selectionRequired)
        assertEquals(key, strengthTrace.selectedStableKey)
        val movementTrace = result.traces.single { it.targetId == movement.targetId }
        assertEquals(key, movementTrace.selectedStableKey)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", movementTrace.selectedSelectionRole)
        assertTrue(movementTrace.reasonCodes.contains("MOVEMENT_COVERED_BY_CANONICAL_B5_OWNER"))
        assertEquals(setOf("QUALITY:STRENGTH", movement.targetId), result.selectedCandidates.single().coveredTargetIds)
    }

    @Test
    fun movementTargetReusesOnlyTheExactAuthorizedQualityPrescription() {
        val key = "barbell_back_squat"
        val candidate = exercise(key, mode = "repetitions")
        val base = fixture(listOf(candidate), listOf(relation(key)))
        val snapshot = base.snapshot.copy(
            canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(100.0, observationCount = 2)),
            metadata = base.snapshot.metadata.mapValues { (_, metadata) ->
                metadata.copy(activityKind = "EXERCISE", programSlot = "CORE_STABILITY_ACCESSORY", progressMetricType = "LOAD_REPS",
                    analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"))
            }
        )
        val movement = StimulusMovementTarget(
            MovementCoverage.CORE_DIRECT, TargetPriority.PRIMARY,
            reasonCodes = listOf("B4_MOVEMENT_TARGET_ADMITTED"), evidence = emptyList()
        )
        val strength = target(TrainableQuality.STRENGTH, TargetPriority.SECONDARY)
        val targetPlan = StimulusTargetPlan(listOf(strength), emptyList(), emptyList(), movementTargets = listOf(movement))
        val selection = StimulusTargetCandidateSelector(coreCatalog = directCoreCatalog(key)).build(
            targetPlan, snapshot, base.state, base.request, base.catalog
        )
        val owner = selection.selectedCandidates.single()
        assertEquals(key, owner.stableKey)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", owner.selectionRole)
        assertTrue(movement.targetId in owner.coveredTargetIds)

        val exactPrescription = PlannedPrescription(
            text = "2 x 5", sets = listOf(
                ProgramSetPrescription(1, 5, 80.0, 0),
                ProgramSetPrescription(2, 5, 80.0, 0)
            ), restSeconds = 120, weightSource = "EXACT_TEST_AUTHORITY"
        )
        val authorization = StimulusPrescriptionAuthorizationEngine().build(
            targetPlan, selection, snapshot,
            mapOf(StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) to exactPrescription)
        )

        val movementAuthorization = authorization.movementAuthorizations.single()
        assertEquals(StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6, movementAuthorization.status)
        assertEquals("QUALITY:STRENGTH", movementAuthorization.existingAuthorityTargetId)
        assertEquals(exactPrescription, authorization.authorizedPrescriptions.values.single())
    }

    @Test
    fun movementTargetDoesNotReuseSameStableKeyAuthorityFromDifferentRole() {
        val key = "barbell_back_squat"
        val candidate = exercise(key, mode = "repetitions")
        val base = fixture(listOf(candidate), listOf(relation(key)))
        val snapshot = base.snapshot.copy(
            canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(100.0, observationCount = 2)),
            metadata = base.snapshot.metadata.mapValues { (_, metadata) ->
                metadata.copy(activityKind = "EXERCISE", programSlot = "CORE_STABILITY_ACCESSORY", progressMetricType = "LOAD_REPS",
                    analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"))
            }
        )
        val movement = StimulusMovementTarget(
            MovementCoverage.CORE_DIRECT, TargetPriority.PRIMARY,
            reasonCodes = listOf("B4_MOVEMENT_TARGET_ADMITTED"), evidence = emptyList()
        )
        val strength = target(TrainableQuality.STRENGTH, TargetPriority.SECONDARY)
        val targetPlan = StimulusTargetPlan(listOf(strength), emptyList(), emptyList(), movementTargets = listOf(movement))
        val selection = StimulusTargetCandidateSelector(coreCatalog = directCoreCatalog(key)).build(
            targetPlan, snapshot, base.state, base.request, base.catalog
        )
        val selected = selection.selectedCandidates.single()
        val differentRolePrescription = PlannedPrescription(
            text = "2 x 5", sets = listOf(
                ProgramSetPrescription(1, 5, 80.0, 0),
                ProgramSetPrescription(2, 5, 80.0, 0)
            ), restSeconds = 120, weightSource = "UNRELATED_ROLE_AUTHORITY"
        )

        val authorization = StimulusPrescriptionAuthorizationEngine().build(
            targetPlan, selection, snapshot,
            mapOf(StimulusPrescriptionOwnerIdentity(selected.stableKey, "CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT") to differentRolePrescription)
        )

        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", selected.selectionRole)
        assertEquals(StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY,
            authorization.movementAuthorizations.single().status)
    }

    @Test
    fun selectedOwnerCanCoverAnotherTargetWithoutSelectingASecondRole() {
        val dual = exercise("barbell_back_squat")
        val fixture = fixture(listOf(dual), listOf(
            relation("barbell_back_squat", quality = TrainableQuality.STRENGTH),
            relation("barbell_back_squat", quality = TrainableQuality.POWER)
        ))
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY),
                target(TrainableQuality.POWER, TargetPriority.SECONDARY)
            ), taskTargets = emptyList(), unresolved = emptyList()
        )
        val result = select(plan, fixture, emptyList())
        assertEquals(1, result.selectedCandidates.size)
        val covered = result.candidateDispositionIndex.entries.single { it.targetId == "QUALITY:POWER" && it.stableKey == "barbell_back_squat" }
        assertEquals(StimulusCandidateDispositionStatus.REUSED_FOR_TARGET, covered.status)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", covered.selectedInstead?.selectionRole)
        assertTrue(covered.targetCoveredBySelectedOwner)
    }

    @Test
    fun materializationFailureIsNotReportedAsAnEligibilityOrRankingOutcome() {
        val candidate = exercise("barbell_back_squat")
        val fixture = fixture(listOf(candidate), listOf(relation("barbell_back_squat")))
            .copy(request = request().copy(sessionMinutes = 0))
        val result = select(qualityPlan(), fixture, emptyList())
        val disposition = result.candidateDispositionIndex.entries.single()
        assertEquals(StimulusCandidateDispositionStatus.MATERIALIZATION_FAILED, disposition.status)
        assertEquals(StimulusCandidateDispositionReason.MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME, disposition.reasons.single())
        assertNull(disposition.selectedInstead)
    }

    @Test
    fun lateComparisonAttachesControlRoleOnlyToExactCanonicalReplacementEvidence() {
        val request = request()
        val control = skeleton(request, listOf(item("same_exercise", 1, "LEGACY_ROLE")))
        val experimental = skeleton(request, listOf(item("same_exercise", 1, "CANONICAL_STIMULUS_QUALITY_STRENGTH")))
        val targetId = "QUALITY:STRENGTH"
        val disposition = StimulusCandidateDisposition(
            targetId = targetId,
            stableKey = "same_exercise",
            canonicalSelectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH",
            directTargetCandidate = true,
            selectionRequired = true,
            status = StimulusCandidateDispositionStatus.SELECTED,
            reasons = emptyList()
        )
        val plan = StimulusCandidateSelectionPlan(
            emptyList(), emptyList(), MaterialDemand(emptyList(), emptyMap(), emptyMap()),
            candidateDispositionIndex = StimulusCandidateDispositionIndex(listOf(disposition))
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(control, experimental, qualityPlan(), plan, null, null)
        val provenance = comparison.nonSelectionProvenance.single()
        assertEquals(StimulusPrescriptionOwnerIdentity("same_exercise", "LEGACY_ROLE"), provenance.omittedControlOwner)
        assertEquals(StimulusNonSelectionClassification.CANONICAL_REPLACEMENT, provenance.classification)
        assertEquals(targetId, provenance.targetEvidence.single().targetId)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", provenance.targetEvidence.single().disposition.canonicalSelectionRole)
    }

    @Test
    fun lateComparisonClassifiesExactTargetAlreadyCoveredWithoutGrantingAuthority() {
        val request = request()
        val control = skeleton(request, listOf(item("legacy_owner", 1, "LEGACY_ROLE")))
        val selectedOwner = StimulusPrescriptionOwnerIdentity("selected_owner", "CANONICAL_STIMULUS_QUALITY_STRENGTH")
        val experimental = skeleton(request, listOf(item(selectedOwner.stableKey, 1, selectedOwner.selectionRole)))
        val disposition = StimulusCandidateDisposition(
            targetId = "QUALITY:STRENGTH",
            stableKey = "legacy_owner",
            canonicalSelectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH",
            directTargetCandidate = true,
            selectionRequired = false,
            status = StimulusCandidateDispositionStatus.TARGET_ALREADY_COVERED,
            reasons = listOf(StimulusCandidateDispositionReason.TARGET_ALREADY_COVERED_BY_SELECTED_OWNER),
            selectedInstead = selectedOwner,
            targetCoveredBySelectedOwner = true
        )
        val plan = StimulusCandidateSelectionPlan(
            selectedCandidates = listOf(StimulusSelectedCandidate(
                selectedOwner.stableKey, setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", listOf("B5"),
                "REALIZATION_UNCLASSIFIED", 2, selectedOwner.selectionRole
            )),
            traces = emptyList(),
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap()),
            candidateDispositionIndex = StimulusCandidateDispositionIndex(listOf(disposition))
        )
        val result = StimulusSelectionProgramComparisonEngine().compare(control, experimental, qualityPlan(), plan, null, null)
        val provenance = result.nonSelectionProvenance.single()
        assertEquals(StimulusNonSelectionClassification.TARGET_ALREADY_COVERED, provenance.classification)
        assertEquals(selectedOwner, provenance.targetEvidence.single().disposition.selectedInstead)
    }

    @Test
    fun repeatedRecentCompatibleDirectHistoryWinsContinuityRanking() {
        val recent = exercise("barbell_back_squat")
        val fresh = exercise("barbell_deadlift")
        val cutoff = LocalDate.of(2026, 9, 20)
        val fixture = fixture(
            exercises = listOf(fresh, recent),
            relations = listOf(relation("barbell_deadlift"), relation("barbell_back_squat")),
            history = listOf(
                PlanningSetRecord(cutoff.minusDays(7), "barbell_back_squat", "recent squat", "STRENGTH", 1, 5, 80.0, 0, 8.0),
                PlanningSetRecord(cutoff.minusDays(14), "barbell_back_squat", "recent squat", "STRENGTH", 1, 5, 80.0, 0, 8.0)
            )
        )

        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("barbell_back_squat", result.selectedCandidates.single().stableKey)
        assertEquals(listOf("barbell_back_squat"), result.traces.single().historyDirectCapabilityIdentities)
    }

    @Test
    fun historyOlderThanSixtyDaysDoesNotReceiveCurrentContinuityPriority() {
        val old = exercise("barbell_deadlift")
        val fresh = exercise("barbell_back_squat")
        val cutoff = LocalDate.of(2026, 9, 20)
        val fixture = fixture(
            exercises = listOf(old, fresh),
            relations = listOf(relation("barbell_deadlift"), relation("barbell_back_squat")),
            history = listOf(PlanningSetRecord(cutoff.minusDays(65), "barbell_deadlift", "old squat", "STRENGTH", 1, 5, 80.0, 0, 8.0))
        )

        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("barbell_back_squat", result.selectedCandidates.single().stableKey)
        assertTrue(result.traces.single().historyDirectCapabilityIdentities.isEmpty())
    }

    @Test
    fun recentButPrescriptionIncompatibleHistoryIsNotClassifiedAsStrengthCompatible() {
        val incompatible = exercise("barbell_back_squat")
        val fresh = exercise("barbell_deadlift")
        val fixture = fixture(
            exercises = listOf(incompatible, fresh),
            relations = listOf(relation("barbell_back_squat"), relation("barbell_deadlift")),
            history = listOf(PlanningSetRecord(LocalDate.of(2026, 9, 10), "barbell_back_squat", "incompatible squat", "STRENGTH", 1, 10, 80.0, 0, 8.0))
        )

        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("barbell_back_squat", result.selectedCandidates.single().stableKey)
        assertEquals(SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE,
            result.selectedCandidates.single().probePrescriptionCompatibility)
    }

    @Test
    fun tissueRestrictionOverridesRecentContinuityAndNoHistorySelectionIsDeterministic() {
        val restricted = exercise("barbell_back_squat")
        val safe = exercise("barbell_deadlift")
        val cutoff = LocalDate.of(2026, 9, 20)
        val base = fixture(
            exercises = listOf(restricted, safe),
            relations = listOf(relation("barbell_back_squat"), relation("barbell_deadlift")),
            history = listOf(PlanningSetRecord(cutoff.minusDays(3), "barbell_back_squat", "restricted squat", "STRENGTH", 1, 5, 80.0, 0, 8.0))
        )
        val tissueRestricted = base.copy(snapshot = base.snapshot.copy(
            recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("barbell_back_squat"))
        ))
        val gated = select(qualityPlan(), tissueRestricted, emptyList())
        assertEquals("barbell_deadlift", gated.selectedCandidates.single().stableKey)
        assertFalse(gated.traces.single().candidatePool.contains("barbell_back_squat"))

        val noHistory = fixture(listOf(safe, exercise("ex_c5043892")), listOf(relation("barbell_deadlift"), relation("ex_c5043892")))
        assertEquals(select(qualityPlan(), noHistory, emptyList()), select(qualityPlan(), noHistory, emptyList()))
    }

    @Test
    fun controlIdentityCannotSuppressCanonicalSelectionForDoseGap() {
        val existing = exercise("barbell_back_squat")
        val replacement = exercise("barbell_deadlift")
        val fixture = fixture(
            exercises = listOf(existing, replacement),
            relations = listOf(relation("barbell_back_squat"), relation("barbell_deadlift"))
        )
        val result = select(qualityPlan(), fixture, listOf(existing))
        val trace = result.traces.single()
        assertTrue(trace.selectionRequired)
        assertTrue(trace.historyDirectCapabilityIdentities.isEmpty())
        assertEquals("barbell_back_squat", result.selectedCandidates.single().stableKey)
        assertTrue(trace.reasonCodes.contains("SELECTION_IDENTITY_PRESENT"))
        assertEquals(select(qualityPlan(), fixture, emptyList()).candidateDispositionIndex,
            result.candidateDispositionIndex)
    }

    @Test
    fun canonicalSelectorHasNoLegacySeedAndAlwaysBuildsFromTargetAndHistory() {
        val existing = exercise("barbell_back_squat")
        val fixture = fixture(listOf(existing), listOf(relation("barbell_back_squat")))
        val control = skeleton(fixture.request, listOf(
            item("existing", 1, role = "MAIN"),
            item("existing", 2, role = "MAIN", week = 2),
            item("existing", 3, role = "ACCESSORY")
        ))
        assertEquals(setOf("existing"), control.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey))
        val actual = StimulusTargetCandidateSelector().build(
            qualityPlan(), fixture.snapshot, fixture.state, fixture.request, fixture.catalog
        )
        assertEquals("barbell_back_squat", actual.selectedCandidates.single().stableKey)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", actual.selectedCandidates.single().selectionRole)
        assertTrue(actual.traces.single().selectionRequired)
    }

    @Test
    fun selectorRunsAfterControlObjectIsDiscardedAndHasNoProgramObjectParameter() {
        val candidate = exercise("barbell_back_squat")
        val fixture = fixture(listOf(candidate), listOf(relation("barbell_back_squat")))
        var control: GeneratedProgramSkeleton? = skeleton(fixture.request, listOf(item("old", 1, role = "MAIN")))
        assertEquals(setOf("old"), requireNotNull(control).items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey))
        control = null

        assertNull(control)
        val build = StimulusTargetCandidateSelector::class.java.methods.single { it.name == "build" }
        assertFalse(build.parameterTypes.any { it == GeneratedProgramSkeleton::class.java || it == ProgramSkeletonItem::class.java })
        val result = StimulusTargetCandidateSelector().build(
            qualityPlan(), fixture.snapshot, fixture.state, fixture.request, fixture.catalog
        )
        assertEquals(listOf("barbell_back_squat"), result.selectedCandidates.map { it.stableKey })
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", result.selectedCandidates.single().selectionRole)
    }

    @Test
    fun malformedSeedRejectsBlankStableKeyAndDuplicateOwnerButAllowsBlankLegacyRole() {
        assertThrows(IllegalArgumentException::class.java) {
            StimulusIncumbentIdentity(" ", "MAIN")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StimulusIncumbentIdentitySeed(listOf(
                StimulusIncumbentIdentity("lift", "MAIN"),
                StimulusIncumbentIdentity("lift", "MAIN")
            ))
        }
        // ProgramSkeletonItem's existing role default is the empty string; role is preserved verbatim.
        assertEquals(listOf(StimulusIncumbentIdentity("legacy", "")),
            StimulusIncumbentIdentitySeed.fromControl(skeleton(request(), listOf(item("legacy", 1)))).owners)
    }

    @Test
    fun sameStableKeyWithDifferentSelectionRolesIsNeverCollapsed() {
        val seed = StimulusIncumbentIdentitySeed(listOf(
            StimulusIncumbentIdentity("shared", "MAIN"),
            StimulusIncumbentIdentity("shared", "ACCESSORY")
        ))
        assertEquals(2, seed.owners.size)
        assertEquals(setOf("shared"), seed.stableKeys)
    }

    @Test
    fun incumbentIdentityHasNoMetadataFieldsThatCouldConflictForOneOwner() {
        assertEquals(
            listOf("selectionRole", "stableKey"),
            StimulusIncumbentIdentity::class.java.declaredFields.map { it.name }
                .filterNot { it.startsWith("\$") }.sorted()
        )
    }

    @Test
    fun multiTargetDirectCandidateIsSelectedOnceAndReused() {
        val shared = exercise("shared").copy(laterality = "BILATERAL")
        val fixture = fixture(
            exercises = listOf(shared),
            relations = listOf(
                powerRelation("shared"),
                relation("shared", StimulusCapabilityLevel.DIRECT_CAPABILITY, TrainableQuality.RAPID_FORCE_PRODUCTION)
            )
        )
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                approvedPowerTarget(TargetPriority.PRIMARY),
                target(TrainableQuality.RAPID_FORCE_PRODUCTION, TargetPriority.SECONDARY)
            ), taskTargets = emptyList(), unresolved = emptyList(),
            powerJumpDoseDecisions = listOf(approvedPowerDose())
        )
        val result = select(plan, fixture, emptyList())
        assertEquals(listOf("shared"), result.selectedCandidates.map { it.stableKey })
        assertEquals(setOf("QUALITY:POWER", "QUALITY:RAPID_FORCE_PRODUCTION"), result.selectedCandidates.single().coveredTargetIds)
        assertTrue(result.traces[1].reasonCodes.contains("TARGET_COVERED_BY_ALREADY_SELECTED_IDENTITY"))
    }

    @Test
    fun powerCandidateCannotMaterializeFromTargetFieldsWithoutTypedB4DoseDecision() {
        val fixture = fixture(
            exercises = listOf(exercise("shared")),
            relations = listOf(powerRelation("shared"))
        )
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(approvedPowerTarget(TargetPriority.PRIMARY)),
            taskTargets = emptyList(), unresolved = emptyList()
        )

        val result = select(plan, fixture, emptyList())

        assertTrue(result.selectedCandidates.isEmpty())
        assertEquals("B5_POWER_JUMP_REQUIRES_EXACT_TYPED_B4_DOSE_DECISION",
            result.traces.single().candidateRejectionReasons.getValue("shared"))
    }

    @Test
    fun noMinimumTargetCannotBorrowAnIdentitySelectedForAnotherQuality() {
        val shared = exercise("shared")
        val fixture = fixture(
            exercises = listOf(shared),
            relations = listOf(
                relation("shared", quality = TrainableQuality.HYPERTROPHY),
                relation("shared", quality = TrainableQuality.STRENGTH)
            )
        )
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                target(TrainableQuality.HYPERTROPHY, TargetPriority.PRIMARY),
                target(TrainableQuality.STRENGTH, TargetPriority.SECONDARY,
                    StimulusDoseStrategy.NO_MINIMUM_TARGET, StimulusTargetNumericAuthority.NONE)
            ), taskTargets = emptyList(), unresolved = emptyList()
        )

        val result = select(plan, fixture, emptyList())

        assertEquals(setOf("QUALITY:HYPERTROPHY"), result.selectedCandidates.single().coveredTargetIds)
        assertFalse(result.traces.last().selectionRequired)
        assertNull(result.traces.last().selectedStableKey)
        assertTrue(result.traces.last().reasonCodes.contains("NO_MINIMUM_TARGET"))
        assertTrue("a no-minimum Strength target is not an unmet exposure shortfall", result.strengthShortfalls.isEmpty())
    }

    @Test
    fun selectedIdentityRedundancyAvoidsUnnecessaryDuplicateGroup() {
        val first = exercise("first_power").copy(laterality = "BILATERAL")
        val redundant = exercise("a_redundant_rfd")
        val independent = exercise("z_independent_rfd")
        val fixture = fixture(
            exercises = listOf(first, redundant, independent),
            relations = listOf(
                powerRelation("first_power"),
                relation("a_redundant_rfd", quality = TrainableQuality.RAPID_FORCE_PRODUCTION),
                relation("z_independent_rfd", quality = TrainableQuality.RAPID_FORCE_PRODUCTION)
            )
        )
        val metadata = fixture.snapshot.metadata.toMutableMap()
        metadata["first_power"] = metadata.getValue("first_power").copy(redundancyGroup = "BARBELL_LOWER")
        metadata["a_redundant_rfd"] = metadata.getValue("a_redundant_rfd").copy(redundancyGroup = "BARBELL_LOWER")
        metadata["z_independent_rfd"] = metadata.getValue("z_independent_rfd").copy(redundancyGroup = "JUMP")
        val withRedundancy = fixture.copy(snapshot = fixture.snapshot.copy(metadata = metadata))
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                approvedPowerTarget(TargetPriority.PRIMARY),
                target(TrainableQuality.RAPID_FORCE_PRODUCTION, TargetPriority.SECONDARY)
            ), taskTargets = emptyList(), unresolved = emptyList(),
            powerJumpDoseDecisions = listOf(approvedPowerDose())
        )

        val selected = select(plan, withRedundancy, emptyList())
        assertEquals(listOf("first_power", "z_independent_rfd"), selected.selectedCandidates.map { it.stableKey })
    }

    @Test
    fun noMinimumReductionAndRedistributionNeverCreateNewIdentity() {
        val candidate = exercise("candidate")
        val fixture = fixture(listOf(candidate), listOf(relation("candidate")))
        val targets = listOf(
            target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY, StimulusDoseStrategy.NO_MINIMUM_TARGET, StimulusTargetNumericAuthority.NONE),
            target(TrainableQuality.HYPERTROPHY, TargetPriority.SECONDARY, StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE),
            target(TrainableQuality.POWER, TargetPriority.BACKGROUND, StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY)
        )
        val result = select(StimulusTargetPlan(targets, emptyList(), emptyList()), fixture, emptyList())
        assertTrue(result.selectedCandidates.isEmpty())
        assertTrue(result.traces.any { it.reasonCodes.contains("NO_MINIMUM_TARGET") })
        assertTrue(result.traces.any { it.reasonCodes.contains("REDUCTION_DOES_NOT_AUTHORIZE_NEW_EXERCISE") })
        assertTrue(result.traces.any { it.reasonCodes.contains("DISTRIBUTION_AUTHORITY_DEFERRED") })
    }

    @Test
    fun genericCourtCannotSatisfyTaskIdentity() {
        val court = exercise("court", activityKind = "GENERIC_COURT_SESSION")
        val fixture = fixture(
            exercises = listOf(court),
            relations = emptyList(),
            directTasks = mapOf("court" to setOf("DECELERATION"))
        )
        val plan = StimulusTargetPlan(
            qualityTargets = emptyList(),
            taskTargets = listOf(StimulusTaskTarget("DECELERATION", StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
                TargetPriority.PRIMARY, StimulusTargetNumericAuthority.DIRECTION_ONLY, emptyList(), emptyList())),
            unresolved = emptyList()
        )
        val result = select(plan, fixture, emptyList())
        assertTrue(result.selectedCandidates.isEmpty())
        assertTrue(result.traces.single().candidatePool.isEmpty())
        assertTrue(result.traces.single().reasonCodes.contains("TARGET_REQUIRES_SELECTION_BUT_NO_MATERIALIZABLE_CANDIDATE"))
        val disposition = result.candidateDispositionIndex.entries.single()
        assertEquals(StimulusCandidateDispositionStatus.INELIGIBLE, disposition.status)
        assertEquals(StimulusCandidateDispositionReason.TASK_ACTIVITY_NOT_SELECTABLE, disposition.reasons.single())
    }

    @Test
    fun b5UsesExistingPrescriptionSetCountAndLeavesStrengthGapForB6() {
        val candidate = exercise("barbell_back_squat")
        val fixture = fixture(listOf(candidate), listOf(relation("barbell_back_squat")))
        val result = select(qualityPlan().copy(
            qualityTargets = listOf(target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY,
                StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, StimulusTargetNumericAuthority.DIRECTION_ONLY,
                weekly = StimulusTargetRange(4.0, 9.0, 12.0)))
        ), fixture, emptyList())
        val selected = result.selectedCandidates.single()
        assertEquals(2, selected.targetSetsFromExistingPrescription)
        assertEquals(2, result.materialDemand.candidates.single().targetSets)
        assertEquals("PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6", selected.currentPrescriptionCompatibility)
        assertTrue(result.traces.single().reasonCodes.contains("B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY"))
    }

    @Test
    fun comparisonReportsAddedAndRemovedIdentitiesWithoutWinner() {
        val request = request()
        val control = skeleton(request, listOf(item("old", 1)))
        val experimental = skeleton(request, listOf(item("new", 1)))
        val plan = qualityPlan()
        val selection = StimulusCandidateSelectionPlan(emptyList(), emptyList(), MaterialDemand(emptyList(), emptyMap(), emptyMap()))
        val comparison = StimulusSelectionProgramComparisonEngine().compare(control, experimental, plan, selection, null, null)
        assertEquals(setOf("new"), comparison.addedStableKeys)
        assertEquals(setOf("old"), comparison.removedStableKeys)
        assertTrue(comparison.winner == null)
        assertTrue(comparison.differences.isNotEmpty())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun select(plan: StimulusTargetPlan, fixture: Fixture, controlItems: List<Exercise>): StimulusCandidateSelectionPlan =
        StimulusTargetCandidateSelector().build(plan, fixture.snapshot, fixture.state, fixture.request, fixture.catalog)

    private fun qualityPlan(): StimulusTargetPlan = StimulusTargetPlan(
        qualityTargets = listOf(target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY)),
        taskTargets = emptyList(), unresolved = emptyList()
    )

    private fun directCoreCatalog(stableKey: String): CanonicalCoreCatalog = CanonicalCoreCatalog.of(
        listOf(CanonicalCoreProfile(stableKey, CoreClass.DIRECT, CoreDirectTarget.BRACING))
    )

    private fun target(
        quality: TrainableQuality,
        priority: TargetPriority,
        strategy: StimulusDoseStrategy = StimulusDoseStrategy.HOLD_PERSONAL_BASELINE,
        authority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        weekly: StimulusTargetRange = StimulusTargetRange(4.0, 6.0, 9.0)
    ) = StimulusQualityTarget(quality, strategy, priority, authority, SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        PlanningConfidence.HIGH, weekly, StimulusTargetRange(1.0, 2.0, 3.0), null, null, null, emptyList(), emptyList(), true, true)

    private fun approvedPowerTarget(priority: TargetPriority): StimulusQualityTarget = target(
        quality = TrainableQuality.POWER,
        priority = priority,
        strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        authority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
        weekly = StimulusTargetRange(2.0, 2.0, 2.0)
    ).copy(
        needDecision = TrainingNeedDecision.DEVELOP,
        requiredPhysicalModes = setOf(PhysicalQualityMode.BALLISTIC.name, PhysicalQualityMode.PLYOMETRIC.name)
    )

    private fun approvedPowerDose(): PowerJumpDoseDecision = PowerJumpIntegratedDosePolicy.resolve(
        PowerJumpDoseNeed(
            targetId = "QUALITY:POWER",
            kind = PowerJumpDoseKind.POWER,
            region = PowerJumpBodyRegion.LOWER,
            needDecision = TrainingNeedDecision.DEVELOP,
            priority = TargetPriority.PRIMARY
        ),
        PowerJumpDoseEvidence(
            resistanceWorkload = PowerJumpResistanceWorkload(PowerJumpWorkloadStatus.COMPLETE, 0, 0),
            validRecentBadmintonWeekMinutes = listOf(60.0)
        )
    )

    private fun powerRelation(key: String) = ExercisePhysicalQualityRelation(
        relationId = "$key-POWER-BALLISTIC",
        exerciseStableKey = key,
        qualityId = TrainableQuality.POWER,
        relationLevel = StimulusCapabilityLevel.DIRECT_CAPABILITY,
        regionQualifier = PhysicalQualityRegion.LOWER,
        modeQualifier = PhysicalQualityMode.BALLISTIC,
        prescriptionDependent = true,
        provenance = "USER_APPROVED_PROJECT_POLICY",
        evidenceRelationKeys = emptySet(),
        reviewStatus = "PASS",
        notes = "test exact direct Power relation"
    )

    private fun relation(key: String, level: StimulusCapabilityLevel = StimulusCapabilityLevel.DIRECT_CAPABILITY,
        quality: TrainableQuality = TrainableQuality.STRENGTH) = ExercisePhysicalQualityRelation(
        relationId = "$key-${quality.name}", exerciseStableKey = key, qualityId = quality, relationLevel = level,
        regionQualifier = PhysicalQualityRegion.SYSTEMIC, modeQualifier = PhysicalQualityMode.GENERAL,
        prescriptionDependent = true, provenance = "TEST", evidenceRelationKeys = emptySet(), reviewStatus = "APPROVED", notes = ""
    )

    private data class Fixture(val snapshot: PlanningHistorySnapshot, val state: AthletePlanningState,
        val catalog: CanonicalExercisePhysicalQualityCatalog, val request: ProgramSkeletonRequest)

    private fun fixture(exercises: List<Exercise>, relations: List<ExercisePhysicalQualityRelation>,
        history: List<PlanningSetRecord> = emptyList(), directTasks: Map<String, Set<String>> = emptyMap()): Fixture {
        val effectiveHistory = history.ifEmpty {
            listOf(PlanningSetRecord(LocalDate.of(2026, 6, 1), "history-sentinel", "history-sentinel", "TEST", 1, 1, 0.0, 0, 8.0))
        }
        val metadata = exercises.associate { exercise ->
            exercise.stableKey to RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                activityKind = if (exercise.activityKind == "GENERIC_COURT_SESSION") "SPORT_SESSION" else "EXERCISE",
                planningEligibility = "PROGRAM_SELECTABLE",
                progressMetricType = "LOAD_REPS",
                analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"),
                sourceConfidenceLevel = "HIGH"
            )
        }
        val snapshot = PlanningHistorySnapshot(
            cutoff = LocalDate.of(2026, 9, 20), allConfirmedSets = effectiveHistory, exercises = exercises.associateBy(Exercise::stableKey),
            metadata = metadata, badmintonObjectives = emptyMap(), profilePrimaryGoal = "STRENGTH_GAIN",
            strengthTrainingYears = 1.0, badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(StrengthIntent.STRENGTH_PRIORITY, BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.WILLING),
            badmintonDirectObjectives = directTasks
        )
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        return Fixture(snapshot, state, CanonicalExercisePhysicalQualityCatalog.of(relations), request())
    }

    private fun exercise(key: String, activityKind: String = "RESISTANCE", mode: String = "") = Exercise(
        stableKey = key, name = key, category = "STRENGTH", activityKind = activityKind, equipment = "BODYWEIGHT", mode = mode
    )

    private fun request() = ProgramSkeletonRequest("test", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 2)

    private fun item(key: String, order: Int, role: String = "", week: Int = 1) = ProgramSkeletonItem(
        localId = "$key-$order-$week-$role", weekNumber = week, dayOfWeek = 1, orderIndex = order, exerciseStableKey = key,
        exerciseName = key, category = "STRENGTH", restSeconds = 90, prescription = "8 reps", setCount = 2,
        reps = 8, weightKg = 0.0, seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = role
    )

    private fun skeleton(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = request.durationWeeks * 7, request = request,
        periodizationType = request.periodizationType, weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)),
        items = items
    )
}
