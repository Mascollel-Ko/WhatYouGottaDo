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
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegionalAuthorityTest {
    @Test
    fun hypertrophyColdStartIsRegionBudgetAndPersonalBandTakesPrecedence() {
        val cold = RegionalStimulusTargetResolver().resolve(morphologyDiagnosis())
        assertEquals(RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY, cold.numericAuthority)
        assertEquals(8.0, cold.weeklyDoseTarget!!, 0.0)
        assertNull(cold.exposureFrequencyTarget)
        assertTrue(cold.reasonCodes.any { it.contains("USER_APPROVED_PROJECT_POLICY") })

        val personalBand = RegionalDoseBand(
            eligibleWeekCount = 4, weeklyUnitsMedian = 10.0,
            current28dWeeklyUnitsMedian = 10.0, previous28dWeeklyUnitsMedian = 9.0,
            previous28dExposureWeekUnitsMedian = 9.0, directExposureWeekCount = 4,
            confidence = PlanningConfidence.HIGH, source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS
        )
        val personal = RegionalStimulusTargetResolver().resolve(morphologyDiagnosis().copy(hypertrophyDoseBand = personalBand))
        assertEquals(RegionalNumericAuthority.PRIOR_TOLERATED_HYPERTROPHY, personal.numericAuthority)
        assertEquals(9.0, personal.weeklyDoseTarget!!, 0.0)
    }

    @Test
    fun coldStartDoseDoesNotCreateNeedAndIncompletePersonalNumericAuthorityDoesNotFallBackToEight() {
        val noNeedDiagnosis = morphologyDiagnosis().copy(
            limitingFactors = listOf(RegionalLimitingFactor.NO_CLEAR_LIMITATION)
        )
        val noNeed = RegionalStimulusTargetResolver().resolve(noNeedDiagnosis)
        assertEquals(RegionalTargetAction.NONE, noNeed.action)
        assertEquals(RegionalNumericAuthority.NONE, noNeed.numericAuthority)
        assertNull(noNeed.weeklyDoseTarget)

        val incompletePersonalBand = RegionalDoseBand(
            eligibleWeekCount = 4,
            weeklyUnitsMedian = null,
            previous28dExposureWeekUnitsMedian = null,
            directExposureWeekCount = 3,
            confidence = PlanningConfidence.HIGH,
            source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS
        )
        val incompletePersonal = RegionalStimulusTargetResolver().resolve(
            morphologyDiagnosis().copy(hypertrophyDoseBand = incompletePersonalBand)
        )
        assertEquals(RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, incompletePersonal.numericAuthority)
        assertNull(incompletePersonal.weeklyDoseTarget)
        assertFalse(incompletePersonal.reasonCodes.any { it.contains("COLD_START_8") })
    }

    @Test
    fun directAndCanonicalSupportiveCreditsAreEquivalentAndDeduplicatedPerRegion() {
        val directKey = "direct-lower"
        val supportiveKey = "supportive-lower"
        val directMeta = RuntimeExerciseMetadataDefaults.forIdentity(directKey, directKey).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH"
        )
        val supportiveMeta = RuntimeExerciseMetadataDefaults.forIdentity(supportiveKey, supportiveKey).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(),
            mapOf(directKey to directMeta, supportiveKey to supportiveMeta), emptyMap(),
            "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        fun relation(id: String, key: String, level: StimulusCapabilityLevel, region: PhysicalQualityRegion) =
            ExercisePhysicalQualityRelation(id, key, TrainableQuality.HYPERTROPHY, level, region,
                PhysicalQualityMode.GENERAL, true, "TEST", setOf("TEST"), "PASS", "")
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("direct-lower", directKey, StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.LOWER),
            relation("direct-quads", directKey, StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.QUADS_GLUTE),
            relation("supportive-lower", supportiveKey, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY, PhysicalQualityRegion.LOWER)
        ))
        val plan = GeneratedProgramSkeleton(
            "regional-credit", 7, ProgramSkeletonRequest("regional-credit", com.training.trackplanner.data.ProgramGoal.BODYBUILDING,
                1, 60, emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1),
            ProgramPeriodizationType.AUTO, emptyList(), listOf(
                skeletonItem(directKey, 1, 2), skeletonItem(supportiveKey, 2, 4)
            )
        )
        val credit = RegionalStimulusCreditProjector().project(plan, snapshot, catalog)
            .getValue(MovementCoverage.LOWER_KNEE to TrainableQuality.HYPERTROPHY)
        assertEquals(4.0, credit.weeklyEquivalentUnits, 0.0)
    }

    @Test
    fun regionalHypertrophyCandidateReceivesOnlyTheUnfilledWeeklyResidual() {
        val directKey = "existing-direct-press"
        val supportiveKey = "existing-supportive-press"
        val candidateKey = "candidate-direct-press"
        val keys = listOf(directKey, supportiveKey, candidateKey)
        val exercises = keys.associateWith { Exercise(it, it, "RESISTANCE", equipment = "BODYWEIGHT") }
        val metadata = exercises.mapValues { (key, exercise) ->
            RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                activityKind = "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE",
                programSlot = "UPPER_PUSH_ACCESSORY", sourceConfidenceLevel = "HIGH"
            )
        }
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19),
            listOf(PlanningSetRecord(LocalDate.of(2026, 9, 18), directKey, directKey, "RESISTANCE", 1, 8, 20.0, 0, 7.0)),
            exercises, metadata, emptyMap(), "HYPERTROPHY",
            1.0, 0.0, PersonalizedPlanningPreferences()
        )
        fun relation(key: String, level: StimulusCapabilityLevel) = ExercisePhysicalQualityRelation(
            "relation-$key", key, TrainableQuality.HYPERTROPHY, level, PhysicalQualityRegion.UPPER_PUSH,
            PhysicalQualityMode.GENERAL, true, "TEST", setOf("TEST"), "PASS", ""
        )
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation(directKey, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation(supportiveKey, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY),
            relation(candidateKey, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val request = ProgramSkeletonRequest(
            "regional-residual", com.training.trackplanner.data.ProgramGoal.BODYBUILDING, 1, 60,
            emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1
        )
        fun existingRow(key: String, count: Int, role: String) = ProgramSkeletonItem(
            localId = key, weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "8 reps", setCount = count,
            reps = 8, weightKg = 20.0, seconds = 0, selectionReason = "authorized test material",
            weightSource = "TEST_AUTHORITY", selectionRole = role,
            setPrescriptions = List(count) { ProgramSetPrescription(it + 1, 8, 20.0, 0, targetRpeMin = 7.0) }
        )
        fun existingPlan(direct: Int, supportive: Int): GeneratedProgramSkeleton = GeneratedProgramSkeleton(
            "regional-residual", 7, request, ProgramPeriodizationType.AUTO, emptyList(), buildList {
                if (direct > 0) add(existingRow(directKey, direct, "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY"))
                if (supportive > 0) add(existingRow(supportiveKey, supportive, "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY"))
            }
        )
        val target = RegionalStimulusTarget(
            MovementCoverage.HORIZONTAL_PUSH, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
            RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY, weeklyDoseTarget = 8.0
        )
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        val selector = RegionalTargetCandidateSelector()

        val partialPlan = existingPlan(2, 4)
        assertEquals(MovementCoverage.HORIZONTAL_PUSH, snapshot.movementCoverage(directKey))
        assertTrue(catalog.relations(directKey).isNotEmpty())
        assertEquals(4.0, RegionalStimulusCreditProjector().project(partialPlan, snapshot, catalog)
            .getValue(MovementCoverage.HORIZONTAL_PUSH to TrainableQuality.HYPERTROPHY).weeklyEquivalentUnits, 0.0)
        val b4Residual = RegionalB4ResidualDoseAuthorityResolver().resolve(target, snapshot, partialPlan, catalog)
        assertEquals(4.0, b4Residual.existingEquivalentUnits, 0.0)
        assertEquals(8.0, b4Residual.targetEquivalentUnits!!, 0.0)
        assertEquals(4.0, b4Residual.residualEquivalentUnits, 0.0)
        assertEquals(4, b4Residual.authorizedWholeSetUnits)
        val partial = selector.selectB5(
            b4Residual, snapshot, state, request, partialPlan, setOf(directKey, supportiveKey), catalog
        )
        assertEquals(4, partial.residualUnits)
        assertEquals(candidateKey, partial.selected?.stableKey)
        assertEquals(4, partial.selected?.targetSets)

        val sufficient = selector.select(target, snapshot, state, request, existingPlan(4, 8), emptySet(), catalog)
        assertEquals(0, sufficient.residualUnits)
        assertNull(sufficient.selected)
        assertTrue(sufficient.reasons.contains("EXISTING_PLAN_CREDIT_COVERS_TARGET"))
    }

    @Test
    fun coldStartHypertrophyPrescriptionRequiresUserLoadCalibration() {
        val key = "new-regional-accessory"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(), mapOf(key to metadata), emptyMap(),
            "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val target = RegionalStimulusTarget(
            MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
            RegionalNumericAuthority.USER_APPROVED_PROJECT_POLICY, weeklyDoseTarget = 4.0
        )
        val resolution = RegionalTargetPrescriptionResolver().resolve(
            b4Residual(target, 4), PlannedExercise(key, "REGIONAL_TARGET", "test", 1, targetSets = 4), snapshot
        )
        assertEquals(RegionalPrescriptionAuthoritySource.USER_APPROVED_COLD_START_CALIBRATION, resolution.authoritySource)
        val prescription = requireNotNull(resolution.prescription)
        assertEquals(4, prescription.sets.size)
        assertTrue(prescription.sets.all {
            it.reps in RegionalColdStartDosePolicy.HYPERTROPHY_PRACTICAL_REPS_MIN..
                RegionalColdStartDosePolicy.HYPERTROPHY_PRACTICAL_REPS_MAX &&
                it.reps == RegionalColdStartDosePolicy.HYPERTROPHY_COLD_START_REPS &&
                it.targetRpeMin!! >= RegionalColdStartDosePolicy.HYPERTROPHY_MINIMUM_TARGET_RPE && it.weightKg == 0.0 &&
                it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
        })
        assertEquals(RegionalColdStartDosePolicy.PROVENANCE, prescription.weightSource)

        val fiveSetResolution = RegionalTargetPrescriptionResolver().resolve(
            b4Residual(target, 5), PlannedExercise(key, "REGIONAL_TARGET", "test", 1, targetSets = 5), snapshot
        )
        assertEquals(5, requireNotNull(fiveSetResolution.prescription).sets.size)

        val noResidual = RegionalTargetPrescriptionResolver().resolve(
            b4Residual(target, 0), PlannedExercise(key, "REGIONAL_TARGET", "test", 1, targetSets = 0), snapshot
        )
        assertNull(noResidual.prescription)
        assertTrue(noResidual.reasonCodes.contains("NO_B4_RESIDUAL_SET_AUTHORITY"))

        val mismatchedB5Count = RegionalTargetPrescriptionResolver().resolve(
            b4Residual(target, 4), PlannedExercise(key, "REGIONAL_TARGET", "test", 1, targetSets = 2), snapshot
        )
        assertNull(mismatchedB5Count.prescription)
        assertTrue(mismatchedB5Count.reasonCodes.contains("B5_OWNER_SET_COUNT_DOES_NOT_MATCH_B4_RESIDUAL"))
    }

    @Test
    fun hypertrophyB6PreservesExactSuccessfulRepPatternButNeverChangesB4ResidualCount() {
        val key = "personal-regional-accessory"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "UPPER_PUSH_ACCESSORY", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val target = RegionalStimulusTarget(
            MovementCoverage.HORIZONTAL_PUSH, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
            RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, weeklyDoseTarget = 10.0
        )
        fun resolve(reps: Int, rpe: Double?, weightKg: Double = 35.0) = RegionalTargetPrescriptionResolver().resolve(
            b4Residual(target, 3),
            PlannedExercise(key, "REGIONAL_TARGET", "test", 1, targetSets = 3),
            PlanningHistorySnapshot(
                LocalDate.of(2026, 9, 19),
                listOf(PlanningSetRecord(LocalDate.of(2026, 9, 18), key, key, "RESISTANCE", 1, reps, weightKg, 0, rpe)),
                mapOf(key to Exercise(key, key, "RESISTANCE", equipment = "BODYWEIGHT")),
                mapOf(key to metadata), emptyMap(), "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
            )
        )

        listOf(7, 12, 15).forEach { reps ->
            val resolution = resolve(reps, 8.0)
            assertEquals(RegionalPrescriptionAuthoritySource.PERSONAL_SUCCESSFUL_HISTORY, resolution.authoritySource)
            val prescription = requireNotNull(resolution.prescription)
            assertEquals(3, prescription.sets.size)
            assertTrue(prescription.sets.all { it.reps == reps && it.targetRpeMin!! >= 7.0 })
        }

        listOf(6, 16).forEach { reps ->
            val prescription = requireNotNull(resolve(reps, 8.0).prescription)
            assertEquals(3, prescription.sets.size)
            assertTrue("out-of-band $reps reps must not be copied", prescription.sets.all { it.reps != reps })
        }

        val lowEffort = resolve(15, 6.0)
        assertNotEquals(RegionalPrescriptionAuthoritySource.PERSONAL_SUCCESSFUL_HISTORY, lowEffort.authoritySource)
        val lowEffortPrescription = requireNotNull(lowEffort.prescription)
        assertEquals(3, lowEffortPrescription.sets.size)
        assertTrue(lowEffortPrescription.sets.all { it.targetRpeMin!! >= 7.0 })

        val zeroLoadHistory = resolve(12, 8.0, weightKg = 0.0)
        assertEquals(RegionalPrescriptionAuthoritySource.PERSONAL_SUCCESSFUL_HISTORY, zeroLoadHistory.authoritySource)
        val calibrated = requireNotNull(zeroLoadHistory.prescription)
        assertEquals(3, calibrated.sets.size)
        assertTrue(calibrated.sets.all {
            it.reps == 12 &&
                it.weightKg == 0.0 && it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
        })
        assertEquals("PERSONAL_SUCCESSFUL_REP_SHAPE_USER_CALIBRATION_REQUIRED", calibrated.weightSource)
    }

    @Test
    fun canonicalMovementB4CarriesRegionalHypertrophyAndCoreDoseWithoutInventingCoreShape() {
        val key = "new-horizontal-press"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_UPPER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(), mapOf(key to metadata), emptyMap(),
            "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val relation = ExercisePhysicalQualityRelation(
            "direct-horizontal-push", key, TrainableQuality.HYPERTROPHY,
            StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.CHEST,
            PhysicalQualityMode.GENERAL, true, "TEST", setOf("TEST"), "PASS", ""
        )
        val movementTargets = listOf(
            StimulusMovementTarget(MovementCoverage.HORIZONTAL_PUSH, TargetPriority.PRIMARY, reasonCodes = listOf("ADDRESS"), evidence = emptyList()),
            StimulusMovementTarget(MovementCoverage.CORE_DIRECT, TargetPriority.SECONDARY, reasonCodes = listOf("ADDRESS"), evidence = emptyList())
        )
        val doses = RegionalMovementDoseTargetBuilder().build(
            movementTargets, snapshot, AthletePlanningState(
                observedBehavior = ObservedTrainingBehavior.UNKNOWN,
                strengthExposure = StrengthExposure.UNKNOWN,
                strengthIntent = StrengthIntent.MIXED,
                badmintonIntent = BadmintonPlanningIntent.DISABLED,
                freeWeightWillingness = FreeWeightWillingness.WILLING,
                primaryAdaptation = "TEST", historyDays = 0, recentTrainingDaysPerWeek = 0.0,
                scheduleVolatility = 0.0, machineSetRatio = 0.0, freeWeightSetRatio = 0.0,
                anchors = emptyList(), observedStrengthStyle = StrengthProgrammingStyle.UNRESOLVED,
                observedStyleConfidence = PlanningConfidence.LOW, structuredBadmintonSessions = 0,
                recoveryConstraint = "NONE", confidence = PlanningConfidence.LOW
            ), CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)),
            CanonicalCoreCatalog.of(listOf(CanonicalCoreProfile("bird-dog", CoreClass.DIRECT, CoreDirectTarget.BRACING)))
        )

        val hypertrophy = doses.getValue(MovementCoverage.HORIZONTAL_PUSH).single()
        assertEquals(StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET, hypertrophy.kind)
        assertEquals(StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY, hypertrophy.numericAuthority)
        assertEquals(8.0, hypertrophy.weeklyTarget!!, 0.0)
        assertEquals(StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION,
            hypertrophy.shapeAuthority)
        val core = doses.getValue(MovementCoverage.CORE_DIRECT).single()
        assertEquals(StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET, core.kind)
        assertEquals(StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY, core.numericAuthority)
        assertEquals(6.0, core.weeklyTarget!!, 0.0)
        assertEquals(StimulusMovementDoseShapeAuthority.NONE, core.shapeAuthority)
        assertTrue(core.reasonCodes.contains("CORE_PRESCRIPTION_SHAPE_AUTHORITY_UNAVAILABLE"))
        assertTrue(core.evidence.contains("prescriptionShapeAuthority=NONE"))
    }

    @Test
    fun requirementUsesBasePriorityAndMapsUpperPullWithoutMergingHistory() {
        val resolver = RegionalStrengthRequirementResolver()
        val result = resolver.resolve(NeedRelevance.HIGH, listOf(
            representation("LOWER_KNEE", RepresentationPriority.HIGH),
            representation("POSTERIOR_CHAIN", RepresentationPriority.MODERATE),
            representation("UPPER_PULL", RepresentationPriority.HIGH)
        ))
        assertEquals(NeedRelevance.HIGH, result[MovementCoverage.LOWER_KNEE])
        assertEquals(NeedRelevance.MODERATE, result[MovementCoverage.POSTERIOR_CHAIN])
        assertEquals(NeedRelevance.HIGH, result[MovementCoverage.HORIZONTAL_PULL])
        assertEquals(NeedRelevance.HIGH, result[MovementCoverage.VERTICAL_PULL])
        assertTrue(MovementCoverage.VERTICAL_PUSH !in result)
    }

    @Test
    fun mixedSetPrescriptionsAreClassifiedPerSet() {
        val key = "barbell_back_squat"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(), mapOf(key to metadata), emptyMap(),
            "STRENGTH_GAIN", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val relation = { id: String, quality: TrainableQuality -> ExercisePhysicalQualityRelation(
            id, key, quality, StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.LOWER,
            PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", ""
        ) }
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("strength", TrainableQuality.STRENGTH), relation("hypertrophy", TrainableQuality.HYPERTROPHY)
        ))
        val item = ProgramSkeletonItem(
            localId = "mixed", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "mixed", setCount = 4,
            reps = 4, weightKg = 100.0, seconds = 0, selectionReason = "TEST", weightSource = "TEST",
            selectionRole = CANONICAL_STRENGTH_SELECTION_ROLE,
            setPrescriptions = listOf(
                ProgramSetPrescription(1, 4, 100.0, 0), ProgramSetPrescription(2, 8, 80.0, 0),
                ProgramSetPrescription(3, 8, 80.0, 0), ProgramSetPrescription(4, 8, 80.0, 0)
            )
        )
        val skeleton = GeneratedProgramSkeleton(
            "mixed", 7, ProgramSkeletonRequest(
                "mixed", com.training.trackplanner.data.ProgramGoal.STRENGTH, 1, 60,
                emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1
            ),
            ProgramPeriodizationType.AUTO, emptyList(), listOf(item)
        )
        val labels = ProgramEmphasisProjector().project(skeleton, snapshot, catalog)
        assertEquals(1, labels.first { it.quality == TrainableQuality.STRENGTH }.plannedUnits)
        assertFalse(labels.any { it.quality == TrainableQuality.HYPERTROPHY })

        val movementOwnedLabels = ProgramEmphasisProjector().project(
            skeleton.copy(items = listOf(item.copy(selectionRole = "MOVEMENT_OWNER"))), snapshot, catalog
        )
        assertFalse(movementOwnedLabels.any { it.quality == TrainableQuality.STRENGTH })
        assertEquals(3, movementOwnedLabels.first { it.quality == TrainableQuality.HYPERTROPHY }.plannedUnits)
    }

    @Test
    fun hypertrophyTargetCannotReuseStrengthLikeHistory() {
        val key = "lower-knee-history"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19),
            listOf(PlanningSetRecord(LocalDate.of(2026, 9, 18), key, key, "RESISTANCE", 1, 5, 100.0, 0, 8.0)),
            mapOf(key to Exercise(key, key, "RESISTANCE", activityKind = "RESISTANCE")),
            mapOf(key to metadata), emptyMap(), "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val target = RegionalStimulusTarget(
            MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
            RegionalNumericAuthority.PRIOR_TOLERATED_HYPERTROPHY, weeklyDoseTarget = 3.0
        )
        val resolved = RegionalTargetPrescriptionResolver().resolve(
            b4Residual(target, 3), PlannedExercise(key, "REGIONAL", "test", 1, targetSets = 3), snapshot
        ).prescription
        assertTrue(resolved != null)
        assertTrue(resolved!!.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE })
        assertTrue(resolved.sets.none { it.reps == 5 })
    }

    @Test
    fun finalProjectorReportsOnlyTargetCompatibleSets() {
        val key = "mixed-squat-final"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(), mapOf(key to metadata), emptyMap(),
            "STRENGTH_GAIN", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val relation = ExercisePhysicalQualityRelation(
            "hypertrophy", key, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", ""
        )
        val item = ProgramSkeletonItem(
            localId = "mixed-final", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "mixed", setCount = 4,
            reps = 4, weightKg = 100.0, seconds = 0, selectionReason = "TEST", weightSource = "TEST",
            setPrescriptions = listOf(
                ProgramSetPrescription(1, 4, 100.0, 0), ProgramSetPrescription(2, 8, 80.0, 0),
                ProgramSetPrescription(3, 8, 80.0, 0), ProgramSetPrescription(4, 8, 80.0, 0)
            )
        )
        val request = ProgramSkeletonRequest(
            "mixed-final", com.training.trackplanner.data.ProgramGoal.STRENGTH, 1, 60,
            emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1
        )
        val plan = GeneratedProgramSkeleton("mixed-final", 7, request, ProgramPeriodizationType.AUTO, emptyList(), listOf(item))
        val projection = FinalRegionalStimulusProjector().project(
            target = RegionalStimulusTarget(
                MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
                RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, weeklyDoseTarget = 4.0
            ), finalPlan = plan, snapshot = snapshot,
            catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)), selectedIdentity = RegionalSelectionIdentity(key, item.selectionRole),
            creditedUnits = 0, residualUnits = 4, authorizedUnits = 4
        )
        assertEquals(3, projection.targetCompatibleMaterializedUnits)
        assertEquals(1, projection.shortfall)

        val survivingPlan = plan.copy(items = listOf(item.copy(
            setCount = 2, reps = 8, weightKg = 80.0,
            setPrescriptions = item.setPrescriptions.drop(1).take(2)
        )))
        val shortfall = FinalRegionalStimulusProjector().project(
            target = RegionalStimulusTarget(
                MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
                RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, weeklyDoseTarget = 4.0
            ), finalPlan = survivingPlan, snapshot = snapshot,
            catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)), selectedIdentity = RegionalSelectionIdentity(key, item.selectionRole),
            creditedUnits = 0, residualUnits = 4, authorizedUnits = 4
        )
        assertEquals(2, shortfall.targetCompatibleMaterializedUnits)
        assertEquals(2, shortfall.shortfall)
    }

    @Test
    fun authorizedRegionalPrescriptionIsVisibleToSchedulingBeforeFinalOutput() {
        val snapshot = PostGenerationFixture.snapshot()
        val state = PostGenerationFixture.state(snapshot)
        val item = PostGenerationFixture.source("press", 3).copy(role = "REGIONAL_TARGET_LOWER_KNEE_HYPERTROPHY")
        val authorized = PlannedPrescription(
            "target-compatible 10-rep prescription",
            List(3) { ProgramSetPrescription(it + 1, 10, 0.0, 0) },
            90,
            "TARGET_COMPATIBLE_TEST"
        )
        val demand = AuthorizedSchedulingDemand("regional_press", item, authorized, continuity = false)
        val allocation = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).allocateAuthorized(
            snapshot, state, listOf(demand), 3, 60,
            PostGenerationFixture.plan(listOf(PostGenerationFixture.row("press", 1))).request
        )
        assertEquals(authorized, allocation.trace.authorized.single().prescription)
        assertTrue(allocation.days.values.flatten().all { atom -> atom.timed.prescription.sets.all { it.reps == 10 } })
    }

    @Test
    fun regionalFinalizerIsAuditOnlyAndCannotMutatePostReflowPrescription() {
        val key = "mixed-final"
        val item = ProgramSkeletonItem(
            localId = "post-reflow", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "ordinary five-rep",
            setCount = 3, reps = 5, weightKg = 100.0, seconds = 0, selectionReason = "TEST", weightSource = "HISTORY",
            selectionRole = "REGIONAL_ROLE",
            setPrescriptions = List(3) { ProgramSetPrescription(it + 1, 5, 100.0, 0) }
        )
        val plan = GeneratedProgramSkeleton(
            "post-reflow", 7,
            ProgramSkeletonRequest("post-reflow", com.training.trackplanner.data.ProgramGoal.STRENGTH, 1, 60,
                emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1),
            ProgramPeriodizationType.AUTO, emptyList(), listOf(item)
        )
        val result = RegionalTargetAwareFinalizer().apply(
            plan, PostGenerationFixture.snapshot(),
            mapOf(key to RegionalStimulusTarget(MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY,
                RegionalTargetAction.ADD_SUPPORT, RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, 3.0)),
            mapOf("$key|REGIONAL_ROLE" to RegionalStimulusTarget(MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY,
                RegionalTargetAction.ADD_SUPPORT, RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, 3.0))
        )
        assertSame(plan, result)
        assertTrue(result.items.single().setPrescriptions.all { it.reps == 5 })
    }

    @Test
    fun sameRegionUnrelatedQualitySurvivesTypedOwnershipForHoldOrNoChange() {
        val base = PostGenerationFixture.snapshot()
        val strengthKey = "lower-strength"
        val hyperKey = "lower-hyper"
        val strengthExercise = Exercise(strengthKey, strengthKey, "RESISTANCE", equipment = "BODYWEIGHT")
        val hyperExercise = Exercise(hyperKey, hyperKey, "RESISTANCE", equipment = "BODYWEIGHT")
        val lowerMeta = base.metadata.getValue("squat")
        val snapshot = base.copy(
            allConfirmedSets = base.allConfirmedSets + PlanningSetRecord(base.cutoff.minusDays(2), strengthKey, strengthKey, "RESISTANCE", 1, 5, 100.0, 0, 8.0),
            exercises = base.exercises + (strengthKey to strengthExercise) + (hyperKey to hyperExercise),
            metadata = base.metadata + (strengthKey to lowerMeta.copy(stableKey = strengthKey, exerciseName = strengthKey)) +
                (hyperKey to lowerMeta.copy(stableKey = hyperKey, exerciseName = hyperKey))
        )
        val candidates = MaterialDemand(listOf(
            PlannedExercise(strengthKey, "OTHER_OBJECTIVE", "unrelated strength", 80, targetSets = 2),
            PlannedExercise(hyperKey, "REGIONAL_DUPLICATE", "owned hyper", 80, targetSets = 2)
        ), emptyMap(), emptyMap())
        val relations = listOf(
            ExercisePhysicalQualityRelation("strength", strengthKey, TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", ""),
            ExercisePhysicalQualityRelation("hyper", hyperKey, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", "")
        )
        val filtered = RegionalMaterialDemandOwnershipFilter.filter(
            candidates, snapshot, PostGenerationFixture.state(snapshot),
            setOf(RegionalOwnershipKey(MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY)),
            catalog = CanonicalExercisePhysicalQualityCatalog.of(relations)
        )
        assertTrue(filtered.candidates.any { it.stableKey == strengthKey })
        assertFalse(filtered.candidates.any { it.stableKey == hyperKey })
    }

    private fun morphologyDiagnosis() = RegionalBottleneckDiagnosis(
        region = MovementCoverage.LOWER_KNEE,
        outcomeQuality = TrainableQuality.HYPERTROPHY,
        requirement = NeedRelevance.MODERATE,
        performanceResponse = TrainingResponseState.STABLE_RESPONSE,
        validStrengthObservationCount = 2,
        strengthExposureStatus = RegionalStrengthExposureStatus.SUFFICIENT,
        strengthDoseBand = RegionalDoseBand(),
        strengthExposureFrequency = null,
        specificityContinuity = SpecificityContinuity.UNKNOWN,
        hypertrophySupportStatus = RegionalHypertrophySupportStatus.LOW,
        hypertrophyDoseBand = RegionalDoseBand(),
        hypertrophyExposureFrequency = null,
        recoveryConstraint = false,
        sportLoadInterference = false,
        limitingFactors = listOf(RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING),
        primaryInterpretation = RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING,
        confidence = PlanningConfidence.MODERATE
    )

    private fun b4Residual(target: RegionalStimulusTarget, units: Int) = RegionalB4ResidualDoseAuthority(
        target = target,
        existingCredit = null,
        existingEquivalentUnits = 0.0,
        targetEquivalentUnits = target.weeklyDoseTarget,
        residualEquivalentUnits = units.toDouble(),
        authorizedWholeSetUnits = units,
        reasonCodes = listOf("TEST_B4_RESIDUAL_AUTHORITY")
    )

    private fun skeletonItem(key: String, order: Int, sets: Int) = ProgramSkeletonItem(
        localId = "regional-$key", weekNumber = 1, dayOfWeek = 1, orderIndex = order, exerciseStableKey = key,
        exerciseName = key, category = "TEST", restSeconds = 90, prescription = "8 reps", setCount = sets,
        reps = 8, weightKg = 0.0, seconds = 0, selectionReason = "TEST", weightSource = "TEST",
        selectionRole = "REGIONAL_TARGET_LOWER_KNEE_HYPERTROPHY",
        setPrescriptions = List(sets) { ProgramSetPrescription(it + 1, 8, 0.0, 0) }
    )

    private fun representation(key: String, priority: RepresentationPriority) = MovementExposureRepresentation(
        movementCoverage = key, basePriority = priority, currentExposure28d = 1.0, priorExposure28d = 1.0,
        currentActiveBins = 4, currentShare = .5, priorShare = .5, peerReference = null,
        peerRepresentationRatio = null, personalRetentionRatio = 1.0, representationState = RepresentationState.NO_CLEAR_DEFICIT_SIGNAL,
        evidenceConfidence = PlanningConfidence.HIGH, reasonCodes = emptyList()
    )
}
