package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusPrescriptionMaterializationTest {
    private val key = "b6.materialized.squat"
    private val role = "B5_ROLE"
    private val snapshot = PlanningHistorySnapshot(
        cutoff = LocalDate.of(2026, 9, 23), allConfirmedSets = emptyList(), exercises = emptyMap(),
        metadata = emptyMap(), badmintonObjectives = emptyMap(), profilePrimaryGoal = "MIXED",
        strengthTrainingYears = 1.0, badmintonTrainingYears = 0.0, preferences = PersonalizedPlanningPreferences(),
        canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(100.0, observationCount = 2))
    )

    private fun target(quality: TrainableQuality) = StimulusQualityTarget(
        quality = quality, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, numericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, baselineConfidence = PlanningConfidence.HIGH,
        weeklyDirectUnitsTarget = null, weeklyDirectSessionsTarget = null,
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null,
        exposureWeekFrequencyReference = null, reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun candidate(quality: TrainableQuality = TrainableQuality.STRENGTH) = StimulusSelectedCandidate(
        stableKey = key, coveredTargetIds = setOf("QUALITY:${quality.name}"), primaryTargetId = "QUALITY:${quality.name}",
        selectionReasons = emptyList(), currentPrescriptionCompatibility = "REALIZED_INCOMPATIBLE",
        targetSetsFromExistingPrescription = 2, selectionRole = role,
        probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
    )

    private fun selection(candidate: StimulusSelectedCandidate) = StimulusCandidateSelectionPlan(
        selectedCandidates = listOf(candidate), traces = emptyList(),
        materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
    )

    private fun prescription(reps: Int, load: Double, sets: Int = 2) = PlannedPrescription(
        text = "test", sets = List(sets) { ProgramSetPrescription(it + 1, reps, load, 0) },
        restSeconds = 120, weightSource = "TEST"
    )

    private fun experimental(weeks: List<Int>, authorized: PlannedPrescription, mutateWeek: Int? = null) =
        GeneratedProgramSkeleton(
            suggestedName = "B6 test", durationDays = weeks.size * 7,
            request = ProgramSkeletonRequest(
                name = "B6 test", goal = ProgramGoal.STRENGTH, weeklyTrainingDays = 4,
                sessionMinutes = 60, availableEquipment = setOf("BARBELL"), excludedExerciseText = "",
                badmintonTransferRatio = 0.0, sportStrengthRatio = "BALANCED",
                periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = weeks.size
            ), periodizationType = ProgramPeriodizationType.AUTO, weekPlans = emptyList(),
            items = weeks.flatMap { week ->
                val sets = if (week == mutateWeek) authorized.sets.map { it.copy(reps = it.reps + 1) } else authorized.sets
                if (week < 0) emptyList() else listOf(
                    ProgramSkeletonItem(
                        localId = "b6-$week", weekNumber = week, dayOfWeek = 1, orderIndex = 0,
                        exerciseStableKey = key, exerciseName = "Squat", category = "STRENGTH",
                        restSeconds = authorized.restSeconds, prescription = authorized.text,
                        setCount = sets.size, reps = sets.firstOrNull()?.reps ?: 0,
                        weightKg = sets.firstOrNull()?.weightKg ?: 0.0, seconds = 0,
                        selectionReason = "test", weightSource = authorized.weightSource,
                        stableKey = key, selectionRole = role, setPrescriptions = sets
                    )
                )
            }
        )

    private fun auditPlan(authorized: PlannedPrescription) = StimulusPrescriptionAuthorizationPlan(listOf(
        StimulusPrescriptionAuthorization(
            targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH,
            owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION"),
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = authorized, plannedCompatibility = null,
            authorizedPrescription = authorized,
            status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
        )
    ))

    private fun auditWithWeeks(counts: List<Int>, mutateWeek: Int? = null): StimulusPrescriptionMaterializationAudit {
        val authorized = prescription(5, 80.0, sets = 3)
        val skeleton = experimental(counts.indices.map { it + 1 }, authorized).copy(
            items = counts.mapIndexedNotNull { index, count ->
                if (count == 0) null else {
                    val sets = List(count) { setIndex -> authorized.sets[setIndex % authorized.sets.size].copy(setIndex = setIndex + 1) }.map { set ->
                        if (index + 1 == mutateWeek) set.copy(reps = set.reps + 1) else set
                    }
                    ProgramSkeletonItem(
                        localId = "audit-$index", weekNumber = index + 1, dayOfWeek = 1, orderIndex = 0,
                        exerciseStableKey = key, exerciseName = "Squat", category = "STRENGTH",
                        restSeconds = authorized.restSeconds, prescription = authorized.text,
                        setCount = sets.size, reps = sets.first().reps, weightKg = sets.first().weightKg,
                        seconds = 0, selectionReason = "test", weightSource = authorized.weightSource,
                        stableKey = key, selectionRole = role, setPrescriptions = sets
                    )
                }
            }
        )
        return StimulusPrescriptionMaterializationAuditEngine().audit(auditPlan(authorized), skeleton, snapshot).single()
    }

    @Test
    fun materializationAuditCoversEveryExpectedWeekAndUsesConservativeHorizonSemantics() {
        assertEquals(StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED, auditWithWeeks(listOf(3, 3, 3, 3)).state)
        assertEquals(StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED, auditWithWeeks(listOf(3, 3, 2, 3)).state)
        val missing = auditWithWeeks(listOf(3, 3, 0, 3))
        assertEquals(StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED, missing.state)
        assertEquals(1, missing.missingWeekCount)
        assertEquals(4, missing.weeklyAudits.size)
        assertEquals(0, missing.minimumWeeklyMaterializedUnits)
        assertEquals(3, missing.totalShortfallUnits)
        assertEquals(StimulusPrescriptionMaterializationState.NOT_MATERIALIZED, auditWithWeeks(listOf(0, 0, 0, 0)).state)
        assertEquals(StimulusPrescriptionMaterializationState.INVARIANT_FAILURE, auditWithWeeks(listOf(3, 4, 3, 3)).state)
        assertEquals(StimulusPrescriptionMaterializationState.INVARIANT_FAILURE, auditWithWeeks(listOf(3, 3, 3, 3), mutateWeek = 2).state)
    }

    @Test
    fun nonExecutableAuthorizationDoesNotCreateWeeklyShortfall() {
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:HYPERTROPHY", quality = TrainableQuality.HYPERTROPHY,
                owner = null, source = null, inputPrescription = null, plannedCompatibility = null,
                authorizedPrescription = null,
                status = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                reasonCodes = listOf("PROPOSAL_NOT_EXECUTABLE_NO_LOAD_AUTHORITY")
            )
        ))
        val audit = StimulusPrescriptionMaterializationAuditEngine().audit(
            plan, experimental(listOf(1, 2, 3, 4), prescription(8, 0.0)), snapshot
        ).single()
        assertEquals(StimulusPrescriptionMaterializationState.NOT_MATERIALIZED, audit.state)
        assertEquals(0, audit.totalShortfallUnits)
        assertTrue(audit.weeklyAudits.all { it.authorizedSetUnits == 0 && it.shortfall == 0 })
    }

    @Test
    fun strengthAuthorizationMaterializesOnlyAnExactOwnerAndAllowsPrefixSubset() {
        val owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION")
        val authorized = prescription(5, 80.0)
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = owner,
                source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = authorized, plannedCompatibility = null, authorizedPrescription = authorized,
                status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
            )
        ))
        val provider = plan.provider()
        val item = PlannedExercise(key, role, "test", 1, targetSets = 2)
        val other = item.copy(role = "OTHER_ROLE")
        assertEquals(2, provider.authorizedPrescriptionFor(item)?.sets?.size)
        assertEquals(1, provider.prefixFor(item, 1)?.sets?.size)
        assertNull(provider.authorizedPrescriptionFor(other))
        assertTrue(plan.shadowOnly)
        assertTrue(!plan.productionAuthority)
    }

    @Test
    fun heterogeneousRowsAreValidatedAsAWeeklyMultisetSubset() {
        val authorized = PlannedPrescription("heterogeneous", listOf(
            ProgramSetPrescription(1, 5, 80.0, 0),
            ProgramSetPrescription(2, 5, 75.0, 0),
            ProgramSetPrescription(3, 5, 70.0, 0)
        ), 120, "TEST")
        fun row(id: String, sets: List<ProgramSetPrescription>) = ProgramSkeletonItem(
            localId = id, weekNumber = 1, dayOfWeek = if (id == "monday") 1 else 4, orderIndex = 0,
            exerciseStableKey = key, exerciseName = "Squat", category = "STRENGTH", restSeconds = 120,
            prescription = "diagnostic text may differ", setCount = sets.size, reps = sets.first().reps,
            weightKg = sets.first().weightKg, seconds = 0, selectionReason = "test", weightSource = "TEST",
            stableKey = key, selectionRole = role, setPrescriptions = sets
        )
        fun audit(rows: List<ProgramSkeletonItem>) = StimulusPrescriptionMaterializationAuditEngine().audit(
            auditPlan(authorized), experimental(listOf(1), authorized).copy(items = rows), snapshot
        ).single()
        val split = audit(listOf(row("monday", listOf(authorized.sets[0])), row("thursday", authorized.sets.drop(1))))
        assertEquals(StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED, split.state)
        assertTrue(split.prescriptionPreservedOrSubset)
        val duplicate = audit(listOf(row("monday", listOf(authorized.sets[0])), row("thursday", listOf(authorized.sets[0], authorized.sets[1]))))
        assertEquals(StimulusPrescriptionMaterializationState.INVARIANT_FAILURE, duplicate.state)
        assertTrue(duplicate.reasonCodes.contains("B6_AUTHORIZED_SET_REUSED"))
        assertTrue(duplicate.reasonCodes.contains("B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED"))
        val unauthorized = audit(listOf(row("monday", listOf(authorized.sets[0])), row("thursday", listOf(authorized.sets[2].copy(weightKg = 72.5)))))
        assertEquals(StimulusPrescriptionMaterializationState.INVARIANT_FAILURE, unauthorized.state)
        assertTrue(unauthorized.reasonCodes.contains("B6_UNAUTHORIZED_SET_CONTENT"))
    }

    @Test
    fun authorizationEngineUsesB61RulesAndAuthorizesExactHypertrophyOwnersButKeepsProxiesNonExecutable() {
        val engine = StimulusPrescriptionAuthorizationEngine()
        val strengthPlan = engine.build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(StimulusPrescriptionOwnerIdentity(key, role) to prescription(8, 80.0))
        )
        assertEquals(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, strengthPlan.authorizations.single().status)
        assertEquals(5, strengthPlan.authorizations.single().authorizedPrescription?.sets?.first()?.reps)

        val hypertrophy = engine.build(
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY)), emptyList(), emptyList()),
            selection(candidate(TrainableQuality.HYPERTROPHY)), snapshot,
            mapOf(StimulusPrescriptionOwnerIdentity(key, role) to prescription(8, 60.0))
        )
        assertEquals(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            hypertrophy.authorizations.single().status)
        assertEquals(TrainableQuality.HYPERTROPHY, hypertrophy.authorizations.single().quality)
        assertTrue(hypertrophy.authorizedOwners.containsKey(StimulusPrescriptionOwnerIdentity(key, role)))

        val nonExecutable = engine.build(
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY), target(TrainableQuality.POWER)), emptyList(), emptyList()),
            StimulusCandidateSelectionPlan(
                listOf(candidate(TrainableQuality.HYPERTROPHY)), emptyList(), MaterialDemand(emptyList(), emptyMap(), emptyMap())
            ), snapshot, emptyMap()
        )
        assertEquals(StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            nonExecutable.authorizations.first { it.quality == TrainableQuality.HYPERTROPHY }.status)
        assertEquals(StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
            nonExecutable.authorizations.first { it.quality == TrainableQuality.POWER }.status)
    }

    @Test
    fun hypertrophySafeRepairMaterializesAcrossTheFullHorizon() {
        val engine = StimulusPrescriptionAuthorizationEngine()
        val plan = engine.build(
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY)), emptyList(), emptyList()),
            selection(candidate(TrainableQuality.HYPERTROPHY)), snapshot,
            mapOf(StimulusPrescriptionOwnerIdentity(key, role) to prescription(4, 60.0))
        )
        val authorization = plan.authorizations.single()
        assertEquals(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, authorization.status)
        assertTrue(authorization.authorizedPrescription?.sets?.all { it.reps in 7..15 && it.weightKg == 60.0 } == true)
        val audit = StimulusPrescriptionMaterializationAuditEngine().audit(
            plan, experimental(listOf(1, 2, 3, 4), requireNotNull(authorization.authorizedPrescription)), snapshot
        ).single()
        assertEquals(StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED, audit.state)
        assertEquals(4, audit.weeklyAudits.size)
        assertTrue(audit.weeklyAudits.all { it.shortfall == 0 && it.overrun == 0 && it.targetCompatibleMaterializedUnits == it.materializedSetUnits })
    }

    @Test
    fun hypertrophyWithoutNumericAuthorityDoesNotMaterialize() {
        val plan = StimulusPrescriptionAuthorizationEngine().build(
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY).copy(numericAuthority = StimulusTargetNumericAuthority.NONE)), emptyList(), emptyList()),
            selection(candidate(TrainableQuality.HYPERTROPHY)), snapshot,
            mapOf(StimulusPrescriptionOwnerIdentity(key, role) to prescription(8, 60.0))
        )
        assertEquals(StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION, plan.authorizations.single().status)
        val audit = StimulusPrescriptionMaterializationAuditEngine().audit(plan, experimental(listOf(1, 2), prescription(8, 60.0)), snapshot).single()
        assertEquals(StimulusPrescriptionMaterializationState.NOT_MATERIALIZED, audit.state)
        assertTrue(audit.weeklyAudits.all { it.authorizedSetUnits == 0 && it.shortfall == 0 })
    }

    @Test
    fun hypertrophyReusesTheSameFundedMultisetIntegrityRules() {
        val authorized = prescription(8, 60.0, sets = 3)
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:HYPERTROPHY", quality = TrainableQuality.HYPERTROPHY,
                owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION"),
                source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = authorized, plannedCompatibility = null,
                authorizedPrescription = authorized,
                status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
            )
        ))
        val duplicate = experimental(listOf(1), authorized).copy(
            items = listOf(
                experimental(listOf(1), authorized).items.single().copy(
                    setPrescriptions = listOf(authorized.sets[0], authorized.sets[0], authorized.sets[1], authorized.sets[2])
                )
            )
        )
        val audit = StimulusPrescriptionMaterializationAuditEngine().audit(plan, duplicate, snapshot).single()
        assertEquals(StimulusPrescriptionMaterializationState.INVARIANT_FAILURE, audit.state)
        assertTrue(audit.reasonCodes.contains("B6_AUTHORIZED_SET_REUSED"))
        assertTrue(audit.reasonCodes.contains("B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED"))
    }

    @Test
    fun multiQualityAuthorityIsLosslessAndConflictsFailClosedRegardlessOfInputOrder() {
        val owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION")
        val strength = prescription(5, 80.0)
        val hypertrophy = prescription(8, 80.0)
        fun authorization(quality: TrainableQuality, value: PlannedPrescription) = StimulusPrescriptionAuthorization(
            targetId = "QUALITY:${quality.name}", quality = quality, owner = owner,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = value, plannedCompatibility = null, authorizedPrescription = value,
            status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
        )
        fun plan(rows: List<StimulusPrescriptionAuthorization>) = StimulusPrescriptionAuthorizationPlan(rows)
        val first = plan(listOf(authorization(TrainableQuality.STRENGTH, strength), authorization(TrainableQuality.HYPERTROPHY, hypertrophy)))
        val reversed = plan(listOf(authorization(TrainableQuality.HYPERTROPHY, hypertrophy), authorization(TrainableQuality.STRENGTH, strength)))
        val identityStrength = StimulusPrescriptionAuthorityIdentity(key, role, TrainableQuality.STRENGTH)
        val identityHypertrophy = StimulusPrescriptionAuthorityIdentity(key, role, TrainableQuality.HYPERTROPHY)
        assertEquals(strength, first.authorizedPrescriptions.getValue(identityStrength))
        assertEquals(hypertrophy, first.authorizedPrescriptions.getValue(identityHypertrophy))
        assertEquals(first.authorizedPrescriptions, reversed.authorizedPrescriptions)
        assertEquals(
            StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY,
            first.multiQualityResolutions.getValue(StimulusPrescriptionOwnerIdentity(key, role)).status
        )
        assertTrue(first.multiQualityResolutions.getValue(StimulusPrescriptionOwnerIdentity(key, role)).reasonCodes.contains("B6_MULTI_QUALITY_OWNER_PRESCRIPTION_CONFLICT"))
        assertTrue(first.authorizedOwners.isEmpty())
        assertNull(first.provider().authorizedPrescriptionFor(PlannedExercise(key, role, "test", 1, targetSets = 2)))
    }

    @Test
    fun qualitySpecificLookupAndFundedSliceCannotBorrowAcrossQualities() {
        val owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION")
        val strength = prescription(5, 80.0, sets = 3)
        val hypertrophy = prescription(8, 60.0, sets = 3)
        fun authorization(quality: TrainableQuality, value: PlannedPrescription) = StimulusPrescriptionAuthorization(
            targetId = "QUALITY:${quality.name}", quality = quality, owner = owner,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = value, plannedCompatibility = null, authorizedPrescription = value,
            status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
        )
        val provider = StimulusPrescriptionAuthorizationPlan(listOf(
            authorization(TrainableQuality.STRENGTH, strength), authorization(TrainableQuality.HYPERTROPHY, hypertrophy)
        )).provider()
        val item = PlannedExercise(key, role, "test", 1, targetSets = 2)
        assertEquals(strength, provider.authorizedPrescriptionFor(item, TrainableQuality.STRENGTH, 3))
        assertEquals(hypertrophy, provider.authorizedPrescriptionFor(item, TrainableQuality.HYPERTROPHY, 3))
        assertEquals(80.0, provider.sliceFor(item, TrainableQuality.STRENGTH, 1, 1)!!.sets.single().weightKg, 0.0)
        assertEquals(60.0, provider.sliceFor(item, TrainableQuality.HYPERTROPHY, 1, 1)!!.sets.single().weightKg, 0.0)
        assertNull(provider.authorizedPrescriptionFor(item.copy(role = "OTHER"), TrainableQuality.HYPERTROPHY, 3))
    }

    @Test
    fun identicalMultiQualityAuthorityUsesOneSharedOwnerPrescriptionWithoutDuplication() {
        val owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION")
        val shared = prescription(8, 60.0)
        fun authorization(quality: TrainableQuality) = StimulusPrescriptionAuthorization(
            targetId = "QUALITY:${quality.name}", quality = quality, owner = owner,
            source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
            inputPrescription = shared, plannedCompatibility = null, authorizedPrescription = shared,
            status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
        )
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(
            authorization(TrainableQuality.HYPERTROPHY), authorization(TrainableQuality.STRENGTH)
        ))
        val resolution = plan.multiQualityResolutions.getValue(StimulusPrescriptionOwnerIdentity(key, role))
        assertEquals(StimulusMultiQualityPrescriptionResolutionStatus.IDENTICAL_MULTI_QUALITY_AUTHORITY, resolution.status)
        assertEquals(shared, plan.authorizedOwners.getValue(StimulusPrescriptionOwnerIdentity(key, role)))
        assertEquals(2, plan.authorizedPrescriptions.size)
    }

    @Test
    fun hypertrophyMaterializationExposesConditionalEffortExecutionAuthority() {
        val shared = prescription(8, 60.0)
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:HYPERTROPHY", quality = TrainableQuality.HYPERTROPHY,
                owner = StimulusPrescriptionOwner(key, role), source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = shared, plannedCompatibility = null, authorizedPrescription = shared,
                status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
            )
        ))
        assertEquals(StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT, plan.authorizations.single().executionAuthority)
        val audit = StimulusPrescriptionMaterializationAuditEngine().audit(plan, experimental(listOf(1), shared), snapshot).single()
        assertEquals(StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT, audit.executionAuthority)
    }
}
