package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class C23TaskAuthorityFoundationTest {
    private val exactBinding = ReviewedTaskProtocolBinding(
        stableKey = "reviewed_accel_owner",
        selectionRole = "CANONICAL_STIMULUS_TASK_ACCELERATION",
        task = CanonicalTaskTarget.ACCELERATION,
        reviewedCategory = ReviewedBadmintonCategory.ACCELERATION,
        source = ReviewedTaskProtocolBindingSource.PROJECT_APPROVED_EXACT_BINDING,
        weeklySessions = 1
    )

    @Test
    fun `protocol binding requires exact owner task and reviewed category`() {
        val approvals = listOf(exactBinding)
        assertEquals(
            ReviewedTaskProtocolBindingState.EXACT_REVIEWED_PROTOCOL_BOUND,
            ReviewedTaskProtocolBindings.resolve(
                "reviewed_accel_owner", exactBinding.selectionRole, CanonicalTaskTarget.ACCELERATION,
                ReviewedBadmintonCategory.ACCELERATION, approvals
            ).state
        )
        assertEquals(
            ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve(
                "reviewed_accel_owner", exactBinding.selectionRole, CanonicalTaskTarget.FOOTWORK,
                ReviewedBadmintonCategory.ACCELERATION, approvals
            ).state
        )
        assertEquals(
            ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve(
                "reviewed_accel_owner", exactBinding.selectionRole, CanonicalTaskTarget.ACCELERATION,
                ReviewedBadmintonCategory.STEP, approvals
            ).state
        )
        assertEquals(
            ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve(
                "other_owner", exactBinding.selectionRole, CanonicalTaskTarget.ACCELERATION,
                ReviewedBadmintonCategory.ACCELERATION, approvals
            ).state
        )
        assertEquals(
            ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve(
                "reviewed_accel_owner", exactBinding.selectionRole, CanonicalTaskTarget.ACCELERATION, null, approvals
            ).state
        )
        assertEquals(ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve("ex_33841b88", "CANONICAL_STIMULUS_TASK_ACCELERATION", CanonicalTaskTarget.FOOTWORK,
                ReviewedBadmintonCategory.STEP).state)
        assertEquals(ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve("reviewed_accel_owner", "OTHER_ROLE", CanonicalTaskTarget.ACCELERATION,
                ReviewedBadmintonCategory.ACCELERATION, approvals).state)
        assertEquals(ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
            ReviewedTaskProtocolBindings.resolve("reviewed_accel_owner", "CANONICAL_STIMULUS_TASK_FOOTWORK", CanonicalTaskTarget.ACCELERATION,
                ReviewedBadmintonCategory.ACCELERATION, approvals).state)
    }

    @Test
    fun `conflicting exact protocol bindings fail closed`() {
        val conflict = exactBinding.copy(source = ReviewedTaskProtocolBindingSource.EXPLICIT_REVIEWED_TASK_PROTOCOL)
        val result = ReviewedTaskProtocolBindings.resolve(
            exactBinding.stableKey, exactBinding.selectionRole, exactBinding.task, exactBinding.reviewedCategory,
            listOf(exactBinding, conflict)
        )
        assertEquals(ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS, result.state)
        assertNull(result.binding)
    }

    @Test
    fun `personal task frequency needs repeated exact direct completed weeks`() {
        val twoWeeks = listOf(
            week("owner_a", CanonicalTaskTarget.ACCELERATION, "2026-06-01", "a", "b"),
            week("owner_a", CanonicalTaskTarget.ACCELERATION, "2026-06-08", "a", "b")
        )
        val authority = PersonalTaskFrequencyAuthorityResolver.resolve(
            ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), CanonicalTaskTarget.ACCELERATION, twoWeeks
        )
        assertEquals(TaskFrequencyAuthorityState.PERSONAL_BASELINE, authority.state)
        assertEquals(2, authority.weeklySessions)
        assertEquals(2, authority.eligibleCompletedWeeks)
        assertEquals(2, authority.directExposureWeeks)
        assertEquals("owner_a", authority.sourceStableKey)
        assertEquals("TASK_ROLE_A", authority.sourceSelectionRole)
    }

    @Test
    fun `frequency refuses another owner supportive exposure and one week`() {
        val task = CanonicalTaskTarget.ACCELERATION
        val otherOwnerOnly = listOf(
            week("owner_b", task, "2026-06-01", "a"),
            week("owner_b", task, "2026-06-08", "a")
        )
        assertEquals(TaskFrequencyAuthorityState.NONE,
            PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, otherOwnerOnly).state)

        val otherRoleOnly = listOf(
            week("owner_a", task, "2026-06-01", "a", selectionRole = "TASK_ROLE_B"),
            week("owner_a", task, "2026-06-08", "a", selectionRole = "TASK_ROLE_B")
        )
        assertEquals(TaskFrequencyAuthorityState.NONE,
            PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, otherRoleOnly).state)

        val supportive = listOf(
            week("owner_a", task, "2026-06-01", "a", transfer = BadmintonObjectiveTransferLevel.SUPPORTIVE),
            week("owner_a", task, "2026-06-08", "a", transfer = BadmintonObjectiveTransferLevel.SUPPORTIVE)
        )
        assertEquals(TaskFrequencyAuthorityState.NONE,
            PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, supportive).state)

        val oneWeek = listOf(week("owner_a", task, "2026-06-01", "a"))
        assertEquals(TaskFrequencyAuthorityState.NONE,
            PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, oneWeek).state)

        val noExposure = listOf(
            week("owner_a", task, "2026-06-01"),
            week("owner_a", task, "2026-06-08")
        )
        assertEquals(TaskFrequencyAuthorityState.NONE,
            PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, noExposure).state)

        val changingFrequency = listOf(
            week("owner_a", task, "2026-06-01", "a"),
            week("owner_a", task, "2026-06-08", "a", "b")
        )
        assertEquals(TaskFrequencyAuthorityState.CONFLICT,
            PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, changingFrequency).state)
    }

    @Test
    fun `conflicting duplicate week evidence is not arbitrarily selected`() {
        val task = CanonicalTaskTarget.DECELERATION
        val observations = listOf(
            week("owner_a", task, "2026-06-01", "a"),
            week("owner_a", task, "2026-06-01", "a", "b"),
            week("owner_a", task, "2026-06-08", "a")
        )
        val result = PersonalTaskFrequencyAuthorityResolver.resolve(ExactTaskOwnerRef("owner_a", "TASK_ROLE_A"), task, observations)
        assertEquals(TaskFrequencyAuthorityState.CONFLICT, result.state)
        assertNull(result.weeklySessions)
    }

    @Test
    fun `reviewed weekly frequency must be explicitly present on exact binding`() {
        val withoutFrequency = exactBinding.copy(weeklySessions = null)
        try {
            TaskFrequencyAuthority(
                state = TaskFrequencyAuthorityState.REVIEWED_PROTOCOL,
                weeklySessions = 1,
                sourceStableKey = withoutFrequency.stableKey,
                sourceSelectionRole = withoutFrequency.selectionRole,
                task = withoutFrequency.task,
                reviewedProtocolBinding = withoutFrequency,
                reasonCode = "TEST_UNSUPPORTED_REVIEWED_FREQUENCY"
            )
            throw AssertionError("Expected a reviewed binding without weekly frequency to be rejected")
        } catch (_: IllegalArgumentException) {
            // The within-session guide cannot silently turn into frequency authority.
        }
    }

    @Test
    fun `available weekdays and reviewed within-session guide do not create weekly frequency`() {
        val task = CanonicalTaskTarget.FOOTWORK
        val availableDays = RecordBasedReviewedPolicy.defaultWeekdays(4)
        val guide = RecordBasedReviewedPolicy.badminton(ReviewedBadmintonCategory.STEP)
        assertEquals(4, availableDays.size)
        assertNull(guide.taskShape.targetRpe)
        assertEquals(TaskFrequencyAuthorityState.NONE,
            PersonalTaskFrequencyAuthorityResolver.resolve(
                ExactTaskOwnerRef("ex_33841b88", "CANONICAL_STIMULUS_TASK_ACCELERATION"), task, emptyList()
            ).state)
    }

    @Test
    fun `task shapes round trip scalar range per side duration and rest`() {
        val shapes = listOf(
            TaskPrescriptionShape(TaskPrescriptionMode.REPETITIONS_PER_SIDE, 3, reps = 5,
                laterality = TaskLateralitySemantics.PER_SIDE, restSeconds = 75),
            TaskPrescriptionShape(TaskPrescriptionMode.DURATION_RANGE_SECONDS, 3,
                minSeconds = 10, maxSeconds = 20, restSeconds = 60),
            TaskPrescriptionShape(TaskPrescriptionMode.REPETITION_RANGE, 3,
                minReps = 8, maxReps = 12, restSeconds = 60),
            TaskPrescriptionShape(TaskPrescriptionMode.REPETITIONS, 2, reps = 6, restSeconds = 45),
            TaskPrescriptionShape(TaskPrescriptionMode.DURATION_SECONDS, 2, seconds = 30, restSeconds = 45)
        )
        shapes.forEach { shape -> assertEquals(shape, TaskPrescriptionShape.fromJson(shape.toJson())) }
        assertEquals("3 세트 × 5회/side · 휴식 75초", shapes[0].format())
        assertEquals("3 라운드 × 10–20초 · 휴식 60초", shapes[1].format())
        assertEquals("3 세트 × 8–12회 · 휴식 60초", shapes[2].format())
        assertFalse(shapes.first().executionContextComplete)
        assertTrue(shapes.first().copy(
            loadMode = TaskPrescriptionLoadMode.BODYWEIGHT,
            activityKind = PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        ).executionContextComplete)
        assertFalse(shapes.first().toJson().has("display"))
    }

    @Test
    fun `reviewed task guide gains typed shape without changing legacy compatibility fields`() {
        val step = RecordBasedReviewedPolicy.badminton(ReviewedBadmintonCategory.STEP)
        assertEquals(3, step.setCount)
        assertEquals(0, step.reps)
        assertEquals(20, step.seconds)
        assertEquals(60, step.restSeconds)
        assertEquals("3라운드 x 10-20초", step.text)
        assertEquals(TaskPrescriptionMode.DURATION_RANGE_SECONDS, step.taskShape.mode)
        assertEquals(10, step.taskShape.minSeconds)
        assertEquals(20, step.taskShape.maxSeconds)

        val acceleration = RecordBasedReviewedPolicy.badminton(ReviewedBadmintonCategory.ACCELERATION)
        assertEquals(5, acceleration.reps)
        assertEquals(75, acceleration.restSeconds)
        assertEquals(TaskPrescriptionMode.REPETITIONS_PER_SIDE, acceleration.taskShape.mode)
        assertEquals(TaskLateralitySemantics.PER_SIDE, acceleration.taskShape.laterality)

        val antiRotation = RecordBasedReviewedPolicy.badminton(ReviewedBadmintonCategory.ANTI_ROTATION)
        assertEquals(10, antiRotation.reps)
        assertEquals(TaskPrescriptionMode.REPETITION_RANGE, antiRotation.taskShape.mode)
        assertEquals(8, antiRotation.taskShape.minReps)
        assertEquals(12, antiRotation.taskShape.maxReps)
    }

    @Test
    fun `invalid task shapes and ranges are rejected`() {
        assertRejected {
            TaskPrescriptionShape(TaskPrescriptionMode.REPETITIONS_PER_SIDE, 3, reps = 5,
                restSeconds = 75, laterality = TaskLateralitySemantics.NOT_APPLICABLE)
        }
        assertRejected {
            TaskPrescriptionShape(TaskPrescriptionMode.DURATION_RANGE_SECONDS, 3,
                minSeconds = 20, maxSeconds = 10, restSeconds = 60)
        }
        assertRejected {
            TaskPrescriptionShape(TaskPrescriptionMode.REPETITION_RANGE, 3,
                minReps = 8, maxReps = 12, reps = 10, restSeconds = 60)
        }
        assertRejected {
            TaskPrescriptionShape(TaskPrescriptionMode.DURATION_SECONDS, 3, seconds = 0, restSeconds = 60)
        }
        assertRejected {
            TaskPrescriptionShape(TaskPrescriptionMode.REPETITIONS, 0, reps = 8, restSeconds = 60)
        }
    }

    @Test
    fun `task authority completeness requires all exact typed links`() {
        val task = CanonicalTaskTarget.ACCELERATION
        val frequency = TaskFrequencyAuthority(
            TaskFrequencyAuthorityState.PERSONAL_BASELINE,
            weeklySessions = 1,
            sourceStableKey = exactBinding.stableKey,
            sourceSelectionRole = exactBinding.selectionRole,
            task = task,
            eligibleCompletedWeeks = 2,
            directExposureWeeks = 2,
            reasonCode = "TEST_EXACT_PERSONAL_FREQUENCY"
        )
        val shape = TaskPrescriptionShape(
            mode = TaskPrescriptionMode.REPETITIONS_PER_SIDE,
            setCount = 3,
            reps = 5,
            laterality = TaskLateralitySemantics.PER_SIDE,
            restSeconds = 75,
            loadMode = TaskPrescriptionLoadMode.BODYWEIGHT,
            activityKind = PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        )
        val binding = ReviewedTaskProtocolBindings.resolve(
            exactBinding.stableKey, exactBinding.selectionRole, task, exactBinding.reviewedCategory, listOf(exactBinding)
        )
        val complete = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(
                task = task,
                exactTargetPresent = true,
                owner = ExactTaskOwnerRef(exactBinding.stableKey, "CANONICAL_STIMULUS_TASK_ACCELERATION"),
                transferLevel = BadmintonObjectiveTransferLevel.DIRECT,
                binding = binding,
                frequency = frequency,
                shape = shape
            )
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE, complete.state)

        val reviewedFrequency = TaskFrequencyAuthority(
            state = TaskFrequencyAuthorityState.REVIEWED_PROTOCOL,
            weeklySessions = 1,
            sourceStableKey = exactBinding.stableKey,
            sourceSelectionRole = exactBinding.selectionRole,
            task = task,
            reviewedProtocolBinding = exactBinding,
            reasonCode = "TEST_EXACT_REVIEWED_FREQUENCY"
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE,
            TaskAuthorityCompletenessEvaluator.evaluate(
                TaskAuthorityCompletenessInput(task, true,
                    ExactTaskOwnerRef(exactBinding.stableKey, "CANONICAL_STIMULUS_TASK_ACCELERATION"),
                    BadmintonObjectiveTransferLevel.DIRECT, binding, reviewedFrequency, shape)
            ).state)
        val wrongReviewedFrequency = reviewedFrequency.copy(
            reviewedProtocolBinding = exactBinding.copy(stableKey = "another_exact_owner"),
            sourceStableKey = "another_exact_owner"
        )
        val wrongReviewedFrequencyResult = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(task, true,
                ExactTaskOwnerRef(exactBinding.stableKey, "CANONICAL_STIMULUS_TASK_ACCELERATION"),
                BadmintonObjectiveTransferLevel.DIRECT, binding, wrongReviewedFrequency, shape)
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, wrongReviewedFrequencyResult.state)
        assertTrue("TASK_FREQUENCY_AUTHORITY_IDENTITY_MISMATCH" in wrongReviewedFrequencyResult.reasonCodes)

        val missingMapping = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(task, true, ExactTaskOwnerRef(exactBinding.stableKey, "TASK"),
                BadmintonObjectiveTransferLevel.DIRECT,
                ReviewedTaskProtocolBindings.resolve(exactBinding.stableKey, "TASK", task, exactBinding.reviewedCategory),
                frequency, shape)
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, missingMapping.state)
        assertTrue("NO_APPROVED_TASK_PROTOCOL_BINDING" in missingMapping.reasonCodes)

        val missingFrequency = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(task, true, ExactTaskOwnerRef(exactBinding.stableKey, "TASK"),
                BadmintonObjectiveTransferLevel.DIRECT, binding, TaskFrequencyAuthority.none(task), shape)
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, missingFrequency.state)

        val lossyShape = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(task, true, ExactTaskOwnerRef(exactBinding.stableKey, "TASK"),
                BadmintonObjectiveTransferLevel.DIRECT, binding, frequency, shape.copy(loadMode = null))
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, lossyShape.state)
        assertTrue("TASK_LOAD_OR_ACTIVITY_SEMANTICS_UNRESOLVED" in lossyShape.reasonCodes)

        val conflicting = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(task, true, ExactTaskOwnerRef(exactBinding.stableKey, "TASK"),
                BadmintonObjectiveTransferLevel.DIRECT,
                ReviewedTaskProtocolBindings.resolve(exactBinding.stableKey, exactBinding.selectionRole, task, exactBinding.reviewedCategory,
                    listOf(exactBinding, exactBinding.copy(source = ReviewedTaskProtocolBindingSource.EXPLICIT_REVIEWED_TASK_PROTOCOL))),
                frequency, shape)
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_CONFLICT, conflicting.state)

        val wrongOwnerBinding = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(
                task = task,
                exactTargetPresent = true,
                owner = ExactTaskOwnerRef("different_owner", "TASK"),
                transferLevel = BadmintonObjectiveTransferLevel.DIRECT,
                binding = binding,
                frequency = frequency,
                shape = shape
            )
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, wrongOwnerBinding.state)
        assertTrue("TASK_PROTOCOL_BINDING_IDENTITY_MISMATCH" in wrongOwnerBinding.reasonCodes)

        val wrongOwnerFrequency = TaskAuthorityCompletenessEvaluator.evaluate(
            TaskAuthorityCompletenessInput(
                task = task,
                exactTargetPresent = true,
                owner = ExactTaskOwnerRef(exactBinding.stableKey, "TASK"),
                transferLevel = BadmintonObjectiveTransferLevel.DIRECT,
                binding = binding,
                frequency = frequency.copy(sourceStableKey = "other_owner"),
                shape = shape
            )
        )
        assertEquals(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, wrongOwnerFrequency.state)
        assertTrue("TASK_FREQUENCY_AUTHORITY_IDENTITY_MISMATCH" in wrongOwnerFrequency.reasonCodes)
    }

    private fun week(
        stableKey: String,
        task: CanonicalTaskTarget,
        date: String,
        vararg sessions: String,
        selectionRole: String = "TASK_ROLE_A",
        transfer: BadmintonObjectiveTransferLevel = BadmintonObjectiveTransferLevel.DIRECT
    ) = CompletedOwnerTaskWeek(
        stableKey = stableKey,
        selectionRole = selectionRole,
        task = task,
        weekStart = LocalDate.parse(date),
        successfulSessionIds = sessions.toSet(),
        transferLevel = transfer
    )

    private fun assertRejected(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected invalid task prescription shape to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected constructor validation.
        }
    }
}
