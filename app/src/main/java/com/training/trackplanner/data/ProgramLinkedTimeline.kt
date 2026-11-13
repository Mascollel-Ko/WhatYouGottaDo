package com.training.trackplanner.data

import java.time.LocalDate

/**
 * Read-only presentation of already-applied program links. Dates come from live
 * workout_entries and therefore follow calendar moves / 밀기 automatically.
 * A track is only shared within one application and one source program.
 */
internal object ProgramLinkedTimeline {
    data class Neighbor(val entryId: Long, val date: String)
    data class Item(
        val position: Int,
        val total: Int,
        val previous: Neighbor?,
        val next: Neighbor?
    )

    fun build(
        links: List<ProgramWorkoutLink>,
        entries: List<WorkoutEntry>
    ): Map<Long, Item> {
        val byEntryId = entries.associateBy { it.id }
        val output = mutableMapOf<Long, Item>()
        links.filter { it.trackId.isNotBlank() && it.trackId != "untracked" &&
            it.mode != ProgressionMode.OFF && it.entryId in byEntryId
        }.groupBy { Triple(it.applicationId, it.sourceProgramStableKey, it.trackId) }
            .values.forEach { group ->
                val chain = group.sortedWith(compareBy<ProgramWorkoutLink> { it.sequence }.thenBy { it.entryId })
                // Duplicated sequence means the saved linkage is inconsistent; do not
                // present a misleading predecessor or successor.
                if (chain.map { it.sequence }.distinct().size != chain.size) {
                    chain.forEach { output[it.entryId] = Item(0, chain.size, null, null) }
                } else {
                    chain.forEachIndexed { index, link ->
                        fun adjacent(i: Int): Neighbor? =
                            chain.getOrNull(i)?.let { other ->
                                byEntryId[other.entryId]?.let { Neighbor(other.entryId, it.date) }
                            }
                        output[link.entryId] = Item(
                            position = index + 1,
                            total = chain.size,
                            previous = adjacent(index - 1),
                            next = adjacent(index + 1)
                        )
                    }
                }
            }
        return output
    }
}

internal fun programLinkedDateLabel(iso: String): String = runCatching {
    val date = LocalDate.parse(iso)
    date.monthValue.toString() + "월 " + date.dayOfMonth + "일"
}.getOrDefault(iso)
