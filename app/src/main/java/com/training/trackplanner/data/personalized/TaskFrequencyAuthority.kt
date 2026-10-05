package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import java.time.DayOfWeek
import java.time.LocalDate

internal enum class TaskFrequencyAuthorityState { PERSONAL_BASELINE, REVIEWED_PROTOCOL, NONE, CONFLICT }

internal data class TaskFrequencyAuthority(
    val state: TaskFrequencyAuthorityState,
    val weeklySessions: Int? = null,
    val sourceStableKey: String? = null,
    val sourceSelectionRole: String? = null,
    val task: CanonicalTaskTarget? = null,
    val reviewedProtocolBinding: ReviewedTaskProtocolBinding? = null,
    val eligibleCompletedWeeks: Int = 0,
    val directExposureWeeks: Int = 0,
    val reasonCode: String
) {
    init {
        require(eligibleCompletedWeeks >= 0 && directExposureWeeks >= 0)
        require(directExposureWeeks <= eligibleCompletedWeeks)
        when (state) {
            TaskFrequencyAuthorityState.PERSONAL_BASELINE -> {
                require(weeklySessions != null && weeklySessions > 0)
                require(!sourceStableKey.isNullOrBlank() && !sourceSelectionRole.isNullOrBlank() &&
                    task != null && reviewedProtocolBinding == null)
            }
            TaskFrequencyAuthorityState.REVIEWED_PROTOCOL -> {
                require(weeklySessions != null && weeklySessions > 0)
                require(reviewedProtocolBinding != null && task == reviewedProtocolBinding.task)
                require(sourceStableKey == reviewedProtocolBinding.stableKey &&
                    sourceSelectionRole == reviewedProtocolBinding.selectionRole)
            }
            TaskFrequencyAuthorityState.NONE,
            TaskFrequencyAuthorityState.CONFLICT -> require(weeklySessions == null && reviewedProtocolBinding == null)
        }
    }

    companion object {
        fun none(task: CanonicalTaskTarget?, reasonCode: String = "TASK_WEEKLY_FREQUENCY_AUTHORITY_ABSENT") =
            TaskFrequencyAuthority(TaskFrequencyAuthorityState.NONE, task = task, reasonCode = reasonCode)

        fun conflict(task: CanonicalTaskTarget?, reasonCode: String = "TASK_WEEKLY_FREQUENCY_CONFLICT") =
            TaskFrequencyAuthority(TaskFrequencyAuthorityState.CONFLICT, task = task, reasonCode = reasonCode)
    }
}

/** A completed week bucket derived from exact successful observations for one canonical owner-role and task. */
internal data class CompletedOwnerTaskWeek(
    val stableKey: String,
    val selectionRole: String,
    val task: CanonicalTaskTarget,
    val weekStart: LocalDate,
    val successfulSessionIds: Set<String>,
    val transferLevel: BadmintonObjectiveTransferLevel,
    val completedWeek: Boolean = true
) {
    init {
        require(stableKey.isNotBlank() && selectionRole.isNotBlank())
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY)
        require(successfulSessionIds.none(String::isBlank))
    }
}

/** Uses the existing two-completed-week/two-exposure-week minimum and requires a stable observed frequency. */
internal object PersonalTaskFrequencyAuthorityResolver {
    const val MIN_ELIGIBLE_COMPLETED_WEEKS = 2
    const val MIN_DIRECT_EXPOSURE_WEEKS = 2

    fun resolve(
        owner: ExactTaskOwnerRef,
        task: CanonicalTaskTarget,
        observations: List<CompletedOwnerTaskWeek>
    ): TaskFrequencyAuthority {
        val exact = observations.filter {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole && it.task == task
        }
        val eligible = exact.filter {
            it.completedWeek && it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT
        }.sortedBy { it.weekStart }
        if (eligible.isEmpty()) return TaskFrequencyAuthority.none(task)

        val duplicateWeeks = eligible.groupBy(CompletedOwnerTaskWeek::weekStart)
        if (duplicateWeeks.values.any { rows -> rows.distinctBy { it.successfulSessionIds }.size > 1 }) {
            return TaskFrequencyAuthority.conflict(task, "TASK_PERSONAL_WEEK_EVIDENCE_CONFLICT")
        }
        val weeklyCounts = duplicateWeeks.toSortedMap().values.map { it.first().successfulSessionIds.size }
        val exposureWeeks = weeklyCounts.count { it > 0 }
        if (weeklyCounts.size < MIN_ELIGIBLE_COMPLETED_WEEKS || exposureWeeks < MIN_DIRECT_EXPOSURE_WEEKS) {
            return TaskFrequencyAuthority.none(task, "TASK_PERSONAL_FREQUENCY_MINIMUM_WEEKS_NOT_MET")
        }
        if (weeklyCounts.distinct().size != 1) {
            return TaskFrequencyAuthority.conflict(task, "TASK_PERSONAL_WEEKLY_FREQUENCY_CONFLICT")
        }
        val observedSessions = weeklyCounts.first()
        if (observedSessions <= 0) return TaskFrequencyAuthority.none(task, "TASK_PERSONAL_WEEKLY_FREQUENCY_NOT_POSITIVE")
        return TaskFrequencyAuthority(
            state = TaskFrequencyAuthorityState.PERSONAL_BASELINE,
            weeklySessions = observedSessions,
            sourceStableKey = owner.stableKey,
            sourceSelectionRole = owner.selectionRole,
            task = task,
            eligibleCompletedWeeks = weeklyCounts.size,
            directExposureWeeks = exposureWeeks,
            reasonCode = "TASK_PERSONAL_EXACT_OWNER_COMPLETED_WEEK_BASELINE"
        )
    }
}
