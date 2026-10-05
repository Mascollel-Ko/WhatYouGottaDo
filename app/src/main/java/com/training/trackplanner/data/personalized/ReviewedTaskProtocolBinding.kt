package com.training.trackplanner.data.personalized

/** Explicit provenance for a reviewed protocol's use as a canonical task prescription source. */
internal enum class ReviewedTaskProtocolBindingSource {
    PROJECT_APPROVED_EXACT_BINDING,
    EXPLICIT_REVIEWED_TASK_PROTOCOL
}

internal data class ReviewedTaskProtocolBinding(
    val stableKey: String,
    val selectionRole: String,
    val task: CanonicalTaskTarget,
    val reviewedCategory: ReviewedBadmintonCategory,
    val source: ReviewedTaskProtocolBindingSource,
    /** Null unless the approved protocol itself explicitly owns sessions per week. */
    val weeklySessions: Int? = null
) {
    init {
        require(stableKey.isNotBlank() && selectionRole.isNotBlank())
        require(weeklySessions == null || weeklySessions > 0)
    }
}

internal enum class ReviewedTaskProtocolBindingState {
    EXACT_REVIEWED_PROTOCOL_BOUND,
    NO_APPROVED_PROTOCOL_BINDING,
    MULTIPLE_CONFLICTING_BINDINGS
}

internal data class ReviewedTaskProtocolBindingResolution(
    val state: ReviewedTaskProtocolBindingState,
    val binding: ReviewedTaskProtocolBinding? = null,
    val reasonCode: String
)

/** Exact-key registry only. Empty until a repository-owned approval explicitly binds a protocol to a task. */
internal object ReviewedTaskProtocolBindings {
    val approved: List<ReviewedTaskProtocolBinding> = emptyList()

    fun resolve(
        stableKey: String,
        selectionRole: String,
        task: CanonicalTaskTarget,
        reviewedCategory: ReviewedBadmintonCategory?,
        bindings: List<ReviewedTaskProtocolBinding> = approved
    ): ReviewedTaskProtocolBindingResolution {
        if (reviewedCategory == null) return missing("NO_REVIEWED_CATEGORY")
        val exact = bindings.filter {
            it.stableKey == stableKey && it.selectionRole == selectionRole &&
                it.task == task && it.reviewedCategory == reviewedCategory
        }.distinct()
        return when (exact.size) {
            0 -> missing("NO_APPROVED_TASK_PROTOCOL_BINDING")
            1 -> ReviewedTaskProtocolBindingResolution(
                ReviewedTaskProtocolBindingState.EXACT_REVIEWED_PROTOCOL_BOUND,
                exact.single(),
                "EXACT_REVIEWED_PROTOCOL_BINDING"
            )
            else -> ReviewedTaskProtocolBindingResolution(
                ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS,
                reasonCode = "MULTIPLE_EXACT_REVIEWED_PROTOCOL_BINDINGS"
            )
        }
    }

    private fun missing(reason: String) = ReviewedTaskProtocolBindingResolution(
        ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING,
        reasonCode = reason
    )
}
