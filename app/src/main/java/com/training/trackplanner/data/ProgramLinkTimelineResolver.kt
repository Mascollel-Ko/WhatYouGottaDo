package com.training.trackplanner.data

import java.time.LocalDate

/**
 * A single source of truth for visualizing applied Plan connections.
 * The link group is the applied-program track snapshot; calendar dates come from
 * current WorkoutEntry rows, not guessed from the original week/weekday.
 */
internal data class ProgramLinkTimeline(
    val ordinal: Int,
    val total: Int,
    val previousDate: String?,
    val nextDate: String?,
    val previousEntryId: Long?,
    val nextEntryId: Long?
)

internal object ProgramLinkTimelineResolver {
    fun resolve(
        entryId: Long,
        links: List<ProgramWorkoutLink>,
        entries: List<WorkoutEntry>
    ): ProgramLinkTimeline? {
        val current = links.singleOrNull { it.entryId == entryId } ?: return null
        if (current.trackId.isBlank() || current.trackId == "untracked" ||
            current.mode == ProgressionMode.OFF || current.mode == ProgressionMode.DIRECT
        ) return null
        val chain = links.filter {
            it.applicationId == current.applicationId &&
                it.sourceProgramStableKey == current.sourceProgramStableKey &&
                it.trackId == current.trackId &&
                it.mode != ProgressionMode.OFF &&
                it.mode != ProgressionMode.DIRECT
        }.sortedWith(compareBy<ProgramWorkoutLink> { it.sequence }.thenBy { it.entryId })
        val index = chain.indexOfFirst { it.entryId == entryId }
        if (index < 0 || chain.map { it.sequence }.distinct().size != chain.size) return null
        val dateById = entries.associate { it.id to it.date }
        val previous = chain.getOrNull(index - 1)
        val next = chain.getOrNull(index + 1)
        // A dangling link must not fabricate a date or infer its destination.
        return ProgramLinkTimeline(
            ordinal = index + 1, total = chain.size,
            previousDate = previous?.let { dateById[it.entryId] },
            nextDate = next?.let { dateById[it.entryId] },
            previousEntryId = previous?.entryId,
            nextEntryId = next?.entryId
        )
    }

    fun koreanDate(date: String): String =
        runCatching { LocalDate.parse(date) }.getOrNull()?.let {
            "${it.monthValue}월 ${it.dayOfMonth}일"
        } ?: date
}
