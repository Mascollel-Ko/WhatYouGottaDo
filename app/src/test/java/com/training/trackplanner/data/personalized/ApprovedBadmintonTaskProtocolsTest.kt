package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.personalized.StimulusTaskTarget
import java.time.LocalDate
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedBadmintonTaskProtocolsTest {
    @Test
    fun approvedDefinitionsAreExactBoundedAndNotCategoryWide() {
        assertEquals(3, ApprovedBadmintonTaskProtocols.definitions.size)
        val six = ApprovedBadmintonTaskProtocols.exact(
            "ex_33841b88", "CANONICAL_STIMULUS_TASK_ACCELERATION", CanonicalTaskTarget.ACCELERATION
        )!!
        assertEquals("BADMINTON_SIX_CORNER_FOOTWORK_V1", six.protocolId)
        assertEquals(setOf(CanonicalTaskTarget.ACCELERATION, CanonicalTaskTarget.DECELERATION,
            CanonicalTaskTarget.FOOTWORK, CanonicalTaskTarget.REACTION), six.authorizedTasks)
        assertEquals("3 라운드 × 10–20초 · 휴식 60초", six.shape.format())
        assertEquals(2, six.weeklyExposures)
        assertEquals(TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY, six.provenance)

        val lateral = ApprovedBadmintonTaskProtocols.exact(
            "ex_421ba24b", "CANONICAL_STIMULUS_TASK_LUNGE_REACH", CanonicalTaskTarget.LUNGE_REACH
        )!!
        assertEquals("BADMINTON_LATERAL_SHUTTLE_LUNGE_V1", lateral.protocolId)
        assertEquals("3 세트 × 5회/side · 휴식 75초", lateral.shape.format())
        assertEquals(TaskLateralitySemantics.PER_SIDE, lateral.shape.laterality)

        val split = ApprovedBadmintonTaskProtocols.exact(
            "ex_8e69fc74", "CANONICAL_STIMULUS_TASK_REACTION", CanonicalTaskTarget.REACTION
        )!!
        assertEquals("BADMINTON_SPLIT_STEP_REACTION_V1", split.protocolId)
        assertNull(ApprovedBadmintonTaskProtocols.exact(
            "ex_314df428", "CANONICAL_STIMULUS_QUALITY_POWER", CanonicalTaskTarget.JUMP_LANDING
        ))
        assertNull(ApprovedBadmintonTaskProtocols.exact(
            "another-deceleration-owner", lateral.selectionRole, CanonicalTaskTarget.LUNGE_REACH
        ))
        assertFalse(ApprovedBadmintonTaskProtocols.definitions.any { CanonicalTaskTarget.JUMP_LANDING in it.authorizedTasks })
    }

    @Test
    fun protocolMetadataRoundTripsExactPerSideAndRangeSemanticsWithoutDisplayParsing() {
        ApprovedBadmintonTaskProtocols.definitions.forEach { definition ->
            val kind = if (definition.stableKey == "ex_8e69fc74") PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
                else PlannedActivityKind.STRUCTURED_BADMINTON_DRILL
            val tasks = definition.authorizedTasks
            val auth = TaskProtocolB6Authorization(
                status = TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
                definition = definition,
                materializationActivityKind = kind,
                attributedTasks = tasks,
                transferEvidence = tasks.associateWith { BadmintonObjectiveTransferLevel.DIRECT }
            )
            val encoded = TaskProtocolExposureMetadata(auth, exposureIndex = 1).toJsonString()
            val decoded = TaskProtocolExposureMetadata.fromJsonString(encoded)
            assertEquals(definition.shape, decoded.authorization.definition.shape)
            assertEquals("USER_APPROVED_PROJECT_POLICY", decoded.toJson().getString("policyProvenance"))
            assertEquals(definition.protocolId, decoded.authorization.definition.protocolId)
            assertEquals(1, decoded.exposureIndex)
            assertFalse(encoded.contains("15초"))
            if (definition.shape.mode == TaskPrescriptionMode.REPETITIONS_PER_SIDE) {
                assertEquals(5, decoded.authorization.definition.shape.reps)
                assertEquals(TaskLateralitySemantics.PER_SIDE, decoded.authorization.definition.shape.laterality)
                assertFalse(encoded.contains("10"))
            }
        }
    }

    @Test
    fun editedShapeOrOwnerCannotBorrowApprovedProtocolMetadata() {
        val definition = ApprovedBadmintonTaskProtocols.definitions.first()
        val tasks = definition.authorizedTasks
        val authorization = TaskProtocolB6Authorization(
            TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
            definition,
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
            tasks,
            tasks.associateWith { BadmintonObjectiveTransferLevel.DIRECT }
        )
        val metadata = TaskProtocolExposureMetadata(authorization, 1)
        assertThrows(IllegalArgumentException::class.java) {
            TaskPrescriptionShape(
                mode = TaskPrescriptionMode.DURATION_RANGE_SECONDS, setCount = 3,
                minSeconds = 20, maxSeconds = 10, restSeconds = 60,
                loadMode = TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD
            )
        }
        val serialized = metadata.toJson().put("stableKey", "different-owner").toString()
        assertThrows(Exception::class.java) { TaskProtocolExposureMetadata.fromJsonString(serialized) }
    }

    @Test
    fun approvedProtocolFrequencyShortfallIsTypedAndDoesNotInventPlacement() {
        val definition = ApprovedBadmintonTaskProtocols.definitions.first()
        val owner = StimulusPrescriptionOwnerIdentity(definition.stableKey, definition.selectionRole)
        val tasks = definition.authorizedTasks
        val grant = TaskProtocolB6Authorization(
            TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
            definition,
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
            tasks,
            tasks.associateWith { BadmintonObjectiveTransferLevel.DIRECT }
        )
        val plan = TaskProtocolAuthorizationPlan(mapOf(owner to grant), emptyMap(), setOf(owner))
        val request = ProgramSkeletonRequest(
            name = "test", goal = ProgramGoal.BADMINTON_SUPPORT, weeklyTrainingDays = 3,
            sessionMinutes = 60, availableEquipment = emptySet(), excludedExerciseText = "",
            badmintonTransferRatio = 0.5, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 1
        )
        val skeleton = ProgramSkeletonItem(
            localId = "test", weekNumber = 1, dayOfWeek = 2, orderIndex = 1,
            exerciseStableKey = "unrelated", exerciseName = "test", category = "SPORTS",
            restSeconds = 60, prescription = "", setCount = 1, reps = 1, weightKg = 0.0,
            seconds = 0, selectionReason = "", weightSource = ""
        )
        val emptyAuthorityResult = com.training.trackplanner.data.GeneratedProgramSkeleton(
            "test", 7, request, ProgramPeriodizationType.AUTO, emptyList(), listOf(skeleton)
        ).withTaskProtocolFrequencyOutcomes(plan)
        val outcome = emptyAuthorityResult.taskProtocolFrequencyOutcomes.single()
        assertEquals(TaskProtocolFrequencyStatus.SHORTFALL, outcome.status)
        assertEquals(2, outcome.requestedExposures)
        assertEquals(0, outcome.placedExposures)
        assertEquals(2, outcome.shortfall)
        assertEquals(TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY, outcome.authority)
    }

    @Test
    fun splitStepRequiresTheRoleDerivedByNormalB5AndAnExactDirectReactionTarget() {
        val key = "ex_8e69fc74"
        // B5 derives this role from TASK:REACTION via roleFor(); protocol authority does not
        // alter candidate ranking or make this owner appear in the selected set.
        val selectionRole = "CANONICAL_STIMULUS_TASK_REACTION"
        val exercise = Exercise(key, "스플릿 스텝 리액션", "SPORTS", activityKind = "EXERCISE")
        val runtime = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            activityKind = "EXERCISE",
            programSlot = "BADMINTON_FOOTWORK",
            analysisEligibility = MetadataTokenField.parse("BADMINTON_TRANSFER"),
            badmintonTransferLevel = "DIRECT"
        )
        val snapshot = PlanningHistorySnapshot(
            cutoff = LocalDate.of(2026, 10, 1), allConfirmedSets = emptyList(),
            exercises = mapOf(key to exercise), metadata = mapOf(key to runtime),
            badmintonObjectives = emptyMap(), profilePrimaryGoal = "BADMINTON",
            strengthTrainingYears = 0.0, badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            badmintonDirectObjectives = mapOf(key to setOf("REACTION")),
            exerciseRoleCatalog = ExerciseRoleRelationCatalog.EMPTY
        )
        val target = StimulusTaskTarget(
            task = "REACTION", strategy = StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY,
            priority = TargetPriority.PRIMARY, numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
            reasonCodes = emptyList(), evidence = emptyList()
        )
        val targetPlan = StimulusTargetPlan(emptyList(), listOf(target), emptyList())
        fun selection(role: String) = StimulusCandidateSelectionPlan(
            selectedCandidates = listOf(StimulusSelectedCandidate(
                stableKey = key, coveredTargetIds = setOf("TASK:REACTION"), primaryTargetId = "TASK:REACTION",
                selectionReasons = emptyList(), currentPrescriptionCompatibility = "NO_PERSONAL_BASELINE",
                targetSetsFromExistingPrescription = 0, selectionRole = role
            )),
            traces = emptyList(), materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )

        val exact = TaskProtocolB6AuthorizationEngine.build(targetPlan, selection(selectionRole), snapshot)
        val owner = StimulusPrescriptionOwnerIdentity(key, selectionRole)
        assertEquals("BADMINTON_SPLIT_STEP_REACTION_V1", exact.authorizedByOwner.getValue(owner).definition.protocolId)
        assertTrue(exact.deferredByOwner.isEmpty())

        val wrongRole = TaskProtocolB6AuthorizationEngine.build(targetPlan, selection("CANONICAL_STIMULUS_TASK_FOOTWORK"), snapshot)
        assertFalse(wrongRole.authorizedByOwner.containsKey(owner))
        assertEquals("NO_EXACT_USER_APPROVED_TASK_PROTOCOL", wrongRole.deferredByOwner.values.single())

        val noDirectRelation = TaskProtocolB6AuthorizationEngine.build(
            targetPlan, selection(selectionRole), snapshot.copy(badmintonDirectObjectives = emptyMap())
        )
        assertTrue(noDirectRelation.authorizedByOwner.isEmpty())
        assertEquals("APPROVED_PROTOCOL_CANONICAL_DIRECT_RELATION_MISSING", noDirectRelation.deferredByOwner.values.single())
    }

    @Test
    fun sameProtocolNeverMaterializesTwiceOnOneDayAndReportsSafeFrequencyShortfall() {
        val definition = ApprovedBadmintonTaskProtocols.definitions.first()
        val owner = StimulusPrescriptionOwnerIdentity(definition.stableKey, definition.selectionRole)
        val tasks = definition.authorizedTasks
        val grant = TaskProtocolB6Authorization(
            TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL, definition,
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, tasks,
            tasks.associateWith { BadmintonObjectiveTransferLevel.DIRECT }
        )
        val plan = TaskProtocolAuthorizationPlan(mapOf(owner to grant), emptyMap(), setOf(owner))
        val request = ProgramSkeletonRequest(
            name = "same-day fixture", goal = ProgramGoal.BADMINTON_SUPPORT, weeklyTrainingDays = 3,
            sessionMinutes = 60, availableEquipment = emptySet(), excludedExerciseText = "",
            badmintonTransferRatio = 0.5, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 1
        )
        val generated = com.training.trackplanner.data.GeneratedProgramSkeleton(
            "same-day fixture", 7, request, ProgramPeriodizationType.AUTO, emptyList(),
            listOf(protocolItem(definition, grant, exposure = 1, day = 2),
                protocolItem(definition, grant, exposure = 2, day = 2))
        ).withTaskProtocolFrequencyOutcomes(plan)

        assertEquals(1, generated.items.size)
        val outcome = generated.taskProtocolFrequencyOutcomes.single()
        assertEquals(TaskProtocolFrequencyStatus.SHORTFALL, outcome.status)
        assertEquals(1, outcome.placedExposures)
        assertEquals(1, outcome.shortfall)
        assertEquals("SAME_PROTOCOL_DUPLICATE_DAY_REMOVED_FREQUENCY_SHORTFALL", outcome.reasonCode)
        assertEquals(1, TaskProtocolExposureMetadata.fromJsonString(generated.items.single().taskProtocolSemanticsJson!!).exposureIndex)
    }

    @Test
    fun overlappingProtocolsCreditDecelerationAndReactionOnlyUpToTaskFrequency() {
        // Six-corner + lateral-shuttle both attribute DECELERATION; the physical protocols
        // remain separate, while target credit is the union of at most two weekly sessions.
        val deceleration = TaskProtocolNonAdditiveCreditLedger()
        assertTrue(deceleration.credit(1 to 2, maximumWeeklyExposures = 2)) // six-corner
        assertTrue(deceleration.credit(1 to 5, maximumWeeklyExposures = 2)) // six-corner
        assertFalse(deceleration.credit(1 to 2, maximumWeeklyExposures = 2)) // lateral-shuttle overlap
        assertFalse(deceleration.credit(1 to 4, maximumWeeklyExposures = 2)) // cap already met

        // Six-corner + split-step may both be selected for REACTION. Their overlapping
        // task attribution still contributes no more than the protocol-level weekly target.
        val reaction = TaskProtocolNonAdditiveCreditLedger()
        assertTrue(reaction.credit(1 to 1, maximumWeeklyExposures = 2)) // six-corner
        assertTrue(reaction.credit(1 to 4, maximumWeeklyExposures = 2)) // six-corner
        assertFalse(reaction.credit(1 to 2, maximumWeeklyExposures = 2)) // split-step overlap
        assertFalse(reaction.credit(1 to 5, maximumWeeklyExposures = 2)) // cap already met
    }

    private fun protocolItem(
        definition: ApprovedTaskProtocolDefinition,
        authorization: TaskProtocolB6Authorization,
        exposure: Int,
        day: Int
    ): ProgramSkeletonItem {
        val shape = definition.shape
        val seconds = shape.maxSeconds ?: shape.seconds ?: 0
        val reps = shape.reps ?: 0
        return ProgramSkeletonItem(
            localId = "${definition.protocolId}-$exposure", weekNumber = 1, dayOfWeek = day, orderIndex = exposure,
            exerciseStableKey = definition.stableKey, exerciseName = definition.stableKey, category = "SPORTS",
            restSeconds = shape.restSeconds, prescription = shape.format(), setCount = shape.setCount,
            reps = reps, weightKg = 0.0, seconds = seconds, selectionReason = "exact protocol",
            weightSource = definition.provenance.name, stableKey = definition.stableKey,
            selectionRole = definition.selectionRole,
            setPrescriptions = List(shape.setCount) { index -> ProgramSetPrescription(
                setIndex = index + 1, reps = reps, weightKg = 0.0, seconds = seconds,
                loadState = ProgramLoadState.NOT_APPLICABLE
            ) },
            taskProtocolSemanticsJson = TaskProtocolExposureMetadata(authorization, exposure).toJsonString()
        )
    }
}
