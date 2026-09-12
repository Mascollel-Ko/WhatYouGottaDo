package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/** Reviewed scheduling identity only: neither progression MAIN nor a strength/proxy/physiology model. */
internal object StrengthPrimaryMainPolicy {
    private val canonicalKeys = setOf("barbell_back_squat", "barbell_bench_press", "ex_a61f1e96",
        "barbell_deadlift", "ex_e41f4c2b")

    fun isPrimary(stableKey: String): Boolean = stableKey in canonicalKeys
    fun objective(rows: List<ProgramSkeletonItem>, days: Collection<Int>): StrengthPrimaryObjective =
        counts(days.map { day -> rows.count { it.dayOfWeek == day && isPrimary(it.exerciseStableKey) } })
    fun counts(counts: Collection<Int>) = StrengthPrimaryObjective(counts.sumOf { (it - 1).coerceAtLeast(0) }, counts.maxOrNull() ?: 0)
}

data class StrengthPrimaryObjective(val overlap: Int, val maximumPerDay: Int): Comparable<StrengthPrimaryObjective> {
    override fun compareTo(other: StrengthPrimaryObjective) = compareValuesBy(this, other,
        StrengthPrimaryObjective::overlap, StrengthPrimaryObjective::maximumPerDay)
}
