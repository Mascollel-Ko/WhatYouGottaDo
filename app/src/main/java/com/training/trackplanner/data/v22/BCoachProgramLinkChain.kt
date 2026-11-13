package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionMode

/**
 * The Plan editor owns session linkage. Applied workouts snapshot that linkage as
 * (applicationId, sourceProgramStableKey, trackId) and a monotonically increasing
 * program sequence. Calendar date, weekday, and week number are NOT linkage keys.
 *
 * Do not fall back to same stableKey/day on a different track: that would reconnect
 * an explicit SEPARATE/OFF exercise without the athlete's authorization.
 */
internal object BCoachProgramLinkChain {
    data class Match(val status: String, val link: ProgramWorkoutLink? = null) {
        val found: Boolean get() = status == "LINKED" && link != null
    }

    fun successor(
        source: ProgramWorkoutLink,
        links: List<ProgramWorkoutLink>
    ): Match = neighbor(source, links, forward = true)

    fun predecessor(
        target: ProgramWorkoutLink,
        links: List<ProgramWorkoutLink>
    ): Match = neighbor(target, links, forward = false)

    private fun neighbor(
        current: ProgramWorkoutLink,
        links: List<ProgramWorkoutLink>,
        forward: Boolean
    ): Match {
        if (current.trackId == "untracked" || current.trackId.isBlank() ||
            current.mode != ProgressionMode.APP
        ) return Match("LINK_MODE_NOT_ELIGIBLE")

        val siblings = links.filter { candidate ->
            candidate.entryId != current.entryId &&
                candidate.applicationId == current.applicationId &&
                candidate.sourceProgramStableKey == current.sourceProgramStableKey &&
                candidate.trackId == current.trackId &&
                if (forward) candidate.sequence > current.sequence
                else candidate.sequence < current.sequence
        }
        val nextSequence = if (forward) siblings.minOfOrNull { it.sequence }
            else siblings.maxOfOrNull { it.sequence }
        if (nextSequence == null) {
            return Match(if (forward) "NO_NEXT_LINKED_EXERCISE" else "NO_LINKED_PREDECESSOR")
        }
        val nearest = siblings.filter { it.sequence == nextSequence }
        if (nearest.size != 1) return Match("AMBIGUOUS_LINKED_SEQUENCE")
        val chosen = nearest.single()
        if (chosen.mode != ProgressionMode.APP) return Match("LINK_MODE_NOT_ELIGIBLE")
        return Match("LINKED", chosen)
    }
}
