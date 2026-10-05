package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel

internal enum class TaskAuthorityCompletenessState { TASK_AUTHORITY_COMPLETE, TASK_AUTHORITY_INCOMPLETE, TASK_AUTHORITY_CONFLICT }

internal data class ExactTaskOwnerRef(val stableKey: String, val selectionRole: String) {
    init { require(stableKey.isNotBlank() && selectionRole.isNotBlank()) }
}

internal data class TaskAuthorityCompletenessInput(
    val task: CanonicalTaskTarget,
    val exactTargetPresent: Boolean,
    val owner: ExactTaskOwnerRef?,
    val transferLevel: BadmintonObjectiveTransferLevel?,
    val binding: ReviewedTaskProtocolBindingResolution,
    val frequency: TaskFrequencyAuthority,
    val shape: TaskPrescriptionShape?,
    val conflictingAuthority: Boolean = false
)

internal data class TaskAuthorityCompletenessResult(
    val state: TaskAuthorityCompletenessState,
    val reasonCodes: List<String>
)

internal object TaskAuthorityCompletenessEvaluator {
    fun evaluate(input: TaskAuthorityCompletenessInput): TaskAuthorityCompletenessResult {
        if (input.conflictingAuthority ||
            input.binding.state == ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS ||
            input.frequency.state == TaskFrequencyAuthorityState.CONFLICT
        ) return TaskAuthorityCompletenessResult(
            TaskAuthorityCompletenessState.TASK_AUTHORITY_CONFLICT,
            buildList {
                if (input.conflictingAuthority) add("TASK_MULTIPLE_AUTHORITY_CONFLICT")
                if (input.binding.state == ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS) add("TASK_PROTOCOL_BINDING_CONFLICT")
                if (input.frequency.state == TaskFrequencyAuthorityState.CONFLICT) add(input.frequency.reasonCode)
            }.distinct().sorted()
        )

        val missing = buildList {
            if (!input.exactTargetPresent) add("TASK_TARGET_MISSING")
            if (input.owner == null) add("EXACT_B5_TASK_OWNER_MISSING")
            if (input.transferLevel != BadmintonObjectiveTransferLevel.DIRECT) add("DIRECT_TASK_RELATION_REQUIRED")
            if (input.binding.state != ReviewedTaskProtocolBindingState.EXACT_REVIEWED_PROTOCOL_BOUND) add(input.binding.reasonCode)
            else {
                val binding = input.binding.binding
                if (binding == null || input.owner == null || binding.stableKey != input.owner.stableKey ||
                    binding.selectionRole != input.owner.selectionRole || binding.task != input.task) {
                    add("TASK_PROTOCOL_BINDING_IDENTITY_MISMATCH")
                }
            }
            if (input.frequency.weeklySessions == null || input.frequency.state !in setOf(
                    TaskFrequencyAuthorityState.PERSONAL_BASELINE,
                    TaskFrequencyAuthorityState.REVIEWED_PROTOCOL
                )) add(input.frequency.reasonCode)
            else if (input.frequency.task != input.task ||
                (input.frequency.state == TaskFrequencyAuthorityState.PERSONAL_BASELINE &&
                    (input.frequency.sourceStableKey != input.owner?.stableKey ||
                        input.frequency.sourceSelectionRole != input.owner?.selectionRole)) ||
                (input.frequency.state == TaskFrequencyAuthorityState.REVIEWED_PROTOCOL &&
                    input.frequency.reviewedProtocolBinding != input.binding.binding)
            ) add("TASK_FREQUENCY_AUTHORITY_IDENTITY_MISMATCH")
            val shape = input.shape
            if (shape == null) add("TASK_PRESCRIPTION_SHAPE_MISSING")
            else if (!shape.executionContextComplete) add("TASK_LOAD_OR_ACTIVITY_SEMANTICS_UNRESOLVED")
        }.distinct().sorted()

        return if (missing.isEmpty()) TaskAuthorityCompletenessResult(
            TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE,
            listOf("EXACT_TASK_AUTHORITY_CHAIN_COMPLETE")
        ) else TaskAuthorityCompletenessResult(
            TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE,
            missing
        )
    }
}
